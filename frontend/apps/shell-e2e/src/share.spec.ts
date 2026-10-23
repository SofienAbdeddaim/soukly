import { expect, Page, test } from '@playwright/test';
import { expectNoSeriousA11yViolations } from './support/axe';
import { fakeAccountApi } from './support/fake-api';
import {
  draft,
  FakeListing,
  FakeListingApi,
  fakeListingApi,
  fakePhoto,
  LISTING_ID,
} from './support/fake-listing-api';
import {
  connection,
  FakeSocialApi,
  fakeSocialApi,
  instagramConnection,
} from './support/fake-social-api';

/**
 * "Share the listing" on the preview of SOC-03, and the switches of "My networks", with faked
 * listing-service and social-publisher (support/fake-listing-api.ts, support/fake-social-api.ts).
 */
test.use({ viewport: { width: 390, height: 844 } });

const COMPLETE = {
  title: 'Peugeot 208 diesel, première main',
  description: "Première main, carnet d'entretien complet chez le concessionnaire.",
  priceMode: 'FIXED',
  price: 32000,
  negotiable: true,
  governorateCode: 'sfax',
  delegationCode: 'sfax.sakiet-ezzit',
  attributes: {
    brand: 'Peugeot',
    year: 2019,
    'mileage-km': 62000,
    fuel: 'diesel',
    gearbox: 'manual',
  },
  photos: [fakePhoto('a', 0), fakePhoto('b', 1), fakePhoto('c', 2), fakePhoto('d', 3)],
};

async function preview(
  page: Page,
  social: Parameters<typeof fakeSocialApi>[1],
  listing: Partial<FakeListing> = {},
  lang = 'fr',
): Promise<{ listings: FakeListingApi; social: FakeSocialApi }> {
  await fakeAccountApi(page, { signedIn: true });
  const listings = await fakeListingApi(page, [draft({ ...COMPLETE, ...listing })]);
  const socialApi = await fakeSocialApi(page, social);
  await page.goto(`/${lang}/publish/${LISTING_ID}/preview`);
  return { listings, social: socialApi };
}

function checkbox(page: Page, name: string | RegExp) {
  return page.getByTestId('share-block').getByRole('checkbox', { name });
}

