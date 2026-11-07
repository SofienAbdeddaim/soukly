package com.soukly.social.post;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Pace of the publications (SOC-04), in memory: at most one post per Page every {@code pageInterval}, and at most
 * {@code callsPerMinute} publishing calls over any minute, every Page together. One instance of social-publisher in
 * V1; a limiter shared between instances is V2.
 */
final class PublishRateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final Duration pageInterval;
    private final int callsPerMinute;
    private final Clock clock;
    private final Map<String, Instant> lastPostOfPage = new HashMap<>();
    private final Deque<Instant> calls = new ArrayDeque<>();

    PublishRateLimiter(Duration pageInterval, int callsPerMinute, Clock clock) {
        this.pageInterval = pageInterval;
        this.callsPerMinute = callsPerMinute;
        this.clock = clock;
    }

    /**
     * The answer of {@link #acquire}.
     *
     * @param granted   the post may be published now
     * @param pageBusy  refused because of the Page; otherwise because of the calls of the minute
     * @param notBefore when to ask again
     */
    record Permit(boolean granted, boolean pageBusy, Instant notBefore) {
    }

    /**
     * Asks to publish a post on this Page with {@code calls} publishing calls (its photos and the post). Granted: the
     * Page and the calls are counted from now.
     */
    synchronized Permit acquire(String pageId, int calls) {
        Instant now = clock.instant();
        Instant last = lastPostOfPage.get(pageId);
        if (last != null && now.isBefore(last.plus(pageInterval))) {
            return new Permit(false, true, last.plus(pageInterval));
        }
        forgetBefore(now.minus(WINDOW));
        // A post with more calls than the limit (never with the defaults) waits for an empty minute
        int needed = Math.min(calls, callsPerMinute);
        if (this.calls.size() + needed > callsPerMinute) {
            Instant freed = this.calls.stream().skip(this.calls.size() + needed - callsPerMinute - 1L).findFirst()
                    .orElse(now);
            return new Permit(false, false, freed.plus(WINDOW));
        }
        lastPostOfPage.put(pageId, now);
        for (int i = 0; i < calls; i++) {
            this.calls.addLast(now);
        }
        return new Permit(true, false, now);
    }

    /** The post of this Page was created now: the next one waits {@code pageInterval} from now. */
    synchronized void posted(String pageId) {
        lastPostOfPage.put(pageId, clock.instant());
    }

    private void forgetBefore(Instant start) {
        while (!calls.isEmpty() && !calls.peekFirst().isAfter(start)) {
            calls.removeFirst();
        }
        lastPostOfPage.values().removeIf(last -> last.plus(pageInterval).isBefore(clock.instant()));
    }
}
