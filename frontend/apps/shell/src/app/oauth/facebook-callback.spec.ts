import { Location } from '@angular/common';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { AuthService, authInterceptor, sessionResponse } from '@soukly/auth';
import { shellI18nTesting } from '../testing/shell-i18n-testing';
import { FacebookCallback } from './facebook-callback';

@Component({ template: '' })
class BlankPage {}

const settle = () => new Promise((resolve) => setTimeout(resolve));

describe('FacebookCallback', () => {
  async function setup(signedIn = true) {
    TestBed.configureTestingModule({
      imports: [shellI18nTesting()],
      providers: [
        provideRouter([
          { path: 'oauth/facebook/callback', component: FacebookCallback },
          { path: '**', component: BlankPage },
        ]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    const http = TestBed.inject(HttpTestingController);
    if (signedIn) {
      const login = TestBed.inject(AuthService).login('amira@example.tn', 'secret-password');
      http.expectOne('/api/users/login').flush(sessionResponse('token-1'));
      await login;
    }
    const replaceState = vi.spyOn(TestBed.inject(Location), 'replaceState');
    const harness = await RouterTestingHarness.create();
    return { http, harness, replaceState };
  }

  it('hands the code to the API, hides it from the address bar and goes back in the language of the connection', async () => {
    const { http, harness, replaceState } = await setup();
    await harness.navigateByUrl('/oauth/facebook/callback?code=the-code&state=the-state');

    expect(harness.routeNativeElement?.textContent).toContain('Connexion à Facebook');
    expect(replaceState).toHaveBeenCalledWith('/oauth/facebook/callback');
    const request = http.expectOne('/api/social/facebook/callback');
    expect(request.request.body).toEqual({ code: 'the-code', state: 'the-state', error: null });
    expect(request.request.headers.get('Authorization')).toBe('Bearer token-1');
    request.flush({
      lang: 'ar',
      connection: {
        network: 'FACEBOOK',
        status: 'CONNECTED',
        displayName: 'Sfax Auto Occasion',
        pictureUrl: null,
        connectedAt: '2026-10-12T09:30:00Z',
        autoShare: true,
      },
    });
    await settle();

    expect(TestBed.inject(Router).url).toBe('/ar/account/social?facebook=connected');
  });

  it('opens the choice of a Page when the member manages several', async () => {
    const { http, harness } = await setup();
    await harness.navigateByUrl('/oauth/facebook/callback?code=c&state=s');
    http.expectOne('/api/social/facebook/callback').flush({
      lang: 'fr',
      pendingSelection: [{ pageId: '1', name: 'A', category: null, pictureUrl: null }],
    });
    await settle();

    expect(TestBed.inject(Router).url).toBe('/fr/account/social/facebook/page');
  });

  it('brings back the reason of a failure', async () => {
    const { http, harness } = await setup();
    await harness.navigateByUrl('/oauth/facebook/callback?error=access_denied&state=s');
    const request = http.expectOne('/api/social/facebook/callback');
    expect(request.request.body).toEqual({ code: null, state: 's', error: 'access_denied' });
    request.flush(
      { type: 'urn:soukly:problem:oauth-cancelled', status: 400, lang: 'en' },
      { status: 400, statusText: 'Bad Request' },
    );
    await settle();

    expect(TestBed.inject(Router).url).toBe('/en/account/social?facebook=oauth-cancelled');
  });

  it('falls back to the preferred language when the state was not valid', async () => {
    const { http, harness } = await setup();
    await harness.navigateByUrl('/oauth/facebook/callback?code=c&state=forged');
    http
      .expectOne('/api/social/facebook/callback')
      .flush(
        { type: 'urn:soukly:problem:oauth-state-invalid', status: 400 },
        { status: 400, statusText: 'Bad Request' },
      );
    await settle();

    expect(TestBed.inject(Router).url).toMatch(
      /^\/(fr|ar|en)\/account\/social\?facebook=oauth-state-invalid$/,
    );
  });

  it('sends a visitor without session to the login page, never posting the code', async () => {
    const { http, harness } = await setup(false);
    await harness.navigateByUrl('/oauth/facebook/callback?code=c&state=s');
    await settle();

    http.expectNone('/api/social/facebook/callback');
    expect(TestBed.inject(Router).url).toMatch(
      /^\/(fr|ar|en)\/account\/login\?returnUrl=%2F(fr|ar|en)%2Faccount%2Fsocial$/,
    );
  });
});
