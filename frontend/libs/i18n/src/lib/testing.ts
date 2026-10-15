import { ModuleWithProviders } from '@angular/core';
import { Translation, TranslocoTestingModule } from '@jsverse/transloco';
import { DEFAULT_LANG, Lang, SUPPORTED_LANGS } from './languages';

/**
 * Transloco for unit tests, with the given translations already loaded (no HTTP). Scoped
 * translations use the "<scope>/<lang>" key: `{ fr, 'publish/fr': publishFr }`.
 */
export function i18nTesting(
  langs: Record<string, Translation>,
  lang: Lang = DEFAULT_LANG,
): ModuleWithProviders<TranslocoTestingModule> {
  return TranslocoTestingModule.forRoot({
    langs,
    translocoConfig: {
      availableLangs: [...SUPPORTED_LANGS],
      defaultLang: lang,
      fallbackLang: DEFAULT_LANG,
      missingHandler: { useFallbackTranslation: true, logMissingKey: false },
    },
    preloadLangs: true,
  });
}
