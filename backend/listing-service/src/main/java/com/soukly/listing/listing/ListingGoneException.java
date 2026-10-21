package com.soukly.listing.listing;

/**
 * A listing that was online and is not any more ({@code 410}): expired, deactivated or deleted (ANN-05), or its
 * seller's account is no longer active. Only its subcategory and governorate are given, to suggest similar listings.
 */
public class ListingGoneException extends RuntimeException {

    private final String categoryCode;
    private final String governorateCode;

    public ListingGoneException(Listing listing) {
        super("Listing gone", null, false, false);
        this.categoryCode = listing.content().categoryCode();
        this.governorateCode = listing.content().governorateCode();
    }

    public String categoryCode() {
        return categoryCode;
    }

    public String governorateCode() {
        return governorateCode;
    }
}
