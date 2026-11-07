package com.soukly.user.event;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Consumers of the listing events (ANN-03) and of the social events (SOC-06), bound from
 * {@code soukly.user.listing-events.*} (the name of ANN-03, kept for the existing configurations).
 *
 * @param retries       new attempts of a failed message before it goes to the DLQ
 * @param retryInterval pause between two attempts
 */
@ConfigurationProperties("soukly.user.listing-events")
public record EventsProperties(@DefaultValue("3") int retries, @DefaultValue("2s") Duration retryInterval) {
}
