/**
 * Organizer · Participant CSV import (Story 4.10c): the import sends the people of each event to
 * `POST /events/{eventCode}/participants` (mode `people`), so one CSV person in two events means
 * two requests that both carry them. Fixture `e2e/fixtures/participant-import-two-events.csv`:
 * two invented people under the synthetic `@e2e.batbern.invalid` domain, Pia in BATbern1 and
 * BATbern2, Otto in BATbern2.
 *
 * Guarded to LOCAL DEV: the CSV columns are the historical events BATbern1..57, so the import
 * writes registrations into those (local, anonymized) events and creates users. It sends no
 * mail (proven with a positive control in ParticipantsControllerIntegrationTest).
 * Before and after, the spec deletes the fixture people's registrations there and sweeps their
 * users. Never tag this file @gate.
 */

import path from 'path';
import { fileURLToPath } from 'url';
import { test, expect } from '@playwright/test';
import { readOrganizerToken } from '../helpers/event-fixture';
import { waitForAppShell } from '../helpers/app-shell';
import { forceUserProfileLanguage } from '../helpers/mock-user-profile';

const LOCAL_DEV = (process.env.TEST_ENV ?? 'development') === 'development';
const API_URL = process.env.E2E_API_URL || 'http://localhost:8000';
const describeLocal = LOCAL_DEV ? test.describe : test.describe.skip;

const CSV = path.join(
  path.dirname(fileURLToPath(import.meta.url)),
  '..',
  'fixtures',
  'participant-import-two-events.csv'
);
const EXPECTED: Array<{ eventCode: string; usernames: string[] }> = [
  { eventCode: 'BATbern1', usernames: ['pia.importprobe'] },
  { eventCode: 'BATbern2', usernames: ['pia.importprobe', 'otto.importprobe'] },
];

async function api(token: string, method: string, apiPath: string, body?: unknown) {
  return fetch(`${API_URL}/api/v1${apiPath}`, {
    method,
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
      Accept: 'application/json',
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}

/** The fixture people's registrations in an event (searched by their shared last name). */
async function fixtureRegistrations(token: string, eventCode: string) {
  const res = await api(
    token,
    'GET',
    `/events/${eventCode}/registrations?limit=100&search=Importprobe`
  );
  expect(res.status).toBe(200);
  const body = (await res.json()) as {
    data: Array<{ attendeeUsername: string; registrationCode: string; status: string }>;
  };
  return body.data.filter((r) => /^(pia|otto)\.importprobe/.test(r.attendeeUsername));
}

/** Leaves BATbern1/2 and CUMS without the fixture people (before and after the test). */
async function removeFixturePeople(token: string) {
  for (const { eventCode } of EXPECTED) {
    for (const reg of await fixtureRegistrations(token, eventCode)) {
      await api(token, 'DELETE', `/events/${eventCode}/registrations/${reg.registrationCode}`);
    }
  }
  await api(token, 'POST', '/admin/test-fixtures/cums/cleanup', {
    entityType: 'users_by_email',
    prefix: '@e2e.batbern.invalid',
  });
}

describeLocal('Organizer · Participant CSV import (4.10c)', () => {
  let token: string;

  test.beforeAll(async () => {
    token = readOrganizerToken();
    await removeFixturePeople(token);
  });

  test.afterAll(async () => {
    if (token) await removeFixturePeople(token);
  });

  test('imports per event, one person in two events, and it persists after reload', async ({
    page,
  }) => {
    await forceUserProfileLanguage(page, 'en');
    await page.goto('/organizer/admin?tab=1');
    await waitForAppShell(page);
    await page.getByTestId('import-participants-btn').click();
    await page.getByTestId('csv-file-input').setInputFiles(CSV);

    const start = page.getByTestId('participant-import-start-button');
    await expect(start).toBeVisible({ timeout: 15_000 });
    const posts: Array<{ eventCode: string; emails: string[]; status: number }> = [];
    page.on('response', (r) => {
      const m = /\/events\/([^/]+)\/participants$/.exec(new URL(r.url()).pathname);
      if (m && r.request().method() === 'POST') {
        const body = JSON.parse(r.request().postData() ?? '{}') as {
          people?: Array<{ email: string }>;
        };
        posts.push({
          eventCode: m[1],
          emails: (body.people ?? []).map((p) => p.email),
          status: r.status(),
        });
      }
    });
    await start.click();

    const modal = page.getByTestId('participant-import-modal');
    await expect(modal.getByText('Success', { exact: true })).toHaveCount(2, { timeout: 30_000 });
    expect(posts).toEqual([
      { eventCode: 'BATbern1', emails: ['pia.importprobe@e2e.batbern.invalid'], status: 200 },
      {
        eventCode: 'BATbern2',
        emails: ['pia.importprobe@e2e.batbern.invalid', 'otto.importprobe@e2e.batbern.invalid'],
        status: 200,
      },
    ]);

    await page.reload();
    await waitForAppShell(page);
    for (const { eventCode, usernames } of EXPECTED) {
      const regs = await fixtureRegistrations(token, eventCode);
      expect(regs.map((r) => r.attendeeUsername).sort()).toEqual([...usernames].sort());
      regs.forEach((r) => expect(r.status).toBe('ATTENDED'));
    }
  });
});
