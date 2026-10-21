package com.soukly.listing.listing;

/**
 * Only an expired listing, or a published one that expires within the reminder period, can be renewed
 * ({@code 409}).
 */
public class RenewNotAllowedException extends RuntimeException {

    public RenewNotAllowedException() {
        super("Renewal not allowed", null, false, false);
    }
}
