package com.soukly.listing.photo;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Photos of the listings. Every change is made while the listing row is locked ({@code ListingService}), so the
 * count of photos and the positions never interleave between two requests.
 */
@Repository
class PhotoRepository {

    private static final String COLUMNS = """
            id, listing_id, status, position, width, height, bytes, rejection_code, created_at, updated_at""";

    private final JdbcClient jdbc;

    PhotoRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<ListingPhoto> find(UUID listingId, UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM listing_photos WHERE id = :id AND listing_id = :listing")
                .param("id", id)
                .param("listing", listingId)
                .query(PhotoRepository::photo)
                .optional();
    }

    /** READY photos, cover first. */
    List<ListingPhoto> ready(UUID listingId) {
        return jdbc.sql("SELECT " + COLUMNS
                + " FROM listing_photos WHERE listing_id = :listing AND status = 'READY' ORDER BY position")
                .param("listing", listingId)
                .query(PhotoRepository::photo)
                .list();
    }

    List<ListingPhoto> all(UUID listingId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM listing_photos WHERE listing_id = :listing")
                .param("listing", listingId)
                .query(PhotoRepository::photo)
                .list();
    }

    /** Photos counted by the limit: READY and uploads in progress. */
    int countActive(UUID listingId) {
        return jdbc.sql("""
                SELECT count(*) FROM listing_photos
                WHERE listing_id = :listing AND status IN ('READY', 'PENDING_UPLOAD')""")
                .param("listing", listingId)
                .query(Integer.class)
                .single();
    }

    List<ListingPhoto> pendingCreatedBefore(Instant before) {
        return jdbc.sql("SELECT " + COLUMNS
                + " FROM listing_photos WHERE status = 'PENDING_UPLOAD' AND created_at < :before ORDER BY created_at")
                .param("before", Timestamp.from(before))
                .query(PhotoRepository::photo)
                .list();
    }

    void insertPending(UUID id, UUID listingId, long declaredBytes, Instant now) {
        jdbc.sql("""
                INSERT INTO listing_photos (id, listing_id, status, bytes, created_at, updated_at)
                VALUES (:id, :listing, 'PENDING_UPLOAD', :bytes, :now, :now)""")
                .param("id", id)
                .param("listing", listingId)
                .param("bytes", declaredBytes)
                .param("now", Timestamp.from(now))
                .update();
    }

    /** Last position: after every READY photo of the listing. */
    void markReady(UUID id, int width, int height, long bytes, Instant now) {
        jdbc.sql("""
                UPDATE listing_photos
                SET status = 'READY', width = :width, height = :height, bytes = :bytes, updated_at = :now,
                    position = (SELECT count(*) FROM listing_photos other
                                WHERE other.listing_id = listing_photos.listing_id AND other.status = 'READY')
                WHERE id = :id AND status = 'PENDING_UPLOAD'""")
                .param("id", id)
                .param("width", width)
                .param("height", height)
                .param("bytes", bytes)
                .param("now", Timestamp.from(now))
                .update();
    }

    /** @return false when the photo is no longer {@code PENDING_UPLOAD} */
    boolean markRejected(UUID id, PhotoRejection rejection, Instant now) {
        return jdbc.sql("""
                UPDATE listing_photos SET status = 'REJECTED', rejection_code = :code, updated_at = :now
                WHERE id = :id AND status = 'PENDING_UPLOAD'""")
                .param("id", id)
                .param("code", rejection.code())
                .param("now", Timestamp.from(now))
                .update() == 1;
    }

    void setPosition(UUID id, int position, Instant now) {
        jdbc.sql("UPDATE listing_photos SET position = :position, updated_at = :now WHERE id = :id")
                .param("id", id)
                .param("position", position)
                .param("now", Timestamp.from(now))
                .update();
    }

    /** Positions of the READY photos back to 0..n-1, in their current order. */
    void compact(UUID listingId, Instant now) {
        jdbc.sql("""
                UPDATE listing_photos photo SET position = ranked.rank - 1, updated_at = :now
                FROM (SELECT id, row_number() OVER (ORDER BY position) AS rank
                      FROM listing_photos WHERE listing_id = :listing AND status = 'READY') ranked
                WHERE photo.id = ranked.id AND photo.position <> ranked.rank - 1""")
                .param("listing", listingId)
                .param("now", Timestamp.from(now))
                .update();
    }

    void delete(UUID id) {
        jdbc.sql("DELETE FROM listing_photos WHERE id = :id").param("id", id).update();
    }

    void deleteAll(UUID listingId) {
        jdbc.sql("DELETE FROM listing_photos WHERE listing_id = :listing").param("listing", listingId).update();
    }

    private static ListingPhoto photo(ResultSet rs, int row) throws SQLException {
        return new ListingPhoto(rs.getObject("id", UUID.class), rs.getObject("listing_id", UUID.class),
                PhotoStatus.valueOf(rs.getString("status")), rs.getObject("position", Integer.class),
                rs.getObject("width", Integer.class), rs.getObject("height", Integer.class), rs.getLong("bytes"),
                rs.getString("rejection_code"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }
}
