/**
 * E2E: Profile Management — slice 3 / users (plan §C)
 * docs/plans/playwright-staging-hardening.md
 *
 * Rewritten 2026-05-30 to the quality bar (getByTestId locators, no empty tests, signal-based
 * waits, mandatory cleanup for the one mutating path). Story 2.6 Profile Tab.
 *
 * Operates on the authenticated organizer test account's OWN /account profile (lowest possible
 * blast radius). The one mutating test (save bio) captures the original bio via the API in
 * `beforeAll` and RESTORES it in `afterAll`, so staging (= prod) is left byte-identical — the
 * same own-account capture/restore discipline the photo-upload @smoke uses for the picture.
 *
 * Deleted (asserted features that don't exist for the test account / were `test.skip`):
 *   • verified-email badge (varies by account), role-specific tabs (not rendered on this page),
 *     activity history ×2 (test account has none), bio-length validation (browser maxlength
 *     truncates, no error surfaces). All were empty `test.skip` placeholders → removed.
 *   • The exact "2 role badges = Organizer + Speaker" assertion was data-dependent on the
 *     account's role set; replaced with a data-agnostic "≥1 role badge" assertion.
 */

import { test, expect } from '@playwright/test';
import { readOrganizerToken } from '../helpers/user-fixture';

const API_URL = process.env.E2E_API_URL || 'http://localhost:8000';

async function getOwnBio(token: string): Promise<string> {
  const res = await fetch(`${API_URL}/api/v1/users/me`, {
    headers: { Authorization: `Bearer ${token}`, Accept: 'application/json' },
  });
  if (res.status !== 200) return '';
  const data = (await res.json()) as { bio?: string };
  return data.bio ?? '';
}

async function setOwnBio(token: string, bio: string): Promise<void> {
  await fetch(`${API_URL}/api/v1/users/me`, {
    method: 'PUT',
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ bio }),
  }).catch(() => undefined);
}

test.describe('Profile Management', { tag: '@gate' }, () => {
  // Serial: the bio test mutates the shared organizer account; serialise so capture/restore
  // can't interleave with another test reading the same account.
  test.describe.configure({ mode: 'serial' });

  let token: string;
  let originalBio: string;

  test.beforeAll(async () => {
    token = readOrganizerToken();
    originalBio = await getOwnBio(token);
  });

  test.afterAll(async () => {
    // Restore the account's bio to exactly what it was before this spec ran.
    await setOwnBio(token, originalBio);
  });

  test.beforeEach(async ({ page }) => {
    await page.goto('/account');
    await expect(page).toHaveURL('/account');
  });

  test('should_displayProfileHeader_when_userDataLoaded', async ({ page }) => {
    await expect(page.getByTestId('profile-photo')).toBeVisible();
    await expect(page.getByTestId('user-name')).not.toBeEmpty();
    await expect(page.getByTestId('user-email')).not.toBeEmpty();
    // At least one role badge renders (data-agnostic — the account always has ≥1 role).
    await expect(page.getByTestId('role-badge').first()).toBeVisible();
  });

  test('should_displayMemberSince_when_profileLoaded', async ({ page }) => {
    await expect(page.getByTestId('member-since')).toBeVisible();
  });

  test('should_enableEditMode_when_editProfileClicked', async ({ page }) => {
    await page.getByTestId('edit-profile-button').click();

    await expect(page.getByTestId('first-name-field').locator('input')).toBeEditable();
    await expect(page.getByTestId('last-name-field').locator('input')).toBeEditable();
    await expect(page.getByTestId('bio-field').locator('textarea').first()).toBeEditable();
    await expect(page.getByTestId('save-profile-button')).toBeVisible();
    await expect(page.getByTestId('cancel-edit-button')).toBeVisible();
  });

  test('should_displayBioCharacterCounter_when_editing', async ({ page }) => {
    await page.getByTestId('edit-profile-button').click();
    const counter = page.getByTestId('bio-char-counter');
    await expect(counter).toBeVisible();
    // UserProfileTab enforces a 5000-char bio (bioMaxLength), so the counter reads "…/5000".
    await expect(counter).toContainText('/5000');
  });

  test('should_saveProfileChanges_when_bioEdited', async ({ page }) => {
    await page.getByTestId('edit-profile-button').click();

    const bio = page.getByTestId('bio-field').locator('textarea').first();
    await bio.clear();
    await bio.fill('Playwright slice-3 bio — restored in afterAll.');

    await page.getByTestId('save-profile-button').click();

    // Save success ⇔ edit mode exits and the Edit button reappears (no fixed sleep).
    await expect(page.getByTestId('edit-profile-button')).toBeVisible({ timeout: 15_000 });
  });
});
