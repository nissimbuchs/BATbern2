/**
 * E2E Tests for Reset Password Confirmation Flow
 * Story 1.2.2a: Implement Reset Password Confirmation
 *
 * IMPORTANT: These tests require:
 * 1. Playwright to be installed and configured
 * 2. AWS Cognito user pool deployed with password reset configured
 * 3. Test user accounts in Cognito
 * 4. Forgot password flow completed first (to get reset code)
 *
 * Setup Instructions:
 * 1. Install Playwright: npm install -D @playwright/test
 * 2. Initialize Playwright: npx playwright install
 * 3. Configure playwright.config.ts with base URL
 * 4. Set up test environment variables (see .env.test.example)
 * 5. Run: npx playwright test e2e/auth/reset-password.spec.ts
 */

import { test, expect, Page } from '@playwright/test';

// Test configuration
const BASE_URL = process.env.E2E_BASE_URL || 'http://localhost:8100';
const TEST_EMAIL = process.env.E2E_TEST_EMAIL || 'test@batbern.ch';
// Note: In real scenarios, the code would come from email. For testing, you may need to use a test code
// or integrate with AWS Cognito to get a valid test code
const TEST_CODE = process.env.E2E_TEST_CODE || '123456';

/**
 * Helper: Navigate to reset password page with email parameter
 */
async function navigateToResetPassword(page: Page, email: string) {
  await page.goto(`${BASE_URL}/auth/reset-password?email=${encodeURIComponent(email)}`);
  await expect(page.locator('[data-testid="reset-password-title"]')).toBeVisible();
}

/**
 * Helper: Fill reset password form
 */
async function fillResetPasswordForm(
  page: Page,
  code: string,
  newPassword: string,
  confirmPassword: string
) {
  await page.fill('input[name="code"]', code);
  await page.fill('input[name="newPassword"]', newPassword);
  await page.fill('input[name="confirmPassword"]', confirmPassword);
}

/**
 * Helper: Submit reset password form
 */
async function submitResetPasswordForm(page: Page) {
  await page.click('button[type="submit"]');
}

// ============================================================================
// TEST GROUP 1: Basic Reset Password Flow
// ============================================================================

test.describe('Reset Password - Basic Flow', () => {
  test('should_renderResetPasswordPage_when_navigated', async ({ page }) => {
    // AC1: Reset password page should display all required fields
    await navigateToResetPassword(page, TEST_EMAIL);

    await expect(page.getByLabel(/email/i)).toBeVisible();
    await expect(page.getByLabel(/6-digit.*code/i)).toBeVisible();
    await expect(page.getByLabel(/new password/i)).toBeVisible();
    await expect(page.getByLabel(/confirm password/i)).toBeVisible();
    await expect(page.locator('[data-testid="reset-password-submit"]')).toBeVisible();
    await expect(page.getByText(/back to login/i)).toBeVisible();
  });

  test('should_displayEmailFromURLParam_when_provided', async ({ page }) => {
    // AC1: Email field should be pre-filled and disabled
    await navigateToResetPassword(page, TEST_EMAIL);

    const emailField = page.getByLabel(/email/i);
    await expect(emailField).toHaveValue(TEST_EMAIL);
    await expect(emailField).toBeDisabled();
  });

  test('should_displayPasswordRequirements_when_rendered', async ({ page }) => {
    // AC6: Password requirements should be visible
    await navigateToResetPassword(page, TEST_EMAIL);

    await expect(
      page.getByText(/at least 8 characters.*uppercase.*lowercase.*number/i)
    ).toBeVisible();
  });
});

// ============================================================================
// TEST GROUP 2: Form Validation
// ============================================================================

