package com.soukly.listing.listing;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
class ListingRepository {

    private static final TypeReference<Map<String, Object>> ATTRIBUTES = new TypeReference<>() {
    };

    private static final String COLUMNS = """
            id, owner_id, status, category_code, title, description, price_mode, price_tnd, negotiable,
            governorate_code, delegation_code, attributes, version, created_at, updated_at, published_at, expires_at,
            reminder_sent_at, deleted_at, share_to, share_language, short_ref""";

    private final JdbcClient jdbc;
    private final JsonMapper json;

    ListingRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    Optional<Listing> find(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM listings WHERE id = :id")
                .param("id", id)
                .query(this::listing)
                .optional();
    }

    /** Locks the row until the end of the transaction: two changes of the same listing never interleave. */
    Optional<Listing> findForUpdate(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM listings WHERE id = :id FOR UPDATE")
                .param("id", id)
                .query(this::listing)
                .optional();
    }

    long countByOwnerAndStatus(UUID ownerId, ListingStatus status) {
        return jdbc.sql("SELECT count(*) FROM listings WHERE owner_id = :owner AND status = :status")
                .param("owner", ownerId)
                .param("status", status.name())
                .query(Long.class)
                .single();
    }

    /**
     * Serializes the creations of one account until the end of the transaction, so that two requests in parallel
     * cannot both pass the draft limit.
     */
    void lockOwner(UUID ownerId) {
        jdbc.sql("SELECT pg_advisory_xact_lock(:key)")
                .param("key", ownerId.getMostSignificantBits() ^ ownerId.getLeastSignificantBits())
                .query((rs, row) -> 1)
                .single();
    }

    /**
     * Inserts a new listing with its short reference.
     *
     * @return {@code false} when another listing has this short reference already: nothing is inserted, and the
     *         transaction can go on with another one
     */
    boolean insert(Listing listing) {
        return jdbc.sql("""
                INSERT INTO listings (id, owner_id, status, category_code, title, description, price_mode, price_tnd,
                        negotiable, governorate_code, delegation_code, attributes, share_to, share_language, version,
                        created_at, updated_at, published_at, short_ref)
                VALUES (:id, :owner, :status, :category, :title, :description, :priceMode, :price, :negotiable,
                        :governorate, :delegation, CAST(:attributes AS jsonb), CAST(:shareTo AS text[]),
                        :shareLanguage, :version, :createdAt, :updatedAt, :publishedAt, :shortRef)
                ON CONFLICT (short_ref) DO NOTHING""")
                .param("id", listing.id())
                .param("owner", listing.ownerId())
                .param("status", listing.status().name())
                .params(contentParams(listing.content()))
                .param("version", listing.version())
                .param("createdAt", Timestamp.from(listing.createdAt()))
                .param("updatedAt", Timestamp.from(listing.updatedAt()))
                .param("publishedAt", timestamp(listing.publishedAt()))
                .param("shortRef", listing.shortRef())
                .update() == 1;
    }

    /**
     * Gives a short reference to a listing locked by {@link #findForUpdate(UUID)} that has none (created by a version
     * before SOC-05).
     *
     * @return {@code false} when another listing has this reference: nothing changes
     */
    boolean setShortRef(UUID id, String shortRef) {
        return jdbc.sql("""
                UPDATE listings SET short_ref = :shortRef
                WHERE id = :id AND short_ref IS NULL
                    AND NOT EXISTS (SELECT 1 FROM listings other WHERE other.short_ref = :shortRef)""")
                .param("id", id)
                .param("shortRef", shortRef)
                .update() == 1;
    }

