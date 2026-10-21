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
`jobs` families, `SALE` otherwise, never entered), `photos` (below), `missingFields`, `warnings`, `createdAt`, `updatedAt`. Lengths are
counted in Unicode characters. Texts are plain text: HTML is stored and returned as typed, never interpreted; control
characters other than line breaks and tabs are refused. The seller's phone number, email and name are never copied
into a listing.

```json
{ "id": "…", "status": "DRAFT", "version": 2, "kind": "SALE",
  "categoryCode": "vehicles.cars", "title": "Peugeot 208 Allure", "description": "…",
  "priceMode": "FIXED", "price": 32000, "negotiable": true,
  "governorateCode": "sfax", "delegationCode": null,
  "attributes": { "brand": "Peugeot", "year": 2019, "mileage-km": 62000, "fuel": "diesel", "gearbox": "manual" },
  "photos": [{ "id": "…", "url": "…/1600.webp", "thumbnailUrl": "…/400.webp", "width": 1600, "height": 1200,
               "position": 0 }],
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

**Completeness**: `missingFields` lists the required fields still empty, in form order: `photos` (no READY photo
yet, ANN-02), `title`, `description`, `priceMode`, `price` (with `FIXED`), `governorateCode`, then every required
`attributes.<code>` of the subcategory (`categoryCode` first if its category was deactivated since).

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

## Listing photos (ANN-02)

The owner of a listing adds up to **10 photos**, orders them and deletes them. The browser sends each file **straight
to the S3 storage** with a presigned URL, never through the gateway; listing-service then checks the real file and
converts it with **libvips** to two WebP versions without any metadata. Package `com.soukly.listing.photo`.

| Endpoint (through the gateway, JWT) | Input | Answers |
| --- | --- | --- |
| `POST /api/listings/{id}/photos` | `{ contentType, sizeBytes }` | `201` upload ticket, `400` `validation-failed`, `404`, `409` `photo-limit-reached` |
| `POST /api/listings/{id}/photos/{photoId}/complete` | - | `200` READY photo, `400` `photo-rejected`, `404` |
| `PUT /api/listings/{id}/photos/order` | `{ photoIds: [...] }` | `200` READY photos in order, `400` `validation-failed`, `404` |
| `DELETE /api/listings/{id}/photos/{photoId}` | - | `204`, `404` |

1. **Upload request**: `contentType` is `image/jpeg`, `image/png` or `image/webp`, `sizeBytes` at most 5 MB. A photo is
   created `PENDING_UPLOAD` and the answer says how to upload it, within **10 minutes**:
   ```json
   { "photoId": "…", "uploadUrl": "https://s3…/soukly-listings/incoming/{listingId}/{photoId}?X-Amz-…",
     "method": "PUT", "headers": { "Content-Type": "image/jpeg" }, "expiresAt": "…" }
   ```
2. **Upload**: the browser sends the file with `PUT` on `uploadUrl`, with exactly `headers` (the signature covers
   `Content-Type`). The bucket's CORS rule allows the site's origin.
3. **Completion**: listing-service checks, from the cheapest to the most expensive and **before decoding anything**:
   the object exists, its real size is at most 5 MB, its first bytes are a JPEG, PNG or WebP signature (the name and
   the declared type are never trusted), its header dimensions are at most 50 megapixels and at least 400 × 300 (long
   side ≥ 400, short side ≥ 300, so a portrait photo of 300 × 400 is accepted). Then libvips writes
   `listings/{listingId}/{photoId}/1600.webp` (longest side ≤ 1600 px) and `…/400.webp` (≤ 400 px), never enlarged,
   rotated according to the EXIF orientation, converted to sRGB, WebP quality 80, **every metadata removed** (EXIF with
   the GPS position, XMP, IPTC, ICC profile). The original is deleted from `incoming/` and the photo becomes `READY`,
   last of the listing. The conversion runs in the request (30 s at most) and outside any database transaction.
   Completing a photo again answers it again (`READY` as is, `REJECTED` with the same code), so a retry is safe.

A photo answers `{ id, status, url, thumbnailUrl, width, height, position }` (`width` and `height` of the 1600
version). `GET /api/listings/{id}` lists the READY photos in `photos`, cover first, without `status`.

- **Access**: the owner of a draft only (published listings join with ANN-03); anyone else gets `404`
  `listing-not-found`, like for the listing. An unknown photo is `404` `photo-not-found`.
- **Limit**: 10 photos per listing, READY photos and uploads in progress (`PENDING_UPLOAD`) counted; a rejected photo
  frees its place. Every change locks the listing row, so two requests in parallel never pass the limit or mix the
  positions. Photo changes do not change the `version` (ETag) of the listing: the form keeps its `If-Match`.
- **Order**: `photoIds` lists every READY photo once; the first one is the cover (`position` 0).
- **Deletion**: deletes the photo whatever its status and its three possible objects, then moves the next ones up.
  `PhotoService.deleteAllPhotos(listingId)` deletes every photo of a listing, for ANN-05.
- **Cleanup**: every hour, the uploads not completed after 1 hour are `REJECTED` (`photo.not-uploaded`) and every
  object left in `incoming/` for more than 1 hour is deleted.
- **Public URLs**: `PHOTO_PUBLIC_BASE_URL` + key. Keys contain two random UUIDs. Only `listings/` is publicly
  readable: locally and in dev, Garage's website endpoint hides `incoming/` with a routing rule (and the dev Caddy only
  forwards `listings/`); in prod, the bucket is private and a pre-authenticated request covers `listings/` only. Photos
  of a draft are readable by whoever knows their URL (accepted in V1).
- At most 2 conversions run at the same time (`max-concurrent-conversions`): libvips memory is outside the JVM heap.

### Photo error codes

| Status | Type | `code` / `errors` |
| --- | --- | --- |
| `400` | `photo-rejected` (completion; the photo is `REJECTED`, its original deleted) | `code`: `photo.not-uploaded`, `photo.too-large`, `photo.unsupported-format`, `photo.too-small`, `photo.too-many-pixels`, `photo.processing-failed` |
| `400` | `validation-failed` (upload request) | `contentType`: `photo.unsupported-format`; `sizeBytes`: `photo.invalid-size` (missing or below 1), `photo.too-large` |
| `400` | `validation-failed` (order) | `photoIds`: `photo-order.invalid` (not exactly the READY photos, each once) |
| `404` | `listing-not-found`, `photo-not-found` | |
| `409` | `photo-limit-reached` | |

```json
{ "type": "urn:soukly:problem:photo-rejected", "status": 400, "code": "photo.unsupported-format" }
```

### libvips

The conversion calls the command-line tools of libvips with `ProcessBuilder` (`vipsheader -f width|height` for the
dimensions, `vipsthumbnail` for the conversion), every argument separate and every path a temporary file named by the
service, with `VIPS_BLOCK_UNTRUSTED=1`. Exact commands, checked with libvips 8.15.1:

```bash
vipsthumbnail original.jpg --size '1600x1600>' -o '/tmp/…/1600.webp[Q=80,keep=none]'
vipsthumbnail 1600.webp --size '400x400>' -o '/tmp/…/400.webp[Q=80,keep=none]'
```

`>` only shrinks; `keep=none` (libvips 8.15, replaces the deprecated `strip`) removes every metadata; `vipsthumbnail`
applies the EXIF orientation by default. Version 8.15.1 is the `libvips-tools` package of Ubuntu 24.04, used by the CI
runner (`ubuntu-24.04`) and by the runtime image (`eclipse-temurin:21-jre-noble`, `EXTRA_PACKAGES=libvips-tools` in
`backend/Dockerfile`, DEP-01), on amd64 and arm64. On a development machine: `sudo apt install libvips-tools`
(Debian/Ubuntu) or `brew install vips` (macOS); `soukly.listing.photos.vips-bin-directory` points to another
directory if needed.

### Storage

AWS SDK for Java v2 (`software.amazon.awssdk:s3` 2.55.14, its presigner included; Apache HTTP client, no Netty), with
path-style access and checksums only when an operation requires them (the default CRC32 checksums of recent SDK
versions are not supported by every S3-compatible storage). The presigner signs for `S3_PUBLIC_ENDPOINT`, the address
the browsers reach; the service itself uses `S3_ENDPOINT`.

- **Garage** (local and dev): presigned `PUT`, CORS and website access checked by the tests and by hand.
- **Oracle Object Storage** (prod), **to be confirmed on the real bucket before prod opens**. Oracle documents
  presigned URLs on its [Amazon S3 Compatibility API](https://docs.oracle.com/iaas/Content/Object/Tasks/s3compatibleapi.htm)
  ([announcement](https://blogs.oracle.com/cloud-infrastructure/s3-compat-objectstorage-post-virthost)), with
  path-style endpoints `https://<namespace>.compat.objectstorage.<region>.oraclecloud.com`. What is not confirmed is
  CORS: the compatibility API documents no `PutBucketCors`, so the browser `PUT` from `https://soukly.tn` may be
  refused by its preflight (also listed as an open risk in `infra/vm/README.md`). If it is, the fallbacks are Hetzner
  Object Storage (presigned URLs and CORS documented) or a same-origin upload route; nothing else in this service
  changes.

