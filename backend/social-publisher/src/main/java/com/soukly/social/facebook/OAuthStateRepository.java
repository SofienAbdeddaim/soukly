package com.soukly.social.facebook;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.soukly.social.connection.Network;

/**
 * The {@code oauth_states} table: the OAuth states of the connections in progress, stored as a SHA-256 hash only, with
 * their member, network, language and expiry. A state is consumed once.
 */
@Repository
class OAuthStateRepository {

    private final JdbcTemplate jdbc;

    OAuthStateRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    void save(String state, UUID userId, Network network, String lang, Instant expiresAt) {
        jdbc.update("INSERT INTO oauth_states (state_hash, user_id, network, lang, expires_at) VALUES (?, ?, ?, ?, ?)",
                hash(state), userId, network.name(), lang, Timestamp.from(expiresAt));
    }

    /**
     * Deletes the state if it belongs to this member and network, so that it can never be used again.
     *
     * @return the language of the connection when the state existed and had not expired; empty otherwise (unknown,
     *         used, expired, or started by another member: then nothing is deleted)
     */
    Optional<String> consume(String state, UUID userId, Network network, Instant now) {
        return jdbc.query("""
                DELETE FROM oauth_states WHERE state_hash = ? AND user_id = ? AND network = ?
                RETURNING lang, expires_at
                """, (rs, row) -> rs.getTimestamp("expires_at").toInstant().isAfter(now)
                        ? Optional.of(rs.getString("lang"))
                        : Optional.<String>empty(),
                hash(state), userId, network.name())
                .stream().findFirst().flatMap(lang -> lang);
    }

    int deleteExpired(Instant now) {
        return jdbc.update("DELETE FROM oauth_states WHERE expires_at <= ?", Timestamp.from(now));
    }

    void deleteAll(UUID userId) {
        jdbc.update("DELETE FROM oauth_states WHERE user_id = ?", userId);
    }

    static byte[] hash(String state) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(state.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
