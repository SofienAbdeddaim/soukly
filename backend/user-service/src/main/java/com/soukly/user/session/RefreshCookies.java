package com.soukly.user.session;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * The refresh cookie {@code soukly_rt}: {@code HttpOnly}, {@code Secure}, {@code SameSite=Strict}, limited to the
 * session endpoints. JavaScript never reads it and no JSON body ever contains its value.
 */
@Component
public class RefreshCookies {

    public static final String NAME = "soukly_rt";

    private final SessionProperties properties;

    RefreshCookies(SessionProperties properties) {
        this.properties = properties;
    }

    ResponseCookie create(String refreshToken) {
        return builder(refreshToken).maxAge(properties.refreshTokenTtl()).build();
    }

    /** {@code Set-Cookie} value that makes the browser delete the cookie. */
    public String clear() {
        return builder("").maxAge(0).build().toString();
    }

    private ResponseCookie.ResponseCookieBuilder builder(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Strict")
                .path(properties.cookiePath());
    }
}
