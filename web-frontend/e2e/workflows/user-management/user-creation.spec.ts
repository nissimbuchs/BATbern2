/**
 * E2E: User Creation — slice 3 / users (plan §C)
 * docs/plans/playwright-staging-hardening.md
 *
 * Rewritten 2026-05-30 to reality + the quality bar (testid-only locators, factory data,
 * mandatory cleanup, no empty tests). Story 2.5.2.
 *
 * Reality check (verified in UserCreateEditModal.tsx + UserList.tsx):
 *   • Creation is a MODAL (UserCreateEditModal) opened from `user-add-button` on
 *     /organizer/users — fields are `user-create-{firstName,lastName,email}`, role checkboxes
 *     `user-create-role-{ROLE}`, submit `user-create-submit`, cancel `user-create-cancel`.
 *   • On a successful create the create mutation resolves and the modal calls `onClose()` — a
 *     failed create renders a server-error Alert and keeps the dialog open. So the dialog
 *     CLOSING is the exact success signal (no fixed sleeps), exactly as the company-creation
 *     @smoke uses the form's onClose.
 *   • The username is server-derived from `firstName.lastName` (Bruno/Test → `bruno.test`(.N)),
 *     so the created row is reachable by the `cums/users` `bruno.test%` sweep; teardown deletes
 *     it by the exact username resolved via search (race-free targeted delete).
 *
 * The old spec's `should_showEmailValidationError` / extra validation tests asserted MUI
 * helperText that carries no testid; email/format validation is covered by Bruno's users-api
 * collection. The retained validation test asserts the one DETERMINISTIC testid signal
 * (`user-create-role-error` on empty submit) and that the dialog stays open.
 *
 * Prod-safety (plan risk #1): the `@smoke` creates a real CUMS user (DB + Cognito) on staging
 * (= prod). Teardown resolves the username and `cleanupById(token, 'users', username)` removes
 * it in afterEach, with the `bruno.test%` global-teardown sweep as backstop.
 */

import { test, expect } from '@playwright/test';
import * as factory from '../../helpers/test-data-factory';
import { readOrganizerToken, findUsernameByEmail } from '../../helpers/user-fixture';
import { cleanupById } from '../../helpers/test-fixtures-cleanup';

test.describe('User Creation', { tag: '@gate' }, () => {
  // Serial: the @smoke creates a real user; serialising keeps the shared cleanup deterministic.
  test.describe.configure({ mode: 'serial' });

  let token: string;
  // Emails created via the UI this run — resolved to usernames + explicit-deleted in afterEach.
  const createdEmails: string[] = [];

  test.beforeAll(() => {
    token = readOrganizerToken();
  });

  test.beforeEach(async ({ page }) => {
    await page.goto('/organizer/users');
    await expect(page.getByTestId('user-table')).toBeVisible({ timeout: 15_000 });
  });

  test.afterEach(async () => {
    while (createdEmails.length > 0) {
      const email = createdEmails.pop();
      if (!email) continue;
      const username = await findUsernameByEmail(token, email);
      if (username) await cleanupById(token, 'users', username);
    }
  });

  test('should_displayCreationModal_when_addUserClicked', async ({ page }) => {
    await page.getByTestId('user-add-button').click();

    await expect(page.getByTestId('user-create-dialog')).toBeVisible();
    // Field testids sit on the MUI TextField wrapper; the native <input> is nested. Asserting
    // the wrapper visible is fine here, but any .fill() below must target `.locator('input')`.
    await expect(page.getByTestId('user-create-firstName')).toBeVisible();
    await expect(page.getByTestId('user-create-lastName')).toBeVisible();
    await expect(page.getByTestId('user-create-email')).toBeVisible();
    // The four role checkboxes (ORGANIZER, SPEAKER, PARTNER, ATTENDEE).
    await expect(page.locator('[data-testid^="user-create-role-"]')).toHaveCount(4);
    await expect(page.getByTestId('user-create-submit')).toBeVisible();
  });

  test('should_requireRole_when_submittingWithoutRole', async ({ page }) => {
    await page.getByTestId('user-add-button').click();
    await expect(page.getByTestId('user-create-dialog')).toBeVisible();

    // Fill name + email but select NO role, then submit → deterministic role-error testid.
    await page.getByTestId('user-create-firstName').locator('input').fill(factory.USER_FIRST_NAME);
    await page.getByTestId('user-create-lastName').locator('input').fill(factory.USER_LAST_NAME);
    await page.getByTestId('user-create-email').locator('input').fill(factory.email());
    await page.getByTestId('user-create-submit').click();

    await expect(page.getByTestId('user-create-role-error')).toBeVisible();
    // Validation blocked the submit → the dialog stays open (no row created → nothing to clean).
    await expect(page.getByTestId('user-create-dialog')).toBeVisible();
  });

  test('should_closeModal_when_cancelClicked', async ({ page }) => {
    await page.getByTestId('user-add-button').click();
    await expect(page.getByTestId('user-create-dialog')).toBeVisible();

    await page.getByTestId('user-create-cancel').click();
    await expect(page.getByTestId('user-create-dialog')).toBeHidden();
  });

  test(
    'should_createUser_when_validDataProvided',
    { tag: ['@smoke', '@gate'] },
    async ({ page }) => {
      const email = factory.email(); // bruno-test-<ts>@e2e.batbern.invalid — unique per run
      createdEmails.push(email); // register for cleanup before the network call

      await page.getByTestId('user-add-button').click();
      await expect(page.getByTestId('user-create-dialog')).toBeVisible();

      // Bruno/Test → server derives username `bruno.test`(.N) → swept by cums/users.
      await page
        .getByTestId('user-create-firstName')
        .locator('input')
        .fill(factory.USER_FIRST_NAME);
      await page.getByTestId('user-create-lastName').locator('input').fill(factory.USER_LAST_NAME);
      await page.getByTestId('user-create-email').locator('input').fill(email);

      // Select the ATTENDEE role (MUI Checkbox toggles via its label).
      const attendee = page.getByTestId('user-create-role-ATTENDEE');
      await page.locator('label').filter({ has: attendee }).click();

      await page.getByTestId('user-create-submit').click();

      // Success ⇔ the create mutation resolved and the modal called onClose() (a failed create
      // renders a server-error Alert and keeps the dialog open). afterEach then resolves the
      // username via search and explicit-deletes the real row.
      await expect(page.getByTestId('user-create-dialog')).toBeHidden({ timeout: 15_000 });
    }
  );
});
