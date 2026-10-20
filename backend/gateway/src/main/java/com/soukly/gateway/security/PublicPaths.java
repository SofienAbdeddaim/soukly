package com.soukly.gateway.security;

import org.springframework.http.HttpMethod;
import org.springframework.security.web.server.util.matcher.OrServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;

/**
 * Explicit whitelist of the requests that need no JWT. Every other request requires a valid token.
 *
 * <p>This is the single place to change the access policy. AUTH-01 fixed the registration paths ({@code register},
 * {@code verify-email}, {@code verify-email/resend}); AUTH-02 fixes the login paths; CAT-01 added the read-only
 * referentials (categories and locations).
 */
public final class PublicPaths {

    public static final ServerWebExchangeMatcher MATCHER = new OrServerWebExchangeMatcher(
            ServerWebExchangeMatchers.pathMatchers(HttpMethod.POST,
                    "/api/users/register",
                    "/api/users/login",
                    "/api/users/refresh",
                    "/api/users/verify-email",
                    "/api/users/verify-email/resend",
                    "/api/users/password/**"),
            ServerWebExchangeMatchers.pathMatchers(HttpMethod.GET,
                    "/api/listings/**",
                    "/api/categories/**",
                    "/api/locations/**",
                    "/api/actuator/info",
                    "/actuator/health"));

    private PublicPaths() {
    }
}
