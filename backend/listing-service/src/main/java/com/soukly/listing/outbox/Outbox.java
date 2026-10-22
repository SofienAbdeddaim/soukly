package com.soukly.listing.outbox;

import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.soukly.common.event.EventEnvelope;
import com.soukly.common.outbox.OutboxRelay;
import com.soukly.common.outbox.OutboxRepository;

import tools.jackson.databind.json.JsonMapper;

/**
 * Writes listing events in {@code outbox_events}, in the transaction of the change they describe: the event exists
 * if and only if the change is committed. Business code never sends to Kafka itself; {@link OutboxRelay} does.
 */
@Component
public class Outbox {

    private final OutboxRepository repository;
    private final JsonMapper json;

    Outbox(OutboxRepository repository, JsonMapper json) {
        this.repository = repository;
        this.json = json;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void write(EventEnvelope<?> event) {
        repository.insert(event.eventId(), event.listingId(), event.type(), json.writeValueAsString(event),
                event.occurredAt().truncatedTo(ChronoUnit.MICROS));
    }
}
