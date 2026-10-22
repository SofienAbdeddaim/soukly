# user-service

Accounts of Soukly: registration and email verification (AUTH-01), sign-in, sessions and the JWKS (AUTH-02), the
forgotten password (AUTH-04), the profile and the deletion of the account (AUTH-05), and the reminder email of the listings that expire soon (ANN-03). The screens are in `frontend/apps/mfe-account` (AUTH-03). Port 8081, package `com.soukly.user`, database `soukly_user` (user `user_svc`).

## Endpoints

Exposed by the gateway under `/api` (`POST /api/users/register` reaches this service as `POST /users/register`).

| Endpoint                          | Body                                                   | Answers                                                                       |
| --------------------------------- | ------------------------------------------------------ | ----------------------------------------------------------------------------- |
| `POST /users/register`            | `{ email, password, displayName, phone, acceptTerms }` | `202`, `400`                                                                  |
| `POST /users/verify-email`        | `{ token }`                                            | `204`, `400`                                                                  |
| `POST /users/verify-email/resend` | `{ email }`                                            | `202`, `400` (malformed email)                                                |
| `POST /users/login`               | `{ email, password }`                                  | `200`, `401` `invalid-credentials`, `403` `email-not-verified`, `429`         |
| `POST /users/refresh`             | cookie `soukly_rt`, header `Origin`                    | `200`, `401` `session-expired` or `session-revoked`, `403`                    |
| `POST /users/logout`              | cookie `soukly_rt` (optional), header `Origin`         | `204`, `403`                                                                  |
| `POST /users/password/forgot`     | `{ email }`                                            | `202`, `400` (malformed email)                                                |
| `POST /users/password/reset`      | `{ token, newPassword }`, header `Origin`              | `200` like `login`, `400` `reset-token-invalid` or `validation-failed`, `403` |
| `POST /users/email/confirm`       | `{ token }`                                            | `204`, `400` `email-change-token-invalid`, `409` `email-taken`                |
| `GET /.well-known/jwks.json`      | -                                                      | `200`; internal only, never routed by the gateway                             |

Profile of the signed-in account (AUTH-05), with an access token; the account is its subject:

| Endpoint                        | Body                                                              | Answers                                                                       |
| ------------------------------- | ----------------------------------------------------------------- | ----------------------------------------------------------------------------- |
| `GET /users/me`                 | -                                                                 | `200` profile, `404` once deleted                                             |
| `PATCH /users/me`               | JSON Merge Patch of `displayName`, `phone`, `preferredLanguage`   | `200` profile, `400` `validation-failed`                                      |
| `POST /users/me/password`       | `{ currentPassword, newPassword }`, cookie `soukly_rt` (optional) | `204`, `400` `current-password-invalid` or `validation-failed`, `429`         |
| `POST /users/me/email`          | `{ newEmail, currentPassword }`                                   | `202`, `400` `current-password-invalid` or `validation-failed`, `429`         |
| `DELETE /users/me/email-change` | -                                                                 | `204`                                                                         |
| `DELETE /users/me`              | `{ currentPassword }`, header `Origin`                            | `204` with the cookie deleted, `400` `current-password-invalid`, `403`, `429` |

The endpoints of the first table and `GET /actuator/health` are public. Every other path needs a valid access token: the service
validates it itself with its own keys, with the gateway's rules (RS256 only, `iss`, `exp` with 30 s of clock skew),
as defense in depth. On a public path the `Authorization` header is ignored, so an expired token sent along with a
refresh does not fail it.

### Internal endpoints (CATA-03)

Read by listing-service for the listing page, never routed by the gateway (it only routes `/api/**`). They have
their own security chain: the caller sends the shared token `INTERNAL_API_TOKEN` in `X-Internal-Token`, compared in
constant time; a user's access token does not open them. A missing or wrong token, or an empty `INTERNAL_API_TOKEN`,
gives `401`. Only `ACTIVE` accounts are answered; any other id is `404` `user-not-found`. An assumption of V1: mTLS or
service JWTs in V2.

| Endpoint                                  | Answers                                                                                                            |
| ----------------------------------------- | ------------------------------------------------------------------------------------------------------------------ |
| `GET /internal/users/{id}/public-profile` | `200` `{ displayName, memberSince }` (`memberSince` = month of the registration in Tunis, `2026-03`), `401`, `404` |
| `GET /internal/users/{id}/contact`        | `200` `{ phone }` (E.164) with `Cache-Control: no-store`, `401`, `404`                                             |

