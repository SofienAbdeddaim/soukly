package com.soukly.listing.search;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A listing found, as read for its card.
 *
 * @param rankKey  value of the rank key of the ranking (for the cursor), null for {@code recent}
 * @param coverId  READY photo at position 0, null without photo
 */
record SearchRow(UUID id, String title, String categoryCode, String priceMode, Long priceTnd, boolean negotiable,
        String governorateCode, String delegationCode, Map<String, Object> attributes, Instant publishedAt,
        Object rankKey, UUID coverId, int photoCount) {
}
