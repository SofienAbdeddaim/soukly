import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { LocaleService } from '@soukly/i18n';
import { accountI18nTesting, accountTestingProviders } from '../testing/account-testing';
import { ForgotPasswordPage } from './forgot-password-page';
import { ResetLinkSentPage } from './reset-link-sent-page';

const settle = () => new Promise((resolve) => setTimeout(resolve));

describe('ForgotPasswordPage and ResetLinkSentPage', () => {
  async function setup(state?: { email: string }) {
    TestBed.configureTestingModule({
      imports: [accountI18nTesting('fr')],
      providers: accountTestingProviders([
        { path: 'fr/account/forgot-password', component: ForgotPasswordPage },
        { path: 'fr/account/forgot-password/sent', component: ResetLinkSentPage },
      ]),
    });
    TestBed.inject(LocaleService).use('fr');
    const router = TestBed.inject(Router);
    const harness = await RouterTestingHarness.create();
    await router.navigateByUrl('/fr/account/forgot-password', { state });
    await harness.fixture.whenStable();
    return {
      harness,
      router,
      http: TestBed.inject(HttpTestingController),
      element: () => harness.routeNativeElement as HTMLElement,
    };
  }

  const emailInput = (element: HTMLElement) =>
    element.querySelector('#forgot-email') as HTMLInputElement;

  it('is prefilled with the address typed on the login page', async () => {
    const { element } = await setup({ email: 'amira@example.tn' });

    expect(emailInput(element()).value).toBe('amira@example.tn');
  });

  it('checks the address before sending', async () => {
    const { harness, http, element } = await setup();
    emailInput(element()).value = 'amira@';
    emailInput(element()).dispatchEvent(new Event('input'));
    (element().querySelector('button[type=submit]') as HTMLButtonElement).click();
    await harness.fixture.whenStable();

    http.expectNone('/api/users/password/forgot');
    expect(element().textContent).toContain('Adresse email invalide');
  });

  it('sends the link, then shows the neutral "check your email" page with a 60 s cooldown', async () => {
    const { harness, http, element, router } = await setup();
    emailInput(element()).value = ' amira@example.tn ';
    emailInput(element()).dispatchEvent(new Event('input'));
    (element().querySelector('button[type=submit]') as HTMLButtonElement).click();

    const request = http.expectOne('/api/users/password/forgot');
    expect(request.request.body).toEqual({ email: 'amira@example.tn' });
    request.flush(null, { status: 202, statusText: 'Accepted' });
    await settle();
    await harness.fixture.whenStable();

    expect(router.url).toBe('/fr/account/forgot-password/sent');
    expect(element().querySelector('[data-testid=sent-address]')?.textContent).toBe(
      'amira@example.tn',
    );
    expect(element().textContent).toContain('Si un compte existe pour');
    const resend = element().querySelector('[data-testid=resend-link]') as HTMLButtonElement;
    expect(resend.disabled).toBe(true);
    expect(resend.textContent).toMatch(/\(60 s\)/);
  });

  it('says to wait a few minutes on a 429 of the gateway', async () => {
    const { harness, http, element } = await setup({ email: 'amira@example.tn' });
    (element().querySelector('button[type=submit]') as HTMLButtonElement).click();
    http
      .expectOne('/api/users/password/forgot')
      .flush(
        { type: 'urn:soukly:problem:too-many-requests' },
        { status: 429, statusText: 'Too Many Requests' },
      );
    await settle();
    await harness.fixture.whenStable();

    expect(element().querySelector('[data-testid=forgot-error]')?.textContent).toContain(
      'Trop de tentatives, réessayez dans quelques minutes.',
    );
  });

  it('offers a new request when the sent page is opened without an address', async () => {
    TestBed.configureTestingModule({
      imports: [accountI18nTesting('fr')],
      providers: accountTestingProviders([
        { path: 'fr/account/forgot-password/sent', component: ResetLinkSentPage },
      ]),
    });
    TestBed.inject(LocaleService).use('fr');
    const harness = await RouterTestingHarness.create('/fr/account/forgot-password/sent');
    const element = harness.routeNativeElement as HTMLElement;

    expect(element.querySelector('[data-testid=resend-link]')).toBeNull();
    expect(element.querySelector('a[href="/fr/account/forgot-password"]')).not.toBeNull();
  });
});