    /** The listing of a short reference, already upper case (SOC-05). */
    Optional<Listing> findByShortRef(String shortRef) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM listings WHERE short_ref = :shortRef")
                .param("shortRef", shortRef)
                .query(this::listing)
                .optional();
    }

    /** Writes the content and the version of a listing locked by {@link #findForUpdate(UUID)}. */
    void update(Listing listing) {
        jdbc.sql("""
                UPDATE listings
                SET category_code = :category, title = :title, description = :description, price_mode = :priceMode,
                    price_tnd = :price, negotiable = :negotiable, governorate_code = :governorate,
                    delegation_code = :delegation, attributes = CAST(:attributes AS jsonb),
                    share_to = CAST(:shareTo AS text[]), share_language = :shareLanguage, version = :version,
                    updated_at = :updatedAt
                WHERE id = :id""")
                .param("id", listing.id())
                .params(contentParams(listing.content()))
                .param("version", listing.version())
                .param("updatedAt", Timestamp.from(listing.updatedAt()))
                .update();
    }

    /** Writes the status, its dates and the version of a listing locked by {@link #findForUpdate(UUID)}. */
    void updateStatus(Listing listing) {
        jdbc.sql("""
                UPDATE listings
                SET status = :status, version = :version, updated_at = :updatedAt, published_at = :publishedAt,
                    expires_at = :expiresAt, reminder_sent_at = :reminderSentAt, deleted_at = :deletedAt
                WHERE id = :id""")
                .param("id", listing.id())
                .param("status", listing.status().name())
                .param("version", listing.version())
                .param("updatedAt", Timestamp.from(listing.updatedAt()))
                .param("publishedAt", timestamp(listing.publishedAt()))
                .param("expiresAt", timestamp(listing.expiresAt()))
                .param("reminderSentAt", timestamp(listing.reminderSentAt()))
                .param("deletedAt", timestamp(listing.deletedAt()))
                .update();
    }

    /** Erases a listing whose photos are deleted already; its contact reveals go with it (ANN-05). */
    void delete(UUID id) {
        jdbc.sql("DELETE FROM listings WHERE id = :id").param("id", id).update();
    }

    /** Listings of an account that are not deleted yet, oldest first (deletion of the account, AUTH-05). */
    List<UUID> idsOfOwner(UUID ownerId) {
        return jdbc.sql("""
                SELECT id FROM listings
                WHERE owner_id = :owner AND status <> 'DELETED'
                ORDER BY created_at, id""")
                .param("owner", ownerId)
                .query(UUID.class)
                .list();
    }

    /**
     * Locks at most {@code limit} listings deleted at {@code before} or earlier, skipping those another transaction
     * holds, for the purge (ANN-05).
     */
    List<UUID> lockDeletedBefore(Instant before, int limit) {
        return jdbc.sql("""
                SELECT id FROM listings
                WHERE status = 'DELETED' AND deleted_at <= :before
                ORDER BY deleted_at
                LIMIT :limit
                FOR UPDATE SKIP LOCKED""")
                .param("before", Timestamp.from(before))
                .param("limit", limit)
                .query(UUID.class)
                .list();
    }

    /**
     * A page of the listings of {@code ownerId} in {@code status}, the latest changed first, with the id of their
     * cover photo and the number of members who were shown the phone number (ANN-05).
     *
     * @param after the last listing of the previous page, or null for the first page
     */
    List<OwnListingRow> ownPage(UUID ownerId, ListingStatus status, OwnListingsCursor after, int limit) {
        Map<String, Object> params = new HashMap<>();
        params.put("owner", ownerId);
        params.put("status", status.name());
        params.put("limit", limit);
        String keyset = "";
        if (after != null) {
            keyset = " AND (l.updated_at, l.id) < (:afterUpdatedAt, :afterId)";
            params.put("afterUpdatedAt", Timestamp.from(after.updatedAt()));
            params.put("afterId", after.id());
        }
        String sql = """
                WITH page AS (
                    SELECT %s FROM listings l
                    WHERE l.owner_id = :owner AND l.status = :status%s
                    ORDER BY l.updated_at DESC, l.id DESC
                    LIMIT :limit)
                SELECT page.*, cover.id AS cover_id,
                       (SELECT count(DISTINCT c.viewer_id) FROM contact_reveals c
                        WHERE c.listing_id = page.id) AS contact_reveals
                FROM page
                LEFT JOIN listing_photos cover
                       ON cover.listing_id = page.id AND cover.status = 'READY' AND cover.position = 0
                ORDER BY page.updated_at DESC, page.id DESC""".formatted(qualified(), keyset);
        return jdbc.sql(sql).params(params)
                .query((rs, row) -> new OwnListingRow(listing(rs, row), rs.getObject("cover_id", UUID.class),
                        rs.getLong("contact_reveals")))
                .list();
    }

    /** Listings of {@code ownerId} by status; {@code DELETED} ones are never counted. */
    Map<ListingStatus, Long> countByStatus(UUID ownerId) {
        Map<ListingStatus, Long> counts = new HashMap<>();
        jdbc.sql("""
                SELECT status, count(*) AS total FROM listings
                WHERE owner_id = :owner AND status <> 'DELETED'
                GROUP BY status""")
                .param("owner", ownerId)
                .query(rs -> {
                    counts.put(ListingStatus.valueOf(rs.getString("status")), rs.getLong("total"));
                });
        return counts;
    }

    /** {@link #COLUMNS} of the alias {@code l}. */
    private static String qualified() {
        return Arrays.stream(COLUMNS.split(","))
                .map(column -> "l." + column.strip())
                .collect(Collectors.joining(", "));
    }

    /**
     * Makes {@code EXPIRED} at most {@code limit} published listings whose expiry date is reached, skipping those
     * another transaction holds, and returns them as changed.
     */
    List<Listing> expireDue(Instant now, int limit) {
        return jdbc.sql("""
                UPDATE listings
                SET status = 'EXPIRED', version = version + 1, updated_at = :now
                WHERE id IN (SELECT id FROM listings
                             WHERE status = 'PUBLISHED' AND expires_at <= :now
                             ORDER BY expires_at
                             LIMIT :limit
                             FOR UPDATE SKIP LOCKED)
                RETURNING\s""" + COLUMNS)
                .param("now", Timestamp.from(now))
                .param("limit", limit)
                .query(this::listing)
                .list();
    }

    /**
     * Marks the reminder as written for at most {@code limit} published listings that expire after {@code now} and
     * at the latest at {@code until}, without a reminder yet, skipping those another transaction holds; returns them
     * as changed.
     */
    List<Listing> markReminderDue(Instant now, Instant until, int limit) {
        return jdbc.sql("""
                UPDATE listings
                SET reminder_sent_at = :now
                WHERE id IN (SELECT id FROM listings
                             WHERE status = 'PUBLISHED' AND reminder_sent_at IS NULL
                               AND expires_at > :now AND expires_at <= :until
                             ORDER BY expires_at
                             LIMIT :limit
                             FOR UPDATE SKIP LOCKED)
                RETURNING\s""" + COLUMNS)
                .param("now", Timestamp.from(now))
                .param("until", Timestamp.from(until))
                .param("limit", limit)
                .query(this::listing)
                .list();
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static List<ShareNetwork> shareTo(java.sql.Array array) throws SQLException {
        if (array == null) {
            return null;
        }
        return Arrays.stream((String[]) array.getArray()).map(ShareNetwork::valueOf).toList();
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private Map<String, Object> contentParams(ListingContent content) {
        // Map.of refuses null values
        Map<String, Object> params = new HashMap<>();
        params.put("category", content.categoryCode());
        params.put("title", content.title());
        params.put("description", content.description());
        params.put("priceMode", content.priceMode() == null ? null : content.priceMode().name());
        params.put("price", content.price());
        params.put("negotiable", content.negotiable());
        params.put("governorate", content.governorateCode());
        params.put("delegation", content.delegationCode());
        params.put("attributes", json.writeValueAsString(content.attributes()));
        // A PostgreSQL array literal: the names of the enum need no quoting
        params.put("shareTo", content.shareTo() == null ? null
                : content.shareTo().stream().map(Enum::name).collect(Collectors.joining(",", "{", "}")));
        params.put("shareLanguage", content.shareLanguage());
        return params;
    }

    private Listing listing(ResultSet rs, int row) throws SQLException {
        String priceMode = rs.getString("price_mode");
        long priceTnd = rs.getLong("price_tnd");
        Long price = rs.wasNull() ? null : priceTnd;
        ListingContent content = new ListingContent(rs.getString("category_code"), rs.getString("title"),
                rs.getString("description"), priceMode == null ? null : PriceMode.valueOf(priceMode),
                price, rs.getBoolean("negotiable"), rs.getString("governorate_code"),
                rs.getString("delegation_code"), json.readValue(rs.getString("attributes"), ATTRIBUTES),
                shareTo(rs.getArray("share_to")), rs.getString("share_language"));
        return new Listing(rs.getObject("id", UUID.class), rs.getObject("owner_id", UUID.class),
                ListingStatus.valueOf(rs.getString("status")), content, rs.getLong("version"),
                instant(rs, "created_at"), instant(rs, "updated_at"), instant(rs, "published_at"),
                instant(rs, "expires_at"), instant(rs, "reminder_sent_at"), instant(rs, "deleted_at"),
                rs.getString("short_ref"));
    }
}
