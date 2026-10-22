-- SOC-01: the social network accounts of the members, and the state of a connection in progress.

-- One row per member and per network. The token is encrypted with AES-256-GCM (TokenCipher): never stored, returned,
-- logged or put in an event in clear.
CREATE TABLE social_connections (
    id                uuid          PRIMARY KEY,
    user_id           uuid          NOT NULL,
    network           text          NOT NULL CHECK (network IN ('FACEBOOK')),
    -- Facebook: id of the Page
    external_id       text          NOT NULL,
    -- Facebook: app-scoped id of the person who connected the Page, to revoke the permissions on disconnection
    external_user_id  text,
    display_name      text          NOT NULL,
    picture_url       text,
    -- 12-byte nonce followed by the ciphertext and the 16-byte tag
    token_ciphertext  bytea         NOT NULL,
    key_version       smallint      NOT NULL,
    -- NEEDS_RECONNECT is set by SOC-04 when Meta refuses the token
    status            text          NOT NULL CHECK (status IN ('CONNECTED', 'NEEDS_RECONNECT')),
    connected_at      timestamptz   NOT NULL,
    updated_at        timestamptz   NOT NULL,
    CONSTRAINT social_connections_user_network_key UNIQUE (user_id, network)
);

-- OAuth state of a connection started by POST /social/{network}/connect: single use, owned by one member. Only its
-- SHA-256 hash is stored.
CREATE TABLE oauth_states (
    state_hash  bytea         PRIMARY KEY,
    user_id     uuid          NOT NULL,
    network     text          NOT NULL,
    -- Language of the page that started the connection, to go back to it
    lang        text          NOT NULL,
    expires_at  timestamptz   NOT NULL
);

CREATE INDEX oauth_states_expires_at_idx ON oauth_states (expires_at);

-- Pages offered to a member who manages several (one choice at a time per member). The list with the Page tokens is
-- encrypted like the connection tokens.
CREATE TABLE pending_page_selections (
    user_id      uuid          PRIMARY KEY,
    ciphertext   bytea         NOT NULL,
    key_version  smallint      NOT NULL,
    expires_at   timestamptz   NOT NULL
);

CREATE INDEX pending_page_selections_expires_at_idx ON pending_page_selections (expires_at);

-- Events of other services already handled (user.deleted), so that an event delivered again has no second effect.
CREATE TABLE processed_events (
    event_id     uuid          PRIMARY KEY,
    processed_at timestamptz   NOT NULL
);
