package com.soukly.listing.listing;

import java.time.Instant;
import java.util.UUID;

/**
 * A listing as stored. The seller's phone number, email and name are never part of it.
 *
 * @param ownerId {@code sub} of the access token that created it
 * @param version incremented by every change, sent as {@code ETag}
 */
public record Listing(UUID id, UUID ownerId, ListingStatus status, ListingContent content, long version,
        Instant createdAt, Instant updatedAt, Instant publishedAt) {

    public boolean isOwnedBy(UUID userId) {
        return ownerId.equals(userId);
    }

    Listing edited(ListingContent newContent, Instant now) {
        return new Listing(id, ownerId, status, newContent, version + 1, createdAt, now, publishedAt);
    }
}
