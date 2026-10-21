import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { AuthService, sessionResponse } from '@soukly/auth';
import { LocaleService } from '@soukly/i18n';
import { accountI18nTesting, accountTestingProviders } from '../testing/account-testing';
import { LoginPage } from './login-page';

const settle = () => new Promise((resolve) => setTimeout(resolve));

describe('LoginPage', () => {
  async function setup(query: Record<string, string> = {}) {
    TestBed.configureTestingModule({
      imports: [LoginPage, accountI18nTesting('fr')],
      providers: [
        ...accountTestingProviders(),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap(query) } },
        },
      ],
    });
    TestBed.inject(LocaleService).use('fr');
    const fixture = TestBed.createComponent(LoginPage);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    const type = (id: string, value: string) => {
      const input = element.querySelector(`#${id}`) as HTMLInputElement;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    };
    const logIn = async (password = 'Jasmin-de-Carthage-2026') => {
      type('login-email', 'amira@example.tn');
      type('login-password', password);
      (element.querySelector('button[type=submit]') as HTMLButtonElement).click();
      await fixture.whenStable();
    };
    const answer = async (body: object, status = 200, headers?: Record<string, string>) => {
      TestBed.inject(HttpTestingController)
        .expectOne('/api/users/login')
        .flush(body, { status, statusText: String(status), headers });
      await settle();
      await fixture.whenStable();
    };
    return {
      fixture,
      element,
      logIn,
      answer,
      navigateByUrl: vi.spyOn(TestBed.inject(Router), 'navigateByUrl'),
      navigate: vi.spyOn(TestBed.inject(Router), 'navigate'),
    };
  }

  it('goes to the requested page after the login', async () => {
    const { logIn, answer, navigateByUrl } = await setup({ returnUrl: '/fr/publish/new' });
    await logIn();
    await answer(sessionResponse('token-1'));

    expect(navigateByUrl).toHaveBeenCalledWith('/fr/publish/new');
    expect(TestBed.inject(AuthService).isAuthenticated()).toBe(true);
  });

  it('goes home when the return page is not an internal path', async () => {
    const { logIn, answer, navigateByUrl } = await setup({ returnUrl: '//evil.example/fr' });
    await logIn();
    await answer(sessionResponse('token-1'));

    expect(navigateByUrl).toHaveBeenCalledWith('/fr');
  });

  it('shows the welcome screen first on the very first login, keeping the return page', async () => {
    const { logIn, answer, navigate } = await setup({ returnUrl: '/fr/publish/new' });
    await logIn();
    await answer(sessionResponse('token-1', true));

    expect(navigate).toHaveBeenCalledWith(['/', 'fr', 'account', 'welcome'], {
      queryParams: { returnUrl: '/fr/publish/new' },
    });
  });

  it('shows one general message for wrong credentials, focused, with no field marked', async () => {
    const { element, logIn, answer } = await setup();
    await logIn('wrong-password');
    await answer({ type: 'urn:soukly:problem:invalid-credentials', detail: 'API text' }, 401);

    const alert = element.querySelector('ui-alert') as HTMLElement;
    expect(alert.getAttribute('role')).toBe('alert');
    expect(alert.textContent?.trim()).toBe('Email ou mot de passe incorrect.');
    expect(document.activeElement).toBe(alert);
    expect(element.querySelectorAll('[aria-invalid="true"]')).toHaveLength(0);
    expect(element.textContent).not.toContain('API text');
    expect((element.querySelector('#login-password') as HTMLInputElement).value).toBe('');
  });

  it('offers to resend the verification email for an unverified account', async () => {
    const { element, logIn, answer } = await setup();
    await logIn();
    await answer({ type: 'urn:soukly:problem:email-not-verified' }, 403);

    expect(element.querySelector('ui-alert')?.textContent).toContain("n'est pas encore vérifiée");
    expect(element.querySelector('[data-testid=resend-email]')?.textContent).toContain(
      "Renvoyer l'email de vérification",
    );
  });

  it.each([
    ['30', 'Trop de tentatives. Réessayez dans 30 s.'],
    ['150', 'Trop de tentatives. Réessayez dans 3 min.'],
  ])('gives the remaining delay of a 429 (Retry-After %s)', async (retryAfter, message) => {
    const { element, logIn, answer } = await setup();
    await logIn();
    await answer({ type: 'urn:soukly:problem:too-many-requests' }, 429, {
      'Retry-After': retryAfter,
    });

    expect(element.querySelector('ui-alert')?.textContent?.trim()).toBe(message);
  });
});