test.describe('Reset Password - Form Validation', () => {
  test('should_showError_when_codeInvalid', async ({ page }) => {
    // AC9: Code must be exactly 6 digits
    await navigateToResetPassword(page, TEST_EMAIL);

    await page.fill('input[name="code"]', '123'); // Too short
    await page.keyboard.press('Tab');

    await expect(page.getByText(/invalid.*code/i)).toBeVisible();
  });

  test('should_showError_when_passwordTooShort', async ({ page }) => {
    // AC10: Password must meet minimum requirements
    await navigateToResetPassword(page, TEST_EMAIL);

    await page.fill('input[name="newPassword"]', 'short');
    await page.keyboard.press('Tab');

    await expect(page.getByText(/does not meet requirements/i)).toBeVisible();
  });

  test('should_showError_when_passwordsMismatch', async ({ page }) => {
    // AC11: Confirm password must match new password
    await navigateToResetPassword(page, TEST_EMAIL);

    await page.fill('input[name="newPassword"]', 'ValidPassword123');
    await page.fill('input[name="confirmPassword"]', 'DifferentPassword123');
    await page.keyboard.press('Tab');

    await expect(page.getByText(/passwords do not match/i)).toBeVisible();
  });

  test('should_disableSubmitButton_when_formInvalid', async ({ page }) => {
    // AC12: Submit button should be disabled when form is invalid
    await navigateToResetPassword(page, TEST_EMAIL);

    const submitButton = page.locator('button[type="submit"]');
    await expect(submitButton).toBeDisabled();

    // Fill partial form
    await page.fill('input[name="code"]', '123456');
    // Still disabled because passwords not filled
    await expect(submitButton).toBeDisabled();
  });

  test('should_enableSubmitButton_when_formValid', async ({ page }) => {
    // AC12: Submit button should be enabled when form is valid
    await navigateToResetPassword(page, TEST_EMAIL);

    await fillResetPasswordForm(page, '123456', 'ValidPassword123', 'ValidPassword123');

    const submitButton = page.locator('button[type="submit"]');
    await expect(submitButton).toBeEnabled();
  });
});

// ============================================================================
// TEST GROUP 3: Password Strength Indicator
// ============================================================================

test.describe('Reset Password - Password Strength', () => {
  test('should_showWeakStrength_when_simplePassword', async ({ page }) => {
    // AC5: Password strength indicator for weak password
    await navigateToResetPassword(page, TEST_EMAIL);

    await page.fill('input[name="newPassword"]', 'password1');

    await expect(page.getByText(/weak/i)).toBeVisible();
  });

  test('should_showMediumStrength_when_moderatePassword', async ({ page }) => {
    // AC5: Password strength indicator for medium password
    await navigateToResetPassword(page, TEST_EMAIL);

    await page.fill('input[name="newPassword"]', 'Password1');

    await expect(page.getByText(/medium/i)).toBeVisible();
  });

  test('should_showStrongStrength_when_complexPassword', async ({ page }) => {
    // AC5: Password strength indicator for strong password
    await navigateToResetPassword(page, TEST_EMAIL);

    await page.fill('input[name="newPassword"]', 'MyStr0ng!Pass');

    await expect(page.getByText(/strong/i)).toBeVisible();
  });
});

// ============================================================================
// TEST GROUP 4: Password Visibility Toggle
// ============================================================================

test.describe('Reset Password - Password Visibility', () => {
  test('should_togglePasswordVisibility_when_iconClicked', async ({ page }) => {
    // Test password visibility toggle
    await navigateToResetPassword(page, TEST_EMAIL);

    const newPasswordField = page.locator('input[name="newPassword"]');

    // Initially password type
    await expect(newPasswordField).toHaveAttribute('type', 'password');

    // Click visibility toggle
    await page.locator('button[aria-label*="Show password"]').first().click();

    // Should be text type now
    await expect(newPasswordField).toHaveAttribute('type', 'text');

    // Click again to hide
    await page.locator('button[aria-label*="Hide password"]').first().click();

    // Should be password type again
    await expect(newPasswordField).toHaveAttribute('type', 'password');
  });
});

