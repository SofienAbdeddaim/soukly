import { expect, Page, test } from '@playwright/test';
import { expectNoSeriousA11yViolations } from './support/axe';
import { fakeAccountApi } from './support/fake-api';
import { draft, FakeListing, fakeListingApi, fakePhoto, inDays } from './support/fake-listing-api';
import {
  connection,
  FakePost,
  fakePost,
  FakeSocialApi,
  fakeSocialApi,
} from './support/fake-social-api';

/**
 * Where the listings were posted (SOC-06): the pills of "My listings", "Try again", the link to
 * connect a network again and the relaunch banner of "My networks", with a faked listing-service
 * and social-publisher. Meta is never called.
 */
test.use({ viewport: { width: 390, height: 844 } });

const id = (n: number) => `0b9d6c3e-6a43-4c5e-9f0e-00000000000${n}`;

function listing(n: number, title: string): FakeListing {
  return draft({
    id: id(n),
    version: 3,
    categoryCode: 'home.other',
    title,
    description: 'Une description assez longue pour être complète.',
    priceMode: 'FIXED',
    price: 1000 * n,
    governorateCode: 'tunis',
    photos: [fakePhoto(`p${n}`, 0)],
    status: 'PUBLISHED',
    expiresAt: inDays(58),
    contactRevealsCount: n,
  });
}

const LISTINGS = [
  listing(1, 'Peugeot 208 diesel'),
  listing(2, 'Appartement S+2 meublé'),
  listing(3, 'Canapé d’angle en velours'),
];

function posts(): Record<string, FakePost[]> {
  return {
    [id(1)]: [
      fakePost({ permalink: 'https://www.facebook.com/1001_1' }),
      fakePost({ network: 'INSTAGRAM', permalink: 'https://www.instagram.com/p/abc/' }),
    ],
    [id(2)]: [
      fakePost({ permalink: 'https://www.facebook.com/1001_2' }),
      fakePost({ network: 'INSTAGRAM', status: 'PENDING', badge: 'PENDING' }),
    ],
    [id(3)]: [fakePost({ status: 'SKIPPED', badge: 'TO_RECONNECT', errorCode: 'needs-reconnect' })],
  };
}

async function open(
  page: Page,
  url = '/fr/account/listings',
  social: Partial<Pick<FakeSocialApi, 'posts' | 'facebook' | 'blocked'>> = {},
): Promise<FakeSocialApi> {
  await fakeAccountApi(page, { signedIn: true });
  await fakeListingApi(page, LISTINGS);
  const api = await fakeSocialApi(page, { posts: posts(), ...social });
  await page.goto(url);
  return api;
}

const card = (page: Page, title: string) =>
  page.getByTestId('own-listing').filter({ has: page.getByRole('heading', { name: title }) });

