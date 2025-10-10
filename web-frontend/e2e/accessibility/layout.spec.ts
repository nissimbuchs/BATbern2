/**
 * Layout Accessibility E2E Tests
 * Story 1.17 AC10 - Focus Management and Contrast
 *
 * Tests for focus indicators, color contrast, and responsive layout accessibility
 */

import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

test.describe('Layout Accessibility (WCAG 2.1 AA)', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/dashboard');
  });

  test('should have no accessibility violations on base layout', async ({ page }) => {
    const accessibilityScanResults = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .analyze();

    expect(accessibilityScanResults.violations).toEqual([]);
  });

  test('should have visible focus indicators on all interactive elements', async ({ page }) => {
    // Get all focusable elements
    const focusableElements = await page
      .locator('a, button, input, select, textarea, [tabindex]:not([tabindex="-1"])')
      .all();

    for (const element of focusableElements) {
      // Focus the element
      await element.focus();

      // Check if element has visible focus indicator
      const hasOutline = await element.evaluate((el) => {
        const styles = window.getComputedStyle(el);
        return (
          styles.outline !== 'none' ||
          styles.outlineWidth !== '0px' ||
          styles.boxShadow !== 'none' ||
          el.classList.contains('focus-visible')
        );
      });

      expect(hasOutline).toBe(true);
    }
  });

  test('should meet 4.5:1 contrast ratio for normal text', async ({ page }) => {
    const accessibilityScanResults = await new AxeBuilder({ page }).withTags(['wcag2aa']).analyze();

    const contrastViolations = accessibilityScanResults.violations.filter(
      (violation) => violation.id === 'color-contrast'
    );

    expect(contrastViolations).toHaveLength(0);
  });

  test('should maintain responsive layout accessibility on mobile', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 667 });

    const accessibilityScanResults = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa'])
      .analyze();

    expect(accessibilityScanResults.violations).toEqual([]);
  });

  test('should maintain responsive layout accessibility on tablet', async ({ page }) => {
    await page.setViewportSize({ width: 768, height: 1024 });

    const accessibilityScanResults = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa'])
      .analyze();

    expect(accessibilityScanResults.violations).toEqual([]);
  });

  test('should have proper heading hierarchy', async ({ page }) => {
    // Get all headings
    const headings = await page.locator('h1, h2, h3, h4, h5, h6').all();

    let previousLevel = 0;
    for (const heading of headings) {
      const tagName = await heading.evaluate((el) => el.tagName.toLowerCase());
      const level = parseInt(tagName.charAt(1));

      // Heading levels should not skip (e.g., h1 -> h3 is invalid)
      if (previousLevel > 0) {
        expect(level - previousLevel).toBeLessThanOrEqual(1);
      }

      previousLevel = level;
    }

    // Page should have exactly one h1
    const h1Count = await page.locator('h1').count();
    expect(h1Count).toBe(1);
  });

  test('should have descriptive page title', async ({ page }) => {
    const title = await page.title();
    expect(title).toBeTruthy();
    expect(title.length).toBeGreaterThan(0);
    expect(title).not.toBe('Vite + React + TS'); // Default Vite title
  });

  test('should restore focus after modal close', async ({ page }) => {
    // Find a button that opens a modal/dialog
    const triggerButton = page.getByRole('button').first();
    await triggerButton.focus();
    await triggerButton.click();

    // Wait for modal to open
    const modal = page.locator('[role="dialog"], [role="alertdialog"]');
    if ((await modal.count()) > 0) {
      // Close modal (Escape key)
      await page.keyboard.press('Escape');

      // Focus should return to trigger button
      await expect(triggerButton).toBeFocused();
    }
  });

  test('should support zoom up to 200% without horizontal scroll', async ({ page }) => {
    // Set viewport and zoom
    await page.setViewportSize({ width: 1920, height: 1080 });

    // Zoom to 200%
    await page.evaluate(() => {
      (document.body.style as any).zoom = '200%';
    });

    // Check for horizontal scrollbar
    const hasHorizontalScroll = await page.evaluate(() => {
      return document.documentElement.scrollWidth > document.documentElement.clientWidth;
    });

    expect(hasHorizontalScroll).toBe(false);
  });

  test('should have proper lang attribute', async ({ page }) => {
    const lang = await page.locator('html').getAttribute('lang');
    expect(lang).toBeTruthy();
    expect(['en', 'de', 'fr', 'it']).toContain(lang);
  });
});
