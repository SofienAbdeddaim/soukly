import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { LANG_STORAGE_KEY } from './lang-storage';
import { withLangPrefix } from './lang-routes';
import { LocaleService } from './locale.service';
import { i18nTesting } from './testing';

@Component({ template: '' })
class Page {}

describe('withLangPrefix', () => {
  let router: Router;

  function browserLanguages(languages: string[]) {
    vi.spyOn(navigator, 'languages', 'get').mockReturnValue(languages);
  }

  async function open(url: string): Promise<string> {
    await router.navigateByUrl(url);
    return router.url;
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [i18nTesting({ fr: { hello: 'Bonjour' }, ar: { hello: 'مرحبا' }, en: {} })],
      providers: [
        provideRouter(
          withLangPrefix([
            { path: '', pathMatch: 'full', component: Page },
            { path: 'account/login', component: Page },
            { path: 'publish/new', component: Page },
          ]),
        ),
      ],
    });
    router = TestBed.inject(Router);
    browserLanguages(['fr-FR']);
  });

  afterEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
    TestBed.inject(LocaleService).use('fr');
  });

  it('redirects "/" to the first supported browser language', async () => {
    browserLanguages(['ar-TN', 'fr']);

    expect(await open('/')).toBe('/ar');
    expect(document.documentElement.lang).toBe('ar');
    expect(document.documentElement.dir).toBe('rtl');
  });

  it('redirects a browser in an unsupported language to French', async () => {
    browserLanguages(['de-DE', 'de']);

    expect(await open('/')).toBe('/fr');
  });

  it('prefers the remembered language over the browser languages', async () => {
    browserLanguages(['ar-TN']);
    localStorage.setItem(LANG_STORAGE_KEY, 'en');

    expect(await open('/')).toBe('/en');
  });

  it('prefixes a path without language, keeping the query parameters and the fragment', async () => {
    browserLanguages(['ar']);

    expect(await open('/account/login?next=%2Fpublish#form')).toBe(
      '/ar/account/login?next=%2Fpublish#form',
    );
  });

  it('replaces an unknown language prefix with French', async () => {
    browserLanguages(['ar']);

    expect(await open('/de/publish/new')).toBe('/fr/publish/new');
  });

  it('activates the language of the prefix, with its translations', async () => {
    expect(await open('/ar/publish/new')).toBe('/ar/publish/new');
    const locale = TestBed.inject(LocaleService);
    expect(locale.lang()).toBe('ar');
    expect(document.documentElement.dir).toBe('rtl');

    expect(await open('/en/publish/new')).toBe('/en/publish/new');
    expect(locale.lang()).toBe('en');
    expect(document.documentElement.lang).toBe('en');
    expect(document.documentElement.dir).toBe('ltr');
  });
});
