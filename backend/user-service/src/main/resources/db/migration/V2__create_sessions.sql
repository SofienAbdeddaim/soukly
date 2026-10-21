-- AUTH-02: refresh tokens (one family per signed-in device) and login failure throttling.

CREATE TABLE refresh_tokens (
    id         uuid         PRIMARY KEY,
    user_id    uuid         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- One family = one session on one device; every rotation stays in the family
    family_id  uuid         NOT NULL,
    -- SHA-256 of the token, hexadecimal; the token itself is never stored
    token_hash varchar(64)  NOT NULL,
    expires_at timestamptz  NOT NULL,
    -- Set when the token is rotated: presenting it again revokes the whole family
    used_at    timestamptz,
    revoked_at timestamptz,
    created_at timestamptz  NOT NULL,
    -- Truncated to 255 characters, for a future list of the signed-in devices
    user_agent varchar(255),
    CONSTRAINT refresh_tokens_token_hash_key UNIQUE (token_hash)
);

CREATE INDEX refresh_tokens_family_id_idx ON refresh_tokens (family_id);
CREATE INDEX refresh_tokens_user_id_idx ON refresh_tokens (user_id);

-- Keyed by the SHA-256 of the normalized email, known or not, so that throttling never reveals
-- whether an account exists.
CREATE TABLE login_throttle (
    email_hash     varchar(64) PRIMARY KEY,
    failed_count   integer     NOT NULL,
    last_failed_at timestamptz NOT NULL,
    -- No attempt is checked before this instant; null while the free attempts are not used up
    retry_after    timestamptz
);
