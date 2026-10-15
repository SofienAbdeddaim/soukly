# @soukly/ui

Design system de Soukly, ambiance A « Marché chaleureux » : design tokens en variables CSS, thème Tailwind CSS v4 qui les expose, polices auto-hébergées et 5 composants Angular standalone (`OnPush`, signal inputs, préfixe `ui-`), utilisables en LTR (fr, en) et en RTL (ar).

C'est une librairie, pas un micro frontend. L'app `ui-showcase` affiche chaque composant dans tous ses états :

```bash
cd frontend
pnpm nx serve ui-showcase   # http://localhost:4200, bouton « Passer en arabe (RTL) » en haut de page
pnpm nx test ui             # tests unitaires des composants (Vitest)
pnpm nx test ui-showcase    # test d'accessibilité axe de la page, en LTR et en RTL
pnpm nx lint ui             # ESLint, dont la règle RTL
```

## Utiliser la librairie dans une app

1. Feuille de style globale de l'app, dans cet ordre :

   ```css
   @import 'tailwindcss';
   @import '../../../libs/ui/src/styles/tailwind.css'; /* thème Tailwind de Soukly */
   @import '../../../libs/ui/src/styles/soukly.css'; /* polices, tokens, base */
   ```

2. `.postcssrc.json` à la racine de l'app : `{ "plugins": { "@tailwindcss/postcss": {} } }`.
3. Dans `eslint.config.mjs` de l'app : `import rtlConfig from '../../libs/ui/eslint.rtl.mjs';` puis `...rtlConfig` après la config de base.
4. Dans les composants : `import { UiButton, UiField, UiFieldControl, UiListingCard, UiSkeleton, ToastService } from '@soukly/ui';`.

Les polices sont référencées par `url()` relative dans `fonts.css` : le builder Angular les copie dans `media/` avec un hash. Aucun appel réseau vers un service tiers (polices, icônes, CDN).

## Tokens

Définis une seule fois dans [`src/styles/tokens.css`](src/styles/tokens.css), sur `:root`, en deux niveaux :

1. **palette brute** (`--soukly-indigo-900`, `--soukly-saffron-400`…) : jamais utilisée par un composant ni par Tailwind ;
2. **tokens sémantiques** (rôles) : seuls utilisés par les composants et par le thème Tailwind.

`tokens.css` est le seul fichier du workspace qui contient des valeurs de couleur. Un mode sombre (V2) redéfinira les tokens sémantiques sous `[data-theme='dark']` sans toucher aux composants.

| Token                                    | Valeur                                 | Usage                                                               | Classe Tailwind               |
| ---------------------------------------- | -------------------------------------- | ------------------------------------------------------------------- | ----------------------------- |
| `--color-bg`                             | `#FFF8EC` crème                        | fond de page, badge de carte                                        | `bg-bg`                       |
| `--color-surface`                        | `#FFFFFF`                              | cartes, champs, panneaux, toasts                                    | `bg-surface`                  |
| `--color-surface-muted`                  | `#FFF3DD`                              | puces de catégorie, placeholder d'image, skeleton                   | `bg-surface-muted`            |
| `--color-border`                         | `#F0E3CC`                              | bordures décoratives de carte                                       | `border-border`               |
| `--color-border-strong`                  | `#8A7F9C`                              | bordure des champs et du bouton `ghost` (3:1)                       | `border-border-strong`        |
| `--color-text`                           | `#2A2140` indigo                       | texte principal                                                     | `text-text`                   |
| `--color-text-muted`                     | `#6A5F7E`                              | texte secondaire (ville, date, aide)                                | `text-text-muted`             |
| `--color-brand` / `--color-on-brand`     | `#2A2140` / `#FFF8EC`                  | header, footer (SETUP-05)                                           | `bg-brand`, `text-on-brand`   |
| `--color-accent` / `--color-on-accent`   | `#F2B33D` safran / `#2A2140`           | bouton secondaire, logo, bandeaux                                   | `bg-accent`, `text-on-accent` |
| `--color-primary` / `--color-on-primary` | `#B4441F` terracotta foncé / `#FFFFFF` | bouton principal, liens, prix                                       | `bg-primary`, `text-primary`  |
| `--color-primary-hover`                  | `#8E3416`                              | survol du bouton principal et des liens                             | `bg-primary-hover`            |
| `--color-decorative`                     | `#E0602F` terracotta clair             | décor uniquement (pastille, icône sur indigo), jamais sous du texte | `bg-decorative`               |
| `--color-danger`                         | `#A8201A`                              | erreurs de champ, toast d'erreur                                    | `text-danger`                 |
| `--color-success`                        | `#1B6E45`                              | toast de succès                                                     | `text-success`                |

