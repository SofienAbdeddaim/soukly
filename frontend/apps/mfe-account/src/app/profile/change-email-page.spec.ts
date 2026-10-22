import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { ToastService } from '@soukly/ui';
import { accountI18nTesting, accountTestingProviders, PROFILE } from '../testing/account-testing';
import { ChangeEmailPage } from './change-email-page';

const settle = () => new Promise((resolve) => setTimeout(resolve));

describe('ChangeEmailPage', () => {
  async function setup() {
    TestBed.configureTestingModule({
      imports: [accountI18nTesting()],
      providers: accountTestingProviders([
        { path: 'fr/account/profile/email', component: ChangeEmailPage },
      ]),
    });
    const http = TestBed.inject(HttpTestingController);
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/fr/account/profile/email');
    http.expectOne('/api/users/me').flush(PROFILE);
    await settle();
    await harness.fixture.whenStable();
    return { harness, http, element: () => harness.routeNativeElement as HTMLElement };
  }

  async function fill(
    harness: RouterTestingHarness,
    element: HTMLElement,
    email: string,
    password: string,
  ) {
    for (const [id, value] of [
      ['email-change-new', email],
      ['email-change-password', password],
    ]) {
      const input = element.querySelector(`#${id}`) as HTMLInputElement;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    }
    (element.querySelector('button[type=submit]') as HTMLButtonElement).click();
    await harness.fixture.whenStable();
  }

  it('sends the new address with the current password, then asks to confirm it', async () => {
    const { harness, http, element } = await setup();
    expect(element().querySelector('[data-testid=email-change-current]')?.textContent).toContain(
      'amira@example.tn',
    );
    await fill(harness, element(), ' amira.bs@example.tn ', 'Jasmin-de-Carthage-2026');

    const request = http.expectOne('/api/users/me/email');
    expect(request.request.body).toEqual({
      newEmail: 'amira.bs@example.tn',
      currentPassword: 'Jasmin-de-Carthage-2026',
    });
    request.flush(null, { status: 202, statusText: 'Accepted' });
    await settle();
    await harness.fixture.whenStable();

    const banner = element().querySelector('[data-testid=email-change-sent]');
    expect(banner?.textContent).toContain('Confirmez votre nouvelle adresse.');
    expect(banner?.textContent).toContain('amira.bs@example.tn');
    expect(banner?.textContent).toContain('valable 24 heures');
    expect(element().querySelector('[data-testid=email-change-form]')).toBeNull();
  });

  it('shows a wrong current password under its field', async () => {
    const { harness, http, element } = await setup();
    await fill(harness, element(), 'amira.bs@example.tn', 'wrong-password');
    http
      .expectOne('/api/users/me/email')
      .flush(
        { type: 'urn:soukly:problem:current-password-invalid' },
        { status: 400, statusText: 'Bad Request' },
      );
    await settle();
    await harness.fixture.whenStable();

    expect(element().textContent).toContain('Mot de passe actuel incorrect.');
    expect(element().querySelector('#email-change-password')?.getAttribute('aria-invalid')).toBe(
      'true',
    );
  });

  it('checks the format of the address before sending', async () => {
    const { harness, http, element } = await setup();
    await fill(harness, element(), 'amira@example', 'Jasmin-de-Carthage-2026');

    http.expectNone('/api/users/me/email');
    expect(element().textContent).toContain('Adresse email invalide');
  });

  it('cancels the change from the banner and goes back to the profile with a toast', async () => {
    const { harness, http, element } = await setup();
    const show = vi.spyOn(TestBed.inject(ToastService), 'show');
    await fill(harness, element(), 'amira.bs@example.tn', 'Jasmin-de-Carthage-2026');
    http.expectOne('/api/users/me/email').flush(null, { status: 202, statusText: 'Accepted' });
    await settle();
    await harness.fixture.whenStable();

    (element().querySelector('[data-testid=email-change-cancel]') as HTMLButtonElement).click();
    const request = http.expectOne('/api/users/me/email-change');
    expect(request.request.method).toBe('DELETE');
    request.flush(null, { status: 204, statusText: 'No Content' });
    await settle();
    await harness.fixture.whenStable();

    expect(TestBed.inject(Router).url).toBe('/fr/account/profile');
    expect(show).toHaveBeenCalledWith(
      expect.objectContaining({ message: "Changement d'adresse annulé" }),
    );
  });
});
