package com.soukly.common.event;

import java.net.URI;
import java.time.Instant;
import java.util.List;

/**
 * {@code data} of {@code listing.updated} (ANN-05): the members of {@link ListingPublishedData} after the change,
 * without {@code shareTo}, and the names of the members that changed.
 *
 * @param changedFields names of the changed members of the listing ({@code price}, {@code photos}...), in form order
 */
public record ListingUpdatedData(List<String> changedFields, String title, String description, String categoryCode,
        String kind, String priceMode, Long price, String currency, boolean negotiable, String governorateCode,
        String delegationCode, URI coverPhotoUrl, List<URI> photoUrls, ListingPublishedData.PublicUrls publicUrls,
        Instant expiresAt) {
}
