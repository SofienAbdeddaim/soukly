package com.soukly.listing.listing;

public enum PriceMode {
    /** A price in whole dinars. */
    FIXED,
    /** "Sur devis": no price, only for the services and jobs families ({@link ListingKind#SERVICE}). */
    ON_QUOTE
}
