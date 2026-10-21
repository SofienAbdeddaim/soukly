import { defineConfig, devices } from '@playwright/test';
import baseConfig from './playwright.config.mts';

/**
 * End-to-end tests of the account screens against the real local stack (AUTH-03): the dev servers
 * of the front end (started like in playwright.config.mts), the gateway on 8080, user-service on
 * 8081, and PostgreSQL and Mailpit from docker-compose.yml, all started beforehand (see README).
 * Not part of the CI, which has no back end: there, src/account.spec.ts covers the same screens
 * with a faked API.
 */
export default defineConfig({
  ...baseConfig,
  testDir: './src/stack',
  testIgnore: [],
  outputDir: '../../dist/.playwright/apps/shell-e2e/stack-output',
  reporter: [
    ['list'],
    ['html', { outputFolder: '../../dist/.playwright/apps/shell-e2e/stack-report', open: 'never' }],
  ],
  // One account per test, but the gateway limits the auth calls per IP: no parallel run.
  fullyParallel: false,
  workers: 1,
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
