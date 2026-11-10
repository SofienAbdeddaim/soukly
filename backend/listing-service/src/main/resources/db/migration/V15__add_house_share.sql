-- SOC-07: whether a listing is also posted on Soukly's own Facebook Page and Instagram account.
-- Additive only: the previous version neither reads nor writes the column, and its inserts get the default.

ALTER TABLE listings ADD COLUMN house_share boolean NOT NULL DEFAULT true;
