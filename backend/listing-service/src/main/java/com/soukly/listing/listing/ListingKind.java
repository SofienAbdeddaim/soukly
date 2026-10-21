package com.soukly.listing.listing;

import java.util.Set;

/** Sale or service, deduced from the family of the category, never entered by the seller. */
public enum ListingKind {
    SALE, SERVICE;

    private static final Set<String> SERVICE_FAMILIES = Set.of("services", "jobs");

    /**
     * @param categoryCode a subcategory code, {@code <family>.<subcategory>}
     */
    public static ListingKind of(String categoryCode) {
        String family = categoryCode.substring(0, Math.max(0, categoryCode.indexOf('.')));
        return SERVICE_FAMILIES.contains(family) ? SERVICE : SALE;
    }

    /** Only services may be priced "on quote". */
    public boolean allows(PriceMode priceMode) {
        return priceMode != PriceMode.ON_QUOTE || this == SERVICE;
    }
}
