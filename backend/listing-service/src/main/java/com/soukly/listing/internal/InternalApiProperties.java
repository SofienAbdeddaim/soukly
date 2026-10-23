package com.soukly.listing.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Service-to-service calls received by listing-service (SOC-06), bound from {@code soukly.listing.internal-api.*}.
 *
 * @param token shared secret that callers send in {@code X-Internal-Token} ({@code INTERNAL_API_TOKEN}); when empty,
 *              every internal request is refused
 */
@ConfigurationProperties("soukly.listing.internal-api")
public record InternalApiProperties(String token) {

    public boolean configured() {
        return token != null && !token.isBlank();
    }
}
