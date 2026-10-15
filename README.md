# Soukly

Classified ads site (sales and services) for Tunisia, in three languages (French by default, Arabic in RTL, English), with automatic publishing to Facebook and Instagram.

This single repository holds two code roots:

- `frontend/`: Nx workspace (pnpm) for the Angular shell and micro frontends (Native Federation).
- `backend/`: multi-module Maven project, Java 21 / Spring Boot.

All documentation, comments, commits and PR descriptions in this repository are written in English.

## Prerequisites and pinned versions

| Tool | Version |
| --- | --- |
| Node.js | 24 LTS (`frontend/.nvmrc`); 22.12 minimum |
| pnpm | 10.28.0 (`packageManager` in `frontend/package.json`); npm and yarn are not used |
| Nx | 23.3.0 |
| Angular (devkit, schematics, build, CLI) | 22.2.2 |
| Angular (framework, CDK) | 22.2.3 |
| Tailwind CSS | 4.3.3 |
| angular-eslint | 22.6.0 |
| TypeScript | 6.0.x |
| Java | 21 |
| Maven | 3.9.16 through the Maven Wrapper (`backend/mvnw`) |
| Spring Boot | 4.0.8 |
| Spring Cloud | 2025.1.3 |

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
pnpm prettier --check .
```

| Project | Role | Commands |
| --- | --- | --- |
| `libs/ui` (`@soukly/ui`) | design system: tokens, Tailwind theme, fonts, base components, RTL lint rule ([README](frontend/libs/ui/README.md)) | `pnpm nx test ui`, `pnpm nx lint ui` |
| `apps/ui-showcase` | visual check page of the components in LTR and RTL, never deployed | `pnpm nx serve ui-showcase` (http://localhost:4200), `pnpm nx test ui-showcase` (axe test) |
| `apps/shell` | host: header, footer, mobile bottom bar, loading of the remotes, error and 404 pages | `pnpm nx serve shell` (http://localhost:4200) |
| `apps/mfe-account` | remote, account area (AUTH), waiting page for now | `pnpm nx serve mfe-account` (http://localhost:4201) |
| `apps/mfe-catalog` | remote, home and catalogue (CATA), waiting page for now | `pnpm nx serve mfe-catalog` (http://localhost:4202) |
| `apps/mfe-publish` | remote, publishing and management of listings (ANN, SOC), waiting page for now | `pnpm nx serve mfe-publish` (http://localhost:4203) |
| `apps/shell-e2e` | Playwright end-to-end tests of the shell and its remotes (Chromium) | `pnpm nx e2e shell-e2e` (starts the 4 dev servers) |

The `@soukly/<lib>` aliases point to `libs/<lib>/src/index.ts` (`tsconfig.base.json`).

### Running the shell with its remotes

Native Federation loads the remotes at runtime. Start the four dev servers, each in its own terminal, then open http://localhost:4200:

```bash
cd frontend
pnpm nx serve shell          # 4200, reads public/federation.manifest.json
pnpm nx serve mfe-account    # 4201
pnpm nx serve mfe-catalog    # 4202
pnpm nx serve mfe-publish    # 4203
```

- A remote can also run alone (for example `pnpm nx serve mfe-catalog`, then http://localhost:4202): it mounts its routes with a minimal layout, without the shell.
- `apps/shell/public/federation.manifest.json` maps each remote name to the URL of its `remoteEntry.json`. The shell reads it before Angular starts, so another environment changes the URLs by replacing that file, without rebuilding the shell. The repository only holds the local version: the dev and prod manifests are produced by the deployment workflows (DEP), never committed with real addresses.
- Shared dependencies (Angular, rxjs, `@angular/cdk`, `@lucide/angular`, `@soukly/*`) are declared once in `frontend/federation.shared.mjs` and used by every `federation.config.mjs`: each one is a singleton with a strict version, so the browser downloads Angular once.
- If a remote does not load, the content area shows an error with a "Réessayer" button; the header and the footer stay.
- Playwright end-to-end tests: `pnpm nx e2e shell-e2e` (Chromium). The first run needs `pnpm exec playwright install --with-deps chromium`. Set `CHROMIUM_EXECUTABLE_PATH` to use a Chromium already installed.

### Backend

```bash
cd backend
./mvnw -B verify                                  # compiles and tests common and the 4 services
./mvnw -pl listing-service spring-boot:run        # runs one service
curl http://localhost:8082/actuator/health        # {"status":"UP",...}
```

`spring-boot:run` on a service uses `common` from the local Maven repository: run `./mvnw install -DskipTests` once before, and again after changing `common`.

| Service | Port | Package |
| --- | --- | --- |
| `gateway` (Spring Cloud Gateway, WebFlux) | 8080 | `com.soukly.gateway` |
| `user-service` | 8081 | `com.soukly.user` |
| `listing-service` | 8082 | `com.soukly.listing` |
| `social-publisher` | 8083 | `com.soukly.social` |
| `common` (library) | - | `com.soukly.common` |

Every service exposes `GET /actuator/health`.

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

Without `.env`, Compose refuses to start and names the missing variable. On first start, the one-shot `garage-init` service creates the Garage layout, imports the `soukly-app` key (ID and secret read from `.env`), creates the `soukly-listings` bucket, grants it read and write, then applies the CORS rule. It can be re-run safely: `docker compose run --rm garage-init`.

| Service | Host port(s) | Use | Image |
| --- | --- | --- | --- |
| `postgres` | 5432 | database | `postgres:18.6` |
| `redpanda` | 19092 (external Kafka), 9644 (admin) | broker | `redpandadata/redpanda:v26.2.4` |
| `garage` | 3900 (S3 API), 3903 (admin) | photo storage | `dxflrs/garage:v2.4.1` |
| `mailpit` | 1025 (SMTP), 8025 (web UI) | test emails | `axllent/mailpit:v1.31.5` |
| `garage-init` | - | one-shot Garage setup | `amazon/aws-cli:2.38.0` |
| `redpanda-console` | 8090 | Kafka UI (`tools` profile) | `redpandadata/console:v3.12.0` |
| `pgadmin` | 5050 | Postgres UI (`tools` profile) | `dpage/pgadmin4:9.18.0` |

Every image is pinned to an exact version published for `linux/amd64` and `linux/arm64` (checked on 2026-10-14). PostgreSQL is on the latest patch of version 18 (`18.6`).

- **PostgreSQL**: one database and one user per service (`soukly_user` / `user_svc`, `soukly_listing` / `listing_svc`, `soukly_social` / `social_svc`), created on first start by `infra/postgres/init/01-databases.sh`. Each user can only connect to its own database. The script only runs on an empty volume: after changing a password in `.env`, reset with `down -v`.
- **Redpanda**: one node in `dev-container` mode. Services running outside Docker use `localhost:19092`, containers use `redpanda:9092`. Topics are created automatically on first use, locally only.
- **Garage**: path-style access (`http://localhost:3900/soukly-listings/<key>`), region `garage`. The key ID follows the Garage format (`GK` + 24 hex characters) and the secret has 64 hex characters. Garage restricts `PutBucketCors` to the bucket owner: `garage-init` grants this right to the key for the duration of the call, then removes it.
- **Mailpit**: SMTP on `localhost:1025` without authentication, emails visible at http://localhost:8025.

Memory budget without the `tools` profile: 2.1 GB at most (postgres 512 MB, redpanda 1.2 GB, garage 256 MB, mailpit 128 MB).

## CI

The `.github/workflows/ci.yml` workflow runs on every PR to `main` and every push to `main`. It deploys nothing, builds no Docker image and uses no secret: the token is read-only, including for PRs opened from a fork.

| Job | What it checks | When |
| --- | --- | --- |
| `changes` | which folders changed (`dorny/paths-filter`) | always |
| `frontend` | `pnpm install --frozen-lockfile`, `pnpm nx affected -t lint test build e2e`, `pnpm prettier --check .` | if `frontend/**` or `.github/**` changed |
| `backend` | `./mvnw -B verify` (Testcontainers integration tests included) | if `backend/**` or `.github/**` changed |
| `ci-ok` | aggregates the results: red if a job that ran failed, green if all succeeded or were skipped | always |

A PR that only touches documentation skips `frontend` and `backend`, and `ci-ok` stays green. A new commit on a PR cancels the running execution. `nx affected` compares the PR with its target branch: only the touched projects are checked.

Reproduce the CI locally:

```bash
cd frontend && pnpm install --frozen-lockfile && pnpm nx run-many -t lint test build && pnpm prettier --check .
cd backend && ./mvnw -B verify                                         # Docker required for Testcontainers
docker run --rm -v "$PWD:/repo" -w /repo rhysd/actionlint:1.7.12        # checks the workflows
```

Dependabot (`.github/dependabot.yml`) proposes npm, Maven, GitHub Actions and Docker updates every week, with at most 5 open PRs per ecosystem.

### Manual settings for `main` protection

To be applied by the maintainer in *Settings > Rules* (or *Branches*); the CI does not change them:

1. Require a pull request before merging (no direct push to `main`).
2. Require the `ci-ok` check (the only required one: a skipped job never blocks a PR) and up-to-date branches.
3. Block force pushes.
4. Forbid deleting `main`.
5. In *Settings > Actions > General*: `GITHUB_TOKEN` read-only by default, and approval required for workflows from outside contributors.

## Structure

```
soukly/
├── README.md
├── CLAUDE.md          # instructions for Claude Code
├── .editorconfig      # 2 spaces (front), 4 spaces (Java), LF, UTF-8
├── .env.example       # expected variables, dummy values
├── .github/           # CI (workflows/ci.yml), Dependabot, PR template
├── docker-compose.yml # local infrastructure
├── infra/             # postgres/init (one database per service), garage (config and init)
├── scripts/           # infra-check.sh
├── frontend/          # Nx workspace (pnpm)
└── backend/           # Maven: common, gateway, user-service, listing-service, social-publisher
```

## Conventions

- Everything in **English**: code, identifiers, packages, file names (`Listing`, not `Annonce`), comments, documentation, commits and PR descriptions. Only user-facing copy is translated (fr, ar, en).
- Commits follow [Conventional Commits](https://www.conventionalcommits.org/) with the ticket ID as scope: `chore(SETUP-01): init monorepo`, `feat(ANN-04): add photo step`.
- Branches `<type>/<ID>-<slug>`, for example `chore/SETUP-01-init-monorepo`.
- One ticket = one PR to `main`; no direct push to `main`.
- No secret in the (public) repository: only `.env.example` is committed.
- No automatic deployment.
