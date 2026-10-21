# Soukly

Classified ads site (sales and services) for Tunisia, in three languages (French by default, Arabic in RTL, English), with automatic publishing to Facebook and Instagram.

This single repository holds two code roots:

- `frontend/`: Nx workspace (pnpm) for the Angular shell and micro frontends (Native Federation).
- `backend/`: multi-module Maven project, Java 21 / Spring Boot.

All documentation, comments, commits and PR descriptions in this repository are written in English.

## Prerequisites and pinned versions

| Tool                                      | Version                                                                          |
| ----------------------------------------- | -------------------------------------------------------------------------------- |
| Node.js                                   | 24 LTS (`frontend/.nvmrc`); 22.12 minimum                                        |
| pnpm                                      | 10.28.0 (`packageManager` in `frontend/package.json`); npm and yarn are not used |
| Nx                                        | 23.3.0                                                                           |
| Angular (devkit, schematics, build, CLI)  | 22.2.2                                                                           |
| Angular (framework, CDK)                  | 22.2.3                                                                           |
| Tailwind CSS                              | 4.3.3                                                                            |
| Transloco (`@jsverse/transloco`)          | 8.4.0                                                                            |
| angular-eslint                            | 22.6.0                                                                           |
| TypeScript                                | 6.0.x                                                                            |
| Java                                      | 21                                                                               |
| Maven                                     | 3.9.16 through the Maven Wrapper (`backend/mvnw`)                                |
| Spring Boot                               | 4.0.8                                                                            |
| Spring Cloud                              | 2025.1.3                                                                         |
| libvips tools (photos of listing-service) | 8.15 (`apt install libvips-tools`; `brew install vips` on macOS)                 |

Nx and Angular versions are pinned (no `^`). No milestone or RC version is used.

## Getting started

```bash
cp .env.example .env   # never committed
```

### Frontend

```bash
cd frontend
pnpm install --frozen-lockfile
pnpm nx run-many -t lint test build
pnpm nx run-many -t i18n-check   # same translation keys in fr, ar and en, for every scope
pnpm prettier --check .
```

