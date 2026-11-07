# Social events

Events of the social network connections, produced by `social-publisher` (SOC-06) through its transactional outbox.
This file is the contract between the producer and every consumer: read it before consuming the topic, and update it
in the same pull request as any change of an event.

## Topics

| Topic                      | Partitions | Key          | Content                                                          |
| -------------------------- | ---------- | ------------ | ---------------------------------------------------------------- |
| `soukly.social-events`     | 3          | `userId`     | every social event, in the order they happened for a given account |
| `soukly.social-events.dlq` | 1          | original key | messages a consumer could not process, with the cause in headers |

- One topic for every type, owned by its producer (`soukly.user-events` belongs to `user-service`): the key
  (`userId`) puts all the events of an account in the same partition, so a consumer reads them in order. There is no
  ordering between two accounts.
- Replication 1: a single Redpanda node locally and on the VM (DEP-02).
- Locally and in dev, `social-publisher` declares both topics at startup (`NewTopic` beans; existing topics are left
  as they are). On the VM, `infra/compose/redpanda/init-topics.sh` creates them before the services start, since topic
  auto-creation is off there.
- Values are UTF-8 JSON, keys are the account id as a string. No schema registry in V1.

## Delivery

The same as the listing and user events ([`listing-events.md`](listing-events.md#delivery)), with the same outbox
code (`com.soukly.common.outbox`, module `common`):

- **At least once**. `social-publisher` writes an event in its `outbox_events` table in the same database transaction
  as the change it describes, then a relay sends it to the topic and marks it sent only after the broker acknowledged
  it (`acks=all`, idempotent producer).
- **Consumers deduplicate by `eventId`** (a `processed_events` table written in the same transaction as the effect)
  and ignore the types they do not handle.
- A consumer that cannot process a message retries it, then sends it to `soukly.social-events.dlq` with the cause
  (Spring Kafka `kafka_dlt-*` headers) and moves on. Messages of the DLQ are replayed by hand.

## Envelope

The envelope of the user events:

```json
{
  "eventId": "0b7c2d4e-6f8a-4b1c-9d3e-5f7a9b1c3d5e",
  "type": "social.connection.needs-reconnect",
  "schemaVersion": 1,
  "occurredAt": "2026-10-23T09:00:00Z",
  "userId": "c3a1b2d4-5e6f-4a7b-8c9d-0e1f2a3b4c5d",
  "data": { "network": "FACEBOOK", "accountName": "Sfax Auto" }
}
```

| Member          | Type                   | Meaning                                                                                   |
| --------------- | ---------------------- | ----------------------------------------------------------------------------------------- |
| `eventId`       | UUID                   | unique id of the event, the deduplication key; the same when the event is delivered again |
| `type`          | string                 | one of the types below                                                                    |
| `schemaVersion` | integer                | version of the `data` of this type, `1` for every type today                              |
| `occurredAt`    | ISO-8601 instant (UTC) | when the change happened                                                                  |
| `userId`        | UUID                   | the account (`sub` of the access tokens), also the message key                            |
| `data`          | object                 | depends on the type                                                                       |

The order of the members of a JSON object is not significant. Java classes: `com.soukly.common.event.SocialEvents`,
`SocialEventEnvelope` and `SocialConnectionNeedsReconnectData` (module `common`).

## Types

| Type                                | When                                                                                                                                                         | Consumed by                                                                                   |
| ----------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------- |
| `social.connection.needs-reconnect` | a connection goes from `CONNECTED` to `NEEDS_RECONNECT` (token refused at a publication, SOC-04 and SOC-05, or Instagram token that cannot be refreshed, SOC-02), **at most once per connection within 24 hours** | `user-service`: email "connect again" in the preferred language of the member, to `{FRONT_BASE_URL}/{lang}/account/social` |

### `social.connection.needs-reconnect`

| Member of `data` | Type           | Meaning                                                                                                  |
| ---------------- | -------------- | -------------------------------------------------------------------------------------------------------- |
| `network`        | string         | `FACEBOOK` or `INSTAGRAM`                                                                                |
| `accountName`    | string or null | public name of the connected account: the name of the Facebook Page, or `@username` of the Instagram account |

- The 24-hour limit is applied by the producer (`social_connections.reconnect_notified_at`, set in the same statement
  as the change of status): a failed reconnection a few hours later writes no second event. A connection that is
  already `NEEDS_RECONNECT` writes none either.
- No event for a simple failure of a post, a published post or a limit of the network.

## Personal data

Never a token, an email address or a phone number: only the account id and the public name of the Page or Instagram
account the member connected.

## Evolution rules

The rules of the listing events ([`listing-events.md`](listing-events.md#evolution-rules)): adding a member or a
type is allowed at any time (consumers ignore what they do not know); removing, renaming or changing a member needs a
new `schemaVersion` of that type. Every change is documented here, in the same pull request.

## History

| Ticket | Change                                                                                  |
| ------ | --------------------------------------------------------------------------------------- |
| SOC-06 | topics, envelope and the type `social.connection.needs-reconnect` (`schemaVersion` 1)   |
