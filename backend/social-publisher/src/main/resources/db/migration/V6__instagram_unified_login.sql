-- Meta Unified login (2026-11): Instagram is connected with Facebook Login for Business, whose system-user tokens do
-- not expire (token_expires_at NULL) and work on graph.facebook.com only. The tokens of Instagram Login (SOC-02), the
-- only ones with an expiry, are not accepted there: those accounts are to reconnect. Data only, no schema change, so
-- the previous version still runs on this database.
UPDATE social_connections
SET status = 'NEEDS_RECONNECT', updated_at = now()
WHERE network = 'INSTAGRAM' AND token_expires_at IS NOT NULL AND status = 'CONNECTED';