Autres tokens : `--font-display` (Lalezar, titres `h1`/`h2`), `--font-body` (Rubik 400, 500, 600) ; `--radius-sm` 6px, `--radius-md` 10px, `--radius-lg` 14px, `--radius-full` ; `--shadow-card`, `--shadow-overlay` ; `--focus-ring` (anneau double : espace couleur de fond puis anneau indigo, visible sur fond clair comme sur l'indigo) ; `--control-height` 44px et `--control-height-lg` 52px. Les espacements suivent l'échelle Tailwind par défaut.

### Thème Tailwind

Tailwind CSS v4 n'a plus de preset JavaScript : le « preset » est le fichier CSS [`src/styles/tailwind.css`](src/styles/tailwind.css). Il vide les couleurs, polices, rayons et ombres par défaut de Tailwind et ne déclare que des références aux tokens (`--color-primary: var(--color-primary)`) dans un bloc `@theme inline reference` : les utilitaires produisent `background-color: var(--color-primary)` et Tailwind n'émet pas de seconde copie des variables. Une classe comme `bg-red-500` n'existe donc pas.

## Contrastes (WCAG 2.2 AA)

Ratios calculés avec la formule WCAG 2.x sur les valeurs de `tokens.css` (2026-10-15), puis vérifiés par axe (règle `color-contrast`) dans Chromium sur `ui-showcase` en LTR et en RTL : aucune violation. Seuil : 4,5:1 pour le texte, 3:1 pour le texte ≥ 24px (ou ≥ 18,66px gras) et pour les composants d'interface (bordures de champ, icônes, focus).

| Couple (premier plan sur fond)   | Utilisé par                                        | Ratio | Verdict              |
| -------------------------------- | -------------------------------------------------- | ----- | -------------------- |
| `text` sur `bg`                  | texte courant, badge de carte                      | 14,31 | OK                   |
| `text` sur `surface`             | carte, champ, toast                                | 15,11 | OK                   |
| `text` sur `surface-muted`       | bouton `ghost` au survol                           | 13,75 | OK                   |
| `text-muted` sur `bg`            | texte secondaire                                   | 5,60  | OK                   |
| `text-muted` sur `surface`       | ville et date de carte, aide de champ, placeholder | 5,91  | OK                   |
| `text-muted` sur `surface-muted` | icône du placeholder d'image                       | 5,38  | OK                   |
| `primary` sur `bg`               | liens, bouton favori                               | 5,25  | OK                   |
| `primary` sur `surface`          | prix de carte                                      | 5,54  | OK                   |
| `on-primary` sur `primary`       | bouton principal                                   | 5,54  | OK                   |
| `on-primary` sur `primary-hover` | bouton principal au survol                         | 7,92  | OK                   |
| `on-accent` sur `accent`         | bouton secondaire                                  | 8,12  | OK                   |
| `danger` sur `surface`           | toast d'erreur, astérisque requis                  | 7,28  | OK                   |
| `danger` sur `bg`                | message d'erreur de champ                          | 6,89  | OK                   |
| `success` sur `surface`          | icône et bordure du toast de succès                | 6,24  | OK                   |
| `border-strong` sur `surface`    | bordure de champ, bouton `ghost` (non textuel)     | 3,75  | OK (3:1)             |
| `border-strong` sur `bg`         | idem sur fond crème                                | 3,55  | OK (3:1)             |
| `on-brand` sur `brand`           | header, footer                                     | 14,31 | OK                   |
| blanc sur `#E0602F`              | aucun texte                                        | 3,57  | refusé pour du texte |
| safran sur crème                 | aucun texte                                        | 1,76  | refusé pour du texte |

Le bouton désactivé (opacité réduite) est exempté par WCAG (1.4.3, composant inactif).

## Composants

Aucun texte en dur : tous les libellés (« Fermer », « Ajouter aux favoris »…) arrivent par input ou par option. L'intégration Transloco est faite par SETUP-07.

| Composant                    | Utilisation                                                                                                                                                                | Accessibilité                                                                                                                                                    |
| ---------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `UiButton`                   | `<button ui-button variant="primary\|secondary\|ghost" size="md\|lg" [loading] [disabled]>` ou `<a ui-button>` ; icône optionnelle : `<svg lucidePlus uiButtonIcon></svg>` | hauteur ≥ 44px, focus visible, `aria-busy` et clics ignorés en chargement (le bouton reste focalisable), `aria-disabled` + `tabindex="-1"` sur un lien désactivé |
| `UiField` + `UiFieldControl` | `<ui-field label="…" hint="…" [error]="…" required><input uiControl /></ui-field>` (aussi `textarea`, `select`)                                                            | `label for`, `aria-describedby` vers l'aide et l'erreur, `aria-invalid="true"` quand une erreur est affichée                                                     |
| `UiListingCard`              | `title`, `price` (formaté), `city`, `dateLabel`, `dateTime`, `typeLabel`, `href`, `imageUrl` (ou placeholder), `favorite`, `favoriteLabel`, sortie `favoriteToggle`        | `article`, titre en `h3` (lien étiré sur toute la carte), bouton favori avec `aria-label` et `aria-pressed`, prix en `<bdi dir="ltr">`                           |
| `UiSkeleton`                 | `variant="line\|block\|card"`, `inlineSize`, `blockSize`                                                                                                                   | `aria-hidden="true"` (la zone qui charge porte `aria-busy`), animation coupée si `prefers-reduced-motion`                                                        |
| `ToastService`               | `show({ type: 'info'\|'success'\|'error', message, closeLabel, duration? })` renvoie un id ; `dismiss(id)`                                                                 | overlay Angular CDK, région `aria-live="polite"` (`assertive` pour `error`), fermeture auto après 5 s (en pause au survol et au focus) et bouton de fermeture    |

Le bouton favori n'a pas de logique : il reflète `favorite` et émet l'état suivant ; la gestion des favoris est hors V1.

Icônes : [Lucide](https://lucide.dev) via `@lucide/angular` (paquet Angular officiel, standalone et signals), ex. `<svg lucideHeart aria-hidden="true"></svg>`. Icônes décoratives en `aria-hidden="true"`.

## Règles RTL

- Uniquement des propriétés logiques : `margin-inline-start`, `padding-inline`, `inset-inline-end`, `border-inline-start`, `text-align: start`… Jamais `left`/`right`, `margin-left`, `padding-right`, `text-align: left`.
- En Tailwind : `ms-`/`me-`/`ps-`/`pe-`/`start-`/`end-`/`border-s`/`rounded-s`/`text-start`, jamais `ml-`/`mr-`/`pl-`/`pr-`/`left-`/`right-`/`border-l`/`rounded-l`/`text-left`.
- Icônes directionnelles (flèches, chevrons) : classe globale `ui-flip-rtl`, inversée en RTL.
- Prix et nombres : `<bdi dir="ltr">` ou classe `ui-ltr` (`direction: ltr; unicode-bidi: isolate`).

Le lint fait respecter ces règles dans `libs/ui` et dans chaque app qui importe [`eslint.rtl.mjs`](eslint.rtl.mjs) :

- CSS : règle `css/prefer-logical-properties` de [`@eslint/css`](https://github.com/eslint/css) (les tailles `width`/`height` et les unités de viewport restent permises, elles ne dépendent pas du sens d'écriture) ;
- classes Tailwind dans les templates (attribut `class`, `[class.x]`, chaînes de `[class]`/`[ngClass]`) et dans les chaînes TypeScript (`host: { class }`) : règle locale `soukly-rtl/no-physical-tailwind-classes`.

Exemple de sortie :

```text
error  Expected logical property 'margin-inline-start' instead of 'margin-left'                css/prefer-logical-properties
error  Physical Tailwind class "ml-4" breaks RTL layouts: use the logical class "ms-4"           soukly-rtl/no-physical-tailwind-classes
```

## Polices et licences

Fichiers `woff2` dans [`src/assets/fonts`](src/assets/fonts), sous-ensembles latin et arabe (le navigateur ne télécharge un fichier que si la page contient un caractère de sa plage `unicode-range`), `font-display: swap`. Fichiers issus des paquets Fontsource 5.3.0 (`@fontsource-variable/rubik`, `@fontsource/lalezar`), copiés dans le dépôt : aucune dépendance npm ni appel réseau à l'exécution.

| Police                               | Fichiers                                                            | Auteurs                                                                               | Licence                                                                          |
| ------------------------------------ | ------------------------------------------------------------------- | ------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------- |
| Rubik (variable, graisses 300 à 900) | `rubik-latin-wght-normal.woff2`, `rubik-arabic-wght-normal.woff2`   | The Rubik Project Authors ([googlefonts/rubik](https://github.com/googlefonts/rubik)) | SIL Open Font License 1.1, [`OFL-Rubik.txt`](src/assets/fonts/OFL-Rubik.txt)     |
| Lalezar (400)                        | `lalezar-latin-400-normal.woff2`, `lalezar-arabic-400-normal.woff2` | The Lalezar Project Authors ([BornaIz/Lalezar](https://github.com/BornaIz/Lalezar))   | SIL Open Font License 1.1, [`OFL-Lalezar.txt`](src/assets/fonts/OFL-Lalezar.txt) |

La licence OFL permet l'usage, la modification et la redistribution des polices avec un logiciel, à condition de conserver le texte de licence et de ne pas les vendre seules.
