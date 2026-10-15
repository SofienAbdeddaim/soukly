package com.soukly.gateway.security;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

import com.soukly.gateway.config.GatewayProperties;

/**
 * Validates the access tokens issued by user-service.
 *
 * <ul>
 * <li>RS256 only: {@code none}, HS256 and every other algorithm are rejected.</li>
 * <li>Public keys come from the JWKS of user-service. They are cached, and the JWKS is fetched again when a
 * token carries an unknown {@code kid} (key rotation). The first fetch happens on the first token, so the
 * gateway starts even when user-service is down.</li>
 * <li>{@code exp} and {@code nbf} are checked with the configured clock skew, {@code iss} must match.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
class JwtDecoderConfig {

    @Bean
    ReactiveJwtDecoder jwtDecoder(GatewayProperties properties) {
        GatewayProperties.Jwt jwt = properties.jwt();
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwt.jwkSetUri().toString())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(List.of(
                new JwtTimestampValidator(jwt.clockSkew()),
                new JwtIssuerValidator(jwt.issuer())));
        decoder.setJwtValidator(validator);
        return decoder;
    }
}
