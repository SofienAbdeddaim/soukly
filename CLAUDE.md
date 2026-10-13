# CLAUDE.md

## Projet

Soukly est un site de petites annonces (ventes et services) pour la **Tunisie**, avec publication automatique sur Facebook et Instagram. Les tickets détaillés sont la source de vérité fonctionnelle : un ticket = une PR.

- Langues : `fr` (par défaut), `ar` (RTL), `en`. Toute URL de page commence par `/{lang}/`, sauf `/oauth/facebook/callback`, `/oauth/instagram/callback` et `/r/{ref}`.
- Prix en **dinars entiers** (TND, pas de millimes). Téléphones `+216`. Lieux : 24 gouvernorats et leurs délégations, issus de la source officielle de CAT-01 ; **n'invente jamais de donnée géographique**.
- Code, noms techniques et commits en **anglais** ; tickets et textes métier en français.

## Structure (cible, créée au fil des tickets)

```
frontend/   # Nx (pnpm uniquement) : apps shell, mfe-account, mfe-catalog, mfe-publish, ui-showcase ; libs ui, i18n, auth, listing-data
backend/    # Maven : common, gateway (8080), user-service (8081), listing-service (8082), social-publisher (8083)
infra/      # vm/, edge/, compose/, backup/ (DEP-02 à DEP-04)
docs/events/  # contrats d'événements (listing-events.md, social-events.md)
.github/workflows/  # ci.yml, build-images.yml, deploy-dev.yml, deploy-prod.yml
docker-compose.yml  # infrastructure locale (SETUP-02)
```

## Commandes

Les commandes de référence sont dans le `README.md`.

- Front : `cd frontend && pnpm install --frozen-lockfile && pnpm nx run-many -t lint test build`, puis `pnpm prettier --check .`.
- Back : `cd backend && ./mvnw -B verify`.
- Lancer un service : `cd backend && ./mvnw install -DskipTests` une fois, puis `./mvnw -pl <service> spring-boot:run` ; une app front : `pnpm nx serve <app>` (à partir de SETUP-05).
- Infrastructure locale : `docker compose up -d --wait` (outils : `--profile tools`), à partir de SETUP-02.
- Front, e2e et i18n : cibles Nx e2e (Playwright) et `i18n-check`, à ajouter au README par les tickets qui les créent.
- Les tests d'intégration back utilisent Testcontainers (Docker requis) dès qu'une base ou Redpanda est introduite.

Avant d'ouvrir une PR, ces vérifications doivent toutes être vertes pour ce que tu as touché.

## Conventions

- Branche `<type>/<ID>-<slug>` (`feat/…`, `fix/…`, `chore/…`), commits Conventional Commits avec l'ID en scope : `feat(ANN-04): add photo step`.
- Front : Angular standalone et signals ; composants de base et couleurs **uniquement** dans `libs/ui` (tokens de l'ambiance A, propriétés logiques CSS pour le RTL) ; textes dans Transloco, scope par remote, 3 langues complètes.
- Back : erreurs `application/problem+json` (classe commune dans `common`) ; Flyway pour tout changement de schéma ; événements par Outbox + relais `SKIP LOCKED`, consommateurs idempotents (`processed_events`) avec DLQ.
- **Migrations compatibles avec la version précédente** : ajouter d'abord, supprimer ou renommer une version plus tard (un retour arrière n'annule pas la base).
- Contrats d'événements : ajout de champs uniquement ; tout changement documenté dans `docs/events/`.
- Appels entre services : endpoints `/internal/**` protégés par `X-Internal-Token`, jamais exposés par la gateway.
- Le domaine n'est jamais écrit en dur : utilise l'hôte de `PUBLIC_BASE_URL`.

## Garde-fous

- Ne travaille que sur le ticket demandé ; signale ce qui sort du périmètre au lieu de le faire.
- Ne crée jamais de déploiement automatique : `build-images`, `deploy-dev` et `deploy-prod` restent en `workflow_dispatch`.
- Aucun secret, jeton, mot de passe ou fichier `.env` dans le dépôt ; seulement des `.env.example`.
- Ne journalise jamais un jeton, un mot de passe ni une donnée personnelle ; aucune donnée personnelle dans les textes publiés sur les réseaux.
- N'appelle jamais les API réelles de Meta dans les tests automatisés.
- N'utilise aucune image Docker ni action GitHub non épinglée.
- Ne désactive ni ne contourne jamais un test pour obtenir une CI verte.
- Les points marqués « à confirmer » dans un ticket se vérifient dans la documentation officielle ; note le résultat dans la PR, n'invente ni version ni commande.

## Pour chaque ticket

1. Lis le ticket, ses dépendances telles qu'implémentées et les maquettes de sa ligne.
2. Suis l'ordre des « Instructions pour Claude Code » du ticket.
3. Vérifie chaque critère Gherkin et chaque case de la Definition of Done.
4. Décris la PR : ce qui change pour l'utilisateur, captures FR et AR pour le front, écarts avec le ticket et points « à confirmer » vérifiés.

## Liens

- Plan et tickets : https://claude.ai/artifact/DNrrM7P7V9ESLXeSKCrmPq
- Maquettes : https://claude.ai/artifact/PefL3sZgfFqT24UGQB3DxE
