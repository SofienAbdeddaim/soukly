import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Meta } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { accountI18nTesting, accountTestingProviders } from '../testing/account-testing';
import { ConfirmEmailPage } from './confirm-email-page';

const settle = () => new Promise((resolve) => setTimeout(resolve));

describe('ConfirmEmailPage', () => {
  async function setup(url = '/fr/account/confirm-email?token=abc') {
    TestBed.configureTestingModule({
      imports: [accountI18nTesting()],
      providers: accountTestingProviders([
        { path: 'fr/account/confirm-email', component: ConfirmEmailPage },
      ]),
    });
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url);
    return {
      harness,
      http: TestBed.inject(HttpTestingController),
      element: () => harness.routeNativeElement as HTMLElement,
    };
  }

  async function answer(
    harness: RouterTestingHarness,
    http: HttpTestingController,
    body: object | null,
    status: number,
  ) {
    const request = http.expectOne('/api/users/email/confirm');
    expect(request.request.body).toEqual({ token: 'abc' });
    request.flush(body, { status, statusText: 'Status' });
    await settle();
    await harness.fixture.whenStable();
  }

  it('removes the token from the address bar, sends no Referer and confirms the address', async () => {
    const { harness, http, element } = await setup();
    await answer(harness, http, null, 204);

    expect(TestBed.inject(Router).url).toBe('/fr/account/confirm-email');
    expect(TestBed.inject(Meta).getTag('name="referrer"')?.content).toBe('no-referrer');
    expect(element().querySelector('[data-testid=confirm-email-confirmed]')?.textContent).toContain(
      'Nouvelle adresse confirmée',
    );
  });

  it('says the link is no longer valid for a refused token', async () => {
    const { harness, http, element } = await setup();
    await answer(harness, http, { type: 'urn:soukly:problem:email-change-token-invalid' }, 400);

    expect(element().querySelector('[data-testid=confirm-email-invalid]')).not.toBeNull();
    expect(element().querySelector('a[href="/fr/account/profile/email"]')).not.toBeNull();
  });

  it('says the address was taken in the meantime', async () => {
    const { harness, http, element } = await setup();
    await answer(harness, http, { type: 'urn:soukly:problem:email-taken' }, 409);

    expect(element().querySelector('[data-testid=confirm-email-taken]')?.textContent).toContain(
      'Cette adresse est déjà utilisée',
    );
  });

  it('tries again with the token kept in memory after a network error', async () => {
    const { harness, http, element } = await setup();
    await answer(harness, http, null, 503);
    expect(element().querySelector('[data-testid=confirm-email-error]')).not.toBeNull();

    (element().querySelector('button') as HTMLButtonElement).click();
    await answer(harness, http, null, 204);
    expect(element().querySelector('[data-testid=confirm-email-confirmed]')).not.toBeNull();
  });

  it('shows the invalid link at once without a token', async () => {
    const { http, element, harness } = await setup('/fr/account/confirm-email');
    await harness.fixture.whenStable();

    http.expectNone('/api/users/email/confirm');
    expect(element().querySelector('[data-testid=confirm-email-invalid]')).not.toBeNull();
  });
});
