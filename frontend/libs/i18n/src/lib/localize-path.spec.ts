import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { localizePath, LocalePathPipe, urlInLang } from './localize-path';
import { LocaleService } from './locale.service';

describe('localizePath', () => {
  it('prefixes an absolute path with the language', () => {
    expect(localizePath('/', 'ar')).toBe('/ar');
    expect(localizePath('/account/login', 'en')).toBe('/en/account/login');
  });
});

describe('urlInLang', () => {
  it('replaces the language prefix and keeps the rest of the URL', () => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    const router = TestBed.inject(Router);
    const url = (value: string, lang: 'fr' | 'ar' | 'en') =>
      router.serializeUrl(urlInLang(router, value, lang));

    expect(url('/fr/account/login?next=%2Fpublish#top', 'ar')).toBe(
      '/ar/account/login?next=%2Fpublish#top',
    );
    expect(url('/fr', 'en')).toBe('/en');
    expect(url('/publish/new', 'ar')).toBe('/ar/publish/new');
  });
});

describe('LocalePathPipe', () => {
  @Component({
    imports: [LocalePathPipe],
    template: `<span>{{ '/publish/new' | localePath }}</span>`,
  })
  class Host {}

  it('follows the current language', async () => {
    const fixture = TestBed.createComponent(Host);
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toBe('/fr/publish/new');

    TestBed.inject(LocaleService).use('ar');
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toBe('/ar/publish/new');
    TestBed.inject(LocaleService).use('fr');
  });
});
