# @soukly/ui

Soukly design system, ambiance A "Warm market" (Marché chaleureux): design tokens as CSS variables, a Tailwind CSS v4 theme that exposes them, self-hosted fonts and 7 standalone Angular components (`OnPush`, signal inputs, `ui-` prefix), usable in LTR (fr, en) and RTL (ar).

It is a library, not a micro frontend. The `ui-showcase` app shows every component in all its states:

```bash
cd frontend
pnpm nx serve ui-showcase   # http://localhost:4200, "Passer en arabe (RTL)" toggle at the top of the page
pnpm nx test ui             # component unit tests (Vitest)
pnpm nx test ui-showcase    # axe accessibility test of the page, in LTR and RTL
pnpm nx lint ui             # ESLint, including the RTL rule
```

## Using the library in an app

1. Global stylesheet of the app, in this order:

   ```css
   @import 'tailwindcss';
   @import '../../../libs/ui/src/styles/tailwind.css'; /* Soukly Tailwind theme */
   @import '../../../libs/ui/src/styles/soukly.css'; /* fonts, tokens, base */
   ```

2. `.postcssrc.json` at the root of the app: `{ "plugins": { "@tailwindcss/postcss": {} } }`.
3. In the app's `eslint.config.mjs`: `import rtlConfig from '../../libs/ui/eslint.rtl.mjs';` then `...rtlConfig` after the base config.
4. In components: `import { UiAlert, UiButton, UiCheckbox, UiCheckboxControl, UiField, UiFieldControl, UiFieldPrefix, UiFieldSuffix, UiListingCard, UiPhotoTile, UiProgress, UiSkeleton, UiToggleGroup, ToastService } from '@soukly/ui';`.

Fonts are referenced with relative `url()` in `fonts.css`: the Angular builder copies them to `media/` with a hash. No network call to a third-party service (fonts, icons, CDN).

## Tokens

Defined once in [`src/styles/tokens.css`](src/styles/tokens.css), on `:root`, on two levels:

1. **raw palette** (`--soukly-indigo-900`, `--soukly-saffron-400`…): never used by a component or by Tailwind;
2. **semantic tokens** (roles): the only ones used by components and by the Tailwind theme.

`tokens.css` is the only file of the workspace that contains colour values. A dark mode (V2) will redefine the semantic tokens under `[data-theme='dark']` without touching the components.

| Token                                            | Value                                 | Use                                                                  | Tailwind class                |
| ------------------------------------------------ | ------------------------------------- | -------------------------------------------------------------------- | ----------------------------- |
| `--color-bg`                                     | `#FFF8EC` cream                       | page background, card badge                                          | `bg-bg`                       |
| `--color-surface`                                | `#FFFFFF`                             | cards, fields, panels, toasts                                        | `bg-surface`                  |
| `--color-surface-muted`                          | `#FFF3DD`                             | category chips, image placeholder, skeleton                          | `bg-surface-muted`            |
| `--color-border`                                 | `#F0E3CC`                             | decorative card borders                                              | `border-border`               |
| `--color-border-strong`                          | `#8A7F9C`                             | field and `ghost` button borders (3:1)                               | `border-border-strong`        |
| `--color-text`                                   | `#2A2140` indigo                      | main text                                                            | `text-text`                   |
| `--color-text-muted`                             | `#6A5F7E`                             | secondary text (city, date, hint)                                    | `text-text-muted`             |
| `--color-brand` / `--color-on-brand`             | `#2A2140` / `#FFF8EC`                 | header, footer (SETUP-05)                                            | `bg-brand`, `text-on-brand`   |
| `--color-on-brand-muted` / `--color-brand-track` | `#E6DCF5` / `#4A3F66`                 | secondary text on indigo, track of a progress bar on indigo (ANN-04) |                               |
| `--color-accent` / `--color-on-accent`           | `#F2B33D` saffron / `#2A2140`         | secondary button, logo, banners                                      | `bg-accent`, `text-on-accent` |
| `--color-primary` / `--color-on-primary`         | `#B4441F` dark terracotta / `#FFFFFF` | primary button, links, prices                                        | `bg-primary`, `text-primary`  |
| `--color-primary-hover`                          | `#8E3416`                             | hover of the primary button and links                                | `bg-primary-hover`            |
| `--color-decorative`                             | `#E0602F` light terracotta            | decoration only (dot, icon on indigo), never under text              | `bg-decorative`               |
| `--color-danger`                                 | `#A8201A`                             | field errors, error toast                                            | `text-danger`                 |
| `--color-danger-surface`                         | `#FDECEA`                             | background of an error alert                                         | `bg-danger-surface`           |
| `--color-success`                                | `#1B6E45`                             | success toast                                                        | `text-success`                |
| `--color-success-surface`                        | `#E8F3EC`                             | background of a success alert                                        | `bg-success-surface`          |

