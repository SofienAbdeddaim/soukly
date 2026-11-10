-- SOC-07: the posts of the listings on Soukly's own Facebook Page and Instagram account, one row per listing and per
-- network, next to the posts on the seller's accounts (social_posts). A table of its own keeps the unique constraint
-- of social_posts as the previous version uses it. Additive only: the previous version does not know the table.
CREATE TABLE house_posts (
    id                   uuid          PRIMARY KEY,
    listing_id           uuid          NOT NULL,
    -- The seller (ownerId of the listing events), for the removal of their posts when their account is deleted
    user_id              uuid          NOT NULL,
    network              text          NOT NULL CHECK (network IN ('FACEBOOK', 'INSTAGRAM')),
    status               text          NOT NULL
        CHECK (status IN ('PENDING', 'PUBLISHED', 'SKIPPED', 'FAILED', 'CANCELLED', 'DELETED')),
    -- PostContent from listing.published: no personal data, the event carries none
    content              jsonb         NOT NULL,
    -- Account the post was published on (Facebook: id of Soukly's Page)
    external_account_id  text,
    external_post_id     text,
    permalink            text,
    attempts             integer       NOT NULL DEFAULT 0,
    -- Same columns as social_posts, so both tables share one repository; no "Try again" on these posts
    manual_retries       integer       NOT NULL DEFAULT 0 CHECK (manual_retries >= 0),
    -- Why the row is SKIPPED or FAILED (not-connected, needs-reconnect, stale, meta-{code}...)
    last_error_code      text,
    next_attempt_at      timestamptz,
    request_id           text,
    created_at           timestamptz   NOT NULL,
    updated_at           timestamptz   NOT NULL,
    published_at         timestamptz,
    deleted_at           timestamptz,
    CONSTRAINT house_posts_listing_network_key UNIQUE (listing_id, network)
);

CREATE INDEX house_posts_due_idx ON house_posts (network, next_attempt_at) WHERE status = 'PENDING';
CREATE INDEX house_posts_user_id_idx ON house_posts (user_id);
