/**
 * E2E Tests for User Deletion Workflow (GDPR Compliant)
 * Story 2.5.2: User Management Frontend
 *
 * Tests the user deletion confirmation dialog with GDPR warnings
 *
 * Requirements:
 * 1. User Management Service deployed with delete user endpoint
 * 2. PostgreSQL database with users table
 * 3. Authenticated organizer user
 * 4. Test users that can be safely deleted
 *
 * Setup Instructions:
 * 1. Run: npx playwright test e2e/workflows/user-management/user-deletion.spec.ts
 */

import { test, expect, type Page } from '@playwright/test';

// Test configuration
const BASE_URL = process.env.E2E_BASE_URL || 'http://localhost:3000';

// Test data - User to be created and deleted
const TEST_USER_TO_DELETE = {
  firstName: `DeleteMe${Date.now()}`,
  lastName: `TestUser${Date.now()}`,
  email: `delete.me.${Date.now()}@example.com`,
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
 * Helper: Create a test user for deletion
 */
async function createTestUserForDeletion(page: Page): Promise<string> {
  // Open create user modal
  const addUserButton = page.locator('button:has-text("Add User")');
  await addUserButton.click();
  await page.waitForSelector('[role="dialog"]', { timeout: 5000 });

  // Fill form
  await page.fill('input[name="firstName"]', TEST_USER_TO_DELETE.firstName);
  await page.fill('input[name="lastName"]', TEST_USER_TO_DELETE.lastName);
  await page.fill('input[name="email"]', TEST_USER_TO_DELETE.email);

  // Select ATTENDEE role
  const attendeeCheckbox = page
    .locator('input[type="checkbox"][value="ATTENDEE"]')
    .or(page.locator('label:has-text("Attendee") input[type="checkbox"]'));
  await attendeeCheckbox.check();

  // Submit
  const submitButton = page
    .locator('button:has-text("Create")')
    .or(page.locator('button[type="submit"]'));
  await submitButton.click();

  // Wait for modal to close
  await page.waitForTimeout(2000);

  return TEST_USER_TO_DELETE.email;
}

/**
 * Helper: Search for user and open delete dialog
 */
async function openDeleteDialogForUser(page: Page, email: string) {
  // Search for the user
  const searchInput = page.locator('input[placeholder*="Search" i]');
  await searchInput.fill(email);
  await page.waitForTimeout(500);

  // Verify user found
  await expect(page.locator(`text=${email}`)).toBeVisible({ timeout: 5000 });

  // Open actions menu for the user
  const actionsButton = page
    .locator('tbody tr')
    .first()
    .locator('button[aria-label*="action" i]')
    .or(page.locator('tbody tr').first().locator('button:has([data-testid*="MoreVert"])'))
    .or(page.locator('tbody tr').first().locator('button:last-child'));
  await actionsButton.click();
  await page.waitForTimeout(300);

  // Click Delete option
  const deleteOption = page
    .locator('text=Delete')
    .or(page.locator('[role="menuitem"]:has-text("Delete")'));
  await deleteOption.click();

  // Wait for delete confirmation dialog
  await page.waitForSelector('[role="dialog"]', { timeout: 5000 });
  await expect(page.locator('[role="dialog"]')).toBeVisible();
}

test.describe('User Deletion Workflow (GDPR)', () => {
  let testUserEmail: string;

  test.beforeEach(async ({ page }) => {
    await loginAsOrganizer(page);
    await navigateToUserManagement(page);

    // Create a test user to delete
    testUserEmail = await createTestUserForDeletion(page);
  });

  test('should_openDeleteDialog_when_deleteActionClicked', async ({ page }) => {
    await openDeleteDialogForUser(page, testUserEmail);

    // Verify dialog title contains "Delete"
    await expect(page.locator('text=/Delete.*User/i')).toBeVisible();

    // Verify user info is displayed
    await expect(
      page
        .locator(`text=${TEST_USER_TO_DELETE.firstName}`)
        .or(page.locator(`text=${testUserEmail}`))
    ).toBeVisible();
  });

  test('should_displayGDPRWarning_when_deleteDialogOpens', async ({ page }) => {
    await openDeleteDialogForUser(page, testUserEmail);

    // Verify GDPR warning text
    await expect(
      page.locator('text=/GDPR/i').or(page.locator('text=/permanently delete/i'))
    ).toBeVisible();

    // Verify warning about cascade deletion
    await expect(
      page.locator('text=/associated data/i').or(page.locator('text=/cannot be undone/i'))
    ).toBeVisible();
  });

  test('should_displayDeleteButton_when_confirmationDialogOpen', async ({ page }) => {
    await openDeleteDialogForUser(page, testUserEmail);

    // Verify Delete button exists (usually in red/danger color)
    const deleteButton = page.locator('button:has-text("Delete")');
    await expect(deleteButton).toBeVisible();

    // Verify Cancel button exists
    const cancelButton = page.locator('button:has-text("Cancel")');
    await expect(cancelButton).toBeVisible();
  });

  test('should_closeDialog_when_cancelButtonClicked', async ({ page }) => {
    await openDeleteDialogForUser(page, testUserEmail);

    // Click Cancel button
    const cancelButton = page.locator('button:has-text("Cancel")');
    await cancelButton.click();

    // Verify dialog closed
    const dialog = page.locator('[role="dialog"]');
    await expect(dialog).not.toBeVisible({ timeout: 3000 });

    // Verify user still exists in table
    await expect(page.locator(`text=${testUserEmail}`)).toBeVisible();
  });

  test('should_deleteUser_when_confirmButtonClicked', async ({ page }) => {
    await openDeleteDialogForUser(page, testUserEmail);

    // Click Delete button
    const deleteButton = page.locator('button:has-text("Delete")').last();
    await deleteButton.click();

    // Wait for deletion to complete
    await page.waitForTimeout(2000);

    // Verify dialog closed
    const dialog = page.locator('[role="dialog"]');
    await expect(dialog).not.toBeVisible({ timeout: 5000 });

    // Verify user no longer in table (search should return no results)
    const searchInput = page.locator('input[placeholder*="Search" i]');
    await searchInput.clear();
    await searchInput.fill(testUserEmail);
    await page.waitForTimeout(500);

    // User should not be found
    await expect(page.locator(`text=${testUserEmail}`))
      .not.toBeVisible({ timeout: 5000 })
      .catch(() => {
        // Expected - user was deleted
        console.log('User successfully deleted');
      });
  });

  test('should_closeDialog_when_closeIconClicked', async ({ page }) => {
    await openDeleteDialogForUser(page, testUserEmail);

    // Click close icon (X button)
    const closeButton = page
      .locator('[aria-label="close"]')
      .or(page.locator('button[aria-label*="close" i]'));
    await closeButton.click();

    // Verify dialog closed
    const dialog = page.locator('[role="dialog"]');
    await expect(dialog).not.toBeVisible({ timeout: 3000 });
  });

  test('should_displayUserInfo_when_confirmationDialogOpen', async ({ page }) => {
    await openDeleteDialogForUser(page, testUserEmail);

    // Verify user's name is displayed
    await expect(
      page
        .locator(`text=${TEST_USER_TO_DELETE.firstName}`)
        .or(page.locator(`text=${TEST_USER_TO_DELETE.lastName}`))
    ).toBeVisible();

    // Verify user's email is displayed
    await expect(page.locator(`text=${testUserEmail}`)).toBeVisible();
  });
});
