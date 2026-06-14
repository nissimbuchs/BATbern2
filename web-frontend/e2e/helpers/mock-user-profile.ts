/**
 * Force the authenticated user's UI language to English for E2E determinism.
 * docs/plans/playwright-staging-hardening.md
 *
 * ── Why this exists ─────────────────────────────────────────────────────────────────────
 * `LanguageSync` reads `GET /api/v1/users/me` `preferences.language` and flips the whole UI
 * to that locale on load. Several test users carry a German preference, which breaks specs
 * that assert English copy. We pin the UI to English by intercepting `/users/me` and forcing
 * `preferences.language = 'en'`.
 *
 * ── Why a PASS-THROUGH (not a hand-rolled stub) ─────────────────────────────────────────
 * The original specs returned a *hardcoded minimal* body (`{id,email,preferences}`) with no
 * `role` and no `termsAcceptedAt`. That silently broke once `ProtectedRoute` (Story 12.11)
 * began redirecting any user whose hydrated `termsAcceptedAt === null` to
 * `/profile?onboarding=1`, and once role-gating started reading `user.roles`/`user.role`.
 * A stub that omits those fields lands every organizer/partner spec on the onboarding page
 * instead of the target screen — the exact divergence between local dev (real profile, works)
 * and the mocked test run (stub, redirects). Fetching the REAL response and overriding only
 * `preferences.language` keeps the mock correct against whatever shape `/users/me` evolves to
 * (role, roles, termsAcceptedAt, emailVerified, avatar, …) — "rewrite-to-reality".
 */

import type { Page } from '@playwright/test';

export async function forceUserProfileLanguage(
  page: Page,
  language: 'en' | 'de' | 'fr' | 'it' = 'en'
): Promise<void> {
  // Match the bare profile endpoint with or without a query string, but NOT its
  // sub-resources (`/users/me/additional-emails`, `/users/me/picture`, …): `me` must be
  // followed by end-of-URL or a `?`. Playwright runs the most-recently-registered matching
  // route first, so a per-test override registered after a beforeEach default wins.
  await page.route(/\/api\/v1\/users\/me(\?|$)/, async (route) => {
    if (route.request().method() !== 'GET') {
      await route.continue();
      return;
    }
    try {
      const response = await route.fetch();
      const body = await response.json();
      body.preferences = { ...(body.preferences ?? {}), language };
      await route.fulfill({ response, json: body });
    } catch {
      // Fail-open: if the real call can't be replayed, let it through unmodified rather
      // than blocking the page on a broken mock.
      await route.continue();
    }
  });
}

/** Convenience wrapper: pin the authenticated user's UI language to English. */
export async function forceEnglishUserProfile(page: Page): Promise<void> {
  await forceUserProfileLanguage(page, 'en');
}
