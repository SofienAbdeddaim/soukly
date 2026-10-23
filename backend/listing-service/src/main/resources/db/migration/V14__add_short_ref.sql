-- SOC-05: short reference of a listing, written in the Instagram captions ("soukly.tn/r/8F3C2K9A") where links are not
-- clickable, and resolved by GET /listings/by-ref/{ref}. 8 characters of an alphabet without the ambiguous 0, O, 1, I.
-- Additive only: the previous version neither reads nor writes the column, and leaves it NULL on the listings it
-- creates; this version gives one to such a listing when it is published (ListingRepository.ensureShortRef).

ALTER TABLE listings ADD COLUMN short_ref text;

ALTER TABLE listings ADD CONSTRAINT listings_short_ref_check CHECK (short_ref ~ '^[2-9A-HJ-NP-Z]{8}$');
ALTER TABLE listings ADD CONSTRAINT listings_short_ref_key UNIQUE (short_ref);

-- Existing listings, one by one: a collision (about one in a thousand billion) draws another reference.
DO $$
DECLARE
    alphabet constant text := '23456789ABCDEFGHJKLMNPQRSTUVWXYZ';
    listing_id uuid;
    candidate text;
BEGIN
    FOR listing_id IN SELECT id FROM listings WHERE short_ref IS NULL LOOP
        LOOP
            SELECT string_agg(substr(alphabet, 1 + floor(random() * length(alphabet))::int, 1), '')
                INTO candidate FROM generate_series(1, 8);
            BEGIN
                UPDATE listings SET short_ref = candidate WHERE id = listing_id;
                EXIT;
            EXCEPTION WHEN unique_violation THEN
                -- drawn again
            END;
        END LOOP;
    END LOOP;
END
$$;
