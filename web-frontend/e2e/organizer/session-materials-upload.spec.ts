/**
 * Organizer · Session materials upload (Story 17.11d): the session editor (Speakers tab, agenda
 * view, Materials tab) uploads a PDF through the upload contract, purpose `session-material`,
 * confirmed without a target — `POST /uploads/session-material`, the presigned PUT,
 * `POST /uploads/session-material/{uploadId}/confirm` — and the save attaches it with
 * `POST /events/{eventCode}/sessions/{sessionSlug}/materials` `{ materials: [{ uploadId }] }`. After
 * a reopen the material is listed under its original file name and downloads under it. The
 * removed `/materials/presigned-url` and `/materials/{uploadId}/confirm` are never called.
 *
 * Guarded to LOCAL DEV (`TEST_ENV=development`, never `@gate`): it creates a fixture event (event
 * number >= 10000, the sweepable test marker) with one session. Teardown deletes the material (row
 * and S3 object) and the event.
 */

import { test, expect, type Download, type Page } from '@playwright/test';
import { createRegistrationEvent, readOrganizerToken } from '../helpers/event-fixture';
import { cleanupByCode } from '../helpers/test-fixtures-cleanup';
import { waitForAppShell } from '../helpers/app-shell';
import { stripPresignedAuthHeader } from '../helpers/strip-presigned-auth';

const LOCAL_DEV = (process.env.TEST_ENV ?? 'development') === 'development';
const API_URL = process.env.E2E_API_URL || 'http://localhost:8000';
const describeLocal = LOCAL_DEV ? test.describe : test.describe.skip;

/** A minimal PDF, enough for a typed upload. */
const PDF = Buffer.from('%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF\n');

describeLocal('Organizer · Session materials upload (17.11d)', () => {
  let token: string;
  let eventCode = '';
  let sessionSlug = '';
  const fileName = `Vortrag Brünnimann ${Date.now()}.pdf`;

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
    // A fixed evening slot: a start near midnight would end on the next day, which the editor's
    // time validation refuses on save.
    const start = new Date(Date.now() + 60 * 24 * 60 * 60 * 1000);
    start.setUTCHours(17, 0, 0, 0);
    const created = await api(`/events/${eventCode}/sessions`, {
      method: 'POST',
      body: JSON.stringify({
        title: 'BATPW Materials Session',
        description: 'Session for the session-material upload (Story 17.11d)',
        sessionType: 'presentation',
        startTime: start.toISOString(),
        endTime: new Date(start.getTime() + 45 * 60 * 1000).toISOString(),
        language: 'de',
      }),
    });
    expect(created.status).toBe(201);
    sessionSlug = ((await created.json()) as { session: { sessionSlug: string } }).session
      .sessionSlug;
  });

  test.afterAll(async () => {
    if (sessionSlug) {
      const listed = await api(`/events/${eventCode}/sessions/${sessionSlug}/materials`).catch(
        () => undefined
      );
      const materials = listed?.ok
        ? ((await listed.json()) as { materials: Array<{ id: string }> }).materials
        : [];
      for (const material of materials) {
        await api(`/events/${eventCode}/sessions/${sessionSlug}/materials/${material.id}`, {
          method: 'DELETE',
        }).catch(() => undefined);
      }
    }
    await cleanupByCode(token, eventCode);
  });

  async function openMaterialsTab(page: Page) {
    await page.goto(`/organizer/events/${eventCode}?tab=speakers&view=agenda`);
    await waitForAppShell(page);
    const row = page.getByTestId(`session-row-${sessionSlug}`);
    await expect(row).toBeVisible({ timeout: 15_000 });
    await row.click();
    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();
    await dialog.getByRole('tab', { name: /materials|materialien/i }).click();
    return dialog;
  }

  test('should_listAndDownloadMaterial_when_uploadedInTheSessionEditor', async ({ page }) => {
    test.setTimeout(90_000);
    // The context, not the page: the download opens the presigned URL in a popup.
    await stripPresignedAuthHeader(page.context());

    // Every API call that could carry a material upload, to prove it runs on the contract only.
    const uploadCalls: string[] = [];
    page.on('request', (request) => {
      const { pathname } = new URL(request.url());
      if (
        request.method() !== 'GET' &&
        /\/api\/v1\/(uploads\/|materials\/|events\/[^/]+\/sessions\/[^/]+\/materials$)/.test(
          pathname
        )
      ) {
        uploadCalls.push(`${request.method()} ${pathname}`);
      }
    });

    let dialog = await openMaterialsTab(page);
    const confirmed = page.waitForResponse(
      (r) => /\/api\/v1\/uploads\/session-material\/[^/]+\/confirm$/.test(r.url()) && r.ok()
    );
    await dialog
      .getByTestId('file-dropzone')
      .locator('input[type="file"]')
      .setInputFiles({ name: fileName, mimeType: 'application/pdf', buffer: PDF });
    await confirmed;

    const attached = page.waitForResponse(
      (r) =>
        r.request().method() === 'POST' &&
        new URL(r.url()).pathname.endsWith(`/sessions/${sessionSlug}/materials`)
    );
    await dialog.getByRole('button', { name: /^(save|speichern)$/i }).click();
    expect((await attached).status()).toBe(201);
    await expect(dialog).toBeHidden({ timeout: 15_000 });

    expect(uploadCalls).toHaveLength(3);
    expect(uploadCalls[0]).toBe('POST /api/v1/uploads/session-material');
    expect(uploadCalls[1]).toMatch(
      /^POST \/api\/v1\/uploads\/session-material\/[0-9a-f-]{36}\/confirm$/
    );
    expect(uploadCalls[2]).toBe(
      `POST /api/v1/events/${eventCode}/sessions/${sessionSlug}/materials`
    );
    const uploadId = uploadCalls[1].split('/')[5];

    // Stored, not only in local state: listed after a reopen, under its original name.
    dialog = await openMaterialsTab(page);
    const link = dialog.getByText(fileName);
    await expect(link).toBeVisible({ timeout: 15_000 });

    const download = page.waitForResponse((r) =>
      /\/sessions\/[^/]+\/materials\/[0-9a-f-]{36}\/download$/.test(new URL(r.url()).pathname)
    );
    // The download may be reported on the popup or on the opener: listen on every page, from the
    // moment it exists (a listener added after the popup's navigation would miss the event).
    const saved = new Promise<Download>((resolve) => {
      page.on('download', resolve);
      page.context().on('page', (popup) => popup.on('download', resolve));
    });
    await link.click();
    const downloadResponse = await download;
    expect(downloadResponse.status()).toBe(200);
    const { downloadUrl } = (await downloadResponse.json()) as { downloadUrl: string };
    expect(downloadUrl).toContain(`file-${uploadId}.pdf`);
    // The presigned URL answers with Content-Disposition: the browser saves the original name.
    const file = await saved;
    expect(file.suggestedFilename()).toBe(fileName);

    const listed = await api(`/events/${eventCode}/sessions/${sessionSlug}/materials`);
    const { materials } = (await listed.json()) as {
      materials: Array<{ uploadId: string; fileName: string; materialType: string; s3Key: string }>;
    };
    expect(materials).toEqual([
      expect.objectContaining({
        uploadId,
        fileName,
        materialType: 'DOCUMENT',
        s3Key: `materials/events/${eventCode}/sessions/${sessionSlug}/file-${uploadId}.pdf`,
      }),
    ]);
  });
});
