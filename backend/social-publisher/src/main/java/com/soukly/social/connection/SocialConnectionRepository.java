package com.soukly.social.connection;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
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
 * {@link TokenCipher}, bound to the member and the network; only the refresh of the Instagram tokens reads them back
 * (SOC-02), then the publication (SOC-04, SOC-05).
 */
@Repository
public class SocialConnectionRepository {

    private static final RowMapper<ConnectionView> VIEW = SocialConnectionRepository::view;
    private static final String VIEW_COLUMNS = "network, status, display_name, picture_url, connected_at, auto_share";

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
     * Connects the account, or replaces the one already connected for this network (one account per network): the
     * status goes back to {@code CONNECTED} and the connection date is the current one.
     */
    public ConnectionView save(UUID userId, NewConnection connection) {
        Instant now = clock.instant();
        byte[] ciphertext = cipher.encrypt(connection.token(), associatedData(userId, connection.network()));
        // auto_share is left out of the update: a new connection of a network already connected keeps the preference
        return jdbc.queryForObject("""
                INSERT INTO social_connections (id, user_id, network, external_id, external_user_id, display_name,
                    picture_url, token_ciphertext, key_version, token_expires_at, status, connected_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'CONNECTED', ?, ?)
                ON CONFLICT (user_id, network) DO UPDATE SET external_id = EXCLUDED.external_id,
                    external_user_id = EXCLUDED.external_user_id, display_name = EXCLUDED.display_name,
                    picture_url = EXCLUDED.picture_url, token_ciphertext = EXCLUDED.token_ciphertext,
                    key_version = EXCLUDED.key_version, token_expires_at = EXCLUDED.token_expires_at,
                    status = 'CONNECTED', connected_at = EXCLUDED.connected_at, updated_at = EXCLUDED.updated_at
                RETURNING\s""" + VIEW_COLUMNS, VIEW, UUID.randomUUID(), userId, connection.network().name(),
                connection.externalId(), connection.externalUserId(), connection.displayName(),
                connection.pictureUrl(), ciphertext, cipher.keyVersion(), timestamp(connection.tokenExpiresAt()),
                Timestamp.from(now), Timestamp.from(now));
    }

    public List<ConnectionView> findAll(UUID userId) {
        return jdbc.query("SELECT " + VIEW_COLUMNS + " FROM social_connections WHERE user_id = ? ORDER BY network",
                VIEW, userId);
    }

