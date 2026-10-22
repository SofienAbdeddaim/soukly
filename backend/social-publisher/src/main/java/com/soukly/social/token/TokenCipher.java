package com.soukly.social.token;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Encrypts the tokens of the social networks with AES-256-GCM (JCA of the JDK) before they are stored (SOC-01). Every
 * encryption draws a new 12-byte nonce, stored in front of the ciphertext: {@code nonce || ciphertext || tag}. The
 * associated data binds a ciphertext to its row (for example the member and the network), so that a ciphertext copied
 * to another row does not decrypt.
 *
 * <p>The key ({@code SOCIAL_TOKEN_ENC_KEY}, 32 bytes in base64) is checked when the service starts: a missing or
 * invalid key stops the start. Neither the key nor a token is ever logged or put in an exception message.
 */
@Component
@EnableConfigurationProperties(TokenEncryptionProperties.class)
public class TokenCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final int keyVersion;
    private final SecureRandom random = new SecureRandom();

    public TokenCipher(TokenEncryptionProperties properties) {
        this.key = new SecretKeySpec(decodeKey(properties.key()), "AES");
        this.keyVersion = properties.keyVersion();
        if (keyVersion < 1 || keyVersion > Short.MAX_VALUE) {
            throw new IllegalStateException("soukly.social.token-encryption.key-version must be between 1 and "
                    + Short.MAX_VALUE);
        }
    }

    /** Version of the key that {@link #encrypt} uses, to store with the ciphertext. */
    public int keyVersion() {
        return keyVersion;
    }

    public byte[] encrypt(String plaintext, String associatedData) {
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(NONCE_BYTES + encrypted.length).put(nonce).put(encrypted).array();
        } catch (GeneralSecurityException e) {
            throw new TokenCipherException("Encryption failed: " + e.getClass().getSimpleName());
        }
    }

    /**
     * @param keyVersion the version stored with the ciphertext; only the current key exists today
     * @throws TokenCipherException the ciphertext was changed, was encrypted for other associated data or with
     *                              another key
     */
    public String decrypt(byte[] ciphertext, int keyVersion, String associatedData) {
        if (keyVersion != this.keyVersion) {
            throw new TokenCipherException("No key of version " + keyVersion);
        }
        if (ciphertext == null || ciphertext.length < NONCE_BYTES + TAG_BITS / 8) {
            throw new TokenCipherException("Ciphertext too short");
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key,
                    new GCMParameterSpec(TAG_BITS, Arrays.copyOfRange(ciphertext, 0, NONCE_BYTES)));
            cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
            byte[] plaintext = cipher.doFinal(ciphertext, NONCE_BYTES, ciphertext.length - NONCE_BYTES);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new TokenCipherException("Decryption failed: " + e.getClass().getSimpleName());
        }
    }

    private static byte[] decodeKey(String base64) {
        if (base64 == null || base64.isBlank()) {
            throw new IllegalStateException("SOCIAL_TOKEN_ENC_KEY is not set: 32 random bytes in base64 are required "
                    + "(openssl rand -base64 32)");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("SOCIAL_TOKEN_ENC_KEY is not valid base64");
        }
        if (bytes.length != KEY_BYTES) {
            throw new IllegalStateException("SOCIAL_TOKEN_ENC_KEY must decode to " + KEY_BYTES + " bytes, not "
                    + bytes.length);
        }
        return bytes;
    }
}
