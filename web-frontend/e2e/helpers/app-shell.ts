/**
 * Wait for the authenticated app shell (BaseLayout) to finish rendering.
 * docs/plans/playwright-staging-hardening.md
 *
 * ── Why this exists (a real local-dev vs staging divergence) ────────────────────────────
 * BaseLayout renders the skip link, the AppHeader (nav links, icon buttons, the aria-live
 * notification badge) and `<main id="main-content">` only after the lazily-loaded
 * authenticated bundle resolves. On a production build (staging) that bundle is a single
 * pre-built chunk, so by the time `networkidle` fires the shell is already in the DOM. On
 * the Vite dev server modules stream in lazily and the app shows a "Loading" Suspense
 * fallback — `networkidle` (no network for 500ms) can fire DURING that fallback, before the
 * shell exists. Tests that then count `[aria-live]` regions / icon buttons / nav links, or
 * Tab to the skip link, see an empty page and fail ONLY on local dev.
 *
 * Anchor on `<main id="main-content">`: it is rendered by BaseLayout alongside the skip link
 * and AppHeader, so once it is attached the whole shell is present.
 */

import type { Page } from '@playwright/test';

export async function waitForAppShell(page: Page): Promise<void> {
  await page.locator('main#main-content').waitFor({ state: 'attached', timeout: 15000 });
}
