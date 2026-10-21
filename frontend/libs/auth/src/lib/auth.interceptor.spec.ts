import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { authGuard } from './auth.guard';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';
import { sessionResponse } from './testing';

@Component({ template: '' })
class Page {}

const unauthorized = { status: 401, statusText: 'Unauthorized' };

/** Lets the promises of the refresh settle. */
const settle = () => new Promise((resolve) => setTimeout(resolve));

describe('authInterceptor', () => {
  let auth: AuthService;
  let http: HttpTestingController;
  let client: HttpClient;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'fr', component: Page },
          { path: 'fr/publish/new', component: Page, canActivate: [authGuard] },
          { path: 'fr/account/login', component: Page },
        ]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
    client = TestBed.inject(HttpClient);
  });

  afterEach(() => http.verify());

  async function signIn(token = 'token-1') {
    const login = auth.login('amira@example.tn', 'secret-password');
    http.expectOne('/api/users/login').flush(sessionResponse(token));
    await login;
  }

  it('sends the bearer token and the cookie to the API', async () => {
    await signIn();
    const call = firstValueFrom(client.get('/api/listings/mine'));
    const request = http.expectOne('/api/listings/mine');

    expect(request.request.headers.get('Authorization')).toBe('Bearer token-1');
    expect(request.request.withCredentials).toBe(true);
    request.flush([]);
    await call;
  });

  it('never sends the token to another origin, nor to the session endpoints', async () => {
    await signIn();
    void firstValueFrom(client.get('https://other.example/api/x')).catch(() => undefined);
    const external = http.expectOne('https://other.example/api/x');
    expect(external.request.headers.has('Authorization')).toBe(false);
    expect(external.request.withCredentials).toBe(false);
    external.flush({});

    void auth.refresh();
    const refresh = http.expectOne('/api/users/refresh');
    expect(refresh.request.headers.has('Authorization')).toBe(false);
    expect(refresh.request.withCredentials).toBe(true);
    refresh.flush(sessionResponse('token-2'));
  });

  it('renews an expired token once and replays the call, unseen by the caller', async () => {
    await signIn('expired');
    const call = firstValueFrom(client.post('/api/listings', { title: 'Vélo' }));

    http.expectOne('/api/listings').flush(null, unauthorized);
    http.expectOne('/api/users/refresh').flush(sessionResponse('token-2'));
    await settle();
    const replay = http.expectOne('/api/listings');
    expect(replay.request.headers.get('Authorization')).toBe('Bearer token-2');
    expect(replay.request.body).toEqual({ title: 'Vélo' });
    replay.flush({ id: 'l-1' });

    expect(await call).toEqual({ id: 'l-1' });
  });

  it('makes concurrent failing calls wait for one single refresh', async () => {
    await signIn('expired');
    const calls = Promise.all([
      firstValueFrom(client.get('/api/a')),
      firstValueFrom(client.get('/api/b')),
      firstValueFrom(client.get('/api/c')),
    ]);

    for (const url of ['/api/a', '/api/b', '/api/c']) {
      http.expectOne(url).flush(null, unauthorized);
    }
    const refreshes = http.match('/api/users/refresh');
    expect(refreshes).toHaveLength(1);
    refreshes[0].flush(sessionResponse('token-2'));
    await settle();
    for (const url of ['/api/a', '/api/b', '/api/c']) {
      const replay = http.expectOne(url);
      expect(replay.request.headers.get('Authorization')).toBe('Bearer token-2');
      replay.flush(url);
    }

    expect(await calls).toEqual(['/api/a', '/api/b', '/api/c']);
  });

  it('does not loop when the replayed call is refused again', async () => {
    await signIn('expired');
    const call = firstValueFrom(client.get('/api/a'));

    http.expectOne('/api/a').flush(null, unauthorized);
    http.expectOne('/api/users/refresh').flush(sessionResponse('token-2'));
    await settle();
    http.expectOne('/api/a').flush(null, unauthorized);

    await expect(call).rejects.toMatchObject({ status: 401 });
    http.expectNone('/api/users/refresh');
  });

  it('never refreshes on a 401 of the login itself', async () => {
    const login = auth.login('amira@example.tn', 'wrong');
    http.expectOne('/api/users/login').flush(null, unauthorized);

    await expect(login).rejects.toMatchObject({ status: 401 });
    http.expectNone('/api/users/refresh');
  });

  it('clears the session and sends to the login page when the refresh fails on a protected page', async () => {
    const router = TestBed.inject(Router);
    await signIn('expired');
    await router.navigateByUrl('/fr/publish/new');
    const call = firstValueFrom(client.get('/api/a'));

    http.expectOne('/api/a').flush(null, unauthorized);
    http.expectOne('/api/users/refresh').flush(null, unauthorized);

    await expect(call).rejects.toMatchObject({ status: 401 });
    expect(auth.isAuthenticated()).toBe(false);
    await vi.waitFor(() =>
      expect(router.url).toBe('/fr/account/login?returnUrl=%2Ffr%2Fpublish%2Fnew'),
    );
  });

  it('stays on a public page when the refresh fails there', async () => {
    const router = TestBed.inject(Router);
    await signIn('expired');
    await router.navigateByUrl('/fr');
    const call = firstValueFrom(client.get('/api/a'));

    http.expectOne('/api/a').flush(null, unauthorized);
    http.expectOne('/api/users/refresh').flush(null, unauthorized);

    await expect(call).rejects.toMatchObject({ status: 401 });
    expect(router.url).toBe('/fr');
  });
});
