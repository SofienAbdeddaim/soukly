import { expect, Page, test } from '@playwright/test';
import { expectNoSeriousA11yViolations } from './support/axe';
import { CURRENT_PASSWORD, FakeAccountApi, fakeAccountApi } from './support/fake-api';
import { draft, fakeListingApi, inDays } from './support/fake-listing-api';

/**
 * "My profile" of AUTH-05 with a faked user-service (support/fake-api.ts) and a signed-in member:
 * details, email and password changes, the link of the email change and the deletion of the
 * account.
 */
test.use({ viewport: { width: 390, height: 844 } });

async function open(page: Page, url = '/fr/account/profile'): Promise<FakeAccountApi> {
  const api = await fakeAccountApi(page, { signedIn: true });
  await page.goto(url);
  return api;
}

test.describe('my profile', () => {
  test('the "Account" tab leads to the profile, whose details are saved', async ({ page }) => {
    const api = await open(page, '/fr');
    await page
      .getByRole('navigation', { name: /navigation/i })
      .getByRole('link', { name: 'Compte' })
      .click();

    await expect(page).toHaveURL('/fr/account/profile');
    await expect(page.getByRole('heading', { level: 1, name: 'Mon profil' })).toBeVisible();
    await expect(page.getByText('Membre depuis mars 2026')).toBeVisible();
    await expect(page.getByTestId('profile-email')).toHaveText('amira@example.tn');
    await expectNoSeriousA11yViolations(page);

    await page.getByLabel('Nom affiché').fill('Amira Ben Salah');
    await page.getByRole('textbox', { name: 'Téléphone' }).fill('98 765 432');
    await page.getByLabel('Langue des emails').selectOption('ar');
    await page.getByRole('button', { name: 'Enregistrer' }).click();

    await expect(page.getByText('Profil mis à jour')).toBeVisible();
    await expect(page.getByTestId('user-name')).toHaveText('Amira Ben Salah');
    expect(api.calls.findLast((call) => call.path === '/api/users/me')?.body).toEqual({
      displayName: 'Amira Ben Salah',
      phone: '+21698765432',
      preferredLanguage: 'ar',
    });
  });

  test('a wrong phone is refused under the field', async ({ page }) => {
    await open(page);
    const phone = page.getByRole('textbox', { name: 'Téléphone' });
    await phone.fill('1234');
    await page.getByRole('button', { name: 'Enregistrer' }).click();

    await expect(phone).toHaveAttribute('aria-invalid', 'true');
    await expect(phone).toHaveAccessibleDescription(/8 chiffres/);
    await expect(phone).toBeFocused();
  });

  test('changing the password signs out the other devices, this one stays', async ({ page }) => {
    const api = await open(page);
    await page.getByRole('link', { name: 'Modifier le mot de passe' }).click();

    await expect(page).toHaveURL('/fr/account/profile/password');
    await expect(page.getByText('Vos autres appareils seront déconnectés.')).toBeVisible();
    await expectNoSeriousA11yViolations(page);
    await page.getByRole('textbox', { name: 'Mot de passe actuel' }).fill('wrong-password');
    await page.getByRole('textbox', { name: 'Nouveau mot de passe' }).fill('Sidi-Bou-Said-bleu-42');
    await page.getByRole('button', { name: 'Enregistrer le mot de passe' }).click();
    await expect(
      page.getByRole('textbox', { name: 'Mot de passe actuel' }),
    ).toHaveAccessibleDescription('Mot de passe actuel incorrect.');

    await page.getByRole('textbox', { name: 'Mot de passe actuel' }).fill(CURRENT_PASSWORD);
    await page.getByRole('button', { name: 'Enregistrer le mot de passe' }).click();
    await expect(page).toHaveURL('/fr/account/profile');
    await expect(
      page.getByText('Mot de passe modifié. Vos autres appareils sont déconnectés.'),
    ).toBeVisible();
    expect(api.calls.findLast((call) => call.path === '/api/users/me/password')?.body).toEqual({
      currentPassword: CURRENT_PASSWORD,
      newPassword: 'Sidi-Bou-Said-bleu-42',
    });
  });

  test('changing the email: link sent, address pending until the link is opened', async ({
    page,
  }) => {
    const api = await open(page);
    await page.getByRole('link', { name: "Modifier l'email" }).click();

    await expect(page).toHaveURL('/fr/account/profile/email');
    await expect(page.getByTestId('email-change-current')).toContainText('amira@example.tn');
    await page.getByLabel('Nouvelle adresse email').fill('amira.bs@example.tn');
    await page.getByRole('textbox', { name: 'Mot de passe actuel' }).fill(CURRENT_PASSWORD);
    await page.getByRole('button', { name: 'Envoyer le lien de confirmation' }).click();

    const banner = page.getByTestId('email-change-sent');
    await expect(banner).toContainText('Confirmez votre nouvelle adresse.');
    await expect(banner).toContainText('amira.bs@example.tn');
    await expectNoSeriousA11yViolations(page);

    await page.getByRole('link', { name: 'Retour au profil' }).last().click();
    await expect(page.getByTestId('profile-pending-email')).toContainText('amira.bs@example.tn');
    await expect(page.getByTestId('profile-email')).toHaveText('amira@example.tn');

    await page.goto('/fr/account/confirm-email?token=valid-email-token');
    await expect(page.getByTestId('confirm-email-confirmed')).toHaveText(
      'Nouvelle adresse confirmée',
    );
    await expect(page).toHaveURL('/fr/account/confirm-email');
    await expect(page.locator('meta[name="referrer"]')).toHaveAttribute('content', 'no-referrer');
    await expectNoSeriousA11yViolations(page);
    await page.getByRole('link', { name: 'Aller à mon profil' }).click();
    await expect(page.getByTestId('profile-email')).toHaveText('amira.bs@example.tn');
    await expect(page.getByTestId('profile-pending-email')).toHaveCount(0);
    expect(api.profile.email).toBe('amira.bs@example.tn');
  });

  test('a pending email change can be cancelled from the profile', async ({ page }) => {
    const api = await fakeAccountApi(page, { signedIn: true });
    api.profile.pendingEmail = 'amira.bs@example.tn';
    await page.goto('/fr/account/profile');
    await page.getByRole('button', { name: 'Annuler' }).click();

    await expect(page.getByText("Changement d'adresse annulé")).toBeVisible();
    await expect(page.getByTestId('profile-pending-email')).toHaveCount(0);
  });

  test('an expired email link says so, signed out', async ({ page }) => {
    await fakeAccountApi(page);
    await page.goto('/fr/account/confirm-email?token=expired');

    await expect(page.getByTestId('confirm-email-invalid')).toHaveText(
      "Ce lien n'est plus valable",
    );
    await expectNoSeriousA11yViolations(page);
  });

  test('deleting the account: consequences, password, confirmation, then home', async ({
    page,
  }) => {
    const api = await fakeAccountApi(page, { signedIn: true });
    await fakeListingApi(page, [
      draft({ id: 'a1', status: 'PUBLISHED', title: 'Peugeot 208', expiresAt: inDays(30) }),
      draft({ id: 'a2', status: 'PUBLISHED', title: 'Canapé', expiresAt: inDays(30) }),
      draft({ id: 'a3', status: 'PUBLISHED', title: 'Vélo', expiresAt: inDays(30) }),
      draft({ id: 'a4', title: 'Table basse' }),
    ]);
    await page.goto('/fr/account/profile');
    await page.getByRole('link', { name: 'Supprimer mon compte' }).click();

    await expect(page).toHaveURL('/fr/account/profile/delete');
    await expect(page.getByTestId('delete-consequences')).toContainText(
      'vos 4 annonces disparaissent tout de suite du site',
    );
    await expectNoSeriousA11yViolations(page);

    await page.locator('#delete-password').fill(CURRENT_PASSWORD);
    await page.getByRole('button', { name: 'Supprimer mon compte' }).click();
    const box = page.getByRole('checkbox', { name: /Je comprends/ });
    await expect(box).toHaveAttribute('aria-invalid', 'true');
    await expect(box).toBeFocused();

    await box.check();
    await page.getByRole('button', { name: 'Supprimer mon compte' }).click();
    await expect(page).toHaveURL('/fr');
    await expect(page.getByText('Votre compte a été supprimé')).toBeVisible();
    await expect(page.getByTestId('user-name')).toHaveCount(0);
    expect(api.calls.findLast((call) => call.path === '/api/users/me')?.body).toEqual({
      currentPassword: CURRENT_PASSWORD,
    });
  });

  test('"Keep my account" goes back to the profile', async ({ page }) => {
    await open(page, '/fr/account/profile/delete');
    await page.getByRole('link', { name: 'Garder mon compte' }).click();

    await expect(page).toHaveURL('/fr/account/profile');
  });
});

test.describe('Arabic', () => {
  test('the profile is right to left and the phone stays left to right', async ({ page }) => {
    await open(page, '/ar/account/profile');

    await expect(page.getByRole('heading', { level: 1, name: 'ملفي الشخصي' })).toBeVisible();
    await expect(page.locator('html')).toHaveAttribute('dir', 'rtl');
    const phone = page.getByRole('textbox', { name: 'الهاتف' });
    await expect(phone).toHaveValue('20 123 456');
    await expect(phone.locator('xpath=ancestor::*[@dir="ltr"][1]')).toHaveCount(1);
    await expectNoSeriousA11yViolations(page);
  });

  test('the deletion screen is right to left', async ({ page }) => {
    await open(page, '/ar/account/profile/delete');

    await expect(page.getByRole('heading', { level: 1, name: 'حذف حسابي' })).toBeVisible();
    await expectNoSeriousA11yViolations(page);
  });
});
