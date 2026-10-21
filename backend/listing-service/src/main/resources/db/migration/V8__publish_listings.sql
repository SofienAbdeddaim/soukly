-- ANN-03: publication, expiry after 60 days and listing events through a transactional outbox.
-- Additive only: the previous version keeps working on this schema (it writes DRAFT listings and ignores the rest).

ALTER TABLE listings
    -- End of visibility of a PUBLISHED listing, 60 days after its publication or its last renewal
    ADD COLUMN expires_at       timestamptz,
    -- When listing.expiring-soon was written; reset by a renewal so that the next expiry gets its reminder
    ADD COLUMN reminder_sent_at timestamptz;

-- ANN-05 widens it again with INACTIVE and DELETED
ALTER TABLE listings DROP CONSTRAINT listings_status_check;
ALTER TABLE listings ADD CONSTRAINT listings_status_check CHECK (status IN ('DRAFT', 'PUBLISHED', 'EXPIRED'));
-- A listing that was published always has both dates
ALTER TABLE listings ADD CONSTRAINT listings_published_dates_check
    CHECK (status = 'DRAFT' OR (published_at IS NOT NULL AND expires_at IS NOT NULL));

-- Hourly expiry and reminder task
CREATE INDEX listings_published_expires_at_idx ON listings (expires_at) WHERE status = 'PUBLISHED';

-- Events written in the same transaction as the change they describe, then sent to Kafka by the relay
-- (docs/events/listing-events.md).
CREATE TABLE outbox_events (
    -- eventId of the envelope
    id           uuid          PRIMARY KEY,
    -- Order of creation, also inside one transaction (created_at is the same for its events)
    sequence     bigint        GENERATED ALWAYS AS IDENTITY UNIQUE,
    -- listingId: the message key
    aggregate_id uuid          NOT NULL,
    type         varchar(64)   NOT NULL,
    -- The whole envelope, sent as is
    payload      jsonb         NOT NULL,
    created_at   timestamptz   NOT NULL,
    -- Set once the broker acknowledged the event; sent events are deleted after 7 days
    sent_at      timestamptz,
    -- Failed sends, and the type of the last error (never the payload)
    attempts     integer       NOT NULL DEFAULT 0,
    last_error   varchar(500),
    CONSTRAINT outbox_events_attempts_check CHECK (attempts >= 0)
);

-- The relay reads the unsent events in order of creation
CREATE INDEX outbox_events_unsent_idx ON outbox_events (sequence) WHERE sent_at IS NULL;
CREATE INDEX outbox_events_unsent_aggregate_idx ON outbox_events (aggregate_id, sequence) WHERE sent_at IS NULL;
-- Purge of the sent events
CREATE INDEX outbox_events_sent_at_idx ON outbox_events (sent_at) WHERE sent_at IS NOT NULL;
