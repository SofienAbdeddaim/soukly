package com.soukly.social.post;

/** listing-service did not say whether a listing is online (SOC-06): {@code 503}, the member tries again later. */
public class ListingStatusUnavailableException extends RuntimeException {

    ListingStatusUnavailableException(Throwable cause) {
        super("listing-service did not answer", cause);
    }
}
