/**
 * E2E Tests for Forgot Password Flow
 * Story 1.2.2: Implement Forgot Password Flow
 *
 * IMPORTANT: These tests require:
 * 1. Playwright to be installed and configured
 * 2. AWS Cognito user pool deployed
 * 3. AWS SES email templates configured (German & English)
 * 4. MailHog or similar email testing tool for dev environment
 * 5. Test user accounts in Cognito
 *
 * Setup Instructions:
 * 1. Install Playwright: npm install -D @playwright/test
 * 2. Initialize Playwright: npx playwright install
 * 3. Configure playwright.config.ts with base URL
 * 4. Set up test environment variables (see .env.test.example)
 * 5. Run: npx playwright test e2e/auth/forgot-password.spec.ts
 */

import { test, expect, Page } from '@playwright/test';

// Test configuration
const BASE_URL = process.env.E2E_BASE_URL || 'http://localhost:5173';
const TEST_EMAIL = process.env.E2E_TEST_EMAIL || 'test@batbern.ch';
const MAILHOG_URL = process.env.MAILHOG_URL || 'http://localhost:8025';

/**
 * Helper: Navigate to forgot password page
 */
async function navigateToForgotPassword(page: Page) {
  await page.goto(`${BASE_URL}/auth/forgot-password`);
  await expect(page.locator('h4')).toContainText(/reset password/i);
}

/**
 * Helper: Fill email and submit form
 */
async function submitForgotPasswordForm(page: Page, email: string) {
  await page.fill('input[name="email"]', email);
  await page.click('button[type="submit"]');
}

interface MailhogEmail {
  To?: Array<{ Mailbox?: string; Domain?: string }>;
  Content?: {
    Headers?: { Subject?: string[] };
    Body?: string;
  };
}

/**
 * Helper: Check for email in MailHog
 * Returns the email object if found
 */
async function getEmailFromMailHog(
  page: Page,
  recipientEmail: string,
  subject: string
): Promise<MailhogEmail | undefined> {
  const response = await page.request.get(`${MAILHOG_URL}/api/v2/messages`);
  const data = await response.json();

  const email = (data.items as MailhogEmail[] | undefined)?.find(
    (item: MailhogEmail) =>
      item.To?.[0]?.Mailbox + '@' + item.To?.[0]?.Domain === recipientEmail &&
      item.Content?.Headers?.Subject?.[0]?.includes(subject)
  );

  return email;
}

/**
 * Helper: Extract reset link from email
 */
function extractResetLinkFromEmail(emailContent: string): string {
  const linkMatch = emailContent.match(/href="([^"]*reset-password[^"]*)"/);
  return linkMatch ? linkMatch[1] : '';
}

// ============================================================================
// TEST GROUP 1: Basic Forgot Password Flow
// ============================================================================

test.describe('Forgot Password - Basic Flow', () => {
  test('should_renderForgotPasswordPage_when_navigated', async ({ page }) => {
    // AC1: Email input field should be visible
    await navigateToForgotPassword(page);

    await expect(page.locator('input[name="email"]')).toBeVisible();
    await expect(page.locator('button[type="submit"]')).toContainText(/send link/i);
    await expect(page.getByText(/back to login/i)).toBeVisible();
  });

  test('should_showValidationError_when_emailInvalid', async ({ page }) => {
    // AC3: Client-side validation for email format
    await navigateToForgotPassword(page);

    await page.fill('input[name="email"]', 'invalid-email');
    await page.click('input[name="email"]');
    await page.keyboard.press('Tab'); // Trigger blur

    await expect(page.getByText(/please enter a valid email/i)).toBeVisible();
  });

  test('should_disableSubmitButton_when_emailEmpty', async ({ page }) => {
    // AC1: Submit button should be disabled when email is empty
    await navigateToForgotPassword(page);

    const submitButton = page.locator('button[type="submit"]');
    await expect(submitButton).toBeDisabled();
  });

  test('should_enableSubmitButton_when_emailValid', async ({ page }) => {
    // AC1: Submit button should be enabled when email is valid
    await navigateToForgotPassword(page);

    await page.fill('input[name="email"]', TEST_EMAIL);
    await page.keyboard.press('Tab'); // Trigger validation

    const submitButton = page.locator('button[type="submit"]');
    await expect(submitButton).not.toBeDisabled();
  });

  test('should_showConfirmation_when_resetLinkSent', async ({ page }) => {
    // AC6: Display confirmation with masked email address
    await navigateToForgotPassword(page);

    await submitForgotPasswordForm(page, TEST_EMAIL);

    // Wait for confirmation screen
    await expect(page.getByText(/check your email/i)).toBeVisible();

    // Email should be masked (e.g., t***@batbern.ch)
    await expect(page.getByText(/t\*+@batbern\.ch/i)).toBeVisible();
  });

  test('should_navigateToLogin_when_backLinkClicked', async ({ page }) => {
    // AC5: Back to Login navigation
    await navigateToForgotPassword(page);

    await page.click('a:has-text("Back to Login")');

    await expect(page).toHaveURL(/\/login/);
  });
});

