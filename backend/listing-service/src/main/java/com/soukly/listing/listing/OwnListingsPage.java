package com.soukly.listing.listing;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Body of {@code GET /listings/mine} (ANN-05).
 *
 * @param nextCursor to read the next page of the same status; absent on the last page
 * @param counts     listings of the account in each status of {@link #LISTED}; only on the first page (without
 *                   {@code cursor})
 */
public record OwnListingsPage(List<OwnListingCard> items,
        @JsonInclude(JsonInclude.Include.NON_NULL) String nextCursor,
        @JsonInclude(JsonInclude.Include.NON_NULL) Map<ListingStatus, Long> counts) {

    /** The tabs of the page; deleted listings are never listed. */
    static final List<ListingStatus> LISTED = List.of(ListingStatus.PUBLISHED, ListingStatus.DRAFT,
            ListingStatus.EXPIRED, ListingStatus.INACTIVE);
}