### Registration rules

| Field         | Rule                                                                                                                                                                                    | Error codes                                                                             |
| ------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------- |
| `email`       | trimmed and lower-cased, 254 characters at most, `local@domain.tld`                                                                                                                     | `email.required`, `email.invalid`                                                       |
| `password`    | 8 to 128 characters, not in the common password list (case ignored), not equal to the email                                                                                             | `password.required`, `password.length`, `password.too-common`, `password.same-as-email` |
| `displayName` | 2 to 50 characters after trimming, shown publicly on listings                                                                                                                           | `displayName.required`, `displayName.length`                                            |
| `phone`       | valid Tunisian number (libphonenumber, region `TN`, mobile or fixed line), with or without `+216`/`00216`, spaces allowed; stored in E.164 (`+21620123456`); not unique, never returned | `phone.required`, `phone.invalid`                                                       |
| `acceptTerms` | must be `true`; the acceptance date is stored                                                                                                                                           | `terms.required`                                                                        |

The preferred language comes from `Accept-Language` (`fr`, `ar`, `en`, French by default), is stored on the account
and used for all its emails.

The answer never reveals whether an email is known: `register` always answers `202` with an empty body, and the
password is hashed in every case so that the response time does not differ.

| Email state                    | What happens                                                                                                                                      |
| ------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------- |
| new                            | account `PENDING_VERIFICATION`, verification email                                                                                                |
| `ACTIVE` account               | nothing changes, "You already have an account" email (login and forgot-password links) in the account's language                                  |
| `PENDING_VERIFICATION` account | the registration data is replaced by the new one, the older links stop working and a new verification email is sent (counted in the resend limit) |

### Verification and resend

- Token: 32 bytes from `SecureRandom`, Base64 URL; only its SHA-256 is stored. Valid 24 h, single use.
- Link: `{FRONT_BASE_URL}/{lang}/account/verify-email?token=…`.
- `verify-email`: valid token → account `ACTIVE`, `email_verified_at` set, `204`. Unknown, expired or used token →
  `400` with type `urn:soukly:problem:verification-token-invalid` (one answer for the three cases).
- `resend`: always `202`. For a pending account, the older links stop working and a new email is sent, at most 3
  times per hour and per account (registrations of a pending email count too); beyond, nothing is sent.

### Errors

`application/problem+json` built with `com.soukly.common.error.ProblemDetails`. Invalid fields:

```json
{
  "type": "urn:soukly:problem:validation-failed",
  "title": "Validation failed",
  "status": 400,
  "detail": "One or more fields are invalid.",
  "instance": "/users/register",
  "requestId": "6a32b2e8-4d6d-4b39-a79c-af7e8e823d6a",
  "errors": [{ "field": "phone", "code": "phone.invalid" }]
}
```

Each invalid field gets one code, the first rule it breaks. The codes are stable keys translated by the front end.

## Sessions

The access token is a short JWT kept in memory by the front end; the session itself is an opaque refresh token in
a cookie that JavaScript cannot read.

`login` and `refresh` answer:

```json
{ "accessToken": "eyJ…", "tokenType": "Bearer", "expiresIn": 900, "user": { "id": "…", "displayName": "Amira", "preferredLanguage": "ar" }, "firstLogin": false }
```

with `Cache-Control: no-store` and the cookie
`soukly_rt=…; Path=/api/users; Max-Age=2592000; Secure; HttpOnly; SameSite=Strict`.

- **Access token**: JWT RS256 with the `kid` of the active key; claims `iss` (`soukly-user-service`), `sub` (account
  id), `iat`, `exp` (15 min), `jti`, `roles` (`["USER"]`). No email, phone number or name. A token stays valid until
  it expires, even after logout (15 min at most): there is no deny list.
- **Refresh token**: 32 random bytes in Base64 URL, only its SHA-256 stored in `refresh_tokens`. One family per
  sign-in (one device). Each token is used once: `refresh` marks it replaced and issues a new one in the same family,
  valid **30 days from now**, so a user who comes back at least once every 30 days stays signed in with no absolute
  limit. The `User-Agent` is stored (255 characters) for a future list of devices.
