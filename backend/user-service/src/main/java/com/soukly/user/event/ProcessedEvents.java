package com.soukly.user.event;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Deduplication of the consumed events (listing and social events) by {@code eventId}. */
@Repository
public class ProcessedEvents {

    private final JdbcTemplate jdbc;

    ProcessedEvents(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Records the event in the current transaction.
     *
     * @return {@code false} when it was processed already: its effect must not happen again
     */
    public boolean markProcessed(UUID eventId, Instant now) {
        return jdbc.update("INSERT INTO processed_events (event_id, processed_at) VALUES (?, ?) ON CONFLICT DO NOTHING",
                eventId, Timestamp.from(now)) == 1;
    }
}
