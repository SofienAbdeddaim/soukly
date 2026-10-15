import {
  DOCUMENT,
  EnvironmentProviders,
  inject,
  Injectable,
  isDevMode,
  makeEnvironmentProviders,
  provideAppInitializer,
  provideEnvironmentInitializer,
} from '@angular/core';
import { provideTransloco, Translation, TranslocoLoader } from '@jsverse/transloco';
import { LangActivation } from './lang-activation';
import { DEFAULT_LANG, SUPPORTED_LANGS } from './languages';
import { LocaleService } from './locale.service';
import { MissingTranslationReporter } from './missing-translations';
import { fetchTranslation } from './translation-files';

/** Root translations of the shell: `public/i18n/<lang>.json`, served next to the shell. */
@Injectable({ providedIn: 'root' })
export class RootTranslationLoader implements TranslocoLoader {
  getTranslation(lang: string): Promise<Translation> {
    return fetchTranslation(`/i18n/${lang}.json`);
  }
}

/** For a remote launched alone: it has no root translations, only its own scope. */
@Injectable({ providedIn: 'root' })
export class NoRootTranslationLoader implements TranslocoLoader {
  getTranslation(): Promise<Translation> {
    return Promise.resolve({});
  }
}

export interface I18nOptions {
  /** False for a remote launched alone, which has no root translation files. Default true. */
  rootTranslations?: boolean;
}

/**
 * Transloco for Soukly: fr (default and fallback), ar, en. A missing key falls back to French,
 * with a console warning in development only (MissingTranslationReporter). Before the first render, the language of the URL
 * prefix (or the preferred one, for a URL the router will redirect) is activated, so the first
 * paint already has the right texts and direction.
 */
export function provideI18n(options: I18nOptions = {}): EnvironmentProviders {
  const devMode = isDevMode();
  return makeEnvironmentProviders([
    provideTransloco({
      config: {
        availableLangs: [...SUPPORTED_LANGS],
        defaultLang: DEFAULT_LANG,
        fallbackLang: DEFAULT_LANG,
        reRenderOnLangChange: true,
        prodMode: !devMode,
        missingHandler: { useFallbackTranslation: true, logMissingKey: devMode },
      },
      loader: options.rootTranslations === false ? NoRootTranslationLoader : RootTranslationLoader,
    }),
    devMode ? provideEnvironmentInitializer(() => inject(MissingTranslationReporter).start()) : [],
    provideAppInitializer(() => {
      const firstSegment = inject(DOCUMENT).location.pathname.split('/')[1];
      return inject(LangActivation).activate(inject(LocaleService).langForSegment(firstSegment));
    }),
  ]);
}
