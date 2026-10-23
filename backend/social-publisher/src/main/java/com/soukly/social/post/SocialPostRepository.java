package com.soukly.social.post;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
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
     * @return the network of each post cancelled
     */
    List<Network> cancelPendingOfListing(UUID listingId) {
        return jdbc.query("""
                UPDATE social_posts SET status = 'CANCELLED', next_attempt_at = NULL, updated_at = ?
                WHERE listing_id = ? AND status = 'PENDING' RETURNING network
                """, (rs, row) -> Network.valueOf(rs.getString("network")), Timestamp.from(clock.instant()),
                listingId);
    }

    /** Same as {@link #cancelPendingOfListing} for every listing of a member. */
    List<Network> cancelPendingOfUser(UUID userId) {
        return jdbc.query("""
                UPDATE social_posts SET status = 'CANCELLED', next_attempt_at = NULL, updated_at = ?
                WHERE user_id = ? AND status = 'PENDING' RETURNING network
                """, (rs, row) -> Network.valueOf(rs.getString("network")), Timestamp.from(clock.instant()), userId);
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

    private static final String ROW_COLUMNS = "listing_id, network, status, permalink, last_error_code, attempts, "
            + "manual_retries, created_at, updated_at, content->>'title' AS title";

    /** The posts of these listings that belong to the member (SOC-06); a listing of another member gives nothing. */
    List<PostRow> rowsOfListings(UUID userId, Collection<UUID> listingIds) {
        if (listingIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query("SELECT " + ROW_COLUMNS + " FROM social_posts WHERE user_id = ? AND listing_id = ANY (?) "
                + "ORDER BY listing_id, network", this::row, userId, listingIds.toArray(UUID[]::new));
    }

    Optional<PostRow> row(UUID userId, UUID listingId, Network network) {
        return jdbc.query("SELECT " + ROW_COLUMNS + " FROM social_posts WHERE user_id = ? AND listing_id = ? "
                + "AND network = ?", this::row, userId, listingId, network.name()).stream().findFirst();
    }

    /**
     * The posts of the member on this network blocked by a connection problem ({@code codes}) and created after
     * {@code since}, the oldest first.
     */
    List<PostRow> blocked(UUID userId, Network network, Collection<String> codes, Instant since) {
        return jdbc.query("SELECT " + ROW_COLUMNS + " FROM social_posts WHERE user_id = ? AND network = ? "
                + "AND status IN ('FAILED', 'SKIPPED') AND last_error_code = ANY (?) AND created_at > ? "
                + "ORDER BY created_at, listing_id", this::row, userId, network.name(),
                codes.toArray(String[]::new), Timestamp.from(since));
    }

    /**
     * "Try again" (SOC-06): the post goes back to {@code PENDING}, due now, with its attempts reset and one more manual
     * attempt, if it is still {@code FAILED} or {@code SKIPPED} and has a manual attempt left.
     *
     * @return {@code false} when the row changed in the meantime: nothing is done
     */
    boolean retryByHand(UUID userId, UUID listingId, Network network, int maxManualRetries) {
        Timestamp now = Timestamp.from(clock.instant());
        return jdbc.update("""
                UPDATE social_posts SET status = 'PENDING', attempts = 0, next_attempt_at = ?, last_error_code = NULL,
                    manual_retries = manual_retries + 1, updated_at = ?
                WHERE user_id = ? AND listing_id = ? AND network = ? AND status IN ('FAILED', 'SKIPPED')
                    AND manual_retries < ?
                """, now, now, userId, listingId, network.name(), maxManualRetries) == 1;
    }

    /**
     * "Publish them now" after a reconnection (SOC-06): these blocked posts go back to {@code PENDING}, due now, with
     * their attempts reset, without counting a manual attempt.
     *
     * @return the posts put back in the queue
     */
    int relaunch(UUID userId, Network network, Collection<UUID> listingIds, Collection<String> codes) {
        if (listingIds.isEmpty()) {
            return 0;
        }
        Timestamp now = Timestamp.from(clock.instant());
        return jdbc.update("""
                UPDATE social_posts SET status = 'PENDING', attempts = 0, next_attempt_at = ?, last_error_code = NULL,
                    updated_at = ?
                WHERE user_id = ? AND network = ? AND listing_id = ANY (?) AND status IN ('FAILED', 'SKIPPED')
                    AND last_error_code = ANY (?)
                """, now, now, userId, network.name(), listingIds.toArray(UUID[]::new), codes.toArray(String[]::new));
    }

    private PostRow row(ResultSet rs, int row) throws SQLException {
        return new PostRow(rs.getObject("listing_id", UUID.class), Network.valueOf(rs.getString("network")),
                PostStatus.valueOf(rs.getString("status")), rs.getString("permalink"), rs.getString("last_error_code"),
                rs.getInt("attempts"), rs.getInt("manual_retries"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(), rs.getString("title"));
    }

    private SocialPost post(ResultSet rs, int row) throws SQLException {
        return new SocialPost(rs.getObject("id", UUID.class), rs.getObject("listing_id", UUID.class),
                rs.getObject("user_id", UUID.class), Network.valueOf(rs.getString("network")),
                PostStatus.valueOf(rs.getString("status")), json.readValue(rs.getString("content"), PostContent.class),
                rs.getString("external_account_id"), rs.getString("external_post_id"), rs.getInt("attempts"),
                rs.getString("request_id"));
    }
}
