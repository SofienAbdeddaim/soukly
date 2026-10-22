package com.soukly.social.facebook;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.soukly.social.token.TokenCipher;

import tools.jackson.databind.json.JsonMapper;

/**
 * The {@code pending_page_selections} table: one choice in progress per member, encrypted like the connection tokens
 * because it holds the Page tokens.
 */
@Repository
class PendingSelectionRepository {

    private final JdbcTemplate jdbc;
    private final TokenCipher cipher;
    private final JsonMapper json;

    PendingSelectionRepository(JdbcTemplate jdbc, TokenCipher cipher, JsonMapper json) {
        this.jdbc = jdbc;
        this.cipher = cipher;
        this.json = json;
    }

    /** Replaces the choice in progress of the member. */
    void save(UUID userId, PendingSelection selection, Instant expiresAt) {
        byte[] ciphertext = cipher.encrypt(json.writeValueAsString(selection), associatedData(userId));
        jdbc.update("""
                INSERT INTO pending_page_selections (user_id, ciphertext, key_version, expires_at) VALUES (?, ?, ?, ?)
                ON CONFLICT (user_id) DO UPDATE SET ciphertext = EXCLUDED.ciphertext,
                    key_version = EXCLUDED.key_version, expires_at = EXCLUDED.expires_at
                """, userId, ciphertext, cipher.keyVersion(), Timestamp.from(expiresAt));
    }

    /** The choice in progress of the member, empty when there is none or it expired. */
    Optional<PendingSelection> find(UUID userId, Instant now) {
        return jdbc.query("""
                SELECT ciphertext, key_version FROM pending_page_selections WHERE user_id = ? AND expires_at > ?
                """, (rs, row) -> json.readValue(
                        cipher.decrypt(rs.getBytes("ciphertext"), rs.getInt("key_version"), associatedData(userId)),
                        PendingSelection.class),
                userId, Timestamp.from(now)).stream().findFirst();
    }

    void delete(UUID userId) {
        jdbc.update("DELETE FROM pending_page_selections WHERE user_id = ?", userId);
    }

    int deleteExpired(Instant now) {
        return jdbc.update("DELETE FROM pending_page_selections WHERE expires_at <= ?", Timestamp.from(now));
    }

    private static String associatedData(UUID userId) {
        return "pending_page_selections:" + userId;
    }
}
