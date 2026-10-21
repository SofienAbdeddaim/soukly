package com.soukly.user.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Service-to-service calls (CATA-03), bound from {@code soukly.user.internal-api.*}.
 *
 * @param token shared secret that callers send in {@code X-Internal-Token} ({@code INTERNAL_API_TOKEN}); when empty,
 *              every internal request is refused
 */
@ConfigurationProperties("soukly.user.internal-api")
public record InternalApiProperties(String token) {

    public boolean configured() {
        return token != null && !token.isBlank();
    }
}
