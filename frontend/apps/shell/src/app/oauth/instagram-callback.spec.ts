import { Location } from '@angular/common';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { AuthService, authInterceptor, sessionResponse } from '@soukly/auth';
import { shellI18nTesting } from '../testing/shell-i18n-testing';
import { InstagramCallback } from './instagram-callback';

@Component({ template: '' })
class BlankPage {}

const settle = () => new Promise((resolve) => setTimeout(resolve));

describe('InstagramCallback', () => {
  async function setup(signedIn = true) {
    TestBed.configureTestingModule({
      imports: [shellI18nTesting()],
      providers: [
        provideRouter([
          { path: 'oauth/instagram/callback', component: InstagramCallback },
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
    await harness.navigateByUrl('/oauth/instagram/callback?code=the-code&state=the-state');

    expect(harness.routeNativeElement?.textContent).toContain('Connexion à Instagram');
    expect(replaceState).toHaveBeenCalledWith('/oauth/instagram/callback');
    const request = http.expectOne('/api/social/instagram/callback');
    expect(request.request.body).toEqual({ code: 'the-code', state: 'the-state', error: null });
    expect(request.request.headers.get('Authorization')).toBe('Bearer token-1');
    request.flush({
      lang: 'ar',
      connection: {
        network: 'INSTAGRAM',
        status: 'CONNECTED',
        displayName: '@sfax.auto',
        pictureUrl: null,
        connectedAt: '2026-10-12T09:30:00Z',
      },
    });
    await settle();

    expect(TestBed.inject(Router).url).toBe('/ar/account/social?instagram=connected');
  });

  it('brings back the reason of a failure, a personal account included', async () => {
    const { http, harness } = await setup();
    await harness.navigateByUrl('/oauth/instagram/callback?code=c&state=s');
    http
      .expectOne('/api/social/instagram/callback')
      .flush(
        { type: 'urn:soukly:problem:instagram-not-professional', status: 400, lang: 'en' },
        { status: 400, statusText: 'Bad Request' },
      );
    await settle();

    expect(TestBed.inject(Router).url).toBe(
      '/en/account/social?instagram=instagram-not-professional',
    );
  });

  it('falls back to the preferred language when the state was not valid', async () => {
    const { http, harness } = await setup();
    await harness.navigateByUrl('/oauth/instagram/callback?error=access_denied&state=forged');
    http
      .expectOne('/api/social/instagram/callback')
      .flush(
        { type: 'urn:soukly:problem:oauth-state-invalid', status: 400 },
        { status: 400, statusText: 'Bad Request' },
      );
    await settle();

    expect(TestBed.inject(Router).url).toMatch(
      /^\/(fr|ar|en)\/account\/social\?instagram=oauth-state-invalid$/,
    );
  });

  it('sends a visitor without session to the login page, never posting the code', async () => {
    const { http, harness } = await setup(false);
    await harness.navigateByUrl('/oauth/instagram/callback?code=c&state=s');
    await settle();

    http.expectNone('/api/social/instagram/callback');
    expect(TestBed.inject(Router).url).toMatch(
      /^\/(fr|ar|en)\/account\/login\?returnUrl=%2F(fr|ar|en)%2Faccount%2Fsocial$/,
    );
  });
});
