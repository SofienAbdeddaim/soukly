package com.soukly.listing.contact;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Limit of the phone numbers shown to a member (CATA-03), bound from {@code soukly.listing.contact.*}.
 *
 * @param maxListings distinct listings whose number a member may see per {@code window}; seeing a number again does
 *                    not count
 * @param window      sliding window of the limit
 */
@ConfigurationProperties("soukly.listing.contact")
public record ContactProperties(@DefaultValue("30") int maxListings, @DefaultValue("24h") Duration window) {
}
