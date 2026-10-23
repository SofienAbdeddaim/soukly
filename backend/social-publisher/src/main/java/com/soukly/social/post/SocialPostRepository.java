package com.soukly.social.post;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.soukly.social.connection.Network;

import tools.jackson.databind.json.JsonMapper;

/**
 * The {@code social_posts} table (SOC-04): one row per listing and per network, never two
 * ({@code social_posts_listing_network_key}).
 */
@Repository
class SocialPostRepository {

    private static final String COLUMNS = "id, listing_id, user_id, network, status, content, external_account_id, "
            + "external_post_id, attempts, request_id";

    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final Clock clock;

    SocialPostRepository(JdbcTemplate jdbc, JsonMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    /**
     * Records the post of a listing on a network, {@code PENDING} and due now, or {@code SKIPPED} with its reason.
     *
     * @return {@code false} when the listing has a row for this network already: nothing changes
     */
    boolean create(UUID listingId, UUID userId, Network network, PostContent content, PostStatus status,
            String reason, String requestId) {
        Timestamp now = Timestamp.from(clock.instant());
        return jdbc.update("""
                INSERT INTO social_posts (id, listing_id, user_id, network, status, content, last_error_code,
                    next_attempt_at, request_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?)
                ON CONFLICT (listing_id, network) DO NOTHING
                """, UUID.randomUUID(), listingId, userId, network.name(), status.name(), json.writeValueAsString(content),
                reason, status == PostStatus.PENDING ? now : null, requestId, now, now) == 1;
    }

    /**
     * Locks the next {@code PENDING} post of this network that is due, the oldest first. Must run in a transaction:
     * the row stays locked until it ends, and a row locked by another run, or by a consumer cancelling it, is
     * skipped ({@code SKIP LOCKED}).
     */
    Optional<SocialPost> lockNextDue(Network network) {
        return jdbc.query("SELECT " + COLUMNS + " FROM social_posts WHERE network = ? AND status = 'PENDING' "
                + "AND next_attempt_at <= ? ORDER BY next_attempt_at, created_at LIMIT 1 FOR UPDATE SKIP LOCKED",
                this::post, network.name(), Timestamp.from(clock.instant())).stream().findFirst();
    }

    void markPublished(UUID id, String accountId, String postId, String permalink) {
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update("""
                UPDATE social_posts SET status = 'PUBLISHED', external_account_id = ?, external_post_id = ?,
                    permalink = ?, last_error_code = NULL, next_attempt_at = NULL, published_at = ?, updated_at = ?
                WHERE id = ?
                """, accountId, postId, permalink, now, now, id);
    }

    /** {@code SKIPPED} or {@code FAILED}, with the reason; never tried again. */
    void markEnded(UUID id, PostStatus status, String reason, int attempts) {
        jdbc.update("""
                UPDATE social_posts SET status = ?, last_error_code = ?, attempts = ?, next_attempt_at = NULL,
                    updated_at = ? WHERE id = ?
                """, status.name(), reason, attempts, Timestamp.from(clock.instant()), id);
    }

    /** A temporary failure: tried again at {@code next}. */
    void retryAt(UUID id, int attempts, String reason, Instant next) {
        jdbc.update("""
                UPDATE social_posts SET attempts = ?, last_error_code = ?, next_attempt_at = ?, updated_at = ?
                WHERE id = ?
                """, attempts, reason, Timestamp.from(next), Timestamp.from(clock.instant()), id);
    }

    /** Waits for the pace of the publications, without counting an attempt. */
    void postpone(UUID id, Instant next) {
        jdbc.update("UPDATE social_posts SET next_attempt_at = ?, updated_at = ? WHERE id = ?", Timestamp.from(next),
                Timestamp.from(clock.instant()), id);
    }

    /**
     * Cancels the {@code PENDING} posts of a listing. Waits for a publication in progress (its row lock): a post just
     * published is then {@code PUBLISHED} and not cancelled.
     *
     * @return the number of posts cancelled
     */
    int cancelPendingOfListing(UUID listingId) {
        return jdbc.update("""
                UPDATE social_posts SET status = 'CANCELLED', next_attempt_at = NULL, updated_at = ?
                WHERE listing_id = ? AND status = 'PENDING'
                """, Timestamp.from(clock.instant()), listingId);
    }

    /** Same as {@link #cancelPendingOfListing} for every listing of a member. */
    int cancelPendingOfUser(UUID userId) {
        return jdbc.update("""
                UPDATE social_posts SET status = 'CANCELLED', next_attempt_at = NULL, updated_at = ?
                WHERE user_id = ? AND status = 'PENDING'
                """, Timestamp.from(clock.instant()), userId);
    }

    List<SocialPost> publishedOfListing(UUID listingId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM social_posts WHERE listing_id = ? AND status = 'PUBLISHED' "
                + "ORDER BY network", this::post, listingId);
    }

    List<SocialPost> publishedOfUser(UUID userId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM social_posts WHERE user_id = ? AND status = 'PUBLISHED' "
                + "ORDER BY created_at", this::post, userId);
    }

    void markDeleted(UUID id) {
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update("""
                UPDATE social_posts SET status = 'DELETED', last_error_code = NULL, deleted_at = ?, updated_at = ?
                WHERE id = ?
                """, now, now, id);
    }

    /** The post could not be removed: it stays {@code PUBLISHED} with the reason. */
    void markNotDeleted(UUID id, String reason) {
        jdbc.update("UPDATE social_posts SET last_error_code = ?, updated_at = ? WHERE id = ?", reason,
                Timestamp.from(clock.instant()), id);
    }

    /** Deletes every post row of a member (account deleted). */
    int deleteAll(UUID userId) {
        return jdbc.update("DELETE FROM social_posts WHERE user_id = ?", userId);
    }

    private SocialPost post(ResultSet rs, int row) throws SQLException {
        return new SocialPost(rs.getObject("id", UUID.class), rs.getObject("listing_id", UUID.class),
                rs.getObject("user_id", UUID.class), Network.valueOf(rs.getString("network")),
                PostStatus.valueOf(rs.getString("status")), json.readValue(rs.getString("content"), PostContent.class),
                rs.getString("external_account_id"), rs.getString("external_post_id"), rs.getInt("attempts"),
                rs.getString("request_id"));
    }
}