test.describe('publication statuses', () => {
  test('a pill per network, a link to the published post, "to connect again"', async ({ page }) => {
    await open(page);

    const peugeot = card(page, 'Peugeot 208 diesel');
    const facebook = peugeot.getByRole('link', {
      name: 'Facebook : publié, ouvrir le post (nouvel onglet)',
    });
    await expect(facebook).toHaveAttribute('href', 'https://www.facebook.com/1001_1');
    await expect(facebook).toHaveAttribute('target', '_blank');
    await expect(peugeot.getByTestId('post-badge')).toHaveText([
      /Facebook · publié/,
      /Instagram · publié/,
    ]);
    await expect(card(page, 'Appartement S+2 meublé').getByTestId('post-badge')).toHaveText([
      /Facebook · publié/,
      /Instagram · en cours/,
    ]);

    const sofa = card(page, 'Canapé d’angle en velours');
    await expect(sofa.getByTestId('post-badge')).toHaveText(/Facebook · à relier/);
    await expect(sofa.getByTestId('post-reconnect')).toContainText(
      'Votre Page Facebook doit être reliée à nouveau.',
    );
    await expect(sofa.getByRole('button', { name: /Réessayer/ })).toHaveCount(0);
    await expectNoSeriousA11yViolations(page);

    await sofa.getByRole('link', { name: 'Relier à nouveau' }).click();
    await expect(page).toHaveURL(/\/fr\/account\/social$/);
  });

  test('a post in progress becomes published without reloading', async ({ page }) => {
    await page.clock.install();
    const api = await open(page);
    const flat = card(page, 'Appartement S+2 meublé');
    await expect(flat.getByTestId('post-badge').nth(1)).toHaveText(/Instagram · en cours/);

    api.posts[id(2)][1] = fakePost({
      network: 'INSTAGRAM',
      permalink: 'https://instagram.com/p/x',
    });
    await page.clock.fastForward(20_000);

    await expect(flat.getByTestId('post-badge').nth(1)).toHaveText(/Instagram · publié/);
  });

  test('"Try again" on a failure puts the post back in progress, with a toast', async ({
    page,
  }) => {
    const failed = posts();
    failed[id(1)][1] = fakePost({
      network: 'INSTAGRAM',
      status: 'FAILED',
      badge: 'FAILED',
      reason: 'RETRIES_EXHAUSTED',
      retryable: true,
    });
    await open(page, '/fr/account/listings', { posts: failed });

    const peugeot = card(page, 'Peugeot 208 diesel');
    await expect(peugeot.getByTestId('post-badge').nth(1)).toHaveText(/Instagram · échec/);
    await expect(peugeot.getByTestId('post-failure')).toContainText(
      'Instagram n’a pas publié l’annonce. Le service n’a pas répondu après plusieurs essais.',
    );
    await expect(peugeot.getByTestId('post-retries-left')).toHaveText('3 essais restants');
    await expectNoSeriousA11yViolations(page);

    await peugeot.getByRole('button', { name: 'Réessayer la publication sur Instagram' }).click();

    await expect(peugeot.getByTestId('post-badge').nth(1)).toHaveText(/Instagram · en cours/);
    await expect(peugeot.getByTestId('post-failure')).toHaveCount(0);
    await expect(page.getByText('Nouvelle tentative lancée pour Instagram.')).toBeVisible();
  });

  test('connecting the Page again offers to post the blocked listings', async ({ page }) => {
    const api = await open(page, '/fr/account/social', {
      facebook: { ...connection(), status: 'NEEDS_RECONNECT' },
      blocked: {
        FACEBOOK: [
          { listingId: id(3), title: 'Canapé d’angle en velours' },
          { listingId: id(4), title: 'Table basse en olivier' },
          { listingId: id(5), title: 'Lampe berbère en cuivre' },
        ],
        INSTAGRAM: [],
      },
    });

    await page
      .getByTestId('facebook-card')
      .getByRole('button', { name: 'Relier à nouveau' })
      .click();

    const banner = page.getByTestId('relaunch-banner');
    await expect(banner).toContainText(
      'Page reliée à nouveau. 3 annonces n’ont pas été publiées sur Facebook pendant la coupure. Les publier maintenant ?',
    );
    await expect(banner.getByRole('listitem')).toHaveText([
      'Canapé d’angle en velours',
      'Table basse en olivier',
      'Lampe berbère en cuivre',
    ]);
    await expectNoSeriousA11yViolations(page);

    await banner.getByRole('button', { name: 'Publier les 3 annonces' }).click();

    await expect(banner).toHaveCount(0);
    expect(api.calls).toContainEqual({
      method: 'POST',
      path: '/api/social/posts/relaunch',
      body: { network: 'FACEBOOK' },
    });
    expect(api.posts[id(3)][0].badge).toBe('PENDING');
  });

  test('Arabic: pills and reasons right to left', async ({ page }) => {
    const failed = posts();
    failed[id(1)][1] = fakePost({
      network: 'INSTAGRAM',
      status: 'FAILED',
      badge: 'FAILED',
      reason: 'MEDIA_REJECTED',
      retryable: true,
      manualRetriesLeft: 1,
    });
    await open(page, '/ar/account/listings', { posts: failed });
    await expect(page.locator('html')).toHaveAttribute('dir', 'rtl');

    const peugeot = card(page, 'Peugeot 208 diesel');
    await expect(peugeot.getByTestId('post-badge')).toHaveText([
      /فيسبوك · منشور/,
      /إنستغرام · فشل/,
    ]);
    await expect(peugeot.getByTestId('post-failure')).toContainText('رفض إنستغرام إحدى الصور.');
    await expect(peugeot.getByRole('button', { name: /إعادة المحاولة/ })).toBeVisible();
    await expect(peugeot.getByTestId('post-retries-left')).toHaveText('محاولة واحدة متبقية');
    await expect(card(page, 'Canapé d’angle en velours').getByTestId('post-badge')).toHaveText(
      /فيسبوك · يجب إعادة الربط/,
    );
    await expectNoSeriousA11yViolations(page);
  });
});
