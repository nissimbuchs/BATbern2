/**
 * Regression repro — perf/public-homepage-followup #2 config-gate race.
 *
 * The config gate was decoupled from bootstrap: runtime config (Cognito pool/client IDs)
 * now loads AFTER first paint via ConfigProvider. AuthProvider's session restore used to
 * run on mount regardless, racing the `GET /api/v1/config` round-trip; it ran against an
 * unconfigured Amplify, resolved to "no user", and bounced authenticated users to the
 * public login ("Welcome Back"). On real staging the network latency made this fire ~100%
 * of the time, so every authenticated @gate smoke test failed (run 26837753013).
 *
 * Against localhost the config call is near-instant, so the race does NOT reproduce on its
 * own — this spec WIDENS the window by delaying `/api/v1/config`, making the failure
 * deterministic locally. With the fix (AuthProvider gates restore on config) the organizer
 * stays authenticated and the admin tab the staging gate waited for becomes visible.
 *
 * Runs in the `chromium` (organizer) project, which seeds the organizer storageState.
 */

import { test, expect } from '@playwright/test';

const ADMIN_URL = '/organizer/admin';
const CONFIG_DELAY_MS = 3_000; // comfortably longer than React mount + first restore attempt

test.describe('Auth bootstrap survives a slow runtime-config fetch @regression', () => {
  test('authenticated organizer is NOT bounced to login when /api/v1/config is delayed', async ({
    page,
  }) => {
    // Widen the bootstrap race: hold the runtime-config response so AuthProvider would,
    // pre-fix, restore the session before Amplify is configured.
    await page.route('**/api/v1/config', async (route) => {
      await new Promise((resolve) => setTimeout(resolve, CONFIG_DELAY_MS));
      await route.continue();
    });

    await page.goto(`${ADMIN_URL}?tab=3`);

    // Pre-fix failure mode: the public login screen rendered instead of the admin page.
    await expect(
      page.getByRole('heading', { name: 'Welcome Back' }),
      'must not fall back to the public login screen during the config window'
    ).toHaveCount(0);

    // The exact assertion the staging @gate failed on: the admin Email Templates tab is
    // reachable, i.e. the stored session was restored once config arrived.
    await expect(page.getByTestId('email-templates-tab')).toBeVisible({ timeout: 20_000 });
  });
});
