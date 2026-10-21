package com.soukly.listing.listing;

/** A change was sent without {@code If-Match} ({@code 428}). */
public class IfMatchRequiredException extends RuntimeException {

    public IfMatchRequiredException() {
        super("If-Match required", null, false, false);
    }
}
