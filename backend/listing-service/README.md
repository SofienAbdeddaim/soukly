# listing-service

Listings of Soukly. CAT-01 adds the read-only **referentials** that listings (ANN) and search (CATA) rely on:
categories, specific attributes, governorates and delegations. ANN-01 adds the **drafts** of listings. Port 8082,
package `com.soukly.listing`, database `soukly_listing` (user `listing_svc`).

## Referential endpoints

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

## Listing drafts (ANN-01)

A signed-in seller creates a listing as a **draft** and completes it step by step (form of ANN-04). A draft is
invisible to the public. Every value sent is validated at once; completeness is only required at publication (ANN-03),
but every answer lists what is still missing. Package `com.soukly.listing.listing`.

| Endpoint (through the gateway) | Input | Answers |
| --- | --- | --- |
| `POST /api/listings` | `{ categoryCode, …optional fields }` | `201` + `Location` + `ETag`, `400`, `401`, `409` `draft-limit-reached` |
| `PATCH /api/listings/{id}` | JSON Merge Patch (`application/merge-patch+json` or `application/json`), `If-Match` | `200` + `ETag`, `400`, `401`, `404`, `412` `version-conflict`, `428` `if-match-required` |
| `GET /api/listings/{id}` | bearer token of the owner | `200` + `ETag`, `404` |

- **Access**: the owner is the `sub` of the access token. The service validates the token again with the rules of the
  gateway (RS256 only, JWKS of user-service, `iss`, `exp` with 30 s of clock skew, `sub` must be an account id).
  `GET /api/listings/{id}` is public at the gateway: the token, when there is one, is forwarded and says who reads. A
  draft of another account, a listing without a token, an unknown id or an id that is not a UUID all answer `404`
  `listing-not-found`, never `403`. An invalid token answers `401`, even on `GET`, so that the front end refreshes it.
- **Creation**: only `categoryCode` is required. `Location` is the relative reference `listings/{id}`, resolved
  against the called URL (`/api/listings/{id}` through the gateway, which strips `/api` without rewriting headers).
- **Merge Patch** (RFC 7396): an absent member is unchanged, `null` clears an optional field (`negotiable` goes back
  to `false`), a value replaces it. `attributes` is merged member by member: `{"attributes": {"model": null}}` removes
  one attribute, `{"attributes": null}` removes them all. `categoryCode` cannot be cleared. The patch is read as a
  `JsonNode` and applied field by field: no patch library is needed, and the three states of a member stay apart.
- **Concurrency**: every listing has a `version` (1 at creation, +1 at every accepted change), sent as `ETag: "4"`. A
  `PATCH` must send `If-Match: "4"` (or `*`); another version answers `412` (two tabs open), no header answers `428`.
  The comparison is strong: a weak tag `W/"4"` never matches.
- **Nothing is saved when a request has an error**: the version is unchanged.
- Answers carry `Cache-Control: no-store`: a draft is private.

### Fields

| Field | Rule |
| --- | --- |
| `categoryCode` | an active **subcategory** of CAT-01 (a family code is refused) |
| `title` | 5 to 80 characters after trimming and collapsing inner whitespace (line breaks included) |
| `description` | 20 to 4,000 characters after trimming; line breaks kept |
| `priceMode` | `FIXED` or `ON_QUOTE`; `ON_QUOTE` only for the `jobs` and `services` families |
| `price` | whole dinars, JSON integer from 1 to 100,000,000; refused with `ON_QUOTE`, required by completeness with `FIXED` |
| `negotiable` | boolean, `false` by default, without effect with `ON_QUOTE` |
| `governorateCode` | one of the 24 governorates |
| `delegationCode` | optional, a delegation **of the chosen governorate** |
| `attributes` | `{ "<code>": value }`, checked against the attributes of the subcategory (below) |

Read-only members of the answer: `id`, `status` (`DRAFT`), `version`, `kind` (`SERVICE` for the `services` and
`jobs` families, `SALE` otherwise, never entered), `missingFields`, `warnings`, `createdAt`, `updatedAt`. Lengths are
counted in Unicode characters. Texts are plain text: HTML is stored and returned as typed, never interpreted; control
characters other than line breaks and tabs are refused. The seller's phone number, email and name are never copied
into a listing.

```json
{ "id": "…", "status": "DRAFT", "version": 2, "kind": "SALE",
  "categoryCode": "vehicles.cars", "title": "Peugeot 208 Allure", "description": "…",
  "priceMode": "FIXED", "price": 32000, "negotiable": true,
  "governorateCode": "sfax", "delegationCode": null,
  "attributes": { "brand": "Peugeot", "year": 2019, "mileage-km": 62000, "fuel": "diesel", "gearbox": "manual" },
  "missingFields": [], "warnings": [],
  "createdAt": "…", "updatedAt": "…" }
```

**Attributes** are validated generically from `category_attributes` and `attribute_definitions` (no attribute is known
by its code in Java): `TEXT` is a string normalized like the title (case kept, never empty) within `minLength` and
`maxLength`; `INTEGER` a JSON integer within `minValue` and `maxValue` (for `year`, the current year of the server
clock + 1); `ENUM` one of the value codes; `BOOLEAN` a JSON boolean. A code the subcategory does not define is refused.
They are answered in the display order of the subcategory.

