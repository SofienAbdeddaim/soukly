package com.soukly.listing.listing;

/**
 * Life cycle of a listing: {@code DRAFT} → {@code PUBLISHED} → {@code EXPIRED}, and back to {@code PUBLISHED} by a
 * renewal (ANN-03); {@code INACTIVE} when the seller deactivates it and {@code DELETED} when they delete it (ANN-05).
 * The allowed changes are those of {@link ListingTransitions}.
 */
public enum ListingStatus {
    DRAFT,
    PUBLISHED,
    EXPIRED,
    /** Deactivated by the seller: hidden, and never expires while it stays so. */
    INACTIVE,
    /** Deleted by the seller (logical deletion): hidden from everyone, purged after a delay. */
    DELETED
}
