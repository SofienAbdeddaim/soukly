package com.soukly.social.connection;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.soukly.social.token.TokenCipher;

/**
 * The {@code social_connections} table: one connection per member and per network. Tokens are encrypted here with
 * {@link TokenCipher}, bound to the member and the network, and never read back by this ticket (SOC-04 does).
 */
@Repository
public class SocialConnectionRepository {

    private static final RowMapper<ConnectionView> VIEW = SocialConnectionRepository::view;

    private final JdbcTemplate jdbc;
    private final TokenCipher cipher;
    private final Clock clock;

    SocialConnectionRepository(JdbcTemplate jdbc, TokenCipher cipher, Clock clock) {
        this.jdbc = jdbc;
        this.cipher = cipher;
        this.clock = clock;
    }

    /** Associated data of a connection token: a ciphertext only decrypts in the row of its member and network. */
    public static String associatedData(UUID userId, Network network) {
        return "social_connections:" + userId + ":" + network;
    }

    /**
     * Connects the account, or replaces the one already connected for this network (one Page per member): the status
     * goes back to {@code CONNECTED} and the connection date is the current one.
     */
    public ConnectionView save(UUID userId, NewConnection connection) {
        Instant now = clock.instant();
        byte[] ciphertext = cipher.encrypt(connection.token(), associatedData(userId, connection.network()));
        jdbc.update("""
                INSERT INTO social_connections (id, user_id, network, external_id, external_user_id, display_name,
                    picture_url, token_ciphertext, key_version, status, connected_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'CONNECTED', ?, ?)
                ON CONFLICT (user_id, network) DO UPDATE SET external_id = EXCLUDED.external_id,
                    external_user_id = EXCLUDED.external_user_id, display_name = EXCLUDED.display_name,
                    picture_url = EXCLUDED.picture_url, token_ciphertext = EXCLUDED.token_ciphertext,
                    key_version = EXCLUDED.key_version, status = 'CONNECTED', connected_at = EXCLUDED.connected_at,
                    updated_at = EXCLUDED.updated_at
                """, UUID.randomUUID(), userId, connection.network().name(), connection.externalId(),
                connection.externalUserId(), connection.displayName(), connection.pictureUrl(), ciphertext,
                cipher.keyVersion(), Timestamp.from(now), Timestamp.from(now));
        return new ConnectionView(connection.network(), ConnectionStatus.CONNECTED, connection.displayName(),
                connection.pictureUrl(), now);
    }

    public List<ConnectionView> findAll(UUID userId) {
        return jdbc.query("""
                SELECT network, status, display_name, picture_url, connected_at FROM social_connections
                WHERE user_id = ? ORDER BY network
                """, VIEW, userId);
    }

    /**
     * Deletes the connection of this network.
     *
     * @return the app-scoped id of the person who connected it, to revoke the permissions; empty when there was none
     *         or the id is unknown
     */
    public Optional<String> delete(UUID userId, Network network) {
        return jdbc.query("""
                DELETE FROM social_connections WHERE user_id = ? AND network = ? RETURNING external_user_id
                """, (rs, row) -> Optional.ofNullable(rs.getString(1)), userId, network.name())
                .stream().findFirst().flatMap(id -> id);
    }

    /** Deletes every connection of the member; returns the app-scoped ids of the people who connected them. */
    public List<String> deleteAll(UUID userId) {
        return jdbc.query("DELETE FROM social_connections WHERE user_id = ? RETURNING external_user_id",
                (rs, row) -> rs.getString(1), userId).stream().filter(id -> id != null).toList();
    }

    /** Whether another connection still uses the permissions this person granted (then they are not revoked). */
    public boolean usedByAnyConnection(Network network, String externalUserId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM social_connections WHERE network = ? AND external_user_id = ?)
                """, Boolean.class, network.name(), externalUserId));
    }

    private static ConnectionView view(ResultSet rs, int row) throws SQLException {
        return new ConnectionView(Network.valueOf(rs.getString("network")),
                ConnectionStatus.valueOf(rs.getString("status")), rs.getString("display_name"),
                rs.getString("picture_url"), rs.getTimestamp("connected_at").toInstant());
    }
}