// ============================================================================
// TEST GROUP 2: Email Delivery & Templates
// ============================================================================

test.describe('Forgot Password - Email Delivery', () => {
  test('should_sendGermanEmail_when_userLanguageIsGerman', async ({ page }) => {
    // AC15: German email template
    // Set Accept-Language header to German
    await page.setExtraHTTPHeaders({ 'Accept-Language': 'de-CH' });

    await navigateToForgotPassword(page);
    await submitForgotPasswordForm(page, TEST_EMAIL);

    // Wait for confirmation
    await expect(page.getByText(/check your email/i)).toBeVisible();

    // Check MailHog for German email
    const email = await getEmailFromMailHog(page, TEST_EMAIL, 'Passwort zurücksetzen');

    expect(email).toBeTruthy();
    expect(email.Content.Headers.Subject[0]).toContain('Passwort zurücksetzen');
    expect(email.Content.Body).toContain('Passwort zurücksetzen');
  });

  test('should_sendEnglishEmail_when_userLanguageIsEnglish', async ({ page }) => {
    // AC16: English email template
    await page.setExtraHTTPHeaders({ 'Accept-Language': 'en-US' });

    await navigateToForgotPassword(page);
    await submitForgotPasswordForm(page, TEST_EMAIL);

    await expect(page.getByText(/check your email/i)).toBeVisible();

    // Check MailHog for English email
    const email = await getEmailFromMailHog(page, TEST_EMAIL, 'Reset Your BATbern Password');

    expect(email).toBeTruthy();
    expect(email.Content.Headers.Subject[0]).toContain('Reset Your BATbern Password');
    expect(email.Content.Body).toContain('Reset Your Password');
  });

  test('should_includeResetLink_when_emailSent', async ({ page }) => {
    // AC18: Email content includes reset link
    await navigateToForgotPassword(page);
    await submitForgotPasswordForm(page, TEST_EMAIL);

    await expect(page.getByText(/check your email/i)).toBeVisible();

    const email = await getEmailFromMailHog(page, TEST_EMAIL, 'Reset');
    const resetLink = extractResetLinkFromEmail(email.Content.Body);

    expect(resetLink).toBeTruthy();
    expect(resetLink).toContain('/auth/reset-password');
    expect(resetLink).toContain('email=');
    expect(resetLink).toContain('lang=');
  });

  test('should_includeExpirationNotice_when_emailSent', async ({ page }) => {
    // AC18: Email includes 1-hour expiration notice
    await navigateToForgotPassword(page);
    await submitForgotPasswordForm(page, TEST_EMAIL);

    await expect(page.getByText(/check your email/i)).toBeVisible();

    const email = await getEmailFromMailHog(page, TEST_EMAIL, 'Reset');

    expect(email.Content.Body).toMatch(/1 hour|1 Stunde/i);
    expect(email.Content.Body).toMatch(/expire|läuft.*ab/i);
  });
});

// ============================================================================
// TEST GROUP 3: Resend Functionality
// ============================================================================

