import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthService, authInterceptor, sessionResponse } from '@soukly/auth';
import { SocialConnection } from './models';
import { SocialApi } from './social-api';
import { toFacebookFailure, toInstagramFailure } from './social-routes';

const CONNECTION: SocialConnection = {
  network: 'FACEBOOK',
  status: 'CONNECTED',
  displayName: 'Sfax Auto Occasion',
  pictureUrl: null,
  connectedAt: '2026-10-12T09:30:00Z',
  autoShare: true,
};

describe('SocialApi', () => {
  let http: HttpTestingController;
  let api: SocialApi;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    api = TestBed.inject(SocialApi);
    const login = TestBed.inject(AuthService).login('amira@example.tn', 'secret-password');
    http.expectOne('/api/users/login').flush(sessionResponse('token-1'));
    await login;
  });

  afterEach(() => http.verify());

  it('reads the connections with the access token', async () => {
    const connections = api.connections();
    const request = http.expectOne('/api/social/connections');
    expect(request.request.headers.get('Authorization')).toBe('Bearer token-1');
    request.flush([CONNECTION]);

    expect(await connections).toEqual([CONNECTION]);
  });

  it('starts a connection in a language and returns the URL of the dialog', async () => {
    const url = api.connectFacebook('ar');
    const request = http.expectOne('/api/social/facebook/connect');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ lang: 'ar' });
    request.flush({ authorizeUrl: 'https://www.facebook.com/dialog' });

    expect(await url).toBe('https://www.facebook.com/dialog');
  });

  it('forwards the redirect of Facebook and tells a connection from a choice', async () => {
    const redirect = { code: 'the-code', state: 'the-state', error: null };
    const connected = api.facebookCallback(redirect);
    const first = http.expectOne('/api/social/facebook/callback');
    expect(first.request.body).toEqual(redirect);
    first.flush({ lang: 'ar', connection: CONNECTION });
    expect(await connected).toEqual({ kind: 'connected', lang: 'ar', connection: CONNECTION });

    const choose = api.facebookCallback(redirect);
    const pages = [{ pageId: '1', name: 'A', category: null, pictureUrl: null }];
    http.expectOne('/api/social/facebook/callback').flush({ lang: 'fr', pendingSelection: pages });
    expect(await choose).toEqual({ kind: 'choose', lang: 'fr', pages });
  });

  it('turns a problem into a failure with its language, never a rejection', async () => {
    const cancelled = api.facebookCallback({ code: null, state: 's', error: 'access_denied' });
    http
      .expectOne('/api/social/facebook/callback')
      .flush(
        { type: 'urn:soukly:problem:oauth-cancelled', status: 400, lang: 'en' },
        { status: 400, statusText: 'Bad Request' },
      );
    expect(await cancelled).toEqual({ kind: 'failed', lang: 'en', failure: 'oauth-cancelled' });

    const offline = api.facebookCallback({ code: 'c', state: 's', error: null });
    http.expectOne('/api/social/facebook/callback').error(new ProgressEvent('error'));
    expect(await offline).toEqual({ kind: 'failed', lang: null, failure: 'unknown' });
  });

  it('answers null when no choice of Page is in progress', async () => {
    const selection = api.pageSelection();
    http
      .expectOne('/api/social/facebook/page-selection')
      .flush(
        { type: 'urn:soukly:problem:page-selection-not-found' },
        { status: 404, statusText: 'Not Found' },
      );

    expect(await selection).toBeNull();
  });

  it('chooses a Page and disconnects', async () => {
    const chosen = api.chooseFacebookPage('1002');
    const choose = http.expectOne('/api/social/facebook/page');
    expect(choose.request.body).toEqual({ pageId: '1002' });
    choose.flush(CONNECTION);
    expect(await chosen).toEqual(CONNECTION);

    const disconnected = api.disconnectFacebook();
    const request = http.expectOne('/api/social/facebook');
    expect(request.request.method).toBe('DELETE');
    request.flush(null, { status: 204, statusText: 'No Content' });
    await disconnected;
  });

  it('keeps only the known failures', () => {
    expect(toFacebookFailure('facebook-no-page')).toBe('facebook-no-page');
    expect(toFacebookFailure('<script>')).toBe('unknown');
    expect(toFacebookFailure(null)).toBe('unknown');
  });

  it('connects Instagram, forwards its redirect and disconnects it', async () => {
    const url = api.connectInstagram('fr');
    const start = http.expectOne('/api/social/instagram/connect');
    expect(start.request.body).toEqual({ lang: 'fr' });
    start.flush({ authorizeUrl: 'https://www.instagram.com/oauth/authorize' });
    expect(await url).toBe('https://www.instagram.com/oauth/authorize');

    const redirect = { code: 'the-code', state: 'the-state', error: null };
    const instagram: SocialConnection = {
      ...CONNECTION,
      network: 'INSTAGRAM',
      displayName: '@sfax.auto',
    };
    const connected = api.instagramCallback(redirect);
    const callback = http.expectOne('/api/social/instagram/callback');
    expect(callback.request.body).toEqual(redirect);
    callback.flush({ lang: 'ar', connection: instagram });
    expect(await connected).toEqual({ kind: 'connected', lang: 'ar', connection: instagram });

    const disconnected = api.disconnectInstagram();
    const request = http.expectOne('/api/social/instagram');
    expect(request.request.method).toBe('DELETE');
    request.flush(null, { status: 204, statusText: 'No Content' });
    await disconnected;
  });

  it('turns an Instagram problem into a failure with its language', async () => {
    const personal = api.instagramCallback({ code: 'c', state: 's', error: null });
    http
      .expectOne('/api/social/instagram/callback')
      .flush(
        { type: 'urn:soukly:problem:instagram-not-professional', status: 400, lang: 'fr' },
        { status: 400, statusText: 'Bad Request' },
      );
    expect(await personal).toEqual({
      kind: 'failed',
      lang: 'fr',
      failure: 'instagram-not-professional',
    });
    expect(toInstagramFailure('facebook-no-page')).toBe('unknown');
  });
});
