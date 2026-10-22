-- SOC-03: where the seller shares a listing when it is published, and in which language the post texts are written.
-- Additive only: the previous version does not read nor write these columns, and its listings keep NULL (no choice).

ALTER TABLE listings
    -- NULL until the seller chose on the preview; an empty array is the choice "no network"
    ADD COLUMN share_to text[],
    ADD COLUMN share_language text;

ALTER TABLE listings ADD CONSTRAINT listings_share_to_check
    CHECK (share_to <@ ARRAY['FACEBOOK', 'INSTAGRAM']::text[]);
ALTER TABLE listings ADD CONSTRAINT listings_share_language_check
    CHECK (share_language IN ('fr', 'ar', 'en'));
