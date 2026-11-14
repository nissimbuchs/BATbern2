/**
 * E2E: Profile Photo Upload — slice 1 / uploads (plan §C)
 * docs/plans/playwright-staging-hardening.md
 *
 * Rewritten 2026-05-30 to reality + the quality bar (testid-only locators, factory data,
 * mandatory cleanup, no empty tests). Story 2.6 AC10-13 / AC39.
 *
 * Reality check (verified in ProfileHeader.tsx + userAccountApi.ts):
 *   • The control is a hidden `<input type="file" accept="image/jpeg,image/png">` behind a
 *     camera IconButton — there is NO crop dialog and NO client-side progress UI. The four
 *     prior `test.skip`s asserted a crop interface / a phase-by-phase progress UI / a
 *     standalone size-validation screen that were NEVER built → DELETED (logged below).
 *   • Upload is the upload contract (ADR-002, Story 17.11a): POST /uploads/profile-picture with
 *     the own username as target → PUT to S3 → POST /uploads/profile-picture/{uploadId}/confirm
 *     with the target (confirm checks the file is in S3 and attaches it). The happy-path `@smoke`
 *     exercises all three phases end-to-end and asserts that only `/uploads/profile-picture*`
 *     calls carry the upload; a per-phase UI test is therefore redundant and was deleted.
 *   • Upload failure surfaces only via `console.error` — there is NO inline error UI, so the
 *     prior zero-assertion `should_showInlineError_*` test asserted a non-existent feature
 *     → DELETED.
 *   • File-TYPE validation is the input's `accept` attribute (asserted in the control test);
 *     file-SIZE validation is server-side during S3/confirm with no client UI → no UI test.
 *
 * Prod-safety (plan risk #1): the `@smoke` mutates the authenticated organizer test
 * account's OWN profile photo — the lowest-blast-radius mutation available. Uploads have no
 * prefix-sweep path, so teardown is an explicit `DELETE /users/{own username}/picture` (cleanupOwn-
 * ProfilePicture) in afterAll, leaving the account residue-free (photo-less) even if the
 * test crashes mid-flow. The chromium project is authenticated (the upload NEEDS the JWT),
 * so — unlike the registration @smoke — we do NOT force an anonymous storageState.
 */

import { test, expect } from '@playwright/test';
import * as factory from '../helpers/test-data-factory';
import { readOrganizerToken } from '../helpers/event-fixture';
import { cleanupOwnProfilePicture } from '../helpers/test-fixtures-cleanup';
import { stripPresignedAuthHeader } from '../helpers/strip-presigned-auth';

test.describe('Profile Photo Upload', { tag: '@gate' }, () => {
  // Serial: the @smoke mutates the shared organizer account photo; serialising avoids a
  // parallel-worker race on that single shared resource and its afterAll teardown.
  test.describe.configure({ mode: 'serial' });

  let token: string;

  test.beforeAll(() => {
    // Organizer idToken — required for the afterAll API teardown (DELETE /users/{own username}/picture).
    token = readOrganizerToken();
  });

  test.beforeEach(async ({ page }) => {
    await page.goto('/account');
    await expect(page).toHaveURL('/account');
  });

  test.afterAll(async () => {
    // Belt-and-suspenders: restore the test account to photo-less regardless of test outcome.
    await cleanupOwnProfilePicture(token);
  });

  test('should_exposeUploadControl_when_accountLoaded', async ({ page }) => {
    // AC10 + AC39: the upload button is visible and the hidden file input restricts to
    // JPEG/PNG (this IS the file-type-validation coverage — the accept attribute is the gate).
    await expect(page.getByTestId('upload-photo-button')).toBeVisible();

    const fileInput = page.getByTestId('profile-photo-input');
    await expect(fileInput).toBeAttached();

    const accept = await fileInput.getAttribute('accept');
    expect(accept).toContain('image/jpeg');
    expect(accept).toContain('image/png');
  });

  test(
    'should_uploadAndRemovePhoto_when_validImageSelected',
    { tag: ['@smoke', '@gate'] },
    async ({ page }) => {
      // AC11-13: select a valid PNG → the 3-phase upload runs → the photo is associated →
      // the remove control appears (it renders ONLY when profilePictureUrl is truthy, so its
      // visibility is a precise success signal — no fixed sleeps). Then remove it (AC13),
      // which also serves as the in-test cleanup; afterAll is the safety net.
      const png = factory.uploadPngFile();

      // The global Authorization header (extraHTTPHeaders) would make the object store reject
      // the presigned PUT for carrying two auth mechanisms — strip it on the storage request.
      await stripPresignedAuthHeader(page);

      // Every API call that touches the picture, to prove the upload runs on the contract only.
      const pictureCalls: string[] = [];
      page.on('request', (request) => {
        const { pathname } = new URL(request.url());
        if (/\/api\/v1\/(uploads\/|users\/[^/]+\/(picture|profile-picture))/.test(pathname)) {
          pictureCalls.push(`${request.method()} ${pathname}`);
        }
      });

      await page.getByTestId('profile-photo-input').setInputFiles(png);

      // S3 round-trip + confirm + react-query refetch — generous wait, but signal-based.
      await expect(page.getByTestId('remove-photo-button')).toBeVisible({ timeout: 30_000 });
      expect(pictureCalls).toEqual([
        'POST /api/v1/uploads/profile-picture',
        expect.stringMatching(/^POST \/api\/v1\/uploads\/profile-picture\/[^/]+\/confirm$/),
      ]);

      // The picture is stored, not only shown from local state: it is still there after a reload.
      await page.reload();
      await expect(page.getByTestId('remove-photo-button')).toBeVisible({ timeout: 15_000 });

      // AC13: removal goes through a native window.confirm — auto-accept it.
      page.on('dialog', (dialog) => dialog.accept());
      await page.getByTestId('remove-photo-button').click();

      // The remove control unmounts once profilePictureUrl is cleared.
      await expect(page.getByTestId('remove-photo-button')).toHaveCount(0);
    }
  );
});
