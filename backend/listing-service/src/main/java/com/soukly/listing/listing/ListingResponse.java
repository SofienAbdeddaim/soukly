package com.soukly.listing.listing;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Body of the listing endpoints. Empty fields are present with {@code null}, so the front end can tell them apart
 * from unknown members.
 *
 * @param price whole dinars (TND)
 */
public record ListingResponse(UUID id, ListingStatus status, long version, ListingKind kind, String categoryCode,
        String title, String description, PriceMode priceMode, Long price, boolean negotiable,
        String governorateCode, String delegationCode, Map<String, Object> attributes, List<String> missingFields,
        List<String> warnings, Instant createdAt, Instant updatedAt) {

    static ListingResponse of(ListingView view) {
        Listing listing = view.listing();
        ListingContent content = listing.content();
        return new ListingResponse(listing.id(), listing.status(), listing.version(), content.kind(),
                content.categoryCode(), content.title(), content.description(), content.priceMode(),
                content.price(), content.negotiable(), content.governorateCode(), content.delegationCode(),
                content.attributes(), view.missingFields(), view.warnings(), listing.createdAt(),
                listing.updatedAt());
    }
}
