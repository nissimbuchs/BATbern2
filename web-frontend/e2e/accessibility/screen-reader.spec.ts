/**
 * Screen Reader Accessibility E2E Tests
 * Story 1.17 AC10 - ARIA Live Regions and Announcements
 *
 * Tests for screen reader announcements and ARIA attributes
 */

import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

test.describe('Screen Reader Accessibility (WCAG 2.1 AA)', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/dashboard');
  });

  test('should have proper ARIA live regions for dynamic content', async ({ page }) => {
    // Check for aria-live regions
    const liveRegions = await page.locator('[aria-live]').all();
    expect(liveRegions.length).toBeGreaterThan(0);

    // Verify politeness levels are appropriate
    for (const region of liveRegions) {
      const ariaLive = await region.getAttribute('aria-live');
      expect(['polite', 'assertive', 'off']).toContain(ariaLive);
    }
  });

  test('should announce form errors to screen readers', async ({ page }) => {
    // Navigate to a form (login page)
    await page.goto('/login');

    // Submit empty form
    const submitButton = page.getByRole('button', { name: /sign in|login/i });
    await submitButton.click();

    // Check for error announcements
    const errorRegion = page.locator('[role="alert"], [aria-live="assertive"]');
    await expect(errorRegion).toBeVisible({ timeout: 2000 });

    // Verify error message content
    const errorText = await errorRegion.textContent();
    expect(errorText).toBeTruthy();
  });

  test('should have descriptive labels for all form inputs', async ({ page }) => {
    await page.goto('/login');

    const inputs = await page.locator('input').all();

    for (const input of inputs) {
      const ariaLabel = await input.getAttribute('aria-label');
      const ariaLabelledBy = await input.getAttribute('aria-labelledby');
      const id = await input.getAttribute('id');

      // Check if input has label via aria-label, aria-labelledby, or associated <label>
      const hasLabel =
        ariaLabel !== null ||
        ariaLabelledBy !== null ||
        (id !== null && (await page.locator(`label[for="${id}"]`).count()) > 0);

      expect(hasLabel).toBe(true);
    }
  });

  test('should announce loading states to screen readers', async ({ page }) => {
    // Trigger an async action (e.g., data fetch)
    const button = page.getByRole('button').first();
    await button.click();

    // Check for loading announcement
    const loadingRegion = page.locator('[aria-busy="true"], [aria-live][class*="loading"]');

    if ((await loadingRegion.count()) > 0) {
      // Verify loading state is announced
      const ariaLive = await loadingRegion.getAttribute('aria-live');
      expect(['polite', 'assertive']).toContain(ariaLive);
    }
  });

  test('should have visually hidden screen reader text for icons', async ({ page }) => {
    // Find icon-only buttons (buttons without visible text)
    const iconButtons = await page.locator('button:has(svg):not(:has-text(/[a-zA-Z]/))').all();

    for (const button of iconButtons) {
      const ariaLabel = await button.getAttribute('aria-label');
      const ariaLabelledBy = await button.getAttribute('aria-labelledby');

      // Icon buttons must have aria-label or aria-labelledby
      expect(ariaLabel !== null || ariaLabelledBy !== null).toBe(true);
    }
  });

  test('should have proper table accessibility', async ({ page }) => {
    // If there are tables on the page
    const tables = await page.locator('table').all();

    for (const table of tables) {
      // Check for caption or aria-label
      const caption = await table.locator('caption').count();
      const ariaLabel = await table.getAttribute('aria-label');
      const ariaLabelledBy = await table.getAttribute('aria-labelledby');

      expect(caption > 0 || ariaLabel !== null || ariaLabelledBy !== null).toBe(true);

      // Check for proper table headers
      const headers = await table.locator('th').all();
      expect(headers.length).toBeGreaterThan(0);

      // Verify scope attribute on headers
      for (const header of headers) {
        const scope = await header.getAttribute('scope');
        if (scope) {
          expect(['col', 'row', 'colgroup', 'rowgroup']).toContain(scope);
        }
      }
    }
  });

  test('should handle notification announcements', async ({ page }) => {
    // Click notification button to open dropdown
    const notificationButton = page.getByRole('button', { name: /notifications/i });
    await notificationButton.click();

    // Check for notification count announcement
    const badge = page.locator('[aria-live="polite"]');
    if ((await badge.count()) > 0) {
      const badgeText = await badge.textContent();
      expect(badgeText).toMatch(/\d+.*notification/i);
    }
  });

  test('should announce route changes to screen readers', async ({ page }) => {
    // Navigate to different route
    const link = page.getByRole('link').first();
    const linkText = await link.textContent();

    await link.click();
    await page.waitForLoadState('networkidle');

    // Check if route change is announced
    // This could be via document.title change or aria-live region
    const newTitle = await page.title();
    expect(newTitle).toBeTruthy();
    expect(newTitle).not.toBe('BATbern Platform'); // Should be more specific
  });

  test('should have no ARIA violations', async ({ page }) => {
    const accessibilityScanResults = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .analyze();

    // Filter for ARIA-specific violations
    const ariaViolations = accessibilityScanResults.violations.filter((violation) =>
      violation.id.includes('aria')
    );

    expect(ariaViolations).toEqual([]);
  });

  test('should support high contrast mode', async ({ page }) => {
    // Enable forced colors (high contrast mode simulation)
    await page.emulateMedia({ forcedColors: 'active' });

    // Run accessibility scan
    const accessibilityScanResults = await new AxeBuilder({ page }).withTags(['wcag2aa']).analyze();

    expect(accessibilityScanResults.violations).toEqual([]);
  });
});
