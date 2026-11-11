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
 * The tabs tests are read-only. Story 4.10 adds one mutating test: saving the own bio sends
 * `PATCH /users/{own username}` (the /users/me PATCH twin is gone); it captures the bio first
 * and restores it in a finally, so the shared organizer account is left as it was.
 */

import { test, expect } from '@playwright/test';
import { readOrganizerToken } from '../helpers/event-fixture';

const API_URL = process.env.E2E_API_URL || 'http://localhost:8000';

async function ownProfile(token: string): Promise<{ username: string; bio: string | null }> {
  const res = await fetch(`${API_URL}/api/v1/users/me`, {
    headers: { Authorization: `Bearer ${token}`, Accept: 'application/json' },
  });
  expect(res.status, 'GET /users/me').toBe(200);
  const body = (await res.json()) as { id: string; bio?: string | null };
  // "" is read as null: a blank bio is stored as NULL since Story 4.10 (rows from before may hold "").
  return { username: body.id, bio: body.bio?.trim() ? body.bio : null };
}

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
    // Newsletter section present (EMS newsletter_subscribers self-service; #1244 replaced the
    // single toggle with one checkbox per address, the old `newsletter-toggle` id is gone).
    await expect(page.getByTestId('newsletter-section')).toBeVisible();
  });

  test('should_redirectOldSpeakerPortalProfilePath_when_visited', async ({ page }) => {
    await page.goto('/speaker-portal/profile');
    await expect(page.getByTestId('profile-tab')).toBeVisible({ timeout: 20_000 });
    expect(page.url()).toContain('/profile');
  });

  test('should_patchOwnUsernameAndPersist_when_bioSaved', async ({ page }) => {
    const token = readOrganizerToken();
    const before = await ownProfile(token);
    const marker = `Bio saved by the Story 4.10 e2e test ${Date.now()}`;
    try {
      await page.goto('/profile');
      const bio = page.locator('#bio');
      await expect(bio).toBeVisible({ timeout: 20_000 });
      await bio.fill(marker);

      const patch = page.waitForRequest(
        (req) => req.method() === 'PATCH' && /\/api\/v1\/users\/[^/?]+$/.test(req.url())
      );
      // By test id: the organizer's profile language is German, the label is not "Save Changes".
      await page.getByTestId('profile-save-button').click();
      const request = await patch;
      expect(new URL(request.url()).pathname).toBe(
        `/api/v1/users/${encodeURIComponent(before.username)}`
      );

      await page.reload();
      await expect(page.locator('#bio')).toHaveValue(marker, { timeout: 20_000 });
    } finally {
      const res = await fetch(`${API_URL}/api/v1/users/${encodeURIComponent(before.username)}`, {
        method: 'PATCH',
        headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
        // A blank bio clears it to NULL, so "" puts back a NULL bio exactly.
        body: JSON.stringify({ bio: before.bio ?? '' }),
      });
      expect(res.status, 'restore the bio').toBe(200);
      expect((await ownProfile(token)).bio, 'the bio is exactly as before').toBe(before.bio);
    }
  });
});
