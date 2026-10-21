package com.soukly.listing.listing;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A listing on the "My listings" page of its owner (ANN-05).
 *
 * @param price               whole dinars, null "on quote" or not filled in yet
 * @param coverThumbnailUrl   400 px WebP of the cover, null without photo
 * @param publishedAt         first publication, null for a draft
 * @param expiresAt           end of visibility of a published listing, its past expiry for an expired one, the one
 *                            it had for a deactivated one; null for a draft
 * @param missingFields       required fields still empty, in form order; only for a draft
 * @param missingFieldsCount  size of {@code missingFields}; only for a draft
 * @param contactRevealsCount members who were shown the phone number on this listing (CATA-03)
 */
public record OwnListingCard(UUID id, ListingStatus status, String title, ListingKind kind, String categoryCode,
        PriceMode priceMode, Long price, String currency, URI coverThumbnailUrl, Instant updatedAt,
        Instant publishedAt, Instant expiresAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<String> missingFields,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer missingFieldsCount, long contactRevealsCount) {

    static OwnListingCard of(Listing listing, URI coverThumbnailUrl, List<String> missingFields,
            long contactRevealsCount) {
        ListingContent content = listing.content();
        return new OwnListingCard(listing.id(), listing.status(), content.title(), content.kind(),
                content.categoryCode(), content.priceMode(), content.price(), ListingResponse.CURRENCY,
                coverThumbnailUrl, listing.updatedAt(), listing.publishedAt(), listing.expiresAt(), missingFields,
                missingFields == null ? null : missingFields.size(), contactRevealsCount);
    }
}
