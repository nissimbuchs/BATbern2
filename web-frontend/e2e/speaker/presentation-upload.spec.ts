/**
 * Speaker portal · Presentation upload (Story 17.11d): the content page uploads the speaker's slides
 * through the upload contract, purpose `session-material` with the target
 * `{eventCode, sessionSlug}` of the speaker's own session — `POST /uploads/session-material`, the
 * presigned PUT, `POST /uploads/session-material/{uploadId}/confirm` — and the confirm creates the
 * session material (PRESENTATION). The removed speaker-portal `…/materials/presigned-url` and
 * `…/materials/confirm` are never called.
 *
 * Setup over the API with the organizer token: a fixture event (event number >= 10000, the
 * sweepable test marker), the SPEAKER test user (batbern.speaker) put in its pool, promoted with
 * their existing account and its names (READY provisions the session and the session_users row) and
 * accepted on their behalf. Teardown deletes the material (row and S3 object) and the event.
 *
 * Guarded to LOCAL DEV (`TEST_ENV=development`, never `@gate`); runs in the `speaker` project, which
 * needs SPEAKER_AUTH_TOKEN.
 */

import { test, expect } from '@playwright/test';
import { createRegistrationEvent, readOrganizerToken } from '../helpers/event-fixture';
import { cleanupByCode } from '../helpers/test-fixtures-cleanup';
import { stripPresignedAuthHeader } from '../helpers/strip-presigned-auth';
import {
  promoteSpeaker,
  readSpeakerIdentity,
  seedSpeaker,
  setSpeakerStatus,
} from '../helpers/speaker-pool-fixture';

const LOCAL_DEV = (process.env.TEST_ENV ?? 'development') === 'development';
const API_URL = process.env.E2E_API_URL || 'http://localhost:8000';
const describeLocal = LOCAL_DEV ? test.describe : test.describe.skip;

/** A minimal PDF, enough for a typed upload. */
const PDF = Buffer.from('%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF\n');

describeLocal('Speaker portal · Presentation upload (17.11d)', () => {
  test.skip(!process.env.SPEAKER_AUTH_TOKEN, 'SPEAKER_AUTH_TOKEN not set — run setup-test-users');

  let organizerToken = '';
  let eventCode = '';
  let sessionSlug = '';
  const fileName = `Folien Müller ${Date.now()}.pdf`;

  const api = (token: string, path: string, init: RequestInit = {}) =>
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
    const speaker = readSpeakerIdentity();
    expect(speaker, 'speaker token file').toBeDefined();
    organizerToken = readOrganizerToken();
    ({ eventCode } = await createRegistrationEvent(organizerToken));

    // Promote with the account's own names, so the existing user is not renamed.
    const me = (await (await api(speaker!.token, '/users/me')).json()) as {
      firstName: string;
      lastName: string;
    };
    const speakerId = await seedSpeaker(organizerToken, eventCode, 'BATPW Slides Speaker');
    await setSpeakerStatus(organizerToken, eventCode, speakerId, 'CONTACTED');
    await promoteSpeaker(organizerToken, eventCode, speakerId, {
      email: speaker!.email,
      firstName: me.firstName,
      lastName: me.lastName,
    });
    await setSpeakerStatus(organizerToken, eventCode, speakerId, 'ACCEPTED');

    const info = (await (
      await api(speaker!.token, `/speaker-portal/events/${eventCode}/content`)
    ).json()) as { sessionSlug?: string | null };
    expect(info.sessionSlug).toBeTruthy();
    sessionSlug = info.sessionSlug!;
  });

  test.afterAll(async () => {
    if (sessionSlug) {
      const listed = await api(
        organizerToken,
        `/events/${eventCode}/sessions/${sessionSlug}/materials`
      ).catch(() => undefined);
      const materials = listed?.ok
        ? ((await listed.json()) as { materials: Array<{ id: string }> }).materials
        : [];
      for (const material of materials) {
        await api(
          organizerToken,
          `/events/${eventCode}/sessions/${sessionSlug}/materials/${material.id}`,
          { method: 'DELETE' }
        ).catch(() => undefined);
      }
    }
    await cleanupByCode(organizerToken, eventCode);
  });

  test('should_attachSlidesToOwnSession_when_uploadedInThePortal', async ({ page }) => {
    test.setTimeout(90_000);
    await stripPresignedAuthHeader(page);

    const uploadCalls: string[] = [];
    page.on('request', (request) => {
      const { pathname } = new URL(request.url());
      if (
        /\/api\/v1\/(uploads\/|materials\/|speaker-portal\/events\/[^/]+\/materials)/.test(pathname)
      ) {
        uploadCalls.push(`${request.method()} ${pathname}`);
      }
    });

    await page.goto(`/speaker-portal/content/${eventCode}`);
    const input = page.getByLabel(
      /upload presentation file|präsentationsdatei (hochladen|uflade)/i
    );
    await expect(input).toBeAttached({ timeout: 20_000 }); // the portal has no app-shell <main>

    const confirmed = page.waitForResponse((r) =>
      /\/api\/v1\/uploads\/session-material\/[^/]+\/confirm$/.test(new URL(r.url()).pathname)
    );
    await input.setInputFiles({ name: fileName, mimeType: 'application/pdf', buffer: PDF });
    expect((await confirmed).status()).toBe(200);

    // The page shows the attached material under its original name.
    await expect(page.getByRole('link', { name: new RegExp(fileName) })).toBeVisible({
      timeout: 15_000,
    });

    expect(uploadCalls).toHaveLength(2);
    expect(uploadCalls[0]).toBe('POST /api/v1/uploads/session-material');
    expect(uploadCalls[1]).toMatch(
      /^POST \/api\/v1\/uploads\/session-material\/[0-9a-f-]{36}\/confirm$/
    );
    const uploadId = uploadCalls[1].split('/')[5];

    const listed = await api(
      organizerToken,
      `/events/${eventCode}/sessions/${sessionSlug}/materials`
    );
    const { materials } = (await listed.json()) as {
      materials: Array<{ id: string; uploadId: string; fileName: string; materialType: string }>;
    };
    expect(materials).toEqual([
      expect.objectContaining({ uploadId, fileName, materialType: 'PRESENTATION' }),
    ]);
    const download = await api(
      organizerToken,
      `/events/${eventCode}/sessions/${sessionSlug}/materials/${materials[0].id}/download`
    );
    expect(download.status).toBe(200);
  });
});
