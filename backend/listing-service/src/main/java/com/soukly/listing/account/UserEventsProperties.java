package com.soukly.listing.account;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Consumer of the user events (AUTH-05), bound from {@code soukly.listing.user-events.*}.
 *
 * @param retries       new attempts of a failed message before it goes to the DLQ
 * @param retryInterval pause between two attempts
 */
@ConfigurationProperties("soukly.listing.user-events")
public record UserEventsProperties(@DefaultValue("3") int retries, @DefaultValue("2s") Duration retryInterval) {
}