- **Reuse detection**: a replaced token presented again means it was copied. The whole family is revoked and both
  the thief and the user get `401 session-revoked`; the user signs in again. Two refreshes sent at the same time with
  the same cookie are treated the same way, so the front end must not run them in parallel (AUTH-03).
- **Errors of `refresh`**: missing, unknown or expired cookie → `401 session-expired`; revoked family (logout or
  reuse) → `401 session-revoked`. Both answers delete the cookie.
- **Logout** revokes the family of the cookie and deletes the cookie, `204` even without a cookie.
- **CSRF**: on top of `SameSite=Strict`, `refresh` and `logout` require an `Origin` header equal to one of
  `ALLOWED_ORIGINS` (the CORS origins of the gateway); a missing or other origin gets `403` before the cookie is read.
- **First login** (AUTH-03): `firstLogin` is `true` on the very first successful login of an account only, so the
  front end shows its optional welcome screen once; it is always `false` on a refresh. Every successful login sets
  `users.last_login_at` (`NULL` until the first one); failed and refused logins do not.
- **Login answers**: unknown email and wrong password give the same `401 invalid-credentials` in the same time (a
  dummy Argon2 hash is checked when the email is unknown). Right password on a `PENDING_VERIFICATION` account:
  `403 email-not-verified`, no token. The email is normalized as in registration.
- **Failure throttling**: after 5 consecutive failures on an email, every attempt waits 30 s, then 1 min, 2 min,
  4 min... up to 15 min; meanwhile `429` with `Retry-After` (seconds), even with the right password, and the password
  is not checked. A successful login resets the counter; an account is never locked for good. Counters are keyed by
  the SHA-256 of the email, known or not, so throttling does not reveal whether an account exists. This comes on top
  of the per-IP limit of the gateway.
- **Purge**: every day at 03:30 UTC, refresh tokens expired or revoked for more than 7 days and failure counters
  idle for a day are deleted.

## Forgotten password (AUTH-04)

- **Request** `forgot`: always `202` with an empty body, whatever the email. For an `ACTIVE` or
  `PENDING_VERIFICATION` account, the unused links of the account stop working and a new one is emailed in the
  account's language: `{FRONT_BASE_URL}/{lang}/account/reset-password?token=…`. At most 3 links per hour and per
  account (`PASSWORD_RESETS_PER_HOUR`); beyond, nothing is sent and nothing tells the caller. The email leaves after
  the commit, on another thread, so the response time does not depend on the account.
- **Token**: 32 random bytes in Base64 URL, only its SHA-256 stored in `password_reset_tokens`; valid 1 hour
  (`PASSWORD_RESET_TOKEN_TTL`), single use.
- **Reset**: `Origin` checked as on `refresh`. An unknown, expired, used or replaced token gives `400` type
  `urn:soukly:problem:reset-token-invalid` (one answer). A password that breaks a registration rule gives
  `400 validation-failed` on the field `newPassword` (`password.length`, `password.too-common`,
  `password.same-as-email`…) and the token stays usable. On success, in one transaction: new Argon2id hash, token
  used and the other links invalidated, **every session family of the account revoked**, login failure counter
  reset, a pending account activated (the link proves the mailbox), and a new session for this device. The answer
  is the one of `login`, with the `soukly_rt` cookie. Access tokens already issued elsewhere stay valid until they
  expire (15 min at most), as after a logout.
- **Confirmation email** `password-changed`: date and time in Tunis, and a link to `/{lang}/account/forgot-password`
  in case it was not the owner. No device information in V1.

## Profile and deletion of the account (AUTH-05)

- **Profile** `{ id, email, pendingEmail, displayName, phone, preferredLanguage, termsVersion, createdAt }` (`phone`
  in E.164, `pendingEmail` left out when no change waits, `termsVersion` the version of the terms of use accepted at
  registration (LEG-01), left out for accounts created before it was recorded). `PATCH` takes `application/merge-patch+json` (or JSON): a member
  left out keeps its value, `null` is refused (`displayName.required`…), other members (the email) are ignored. The
  rules and codes are the ones of the registration, plus `preferredLanguage.invalid`. `preferredLanguage` is the
  language of the emails; the screens follow the language of the URL. The phone number is read by listing-service at
  each "Show the number" (CATA-03), so the new one applies to every listing at once.
