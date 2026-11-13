/**
 * Organizer · Event photo upload (Story 17.11b): the Wrap-up tab uploads photos through the upload
 * contract, purpose `event-photo` with the target `eventCode` — `POST /uploads/event-photo`, the
 * presigned PUT, `POST /uploads/event-photo/{uploadId}/confirm` — and the confirm creates the photo.
 * It shows in the gallery after a reload and in the homepage sample (`GET /public/event-photos`).
 * The removed `…/photos/upload-url` and `…/photos/confirm` are never called.
 *
 * Guarded to LOCAL DEV (`TEST_ENV=development`, never `@gate`): it creates a fixture event (event
 * number >= 10000, the sweepable test marker), dates it an hour ago so the homepage sample holds
 * it, and force-advances it to EVENT_COMPLETED (Wrap-up is locked before EVENT_LIVE). Teardown
 * deletes the photo (row and S3 object) and the event.
 */

import { test, expect } from '@playwright/test';
import {
  createRegistrationEvent,
  readOrganizerToken,
  transitionWorkflow,
} from '../helpers/event-fixture';
import { cleanupByCode } from '../helpers/test-fixtures-cleanup';
import { waitForAppShell } from '../helpers/app-shell';
import { stripPresignedAuthHeader } from '../helpers/strip-presigned-auth';
import { uploadPngFile } from '../helpers/test-data-factory';

const LOCAL_DEV = (process.env.TEST_ENV ?? 'development') === 'development';
const API_URL = process.env.E2E_API_URL || 'http://localhost:8000';
const describeLocal = LOCAL_DEV ? test.describe : test.describe.skip;

describeLocal('Organizer · Event photo upload (17.11b)', () => {
  let token: string;
  let eventCode = '';
  const uploadedPhotoIds: string[] = [];

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
    // Photos come after the event: an hour ago, so it is the latest past event for the homepage.
    const hourAgo = new Date(Date.now() - 60 * 60 * 1000).toISOString();
    const patched = await api(`/events/${eventCode}`, {
      method: 'PATCH',
      body: JSON.stringify({ date: hourAgo }),
    });
    expect(patched.status, await patched.clone().text()).toBe(200);
    await transitionWorkflow(token, eventCode, 'EVENT_COMPLETED');
  });

  test.afterAll(async () => {
    for (const id of uploadedPhotoIds) {
      await api(`/events/${eventCode}/photos/${id}`, { method: 'DELETE' }).catch(() => undefined);
    }
    await cleanupByCode(token, eventCode);
  });

  test('should_showPhotoInGalleryAndHomepage_when_uploadedOnTheContract', async ({ page }) => {
    await stripPresignedAuthHeader(page);

    // Every API call that could carry a photo upload, to prove it runs on the contract only.
    const uploadCalls: string[] = [];
    page.on('request', (request) => {
      const { pathname } = new URL(request.url());
      if (/\/api\/v1\/(uploads\/|events\/[^/]+\/photos\/(upload-url|confirm))/.test(pathname)) {
        uploadCalls.push(`${request.method()} ${pathname}`);
      }
    });

    await page.goto(`/organizer/events/${eventCode}?tab=wrapup`);
    await waitForAppShell(page);
    await expect(page.getByTestId('wrapup-panel')).toBeVisible({ timeout: 15_000 });
    await expect(page.getByTestId('event-photo-card')).toHaveCount(0);

    await page.getByTestId('event-photos-input').setInputFiles(uploadPngFile());

    const card = page.getByTestId('event-photo-card');
    await expect(card).toHaveCount(1, { timeout: 30_000 });
    const photoId = (await card.getAttribute('data-photo-id')) ?? '';
    expect(photoId).not.toBe('');
    uploadedPhotoIds.push(photoId);

    expect(uploadCalls).toEqual([
      'POST /api/v1/uploads/event-photo',
      `POST /api/v1/uploads/event-photo/${photoId}/confirm`,
    ]);

    // Stored, not only in local state: still there after a reload.
    await page.reload();
    await waitForAppShell(page);
    await expect(page.getByTestId('event-photo-card')).toHaveCount(1, { timeout: 15_000 });
    await expect(page.getByTestId('event-photo-card')).toHaveAttribute('data-photo-id', photoId);

    // The homepage sample holds it. lastNEvents=1 (the fixture is the latest past event, an hour
    // ago) keeps it deterministic: with 5 events and more than 100 photos the sample is random.
    const recent = await fetch(`${API_URL}/api/v1/public/event-photos?limit=100&lastNEvents=1`, {
      headers: { Accept: 'application/json' },
    });
    expect(recent.status).toBe(200);
    const photos = (await recent.json()) as Array<{ id: string; eventCode: string }>;
    expect(photos).toContainEqual(expect.objectContaining({ id: photoId, eventCode }));
  });
});
