package com.soukly.gateway.security;

import org.springframework.http.HttpMethod;
import org.springframework.security.web.server.util.matcher.OrServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;

/**
 * Explicit whitelist of the requests that need no JWT. Every other request requires a valid token.
 *
 * <p>This is the single place to change the access policy. Exact AUTH paths are fixed by AUTH-01 and AUTH-02;
 * CAT-01 adds the public referential paths.
 */
public final class PublicPaths {

    public static final ServerWebExchangeMatcher MATCHER = new OrServerWebExchangeMatcher(
            ServerWebExchangeMatchers.pathMatchers(HttpMethod.POST,
                    "/api/users/register",
                    "/api/users/login",
                    "/api/users/refresh",
                    "/api/users/verify-email",
                    "/api/users/password/**"),
            ServerWebExchangeMatchers.pathMatchers(HttpMethod.GET,
                    "/api/listings/**",
                    "/api/actuator/info",
                    "/actuator/health"));

    private PublicPaths() {
    }
}
