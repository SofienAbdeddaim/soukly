package com.soukly.listing.referential;

/** A subcategory, the only level a listing belongs to; {@code code} is {@code <family>.<subcategory>}. */
public record Subcategory(String code, String familyCode, Labels label) {
}
