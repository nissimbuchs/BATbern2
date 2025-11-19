/**
 * E2E Test: Settings Management Flow
 * Story 2.6: User Account Management Frontend
 * Tests AC17-35: Settings Tab functionality
 */

import { test, expect } from '@playwright/test';

test.describe('Settings Management Flow', () => {
  test.beforeEach(async ({ page }) => {
    // Navigate to login and authenticate
    await page.goto('/login');
    await page.fill('[data-testid="email-input"]', 'anna.mueller@techcorp.ch');
    await page.fill('[data-testid="password-input"]', 'TestPassword123!');
    await page.click('[data-testid="login-button"]');
    await expect(page).toHaveURL('/dashboard');

    // Navigate to account page
    await page.click('[data-testid="user-menu-button"]');
    await page.click('[data-testid="my-account-link"]');
    await expect(page).toHaveURL('/account');

    // Switch to Settings tab
    await page.click('[data-testid="settings-tab"]');
  });

  test.describe('Account Settings Sub-Tab', () => {
    test.beforeEach(async ({ page }) => {
      // Ensure we're on Account sub-tab (default)
      await expect(page.locator('[data-testid="account-settings-panel"]')).toBeVisible();
    });

    test('should_displayEmailAsReadOnly_when_settingsTabOpened', async ({ page }) => {
      // AC17-18: Email address displays as read-only with Cognito status
      const emailField = page.locator('[data-testid="email-field"]');
      await expect(emailField).toBeVisible();
      await expect(emailField).toContainText('anna.mueller@techcorp.ch');
      await expect(emailField).toHaveAttribute('readonly', '');

      await expect(page.locator('[data-testid="email-status"]')).toContainText(
        'Verified (managed by Cognito)'
      );
    });

    test('should_showCognitoPasswordChangeFlow_when_changePasswordClicked', async ({ page }) => {
      // AC19: Change Password button redirects to Cognito flow
      await page.click('[data-testid="change-password-button"]');

      // Should redirect to Cognito hosted UI or show modal
      // For now, we'll check if a dialog or redirect happens
      await page.waitForTimeout(1000); // Allow redirect/modal to appear

      // Check if redirected or dialog opened
      const hasDialog = await page.locator('[role="dialog"]').isVisible();
      const urlChanged = page.url() !== '/account';

      expect(hasDialog || urlChanged).toBeTruthy();
    });

    test('should_persistThemeChange_when_themeSelected', async ({ page }) => {
      // AC20-21: Theme selector persists changes
      await page.click('[data-testid="theme-dark"]');

      // Save changes
      await page.click('[data-testid="save-account-settings-button"]');

      // Verify success message
      await expect(page.locator('[data-testid="success-toast"]')).toBeVisible();

      // Reload page and verify theme persists
      await page.reload();
      await page.click('[data-testid="settings-tab"]');

      const darkThemeRadio = page.locator('[data-testid="theme-dark"]');
      await expect(darkThemeRadio).toBeChecked();
    });

    test('should_persistTimezoneChange_when_timezoneSelected', async ({ page }) => {
      // AC23-24: Timezone selector persists changes
      const timezoneAutocomplete = page.locator('[data-testid="timezone-autocomplete"]');

      await timezoneAutocomplete.click();
      await timezoneAutocomplete.fill('Europe/Berlin');
      await page.click('[data-testid="timezone-option-Europe/Berlin"]');

      // Save changes
      await page.click('[data-testid="save-account-settings-button"]');

      // Verify success message
      await expect(page.locator('[data-testid="success-toast"]')).toBeVisible();

      // Reload page and verify timezone persists
      await page.reload();
      await page.click('[data-testid="settings-tab"]');

      await expect(page.locator('[data-testid="timezone-autocomplete"]')).toContainText(
        'Europe/Berlin'
      );
    });
  });

  test.describe('Notification Settings Sub-Tab', () => {
    test.beforeEach(async ({ page }) => {
      // Switch to Notifications sub-tab
      await page.click('[data-testid="notifications-subtab"]');
      await expect(page.locator('[data-testid="notification-settings-panel"]')).toBeVisible();
    });

    test('should_displayNotificationChannelToggles_when_notificationsTabOpened', async ({
      page,
    }) => {
      // AC25: Notification channel toggles display
      await expect(page.locator('[data-testid="channel-email"]')).toBeVisible();
      await expect(page.locator('[data-testid="channel-in-app"]')).toBeVisible();
      await expect(page.locator('[data-testid="channel-push"]')).toBeVisible();
    });

    test('should_displayFrequencySelector_when_notificationsTabOpened', async ({ page }) => {
      // AC26: Notification frequency selector displays
      await expect(page.locator('[data-testid="frequency-immediate"]')).toBeVisible();
      await expect(page.locator('[data-testid="frequency-daily"]')).toBeVisible();
      await expect(page.locator('[data-testid="frequency-weekly"]')).toBeVisible();
    });

    test('should_showAdvancedFeaturesInfo_when_notificationsTabRendered', async ({ page }) => {
      // AC27: Info message displays for advanced features
      await expect(page.locator('[data-testid="advanced-features-info"]')).toBeVisible();
      await expect(page.locator('[data-testid="advanced-features-info"]')).toContainText('Epic 7');
    });

    test('should_persistNotificationPreferences_when_saveClicked', async ({ page }) => {
      // AC28-29: Changes persist and success toast displays
      // Disable email notifications
      await page.click('[data-testid="channel-email"]');

      // Change frequency to daily digest
      await page.click('[data-testid="frequency-daily"]');

      // Save changes
      await page.click('[data-testid="save-notification-settings-button"]');

      // Verify success message
      await expect(page.locator('[data-testid="success-toast"]')).toBeVisible();
      await expect(page.locator('[data-testid="success-toast"]')).toContainText('saved');

      // Reload and verify persistence
      await page.reload();
      await page.click('[data-testid="settings-tab"]');
      await page.click('[data-testid="notifications-subtab"]');

      await expect(page.locator('[data-testid="channel-email"]')).not.toBeChecked();
      await expect(page.locator('[data-testid="frequency-daily"]')).toBeChecked();
    });
  });

  test.describe('Privacy Settings Sub-Tab', () => {
    test.beforeEach(async ({ page }) => {
      // Switch to Privacy sub-tab
      await page.click('[data-testid="privacy-subtab"]');
      await expect(page.locator('[data-testid="privacy-settings-panel"]')).toBeVisible();
    });

    test('should_displayVisibilitySelector_when_privacyTabOpened', async ({ page }) => {
      // AC30: Profile visibility selector displays
      await expect(page.locator('[data-testid="visibility-public"]')).toBeVisible();
      await expect(page.locator('[data-testid="visibility-members-only"]')).toBeVisible();
      await expect(page.locator('[data-testid="visibility-private"]')).toBeVisible();
    });

    test('should_displayInformationToggles_when_privacyTabOpened', async ({ page }) => {
      // AC31: Profile information display toggles
      await expect(page.locator('[data-testid="show-email-toggle"]')).toBeVisible();
      await expect(page.locator('[data-testid="show-company-toggle"]')).toBeVisible();
      await expect(page.locator('[data-testid="show-activity-toggle"]')).toBeVisible();
    });

    test('should_displayMessagingToggle_when_privacyTabOpened', async ({ page }) => {
      // AC32: Communication toggle displays
      await expect(page.locator('[data-testid="allow-messaging-toggle"]')).toBeVisible();
    });

    test('should_openPrivacyPolicy_when_linkClicked', async ({ page }) => {
      // AC33: Privacy Policy link opens document
      const [privacyPage] = await Promise.all([
        page.waitForEvent('popup'),
        page.click('[data-testid="privacy-policy-link"]'),
      ]);

      // Verify privacy policy page opened
      expect(privacyPage.url()).toContain('privacy');
    });

    test('should_persistPrivacySettings_when_saveClicked', async ({ page }) => {
      // AC34-35: Changes persist and success toast displays
      // Change visibility to Members Only
      await page.click('[data-testid="visibility-members-only"]');

      // Disable email display
      await page.click('[data-testid="show-email-toggle"]');

      // Save changes
      await page.click('[data-testid="save-privacy-settings-button"]');

      // Verify success message
      await expect(page.locator('[data-testid="success-toast"]')).toBeVisible();

      // Reload and verify persistence
      await page.reload();
      await page.click('[data-testid="settings-tab"]');
      await page.click('[data-testid="privacy-subtab"]');

      await expect(page.locator('[data-testid="visibility-members-only"]')).toBeChecked();
      await expect(page.locator('[data-testid="show-email-toggle"]')).not.toBeChecked();
    });
  });

  test.describe('General Settings Features', () => {
    test('should_switchBetweenSubTabs_when_settingsSubTabClicked', async ({ page }) => {
      // AC37: Settings sub-tab navigation works
      await expect(page.locator('[data-testid="account-settings-panel"]')).toBeVisible();

      await page.click('[data-testid="notifications-subtab"]');
      await expect(page.locator('[data-testid="notification-settings-panel"]')).toBeVisible();

      await page.click('[data-testid="privacy-subtab"]');
      await expect(page.locator('[data-testid="privacy-settings-panel"]')).toBeVisible();

      await page.click('[data-testid="account-subtab"]');
      await expect(page.locator('[data-testid="account-settings-panel"]')).toBeVisible();
    });
  });
});
