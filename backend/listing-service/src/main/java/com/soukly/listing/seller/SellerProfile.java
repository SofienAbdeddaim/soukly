package com.soukly.listing.seller;

/**
 * What a listing page shows of its seller. Never the phone number or the email.
 *
 * @param memberSince month of the registration, {@code 2026-03}
 */
public record SellerProfile(String displayName, String memberSince) {
}
