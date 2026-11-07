package com.soukly.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Envelope shared by every social event ({@code docs/events/social-events.md}): the one of the user events, keyed by
 * the account id.
 *
 * @param eventId       deduplication key of the consumers
 * @param schemaVersion version of {@code data} for this type
 * @param occurredAt    when the change happened
 * @param userId        the account, also the message key
 * @param <T>           type of {@code data}: a record of this package for a producer, a JSON tree for a consumer
 */
public record SocialEventEnvelope<T>(UUID eventId, String type, int schemaVersion, Instant occurredAt, UUID userId,
        T data) {
}
