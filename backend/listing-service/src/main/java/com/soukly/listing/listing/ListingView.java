package com.soukly.listing.listing;

import java.util.List;

import com.soukly.listing.photo.PhotoView;

/**
 * A listing as answered to its owner.
 *
 * @param photos        READY photos, cover first (ANN-02)
 * @param missingFields required fields still empty, in form order; empty when the draft can be published
 * @param warnings      automatic changes made by the last request ({@code attributes-removed}, {@code price-mode-reset})
 */
public record ListingView(Listing listing, List<PhotoView> photos, List<String> missingFields,
        List<String> warnings) {
}
