package com.soukly.social.post;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * When to try a post again after a temporary failure (SOC-04): after 1 min, 5 min, 30 min, 2 h then 6 h by default,
 * then never.
 */
final class RetrySchedule {

    private final List<Duration> delays;

    RetrySchedule(List<Duration> delays) {
        this.delays = List.copyOf(delays);
    }

    /**
     * @param failures temporary failures so far, this one included (1 for the first)
     * @return the delay before the next attempt; empty when every new attempt was used: the post is {@code FAILED}
     */
    Optional<Duration> delayAfter(int failures) {
        if (failures < 1 || failures > delays.size()) {
            return Optional.empty();
        }
        return Optional.of(delays.get(failures - 1));
    }
}
