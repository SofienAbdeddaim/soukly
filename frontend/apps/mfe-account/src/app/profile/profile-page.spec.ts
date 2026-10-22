import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { AuthService, sessionResponse } from '@soukly/auth';
import { LocaleService } from '@soukly/i18n';
import { ToastService } from '@soukly/ui';
import { accountI18nTesting, accountTestingProviders, PROFILE } from '../testing/account-testing';
import { Profile } from './profile-api';
import { ProfilePage } from './profile-page';

const settle = () => new Promise((resolve) => setTimeout(resolve));

describe('ProfilePage', () => {
  async function setup(lang: 'fr' | 'ar' = 'fr', profile: Profile = PROFILE) {
    TestBed.configureTestingModule({
      imports: [accountI18nTesting(lang)],
      providers: accountTestingProviders([
        { path: `${lang}/account/profile`, component: ProfilePage },
      ]),
    });
    TestBed.inject(LocaleService).use(lang);
    const http = TestBed.inject(HttpTestingController);
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(`/${lang}/account/profile`);
    http.expectOne('/api/users/me').flush(profile);
    await settle();
    await harness.fixture.whenStable();
    return { harness, http, element: () => harness.routeNativeElement as HTMLElement };
  }

  async function type(
    harness: RouterTestingHarness,
    element: HTMLElement,
    id: string,
    value: string,
  ) {
    const input = element.querySelector(`#${id}`) as HTMLInputElement;
    input.value = value;
    input.dispatchEvent(new Event('input'));
    await harness.fixture.whenStable();
  }

  it('shows the initial, the name, the date of registration and the details', async () => {
    const { element } = await setup();

    expect(element().querySelector('h1')?.textContent).toContain('Mon profil');
    expect(element().querySelector('[data-testid=profile-name]')?.textContent).toContain(
      'Amira Ben Salah',
    );
    expect(element().textContent).toContain('Membre depuis mars 2026');
    expect((element().querySelector('#profile-phone') as HTMLInputElement).value).toBe(
      '20 123 456',
    );
    expect((element().querySelector('#profile-language') as HTMLSelectElement).value).toBe('fr');
    expect(element().querySelector('[data-testid=profile-email]')?.textContent).toContain(
      'amira@example.tn',
    );
    expect(element().querySelector('a[href="/fr/account/profile/delete"]')).not.toBeNull();
    expect(element().querySelector('a[href="/fr/account/listings"]')).not.toBeNull();
  });

  it('saves the details with a merge patch, updates the header name and shows a toast', async () => {
    const { harness, http, element } = await setup();
    const auth = TestBed.inject(AuthService);
    const login = auth.login('amira@example.tn', 'secret-password');
    http.expectOne('/api/users/login').flush(sessionResponse('token-1'));
    await login;
    const show = vi.spyOn(TestBed.inject(ToastService), 'show');
    await type(harness, element(), 'profile-phone', '98 765 432');
    (
      element().querySelector(
        '[data-testid=profile-info-form] button[type=submit]',
      ) as HTMLButtonElement
    ).click();
    await harness.fixture.whenStable();

    const request = http.expectOne('/api/users/me');
    expect(request.request.method).toBe('PATCH');
    expect(request.request.headers.get('Content-Type')).toBe('application/merge-patch+json');
    expect(request.request.body).toEqual({
      displayName: 'Amira Ben Salah',
      phone: '+21698765432',
      preferredLanguage: 'fr',
    });
    request.flush({ ...PROFILE, phone: '+21698765432', displayName: 'Amira B.' });
    await settle();
    await harness.fixture.whenStable();

    expect(auth.user()?.displayName).toBe('Amira B.');
    expect(element().querySelector('[data-testid=profile-name]')?.textContent).toContain(
      'Amira B.',
    );
    expect(show).toHaveBeenCalledWith(
      expect.objectContaining({ type: 'success', message: 'Profil mis à jour' }),
    );
  });

  it('checks the phone before sending and shows the codes of the API under their field', async () => {
    const { harness, http, element } = await setup();
    await type(harness, element(), 'profile-phone', '1234');
    const save = element().querySelector(
      '[data-testid=profile-info-form] button[type=submit]',
    ) as HTMLButtonElement;
    save.click();
    await harness.fixture.whenStable();

    http.expectNone('/api/users/me');
    expect(element().textContent).toContain('Numéro tunisien à 8 chiffres attendu.');

    await type(harness, element(), 'profile-phone', '20 123 456');
    save.click();
    await harness.fixture.whenStable();
    http.expectOne('/api/users/me').flush(
      {
        type: 'urn:soukly:problem:validation-failed',
        errors: [{ field: 'displayName', code: 'displayName.length' }],
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await settle();
    await harness.fixture.whenStable();

    expect(element().textContent).toContain('Le nom doit compter entre 2 et 50 caractères.');
    expect(element().querySelector('#profile-display-name')?.getAttribute('aria-invalid')).toBe(
      'true',
    );
  });

  it('shows the address waiting for confirmation and cancels it', async () => {
    const { harness, http, element } = await setup('fr', {
      ...PROFILE,
      pendingEmail: 'amira.bs@example.tn',
    });

    expect(element().querySelector('[data-testid=profile-pending-email]')?.textContent).toContain(
      'amira.bs@example.tn',
    );
    (
      element().querySelector('[data-testid=profile-cancel-email-change]') as HTMLButtonElement
    ).click();
    const request = http.expectOne('/api/users/me/email-change');
    expect(request.request.method).toBe('DELETE');
    request.flush(null, { status: 204, statusText: 'No Content' });
    await settle();
    await harness.fixture.whenStable();

    expect(element().querySelector('[data-testid=profile-pending-email]')).toBeNull();
  });

  it('keeps the phone left to right on the Arabic page', async () => {
    const { element } = await setup('ar');

    expect(element().querySelector('h1')?.textContent).toContain('ملفي الشخصي');
    expect(element().querySelector('#profile-phone')?.closest('[dir=ltr]')).not.toBeNull();
    expect(element().querySelector('[data-testid=profile-email]')?.getAttribute('dir')).toBe('ltr');
  });

  it('offers to try again when the profile cannot be loaded', async () => {
    TestBed.configureTestingModule({
      imports: [accountI18nTesting()],
      providers: accountTestingProviders([{ path: 'fr/account/profile', component: ProfilePage }]),
    });
    const http = TestBed.inject(HttpTestingController);
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/fr/account/profile');
    http.expectOne('/api/users/me').flush(null, { status: 503, statusText: 'Unavailable' });
    await settle();
    await harness.fixture.whenStable();
    const element = harness.routeNativeElement as HTMLElement;

    expect(element.querySelector('[data-testid=profile-load-error]')).not.toBeNull();
    (element.querySelector('[data-testid=profile-page] button') as HTMLButtonElement).click();
    http.expectOne('/api/users/me').flush(PROFILE);
    await settle();
    await harness.fixture.whenStable();
    expect(element.querySelector('[data-testid=profile-name]')).not.toBeNull();
  });

  it('logs out and goes home', async () => {
    const { harness, http, element } = await setup();
    (element().querySelector('[data-testid=profile-logout]') as HTMLButtonElement).click();
    http.expectOne('/api/users/logout').flush(null, { status: 204, statusText: 'No Content' });
    await settle();
    await harness.fixture.whenStable();

    expect(TestBed.inject(Router).url).toBe('/fr');
  });
});