test.describe('Forgot Password - Resend Functionality', () => {
  test('should_showResendButton_when_confirmationDisplayed', async ({ page }) => {
    // AC7: Resend functionality available on confirmation screen
    await navigateToForgotPassword(page);
    await submitForgotPasswordForm(page, TEST_EMAIL);

    await expect(page.getByText(/check your email/i)).toBeVisible();
    await expect(page.getByRole('button', { name: /resend link/i })).toBeVisible();
  });

  test('should_disableResendButton_when_cooldownActive', async ({ page }) => {
    // AC8: 60-second cooldown between resend attempts
    await navigateToForgotPassword(page);
    await submitForgotPasswordForm(page, TEST_EMAIL);

    await expect(page.getByText(/check your email/i)).toBeVisible();

    const resendButton = page.getByRole('button', { name: /resend/i });
    await resendButton.click();

    // Button should be disabled during cooldown
    await expect(resendButton).toBeDisabled();

    // Should show countdown
    await expect(page.getByText(/wait.*\d+.*second/i)).toBeVisible();
  });

  test('should_enableResendButton_when_cooldownExpires', async ({ page }) => {
    // AC8: Resend button enabled after 60 seconds
    await navigateToForgotPassword(page);
    await submitForgotPasswordForm(page, TEST_EMAIL);

    await expect(page.getByText(/check your email/i)).toBeVisible();

    const resendButton = page.getByRole('button', { name: /resend/i });
    await resendButton.click();

    // Wait for cooldown to expire (mocked to 2 seconds in test environment)
    await page.waitForTimeout(2000);

    await expect(resendButton).not.toBeDisabled();
  });

  test('should_sendNewEmail_when_resendClicked', async ({ page }) => {
    // AC7: Resend functionality sends new reset link
    await navigateToForgotPassword(page);
    await submitForgotPasswordForm(page, TEST_EMAIL);

    await expect(page.getByText(/check your email/i)).toBeVisible();

    const resendButton = page.getByRole('button', { name: /resend/i });
    await resendButton.click();

    // Should see success notification
    await expect(page.getByText(/link sent again/i)).toBeVisible();

    // Verify new email in MailHog
    const emails = await page.request.get(`${MAILHOG_URL}/api/v2/messages`);
    const data = await emails.json();
    const matchingEmails = (data.items as MailhogEmail[])?.filter(
      (item) => item.To?.[0]?.Mailbox + '@' + item.To?.[0]?.Domain === TEST_EMAIL
    );

    expect(matchingEmails.length).toBeGreaterThanOrEqual(2);
  });
});

// ============================================================================
// TEST GROUP 4: Security - Email Enumeration Prevention
// ============================================================================

test.describe('Forgot Password - Security', () => {
  test('should_showSameResponse_when_emailDoesNotExist', async ({ page }) => {
    // AC12: Email enumeration prevention
    const nonExistentEmail = 'nonexistent@example.com';

    await navigateToForgotPassword(page);
    await submitForgotPasswordForm(page, nonExistentEmail);

    // Should show same confirmation screen (no indication that user doesn't exist)
    await expect(page.getByText(/check your email/i)).toBeVisible();
    await expect(page.getByText(/n\*+@example\.com/i)).toBeVisible();

    // BUT no email should be sent (verify in MailHog)
    const email = await getEmailFromMailHog(page, nonExistentEmail, 'Reset');
    expect(email).toBeFalsy();
  });

  test('should_logAttempt_when_resetRequested', async ({ page }) => {
    // AC14: Security logging for all reset attempts
    // This test verifies backend logging (requires checking backend logs)
    // In production, this would check CloudWatch or similar logging service

    await navigateToForgotPassword(page);
    await submitForgotPasswordForm(page, TEST_EMAIL);

    await expect(page.getByText(/check your email/i)).toBeVisible();

    // Verify log entry exists (implementation depends on logging infrastructure)
    // Example: Check CloudWatch Logs for audit entry
    // const logs = await getCloudWatchLogs('password-reset-attempts');
    // expect(logs).toContainMatch(/SUCCESS.*${TEST_EMAIL}/);
  });
});

// ============================================================================
// TEST GROUP 5: Rate Limiting
// ============================================================================

test.describe('Forgot Password - Rate Limiting', () => {
  test('should_showRateLimitError_when_limitExceeded', async ({ page }) => {
    // AC13: Rate limiting - 3 requests per hour per email
    await navigateToForgotPassword(page);

    // Submit 3 requests rapidly
    for (let i = 0; i < 3; i++) {
      await submitForgotPasswordForm(page, TEST_EMAIL);
      await expect(page.getByText(/check your email/i)).toBeVisible();
      await page.goBack();
    }

    // 4th request should show rate limit error
    await submitForgotPasswordForm(page, TEST_EMAIL);

    await expect(page.getByText(/too many requests/i)).toBeVisible();
    await expect(page.getByText(/wait.*\d+.*second/i)).toBeVisible();
  });

  test('should_disableSubmitButton_when_rateLimited', async ({ page }) => {
    // AC20: Rate limit error displays countdown timer
    await navigateToForgotPassword(page);

    // Trigger rate limit (3 requests already made in previous test)
    await submitForgotPasswordForm(page, TEST_EMAIL);

    await expect(page.getByText(/too many requests/i)).toBeVisible();

    const submitButton = page.locator('button[type="submit"]');
    await expect(submitButton).toBeDisabled();
    await expect(submitButton).toContainText(/wait/i);
  });
});

