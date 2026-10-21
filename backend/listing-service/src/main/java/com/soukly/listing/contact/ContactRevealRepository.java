package com.soukly.listing.contact;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class ContactRevealRepository {

    /** First key of the advisory locks of the contact reveals, apart from those of the drafts. */
    private static final int LOCK_SPACE = 3;

    private final JdbcClient jdbc;

    ContactRevealRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Serializes the reveals of one member until the end of the transaction, so the limit holds in parallel. */
    void lockViewer(UUID viewerId) {
        jdbc.sql("SELECT pg_advisory_xact_lock(:space, hashtext(:viewer))")
                .param("space", LOCK_SPACE)
                .param("viewer", viewerId.toString())
                .query((rs, row) -> 1)
                .single();
    }

    boolean revealedSince(UUID viewerId, UUID listingId, Instant since) {
        return jdbc.sql("""
                SELECT EXISTS (SELECT 1 FROM contact_reveals
                               WHERE viewer_id = :viewer AND listing_id = :listing AND revealed_at > :since)""")
                .param("viewer", viewerId)
                .param("listing", listingId)
                .param("since", Timestamp.from(since))
                .query(Boolean.class)
                .single();
    }

    long countListingsSince(UUID viewerId, Instant since) {
        return jdbc.sql("""
                SELECT count(DISTINCT listing_id) FROM contact_reveals
                WHERE viewer_id = :viewer AND revealed_at > :since""")
                .param("viewer", viewerId)
                .param("since", Timestamp.from(since))
                .query(Long.class)
                .single();
    }

    /**
     * The listing that leaves the window first: the oldest of the last reveals of each listing.
     */
    Optional<Instant> firstToLeaveSince(UUID viewerId, Instant since) {
        return jdbc.sql("""
                SELECT min(last_revealed_at) FROM (
                    SELECT max(revealed_at) AS last_revealed_at FROM contact_reveals
                    WHERE viewer_id = :viewer AND revealed_at > :since
                    GROUP BY listing_id) AS listings""")
                .param("viewer", viewerId)
                .param("since", Timestamp.from(since))
                .query(Timestamp.class)
                .optional()
                .map(Timestamp::toInstant);
    }

    void insert(UUID listingId, UUID viewerId, Instant revealedAt) {
        jdbc.sql("INSERT INTO contact_reveals (listing_id, viewer_id, revealed_at) VALUES (:listing, :viewer, :at)")
                .param("listing", listingId)
                .param("viewer", viewerId)
                .param("at", Timestamp.from(revealedAt))
                .update();
    }
}
