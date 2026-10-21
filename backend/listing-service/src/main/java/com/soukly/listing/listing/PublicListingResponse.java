package com.soukly.listing.listing;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Body of {@code GET /listings/{id}} for a reader who does not own the listing (CATA-03): what the listing page
 * shows. Never the phone number or the email of the seller: the number is only given by
 * {@code POST /listings/{id}/contact}.
 *
 * @param status   always {@code PUBLISHED}
 * @param price    whole dinars, null "on quote"
 * @param currency always {@code TND} in V1
 * @param photos   READY photos, cover first, with their 1600 and 400 px WebP versions (ANN-02)
 */
public record PublicListingResponse(UUID id, ListingStatus status, ListingKind kind, String categoryCode,
        String title, String description, PriceMode priceMode, Long price, String currency, boolean negotiable,
        String governorateCode, String delegationCode, Map<String, Object> attributes,
        List<ListingResponse.Photo> photos, Instant publishedAt, ListingResponse.Seller seller) {

    static PublicListingResponse of(ListingReading reading) {
        Listing listing = reading.view().listing();
        ListingContent content = listing.content();
        return new PublicListingResponse(listing.id(), listing.status(), content.kind(), content.categoryCode(),
                content.title(), content.description(), content.priceMode(), content.price(), ListingResponse.CURRENCY,
                content.negotiable(), content.governorateCode(), content.delegationCode(), content.attributes(),
                reading.view().photos().stream().map(ListingResponse.Photo::of).toList(), listing.publishedAt(),
                ListingResponse.Seller.of(listing.ownerId(), reading.seller()));
    }
}