### Trying it through the gateway

With the local infrastructure, user-service, listing-service and the gateway running, an access token in `$TOKEN` and a
draft id in `$ID`:

```bash
curl -s -X POST http://localhost:8080/api/listings/$ID/photos -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d "{\"contentType\":\"image/jpeg\",\"sizeBytes\":$(stat -c %s photo.jpg)}"
curl -X PUT --upload-file photo.jpg -H 'Content-Type: image/jpeg' "$UPLOAD_URL"               # 200, from Garage
curl -s -X POST http://localhost:8080/api/listings/$ID/photos/$PHOTO_ID/complete -H "Authorization: Bearer $TOKEN"
# then open url and thumbnailUrl: http://soukly-listings.web.localhost:3902/listings/…/1600.webp
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
| `V7__create_listing_photos.sql` | table `listing_photos` (ANN-02): status, position, size of the 1600 version, bytes, rejection code |

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
| `S3_ENDPOINT` | `http://localhost:3900` | S3 API of the photo storage, as the service reaches it |
| `S3_PUBLIC_ENDPOINT` | `S3_ENDPOINT` | S3 API as the browsers reach it: host of the presigned upload URLs |
| `S3_REGION` | `garage` | storage region |
| `S3_BUCKET` | `soukly-listings` | bucket of the photos |
| `S3_ACCESS_KEY`, `S3_SECRET_KEY` | `S3_ACCESS_KEY_ID`, `S3_SECRET_ACCESS_KEY` of the root `.env` | key with read and write on the bucket |
| `PHOTO_PUBLIC_BASE_URL` | `http://soukly-listings.web.localhost:3902` | public URL of the bucket root, the object key is appended |

