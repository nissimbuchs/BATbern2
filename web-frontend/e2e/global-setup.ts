/**
 * Playwright Global Setup
 *
 * Handles authentication state setup before running tests.
 * Reads AUTH_TOKEN from environment (set by run-playwright-tests.sh)
 * and injects it into browser storage for authenticated test execution.
 */

import { chromium, FullConfig } from '@playwright/test';

async function globalSetup(config: FullConfig) {
  const authToken = process.env.AUTH_TOKEN;
  const testEnv = process.env.TEST_ENV || 'development';

  // Get environment-specific configuration
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
    throw new Error(`Invalid TEST_ENV: ${testEnv}`);
  }

  console.log(`[Global Setup] Environment: ${testEnv}`);
  console.log(`[Global Setup] Base URL: ${envConfig.baseURL}`);

  // If no auth token, skip auth setup (tests requiring auth will be skipped)
  if (!authToken || authToken === 'null') {
    console.log('[Global Setup] ⚠️  No AUTH_TOKEN found - tests requiring auth will fail');
    console.log('[Global Setup] Run: ./scripts/auth/get-token.sh <environment> <email> <password>');
    return;
  }

  console.log('[Global Setup] ✓ AUTH_TOKEN found, setting up authenticated state');

  // Create browser and page to set up auth state
  const browser = await chromium.launch();
  const context = await browser.newContext();
  const page = await context.newPage();

  try {
    // Navigate to the app to establish domain context
    await page.goto(envConfig.baseURL);

    // Inject auth token into localStorage
    await page.evaluate((token) => {
      localStorage.setItem('authToken', token);
      localStorage.setItem('idToken', token);

      // Also set the token in the format the app might expect
      const authState = {
        idToken: token,
        accessToken: token,
        refreshToken: token,
        expiresAt: Date.now() + (60 * 60 * 1000), // 1 hour from now
        isAuthenticated: true,
      };
      localStorage.setItem('auth', JSON.stringify(authState));
    }, authToken);

    console.log('[Global Setup] ✓ Auth token injected into localStorage');

    // Save the storage state to be reused in tests
    await context.storageState({ path: '.playwright-auth-state.json' });
    console.log('[Global Setup] ✓ Auth state saved to .playwright-auth-state.json');

  } catch (error) {
    console.error('[Global Setup] ✗ Failed to set up auth state:', error);
    throw error;
  } finally {
    await browser.close();
  }
}

export default globalSetup;