**Coherence**: the draft after the patch must be coherent, so a client that changes one side also sends the other:
`{"priceMode": "ON_QUOTE", "price": null}`, `{"governorateCode": "tunis", "delegationCode": null}`. The only
automatic changes come with a **change of category**, and are reported in `warnings` (for that answer only):

| Warning | Change |
| --- | --- |
| `attributes-removed` | attributes the new subcategory does not define were removed (those it shares are kept) |
| `price-mode-reset` | `ON_QUOTE` is not allowed in the new subcategory: `priceMode` is cleared |

Attributes sent with a new category are checked against the new one.

**Completeness**: `missingFields` lists the required fields still empty, in form order: `title`, `description`,
`priceMode`, `price` (with `FIXED`), `governorateCode`, then every required `attributes.<code>` of the subcategory
(`categoryCode` first if its category was deactivated since). ANN-02 adds `photos`.

**Limit**: 20 drafts per account at the same time (`soukly.listing.drafts.max-per-owner`); the 21st answers `409`
`draft-limit-reached`. Creations of one account are serialized by a PostgreSQL advisory lock, so two requests in
parallel cannot both pass the limit.

### Error codes

Errors are `application/problem+json`. A `400` has the type `urn:soukly:problem:validation-failed` and lists every
invalid field with a stable code that the front end translates:

```json
{ "type": "urn:soukly:problem:validation-failed", "status": 400,
  "errors": [{ "field": "attributes.year", "code": "attribute.out-of-range" },
             { "field": "attributes.color", "code": "attribute.unknown" }] }
```

| Field | Codes |
| --- | --- |
| `body` | `body.not-an-object` (a malformed JSON body is a plain `400` `bad-request`) |
| any other member | `field.unknown` (for example `status` or `phone`: they cannot be written) |
| `categoryCode` | `category.required`, `category.unknown` |
| `title` | `title.invalid` (not a string, control character), `title.too-short`, `title.too-long` |
| `description` | `description.invalid`, `description.too-short`, `description.too-long` |
| `priceMode` | `price-mode.invalid`, `price-mode.not-allowed` |
| `price` | `price.invalid` (not a JSON integer: `32000.5`, `"32000"`), `price.out-of-range`, `price.not-allowed` (with `ON_QUOTE`) |
| `negotiable` | `negotiable.invalid` |
| `governorateCode` | `governorate.unknown` |
| `delegationCode` | `delegation.unknown`, `delegation.not-in-governorate` |
| `attributes` | `attributes.invalid` (not an object) |
| `attributes.<code>` | `attribute.unknown`, `attribute.invalid-type`, `attribute.invalid-value` (unknown enum value, control character), `attribute.out-of-range`, `attribute.too-short`, `attribute.too-long` |

| Status | Type |
| --- | --- |
| `401` | `unauthorized` (missing or invalid token, `WWW-Authenticate: Bearer`) |
| `404` | `listing-not-found` |
| `409` | `draft-limit-reached` |
| `412` | `version-conflict` |
| `428` | `if-match-required` |

### Trying it through the gateway

With PostgreSQL, user-service, listing-service and the gateway running (see the root README), and an access token from
`POST /api/users/login`:

```bash
curl -i -X POST http://localhost:8080/api/listings -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"categoryCode":"vehicles.cars"}'          # 201, ETag: "1"
curl -i -X PATCH http://localhost:8080/api/listings/$ID -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/merge-patch+json' -H 'If-Match: "1"' \
  -d '{"title":"Peugeot 208 Allure","priceMode":"FIXED","price":32000,"governorateCode":"sfax"}'   # 200, ETag: "2"
curl -i http://localhost:8080/api/listings/$ID -H "Authorization: Bearer $TOKEN"     # 200
```

## Data

Flyway migrations in `src/main/resources/db/migration`:

| Migration | Content |
| --- | --- |
| `V1__create_referentials.sql` | tables `categories`, `attribute_definitions`, `attribute_enum_values`, `category_attributes`, `governorates`, `delegations` |
| `V2__insert_categories.sql` | 10 families, 44 subcategories |
| `V3__insert_attributes.sql` | 10 attributes of vehicles and real estate, their enum values and categories |
| `V4__insert_governorates.sql` | 24 governorates, with their INS code |
| `V5__insert_delegations.sql` | 264 delegations, with their INS code |
| `V6__create_listings.sql` | table `listings` (ANN-01): content of a listing, `attributes` in JSONB, `version`, index `(owner_id, status)` |

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
| `USER_SERVICE_JWKS_URI` | `http://localhost:8081/.well-known/jwks.json` | public keys of user-service, same as the gateway |
| `JWT_ISSUER` | `soukly-user-service` | expected `iss` of the access tokens, same as the gateway |

## Tests

`./mvnw -pl listing-service verify` (Docker required): integration tests against PostgreSQL 18.6 (Testcontainers),
one shared Spring context, base class `support/IntegrationTest`. Access tokens are signed by an RSA key generated when
the tests start and published by a local JWKS server (`support/TestJwks`); no key is committed. Listing tests use a new
account id each time instead of resetting the database. `AttributeValidatorTest` covers every attribute type and
bound without the database.
