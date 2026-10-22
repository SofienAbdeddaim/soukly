package com.soukly.social.account;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Consumer of the user events (AUTH-05), bound from {@code soukly.social.user-events.*}.
 *
 * @param retries       new attempts of a failed message before it goes to the DLQ
 * @param retryInterval pause between two attempts
 */
@ConfigurationProperties("soukly.social.user-events")
public record UserEventsProperties(@DefaultValue("3") int retries, @DefaultValue("2s") Duration retryInterval) {
}
