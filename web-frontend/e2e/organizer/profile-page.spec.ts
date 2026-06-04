/**
 * E2E: Role-neutral /profile page + onboarding consent gate (Story 12.11 AC9)
 *
 * Smoke for the chromium (organizer) project: the staging organizer test user was
 * backfilled as consented by CUMS migration V17 (created long before the SSO go-live
 * cutoff), so:
 *   • /dashboard must NOT bounce to /profile?onboarding=1 (gate stays quiet), and
 *   • /profile renders BOTH tabs, with the consent tab showing the read-only
 *     "accepted on" line instead of the consent checkbox.
 *
 * Read-only — no mutations, no cleanup owed. testid-only locators.
 */

import { test, expect } from '@playwright/test';

test.describe('Profile page · Story 12.11', { tag: '@gate' }, () => {
  test('should_notGateConsentedOrganizer_when_dashboardOpened', async ({ page }) => {
    await page.goto('/dashboard');

    // The gate redirects consent-less users to /profile?onboarding=1 — a consented
    // (backfilled) organizer must stay on an organizer surface instead.
    await page.waitForLoadState('networkidle');
    expect(page.url()).not.toContain('onboarding=1');
  });

  test('should_renderBothTabsWithoutGate_when_profileOpened', async ({ page }) => {
    await page.goto('/profile');

    await expect(page.getByTestId('profile-tab')).toBeVisible({ timeout: 20_000 });
    await expect(page.getByTestId('consent-tab')).toBeVisible();
    // Consented user: no onboarding notice.
    await expect(page.getByTestId('onboarding-notice')).not.toBeVisible();

    // Consent tab shows the read-only accepted-on line, never the checkbox.
    await page.getByTestId('consent-tab').click();
    await expect(page.getByTestId('consent-accepted-on')).toBeVisible();
    await expect(page.getByTestId('consent-checkbox')).not.toBeVisible();
    // Newsletter toggle present (EMS newsletter_subscribers self-service).
    await expect(page.getByTestId('newsletter-toggle')).toBeVisible();
  });

  test('should_redirectOldSpeakerPortalProfilePath_when_visited', async ({ page }) => {
    await page.goto('/speaker-portal/profile');
    await expect(page.getByTestId('profile-tab')).toBeVisible({ timeout: 20_000 });
    expect(page.url()).toContain('/profile');
  });
});
