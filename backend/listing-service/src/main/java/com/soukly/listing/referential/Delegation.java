package com.soukly.listing.referential;

/** A delegation of a governorate; {@code code} is {@code <governorate>.<delegation>}. */
public record Delegation(String code, String governorateCode, Labels label) {
}
