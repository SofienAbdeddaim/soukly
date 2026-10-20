# user-service

Accounts of Soukly: registration and email verification (AUTH-01), sign-in, sessions and the JWKS (AUTH-02). The
screens arrive with AUTH-03. Port 8081, package `com.soukly.user`, database `soukly_user` (user `user_svc`).

## Endpoints

Exposed by the gateway under `/api` (`POST /api/users/register` reaches this service as `POST /users/register`).

| Endpoint | Body | Answers |
| --- | --- | --- |
| `POST /users/register` | `{ email, password, displayName, phone, acceptTerms }` | `202`, `400` |
| `POST /users/verify-email` | `{ token }` | `204`, `400` |
| `POST /users/verify-email/resend` | `{ email }` | `202`, `400` (malformed email) |
| `POST /users/login` | `{ email, password }` | `200`, `401` `invalid-credentials`, `403` `email-not-verified`, `429` |
| `POST /users/refresh` | cookie `soukly_rt`, header `Origin` | `200`, `401` `session-expired` or `session-revoked`, `403` |
| `POST /users/logout` | cookie `soukly_rt` (optional), header `Origin` | `204`, `403` |
| `GET /.well-known/jwks.json` | - | `200`; internal only, never routed by the gateway |

These endpoints and `GET /actuator/health` are public. Every other path needs a valid access token: the service
validates it itself with its own keys, with the gateway's rules (RS256 only, `iss`, `exp` with 30 s of clock skew),
as defense in depth. On a public path the `Authorization` header is ignored, so an expired token sent along with a
refresh does not fail it.

### Registration rules

| Field | Rule | Error codes |
| --- | --- | --- |
| `email` | trimmed and lower-cased, 254 characters at most, `local@domain.tld` | `email.required`, `email.invalid` |
| `password` | 8 to 128 characters, not in the common password list (case ignored), not equal to the email | `password.required`, `password.length`, `password.too-common`, `password.same-as-email` |
| `displayName` | 2 to 50 characters after trimming, shown publicly on listings | `displayName.required`, `displayName.length` |
| `phone` | valid Tunisian number (libphonenumber, region `TN`, mobile or fixed line), with or without `+216`/`00216`, spaces allowed; stored in E.164 (`+21620123456`); not unique, never returned | `phone.required`, `phone.invalid` |
| `acceptTerms` | must be `true`; the acceptance date is stored | `terms.required` |

The preferred language comes from `Accept-Language` (`fr`, `ar`, `en`, French by default), is stored on the account
and used for all its emails.

The answer never reveals whether an email is known: `register` always answers `202` with an empty body, and the
password is hashed in every case so that the response time does not differ.

| Email state | What happens |
| --- | --- |
| new | account `PENDING_VERIFICATION`, verification email |
| `ACTIVE` account | nothing changes, "You already have an account" email (login and forgot-password links) in the account's language |
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
{ "accessToken": "eyJ…", "tokenType": "Bearer", "expiresIn": 900,
  "user": { "id": "…", "displayName": "Amira", "preferredLanguage": "ar" } }
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
- Templates (Thymeleaf, HTML and plain text) in `src/main/resources/mail/`: `verification`, `account-exists` and
  the shared `layout.html`. Texts in `mail/messages_{fr,ar,en}.properties`; Arabic emails have `dir="rtl"`.
- The SMTP health check is disabled: an SMTP outage must not mark the service down.

## Configuration

| Variable | Local default | Use |
| --- | --- | --- |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/soukly_user` | database |
| `SPRING_DATASOURCE_USERNAME` | `user_svc` | database user |
| `SPRING_DATASOURCE_PASSWORD` | `USER_SVC_DB_PASSWORD` | database password |
| `MAIL_HOST`, `MAIL_PORT` | `localhost`, `1025` (Mailpit) | SMTP server |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | empty | SMTP credentials |
| `MAIL_FROM` | `no-reply@example.com` | sender |
| `FRONT_BASE_URL` | `http://localhost:4200` | origin of the links in the emails |
| `USER_SERVICE_PORT` | `8081` | HTTP port |
| `JWT_PRIVATE_KEY_PATH` | `../../.secrets/jwt/jwt-private.pem` | active private key (PEM PKCS#8) |
| `JWT_KEY_ID` | `local` | `kid` of the active key |
| `JWT_PREVIOUS_KEY_ID`, `JWT_PREVIOUS_PUBLIC_KEY_PATH` | empty | previous key during a rotation |
| `JWT_ISSUER` | `soukly-user-service` | `iss` claim, same value on the gateway |
| `ACCESS_TOKEN_TTL` | `15m` | lifetime of an access token |
| `REFRESH_TOKEN_TTL` | `30d` | lifetime of a refresh token, renewed by every refresh |
| `REFRESH_COOKIE_PATH` | `/api/users` | cookie path seen by the browser (the gateway removes `/api`) |
| `REFRESH_COOKIE_SECURE` | `true` | `Secure` attribute of the cookie, see below |
| `ALLOWED_ORIGINS` | `CORS_ALLOWED_ORIGINS`, else `http://localhost:4200` | origins allowed on `refresh` and `logout` |

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
tests move (`MutableClock`) for expirations and delays. The nominal scenario through the real gateway is in
`backend/e2e-tests`.
