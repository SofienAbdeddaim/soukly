package com.soukly.user.listing;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.soukly.common.event.EventEnvelope;
import com.soukly.common.event.ListingEvents;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads {@code soukly.listing-events} in the group {@code user-service} and handles {@code listing.expiring-soon}
 * only: the reminder email of the seller. Every other type is ignored. A failure is retried, then the message goes to
 * {@code soukly.listing-events.dlq} ({@code ListingEventsConfig}).
 */
@Component
class ListingEventsConsumer {

    private static final Logger log = LoggerFactory.getLogger(ListingEventsConsumer.class);

    private static final TypeReference<EventEnvelope<JsonNode>> ENVELOPE = new TypeReference<>() {
    };

    private final ExpiryReminders reminders;
    private final JsonMapper json;

    ListingEventsConsumer(ExpiryReminders reminders, JsonMapper json) {
        this.reminders = reminders;
        this.json = json;
    }

    @KafkaListener(topics = ListingEvents.TOPIC)
    void onEvent(ConsumerRecord<String, String> record) {
        EventEnvelope<JsonNode> event = parse(record);
        if (!ListingEvents.EXPIRING_SOON.equals(event.type())) {
            return;
        }
        if (event.schemaVersion() != ListingEvents.SCHEMA_VERSION) {
            throw new InvalidEventException("Unsupported schemaVersion " + event.schemaVersion() + " of event "
                    + event.eventId());
        }
        JsonNode data = event.data();
        String title = text(data, "title", event.eventId());
        Instant expiresAt;
        try {
            expiresAt = Instant.parse(text(data, "expiresAt", event.eventId()));
        } catch (DateTimeParseException e) {
            throw new InvalidEventException("Invalid data.expiresAt in event " + event.eventId());
        }
        reminders.remind(event.eventId(), event.ownerId(), event.listingId(), title, expiresAt);
    }

    private EventEnvelope<JsonNode> parse(ConsumerRecord<String, String> record) {
        EventEnvelope<JsonNode> event;
        try {
            event = record.value() == null ? null : json.readValue(record.value(), ENVELOPE);
        } catch (JacksonException e) {
            // The exception message may quote the payload: only its type is kept.
            throw new InvalidEventException("Malformed listing event at offset " + record.offset() + " of partition "
                    + record.partition() + ": " + e.getClass().getSimpleName());
        }
        if (event == null || event.eventId() == null || event.type() == null || event.listingId() == null
                || event.ownerId() == null) {
            throw new InvalidEventException("Listing event without eventId, type, listingId or ownerId at offset "
                    + record.offset() + " of partition " + record.partition());
        }
        log.debug("Listing event {} of type {}", event.eventId(), event.type());
        return event;
    }

    private static String text(JsonNode data, String member, UUID eventId) {
        JsonNode value = data == null ? null : data.get(member);
        if (value == null || !value.isString() || value.asString().isBlank()) {
            throw new InvalidEventException("Missing data." + member + " in event " + eventId);
        }
        return value.asString();
    }
}