| Project                                      | Role                                                                                                                                                      | Commands                                                                                                                                     |
| -------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------- |
| `libs/auth` (`@soukly/auth`)                 | front-end session: `AuthService` (access token in memory), `authInterceptor`, `authGuard`, session restore ([README](frontend/libs/auth/README.md))       | `pnpm nx test auth`, `pnpm nx lint auth`                                                                                                     |
| `libs/i18n` (`@soukly/i18n`)                 | languages, URL prefix, Transloco setup, `price`/`localDate`/`relativeTime` pipes ([README](frontend/libs/i18n/README.md))                                 | `pnpm nx test i18n`, `pnpm nx lint i18n`                                                                                                     |
| `libs/listing-data` (`@soukly/listing-data`) | listing API clients (drafts, photos, referentials), photo reduction before upload, test fixtures ([README](frontend/libs/listing-data/README.md))         | `pnpm nx test listing-data`, `pnpm nx lint listing-data`                                                                                     |
| `libs/ui` (`@soukly/ui`)                     | design system: tokens, Tailwind theme, fonts, base components, RTL lint rule ([README](frontend/libs/ui/README.md))                                       | `pnpm nx test ui`, `pnpm nx lint ui`                                                                                                         |
| `apps/ui-showcase`                           | visual check page of the components in LTR and RTL, never deployed                                                                                        | `pnpm nx serve ui-showcase` (http://localhost:4200), `pnpm nx test ui-showcase` (axe test)                                                   |
| `apps/shell`                                 | host: header, footer, mobile bottom bar, loading of the remotes, error and 404 pages                                                                      | `pnpm nx serve shell` (http://localhost:4200)                                                                                                |
| `apps/mfe-account`                           | remote, account area (AUTH): registration, email verification, login, welcome screen                                                                      | `pnpm nx serve mfe-account` (http://localhost:4201)                                                                                          |
| `apps/mfe-catalog`                           | remote, home page and search results with filters (CATA-02)                                                                                               | `pnpm nx serve mfe-catalog` (http://localhost:4202)                                                                                          |
| `apps/mfe-publish`                           | remote, publishing and management of listings (ANN, SOC): publish form in 5 steps and its confirmation (ANN-04), dev server proxies `/api` to the gateway | `pnpm nx serve mfe-publish` (http://localhost:4203)                                                                                          |
| `apps/shell-e2e`                             | Playwright end-to-end tests of the shell and its remotes (Chromium)                                                                                       | `pnpm nx e2e shell-e2e` (starts the 4 dev servers, fakes the API), `pnpm nx run shell-e2e:e2e-stack` (against the local back end, see below) |
| `tools/i18n-check`                           | translation key check, run by the `i18n-check` target of the shell and of each remote                                                                     | `pnpm nx run-many -t i18n-check`, `pnpm nx test i18n-check`                                                                                  |

The `@soukly/<lib>` aliases point to `libs/<lib>/src/index.ts` (`tsconfig.base.json`).

### Running the shell with its remotes

Native Federation loads the remotes at runtime. Start the four dev servers, each in its own terminal, then open http://localhost:4200 (redirected to `/fr`, `/ar` or `/en`, see [Languages](frontend/libs/i18n/README.md)):

```bash
cd frontend
pnpm nx serve shell          # 4200, reads public/federation.manifest.json
pnpm nx serve mfe-account    # 4201
pnpm nx serve mfe-catalog    # 4202
pnpm nx serve mfe-publish    # 4203
```

- A remote can also run alone (for example `pnpm nx serve mfe-catalog`, then http://localhost:4202): it mounts its routes under the language prefix with a minimal layout, without the shell.
- `apps/shell/public/federation.manifest.json` maps each remote name to the URL of its `remoteEntry.json`. The shell reads it before Angular starts, so another environment changes the URLs by replacing that file, without rebuilding the shell. The repository only holds the local version: in the `soukly-frontend` image, the manifest is written at container startup from `PUBLIC_BASE_URL` (see [Docker images](#docker-images)), never committed with real addresses.
- Shared dependencies (Angular, rxjs, `@angular/cdk`, `@lucide/angular`, `@soukly/*`) are declared once in `frontend/federation.shared.mjs` and used by every `federation.config.mjs`: each one is a singleton with a strict version, so the browser downloads Angular once.
- Every page URL starts with its language: `/fr/account/login`, `/ar/publish/new`. Each remote ships its translations in `public/i18n/<scope>/`, downloaded from the remote itself when one of its pages is shown.
- If a remote does not load, the content area shows an error with a retry button; the header and the footer stay.
- Playwright end-to-end tests: `pnpm nx e2e shell-e2e` (Chromium). The first run needs `pnpm exec playwright install --with-deps chromium`. Set `CHROMIUM_EXECUTABLE_PATH` to use a Chromium already installed.
- **API proxy**: the dev servers of the shell and of `mfe-account` forward `/api/**` to the gateway on `http://localhost:8080` (`apps/<app>/proxy.conf.json`, `proxyConfig` option of `@angular/build:dev-server`). The front end and the API then share one origin, as behind Caddy in dev and prod, which the `SameSite=Strict` refresh cookie needs. Without a running gateway, the API calls fail and the visitor simply stays signed out.
- **Account screens against the real back end** (`apps/shell-e2e/src/stack`, not run by the CI, which has no back end; there `src/account.spec.ts` covers the same screens with a faked API):

  ```bash
  docker compose up -d --wait postgres mailpit    # from the repository root
  scripts/gen-jwt-key.sh                           # once
  cd backend && ./mvnw install -DskipTests         # then, with the root .env sourced:
  ./mvnw -pl user-service spring-boot:run          # 8081, in one terminal
  ./mvnw -pl gateway spring-boot:run               # 8080, in another
  cd ../frontend && pnpm nx run shell-e2e:e2e-stack  # starts the 4 dev servers if needed
  ```

  The tests register new accounts, read the verification link in Mailpit (`http://localhost:8025`) and log in through the gateway.

### Backend

```bash
cd backend
./mvnw -B verify                                  # compiles and tests common, the 4 services and e2e-tests
./mvnw -pl listing-service spring-boot:run        # runs one service
curl http://localhost:8082/actuator/health        # {"status":"UP",...}
```

`spring-boot:run` on a service uses `common` from the local Maven repository: run `./mvnw install -DskipTests` once before, and again after changing `common`.

| Service                                   | Port | Package              |
| ----------------------------------------- | ---- | -------------------- |
| `gateway` (Spring Cloud Gateway, WebFlux) | 8080 | `com.soukly.gateway` |
| `user-service`                            | 8081 | `com.soukly.user`    |
| `listing-service`                         | 8082 | `com.soukly.listing` |
| `social-publisher`                        | 8083 | `com.soukly.social`  |
| `common` (library)                        | -    | `com.soukly.common`  |

Every service exposes `GET /actuator/health`.

`backend/e2e-tests` holds end-to-end tests only: they start the packaged `gateway` and `user-service` jars as two processes in front of PostgreSQL (Testcontainers) and go through the gateway like the browser (AUTH-02: login, protected route, refresh, logout, forged token). `./mvnw verify` packages the services before running them; alone, run `./mvnw -pl e2e-tests -am verify`.

## user-service

Accounts of Soukly (package `com.soukly.user`, database `soukly_user`, schema managed by Flyway in `user-service/src/main/resources/db/migration`). Details, rules and error codes: [`backend/user-service/README.md`](backend/user-service/README.md).

| Endpoint (through the gateway)        | Body                                                                             | Answers                                                                                                 |
| ------------------------------------- | -------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------- |
| `POST /api/users/register`            | `{ email, password, displayName, phone, acceptTerms }`, header `Accept-Language` | `202` (always the same, new or known email), `400`                                                      |
| `POST /api/users/verify-email`        | `{ token }`                                                                      | `204`, `400` `verification-token-invalid`                                                               |
| `POST /api/users/verify-email/resend` | `{ email }`                                                                      | `202` (always), `400` malformed email                                                                   |
| `POST /api/users/login`               | `{ email, password }`                                                            | `200` access token + `soukly_rt` cookie, `401` `invalid-credentials`, `403` `email-not-verified`, `429` |
| `POST /api/users/refresh`             | cookie `soukly_rt`, header `Origin`                                              | `200` new access token + new cookie, `401` `session-expired` / `session-revoked`, `403`                 |
| `POST /api/users/logout`              | cookie `soukly_rt`, header `Origin`                                              | `204`, `403`                                                                                            |

Sessions last **30 days, renewed at every refresh**: the access token (JWT RS256) lives 15 minutes, the refresh token rotates at each use and a reused one revokes its session. Rules, keys and rotation: [`backend/user-service/README.md`](backend/user-service/README.md#sessions).

Running it locally against the Docker infrastructure (PostgreSQL and Mailpit):

```bash
docker compose up -d --wait                                       # from the repository root
scripts/gen-jwt-key.sh                                            # once: local JWT key in .secrets/jwt/ (ignored by git)
cd backend && ./mvnw install -DskipTests                          # once
(set -a; . ../.env; set +a; ./mvnw -pl user-service spring-boot:run)   # 8081, reads USER_SVC_DB_PASSWORD from .env
./mvnw -pl gateway spring-boot:run                                # 8080, in another terminal
curl -i -X POST http://localhost:8080/api/users/register -H 'Content-Type: application/json' -H 'Accept-Language: ar' \
  -d '{"email":"amira@example.tn","password":"Jasmin-de-Carthage-2026","displayName":"Amira","phone":"20 123 456","acceptTerms":true}'
```

Then sign in, call a protected path, renew and sign out with a cookie jar (the `Origin` header is required on `refresh` and `logout`):

```bash
curl -i -c jar -X POST http://localhost:8080/api/users/login -H 'Content-Type: application/json' \
  -d '{"email":"amira@example.tn","password":"Jasmin-de-Carthage-2026"}'          # 200, Set-Cookie soukly_rt
curl -i http://localhost:8080/api/users/me -H "Authorization: Bearer <accessToken>"  # passes the gateway (404 until AUTH-05)
curl -i -b jar -c jar -X POST http://localhost:8080/api/users/refresh -H 'Origin: http://localhost:4200'   # 200
curl -i -b jar -c jar -X POST http://localhost:8080/api/users/logout -H 'Origin: http://localhost:4200'    # 204
```

curl keeps the `Secure` cookie on `http://localhost` like Chrome and Firefox do.

The verification email appears in Mailpit (http://localhost:8025); its link targets `FRONT_BASE_URL/{lang}/account/verify-email?token=…` (the page arrives with AUTH-03). The tests use Testcontainers (Docker required) and an in-memory SMTP server.

## listing-service

Listings of Soukly (package `com.soukly.listing`, database `soukly_listing`): the read-only referentials (CAT-01), the drafts of listings (ANN-01) and their photos (ANN-02: uploaded by the browser straight to S3 with a presigned URL, then converted to WebP by libvips, which must be installed, see the prerequisites). Details, rules and error codes: [`backend/listing-service/README.md`](backend/listing-service/README.md).

| Endpoint (through the gateway)                                                            | Answers                                                               |
| ----------------------------------------------------------------------------------------- | --------------------------------------------------------------------- |
| `GET /api/categories`, `GET /api/categories/{code}/attributes`                            | `200`, `404` `category-not-found`                                     |
| `GET /api/locations/governorates[/{code}/delegations]`                                    | `200`, `404` `governorate-not-found`                                  |
| `POST /api/listings` (JWT)                                                                | `201` draft + `ETag`, `400`, `401`, `409` `draft-limit-reached`       |
| `PATCH /api/listings/{id}` (JWT, JSON Merge Patch, `If-Match`)                            | `200` + `ETag`, `400`, `401`, `404`, `412` `version-conflict`, `428`  |
| `GET /api/listings/{id}` (public, owner's JWT for the full view)                          | `200`, `404` (draft of another account), `410` `listing-gone`         |
| `POST /api/listings/{id}/contact` (JWT)                                                   | `200` `{ phone }`, `401`, `404`, `409` own listing, `410`, `429`      |
| `POST /api/listings/{id}/photos` (JWT)                                                    | `201` presigned upload URL, `400`, `404`, `409` `photo-limit-reached` |
| `POST /api/listings/{id}/photos/{photoId}/complete` (JWT)                                 | `200` READY photo, `400` `photo-rejected`, `404`                      |
| `PUT /api/listings/{id}/photos/order`, `DELETE /api/listings/{id}/photos/{photoId}` (JWT) | `200`, `400`, `404` / `204`, `404`                                    |

listing-service validates the access tokens again with the rules of the gateway, from the same `USER_SERVICE_JWKS_URI` and `JWT_ISSUER`. It reads the sellers from the internal endpoints of user-service (`/internal/**`, `USER_SERVICE_URL`), with the shared `INTERNAL_API_TOKEN` in `X-Internal-Token` (CATA-03); the gateway never routes `/internal/**`.

## Gateway

The front end only calls the gateway (`http://localhost:8080`, Spring Cloud Gateway on WebFlux). It routes to the services, applies CORS, checks the JWT, adds `X-Request-Id`, limits the authentication endpoints and answers its own errors in `application/problem+json`.

### Routes

The `/api` prefix is removed before forwarding: `GET /api/listings/42` reaches `listing-service` as `GET /listings/42`.

| Incoming path                                                          | Target service     | Variable (local default)                        |
| ---------------------------------------------------------------------- | ------------------ | ----------------------------------------------- |
| `/api/users/**`                                                        | `user-service`     | `USER_SERVICE_URL` (`http://localhost:8081`)    |
| `/api/listings/**`, `/api/categories/**`, `/api/locations/**` (CAT-01) | `listing-service`  | `LISTING_SERVICE_URL` (`http://localhost:8082`) |
| `/api/social/**`                                                       | `social-publisher` | `SOCIAL_SERVICE_URL` (`http://localhost:8083`)  |

### Access policy

The whitelist lives in a single class, `gateway/.../security/PublicPaths.java`. Everything not listed needs a valid JWT.

| Access       | Paths                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| ------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Public       | `POST /api/users/register`, `POST /api/users/verify-email`, `POST /api/users/verify-email/resend` (AUTH-01), `POST /api/users/login`, `POST /api/users/refresh`, `POST /api/users/logout` (AUTH-02, the refresh cookie authenticates them), `POST /api/users/password/**`, `GET /api/listings/**`, `GET /api/categories/**` and `GET /api/locations/**` (referentials, CAT-01), `GET /api/actuator/info` (version of the running images, DEP-03), `GET /actuator/health` |
| JWT required | everything else, for example `POST/PUT/PATCH/DELETE /api/listings/**` (including `POST /api/listings/{id}/contact`, CATA-03), `/api/users/me/**`, `/api/social/**`                                                                                                                                                                                                                                                                                                                                                              |

- **JWKS**: the gateway reads the public keys from `USER_SERVICE_JWKS_URI` (internal address of `user-service`); `/.well-known/jwks.json` is never routed to the outside.
- **JWT**: RS256 only (`none`, HS256 and any other algorithm are rejected); signature checked with the public keys of the `user-service` JWKS (`USER_SERVICE_JWKS_URI`), cached and fetched again when a token carries an unknown `kid` (key rotation); `exp` with a 30 s clock skew; `iss` equal to `JWT_ISSUER`. No secret is shared between services.
- Missing or invalid token on a protected path: `401`, `WWW-Authenticate: Bearer`, and the request never reaches the service. On a public path the token is not checked and is forwarded as is.
- The `Authorization` header is forwarded unchanged; the services validate it again themselves (AUTH and ANN tickets). The gateway adds no identity header.
- **CORS**: exact origins from `CORS_ALLOWED_ORIGINS` (never `*`, the gateway refuses to start otherwise), credentials allowed, methods `GET, POST, PUT, PATCH, DELETE, OPTIONS`, headers `Authorization, Content-Type, Accept-Language, X-Request-Id`, `X-Request-Id` exposed, preflight cached 1 h.
- **Correlation**: the client's `X-Request-Id` is kept when it is a safe identifier (letters, digits, `.`, `_`, `-`, 128 characters at most), otherwise the gateway generates a UUID. It is forwarded to the service, returned in the response and written in the logs and error bodies.
- **Rate limit**: `POST /api/users/login`, `POST /api/users/register` and `POST /api/users/verify-email/resend`, each with its own budget of `RATE_LIMIT_AUTH_PER_MINUTE` requests per minute and per IP (10 by default), then `429` with `Retry-After`; the search `GET /api/listings` likewise with `RATE_LIMIT_SEARCH_PER_MINUTE` (120 by default, CATA-01). In memory, for a single gateway instance. `X-Forwarded-For` is only read when the connection comes from `GATEWAY_TRUSTED_PROXIES` (the reverse proxy, set by DEP); otherwise the connection IP is used.
- **Service down**: `503` when the service refuses the connection or does not accept it within `GATEWAY_CONNECT_TIMEOUT_MS` (2 s); `504` when it does not answer within `GATEWAY_RESPONSE_TIMEOUT` (10 s).
- **Logs**: one line per request (`com.soukly.gateway.access`: method, path without query string, status, duration, `requestId`), never headers, cookies or bodies.

### Error format

Every error is `application/problem+json` (RFC 9457), built by `com.soukly.common.error.ProblemDetails` in `common` so the services return the same format:

```json
{
  "type": "urn:soukly:problem:unauthorized",
  "title": "Unauthorized",
  "status": 401,
  "detail": "A valid bearer token is required.",
  "instance": "/api/listings",
  "requestId": "4f0b7a8e-2c1d-4e57-9a43-0c5f1e8b9d21"
}
```

`type` is a URN (`urn:soukly:problem:<slug>`), so no domain is hard-coded. Generic errors use the HTTP reason as slug (`not-found`, `too-many-requests`, `service-unavailable`); a service defines its own slugs for business errors with `ProblemDetails.type("email-taken")`. Clients branch on `type` or `status`, never on `title` or `detail`.

### Trying it locally

```bash
cd backend
./mvnw install -DskipTests                       # once
./mvnw -pl gateway spring-boot:run               # 8080
./mvnw -pl listing-service spring-boot:run       # 8082, in another terminal
curl -i http://localhost:8080/actuator/health    # 200, X-Request-Id in the response
curl -i -X POST http://localhost:8080/api/listings   # 401 problem+json
```

The integration tests (`gateway/src/test`) play `user-service` (with its JWKS), `listing-service` and a stopped `social-publisher` with local fake servers, and sign tokens with RSA keys generated during the test run (no key is committed).

## Local infrastructure

Docker (with Compose v2) is enough: PostgreSQL, Redpanda (Kafka API), Garage (S3 storage) and Mailpit (test SMTP) start with one command, with nothing else to install.

```bash
cp .env.example .env                           # once; dummy values to replace (change-me)
docker compose up -d --wait                    # starts the infrastructure and waits until it is healthy
./scripts/infra-check.sh                       # checks databases, Kafka, S3, CORS, idempotence, tools profile
docker compose --profile tools up -d --wait    # adds Redpanda Console and pgAdmin
docker compose down                            # stops, keeping the data
docker compose down -v                         # stops and deletes the data (full reset)
```

Without `.env`, Compose refuses to start and names the missing variable. On first start, the one-shot `garage-init` service creates the Garage layout, imports the `soukly-app` key (ID and secret read from `.env`), creates the `soukly-listings` bucket, grants it read and write, then applies the CORS rule and the website access (public read of `listings/` only). It can be re-run safely: `docker compose run --rm garage-init`.

| Service            | Host port(s)                                               | Use                           | Image                           |
| ------------------ | ---------------------------------------------------------- | ----------------------------- | ------------------------------- |
| `postgres`         | 5432                                                       | database                      | `postgres:18.6`                 |
| `redpanda`         | 19092 (external Kafka), 9644 (admin)                       | broker                        | `redpandadata/redpanda:v26.2.4` |
| `garage`           | 3900 (S3 API), 3902 (website, public photos), 3903 (admin) | photo storage                 | `dxflrs/garage:v2.4.1`          |
| `mailpit`          | 1025 (SMTP), 8025 (web UI)                                 | test emails                   | `axllent/mailpit:v1.31.5`       |
| `garage-init`      | -                                                          | one-shot Garage setup         | `amazon/aws-cli:2.38.0`         |
| `redpanda-console` | 8090                                                       | Kafka UI (`tools` profile)    | `redpandadata/console:v3.12.0`  |
| `pgadmin`          | 5050                                                       | Postgres UI (`tools` profile) | `dpage/pgadmin4:9.18.0`         |

Every image is pinned to an exact version published for `linux/amd64` and `linux/arm64` (checked on 2026-10-14). PostgreSQL is on the latest patch of version 18 (`18.6`).

- **PostgreSQL**: one database and one user per service (`soukly_user` / `user_svc`, `soukly_listing` / `listing_svc`, `soukly_social` / `social_svc`), created on first start by `infra/postgres/init/01-databases.sh`. Each user can only connect to its own database. The script only runs on an empty volume: after changing a password in `.env`, reset with `down -v`.
- **Redpanda**: one node in `dev-container` mode. Services running outside Docker use `localhost:19092`, containers use `redpanda:9092`. Topics are created automatically on first use, locally only. Event contracts are in [`docs/events/`](docs/events/) (listing events: ANN-03); read messages in Redpanda Console (http://localhost:8090, `tools` profile).
- **Garage**: path-style access (`http://localhost:3900/soukly-listings/<key>`), region `garage`. The key ID follows the Garage format (`GK` + 24 hex characters) and the secret has 64 hex characters. Garage restricts `PutBucketCors` and `PutBucketWebsite` to the bucket owner: `garage-init` grants this right to the key for the duration of the calls, then removes it. The photos are public on the website endpoint, `http://soukly-listings.web.localhost:3902/listings/<key>` (`PHOTO_PUBLIC_BASE_URL`; browsers and curl resolve `*.localhost` to the loopback address, otherwise add `127.0.0.1 soukly-listings.web.localhost` to the hosts file); a routing rule answers `404` for `incoming/`, where the browsers upload the originals.
- **Mailpit**: SMTP on `localhost:1025` without authentication, emails visible at http://localhost:8025.

Memory budget without the `tools` profile: 2.1 GB at most (postgres 512 MB, redpanda 1.2 GB, garage 256 MB, mailpit 128 MB).

## CI

The `.github/workflows/ci.yml` workflow runs on every PR to `main` and every push to `main`. It deploys nothing, builds no Docker image and uses no secret: the token is read-only, including for PRs opened from a fork.

| Job        | What it checks                                                                                                                           | When                                     |
| ---------- | ---------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------- |
| `changes`  | which folders changed (`dorny/paths-filter`)                                                                                             | always                                   |
| `frontend` | `pnpm install --frozen-lockfile`, `pnpm nx affected -t lint test build e2e`, `pnpm nx run-many -t i18n-check`, `pnpm prettier --check .` | if `frontend/**` or `.github/**` changed |
| `backend`  | installs `libvips-tools` (apt), then `./mvnw -B verify` (Testcontainers integration tests included)                                      | if `backend/**` or `.github/**` changed  |
| `ci-ok`    | aggregates the results: red if a job that ran failed, green if all succeeded or were skipped                                             | always                                   |

A PR that only touches documentation skips `frontend` and `backend`, and `ci-ok` stays green. A new commit on a PR cancels the running execution. `nx affected` compares the PR with its target branch: only the touched projects are checked.

Reproduce the CI locally:

```bash
cd frontend && pnpm install --frozen-lockfile && pnpm nx run-many -t lint test build i18n-check && pnpm prettier --check .
cd backend && ./mvnw -B verify                                         # Docker required for Testcontainers
docker run --rm -v "$PWD:/repo" -w /repo rhysd/actionlint:1.7.12        # checks the workflows
```

Dependabot (`.github/dependabot.yml`) proposes npm, Maven, GitHub Actions and Docker updates every week, with at most 5 open PRs per ecosystem.

### Manual settings for `main` protection

To be applied by the maintainer in _Settings > Rules_ (or _Branches_); the CI does not change them:

1. Require a pull request before merging (no direct push to `main`).
2. Require the `ci-ok` check (the only required one: a skipped job never blocks a PR) and up-to-date branches.
3. Block force pushes.
4. Forbid deleting `main`.
5. In _Settings > Actions > General_: `GITHUB_TOKEN` read-only by default, and approval required for workflows from outside contributors.

## Docker images

Five images, one per deployable part, published on GHCR as `ghcr.io/<owner>/soukly-<name>:<full commit sha>` for `linux/amd64` and `linux/arm64`. There is no `latest` tag; version tags (`v1.2.0`) come with `deploy-prod`. An image holds nothing specific to an environment: the same image runs in dev, then in prod, configured by environment variables.

| Image                     | Built from                                       | Port | Notes                                      |
| ------------------------- | ------------------------------------------------ | ---- | ------------------------------------------ |
| `soukly-gateway`          | `backend/Dockerfile`, `SERVICE=gateway`          | 8080 |                                            |
| `soukly-user-service`     | `backend/Dockerfile`, `SERVICE=user-service`     | 8081 |                                            |
| `soukly-listing-service`  | `backend/Dockerfile`, `SERVICE=listing-service`  | 8082 | `libvips-tools` (photos, ANN-02)           |
| `soukly-social-publisher` | `backend/Dockerfile`, `SERVICE=social-publisher` | 8083 | `libvips-tools` (Instagram images, SOC-05) |
| `soukly-frontend`         | `frontend/Dockerfile`                            | 8080 | shell and the 3 remotes, served by Caddy   |

- **Java images**: built with JDK 21, run on the Temurin 21 JRE (Ubuntu 24.04 "noble"), base images pinned by tag and digest. The jar is split into Spring Boot layers (dependencies, loader, snapshot dependencies, application), so a new commit usually only changes the last layer. The process runs as UID `10001` with `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75`.
- **Front-end image**: Caddy on port 8080, as UID `10001`. The shell is served at `/`, each remote at `/remotes/<mfe>/`. Every application route (`/fr/...`, `/ar/listings/123`, `/r/<ref>`, `/oauth/<network>/callback`) gets the shell's `index.html`; `/remotes/**` and a missing file with an extension get a `404`. `index.html`, `federation.manifest.json`, `remoteEntry.json` and the translations are `no-cache`; files with a content hash in their name are cached for a year (`immutable`).
- **`PUBLIC_BASE_URL` (required by the front-end image)**: at startup the container writes `/federation.manifest.json` from it, for example `"mfe-account": "https://dev.example.test/remotes/mfe-account/remoteEntry.json"`. Without it, the container stops and names the missing variable.

### Building locally

```bash
docker build -f backend/Dockerfile --build-arg SERVICE=listing-service --build-arg SERVICE_PORT=8082 \
  --build-arg EXTRA_PACKAGES=libvips-tools -t soukly-listing-service:local backend
docker build -t soukly-frontend:local frontend

docker run --rm -p 8082:8082 --env-file .env.example soukly-listing-service:local   # GET /actuator/health
docker run --rm -p 8080:8080 -e PUBLIC_BASE_URL=http://localhost:8080 soukly-frontend:local
```

`gateway` and `user-service` take no `EXTRA_PACKAGES`. Behind a network that blocks Maven Central, add `--build-arg MAVEN_MIRROR_URL=<mirror>` to the Java builds. Lint the Dockerfiles with `docker run --rm -v "$PWD:/repo" -w /repo hadolint/hadolint:v2.15.1 hadolint backend/Dockerfile frontend/Dockerfile`.

### Publishing with `build-images`

`.github/workflows/build-images.yml` only runs by hand (_Actions > build-images > Run workflow_) or when called by `deploy-dev` and `deploy-prod`; never on a push, a PR or a schedule.

- Inputs: `ref` (branch, tag or SHA, default `main`) and `force` (default `false`). Output for a calling workflow: `sha`, the full commit SHA.
- An image already published for that SHA is not rebuilt (unless `force`); when all 5 exist, the run succeeds with "Images already present".
- Each image is built on a native runner per architecture (`ubuntu-24.04` and `ubuntu-24.04-arm`, free on public repositories, no QEMU), pushed by digest, then assembled into one multi-architecture tag whose platforms are checked. A `verify` job then runs every image on both architectures (non-root user, `vips` present only in `listing-service` and `social-publisher`), and the job summary lists each image with its status (built or reused) and size.
- Authentication with `GITHUB_TOKEN` (`packages: write`) only; no other secret.

### Making the packages public (once)

GHCR creates a package as **private** the first time it is pushed. After the first `build-images` run, for each of the 5 `soukly-*` packages: _GitHub profile > Packages > soukly-<name> > Package settings > Danger zone > Change visibility > Public_. The VM then pulls the images without credentials. The `org.opencontainers.image.source` label links each package to this repository.

## VM (dev and prod)

One Ubuntu VM (Oracle Cloud Always Free by default, or any provider) runs the `dev` and `prod` environments as two isolated Compose projects behind a shared Caddy in HTTPS. Nothing is deployed automatically.

- [`infra/vm/README.md`](infra/vm/README.md): creating the VM, `infra/vm/setup.sh` (idempotent preparation), first launch, switching to the real domain, operations.
- [`infra/backup/README.md`](infra/backup/README.md): nightly backup of the prod databases to object storage (7 days) and the restore procedure.
- **Deploying to dev**: _Actions > deploy-dev > Run workflow_, with any branch, tag or commit; if the new version does not start, the VM returns to the previous one ([`infra/vm/README.md`](infra/vm/README.md#7-deploy-to-dev)). Tests of the VM script: `bash infra/vm/tests/deploy.test.sh`.
- **Putting in prod**: _Actions > deploy-prod > Run workflow_ from `main`, after your approval; prod databases are backed up first, the commit must have been deployed in dev, and a successful deployment is tagged `vX.Y.Z` with a GitHub release ([`infra/vm/README.md`](infra/vm/README.md#8-put-in-prod)). Tests of the version and release-notes scripts: `bash scripts/release/release.test.sh`.

```bash
docker compose -p soukly-edge --env-file /opt/soukly/edge/.env -f infra/edge/compose.yml up -d --wait
docker compose -p soukly-<env> --env-file /opt/soukly/<env>/.env -f infra/compose/compose.yml [-f infra/compose/compose.dev.yml] up -d --wait
```

## Structure

```
soukly/
├── README.md
├── CLAUDE.md          # instructions for Claude Code
├── .editorconfig      # 2 spaces (front), 4 spaces (Java), LF, UTF-8
├── .env.example       # expected variables, dummy values
├── .github/           # CI (workflows/ci.yml), images (workflows/build-images.yml), Dependabot, PR template
├── docker-compose.yml # local infrastructure
├── infra/             # postgres/init, garage (local), vm (server setup), edge (HTTPS Caddy), compose (dev/prod stacks), backup
├── scripts/           # infra-check.sh, gen-jwt-key.sh (local JWT key)
├── frontend/          # Nx workspace (pnpm)
└── backend/           # Maven: common, gateway, user-service, listing-service, social-publisher, e2e-tests
```

## Conventions

- Everything in **English**: code, identifiers, packages, file names (`Listing`, not `Annonce`), comments, documentation, commits and PR descriptions. Only user-facing copy is translated (fr, ar, en).
- Commits follow [Conventional Commits](https://www.conventionalcommits.org/) with the ticket ID as scope: `chore(SETUP-01): init monorepo`, `feat(ANN-04): add photo step`.
- Branches `<type>/<ID>-<slug>`, for example `chore/SETUP-01-init-monorepo`.
- One ticket = one PR to `main`; no direct push to `main`.
- No secret in the (public) repository: only `.env.example` is committed.
- No automatic deployment and no automatic image build.
