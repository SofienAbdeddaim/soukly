import { expect, Page, test } from '@playwright/test';
import { fakeAccountApi } from './support/fake-api';

async function hasHorizontalOverflow(page: Page): Promise<boolean> {
  return page.evaluate(
    () => document.documentElement.scrollWidth > document.documentElement.clientWidth,
  );
}

test.describe('languages', () => {
  test.describe('browser in Arabic', () => {
    test.use({ locale: 'ar-TN' });

    test('"/" redirects to /ar, right-to-left, with the header and footer in Arabic', async ({
      page,
    }) => {
      await page.goto('/');

      await expect(page).toHaveURL('/ar');
      await expect(page.locator('html')).toHaveAttribute('lang', 'ar');
      await expect(page.locator('html')).toHaveAttribute('dir', 'rtl');
      const header = page.getByRole('banner');
      await expect(header.getByRole('link', { name: 'تسجيل الدخول' })).toBeVisible();
      await expect(header.getByRole('link', { name: 'أضف إعلانًا' })).toBeVisible();
      await expect(
        page.getByRole('contentinfo').getByRole('link', { name: 'الخصوصية' }),
      ).toBeVisible();
      await expect(page.getByRole('heading', { level: 1 })).toHaveText('السوق كلّه، قريب منك');
      expect(await hasHorizontalOverflow(page)).toBe(false);
    });

    test('mobile (390px): no horizontal overflow in Arabic', async ({ page }) => {
      await page.setViewportSize({ width: 390, height: 844 });
      await page.goto('/ar');
      await expect(
        page.getByTestId('bottom-nav').getByRole('link', { name: 'الرئيسية' }),
      ).toBeVisible();

      expect(await hasHorizontalOverflow(page)).toBe(false);
    });
  });

  test.describe('browser in German only', () => {
    test.use({ locale: 'de-DE' });

    test('"/" redirects to /fr', async ({ page }) => {
      await page.goto('/');

      await expect(page).toHaveURL('/fr');
      await expect(page.locator('html')).toHaveAttribute('lang', 'fr');
      await expect(page.locator('html')).toHaveAttribute('dir', 'ltr');
    });
  });

  test('an unknown language prefix is replaced with French', async ({ page }) => {
    await fakeAccountApi(page, { signedIn: true });
    await page.goto('/de/publish/new');

    await expect(page).toHaveURL('/fr/publish/new');
    await expect(page.getByRole('heading', { level: 1, name: 'Catégorie' })).toBeVisible();
  });

  test('a path without prefix gets one, with its query parameters', async ({ page }) => {
    await page.goto('/account/login?next=%2Fpublish');

    await expect(page).toHaveURL('/fr/account/login?next=%2Fpublish');
  });

  test('the switcher changes the prefix without reloading, and the choice is remembered', async ({
    page,
  }) => {
    await page.goto('/fr/account/login');
    await expect(page.getByTestId('login-form')).toBeVisible();
    let documentLoads = 0;
    page.on('load', () => documentLoads++);

    await page.getByTestId('language-switcher').click();
    await page.getByRole('link', { name: 'English' }).click();

    await expect(page).toHaveURL('/en/account/login');
    await expect(page.locator('html')).toHaveAttribute('lang', 'en');
    await expect(page.locator('html')).toHaveAttribute('dir', 'ltr');
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Log in');
    await expect(page.getByRole('banner').getByRole('link', { name: 'Log in' })).toBeVisible();
    expect(documentLoads).toBe(0);

    await page.goto('/');
    await expect(page).toHaveURL('/en');
  });

  test('the switcher works with the keyboard and switches to right-to-left', async ({ page }) => {
    await page.goto('/fr/help');
    const toggle = page.getByTestId('language-switcher');
    await expect(toggle).toContainText('Français');

    await toggle.focus();
    await page.keyboard.press('Enter');
    await expect(toggle).toHaveAttribute('aria-expanded', 'true');
    await page.keyboard.press('Tab');
    await page.keyboard.press('Tab');
    await expect(page.getByRole('link', { name: 'العربية' })).toBeFocused();
    await page.keyboard.press('Enter');

    await expect(page).toHaveURL('/ar/help');
    await expect(page.locator('html')).toHaveAttribute('dir', 'rtl');
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('المساعدة');
    await expect(toggle).toContainText('العربية');
    expect(await hasHorizontalOverflow(page)).toBe(false);

    await toggle.click();
    await page.keyboard.press('Escape');
    await expect(toggle).toHaveAttribute('aria-expanded', 'false');
    await expect(toggle).toBeFocused();
  });

  test('the translations of a remote are downloaded from the remote, when one of its pages opens', async ({
    page,
  }) => {
    const publishFiles: string[] = [];
    page.on('request', (request) => {
      if (request.url().includes('/i18n/publish/')) {
        publishFiles.push(request.url());
      }
    });
    await fakeAccountApi(page, { signedIn: true });
    await page.goto('/ar');
    await expect(page.getByTestId('catalog-home')).toBeVisible();
    expect(publishFiles).toEqual([]);

    await page.getByRole('banner').getByRole('link', { name: 'أضف إعلانًا' }).click();

    await expect(page).toHaveURL('/ar/publish/new');
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('الصنف');
    expect(publishFiles).toContain('http://localhost:4203/i18n/publish/ar.json');
  });

  test('every page declares its three language variants (hreflang)', async ({ page }) => {
    await page.goto('/fr/account/login');
    await expect(page.getByTestId('login-form')).toBeVisible();

    const alternates = await page
      .locator('link[rel="alternate"][hreflang]')
      .evaluateAll((links) =>
        links.map((link) => [link.getAttribute('hreflang'), link.getAttribute('href')]),
      );
    expect(alternates).toEqual([
      ['fr', 'http://localhost:4200/fr/account/login'],
      ['ar', 'http://localhost:4200/ar/account/login'],
      ['en', 'http://localhost:4200/en/account/login'],
      ['x-default', 'http://localhost:4200/fr/account/login'],
    ]);
  });
});
