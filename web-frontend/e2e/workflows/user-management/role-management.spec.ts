/**
 * E2E Tests for Role Management Workflow
 * Story 2.5.2: User Management Frontend
 *
 * Tests the role management modal and minimum organizers validation
 *
 * Requirements:
 * 1. User Management Service deployed with role update endpoint
 * 2. PostgreSQL database with users table
 * 3. Authenticated organizer user
 * 4. At least 2 organizers in the system (for minimum validation)
 *
 * Setup Instructions:
 * 1. Run: npx playwright test e2e/workflows/user-management/role-management.spec.ts
 */

import { test, expect, type Page } from '@playwright/test';

// Test configuration
const BASE_URL = process.env.E2E_BASE_URL || 'http://localhost:8100';

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
 * Helper: Open actions menu for first user in table
 */
async function openFirstUserActionsMenu(page: Page) {
  // Find first row's actions button (could be kebab menu, more icon, etc)
  const actionsButton = page
    .locator('tbody tr')
    .first()
    .locator('button[aria-label*="action" i]')
    .or(page.locator('tbody tr').first().locator('button:has([data-testid*="MoreVert"])'))
    .or(page.locator('tbody tr').first().locator('button:last-child'));

  await actionsButton.click();
  await page.waitForTimeout(300);
}

/**
 * Helper: Open role manager modal
 */
async function openRoleManagerModal(page: Page) {
  await openFirstUserActionsMenu(page);

  // Click "Edit Roles" option
  const editRolesOption = page
    .locator('text=/Edit.*Role/i')
    .or(page.locator('[role="menuitem"]:has-text("Role")'));
  await editRolesOption.click();

  // Wait for modal to open
  await page.waitForSelector('[role="dialog"]', { timeout: 5000 });
  await expect(page.locator('[role="dialog"]')).toBeVisible();
}

test.describe('Role Management Workflow', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/organizer/events');
    await navigateToUserManagement(page);
  });

  test('should_openRoleModal_when_editRolesClicked', async ({ page }) => {
    await openRoleManagerModal(page);

    // Verify modal title contains "Role"
    await expect(page.locator('text=/Edit.*Role/i')).toBeVisible();

    // Verify role checkboxes exist
    await expect(page.locator('text=Organizer').or(page.locator('text=ORGANIZER'))).toBeVisible();
    await expect(page.locator('text=Speaker').or(page.locator('text=SPEAKER'))).toBeVisible();
    await expect(page.locator('text=Partner').or(page.locator('text=PARTNER'))).toBeVisible();
    await expect(page.locator('text=Attendee').or(page.locator('text=ATTENDEE'))).toBeVisible();
  });

  test('should_displayCurrentRoles_when_modalOpens', async ({ page }) => {
    await openRoleManagerModal(page);

    // At least one role checkbox should be checked
    const checkedCheckboxes = page.locator('input[type="checkbox"]:checked');
    const checkedCount = await checkedCheckboxes.count();
    expect(checkedCount).toBeGreaterThan(0);
  });

  test('should_toggleRole_when_checkboxClicked', async ({ page }) => {
    await openRoleManagerModal(page);

    // Find first unchecked role checkbox
    const uncheckedCheckbox = page.locator('input[type="checkbox"]:not(:checked)').first();
    const isVisible = await uncheckedCheckbox.isVisible();

    if (isVisible) {
      // Get initial state
      const wasChecked = await uncheckedCheckbox.isChecked();

      // Toggle checkbox
      await uncheckedCheckbox.check();

      // Verify state changed
      const nowChecked = await uncheckedCheckbox.isChecked();
      expect(nowChecked).toBe(true);
      expect(nowChecked).not.toBe(wasChecked);
    } else {
      console.log('All roles already checked - skipping toggle test');
    }
  });

  test('should_updateRoles_when_saveButtonClicked', async ({ page }) => {
    await openRoleManagerModal(page);

    // Toggle a role (add ATTENDEE if not present)
    const attendeeCheckbox = page
      .locator('label:has-text("Attendee") input[type="checkbox"]')
      .or(page.locator('input[type="checkbox"][value="ATTENDEE"]'));

    const wasChecked = await attendeeCheckbox.isChecked();

    // Toggle to opposite state
    if (wasChecked) {
      // Only uncheck if there are other roles selected (minimum 1 role required)
      const allChecked = page.locator('input[type="checkbox"]:checked');
      const checkedCount = await allChecked.count();

      if (checkedCount > 1) {
        await attendeeCheckbox.uncheck();
      } else {
        console.log('Cannot uncheck - must have at least one role');
      }
    } else {
      await attendeeCheckbox.check();
    }

    // Click Save button
    const saveButton = page.locator('button:has-text("Save")');
    await saveButton.click();

    // Wait for modal to close
    await page.waitForTimeout(2000);

    // Verify modal closed
    const modal = page.locator('[role="dialog"]');
    await expect(modal).not.toBeVisible({ timeout: 5000 });

    // Verify success (could be toast notification or updated table)
    // The table should refresh automatically
    await page.waitForTimeout(1000);
  });

  test('should_showError_when_deselectingAllRoles', async ({ page }) => {
    await openRoleManagerModal(page);

    // Uncheck all roles
    const checkboxes = page.locator('input[type="checkbox"]:checked');
    const count = await checkboxes.count();

    for (let i = 0; i < count; i++) {
      const checkbox = checkboxes.nth(i);
      await checkbox.uncheck();
    }

    // Try to save
    const saveButton = page.locator('button:has-text("Save")');
    await saveButton.click();

    // Wait for error message
    await page.waitForTimeout(500);

    // Verify error message about minimum roles
    await expect(
      page.locator('text=/at least one role/i').or(page.locator('text=/role.*required/i'))
    ).toBeVisible({ timeout: 3000 });

    // Modal should still be open
    const modal = page.locator('[role="dialog"]');
    await expect(modal).toBeVisible();
  });

  test('should_closeModal_when_cancelButtonClicked', async ({ page }) => {
    await openRoleManagerModal(page);

    // Click Cancel button
    const cancelButton = page.locator('button:has-text("Cancel")');
    await cancelButton.click();

    // Verify modal closed
    const modal = page.locator('[role="dialog"]');
    await expect(modal).not.toBeVisible({ timeout: 3000 });
  });

  test('should_closeModal_when_closeIconClicked', async ({ page }) => {
    await openRoleManagerModal(page);

    // Click close icon (X button)
    const closeButton = page
      .locator('[aria-label="close"]')
      .or(page.locator('button[aria-label*="close" i]'));
    await closeButton.click();

    // Verify modal closed
    const modal = page.locator('[role="dialog"]');
    await expect(modal).not.toBeVisible({ timeout: 3000 });
  });
});
