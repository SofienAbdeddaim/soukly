package com.soukly.listing.photo;

/** The listing already has the maximum number of photos, uploads in progress included ({@code 409}). */
public class PhotoLimitReachedException extends RuntimeException {

    private final int limit;

    public PhotoLimitReachedException(int limit) {
        super("Photo limit reached", null, false, false);
        this.limit = limit;
    }

    public int limit() {
        return limit;
    }
}
