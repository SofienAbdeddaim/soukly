package com.soukly.listing.listing;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * A listing as stored. The seller's phone number, email and name are never part of it.
 *
 * @param ownerId        {@code sub} of the access token that created it
 * @param version        incremented by every change, sent as {@code ETag}
 * @param publishedAt    first publication (ANN-03), null for a draft
 * @param expiresAt      end of visibility once published, 60 days after the publication or the last renewal
 * @param reminderSentAt when {@code listing.expiring-soon} was written for the current expiry date
 */
public record Listing(UUID id, UUID ownerId, ListingStatus status, ListingContent content, long version,
        Instant createdAt, Instant updatedAt, Instant publishedAt, Instant expiresAt, Instant reminderSentAt) {

    public boolean isOwnedBy(UUID userId) {
        return ownerId.equals(userId);
    }

    Listing edited(ListingContent newContent, Instant now) {
        return new Listing(id, ownerId, status, newContent, version + 1, createdAt, now, publishedAt, expiresAt,
                reminderSentAt);
    }

    Listing withContent(ListingContent newContent) {
        return new Listing(id, ownerId, status, newContent, version, createdAt, updatedAt, publishedAt, expiresAt,
                reminderSentAt);
    }

    /** Online until {@code now + ttl}. */
    Listing published(Instant now, Duration ttl) {
        return new Listing(id, ownerId, ListingStatus.PUBLISHED, content, version + 1, createdAt, now, now,
                now.plus(ttl), null);
    }

    /** Online again until {@code now + ttl}, with a new reminder; the first publication date is kept. */
    Listing renewed(Instant now, Duration ttl) {
        return new Listing(id, ownerId, ListingStatus.PUBLISHED, content, version + 1, createdAt, now, publishedAt,
                now.plus(ttl), null);
    }
}
