import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { shellI18nTesting } from '../testing/shell-i18n-testing';
import { ShortRefRedirect } from './short-ref-redirect';

@Component({ template: '' })
class BlankPage {}

const settle = () => new Promise((resolve) => setTimeout(resolve));

describe('ShortRefRedirect', () => {
  async function setup(lang?: string) {
    if (lang) {
      localStorage.setItem('soukly.lang', lang);
    }
    TestBed.configureTestingModule({
      imports: [shellI18nTesting()],
      providers: [
        provideRouter([
          { path: 'r/:ref', component: ShortRefRedirect },
          { path: '**', component: BlankPage },
        ]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    const http = TestBed.inject(HttpTestingController);
    const harness = await RouterTestingHarness.create();
    return { http, harness };
  }

  afterEach(() => localStorage.clear());

  it('opens the listing of a reference typed in lower case, with the Instagram tracking', async () => {
    const { http, harness } = await setup('fr');
    await harness.navigateByUrl('/r/8f3c2k9a');

    expect(harness.routeNativeElement?.textContent).toContain("Ouverture de l'annonce");
    http
      .expectOne('/api/listings/by-ref/8f3c2k9a')
      .flush({ id: '5f0c1d2e-0000-4000-8000-000000000001' });
    await settle();

    expect(TestBed.inject(Router).url).toBe(
      '/fr/listings/5f0c1d2e-0000-4000-8000-000000000001' +
        '?utm_source=instagram&utm_medium=social&utm_campaign=soukly_share',
    );
  });

  it('uses the remembered language', async () => {
    const { http, harness } = await setup('ar');
    await harness.navigateByUrl('/r/8F3C2K9A');
    http.expectOne('/api/listings/by-ref/8F3C2K9A').flush({ id: 'abc' });
    await settle();

    expect(TestBed.inject(Router).url).toMatch(/^\/ar\/listings\/abc\?utm_source=instagram/);
  });

  it('shows the "listing not found" page of an unknown reference', async () => {
    const { http, harness } = await setup('en');
    await harness.navigateByUrl('/r/ZZZZZZZZ');
    http
      .expectOne('/api/listings/by-ref/ZZZZZZZZ')
      .flush(
        { type: 'urn:soukly:problem:listing-not-found', status: 404 },
        { status: 404, statusText: 'Not Found' },
      );
    await settle();

    expect(TestBed.inject(Router).url).toBe('/en/listings/ZZZZZZZZ');
  });

  it('lets the visitor try again when the service does not answer', async () => {
    const { http, harness } = await setup('fr');
    await harness.navigateByUrl('/r/8F3C2K9A');
    http
      .expectOne('/api/listings/by-ref/8F3C2K9A')
      .flush(null, { status: 503, statusText: 'Service Unavailable' });
    await settle();
    harness.detectChanges();

    expect(TestBed.inject(Router).url).toBe('/r/8F3C2K9A');
    const retry = harness.routeNativeElement?.querySelector('button') as HTMLButtonElement;
    expect(harness.routeNativeElement?.textContent).toContain("L'annonce n'a pas pu être ouverte");
    retry.click();
    http.expectOne('/api/listings/by-ref/8F3C2K9A').flush({ id: 'abc' });
    await settle();

    expect(TestBed.inject(Router).url).toMatch(/^\/fr\/listings\/abc\?/);
  });
});
