/**
 * Navigation Accessibility E2E Tests
 * Story 1.17 AC10 - WCAG 2.1 AA Compliance
 *
 * These tests replace jsdom-based accessibility tests with real browser validation
 * to address Quinn's TEST-001 issue.
 */

import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

test.describe('Navigation Accessibility (WCAG 2.1 AA)', () => {
  test.beforeEach(async ({ page }) => {
    // Mock authentication for organizer role
    await page.goto('/login');
    // TODO: Add proper login flow once authentication is set up
  });

  test('should have no accessibility violations on navigation', async ({ page }) => {
    await page.goto('/dashboard');

    const accessibilityScanResults = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .analyze();

    expect(accessibilityScanResults.violations).toEqual([]);
  });

  test('should support keyboard navigation through menu items', async ({ page }) => {
    await page.goto('/dashboard');

    // Tab to first navigation item
    await page.keyboard.press('Tab');

    // Verify focus indicator is visible
    const focusedElement = await page.locator(':focus');
    await expect(focusedElement).toBeVisible();

    // Check focus has visible outline (CSS focus indicator)
    const outline = await focusedElement.evaluate((el) => window.getComputedStyle(el).outline);
    expect(outline).not.toBe('none');
  });

  test('should announce unread notification count to screen readers', async ({ page }) => {
    await page.goto('/dashboard');

    // Find notification badge
    const notificationBadge = page.locator('[aria-live="polite"]');

    // Verify aria-live region exists
    await expect(notificationBadge).toBeAttached();

    // Verify screen reader text
    const srText = page.locator('.sr-only, [class*="visually-hidden"]');
    const text = await srText.textContent();
    expect(text).toMatch(/\d+ unread notification/);
  });

  test('should have proper ARIA attributes on navigation elements', async ({ page }) => {
    await page.goto('/dashboard');

    // Check hamburger menu button (mobile)
    const menuButton = page.getByRole('button', { name: /menu/i });
    await expect(menuButton).toHaveAttribute('aria-label', 'menu');

    // Check notification button
    const notificationButton = page.getByRole('button', { name: /notifications/i });
    await expect(notificationButton).toHaveAttribute('aria-expanded');
    await expect(notificationButton).toHaveAttribute('aria-haspopup', 'true');

    // Check user menu button
    const userMenuButton = page.getByRole('button', { name: /user menu/i });
    await expect(userMenuButton).toHaveAttribute('aria-expanded');
    await expect(userMenuButton).toHaveAttribute('aria-haspopup', 'true');
  });

  test('should have semantic HTML landmarks', async ({ page }) => {
    await page.goto('/dashboard');

    // Verify header landmark
    const header = page.locator('header');
    await expect(header).toBeAttached();

    // Verify main landmark
    const main = page.locator('main');
    await expect(main).toBeAttached();

    // Verify navigation landmark
    const nav = page.locator('nav');
    await expect(nav).toBeAttached();
  });

  test('should have skip to main content link', async ({ page }) => {
    await page.goto('/dashboard');

    // Tab to first element (should be skip link)
    await page.keyboard.press('Tab');

    const skipLink = await page.locator(':focus');
    const text = await skipLink.textContent();
    expect(text?.toLowerCase()).toContain('skip to main content');

    // Clicking skip link should move focus to main content
    await skipLink.click();
    const mainContent = page.locator('main');
    await expect(mainContent).toBeFocused();
  });

  test('should meet color contrast requirements', async ({ page }) => {
    await page.goto('/dashboard');

    // Run axe test specifically for color contrast
    const accessibilityScanResults = await new AxeBuilder({ page })
      .withTags(['wcag2aa'])
      .include('nav')
      .analyze();

    const contrastViolations = accessibilityScanResults.violations.filter(
      (violation) => violation.id === 'color-contrast'
    );

    expect(contrastViolations).toEqual([]);
  });

  test('should handle mobile drawer accessibility', async ({ page }) => {
    // Set mobile viewport
    await page.setViewportSize({ width: 375, height: 667 });
    await page.goto('/dashboard');

    // Open mobile drawer
    const menuButton = page.getByRole('button', { name: /menu/i });
    await menuButton.click();

    // Verify drawer is announced to screen readers
    const drawer = page.locator('[role="presentation"]').first();
    await expect(drawer).toBeVisible();

    // Verify drawer can be closed with Escape key
    await page.keyboard.press('Escape');
    await expect(drawer).not.toBeVisible();
  });

  test('should trap focus within open notification dropdown', async ({ page }) => {
    await page.goto('/dashboard');

    // Open notification dropdown
    const notificationButton = page.getByRole('button', { name: /notifications/i });
    await notificationButton.click();

    // Verify dropdown menu is visible
    const dropdown = page.getByRole('menu', { name: /notifications menu/i });
    await expect(dropdown).toBeVisible();

    // Tab through items - focus should stay within dropdown
    await page.keyboard.press('Tab');
    const focusedElement = await page.locator(':focus');

    // Verify focused element is inside dropdown
    const isInDropdown = await focusedElement.evaluate(
      (el, dropdownEl) => {
        return dropdownEl?.contains(el) ?? false;
      },
      await dropdown.elementHandle()
    );

    expect(isInDropdown).toBe(true);
  });
});
