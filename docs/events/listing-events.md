# Listing events

Events of the life cycle of a listing, produced by `listing-service` (ANN-03, ANN-05) through its transactional outbox.
This file is the contract between the producer and every consumer: read it before consuming a topic, and update it
in the same pull request as any change of an event.

## Topics

| Topic | Partitions | Key | Content |
| --- | --- | --- | --- |
| `soukly.listing-events` | 3 | `listingId` | every listing event, in the order they happened for a given listing |
| `soukly.listing-events.dlq` | 1 | original key | messages a consumer could not process, with the cause in headers |

- One topic for every type: the key (`listingId`) puts all the events of a listing in the same partition, so a
  consumer reads them in order. There is no ordering between two listings.
- Replication 1: a single Redpanda node locally and on the VM (DEP-02).
- Locally and in dev, `listing-service` declares both topics at startup (`NewTopic` beans; existing topics are left
  as they are). On the VM, `infra/compose/redpanda/init-topics.sh` creates them before the services start, since topic
  auto-creation is off there.
- Values are UTF-8 JSON, keys are the listing id as a string. No schema registry in V1.

## Delivery

- **At least once**. `listing-service` writes an event in the `outbox_events` table in the same database
  transaction as the change it describes, then a relay sends it to the topic and marks it sent only after the
  broker acknowledged it (`acks=all`, idempotent producer). A crash between the acknowledgement and the mark sends
  the event again.
- **Consumers deduplicate by `eventId`** (for example a `processed_events` table written in the same transaction as
  the effect of the event) and ignore the types they do not handle.
- When the broker is down, events wait in the outbox and leave in order when it comes back; the change that wrote
  them (a publication, for example) never fails because of the broker.
- A consumer that cannot process a message retries it, then sends it to `soukly.listing-events.dlq` with the cause
  (Spring Kafka `kafka_dlt-*` headers) and moves on. Messages of the DLQ are replayed by hand.

## Envelope

Every event has the same envelope:

```json
{
  "eventId": "0b8d1c6e-6f0e-4d4b-9a51-5a3f7e2c9b10",
  "type": "listing.published",
  "schemaVersion": 1,
  "occurredAt": "2026-10-12T20:00:00Z",
  "listingId": "5f0c1e2a-8d1b-4b0e-9c3a-2f6d7e8a9b0c",
  "ownerId": "c3a1b2d4-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
  "data": { }
}
```

| Member | Type | Meaning |
| --- | --- | --- |
| `eventId` | UUID | unique id of the event, the deduplication key; the same when the event is delivered again |
| `type` | string | one of the types below |
| `schemaVersion` | integer | version of the `data` of this type, `1` for every type today |
| `occurredAt` | ISO-8601 instant (UTC) | when the change happened (time of the database transaction) |
| `listingId` | UUID | the listing, also the message key |
| `ownerId` | UUID | account id of the seller (`sub` of the access tokens); never their name, email or phone number |
| `data` | object | depends on the type |

The order of the members of a JSON object is not significant. Java classes of the envelope and of the `data`:
`com.soukly.common.event` (module `common`).

## Types

| Type | When | Consumed by |
| --- | --- | --- |
| `listing.published` | a draft is published (`POST /listings/{id}/publish`) | `social-publisher` (SOC-04) |
| `listing.renewed` | an expired or deactivated listing, or one that expires soon, is put back online for 60 days (`POST /listings/{id}/renew`) | nobody in V1: `social-publisher` ignores it, a renewal is never posted again on the social networks |
| `listing.updated` | the seller changed a `PUBLISHED` listing (`PATCH /listings/{id}` or its photos); visible at once | nobody in V1: `social-publisher` ignores it, a change is never posted again |
| `listing.deactivated` | the seller deactivated a published listing (`POST /listings/{id}/deactivate`): hidden until a renewal | nobody in V1 |
| `listing.deleted` | the seller deleted a listing that was published (`DELETE /listings/{id}`), or deleted their account (`user.deleted`, [`user-events.md`](user-events.md)); a draft is erased without event | `social-publisher` (SOC-04, SOC-05): removes the posts of the listing |
| `listing.expiring-soon` | a published listing expires in less than 3 days, once per publication or renewal | `user-service` (reminder email) |
| `listing.expired` | a published listing reached its expiry date and is no longer visible | nobody in V1 |

### `listing.published`

```json
{
  "title": "Peugeot 208 Allure",
  "description": "Première main, carnet d'entretien complet.\nVisible à Sfax.",
  "categoryCode": "vehicles.cars",
  "kind": "SALE",
  "priceMode": "FIXED",
  "price": 32000,
  "currency": "TND",
  "negotiable": true,
  "governorateCode": "sfax",
  "delegationCode": "sfax.sfax-ville",
  "coverPhotoUrl": "https://photos.example.tn/listings/5f0c…/1b2c…/1600.webp",
  "photoUrls": [
    "https://photos.example.tn/listings/5f0c…/1b2c…/1600.webp",
    "https://photos.example.tn/listings/5f0c…/7d8e…/1600.webp"
  ],
  "publicUrls": {
    "fr": "https://soukly.tn/fr/listings/5f0c…",
    "ar": "https://soukly.tn/ar/listings/5f0c…",
    "en": "https://soukly.tn/en/listings/5f0c…"
  },
  "shareTo": ["FACEBOOK", "INSTAGRAM"],
  "shareLanguage": "fr",
  "expiresAt": "2026-12-11T20:00:00Z",
  "shortRef": "8F3C2K9A"
}
```

