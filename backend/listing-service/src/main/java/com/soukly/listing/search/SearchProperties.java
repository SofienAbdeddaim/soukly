package com.soukly.listing.search;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Public search of the listings (CATA-01), bound from {@code soukly.listing.search.*}.
 *
 * @param defaultLimit        cards per page without {@code limit}
 * @param maxLimit            highest {@code limit} accepted
 * @param similarityThreshold lowest trigram word similarity for a title or a brand to match a query with a typing
 *                            mistake ({@code pg_trgm.word_similarity_threshold}; "peugot" in "Peugeot 208" is 0.57)
 * @param cursorSecret        key that signs the cursors ({@code SEARCH_CURSOR_SECRET}); when empty, a random key is
 *                            drawn at start, so the cursors of a previous start are refused ({@code cursor.invalid})
 * @param cacheMaxAge         {@code Cache-Control: public, max-age} of the answers
 */
@ConfigurationProperties("soukly.listing.search")
record SearchProperties(@DefaultValue("24") int defaultLimit, @DefaultValue("48") int maxLimit,
        @DefaultValue("0.5") double similarityThreshold, String cursorSecret,
        @DefaultValue("30s") Duration cacheMaxAge) {
}
