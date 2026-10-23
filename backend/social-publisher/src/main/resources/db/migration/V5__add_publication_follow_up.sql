-- SOC-06: what the seller sees and does about the posts, and the email "connect again". Additive only: the previous
-- version ignores the new columns and the new table.

-- Manual "Try again" of a FAILED post, 3 at most per row
ALTER TABLE social_posts ADD COLUMN manual_retries integer NOT NULL DEFAULT 0;
ALTER TABLE social_posts ADD CONSTRAINT social_posts_manual_retries_check CHECK (manual_retries >= 0);

-- Last time the member was told by email to connect this account again: one email per connection and per 24 hours
ALTER TABLE social_connections ADD COLUMN reconnect_notified_at timestamptz;

-- Blocked posts of a member and network, for the relaunch after a reconnection
CREATE INDEX social_posts_user_network_idx ON social_posts (user_id, network, created_at)
    WHERE status IN ('FAILED', 'SKIPPED');

-- Social events (social.connection.needs-reconnect), same outbox as listing-service and user-service
CREATE TABLE outbox_events (
    -- eventId of the envelope
    id           uuid          PRIMARY KEY,
    -- Order of creation, also inside one transaction (created_at is the same for its events)
    sequence     bigint        GENERATED ALWAYS AS IDENTITY UNIQUE,
    -- userId: the message key
    aggregate_id uuid          NOT NULL,
    type         varchar(64)   NOT NULL,
    -- The whole envelope, sent as is
    payload      jsonb         NOT NULL,
    created_at   timestamptz   NOT NULL,
    -- Set once the broker acknowledged the event; sent events are deleted after 7 days
    sent_at      timestamptz,
    attempts     integer       NOT NULL DEFAULT 0,
    -- Type and message of the last send error, never the payload
    last_error   varchar(500),
    CONSTRAINT outbox_events_attempts_check CHECK (attempts >= 0)
);

-- The relay reads the unsent events in order of creation
CREATE INDEX outbox_events_unsent_idx ON outbox_events (sequence) WHERE sent_at IS NULL;
CREATE INDEX outbox_events_unsent_aggregate_idx ON outbox_events (aggregate_id, sequence) WHERE sent_at IS NULL;
-- Purge of the sent events
CREATE INDEX outbox_events_sent_at_idx ON outbox_events (sent_at) WHERE sent_at IS NOT NULL;
