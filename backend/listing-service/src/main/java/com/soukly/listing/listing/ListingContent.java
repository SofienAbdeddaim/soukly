package com.soukly.listing.listing;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the seller enters in a listing. Every field but the category may be empty on a draft.
 *
 * @param price      whole dinars (TND)
 * @param attributes    specific attributes of the subcategory: {@code String}, {@code Long} or {@code Boolean} values
 * @param shareTo       networks to share the listing to when it is published (SOC-03), in the order of
 *                      {@link ShareNetwork}; null until the seller chose (the preview then ticks their preferences)
 * @param shareLanguage language of the post texts, {@code fr}, {@code ar} or {@code en}, or null
 */
public record ListingContent(String categoryCode, String title, String description, PriceMode priceMode, Long price,
        boolean negotiable, String governorateCode, String delegationCode, Map<String, Object> attributes,
        List<ShareNetwork> shareTo, String shareLanguage) {

    public ListingContent {
        // Insertion order kept: the display order of the subcategory
        attributes = Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
        shareTo = shareTo == null ? null : List.copyOf(shareTo);
    }

    /** A content without a choice of networks yet. */
    public ListingContent(String categoryCode, String title, String description, PriceMode priceMode, Long price,
            boolean negotiable, String governorateCode, String delegationCode, Map<String, Object> attributes) {
        this(categoryCode, title, description, priceMode, price, negotiable, governorateCode, delegationCode,
                attributes, null, null);
    }

    public ListingKind kind() {
        return ListingKind.of(categoryCode);
    }
}
