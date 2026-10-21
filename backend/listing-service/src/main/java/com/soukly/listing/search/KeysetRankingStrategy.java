package com.soukly.listing.search;

/**
 * An order by one optional rank key, ascending or descending, then by {@code published_at DESC, id DESC}: the keyset
 * conditions follow from it.
 */
abstract class KeysetRankingStrategy implements ListingRankingStrategy {

    private static final String DATE_AFTER = "(l.published_at, l.id) < (:cursorPublishedAt, :cursorId)";

    private final String name;
    private final String key;
    private final boolean descending;

    /**
     * @param key SQL expression of the rank key, or null to order by date only
     */
    KeysetRankingStrategy(String name, String key, boolean descending) {
        this.name = name;
        this.key = key;
        this.descending = descending;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String rankKey() {
        return key == null ? "NULL" : key;
    }

    @Override
    public String orderBy() {
        String date = "published_at DESC, id DESC";
        return key == null ? date : "rank_key " + (descending ? "DESC" : "ASC") + ", " + date;
    }

    @Override
    public String after() {
        if (key == null) {
            return DATE_AFTER;
        }
        if (descending) {
            return "(" + key + ", l.published_at, l.id) < (:cursorKey, :cursorPublishedAt, :cursorId)";
        }
        return "(" + key + " > :cursorKey OR (" + key + " = :cursorKey AND " + DATE_AFTER + "))";
    }
}
