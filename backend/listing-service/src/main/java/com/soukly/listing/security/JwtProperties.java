package com.soukly.listing.security;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Validation of the access tokens issued by user-service, bound from {@code soukly.listing.jwt.*}: the same
 * variables and rules as the gateway.
 *
 * @param jwkSetUri JWKS of user-service, internal address ({@code USER_SERVICE_JWKS_URI})
 * @param issuer    expected {@code iss} claim ({@code JWT_ISSUER})
 * @param clockSkew tolerance on {@code exp} and {@code nbf}
 */
@ConfigurationProperties("soukly.listing.jwt")
public record JwtProperties(URI jwkSetUri, @DefaultValue("soukly-user-service") String issuer,
        @DefaultValue("30s") Duration clockSkew) {
}
