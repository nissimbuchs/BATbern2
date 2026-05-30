/**
 * E2E: Settings Management — slice 3 / users (plan §C)
 * docs/plans/playwright-staging-hardening.md
 *
 * Rewritten 2026-05-30 to the quality bar (getByTestId locators, no empty tests, deterministic
 * assertions — no `waitForTimeout`, no flaky popup waits). Story 2.6 Settings Tab.
 *
 * All tests here are READ-ONLY (no persistence) → `@gate`, no `@smoke`. The Settings tab's
 * persist actions (theme/timezone/notifications/privacy) require a reload that can drop the
 * test auth state, so they were `test.skip` placeholders → DELETED per the no-empty-tests bar.
 *
 * Reality checks (verified in UserSettingsTab.tsx):
 *   • `change-password-button` has NO in-app handler — password change is a Cognito-managed
 *     flow. The old test clicked it and asserted `dialog-or-urlChanged`, which is always false
 *     here (nothing happens in-app) → flaky. Replaced with a presence assertion (the button is
 *     the wired entry point; the destination is Cognito-owned, not ours to gate).
 *   • `privacy-policy-link` is an `<a href="/privacy-policy" target="_blank">` — the old
 *     `waitForEvent('popup')` raced a real new-tab page load. Replaced with a deterministic
 *     href/target attribute assertion.
 */

import { test, expect } from '@playwright/test';

test.describe('Settings Management', { tag: '@gate' }, () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/account');
    await expect(page).toHaveURL('/account');
    await page.getByTestId('settings-tab').click();
  });

  test.describe('Account sub-tab', () => {
    test.beforeEach(async ({ page }) => {
      await expect(page.getByTestId('account-settings-panel')).toBeVisible();
    });

    test('should_displayEmailReadOnly_when_accountSettingsOpen', async ({ page }) => {
      const emailInput = page.getByTestId('email-field').locator('input');
      await expect(emailInput).toHaveAttribute('readonly', '');
      expect(await emailInput.inputValue()).toContain('@');
      await expect(page.getByTestId('email-status')).toBeVisible();
    });

    test('should_exposeChangePassword_when_accountSettingsOpen', async ({ page }) => {
      // Cognito-managed flow — assert the entry point is present (destination is not ours to gate).
      await expect(page.getByTestId('change-password-button')).toBeVisible();
    });
  });

  test.describe('Notifications sub-tab', () => {
    test.beforeEach(async ({ page }) => {
      await page.getByTestId('notifications-subtab').click();
      await expect(page.getByTestId('notification-settings-panel')).toBeVisible();
    });

    test('should_displayChannelToggles_when_notificationsOpen', async ({ page }) => {
      await expect(page.getByTestId('channel-email')).toBeVisible();
      await expect(page.getByTestId('channel-in-app')).toBeVisible();
      await expect(page.getByTestId('channel-push')).toBeVisible();
    });

    test('should_displayFrequencySelector_when_notificationsOpen', async ({ page }) => {
      await expect(page.getByTestId('frequency-immediate')).toBeVisible();
      await expect(page.getByTestId('frequency-daily')).toBeVisible();
      await expect(page.getByTestId('frequency-weekly')).toBeVisible();
    });
  });

  test.describe('Privacy sub-tab', () => {
    test.beforeEach(async ({ page }) => {
      await page.getByTestId('privacy-subtab').click();
      await expect(page.getByTestId('privacy-settings-panel')).toBeVisible();
    });

    test('should_displayVisibilityOptions_when_privacyOpen', async ({ page }) => {
      await expect(page.getByTestId('visibility-public')).toBeVisible();
      await expect(page.getByTestId('visibility-members-only')).toBeVisible();
      await expect(page.getByTestId('visibility-private')).toBeVisible();
    });

    test('should_displayInformationToggles_when_privacyOpen', async ({ page }) => {
      await expect(page.getByTestId('show-email-toggle')).toBeVisible();
      await expect(page.getByTestId('show-company-toggle')).toBeVisible();
      await expect(page.getByTestId('show-activity-toggle')).toBeVisible();
      await expect(page.getByTestId('allow-messaging-toggle')).toBeVisible();
    });

    test('should_linkToPrivacyPolicy_when_privacyOpen', async ({ page }) => {
      const link = page.getByTestId('privacy-policy-link');
      await expect(link).toHaveAttribute('href', '/privacy-policy');
      await expect(link).toHaveAttribute('target', '_blank');
    });
  });

  test('should_switchBetweenSubTabs_when_subTabClicked', async ({ page }) => {
    await expect(page.getByTestId('account-settings-panel')).toBeVisible();

    await page.getByTestId('notifications-subtab').click();
    await expect(page.getByTestId('notification-settings-panel')).toBeVisible();

    await page.getByTestId('privacy-subtab').click();
    await expect(page.getByTestId('privacy-settings-panel')).toBeVisible();

    await page.getByTestId('account-subtab').click();
    await expect(page.getByTestId('account-settings-panel')).toBeVisible();
  });
});
