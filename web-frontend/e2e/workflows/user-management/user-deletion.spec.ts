/**
 * E2E: User Deletion (GDPR) — slice 3 / users (plan §C)
 * docs/plans/playwright-staging-hardening.md
 *
 * Rewritten 2026-05-30 to reality + the quality bar (testid-only locators, factory data,
 * mandatory cleanup, no empty tests). Story 2.5.2.
 *
 * Reality + prod-safety: each test's fixture user is created via the API (`createTestUser`,
 * a `bruno.test`(.N) row) — fast and deterministic, no form-fill flake — then the test drives
 * the real DELETE flow through the UI. The happy-path test IS self-cleaning (the UI deletes
 * the user); `afterEach` runs `cleanupById` as a 404-tolerant backstop for the tests that
 * only OPEN the dialog (cancel / display), plus the `bruno.test%` global sweep behind that.
 *
 * The old spec created the fixture user through the create FORM in `beforeEach` (slow, and
 * the create-form path is already covered by `user-creation.spec.ts`), used `tbody tr` /
 * `button:last-child` / `text=` locators, and `waitForTimeout`. All replaced with testids +
 * signal-based waits. The user row + actions button are keyed by username so we never touch a
 * real user.
 */

import { test, expect, type Page } from '@playwright/test';
import { createTestUser, readOrganizerToken, type TestUser } from '../../helpers/user-fixture';
import { cleanupById } from '../../helpers/test-fixtures-cleanup';

/** Search for the fixture user by email and open its row's delete dialog. */
async function openDeleteDialog(page: Page, user: TestUser) {
  await page.getByTestId('user-search-input').fill(user.email);
  // Debounced search (300ms) → the keyed row appears; expect() auto-retries through the refetch.
  await expect(page.getByTestId(`user-table-row-${user.username}`)).toBeVisible({
    timeout: 10_000,
  });

  await page.getByTestId(`user-actions-button-${user.username}`).click();
  await page.getByTestId('user-action-delete').click();
  await expect(page.getByTestId('delete-user-dialog')).toBeVisible();
}

test.describe('User Deletion (GDPR)', { tag: '@gate' }, () => {
  test.describe.configure({ mode: 'serial' });

  let token: string;
  let user: TestUser;

  test.beforeAll(() => {
    token = readOrganizerToken();
  });

  test.beforeEach(async ({ page }) => {
    user = await createTestUser(token);
    await page.goto('/organizer/users');
    await expect(page.getByTestId('user-table')).toBeVisible({ timeout: 15_000 });
  });

  test.afterEach(async () => {
    // 404-tolerant backstop: the delete test already removed the user; the others left it.
    await cleanupById(token, 'users', user.username);
  });

  test('should_showGdprDialog_when_deleteActionClicked', async ({ page }) => {
    await openDeleteDialog(page, user);

    await expect(page.getByTestId('delete-user-email')).toHaveText(user.email);
    await expect(page.getByTestId('delete-user-gdpr-warning')).toBeVisible();
    await expect(page.getByTestId('delete-user-cascade-warning')).toBeVisible();
    await expect(page.getByTestId('delete-user-confirm')).toBeVisible();
    await expect(page.getByTestId('delete-user-cancel')).toBeVisible();
  });

  test('should_keepUser_when_deletionCancelled', async ({ page }) => {
    await openDeleteDialog(page, user);

    await page.getByTestId('delete-user-cancel').click();
    await expect(page.getByTestId('delete-user-dialog')).toBeHidden();

    // The user row is still present (cancel did not mutate).
    await expect(page.getByTestId(`user-table-row-${user.username}`)).toBeVisible();
  });

  test('should_deleteUser_when_confirmed', async ({ page }) => {
    await openDeleteDialog(page, user);

    await page.getByTestId('delete-user-confirm').click();

    // Success ⇔ the dialog closes (the delete mutation resolved) and the row unmounts after the
    // list refetch — both are precise signals, no fixed sleeps.
    await expect(page.getByTestId('delete-user-dialog')).toBeHidden({ timeout: 15_000 });
    await expect(page.getByTestId(`user-table-row-${user.username}`)).toHaveCount(0);
  });
});
