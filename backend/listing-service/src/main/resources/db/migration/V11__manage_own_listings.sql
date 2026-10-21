-- ANN-05: the seller manages their listings: deactivation (INACTIVE), logical deletion (DELETED) purged after 30 days,
-- and the "My listings" page. Additive only: the previous version keeps working on this schema.

ALTER TABLE listings
    -- When the seller deleted the listing; the purge erases it some time later
    ADD COLUMN deleted_at timestamptz;

ALTER TABLE listings DROP CONSTRAINT listings_status_check;
ALTER TABLE listings ADD CONSTRAINT listings_status_check
    CHECK (status IN ('DRAFT', 'PUBLISHED', 'EXPIRED', 'INACTIVE', 'DELETED'));
-- A draft is erased at once, never DELETED: every deleted listing was published, and has its date of deletion
ALTER TABLE listings ADD CONSTRAINT listings_deleted_at_check CHECK ((status = 'DELETED') = (deleted_at IS NOT NULL));

-- "My listings": one status of one account, the latest changed first
CREATE INDEX listings_owner_status_updated_at_idx ON listings (owner_id, status, updated_at DESC, id DESC);
-- Daily purge of the listings deleted for more than 30 days
CREATE INDEX listings_deleted_at_idx ON listings (deleted_at) WHERE status = 'DELETED';
