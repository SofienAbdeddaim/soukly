package com.soukly.user.session;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Sessions, bound from {@code soukly.user.session.*}.
 *
 * @param refreshTokenTtl validity of a refresh token, renewed by every refresh ({@code REFRESH_TOKEN_TTL})
 * @param cookiePath      {@code Path} of the refresh cookie as the browser sees it, before the gateway removes
 *                        {@code /api} ({@code REFRESH_COOKIE_PATH})
 * @param cookieSecure    {@code Secure} attribute of the refresh cookie ({@code REFRESH_COOKIE_SECURE})
 * @param allowedOrigins  exact origins allowed to call refresh and logout, same list as the CORS of the gateway
 *                        ({@code ALLOWED_ORIGINS})
 * @param purgeRetention  how long expired or revoked refresh tokens are kept before the daily purge
 * @param throttle        delays after repeated login failures
 */
@ConfigurationProperties("soukly.user.session")
public record SessionProperties(@DefaultValue("30d") Duration refreshTokenTtl,
        @DefaultValue("/api/users") String cookiePath, @DefaultValue("true") boolean cookieSecure,
        List<String> allowedOrigins, @DefaultValue("7d") Duration purgeRetention,
        @DefaultValue Throttle throttle) {

    /**
     * @param freeFailures consecutive failures allowed before the first delay
     * @param firstDelay   delay after the last free failure, doubled by every further failure
     * @param maxDelay     cap of the delay; an account is never locked for good
     * @param forgetAfter  a failure counter with no failure for this long is deleted by the daily purge
     */
    public record Throttle(@DefaultValue("5") int freeFailures, @DefaultValue("30s") Duration firstDelay,
            @DefaultValue("15m") Duration maxDelay, @DefaultValue("1d") Duration forgetAfter) {

        /** Delay after {@code failures} consecutive failures, zero while they are free. */
        Duration delayAfter(int failures) {
            if (failures < freeFailures) {
                return Duration.ZERO;
            }
            // 2^20 times the first delay is far beyond any sensible cap: stop doubling there.
            int doublings = Math.min(failures - freeFailures, 20);
            Duration delay = firstDelay.multipliedBy(1L << doublings);
            return delay.compareTo(maxDelay) > 0 ? maxDelay : delay;
        }
    }
}