Other tokens: `--font-display` (Lalezar, `h1`/`h2` headings), `--font-body` (Rubik 400, 500, 600); `--radius-sm` 6px, `--radius-md` 10px, `--radius-lg` 14px, `--radius-full`; `--shadow-card`, `--shadow-overlay`; `--focus-ring` (double ring: a background-coloured gap then an indigo ring, visible on light backgrounds and on indigo); `--control-height` 44px and `--control-height-lg` 52px. Spacing follows the default Tailwind scale.

### Tailwind theme

Tailwind CSS v4 no longer has JavaScript presets: the "preset" is the CSS file [`src/styles/tailwind.css`](src/styles/tailwind.css). It clears Tailwind's default colours, fonts, radii and shadows and only declares references to the tokens (`--color-primary: var(--color-primary)`) in an `@theme inline reference` block: utilities output `background-color: var(--color-primary)` and Tailwind does not emit a second copy of the variables. A class such as `bg-red-500` therefore does not exist.

## Contrast (WCAG 2.2 AA)

Ratios computed with the WCAG 2.x formula on the `tokens.css` values (2026-10-15), then checked by axe (`color-contrast` rule) in Chromium on `ui-showcase` in LTR and RTL: no violation. Threshold: 4.5:1 for text, 3:1 for text ≥ 24px (or ≥ 18.66px bold) and for UI components (field borders, icons, focus).

| Pair (foreground on background) | Used by                                     | Ratio | Verdict           |
| ------------------------------- | ------------------------------------------- | ----- | ----------------- |
| `text` on `bg`                  | body text, card badge                       | 14.31 | OK                |
| `text` on `surface`             | card, field, toast                          | 15.11 | OK                |
| `text` on `surface-muted`       | `ghost` button on hover                     | 13.75 | OK                |
| `text-muted` on `bg`            | secondary text                              | 5.60  | OK                |
| `text-muted` on `surface`       | card city and date, field hint, placeholder | 5.91  | OK                |
| `text-muted` on `surface-muted` | image placeholder icon                      | 5.38  | OK                |
| `primary` on `bg`               | links, favourite button                     | 5.25  | OK                |
| `primary` on `surface`          | card price                                  | 5.54  | OK                |
| `on-primary` on `primary`       | primary button                              | 5.54  | OK                |
| `on-primary` on `primary-hover` | primary button on hover                     | 7.92  | OK                |
| `on-accent` on `accent`         | secondary button                            | 8.12  | OK                |
| `danger` on `surface`           | error toast, required asterisk              | 7.28  | OK                |
| `danger` on `bg`                | field error message                         | 6.89  | OK                |
| `success` on `surface`          | success toast icon and border               | 6.24  | OK                |
| `danger` on `danger-surface`    | error alert                                 | 6.36  | OK                |
| `success` on `success-surface`  | success alert                               | 5.49  | OK                |
| `accent` on `brand`             | welcome text on indigo (AUTH-03)            | 8.12  | OK                |
| `border-strong` on `surface`    | field border, `ghost` button (non-text)     | 3.75  | OK (3:1)          |
| `border-strong` on `bg`         | same on cream                               | 3.55  | OK (3:1)          |
| `on-brand` on `brand`           | header, footer                              | 14.31 | OK                |
| `on-brand-muted` on `brand`     | step number of the publish header (ANN-04)  | 11.45 | OK                |
| `accent` on `brand-track`       | filled part of the progress bar (non-text)  | 5.15  | OK (3:1)          |
| white on `#E0602F`              | no text                                     | 3.57  | rejected for text |
| saffron on cream                | no text                                     | 1.76  | rejected for text |

