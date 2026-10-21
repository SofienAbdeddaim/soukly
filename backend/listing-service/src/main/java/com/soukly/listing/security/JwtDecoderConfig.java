package com.soukly.listing.security;

import java.util.List;
import java.util.UUID;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Validates the access tokens again, with the rules of the gateway (defense in depth): RS256 only, public keys from
 * the JWKS of user-service (cached, fetched again for an unknown {@code kid}, first fetch on the first token),
 * {@code exp} and {@code nbf} with the clock skew, {@code iss} checked. The {@code sub} must be an account id.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
class JwtDecoderConfig {

    @Bean
    JwtDecoder jwtDecoder(JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri().toString())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(List.of(
                new JwtTimestampValidator(properties.clockSkew()),
                new JwtIssuerValidator(properties.issuer()),
                subjectIsAnAccountId())));
        return decoder;
    }

    private static OAuth2TokenValidator<Jwt> subjectIsAnAccountId() {
        OAuth2TokenValidatorResult invalid = OAuth2TokenValidatorResult.failure(
                new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "The subject is not an account id.", null));
        return jwt -> {
            try {
                UUID.fromString(jwt.getSubject());
                return OAuth2TokenValidatorResult.success();
            } catch (IllegalArgumentException | NullPointerException e) {
                return invalid;
            }
        };
    }
}
