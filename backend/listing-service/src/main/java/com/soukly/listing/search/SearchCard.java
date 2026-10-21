package com.soukly.listing.search;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.soukly.listing.listing.ListingKind;
import com.soukly.listing.listing.PriceMode;

/**
 * A listing in the search results. Never any data of the seller (account, name, phone number).
 *
 * @param price             whole dinars, null "on quote"
 * @param currency          always {@code TND} in V1
 * @param coverThumbnailUrl 400 px WebP of the cover, null without photo
 * @param summary           at most 3 specific attributes, in the display order of the subcategory (CAT-01)
 */
public record SearchCard(UUID id, String title, ListingKind kind, String categoryCode, PriceMode priceMode,
        Long price, String currency, boolean negotiable, String governorateCode, String delegationCode,
        URI coverThumbnailUrl, int photoCount, Map<String, Object> summary, Instant publishedAt) {
}
