package com.soukly.listing.contact;

import java.time.Duration;

/** The member saw the numbers of too many listings within the window ({@code 429}). */
public class ContactLimitReachedException extends RuntimeException {

    private final Duration retryAfter;

    ContactLimitReachedException(Duration retryAfter) {
        super("Contact limit reached", null, false, false);
        this.retryAfter = retryAfter;
    }

    /** Until a listing leaves the window, at least one second. */
    public Duration retryAfter() {
        return retryAfter;
    }
}
