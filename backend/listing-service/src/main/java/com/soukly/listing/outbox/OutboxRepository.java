package com.soukly.listing.outbox;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class OutboxRepository {

    /** {@code last_error} is cut to the size of its column. */
    static final int MAX_ERROR_LENGTH = 500;

    private final JdbcClient jdbc;

    OutboxRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void insert(UUID id, UUID aggregateId, String type, String payload, Instant createdAt) {
        jdbc.sql("""
                INSERT INTO outbox_events (id, aggregate_id, type, payload, created_at)
                VALUES (:id, :aggregateId, :type, CAST(:payload AS jsonb), :createdAt)""")
                .param("id", id)
                .param("aggregateId", aggregateId)
                .param("type", type)
                .param("payload", payload)
                .param("createdAt", Timestamp.from(createdAt))
                .update();
    }

    /**
     * Locks at most {@code limit} unsent events until the end of the transaction, oldest first, skipping those another
     * relay holds ({@code FOR UPDATE SKIP LOCKED}).
     */
    List<OutboxEvent> lockUnsent(int limit) {
        return jdbc.sql("""
                SELECT id, sequence, aggregate_id, type, payload::text AS payload, attempts
                FROM outbox_events
                WHERE sent_at IS NULL
                ORDER BY sequence
                LIMIT :limit
                FOR UPDATE SKIP LOCKED""")
                .param("limit", limit)
                .query((rs, row) -> new OutboxEvent(rs.getObject("id", UUID.class), rs.getLong("sequence"),
                        rs.getObject("aggregate_id", UUID.class), rs.getString("type"), rs.getString("payload"),
                        rs.getInt("attempts")))
                .list();
    }

    /** Unsent events of these aggregates up to {@code maxSequence}, locked or not, in order of creation. */
    List<UnsentRef> unsentOf(Collection<UUID> aggregateIds, long maxSequence) {
        return jdbc.sql("""
                SELECT id, aggregate_id
                FROM outbox_events
                WHERE sent_at IS NULL AND aggregate_id IN (:aggregateIds) AND sequence <= :maxSequence
                ORDER BY sequence""")
                .param("aggregateIds", aggregateIds)
                .param("maxSequence", maxSequence)
                .query((rs, row) -> new UnsentRef(rs.getObject("id", UUID.class),
                        rs.getObject("aggregate_id", UUID.class)))
                .list();
    }

    void markSent(Collection<UUID> ids, Instant now) {
        jdbc.sql("UPDATE outbox_events SET sent_at = :now WHERE id IN (:ids)")
                .param("now", Timestamp.from(now))
                .param("ids", ids)
                .update();
    }

    void recordFailure(UUID id, String error) {
        jdbc.sql("UPDATE outbox_events SET attempts = attempts + 1, last_error = :error WHERE id = :id")
                .param("id", id)
                .param("error", error.length() > MAX_ERROR_LENGTH ? error.substring(0, MAX_ERROR_LENGTH) : error)
                .update();
    }

    int deleteSentBefore(Instant limit) {
        return jdbc.sql("DELETE FROM outbox_events WHERE sent_at < :limit")
                .param("limit", Timestamp.from(limit))
                .update();
    }

    record UnsentRef(UUID id, UUID aggregateId) {
    }
}