- **Current password**: the password, email and deletion actions check it with the login throttling of AUTH-02, on
  the same counter (a wrong current password counts as a failed login, 5 free failures then a growing delay, `429`
  with `Retry-After`). A wrong one gives `400` type `urn:soukly:problem:current-password-invalid`.
- **Change of password**: the rules of the registration on `newPassword`; then every session family of the account
  but the one of the `soukly_rt` cookie sent with the request (path `/api/users`) is revoked, so the device that
  asked stays signed in; the `password-changed` email of AUTH-04 is sent.
- **Change of email**, in two steps. The request (`202`, empty body) replaces any previous one, shows the address in
  `pendingEmail`, emails the link `{FRONT_BASE_URL}/{lang}/account/confirm-email?token=…` to the **new** address
  (`email-change`, valid 24 h, single use, SHA-256 in `email_change_tokens`) and a notice to the **current** one
  (`email-change-requested`, with a link to choose a new password). When the new address has an account, the answer
  is the same and that address gets the `account-exists` email of AUTH-01 instead of the link. The current address
  is refused (`email.unchanged`). At most 3 requests per hour and per account; beyond, nothing is sent and the answer
  is the same. Until the confirmation, the login keeps the current address. `POST /users/email/confirm` is public
  (the link may be opened on another device); an address taken since the request gives `409 email-taken`.
- **Deletion**, in one transaction: status `DELETED`, `deleted_at`, the address moved to `deleted_email` (it is free
  for a new registration at once; kept until the purge to handle fraud), every session family revoked, pending
  verification, reset and email change links invalidated, and `user.deleted` written to the outbox. `Origin` is
  checked as on `logout`, since the response deletes the cookie. A deleted account cannot sign in (`401
invalid-credentials`, as an unknown email), its access tokens get `404` on `/users/me` until they expire, and the
  internal endpoints answer `404`, so its listings are `410` (CATA-03). The `account-deleted` email goes to the
  deleted address.
- **Purge**: every day at 03:45 UTC (`soukly.user.account-purge-cron`), the accounts deleted for more than 30 days
  (`ACCOUNT_PURGE_AFTER`) are erased for good with their tokens and sessions (`ON DELETE CASCADE`) and their login
  failure counter, in batches of 100 locked with `FOR UPDATE SKIP LOCKED`. Their listings are erased by the purge of
  listing-service. The 30 days are an assumption, to confirm with the legal notice (LEG-01) before production.

## User events (AUTH-05)

user-service produces `soukly.user-events` (contract: [`docs/events/user-events.md`](../../docs/events/user-events.md)):
`user.deleted` only, with the account id and no personal data. It is written to `outbox_events` in the transaction of
the deletion, then sent by the relay of `com.soukly.common.outbox`, the same code as listing-service's (ANN-03):
every second, oldest first, key `userId`, marked sent once acknowledged, kept in the outbox while the broker is down;
sent events are deleted after 7 days (`soukly.user.outbox.*`). user-service declares `soukly.user-events` (3
partitions) and `soukly.user-events.dlq` at startup, locally and in dev; on the VM, `init-topics.sh` creates them.

## Signing keys

- RSA, 2048 bits at least, private key in PEM PKCS#8 (`-----BEGIN PRIVATE KEY-----`) read from
  `JWT_PRIVATE_KEY_PATH`; `kid` from `JWT_KEY_ID`. The service refuses to start when the key is missing, and the
  message gives the command to run.
- **Locally**: `scripts/gen-jwt-key.sh` (from the repository root) writes `.secrets/jwt/jwt-private.pem` and
  `jwt-public.pem`, ignored by git; the default `JWT_PRIVATE_KEY_PATH` points there from `backend/user-service`.
- **Dev and prod**: keys generated on the VM by `infra/vm/gen-jwt-keys.sh <env>` and mounted read-only, never in an
  image or in the repository.
- **Rotation**: generate a new pair with a new `JWT_KEY_ID`, and set `JWT_PREVIOUS_KEY_ID` and
  `JWT_PREVIOUS_PUBLIC_KEY_PATH` to the `kid` and public key of the old pair. The JWKS publishes both, so tokens
  signed by the old key stay valid until they expire; remove the previous key after 15 minutes. New tokens are
  always signed by the active key, and sessions are not affected. On the VM: `infra/vm/README.md`, section 6.
