import { APIRequestContext, expect, Page, test } from '@playwright/test';
import { expectNoSeriousA11yViolations } from '../support/axe';
import { verificationLink } from '../support/mailpit';

/**
 * AUTH-03 against the real stack: gateway, user-service, PostgreSQL and Mailpit (see
 * playwright.stack.config.mts). Every test uses a new email address.
 */
const PASSWORD = 'Jasmin-de-Carthage-2026';

function newEmail(): string {
  return `amira.${Date.now()}.${Math.floor(Math.random() * 1e6)}@example.tn`;
}

async function register(page: Page, email: string, name = 'Amira B.'): Promise<void> {
  await page.goto('/fr/account/register');
  await page.getByLabel('Nom affiché').fill(name);
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Téléphone').fill('20 123 456');
  await page.getByRole('textbox', { name: 'Mot de passe', exact: true }).fill(PASSWORD);
  await page.getByLabel("J'accepte les conditions d'utilisation").check();
  await page.getByRole('button', { name: 'Créer mon compte' }).click();
  await expect(page).toHaveURL('/fr/account/check-email');
}

/** A verified account that already logged in once, so the welcome screen is not shown again. */
async function existingMember(page: Page, request: APIRequestContext): Promise<string> {
  const email = newEmail();
  await register(page, email);
  await page.goto(await verificationLink(request, email));
  await expect(page.getByRole('heading', { name: 'Votre compte est activé' })).toBeVisible();
  const login = await request.post('http://localhost:8080/api/users/login', {
    data: { email, password: PASSWORD },
  });
  expect(login.status()).toBe(200);
  return email;
}

async function logIn(page: Page, email: string): Promise<void> {
  await page.getByLabel('Email').fill(email);
  await page.getByRole('textbox', { name: 'Mot de passe', exact: true }).fill(PASSWORD);
  await page.getByRole('button', { name: 'Se connecter' }).click();
}

test.describe('account screens on the local stack', () => {
  test('registration, verification, first login with the welcome screen, logout, second login', async ({
    page,
    request,
  }) => {
    const email = newEmail();
    await register(page, email);
    await expect(page.getByTestId('check-email-address')).toHaveText(email);
    await expect(page).not.toHaveURL(/@/);
    await expectNoSeriousA11yViolations(page);

    await page.goto(await verificationLink(request, email));
    await expect(page.getByRole('heading', { name: 'Votre compte est activé' })).toBeVisible();
    await expect(page).toHaveURL('/fr/account/verify-email');
    await page.getByRole('main').getByRole('link', { name: 'Se connecter' }).click();

    await expect(page).toHaveURL('/fr/account/login');
    await logIn(page, email);
    await expect(page.getByRole('heading', { name: 'Une annonce, publiée partout' })).toBeVisible();
    await expect(page.getByText('Bienvenue, Amira B.')).toBeVisible();
    await expectNoSeriousA11yViolations(page);

    await page.getByTestId('welcome-later').click();
    await expect(page).toHaveURL('/fr');
    await expect(page.getByTestId('user-name')).toHaveText('Amira B.');

    // The session survives a reload: the refresh cookie restores it.
    await page.reload();
    await expect(page.getByTestId('user-name')).toHaveText('Amira B.');

    await page.getByTestId('user-menu').click();
    await page.getByRole('button', { name: 'Se déconnecter' }).click();
    await expect(page).toHaveURL('/fr');
    await expect(page.getByTestId('user-menu')).toHaveCount(0);
    await page.reload();
    await expect(page.getByTestId('user-menu')).toHaveCount(0);

    await page.goto('/fr/account/login');
    await logIn(page, email);
    await expect(page).toHaveURL('/fr');
    await expect(page.getByTestId('user-name')).toHaveText('Amira B.');
    await expect(page.getByTestId('welcome-page')).toHaveCount(0);
  });

  test('a used verification link offers a new one', async ({ page, request }) => {
    const email = newEmail();
    await register(page, email);
    const link = await verificationLink(request, email);
    await page.goto(link);
    await expect(page.getByRole('heading', { name: 'Votre compte est activé' })).toBeVisible();

    await page.goto(link);
    await expect(page.getByRole('heading', { name: "Ce lien n'est plus valable" })).toBeVisible();
    await expect(page.getByLabel('Email')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Recevoir un nouveau lien' })).toBeVisible();
    await expectNoSeriousA11yViolations(page);
  });

  test('a protected page asks for a login, then opens', async ({ page, request }) => {
    const email = await existingMember(page, request);

    await page.goto('/fr/publish/new');
    await expect(page).toHaveURL('/fr/account/login?returnUrl=%2Ffr%2Fpublish%2Fnew');
    await logIn(page, email);
    await expect(page).toHaveURL('/fr/publish/new');
    await expect(page.getByRole('heading', { level: 1, name: 'Catégorie' })).toBeVisible();
  });

  test('a wrong password shows one general message, focused', async ({ page, request }) => {
    const email = await existingMember(page, request);

    await page.goto('/fr/account/login');
    await page.getByLabel('Email').fill(email);
    await page
      .getByRole('textbox', { name: 'Mot de passe', exact: true })
      .fill('Not-the-right-one-2026');
    await page.getByRole('button', { name: 'Se connecter' }).click();

    const alert = page.getByRole('alert');
    await expect(alert).toHaveText('Email ou mot de passe incorrect.');
    await expect(page.getByTestId('login-error')).toBeFocused();
    await expect(page.locator('[aria-invalid="true"]')).toHaveCount(0);
  });

  test('the login page is in Arabic and mirrored, the email typed left to right', async ({
    page,
  }) => {
    await page.goto('/ar/account/login');
    await expect(page.getByRole('heading', { name: 'تسجيل الدخول' })).toBeVisible();
    await expect(page.locator('html')).toHaveAttribute('dir', 'rtl');
    const email = page.getByLabel('البريد الإلكتروني');
    expect(await email.evaluate((input) => getComputedStyle(input).direction)).toBe('ltr');
    await expectNoSeriousA11yViolations(page);
  });
});
