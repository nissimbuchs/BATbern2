/**
 * E2E: Role Management — slice 3 / users (plan §C)
 * docs/plans/playwright-staging-hardening.md
 *
 * Rewritten 2026-05-30 to reality + the quality bar (testid-only locators, factory data,
 * mandatory cleanup, no empty tests). Story 2.5.2.
 *
 * Prod-safety crux (why this rewrite mattered): the old spec opened the role modal on the
 * FIRST row in the table (`tbody tr` first) and SAVED role changes to it — i.e. it mutated
 * the roles of whatever real user happened to sort first on staging (= prod), including
 * possibly the organizer themselves. The rewrite creates a dedicated `bruno.test` fixture
 * user via the API, searches for THAT user by email, and only ever edits its roles — zero
 * blast radius on real users. `afterAll` deletes the fixture (`cleanupById`).
 *
 * The API-level role GET/PUT coverage that used to live in `user-sync/role-change-sync.spec.ts`
 * is owned by Bruno's `06-update-user-roles.bru`; that duplicative (and likewise random-user-
 * mutating) Playwright spec was deleted in this slice. This UI spec is the role-management
 * gate.
 *
 * Success signal: on a successful save the update-roles mutation resolves and the modal calls
 * `onClose()`; a failed save renders an error Alert and keeps it open — so the dialog CLOSING
 * is the exact signal (no fixed sleeps).
 */

import { test, expect, type Page } from '@playwright/test';
import { createTestUser, readOrganizerToken, type TestUser } from '../../helpers/user-fixture';
import { cleanupById } from '../../helpers/test-fixtures-cleanup';

async function openRoleModal(page: Page, user: TestUser) {
  await page.getByTestId('user-search-input').fill(user.email);
  await expect(page.getByTestId(`user-table-row-${user.username}`)).toBeVisible({
    timeout: 10_000,
  });
  await page.getByTestId(`user-actions-button-${user.username}`).click();
  await page.getByTestId('user-action-edit-roles').click();
  await expect(page.getByTestId('role-manager-dialog')).toBeVisible();
}

test.describe('Role Management', { tag: '@gate' }, () => {
  test.describe.configure({ mode: 'serial' });

  let token: string;
  let user: TestUser;

  test.beforeAll(async () => {
    token = readOrganizerToken();
    // Dedicated fixture user with a known starting role (ATTENDEE).
    user = await createTestUser(token, ['ATTENDEE']);
  });

  test.afterAll(async () => {
    await cleanupById(token, 'users', user.username);
  });

  test.beforeEach(async ({ page }) => {
    await page.goto('/organizer/users');
    await expect(page.getByTestId('user-table')).toBeVisible({ timeout: 15_000 });
  });

  test('should_displayCurrentRoles_when_modalOpens', async ({ page }) => {
    await openRoleModal(page, user);

    // The four role checkboxes render, and the fixture's current role (ATTENDEE) is checked.
    await expect(page.locator('[data-testid^="role-manager-role-"]')).toHaveCount(4);
    await expect(page.getByTestId('role-manager-role-ATTENDEE')).toBeChecked();
    await expect(page.getByTestId('role-manager-role-SPEAKER')).not.toBeChecked();
  });

  test('should_updateRoles_when_roleAddedAndSaved', async ({ page }) => {
    await openRoleModal(page, user);

    // Add SPEAKER (MUI Checkbox toggles via its label), keeping ATTENDEE → valid (≥1 role).
    const speaker = page.getByTestId('role-manager-role-SPEAKER');
    await page.locator('label').filter({ has: speaker }).click();
    await expect(speaker).toBeChecked();

    await page.getByTestId('role-manager-save').click();

    // Dialog closes ⇔ the update-roles mutation resolved (onClose runs only on success).
    await expect(page.getByTestId('role-manager-dialog')).toBeHidden({ timeout: 15_000 });
  });

  test('should_closeModal_when_cancelClicked', async ({ page }) => {
    await openRoleModal(page, user);

    await page.getByTestId('role-manager-cancel').click();
    await expect(page.getByTestId('role-manager-dialog')).toBeHidden();
  });
});
