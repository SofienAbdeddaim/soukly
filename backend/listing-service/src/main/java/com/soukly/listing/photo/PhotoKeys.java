package com.soukly.listing.photo;

import java.util.List;
import java.util.UUID;

/**
 * Object keys of a photo. Only {@code listings/} is publicly readable; {@code incoming/} never is. Both ids are
 * random UUIDs, so a key cannot be guessed.
 */
final class PhotoKeys {

    static final String INCOMING_PREFIX = "incoming/";
    static final String PUBLIC_PREFIX = "listings/";

    private PhotoKeys() {
    }

    /** The original, as uploaded by the browser. */
    static String incoming(UUID listingId, UUID photoId) {
        return INCOMING_PREFIX + listingId + "/" + photoId;
    }

    static String large(UUID listingId, UUID photoId) {
        return PUBLIC_PREFIX + listingId + "/" + photoId + "/1600.webp";
    }

    static String thumbnail(UUID listingId, UUID photoId) {
        return PUBLIC_PREFIX + listingId + "/" + photoId + "/400.webp";
    }

    /** Every object a photo may have, whatever its status. */
    static List<String> all(UUID listingId, UUID photoId) {
        return List.of(incoming(listingId, photoId), large(listingId, photoId), thumbnail(listingId, photoId));
    }
}
