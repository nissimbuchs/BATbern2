/**
 * E2E: Archive Browsing (hardened — docs/plans/playwright-staging-hardening.md, slice 7 / PR 8)
 *
 * Public, read-only — no auth, no mutations, no cleanup owed. data-testid locators only.
 * Tagged @gate + @smoke: the public archive is the core visitor-facing surface, so its
 * render/browse path is a high-value, zero-flake-risk per-deploy gate (promoted 2026-06-14).
 *
 * Rewritten against the REAL ArchivePage implementation. Removed dead test (logged in PR):
 *  - "should handle empty archive state": unreachable on a populated prod archive — there is
 *    no public-UI path to force zero events, and mocking an empty API response is out of
 *    scope for a staging-deployed gate. The archive-empty-state testid exists for a future
 *    mocked unit test.
 */

import { test, expect } from '@playwright/test';

test.describe('Archive Browsing', { tag: ['@gate', '@smoke'] }, () => {
  test('renders the archive shell with event cards', async ({ page }) => {
    await page.goto('/archive');

    await expect(page.getByTestId('archive-page-title')).toBeVisible();
    await expect(page.getByTestId('event-cards-container')).toBeVisible();

    const firstCard = page.getByTestId('event-card').first();
    await expect(firstCard).toBeVisible();
    await expect(firstCard.getByTestId('event-card-title')).toBeVisible();
    await expect(firstCard.getByTestId('event-card-date')).toBeVisible();
  });

  test('toggles grid/list view and persists the choice', async ({ page }) => {
    await page.goto('/archive');

    const container = page.getByTestId('event-cards-container');
    await expect(container).toBeVisible();
    // Desktop default is grid (Playwright Desktop Chrome viewport is ≥1024px).
    await expect(container).toHaveAttribute('data-view-mode', 'grid');

    await page.getByTestId('view-toggle-list').click();
    await expect(container).toHaveAttribute('data-view-mode', 'list');

    // Persisted under the real localStorage key (archive-view-mode, not archiveViewMode).
    await expect
      .poll(() => page.evaluate(() => localStorage.getItem('archive-view-mode')))
      .toBe('list');

    await page.reload();
    await expect(page.getByTestId('event-cards-container')).toHaveAttribute(
      'data-view-mode',
      'list'
    );
  });

  test('shows the load-progress indicator', async ({ page }) => {
    await page.goto('/archive');

    const progress = page.getByTestId('events-progress');
    await expect(progress).toBeVisible();
    // Assert digits only. The string is currently hardcoded English ("<n> of <m> events" in
    // ArchivePage.tsx) which is itself an i18n gap on a public page; asserting the shape
    // rather than the word means this spec survives that being fixed (#955).
    await expect(progress).toHaveText(/\d+\D+\d+/);
  });

  test('hides the desktop view toggle on a mobile viewport', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 667 });
    await page.goto('/archive');

    await expect(page.getByTestId('event-cards-container')).toBeVisible();
    // The grid/list toggle is desktop-only (rendered only when !isMobile, i.e. width ≥1024px).
    await expect(page.getByTestId('view-toggle-grid')).toHaveCount(0);
  });
});
