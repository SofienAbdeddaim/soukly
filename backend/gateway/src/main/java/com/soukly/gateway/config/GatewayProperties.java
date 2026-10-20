package com.soukly.gateway.config;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the gateway, bound from {@code soukly.gateway.*} in {@code application.yml}
 * (each value comes from an environment variable with a local default).
 *
 * @param jwt            validation of the access tokens issued by user-service
 * @param cors           browser origins allowed to call the API
 * @param rateLimit      in-memory rate limit of the authentication endpoints
 * @param trustedProxies IP addresses or CIDR ranges of the reverse proxy whose {@code X-Forwarded-For} is trusted
 */
@ConfigurationProperties("soukly.gateway")
public record GatewayProperties(Jwt jwt, Cors cors, RateLimit rateLimit,
        @DefaultValue List<String> trustedProxies) {

    /**
     * @param jwkSetUri JWKS URL of user-service
     * @param issuer    expected {@code iss} claim
     * @param clockSkew tolerance applied to {@code exp} and {@code nbf}
     */
    public record Jwt(URI jwkSetUri, String issuer, @DefaultValue("30s") Duration clockSkew) {
    }

    /**
     * @param allowedOrigins exact origins, never {@code *} (credentials are allowed)
     * @param maxAge         how long browsers cache a preflight response
     */
    public record Cors(List<String> allowedOrigins, @DefaultValue("1h") Duration maxAge) {
    }

    /**
     * @param authPerMinute requests per minute and per client IP on login, register and verification resend
     */
    public record RateLimit(@DefaultValue("10") int authPerMinute) {
    }
}
