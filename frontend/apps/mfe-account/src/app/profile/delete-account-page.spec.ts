import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { AuthService, sessionResponse } from '@soukly/auth';
import { ToastService } from '@soukly/ui';
import { accountI18nTesting, accountTestingProviders } from '../testing/account-testing';
import { DeleteAccountPage } from './delete-account-page';

const settle = () => new Promise((resolve) => setTimeout(resolve));
const COUNTS = { PUBLISHED: 3, DRAFT: 1, EXPIRED: 0, INACTIVE: 0 };

describe('DeleteAccountPage', () => {
  async function setup(lang: 'fr' | 'ar' = 'fr') {
    TestBed.configureTestingModule({
      imports: [accountI18nTesting(lang)],
      providers: accountTestingProviders([
        { path: `${lang}/account/profile/delete`, component: DeleteAccountPage },
      ]),
    });
    const http = TestBed.inject(HttpTestingController);
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(`/${lang}/account/profile/delete`);
    http
      .expectOne((request) => request.url === '/api/listings/mine')
      .flush({ items: [], counts: COUNTS });
    await settle();
    await harness.fixture.whenStable();
    return { harness, http, element: () => harness.routeNativeElement as HTMLElement };
  }

  async function submit(
    harness: RouterTestingHarness,
    element: HTMLElement,
    password: string,
    tick: boolean,
  ) {
    const input = element.querySelector('#delete-password') as HTMLInputElement;
    input.value = password;
    input.dispatchEvent(new Event('input'));
    if (tick) {
      (element.querySelector('#delete-understood') as HTMLInputElement).click();
    }
    (element.querySelector('button[type=submit]') as HTMLButtonElement).click();
    await harness.fixture.whenStable();
  }

  it('lists what will happen, with the number of listings', async () => {
    const { element } = await setup();
    const consequences = element().querySelector('[data-testid=delete-consequences]');

    expect(consequences?.textContent).toContain(
      'vos 4 annonces disparaissent tout de suite du site',
    );
    expect(consequences?.textContent).toContain('effacées sous 30 jours');
    expect(consequences?.textContent).toContain('Facebook ou Instagram');
    const keep = element().querySelector('[data-testid=delete-keep]');
    expect(keep?.textContent).toContain('Garder mon compte');
    expect(keep?.getAttribute('href')).toBe('/fr/account/profile');
  });

  it('needs the confirmation box, announced as required', async () => {
    const { harness, http, element } = await setup();
    expect(element().querySelector('#delete-understood')?.getAttribute('aria-required')).toBe(
      'true',
    );
    await submit(harness, element(), 'Jasmin-de-Carthage-2026', false);

    http.expectNone('/api/users/me');
    expect(element().textContent).toContain('Cochez la case pour confirmer la suppression.');
  });

  it('deletes the account, forgets the session and goes home with a toast', async () => {
    const { harness, http, element } = await setup();
    const auth = TestBed.inject(AuthService);
    const login = auth.login('amira@example.tn', 'secret-password');
    http.expectOne('/api/users/login').flush(sessionResponse('token-1'));
    await login;
    const show = vi.spyOn(TestBed.inject(ToastService), 'show');
    await submit(harness, element(), 'Jasmin-de-Carthage-2026', true);

    const request = http.expectOne('/api/users/me');
    expect(request.request.method).toBe('DELETE');
    expect(request.request.body).toEqual({ currentPassword: 'Jasmin-de-Carthage-2026' });
    request.flush(null, { status: 204, statusText: 'No Content' });
    await settle();
    await harness.fixture.whenStable();

    expect(auth.isAuthenticated()).toBe(false);
    expect(TestBed.inject(Router).url).toBe('/fr');
    expect(show).toHaveBeenCalledWith(
      expect.objectContaining({ type: 'success', message: 'Votre compte a été supprimé' }),
    );
  });

  it('keeps the account and shows a wrong password under its field', async () => {
    const { harness, http, element } = await setup();
    await submit(harness, element(), 'wrong-password', true);
    http
      .expectOne('/api/users/me')
      .flush(
        { type: 'urn:soukly:problem:current-password-invalid' },
        { status: 400, statusText: 'Bad Request' },
      );
    await settle();
    await harness.fixture.whenStable();

    expect(element().textContent).toContain('Mot de passe actuel incorrect.');
    expect(TestBed.inject(Router).url).toBe('/fr/account/profile/delete');
  });

  it('uses the Arabic plural forms', async () => {
    const { element } = await setup('ar');

    expect(element().querySelector('[data-testid=delete-consequences]')?.textContent).toContain(
      'تختفي إعلاناتك الـ4 من الموقع فورًا',
    );
  });
});
