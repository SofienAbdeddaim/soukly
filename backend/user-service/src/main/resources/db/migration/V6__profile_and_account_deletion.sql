-- AUTH-05: profile (change of email), deletion of the account and the user events outbox.
-- Additive only: the previous version keeps working on this schema (it never reads the new columns, and a DELETED
-- account has no email, so its login and its registration checks never find it). After a rollback, the only reading
-- of a DELETED row by id (the internal seller endpoints of CATA-03) fails with a 500 instead of a 404, which
-- listing-service already answers like a seller it cannot reach.

ALTER TABLE users
    -- New address waiting for its confirmation link; the login still uses email until then
    ADD COLUMN pending_email varchar(254),
    -- Set when the account is deleted; the row is erased for good by the purge, 30 days later
    ADD COLUMN deleted_at    timestamptz,
    -- The address of a deleted account, kept until the purge (fraud handling); email is NULL so that the address can
    -- be registered again at once
    ADD COLUMN deleted_email varchar(254);

ALTER TABLE users ALTER COLUMN email DROP NOT NULL;
ALTER TABLE users ADD CONSTRAINT users_email_check CHECK (email IS NOT NULL OR status = 'DELETED');

ALTER TABLE users DROP CONSTRAINT users_status_check;
ALTER TABLE users ADD CONSTRAINT users_status_check
    CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'DELETED'));
ALTER TABLE users ADD CONSTRAINT users_deleted_check CHECK ((status = 'DELETED') = (deleted_at IS NOT NULL));

-- Purge of the deleted accounts
CREATE INDEX users_deleted_at_idx ON users (deleted_at) WHERE deleted_at IS NOT NULL;

-- Links of the email changes, same shape as email_verification_tokens
CREATE TABLE email_change_tokens (
    id             uuid         PRIMARY KEY,
    user_id        uuid         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- SHA-256 of the token, hexadecimal; the token itself is never stored
    token_hash     varchar(64)  NOT NULL,
    -- Normalized address the link confirms
    new_email      varchar(254) NOT NULL,
    expires_at     timestamptz  NOT NULL,
    used_at        timestamptz,
    -- Set when a newer request replaces this one, when the request is cancelled or when the account is deleted
    invalidated_at timestamptz,
    created_at     timestamptz  NOT NULL,
    CONSTRAINT email_change_tokens_token_hash_key UNIQUE (token_hash)
);

-- Hourly limit of the requests per account
CREATE INDEX email_change_tokens_user_id_created_at_idx ON email_change_tokens (user_id, created_at);

-- User events (docs/events/user-events.md), same shape as the outbox of listing-service (ANN-03)
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
