import { expect, Page, test } from '@playwright/test';

async function hasHorizontalOverflow(page: Page): Promise<boolean> {
  return page.evaluate(
    () => document.documentElement.scrollWidth > document.documentElement.clientWidth,
  );
}

async function forceRtl(page: Page): Promise<void> {
  await page.evaluate(() => {
    document.documentElement.dir = 'rtl';
  });
}

test.describe('layout', () => {
  test('the skip link is the first focusable element and moves the focus to the content', async ({
    page,
  }) => {
    await page.goto('/fr');
    await expect(page.getByTestId('catalog-home')).toBeVisible();

    await page.keyboard.press('Tab');
    const skipLink = page.getByRole('link', { name: 'Aller au contenu' });
    await expect(skipLink).toBeFocused();
    await page.keyboard.press('Enter');

    await expect(page.getByRole('main')).toBeFocused();
    await expect(page).toHaveURL('/fr');
  });

  test('desktop: no bottom bar, header actions visible, mirrored in RTL', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByTestId('catalog-home')).toBeVisible();

    await expect(page.getByTestId('bottom-nav')).toBeHidden();
    await expect(page.getByTestId('header-actions')).toBeVisible();

    await forceRtl(page);
    const logo = await page.getByRole('banner').getByRole('link', { name: 'Soukly' }).boundingBox();
    const viewport = page.viewportSize();
    expect(logo && viewport && logo.x > viewport.width / 2).toBe(true);
    expect(await hasHorizontalOverflow(page)).toBe(false);
  });

  test.describe('mobile (390px)', () => {
    test.use({ viewport: { width: 390, height: 844 } });

    test('shows the bottom bar with "Déposer" highlighted, without "Favoris"', async ({ page }) => {
      await page.goto('/');
      await expect(page.getByTestId('catalog-home')).toBeVisible();

      const bottomNav = page.getByTestId('bottom-nav');
      await expect(bottomNav).toBeVisible();
      await expect(page.getByTestId('header-actions')).toBeHidden();
      await expect(bottomNav.getByRole('link', { name: 'Accueil' })).toHaveAttribute(
        'aria-current',
        'page',
      );
      await expect(bottomNav.getByRole('link', { name: 'Favoris' })).toHaveCount(0);

      // Posting an ad needs a session: a visitor is asked to log in first (AUTH-03).
      await bottomNav.getByTestId('bottom-nav-publish').click();
      await expect(page).toHaveURL('/fr/account/login?returnUrl=%2Ffr%2Fpublish%2Fnew');
      await expect(page.getByTestId('login-form')).toBeVisible();
    });

    test('has no horizontal overflow in RTL', async ({ page }) => {
      await page.goto('/');
      await expect(page.getByTestId('catalog-home')).toBeVisible();

      await forceRtl(page);

      expect(await hasHorizontalOverflow(page)).toBe(false);
      await expect(page.getByTestId('bottom-nav')).toBeVisible();
    });
  });
});
