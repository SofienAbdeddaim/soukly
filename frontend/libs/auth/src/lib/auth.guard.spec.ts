import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { authGuard, guestGuard } from './auth.guard';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';
import { sessionResponse } from './testing';

@Component({ template: '' })
class Page {}

describe('authGuard and guestGuard', () => {
  let router: Router;
  let http: HttpTestingController;
  let auth: AuthService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: ':lang', component: Page },
          { path: ':lang/publish/new', component: Page, canActivate: [authGuard] },
          { path: ':lang/account/login', component: Page, canActivate: [guestGuard] },
        ]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    router = TestBed.inject(Router);
    http = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
  });

  afterEach(() => http.verify());

  async function signIn() {
    const login = auth.login('amira@example.tn', 'secret-password');
    http.expectOne('/api/users/login').flush(sessionResponse('token-1'));
    await login;
  }

  it('sends a visitor to the login page of the same language, with the page to come back to', async () => {
    await router.navigateByUrl('/fr/publish/new');
    expect(router.url).toBe('/fr/account/login?returnUrl=%2Ffr%2Fpublish%2Fnew');

    await router.navigateByUrl('/ar/publish/new');
    expect(router.url).toBe('/ar/account/login?returnUrl=%2Far%2Fpublish%2Fnew');
  });

  it('lets a member in', async () => {
    await signIn();
    await router.navigateByUrl('/fr/publish/new');
    expect(router.url).toBe('/fr/publish/new');
  });

  it('waits for the session restored at start-up before deciding', async () => {
    void auth.restore();
    const navigation = router.navigateByUrl('/fr/publish/new');
    http.expectOne('/api/users/refresh').flush(sessionResponse('token-1'));
    await navigation;
    expect(router.url).toBe('/fr/publish/new');
  });

  it('sends a member away from the login page, to the return page or home', async () => {
    await signIn();
    await router.navigateByUrl('/fr/account/login?returnUrl=%2Ffr%2Fpublish%2Fnew');
    expect(router.url).toBe('/fr/publish/new');

    await router.navigateByUrl('/ar/account/login?returnUrl=https%3A%2F%2Fevil.example');
    expect(router.url).toBe('/ar');
  });
});
