-- ANN-03: events of other services already handled, so that an event delivered again (at least once delivery) has
-- no second effect. Written in the same transaction as the effect.
CREATE TABLE processed_events (
    -- eventId of the envelope (docs/events/listing-events.md)
    event_id     uuid          PRIMARY KEY,
    processed_at timestamptz   NOT NULL
);
