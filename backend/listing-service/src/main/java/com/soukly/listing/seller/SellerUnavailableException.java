package com.soukly.listing.seller;

/** user-service did not answer, or answered an error: the request can be tried again later ({@code 503}). */
public class SellerUnavailableException extends RuntimeException {

    SellerUnavailableException(Throwable cause) {
        super("user-service is unavailable", cause);
    }
}
