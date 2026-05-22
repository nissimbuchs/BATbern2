import { test, expect } from '@playwright/test';

/**
 * Story 11.E.3 (AC10 + AC9): speaker+organizer logs in, sees both nav sections (Story
 * 11.E.3's cherry-pick of 73d94688), switches between speaker dashboard and organizer
 * kanban without re-authenticating.
 *
 * Requires a dual-role test user (SPEAKER + ORGANIZER). Marked fixme until the test user
 * is provisioned with both roles.
 */
test.describe('Speaker portal — cross-portal nav', () => {
  test.skip(
    !process.env.SPEAKER_AUTH_TOKEN,
    'SPEAKER_AUTH_TOKEN not set — run setup-test-users first'
  );

  test.fixme('should_navigateBetweenPortals_when_userIsSpeakerPlusOrganizer', async ({ page }) => {
    await page.goto('/speaker-portal/dashboard');
    // Grouped nav (NavigationMenu with userRoles.length > 1) renders section dividers.
    // Click into organizer kanban via the organizer section, verify URL changes without
    // re-auth.
    await expect(page).toHaveURL(/\/speaker-portal\/dashboard/);
  });
});
