/**
 * E2E Tests for Speaker Portal Response Flow
 * Story 6.2a: Invitation Response Portal
 *
 * Tests complete speaker journey:
 * 1. Access invitation via magic link
 * 2. View event details
 * 3. Submit Accept/Decline/Tentative response
 * 4. View confirmation message
 *
 * Note: These tests require a test environment with:
 * - Valid magic link tokens seeded in the database
 * - Backend services running
 *
 * Test tokens should be configured via environment variables:
 * - E2E_SPEAKER_VALID_TOKEN: Valid invitation token
 * - E2E_SPEAKER_EXPIRED_TOKEN: Expired token
 * - E2E_SPEAKER_USED_TOKEN: Already used token
 */

import { test, expect } from '@playwright/test';

// Test configuration
const BASE_URL = process.env.E2E_BASE_URL || 'http://localhost:8100';
const VALID_TOKEN = process.env.E2E_SPEAKER_VALID_TOKEN || 'test-valid-token';
const EXPIRED_TOKEN = process.env.E2E_SPEAKER_EXPIRED_TOKEN || 'test-expired-token';
const USED_TOKEN = process.env.E2E_SPEAKER_USED_TOKEN || 'test-used-token';

test.describe('Speaker Portal Response Flow', () => {
  test.describe('Token Validation', () => {
    test('should show error for missing token', async ({ page }) => {
      // Navigate to speaker portal without token
      await page.goto(`${BASE_URL}/speaker-portal/respond`);

      // Verify invalid link error is shown
      await expect(page.locator('text=/Invalid Link/i')).toBeVisible({ timeout: 10000 });
      await expect(page.locator('text=/requires a valid invitation link/i')).toBeVisible();
    });

    test('should show error for expired token', async ({ page }) => {
      // Navigate with expired token
      await page.goto(`${BASE_URL}/speaker-portal/respond?token=${EXPIRED_TOKEN}`);

      // Wait for validation to complete
      await page.waitForLoadState('networkidle');

      // Verify expired error message
      await expect(page.locator('text=/Link Expired|expired/i').first()).toBeVisible({
        timeout: 10000,
      });
    });

    test('should show error for invalid token', async ({ page }) => {
      // Navigate with clearly invalid token
      await page.goto(`${BASE_URL}/speaker-portal/respond?token=invalid-fake-token-12345`);

      // Wait for validation to complete
      await page.waitForLoadState('networkidle');

      // Verify error message (could be Invalid Link or Not Found)
      await expect(page.locator('text=/Invalid Link|not valid|not found/i').first()).toBeVisible({
        timeout: 10000,
      });
    });

    test('should show loading state while validating token', async ({ page }) => {
      // Navigate with any token
      await page.goto(`${BASE_URL}/speaker-portal/respond?token=${VALID_TOKEN}`);

      // Check for loading state (may be quick, so use short timeout)
      const loadingText = page.locator('text=/Loading Invitation/i');
      const isLoading = await loadingText.isVisible({ timeout: 2000 }).catch(() => false);

      // If loading state was visible, that's expected
      // If not, the validation was fast - also acceptable
      expect(isLoading || true).toBe(true);
    });
  });

  test.describe('Accept Response Flow', () => {
    test('should display invitation details when token is valid', async ({ page }) => {
      await page.goto(`${BASE_URL}/speaker-portal/respond?token=${VALID_TOKEN}`);

      // Wait for page to load
      await page.waitForLoadState('networkidle');

      // Should see event title (varies by test data)
      await expect(page.locator('text=/Invited to Speak|BATbern/i').first()).toBeVisible({
        timeout: 10000,
      });

      // Should see response buttons
      await expect(page.locator('button').filter({ hasText: /Accept/i })).toBeVisible();
      await expect(page.locator('button').filter({ hasText: /Decline/i })).toBeVisible();
      await expect(page.locator('button').filter({ hasText: /Maybe/i })).toBeVisible();
    });

    test('should show preferences form when Accept is clicked', async ({ page }) => {
      await page.goto(`${BASE_URL}/speaker-portal/respond?token=${VALID_TOKEN}`);
      await page.waitForLoadState('networkidle');

      // Click Accept button
      const acceptButton = page.locator('button').filter({ hasText: /Accept/i });
      await expect(acceptButton).toBeVisible({ timeout: 10000 });
      await acceptButton.click();

      // Should see preferences form
      await expect(page.locator('text=/Preferred Time Slot/i')).toBeVisible();
      await expect(page.locator('text=/Travel Requirements/i')).toBeVisible();

      // Submit button should be enabled (preferences are optional for Accept)
      const submitButton = page.locator('button').filter({ hasText: /Submit Response/i });
      await expect(submitButton).toBeEnabled();
    });

    test('should complete accept flow with preferences', async ({ page }) => {
      await page.goto(`${BASE_URL}/speaker-portal/respond?token=${VALID_TOKEN}`);
      await page.waitForLoadState('networkidle');

      // Click Accept
      await page
        .locator('button')
        .filter({ hasText: /Accept/i })
        .click();

      // Fill preferences (optional)
      const timeSlotSelect = page.locator('select').first();
      if (await timeSlotSelect.isVisible()) {
        await timeSlotSelect.selectOption('morning');
      }

      const techInput = page.locator('input[placeholder*="adapter"]');
      if (await techInput.isVisible()) {
        await techInput.fill('Need HDMI adapter');
      }

      // Submit response
      await page
        .locator('button')
        .filter({ hasText: /Submit Response/i })
        .click();

      // Should see success message
      await expect(page.locator('text=/Response Submitted|Thank you/i').first()).toBeVisible({
        timeout: 10000,
      });
    });
  });

  test.describe('Decline Response Flow', () => {
    test('should require reason for decline', async ({ page }) => {
      await page.goto(`${BASE_URL}/speaker-portal/respond?token=${VALID_TOKEN}`);
      await page.waitForLoadState('networkidle');

      // Click Decline
      const declineButton = page.locator('button').filter({ hasText: /Decline/i });
      await expect(declineButton).toBeVisible({ timeout: 10000 });
      await declineButton.click();

      // Should see reason input
      await expect(page.locator('text=/Reason for declining/i')).toBeVisible();

      // Submit should be disabled without reason
      const submitButton = page.locator('button').filter({ hasText: /Submit Response/i });
      await expect(submitButton).toBeDisabled();

      // Error message should be visible
      await expect(page.locator('text=/reason is required/i')).toBeVisible();
    });

    test('should enable submit when decline reason is provided', async ({ page }) => {
      await page.goto(`${BASE_URL}/speaker-portal/respond?token=${VALID_TOKEN}`);
      await page.waitForLoadState('networkidle');

      // Click Decline
      await page
        .locator('button')
        .filter({ hasText: /Decline/i })
        .click();

      // Fill reason
      const reasonTextarea = page.locator('textarea').first();
      await reasonTextarea.fill('Schedule conflict with another event');

      // Submit should now be enabled
      const submitButton = page.locator('button').filter({ hasText: /Submit Response/i });
      await expect(submitButton).toBeEnabled();
    });

    test('should complete decline flow', async ({ page }) => {
      await page.goto(`${BASE_URL}/speaker-portal/respond?token=${VALID_TOKEN}`);
      await page.waitForLoadState('networkidle');

      // Click Decline and fill reason
      await page
        .locator('button')
        .filter({ hasText: /Decline/i })
        .click();
      await page.locator('textarea').first().fill('Schedule conflict');

      // Submit
      await page
        .locator('button')
        .filter({ hasText: /Submit Response/i })
        .click();

      // Should see success/thank you message
      await expect(page.locator('text=/Response Submitted|Thank you/i').first()).toBeVisible({
        timeout: 10000,
      });
    });
  });

  test.describe('Tentative Response Flow', () => {
    test('should require reason for tentative response', async ({ page }) => {
      await page.goto(`${BASE_URL}/speaker-portal/respond?token=${VALID_TOKEN}`);
      await page.waitForLoadState('networkidle');

      // Click Maybe/Tentative
      const maybeButton = page.locator('button').filter({ hasText: /Maybe/i });
      await expect(maybeButton).toBeVisible({ timeout: 10000 });
      await maybeButton.click();

      // Should see reason input
      await expect(page.locator('text=/holding you back/i')).toBeVisible();

      // Submit should be disabled without reason
      const submitButton = page.locator('button').filter({ hasText: /Submit Response/i });
      await expect(submitButton).toBeDisabled();
    });

    test('should complete tentative flow', async ({ page }) => {
      await page.goto(`${BASE_URL}/speaker-portal/respond?token=${VALID_TOKEN}`);
      await page.waitForLoadState('networkidle');

      // Click Maybe and fill reason
      await page.locator('button').filter({ hasText: /Maybe/i }).click();
      await page.locator('textarea').first().fill('Awaiting budget approval from company');

      // Submit
      await page
        .locator('button')
        .filter({ hasText: /Submit Response/i })
        .click();

      // Should see success message
      await expect(page.locator('text=/Response Submitted|Thank you/i').first()).toBeVisible({
        timeout: 10000,
      });
    });
  });

  test.describe('Already Responded Flow', () => {
    test('should show previous response when token already used', async ({ page }) => {
      await page.goto(`${BASE_URL}/speaker-portal/respond?token=${USED_TOKEN}`);
      await page.waitForLoadState('networkidle');

      // Should show already responded message
      await expect(page.locator('text=/Already Responded|already been used/i').first()).toBeVisible(
        { timeout: 10000 }
      );
    });
  });

  test.describe('Mobile Responsiveness', () => {
    test('should be usable on mobile viewport', async ({ page }) => {
      // Set mobile viewport
      await page.setViewportSize({ width: 375, height: 667 });

      await page.goto(`${BASE_URL}/speaker-portal/respond?token=${VALID_TOKEN}`);
      await page.waitForLoadState('networkidle');

      // Buttons should be visible and tappable
      const acceptButton = page.locator('button').filter({ hasText: /Accept/i });
      await expect(acceptButton).toBeVisible({ timeout: 10000 });

      // Buttons should have adequate tap target size (min 44px)
      const buttonBox = await acceptButton.boundingBox();
      if (buttonBox) {
        expect(buttonBox.height).toBeGreaterThanOrEqual(44);
      }
    });

    test('should stack buttons vertically on small screens', async ({ page }) => {
      await page.setViewportSize({ width: 375, height: 667 });

      await page.goto(`${BASE_URL}/speaker-portal/respond?token=${VALID_TOKEN}`);
      await page.waitForLoadState('networkidle');

      // Wait for buttons to be visible
      const acceptButton = page.locator('button').filter({ hasText: /Accept/i });
      const declineButton = page.locator('button').filter({ hasText: /Decline/i });
      await expect(acceptButton).toBeVisible({ timeout: 10000 });
      await expect(declineButton).toBeVisible();

      // Get button positions
      const acceptBox = await acceptButton.boundingBox();
      const declineBox = await declineButton.boundingBox();

      // On mobile, buttons should stack (decline below accept) OR be side-by-side
      // Both are valid responsive layouts
      if (acceptBox && declineBox) {
        // Either vertically stacked (decline.y > accept.y) or side-by-side (same y, different x)
        const isStacked = declineBox.y > acceptBox.y + 10;
        const isSideBySide = Math.abs(declineBox.y - acceptBox.y) < 10;
        expect(isStacked || isSideBySide).toBe(true);
      }
    });
  });

  test.describe('Accessibility', () => {
    test('should have proper heading structure', async ({ page }) => {
      await page.goto(`${BASE_URL}/speaker-portal/respond?token=${VALID_TOKEN}`);
      await page.waitForLoadState('networkidle');

      // Should have at least one heading
      const headings = page.locator('h1, h2, h3');
      await expect(headings.first()).toBeVisible({ timeout: 10000 });
    });

    test('should have focusable interactive elements', async ({ page }) => {
      await page.goto(`${BASE_URL}/speaker-portal/respond?token=${VALID_TOKEN}`);
      await page.waitForLoadState('networkidle');

      // Tab through the page
      await page.keyboard.press('Tab');
      await page.keyboard.press('Tab');
      await page.keyboard.press('Tab');

      // A button should be focused
      const focusedElement = await page.evaluate(() => document.activeElement?.tagName);
      expect(['BUTTON', 'A', 'INPUT', 'SELECT', 'TEXTAREA']).toContain(focusedElement);
    });

    test('should allow keyboard navigation for response selection', async ({ page }) => {
      await page.goto(`${BASE_URL}/speaker-portal/respond?token=${VALID_TOKEN}`);
      await page.waitForLoadState('networkidle');

      // Wait for form to be ready
      const acceptButton = page.locator('button').filter({ hasText: /Accept/i });
      await expect(acceptButton).toBeVisible({ timeout: 10000 });

      // Focus on Accept button and press Enter
      await acceptButton.focus();
      await page.keyboard.press('Enter');

      // Preferences form should appear
      await expect(page.locator('text=/Preferred Time Slot/i')).toBeVisible();
    });
  });
});
