-- SOC-04: the posts of the listings on the social networks, one row per listing and per network. Additive only: the
-- previous version does not know the table.
CREATE TABLE social_posts (
    id                   uuid          PRIMARY KEY,
    listing_id           uuid          NOT NULL,
    -- The seller (ownerId of the listing events)
    user_id              uuid          NOT NULL,
    network              text          NOT NULL CHECK (network IN ('FACEBOOK', 'INSTAGRAM')),
    status               text          NOT NULL
        CHECK (status IN ('PENDING', 'PUBLISHED', 'SKIPPED', 'FAILED', 'CANCELLED', 'DELETED')),
    -- What the post is made of, from listing.published (PostContent): the publication is decoupled from Kafka and
    -- builds the text when it runs. No personal data: the event carries none.
    content              jsonb         NOT NULL,
    -- Account the post was published on (Facebook: id of the Page), to remove it from the same one
    external_account_id  text,
    external_post_id     text,
    permalink            text,
    -- Failed attempts so far
    attempts             integer       NOT NULL DEFAULT 0,
    -- Why the row is SKIPPED or FAILED (not-connected, needs-reconnect, permission-missing, meta-{code}...), or why
    -- a PUBLISHED post could not be removed
    last_error_code      text,
    -- PENDING: when the post is due (first attempt or next one)
    next_attempt_at      timestamptz,
    -- X-Request-Id of the Kafka message, when it had one, for the logs
    request_id           text,
    created_at           timestamptz   NOT NULL,
    updated_at           timestamptz   NOT NULL,
    published_at         timestamptz,
    deleted_at           timestamptz,
    -- Never two posts of the same listing on the same network
    CONSTRAINT social_posts_listing_network_key UNIQUE (listing_id, network)
);

-- The publishing task reads the PENDING posts that are due, oldest first
CREATE INDEX social_posts_due_idx ON social_posts (network, next_attempt_at) WHERE status = 'PENDING';

-- Posts of a member (account deletion, My listings in SOC-06)
CREATE INDEX social_posts_user_id_idx ON social_posts (user_id);
