package com.soukly.common.event;

import java.time.Instant;

/**
 * {@code data} of {@code listing.renewed}, {@code listing.expiring-soon} and {@code listing.expired}.
 *
 * @param expiresAt new, coming or reached expiry date, depending on the type
 */
public record ListingExpiryData(String title, Instant expiresAt) {
}
