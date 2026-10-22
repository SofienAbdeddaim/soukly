package com.soukly.social.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;

class TokenCipherTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String TOKEN = "EAAGm0PX4ZCpsBAPage-token-0123456789";

    private final TokenCipher cipher = new TokenCipher(new TokenEncryptionProperties(KEY, 1));

    @Test
    void aTokenComesBackFromItsCiphertext() {
        byte[] ciphertext = cipher.encrypt(TOKEN, "row-1");

        assertThat(cipher.decrypt(ciphertext, 1, "row-1")).isEqualTo(TOKEN);
        assertThat(new String(ciphertext, StandardCharsets.ISO_8859_1)).doesNotContain(TOKEN);
        // nonce (12) + plaintext + tag (16)
        assertThat(ciphertext).hasSize(12 + TOKEN.length() + 16);
    }

    @Test
    void everyEncryptionUsesANewNonce() {
        byte[] first = cipher.encrypt(TOKEN, "row-1");
        byte[] second = cipher.encrypt(TOKEN, "row-1");

        assertThat(first).isNotEqualTo(second);
        assertThat(java.util.Arrays.copyOf(first, 12)).isNotEqualTo(java.util.Arrays.copyOf(second, 12));
    }

    @Test
    void aCiphertextChangedOrMovedToAnotherRowDoesNotDecrypt() {
        byte[] ciphertext = cipher.encrypt(TOKEN, "row-1");
        byte[] changed = ciphertext.clone();
        changed[20] ^= 1;

        assertThatThrownBy(() -> cipher.decrypt(changed, 1, "row-1")).isInstanceOf(TokenCipherException.class)
                .hasMessageNotContaining(TOKEN);
        assertThatThrownBy(() -> cipher.decrypt(ciphertext, 1, "row-2")).isInstanceOf(TokenCipherException.class);
        assertThatThrownBy(() -> cipher.decrypt(ciphertext, 2, "row-1")).isInstanceOf(TokenCipherException.class);
    }

    @Test
    void anotherKeyCannotDecrypt() {
        byte[] other = new byte[32];
        other[0] = 1;
        TokenCipher otherCipher = new TokenCipher(
                new TokenEncryptionProperties(Base64.getEncoder().encodeToString(other), 1));

        assertThatThrownBy(() -> otherCipher.decrypt(cipher.encrypt(TOKEN, "row-1"), 1, "row-1"))
                .isInstanceOf(TokenCipherException.class);
    }

    @Test
    void anInvalidKeyIsRefusedAtStartWithoutQuotingIt() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> new TokenCipher(new TokenEncryptionProperties(null, 1)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("not set");
        assertThatThrownBy(() -> new TokenCipher(new TokenEncryptionProperties(" ", 1)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new TokenCipher(new TokenEncryptionProperties("change-me-base64-32-bytes", 1)))
                .isInstanceOf(IllegalStateException.class).hasMessageNotContaining("change-me");
        assertThatThrownBy(() -> new TokenCipher(new TokenEncryptionProperties(shortKey, 1)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("32 bytes")
                .hasMessageNotContaining(shortKey);
        assertThatThrownBy(() -> new TokenCipher(new TokenEncryptionProperties(KEY, 0)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theKeyIsNotInTheStringFormOfTheProperties() {
        assertThat(new TokenEncryptionProperties(KEY, 1).toString()).doesNotContain(KEY);
    }
}