- `GET /.well-known/jwks.json` returns the public keys (`kty`, `kid`, `use: sig`, `alg: RS256`, `n`, `e`) with
  `Cache-Control: max-age=300`. The gateway reads it from `USER_SERVICE_JWKS_URI` and reloads it when a token
  carries an unknown `kid`.

## Emails

- Sent after the transaction commits (nothing is sent when it fails), on a separate thread. A failed send is logged
  with the user id and the exception type only, and never fails the request: the user can ask for a resend.
- Templates (Thymeleaf, HTML and plain text) in `src/main/resources/mail/`: `verification`, `account-exists`,
  `password-reset`, `password-changed`, `email-change`, `email-change-requested`, `account-deleted` and the shared
  `layout.html`. Texts in `mail/messages_{fr,ar,en}.properties`; Arabic emails have `dir="rtl"`.
- The SMTP health check is disabled: an SMTP outage must not mark the service down.
- Exception: the expiry reminder (below) is sent synchronously by the event consumer, so that a failure is retried.

## Listing events (ANN-03)

user-service consumes `soukly.listing-events` (contract: [`docs/events/listing-events.md`](../../docs/events/listing-events.md))
in the consumer group `user-service`, from the earliest offset the first time. It handles **`listing.expiring-soon`
only** and ignores every other type, known or not.

- **Reminder email** `listing-expiring` (templates `mail/listing-expiring.{html,txt}`, texts in the three languages):
  sent to the seller (`ownerId`) in their preferred language, with the title of the listing, its expiry date (long
  date of the language in the time zone of Tunisia, Western digits in Arabic) and a "Put back online" button to
  `{FRONT_BASE_URL}/{lang}/account/listings` (page "My listings", ANN-05). Nothing is sent to an unknown or
  non-`ACTIVE` account.
- **Deduplication**: the `eventId` is inserted in `processed_events` in the transaction of the email. An event
  delivered again finds it and sends nothing; an email that fails rolls the row back, so the retry sends it.
- **Errors**: a failed message is retried 3 times, 2 s apart (`soukly.user.listing-events.*`), then published to
  `soukly.listing-events.dlq` with the cause in the `kafka_dlt-*` headers, and the consumer moves on. A message that
  does not follow the contract (malformed JSON, missing member, unknown `schemaVersion`) goes to the DLQ at once. The
  error messages and logs name the account by id, never by address. A message of the DLQ is replayed by producing its
  value again to `soukly.listing-events` (for example from Redpanda Console).
- `processed_events` keeps every id; at V1 volumes (a few rows per listing) no purge is needed yet.

## Configuration

