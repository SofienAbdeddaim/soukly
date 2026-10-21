-- CATA-03: every time a member is shown the phone number of a seller. Used by the daily limit (distinct listings per
-- member over 24 hours) and kept for future statistics. Never the number itself.

CREATE TABLE contact_reveals (
    id          bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    listing_id  uuid        NOT NULL REFERENCES listings (id) ON DELETE CASCADE,
    -- sub of the access token of the member who asked
    viewer_id   uuid        NOT NULL,
    revealed_at timestamptz NOT NULL
);

CREATE INDEX contact_reveals_viewer_id_revealed_at_idx ON contact_reveals (viewer_id, revealed_at);
CREATE INDEX contact_reveals_listing_id_idx ON contact_reveals (listing_id);
