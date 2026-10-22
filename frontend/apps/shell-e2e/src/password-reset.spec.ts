import { expect, test } from '@playwright/test';
import { expectNoSeriousA11yViolations } from './support/axe';
import { fakeAccountApi, problem } from './support/fake-api';

/**
 * Forgotten password of AUTH-04 with a faked account API (see support/fake-api.ts). The same flow
 * against the real stack, with the link read in Mailpit: stack/account-flow.spec.ts.
 */
const NEW_PASSWORD = 'Sidi-Bou-Said-bleu-42';

test.describe('forgotten password', () => {
  test('from the login page: the address typed is kept, the link is sent, resend waits 60 s', async ({
    page,
  }) => {
    const api = await fakeAccountApi(page);
    await page.goto('/fr/account/login');
    await page.getByLabel('Email').fill('amira@example.tn');
    await page.getByRole('link', { name: 'Mot de passe oublié ?' }).click();

    await expect(page).toHaveURL('/fr/account/forgot-password');
    await expect(page.getByLabel('Email')).toHaveValue('amira@example.tn');
    await expectNoSeriousA11yViolations(page);
    await page.getByRole('button', { name: 'Envoyer le lien' }).click();

    await expect(page).toHaveURL('/fr/account/forgot-password/sent');
    await expect(page.getByTestId('sent-address')).toHaveText('amira@example.tn');
    await expect(page).not.toHaveURL(/@/);
    expect(api.calls.find((call) => call.path === '/api/users/password/forgot')?.body).toEqual({
      email: 'amira@example.tn',
    });
    await expect(page.getByTestId('resend-link')).toBeDisabled();
    await expect(page.getByTestId('resend-link')).toContainText(/\(\d+ s\)/);
    await expectNoSeriousA11yViolations(page);
  });

  test('a valid link: the token leaves the URL, the new password signs in, toast at home', async ({
    page,
  }) => {
    const api = await fakeAccountApi(page);
    await page.goto('/fr/account/reset-password?token=valid-reset-token');

    await expect(
      page.getByRole('heading', { name: 'Choisissez un nouveau mot de passe' }),
    ).toBeVisible();
    await expect(page).toHaveURL('/fr/account/reset-password');
    await expect(page.locator('meta[name="referrer"]')).toHaveAttribute('content', 'no-referrer');
    await expect(page.getByText('Vous serez déconnecté de vos autres appareils')).toBeVisible();
    await expectNoSeriousA11yViolations(page);

    const show = page.getByRole('button', { name: 'Afficher le mot de passe' });
    await expect(show).toHaveAttribute('aria-pressed', 'false');
    await page.getByRole('textbox', { name: 'Nouveau mot de passe' }).fill(NEW_PASSWORD);
    await page.getByRole('button', { name: 'Enregistrer et me connecter' }).click();

    await expect(page).toHaveURL('/fr');
    await expect(page.getByText('Mot de passe modifié. Vous êtes connecté.')).toBeVisible();
    await expect(page.getByTestId('user-name')).toHaveText('Amira B.');
    expect(api.calls.find((call) => call.path === '/api/users/password/reset')?.body).toEqual({
      token: 'valid-reset-token',
      newPassword: NEW_PASSWORD,
    });
  });

  test('a used or expired link: "This link is no longer valid" and a new request', async ({
    page,
  }) => {
    await fakeAccountApi(page);
    await page.goto('/fr/account/reset-password?token=used-token');
    await page.getByRole('textbox', { name: 'Nouveau mot de passe' }).fill(NEW_PASSWORD);
    await page.getByRole('button', { name: 'Enregistrer et me connecter' }).click();

    await expect(page.getByTestId('reset-invalid')).toHaveText("Ce lien n'est plus valable");
    await expectNoSeriousA11yViolations(page);
    await page.getByRole('link', { name: 'Demander un nouveau lien' }).click();
    await expect(page).toHaveURL('/fr/account/forgot-password');
  });

  test('a common password is refused under the field', async ({ page }) => {
    await fakeAccountApi(page);
    await page.route('**/api/users/password/reset', (route) =>
      route.fulfill({
        status: 400,
        contentType: 'application/problem+json',
        json: {
          ...problem('validation-failed', 400),
          errors: [{ field: 'newPassword', code: 'password.too-common' }],
        },
      }),
    );
    await page.goto('/fr/account/reset-password?token=valid-reset-token');
    await page.getByRole('textbox', { name: 'Nouveau mot de passe' }).fill('password123');
    await page.getByRole('button', { name: 'Enregistrer et me connecter' }).click();

    const field = page.getByRole('textbox', { name: 'Nouveau mot de passe' });
    await expect(field).toHaveAttribute('aria-invalid', 'true');
    await expect(field).toHaveAccessibleDescription(/trop courant/);
  });

  test('too many requests from this IP: wait a few minutes', async ({ page }) => {
    await fakeAccountApi(page);
    await page.route('**/api/users/password/forgot', (route) =>
      route.fulfill({
        status: 429,
        headers: { 'Retry-After': '60' },
        contentType: 'application/problem+json',
        json: problem('too-many-requests', 429),
      }),
    );
    await page.goto('/fr/account/forgot-password');
    await page.getByLabel('Email').fill('amira@example.tn');
    await page.getByRole('button', { name: 'Envoyer le lien' }).click();

    await expect(page.getByTestId('forgot-error')).toHaveText(
      'Trop de tentatives, réessayez dans quelques minutes.',
    );
  });
});

test.describe('mobile (390px), Arabic', () => {
  test.use({ viewport: { width: 390, height: 844 } });

  test('the new password screen is right to left', async ({ page }) => {
    await fakeAccountApi(page);
    await page.goto('/ar/account/reset-password?token=valid-reset-token');

    await expect(page.getByRole('heading', { name: 'اختر كلمة مرور جديدة' })).toBeVisible();
    await expect(page.locator('html')).toHaveAttribute('dir', 'rtl');
    await expect(page).toHaveURL('/ar/account/reset-password');
    await expectNoSeriousA11yViolations(page);
  });
});
