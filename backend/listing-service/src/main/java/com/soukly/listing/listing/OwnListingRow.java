package com.soukly.listing.listing;

import java.util.UUID;

/**
 * A listing of the "My listings" page as read (ANN-05).
 *
 * @param coverId        its READY photo at position 0, null without photo
 * @param contactReveals members who were shown the phone number of the seller on it (CATA-03)
 */
record OwnListingRow(Listing listing, UUID coverId, long contactReveals) {
}
