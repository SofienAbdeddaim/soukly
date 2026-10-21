-- CATA-01: public search of the PUBLISHED listings with the full text search of PostgreSQL (no external engine).
-- Additive only: the previous version ignores the new function, column and indexes.

-- Both are trusted extensions: the owner of the database (listing_svc) may create them without being superuser.
CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- The same normalization for the indexed text and for the query (mirrored by SearchText in Java):
-- lower case; Arabic without its diacritics (tashkil, superscript alef) and tatweel, with the alef variants, the
-- teh marbuta and the alef maksura unified (أ إ آ → ا, ة → ه, ى → ي); then the Latin accents removed by unaccent.
-- IMMUTABLE so that it can be used in a generated column and in indexes: unaccent itself is only STABLE because its
-- dictionary could change, so the dictionary is named explicitly (the usual way to index it).
CREATE FUNCTION soukly_normalize(input text) RETURNS text
    LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT
    RETURN public.unaccent('public.unaccent'::regdictionary,
        translate(
            regexp_replace(lower(input), '[\u064B-\u0652\u0670\u0640]', '', 'g'),
            U&'\0623\0625\0622\0629\0649',
            U&'\0627\0627\0627\0647\064A'));

-- Title (weight A) and description (weight B) with the "simple" configuration: no stemming, the listings often mix
-- languages. A generated column rather than one maintained by the application: always in step with the row, filled
-- for the existing listings by this migration, and nothing to forget in a later UPDATE.
ALTER TABLE listings ADD COLUMN search_vector tsvector GENERATED ALWAYS AS (
    setweight(to_tsvector('simple'::regconfig, coalesce(soukly_normalize(title), '')), 'A')
    || setweight(to_tsvector('simple'::regconfig, coalesce(soukly_normalize(description), '')), 'B')) STORED;

-- Only the published listings are ever searched: partial indexes stay small (drafts and expired listings are left out)
CREATE INDEX listings_search_vector_idx ON listings USING gin (search_vector) WHERE status = 'PUBLISHED';
-- Typing mistakes on the title and the brand ("peugot" finds "Peugeot")
CREATE INDEX listings_title_trgm_idx ON listings USING gin (soukly_normalize(title) gin_trgm_ops)
    WHERE status = 'PUBLISHED';
CREATE INDEX listings_brand_trgm_idx ON listings USING gin (soukly_normalize(attributes ->> 'brand') gin_trgm_ops)
    WHERE status = 'PUBLISHED';

-- Frequent filters and sorts
CREATE INDEX listings_status_published_at_idx ON listings (status, published_at DESC, id DESC);
CREATE INDEX listings_status_category_published_at_idx ON listings (status, category_code, published_at DESC);
CREATE INDEX listings_status_governorate_published_at_idx ON listings (status, governorate_code, published_at DESC);
CREATE INDEX listings_status_price_idx ON listings (status, price_tnd);
