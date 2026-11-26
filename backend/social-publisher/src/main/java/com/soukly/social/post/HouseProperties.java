package com.soukly.social.post;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Soukly's own Page and Instagram account (SOC-07), bound from {@code soukly.social.house.*}. They are the
 * connections of a Soukly account, made in "My networks" like any member's: their tokens are encrypted, checked and
 * marked {@code NEEDS_RECONNECT} the same way, and that account receives the email "connect again" (SOC-06).
 *
 * @param userId         the Soukly account that connected Soukly's Page and Instagram; null turns the house posts off
 * @param pageInterval   at least this delay between two posts on Soukly's Page
 * @param callsPerMinute publishing calls (photo uploads and posts) per minute on Soukly's Page
 * @param maxAge         a post still {@code PENDING} after this delay is {@code SKIPPED} ({@code stale})
 */
@ConfigurationProperties("soukly.social.house")
public record HouseProperties(UUID userId, @DefaultValue("2m") Duration pageInterval,
        @DefaultValue("10") int callsPerMinute, @DefaultValue("48h") Duration maxAge) {

    public Optional<UUID> account() {
        return Optional.ofNullable(userId);
    }

    public boolean enabled() {
        return userId != null;
    }
}
