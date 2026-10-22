import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';
import { sessionResponse } from './testing';

describe('AuthService', () => {
  let auth: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('opens a session on login, with the user and the first-login flag', async () => {
    const login = auth.login('amira@example.tn', 'Jasmin-de-Carthage-2026');
    const request = http.expectOne('/api/users/login');
    expect(request.request.body).toEqual({
      email: 'amira@example.tn',
      password: 'Jasmin-de-Carthage-2026',
    });
    expect(request.request.withCredentials).toBe(true);
    request.flush(sessionResponse('token-1', true));

    expect(await login).toEqual({ firstLogin: true });
    expect(auth.isAuthenticated()).toBe(true);
    expect(auth.user()?.displayName).toBe('Amira');
    expect(auth.accessToken()).toBe('token-1');
  });

  it('keeps the access token in memory only', async () => {
    const setItem = vi.spyOn(Storage.prototype, 'setItem');
    const login = auth.login('amira@example.tn', 'secret-password');
    http.expectOne('/api/users/login').flush(sessionResponse('token-1'));
    await login;

    expect(setItem).not.toHaveBeenCalled();
    expect(JSON.stringify({ ...localStorage, ...sessionStorage })).not.toContain('token-1');
    expect(document.cookie).not.toContain('token-1');
    setItem.mockRestore();
  });

  it('rejects with the API error and stays signed out when the login fails', async () => {
    const login = auth.login('amira@example.tn', 'wrong');
    http
      .expectOne('/api/users/login')
      .flush(
        { type: 'urn:soukly:problem:invalid-credentials' },
        { status: 401, statusText: 'Unauthorized' },
      );

    await expect(login).rejects.toMatchObject({ status: 401 });
    expect(auth.isAuthenticated()).toBe(false);
  });

  it('opens a session when a new password is chosen with a reset link', async () => {
    const reset = auth.resetPassword('reset-token', 'Sidi-Bou-Said-bleu-42');
    const request = http.expectOne('/api/users/password/reset');
    expect(request.request.body).toEqual({
      token: 'reset-token',
      newPassword: 'Sidi-Bou-Said-bleu-42',
    });
    expect(request.request.withCredentials).toBe(true);
    request.flush(sessionResponse('token-2'));

    await reset;
    expect(auth.isAuthenticated()).toBe(true);
    expect(auth.accessToken()).toBe('token-2');
  });

  it('stays signed out when the reset link is refused', async () => {
    const reset = auth.resetPassword('used-token', 'Sidi-Bou-Said-bleu-42');
    http
      .expectOne('/api/users/password/reset')
      .flush(
        { type: 'urn:soukly:problem:reset-token-invalid' },
        { status: 400, statusText: 'Bad Request' },
      );

    await expect(reset).rejects.toMatchObject({ status: 400 });
    expect(auth.isAuthenticated()).toBe(false);
  });

  it('shares one refresh between concurrent callers', async () => {
    const first = auth.refresh();
    const second = auth.refresh();
    const requests = http.match('/api/users/refresh');
    expect(requests).toHaveLength(1);
    requests[0].flush(sessionResponse('token-2'));

    expect(await Promise.all([first, second])).toEqual([true, true]);
    expect(auth.accessToken()).toBe('token-2');

    // Once done, the next refresh is a new call.
    const third = auth.refresh();
    http.expectOne('/api/users/refresh').flush(sessionResponse('token-3'));
    expect(await third).toBe(true);
  });

  it('clears the session when the refresh fails', async () => {
    const login = auth.login('amira@example.tn', 'secret-password');
    http.expectOne('/api/users/login').flush(sessionResponse('token-1'));
    await login;

    const refresh = auth.refresh();
    http
      .expectOne('/api/users/refresh')
      .flush(
        { type: 'urn:soukly:problem:session-expired' },
        { status: 401, statusText: 'Unauthorized' },
      );

    expect(await refresh).toBe(false);
    expect(auth.isAuthenticated()).toBe(false);
  });

  it('restores the session once, and tells when it is known', async () => {
    const restore = auth.restore();
    expect(auth.restore()).toBe(restore);
    http.expectOne('/api/users/refresh').flush(sessionResponse('token-1'));
    await auth.whenRestored();

    expect(auth.user()?.displayName).toBe('Amira');
  });

  it('logs out on the server and in the page, even when the call fails', async () => {
    const login = auth.login('amira@example.tn', 'secret-password');
    http.expectOne('/api/users/login').flush(sessionResponse('token-1'));
    await login;

    const logout = auth.logout();
    const request = http.expectOne('/api/users/logout');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush(null, { status: 503, statusText: 'Unavailable' });
    await logout;

    expect(auth.isAuthenticated()).toBe(false);
  });
});
