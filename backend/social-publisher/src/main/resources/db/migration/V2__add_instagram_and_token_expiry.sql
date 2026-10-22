-- SOC-02: Instagram professional accounts, and the expiry of the tokens that expire (Instagram long-lived tokens,
-- refreshed daily). Additive only: the previous version never writes INSTAGRAM and ignores the new column.

ALTER TABLE social_connections DROP CONSTRAINT social_connections_network_check;
ALTER TABLE social_connections
    ADD CONSTRAINT social_connections_network_check CHECK (network IN ('FACEBOOK', 'INSTAGRAM'));

-- NULL: the token does not expire on its own (a Facebook Page token read with a long-lived user token)
ALTER TABLE social_connections ADD COLUMN token_expires_at timestamptz;

-- The daily refresh reads the connected tokens that expire soon
CREATE INDEX social_connections_token_expires_at_idx ON social_connections (network, token_expires_at)
    WHERE status = 'CONNECTED' AND token_expires_at IS NOT NULL;