test.describe('share the listing', () => {
  test('default boxes: the preference of each network, saved in the draft', async ({ page }) => {
    const instagram = { ...instagramConnection(), autoShare: false };
    const { listings } = await preview(page, { facebook: connection(), instagram });

    await expect(checkbox(page, /Sfax Auto Occasion/)).toBeChecked();
    await expect(checkbox(page, /@sfax\.auto/)).not.toBeChecked();
    await expect.poll(() => listings.listings.get(LISTING_ID)?.shareTo).toEqual(['FACEBOOK']);
    expect(listings.listings.get(LISTING_ID)?.shareLanguage).toBe('fr');
    await expect(page.getByTestId('share-block')).toContainText(
      'Publié sur vos réseaux dès que l’annonce est en ligne'.replace('’', "'"),
    );
    await expectNoSeriousA11yViolations(page);
  });

  test('saved choice: Instagram ticked, left, then both ticked again', async ({ page }) => {
    const instagram = { ...instagramConnection(), autoShare: false };
    const { listings } = await preview(page, { facebook: connection(), instagram });
    await expect.poll(() => listings.listings.get(LISTING_ID)?.shareTo).toEqual(['FACEBOOK']);

    await checkbox(page, /@sfax\.auto/).check();
    await expect
      .poll(() => listings.listings.get(LISTING_ID)?.shareTo)
      .toEqual(['FACEBOOK', 'INSTAGRAM']);
    await page.goto('/fr');
    await page.goto(`/fr/publish/${LISTING_ID}/preview`);

    await expect(checkbox(page, /Sfax Auto Occasion/)).toBeChecked();
    await expect(checkbox(page, /@sfax\.auto/)).toBeChecked();
  });

  test('the post text, with the cover and the number of photos', async ({ page }) => {
    const { social } = await preview(page, { facebook: connection() }, { shareTo: ['FACEBOOK'] });

    await page.getByText('Voir le texte du post').click();

    const text = page.getByTestId('share-caption-text');
    await expect(text).toContainText('32 000 DT · Négociable');
    await expect(text).toContainText('#Soukly #Sfax #Voitures');
    await expect(text).toContainText(`/fr/listings/${LISTING_ID}`);
    await expect(page.getByTestId('share-caption')).toContainText('Avec vos 4 photos');
    const body = social.calls.find((call) => call.path === '/api/social/caption-preview')?.body;
    expect(body).toMatchObject({ language: 'fr', priceTnd: 32000, categoryCode: 'vehicles.cars' });
    expect(JSON.stringify(body)).not.toMatch(/phone|email|displayName/);
    await expectNoSeriousA11yViolations(page);
  });

  test('Instagram alone: the post text ends with the reference, without a link', async ({
    page,
  }) => {
    const { social } = await preview(
      page,
      { instagram: instagramConnection() },
      { shareTo: ['INSTAGRAM'], shortRef: '8F3C2K9A' },
    );

    await page.getByText('Voir le texte du post').click();

    const text = page.getByTestId('share-caption-text');
    await expect(text).toContainText('Annonce complète sur');
    await expect(text).toContainText('/r/8F3C2K9A');
    await expect(text).not.toContainText('http');
    await expect(page.getByTestId('share-caption-instagram-note')).toHaveCount(0);
    const body = social.calls.find((call) => call.path === '/api/social/caption-preview')?.body;
    expect(body).toMatchObject({ network: 'INSTAGRAM', shortRef: '8F3C2K9A' });
  });

  test('both networks: the Facebook text and a word on Instagram', async ({ page }) => {
    await preview(
      page,
      { facebook: connection(), instagram: instagramConnection() },
      { shareTo: ['FACEBOOK', 'INSTAGRAM'] },
    );

    await page.getByText('Voir le texte du post').click();

    await expect(page.getByTestId('share-caption-text')).toContainText(
      `/fr/listings/${LISTING_ID}`,
    );
    await expect(page.getByTestId('share-caption-instagram-note')).toContainText(
      'Sur Instagram, le lien est remplacé par la référence',
    );
    await expectNoSeriousA11yViolations(page);
  });

  test('the post text unavailable offers to try again', async ({ page }) => {
    const { social } = await preview(
      page,
      { facebook: connection(), captionDown: true },
      { shareTo: ['FACEBOOK'] },
    );

    await page.getByText('Voir le texte du post').click();
    await expect(page.getByTestId('share-caption-error')).toContainText('Texte indisponible.');
    social.captionDown = false;
    await page
      .getByTestId('share-caption-error')
      .getByRole('button', { name: 'Réessayer' })
      .click();

    await expect(page.getByTestId('share-caption-text')).toContainText('#Soukly');
  });

  test('a network to connect again is unticked, disabled, with a link', async ({ page }) => {
    await preview(page, {
      facebook: connection(),
      instagram: { ...instagramConnection(), status: 'NEEDS_RECONNECT' },
    });

    const box = checkbox(page, /@sfax\.auto/);
    await expect(box).not.toBeChecked();
    await expect(box).toBeDisabled();
    await expect(page.getByTestId('share-block')).toContainText('à relier à nouveau');
    await page.getByRole('link', { name: 'Relier à nouveau Instagram' }).click();

    await expect(page).toHaveURL('/fr/account/social');
    await page.getByTestId('social-return').click();
    await expect(page).toHaveURL(`/fr/publish/${LISTING_ID}/preview`);
  });

  test('no network: the block asks to connect one', async ({ page }) => {
    const { listings } = await preview(page, {});

    await expect(page.getByTestId('share-none')).toContainText(
      'Reliez Facebook ou Instagram pour partager vos annonces',
    );
    await expect(page.getByTestId('share-block').getByRole('checkbox')).toHaveCount(0);
    expect(listings.patches).toEqual([]);
  });

  test.describe('in Arabic', () => {
    test('the block and the post text are in Arabic, right to left', async ({ page }) => {
      await preview(page, { facebook: connection() }, { shareTo: ['FACEBOOK'] }, 'ar');

      await expect(page.locator('html')).toHaveAttribute('dir', 'rtl');
      await expect(page.getByTestId('share-block')).toContainText('مشاركة الإعلان');
      await page.getByText('عرض نص المنشور').click();
      const text = page.getByTestId('share-caption-text');
      await expect(text).toContainText('32.000 د.ت.');
      await expect(text).toContainText('#Soukly #صفاقس #سيارات');
      await expectNoSeriousA11yViolations(page);
    });
  });
});

test.describe('tick by default on My networks', () => {
  test('turning Facebook off leaves new drafts unticked, chosen drafts unchanged', async ({
    page,
  }) => {
    await fakeAccountApi(page, { signedIn: true });
    const listings = await fakeListingApi(page, [
      draft({ ...COMPLETE }),
      draft({ ...COMPLETE, id: 'b2f1c4d8-7e6a-4b3c-9d2e-1f0a8b7c6d5e', shareTo: ['FACEBOOK'] }),
    ]);
    const social = await fakeSocialApi(page, { facebook: connection() });
    await page.goto('/fr/account/social');

    const toggle = page.getByRole('switch', {
      name: 'Cocher par défaut pour chaque nouvelle annonce',
    });
    await expect(toggle).toHaveAttribute('aria-checked', 'true');
    await expectNoSeriousA11yViolations(page);
    await toggle.click();
    await expect(toggle).toHaveAttribute('aria-checked', 'false');
    expect(social.facebook?.autoShare).toBe(false);

    await page.goto(`/fr/publish/${LISTING_ID}/preview`);
    await expect(checkbox(page, /Sfax Auto Occasion/)).not.toBeChecked();
    await expect.poll(() => listings.listings.get(LISTING_ID)?.shareTo).toEqual([]);
    await page.goto('/fr/publish/b2f1c4d8-7e6a-4b3c-9d2e-1f0a8b7c6d5e/preview');
    await expect(checkbox(page, /Sfax Auto Occasion/)).toBeChecked();
  });
});
