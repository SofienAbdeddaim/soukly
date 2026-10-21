package com.soukly.listing.search;

import org.springframework.stereotype.Component;

/**
 * {@code price-asc} and {@code price-desc}: by price, then the latest publications. Listings "on quote" have no price
 * and come last in both orders: their rank key is a value beyond every price.
 */
abstract class PriceRanking extends KeysetRankingStrategy {

    PriceRanking(String name, String key, boolean descending) {
        super(name, key, descending);
    }

    @Override
    public Number cursorKey(Object rankKey) {
        return ((Number) rankKey).longValue();
    }

    @Override
    public Object bindKey(Number cursorKey) {
        if (!(cursorKey instanceof Long || cursorKey instanceof Integer)) {
            throw new IllegalArgumentException("A whole price is expected");
        }
        return cursorKey.longValue();
    }

    /** Cheapest first; "on quote" after the most expensive one. */
    @Component
    static class Ascending extends PriceRanking {

        Ascending() {
            super("price-asc", "coalesce(l.price_tnd, 9223372036854775807)", false);
        }
    }

    /** Most expensive first; "on quote" after the cheapest one (prices are at least 1). */
    @Component
    static class Descending extends PriceRanking {

        Descending() {
            super("price-desc", "coalesce(l.price_tnd, 0)", true);
        }
    }
}
