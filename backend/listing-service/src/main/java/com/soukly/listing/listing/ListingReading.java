package com.soukly.listing.listing;

import com.soukly.listing.seller.SellerProfile;

/**
 * A listing read by {@code GET /listings/{id}} (ANN-01, CATA-03).
 *
 * @param owner  the reader owns it: the full view of {@link ListingResponse}, otherwise the public one of
 *               {@link PublicListingResponse}
 * @param seller public profile of the owner; null for a draft, which only its owner reads
 */
public record ListingReading(ListingView view, boolean owner, SellerProfile seller) {
}
