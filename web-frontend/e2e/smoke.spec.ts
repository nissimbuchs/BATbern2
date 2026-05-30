/**
 * Smoke spec — the seed of the per-deploy @smoke gate (plan §A6)
 * docs/plans/playwright-staging-hardening.md
 *
 * PR 1 ships exactly ONE trivial smoke spec so the `playwright-tests` CI job has something
 * to run green ×2 against deployed staging (plan "PR 1 exit criteria"). It is deliberately:
 *   • PUBLIC + READ-ONLY  — no auth, no mutations, so no cleanup is owed (quality bar #4).
 *   • data-testid only    — no text/CSS/positional locators (quality bar #1).
 *   • a real app-boot proof — loading `/archive` and seeing its shell render confirms the
 *     SPA booted, fetched runtime config from the backend, routed, and CloudFront served a
 *     coherent asset bundle. That makes it a meaningful edge-readiness canary, not a no-op.
 *
 * Tags drive run-scope routing (plan §A6): @smoke = per-deploy blocking gate (once flipped
 * in PR 15); @gate = full nightly suite. Every @smoke test is also @gate.
 */

import { test, expect } from '@playwright/test';

test.describe('Smoke — public archive shell', () => {
  test(
    'archive page boots and renders its shell',
    { tag: ['@smoke', '@gate'] },
    async ({ page }) => {
      await page.goto('/archive');

      // The events-progress region is rendered unconditionally once the archive shell
      // mounts — its presence proves the SPA booted, routed, and fetched runtime config.
      await expect(page.getByTestId('events-progress')).toBeVisible({ timeout: 15_000 });

      // The grid/list view toggle confirms the interactive archive controls rendered
      // (desktop viewport — Playwright's default Desktop Chrome).
      await expect(page.getByTestId('view-toggle-grid')).toBeVisible();
    }
  );
});
