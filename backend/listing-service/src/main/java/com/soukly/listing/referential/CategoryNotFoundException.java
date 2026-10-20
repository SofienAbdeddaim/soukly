package com.soukly.listing.referential;

/** Unknown or inactive category, or a family where a subcategory is expected. */
public class CategoryNotFoundException extends RuntimeException {

    public CategoryNotFoundException(String code) {
        super("No active subcategory with code " + code);
    }
}
