/**
 * E2E Tests for Additional Email Addresses (Story 10.32)
 *
 * Verifies the new Account-tab section under user settings: organizer adds an
 * additional email, sees the row, reloads, sees it again, then deletes it.
 *
 * Requires:
 *   - Company User Management Service running (V16 migration applied; new
 *     /users/me/additional-emails endpoints live).
 *   - Organizer authenticated via .playwright-auth-chromium.json
 */

import { test, expect } from '@playwright/test';

const BASE_URL = process.env.E2E_BASE_URL || 'http://localhost:8100';

// Use a clearly-test-scoped address so a partial failure mid-run can be cleaned
// up manually without polluting any real organizer profile.
const TEST_EMAIL = `e2e-additional-${Date.now()}@example.com`;

test.describe('User Settings — Additional Emails (Story 10.32)', () => {
  test.use({ storageState: '.playwright-auth-chromium.json' });

  test.afterEach(async ({ page }) => {
    // Best-effort cleanup so re-runs in shared staging don't trip the 5-email cap.
    await page
      .evaluate(async (email) => {
        const r = await fetch(`/api/v1/users/me/additional-emails/${encodeURIComponent(email)}`, {
          method: 'DELETE',
          credentials: 'include',
        });
        // 404 is fine — the test may have already cleaned up.
        return r.status;
      }, TEST_EMAIL)
      .catch(() => undefined);
  });

  test('organizer can add, persist across reload, and delete an additional email', async ({
    page,
  }) => {
    await page.goto(`${BASE_URL}/account`);

    // Navigate to Settings tab on the account page.
    await page.getByTestId('settings-tab').click();

    // Section heading should render.
    await expect(page.getByTestId('additional-emails-section')).toBeVisible({ timeout: 10_000 });

    // Fill the inline form and submit.
    const emailField = page.getByTestId('additional-email-input').locator('input');
    await emailField.fill(TEST_EMAIL);
    await page.getByTestId('additional-email-add-button').click();

    // Row should appear with delete button visible.
    const rowTestId = `additional-email-row-${TEST_EMAIL}`;
    await expect(page.getByTestId(rowTestId)).toBeVisible({ timeout: 5_000 });

    // Reload — row persists from the server.
    await page.reload();
    await page.getByTestId('settings-tab').click();
    await expect(page.getByTestId(rowTestId)).toBeVisible({ timeout: 10_000 });

    // Delete via icon; auto-accept the confirm dialog.
    page.once('dialog', (dialog) => dialog.accept());
    await page.getByTestId(`additional-email-delete-${TEST_EMAIL}`).click();

    // Row gone.
    await expect(page.getByTestId(rowTestId)).toHaveCount(0, { timeout: 5_000 });
  });
});
