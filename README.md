# Soukly

Site de petites annonces (ventes et services) pour la Tunisie, en trois langues (fr par défaut, ar en RTL, en), avec publication automatique sur Facebook et Instagram.

Ce dépôt unique contient deux racines de code :

- `frontend/` : workspace Nx (pnpm) pour le shell et les micro frontends Angular (Native Federation).
- `backend/` : projet Maven multi-module Java 21 / Spring Boot.

## Prérequis et versions figées

| Outil | Version |
| --- | --- |
| Node.js | 24 LTS (`frontend/.nvmrc`) ; 22.12 minimum |
| pnpm | 10.28.0 (`packageManager` dans `frontend/package.json`) ; npm et yarn ne sont pas utilisés |
| Nx | 23.3.0 |
| Angular (devkit, schematics) | 22.2.2 |
| angular-eslint | 22.6.0 |
| TypeScript | 6.0.x |
| Java | 21 |
| Maven | 3.9.16 via le Maven Wrapper (`backend/mvnw`) |
| Spring Boot | 4.0.8 |
| Spring Cloud | 2025.1.3 |

Les versions de Nx et d'Angular sont figées (sans `^`). Aucune version milestone ou RC n'est utilisée.

## Démarrage

```bash
cp .env.example .env   # jamais commité
```

### Frontend

```bash
cd frontend
pnpm install --frozen-lockfile
pnpm nx run-many -t lint test build
pnpm prettier --check .
```

Le workspace ne contient encore aucune application ni librairie (voir SETUP-04 et SETUP-05). Les alias `@soukly/<lib>` sont prêts dans `tsconfig.base.json` (`libs/<lib>/src/index.ts`).

### Backend

```bash
cd backend
./mvnw -B verify                                  # compile et teste common et les 4 services
./mvnw -pl listing-service spring-boot:run        # lance un service
curl http://localhost:8082/actuator/health        # {"status":"UP",...}
```

`spring-boot:run` sur un service utilise `common` depuis le dépôt Maven local : lancer `./mvnw install -DskipTests` une fois avant, ou après une modification de `common`.

| Service | Port | Package |
| --- | --- | --- |
| `gateway` (Spring Cloud Gateway, WebFlux) | 8080 | `com.soukly.gateway` |
| `user-service` | 8081 | `com.soukly.user` |
| `listing-service` | 8082 | `com.soukly.listing` |
| `social-publisher` | 8083 | `com.soukly.social` |
| `common` (librairie) | - | `com.soukly.common` |

Chaque service expose `GET /actuator/health`.

## Infrastructure locale

Docker (avec Compose v2) suffit : PostgreSQL, Redpanda (API Kafka), Garage (stockage S3) et Mailpit (SMTP de test) démarrent avec une seule commande, sans autre installation.

```bash
cp .env.example .env                           # une fois ; valeurs factices à remplacer (change-me)
docker compose up -d --wait                    # démarre l'infrastructure et attend qu'elle soit saine
./scripts/infra-check.sh                       # vérifie bases, Kafka, S3, CORS, idempotence, profil tools
docker compose --profile tools up -d --wait    # ajoute Redpanda Console et pgAdmin
docker compose down                            # arrête, en conservant les données
docker compose down -v                         # arrête et supprime les données (réinitialisation complète)
```

Sans `.env`, Compose refuse de démarrer et nomme la variable manquante. Au premier démarrage, le service one-shot `garage-init` crée le layout Garage, importe la clé `soukly-app` (ID et secret lus dans `.env`), crée le bucket `soukly-listings`, lui donne les droits lecture et écriture, puis applique la règle CORS. Il peut être relancé sans effet de bord : `docker compose run --rm garage-init`.

| Service | Port(s) hôte | Usage | Image |
| --- | --- | --- | --- |
| `postgres` | 5432 | base de données | `postgres:18.6` |
| `redpanda` | 19092 (Kafka externe), 9644 (admin) | broker | `redpandadata/redpanda:v26.2.4` |
| `garage` | 3900 (API S3), 3903 (admin) | stockage photos | `dxflrs/garage:v2.4.1` |
| `mailpit` | 1025 (SMTP), 8025 (interface web) | emails de test | `axllent/mailpit:v1.31.5` |
| `garage-init` | - | initialisation one-shot de Garage | `amazon/aws-cli:2.38.0` |
| `redpanda-console` | 8090 | interface Kafka (profil `tools`) | `redpandadata/console:v3.12.0` |
| `pgadmin` | 5050 | interface Postgres (profil `tools`) | `dpage/pgadmin4:9.18.0` |

Toutes les images sont épinglées sur une version exacte publiée en `linux/amd64` et `linux/arm64` (vérifié le 2026-10-14). PostgreSQL est au dernier patch de la version 18 (`18.6`).

