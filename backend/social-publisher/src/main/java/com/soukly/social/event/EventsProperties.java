package com.soukly.social.event;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Consumers of the user and listing events, bound from {@code soukly.social.events.*}.
 *
 * @param retries       new attempts of a failed message before it goes to the DLQ
 * @param retryInterval pause between two attempts
 */
@ConfigurationProperties("soukly.social.events")
public record EventsProperties(@DefaultValue("3") int retries, @DefaultValue("2s") Duration retryInterval) {
}
