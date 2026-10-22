package com.soukly.gateway.security;

import org.springframework.http.HttpMethod;
import org.springframework.security.web.server.util.matcher.AndServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.NegatedServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.OrServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;

/**
 * Explicit whitelist of the requests that need no JWT. Every other request requires a valid token.
 *
 * <p>This is the single place to change the access policy. AUTH-01 fixed the registration paths ({@code register},
 * {@code verify-email}, {@code verify-email/resend}); AUTH-02 the session paths ({@code login}, {@code refresh} and
 * {@code logout}, which rely on the refresh cookie, not on a JWT); CAT-01 added the read-only referentials
 * (categories and locations); ANN-05 keeps {@code GET /api/listings/mine} protected; AUTH-05 added the confirmation of a
 * new address ({@code email/confirm}, opened from an email, maybe on another device), while every
 * {@code /api/users/me/**} path needs a token. The JWKS of user-service is never routed: the gateway reads it
 * internally.
 */
public final class PublicPaths {

    /** Under a public pattern, yet protected: the listings of the caller (ANN-05) look like the reading of a listing. */
    private static final ServerWebExchangeMatcher PROTECTED = ServerWebExchangeMatchers.pathMatchers(HttpMethod.GET,
            "/api/listings/mine");

    public static final ServerWebExchangeMatcher MATCHER = new AndServerWebExchangeMatcher(
            new NegatedServerWebExchangeMatcher(PROTECTED), new OrServerWebExchangeMatcher(
            ServerWebExchangeMatchers.pathMatchers(HttpMethod.POST,
                    "/api/users/register",
                    "/api/users/login",
                    "/api/users/refresh",
                    "/api/users/logout",
                    "/api/users/verify-email",
                    "/api/users/verify-email/resend",
                    "/api/users/password/**",
                    "/api/users/email/confirm"),
            ServerWebExchangeMatchers.pathMatchers(HttpMethod.GET,
                    "/api/listings/**",
                    "/api/categories/**",
                    "/api/locations/**",
                    "/api/actuator/info",
                    "/actuator/health")));

    private PublicPaths() {
    }
}
