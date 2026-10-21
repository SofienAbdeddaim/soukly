import { expect, Page, test } from '@playwright/test';
import { expectNoSeriousA11yViolations } from './support/axe';
import { fakeAccountApi } from './support/fake-api';
import {
  draft,
  FakeListing,
  FakeListingApi,
  fakeListingApi,
  fakePhoto,
  inDays,
} from './support/fake-listing-api';

/**
 * "My listings" and the edit page of ANN-05, with a faked listing-service
 * (support/fake-listing-api.ts) and a signed-in member.
 */
test.use({ viewport: { width: 390, height: 844 } });

const id = (n: number) => `0b9d6c3e-6a43-4c5e-9f0e-00000000000${n}`;

function listing(n: number, changes: Partial<FakeListing>): FakeListing {
  return draft({
    id: id(n),
    version: 3,
    categoryCode: 'home.other',
    title: `Annonce ${n}`,
    description: 'Une description assez longue pour être complète.',
    priceMode: 'FIXED',
    price: 1000 * n,
    governorateCode: 'tunis',
    photos: [fakePhoto(`p${n}`, 0)],
    status: 'PUBLISHED',
    expiresAt: inDays(58),
    contactRevealsCount: n,
    ...changes,
  });
}

/** 3 listings online (one expires in 2 days), 1 draft, 1 expired and 1 deleted. */
function listings(): FakeListing[] {
  return [
    listing(1, { title: 'Peugeot 208 diesel', price: 32000, contactRevealsCount: 8 }),
    listing(2, { title: 'Canapé d’angle', expiresAt: inDays(1.5) }),
    listing(3, { title: 'Vélo de course' }),
    listing(4, { status: 'DRAFT', title: 'Table basse', price: null, priceMode: null }),
    listing(5, { status: 'EXPIRED', title: 'Climatiseur', expiresAt: inDays(-11) }),
    listing(6, { status: 'DELETED', title: 'Annonce supprimée' }),
  ];
}

async function open(page: Page, url = '/fr/account/listings'): Promise<FakeListingApi> {
  await fakeAccountApi(page, { signedIn: true });
  const api = await fakeListingApi(page, listings());
  await page.goto(url);
  return api;
}

const card = (page: Page, title: string) =>
  page.getByTestId('own-listing').filter({ has: page.getByRole('heading', { name: title }) });

