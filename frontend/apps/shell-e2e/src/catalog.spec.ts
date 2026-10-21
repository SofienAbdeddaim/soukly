import { expect, Page, test } from '@playwright/test';
import { expectNoSeriousA11yViolations } from './support/axe';
import { fakeListingApi } from './support/fake-listing-api';
import { fakeSearchApi } from './support/fake-search-api';

/**
 * Home page and results of CATA-02, with a faked listing-service: referentials
 * (support/fake-listing-api.ts) and a fixed set of published listings (support/fake-search-api.ts).
 */
async function fakeCatalog(page: Page) {
  await fakeListingApi(page);
  return fakeSearchApi(page);
}

const results = (page: Page) => page.getByTestId('listings').locator(':scope > li');

test.describe('catalogue, mobile (390px)', () => {
  test.use({ viewport: { width: 390, height: 844 } });

  test('home: search, the 10 families and the latest listings of all of Tunisia', async ({
    page,
  }) => {
    const api = await fakeCatalog(page);
    await page.goto('/fr/');

    await expect(page.getByRole('search')).toBeVisible();
    await expect(page.getByTestId('families').getByRole('link')).toHaveCount(10);
    await expect(results(page)).toHaveCount(20);
    expect(api.requests[0].get('governorate')).toBeNull();
    const first = results(page).first();
    await expect(first.getByRole('article')).toBeVisible();
    await expect(first.getByRole('heading', { level: 3 })).toHaveText(
      'Peugeot 208 diesel, première main n°1',
    );
    await expect(first.locator('img')).toHaveAttribute('src', /cover-1\.png$/u);
    await expect(first.locator('bdi')).toHaveText(/^20\s500\sDT$/u);
    await expect(first).toContainText('Sfax, Sakiet Ezzit');
    await expect(first.locator('time')).toHaveAttribute('datetime', /^2026-10-21T/u);
    await expect(page.getByRole('combobox', { name: 'Lieu des dernières annonces' })).toHaveValue(
      '',
    );
    await expect(page).toHaveTitle('Soukly · Petites annonces en Tunisie');
    await expectNoSeriousA11yViolations(page);
  });

  test('search and filters: "peugeot", then Cars, Sfax and Diesel in the dialog', async ({
    page,
  }) => {
    await fakeCatalog(page);
    await page.goto('/fr/');
    await page.getByRole('searchbox', { name: 'Rechercher une annonce' }).fill('peugeot');
    await page.getByRole('searchbox', { name: 'Rechercher une annonce' }).press('Enter');

    await expect(page).toHaveURL('/fr/search?q=peugeot');
    await expect(page.getByTestId('results-context')).toHaveText(/48 annonces « peugeot »/u);

    const filtersButton = page.getByTestId('open-filters');
    await filtersButton.click();
    const dialog = page.getByRole('dialog', { name: 'Filtres' });
    await expect(dialog).toBeVisible();
    await dialog
      .getByRole('combobox', { name: 'Catégorie', exact: true })
      .selectOption({ label: 'Véhicules' });
    await dialog
      .getByRole('combobox', { name: 'Sous-catégorie' })
      .selectOption({ label: 'Voitures' });
    await dialog.getByRole('combobox', { name: 'Gouvernorat' }).selectOption({ label: 'Sfax' });
    await dialog.getByRole('button', { name: 'Diesel' }).click();
    await expect(dialog.getByRole('button', { name: 'Diesel' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    await expectNoSeriousA11yViolations(page);
    // Nothing applies before the button.
    await expect(page).toHaveURL('/fr/search?q=peugeot');

    await dialog.getByRole('button', { name: 'Voir 31 annonces' }).click();

    await expect(dialog).toBeHidden();
    await expect(filtersButton).toBeFocused();
    await expect(page).toHaveURL('/fr/c/vehicles.cars?q=peugeot&governorate=sfax&attr.fuel=diesel');
    await expect(page.getByTestId('results-context')).toHaveText(
      '31 annonces « peugeot » en Voitures, Sfax',
    );
    await expect(page.getByTestId('filter-chips').getByRole('button')).toHaveText([
      'Voitures',
      'Sfax',
      'Diesel',
    ]);
    await expect(filtersButton).toHaveText('Filtres · 3');
    await expect(page).toHaveTitle('Voitures à Sfax · Soukly');
  });

  test('chip: removing "Diesel" updates and announces the results, back undoes it', async ({
    page,
  }) => {
    await fakeCatalog(page);
    await page.goto('/fr/c/vehicles.cars?governorate=sfax&attr.fuel=diesel');
    const context = page.getByTestId('results-context');
    await expect(context).toHaveText('31 annonces en Voitures, Sfax');
    await expect(context).toHaveAttribute('aria-live', 'polite');

    await page.getByRole('button', { name: 'Retirer le filtre Diesel' }).click();

    await expect(page).toHaveURL('/fr/c/vehicles.cars?governorate=sfax');
    await expect(context).toHaveText('48 annonces en Voitures, Sfax');
    await page.goBack();
    await expect(context).toHaveText('31 annonces en Voitures, Sfax');
  });

  test('progressive loading: the next 24 are added near the bottom, the position is kept back from a listing', async ({
    page,
  }) => {
    const api = await fakeCatalog(page);
    await page.goto('/fr/c/vehicles');
    await expect(page.getByTestId('results-context')).toHaveText(
      '60 annonces en Véhicules, dans toute la Tunisie',
    );
    await expect(results(page)).toHaveCount(24);

    await results(page).last().scrollIntoViewIfNeeded();
    await expect(results(page)).toHaveCount(48);
    await results(page).last().scrollIntoViewIfNeeded();
    await expect(results(page)).toHaveCount(60);
    await expect(page.getByTestId('show-more')).toHaveCount(0);
    const ids = await results(page).evaluateAll((items) =>
      items.map((item) => item.getAttribute('data-listing-id')),
    );
    expect(new Set(ids).size).toBe(60);

    const target = results(page).nth(40);
    await target.scrollIntoViewIfNeeded();
    const scrollY = await page.evaluate(() => window.scrollY);
    const searches = api.requests.length;
    await target.getByRole('link').click();
    await expect(page).toHaveURL(/\/fr\/listings\//u);

    await page.goBack();
    await expect(results(page)).toHaveCount(60);
    await expect.poll(() => page.evaluate(() => window.scrollY)).toBe(scrollY);
    expect(api.requests.length).toBe(searches);
  });

  test('"Show more" loads the next page without scrolling', async ({ page }) => {
    await fakeCatalog(page);
    await page.goto('/fr/c/vehicles');
    await expect(results(page)).toHaveCount(24);

    await page.getByTestId('show-more').focus();
    await page.keyboard.press('Enter');

    await expect(results(page)).toHaveCount(48);
  });

  test('a shared Arabic link shows the same search in Arabic, right to left', async ({ page }) => {
    await fakeCatalog(page);
    await page.goto('/ar/search?q=سيارة&governorate=tunis');

    await expect(page.locator('html')).toHaveAttribute('dir', 'rtl');
    await expect(page.getByRole('searchbox', { name: 'ابحث عن إعلان' })).toHaveValue('سيارة');
    await expect(page.getByTestId('results-context')).toHaveText('3 إعلانات «سيارة» في تونس');
    await expect(page.getByTestId('filter-chips').getByRole('button')).toHaveText(['تونس']);
    await expect(results(page)).toHaveCount(3);
    await expect(page.getByTestId('open-filters')).toHaveText('الفلاتر · 1');
    await expectNoSeriousA11yViolations(page);
  });

  test('no result: the message and the "Remove the Price filter" suggestion', async ({ page }) => {
    await fakeCatalog(page);
    await page.goto('/fr/search?q=peugeot&priceMax=1000');

    const empty = page.getByTestId('results-empty');
    await expect(
      empty.getByRole('heading', { name: 'Aucune annonce ne correspond' }),
    ).toBeVisible();
    await empty.getByRole('button', { name: 'Retirer le filtre Prix' }).click();

    await expect(page).toHaveURL('/fr/search?q=peugeot');
    await expect(results(page)).toHaveCount(24);
  });

  test('network error: the results stay and "Try again" reloads them', async ({ page }) => {
    const api = await fakeCatalog(page);
    await page.goto('/fr/search');
    await expect(results(page)).toHaveCount(24);

    api.offline = true;
    await page
      .getByRole('combobox', { name: 'Trier par' })
      .selectOption({ label: 'Prix croissant' });
    await expect(page.getByText('La connexion a échoué.')).toBeVisible();
    await expect(results(page)).toHaveCount(24);

    api.offline = false;
    await page.getByRole('button', { name: 'Réessayer' }).click();
    await expect(page.getByText('La connexion a échoué.')).toBeHidden();
    await expect(results(page).first().locator('bdi')).toHaveText('Sur devis');
  });

  test('the governorate chosen is remembered for the home page, until "All of Tunisia"', async ({
    page,
  }) => {
    const api = await fakeCatalog(page);
    await page.goto('/fr/search');
    await page.getByTestId('open-filters').click();
    const dialog = page.getByRole('dialog', { name: 'Filtres' });
    await dialog.getByRole('combobox', { name: 'Gouvernorat' }).selectOption({ label: 'Sousse' });
    await dialog.getByRole('button', { name: 'Voir 5 annonces' }).click();
    await expect(page).toHaveURL('/fr/search?governorate=sousse');

    await page.goto('/fr/');
    const picker = page.getByRole('combobox', { name: 'Lieu des dernières annonces' });
    await expect(picker).toHaveValue('sousse');
    await expect(results(page)).toHaveCount(5);
    expect(api.requests.at(-1)?.get('governorate')).toBe('sousse');
    await expect(results(page).first().locator('bdi')).toHaveText(/ \/ mois$/u);

    await picker.selectOption({ label: 'Toute la Tunisie' });
    await expect(results(page)).toHaveCount(20);
    await page.reload();
    await expect(picker).toHaveValue('');
  });
});

test.describe('catalogue, desktop (1280px)', () => {
  test.use({ viewport: { width: 1280, height: 900 } });

  test('filters in a side column, results in a grid; a change applies at once', async ({
    page,
  }) => {
    await fakeCatalog(page);
    await page.goto('/fr/search');

    const column = page.getByRole('complementary', { name: 'Filtres' });
    await expect(column).toBeVisible();
    await expect(page.getByTestId('open-filters')).toHaveCount(0);
    await expect(page.getByTestId('listings').locator('ui-listing-card')).toHaveCount(24);
    const [first, second] = await Promise.all([
      results(page).nth(0).boundingBox(),
      results(page).nth(1).boundingBox(),
    ]);
    expect(first && second && first.y === second.y).toBe(true);

    await column.getByRole('combobox', { name: 'Gouvernorat' }).selectOption({ label: 'Tunis' });
    await expect(page).toHaveURL('/fr/search?governorate=tunis');
    await expect(page.getByTestId('results-context')).toHaveText('12 annonces à Tunis');
    await expectNoSeriousA11yViolations(page);
  });

  test('home: hero, categories and a grid of the latest listings', async ({ page }) => {
    await fakeCatalog(page);
    await page.goto('/fr/');

    await expect(
      page.getByRole('heading', { level: 1, name: 'Tout le souk, au coin de chez vous' }),
    ).toBeVisible();
    await expect(page.getByTestId('listings').locator('ui-listing-card')).toHaveCount(20);
    await page.getByRole('link', { name: 'Voir toutes les annonces' }).click();
    await expect(page).toHaveURL('/fr/search');
    await expect(page.getByTestId('results-context')).toHaveText(
      '67 annonces dans toute la Tunisie',
    );
  });
});
