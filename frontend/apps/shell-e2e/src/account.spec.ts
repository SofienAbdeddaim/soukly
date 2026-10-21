import { expect, Page, test } from '@playwright/test';
import { expectNoSeriousA11yViolations } from './support/axe';
import { fakeAccountApi, problem, session } from './support/fake-api';

/**
 * Account screens of AUTH-03 with a faked account API (see support/fake-api.ts). The same flows
 * against the real gateway and user-service: stack/account-flow.spec.ts.
 */
const PASSWORD = 'Jasmin-de-Carthage-2026';

function passwordField(page: Page, name = 'Mot de passe') {
  return page.getByRole('textbox', { name, exact: true });
}

test.describe('registration', () => {
  test('a valid form leads to "Check your email", which recalls the address', async ({ page }) => {
    const api = await fakeAccountApi(page);
    await page.goto('/fr/account/register');

    await page.getByLabel('Nom affiché').fill('Amira B.');
    await page.getByLabel('Email').fill('amira@example.tn');
    await page.getByLabel('Téléphone').fill('20 123 456');
    await passwordField(page).fill(PASSWORD);
    await page.getByLabel("J'accepte les conditions d'utilisation").check();
    await page.getByRole('button', { name: 'Créer mon compte' }).click();

    await expect(page).toHaveURL('/fr/account/check-email');
    await expect(page.getByTestId('check-email-address')).toHaveText('amira@example.tn');
    expect(api.calls.find((call) => call.path === '/api/users/register')?.body).toEqual({
      displayName: 'Amira B.',
      email: 'amira@example.tn',
      phone: '+21620123456',
      password: PASSWORD,
      acceptTerms: true,
    });
    await expectNoSeriousA11yViolations(page);

    // "Resend the email" waits 60 s between two clicks.
    const resend = page.getByTestId('resend-email');
    await resend.click();
    await expect(page.getByRole('status')).toContainText('Email renvoyé.');
    await expect(resend).toBeDisabled();
    await expect(resend).toContainText(/\(\d+ s\)/);

    // "Change the email address" brings back the filled-in form, password excepted.
    await page.getByRole('link', { name: "Modifier l'adresse email" }).click();
    await expect(page.getByLabel('Email')).toHaveValue('amira@example.tn');
    await expect(page.getByLabel('Téléphone')).toHaveValue('20 123 456');
    await expect(passwordField(page)).toHaveValue('');
  });

  test('a phone number of 4 digits is refused when the field is left', async ({ page }) => {
    await fakeAccountApi(page);
    await page.goto('/fr/account/register');

    const phone = page.getByLabel('Téléphone');
    await phone.fill('1234');
    await phone.blur();

    await expect(page.getByText('Numéro tunisien à 8 chiffres attendu.')).toBeVisible();
    await expect(phone).toHaveAttribute('aria-invalid', 'true');
    await expect(phone).toHaveAccessibleDescription(/Numéro tunisien à 8 chiffres attendu\./);
  });

  test('an API error code is shown, translated, under its field', async ({ page }) => {
    await fakeAccountApi(page);
    await page.route('**/api/users/register', (route) =>
      route.fulfill({
        status: 400,
        contentType: 'application/problem+json',
        json: {
          ...problem('validation-failed', 400),
          detail: 'One or more fields are invalid.',
          errors: [{ field: 'password', code: 'password.too-common' }],
        },
      }),
    );
    await page.goto('/fr/account/register');
    await page.getByLabel('Nom affiché').fill('Amira B.');
    await page.getByLabel('Email').fill('amira@example.tn');
    await page.getByLabel('Téléphone').fill('20123456');
    await passwordField(page).fill('password123');
    await page.getByLabel("J'accepte les conditions d'utilisation").check();
    await page.getByRole('button', { name: 'Créer mon compte' }).click();

    await expect(
      page.getByText('Ce mot de passe est trop courant. Choisissez-en un autre.'),
    ).toBeVisible();
    await expect(passwordField(page)).toBeFocused();
    await expect(page.getByText('One or more fields are invalid.')).toHaveCount(0);
  });
});

