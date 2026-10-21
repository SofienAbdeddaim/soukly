/**
 * Key of the plural form of a count, from the rules of the language (`Intl.PluralRules`): Arabic
 * has six forms (`zero`, `one`, `two`, `few`, `many`, `other`), French and English fewer. Every
 * pluralized text of the `account` scope has the six keys in each language, so the same key
 * exists everywhere (i18n-check).
 */
export function pluralKey(base: string, count: number, locale: string): string {
  return `${base}.${new Intl.PluralRules(locale).select(count)}`;
}
