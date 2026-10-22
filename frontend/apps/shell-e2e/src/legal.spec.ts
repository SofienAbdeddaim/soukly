import { expect, test } from '@playwright/test';
import { expectNoSeriousA11yViolations } from './support/axe';
import { fakeAccountApi } from './support/fake-api';

/**
 * Legal pages of LEG-01: static HTML generated from frontend/legal by tools/build-legal and served
 * before the shell (by the dev server here, by Caddy in the front-end image).
 */
const TITLES: Record<string, Record<string, string>> = {
  fr: {
    terms: "Conditions d'utilisation",
    privacy: 'Politique de confidentialité',
    'posting-rules': 'Règles de publication',
    notice: 'Mentions légales',
    'data-deletion': 'Supprimer mes données',
  },
  ar: {
    terms: 'شروط الاستخدام',
    privacy: 'سياسة الخصوصية',
    'posting-rules': 'قواعد النشر',
    notice: 'البيانات القانونية',
    'data-deletion': 'حذف بياناتي',
  },
  en: {
    terms: 'Terms of use',
    privacy: 'Privacy policy',
    'posting-rules': 'Posting rules',
    notice: 'Legal notice',
    'data-deletion': 'Delete my data',
  },
};
const DRAFT = {
  fr: 'Texte provisoire, en cours de relecture juridique.',
  ar: 'نص مؤقت قيد المراجعة القانونية.',
  en: 'Provisional text, under legal review.',
};

test.describe('legal pages', () => {
  test('the 5 pages exist in the 3 languages, in the right direction and marked as drafts', async ({
    page,
  }) => {
    for (const [lang, titles] of Object.entries(TITLES)) {
      for (const [slug, title] of Object.entries(titles)) {
        const response = await page.goto(`/${lang}/legal/${slug}`);
        expect(response?.status(), `/${lang}/legal/${slug}`).toBe(200);
        await expect(page.locator('html')).toHaveAttribute('lang', lang);
        await expect(page.locator('html')).toHaveAttribute('dir', lang === 'ar' ? 'rtl' : 'ltr');
        await expect(page.getByRole('heading', { level: 1 })).toHaveText(title);
        await expect(page.getByRole('note')).toHaveText(DRAFT[lang as keyof typeof DRAFT]);
      }
    }
  });

  test.describe('without JavaScript', () => {
    test.use({ javaScriptEnabled: false });

    test('the privacy policy is a complete page with all its sections', async ({ page }) => {
      await page.goto('/fr/legal/privacy');

      await expect(page.getByRole('heading', { level: 1 })).toHaveText(
        'Politique de confidentialité',
      );
      await expect(page.getByRole('heading', { level: 2 })).toHaveText([
        'Données collectées',
        'Finalités',
        'Durées de conservation',
        'Partage avec Facebook et Instagram',
        'Hébergement et transferts',
        'Vos droits',
        'Cookies et stockage local',
        'Contact',
      ]);
      await expect(page.getByRole('contentinfo').getByRole('link')).toHaveCount(5);
    });

    test('the data deletion page explains the account deletion and gives the contact address', async ({
      page,
    }) => {
      await page.goto('/fr/legal/data-deletion');

      await expect(page.getByRole('main')).toContainText('Supprimer mon compte');
      await expect(page.getByRole('link', { name: 'Mon profil' })).toHaveAttribute(
        'href',
        '/fr/account/profile',
      );
      await expect(page.getByRole('link', { name: 'privacy@localhost' })).toHaveAttribute(
        'href',
        'mailto:privacy@localhost',
      );
    });
  });

  test('the language links lead to the same page in the two other languages', async ({ page }) => {
    await page.goto('/ar/legal/terms');
    const languages = page.getByRole('navigation', { name: 'تغيير اللغة' });

    await expect(languages.getByRole('link', { name: 'Français' })).toHaveAttribute(
      'href',
      '/fr/legal/terms',
    );
    await languages.getByRole('link', { name: 'English' }).click();

    await expect(page).toHaveURL('/en/legal/terms');
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Terms of use');
  });

  test('the table of contents lists every section and reaches it with the keyboard', async ({
    page,
  }) => {
    await page.goto('/fr/legal/terms');
    const toc = page.locator('details.toc');
    const sections = await page
      .locator('.prose h2')
      .evaluateAll((headings) => headings.map((heading) => [heading.id, heading.textContent]));

    expect(sections.length).toBe(9);
    await expect(toc.getByRole('link')).toHaveText(sections.map(([, title]) => title ?? ''));
    expect(
      await toc
        .getByRole('link')
        .evaluateAll((links) => links.map((link) => link.getAttribute('href'))),
    ).toEqual(sections.map(([id]) => `#${id}`));

    await toc.getByRole('link', { name: 'Droit applicable' }).focus();
    await page.keyboard.press('Enter');
    await expect(page).toHaveURL('/fr/legal/terms#governing-law');
    await expect(page.locator('#governing-law')).toBeInViewport();

    await toc.locator('summary').focus();
    await page.keyboard.press('Enter');
    await expect(toc).not.toHaveAttribute('open');
  });

  test('the footer of the shell links to the 5 legal pages in the current language', async ({
    page,
  }) => {
    await page.goto('/fr');
    await expect(page.getByTestId('catalog-home')).toBeVisible();
    const footer = page.getByRole('contentinfo');

    await expect(footer.getByRole('link')).toHaveText([
      'Conditions',
      'Confidentialité',
      'Règles de publication',
      'Mentions légales',
      'Supprimer mes données',
    ]);
    expect(
      await footer
        .getByRole('link')
        .evaluateAll((links) => links.map((link) => link.getAttribute('href'))),
    ).toEqual([
      '/fr/legal/terms',
      '/fr/legal/privacy',
      '/fr/legal/posting-rules',
      '/fr/legal/notice',
      '/fr/legal/data-deletion',
    ]);

    await footer.getByRole('link', { name: 'Confidentialité' }).click();
    await expect(page).toHaveURL('/fr/legal/privacy');
    await expect(page.getByRole('heading', { level: 1 })).toHaveText(
      'Politique de confidentialité',
    );
  });

  test('the terms checkbox of the registration links to the terms and the privacy policy', async ({
    page,
  }) => {
    await fakeAccountApi(page);
    await page.goto('/fr/account/register');
    const checkbox = page.locator('ui-checkbox');

    await expect(checkbox.getByRole('link', { name: "conditions d'utilisation" })).toHaveAttribute(
      'href',
      '/fr/legal/terms',
    );
    const [popup] = await Promise.all([
      page.waitForEvent('popup'),
      checkbox.getByRole('link', { name: 'politique de confidentialité' }).click(),
    ]);
    await expect(popup).toHaveURL('/fr/legal/privacy');
    await expect(popup.getByRole('heading', { level: 1 })).toHaveText(
      'Politique de confidentialité',
    );
  });

  test('an unknown legal page shows the 404 page of the shell', async ({ page }) => {
    await page.goto('/fr/legal/inexistant');

    await expect(page.getByTestId('not-found')).toBeVisible();
  });

  for (const lang of ['fr', 'ar', 'en']) {
    test(`no serious accessibility violation (${lang})`, async ({ page }) => {
      await page.goto(`/${lang}/legal/privacy`);
      await expectNoSeriousA11yViolations(page);
    });
  }
});
