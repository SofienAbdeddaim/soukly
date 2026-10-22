import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { LocaleService } from '@soukly/i18n';
import { RegistrationDrafts } from '../shared/registration-draft';
import { accountI18nTesting, accountTestingProviders } from '../testing/account-testing';
import { RegisterPage } from './register-page';

describe('RegisterPage', () => {
  async function setup(before?: () => void) {
    TestBed.configureTestingModule({
      imports: [RegisterPage, accountI18nTesting('fr')],
      providers: accountTestingProviders(),
    });
    before?.();
    TestBed.inject(LocaleService).use('fr');
    const fixture = TestBed.createComponent(RegisterPage);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    const input = (id: string) => element.querySelector(`#${id}`) as HTMLInputElement;
    const type = async (id: string, value: string) => {
      input(id).value = value;
      input(id).dispatchEvent(new Event('input'));
      input(id).dispatchEvent(new Event('blur'));
      await fixture.whenStable();
    };
    const fillValid = async () => {
      await type('register-display-name', 'Amira B.');
      await type('register-email', ' amira@example.tn ');
      await type('register-phone', '20 123 456');
      await type('register-password', 'Jasmin-de-Carthage-2026');
      input('register-terms').click();
      await fixture.whenStable();
    };
    const submit = async () => {
      (element.querySelector('button[type=submit]') as HTMLButtonElement).click();
      await fixture.whenStable();
    };
    return {
      fixture,
      element,
      input,
      type,
      fillValid,
      submit,
      http: TestBed.inject(HttpTestingController),
    };
  }

  const settle = () => new Promise((resolve) => setTimeout(resolve));

  it('has the fields in the order of the ticket, with the fixed +216 prefix', async () => {
    const { element, input } = await setup();
    const ids = Array.from(element.querySelectorAll('input')).map((field) => field.id);
    expect(ids).toEqual([
      'register-display-name',
      'register-email',
      'register-phone',
      'register-password',
      'register-terms',
    ]);
    const phone = input('register-phone');
    expect(phone.type).toBe('tel');
    expect(phone.inputMode).toBe('numeric');
    expect(element.querySelector('.ui-field__prefix')?.textContent?.trim()).toBe('+216');
  });

  it('links the terms checkbox to the terms of use and the privacy policy, in a new tab', async () => {
    const { element } = await setup();
    const links = Array.from(element.querySelectorAll('ui-checkbox a')).map((link) => [
      link.textContent?.trim(),
      link.getAttribute('href'),
      link.getAttribute('target'),
    ]);
    expect(links).toEqual([
      ["conditions d'utilisation", '/fr/legal/terms', '_blank'],
      ['politique de confidentialité', '/fr/legal/privacy', '_blank'],
    ]);
  });

  it('refuses a 4-digit phone number when the field is left', async () => {
    const { element, type, input } = await setup();
    await type('register-phone', '1234');

    expect(input('register-phone').getAttribute('aria-invalid')).toBe('true');
    const error = element.querySelector('#register-phone-error');
    expect(error?.textContent).toContain('Numéro tunisien à 8 chiffres attendu.');
    expect(input('register-phone').getAttribute('aria-describedby')).toContain(
      'register-phone-error',
    );
  });

  it('shows every error on submit and focuses the first invalid field, without calling the API', async () => {
    const { element, submit, input, http } = await setup();
    await submit();
    await settle();

    expect(element.querySelectorAll('[aria-invalid="true"]')).toHaveLength(5);
    expect(document.activeElement).toBe(input('register-display-name'));
    http.expectNone('/api/users/register');
  });

  it('sends the registration, then opens check-email with the address in the navigation state', async () => {
    const { fixture, fillValid, submit, http, element } = await setup();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate');
    await fillValid();
    await submit();

    const request = http.expectOne('/api/users/register');
    expect(request.request.body).toEqual({
      displayName: 'Amira B.',
      email: 'amira@example.tn',
      phone: '+21620123456',
      password: 'Jasmin-de-Carthage-2026',
      acceptTerms: true,
    });
    expect(element.querySelector('fieldset')?.disabled).toBe(true);
    expect(element.querySelector('button[type=submit]')?.getAttribute('aria-busy')).toBe('true');
    request.flush(null, { status: 202, statusText: 'Accepted' });
    await fixture.whenStable();
    await settle();

    expect(navigate).toHaveBeenCalledWith(['/', 'fr', 'account', 'check-email'], {
      state: { email: 'amira@example.tn' },
    });
    expect(TestBed.inject(RegistrationDrafts).draft()).toEqual({
      displayName: 'Amira B.',
      email: 'amira@example.tn',
      phone: '20 123 456',
      acceptTerms: true,
    });
  });

  it('shows the API codes, translated, under their fields', async () => {
    const { fixture, fillValid, submit, http, element, input } = await setup();
    await fillValid();
    await submit();

    http.expectOne('/api/users/register').flush(
      {
        type: 'urn:soukly:problem:validation-failed',
        detail: 'One or more fields are invalid.',
        errors: [
          { field: 'phone', code: 'phone.invalid' },
          { field: 'password', code: 'password.too-common' },
        ],
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await fixture.whenStable();
    await settle();
    await fixture.whenStable();
    await settle();

    expect(element.querySelector('#register-phone-error')?.textContent).toContain(
      'Numéro tunisien à 8 chiffres attendu.',
    );
    expect(element.querySelector('#register-password-error')?.textContent).toContain(
      'Ce mot de passe est trop courant.',
    );
    expect(element.textContent).not.toContain('One or more fields are invalid.');
    expect(document.activeElement).toBe(input('register-phone'));
  });

  it('shows a general message, never the API text, for an unexpected error', async () => {
    const { fixture, fillValid, submit, http, element } = await setup();
    await fillValid();
    await submit();

    http
      .expectOne('/api/users/register')
      .flush({ detail: 'Unexpected error.' }, { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();
    await settle();
    await fixture.whenStable();

    const alert = element.querySelector('ui-alert');
    expect(alert?.getAttribute('role')).toBe('alert');
    expect(alert?.textContent).toContain('Une erreur est survenue.');
    expect(element.textContent).not.toContain('Unexpected error.');
    expect(document.activeElement).toBe(alert);
  });

  it('starts from the last registration, password excepted', async () => {
    const { input } = await setup(() =>
      TestBed.inject(RegistrationDrafts).save({
        displayName: 'Amira B.',
        email: 'amira@example.tn',
        phone: '20 123 456',
        acceptTerms: true,
      }),
    );

    expect(input('register-email').value).toBe('amira@example.tn');
    expect(input('register-phone').value).toBe('20 123 456');
    expect(input('register-terms').checked).toBe(true);
    expect(input('register-password').value).toBe('');
  });
});
