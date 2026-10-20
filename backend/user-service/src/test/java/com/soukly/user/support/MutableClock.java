package com.soukly.user.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * Clock of the tests: starts at the real time and moves only when a test advances it. Truncated to the microsecond,
 * the precision of PostgreSQL, so that an instant read back from the database equals the one written.
 */
public final class MutableClock extends Clock {

    private volatile Instant now = realNow();

    public void reset() {
        now = realNow();
    }

    private static Instant realNow() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public void advance(Duration duration) {
        now = now.plus(duration);
    }

    @Override
    public Instant instant() {
        return now;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException();
    }
}
