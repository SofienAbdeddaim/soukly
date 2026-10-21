package com.soukly.listing.listing;

/**
 * Only an expired or deactivated listing, or a published one that expires within the reminder period, can be put
 * back online ({@code 409}).
 */
public class RenewNotAllowedException extends RuntimeException {

    public RenewNotAllowedException() {
        super("Renewal not allowed", null, false, false);
    }
}