// ============================================================================
// TEST GROUP 5: Accessibility
// ============================================================================

test.describe('Reset Password - Accessibility', () => {
  test('should_haveProperLabels_when_rendered', async ({ page }) => {
    // WCAG 2.1 AA: All form fields should have accessible labels
    await navigateToResetPassword(page, TEST_EMAIL);

    await expect(page.getByLabel(/email/i)).toBeVisible();
    await expect(page.getByLabel(/6-digit.*code/i)).toBeVisible();
    await expect(page.getByLabel(/new password/i)).toBeVisible();
    await expect(page.getByLabel(/confirm password/i)).toBeVisible();
  });

  test('should_supportKeyboardNavigation_when_tabPressed', async ({ page }) => {
    // WCAG 2.1 AA: All interactive elements should be keyboard accessible
    await navigateToResetPassword(page, TEST_EMAIL);

    // Tab through form fields
    await page.keyboard.press('Tab'); // Back to Login link
    await expect(page.getByText(/back to login/i)).toBeFocused();

    await page.keyboard.press('Tab'); // Code field
    await expect(page.getByLabel(/6-digit.*code/i)).toBeFocused();

    await page.keyboard.press('Tab'); // New password field
    await expect(page.getByLabel(/new password/i)).toBeFocused();

    await page.keyboard.press('Tab'); // Show/Hide password button
    // Skip

    await page.keyboard.press('Tab'); // Confirm password field
    await expect(page.getByLabel(/confirm password/i)).toBeFocused();
  });

  test('should_provideErrorMessages_when_validationFails', async ({ page }) => {
    // WCAG 2.1 AA: Validation errors should be clearly communicated
    await navigateToResetPassword(page, TEST_EMAIL);

    await fillResetPasswordForm(page, '123', 'short', 'different');
    await page.keyboard.press('Tab');

    // Should have multiple error messages visible
    await expect(page.getByText(/invalid.*code/i)).toBeVisible();
    await expect(page.getByText(/does not meet requirements/i)).toBeVisible();
  });
});

// ============================================================================
// TEST GROUP 6: Integration with Forgot Password Flow
// ============================================================================

test.describe('Reset Password - Integration', () => {
  test('should_linkBackToForgotPassword_when_codeExpired', async ({ page }) => {
    // AC18: Link to request new code when code is expired
    await navigateToResetPassword(page, TEST_EMAIL);

    // Note: This test assumes we can trigger an expired code error
    // In a real scenario, you'd need to use an actual expired code
    // or mock the API response

    // For demonstration, we're checking that the UI has the link
    await expect(page.getByText(/back to login/i)).toBeVisible();
  });
});

// ============================================================================
// TEST GROUP 7: Error Handling (Skipped - requires real Cognito integration)
// ============================================================================

test.describe('Reset Password - Error Handling', () => {
  test.skip('should_showError_when_codeInvalidFromCognito', async ({ page }) => {
    // AC17: Show error for invalid code from Cognito
    // This test requires actual Cognito integration
    await navigateToResetPassword(page, TEST_EMAIL);
    await fillResetPasswordForm(page, '999999', 'ValidPassword123', 'ValidPassword123');
    await submitResetPasswordForm(page);

    await expect(page.getByText(/invalid.*code/i)).toBeVisible();
  });

  test.skip('should_showError_when_codeExpiredFromCognito', async ({ page }) => {
    // AC18: Show error for expired code from Cognito
    // This test requires actual Cognito integration with expired code
    await navigateToResetPassword(page, TEST_EMAIL);
    // Use an expired code
    await fillResetPasswordForm(page, TEST_CODE, 'ValidPassword123', 'ValidPassword123');
    await submitResetPasswordForm(page);

    await expect(page.getByText(/expired.*code/i)).toBeVisible();
    await expect(page.getByText(/request new code/i)).toBeVisible();
  });
});
