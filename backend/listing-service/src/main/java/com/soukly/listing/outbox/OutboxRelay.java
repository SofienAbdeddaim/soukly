package com.soukly.listing.outbox;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.transaction.support.TransactionTemplate;

import com.soukly.common.event.ListingEvents;

/**
 * Sends the unsent events of the outbox to {@code soukly.listing-events}, oldest first, with the listing id as key,
 * and marks them sent only once the broker acknowledged them. Several relays (instances) can run at the same time:
 * each locks its batch with {@code FOR UPDATE SKIP LOCKED}, and an event is only sent when every older unsent event
 * of its listing is in the same batch, so the events of a listing always leave in order.
 *
 * <p>When the broker is unreachable, the events stay in the outbox with one more attempt and the type of the error,
 * and leave at a later run. Payloads are never logged.
 */
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository repository;
    private final KafkaOperations<String, String> kafka;
    private final TransactionTemplate transaction;
    private final OutboxProperties properties;
    private final Clock clock;

    OutboxRelay(OutboxRepository repository, KafkaOperations<String, String> kafka, TransactionTemplate transaction,
            OutboxProperties properties, Clock clock) {
        this.repository = repository;
        this.kafka = kafka;
        this.transaction = transaction;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Sends one batch.
     *
     * @return the events sent and acknowledged by this run; a full batch means the outbox may hold more
     */
    public int relayOnce() {
        Integer sent = transaction.execute(status -> {
            List<OutboxEvent> batch = repository.lockUnsent(properties.batchSize());
            return batch.isEmpty() ? 0 : send(inOrder(batch));
        });
        return sent == null ? 0 : sent;
    }

    /** Runs batches until one is not full: the outbox is empty, or the broker refuses the events. */
    public void relayAll() {
        while (relayOnce() >= properties.batchSize()) {
            // a full batch was sent: the outbox may hold more
        }
    }

    /** Deletes the events sent more than {@link OutboxProperties#retention()} ago. */
    public int purgeSent() {
        int deleted = repository.deleteSentBefore(clock.instant().minus(properties.retention())
                .truncatedTo(ChronoUnit.MICROS));
        if (deleted > 0) {
            log.info("Outbox purge: {} sent event(s) deleted", deleted);
        }
        return deleted;
    }

    /**
     * The events of the batch that can leave now: for each listing, its events up to the first older unsent event
     * that another relay holds.
     */
    private List<OutboxEvent> inOrder(List<OutboxEvent> batch) {
        Set<UUID> inBatch = new HashSet<>();
        Set<UUID> aggregates = new HashSet<>();
        batch.forEach(event -> {
            inBatch.add(event.id());
            aggregates.add(event.aggregateId());
        });
        long maxSequence = batch.getLast().sequence();
        Set<UUID> blocked = new HashSet<>();
        Set<UUID> sendable = new HashSet<>();
        for (OutboxRepository.UnsentRef unsent : repository.unsentOf(aggregates, maxSequence)) {
            if (blocked.contains(unsent.aggregateId())) {
                continue;
            }
            if (inBatch.contains(unsent.id())) {
                sendable.add(unsent.id());
            } else {
                blocked.add(unsent.aggregateId());
            }
        }
        return batch.stream().filter(event -> sendable.contains(event.id())).toList();
    }

    private int send(List<OutboxEvent> events) {
        Map<OutboxEvent, CompletableFuture<?>> sends = new LinkedHashMap<>();
        for (OutboxEvent event : events) {
            CompletableFuture<?> future;
            try {
                future = kafka.send(ListingEvents.TOPIC, event.aggregateId().toString(), event.payload());
            } catch (RuntimeException e) {
                future = CompletableFuture.failedFuture(e);
            }
            sends.put(event, future);
        }
        Instant deadline = clock.instant().plus(properties.sendTimeout());
        List<UUID> sent = new ArrayList<>();
        List<OutboxEvent> failed = new ArrayList<>();
        String firstError = null;
        for (Map.Entry<OutboxEvent, CompletableFuture<?>> send : sends.entrySet()) {
            try {
                long wait = Math.max(0, deadline.toEpochMilli() - clock.millis());
                send.getValue().get(wait, TimeUnit.MILLISECONDS);
                sent.add(send.getKey().id());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                failed.add(send.getKey());
                firstError = firstError == null ? describe(e) : firstError;
            } catch (ExecutionException | TimeoutException | RuntimeException e) {
                String error = describe(e instanceof ExecutionException ? e.getCause() : e);
                repository.recordFailure(send.getKey().id(), error);
                failed.add(send.getKey());
                firstError = firstError == null ? error : firstError;
            }
        }
        if (!sent.isEmpty()) {
            repository.markSent(sent, clock.instant().truncatedTo(ChronoUnit.MICROS));
        }
        if (!failed.isEmpty()) {
            OutboxEvent first = failed.getFirst();
            log.warn("Could not send {} outbox event(s) to Kafka, they stay in the outbox (first: event {} of type {},"
                    + " attempt {}): {}", failed.size(), first.id(), first.type(), first.attempts() + 1, firstError);
        }
        return sent.size();
    }

    /** Type and message of the error: Kafka errors describe the broker or the topic, never the payload. */
    private static String describe(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return cause.getClass().getName() + (message == null ? "" : ": " + message);
    }
}