The disabled button (reduced opacity) is exempt under WCAG (1.4.3, inactive component).

## Components

No hard-coded text: every label ("Fermer", "Ajouter aux favoris"…) comes in through an input or an option, translated by the app with Transloco and formatted with the pipes of [`@soukly/i18n`](../i18n/README.md).

| Component                          | Usage                                                                                                                                                                                                                                                                                                                                                                                                   | Accessibility                                                                                                                                                        |
| ---------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `UiButton`                         | `<button ui-button variant="primary\|secondary\|ghost" size="md\|lg" [loading] [disabled]>` or `<a ui-button>`; optional icon: `<svg lucidePlus uiButtonIcon></svg>`                                                                                                                                                                                                                                    | height ≥ 44px, visible focus, `aria-busy` and clicks ignored while loading (the button stays focusable), `aria-disabled` + `tabindex="-1"` on a disabled link        |
| `UiField` + `UiFieldControl`       | `<ui-field label="…" hint="…" [error]="…" required><input uiControl /></ui-field>` (also `textarea`, `select`); optional `<span uiFieldPrefix>+216</span>` before the control and `<button uiFieldSuffix aria-label="…">` inside its end; `ltr` keeps the control and its addons left-to-right in RTL (phone, email); `optionalLabel` adds "(optional)" after the label of a field that is not required | `label for`, `aria-describedby` to the hint and the error, `aria-invalid="true"` while an error is shown                                                             |
| `UiCheckbox` + `UiCheckboxControl` | `<ui-checkbox [error]="…"><input uiCheckboxControl type="checkbox" /> label, links allowed</ui-checkbox>`                                                                                                                                                                                                                                                                                               | `label for`, `aria-describedby` to the error and `aria-invalid="true"` while an error is shown, 20px box with the focus ring                                         |
| `UiAlert`                          | `<ui-alert type="info\|success\|error" [live]>message</ui-alert>`; `focus()`                                                                                                                                                                                                                                                                                                                            | `role="alert"` for errors, `role="status"` otherwise (none when `live` is false), focusable (`tabindex="-1"`) so a form can move the focus to its message            |
| `UiListingCard`                    | `title`, `price` (formatted), `city`, `dateLabel`, `dateTime`, `typeLabel`, `href`, `imageUrl` (or placeholder), `favorite`, `favoriteLabel`, `favoriteToggle` output                                                                                                                                                                                                                                   | `article`, `h3` title (link stretched over the whole card), favourite button with `aria-label` and `aria-pressed`, price isolated in `<bdi>`                         |
| `UiSkeleton`                       | `variant="line\|block\|card"`, `inlineSize`, `blockSize`                                                                                                                                                                                                                                                                                                                                                | `aria-hidden="true"` (the loading region carries `aria-busy`), animation stopped under `prefers-reduced-motion`                                                      |
| `UiToggleGroup`                    | `<ui-toggle-group legend="…" [options]="[{ value, label }]" [value] (valueChange) layout="pills\|tiles" [required] [error] [optionalLabel] [hideLegend]>`: one choice among a few, a second press clears it unless `required`                                                                                                                                                                           | `fieldset` and `legend` (visually hidden with `hideLegend`), buttons with `aria-pressed`, `aria-describedby` to the error, height ≥ 44px                             |
| `UiProgress`                       | `<ui-progress [value]="0..100" label="…" [valueText] tone="default\|on-brand">`                                                                                                                                                                                                                                                                                                                         | `role="progressbar"` with `aria-valuenow`, `aria-label` and `aria-valuetext`                                                                                         |
| `UiPhotoTile`                      | `src`, `alt`, `state="ready\|uploading\|failed"`, `progress` + `progressLabel`, `cover` + `coverLabel`, `removable` + `removeLabel`, `errorText`, `retryable` + `retryLabel`, `moveBeforeLabel` / `moveAfterLabel` (`canMoveBefore` / `canMoveAfter`); outputs `remove`, `retry`, `moveBefore`, `moveAfter`                                                                                             | 44px remove button, move buttons reachable with the keyboard (shown on focus and hover), progress bar while uploading, error and retry button when the upload failed |
| `ToastService`                     | `show({ type: 'info'\|'success'\|'error', message, closeLabel, duration? })` returns an id; `dismiss(id)`                                                                                                                                                                                                                                                                                               | Angular CDK overlay, `aria-live="polite"` region (`assertive` for `error`), auto-close after 5 s (paused on hover and focus) and a close button                      |

