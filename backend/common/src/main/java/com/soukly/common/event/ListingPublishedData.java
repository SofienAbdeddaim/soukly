package com.soukly.common.event;

import java.net.URI;
import java.time.Instant;
import java.util.List;

/**
 * {@code data} of {@code listing.published}.
 *
 * @param price      whole dinars, null with {@code ON_QUOTE}
 * @param currency   always {@code TND} in V1
 * @param photoUrls  WebP versions of at most 1600 px, in the seller's order; the first is the cover
 * @param publicUrls page of the listing in each language
 * @param shareTo    social networks chosen by the seller ({@code FACEBOOK}, {@code INSTAGRAM}), possibly empty
 * @param shareLanguage language of the post texts ({@code fr}, {@code ar}, {@code en}), null when the seller chose
 *                   no network (SOC-03)
 * @param shortRef   short reference of the listing, written in the Instagram captions (SOC-05)
 */
public record ListingPublishedData(String title, String description, String categoryCode, String kind,
        String priceMode, Long price, String currency, boolean negotiable, String governorateCode,
        String delegationCode, URI coverPhotoUrl, List<URI> photoUrls, PublicUrls publicUrls, List<String> shareTo,
        Instant expiresAt, String shareLanguage, String shortRef) {

    /** Page of the listing in each language of the site. */
    public record PublicUrls(URI fr, URI ar, URI en) {
    }
}
