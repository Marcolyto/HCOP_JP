import { defineConfig } from '@playwright/test';

const applicationOrigin = (process.env['HCOP_E2E_BASE_URL'] || 'http://127.0.0.1:5182').replace(/\/$/, '');
const executablePath = process.env['HCOP_E2E_BROWSER_PATH']?.trim();
const useBundledBrowser = process.env['HCOP_E2E_USE_BUNDLED_BROWSER'] === 'true';
const browserChannel = process.env['HCOP_E2E_BROWSER_CHANNEL']?.trim() || 'chrome';

export default defineConfig({
  testDir: '.',
  testMatch: /.*\.spec\.ts/,
  fullyParallel: false,
  forbidOnly: true,
  retries: 0,
  workers: 1,
  timeout: 90_000,
  expect: { timeout: 10_000 },
  outputDir: '../../runtime/e2e-artifacts/browser',
  reporter: [['line']],
  use: {
    baseURL: `${applicationOrigin}/app/`,
    locale: 'es-AR',
    timezoneId: 'America/Argentina/Buenos_Aires',
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
    video: 'off',
    launchOptions: executablePath ? { executablePath } : undefined,
    channel: executablePath || useBundledBrowser ? undefined : browserChannel
  }
});
