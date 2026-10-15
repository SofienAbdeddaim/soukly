package com.soukly.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

class RateLimiterTest {

    private final AtomicLong now = new AtomicLong();
    private final RateLimiter limiter = new RateLimiter(10, Duration.ofMinutes(1), now::get);

    @Test
    void allowsTheCapacityThenAsksToWait() {
        for (int i = 0; i < 10; i++) {
            assertThat(limiter.tryAcquire("ip")).isZero();
        }

        assertThat(limiter.tryAcquire("ip")).isEqualTo(Duration.ofSeconds(6));
    }

    @Test
    void refillsOverTheWindow() {
        for (int i = 0; i < 10; i++) {
            limiter.tryAcquire("ip");
        }
        now.addAndGet(Duration.ofSeconds(6).toNanos());

        assertThat(limiter.tryAcquire("ip")).isZero();
        assertThat(limiter.tryAcquire("ip")).isPositive();
    }

    @Test
    void keysHaveSeparateBudgets() {
        for (int i = 0; i < 10; i++) {
            limiter.tryAcquire("a");
        }

        assertThat(limiter.tryAcquire("b")).isZero();
    }

    @Test
    void evictsIdleBuckets() {
        limiter.tryAcquire("a");
        limiter.tryAcquire("b");
        now.addAndGet(Duration.ofMinutes(2).toNanos());

        limiter.tryAcquire("c");

        assertThat(limiter.size()).isEqualTo(1);
    }
}
