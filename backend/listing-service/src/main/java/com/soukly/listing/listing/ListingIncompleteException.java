package com.soukly.listing.listing;

import java.util.List;

/** A draft cannot be published while required fields are missing ({@code 422}). */
public class ListingIncompleteException extends RuntimeException {

    private final List<String> missingFields;

    public ListingIncompleteException(List<String> missingFields) {
        super("Listing incomplete", null, false, false);
        this.missingFields = List.copyOf(missingFields);
    }

    /** Same members and order as {@code missingFields} of the listing. */
    public List<String> missingFields() {
        return missingFields;
    }
}
