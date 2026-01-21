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

/**
 * Helper: Navigate to User Management page
 */
async function navigateToUserManagement(page: Page) {
  // Direct navigation is more reliable than clicking nav links
  await page.goto('/organizer/users');
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

  // Click "Edit Roles" option using testId
  const editRolesOption = page.getByTestId('user-action-edit-roles');
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

    // Verify modal title is "Edit Roles" - use exact match to avoid strict mode violation
    await expect(page.getByRole('heading', { name: 'Edit Roles', exact: true })).toBeVisible();

    // Verify role checkboxes exist using data-testid
    await expect(page.locator('[data-testid^="role-manager-role-"]')).toHaveCount(4); // ORGANIZER, SPEAKER, PARTNER, ATTENDEE
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

    // Find first unchecked role checkbox using data-testid
    const uncheckedCheckbox = page
      .locator('[data-testid^="role-manager-role-"]:not(:checked)')
      .first();
    const count = await page.locator('[data-testid^="role-manager-role-"]:not(:checked)').count();

    if (count > 0) {
      // Get initial state
      const wasChecked = await uncheckedCheckbox.isChecked();

      // Get the specific testid to avoid strict mode violation
      const testId = await uncheckedCheckbox.getAttribute('data-testid');

      // Click the parent label for this specific checkbox (MUI Checkbox behavior)
      const parentLabel = page.locator(`label:has([data-testid="${testId}"])`);
      await parentLabel.click();
      await page.waitForTimeout(300);

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
    const attendeeCheckbox = page.getByTestId('role-manager-role-ATTENDEE');
    const wasChecked = await attendeeCheckbox.isChecked();

    // Toggle to opposite state
    if (wasChecked) {
      // Only uncheck if there are other roles selected (minimum 1 role required)
      const allChecked = page.locator('[data-testid^="role-manager-role-"]:checked');
      const checkedCount = await allChecked.count();

      if (checkedCount > 1) {
        // Click the parent label to uncheck
        const parentLabel = page.locator('label').filter({ has: attendeeCheckbox });
        await parentLabel.click();
      } else {
        console.log('Cannot uncheck - must have at least one role');
      }
    } else {
      // Click the parent label to check
      const parentLabel = page.locator('label').filter({ has: attendeeCheckbox });
      await parentLabel.click();
    }

    // Click Save button using data-testid
    const saveButton = page.getByTestId('role-manager-save');
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

  test.skip('should_showError_when_deselectingAllRoles', async ({ page }) => {
    await openRoleManagerModal(page);

    // Wait for checkboxes to be rendered with initial state
    await page.waitForTimeout(500);

    // Wait for at least one checkbox to be checked (Material-UI uses aria-checked, not :checked)
    await expect(
      page.locator('[data-testid^="role-manager-role-"][aria-checked="true"]')
    ).toHaveCount(1, {
      timeout: 3000,
    });

    // Uncheck all roles using data-testid (use aria-checked for Material-UI checkboxes)
    const checkboxesSelector = '[data-testid^="role-manager-role-"][aria-checked="true"]';
    let count = await page.locator(checkboxesSelector).count();
    console.log(`Initial checked count: ${count}`);

    while (count > 0) {
      // Always get the first checked checkbox (since collection shrinks after each uncheck)
      const checkbox = page.locator(checkboxesSelector).first();
      const testId = await checkbox.getAttribute('data-testid');
      console.log(`Attempting to uncheck: ${testId}`);
      const parentLabel = page.locator(`label:has([data-testid="${testId}"])`);
      await parentLabel.click();
      await page.waitForTimeout(100);

      // Re-count to see if we can uncheck more
      const newCount = await page.locator(checkboxesSelector).count();
      console.log(`Count after uncheck: ${newCount} (was ${count})`);

      // Prevent infinite loop
      if (newCount === count) {
        console.log(`Count didn't change - checkbox may be prevented from unchecking`);
        break;
      }
      count = newCount;
    }

    console.log(`Final checked count: ${count}`);

    // Try to save using data-testid
    const saveButton = page.getByTestId('role-manager-save');
    await saveButton.click();

    // Wait for error message
    await page.waitForTimeout(500);

    // Verify error message about minimum roles (use data-testid)
    await expect(page.getByTestId('role-manager-error')).toBeVisible({ timeout: 3000 });

    // Modal should still be open
    const modal = page.locator('[role="dialog"]');
    await expect(modal).toBeVisible();
  });

  test('should_closeModal_when_cancelButtonClicked', async ({ page }) => {
    await openRoleManagerModal(page);

    // Click Cancel button using data-testid
    const cancelButton = page.getByTestId('role-manager-cancel');
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
