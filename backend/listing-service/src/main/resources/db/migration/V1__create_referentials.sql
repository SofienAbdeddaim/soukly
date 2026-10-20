-- CAT-01: read-only referentials (categories, specific attributes, governorates and delegations).
-- Codes are stable and final: never change a code once released, fix a label in a new migration.

CREATE TABLE categories (
    -- Family: "vehicles"; subcategory: "<family>.<subcategory>", for example "vehicles.cars"
    code        varchar(64)  PRIMARY KEY,
    -- NULL for a family; a listing always belongs to a subcategory (exactly two levels)
    parent_code varchar(64)  REFERENCES categories (code),
    label_fr    varchar(100) NOT NULL,
    label_ar    varchar(100) NOT NULL,
    label_en    varchar(100) NOT NULL,
    sort_order  integer      NOT NULL,
    active      boolean      NOT NULL DEFAULT true,
    CONSTRAINT categories_code_check CHECK (code ~ '^[a-z0-9]+(-[a-z0-9]+)*(\.[a-z0-9]+(-[a-z0-9]+)*)?$'),
    CONSTRAINT categories_level_check CHECK (
        (parent_code IS NULL AND position('.' IN code) = 0)
        OR (parent_code IS NOT NULL AND code LIKE parent_code || '.%')),
    CONSTRAINT categories_labels_check CHECK (label_fr <> '' AND label_ar <> '' AND label_en <> '')
);

CREATE INDEX categories_parent_code_idx ON categories (parent_code);

CREATE TABLE attribute_definitions (
    code        varchar(64)  PRIMARY KEY,
    type        varchar(16)  NOT NULL,
    -- Default for every category; category_attributes.required may override it
    required    boolean      NOT NULL,
    -- INTEGER bounds, inclusive
    min_value   integer,
    max_value   integer,
    -- INTEGER only: the maximum is the current year plus this offset, computed at validation time (year: 1)
    max_value_current_year_offset integer,
    -- TEXT bounds, in characters after trimming
    min_length  integer,
    max_length  integer,
    filterable  boolean      NOT NULL,
    label_fr    varchar(100) NOT NULL,
    label_ar    varchar(100) NOT NULL,
    label_en    varchar(100) NOT NULL,
    sort_order  integer      NOT NULL,
    CONSTRAINT attribute_definitions_code_check CHECK (code ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT attribute_definitions_type_check CHECK (type IN ('TEXT', 'INTEGER', 'ENUM', 'BOOLEAN')),
    CONSTRAINT attribute_definitions_bounds_check CHECK (
        (type = 'INTEGER' OR (min_value IS NULL AND max_value IS NULL AND max_value_current_year_offset IS NULL))
        AND (type = 'TEXT' OR (min_length IS NULL AND max_length IS NULL))
        AND NOT (max_value IS NOT NULL AND max_value_current_year_offset IS NOT NULL)),
    CONSTRAINT attribute_definitions_labels_check CHECK (label_fr <> '' AND label_ar <> '' AND label_en <> '')
);

CREATE TABLE attribute_enum_values (
    attribute_code varchar(64)  NOT NULL REFERENCES attribute_definitions (code),
    code           varchar(64)  NOT NULL,
    label_fr       varchar(100) NOT NULL,
    label_ar       varchar(100) NOT NULL,
    label_en       varchar(100) NOT NULL,
    sort_order     integer      NOT NULL,
    PRIMARY KEY (attribute_code, code),
    CONSTRAINT attribute_enum_values_code_check CHECK (code ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT attribute_enum_values_labels_check CHECK (label_fr <> '' AND label_ar <> '' AND label_en <> '')
);

CREATE TABLE category_attributes (
    category_code  varchar(64) NOT NULL REFERENCES categories (code),
    attribute_code varchar(64) NOT NULL REFERENCES attribute_definitions (code),
    -- NULL: the default of attribute_definitions.required
    required       boolean,
    sort_order     integer     NOT NULL,
    PRIMARY KEY (category_code, attribute_code)
);

CREATE TABLE governorates (
    code       varchar(64)  PRIMARY KEY,
    -- Code of the Institut National de la Statistique (INS), "Code géographique tunisien": 11 Tunis ... 63 Kébili
    ins_code   varchar(2)   NOT NULL UNIQUE,
    label_fr   varchar(100) NOT NULL,
    label_ar   varchar(100) NOT NULL,
    label_en   varchar(100) NOT NULL,
    sort_order integer      NOT NULL,
    CONSTRAINT governorates_code_check CHECK (code ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT governorates_labels_check CHECK (label_fr <> '' AND label_ar <> '' AND label_en <> '')
);

CREATE TABLE delegations (
    -- "<governorate>.<delegation>", for example "sfax.sakiet-ezzit": some names exist in several governorates
    code             varchar(128) PRIMARY KEY,
    governorate_code varchar(64)  NOT NULL REFERENCES governorates (code),
    -- INS code: governorate code + 2 digits, for example 3453 Sakiet Ezzit
    ins_code         varchar(4)   NOT NULL UNIQUE,
    label_fr         varchar(100) NOT NULL,
    label_ar         varchar(100) NOT NULL,
    label_en         varchar(100) NOT NULL,
    CONSTRAINT delegations_code_check CHECK (
        code ~ '^[a-z0-9]+(-[a-z0-9]+)*\.[a-z0-9]+(-[a-z0-9]+)*$' AND code LIKE governorate_code || '.%'),
    CONSTRAINT delegations_labels_check CHECK (label_fr <> '' AND label_ar <> '' AND label_en <> '')
);

CREATE INDEX delegations_governorate_code_idx ON delegations (governorate_code);
