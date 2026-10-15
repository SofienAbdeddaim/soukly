import { TestBed } from '@angular/core/testing';
import { LANG_STORAGE_KEY } from './lang-storage';
import { LocaleService } from './locale.service';

describe('LocaleService', () => {
  let locale: LocaleService;

  beforeEach(() => {
    locale = TestBed.inject(LocaleService);
  });

  afterEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
    locale.use('fr');
  });

  it('starts in French, left-to-right, with the fr-TN locale', () => {
    expect(locale.lang()).toBe('fr');
    expect(locale.dir()).toBe('ltr');
    expect(locale.intlLocale()).toBe('fr-TN');
  });

  it('sets lang and dir on <html> with the language', () => {
    locale.use('ar');

    expect(locale.lang()).toBe('ar');
    expect(locale.dir()).toBe('rtl');
    expect(locale.intlLocale()).toBe('ar-TN');
    expect(document.documentElement.lang).toBe('ar');
    expect(document.documentElement.dir).toBe('rtl');

    locale.use('en');

    expect(document.documentElement.lang).toBe('en');
    expect(document.documentElement.dir).toBe('ltr');
  });

  describe('preferred language', () => {
    function browserLanguages(languages: string[]) {
      vi.spyOn(navigator, 'languages', 'get').mockReturnValue(languages);
    }

    it('is the remembered choice first', () => {
      browserLanguages(['ar-TN']);
      locale.remember('en');

      expect(localStorage.getItem(LANG_STORAGE_KEY)).toBe('en');
      expect(locale.preferredLang()).toBe('en');
    });

    it('then the first supported browser language', () => {
      browserLanguages(['de-DE', 'ar-TN', 'fr-FR']);

      expect(locale.preferredLang()).toBe('ar');
    });

    it('then French', () => {
      browserLanguages(['de-DE']);

      expect(locale.preferredLang()).toBe('fr');
    });

    it('ignores an invalid remembered value and a storage that throws', () => {
      browserLanguages(['en-GB']);
      localStorage.setItem(LANG_STORAGE_KEY, 'klingon');
      expect(locale.preferredLang()).toBe('en');

      vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
        throw new Error('SecurityError');
      });
      vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
        throw new Error('QuotaExceededError');
      });
      expect(() => locale.remember('ar')).not.toThrow();
      expect(locale.preferredLang()).toBe('en');
    });
  });

  it('gives the language of a URL from its first segment', () => {
    vi.spyOn(navigator, 'languages', 'get').mockReturnValue(['ar']);

    expect(locale.langForSegment('en')).toBe('en');
    expect(locale.langForSegment('de')).toBe('fr');
    expect(locale.langForSegment('account')).toBe('ar');
    expect(locale.langForSegment('')).toBe('ar');
  });
});
