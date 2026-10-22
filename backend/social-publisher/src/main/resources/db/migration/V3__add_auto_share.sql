-- SOC-03: the preference of the member for each connected network, "tick by default for every new listing". Additive
-- only: the previous version does not write the column, and every row it inserts takes the default.

-- true when the connection is created; a new connection of an account already connected keeps the preference
ALTER TABLE social_connections ADD COLUMN auto_share boolean NOT NULL DEFAULT true;
