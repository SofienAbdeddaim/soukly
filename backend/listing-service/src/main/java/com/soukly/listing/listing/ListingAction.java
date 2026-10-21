package com.soukly.listing.listing;

/** What a seller does to one of their listings; {@link ListingTransitions} says from which statuses. */
public enum ListingAction {
    EDIT,
    PUBLISH,
    DEACTIVATE,
    RENEW,
    DELETE
}