test.describe('my listings', () => {
  test('tabs with their counts, the deleted listing nowhere', async ({ page }) => {
    await open(page);

    await expect(page.getByRole('heading', { level: 1, name: 'Mes annonces' })).toBeVisible();
    await expect(page.getByRole('tab', { name: 'En ligne 3' })).toHaveAttribute(
      'aria-selected',
      'true',
    );
    await expect(page.getByRole('tab', { name: 'Brouillons 1' })).toBeVisible();
    await expect(page.getByRole('tab', { name: 'Expirées 1' })).toBeVisible();
    await expect(page.getByRole('tab', { name: 'Désactivées', exact: true })).toBeVisible();
    await expect(page.getByTestId('own-listing')).toHaveCount(3);

    const soon = card(page, 'Canapé d’angle');
    await expect(soon.getByTestId('own-listing-badge')).toHaveText('Expire dans 2 j');
    await expect(soon.getByTestId('own-listing-primary')).toHaveText('Prolonger de 60 jours');
    await expect(card(page, 'Peugeot 208 diesel').getByTestId('own-listing-reveals')).toHaveText(
      '8 numéros affichés',
    );
    await expectNoSeriousA11yViolations(page);

    for (const tab of ['Brouillons', 'Expirées', 'Désactivées']) {
      await page.getByRole('tab', { name: tab }).click();
      await expect(page.getByTestId('listings-panel')).not.toHaveAttribute('aria-busy', 'true');
      await expect(page.getByText('Annonce supprimée')).toHaveCount(0);
    }
    await expect(page).toHaveURL('/fr/account/listings?tab=inactive');
    await expect(page.getByText('Aucune annonce désactivée')).toBeVisible();

    // The keyboard moves between the tabs
    await page.getByRole('tab', { name: 'Désactivées' }).press('Home');
    await expect(page).toHaveURL('/fr/account/listings?tab=online');
    await expect(page.getByRole('tab', { name: 'En ligne 3' })).toBeFocused();
  });

  test('edits the price of a listing online in one save', async ({ page }) => {
    const api = await open(page);

    await card(page, 'Peugeot 208 diesel').getByRole('link', { name: 'Modifier' }).click();
    await expect(page).toHaveURL(`/fr/publish/${id(1)}/edit`);
    await expect(page.getByRole('heading', { level: 1, name: "Modifier l'annonce" })).toBeVisible();
    await expect(page.getByTestId('edit-banner')).toContainText(
      'Vos modifications seront visibles dès que vous les enregistrez.',
    );
    await expect(page.getByTestId('edit-category')).toContainText(
      '(non modifiable après publication)',
    );
    await expectNoSeriousA11yViolations(page);

    // Two sections changed, one PATCH: nothing is saved before "Save".
    await page.getByLabel('Titre').fill('Peugeot 208 diesel, 1re main');
    const price = page.getByRole('textbox', { name: 'Prix', exact: true });
    await price.fill('30500');
    await price.blur();
    expect(api.patches).toEqual([]);

    await page.getByTestId('edit-save').click();
    await expect(page).toHaveURL('/fr/account/listings?tab=online');
    expect(api.patches).toEqual([{ title: 'Peugeot 208 diesel, 1re main', price: 30500 }]);
    await expect(page.getByText('Modifications enregistrées')).toBeVisible();
    await expect(
      card(page, 'Peugeot 208 diesel, 1re main').getByTestId('own-listing-price'),
    ).toHaveText(/30\s500\sDT/);
  });

  test('asks before leaving the edit page without saving', async ({ page }) => {
    await open(page, `/fr/publish/${id(1)}/edit`);

    await page.getByLabel('Titre').fill('Peugeot 208 diesel, première main');
    await page.getByTestId('edit-cancel').click();
    const dialog = page.getByRole('alertdialog', { name: 'Quitter sans enregistrer ?' });
    await expect(dialog).toBeVisible();
    await expectNoSeriousA11yViolations(page);

    await dialog.getByRole('button', { name: 'Continuer la modification' }).click();
    await expect(page).toHaveURL(`/fr/publish/${id(1)}/edit`);
    await expect(page.getByLabel('Titre')).toHaveValue('Peugeot 208 diesel, première main');

    await page.getByTestId('edit-cancel').click();
    await page.getByRole('button', { name: 'Quitter sans enregistrer' }).click();
    await expect(page).toHaveURL('/fr/account/listings?tab=online');
  });

  test('deactivates a listing, then puts it back online', async ({ page }) => {
    const api = await open(page);

    const peugeot = card(page, 'Peugeot 208 diesel');
    await peugeot.getByRole('button', { name: /Plus d’actions/ }).click();
    await expect(page.getByRole('menu')).toBeVisible();
    await expectNoSeriousA11yViolations(page);
    await page.getByRole('menuitem', { name: 'Désactiver' }).click();

    await expect(page.getByText('Annonce désactivée')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Annuler' })).toBeVisible();
    await expect(page.getByRole('tab', { name: 'Désactivées 1' })).toBeVisible();
    await expect(page.getByTestId('own-listing')).toHaveCount(2);
    expect(api.actions).toEqual([`deactivate ${id(1)}`]);

    await page.getByRole('tab', { name: 'Désactivées 1' }).click();
    await expect(peugeot.getByTestId('own-listing-badge')).toHaveText('Désactivée');
    await peugeot.getByRole('button', { name: 'Remettre en ligne' }).click();

    await expect(page.getByText('Annonce en ligne pour 60 jours')).toBeVisible();
    await expect(page.getByRole('tab', { name: 'En ligne 3' })).toBeVisible();
    await expect(page.getByText('Aucune annonce désactivée')).toBeVisible();
    expect(api.actions).toEqual([`deactivate ${id(1)}`, `renew ${id(1)}`]);
  });

  test('deletes a listing online after the confirmation', async ({ page }) => {
    const api = await open(page);

    await card(page, 'Vélo de course')
      .getByRole('button', { name: /Plus d’actions/ })
      .click();
    await page.getByRole('menuitem', { name: 'Supprimer' }).click();
    const dialog = page.getByRole('alertdialog', { name: 'Supprimer cette annonce ?' });
    await expect(dialog).toContainText('« Vélo de course » disparaîtra de Soukly.');
    await expect(dialog).toContainText('Cette action est définitive.');
    await expect(dialog.getByRole('button', { name: 'Annuler' })).toBeFocused();
    await expectNoSeriousA11yViolations(page);

    await dialog.getByRole('button', { name: 'Supprimer définitivement' }).click();
    await expect(page.getByText('Annonce supprimée')).toBeVisible();
    await expect(page.getByRole('tab', { name: 'En ligne 2' })).toBeVisible();
    await expect(card(page, 'Vélo de course')).toHaveCount(0);
    expect(api.actions).toEqual([`delete ${id(3)}`]);
  });

  test('Arabic: right to left, ar-TN prices and dates', async ({ page }) => {
    await open(page, '/ar/account/listings?tab=expired');

    await expect(page.locator('html')).toHaveAttribute('dir', 'rtl');
    await expect(page.getByRole('heading', { level: 1, name: 'إعلاناتي' })).toBeVisible();
    await expect(page.getByRole('tab', { name: 'منتهية 1' })).toHaveAttribute(
      'aria-selected',
      'true',
    );
    const expired = page.getByTestId('own-listing');
    await expect(expired.getByTestId('own-listing-price')).toContainText('د.ت.');
    await expect(expired.getByTestId('own-listing-badge')).toContainText('انتهى في');
    await expect(expired.getByTestId('own-listing-primary')).toHaveText('إعادة النشر');
    await expectNoSeriousA11yViolations(page);
  });
});
