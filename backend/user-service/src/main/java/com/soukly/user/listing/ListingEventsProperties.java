package com.soukly.user.listing;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Consumer of the listing events (ANN-03), bound from {@code soukly.user.listing-events.*}.
 *
 * @param retries       new attempts of a failed message before it goes to the DLQ
 * @param retryInterval pause between two attempts
 */
@ConfigurationProperties("soukly.user.listing-events")
public record ListingEventsProperties(@DefaultValue("3") int retries, @DefaultValue("2s") Duration retryInterval) {
}