| Member | Type | Meaning |
| --- | --- | --- |
| `title` | string | 5 to 80 characters, plain text |
| `description` | string | full text, 20 to 4,000 characters, line breaks kept, plain text (never HTML) |
| `categoryCode` | string | subcategory code of CAT-01 (`vehicles.cars`) |
| `kind` | string | `SALE` or `SERVICE` (families `services` and `jobs`) |
| `priceMode` | string | `FIXED` or `ON_QUOTE` |
| `price` | integer or null | whole dinars; `null` with `ON_QUOTE` |
| `currency` | string | always `TND` in V1 |
| `negotiable` | boolean | always `false` with `ON_QUOTE` |
| `governorateCode` | string | one of the 24 governorate codes of CAT-01 |
| `delegationCode` | string or null | delegation code of CAT-01, optional |
| `coverPhotoUrl` | string | public URL of the cover photo, the first of `photoUrls` |
| `photoUrls` | array of strings | public URLs of the WebP versions of at most 1600 px, in the seller's order (1 to 10) |
| `publicUrls` | object | URL of the listing page in each language: `{PUBLIC_BASE_URL}/{lang}/listings/{listingId}` (route of CATA-03) |
| `shareTo` | array of strings | social networks the seller chose for this listing (SOC-03), `FACEBOOK` and/or `INSTAGRAM` in this order, without duplicates; `[]` when they chose none or published without choosing. listing-service does not check the connections: `social-publisher` skips a network that is not connected or needs a reconnection and records why |
| `shareLanguage` | string or null | language of the post texts, `fr`, `ar` or `en` (the language of the page when the seller chose); `fr` when a network was chosen without a language, `null` when `shareTo` is empty |
| `expiresAt` | ISO-8601 instant | end of visibility, 60 days after the publication |
| `shortRef` | string | short reference of the listing (SOC-05): 8 characters among `23456789ABCDEFGHJKLMNPQRSTUVWXYZ` (no `0`, `O`, `1`, `I`), unique, upper case. Written in the Instagram captions as `{host of PUBLIC_BASE_URL}/r/{shortRef}`; `GET /api/listings/by-ref/{ref}` resolves it in any case |

### `listing.renewed`, `listing.expiring-soon`, `listing.expired`

```json
{ "title": "Peugeot 208 Allure", "expiresAt": "2026-12-11T20:00:00Z" }
```

| Member | Type | Meaning |
| --- | --- | --- |
| `title` | string | title of the listing when the event happened |
| `expiresAt` | ISO-8601 instant | new expiry date (`listing.renewed`), coming one (`listing.expiring-soon`) or reached one (`listing.expired`) |

### `listing.updated`

The members of `listing.published` after the change, without `shareTo`, and `changedFields` first:

```json
{
  "changedFields": ["price"],
  "title": "Peugeot 208 Allure",
  "description": "Première main, carnet d'entretien complet.\nVisible à Sfax.",
  "categoryCode": "vehicles.cars",
  "kind": "SALE",
  "priceMode": "FIXED",
  "price": 30500,
  "currency": "TND",
  "negotiable": true,
  "governorateCode": "sfax",
  "delegationCode": "sfax.sfax-ville",
  "coverPhotoUrl": "https://photos.example.tn/listings/5f0c…/1b2c…/1600.webp",
  "photoUrls": ["https://photos.example.tn/listings/5f0c…/1b2c…/1600.webp"],
  "publicUrls": {
    "fr": "https://soukly.tn/fr/listings/5f0c…",
    "ar": "https://soukly.tn/ar/listings/5f0c…",
    "en": "https://soukly.tn/en/listings/5f0c…"
  },
  "expiresAt": "2026-12-11T20:00:00Z"
}
```

| Member | Type | Meaning |
| --- | --- | --- |
| `changedFields` | array of strings | names of the changed members, in form order: `title`, `description`, `priceMode`, `price`, `negotiable`, `governorateCode`, `delegationCode`, `attributes` (any specific attribute), or `photos` (a photo added, removed or moved); never empty |
| other members | | as in `listing.published`, with the values after the change |

The category never changes once a listing was published. A change of an expired or deactivated listing writes no
event: the listing is not visible, and `listing.renewed` comes before it is again.

### `listing.deactivated`, `listing.deleted`

`data` is an empty object: `{}`. The envelope says which listing and which seller. A deleted listing is erased for
good 30 days later (photos and data); its events already sent are not changed.

## Personal data

No event carries personal data of the seller: no name, email address or phone number. A consumer that needs them
(the reminder email of `user-service`) reads them from its own data with `ownerId`. Titles and descriptions are
written by the seller and may be shown publicly; they are not considered personal data here.

## Evolution rules

- **Adding** a member to `data` or to the envelope is allowed without a new version: consumers must ignore members they
  do not know.
- **Removing** a member, **renaming** it or **changing its meaning or type** is a breaking change: increase
  `schemaVersion` of that type, and keep producing the old version until every consumer reads the new one.
- **A new type** is allowed at any time: consumers ignore unknown types.
- Every change is documented here, in the same pull request, with its ticket in the history below.

## History

| Ticket | Change |
| --- | --- |
| ANN-03 | topics, envelope and the types `listing.published`, `listing.renewed`, `listing.expiring-soon`, `listing.expired` (`schemaVersion` 1) |
| ANN-05 | new types `listing.updated`, `listing.deactivated`, `listing.deleted` (`schemaVersion` 1); `listing.renewed` also from a deactivated listing |
| AUTH-05 | `listing.deleted` also for each listing of a deleted account; the outbox relay moved to `common` (same behavior), shared with the user events |
| SOC-03 | `listing.published`: `shareTo` now carries the choice of the seller, new member `shareLanguage` (added, `schemaVersion` still 1) |
| SOC-05 | `listing.published`: new member `shortRef` (added, `schemaVersion` still 1) |
