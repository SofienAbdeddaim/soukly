import { directionOf, firstSupportedLang, isLang, looksLikeLangCode } from './languages';

describe('languages', () => {
  it('supports fr, ar and en only', () => {
    expect(['fr', 'ar', 'en'].every(isLang)).toBe(true);
    expect(isLang('de')).toBe(false);
    expect(isLang(undefined)).toBe(false);
  });

  it('is right-to-left in Arabic only', () => {
    expect(directionOf('ar')).toBe('rtl');
    expect(directionOf('fr')).toBe('ltr');
    expect(directionOf('en')).toBe('ltr');
  });

  it('finds the first supported browser language by its primary subtag', () => {
    expect(firstSupportedLang(['de-DE', 'ar-TN', 'fr'])).toBe('ar');
    expect(firstSupportedLang(['EN-us'])).toBe('en');
    expect(firstSupportedLang(['de', 'it'])).toBeNull();
  });

  it('tells a language code prefix from a page path', () => {
    expect(looksLikeLangCode('de')).toBe(true);
    expect(looksLikeLangCode('pt-BR')).toBe(true);
    expect(looksLikeLangCode('account')).toBe(false);
    expect(looksLikeLangCode(undefined)).toBe(false);
  });
});
