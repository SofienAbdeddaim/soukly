package com.soukly.listing.search;

import java.util.List;

/**
 * A validated search.
 *
 * @param query          {@code q} trimmed, null without text
 * @param categoryCodes  subcategories to search, null for every category
 * @param priceMin       whole dinars; with {@code priceMax}, excludes the listings "on quote"
 * @param cursor         position after the previous page, null for the first page
 */
record SearchCriteria(String query, List<String> categoryCodes, String governorateCode, String delegationCode,
        Long priceMin, Long priceMax, List<AttributeFilter> attributes, ListingRankingStrategy ranking, int limit,
        SearchCursor cursor) {

    boolean filtersPrice() {
        return priceMin != null || priceMax != null;
    }
}
