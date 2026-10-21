package com.soukly.listing.listing;

/** The category of a listing cannot change once it was published ({@code 409}). */
public class CategoryLockedException extends RuntimeException {

    public CategoryLockedException() {
        super("Category locked", null, false, false);
    }
}
