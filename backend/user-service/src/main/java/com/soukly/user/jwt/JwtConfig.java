package com.soukly.user.jwt;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;

/**
 * Signing and validation of the access tokens with the keys of {@link JwtKeys}. The service validates the tokens
 * it receives with the same rules as the gateway (defense in depth), from its own keys rather than over HTTP.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
class JwtConfig {

    /** Same tolerance as the gateway (SETUP-06). */
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

    @Bean
    JwtKeys jwtKeys(JwtProperties properties) {
        return JwtKeys.load(properties);
    }

    /** Only the active key can sign. */
    @Bean
    JwtEncoder jwtEncoder(JwtKeys keys) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(keys.signingKey())));
    }

    /** RS256 only, active or previous key, {@code exp} with 30 s of clock skew, {@code iss} checked. */
    @Bean
    JwtDecoder jwtDecoder(JwtKeys keys, JwtProperties properties, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSource(new ImmutableJWKSet<>(keys.publicKeys()))
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        JwtTimestampValidator timestamps = new JwtTimestampValidator(CLOCK_SKEW);
        timestamps.setClock(clock);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(List.of(timestamps,
                new JwtIssuerValidator(properties.issuer()))));
        return decoder;
    }
}
