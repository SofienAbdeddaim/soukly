import { expect, test } from '@playwright/test';

test.describe('remote loading', () => {
  test('an unavailable remote shows the error page, then loads after "Réessayer"', async ({
    page,
  }) => {
    // mfe-publish "stopped": every request to its dev server is refused.
    const publishOrigin = 'http://localhost:4203/**';
    await page.route(publishOrigin, (route) => route.abort('connectionrefused'));

    await page.goto('/publish/new');

    await expect(page.getByTestId('remote-error')).toBeVisible();
    await expect(page.getByRole('banner')).toBeVisible();
    await expect(page.getByRole('contentinfo')).toBeVisible();

    // mfe-publish "restarted".
    await page.unroute(publishOrigin);
    await page.getByRole('button', { name: 'Réessayer' }).click();

    await expect(page.getByTestId('coming-soon-mfe-publish')).toBeVisible();
    await expect(page).toHaveURL('/publish/new');
  });

  test('the remote URLs come from federation.manifest.json, read at startup', async ({ page }) => {
    // Another URL for mfe-catalog, served by the same dev server: no rebuild of the shell.
    const otherOrigin = 'http://catalog.example.test';
    await page.route('**/federation.manifest.json', async (route) => {
      const manifest = await (await route.fetch()).json();
      await route.fulfill({
        json: { ...manifest, 'mfe-catalog': `${otherOrigin}/remoteEntry.json` },
      });
    });
    await page.route(`${otherOrigin}/**`, async (route) => {
      const url = route.request().url().replace(otherOrigin, 'http://localhost:4202');
      await route.fulfill({ response: await route.fetch({ url }) });
    });
    const remoteEntryRequest = page.waitForRequest(`${otherOrigin}/remoteEntry.json`);

    await page.goto('/');

    await remoteEntryRequest;
    await expect(page.getByTestId('coming-soon-mfe-catalog')).toBeVisible();
  });

  test('Angular is downloaded once, from the shell, even after loading remotes', async ({
    page,
  }) => {
    const angularCore: string[] = [];
    page.on('request', (request) => {
      if (/\/_angular_core\.[^/]*\.js$/.test(request.url())) {
        angularCore.push(request.url());
      }
    });

    await page.goto('/');
    await expect(page.getByTestId('coming-soon-mfe-catalog')).toBeVisible();
    await page.getByRole('banner').getByRole('link', { name: 'Se connecter' }).click();
    await expect(page.getByTestId('coming-soon-mfe-account')).toBeVisible();

    expect(angularCore).toHaveLength(1);
    expect(angularCore[0]).toMatch(/^http:\/\/localhost:4200\//);
  });
});
