import { expect, Page, test } from '@playwright/test';
import { expectNoSeriousA11yViolations } from './support/axe';
import { fakeAccountApi } from './support/fake-api';
import { draft, fakeListingApi, fakePhoto, LISTING_ID } from './support/fake-listing-api';

const OTHER_ID = '1b9d6c3e-6a43-4c5e-9f0e-2d7a1b8c4e52';

/**
 * Publish form of ANN-04, from the category to the confirmation, with a faked listing-service
 * (support/fake-listing-api.ts) and a signed-in member.
 */
test.use({ viewport: { width: 390, height: 844 } });

/** A JPEG of the given size, drawn in the browser: Playwright has no image library. */
async function jpeg(page: Page, width: number, height: number): Promise<Buffer> {
  const dataUrl = await page.evaluate(
    ([w, h]) => {
      const canvas = document.createElement('canvas');
      canvas.width = w;
      canvas.height = h;
      const context = canvas.getContext('2d') as CanvasRenderingContext2D;
      context.fillStyle = '#f2b33d';
      context.fillRect(0, 0, w, h);
      context.fillStyle = '#2a2140';
      context.fillRect(0, 0, w / 2, h / 2);
      return canvas.toDataURL('image/jpeg', 0.92);
    },
    [width, height],
  );
  return Buffer.from(dataUrl.split(',')[1], 'base64');
}

/** Width and height of an image, decoded by the browser. */
async function sizeOf(page: Page, image: Buffer): Promise<{ width: number; height: number }> {
  return page.evaluate(async (base64) => {
    const bytes = Uint8Array.from(atob(base64), (char) => char.charCodeAt(0));
    const bitmap = await createImageBitmap(new Blob([bytes]));
    return { width: bitmap.width, height: bitmap.height };
  }, image.toString('base64'));
}

function step(page: Page, title: string) {
  return page.getByRole('heading', { level: 1, name: title });
}

