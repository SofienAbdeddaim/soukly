package com.soukly.listing.search;

/**
 * Order of the search results and the matching keyset pagination: one implementation per value of {@code sort}.
 * Every order ends with {@code published_at DESC, id DESC}, so it is total and a page never repeats or skips a row.
 * The mixed ranking of V2 (promoted listings, distance, recommendations) is a new implementation; the filters and the
 * pagination do not change.
 *
 * <p>The SQL fragments are constants of the implementation, never built from user input. They may use the listing
 * alias {@code l} and the bound parameters {@code :query} (normalized by SQL), {@code :cursorKey},
 * {@code :cursorPublishedAt} and {@code :cursorId}.
 */
public interface ListingRankingStrategy {

    /** The value of {@code sort}, also written in the cursor. */
    String name();

    /** Whether it needs {@code q} (the relevance score). */
    default boolean requiresQuery() {
        return false;
    }

    /** SQL expression selected as {@code rank_key}, or {@code NULL} when the order only uses the date. */
    String rankKey();

    /** {@code ORDER BY} over {@code rank_key}, {@code published_at} and {@code id}. */
    String orderBy();

    /** Condition of the rows that come after the cursor in this order. */
    String after();

    /** The rank key of a row as written in the cursor (a JSON number), or null. */
    Number cursorKey(Object rankKey);

    /**
     * The rank key read back from a cursor, to bind as {@code :cursorKey}.
     *
     * @throws IllegalArgumentException a key of the wrong type, or missing
     */
    Object bindKey(Number cursorKey);
}
