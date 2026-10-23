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
 * @param deletedAt      when the seller deleted it (ANN-05), null unless {@code DELETED}
 * @param shortRef       short reference of the Instagram captions (SOC-05), {@link ShortRefs}; null only for a
 *                       listing created by a version before SOC-05 and not published since
 */
public record Listing(UUID id, UUID ownerId, ListingStatus status, ListingContent content, long version,
        Instant createdAt, Instant updatedAt, Instant publishedAt, Instant expiresAt, Instant reminderSentAt,
        Instant deletedAt, String shortRef) {

    public boolean isOwnedBy(UUID userId) {
        return ownerId.equals(userId);
    }

    Listing edited(ListingContent newContent, Instant now) {
        return new Listing(id, ownerId, status, newContent, version + 1, createdAt, now, publishedAt, expiresAt,
                reminderSentAt, deletedAt, shortRef);
    }

    Listing withContent(ListingContent newContent) {
        return new Listing(id, ownerId, status, newContent, version, createdAt, updatedAt, publishedAt, expiresAt,
                reminderSentAt, deletedAt, shortRef);
    }

    Listing withShortRef(String ref) {
        return new Listing(id, ownerId, status, content, version, createdAt, updatedAt, publishedAt, expiresAt,
                reminderSentAt, deletedAt, ref);
    }

    /** Online until {@code now + ttl}. */
    Listing published(Instant now, Duration ttl) {
        return new Listing(id, ownerId, ListingStatus.PUBLISHED, content, version + 1, createdAt, now, now,
                now.plus(ttl), null, null, shortRef);
    }

    /** Online again until {@code now + ttl}, with a new reminder; the first publication date is kept. */
    Listing renewed(Instant now, Duration ttl) {
        return new Listing(id, ownerId, ListingStatus.PUBLISHED, content, version + 1, createdAt, now, publishedAt,
                now.plus(ttl), null, null, shortRef);
    }

    /** Hidden by its seller; the expiry date is kept, a renewal replaces it. */
    Listing deactivated(Instant now) {
        return new Listing(id, ownerId, ListingStatus.INACTIVE, content, version + 1, createdAt, now, publishedAt,
                expiresAt, reminderSentAt, null, shortRef);
    }

    /** Deleted by its seller, purged later. */
    Listing deleted(Instant now) {
        return new Listing(id, ownerId, ListingStatus.DELETED, content, version + 1, createdAt, now, publishedAt,
                expiresAt, reminderSentAt, now, shortRef);
    }
}