| Variable                                              | Local default                                        | Use                                                                             |
| ----------------------------------------------------- | ---------------------------------------------------- | ------------------------------------------------------------------------------- |
| `SPRING_DATASOURCE_URL`                               | `jdbc:postgresql://localhost:5432/soukly_user`       | database                                                                        |
| `SPRING_DATASOURCE_USERNAME`                          | `user_svc`                                           | database user                                                                   |
| `SPRING_DATASOURCE_PASSWORD`                          | `USER_SVC_DB_PASSWORD`                               | database password                                                               |
| `MAIL_HOST`, `MAIL_PORT`                              | `localhost`, `1025` (Mailpit)                        | SMTP server                                                                     |
| `MAIL_USERNAME`, `MAIL_PASSWORD`                      | empty                                                | SMTP credentials                                                                |
| `MAIL_FROM`                                           | `no-reply@example.com`                               | sender                                                                          |
| `FRONT_BASE_URL`                                      | `http://localhost:4200`                              | origin of the links in the emails                                               |
| `USER_SERVICE_PORT`                                   | `8081`                                               | HTTP port                                                                       |
| `JWT_PRIVATE_KEY_PATH`                                | `../../.secrets/jwt/jwt-private.pem`                 | active private key (PEM PKCS#8)                                                 |
| `JWT_KEY_ID`                                          | `local`                                              | `kid` of the active key                                                         |
| `JWT_PREVIOUS_KEY_ID`, `JWT_PREVIOUS_PUBLIC_KEY_PATH` | empty                                                | previous key during a rotation                                                  |
| `JWT_ISSUER`                                          | `soukly-user-service`                                | `iss` claim, same value on the gateway                                          |
| `ACCESS_TOKEN_TTL`                                    | `15m`                                                | lifetime of an access token                                                     |
| `REFRESH_TOKEN_TTL`                                   | `30d`                                                | lifetime of a refresh token, renewed by every refresh                           |
| `REFRESH_COOKIE_PATH`                                 | `/api/users`                                         | cookie path seen by the browser (the gateway removes `/api`)                    |
| `REFRESH_COOKIE_SECURE`                               | `true`                                               | `Secure` attribute of the cookie, see below                                     |
| `ALLOWED_ORIGINS`                                     | `CORS_ALLOWED_ORIGINS`, else `http://localhost:4200` | origins allowed on `refresh`, `logout`, `password/reset` and `DELETE /users/me` |
| `KAFKA_BOOTSTRAP_SERVERS`                             | `localhost:19092`                                    | Kafka API of Redpanda (listing and user events)                                 |
| `INTERNAL_API_TOKEN`                                  | empty (internal endpoints refused)                   | shared token of `/internal/**` (CATA-03)                                        |
| `PASSWORD_RESET_TOKEN_TTL`                            | `1h`                                                 | validity of a password reset link (AUTH-04)                                     |
| `PASSWORD_RESETS_PER_HOUR`                            | `3`                                                  | reset links an account may receive per hour                                     |
| `ACCOUNT_PURGE_AFTER`                                 | `30d`                                                | a deleted account is erased for good after this (AUTH-05)                       |
| `LEGAL_TERMS_VERSION`                                 | `version` of `frontend/legal/<lang>/terms.md`        | version of the terms recorded at registration in `terms_version` (LEG-01)       |

`REFRESH_COOKIE_SECURE` stays `true` everywhere, including locally: Chrome (89+) and Firefox (75+) accept a
`Secure` cookie on `http://localhost`. Safari does not, so a developer testing in Safari over plain HTTP sets it to
`false` on their machine only.

## Security notes

- Passwords: Argon2id through Spring Security's `Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()`, still the
  recommended parameters in Spring Security 7.1 (16-byte salt, 32-byte hash, parallelism 1, 16 MiB, 2 iterations).
  It needs Bouncy Castle (`bcprov-jdk18on`).
- Never logged: passwords, tokens (access, refresh, verification), cookie values, emails, phone numbers or request
  bodies; the request and session records hide their fields in `toString`. Logs name accounts and session families
  by id only.

## Common password list

`src/main/resources/security/common-passwords.txt` is `Passwords/Common-Credentials/Pwdb_top-10000.txt` from
[SecLists](https://github.com/danielmiessler/SecLists) (MIT licence, Daniel Miessler), commit
`0c03d70db30ceedb941ac58b0ec910eb85906d6f`, unchanged (10,000 entries,
SHA-256 `d9a018818f2357ac34c0534bdfd67826811859ae858bfd6398559085c7f4e925`). The SecLists
`10k-most-common.txt` list was not used because it does not contain `password123`.

## Tests

```bash
cd backend && ./mvnw -pl user-service -am verify    # Docker required (Testcontainers)
```

`RegistrationTests` covers every Gherkin scenario of AUTH-01 against PostgreSQL 18.6 (Testcontainers, same image as
`docker-compose.yml`) and GreenMail, an in-memory SMTP server: no real email service is ever used. `LoginTests`,
`SessionTests` and `JwksTests` cover the scenarios of AUTH-02 with RSA keys generated for the run and a clock the
tests move (`MutableClock`) for expirations and delays. `ListingReminderTests` covers the consumer of ANN-03 against
Redpanda (Testcontainers, same image as `docker-compose.yml`): reminder in the three languages, the same event
delivered twice, other types and accounts ignored, DLQ after the retries and at once for a malformed message.
`ProfileTests` and `AccountDeletionTests` cover AUTH-05: profile update, password change with the other devices
signed out, the email change from the request to the link, an address of another account, the deletion with
`user.deleted` read back from Redpanda, and the purge. The nominal sign-in through the real gateway and the deletion
of an account with its listings (user-service, listing-service and Redpanda) are in `backend/e2e-tests`.
