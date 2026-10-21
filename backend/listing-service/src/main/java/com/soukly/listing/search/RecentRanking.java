package com.soukly.listing.search;

import org.springframework.stereotype.Component;

/** {@code recent}: the latest publications first; the default without {@code q}. */
@Component
class RecentRanking extends KeysetRankingStrategy {

    RecentRanking() {
        super("recent", null, true);
    }

    @Override
    public Number cursorKey(Object rankKey) {
        return null;
    }

    @Override
    public Object bindKey(Number cursorKey) {
        if (cursorKey != null) {
            throw new IllegalArgumentException("No rank key expected");
        }
        return null;
    }
}
