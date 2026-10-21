package com.soukly.listing.listing;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Position after the last listing of a page of "My listings" (ANN-05): its status, update date and id, as an opaque
 * {@code base64url} token. It is not signed, unlike the search cursors (CATA-01): it only ever selects among the
 * listings of the account that sends it, and its values are bound as SQL parameters.
 */
record OwnListingsCursor(ListingStatus status, Instant updatedAt, UUID id) {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    /** Far above any real cursor; refused before decoding. */
    private static final int MAX_LENGTH = 128;

    static final String FIELD = "cursor";

    static OwnListingsCursor after(Listing listing) {
        return new OwnListingsCursor(listing.status(), listing.updatedAt(), listing.id());
    }

    String encode() {
        // Microseconds since the epoch: exactly the precision of timestamptz
        String body = status.name() + "|" + ChronoUnit.MICROS.between(Instant.EPOCH, updatedAt) + "|" + id;
        return ENCODER.encodeToString(body.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @param status the status of the requested page: a cursor of another tab is refused
     * @throws InvalidListingException {@code cursor.invalid}
     */
    static OwnListingsCursor decode(String token, ListingStatus status) {
        try {
            if (token.length() > MAX_LENGTH) {
                throw invalid();
            }
            String[] parts = new String(DECODER.decode(token), StandardCharsets.UTF_8).split("\\|", -1);
            if (parts.length != 3 || !status.name().equals(parts[0])) {
                throw invalid();
            }
            Instant updatedAt = Instant.EPOCH.plus(Long.parseLong(parts[1]), ChronoUnit.MICROS);
            return new OwnListingsCursor(status, updatedAt, UUID.fromString(parts[2]));
        } catch (IllegalArgumentException | ArithmeticException e) {
            throw invalid();
        }
    }

    private static InvalidListingException invalid() {
        return new InvalidListingException(List.of(new FieldError(FIELD, "cursor.invalid")));
    }
}
