package com.soukly.listing.photo;

import java.time.Instant;
import java.util.UUID;

/**
 * A photo as stored.
 *
 * @param position      order among the READY photos of the listing, 0 being the cover; null otherwise
 * @param width         of the 1600 version, null until READY
 * @param bytes         declared size until the completion, then the real size of the original
 * @param rejectionCode set when REJECTED, see {@link PhotoRejection}
 */
record ListingPhoto(UUID id, UUID listingId, PhotoStatus status, Integer position, Integer width, Integer height,
        long bytes, String rejectionCode, Instant createdAt, Instant updatedAt) {
}
