package com.soukly.listing.listing;

/** The listing is not a draft any more: it was published already ({@code 409}). */
public class AlreadyPublishedException extends RuntimeException {

    public AlreadyPublishedException() {
        super("Listing already published", null, false, false);
    }
}
