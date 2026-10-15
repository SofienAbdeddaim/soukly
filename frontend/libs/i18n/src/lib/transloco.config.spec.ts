import { TestBed } from '@angular/core/testing';
import { TranslocoService } from '@jsverse/transloco';
import { LangActivation } from './lang-activation';
import { LocaleService } from './locale.service';
import { provideI18n } from './transloco.config';

const files: Record<string, unknown> = {
  '/i18n/fr.json': { header: { login: 'Se connecter', 'post-listing': 'Déposer une annonce' } },
  '/i18n/ar.json': { header: { login: 'تسجيل الدخول' } },
  '/i18n/en.json': { header: { login: 'Log in', 'post-listing': 'Post an ad' } },
};

describe('provideI18n', () => {
  let fetchSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    vi.spyOn(navigator, 'languages', 'get').mockReturnValue(['fr-FR']);
    fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(async (url) => {
      const body = files[String(url)];
      return body ? new Response(JSON.stringify(body)) : new Response('', { status: 404 });
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
    TestBed.inject(LocaleService).use('fr');
  });

  it('loads the root translations of the shell and switches language', async () => {
    TestBed.configureTestingModule({ providers: [provideI18n()] });
    const transloco = TestBed.inject(TranslocoService);

    await TestBed.inject(LangActivation).activate('en');

    expect(fetchSpy).toHaveBeenCalledWith('/i18n/en.json');
    expect(transloco.getActiveLang()).toBe('en');
    expect(transloco.translate('header.login')).toBe('Log in');
    expect(document.documentElement.lang).toBe('en');
  });

  it('falls back to French for a missing key, with a warning in development', async () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    TestBed.configureTestingModule({ providers: [provideI18n()] });
    const transloco = TestBed.inject(TranslocoService);

    await TestBed.inject(LangActivation).activate('ar');

    expect(transloco.translate('header.login')).toBe('تسجيل الدخول');
    expect(transloco.translate('header.post-listing')).toBe('Déposer une annonce');
    expect(warn).toHaveBeenCalledWith(
      '[i18n] Missing translation "header.post-listing" in "ar": the French text is shown.',
    );
    expect(warn).toHaveBeenCalledTimes(1);
  });

  it('keeps the page usable when a translation file cannot be loaded', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    TestBed.configureTestingModule({ providers: [provideI18n({ rootTranslations: false })] });

    await TestBed.inject(LangActivation).activate('ar');

    expect(fetchSpy).not.toHaveBeenCalledWith('/i18n/ar.json');
    expect(document.documentElement.dir).toBe('rtl');
  });
});
