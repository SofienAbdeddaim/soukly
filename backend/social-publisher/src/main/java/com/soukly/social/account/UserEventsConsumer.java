package com.soukly.social.account;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.soukly.common.event.UserEventEnvelope;
import com.soukly.common.event.UserEvents;
import com.soukly.social.event.InvalidEventException;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads {@code soukly.user-events} in the group {@code social-publisher} and handles {@code user.deleted}: the social
 * network connections of the account are deleted (SOC-01). Every other type is ignored. A failure is retried, then the message goes to
 * {@code soukly.user-events.dlq} ({@code UserEventsConfig}).
 */
@Component
class UserEventsConsumer {

    private static final Logger log = LoggerFactory.getLogger(UserEventsConsumer.class);

    private static final TypeReference<UserEventEnvelope<JsonNode>> ENVELOPE = new TypeReference<>() {
    };

    private final DeletedAccounts deletedAccounts;
    private final JsonMapper json;

    UserEventsConsumer(DeletedAccounts deletedAccounts, JsonMapper json) {
        this.deletedAccounts = deletedAccounts;
        this.json = json;
    }

    @KafkaListener(topics = UserEvents.TOPIC)
    void onEvent(ConsumerRecord<String, String> record) {
        UserEventEnvelope<JsonNode> event = parse(record);
        if (!UserEvents.DELETED.equals(event.type())) {
            return;
        }
        if (event.schemaVersion() != UserEvents.SCHEMA_VERSION) {
            throw new InvalidEventException("Unsupported schemaVersion " + event.schemaVersion() + " of event "
                    + event.eventId());
        }
        deletedAccounts.deleteConnections(event.eventId(), event.userId());
    }

    private UserEventEnvelope<JsonNode> parse(ConsumerRecord<String, String> record) {
        UserEventEnvelope<JsonNode> event;
        try {
            event = record.value() == null ? null : json.readValue(record.value(), ENVELOPE);
        } catch (JacksonException e) {
            // The exception message may quote the payload: only its type is kept.
            throw new InvalidEventException("Malformed user event at offset " + record.offset() + " of partition "
                    + record.partition() + ": " + e.getClass().getSimpleName());
        }
        if (event == null || event.eventId() == null || event.type() == null || event.userId() == null) {
            throw new InvalidEventException("User event without eventId, type or userId at offset "
                    + record.offset() + " of partition " + record.partition());
        }
        log.debug("User event {} of type {}", event.eventId(), event.type());
        return event;
    }
}
