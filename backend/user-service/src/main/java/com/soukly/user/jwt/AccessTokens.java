package com.soukly.user.jwt;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * Issues the access tokens: JWT signed in RS256 with the active key, valid 15 minutes. The claims identify the
 * account only by its id: no email, phone number or name.
 */
@Component
public class AccessTokens {

    /** Role of every signed-in account in V1. */
    public static final List<String> USER_ROLES = List.of("USER");

    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final Clock clock;

    AccessTokens(JwtEncoder encoder, JwtProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    public AccessToken issue(UUID userId) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenTtl()))
                .id(UUID.randomUUID().toString())
                .claim("roles", USER_ROLES)
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(properties.keyId()).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AccessToken(token, properties.accessTokenTtl().toSeconds());
    }

    /**
     * @param expiresIn validity in seconds
     */
    public record AccessToken(String value, long expiresIn) {

        @Override
        public String toString() {
            return "AccessToken[redacted]";
        }
    }
}