- **PostgreSQL** : une base et un utilisateur par service (`soukly_user` / `user_svc`, `soukly_listing` / `listing_svc`, `soukly_social` / `social_svc`), créés au premier démarrage par `infra/postgres/init/01-databases.sh`. Chaque utilisateur ne peut se connecter qu'à sa base. Le script ne s'exécute que sur un volume vide : après un changement de mot de passe dans `.env`, réinitialiser avec `down -v`.
- **Redpanda** : un nœud en mode `dev-container`. Les services lancés hors Docker utilisent `localhost:19092`, les conteneurs `redpanda:9092`. Les topics sont créés automatiquement à la première utilisation, en local uniquement.
- **Garage** : accès path-style (`http://localhost:3900/soukly-listings/<clé>`), région `garage`. L'ID de clé suit le format Garage (`GK` + 24 caractères hexadécimaux) et le secret fait 64 caractères hexadécimaux. Garage réserve `PutBucketCors` au propriétaire du bucket : `garage-init` donne ce droit à la clé le temps de l'appel, puis le retire.
- **Mailpit** : SMTP sur `localhost:1025` sans authentification, emails visibles sur http://localhost:8025.

Budget mémoire hors profil `tools` : 2,1 Go au maximum (postgres 512 Mo, redpanda 1,2 Go, garage 256 Mo, mailpit 128 Mo).

## CI

Le workflow `.github/workflows/ci.yml` s'exécute sur chaque PR vers `main` et sur chaque push sur `main`. Il ne déploie rien, ne construit aucune image Docker et n'utilise aucun secret : le jeton est en lecture seule, y compris pour les PR ouvertes depuis un fork.

| Job | Ce qu'il vérifie | Quand |
| --- | --- | --- |
| `changes` | quels dossiers ont changé (`dorny/paths-filter`) | toujours |
| `frontend` | `pnpm install --frozen-lockfile`, `pnpm nx affected -t lint test build`, `pnpm prettier --check .` | si `frontend/**` ou `.github/**` a changé |
| `backend` | `./mvnw -B verify` (tests d'intégration Testcontainers compris) | si `backend/**` ou `.github/**` a changé |
| `ci-ok` | agrège les résultats : rouge si un job exécuté a échoué, vert si tous ont réussi ou ont été ignorés | toujours |

Une PR qui ne touche que la documentation ignore `frontend` et `backend`, et `ci-ok` reste vert. Un nouveau commit sur une PR annule l'exécution en cours. `nx affected` compare la PR à sa branche cible : seuls les projets touchés sont vérifiés.

Reproduire la CI en local :

```bash
cd frontend && pnpm install --frozen-lockfile && pnpm nx run-many -t lint test build && pnpm prettier --check .
cd backend && ./mvnw -B verify                                         # Docker requis pour Testcontainers
docker run --rm -v "$PWD:/repo" -w /repo rhysd/actionlint:1.7.12        # vérifie les workflows
```

Dependabot (`.github/dependabot.yml`) propose chaque semaine les mises à jour npm, Maven, GitHub Actions et Docker, 5 PR ouvertes au maximum par écosystème.

### Réglages manuels de la protection de `main`

À appliquer par le mainteneur dans *Settings > Rules* (ou *Branches*), la CI ne les modifie pas :

1. Exiger une pull request avant de merger (pas de push direct sur `main`).
2. Exiger le check `ci-ok` (le seul obligatoire : un job ignoré ne bloque jamais une PR) et des branches à jour.
3. Bloquer les force-push.
4. Interdire la suppression de `main`.
5. Dans *Settings > Actions > General* : `GITHUB_TOKEN` en lecture seule par défaut, et approbation requise pour les workflows des contributeurs externes.

## Structure

```
soukly/
├── README.md
├── CLAUDE.md          # consignes pour Claude Code
├── .editorconfig      # 2 espaces (front), 4 espaces (Java), LF, UTF-8
├── .env.example       # variables attendues, valeurs factices
├── .github/           # CI (workflows/ci.yml), Dependabot, modèle de PR
├── docker-compose.yml # infrastructure locale
├── infra/             # postgres/init (bases par service), garage (config et init)
├── scripts/           # infra-check.sh
├── frontend/          # workspace Nx (pnpm)
└── backend/           # Maven : common, gateway, user-service, listing-service, social-publisher
```

## Conventions

- Code, identifiants, packages et noms de fichiers en **anglais** (`Listing`, pas `Annonce`).
- Commits au format [Conventional Commits](https://www.conventionalcommits.org/) avec l'ID du ticket en scope : `chore(SETUP-01): init monorepo`, `feat(ANN-04): add photo step`.
- Branches `<type>/<ID>-<slug>`, par exemple `chore/SETUP-01-init-monorepo`.
- Un ticket = une PR vers `main` ; pas de push direct sur `main`.
- Aucun secret dans le dépôt (public) : seul `.env.example` est commité.
- Aucun déploiement automatique.
