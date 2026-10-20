package com.soukly.user.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

import com.nimbusds.jose.jwk.RSAKey;

/**
 * RSA keys of the tests, generated when the tests run and written to a temporary directory only: no key is ever
 * committed. {@link #PREVIOUS} plays the key of before a rotation, {@link #ACTIVE} the current one.
 */
public final class TestKeys {

    public static final String PREVIOUS_KID = "k1";
    public static final String ACTIVE_KID = "k2";

    public static final RSAKey PREVIOUS = generate(PREVIOUS_KID, 2048);
    public static final RSAKey ACTIVE = generate(ACTIVE_KID, 2048);

    private static final Path DIRECTORY = createDirectory();
    public static final Path ACTIVE_PRIVATE_PEM = writePrivate(ACTIVE, DIRECTORY.resolve("active-private.pem"));
    public static final Path PREVIOUS_PUBLIC_PEM = writePublic(PREVIOUS, DIRECTORY.resolve("previous-public.pem"));

    private TestKeys() {
    }

    public static RSAKey generate(String kid, int bits) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(bits);
            KeyPair pair = generator.generateKeyPair();
            return new RSAKey.Builder((RSAPublicKey) pair.getPublic()).privateKey((RSAPrivateKey) pair.getPrivate())
                    .keyID(kid).build();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Writes the private key in PEM PKCS#8, as {@code openssl genpkey} does. */
    public static Path writePrivate(RSAKey key, Path path) {
        try {
            return write(key.toPrivateKey(), "PRIVATE KEY", path);
        } catch (com.nimbusds.jose.JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Writes the public key in PEM (SubjectPublicKeyInfo), as {@code openssl pkey -pubout} does. */
    public static Path writePublic(RSAKey key, Path path) {
        try {
            return write(key.toPublicKey(), "PUBLIC KEY", path);
        } catch (com.nimbusds.jose.JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Path write(Key key, String type, Path path) {
        String body = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(key.getEncoded());
        try {
            return Files.writeString(path, "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path createDirectory() {
        try {
            Path directory = Files.createTempDirectory("soukly-jwt-test");
            directory.toFile().deleteOnExit();
            return directory;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
