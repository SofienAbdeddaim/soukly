package com.soukly.user.jwt;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;

/**
 * RSA keys of the access tokens: the active key pair, read from a PEM file, and optionally the public key of the
 * previous pair, still published during a key rotation so that the tokens it signed stay valid until they expire.
 */
public final class JwtKeys {

    static final int MIN_KEY_BITS = 2048;
    static final String GENERATE_COMMAND = "scripts/gen-jwt-key.sh";

    private final RSAKey signingKey;
    private final JWKSet publicKeys;

    private JwtKeys(RSAKey signingKey, RSAKey previousKey) {
        this.signingKey = signingKey;
        List<JWK> keys = new ArrayList<>();
        keys.add(signingKey.toPublicJWK());
        if (previousKey != null) {
            keys.add(previousKey);
        }
        this.publicKeys = new JWKSet(keys);
    }

    /**
     * Reads the keys named by the configuration.
     *
     * @throws IllegalStateException with the command to run when the private key is missing, or when a key is
     *                               invalid or shorter than 2048 bits
     */
    public static JwtKeys load(JwtProperties properties) {
        String keyId = required(properties.keyId(), "JWT_KEY_ID");
        Path privateKeyPath = path(properties.privateKeyPath());
        if (privateKeyPath == null || !Files.isRegularFile(privateKeyPath)) {
            throw new IllegalStateException("The JWT private key was not found at "
                    + (privateKeyPath == null ? "(JWT_PRIVATE_KEY_PATH is not set)"
                            : privateKeyPath.toAbsolutePath().normalize())
                    + ". Generate a local key with " + GENERATE_COMMAND
                    + " (from the root of the repository) or set JWT_PRIVATE_KEY_PATH.");
        }
        RSAKey signingKey = signingKey(keyId, privateKeyPath);

        RSAKey previousKey = null;
        Path previousPath = path(properties.previousPublicKeyPath());
        if (previousPath != null) {
            String previousKeyId = required(properties.previousKeyId(), "JWT_PREVIOUS_KEY_ID");
            if (previousKeyId.equals(keyId)) {
                throw new IllegalStateException("JWT_PREVIOUS_KEY_ID must differ from JWT_KEY_ID");
            }
            previousKey = publicKey(previousKeyId, previousPath);
        }
        return new JwtKeys(signingKey, previousKey);
    }

    /** Active key pair, with its private part: never published. */
    public RSAKey signingKey() {
        return signingKey;
    }

    /** Public keys only: the active one, then the previous one during a rotation. */
    public JWKSet publicKeys() {
        return publicKeys;
    }

    private static RSAKey signingKey(String keyId, Path path) {
        try {
            byte[] der = pem(path, "PRIVATE KEY");
            if (!(KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der))
                    instanceof RSAPrivateCrtKey privateKey)) {
                throw new IllegalStateException("The JWT private key at " + path + " has no public exponent");
            }
            RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
            checkSize(publicKey.getModulus(), path);
            return new RSAKey.Builder(publicKey).privateKey(privateKey).keyID(keyId).keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256).build();
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("The JWT private key at " + path
                    + " is not an RSA private key in PEM PKCS#8 format (-----BEGIN PRIVATE KEY-----)", e);
        }
    }

    private static RSAKey publicKey(String keyId, Path path) {
        try {
            RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(pem(path, "PUBLIC KEY")));
            checkSize(publicKey.getModulus(), path);
            return new RSAKey.Builder(publicKey).keyID(keyId).keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.RS256)
                    .build();
        } catch (GeneralSecurityException | IllegalArgumentException | ClassCastException e) {
            throw new IllegalStateException("The previous JWT public key at " + path
                    + " is not an RSA public key in PEM format (-----BEGIN PUBLIC KEY-----)", e);
        }
    }

    /** DER content of the first PEM block of this type. */
    private static byte[] pem(Path path, String type) {
        String content;
        try {
            content = Files.readString(path, StandardCharsets.US_ASCII);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + path, e);
        }
        String begin = "-----BEGIN " + type + "-----";
        String end = "-----END " + type + "-----";
        int start = content.indexOf(begin);
        int stop = content.indexOf(end);
        if (start < 0 || stop < start) {
            throw new IllegalArgumentException("No " + type + " block");
        }
        return Base64.getMimeDecoder().decode(content.substring(start + begin.length(), stop));
    }

    private static void checkSize(BigInteger modulus, Path path) {
        if (modulus.bitLength() < MIN_KEY_BITS) {
            throw new IllegalStateException("The JWT key at " + path + " has " + modulus.bitLength()
                    + " bits; at least " + MIN_KEY_BITS + " are required");
        }
    }

    private static Path path(String value) {
        return value == null || value.isBlank() ? null : Path.of(value.strip());
    }

    private static String required(String value, String variable) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(variable + " is not set");
        }
        return value;
    }
}
