package com.soukly.user.session;

/** {@code Origin} header missing or not allowed on an endpoint that reads the refresh cookie (CSRF). */
public class ForbiddenOriginException extends RuntimeException {

    ForbiddenOriginException() {
        super("Origin not allowed", null, false, false);
    }
}
