package com.soukly.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Envelope shared by every listing event ({@code docs/events/listing-events.md}). Never carries personal data of the
 * seller: only the account id.
 *
 * @param eventId       deduplication key of the consumers
 * @param schemaVersion version of {@code data} for this type
 * @param occurredAt    when the change happened
 * @param <T>           type of {@code data}: a record of this package for a producer, a JSON tree for a consumer
 */
public record EventEnvelope<T>(UUID eventId, String type, int schemaVersion, Instant occurredAt, UUID listingId,
        UUID ownerId, T data) {
}
