import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { LocaleService } from '@soukly/i18n';
import { accountI18nTesting, accountTestingProviders } from '../testing/account-testing';
import { VerifyEmailPage } from './verify-email-page';

const settle = () => new Promise((resolve) => setTimeout(resolve));

describe('VerifyEmailPage', () => {
  async function setup(url = '/fr/account/verify-email?token=abc') {
    TestBed.configureTestingModule({
      imports: [accountI18nTesting('fr')],
      providers: accountTestingProviders([
        { path: 'fr/account/verify-email', component: VerifyEmailPage },
      ]),
    });
    TestBed.inject(LocaleService).use('fr');
    const router = TestBed.inject(Router);
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url);
    return {
      harness,
      router,
      http: TestBed.inject(HttpTestingController),
      element: () => harness.routeNativeElement as HTMLElement,
    };
  }

  it('verifies the token at once, shows a loading state, then the activated account', async () => {
    const { harness, http, element, router } = await setup();
    expect(element().querySelector('section')?.getAttribute('aria-busy')).toBe('true');

    const request = http.expectOne('/api/users/verify-email');
    expect(request.request.body).toEqual({ token: 'abc' });
    request.flush(null, { status: 204, statusText: 'No Content' });
    await settle();
    await harness.fixture.whenStable();

    expect(element().querySelector('h1')?.textContent).toContain('Votre compte est activé');
    expect(element().querySelector('a[href="/fr/account/login"]')?.textContent).toContain(
      'Se connecter',
    );
    // The token does not stay in the address bar.
    expect(router.url).toBe('/fr/account/verify-email');
  });

  it('offers a new link when the token is no longer valid', async () => {
    const { harness, http, element } = await setup();
    http
      .expectOne('/api/users/verify-email')
      .flush(
        { type: 'urn:soukly:problem:verification-token-invalid' },
        { status: 400, statusText: 'Bad Request' },
      );
    await settle();
    await harness.fixture.whenStable();

    expect(element().querySelector('h1')?.textContent).toContain("Ce lien n'est plus valable");
    const email = element().querySelector('#verify-resend-email') as HTMLInputElement;
    email.value = 'amira@example.tn';
    email.dispatchEvent(new Event('input'));
    (element().querySelector('button[type=submit]') as HTMLButtonElement).click();
    await harness.fixture.whenStable();

    const resend = http.expectOne('/api/users/verify-email/resend');
    expect(resend.request.body).toEqual({ email: 'amira@example.tn' });
    resend.flush(null, { status: 202, statusText: 'Accepted' });
    await settle();
    await harness.fixture.whenStable();

    expect(element().querySelector('[role=status]')?.textContent).toContain('nouveau lien');
    const button = element().querySelector('button[type=submit]') as HTMLButtonElement;
    expect(button.disabled).toBe(true);
    expect(button.textContent).toMatch(/\(60 s\)/);
  });

  it('lets the visitor try again after a network error', async () => {
    const { harness, http, element } = await setup();
    http
      .expectOne('/api/users/verify-email')
      .flush(null, { status: 502, statusText: 'Bad Gateway' });
    await settle();
    await harness.fixture.whenStable();

    expect(element().querySelector('ui-alert')?.textContent).toContain('Une erreur est survenue');
    (element().querySelector('button') as HTMLButtonElement).click();
    http.expectOne('/api/users/verify-email').flush(null, { status: 204, statusText: 'OK' });
    await settle();
    await harness.fixture.whenStable();
    expect(element().querySelector('h1')?.textContent).toContain('Votre compte est activé');
  });

  it('says the link is invalid when it has no token', async () => {
    const { http, element, harness } = await setup('/fr/account/verify-email');
    await harness.fixture.whenStable();
    http.expectNone('/api/users/verify-email');
    expect(element().querySelector('h1')?.textContent).toContain("Ce lien n'est plus valable");
  });
});
