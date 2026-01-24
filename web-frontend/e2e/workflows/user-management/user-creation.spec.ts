/**
 * E2E Tests for User Creation Workflow
 * Story 2.5.2: User Management Frontend
 *
 * Tests the user creation modal and form validation
 *
 * Requirements:
 * 1. User Management Service deployed with user creation endpoint
 * 2. PostgreSQL database with users table
 * 3. Authenticated organizer user
 *
 * Setup Instructions:
 * 1. Run: npx playwright test e2e/workflows/user-management/user-creation.spec.ts
 */

import { test, expect, type Page } from '@playwright/test';

/**
 * Helper: Navigate to User Management page
 */
async function navigateToUserManagement(page: Page) {
  // Direct navigation is more reliable than clicking nav links
  await page.goto('/organizer/users');
  await page.waitForSelector('[data-testid="user-table"]', { timeout: 10000 });
}

/**
 * Helper: Open Create User Modal
 */
async function openCreateUserModal(page: Page) {
  const addUserButton = page.locator('button:has-text("Add User")');
  await addUserButton.click();

  // Wait for modal to open
  await page.waitForSelector('[role="dialog"]', { timeout: 5000 });
  await expect(page.locator('[role="dialog"]')).toBeVisible();
}

test.describe('User Creation Workflow', () => {
  // Test data - use simple names to comply with username format constraint (firstname.lastname pattern)
  const TEST_USER = {
    firstName: 'TestFirst',
    lastName: 'TestLast',
    email: `test.user.${Date.now()}@example.com`,
    roles: ['ATTENDEE'],
  };
  test.beforeEach(async ({ page }) => {
    await page.goto('/organizer/events');
    await navigateToUserManagement(page);
  });

  test('should_openCreateModal_when_addUserButtonClicked', async ({ page }) => {
    await openCreateUserModal(page);

    // Verify modal title - use exact heading to avoid strict mode violation
    await expect(page.getByRole('heading', { name: 'Create New User', exact: true })).toBeVisible();

    // Verify form fields exist using name attributes
    await expect(page.locator('input[name="firstName"]')).toBeVisible();
    await expect(page.locator('input[name="lastName"]')).toBeVisible();
    await expect(page.locator('input[name="email"]')).toBeVisible();

    // Verify role checkboxes exist using data-testid
    await expect(page.locator('[data-testid^="user-create-role-"]')).toHaveCount(4);
  });

  test('should_showValidationErrors_when_submittingEmptyForm', async ({ page }) => {
    await openCreateUserModal(page);

    // Try to submit without filling form using data-testid
    const submitButton = page.getByTestId('user-create-submit');
    await submitButton.click();

    // Wait for validation errors
    await page.waitForTimeout(500);

    // Verify error messages appear (could be inline or toast)
    const errorMessages = page.locator('text=/required/i').or(page.locator('[class*="error"]'));
    const errorCount = await errorMessages.count();
    expect(errorCount).toBeGreaterThan(0);
  });

  test('should_showEmailValidationError_when_invalidEmail', async ({ page }) => {
    await openCreateUserModal(page);

    // Fill form with invalid email using name attributes
    await page.fill('input[name="firstName"]', TEST_USER.firstName);
    await page.fill('input[name="lastName"]', TEST_USER.lastName);
    await page.fill('input[name="email"]', 'invalid-email');

    // Select a role using data-testid
    const roleCheckbox = page.getByTestId('user-create-role-ATTENDEE');
    const parentLabel = page.locator('label').filter({ has: roleCheckbox });
    await parentLabel.click();

    // Try to submit using data-testid
    const submitButton = page.getByTestId('user-create-submit');
    await submitButton.click();

    // Wait for validation
    await page.waitForTimeout(500);

    // Verify email validation error
    await expect(page.locator('text=/invalid.*email/i')).toBeVisible({ timeout: 3000 });
  });

  test('should_createUser_when_validFormSubmitted', async ({ page }) => {
    await openCreateUserModal(page);

    // Fill form with valid data using name attributes
    await page.fill('input[name="firstName"]', TEST_USER.firstName);
    await page.fill('input[name="lastName"]', TEST_USER.lastName);
    await page.fill('input[name="email"]', TEST_USER.email);

    // Select ATTENDEE role using data-testid
    const attendeeCheckbox = page.getByTestId('user-create-role-ATTENDEE');
    const parentLabel = page.locator('label').filter({ has: attendeeCheckbox });
    await parentLabel.click();

    // Submit form using data-testid
    const submitButton = page.getByTestId('user-create-submit');
    await submitButton.click();

    // Wait for either modal to close (success) or error to appear
    const modal = page.locator('[role="dialog"]');
    await Promise.race([
      modal.waitFor({ state: 'hidden', timeout: 10000 }),
      page.locator('[role="alert"]').waitFor({ state: 'visible', timeout: 10000 }),
    ]).catch(() => {
      // Timeout - neither happened
      console.log('Modal did not close and no error appeared');
    });

    // Verify modal closed (if error appeared, this will fail appropriately)
    await expect(modal).not.toBeVisible({ timeout: 2000 });

    // Verify user appears in table (search for email)
    const searchInput = page.locator('input[placeholder*="Search" i]');
    await searchInput.fill(TEST_USER.email);
    await page.waitForTimeout(1000); // Wait for debounce + API call

    // Verify new user in table
    await expect(page.locator(`text=${TEST_USER.email}`)).toBeVisible({ timeout: 5000 });
  });

  test('should_closeModal_when_cancelButtonClicked', async ({ page }) => {
    await openCreateUserModal(page);

    // Click cancel button using data-testid
    const cancelButton = page.getByTestId('user-create-cancel');
    await cancelButton.click();

    // Verify modal closed
    const modal = page.locator('[role="dialog"]');
    await expect(modal).not.toBeVisible({ timeout: 3000 });
  });

  test('should_closeModal_when_closeIconClicked', async ({ page }) => {
    await openCreateUserModal(page);

    // Click close icon (X button)
    const closeButton = page
      .locator('[aria-label="close"]')
      .or(page.locator('button[aria-label*="close" i]'));
    await closeButton.click();

    // Verify modal closed
    const modal = page.locator('[role="dialog"]');
    await expect(modal).not.toBeVisible({ timeout: 3000 });
  });

  test('should_requireAtLeastOneRole_when_creatingUser', async ({ page }) => {
    await openCreateUserModal(page);

    // Fill form without selecting roles using name attributes
    await page.fill('input[name="firstName"]', TEST_USER.firstName);
    await page.fill('input[name="lastName"]', TEST_USER.lastName);
    await page.fill('input[name="email"]', TEST_USER.email);

    // Try to submit without roles using data-testid
    const submitButton = page.getByTestId('user-create-submit');
    await submitButton.click();

    // Wait for validation
    await page.waitForTimeout(500);

    // Verify role validation error using data-testid
    await expect(page.getByTestId('user-create-role-error')).toBeVisible({ timeout: 3000 });
  });
});
