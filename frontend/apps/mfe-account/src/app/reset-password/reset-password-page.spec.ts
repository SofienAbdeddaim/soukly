import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Meta } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { AuthService, sessionResponse } from '@soukly/auth';
import { LocaleService } from '@soukly/i18n';
import { ToastService } from '@soukly/ui';
import { accountI18nTesting, accountTestingProviders } from '../testing/account-testing';
import { ResetPasswordPage } from './reset-password-page';

const settle = () => new Promise((resolve) => setTimeout(resolve));
const PASSWORD = 'Sidi-Bou-Said-bleu-42';

describe('ResetPasswordPage', () => {
  async function setup(url = '/fr/account/reset-password?token=abc', lang: 'fr' | 'ar' = 'fr') {
    TestBed.configureTestingModule({
      imports: [accountI18nTesting(lang)],
      providers: accountTestingProviders([
        { path: `${lang}/account/reset-password`, component: ResetPasswordPage },
      ]),
    });
    TestBed.inject(LocaleService).use(lang);
    const router = TestBed.inject(Router);
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url);
    await harness.fixture.whenStable();
    return {
      harness,
      router,
      http: TestBed.inject(HttpTestingController),
      element: () => harness.routeNativeElement as HTMLElement,
    };
  }

  async function submit(harness: RouterTestingHarness, element: HTMLElement, password: string) {
    const input = element.querySelector('#reset-password') as HTMLInputElement;
    input.value = password;
    input.dispatchEvent(new Event('input'));
    (element.querySelector('button[type=submit]') as HTMLButtonElement).click();
    await harness.fixture.whenStable();
  }

  it('removes the token from the address bar and sends no Referer', async () => {
    const { router } = await setup();

    expect(router.url).toBe('/fr/account/reset-password');
    expect(TestBed.inject(Meta).getTag('name="referrer"')?.content).toBe('no-referrer');
  });

  it('saves the new password, opens the session and goes home with a toast', async () => {
    const { harness, http, element, router } = await setup();
    const show = vi.spyOn(TestBed.inject(ToastService), 'show');
    await submit(harness, element(), PASSWORD);

    const request = http.expectOne('/api/users/password/reset');
    expect(request.request.body).toEqual({ token: 'abc', newPassword: PASSWORD });
    request.flush(sessionResponse('token-1'));
    await settle();
    await harness.fixture.whenStable();

    expect(TestBed.inject(AuthService).isAuthenticated()).toBe(true);
    expect(router.url).toBe('/fr');
    expect(show).toHaveBeenCalledWith(
      expect.objectContaining({
        type: 'success',
        message: 'Mot de passe modifié. Vous êtes connecté.',
      }),
    );
  });

  it('shows the password code of the API under the field and keeps the form', async () => {
    const { harness, http, element } = await setup();
    await submit(harness, element(), 'password123');
    http.expectOne('/api/users/password/reset').flush(
      {
        type: 'urn:soukly:problem:validation-failed',
        errors: [{ field: 'newPassword', code: 'password.too-common' }],
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await settle();
    await harness.fixture.whenStable();

    expect(element().textContent).toContain('Ce mot de passe est trop courant.');
    expect(element().querySelector('#reset-password')?.getAttribute('aria-invalid')).toBe('true');
  });

  it('shows "this link is no longer valid" for a refused token', async () => {
    const { harness, http, element } = await setup();
    await submit(harness, element(), PASSWORD);
    http
      .expectOne('/api/users/password/reset')
      .flush(
        { type: 'urn:soukly:problem:reset-token-invalid' },
        { status: 400, statusText: 'Bad Request' },
      );
    await settle();
    await harness.fixture.whenStable();

    expect(element().querySelector('[data-testid=reset-invalid]')?.textContent).toContain(
      "Ce lien n'est plus valable",
    );
    expect(element().querySelector('a[href="/fr/account/forgot-password"]')?.textContent).toContain(
      'Demander un nouveau lien',
    );
  });

  it('shows the invalid link screen at once without a token', async () => {
    const { element } = await setup('/fr/account/reset-password');

    expect(element().querySelector('[data-testid=reset-invalid]')).not.toBeNull();
  });

  it('lets the member try again after a network error', async () => {
    const { harness, http, element } = await setup();
    await submit(harness, element(), PASSWORD);
    http
      .expectOne('/api/users/password/reset')
      .flush(null, { status: 503, statusText: 'Service Unavailable' });
    await settle();
    await harness.fixture.whenStable();

    expect(element().querySelector('[data-testid=reset-error]')?.textContent).toContain(
      'Une erreur est survenue',
    );
    (element().querySelector('button[type=submit]') as HTMLButtonElement).click();
    await harness.fixture.whenStable();
    http.expectOne('/api/users/password/reset');
  });

  it('has a "Show" button with aria-pressed, in Arabic too', async () => {
    const { harness, element } = await setup('/ar/account/reset-password?token=abc', 'ar');
    const toggle = element().querySelector('button[aria-pressed]') as HTMLButtonElement;
    expect(toggle.getAttribute('aria-pressed')).toBe('false');
    expect(element().querySelector('h1')?.textContent).toContain('اختر كلمة مرور جديدة');

    toggle.click();
    await harness.fixture.whenStable();

    expect(toggle.getAttribute('aria-pressed')).toBe('true');
    expect((element().querySelector('#reset-password') as HTMLInputElement).type).toBe('text');
  });
});
