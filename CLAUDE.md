# CLAUDE.md

## Project

Soukly is a classified ads site (sales and services) for **Tunisia**, with automatic publishing to Facebook and Instagram. The detailed tickets are the functional source of truth: one ticket = one PR.

- Languages: `fr` (default), `ar` (RTL), `en`. Every page URL starts with `/{lang}/`, except `/oauth/facebook/callback`, `/oauth/instagram/callback` and `/r/{ref}`.
- Prices in **whole dinars** (TND, no millimes). Phone numbers `+216`. Places: the 24 governorates and their delegations, from the official source of CAT-01; **never invent geographic data**.
- **Everything in the repository is written in English**: code, technical names, comments, commits, READMEs, PR descriptions and templates. Only user-facing copy is translated (fr, ar, en through i18n). Tickets are written in French outside the repository.

## Structure (target, created ticket by ticket)

```
frontend/   # Nx (pnpm only): apps shell, mfe-account, mfe-catalog, mfe-publish, ui-showcase; libs ui, i18n, auth, listing-data, social-data
backend/    # Maven: common, gateway (8080), user-service (8081), listing-service (8082), social-publisher (8083), e2e-tests (tests only)
infra/      # vm/, edge/, compose/, backup/ (DEP-02 to DEP-04)
docs/events/  # event contracts (listing-events.md, user-events.md, social-events.md)
.github/workflows/  # ci.yml, build-images.yml, deploy-dev.yml, deploy-prod.yml
docker-compose.yml  # local infrastructure (SETUP-02)
```

## Commands

The reference commands are in `README.md`.

- Front: `cd frontend && pnpm install --frozen-lockfile && pnpm nx run-many -t lint test build`, then `pnpm prettier --check .`.
- Back: `cd backend && ./mvnw -B verify`.
- Run a service: `cd backend && ./mvnw install -DskipTests` once, then `./mvnw -pl <service> spring-boot:run`; a front app: `pnpm nx serve <app>`.
- Local infrastructure: `docker compose up -d --wait` (tools: `--profile tools`), from SETUP-02.
- Front, e2e and i18n: Nx e2e (Playwright) and `i18n-check` targets, added to the README by the tickets that create them.
- Back-end integration tests use Testcontainers (Docker required) as soon as a database or Redpanda is introduced.

Before opening a PR, all these checks must be green for what you touched.

## Conventions

- Branch `<type>/<ID>-<slug>` (`feat/…`, `fix/…`, `chore/…`), Conventional Commits with the ID as scope: `feat(ANN-04): add photo step`.
- Front: standalone Angular and signals; base components and colours **only** in `libs/ui` (ambiance A tokens, CSS logical properties for RTL); texts in Transloco, one scope per remote, all 3 languages complete.
- Back: `application/problem+json` errors (shared class in `common`); Flyway for every schema change; events through Outbox + `SKIP LOCKED` relay, idempotent consumers (`processed_events`) with DLQ.
- **Migrations compatible with the previous version**: add first, remove or rename in a later version (a rollback does not revert the database).
- Event contracts: additive fields only; every change documented in `docs/events/`.
- Service-to-service calls: `/internal/**` endpoints protected by `X-Internal-Token`, never exposed by the gateway.
- The domain is never hard-coded: use the host of `PUBLIC_BASE_URL`.

## Guardrails

- Only work on the requested ticket; report what is out of scope instead of doing it.
- Never create an automatic deployment: `build-images`, `deploy-dev` and `deploy-prod` stay `workflow_dispatch`.
- No secret, token, password or `.env` file in the repository; only `.env.example` files.
- Never log a token, a password or personal data; no personal data in texts published to social networks.
- Never call the real Meta APIs in automated tests.
- Never use an unpinned Docker image or GitHub Action.
- Never disable or bypass a test to get a green CI.
- Points marked "à confirmer" (to be confirmed) in a ticket are checked in the official documentation; record the result in the PR, never invent a version or a command.

## For each ticket

1. Read the ticket, its dependencies as implemented and the mockups of its row.
2. Follow the order of the ticket's "Instructions pour Claude Code".
3. Check every Gherkin criterion and every Definition of Done box.
4. Describe the PR in English: what changes for the user, FR and AR screenshots for the front end, deviations from the ticket and the "à confirmer" points checked.

## Links

- Plan and tickets: https://claude.ai/artifact/DNrrM7P7V9ESLXeSKCrmPq
- Mockups: https://claude.ai/artifact/PefL3sZgfFqT24UGQB3DxE
