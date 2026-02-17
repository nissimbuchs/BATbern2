/**
 * Playwright Global Setup
 *
 * Handles authentication state setup before running tests.
 * Reads AUTH_TOKEN from environment (set by run-playwright-tests.sh)
 * and injects it into browser storage for authenticated test execution.
 */

import { chromium } from '@playwright/test';

async function globalSetup() {
  const testEnv = process.env.TEST_ENV || 'development';

  // Get environment-specific configuration
  const envConfig = {
    development: {
      baseURL: 'http://localhost:8100',
      apiURL: 'http://localhost:8000',
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

  // Try to load tokens from ~/.batbern/{environment}.json file
  const fs = await import('fs');
  const os = await import('os');
  const path = await import('path');
  const tokenFilePath = path.join(os.homedir(), '.batbern', `${testEnv}.json`);

  if (!fs.existsSync(tokenFilePath)) {
    console.log('[Global Setup] ⚠️  No token file found - tests requiring auth will fail');
    console.log(`[Global Setup] Expected file: ${tokenFilePath}`);
    console.log('[Global Setup] Run: ./scripts/auth/get-token.sh <environment> <email> <password>');

    // Create empty auth state so Playwright doesn't crash reading a missing file
    fs.writeFileSync('.playwright-auth-state.json', JSON.stringify({ cookies: [], origins: [] }));
    console.log('[Global Setup] ✓ Created empty auth state (unauthenticated tests will still run)');
    return;
  }

  const tokenData = JSON.parse(fs.readFileSync(tokenFilePath, 'utf8'));
  const { idToken, accessToken, refreshToken } = tokenData;

  if (!idToken || !accessToken || !refreshToken) {
    console.log('[Global Setup] ⚠️  Token file missing required tokens');
    return;
  }

  console.log('[Global Setup] ✓ Tokens loaded from file, setting up authenticated state');

  // Export AUTH_TOKEN to environment for API integration tests
  process.env.AUTH_TOKEN = idToken;
  console.log('[Global Setup] ✓ AUTH_TOKEN exported to environment');

  // Create browser and page to set up auth state
  const browser = await chromium.launch();
  const context = await browser.newContext();
  const page = await context.newPage();

  try {
    // Navigate to the app to establish domain context
    await page.goto(envConfig.baseURL);

    // Inject auth tokens into localStorage in AMPLIFY V6 format
    await page.evaluate(
      ({ idToken, accessToken, refreshToken }) => {
        // Parse idToken to get username/sub and client ID
        const base64Url = idToken.split('.')[1];
        const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
        const jsonPayload = decodeURIComponent(
          atob(base64)
            .split('')
            .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
            .join('')
        );
        const payload = JSON.parse(jsonPayload);

        // Extract client ID and username from token
        const clientId = payload.aud; // audience = client ID
        const username = payload['cognito:username'];

        // Set Amplify V6 Cognito token provider keys with CORRECT tokens
        const prefix = `CognitoIdentityServiceProvider.${clientId}`;

        localStorage.setItem(`${prefix}.${username}.idToken`, idToken);
        localStorage.setItem(`${prefix}.${username}.accessToken`, accessToken);
        localStorage.setItem(`${prefix}.${username}.refreshToken`, refreshToken);
        localStorage.setItem(`${prefix}.${username}.clockDrift`, '0');
        localStorage.setItem(`${prefix}.LastAuthUser`, username);

        // Also set legacy keys for backward compatibility
        localStorage.setItem('authToken', idToken);
        localStorage.setItem('idToken', idToken);

        const authState = {
          idToken: idToken,
          accessToken: accessToken,
          refreshToken: refreshToken,
          expiresAt: payload.exp * 1000,
          isAuthenticated: true,
        };
        localStorage.setItem('auth', JSON.stringify(authState));

        // Set language to English for E2E tests (user default might be German)
        localStorage.setItem('batbern-language', 'en');
      },
      { idToken, accessToken, refreshToken }
    );

    console.log('[Global Setup] ✓ Auth token injected into localStorage (Amplify V6 format)');
    console.log('[Global Setup] ✓ Language set to English for E2E tests');

    // Save the storage state to be reused in tests
    await context.storageState({ path: '.playwright-auth-state.json' });
    console.log('[Global Setup] ✓ Auth state saved to .playwright-auth-state.json');

    // Validate storage state was saved correctly
    const fs = await import('fs');
    if (fs.existsSync('.playwright-auth-state.json')) {
      const savedState = JSON.parse(fs.readFileSync('.playwright-auth-state.json', 'utf8'));
      const origins = (savedState.origins || []) as Array<{
        localStorage?: Array<{ name: string; value: string }>;
      }>;
      const hasTokens = origins.some((origin) =>
        origin.localStorage?.some((item) => item.name.includes('CognitoIdentityServiceProvider'))
      );
      if (hasTokens) {
        console.log('[Global Setup] ✓ Storage state validated - Cognito tokens present');
      } else {
        console.warn('[Global Setup] ⚠️  Storage state saved but no Cognito tokens found');
      }
    } else {
      console.warn('[Global Setup] ⚠️  Storage state file was not created');
    }
  } catch (error) {
    console.error('[Global Setup] ✗ Failed to set up auth state:', error);
    throw error;
  } finally {
    await browser.close();
  }
}

export default globalSetup;