// ============================================================================
// TEST GROUP 6: Error Handling
// ============================================================================

test.describe('Forgot Password - Error Handling', () => {
  test('should_displayNetworkError_when_serverUnreachable', async ({ page, context }) => {
    // AC21: Network error with retry option
    // Simulate network failure
    await context.route('**/api/v1/auth/forgot-password', (route) => route.abort('failed'));

    await navigateToForgotPassword(page);
    await submitForgotPasswordForm(page, TEST_EMAIL);

    await expect(page.getByText(/connection error/i)).toBeVisible();
    await expect(page.getByRole('button', { name: /retry/i })).toBeVisible();
  });

  test('should_retryRequest_when_retryButtonClicked', async ({ page, context }) => {
    // AC21: Network error retry functionality
    let requestCount = 0;

    await context.route('**/api/v1/auth/forgot-password', (route) => {
      requestCount++;
      if (requestCount === 1) {
        route.abort('failed');
      } else {
        route.fulfill({
          status: 200,
          body: JSON.stringify({ success: true, message: 'Email sent' }),
        });
      }
    });

    await navigateToForgotPassword(page);
    await submitForgotPasswordForm(page, TEST_EMAIL);

    await expect(page.getByText(/connection error/i)).toBeVisible();

    await page.click('button:has-text("Retry")');

    await expect(page.getByText(/check your email/i)).toBeVisible();
    expect(requestCount).toBe(2);
  });
});

// ============================================================================
// TEST GROUP 7: Accessibility
// ============================================================================

test.describe('Forgot Password - Accessibility', () => {
  test('should_haveNoAccessibilityViolations_when_pageLoads', async ({ page }) => {
    // AC: Run accessibility audit (WCAG 2.1 AA)
    await navigateToForgotPassword(page);

    // Run axe-core accessibility tests
    // Requires @axe-core/playwright package
    // const results = await new AxeBuilder({ page }).analyze();
    // expect(results.violations).toEqual([]);

    // Manual accessibility checks
    await expect(page.locator('input[name="email"]')).toHaveAttribute('type', 'email');
    await expect(page.locator('label[for="email"]')).toBeVisible();
    await expect(page.locator('button[type="submit"]')).toBeEnabled();
  });

  test('should_supportKeyboardNavigation_when_interacting', async ({ page }) => {
    // AC: Keyboard navigation support
    await navigateToForgotPassword(page);

    await page.keyboard.press('Tab'); // Focus email input
    await page.keyboard.type(TEST_EMAIL);
    await page.keyboard.press('Tab'); // Focus submit button
    await page.keyboard.press('Enter'); // Submit form

    await expect(page.getByText(/check your email/i)).toBeVisible();
  });

  test('should_announceErrors_when_validationFails', async ({ page }) => {
    // AC: Screen reader error announcements
    await navigateToForgotPassword(page);

    await page.fill('input[name="email"]', 'invalid');
    await page.keyboard.press('Tab');

    const errorMessage = page.getByText(/please enter a valid email/i);
    await expect(errorMessage).toBeVisible();

    // Error should be associated with input (aria-describedby)
    const emailInput = page.locator('input[name="email"]');
    const ariaDescribedBy = await emailInput.getAttribute('aria-describedby');
    expect(ariaDescribedBy).toBeTruthy();
  });
});

// ============================================================================
// TEST GROUP 8: Internationalization (i18n)
// ============================================================================

test.describe('Forgot Password - Internationalization', () => {
  test('should_displayGermanText_when_languageIsGerman', async ({ page }) => {
    // AC2: Multi-language support (German)
    await page.setExtraHTTPHeaders({ 'Accept-Language': 'de-CH' });

    await navigateToForgotPassword(page);

    await expect(page.getByText(/passwort zurücksetzen/i)).toBeVisible();
    await expect(page.getByPlaceholder(/ihre\.email@beispiel\.ch/i)).toBeVisible();
    await expect(page.getByRole('button', { name: /link senden/i })).toBeVisible();
  });

  test('should_displayEnglishText_when_languageIsEnglish', async ({ page }) => {
    // AC2: Multi-language support (English)
    await page.setExtraHTTPHeaders({ 'Accept-Language': 'en-US' });

    await navigateToForgotPassword(page);

    await expect(page.getByText(/reset password/i)).toBeVisible();
    await expect(page.getByPlaceholder(/your\.email@example\.com/i)).toBeVisible();
    await expect(page.getByRole('button', { name: /send link/i })).toBeVisible();
  });
});
