package com.soukly.social.photo;

import java.net.URI;
import java.util.Optional;

/**
 * Object keys and URLs of the Instagram versions (SOC-05). A photo URL of {@code listing.published} is the public base
 * URL of the bucket followed by {@code listings/{listingId}/{photoId}/1600.webp} (ANN-02); its Instagram version is
 * {@code …/ig-1080.jpg} under the same prefix, so that listing-service deletes it with the photo. Nothing else is ever
 * read or written.
 */
final class InstagramVariants {

    static final String PREFIX = "listings/";
    static final String SOURCE = "/1600.webp";
    static final String VARIANT = "/ig-1080.jpg";

    private final String base;

    InstagramVariants(URI publicBaseUrl) {
        String url = publicBaseUrl.toString();
        this.base = url.endsWith("/") ? url : url + "/";
    }

    /** The key of the 1600 version behind a photo URL; empty for any other URL. */
    Optional<String> sourceKey(URI photoUrl) {
        String url = photoUrl.toString();
        if (!url.startsWith(base)) {
            return Optional.empty();
        }
        String key = url.substring(base.length());
        boolean expected = key.startsWith(PREFIX) && key.endsWith(SOURCE) && !key.contains("..")
                && !key.contains("?") && !key.contains("#") && key.chars().filter(c -> c == '/').count() == 3;
        return expected ? Optional.of(key) : Optional.empty();
    }

    static String variantKey(String sourceKey) {
        return sourceKey.substring(0, sourceKey.length() - SOURCE.length()) + VARIANT;
    }

    URI url(String key) {
        return URI.create(base + key);
    }
}
