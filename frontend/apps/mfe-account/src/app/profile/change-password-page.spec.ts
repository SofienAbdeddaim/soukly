import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { ToastService } from '@soukly/ui';
import { accountI18nTesting, accountTestingProviders } from '../testing/account-testing';
import { ChangePasswordPage } from './change-password-page';

const settle = () => new Promise((resolve) => setTimeout(resolve));

describe('ChangePasswordPage', () => {
  async function setup() {
    TestBed.configureTestingModule({
      imports: [accountI18nTesting()],
      providers: accountTestingProviders([
        { path: 'fr/account/profile/password', component: ChangePasswordPage },
      ]),
    });
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/fr/account/profile/password');
    await harness.fixture.whenStable();
    return {
      harness,
      http: TestBed.inject(HttpTestingController),
      element: () => harness.routeNativeElement as HTMLElement,
    };
  }

  async function fill(
    harness: RouterTestingHarness,
    element: HTMLElement,
    current: string,
    next: string,
  ) {
    for (const [id, value] of [
      ['password-change-current', current],
      ['password-change-new', next],
    ]) {
      const input = element.querySelector(`#${id}`) as HTMLInputElement;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    }
    (element.querySelector('button[type=submit]') as HTMLButtonElement).click();
    await harness.fixture.whenStable();
  }

  it('says the other devices will be signed out', async () => {
    const { element } = await setup();

    expect(element().textContent).toContain('Vos autres appareils seront déconnectés.');
  });

  it('changes the password and goes back to the profile with a toast', async () => {
    const { harness, http, element } = await setup();
    const show = vi.spyOn(TestBed.inject(ToastService), 'show');
    await fill(harness, element(), 'Jasmin-de-Carthage-2026', 'Sidi-Bou-Said-bleu-42');

    const request = http.expectOne('/api/users/me/password');
    expect(request.request.body).toEqual({
      currentPassword: 'Jasmin-de-Carthage-2026',
      newPassword: 'Sidi-Bou-Said-bleu-42',
    });
    request.flush(null, { status: 204, statusText: 'No Content' });
    await settle();
    await harness.fixture.whenStable();

    expect(TestBed.inject(Router).url).toBe('/fr/account/profile');
    expect(show).toHaveBeenCalledWith(
      expect.objectContaining({
        message: 'Mot de passe modifié. Vos autres appareils sont déconnectés.',
      }),
    );
  });

  it('shows the errors of the API under their field', async () => {
    const { harness, http, element } = await setup();
    await fill(harness, element(), 'wrong-password', 'Sidi-Bou-Said-bleu-42');
    http
      .expectOne('/api/users/me/password')
      .flush(
        { type: 'urn:soukly:problem:current-password-invalid' },
        { status: 400, statusText: 'Bad Request' },
      );
    await settle();
    await harness.fixture.whenStable();
    expect(element().textContent).toContain('Mot de passe actuel incorrect.');

    await fill(harness, element(), 'Jasmin-de-Carthage-2026', 'password123');
    http.expectOne('/api/users/me/password').flush(
      {
        type: 'urn:soukly:problem:validation-failed',
        errors: [{ field: 'newPassword', code: 'password.too-common' }],
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await settle();
    await harness.fixture.whenStable();
    expect(element().textContent).toContain('Ce mot de passe est trop courant.');
  });

  it('says to wait after too many wrong passwords', async () => {
    const { harness, http, element } = await setup();
    await fill(harness, element(), 'wrong-password', 'Sidi-Bou-Said-bleu-42');
    http
      .expectOne('/api/users/me/password')
      .flush(null, { status: 429, statusText: 'Too Many Requests' });
    await settle();
    await harness.fixture.whenStable();

    expect(element().querySelector('[data-testid=password-change-error]')?.textContent).toContain(
      'Trop de tentatives',
    );
  });
});
