import { defineConfig, devices } from '@playwright/test';

/**
 * End-to-end tests of the shell with its 3 remotes, on the local dev servers (SETUP-05).
 * Each dev server is started by Nx (`nx run <app>:serve`) unless it already runs. Only Chromium.
 */
const apps = [
  { name: 'shell', port: 4200 },
  { name: 'mfe-account', port: 4201 },
  { name: 'mfe-catalog', port: 4202 },
  { name: 'mfe-publish', port: 4203 },
];

export default defineConfig({
  testDir: './src',
  outputDir: '../../dist/.playwright/apps/shell-e2e/test-output',
  reporter: [
    ['list'],
    ['html', { outputFolder: '../../dist/.playwright/apps/shell-e2e/report', open: 'never' }],
  ],
  fullyParallel: true,
  forbidOnly: !!process.env['CI'],
  retries: process.env['CI'] ? 1 : 0,
  use: {
    baseURL: 'http://localhost:4200',
    trace: 'on-first-retry',
    launchOptions: {
      // Optional: a Chromium installed on the machine, when Playwright cannot download its own.
      executablePath: process.env['CHROMIUM_EXECUTABLE_PATH'] || undefined,
    },
  },
  webServer: apps.map(({ name, port }) => ({
    command: `pnpm exec nx run ${name}:serve`,
    url: `http://localhost:${port}/remoteEntry.json`,
    reuseExistingServer: !process.env['CI'],
    timeout: 180_000,
    cwd: '../..',
  })),
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
