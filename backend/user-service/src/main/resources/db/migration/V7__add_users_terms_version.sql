-- LEG-01: version of the terms of use accepted at registration, next to terms_accepted_at.
-- Nullable and additive: accounts created before keep NULL, and the previous version, which does not know the
-- column, still inserts accounts after a rollback.
ALTER TABLE users ADD COLUMN terms_version varchar(10);
