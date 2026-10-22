package com.soukly.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Envelope shared by every user event ({@code docs/events/user-events.md}): the listing envelope with the account id
 * in place of the listing and seller ids. Never carries personal data: only the account id.
 *
 * @param eventId       deduplication key of the consumers
 * @param schemaVersion version of {@code data} for this type
 * @param occurredAt    when the change happened
 * @param userId        the account, also the message key
 * @param <T>           type of {@code data}: a record of this package for a producer, a JSON tree for a consumer
 */
public record UserEventEnvelope<T>(UUID eventId, String type, int schemaVersion, Instant occurredAt, UUID userId,
        T data) {
}
