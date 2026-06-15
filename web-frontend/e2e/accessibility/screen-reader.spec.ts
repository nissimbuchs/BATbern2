/**
 * Screen Reader Accessibility E2E — slice 13 / cross-cutting (plan §C)
 * docs/plans/playwright-staging-hardening.md
 * Story 1.17 AC10 — ARIA live regions and announcements (real-browser axe).
 *
 * Rewritten 2026-05-31 to reality + the quality bar: navigates via `/dashboard` (redirect shim →
 * `/organizer/events`) + `waitForLoadState('networkidle')` so the authenticated shell settles
 * before asserting. a11y locators (getByRole / axe) are exempt from the testid-only rule.
 * DELETED: the two fictional skips (`should announce form errors` — login validates on blur not
 * submit; `should support high contrast mode` — forced-colors support never built) and the
 * `should announce loading states` probe (clicked `getByRole('button').first()` then asserted
 * nothing when no loading region appeared — zero-assertion, quality-bar #3). Route-change test
 * now targets the nav landmark via getByRole('navigation') (the old `nav[aria-label="main
 * navigation"]` CSS match relied on an EN-only translated label).
 */

import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

test.describe('Screen Reader Accessibility (WCAG 2.1 AA)', { tag: '@gate' }, () => {
  // `/dashboard` redirects organizers to `/organizer/events`; `networkidle` lets the redirect +
  // the authenticated shell (aria-live regions, nav links, icon buttons) finish rendering.
  test.beforeEach(async ({ page }) => {
    await page.goto('/dashboard');
    await page.waitForLoadState('networkidle');
  });

  test('should have proper ARIA live regions for dynamic content', async ({ page }) => {
    // Web-first wait: the authenticated shell (and its aria-live notification badge) hydrates
    // after the /dashboard redirect; `.all()` snapshots immediately and would otherwise count 0.
    await expect(page.locator('[aria-live]').first()).toBeAttached({ timeout: 15_000 });
    const liveRegions = await page.locator('[aria-live]').all();
    expect(liveRegions.length).toBeGreaterThan(0);

    for (const region of liveRegions) {
      const ariaLive = await region.getAttribute('aria-live');
      expect(['polite', 'assertive', 'off']).toContain(ariaLive);
    }
  });

  test('should have descriptive labels for all form inputs', async ({ page, context }) => {
    // Inspect the public login form — a deterministic page with first-class labelled controls.
    await context.clearCookies();
    await page.evaluate(() => localStorage.clear());
    await page.goto('/login');
    await page.waitForLoadState('networkidle');

    await expect(page.getByRole('textbox', { name: /email|e-mail/i })).toBeVisible();
    await expect(page.getByRole('textbox', { name: /password|passwort/i })).toBeVisible();
    await expect(
      page.getByRole('checkbox', { name: /remember|angemeldet bleiben/i })
    ).toBeVisible();
    await expect(page.getByRole('combobox', { name: /language/i })).toBeVisible();
  });

  test('should have visually hidden screen reader text for icons', async ({ page }) => {
    // Wait for the authenticated header (icon-only buttons) to render before snapshotting.
    await expect(page.locator('button:has(svg)').first()).toBeVisible({ timeout: 15_000 });
    const allButtons = await page.locator('button:has(svg)').all();

    const iconButtons = [];
    for (const button of allButtons) {
      const textContent = await button.textContent();
      if (!textContent || textContent.trim().length === 0) {
        iconButtons.push(button);
      }
    }
    // The header alone renders several icon-only buttons (notifications, user menu, …).
    expect(iconButtons.length).toBeGreaterThan(0);

    for (const button of iconButtons) {
      const ariaLabel = await button.getAttribute('aria-label');
      const ariaLabelledBy = await button.getAttribute('aria-labelledby');
      expect(ariaLabel !== null || ariaLabelledBy !== null).toBe(true);
    }
  });

  test('should have proper table accessibility', async ({ page }) => {
    const tables = await page.locator('table').all();

    for (const table of tables) {
      const caption = await table.locator('caption').count();
      const ariaLabel = await table.getAttribute('aria-label');
      const ariaLabelledBy = await table.getAttribute('aria-labelledby');
      expect(caption > 0 || ariaLabel !== null || ariaLabelledBy !== null).toBe(true);

      const headers = await table.locator('th').all();
      expect(headers.length).toBeGreaterThan(0);
      for (const header of headers) {
        const scope = await header.getAttribute('scope');
        if (scope) {
          expect(['col', 'row', 'colgroup', 'rowgroup']).toContain(scope);
        }
      }
    }
  });

  test('should handle notification announcements', async ({ page }) => {
    const badge = page.locator('[aria-live="polite"]').first();
    await expect(badge).toBeAttached();

    const srDescription = page.locator('#notification-badge-description');
    if ((await srDescription.count()) > 0) {
      const descText = await srDescription.textContent();
      expect(descText).toMatch(/\d+.*unread notification/i);
    }
  });

  test('should announce route changes to screen readers', async ({ page }) => {
    const initialUrl = page.url();

    const nav = page.getByRole('navigation').first();
    // Wait for the nav to render its links before snapshotting (post-redirect hydration).
    await expect(nav.getByRole('link').first()).toBeVisible({ timeout: 15_000 });
    const links = await nav.getByRole('link').all();
    expect(links.length).toBeGreaterThan(0);
    // Click the last nav link (avoids re-clicking the current page's own link).
    await links[links.length - 1].click();
    await page.waitForLoadState('networkidle');

    const newUrl = page.url();
    const newTitle = await page.title();
    expect(newTitle).toBeTruthy();
    expect(newUrl !== initialUrl || newTitle !== 'BATbern Platform').toBe(true);
  });

  test('should have no ARIA violations', async ({ page }) => {
    const results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .analyze();
    const ariaViolations = results.violations.filter((v) => v.id.includes('aria'));
    expect(ariaViolations).toEqual([]);
  });
});
