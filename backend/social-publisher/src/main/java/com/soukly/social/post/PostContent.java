package com.soukly.social.post;

import java.net.URI;
import java.util.List;

/**
 * What a post is made of, taken from {@code listing.published} and kept in {@code social_posts.content} (JSON) until
 * the publication builds the text with the {@code CaptionBuilder}. No personal data: the event carries none.
 *
 * @param price      whole dinars, {@code null} with {@code ON_QUOTE}
 * @param photoUrls  public URLs of the 1600 px versions, in the seller's order
 * @param listingUrl page of the listing in {@code language}, without the tracking parameters
 * @param language   {@code shareLanguage}: {@code fr}, {@code ar} or {@code en}
 */
record PostContent(String title, String description, String categoryCode, String priceMode, Long price,
        boolean negotiable, String governorateCode, String delegationCode, List<URI> photoUrls, URI listingUrl,
        String language) {
}
