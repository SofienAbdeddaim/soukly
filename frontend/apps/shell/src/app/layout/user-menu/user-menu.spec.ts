import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { authInterceptor, AuthService, sessionResponse } from '@soukly/auth';
import { LocaleService } from '@soukly/i18n';
import { shellI18nTesting } from '../../testing/shell-i18n-testing';
import { Header } from '../header/header';

@Component({ template: '' })
class Page {}

describe('Header of a member', () => {
  async function setup(lang: 'fr' | 'ar' = 'fr') {
    TestBed.configureTestingModule({
      imports: [Header, shellI18nTesting(lang)],
      providers: [
        provideRouter([{ path: '**', component: Page }]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    TestBed.inject(LocaleService).use(lang);
    const http = TestBed.inject(HttpTestingController);
    const login = TestBed.inject(AuthService).login('amira@example.tn', 'secret-password');
    http.expectOne('/api/users/login').flush(sessionResponse('token-1'));
    await login;
    const fixture = TestBed.createComponent(Header);
    await fixture.whenStable();
    return { fixture, http, element: fixture.nativeElement as HTMLElement };
  }

  afterEach(() => TestBed.inject(LocaleService).use('fr'));

  it('replaces "Se connecter" with the name and a menu', async () => {
    const { element, fixture } = await setup();

    expect(element.querySelector('a[href="/fr/account/login"]')).toBeNull();
    const toggle = element.querySelector('[data-testid=user-menu]') as HTMLButtonElement;
    expect(toggle.textContent).toContain('Amira');
    expect(toggle.getAttribute('aria-expanded')).toBe('false');

    toggle.click();
    await fixture.whenStable();
    expect(toggle.getAttribute('aria-expanded')).toBe('true');
    expect(element.querySelector('a[href="/fr/account"]')?.textContent).toContain('Mon compte');
    expect(element.querySelector('#user-menu button')?.textContent).toContain('Se déconnecter');
  });

  it('logs out on the server, forgets the session and goes home', async () => {
    const { element, fixture, http } = await setup('ar');
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl');
    (element.querySelector('[data-testid=user-menu]') as HTMLButtonElement).click();
    await fixture.whenStable();
    (element.querySelector('#user-menu button') as HTMLButtonElement).click();

    http.expectOne('/api/users/logout').flush(null, { status: 204, statusText: 'No Content' });
    await new Promise((resolve) => setTimeout(resolve));
    await fixture.whenStable();

    expect(TestBed.inject(AuthService).isAuthenticated()).toBe(false);
    expect(navigate).toHaveBeenCalledWith('/ar');
    expect(element.querySelector('[data-testid=user-menu]')).toBeNull();
    expect(element.querySelector('a[href="/ar/account/login"]')?.textContent).toContain(
      'تسجيل الدخول',
    );
  });
});
