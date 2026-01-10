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

// Test configuration
const BASE_URL = process.env.E2E_BASE_URL || 'http://localhost:8100';

// Test data
const TEST_USER = {
  firstName: `TestFirst${Date.now()}`,
  lastName: `TestLast${Date.now()}`,
  email: `test.user.${Date.now()}@example.com`,
  roles: ['ATTENDEE'],
};

/**
 * Helper: Login as an authenticated organizer
 */
async function loginAsOrganizer(page: Page) {
  const testEmail = process.env.E2E_TEST_EMAIL || 'test@batbern.ch';
  const testPassword = process.env.E2E_TEST_PASSWORD || 'Test123!@#';

  await page.goto(`${BASE_URL}/auth/login`);
  await page.fill('input[name="email"]', testEmail);
  await page.fill('input[name="password"]', testPassword);
  await page.click('button[type="submit"]');
  await page.waitForURL(/\/dashboard/);
}

/**
 * Helper: Navigate to User Management page
 */
async function navigateToUserManagement(page: Page) {
  await page.click('text=Users');
  await page.waitForURL(/\/organizer\/users/);
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
  test.beforeEach(async ({ page }) => {
    await page.goto('/organizer/events');
    await navigateToUserManagement(page);
  });

  test('should_openCreateModal_when_addUserButtonClicked', async ({ page }) => {
    await openCreateUserModal(page);

    // Verify modal title
    await expect(page.locator('text=/Create.*User/i')).toBeVisible();

    // Verify form fields exist
    await expect(
      page.locator('input[name="firstName"]').or(page.locator('label:has-text("First Name")'))
    ).toBeVisible();
    await expect(
      page.locator('input[name="lastName"]').or(page.locator('label:has-text("Last Name")'))
    ).toBeVisible();
    await expect(
      page.locator('input[name="email"]').or(page.locator('label:has-text("Email")'))
    ).toBeVisible();

    // Verify role checkboxes exist
    await expect(page.locator('text=/Role/i')).toBeVisible();
  });

  test('should_showValidationErrors_when_submittingEmptyForm', async ({ page }) => {
    await openCreateUserModal(page);

    // Try to submit without filling form
    const submitButton = page
      .locator('button:has-text("Create")')
      .or(page.locator('button[type="submit"]'));
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

    // Fill form with invalid email
    await page.fill('input[name="firstName"]', TEST_USER.firstName);
    await page.fill('input[name="lastName"]', TEST_USER.lastName);
    await page.fill('input[name="email"]', 'invalid-email');

    // Select a role
    const roleCheckbox = page.locator('input[type="checkbox"]').first();
    await roleCheckbox.check();

    // Try to submit
    const submitButton = page
      .locator('button:has-text("Create")')
      .or(page.locator('button[type="submit"]'));
    await submitButton.click();

    // Wait for validation
    await page.waitForTimeout(500);

    // Verify email validation error
    await expect(page.locator('text=/invalid.*email/i')).toBeVisible({ timeout: 3000 });
  });

  test('should_createUser_when_validFormSubmitted', async ({ page }) => {
    await openCreateUserModal(page);

    // Fill form with valid data
    await page.fill('input[name="firstName"]', TEST_USER.firstName);
    await page.fill('input[name="lastName"]', TEST_USER.lastName);
    await page.fill('input[name="email"]', TEST_USER.email);

    // Select ATTENDEE role
    const attendeeCheckbox = page
      .locator('input[type="checkbox"][value="ATTENDEE"]')
      .or(page.locator('label:has-text("Attendee") input[type="checkbox"]'));
    await attendeeCheckbox.check();

    // Submit form
    const submitButton = page
      .locator('button:has-text("Create")')
      .or(page.locator('button[type="submit"]'));
    await submitButton.click();

    // Wait for modal to close or success message
    await page.waitForTimeout(2000);

    // Verify modal closed
    const modal = page.locator('[role="dialog"]');
    await expect(modal).not.toBeVisible({ timeout: 5000 });

    // Verify user appears in table (search for email)
    const searchInput = page.locator('input[placeholder*="Search" i]');
    await searchInput.fill(TEST_USER.email);
    await page.waitForTimeout(500);

    // Verify new user in table
    await expect(page.locator(`text=${TEST_USER.email}`)).toBeVisible({ timeout: 5000 });
  });

  test('should_closeModal_when_cancelButtonClicked', async ({ page }) => {
    await openCreateUserModal(page);

    // Click cancel button
    const cancelButton = page.locator('button:has-text("Cancel")');
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

    // Fill form without selecting roles
    await page.fill('input[name="firstName"]', TEST_USER.firstName);
    await page.fill('input[name="lastName"]', TEST_USER.lastName);
    await page.fill('input[name="email"]', TEST_USER.email);

    // Try to submit without roles
    const submitButton = page
      .locator('button:has-text("Create")')
      .or(page.locator('button[type="submit"]'));
    await submitButton.click();

    // Wait for validation
    await page.waitForTimeout(500);

    // Verify role validation error
    await expect(
      page.locator('text=/role.*required/i').or(page.locator('text=/select.*role/i'))
    ).toBeVisible({ timeout: 3000 });
  });
});
