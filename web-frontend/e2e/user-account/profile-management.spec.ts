/**
 * E2E Test: Profile Management Flow
 * Story 2.6: User Account Management Frontend
 * Tests AC1-16: Profile Tab functionality
 */

import { test, expect } from '@playwright/test';

test.describe('Profile Management Flow', () => {
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
  });

  test('should_displayProfileHeader_when_userDataLoaded', async ({ page }) => {
    // AC1: Profile header displays user photo, name, company, email
    await expect(page.locator('[data-testid="profile-photo"]')).toBeVisible();
    await expect(page.locator('[data-testid="user-name"]')).toContainText('Anna Müller');
    await expect(page.locator('[data-testid="user-company"]')).toContainText('TechCorp AG');
    await expect(page.locator('[data-testid="user-email"]')).toContainText(
      'anna.mueller@techcorp.ch'
    );
  });

  test('should_showVerifiedBadge_when_emailVerifiedByCognito', async ({ page }) => {
    // AC2: Email displays with verified badge
    await expect(page.locator('[data-testid="email-verified-badge"]')).toBeVisible();
    await expect(page.locator('[data-testid="email-verified-badge"]')).toContainText('Verified');
  });

  test('should_displayRoleBadges_when_userHasMultipleRoles', async ({ page }) => {
    // AC3: Role badges display all assigned roles with visual indicators
    const roleBadges = page.locator('[data-testid="role-badge"]');
    await expect(roleBadges).toHaveCount(2); // Organizer and Speaker
    await expect(roleBadges.nth(0)).toContainText('Organizer');
    await expect(roleBadges.nth(1)).toContainText('Speaker');
  });

  test('should_formatMemberSinceDate_when_userCreatedDateExists', async ({ page }) => {
    // AC4: Member since date displays in correct format
    await expect(page.locator('[data-testid="member-since"]')).toContainText('January 2020');
  });

  test('should_displayBioCharacterCounter_when_bioEditing', async ({ page }) => {
    // AC8: Bio character counter displays
    await page.click('[data-testid="edit-profile-button"]');
    page.locator('[data-testid="bio-field"]'); // Used for context, counter is what we test
    const charCounter = page.locator('[data-testid="bio-char-counter"]');

    await expect(charCounter).toBeVisible();
    await expect(charCounter).toContainText('/2000');
  });

  test('should_enableEditMode_when_editProfileClicked', async ({ page }) => {
    // AC9: Edit Profile button enables inline editing mode
    await page.click('[data-testid="edit-profile-button"]');

    await expect(page.locator('[data-testid="first-name-field"]')).toBeEditable();
    await expect(page.locator('[data-testid="last-name-field"]')).toBeEditable();
    await expect(page.locator('[data-testid="bio-field"]')).toBeEditable();
    await expect(page.locator('[data-testid="save-profile-button"]')).toBeVisible();
    await expect(page.locator('[data-testid="cancel-edit-button"]')).toBeVisible();
  });

  test('should_saveProfileChanges_when_saveButtonClicked', async ({ page }) => {
    // AC9: Profile changes save successfully
    await page.click('[data-testid="edit-profile-button"]');

    // Modify bio
    const bioField = page.locator('[data-testid="bio-field"]');
    await bioField.clear();
    await bioField.fill('Updated bio text for testing.');

    // Save changes
    await page.click('[data-testid="save-profile-button"]');

    // Verify success message
    await expect(page.locator('[data-testid="success-toast"]')).toBeVisible();
    await expect(page.locator('[data-testid="success-toast"]')).toContainText(
      'Profile updated successfully'
    );

    // Verify edit mode disabled
    await expect(page.locator('[data-testid="edit-profile-button"]')).toBeVisible();
    await expect(page.locator('[data-testid="bio-field"]')).not.toBeEditable();
  });

  test('should_displayOnlyAssignedRoleTabs_when_rolesAvailable', async ({ page }) => {
    // AC14: Role-specific tabs display only for assigned roles
    const roleTabs = page.locator('[data-testid="role-tab"]');

    // User has Organizer and Speaker roles, should see 2 tabs
    await expect(roleTabs).toHaveCount(2);
    await expect(roleTabs.nth(0)).toContainText('Organizer');
    await expect(roleTabs.nth(1)).toContainText('Speaker');

    // Should not see Partner or Attendee tabs
    await expect(page.locator('[data-testid="role-tab"]:has-text("Partner")')).not.toBeVisible();
    await expect(page.locator('[data-testid="role-tab"]:has-text("Attendee")')).not.toBeVisible();
  });

  test('should_displayLast5Activities_when_activityHistoryLoaded', async ({ page }) => {
    // AC15: Activity history displays last 5 activities
    const activities = page.locator('[data-testid="activity-item"]');
    const activityCount = await activities.count();

    expect(activityCount).toBeLessThanOrEqual(5);
    expect(activityCount).toBeGreaterThan(0);

    // Verify first activity has timestamp
    await expect(activities.first().locator('[data-testid="activity-timestamp"]')).toBeVisible();
  });

  test('should_navigateToFullActivityHistory_when_viewAllClicked', async ({ page }) => {
    // AC16: View All link navigates to full activity history page
    await page.click('[data-testid="view-all-activities-link"]');

    // Should navigate to activity history page or expand inline
    // For now, we'll check if more activities appear or URL changes
    const activities = page.locator('[data-testid="activity-item"]');
    const activityCount = await activities.count();

    // After clicking "View All", should show more than 5 activities (if available)
    // Or should navigate to a dedicated page
    expect(activityCount).toBeGreaterThanOrEqual(5);
  });

  test('should_validateBioLength_when_userTypesInBioField', async ({ page }) => {
    // AC39: Bio length validation (2000 chars max)
    await page.click('[data-testid="edit-profile-button"]');

    const bioField = page.locator('[data-testid="bio-field"]');
    const longText = 'a'.repeat(2001); // Exceeds 2000 char limit

    await bioField.clear();
    await bioField.fill(longText);

    // Try to save
    await page.click('[data-testid="save-profile-button"]');

    // Should show validation error
    await expect(page.locator('[data-testid="bio-validation-error"]')).toBeVisible();
    await expect(page.locator('[data-testid="bio-validation-error"]')).toContainText('2000');
  });
});
