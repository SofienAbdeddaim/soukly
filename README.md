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

## Structure

```
soukly/
├── README.md
├── CLAUDE.md          # consignes pour Claude Code
├── .editorconfig      # 2 espaces (front), 4 espaces (Java), LF, UTF-8
├── .env.example       # variables attendues, valeurs factices
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
