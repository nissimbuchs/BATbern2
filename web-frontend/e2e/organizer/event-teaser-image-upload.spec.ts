/**
 * Organizer · Event teaser image upload (Story 17.11c): the Settings sub-tab of the event's Details
 * tab uploads a teaser image through the upload contract, purpose `teaser-image` with the target
 * `eventCode` — `POST /uploads/teaser-image`, the presigned PUT,
 * `POST /uploads/teaser-image/{uploadId}/confirm` — and the confirm creates the teaser image. It
 * shows on the event after a reload and in `GET /events/{eventCode}/teaser-images`. The removed
 * `…/teaser-images/upload-url` and `…/teaser-images/confirm` are never called.
 *
 * Guarded to LOCAL DEV (`TEST_ENV=development`, never `@gate`): it creates a fixture event (event
 * number >= 10000, the sweepable test marker). Teardown deletes the teaser image (row, upload row and
 * S3 object) and the event.
 */

import { test, expect, type Page } from '@playwright/test';
import { createRegistrationEvent, readOrganizerToken } from '../helpers/event-fixture';
import { cleanupByCode } from '../helpers/test-fixtures-cleanup';
import { waitForAppShell } from '../helpers/app-shell';
import { stripPresignedAuthHeader } from '../helpers/strip-presigned-auth';
import { uploadPngFile } from '../helpers/test-data-factory';

const LOCAL_DEV = (process.env.TEST_ENV ?? 'development') === 'development';
const API_URL = process.env.E2E_API_URL || 'http://localhost:8000';
const describeLocal = LOCAL_DEV ? test.describe : test.describe.skip;

describeLocal('Organizer · Event teaser image upload (17.11c)', () => {
  let token: string;
  let eventCode = '';
  const uploadedImageIds: string[] = [];

  const api = (path: string, init: RequestInit = {}) =>
    fetch(`${API_URL}/api/v1${path}`, {
      ...init,
      headers: {
        Authorization: `Bearer ${token}`,
        Accept: 'application/json',
        'Content-Type': 'application/json',
        ...(init.headers ?? {}),
      },
    });

  test.beforeAll(async () => {
    token = readOrganizerToken();
    ({ eventCode } = await createRegistrationEvent(token));
  });

  test.afterAll(async () => {
    for (const id of uploadedImageIds) {
      await api(`/events/${eventCode}/teaser-images/${id}`, { method: 'DELETE' }).catch(
        () => undefined
      );
    }
    await cleanupByCode(token, eventCode);
  });

  async function openSettings(page: Page) {
    await page.goto(`/organizer/events/${eventCode}?tab=details`);
    await waitForAppShell(page);
    await page.getByTestId('details-subtab-settings').click();
    await expect(page.getByTestId('teaser-image-upload-btn')).toBeVisible({ timeout: 15_000 });
  }

  test('should_showTeaserOnTheEvent_when_uploadedOnTheContract', async ({ page }) => {
    await stripPresignedAuthHeader(page);

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

    await openSettings(page);
    await expect(page.getByTestId('teaser-image-card')).toHaveCount(0);

    await page.getByTestId('teaser-image-input').setInputFiles(uploadPngFile());

    const card = page.getByTestId('teaser-image-card');
    await expect(card).toHaveCount(1, { timeout: 30_000 });
    const imageId = (await card.getAttribute('data-image-id')) ?? '';
    expect(imageId).not.toBe('');
    uploadedImageIds.push(imageId);

    expect(uploadCalls).toHaveLength(2);
    expect(uploadCalls[0]).toBe('POST /api/v1/uploads/teaser-image');
    expect(uploadCalls[1]).toMatch(
      /^POST \/api\/v1\/uploads\/teaser-image\/[0-9a-f-]{36}\/confirm$/
    );
    const uploadId = uploadCalls[1].split('/')[5];

    // Stored, not only in local state: still there after a reload.
    await openSettings(page);
    await expect(page.getByTestId('teaser-image-card')).toHaveCount(1, { timeout: 15_000 });
    await expect(page.getByTestId('teaser-image-card')).toHaveAttribute('data-image-id', imageId);

    const listed = await api(`/events/${eventCode}/teaser-images`);
    expect(listed.status).toBe(200);
    const images = (await listed.json()) as Array<{ id: string; imageUrl: string }>;
    expect(images).toEqual([
      expect.objectContaining({
        id: imageId,
        imageUrl: expect.stringContaining(`/events/${eventCode}/teaser/${uploadId}.png`),
      }),
    ]);
  });
});
