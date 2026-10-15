import { Provider } from '@angular/core';
import { provideTranslocoScope, Translation } from '@jsverse/transloco';
import { SUPPORTED_LANGS } from './languages';
import { fetchTranslation } from './translation-files';

/**
 * Translations of a remote (one Transloco scope per remote), to add to the `providers` of the
 * route that wraps its exposed routes. The files `public/i18n/<scope>/<lang>.json` of the remote
 * are downloaded from where the remote is deployed, not from the shell: `baseUrl` is the
 * `import.meta.url` of a module of the remote, which the browser loaded from that origin.
 * A file is only requested when a page of the remote is shown in that language.
 * Keys are read with the scope as prefix: `'publish.coming-soon.title' | transloco`.
 */
export function provideI18nScope(scope: string, baseUrl: string | URL): Provider {
  const loader = Object.fromEntries(
    SUPPORTED_LANGS.map((lang) => [
      lang,
      (): Promise<Translation> => fetchTranslation(scopeFileUrl(scope, lang, baseUrl)),
    ]),
  );
  return provideTranslocoScope({ scope, loader });
}

/**
 * URL of `i18n/<scope>/<lang>.json` next to the module `baseUrl`: the build puts the chunks of an app
 * and its `public` files in the same folder.
 */
export function scopeFileUrl(scope: string, lang: string, baseUrl: string | URL): URL {
  return new URL(`i18n/${scope}/${lang}.json`, baseUrl);
}
