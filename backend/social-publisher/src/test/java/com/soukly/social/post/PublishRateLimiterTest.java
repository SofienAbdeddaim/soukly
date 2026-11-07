package com.soukly.social.post;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.soukly.social.support.MutableClock;

class PublishRateLimiterTest {

    private final MutableClock clock = new MutableClock();
    private final PublishRateLimiter limiter = new PublishRateLimiter(Duration.ofSeconds(30), 30, clock);

    @Test
    void aPagePostsAtMostOnceEveryThirtySeconds() {
        assertThat(limiter.acquire("page-1", 4).granted()).isTrue();

        clock.advance(Duration.ofSeconds(10));
        PublishRateLimiter.Permit refused = limiter.acquire("page-1", 4);
        assertThat(refused.granted()).isFalse();
        assertThat(refused.pageBusy()).isTrue();
        assertThat(refused.notBefore()).isEqualTo(clock.instant().plusSeconds(20));
        // Another Page is not slowed down
        assertThat(limiter.acquire("page-2", 4).granted()).isTrue();

        clock.advance(Duration.ofSeconds(20));
        assertThat(limiter.acquire("page-1", 4).granted()).isTrue();
    }

    @Test
    void theIntervalCountsFromThePostCreation() {
        assertThat(limiter.acquire("page-1", 11).granted()).isTrue();
        clock.advance(Duration.ofSeconds(25));
        limiter.posted("page-1");

        clock.advance(Duration.ofSeconds(10));
        assertThat(limiter.acquire("page-1", 2).granted()).isFalse();
        clock.advance(Duration.ofSeconds(20));
        assertThat(limiter.acquire("page-1", 2).granted()).isTrue();
    }

    @Test
    void atMostThirtyCallsPerMinuteForEveryPage() {
        for (int page = 0; page < 3; page++) {
            assertThat(limiter.acquire("page-" + page, 10).granted()).isTrue();
        }

        PublishRateLimiter.Permit refused = limiter.acquire("page-3", 1);
        assertThat(refused.granted()).isFalse();
        assertThat(refused.pageBusy()).isFalse();
        assertThat(refused.notBefore()).isEqualTo(clock.instant().plus(Duration.ofMinutes(1)));

        clock.advance(Duration.ofSeconds(59));
        assertThat(limiter.acquire("page-3", 1).granted()).isFalse();
        clock.advance(Duration.ofSeconds(1));
        assertThat(limiter.acquire("page-3", 11).granted()).isTrue();
    }

    @Test
    void aPostWithMoreCallsThanTheLimitWaitsForAnEmptyMinute() {
        PublishRateLimiter small = new PublishRateLimiter(Duration.ofSeconds(30), 5, clock);

        assertThat(small.acquire("page-1", 11).granted()).isTrue();
        assertThat(small.acquire("page-2", 1).granted()).isFalse();
        clock.advance(Duration.ofMinutes(1));
        assertThat(small.acquire("page-2", 1).granted()).isTrue();
    }
}
