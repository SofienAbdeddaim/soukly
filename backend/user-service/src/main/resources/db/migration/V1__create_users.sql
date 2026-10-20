-- AUTH-01: accounts and email verification tokens.

CREATE TABLE users (
    id                 uuid         PRIMARY KEY,
    -- Normalized: trimmed and lower case
    email              varchar(254) NOT NULL,
    password_hash      varchar(255) NOT NULL,
    display_name       varchar(50)  NOT NULL,
    -- E.164, for example +21620123456; not unique (several accounts may share a number)
    phone_e164         varchar(16)  NOT NULL,
    preferred_language varchar(2)   NOT NULL,
    status             varchar(32)  NOT NULL,
    terms_accepted_at  timestamptz  NOT NULL,
    email_verified_at  timestamptz,
    created_at         timestamptz  NOT NULL,
    updated_at         timestamptz  NOT NULL,
    CONSTRAINT users_email_key UNIQUE (email),
    CONSTRAINT users_preferred_language_check CHECK (preferred_language IN ('fr', 'ar', 'en')),
    CONSTRAINT users_status_check CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE'))
);

CREATE TABLE email_verification_tokens (
    id         uuid        PRIMARY KEY,
    user_id    uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- SHA-256 of the token, hexadecimal; the token itself is never stored
    token_hash varchar(64) NOT NULL,
    -- REGISTRATION for the first email, RESEND afterwards (counted by the resend limit)
    reason     varchar(16) NOT NULL,
    expires_at timestamptz NOT NULL,
    used_at    timestamptz,
    created_at timestamptz NOT NULL,
    CONSTRAINT email_verification_tokens_token_hash_key UNIQUE (token_hash),
    CONSTRAINT email_verification_tokens_reason_check CHECK (reason IN ('REGISTRATION', 'RESEND'))
);

CREATE INDEX email_verification_tokens_user_id_created_at_idx
    ON email_verification_tokens (user_id, created_at);
