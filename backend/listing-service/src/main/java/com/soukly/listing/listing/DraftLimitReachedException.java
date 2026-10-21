package com.soukly.listing.listing;

/** The account already has the maximum number of drafts ({@code 409}). */
public class DraftLimitReachedException extends RuntimeException {

    private final int limit;

    public DraftLimitReachedException(int limit) {
        super("Draft limit reached", null, false, false);
        this.limit = limit;
    }

    public int limit() {
        return limit;
    }
}
