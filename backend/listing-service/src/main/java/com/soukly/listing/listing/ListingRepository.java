package com.soukly.listing.listing;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
            reminder_sent_at""";

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

    void insert(Listing listing) {
        ListingContent content = listing.content();
        jdbc.sql("""
                INSERT INTO listings (id, owner_id, status, category_code, title, description, price_mode, price_tnd,
                        negotiable, governorate_code, delegation_code, attributes, version, created_at, updated_at,
                        published_at)
                VALUES (:id, :owner, :status, :category, :title, :description, :priceMode, :price, :negotiable,
                        :governorate, :delegation, CAST(:attributes AS jsonb), :version, :createdAt, :updatedAt,
                        :publishedAt)""")
                .param("id", listing.id())
                .param("owner", listing.ownerId())
                .param("status", listing.status().name())
                .params(contentParams(content))
                .param("version", listing.version())
                .param("createdAt", Timestamp.from(listing.createdAt()))
                .param("updatedAt", Timestamp.from(listing.updatedAt()))
                .param("publishedAt", timestamp(listing.publishedAt()))
                .update();
    }

    /** Writes the content and the version of a listing locked by {@link #findForUpdate(UUID)}. */
    void update(Listing listing) {
        jdbc.sql("""
                UPDATE listings
                SET category_code = :category, title = :title, description = :description, price_mode = :priceMode,
                    price_tnd = :price, negotiable = :negotiable, governorate_code = :governorate,
                    delegation_code = :delegation, attributes = CAST(:attributes AS jsonb), version = :version,
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
                    expires_at = :expiresAt, reminder_sent_at = :reminderSentAt
                WHERE id = :id""")
                .param("id", listing.id())
                .param("status", listing.status().name())
                .param("version", listing.version())
                .param("updatedAt", Timestamp.from(listing.updatedAt()))
                .param("publishedAt", timestamp(listing.publishedAt()))
                .param("expiresAt", timestamp(listing.expiresAt()))
                .param("reminderSentAt", timestamp(listing.reminderSentAt()))
                .update();
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
        return params;
    }

    private Listing listing(ResultSet rs, int row) throws SQLException {
        String priceMode = rs.getString("price_mode");
        long priceTnd = rs.getLong("price_tnd");
        Long price = rs.wasNull() ? null : priceTnd;
        ListingContent content = new ListingContent(rs.getString("category_code"), rs.getString("title"),
                rs.getString("description"), priceMode == null ? null : PriceMode.valueOf(priceMode),
                price, rs.getBoolean("negotiable"), rs.getString("governorate_code"),
                rs.getString("delegation_code"), json.readValue(rs.getString("attributes"), ATTRIBUTES));
        return new Listing(rs.getObject("id", UUID.class), rs.getObject("owner_id", UUID.class),
                ListingStatus.valueOf(rs.getString("status")), content, rs.getLong("version"),
                instant(rs, "created_at"), instant(rs, "updated_at"), instant(rs, "published_at"),
                instant(rs, "expires_at"), instant(rs, "reminder_sent_at"));
    }
}
