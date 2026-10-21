-- ANN-02: photos of a listing. The browser uploads the original straight to S3 (incoming/{listingId}/{id}); the
-- service checks it, converts it to WebP (listings/{listingId}/{id}/1600.webp and 400.webp) and deletes the original.

CREATE TABLE listing_photos (
    id             uuid          PRIMARY KEY,
    listing_id     uuid          NOT NULL REFERENCES listings (id),
    -- PENDING_UPLOAD until the conversion, then READY or REJECTED
    status         varchar(16)   NOT NULL,
    -- Order of the READY photos, 0 is the cover; null otherwise. Kept compact (0..n-1) by the service, which locks
    -- the listing row for every change, so no unique index is needed (it would refuse a reordering in one statement).
    position       integer,
    -- Size of the 1600 version, after the EXIF orientation is applied
    width          integer,
    height         integer,
    -- Size in bytes of the uploaded original
    bytes          bigint,
    -- Stable error code of a REJECTED photo (photo.too-large...), answered again by a new completion
    rejection_code varchar(64),
    created_at     timestamptz   NOT NULL,
    updated_at     timestamptz   NOT NULL,
    CONSTRAINT listing_photos_status_check CHECK (status IN ('PENDING_UPLOAD', 'READY', 'REJECTED')),
    CONSTRAINT listing_photos_ready_check CHECK (
        (status = 'READY') = (position IS NOT NULL AND width IS NOT NULL AND height IS NOT NULL)),
    CONSTRAINT listing_photos_position_check CHECK (position >= 0),
    CONSTRAINT listing_photos_rejection_check CHECK ((status = 'REJECTED') = (rejection_code IS NOT NULL))
);

CREATE INDEX listing_photos_listing_id_status_idx ON listing_photos (listing_id, status);
-- Hourly cleanup of the uploads that were never completed
CREATE INDEX listing_photos_pending_created_at_idx ON listing_photos (created_at) WHERE status = 'PENDING_UPLOAD';
