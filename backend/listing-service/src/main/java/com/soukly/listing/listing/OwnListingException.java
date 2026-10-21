package com.soukly.listing.listing;

/** The seller asked for the phone number of their own listing ({@code 409}); the page never offers it. */
public class OwnListingException extends RuntimeException {

    public OwnListingException() {
        super("Own listing", null, false, false);
    }
}
