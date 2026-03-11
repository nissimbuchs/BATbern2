/**
 * E2E Tests for Admin Settings Tab (Story 10.26 — AC15)
 *
 * Verifies the Settings tab is visible on the Admin page and that
 * support@batbern.ch forwarding recipients can be saved.
 *
 * Requires:
 *   - Event Management Service running (admin settings endpoints)
 *   - Organizer authenticated via .playwright-auth-chromium.json
 */

import { test, expect } from '@playwright/test';

const BASE_URL = process.env.E2E_BASE_URL || 'http://localhost:8100';
const ADMIN_URL = `${BASE_URL}/organizer/admin`;

test.describe('Admin Settings Tab (Story 10.26)', () => {
  test.use({ storageState: '.playwright-auth-chromium.json' });

  test('should show Settings tab on Admin page', async ({ page }) => {
    await page.goto(`${ADMIN_URL}?tab=7`);
    await expect(page.getByRole('tab', { name: /settings/i })).toBeVisible();
  });

  test('should display email forwarding section with support contacts field', async ({ page }) => {
    await page.goto(`${ADMIN_URL}?tab=7`);

    // Wait for settings tab content to load
    await expect(page.getByText(/email forwarding/i)).toBeVisible({ timeout: 10_000 });
    await expect(page.getByLabel(/support@batbern\.ch recipients/i)).toBeVisible();
    await expect(page.getByRole('button', { name: /save/i })).toBeVisible();
  });

  test('should save support contacts and show success toast', async ({ page }) => {
    await page.goto(`${ADMIN_URL}?tab=7`);

    // Wait for field to load
    const field = page.getByLabel(/support@batbern\.ch recipients/i);
    await expect(field).toBeVisible({ timeout: 10_000 });

    // Clear and type a test value
    await field.fill('test-support@batbern.ch');

    // Save
    await page.getByRole('button', { name: /save/i }).click();

    // Verify success toast
    await expect(page.getByText(/settings saved/i)).toBeVisible({ timeout: 5_000 });
  });

  test('should persist saved support contacts after page reload', async ({ page }) => {
    const testEmail = `e2e-test-${Date.now()}@batbern.ch`;

    await page.goto(`${ADMIN_URL}?tab=7`);

    const field = page.getByLabel(/support@batbern\.ch recipients/i);
    await expect(field).toBeVisible({ timeout: 10_000 });

    await field.fill(testEmail);
    await page.getByRole('button', { name: /save/i }).click();
    await expect(page.getByText(/settings saved/i)).toBeVisible();

    // Reload and verify value persisted
    await page.reload();
    await expect(page.getByLabel(/support@batbern\.ch recipients/i)).toHaveValue(testEmail, {
      timeout: 10_000,
    });
  });
});
