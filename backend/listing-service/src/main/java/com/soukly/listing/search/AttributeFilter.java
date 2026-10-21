package com.soukly.listing.search;

import java.util.List;

/** A filter on a filterable specific attribute of the subcategory ({@code attr.<code>...}). */
sealed interface AttributeFilter {

    String code();

    /** INTEGER: {@code attr.<code>.min} and {@code attr.<code>.max}, inclusive, either may be null. */
    record Range(String code, Long min, Long max) implements AttributeFilter {
    }

    /** ENUM: one of the values ({@code attr.fuel=diesel,hybrid}). */
    record OneOf(String code, List<String> values) implements AttributeFilter {
    }

    /** BOOLEAN: {@code attr.furnished=true}. */
    record Is(String code, boolean value) implements AttributeFilter {
    }

    /** TEXT: the same text, whatever the case and the accents ({@code attr.brand=peugeot}). */
    record SameText(String code, String value) implements AttributeFilter {
    }
}