The favourite button has no logic: it reflects `favorite` and emits the next state; favourites management is out of V1.

Icons: [Lucide](https://lucide.dev) through `@lucide/angular` (official Angular package, standalone and signals), e.g. `<svg lucideHeart aria-hidden="true"></svg>`. Decorative icons get `aria-hidden="true"`.

## RTL rules

- Logical properties only: `margin-inline-start`, `padding-inline`, `inset-inline-end`, `border-inline-start`, `text-align: start`… Never `left`/`right`, `margin-left`, `padding-right`, `text-align: left`.
- In Tailwind: `ms-`/`me-`/`ps-`/`pe-`/`start-`/`end-`/`border-s`/`rounded-s`/`text-start`, never `ml-`/`mr-`/`pl-`/`pr-`/`left-`/`right-`/`border-l`/`rounded-l`/`text-left`.
- Directional icons (arrows, chevrons): global `ui-flip-rtl` class, mirrored in RTL.
- Prices: isolated in a `<bdi>` without `dir` (`price` pipe of `@soukly/i18n`): "1 250 DT" stays left-to-right, the Arabic "1.250 د.ت." right-to-left, and neither mixes with the text around it.
- Other numbers that must stay left-to-right (phone numbers): `<bdi dir="ltr">` or the `ui-ltr` class (`direction: ltr; unicode-bidi: isolate`).

The lint enforces these rules in `libs/ui` and in every app that imports [`eslint.rtl.mjs`](eslint.rtl.mjs):

- CSS: the `css/prefer-logical-properties` rule of [`@eslint/css`](https://github.com/eslint/css) (`width`/`height` sizes and viewport units stay allowed, they do not depend on the writing direction);
- Tailwind classes in templates (`class` attribute, `[class.x]`, strings in `[class]`/`[ngClass]`) and in TypeScript strings (`host: { class }`): local rule `soukly-rtl/no-physical-tailwind-classes`.

Sample output:

```text
error  Expected logical property 'margin-inline-start' instead of 'margin-left'                css/prefer-logical-properties
error  Physical Tailwind class "ml-4" breaks RTL layouts: use the logical class "ms-4"           soukly-rtl/no-physical-tailwind-classes
```

## Fonts and licences

`woff2` files in [`src/assets/fonts`](src/assets/fonts), Latin and Arabic subsets (the browser only downloads a file when the page contains a character of its `unicode-range`), `font-display: swap`. Files taken from the Fontsource 5.3.0 packages (`@fontsource-variable/rubik`, `@fontsource/lalezar`) and copied into the repository: no npm dependency and no network call at runtime.

| Font                                 | Files                                                               | Authors                                                                               | Licence                                                                          |
| ------------------------------------ | ------------------------------------------------------------------- | ------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------- |
| Rubik (variable, weights 300 to 900) | `rubik-latin-wght-normal.woff2`, `rubik-arabic-wght-normal.woff2`   | The Rubik Project Authors ([googlefonts/rubik](https://github.com/googlefonts/rubik)) | SIL Open Font License 1.1, [`OFL-Rubik.txt`](src/assets/fonts/OFL-Rubik.txt)     |
| Lalezar (400)                        | `lalezar-latin-400-normal.woff2`, `lalezar-arabic-400-normal.woff2` | The Lalezar Project Authors ([BornaIz/Lalezar](https://github.com/BornaIz/Lalezar))   | SIL Open Font License 1.1, [`OFL-Lalezar.txt`](src/assets/fonts/OFL-Lalezar.txt) |

The OFL allows using, modifying and redistributing the fonts with software, provided the licence text is kept and the fonts are not sold on their own.
