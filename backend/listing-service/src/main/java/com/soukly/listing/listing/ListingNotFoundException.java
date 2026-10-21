package com.soukly.listing.listing;

/** Unknown listing, or a draft of another account: its existence is never revealed ({@code 404}, never 403). */
public class ListingNotFoundException extends RuntimeException {

    public ListingNotFoundException() {
        super("Listing not found", null, false, false);
    }
}
