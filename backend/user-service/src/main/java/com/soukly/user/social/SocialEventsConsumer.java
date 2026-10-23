package com.soukly.user.social;

import java.util.Set;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.soukly.common.event.SocialEventEnvelope;
import com.soukly.common.event.SocialEvents;
import com.soukly.user.event.InvalidEventException;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads {@code soukly.social-events} in the group {@code user-service} and handles
 * {@code social.connection.needs-reconnect} (SOC-06): the email "connect again". Every other type is ignored. A
 * failure is retried, then the message goes to {@code soukly.social-events.dlq} ({@code EventsConfig}).
 */
@Component
class SocialEventsConsumer {

    private static final Logger log = LoggerFactory.getLogger(SocialEventsConsumer.class);

    private static final TypeReference<SocialEventEnvelope<JsonNode>> ENVELOPE = new TypeReference<>() {
    };

    private static final Set<String> NETWORKS = Set.of("FACEBOOK", "INSTAGRAM");

    private final ReconnectReminders reminders;
    private final JsonMapper json;

    SocialEventsConsumer(ReconnectReminders reminders, JsonMapper json) {
        this.reminders = reminders;
        this.json = json;
    }

    @KafkaListener(topics = SocialEvents.TOPIC)
    void onEvent(ConsumerRecord<String, String> record) {
        SocialEventEnvelope<JsonNode> event = parse(record);
        if (!SocialEvents.CONNECTION_NEEDS_RECONNECT.equals(event.type())) {
            return;
        }
        if (event.schemaVersion() != SocialEvents.SCHEMA_VERSION) {
            throw new InvalidEventException("Unsupported schemaVersion " + event.schemaVersion() + " of event "
                    + event.eventId());
        }
        JsonNode data = event.data();
        JsonNode network = data == null ? null : data.get("network");
        if (network == null || !network.isString() || !NETWORKS.contains(network.asString())) {
            throw new InvalidEventException("Missing or unknown data.network in event " + event.eventId());
        }
        JsonNode accountName = data.get("accountName");
        reminders.remind(event.eventId(), event.userId(), network.asString(),
                accountName != null && accountName.isString() ? accountName.asString() : null);
    }

    private SocialEventEnvelope<JsonNode> parse(ConsumerRecord<String, String> record) {
        SocialEventEnvelope<JsonNode> event;
        try {
            event = record.value() == null ? null : json.readValue(record.value(), ENVELOPE);
        } catch (JacksonException e) {
            // The exception message may quote the payload: only its type is kept.
            throw new InvalidEventException("Malformed social event at offset " + record.offset() + " of partition "
                    + record.partition() + ": " + e.getClass().getSimpleName());
        }
        if (event == null || event.eventId() == null || event.type() == null || event.userId() == null) {
            throw new InvalidEventException("Social event without eventId, type or userId at offset "
                    + record.offset() + " of partition " + record.partition());
        }
        log.debug("Social event {} of type {}", event.eventId(), event.type());
        return event;
    }
}
