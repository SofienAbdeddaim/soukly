package com.soukly.listing.listing;

/**
 * Life cycle of a listing: {@code DRAFT} → {@code PUBLISHED} → {@code EXPIRED}, and back to {@code PUBLISHED} by a
 * renewal (ANN-03). ANN-05 adds {@code INACTIVE} and {@code DELETED}.
 */
public enum ListingStatus {
    DRAFT,
    PUBLISHED,
    EXPIRED
}
