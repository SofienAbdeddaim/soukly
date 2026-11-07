package com.soukly.social.post;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Publication of the listings on the Facebook Pages (SOC-04), bound from {@code soukly.social.posts.facebook.*}.
 *
 * @param pageInterval        at least this delay between two posts of the same Page
 * @param callsPerMinute      publishing calls (photo uploads and posts) per minute, every Page together
 * @param retryDelays         delays of the new attempts after a temporary failure, in order; once used up, the post
 *                            is {@code FAILED}
 * @param maxPhotos           photos attached to a post, the first ones in the seller's order
 * @param deleteAttempts      attempts to remove a post when its listing or account is deleted
 * @param deleteRetryInterval pause between two of them
 */
@ConfigurationProperties("soukly.social.posts.facebook")
public record FacebookPostingProperties(@DefaultValue("30s") Duration pageInterval,
        @DefaultValue("30") int callsPerMinute,
        @DefaultValue({ "1m", "5m", "30m", "2h", "6h" }) List<Duration> retryDelays,
        @DefaultValue("10") int maxPhotos, @DefaultValue("3") int deleteAttempts,
        @DefaultValue("5s") Duration deleteRetryInterval) {
}
