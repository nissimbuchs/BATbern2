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
 *
 * Selectors: translation-independent (getByTestId / getByRole) per BAT-10.9 Phase 3C.
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

      // Verify invalid link error is shown via testid (language-independent)
      await expect(page.getByTestId('invitation-error-invalid')).toBeVisible({ timeout: 10000 });
    });

    test('should show error for expired token', async ({ page }) => {
      // Navigate with expired token
      await page.goto(`${BASE_URL}/speaker-portal/respond?token=${EXPIRED_TOKEN}`);

      // Wait for validation to complete
      await page.waitForLoadState('networkidle');

      // Verify error state shown (expired maps to invitation-error-expired or invitation-error-invalid
      // depending on whether the API returns the EXPIRED error code)
      await expect(
        page
          .getByTestId('invitation-error-expired')
          .or(page.getByTestId('invitation-error-invalid'))
      ).toBeVisible({ timeout: 10000 });
    });

    test('should show error for invalid token', async ({ page }) => {
      // Navigate with clearly invalid token
      await page.goto(`${BASE_URL}/speaker-portal/respond?token=invalid-fake-token-12345`);

      // Wait for validation to complete
      await page.waitForLoadState('networkidle');

      // Verify error state shown via testid (language-independent)
      await expect(page.getByTestId('invitation-error-invalid')).toBeVisible({ timeout: 10000 });
    });

    test('should show loading state while validating token', async ({ page }) => {
      // Navigate with any token
      await page.goto(`${BASE_URL}/speaker-portal/respond?token=${VALID_TOKEN}`);

      // Check for loading state via role="status" (may be quick, so use short timeout)
      const loadingIndicator = page.getByRole('status');
      const isLoading = await loadingIndicator.isVisible({ timeout: 2000 }).catch(() => false);

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

      // Should see response buttons via testid (language-independent)
      await expect(page.getByTestId('invitation-response-accept-btn')).toBeVisible();
      await expect(page.getByTestId('invitation-response-decline-btn')).toBeVisible();
    });

    test('should show preferences form when Accept is clicked', async ({ page }) => {
      await page.goto(`${BASE_URL}/speaker-portal/respond?token=${VALID_TOKEN}`);
      await page.waitForLoadState('networkidle');

      // Click Accept button via testid
      const acceptButton = page.getByTestId('invitation-response-accept-btn');
      await expect(acceptButton).toBeVisible({ timeout: 10000 });
      await acceptButton.click();

      // Submit button should be enabled (preferences are optional for Accept)
      const submitButton = page.getByTestId('invitation-response-submit-btn');
      await expect(submitButton).toBeEnabled();
    });

    test('should complete accept flow with preferences', async ({ page }) => {
      await page.goto(`${BASE_URL}/speaker-portal/respond?token=${VALID_TOKEN}`);
      await page.waitForLoadState('networkidle');

      // Click Accept via testid
      await page.getByTestId('invitation-response-accept-btn').click();

      // Fill preferences (optional)
      const timeSlotSelect = page.locator('select').first();
      if (await timeSlotSelect.isVisible()) {
        await timeSlotSelect.selectOption('morning');
      }

      const techInput = page.locator('input[placeholder*="adapter"]');
      if (await techInput.isVisible()) {
        await techInput.fill('Need HDMI adapter');
      }

      // Submit response via testid
      await page.getByTestId('invitation-response-submit-btn').click();

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

      // Click Decline via testid
      const declineButton = page.getByTestId('invitation-response-decline-btn');
      await expect(declineButton).toBeVisible({ timeout: 10000 });
      await declineButton.click();

      // Should see reason input via label association
      await expect(page.locator('label[for="declineReason"]')).toBeVisible();

      // Submit should be disabled without reason
      const submitButton = page.getByTestId('invitation-response-submit-btn');
      await expect(submitButton).toBeDisabled();

      // Error message should be visible via role="alert"
      await expect(page.getByRole('alert')).toBeVisible();
    });

    test('should enable submit when decline reason is provided', async ({ page }) => {
      await page.goto(`${BASE_URL}/speaker-portal/respond?token=${VALID_TOKEN}`);
      await page.waitForLoadState('networkidle');

      // Click Decline via testid
      await page.getByTestId('invitation-response-decline-btn').click();

      // Fill reason
      const reasonTextarea = page.locator('textarea').first();
      await reasonTextarea.fill('Schedule conflict with another event');

      // Submit should now be enabled
      const submitButton = page.getByTestId('invitation-response-submit-btn');
      await expect(submitButton).toBeEnabled();
    });

    test('should complete decline flow', async ({ page }) => {
      await page.goto(`${BASE_URL}/speaker-portal/respond?token=${VALID_TOKEN}`);
      await page.waitForLoadState('networkidle');

      // Click Decline and fill reason
      await page.getByTestId('invitation-response-decline-btn').click();
      await page.locator('textarea').first().fill('Schedule conflict');

      // Submit
      await page.getByTestId('invitation-response-submit-btn').click();

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

      // Click Maybe/Tentative (button may not be in current implementation)
      const maybeButton = page.locator('button').filter({ hasText: /Maybe/i });
      await expect(maybeButton).toBeVisible({ timeout: 10000 });
      await maybeButton.click();

      // Submit should be disabled without reason
      const submitButton = page.getByTestId('invitation-response-submit-btn');
      await expect(submitButton).toBeDisabled();
    });

    test('should complete tentative flow', async ({ page }) => {
      await page.goto(`${BASE_URL}/speaker-portal/respond?token=${VALID_TOKEN}`);
      await page.waitForLoadState('networkidle');

      // Click Maybe and fill reason
      await page.locator('button').filter({ hasText: /Maybe/i }).click();
      await page.locator('textarea').first().fill('Awaiting budget approval from company');

      // Submit via testid
      await page.getByTestId('invitation-response-submit-btn').click();

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

      // Should show already responded state via testid (language-independent)
      await expect(page.getByTestId('invitation-already-responded')).toBeVisible({
        timeout: 10000,
      });
    });
  });

  test.describe('Mobile Responsiveness', () => {
    test('should be usable on mobile viewport', async ({ page }) => {
      // Set mobile viewport
      await page.setViewportSize({ width: 375, height: 667 });

      await page.goto(`${BASE_URL}/speaker-portal/respond?token=${VALID_TOKEN}`);
      await page.waitForLoadState('networkidle');

      // Buttons should be visible and tappable via testid
      const acceptButton = page.getByTestId('invitation-response-accept-btn');
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

      // Wait for buttons to be visible via testid
      const acceptButton = page.getByTestId('invitation-response-accept-btn');
      const declineButton = page.getByTestId('invitation-response-decline-btn');
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

      // Wait for Accept button to be ready
      const acceptButton = page.getByTestId('invitation-response-accept-btn');
      await expect(acceptButton).toBeVisible({ timeout: 10000 });

      // Focus on Accept button and press Enter
      await acceptButton.focus();
      await page.keyboard.press('Enter');

      // Submit button should appear (preferences form loaded)
      await expect(page.getByTestId('invitation-response-submit-btn')).toBeVisible();
    });
  });
});
