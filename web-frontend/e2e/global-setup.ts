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
      baseURL: 'https://www.batbern.ch',
      apiURL: 'https://api.batbern.ch',
    },
    production: {
      baseURL: 'https://batbern.ch',
      apiURL: 'https://api.batbern.ch',
    },
    // Beta = frontend-only canary on the PRODUCTION backend (see playwright.config.ts).
    beta: {
      baseURL: 'https://beta.batbern.ch',
      apiURL: 'https://api.batbern.ch',
    },
  }[testEnv as 'development' | 'staging' | 'production' | 'beta'];

  if (!envConfig) {
    throw new Error(`Invalid TEST_ENV: ${testEnv}`);
  }

  // Beta shares the PRODUCTION Cognito, so its auth tokens live in the staging token files.
  const tokenEnv = testEnv === 'beta' ? 'staging' : testEnv;

  console.log(`[Global Setup] Environment: ${testEnv}`);
  console.log(`[Global Setup] Base URL: ${envConfig.baseURL}`);

  const fs = await import('fs');
  const os = await import('os');
  const path = await import('path');
  const { execFileSync } = await import('child_process');

  /**
   * Refresh a role's token if it is expired or about to be, then report whether it is usable.
   *
   * Why this exists. `scripts/ci/run-bruno-tests.sh` and `scripts/ci/run-playwright-tests.sh`
   * both call `scripts/auth/refresh-token.sh` before loading tokens. This file did not — it
   * checked only that the token FILE existed and that idToken/accessToken/refreshToken were
   * present, never that any of them was still valid. So `cd web-frontend && npm run test:e2e`
   * (the command CLAUDE.md documents) went straight here, injected a stale idToken into
   * localStorage, and the specs failed with 401s and onboarding redirects — with nothing
   * anywhere saying "your token expired 11 days ago". Observed 2026-08-26: every file in
   * ~/.batbern/ had lapsed on 2026-08-15 and the only symptom was a 401.
   *
   * Cognito ID tokens live 24h; the refresh tokens outlive them by weeks, so this almost always
   * succeeds with no credentials. Failure is non-fatal: the caller still gets a clear message
   * naming the actual expiry, which is the part that was missing.
   */
  function ensureFreshToken(role: string, tokenFile: string): boolean {
    const readExp = (): number | null => {
      try {
        const { idToken } = JSON.parse(fs.readFileSync(tokenFile, 'utf8'));
        if (!idToken) return null;
        const claims = JSON.parse(Buffer.from(idToken.split('.')[1], 'base64url').toString());
        return typeof claims.exp === 'number' ? claims.exp : null;
      } catch {
        return null;
      }
    };

    const now = Math.floor(Date.now() / 1000);
    const exp = readExp();

    // Unreadable or unparseable: leave it to the caller's existing validation.
    if (exp === null) return true;

    // 120s of slack so a token that expires mid-suite is renewed up front rather than
    // half-way through a spec.
    if (exp - now > 120) return true;

    const ago = Math.round((now - exp) / 3600);
    console.log(
      `[Global Setup] Token for role=${role} ${exp < now ? `expired ~${ago}h ago` : 'expires imminently'} — refreshing...`
    );

    // Locate the repo root by walking up for the script itself. NOT `__dirname`: this module is
    // loaded as ESM, where __dirname is undefined — the first version of this used it and the
    // refresh silently failed with "__dirname is not defined" while the detection above still
    // reported the token as expired, which looked like a broken refresh script. And NOT a fixed
    // `../..` from cwd either, since Playwright can be invoked from the repo root or from
    // web-frontend/.
    const findRepoRoot = (): string | null => {
      let dir = process.cwd();
      for (let i = 0; i < 6; i++) {
        if (fs.existsSync(path.join(dir, 'scripts', 'auth', 'refresh-token.sh'))) return dir;
        const parent = path.dirname(dir);
        if (parent === dir) break;
        dir = parent;
      }
      return null;
    };

    try {
      const repoRoot = findRepoRoot();
      if (!repoRoot) {
        throw new Error('could not locate scripts/auth/refresh-token.sh from ' + process.cwd());
      }
      execFileSync(path.join(repoRoot, 'scripts', 'auth', 'refresh-token.sh'), [tokenEnv, role], {
        cwd: repoRoot,
        stdio: 'pipe',
        timeout: 60_000,
      });
    } catch (err) {
      console.log(
        `[Global Setup] ⚠️  refresh-token.sh failed for role=${role}: ${err instanceof Error ? err.message.split('\n')[0] : String(err)}`
      );
    }

    const after = readExp();
    if (after !== null && after - now > 120) {
      console.log(`[Global Setup] ✓ Token for role=${role} refreshed`);
      return true;
    }

    // The message that was missing. Say WHEN it expired, not just that auth failed.
    console.log(
      `[Global Setup] ❌ Token for role=${role} is EXPIRED and could not be refreshed` +
        (after !== null ? ` (exp ${new Date(after * 1000).toISOString()})` : '')
    );
    console.log(
      `[Global Setup]    Re-authenticate: ./scripts/auth/get-token.sh ${tokenEnv} <email> <password> ${role}`
    );
    console.log(`[Global Setup]    Or all roles at once: make setup-test-users ENV=${tokenEnv}`);
    return false;
  }

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

    // Refresh before reading, so an expired-but-refreshable token is a non-event rather than a
    // wall of 401s (see ensureFreshToken).
    ensureFreshToken(role, resolvedTokenFile);

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
    path.join(batbernDir, `${tokenEnv}-organizer.json`),
    '.playwright-auth-state.json',
    path.join(batbernDir, `${tokenEnv}.json`) // fallback to legacy file
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
    path.join(batbernDir, `${tokenEnv}-speaker.json`),
    '.playwright-auth-speaker.json'
  );

  // ── PARTNER (optional) ────────────────────────────────────────────────────
  await setupRoleAuth(
    'partner',
    path.join(batbernDir, `${tokenEnv}-partner.json`),
    '.playwright-auth-partner.json'
  );
}

export default globalSetup;
