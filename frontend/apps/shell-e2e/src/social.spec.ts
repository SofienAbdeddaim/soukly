import { expect, Page, test } from '@playwright/test';
import { expectNoSeriousA11yViolations } from './support/axe';
import { fakeAccountApi } from './support/fake-api';
import { connection, FakeSocialApi, fakeSocialApi } from './support/fake-social-api';

/**
 * "My networks" of SOC-01 with faked user-service and social-publisher: connection of a Facebook
 * Page through the return route `/oauth/facebook/callback`, choice among several Pages, failures and
 * disconnection. Facebook is never called (support/fake-social-api.ts).
 */
test.use({ viewport: { width: 390, height: 844 } });

async function open(
  page: Page,
  options: Parameters<typeof fakeSocialApi>[1] = {},
  url = '/fr/account/social',
): Promise<FakeSocialApi> {
  await fakeAccountApi(page, { signedIn: true });
  const api = await fakeSocialApi(page, options);
  await page.goto(url);
  return api;
}

test.describe('my networks', () => {
  test('a member who manages one Page connects it', async ({ page }) => {
    const api = await open(page);
    await expect(page.getByRole('heading', { level: 1, name: 'Mes réseaux' })).toBeVisible();
    await expect(page.getByTestId('facebook-card')).toContainText('Non reliée');
    await expect(page.getByTestId('instagram-card')).toContainText('Bientôt disponible');
    await expectNoSeriousA11yViolations(page);

    await page.getByRole('button', { name: 'Relier' }).click();

    await expect(page).toHaveURL('/fr/account/social');
    await expect(page.getByTestId('facebook-connected')).toContainText('Page reliée.');
    const card = page.getByTestId('facebook-card');
    await expect(card).toHaveAttribute('data-state', 'connected');
    await expect(card).toContainText('Sfax Auto Occasion');
    await expect(card).toContainText('reliée le 12 oct.');
    expect(api.calls.find((call) => call.path === '/api/social/facebook/callback')?.body).toEqual({
      code: 'fake-code',
      state: 'fake-state',
      error: null,
    });
  });

  test('a member who manages three Pages chooses one', async ({ page }) => {
    const api = await open(page, { scenario: 'several-pages' });
    await page.getByRole('button', { name: 'Relier' }).click();

    await expect(page).toHaveURL('/fr/account/social/facebook/page');
    await expect(page.getByRole('heading', { level: 1, name: 'Choisir une Page' })).toBeVisible();
    await expect(page.getByTestId('page-option')).toHaveCount(3);
    await expect(page.getByRole('radio', { name: /Sfax Auto Occasion/ })).toBeChecked();
    await expectNoSeriousA11yViolations(page);

    await page.getByRole('radio', { name: /Atelier Jasmin/ }).check();
    await page.getByRole('button', { name: 'Relier cette Page' }).click();

    await expect(page).toHaveURL('/fr/account/social');
    await expect(page.getByTestId('facebook-card')).toContainText('Atelier Jasmin');
    expect(api.calls.find((call) => call.path === '/api/social/facebook/page')?.body).toEqual({
      pageId: '1002',
    });
  });

  test('cancelling on Facebook explains that nothing was connected', async ({ page }) => {
    await open(page, { scenario: 'cancelled' });
    await page.getByRole('button', { name: 'Relier' }).click();

    await expect(page).toHaveURL('/fr/account/social');
    const failure = page.getByTestId('facebook-failure');
    await expect(failure).toContainText('Vous avez annulé la connexion');
    await expect(page.getByTestId('facebook-card')).toContainText('Non reliée');
    await expectNoSeriousA11yViolations(page);

    await page.getByRole('button', { name: 'Plus tard' }).click();
    await expect(failure).toBeHidden();
  });

  test('a declined permission asks for it again', async ({ page }) => {
    const api = await open(page, { scenario: 'permission-missing' });
    await page.getByRole('button', { name: 'Relier' }).click();

    await expect(page.getByTestId('facebook-failure')).toContainText(
      "Soukly a besoin de l'autorisation de publier sur votre Page",
    );
    api.scenario = 'one-page';
    await page.getByRole('button', { name: 'Réessayer' }).click();
    await expect(page.getByTestId('facebook-card')).toHaveAttribute('data-state', 'connected');
  });

  test('a member without a Page is told how to create one', async ({ page }) => {
    await open(page, { scenario: 'no-page' });
    await page.getByRole('button', { name: 'Relier' }).click();

    await expect(page.getByTestId('facebook-failure')).toContainText(
      'Vous ne gérez aucune Page Facebook',
    );
    await expect(
      page.getByRole('link', { name: 'Comment créer une Page Facebook ?' }),
    ).toHaveAttribute('href', 'https://www.facebook.com/pages/create');
  });

  test('disconnecting asks for a confirmation', async ({ page }) => {
    const api = await open(page, { facebook: connection() });
    const card = page.getByTestId('facebook-card');
    await expect(card).toContainText('Sfax Auto Occasion');

    await card.getByRole('button', { name: 'Déconnecter' }).click();
    const dialog = page.getByRole('alertdialog', { name: 'Déconnecter cette Page ?' });
    await expect(dialog).toContainText('Sfax Auto Occasion');
    await expectNoSeriousA11yViolations(page);
    await dialog.getByRole('button', { name: 'Déconnecter' }).click();

    await expect(page.getByText('Page déconnectée.')).toBeVisible();
    await expect(card).toContainText('Non reliée');
    expect(api.calls.some((call) => call.method === 'DELETE')).toBe(true);
  });

  test('a visitor without a session who comes back from Facebook signs in first', async ({
    page,
  }) => {
    await fakeAccountApi(page, { signedIn: false });
    const api = await fakeSocialApi(page);
    await page.goto('/oauth/facebook/callback?code=fake-code&state=fake-state');

    await expect(page).toHaveURL(/\/fr\/account\/login\?returnUrl=%2Ffr%2Faccount%2Fsocial$/);
    expect(api.calls).toEqual([]);
  });

  test('in Arabic, the page is right to left and Facebook brings the member back to /ar', async ({
    page,
  }) => {
    const api = await open(page, {}, '/ar/account/social');
    await expect(page.locator('html')).toHaveAttribute('dir', 'rtl');
    await expect(page.getByRole('heading', { level: 1, name: 'شبكاتي' })).toBeVisible();
    await expectNoSeriousA11yViolations(page);

    await page.getByRole('button', { name: 'ربط' }).click();

    await expect(page).toHaveURL('/ar/account/social');
    await expect(page.locator('html')).toHaveAttribute('dir', 'rtl');
    await expect(page.getByTestId('facebook-card')).toContainText('Sfax Auto Occasion');
    expect(api.lang).toBe('ar');
  });
});
