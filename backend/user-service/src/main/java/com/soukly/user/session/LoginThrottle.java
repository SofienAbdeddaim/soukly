package com.soukly.user.session;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Delays the login attempts after repeated failures on the same email: after 5 consecutive failures each attempt
 * waits 30 s, then 1 min, 2 min, 4 min... up to 15 min. A successful login resets the counter, and an account is
 * never locked for good. The counters are keyed by the SHA-256 of the normalized email, whether an account exists
 * or not, so that a delay never reveals an account.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
class LoginThrottle {

    private final JdbcClient jdbc;
    private final SessionProperties.Throttle settings;

    LoginThrottle(JdbcClient jdbc, SessionProperties properties) {
        this.jdbc = jdbc;
        this.settings = properties.throttle();
    }

    /** How long an attempt must still wait, empty when it may be checked now. */
    Optional<Duration> waitBefore(String emailHash, Instant now) {
        return jdbc.sql("SELECT retry_after FROM login_throttle WHERE email_hash = :hash")
                .param("hash", emailHash)
                .query((rs, row) -> Optional.ofNullable(rs.getObject("retry_after", OffsetDateTime.class)))
                .optional()
                .flatMap(retryAfter -> retryAfter)
                .map(OffsetDateTime::toInstant)
                .filter(now::isBefore)
                .map(retryAfter -> Duration.between(now, retryAfter));
    }

    void recordFailure(String emailHash, Instant now) {
        int failures = jdbc.sql("""
                INSERT INTO login_throttle (email_hash, failed_count, last_failed_at)
                VALUES (:hash, 1, :now)
                ON CONFLICT (email_hash) DO UPDATE
                    SET failed_count = login_throttle.failed_count + 1, last_failed_at = EXCLUDED.last_failed_at
                RETURNING failed_count""")
                .param("hash", emailHash)
                .param("now", utc(now))
                .query(Integer.class)
                .single();
        Duration delay = settings.delayAfter(failures);
        if (!delay.isZero()) {
            jdbc.sql("UPDATE login_throttle SET retry_after = :retryAfter WHERE email_hash = :hash")
                    .param("retryAfter", utc(now.plus(delay)))
                    .param("hash", emailHash)
                    .update();
        }
    }

    void reset(String emailHash) {
        jdbc.sql("DELETE FROM login_throttle WHERE email_hash = :hash").param("hash", emailHash).update();
    }

    /** Deletes the counters with no failure since {@code before}. */
    int deleteIdleSince(Instant before) {
        return jdbc.sql("DELETE FROM login_throttle WHERE last_failed_at < :before")
                .param("before", utc(before))
                .update();
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
