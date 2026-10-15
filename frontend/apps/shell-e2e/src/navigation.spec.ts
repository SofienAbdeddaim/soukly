import { expect, test } from '@playwright/test';

test.describe('shell navigation', () => {
  test('shows the layout and the waiting page of mfe-catalog on /fr', async ({ page }) => {
    await page.goto('/fr');

    await expect(page.getByRole('banner')).toBeVisible();
    await expect(page.getByRole('contentinfo')).toBeVisible();
    await expect(page.getByTestId('coming-soon-mfe-catalog')).toBeVisible();
    await expect(page.getByRole('heading', { level: 1 })).toHaveText(
      'Catalogue Soukly, bientôt disponible',
    );
  });

  test('"Se connecter" opens mfe-account without a full page reload', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByTestId('coming-soon-mfe-catalog')).toBeVisible();
    let documentLoads = 0;
    page.on('load', () => documentLoads++);

    await page.getByRole('banner').getByRole('link', { name: 'Se connecter' }).click();

    await expect(page).toHaveURL('/fr/account/login');
    await expect(page.getByTestId('coming-soon-mfe-account')).toBeVisible();
    expect(documentLoads).toBe(0);
  });

  test('"Déposer une annonce" opens mfe-publish', async ({ page }) => {
    await page.goto('/');

    await page.getByRole('banner').getByRole('link', { name: 'Déposer une annonce' }).click();

    await expect(page).toHaveURL('/fr/publish/new');
    await expect(page.getByTestId('coming-soon-mfe-publish')).toBeVisible();
  });

  test('an unknown URL shows the 404 page with a link to the home page', async ({ page }) => {
    await page.goto('/fr/nimporte-quoi');

    await expect(page.getByTestId('not-found')).toBeVisible();
    await page.getByRole('link', { name: "Retour à l'accueil" }).click();
    await expect(page).toHaveURL('/fr');
    await expect(page.getByTestId('coming-soon-mfe-catalog')).toBeVisible();
  });

  test('the footer links open the static pages of the shell', async ({ page }) => {
    await page.goto('/');

    await page.getByRole('contentinfo').getByRole('link', { name: 'Confidentialité' }).click();

    await expect(page).toHaveURL('/fr/privacy');
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Confidentialité');
  });
});
