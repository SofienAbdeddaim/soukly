package com.soukly.user.session;

import java.time.Duration;

/**
 * A login, refresh or logout that cannot open or keep a session. The transaction still commits (failure counters,
 * revoked families). Never carries a token, an email or a password.
 */
public abstract sealed class SessionException extends RuntimeException {

    private SessionException(String message) {
        super(message, null, false, false);
    }

    /** Unknown email or wrong password: one answer for both. */
    public static final class InvalidCredentials extends SessionException {

        InvalidCredentials() {
            super("Invalid credentials");
        }
    }

    /** Right credentials, account still {@code PENDING_VERIFICATION}. */
    public static final class EmailNotVerified extends SessionException {

        EmailNotVerified() {
            super("Email not verified");
        }
    }

    /** Too many consecutive failures on this email: the attempt was not checked. */
    public static final class LoginThrottled extends SessionException {

        private final Duration retryAfter;

        LoginThrottled(Duration retryAfter) {
            super("Login throttled");
            this.retryAfter = retryAfter;
        }

        public Duration retryAfter() {
            return retryAfter;
        }
    }

    /** Refresh cookie missing, unknown or expired. */
    public static final class SessionExpired extends SessionException {

        SessionExpired() {
            super("Session expired");
        }
    }

    /** Refresh token of a revoked family: logged out, or a replaced token presented again (theft). */
    public static final class SessionRevoked extends SessionException {

        SessionRevoked() {
            super("Session revoked");
        }
    }
}
