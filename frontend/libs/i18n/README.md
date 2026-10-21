# @soukly/i18n

Languages of Soukly: French (default and fallback), Arabic (right-to-left) and English. The library holds the language list, the `/{lang}/` URL prefix and its redirections, the [Transloco](https://jsverse.gitbook.io/transloco) setup shared by the shell and the remotes, and the Tunisian formats of prices and dates (through `Intl`, no formatting library).

```bash
cd frontend
pnpm nx test i18n                 # unit tests (guard, LocaleService, pipes in the 3 locales…)
pnpm nx run-many -t i18n-check    # same keys in fr, ar and en, for every scope
pnpm nx e2e shell-e2e             # redirections, language switcher, RTL (src/i18n.spec.ts)
```

## Languages and URLs

| Language                        | Prefix  | Direction | `Intl` locale | Name in the switcher |
| ------------------------------- | ------- | --------- | ------------- | -------------------- |
| French (default, fallback)      | `/fr/…` | `ltr`     | `fr-TN`       | Français             |
| Arabic (Modern Standard Arabic) | `/ar/…` | `rtl`     | `ar-TN`       | العربية              |
| English                         | `/en/…` | `ltr`     | `en-TN`       | English              |

- Every page URL starts with its language; the rest of the path is the same in the three languages (`/ar/account/login`). Routes are wrapped with `withLangPrefix(routes)`.
- A URL without prefix (`/`, `/account/login`) is redirected to the same page in the preferred language: the language chosen earlier in the switcher (`localStorage`, key `soukly.lang`), then the first supported language of `navigator.languages`, then `fr`. Query parameters and fragment are kept.
- An unknown language prefix (`/de/publish/new`) is replaced with `/fr/`. A first segment "looks like" a language when it is two letters, optionally with a region (`de`, `pt-BR`).
- The language guard downloads the translations, then sets `lang` and `dir` on `<html>` and switches Transloco in the same tick: no text is ever shown in the wrong direction. Before the first render, an app initializer does the same for the URL being opened.
- The language switcher of the header only changes the URL prefix (router navigation, no reload) and remembers the choice.
- `AlternateLinks` keeps `<link rel="alternate" hreflang="fr|ar|en|x-default">` in `<head>` on the origin of the page (`x-default` is French). The domain is never hard-coded.
- `acceptLanguageInterceptor` sends `Accept-Language: <lang>` with every `HttpClient` call (used by AUTH-01 for the language of the emails).

## Translations

| Scope                                        | Files                                                  | Loaded                                  |
| -------------------------------------------- | ------------------------------------------------------ | --------------------------------------- |
| root (shell: header, footer, generic errors) | `apps/shell/public/i18n/{fr,ar,en}.json`               | with the shell, before the first render |
| `account`                                    | `apps/mfe-account/public/i18n/account/{fr,ar,en}.json` | when a page of mfe-account is shown     |
| `catalog`                                    | `apps/mfe-catalog/public/i18n/catalog/{fr,ar,en}.json` | when a page of mfe-catalog is shown     |
| `publish`                                    | `apps/mfe-publish/public/i18n/publish/{fr,ar,en}.json` | when a page of mfe-publish is shown     |

Templates use the Transloco pipe; scoped keys start with the scope: `{{ 'header.login' | transloco }}`, `{{ 'publish.coming-soon.title' | transloco }}`.

**Remote files come from the remote, not from the shell.** A remote declares its scope on the route that wraps its exposed routes:

```ts
export const routes: Routes = [
  { path: '', providers: [provideI18nScope('publish', import.meta.url)], children: [...] },
];
```

`import.meta.url` is the URL the browser loaded the module from, so `i18n/publish/ar.json` is resolved next to the chunks of the remote (`http://localhost:4203/i18n/publish/ar.json` locally, the remote's own address in each environment, as given by the federation manifest). No extra configuration per environment, and the remote launched alone uses the same files. The remote's server must allow cross-origin `GET` on these files, as it already does for its JavaScript.

**Missing key at runtime**: Transloco shows the French text. In development only, the console warns: `[i18n] Missing translation "header.login" in "ar": the French text is shown.` A key missing in every language shows the key itself, with Transloco's own warning.

### Naming conventions

- Keys in English, `kebab-case`, nested by area: `header.post-listing`, `errors.not-found.title`. No sentence as key.
- One JSON file per language and scope, same nesting in the three files. Values are non-empty strings.
- Shared texts (header, footer, generic errors) go in the root scope; the texts of a remote go in its scope, never in the root one.
- User content (listing titles and descriptions) is never translated: display it with `dir="auto"`.
- Arabic texts are Modern Standard Arabic, reviewed by an Arabic speaker before production.

### Adding a key

1. Add it to `fr.json`, `ar.json` and `en.json` of the scope, at the same place.
2. Use it in the template with the `transloco` pipe (`'<scope>.<key>'` in a remote).
3. Run `pnpm nx run-many -t i18n-check`: it fails, naming the key, the scope and the file, as long as one language is missing.

### Adding a language

1. Add its code to `SUPPORTED_LANGS` in [`languages.ts`](src/lib/languages.ts), with its `INTL_LOCALES`, `LANG_NAMES` (in its own language) and `LANG_SHORT_NAMES` entries; update `directionOf` if it is right-to-left.
2. Add it to `LANGS` in [`tools/i18n-check/i18n-check.mjs`](../../tools/i18n-check/i18n-check.mjs).
3. Create `<lang>.json` in `apps/shell/public/i18n/` and in every `apps/mfe-*/public/i18n/<scope>/`, then run `i18n-check`.
4. Check its fonts: Rubik and Lalezar cover Latin and Arabic only.

## Formats

`Intl` only, Latin digits (`-u-nu-latn`) in every language. Pipes are impure: they follow the current language.

| Pipe / function                       | Rule                                                               | `fr-TN`        | `ar-TN`      | `en-TN`        |
| ------------------------------------- | ------------------------------------------------------------------ | -------------- | ------------ | -------------- |
| `price` / `formatPrice`               | whole dinars (TND, no millimes), no decimals                       | `1 250 DT`     | `1.250 د.ت.` | `TND 1,250`    |
| `currencySymbol`                      | symbol of the dinar, for the unit of a price field                 | `DT`           | `د.ت.`       | `TND`          |
| `formatNumber`                        | whole number with the thousands separators of the language         | `32 000`       | `32.000`     | `32,000`       |
| `localDate` / `formatLocalDate`       | `dateStyle: 'medium'`                                              | `12 oct. 2026` | `12/10/2026` | `Oct 12, 2026` |
| `relativeTime` / `formatRelativeTime` | `Intl.RelativeTimeFormat`, `numeric: 'auto'`, largest fitting unit | `hier`         | `أمس`        | `yesterday`    |

The exact output depends on the ICU version of the browser (checked with Node 22 and Chromium). Prices are always shown isolated from the text around them (`<bdi>`, as `ui-listing-card` does), so Arabic text and digits never mix.

## API

| Export                                             | Role                                                                                                                                                 |
| -------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- |
| `provideI18n(options?)`                            | Transloco (fr/ar/en, fallback fr), activation of the URL language before the first render; `{ rootTranslations: false }` for a remote launched alone |
| `withLangPrefix(routes)`                           | `:lang` route (`langMatchGuard`, `langGuard`) + redirection of every other URL                                                                       |
| `provideI18nScope(scope, import.meta.url)`         | translations of a remote                                                                                                                             |
| `LocaleService`                                    | `lang`, `dir`, `intlLocale` signals; `use()`, `remember()`, `preferredLang()`                                                                        |
| `localePath` pipe, `localizePath()`, `urlInLang()` | links under the current language, same page in another language                                                                                      |
| `price`, `localDate`, `relativeTime` pipes         | formats above                                                                                                                                        |
| `acceptLanguageInterceptor`, `AlternateLinks`      | `Accept-Language` header, `hreflang` links                                                                                                           |
| `i18nTesting(langs, lang?)`                        | Transloco for unit tests, with translations already loaded                                                                                           |
