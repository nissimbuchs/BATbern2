/**
 * E2E: User List & Search — slice 3 / users (plan §C)
 * docs/plans/playwright-staging-hardening.md
 *
 * Rewritten 2026-05-30 to reality + the quality bar (testid-only locators, factory data,
 * no empty tests). Story 2.5.2.
 *
 * A `bruno.test` fixture user is seeded via the API in `beforeAll` so search has a
 * DETERMINISTIC target (its keyed row `user-table-row-<username>`), instead of the old
 * spec's `searchInput.fill('test')` + `.catch(() => console.log(...))` non-assertions. The
 * fixture is deleted in `afterAll` (`cleanupById`).
 *
 * Deleted from the old spec (assertion-free / data-dependent / Bruno-covered):
 *   • `should_sortTable…` and `should_displayPagination…` — logged before/after but asserted
 *     nothing; sort order + pagination presence are data-dependent and not gate-worthy.
 *   • `should_filterUsers_when_selectingRoleFilter` / role-filter UI — role filtering is
 *     covered at the API by Bruno (`15/20-list-users-filter-by-role.bru`); the MUI Autocomplete
 *     option-pick is flaky and adds no gate value over the API contract.
 *   • `text=`/`tbody tr`/`input[placeholder]` locators → testids; `waitForTimeout` → expect().
 */

import { test, expect } from '@playwright/test';
import { createTestUser, readOrganizerToken, type TestUser } from '../../helpers/user-fixture';
import { cleanupById } from '../../helpers/test-fixtures-cleanup';

test.describe('User List & Search', { tag: '@gate' }, () => {
  let token: string;
  let user: TestUser;

  test.beforeAll(async () => {
    token = readOrganizerToken();
    user = await createTestUser(token);
  });

  test.afterAll(async () => {
    await cleanupById(token, 'users', user.username);
  });

  test.beforeEach(async ({ page }) => {
    await page.goto('/organizer/users');
    await expect(page.getByTestId('user-table')).toBeVisible({ timeout: 15_000 });
  });

  test('should_displayUserListWithControls_when_pageLoads', async ({ page }) => {
    // Sortable column headers + the add-user action render.
    await expect(page.getByTestId('user-sort-name')).toBeVisible();
    await expect(page.getByTestId('user-sort-email')).toBeVisible();
    await expect(page.getByTestId('user-sort-company')).toBeVisible();
    await expect(page.getByTestId('user-add-button')).toBeVisible();

    // At least one user row is present (the table is populated on staging = prod).
    expect(await page.locator('[data-testid^="user-table-row-"]').count()).toBeGreaterThan(0);
  });

  test('should_findUser_when_searchingByEmail', async ({ page }) => {
    await page.getByTestId('user-search-input').fill(user.email);

    // Debounced server search → only the seeded fixture row matches; expect() retries the refetch.
    await expect(page.getByTestId(`user-table-row-${user.username}`)).toBeVisible({
      timeout: 10_000,
    });
  });

  test('should_clearSearch_when_clearFiltersClicked', async ({ page }) => {
    const search = page.getByTestId('user-search-input');
    await search.fill(user.email);
    await expect(page.getByTestId(`user-table-row-${user.username}`)).toBeVisible({
      timeout: 10_000,
    });

    await page.getByTestId('user-clear-filters').click();
    await expect(search).toHaveValue('');
  });
});
