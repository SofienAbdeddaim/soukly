import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { LocaleService } from '@soukly/i18n';
import { accountI18nTesting, accountTestingProviders } from '../testing/account-testing';
import { CheckEmailPage } from './check-email-page';

describe('CheckEmailPage', () => {
  afterEach(() => vi.useRealTimers());

  async function setup(state?: { email: string }) {
    TestBed.configureTestingModule({
      imports: [accountI18nTesting('fr')],
      providers: accountTestingProviders([
        { path: 'fr/account/check-email', component: CheckEmailPage },
      ]),
    });
    TestBed.inject(LocaleService).use('fr');
    const harness = await RouterTestingHarness.create();
    await TestBed.inject(Router).navigateByUrl('/fr/account/check-email', { state });
    await harness.fixture.whenStable();
    return {
      harness,
      http: TestBed.inject(HttpTestingController),
      element: harness.routeNativeElement as HTMLElement,
    };
  }

  it('recalls the address given by the registration, and resends at most once a minute', async () => {
    const { harness, http, element } = await setup({ email: 'amira@example.tn' });
    expect(element.querySelector('[data-testid=check-email-address]')?.textContent).toBe(
      'amira@example.tn',
    );
    expect(element.textContent).toContain('valable 24 heures');
    expect(element.textContent).toContain('courriers indésirables');
    expect(element.querySelector('a[href="/fr/account/register"]')?.textContent).toContain(
      "Modifier l'adresse email",
    );

    vi.useFakeTimers({ toFake: ['setInterval'] });
    const button = element.querySelector('[data-testid=resend-email]') as HTMLButtonElement;
    button.click();
    const request = http.expectOne('/api/users/verify-email/resend');
    expect(request.request.body).toEqual({ email: 'amira@example.tn' });
    request.flush(null, { status: 202, statusText: 'Accepted' });
    await new Promise((resolve) => setTimeout(resolve));
    await harness.fixture.whenStable();

    expect(element.querySelector('[role=status]')?.textContent).toContain('Email renvoyé.');
    expect(button.disabled).toBe(true);
    expect(button.textContent).toContain("Renvoyer l'email (60 s)");

    vi.advanceTimersByTime(59_000);
    await harness.fixture.whenStable();
    expect(button.textContent).toContain('(1 s)');
    vi.advanceTimersByTime(1_000);
    await harness.fixture.whenStable();
    expect(button.disabled).toBe(false);
    expect(button.textContent?.trim()).toBe("Renvoyer l'email");
  });

  it('gives a generic text without an address', async () => {
    const { element } = await setup();
    expect(element.querySelector('[data-testid=check-email-address]')).toBeNull();
    expect(element.textContent).toContain('Ouvrez le lien reçu par email');
  });
});
