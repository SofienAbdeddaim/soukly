package com.soukly.listing.listing;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What the seller enters in a listing. Every field but the category may be empty on a draft.
 *
 * @param price      whole dinars (TND)
 * @param attributes specific attributes of the subcategory: {@code String}, {@code Long} or {@code Boolean} values
 */
public record ListingContent(String categoryCode, String title, String description, PriceMode priceMode, Long price,
        boolean negotiable, String governorateCode, String delegationCode, Map<String, Object> attributes) {

    public ListingContent {
        // Insertion order kept: the display order of the subcategory
        attributes = Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }

    public ListingKind kind() {
        return ListingKind.of(categoryCode);
    }
}
