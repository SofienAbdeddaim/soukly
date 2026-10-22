-- AUTH-04: tokens of the password reset links.

CREATE TABLE password_reset_tokens (
    id             uuid        PRIMARY KEY,
    user_id        uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- SHA-256 of the token, hexadecimal; the token itself is never stored
    token_hash     varchar(64) NOT NULL,
    expires_at     timestamptz NOT NULL,
    used_at        timestamptz,
    -- Set when a newer link of the account replaces this one, or when another link is used
    invalidated_at timestamptz,
    created_at     timestamptz NOT NULL,
    CONSTRAINT password_reset_tokens_token_hash_key UNIQUE (token_hash)
);

-- Hourly limit of the requests per account
CREATE INDEX password_reset_tokens_user_id_created_at_idx ON password_reset_tokens (user_id, created_at);
