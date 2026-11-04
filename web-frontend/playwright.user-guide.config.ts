import { defineConfig } from '@playwright/test';

/**
 * User-guide screenshot capture (READ-ONLY).
 *
 * Separate config on purpose: no globalSetup (the spec injects the role tokens itself),
 * no globalTeardown (the default teardown runs a prefix-cleanup sweep, i.e. it WRITES),
 * no webServer (local dev must already be running).
 *
 *   cd web-frontend
 *   npx playwright test -c playwright.user-guide.config.ts --workers=1
 *   ONLY=organizer npx playwright test -c playwright.user-guide.config.ts --workers=1
 *
 * Targets local dev only (http://localhost:8100 + gateway :8000) running an anonymized
 * snapshot. See e2e/workflows/documentation/capture-user-guide.spec.ts.
 */
export default defineConfig({
  testDir: './e2e/workflows/documentation',
  testMatch: /capture-user-guide\.spec\.ts/,
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 120_000,
  expect: { timeout: 15_000 },
  reporter: [['list']],
  outputDir: 'test-results/user-guide-capture',
  use: {
    baseURL: 'http://localhost:8100',
    browserName: 'chromium',
    locale: 'en-US',
    timezoneId: 'Europe/Zurich',
    viewport: { width: 1440, height: 900 },
    deviceScaleFactor: 1,
    actionTimeout: 15_000,
    navigationTimeout: 45_000,
    screenshot: 'off',
    video: 'off',
    trace: 'off',
    extraHTTPHeaders: { 'Accept-Language': 'en-US,en;q=0.9' },
  },
});
