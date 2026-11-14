/**
 * E2E: Admin · Global Teaser Images — full UI CRUD (slice 12 / admin tabs, plan §C)
 * docs/plans/playwright-staging-hardening.md
 *
 * Real Create → Read → Update → Delete of a global teaser image, driven through the frontend
 * (Admin page → Global Images tab). Images are freely creatable+deletable, so they gate
 * per-deploy → `@smoke`.
 *   • CREATE = the upload contract, purpose `teaser-image` with the target `_global` (Story
 *     17.11c: `POST /uploads/teaser-image` → S3/MinIO PUT → confirm with the same target), reusing
 *     the slice-1 `stripPresignedAuthHeader` (the global Authorization header is fatal on a
 *     presigned PUT — see that helper). A real 1×1 PNG from the factory. The removed
 *     `…/teaser-images/upload-url` and `…/confirm` are never called.
 *   • READ   = the new gallery item renders (id-keyed testid).
 *   • UPDATE = change the slide-position Select to a different value; verify it persisted.
 *   • DELETE = the per-image remove button (no confirm dialog); verify it is gone server-side.
 *
 * Cleanup: teaser images have NO prefix-sweep entityType, so the test captures the id assigned
 * at upload-confirm (diffing the gallery before/after) and the UI Delete step IS the teardown;
 * the afterAll `DELETE /events/_global/teaser-images/{id}` is the backstop if a step failed.
 *
 * Cap caveat: the global gallery is capped at 10 (MAX_GLOBAL_IMAGES). The test asserts there is
 * room before uploading (it adds exactly one image transiently and removes it); a full prod
 * gallery would fail this loudly rather than silently skip.
 */

import { test, expect, type Page } from '@playwright/test';
import * as factory from '../helpers/test-data-factory';
import { readOrganizerToken } from '../helpers/event-fixture';
import { stripPresignedAuthHeader } from '../helpers/strip-presigned-auth';
import {
  listGlobalImageIds,
  getGlobalImage,
  deleteGlobalImage,
} from '../helpers/global-image-fixture';

const ADMIN_URL = '/organizer/admin';
const MAX_GLOBAL_IMAGES = 10;

test.describe('AdminTabs · Global Images CRUD', { tag: ['@smoke', '@gate'] }, () => {
  test.describe.configure({ mode: 'serial' });

  let token: string;
  let createdId: string | null = null;

  test.beforeAll(() => {
    token = readOrganizerToken();
  });

  test.afterAll(async () => {
    if (createdId) await deleteGlobalImage(token, createdId);
  });

  async function openGlobalImagesTab(page: Page) {
    await page.goto(`${ADMIN_URL}?tab=7`);
    await expect(page.getByTestId('global-images-tab')).toBeVisible({ timeout: 20_000 });
  }

  test('should_createReadUpdateDelete_globalImage_via UI', async ({ page }) => {
    await stripPresignedAuthHeader(page);

    const before = await listGlobalImageIds(token);
    expect(before.length, 'global gallery must have room for one more image').toBeLessThan(
      MAX_GLOBAL_IMAGES
    );

    // Every API call that could carry a teaser upload, to prove it runs on the contract only.
    const uploadCalls: string[] = [];
    page.on('request', (request) => {
      const { pathname } = new URL(request.url());
      if (
        /\/api\/v1\/(uploads\/|events\/[^/]+\/teaser-images\/(upload-url|confirm))/.test(pathname)
      ) {
        uploadCalls.push(`${request.method()} ${pathname}`);
      }
    });

    await openGlobalImagesTab(page);

    // ── CREATE (upload contract, target _global, via the hidden file input) ──────────────────
    const png = factory.uploadPngFile();
    await page.getByTestId('global-image-input').setInputFiles({
      name: png.name,
      mimeType: png.mimeType,
      buffer: png.buffer,
    });

    // The upload runs reserve → PUT → confirm; the new image appears once confirm resolves.
    // Capture its server id by diffing the gallery (no prefix-sweep path for images).
    await expect
      .poll(
        async () => (await listGlobalImageIds(token)).filter((id) => !before.includes(id)).length,
        {
          timeout: 30_000,
          message: 'upload should add exactly one global image',
        }
      )
      .toBe(1);
    createdId = (await listGlobalImageIds(token)).find((id) => !before.includes(id)) ?? null;
    expect(createdId).not.toBeNull();
    expect(uploadCalls).toHaveLength(2);
    expect(uploadCalls[0]).toBe('POST /api/v1/uploads/teaser-image');
    expect(uploadCalls[1]).toMatch(
      /^POST \/api\/v1\/uploads\/teaser-image\/[0-9a-f-]{36}\/confirm$/
    );

    // ── READ ──────────────────────────────────────────────────────────────────────────────
    await expect(page.getByTestId(`global-image-item-${createdId}`)).toBeVisible({
      timeout: 15_000,
    });

    // ── UPDATE (change the slide position to a different value) ─────────────────────────────
    const current = (await getGlobalImage(token, createdId!))?.presentationPosition;
    const target = current === 'AFTER_COMMITTEE' ? 'AFTER_TOPIC_REVEAL' : 'AFTER_COMMITTEE';
    await page.getByTestId(`global-image-position-${createdId}`).click();
    await page.getByTestId(`global-image-position-option-${target}`).click();
    // PATCH is async (optimistic + invalidate) — poll the API until the new position persists.
    await expect
      .poll(async () => (await getGlobalImage(token, createdId!))?.presentationPosition, {
        timeout: 15_000,
        message: 'position change should persist',
      })
      .toBe(target);

    // ── DELETE (per-image remove button — no confirm dialog) ────────────────────────────────
    const item = page.getByTestId(`global-image-item-${createdId}`);
    await page.getByTestId(`global-image-delete-${createdId}`).click();
    await expect(item).toBeHidden({ timeout: 15_000 });
    // Authoritative verify: gone server-side.
    expect(await getGlobalImage(token, createdId!)).toBeNull();
    createdId = null; // cleaned up via UI → afterAll backstop is a no-op
  });
});
