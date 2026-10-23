package com.soukly.social.post;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * What the seller can do about the posts (SOC-06), bound from {@code soukly.social.posts.follow-up.*}; assumptions of
 * V1.
 *
 * @param maxManualRetries "Try again" per post
 * @param relaunchWindow   posts blocked by a connection problem are offered again after a reconnection when they are
 *                         younger than this
 */
@ConfigurationProperties("soukly.social.posts.follow-up")
public record PostFollowUpProperties(@DefaultValue("3") int maxManualRetries,
        @DefaultValue("30d") Duration relaunchWindow) {
}
