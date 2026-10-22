-- AUTH-05: events of other services already handled (user.deleted), so that an event delivered again (at least once
-- delivery) has no second effect. Written in the same transaction as the effect, like in user-service (ANN-03).
CREATE TABLE processed_events (
    -- eventId of the envelope (docs/events/user-events.md)
    event_id     uuid          PRIMARY KEY,
    processed_at timestamptz   NOT NULL
);
