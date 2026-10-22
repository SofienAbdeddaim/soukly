# User events

Events of the life cycle of an account, produced by `user-service` (AUTH-05) through its transactional outbox. This
file is the contract between the producer and every consumer: read it before consuming the topic, and update it in
the same pull request as any change of an event.

## Topics

| Topic                    | Partitions | Key          | Content                                                          |
| ------------------------ | ---------- | ------------ | ---------------------------------------------------------------- |
| `soukly.user-events`     | 3          | `userId`     | every user event, in the order they happened for a given account |
| `soukly.user-events.dlq` | 1          | original key | messages a consumer could not process, with the cause in headers |

- One topic for every type: the key (`userId`) puts all the events of an account in the same partition, so a consumer
  reads them in order. There is no ordering between two accounts.
- Replication 1: a single Redpanda node locally and on the VM (DEP-02).
- Locally and in dev, `user-service` declares both topics at startup (`NewTopic` beans; existing topics are left as
  they are). On the VM, `infra/compose/redpanda/init-topics.sh` creates them before the services start, since topic
  auto-creation is off there.
- Values are UTF-8 JSON, keys are the account id as a string. No schema registry in V1.

## Delivery

The same as the listing events ([`listing-events.md`](listing-events.md#delivery)), with the same outbox code
(`com.soukly.common.outbox`, module `common`):

- **At least once**. `user-service` writes an event in its `outbox_events` table in the same database transaction as
  the change it describes, then a relay sends it to the topic (locking its batch with `FOR UPDATE SKIP LOCKED`) and
  marks it sent only after the broker acknowledged it (`acks=all`, idempotent producer).
- **Consumers deduplicate by `eventId`** (a `processed_events` table written in the same transaction as the effect)
  and ignore the types they do not handle.
- When the broker is down, events wait in the outbox and leave in order when it comes back; the change that wrote
  them (a deletion, for example) never fails because of the broker.
- A consumer that cannot process a message retries it, then sends it to `soukly.user-events.dlq` with the cause
  (Spring Kafka `kafka_dlt-*` headers) and moves on. Messages of the DLQ are replayed by hand.

## Envelope

The envelope of the listing events, with the account id in place of the listing and seller ids:

```json
{
  "eventId": "7e1f0c3a-2b4d-4e5f-9a6b-7c8d9e0f1a2b",
  "type": "user.deleted",
  "schemaVersion": 1,
  "occurredAt": "2026-10-22T09:00:00Z",
  "userId": "c3a1b2d4-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
  "data": {}
}
```

| Member          | Type                   | Meaning                                                                                         |
| --------------- | ---------------------- | ----------------------------------------------------------------------------------------------- |
| `eventId`       | UUID                   | unique id of the event, the deduplication key; the same when the event is delivered again       |
| `type`          | string                 | one of the types below                                                                          |
| `schemaVersion` | integer                | version of the `data` of this type, `1` for every type today                                    |
| `occurredAt`    | ISO-8601 instant (UTC) | when the change happened (time of the database transaction)                                     |
| `userId`        | UUID                   | the account (`sub` of the access tokens, `ownerId` of the listing events), also the message key |
| `data`          | object                 | depends on the type                                                                             |

The order of the members of a JSON object is not significant. Java classes: `com.soukly.common.event.UserEvents`
and `UserEventEnvelope` (module `common`).

## Types

| Type           | When                                                  | Consumed by                                                                                                                                       |
| -------------- | ----------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------- |
| `user.deleted` | the member deleted their account (`DELETE /users/me`) | `listing-service`: deletes every listing of the account with the rules of ANN-05 (drafts erased, other listings `DELETED` with `listing.deleted`); `social-publisher` (SOC-01): deletes the social network connections of the account and revokes the Facebook permissions at best effort |

### `user.deleted`

`data` is an empty object: `{}`. The account can no longer sign in and its address is free at once; the account is
erased for good 30 days later (`ACCOUNT_PURGE_AFTER`), its listings by the purge of `listing-service`
(`LISTING_PURGE_AFTER`). Its events already sent are not changed.

## Personal data

No user event carries personal data: no name, email address or phone number, only the account id.

## Evolution rules

The rules of the listing events ([`listing-events.md`](listing-events.md#evolution-rules)): adding a member or a
type is allowed at any time (consumers ignore what they do not know); removing, renaming or changing a member needs a
new `schemaVersion` of that type. Every change is documented here, in the same pull request.

## History

| Ticket  | Change                                                           |
| ------- | ---------------------------------------------------------------- |
| AUTH-05 | topics, envelope and the type `user.deleted` (`schemaVersion` 1) |