Photo limits and timings are under `soukly.listing.photos.*` in `application.yml` (10 photos, 5 MB, 50 megapixels,
400 × 300, URL valid 10 minutes, cleanup every hour after 1 hour, WebP quality 80, 30 s per conversion, 2 conversions
at a time).

## Tests

`./mvnw -pl listing-service verify` (Docker required): integration tests against PostgreSQL 18.6 (Testcontainers),
one shared Spring context, base class `support/IntegrationTest`. Access tokens are signed by an RSA key generated when
the tests start and published by a local JWKS server (`support/TestJwks`); no key is committed. Listing tests use a new
account id each time instead of resetting the database. `AttributeValidatorTest` covers every attribute type and
bound without the database.

Photo tests (`photo/PhotoApiTests`) also start Garage v2.4.1 (`support/TestGarage`, set up through its admin API with
random secrets) and need the libvips tools on the machine, like the CI. The images are generated by the tests
(`photo/TestImages`: JPEG with EXIF orientation, GPS position and XMP, PNG, a PNG header declaring 50 megapixels, a PDF
named like a JPEG); the files are uploaded to the presigned URLs over HTTP like a browser. The absence of metadata is
checked twice: no `EXIF`, `XMP ` or `ICCP` chunk in the WebP files, and nothing EXIF, XMP or GPS in `vipsheader -a`.
