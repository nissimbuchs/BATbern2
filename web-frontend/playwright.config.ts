import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright E2E Test Configuration
 *
 * Environment-aware configuration enables testing against different environments:
 * - development: Local Docker environment (localhost:3000)
 * - staging: AWS staging environment (staging.batbern.ch)
 * - production: AWS production environment (batbern.ch)
 *
 * Usage:
 *   npm run test:e2e              # Default: development
 *   npm run test:e2e:staging      # Test staging
 *   npm run test:e2e:production   # Test production
 */

// Get test environment from environment variable
const testEnv = process.env.TEST_ENV || 'development';

// Environment-specific configuration
const envConfig = {
  development: {
    baseURL: 'http://localhost:3000',
    apiURL: 'http://localhost:8080',
  },
  staging: {
    baseURL: 'https://staging.batbern.ch',
    apiURL: 'https://api.staging.batbern.ch',
  },
  production: {
    baseURL: 'https://batbern.ch',
    apiURL: 'https://api.batbern.ch',
  },
}[testEnv as 'development' | 'staging' | 'production'];

if (!envConfig) {
  throw new Error(`Invalid TEST_ENV: ${testEnv}. Must be one of: development, staging, production`);
}

console.log(`Running Playwright tests against: ${testEnv} (${envConfig.baseURL})`);

export default defineConfig({
  testDir: './e2e',

  /* Run tests in files in parallel */
  fullyParallel: true,

  /* Fail the build on CI if you accidentally left test.only in the source code. */
  forbidOnly: !!process.env.CI,

  /* Retry on CI only */
  retries: process.env.CI ? 2 : 0,

  /* Opt out of parallel tests on CI. */
  workers: process.env.CI ? 1 : undefined,

  /* Reporter to use. See https://playwright.dev/docs/test-reporters */
  reporter: [['html'], ['junit', { outputFile: 'test-results/junit.xml' }], ['list']],

  /* Shared settings for all the projects below. See https://playwright.dev/docs/api/class-testoptions. */
  use: {
    /* Base URL for the environment being tested */
    baseURL: envConfig.baseURL,

    /* Collect trace when retrying the failed test. See https://playwright.dev/docs/trace-viewer */
    trace: 'on-first-retry',

    /* Screenshot on failure */
    screenshot: 'only-on-failure',

    /* Video on failure */
    video: 'retain-on-failure',

    /* Extra HTTP headers for all requests */
    extraHTTPHeaders: {
      'X-Test-Environment': testEnv,
    },
  },

  /* Configure projects for major browsers */
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },

    {
      name: 'firefox',
      use: { ...devices['Desktop Firefox'] },
    },

    {
      name: 'webkit',
      use: { ...devices['Desktop Safari'] },
    },

    /* Test against mobile viewports. */
    {
      name: 'Mobile Chrome',
      use: { ...devices['Pixel 5'] },
    },
    {
      name: 'Mobile Safari',
      use: { ...devices['iPhone 12'] },
    },
  ],

  /* Run your local dev server before starting the tests */
  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:3000',
    reuseExistingServer: !process.env.CI,
    timeout: 120 * 1000,
  },
});
