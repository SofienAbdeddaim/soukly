package com.soukly.gateway.ratelimit;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * In-memory token bucket per key: {@code capacity} requests, refilled continuously over one window.
 * Idle buckets are evicted once per window, so memory follows the number of active clients.
 * Single instance only (no Redis in V1).
 */
class RateLimiter {

    private final int capacity;
    private final long windowNanos;
    private final LongSupplier nanoClock;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private volatile long lastEviction;

    RateLimiter(int capacity, Duration window, LongSupplier nanoClock) {
        if (capacity < 1) {
            throw new IllegalArgumentException("Rate limit capacity must be at least 1");
        }
        this.capacity = capacity;
        this.windowNanos = window.toNanos();
        this.nanoClock = nanoClock;
        this.lastEviction = nanoClock.getAsLong();
    }

    /**
     * Consumes one request for {@code key}.
     *
     * @return {@link Duration#ZERO} when allowed, otherwise how long to wait before the next allowed request
     */
    Duration tryAcquire(String key) {
        long now = nanoClock.getAsLong();
        evictIdleBuckets(now);
        return buckets.computeIfAbsent(key, k -> new Bucket(capacity, now)).tryAcquire(now);
    }

    int size() {
        return buckets.size();
    }

    private void evictIdleBuckets(long now) {
        if (now - lastEviction < windowNanos) {
            return;
        }
        lastEviction = now;
        buckets.values().removeIf(bucket -> bucket.isIdleSince(now, windowNanos));
    }

    private final class Bucket {

        private double tokens;
        private long updatedAt;

        Bucket(double tokens, long now) {
            this.tokens = tokens;
            this.updatedAt = now;
        }

        synchronized Duration tryAcquire(long now) {
            refill(now);
            if (tokens >= 1) {
                tokens -= 1;
                return Duration.ZERO;
            }
            double nanosPerToken = (double) windowNanos / capacity;
            return Duration.ofNanos((long) Math.ceil((1 - tokens) * nanosPerToken));
        }

        synchronized boolean isIdleSince(long now, long idleNanos) {
            return now - updatedAt >= idleNanos;
        }

        private void refill(long now) {
            tokens = Math.min(capacity, tokens + (double) (now - updatedAt) * capacity / windowNanos);
            updatedAt = now;
        }
    }
}
