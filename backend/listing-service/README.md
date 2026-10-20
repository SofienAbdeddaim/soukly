# listing-service

Listings of Soukly. CAT-01 adds the read-only **referentials** that listings (ANN) and search (CATA) rely on:
categories, specific attributes, governorates and delegations. Port 8082, package `com.soukly.listing`, database
`soukly_listing` (user `listing_svc`).

## Endpoints

Public (no JWT), exposed by the gateway under `/api` (`GET /api/categories` reaches this service as
`GET /categories`). There is no write endpoint: the data only changes through migrations in V1 (admin screens are
CAT-02, after V1).

| Endpoint | Answer |
| --- | --- |
| `GET /categories` | the 10 families in display order, each with its subcategories |
| `GET /categories/{code}/attributes` | specific attributes of a subcategory (`vehicles.cars`), `[]` when it has none; `404` `category-not-found` for an unknown or inactive code or a family code |
| `GET /locations/governorates` | the 24 governorates in display order |
| `GET /locations/governorates/{code}/delegations` | delegations of a governorate, sorted by French label; `404` `governorate-not-found` |

Every element has a stable `code` and its three labels; the front end picks the one of the current language:

```json
[
  {
    "code": "vehicles",
    "label": { "fr": "Véhicules", "ar": "المركبات", "en": "Vehicles" },
    "subcategories": [
      { "code": "vehicles.cars", "familyCode": "vehicles", "label": { "fr": "Voitures", "ar": "سيارات", "en": "Cars" } }
    ]
  }
]
```

An attribute (`GET /categories/vehicles.cars/attributes`) lists only the members that apply to its type:

```json
[
  { "code": "brand", "type": "TEXT", "required": true, "minLength": 2, "maxLength": 40, "filterable": true,
    "label": { "fr": "Marque", "ar": "الماركة", "en": "Brand" } },
  { "code": "year", "type": "INTEGER", "required": true, "minValue": 1950, "maxValue": 2027, "filterable": true,
    "label": { "fr": "Année", "ar": "سنة الصنع", "en": "Year" } },
  { "code": "fuel", "type": "ENUM", "required": true, "filterable": true, "label": { "…": "…" },
    "values": [{ "code": "petrol", "label": { "fr": "Essence", "ar": "بنزين", "en": "Petrol" } }] }
]
```

- Types: `TEXT` (`minLength`, `maxLength`, in characters after trimming), `INTEGER` (`minValue`, `maxValue`,
  inclusive), `ENUM` (`values`), `BOOLEAN`.
- `year`: its maximum is the current year + 1. The database stores the offset
  (`max_value_current_year_offset = 1`, `max_value` null); the API and `ReferentialService` resolve it.
- `rooms` is shown as `S+n` (`S+2`) by the front end. The price stays a common listing field, not an attribute.
- Only `active` categories are returned; an inactive family hides its subcategories.
- Errors are `application/problem+json` (`com.soukly.common.error.ProblemDetails`).

### HTTP cache

Every referential answer has `Cache-Control: max-age=3600, public` and an `ETag` (hash of the body,
`ShallowEtagHeaderFilter`); a request with the same `If-None-Match` gets `304` without a body. The service reads the
tables once, on first use, and keeps them in memory: they only change with a migration, which comes with a restart.

### For the other features

`com.soukly.listing.referential.ReferentialService` is the Java entry point for ANN-01 (listing validation) and
CATA (filters): `categories()`, `findSubcategory(code)`, `attributesOf(subcategoryCode)` (attributes with the
`required` flag of that category and `effectiveMaxValue(Year)`), `governorates()`, `findGovernorate(code)`,
`delegationsOf(governorateCode)`, `findDelegation(code)`.

## Data

Flyway migrations in `src/main/resources/db/migration`:

| Migration | Content |
| --- | --- |
| `V1__create_referentials.sql` | tables `categories`, `attribute_definitions`, `attribute_enum_values`, `category_attributes`, `governorates`, `delegations` |
| `V2__insert_categories.sql` | 10 families, 44 subcategories |
| `V3__insert_attributes.sql` | 10 attributes of vehicles and real estate, their enum values and categories |
| `V4__insert_governorates.sql` | 24 governorates, with their INS code |
| `V5__insert_delegations.sql` | 264 delegations, with their INS code |

Places come from the official INS "Code géographique tunisien" (December 2012, division at 31 December 2012); the
source, its checksum and the few display fixes are in the header of `V5`. Never invent a place: a delegation created
since 2012 is added by a new migration from a newer official source (Ministry of the Interior or INS). The source has
no English names: `label_en` of a delegation is its French name without diacritics.

### Codes

Codes are English, lower case, `kebab-case`, unique and **final**: URLs, listings and filters use them, never a
technical id. A subcategory code is `<family>.<subcategory>` (`real-estate.apartments`), a delegation code
`<governorate>.<delegation>` (some names exist in several governorates). Check constraints enforce the format, the two
category levels and non-empty labels.

### Adding or changing data

Write a **new** migration (`V5__…`, never edit a released one: Flyway checks their checksums), with
`INSERT … ON CONFLICT … DO NOTHING` so that it can be replayed safely. No Java change is needed.

- **A subcategory**: `INSERT INTO categories (code, parent_code, label_fr, label_ar, label_en, sort_order)`.
- **An attribute**: insert it in `attribute_definitions` (type, `required`, bounds, `filterable`, labels,
  `sort_order`), its values in `attribute_enum_values` for an `ENUM`, then one row per category in
  `category_attributes` (`required` null keeps the default of the definition, `true`/`false` overrides it).
- **A label fix**: `UPDATE … SET label_ar = … WHERE code = …`.
- **Hiding a category**: `UPDATE categories SET active = false WHERE code = …`. Never delete or rename a code that
  listings may use; add the new one and deactivate the old one.

## Configuration

| Variable | Local default | Use |
| --- | --- | --- |
| `LISTING_SERVICE_PORT` | `8082` | HTTP port |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/soukly_listing` | database |
| `SPRING_DATASOURCE_USERNAME` | `listing_svc` | database user |
| `SPRING_DATASOURCE_PASSWORD` | `LISTING_SVC_DB_PASSWORD` | database password |

## Tests

`./mvnw -pl listing-service verify` (Docker required): integration tests against PostgreSQL 18.6 (Testcontainers),
one shared Spring context, base class `support/IntegrationTest`.
