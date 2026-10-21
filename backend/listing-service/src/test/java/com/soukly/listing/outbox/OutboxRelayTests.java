package com.soukly.listing.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.jayway.jsonpath.JsonPath;
import com.soukly.common.event.EventEnvelope;
import com.soukly.common.event.ListingEvents;
import com.soukly.listing.support.IntegrationTest;
import com.soukly.listing.support.TestRedpanda;

/**
 * Relay of the outbox (ANN-03): order per listing, exactly one send per event with two relays at the same time, and
 * events kept while the broker is unreachable.
 */
class OutboxRelayTests extends IntegrationTest {

    /** Not a type of the contract: consumers ignore unknown types. */
    private static final String TEST_TYPE = "test.relay";

    @Autowired
    private Outbox outbox;

    @Autowired
    private OutboxRelay relay;

    @Autowired
    private OutboxRepository repository;

    @Autowired
    private KafkaTemplate<String, String> kafka;

    @Autowired
    private OutboxProperties properties;

    @Autowired
    private PlatformTransactionManager transactions;

    @Test
    void twoRelaysAtTheSameTimeSendEveryEventOnceAndInOrderPerListing() throws Exception {
        // 6 listings, 40 events each, written interleaved
        List<UUID> listings = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID());
        Map<UUID, List<UUID>> written = new HashMap<>();
        TransactionTemplate transaction = new TransactionTemplate(transactions);
        for (int n = 0; n < 40; n++) {
            int number = n;
            transaction.executeWithoutResult(status -> listings.forEach(listing -> written
                    .computeIfAbsent(listing, key -> new ArrayList<>()).add(write(listing, number))));
        }
        OutboxProperties small = new OutboxProperties(properties.relayInterval(), properties.relayInitialDelay(),
                7, properties.sendTimeout(), properties.retention(), properties.purgeInterval(),
                properties.partitions());
        OutboxRelay first = new OutboxRelay(repository, kafka, transaction, small, clock);
        OutboxRelay second = new OutboxRelay(repository, kafka, transaction, small, clock);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Integer>> runs = executor.invokeAll(List.of(untilEmpty(first, listings),
                    untilEmpty(second, listings)));
            int sent = 0;
            for (Future<Integer> run : runs) {
                sent += run.get();
            }
            assertThat(sent).isEqualTo(240);
        }

        Set<String> keys = new HashSet<>(listings.stream().map(UUID::toString).toList());
        List<ConsumerRecord<String, String>> records = TestRedpanda.read(ListingEvents.TOPIC,
                record -> keys.contains(record.key()), received -> received.size() >= 240, Duration.ofSeconds(30));
        assertThat(records).hasSize(240);
        for (UUID listing : listings) {
            List<UUID> received = records.stream().filter(record -> record.key().equals(listing.toString()))
                    .map(record -> UUID.fromString(JsonPath.read(record.value(), "$.eventId"))).toList();
            // Every event once, in the order it was written
            assertThat(received).isEqualTo(written.get(listing));
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE aggregate_id IN (?, ?, ?, ?, ?, ?)"
                + " AND sent_at IS NOT NULL AND attempts = 0", Integer.class, listings.toArray())).isEqualTo(240);
    }

    @Test
    void eventsWaitInTheOutboxWhileTheBrokerIsDownThenLeaveOnce() throws Exception {
        UUID listing = UUID.randomUUID();
        UUID eventId = new TransactionTemplate(transactions).execute(status -> write(listing, 1));
        OutboxRelay unreachable = new OutboxRelay(repository, unreachableBroker(), new TransactionTemplate(
                transactions), properties, clock);

        unreachable.relayAll();
        unreachable.relayAll();

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT sent_at, attempts, last_error FROM outbox_events WHERE id = ?", eventId);
        assertThat(row.get("sent_at")).isNull();
        assertThat(row.get("attempts")).isEqualTo(2);
        assertThat((String) row.get("last_error")).startsWith("org.apache.kafka");

        // The broker is back
        relay.relayAll();

        assertThat(jdbc.queryForObject("SELECT sent_at IS NOT NULL FROM outbox_events WHERE id = ?", Boolean.class,
                eventId)).isTrue();
        List<ConsumerRecord<String, String>> records = TestRedpanda.readKey(ListingEvents.TOPIC, listing.toString(),
                2, Duration.ofSeconds(5));
        assertThat(records).hasSize(1);
        assertThat(JsonPath.<String>read(records.getFirst().value(), "$.eventId")).isEqualTo(eventId.toString());
        relay.relayAll();
        assertThat(TestRedpanda.readKey(ListingEvents.TOPIC, listing.toString(), 2, Duration.ofSeconds(3)))
                .hasSize(1);
    }

    @Test
    void sentEventsAreDeletedAfterSevenDays() {
        TransactionTemplate transaction = new TransactionTemplate(transactions);
        UUID old = transaction.execute(status -> write(UUID.randomUUID(), 1));
        UUID recent = transaction.execute(status -> write(UUID.randomUUID(), 1));
        UUID unsent = transaction.execute(status -> write(UUID.randomUUID(), 1));
        Instant now = clock.instant();
        jdbc.update("UPDATE outbox_events SET sent_at = ? WHERE id = ?",
                Timestamp.from(now.minus(Duration.ofDays(7)).minusSeconds(1)), old);
        jdbc.update("UPDATE outbox_events SET sent_at = ? WHERE id = ?",
                Timestamp.from(now.minus(Duration.ofDays(6))), recent);
        jdbc.update("UPDATE outbox_events SET created_at = ? WHERE id = ?",
                Timestamp.from(now.minus(Duration.ofDays(30))), unsent);

        assertThat(relay.purgeSent()).isGreaterThanOrEqualTo(1);

        assertThat(jdbc.queryForList("SELECT id FROM outbox_events WHERE id IN (?, ?, ?)", UUID.class, old, recent,
                unsent)).containsExactlyInAnyOrder(recent, unsent);
    }

    private UUID write(UUID listing, int number) {
        EventEnvelope<Map<String, Integer>> event = new EventEnvelope<>(UUID.randomUUID(), TEST_TYPE, 1,
                clock.instant(), listing, UUID.randomUUID(), Map.of("number", number));
        outbox.write(event);
        return event.eventId();
    }

    /** Runs a relay until no event of these listings is left unsent; returns the events it sent. */
    private Callable<Integer> untilEmpty(OutboxRelay relay, List<UUID> listings) {
        return () -> {
            int sent = 0;
            Instant deadline = Instant.now().plusSeconds(60);
            while (Instant.now().isBefore(deadline) && unsent(listings) > 0) {
                sent += relay.relayOnce();
            }
            return sent;
        };
    }

    private int unsent(List<UUID> listings) {
        return jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE aggregate_id IN (?, ?, ?, ?, ?, ?)"
                + " AND sent_at IS NULL", Integer.class, listings.toArray());
    }

    private static KafkaTemplate<String, String> unreachableBroker() {
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(Map.of(
                // Nothing listens on port 1
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "127.0.0.1:1",
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.MAX_BLOCK_MS_CONFIG, 1000,
                ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 1000,
                ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 2000)));
    }
}
