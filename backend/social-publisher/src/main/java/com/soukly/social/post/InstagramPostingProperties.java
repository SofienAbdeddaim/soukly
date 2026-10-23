package com.soukly.social.post;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Publication of the listings on the Instagram professional accounts (SOC-05), bound from
 * {@code soukly.social.posts.instagram.*}.
 *
 * @param retryDelays        delays of the new attempts after a temporary failure, in order; once used up, the post is
 *                           {@code FAILED}
 * @param maxPhotos          photos of a carousel, the first ones in the seller's order (10 at most at Instagram)
 * @param statusChecks       readings of the status of a container before giving up for this run
 * @param statusInterval     pause before each reading but the first: 5 readings over less than one minute
 * @param quotaRetryDelay    the account reached the posts allowed through the API within 24 hours: the post waits this
 *                           delay, without counting an attempt, before the limit is read again
 */
@ConfigurationProperties("soukly.social.posts.instagram")
public record InstagramPostingProperties(
        @DefaultValue({ "1m", "5m", "30m", "2h", "6h" }) List<Duration> retryDelays,
        @DefaultValue("10") int maxPhotos, @DefaultValue("5") int statusChecks,
        @DefaultValue("12s") Duration statusInterval, @DefaultValue("1h") Duration quotaRetryDelay) {
}
