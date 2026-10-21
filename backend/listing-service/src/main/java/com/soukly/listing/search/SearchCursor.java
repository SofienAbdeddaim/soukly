package com.soukly.listing.search;

import java.time.Instant;
import java.util.UUID;

/**
 * Position after the last card of a page: the sort it belongs to, the rank key and the publication date of that card,
 * and its id (keyset pagination).
 *
 * @param key rank key of the last card, null for {@code recent}
 */
record SearchCursor(String sort, Number key, Instant publishedAt, UUID id) {
}
