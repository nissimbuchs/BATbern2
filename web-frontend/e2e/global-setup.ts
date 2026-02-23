/**
 * Playwright Global Setup
 *
 * Handles authentication state setup before running tests.
 * Reads tokens from ~/.batbern/{env}-{role}.json (or the legacy {env}.json for organizer)
 * and injects them into browser storage for authenticated test execution.
 *
 * Writes per-role auth state files:
 *   .playwright-auth-state.json          (organizer — legacy path, used by chromium project)
 *   .playwright-auth-speaker.json        (speaker project)
 *   .playwright-auth-partner.json        (partner project)
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

  const fs = await import('fs');
  const os = await import('os');
  const path = await import('path');

  /**
   * Inject tokens for a specific role into a fresh browser context and save the storage state.
   * Returns the idToken string if successful, empty string otherwise.
   */
  async function setupRoleAuth(
    role: string,
    tokenFilePath: string,
    stateFilePath: string,
    fallbackFilePath?: string
  ): Promise<string> {
    // Resolve the token file to use (primary or fallback for backward compat)
    const resolvedTokenFile = fs.existsSync(tokenFilePath)
      ? tokenFilePath
      : fallbackFilePath && fs.existsSync(fallbackFilePath)
        ? fallbackFilePath
        : null;

    if (!resolvedTokenFile) {
      console.log(
        `[Global Setup] ⚠️  No token file for role=${role} — tests for this role will be skipped`
      );
      if (role !== 'organizer') {
        console.log(`[Global Setup] Expected file: ${tokenFilePath}`);
        console.log(
          `[Global Setup] Run: ./scripts/auth/get-token.sh ${testEnv} <email> <password> ${role}`
        );
      }
      return '';
    }

    const tokenData = JSON.parse(fs.readFileSync(resolvedTokenFile, 'utf8'));
    const { idToken, accessToken, refreshToken } = tokenData;

    if (!idToken || !accessToken || !refreshToken) {
      console.log(`[Global Setup] ⚠️  Token file missing required tokens for role=${role}`);
      return '';
    }

    console.log(`[Global Setup] Setting up auth state for role=${role}`);

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

      // Save the storage state for this role
      await context.storageState({ path: stateFilePath });
      console.log(`[Global Setup] ✓ Auth state saved: ${stateFilePath} (role=${role})`);

      // Validate storage state
      const savedState = JSON.parse(fs.readFileSync(stateFilePath, 'utf8'));
      const origins = (savedState.origins || []) as Array<{
        localStorage?: Array<{ name: string; value: string }>;
      }>;
      const hasTokens = origins.some((origin) =>
        origin.localStorage?.some((item) => item.name.includes('CognitoIdentityServiceProvider'))
      );
      if (hasTokens) {
        console.log(`[Global Setup] ✓ Storage state validated for role=${role}`);
      } else {
        console.warn(
          `[Global Setup] ⚠️  Storage state saved but no Cognito tokens found for role=${role}`
        );
      }

      return idToken;
    } catch (error) {
      console.error(`[Global Setup] ✗ Failed to set up auth state for role=${role}:`, error);
      return '';
    } finally {
      await browser.close();
    }
  }

  const homedir = os.homedir();
  const batbernDir = path.join(homedir, '.batbern');

  // ── ORGANIZER (required) ──────────────────────────────────────────────────
  // Write to legacy .playwright-auth-state.json for backward compat with chromium project
  const organizerIdToken = await setupRoleAuth(
    'organizer',
    path.join(batbernDir, `${testEnv}-organizer.json`),
    '.playwright-auth-state.json',
    path.join(batbernDir, `${testEnv}.json`) // fallback to legacy file
  );

  if (!organizerIdToken) {
    console.log(
      '[Global Setup] ⚠️  Organizer auth not configured — tests requiring auth will fail'
    );
    console.log(`[Global Setup] Run: ./scripts/auth/get-token.sh ${testEnv} <email> <password>`);
  } else {
    // Export AUTH_TOKEN from organizer (existing behavior for API integration tests)
    process.env.AUTH_TOKEN = organizerIdToken;
    console.log('[Global Setup] ✓ AUTH_TOKEN exported to environment (organizer)');
  }

  // ── SPEAKER (optional) ────────────────────────────────────────────────────
  await setupRoleAuth(
    'speaker',
    path.join(batbernDir, `${testEnv}-speaker.json`),
    '.playwright-auth-speaker.json'
  );

  // ── PARTNER (optional) ────────────────────────────────────────────────────
  await setupRoleAuth(
    'partner',
    path.join(batbernDir, `${testEnv}-partner.json`),
    '.playwright-auth-partner.json'
  );
}

export default globalSetup;
