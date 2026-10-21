package com.soukly.listing.listing;

/**
 * Invalid field of a request, for example {@code attributes.year}. {@code code} is a stable key that the front end
 * translates, never a sentence.
 */
public record FieldError(String field, String code) {
}
