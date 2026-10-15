package com.soukly.gateway.support;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;

/**
 * Test keys and tokens. RSA key pairs are generated in memory when the tests run and never written to disk.
 * The published JWKS only contains public keys.
 */
public final class TestJwts {

    public static final String ISSUER = "soukly-user-service";

    private final List<RSAKey> published = new CopyOnWriteArrayList<>();

    /** Generates a new RSA key pair. */
    public static RSAKey newKey(String kid) {
        try {
            return new RSAKeyGenerator(2048).keyID(kid).generate();
        } catch (JOSEException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Adds a key to the JWKS served by the fake user-service. */
    public void publish(RSAKey key) {
        published.add(key);
    }

    /** The JWKS document, public keys only. */
    public String jwks() {
        return new JWKSet(published.stream().map(JWK::toPublicJWK).toList()).toString();
    }

    public static JWTClaimsSet.Builder validClaims() {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(900)));
    }

    public static String rs256(RSAKey key, JWTClaimsSet claims) {
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
                    claims);
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        } catch (JOSEException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static String hs256(String kid, JWTClaimsSet claims) {
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).keyID(kid).build(), claims);
            jwt.sign(new MACSigner(new byte[32]));
            return jwt.serialize();
        } catch (JOSEException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static String unsigned(JWTClaimsSet claims) {
        return new PlainJWT(claims).serialize();
    }
}
