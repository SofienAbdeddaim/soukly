/** Languages of Soukly. The first one is the default and fallback language. */
export const SUPPORTED_LANGS = ['fr', 'ar', 'en'] as const;

export type Lang = (typeof SUPPORTED_LANGS)[number];

export const DEFAULT_LANG: Lang = 'fr';

export type TextDirection = 'ltr' | 'rtl';

/** Locale given to `Intl` for prices and dates: the language, as spoken in Tunisia. */
export const INTL_LOCALES: Record<Lang, string> = {
  fr: 'fr-TN',
  ar: 'ar-TN',
  en: 'en-TN',
};

/** Name of each language, written in that language (language switcher). */
export const LANG_NAMES: Record<Lang, string> = {
  fr: 'Français',
  ar: 'العربية',
  en: 'English',
};

/** Short label of each language, for the closed language switcher. */
export const LANG_SHORT_NAMES: Record<Lang, string> = {
  fr: 'FR',
  ar: 'ع',
  en: 'EN',
};

export function isLang(value: unknown): value is Lang {
  return typeof value === 'string' && (SUPPORTED_LANGS as readonly string[]).includes(value);
}

export function directionOf(lang: Lang): TextDirection {
  return lang === 'ar' ? 'rtl' : 'ltr';
}

/**
 * True for a first URL segment that looks like a language code ("de", "pt-BR") but may not be
 * supported: such a prefix is replaced, any other first segment ("account") is kept.
 */
export function looksLikeLangCode(segment: string | undefined): boolean {
  return !!segment && /^[a-z]{2}(?:-[a-z]{2})?$/i.test(segment);
}

/** First supported language among browser languages ("ar-TN" -> "ar"), or null. */
export function firstSupportedLang(languages: readonly string[]): Lang | null {
  for (const language of languages) {
    const primary = language.split('-')[0]?.toLowerCase();
    if (isLang(primary)) {
      return primary;
    }
  }
  return null;
}
