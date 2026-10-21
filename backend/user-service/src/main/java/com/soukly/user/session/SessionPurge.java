package com.soukly.user.session;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Daily purge: refresh tokens expired or revoked for more than 7 days, and login failure counters idle for a day.
 */
@Component
class SessionPurge {

    private static final Logger log = LoggerFactory.getLogger(SessionPurge.class);

    private final RefreshTokenRepository refreshTokens;
    private final LoginThrottle throttle;
    private final SessionProperties properties;
    private final Clock clock;

    SessionPurge(RefreshTokenRepository refreshTokens, LoginThrottle throttle, SessionProperties properties,
            Clock clock) {
        this.refreshTokens = refreshTokens;
        this.throttle = throttle;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(cron = "${soukly.user.session.purge-cron:0 30 3 * * *}", zone = "UTC")
    @Transactional
    public void purge() {
        Instant now = clock.instant();
        int tokens = refreshTokens.deleteExpiredOrRevokedBefore(now.minus(properties.purgeRetention()));
        int counters = throttle.deleteIdleSince(now.minus(properties.throttle().forgetAfter()));
        log.info("Session purge: {} refresh token(s) and {} login failure counter(s) deleted", tokens, counters);
    }
}
