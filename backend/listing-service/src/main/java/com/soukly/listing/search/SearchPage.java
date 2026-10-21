package com.soukly.listing.search;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Body of {@code GET /listings}.
 *
 * @param nextCursor to read the next page; absent on the last page
 * @param total      every matching listing; only on the first page (without {@code cursor})
 */
public record SearchPage(List<SearchCard> items,
        @JsonInclude(JsonInclude.Include.NON_NULL) String nextCursor,
        @JsonInclude(JsonInclude.Include.NON_NULL) Long total) {
}