test.describe('publish form', () => {
  test('nominal: category, 3 photos, details, then "Price and location"', async ({ page }) => {
    await fakeAccountApi(page, { signedIn: true });
    const api = await fakeListingApi(page);
    await page.goto('/fr/publish/new');

    await expect(step(page, 'Catégorie')).toBeVisible();
    await expect(page.getByText('Étape 1 sur 5')).toBeVisible();
    await page.getByRole('button', { name: 'Véhicules' }).click();
    await expectNoSeriousA11yViolations(page);
    await page.getByRole('button', { name: 'Voitures' }).click();

    await expect(page).toHaveURL(`/fr/publish/${LISTING_ID}/photos`);
    await expect(step(page, 'Photos')).toBeVisible();
    const photos = await Promise.all([
      jpeg(page, 4000, 3000),
      jpeg(page, 800, 600),
      jpeg(page, 800, 600),
    ]);
    await page.getByTestId('photo-input').setInputFiles(
      photos.map((buffer, index) => ({
        name: `photo-${index + 1}.jpg`,
        mimeType: 'image/jpeg',
        buffer,
      })),
    );

    const tiles = page.getByTestId('photo-tile');
    await expect(tiles).toHaveCount(3);
    await expect(tiles.locator('img')).toHaveCount(3);
    await expect(tiles.first()).toContainText('Couverture');
    await expect(page.getByTestId('photo-count')).toContainText('Photos : 3 sur 10');
    // The 4000 × 3000 photo reached the storage at 1600 px at most.
    await expect.poll(() => api.uploads.size).toBe(3);
    await expect(tiles.getByRole('progressbar')).toHaveCount(0);
    const sizes = await Promise.all(
      [...api.uploads.values()].map((upload) => sizeOf(page, upload)),
    );
    expect(sizes).toContainEqual({ width: 1600, height: 1200 });
    expect(Math.max(...sizes.map((size) => Math.max(size.width, size.height)))).toBeLessThanOrEqual(
      1600,
    );
    await expectNoSeriousA11yViolations(page);

    await page.getByTestId('continue').click();
    await expect(page).toHaveURL(`/fr/publish/${LISTING_ID}/details`);
    await page.getByLabel('Titre').fill('Peugeot 208 diesel, première main');
    await page
      .getByLabel('Description')
      .fill('Très bon état, entretien chez le concessionnaire, carnet à jour.');
    await page.getByLabel('Marque').fill('Peugeot');
    await page.getByLabel('Année').fill('2019');
    await page.getByLabel('Kilométrage (km)').fill('62000');
    await page.getByRole('button', { name: 'Diesel' }).click();
    await page.getByRole('button', { name: 'Manuelle' }).click();
    await page.getByLabel('Année').focus();
    await expect(page.getByLabel('Kilométrage (km)')).toHaveValue(/^62\s000$/u);
    await expectNoSeriousA11yViolations(page);
    await page.getByTestId('continue').click();

    await expect(page).toHaveURL(`/fr/publish/${LISTING_ID}/price-location`);
    await expect(step(page, 'Prix et lieu')).toBeVisible();
    const saved = api.listings.get(LISTING_ID);
    expect(saved?.title).toBe('Peugeot 208 diesel, première main');
    expect(saved?.attributes).toEqual({
      brand: 'Peugeot',
      year: 2019,
      'mileage-km': 62000,
      fuel: 'diesel',
      gearbox: 'manual',
    });

    // The back button of the browser goes back to the previous step.
    await page.goBack();
    await expect(page).toHaveURL(`/fr/publish/${LISTING_ID}/details`);
    await page.goForward();
    await expect(step(page, 'Prix et lieu')).toBeVisible();

    await page.getByRole('textbox', { name: 'Prix', exact: true }).fill('32000');
    await page.getByLabel('Prix négociable').check();
    await page.getByLabel('Gouvernorat').selectOption({ label: 'Sfax' });
    await expect(page.getByLabel('Délégation')).toContainText('Sakiet Ezzit');
    await expectNoSeriousA11yViolations(page);
    await page.getByTestId('continue').click();

    await expect(page).toHaveURL(`/fr/publish/${LISTING_ID}/preview`);
    await expect(step(page, 'Aperçu')).toBeVisible();
    const card = page.getByTestId('preview-card');
    await expect(card).toContainText('Peugeot 208 diesel, première main');
    await expect(card).toContainText(/32\s000\sDT/u);
    await expect(card).toContainText('négociable');
    await expect(card).toContainText('Sfax');
    await expect(page.getByTestId('missing-fields')).toHaveCount(0);
    await expectNoSeriousA11yViolations(page);
    await page.getByTestId('publish').click();

    await expect(page).toHaveURL(`/fr/publish/${LISTING_ID}/done`);
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Votre annonce est en ligne');
    await expect(page.getByRole('link', { name: "Voir l'annonce" })).toHaveAttribute(
      'href',
      `/fr/listings/${LISTING_ID}`,
    );
    expect(api.listings.get(LISTING_ID)?.status).toBe('PUBLISHED');
    expect(api.listings.get(LISTING_ID)).toMatchObject({
      priceMode: 'FIXED',
      price: 32000,
      negotiable: true,
      governorateCode: 'sfax',
    });
    await expectNoSeriousA11yViolations(page);
  });

  test('"on quote": a service can choose it, a piece of furniture cannot', async ({ page }) => {
    await fakeAccountApi(page, { signedIn: true });
    const api = await fakeListingApi(page, [
      draft({ categoryCode: 'services.home-repair' }),
      draft({ id: OTHER_ID, categoryCode: 'home.furniture' }),
    ]);

    await page.goto(`/fr/publish/${LISTING_ID}/price-location`);
    await page.getByRole('button', { name: 'Sur devis' }).click();
    await expect(page.getByRole('textbox', { name: 'Prix', exact: true })).toHaveCount(0);
    await expect.poll(() => api.listings.get(LISTING_ID)?.priceMode).toBe('ON_QUOTE');

    await page.goto(`/fr/publish/${OTHER_ID}/price-location`);
    await expect(page.getByRole('textbox', { name: 'Prix', exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Sur devis' })).toHaveCount(0);
  });

  test('incomplete preview: the governorate is missing, with a link to step 4', async ({
    page,
  }) => {
    await fakeAccountApi(page, { signedIn: true });
    await fakeListingApi(page, [
      draft({
        title: 'Peugeot 208 diesel',
        description: 'Très bon état, entretien suivi, carnet à jour.',
        priceMode: 'FIXED',
        price: 32000,
        attributes: {
          brand: 'Peugeot',
          year: 2019,
          'mileage-km': 62000,
          fuel: 'diesel',
          gearbox: 'manual',
        },
        photos: [fakePhoto('a', 0)],
      }),
    ]);

    await page.goto(`/fr/publish/${LISTING_ID}/preview`);
    const missing = page.getByTestId('missing-fields');
    await expect(missing.getByRole('link', { name: 'Gouvernorat' })).toHaveAttribute(
      'href',
      `/fr/publish/${LISTING_ID}/price-location`,
    );
    await expect(page.getByTestId('publish')).toBeDisabled();

    await missing.getByRole('link', { name: 'Gouvernorat' }).click();
    await expect(step(page, 'Prix et lieu')).toBeVisible();
  });

  test('resume: the photos and the category are kept, the details are empty', async ({ page }) => {
    await fakeAccountApi(page, { signedIn: true });
    await fakeListingApi(page, [draft({ photos: [fakePhoto('a', 0), fakePhoto('b', 1)] })]);

    await page.goto(`/fr/publish/${LISTING_ID}/details`);
    await expect(step(page, 'Détails')).toBeVisible();
    await expect(page.getByLabel('Titre')).toHaveValue('');
    await expect(page.getByLabel('Marque')).toBeVisible();

    await page.getByTestId('step-back').click();
    await expect(page.getByTestId('photo-tile')).toHaveCount(2);
    await page.getByTestId('step-back').click();
    await expect(page.getByRole('button', { name: 'Voitures' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );

    // Without a step, the draft opens on its first incomplete step.
    await page.goto(`/fr/publish/${LISTING_ID}`);
    await expect(page).toHaveURL(`/fr/publish/${LISTING_ID}/details`);
  });

  test('dynamic fields: an apartment has its fields and none of a car', async ({ page }) => {
    await fakeAccountApi(page, { signedIn: true });
    await fakeListingApi(page, [draft({ categoryCode: 'real-estate.apartments' })]);

    await page.goto(`/fr/publish/${LISTING_ID}/details`);
    await expect(page.getByRole('group', { name: "Type d'offre" })).toBeVisible();
    await expect(page.getByLabel('Surface (m²)')).toBeVisible();
    await expect(page.getByLabel('Nombre de chambres')).toBeVisible();
    await expect(page.getByText('S+', { exact: true })).toBeVisible();
    await expect(page.getByLabel('Meublé')).toBeVisible();
    await expect(page.getByLabel('Marque')).toHaveCount(0);
    await expectNoSeriousA11yViolations(page);
  });

  test('refused photo: a PDF is refused, the valid photo is uploaded', async ({ page }) => {
    await fakeAccountApi(page, { signedIn: true });
    await fakeListingApi(page, [draft()]);
    await page.goto(`/fr/publish/${LISTING_ID}/photos`);

    await page.getByTestId('photo-input').setInputFiles([
      { name: 'voiture.jpg', mimeType: 'image/jpeg', buffer: await jpeg(page, 800, 600) },
      { name: 'scan.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.7') },
    ]);

    await expect(page.getByTestId('photo-notice')).toContainText(
      "Ce fichier n'est pas une image. Formats acceptés : JPEG, PNG, WebP.",
    );
    await expect(page.getByTestId('photo-notice')).toContainText('scan.pdf');
    await expect(page.getByTestId('photo-tile').locator('img')).toHaveCount(1);
  });

  test('keyboard: the third photo becomes the cover', async ({ page }) => {
    await fakeAccountApi(page, { signedIn: true });
    const api = await fakeListingApi(page, [
      draft({ photos: [fakePhoto('a', 0), fakePhoto('b', 1), fakePhoto('c', 2)] }),
    ]);
    await page.goto(`/fr/publish/${LISTING_ID}/photos`);

    await page.getByRole('button', { name: 'Déplacer la photo 3 avant' }).focus();
    await page.keyboard.press('Enter');
    await expect(page.getByRole('button', { name: 'Déplacer la photo 2 avant' })).toBeFocused();
    await page.keyboard.press('Enter');

    await expect(page.getByTestId('photo-tile').first().locator('img')).toHaveAttribute(
      'src',
      'http://photos.test/c-400.png',
    );
    await expect(page.getByTestId('photo-tile').first()).toContainText('Couverture');
    await expect.poll(() => api.listings.get(LISTING_ID)?.photos[0].id).toBe('c');
  });

  test('conflict: a change made in another tab asks to reload', async ({ page }) => {
    await fakeAccountApi(page, { signedIn: true });
    const api = await fakeListingApi(page, [draft()]);
    await page.goto(`/fr/publish/${LISTING_ID}/details`);
    await expect(page.getByLabel('Titre')).toBeVisible();

    api.changeElsewhere(LISTING_ID, { title: 'Titre du premier onglet' });
    await page.getByLabel('Titre').fill('Titre du second onglet');

    await expect(page.getByTestId('conflict')).toContainText(
      'Cette annonce a été modifiée ailleurs.',
    );
    await page.getByRole('button', { name: 'Recharger' }).click();
    await expect(page.getByLabel('Titre')).toHaveValue('Titre du premier onglet');
    await expect(page.getByTestId('conflict')).toHaveCount(0);
  });

  test.describe('in Arabic', () => {
    test('the form is right to left, the numbers typed left to right', async ({ page }) => {
      await fakeAccountApi(page, { signedIn: true });
      await fakeListingApi(page, [draft({ photos: [fakePhoto('a', 0)] })]);
      await page.goto(`/ar/publish/${LISTING_ID}/details`);

      await expect(page.locator('html')).toHaveAttribute('dir', 'rtl');
      await expect(step(page, 'التفاصيل')).toBeVisible();
      await expect(page.getByText('الخطوة 3 من 5')).toBeVisible();
      await expect(page.getByLabel('سنة الصنع')).toHaveCSS('direction', 'ltr');
      await expect(page.getByLabel('الطراز')).toBeVisible();
      await expectNoSeriousA11yViolations(page);
    });

    test('the price is typed left to right, before "د.ت."', async ({ page }) => {
      await fakeAccountApi(page, { signedIn: true });
      await fakeListingApi(page, [draft({ photos: [fakePhoto('a', 0)] })]);
      await page.goto(`/ar/publish/${LISTING_ID}/price-location`);

      await expect(step(page, 'السعر والمكان')).toBeVisible();
      const price = page.getByRole('textbox', { name: 'السعر', exact: true });
      await expect(price).toHaveCSS('direction', 'ltr');
      await expect(page.locator('.ui-field__unit')).toHaveText('د.ت.');
      await price.fill('32000');
      await price.blur();
      await expect(price).toHaveValue('32.000');
      await page.getByLabel('الولاية').selectOption({ label: 'صفاقس' });
      await expectNoSeriousA11yViolations(page);
    });
  });
});