    /**
     * Changes the preference "tick by default for every new listing" of a connection (SOC-03).
     *
     * @return the connection after the change; empty when the member has no connection of this network
     */
    public Optional<ConnectionView> updateAutoShare(UUID userId, Network network, boolean autoShare) {
        return jdbc.query("""
                UPDATE social_connections SET auto_share = ?, updated_at = ? WHERE user_id = ? AND network = ?
                RETURNING\s""" + VIEW_COLUMNS, VIEW, autoShare, Timestamp.from(clock.instant()), userId,
                network.name()).stream().findFirst();
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

    /** Deletes every connection of the member; returns them, to revoke the permissions where the network allows it. */
    public List<DeletedConnection> deleteAll(UUID userId) {
        return jdbc.query("DELETE FROM social_connections WHERE user_id = ? RETURNING network, external_user_id",
                (rs, row) -> new DeletedConnection(Network.valueOf(rs.getString(1)), rs.getString(2)), userId);
    }

    /** Whether another connection still uses the permissions this person granted (then they are not revoked). */
    public boolean usedByAnyConnection(Network network, String externalUserId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM social_connections WHERE network = ? AND external_user_id = ?)
                """, Boolean.class, network.name(), externalUserId));
    }

    /**
     * Locks the next connected token of this network that expires before {@code before}, after {@code after} in the
     * order of the expiry (keyset, {@code null} for the first one). Must run in a transaction: the row stays locked
     * until it ends, and a row locked by another run is skipped ({@code SKIP LOCKED}), so a token is never refreshed
     * twice at once.
     */
    public Optional<ExpiringToken> lockNextExpiring(Network network, Instant before, ExpiringToken after) {
        String keyset = after == null ? "" : "AND (token_expires_at, id) > (?, ?) ";
        Object[] args = after == null
                ? new Object[] { network.name(), Timestamp.from(before) }
                : new Object[] { network.name(), Timestamp.from(before), Timestamp.from(after.expiresAt()),
                    after.id() };
        return jdbc.query("SELECT id, user_id, token_ciphertext, key_version, token_expires_at "
                + "FROM social_connections WHERE network = ? AND status = 'CONNECTED' AND token_expires_at < ? "
                + keyset + "ORDER BY token_expires_at, id LIMIT 1 FOR UPDATE SKIP LOCKED", (rs, row) -> {
                    UUID userId = rs.getObject("user_id", UUID.class);
                    String token = cipher.decrypt(rs.getBytes("token_ciphertext"), rs.getInt("key_version"),
                            associatedData(userId, network));
                    return new ExpiringToken(rs.getObject("id", UUID.class), userId, network, token,
                            rs.getTimestamp("token_expires_at").toInstant());
                }, args).stream().findFirst();
    }

    /** Replaces the token of a connection after a refresh, with its new expiry. */
    public void updateToken(ExpiringToken connection, String token, Instant expiresAt) {
        jdbc.update("""
                UPDATE social_connections SET token_ciphertext = ?, key_version = ?, token_expires_at = ?,
                    updated_at = ? WHERE id = ?
                """, cipher.encrypt(token, associatedData(connection.userId(), connection.network())),
                cipher.keyVersion(), Timestamp.from(expiresAt), Timestamp.from(clock.instant()), connection.id());
    }

    /** Status of the connection of this network, without reading its token; empty when there is none. */
    public Optional<ConnectionStatus> status(UUID userId, Network network) {
        return jdbc.query("SELECT status FROM social_connections WHERE user_id = ? AND network = ?",
                (rs, row) -> ConnectionStatus.valueOf(rs.getString(1)), userId, network.name()).stream().findFirst();
    }

    /** The connection of this network with its token decrypted, to publish or remove a post (SOC-04). */
    public Optional<PublishingAccount> findForPublishing(UUID userId, Network network) {
        return jdbc.query("""
                SELECT id, external_id, status, token_ciphertext, key_version FROM social_connections
                WHERE user_id = ? AND network = ?
                """, (rs, row) -> new PublishingAccount(rs.getObject("id", UUID.class), rs.getString("external_id"),
                ConnectionStatus.valueOf(rs.getString("status")), cipher.decrypt(rs.getBytes("token_ciphertext"),
                        rs.getInt("key_version"), associatedData(userId, network))),
                userId, network.name()).stream().findFirst();
    }

    /**
     * The network refused the token: the member must connect again ({@link #save} clears it). Only a connected account
     * changes; {@code reconnect_notified_at} becomes {@code now} when the last email "connect again" is older than
     * {@code noticeInterval} (or there was none), in the same statement, so two changes at once never both notify.
     * Use {@link ConnectionHealth#needsReconnect}, which writes the event too.
     *
     * @return the change; empty when the connection was not {@code CONNECTED} (already to reconnect, or deleted)
     */
    Optional<NeedsReconnectChange> markNeedsReconnect(UUID id, Duration noticeInterval) {
        Timestamp now = Timestamp.from(clock.instant());
        Timestamp notifiedBefore = Timestamp.from(clock.instant().minus(noticeInterval));
        return jdbc.query("""
                UPDATE social_connections SET status = 'NEEDS_RECONNECT', updated_at = ?,
                    reconnect_notified_at = CASE WHEN reconnect_notified_at IS NULL OR reconnect_notified_at <= ?
                        THEN ? ELSE reconnect_notified_at END
                WHERE id = ? AND status = 'CONNECTED'
                RETURNING user_id, network, display_name, reconnect_notified_at = ? AS send_email
                """, (rs, row) -> new NeedsReconnectChange(rs.getObject("user_id", UUID.class),
                Network.valueOf(rs.getString("network")), rs.getString("display_name"), rs.getBoolean("send_email")),
                now, notifiedBefore, now, id, now).stream().findFirst();
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static ConnectionView view(ResultSet rs, int row) throws SQLException {
        return new ConnectionView(Network.valueOf(rs.getString("network")),
                ConnectionStatus.valueOf(rs.getString("status")), rs.getString("display_name"),
                rs.getString("picture_url"), rs.getTimestamp("connected_at").toInstant(), rs.getBoolean("auto_share"));
    }
}
