package com.soukly.user.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Opaque tokens of the verification links and of the sessions: 32 bytes from {@link SecureRandom} in Base64 URL
 * (43 characters). Only their SHA-256 is stored.
 */
public final class OpaqueTokens {

    private static final int TOKEN_BYTES = 32;
    /** A token is 43 characters; anything much longer is not one of ours. */
    public static final int MAX_LENGTH = 128;

    private static final SecureRandom RANDOM = new SecureRandom();

    private OpaqueTokens() {
    }

    public static String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Whether a value received from a client may be one of our tokens (not empty, not too long). */
    public static boolean isPlausible(String token) {
        return token != null && !token.isEmpty() && token.length() <= MAX_LENGTH;
    }

    /** SHA-256 in hexadecimal (64 characters). */
    public static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
