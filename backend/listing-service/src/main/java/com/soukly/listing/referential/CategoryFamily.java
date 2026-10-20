package com.soukly.listing.referential;

import java.util.List;

/** First level of the category tree, with its active subcategories in display order. */
public record CategoryFamily(String code, Labels label, List<Subcategory> subcategories) {
}
