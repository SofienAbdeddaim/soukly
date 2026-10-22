package com.soukly.listing.listing;

/**
 * {@code shareTo} and {@code shareLanguage} only change while the listing is a draft: it is shared when it is
 * published ({@code 409 share-locked}, SOC-03).
 */
public class ShareLockedException extends RuntimeException {

    public ShareLockedException() {
        super("Share locked", null, false, false);
    }
}
