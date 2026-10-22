package com.soukly.social.support;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;

/**
 * Simulated JWKS of user-service: an RSA key pair generated in memory when the tests start (never written to disk)
 * and a local HTTP server that publishes its public key. Tokens are signed like those of user-service.
 */
public final class TestJwks {

    public static final String ISSUER = "soukly-user-service";

    private static final RSAKey KEY = newKey("test-key");
    private static final HttpServer SERVER = start();

    private TestJwks() {
    }

    public static String uri() {
        return "http://127.0.0.1:" + SERVER.getAddress().getPort() + "/.well-known/jwks.json";
    }

    /** A valid access token of the account {@code userId}. */
    public static String token(UUID userId) {
        return sign(KEY, claims(userId).build());
    }

    public static JWTClaimsSet.Builder claims(UUID userId) {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject(userId.toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(900)))
                .jwtID(UUID.randomUUID().toString());
    }

    /** Signed with the published key. */
    public static String sign(JWTClaimsSet claims) {
        return sign(KEY, claims);
    }

    /** Signed with a key the JWKS does not publish. */
    public static String signWithUnknownKey(JWTClaimsSet claims) {
        return sign(newKey("unknown-key"), claims);
    }

    private static String sign(RSAKey key, JWTClaimsSet claims) {
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
                    claims);
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private static RSAKey newKey(String kid) {
        try {
            return new RSAKeyGenerator(2048).keyID(kid).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private static HttpServer start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            byte[] jwks = new JWKSet(KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            server.createContext("/.well-known/jwks.json", exchange -> {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, jwks.length);
                try (OutputStream body = exchange.getResponseBody()) {
                    body.write(jwks);
                }
            });
            server.setExecutor(null);
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
