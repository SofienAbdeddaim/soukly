import { expect, Page, test } from '@playwright/test';
import { expectNoSeriousA11yViolations } from './support/axe';
import { fakeAccountApi, session } from './support/fake-api';
import {
  arabicDetail,
  fakeDetailApi,
  GONE_ID,
  OWN_ID,
  PUBLIC_ID,
  publicDetail,
} from './support/fake-detail-api';
import { fakeListingApi } from './support/fake-listing-api';
import { fakeSearchApi } from './support/fake-search-api';

/**
 * Listing page of CATA-03 with a faked back end: account (support/fake-api.ts), referentials
 * (support/fake-listing-api.ts), search (support/fake-search-api.ts) and the listing itself
 * (support/fake-detail-api.ts).
 */
const PASSWORD = 'correct-horse-battery';

async function fakeBackEnd(page: Page, signedIn = false, listings?: Record<string, unknown>) {
  const account = await fakeAccountApi(page, {
    signedIn,
    logins: { [PASSWORD]: { status: 200, body: session() } },
  });
  await fakeListingApi(page);
  await fakeSearchApi(page);
  const detail = await fakeDetailApi(page, listings);
  return { account, detail };
}

test.describe('listing page, mobile (390px)', () => {
  test.use({ viewport: { width: 390, height: 844 } });

  test('nominal: gallery, price, negotiable, facts, description, place and seller', async ({
    page,
  }) => {
    await fakeBackEnd(page);
    const answer = page.waitForResponse(`**/api/listings/${PUBLIC_ID}`);
    await page.goto(`/fr/listings/${PUBLIC_ID}`);

    const body = await (await answer).text();
    expect(body).not.toContain('+216');
    expect(body).not.toContain('@');
    await expect(page.getByTestId('gallery-counter')).toHaveText('1 / 4');
    await expect(page.getByRole('img', { name: /^Photo 1 sur 4 : Peugeot 208/u })).toBeVisible();
    await expect(page.getByTestId('price')).toHaveText(/^32\s000\sDT/u);
    await expect(page.getByTestId('negotiable')).toHaveText('Négociable');
    await expect(page.getByTestId('facts')).toContainText('Diesel');
    await expect(page.getByTestId('facts')).toContainText('2019');
    await expect(page.getByTestId('place')).toContainText('Sfax, Sakiet Ezzit');
    await expect(page.getByTestId('seller')).toContainText('Mohamed B.');
    await expect(page.getByTestId('contact-bar')).toBeVisible();
    await expect(page).toHaveTitle(
      /^Peugeot 208 diesel, première main · 32\s000\sDT · Sfax · Soukly$/u,
    );
    await expectNoSeriousA11yViolations(page);
  });

  test('description: the markup shows as text and no script runs', async ({ page }) => {
    await fakeBackEnd(page);
    const dialogs: string[] = [];
    page.on('dialog', (dialog) => {
      dialogs.push(dialog.message());
      void dialog.dismiss();
    });
    await page.goto(`/fr/listings/${PUBLIC_ID}`);

    const description = page.getByTestId('description');
    await expect(description).toContainText('<b>Pas de</b> <script>échange</script>.');
    await expect(description.locator('b, script')).toHaveCount(0);
    expect(dialogs).toEqual([]);
  });

  test('gallery: swipe counter and full-screen viewer with the keyboard', async ({ page }) => {
    await fakeBackEnd(page);
    await page.goto(`/fr/listings/${PUBLIC_ID}`);

    await page.getByRole('button', { name: 'Agrandir la photo 1 sur 4' }).click();
    const viewer = page.getByRole('dialog');
    await expect(viewer.getByTestId('viewer-counter')).toHaveText('1 / 4');
    await page.keyboard.press('ArrowRight');
    await expect(viewer.getByTestId('viewer-counter')).toHaveText('2 / 4');
    await expectNoSeriousA11yViolations(page);
    await page.keyboard.press('Escape');
    await expect(viewer).toBeHidden();
    await expect(page.getByTestId('gallery-counter')).toHaveText('2 / 4');
  });

  test('visitor: login with a return to the listing, then the number shows', async ({ page }) => {
    const { detail } = await fakeBackEnd(page);
    await page.goto(`/fr/listings/${PUBLIC_ID}`);

    await page.getByTestId('show-number').click();
    await expect(page).toHaveURL(
      `/fr/account/login?returnUrl=%2Ffr%2Flistings%2F${PUBLIC_ID}%3Fcontact%3D1`,
    );
    expect(detail.contacts).toEqual([]);
    await page.getByLabel('Email').fill('amira@example.tn');
    await page.getByRole('textbox', { name: 'Mot de passe', exact: true }).fill(PASSWORD);
    await page.getByRole('button', { name: 'Se connecter' }).click();

    await expect(page).toHaveURL(`/fr/listings/${PUBLIC_ID}`);
    const sheet = page.getByTestId('contact-sheet');
    await expect(sheet.getByTestId('contact-phone')).toHaveText('+216 20 123 456');
    await expect(sheet.getByTestId('contact-call')).toBeVisible();
    await expect(sheet.getByTestId('contact-whatsapp')).toBeVisible();
    expect(detail.contacts).toHaveLength(1);
  });

  test('member: the number in a sheet, never cached, WhatsApp with the title', async ({ page }) => {
    const { detail } = await fakeBackEnd(page, true);
    await page.goto(`/fr/listings/${PUBLIC_ID}`);

    const answer = page.waitForResponse(`**/api/listings/${PUBLIC_ID}/contact`);
    await page.getByTestId('show-number').click();
    const response = await answer;
    expect(response.status()).toBe(200);
    expect(response.headers()['cache-control']).toBe('no-store');
    expect(detail.contacts[0].authorization).toBe('Bearer access-token');

    const sheet = page.getByRole('dialog');
    await expect(sheet.getByTestId('contact-call')).toHaveAttribute('href', 'tel:+21620123456');
    const whatsapp = await sheet.getByTestId('contact-whatsapp').getAttribute('href');
    expect(whatsapp).toMatch(/^https:\/\/wa\.me\/21620123456\?text=/u);
    expect(decodeURIComponent(whatsapp?.split('text=')[1] ?? '')).toContain(
      'Peugeot 208 diesel, première main',
    );
    await expect(sheet.getByTestId('contact-safety')).toBeVisible();
    await expectNoSeriousA11yViolations(page);
  });

  test('limit: the message of the daily limit, no sheet', async ({ page }) => {
    const { detail } = await fakeBackEnd(page, true);
    detail.limitReached = true;
    await page.goto(`/fr/listings/${PUBLIC_ID}`);

    await page.getByTestId('show-number').click();

    await expect(page.getByTestId('contact-limit')).toBeVisible();
    await expect(page.getByTestId('contact-sheet')).toHaveCount(0);
  });

  test('owner: "This is your listing" and no contact button', async ({ page }) => {
    await fakeBackEnd(page, true);
    await page.goto(`/fr/listings/${OWN_ID}`);

    await expect(page.getByTestId('owner-banner')).toBeVisible();
    await expect(page.getByTestId('show-number')).toHaveCount(0);
  });

  test('expired: "no longer available" and 4 similar listings', async ({ page }) => {
    await fakeBackEnd(page);
    await page.goto(`/fr/listings/${GONE_ID}`);

    await expect(page.getByTestId('listing-gone')).toBeVisible();
    await expect(page.getByTestId('listings').locator(':scope > li')).toHaveCount(4);
    await expect(page.getByTestId('gone-see-all')).toHaveAttribute(
      'href',
      '/fr/c/vehicles.cars?governorate=sfax',
    );
    await expect(page).toHaveTitle(/Soukly$/u);
    await expectNoSeriousA11yViolations(page);
  });

  test('draft of someone else: "Listing not found"', async ({ page }) => {
    await fakeBackEnd(page);
    await page.goto('/fr/listings/44444444-4444-4444-8444-444444444444');

    await expect(page.getByTestId('listing-not-found')).toBeVisible();
  });

  test('Arabic: right to left, price per month, number read from left to right', async ({
    page,
  }) => {
    await fakeBackEnd(page, true, { [PUBLIC_ID]: arabicDetail() });
    await page.goto(`/ar/listings/${PUBLIC_ID}`);

    await expect(page.locator('html')).toHaveAttribute('dir', 'rtl');
    await expect(page.getByTestId('price')).toHaveText(/^\u200f?1\.100\sد\.ت\.\u200f?\s\/\sشهر$/u);
    await page.getByTestId('show-number').click();
    const phone = page.getByTestId('contact-phone');
    await expect(phone).toHaveText('+216 20 123 456');
    await expect(phone.locator('bdi')).toHaveAttribute('dir', 'ltr');
    await expectNoSeriousA11yViolations(page);
  });
});

test.describe('listing page, desktop (1280px)', () => {
  test.use({ viewport: { width: 1280, height: 900 } });

  test('the summary, seller and contact stay in the right column', async ({ page }) => {
    await fakeBackEnd(page, false, { [PUBLIC_ID]: publicDetail() });
    await page.goto(`/fr/listings/${PUBLIC_ID}`);

    const aside = page.getByRole('complementary');
    await expect(aside.getByTestId('price')).toBeVisible();
    await expect(aside.getByTestId('seller')).toBeVisible();
    await expect(aside.getByTestId('show-number')).toBeVisible();
    await expect(page.getByTestId('contact-bar')).toHaveCount(0);
    await expectNoSeriousA11yViolations(page);
  });
});
