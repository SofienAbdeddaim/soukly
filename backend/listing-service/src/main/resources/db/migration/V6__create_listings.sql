-- ANN-01: listings, created as drafts and completed step by step. A draft is never visible to the public.
-- The phone number, email and name of the seller are never copied here: they are read from the account.

CREATE TABLE listings (
    id               uuid          PRIMARY KEY,
    -- sub of the access token (user-service account id); no foreign key, the account lives in another database
    owner_id         uuid          NOT NULL,
    -- ANN-03 and ANN-05 widen listings_status_check with PUBLISHED and INACTIVE
    status           varchar(16)   NOT NULL,
    category_code    varchar(64)   NOT NULL REFERENCES categories (code),
    -- Every field below may be empty on a draft; completeness is only required at publication (ANN-03)
    title            varchar(80),
    -- Plain text, never interpreted as HTML
    description      varchar(4000),
    price_mode       varchar(16),
    -- Whole dinars (TND), never a decimal
    price_tnd        bigint,
    negotiable       boolean       NOT NULL DEFAULT false,
    governorate_code varchar(64)   REFERENCES governorates (code),
    delegation_code  varchar(128)  REFERENCES delegations (code),
    -- Specific attributes of the subcategory, validated against category_attributes: {"year": 2019, ...}
    attributes       jsonb         NOT NULL DEFAULT '{}',
    -- Optimistic locking: sent as ETag, expected in If-Match
    version          bigint        NOT NULL,
    created_at       timestamptz   NOT NULL,
    updated_at       timestamptz   NOT NULL,
    published_at     timestamptz,
    CONSTRAINT listings_status_check CHECK (status IN ('DRAFT')),
    CONSTRAINT listings_price_mode_check CHECK (price_mode IN ('FIXED', 'ON_QUOTE')),
    CONSTRAINT listings_price_tnd_check CHECK (price_tnd BETWEEN 1 AND 100000000),
    CONSTRAINT listings_price_on_quote_check CHECK (NOT (price_mode = 'ON_QUOTE' AND price_tnd IS NOT NULL)),
    CONSTRAINT listings_attributes_check CHECK (jsonb_typeof(attributes) = 'object'),
    CONSTRAINT listings_version_check CHECK (version >= 1)
);

CREATE INDEX listings_owner_id_status_idx ON listings (owner_id, status);