test.describe('email verification', () => {
  test('a valid link activates the account', async ({ page }) => {
    await fakeAccountApi(page);
    await page.goto('/fr/account/verify-email?token=valid-token');

    await expect(page.getByRole('heading', { name: 'Votre compte est activé' })).toBeVisible();
    await expect(page.getByRole('main').getByRole('link', { name: 'Se connecter' })).toBeVisible();
    // The token does not stay in the address bar.
    await expect(page).toHaveURL('/fr/account/verify-email');
  });

  test('a used link offers a new one', async ({ page }) => {
    const api = await fakeAccountApi(page);
    await page.goto('/fr/account/verify-email?token=used-token');

    await expect(page.getByRole('heading', { name: "Ce lien n'est plus valable" })).toBeVisible();
    await page.getByLabel('Email').fill('amira@example.tn');
    await page.getByRole('button', { name: 'Recevoir un nouveau lien' }).click();

    await expect(page.getByRole('status')).toContainText(
      'un nouveau lien vient de lui être envoyé',
    );
    expect(api.calls.find((call) => call.path === '/api/users/verify-email/resend')?.body).toEqual({
      email: 'amira@example.tn',
    });
    await expectNoSeriousA11yViolations(page);
  });
});

test.describe('login', () => {
  test('first login: welcome screen, "Later", name in the header; logout; no welcome screen after', async ({
    page,
  }) => {
    const api = await fakeAccountApi(page, {
      logins: { [PASSWORD]: { status: 200, body: session('access-token', true) } },
    });
    await page.goto('/fr/account/login');
    await page.getByLabel('Email').fill('amira@example.tn');
    await passwordField(page).fill(PASSWORD);
    await page.getByRole('button', { name: 'Se connecter' }).click();

    await expect(page).toHaveURL('/fr/account/welcome');
    await expect(page.getByRole('heading', { name: 'Une annonce, publiée partout' })).toBeVisible();
    await expect(page.getByText('Bienvenue, Amira B.')).toBeVisible();
    await expectNoSeriousA11yViolations(page);

    await page.getByTestId('welcome-later').click();
    await expect(page).toHaveURL('/fr');
    await expect(page.getByTestId('user-name')).toHaveText('Amira B.');

    await page.getByTestId('user-menu').click();
    await page.getByRole('button', { name: 'Se déconnecter' }).click();
    await expect(page.getByTestId('user-menu')).toHaveCount(0);
    expect(api.calls.map((call) => call.path)).toContain('/api/users/logout');

    api.logins[PASSWORD] = { status: 200, body: session('access-token-2', false) };
    await page.goto('/fr/account/login');
    await page.getByLabel('Email').fill('amira@example.tn');
    await passwordField(page).fill(PASSWORD);
    await page.getByRole('button', { name: 'Se connecter' }).click();
    await expect(page).toHaveURL('/fr');
    await expect(page.getByTestId('user-name')).toHaveText('Amira B.');
  });

  test('wrong credentials: one general message, focused, no field marked', async ({ page }) => {
    await fakeAccountApi(page);
    await page.goto('/fr/account/login');
    await page.getByLabel('Email').fill('amira@example.tn');
    await passwordField(page).fill('Not-the-right-one');
    await page.getByRole('button', { name: 'Se connecter' }).click();

    await expect(page.getByTestId('login-error')).toHaveText('Email ou mot de passe incorrect.');
    await expect(page.getByTestId('login-error')).toBeFocused();
    await expect(page.locator('[aria-invalid="true"]')).toHaveCount(0);
  });

  test('email not verified: a dedicated message and a resend button', async ({ page }) => {
    await fakeAccountApi(page, {
      logins: { [PASSWORD]: { status: 403, body: problem('email-not-verified', 403) } },
    });
    await page.goto('/fr/account/login');
    await page.getByLabel('Email').fill('amira@example.tn');
    await passwordField(page).fill(PASSWORD);
    await page.getByRole('button', { name: 'Se connecter' }).click();

    await expect(page.getByTestId('login-error')).toContainText("n'est pas encore vérifiée");
    await page.getByRole('button', { name: "Renvoyer l'email de vérification" }).click();
    await expect(page.getByRole('status')).toContainText('Email renvoyé.');
  });

  test('too many attempts: the remaining delay', async ({ page }) => {
    await fakeAccountApi(page, {
      logins: {
        [PASSWORD]: {
          status: 429,
          body: problem('too-many-requests', 429),
          headers: { 'Retry-After': '120' },
        },
      },
    });
    await page.goto('/fr/account/login');
    await page.getByLabel('Email').fill('amira@example.tn');
    await passwordField(page).fill(PASSWORD);
    await page.getByRole('button', { name: 'Se connecter' }).click();

    await expect(page.getByTestId('login-error')).toHaveText(
      'Trop de tentatives. Réessayez dans 2 min.',
    );
  });

  test('a protected page asks for a login, then opens', async ({ page }) => {
    await fakeAccountApi(page, {
      logins: { [PASSWORD]: { status: 200, body: session() } },
    });
    await page.goto('/fr/publish/new');

    await expect(page).toHaveURL('/fr/account/login?returnUrl=%2Ffr%2Fpublish%2Fnew');
    await page.getByLabel('Email').fill('amira@example.tn');
    await passwordField(page).fill(PASSWORD);
    await page.getByRole('button', { name: 'Se connecter' }).click();

    await expect(page).toHaveURL('/fr/publish/new');
    await expect(page.getByTestId('coming-soon-mfe-publish')).toBeVisible();
  });

  test('an external returnUrl is ignored', async ({ page }) => {
    await fakeAccountApi(page, {
      logins: { [PASSWORD]: { status: 200, body: session() } },
    });
    await page.goto('/fr/account/login?returnUrl=https%3A%2F%2Fevil.example%2F');
    await page.getByLabel('Email').fill('amira@example.tn');
    await passwordField(page).fill(PASSWORD);
    await page.getByRole('button', { name: 'Se connecter' }).click();

    await expect(page).toHaveURL('/fr');
  });

  test('the session is restored on reload', async ({ page }) => {
    await fakeAccountApi(page, { signedIn: true });
    await page.goto('/fr');
    await expect(page.getByTestId('user-name')).toHaveText('Amira B.');

    await page.reload();
    await expect(page.getByTestId('user-name')).toHaveText('Amira B.');
  });

  test('in Arabic the form is mirrored and the email is typed left to right', async ({ page }) => {
    await fakeAccountApi(page);
    await page.goto('/ar/account/login');

    await expect(page.getByRole('heading', { name: 'تسجيل الدخول' })).toBeVisible();
    await expect(page.locator('html')).toHaveAttribute('dir', 'rtl');
    const email = page.getByLabel('البريد الإلكتروني');
    expect(await email.evaluate((input) => getComputedStyle(input).direction)).toBe('ltr');
    const form = await page.getByTestId('login-form').boundingBox();
    const label = await page.locator('label[for="login-password"]').boundingBox();
    // Labels start on the right.
    expect(form && label && label.x + label.width > form.x + form.width - 2).toBe(true);
    await expectNoSeriousA11yViolations(page);
  });
});

test.describe('mobile (390px)', () => {
  test.use({ viewport: { width: 390, height: 844 } });

  test('the member menu stays in the header', async ({ page }) => {
    await fakeAccountApi(page, { signedIn: true });
    await page.goto('/fr');

    await expect(page.getByTestId('user-menu')).toBeVisible();
    await page.getByTestId('user-menu').click();
    await expect(page.getByRole('link', { name: 'Mon compte' })).toBeVisible();
  });
});
