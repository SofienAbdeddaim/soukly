package com.soukly.social.token;

/** A token cannot be encrypted or decrypted. The message never contains the token, the key or the ciphertext. */
public class TokenCipherException extends RuntimeException {

    TokenCipherException(String message) {
        super(message);
    }
}
