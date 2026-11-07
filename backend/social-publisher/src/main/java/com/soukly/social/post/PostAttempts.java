package com.soukly.social.post;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.soukly.social.connection.Network;

/**
 * What the publication of a network does with a post that was not published (SOC-04, SOC-05): skipped, tried again
 * after the {@code retry-delays} ({@link RetrySchedule}), postponed without counting an attempt, or failed for good.
 * The same for every network; one instance per publisher, with its delays.
 */
final class PostAttempts {

    private static final Logger log = LoggerFactory.getLogger(PostAttempts.class);

    /** What a post became: the publishers count their runs with it. */
    enum Outcome {
        PUBLISHED, RETRIED, ENDED, POSTPONED, THROTTLED
    }

    private final Network network;
    private final SocialPostRepository posts;
    private final PostMetrics metrics;
    private final RetrySchedule retries;
    private final Clock clock;

    PostAttempts(Network network, SocialPostRepository posts, PostMetrics metrics, List<Duration> retryDelays,
            Clock clock) {
        this.network = network;
        this.posts = posts;
        this.metrics = metrics;
        this.retries = new RetrySchedule(retryDelays);
        this.clock = clock;
    }

    /** Disconnected, or the network refused the token, since the listing was published. */
    Outcome skipped(SocialPost post, String reason) {
        posts.markEnded(post.id(), PostStatus.SKIPPED, reason, post.attempts());
        metrics.entered(network, PostStatus.SKIPPED);
        log.info("{} post of listing {} skipped: {}", network.displayName(), post.listingId(), reason);
        return Outcome.ENDED;
    }

    /** A temporary failure: tried again later, or {@code FAILED} once every new attempt was used. */
    Outcome temporaryFailure(SocialPost post, String reason, Duration elapsed) {
        int attempts = post.attempts() + 1;
        Optional<Duration> delay = retries.delayAfter(attempts);
        if (delay.isEmpty()) {
            return failed(post, reason, attempts, elapsed);
        }
        posts.retryAt(post.id(), attempts, reason, clock.instant().plus(delay.get()));
        metrics.published(network, "retry", elapsed);
        log.info("{} post of listing {} tried again in {} (attempt {}): {}", network.displayName(), post.listingId(),
                delay.get(), attempts, reason);
        return Outcome.RETRIED;
    }

    /** Waits until {@code notBefore}, without counting an attempt (pace, or a limit of the network). */
    Outcome postponed(SocialPost post, Instant notBefore, String reason) {
        posts.postpone(post.id(), notBefore);
        log.info("{} post of listing {} postponed until {}: {}", network.displayName(), post.listingId(), notBefore,
                reason);
        return Outcome.POSTPONED;
    }

    Outcome failed(SocialPost post, String reason, Duration elapsed) {
        return failed(post, reason, post.attempts() + 1, elapsed);
    }

    private Outcome failed(SocialPost post, String reason, int attempts, Duration elapsed) {
        posts.markEnded(post.id(), PostStatus.FAILED, reason, attempts);
        metrics.entered(network, PostStatus.FAILED);
        metrics.published(network, "failed", elapsed);
        log.warn("{} post of listing {} failed: {}", network.displayName(), post.listingId(), reason);
        return Outcome.ENDED;
    }
}
