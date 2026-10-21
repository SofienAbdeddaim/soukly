package com.soukly.listing.listing;

/** {@code If-Match} names another version: the listing changed in the meantime ({@code 412}). */
public class VersionConflictException extends RuntimeException {

    public VersionConflictException() {
        super("Version conflict", null, false, false);
    }
}
