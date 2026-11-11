/**
 * Organizer · Event participants (Story 4.10c): add a participant, enrol the organizers and
 * partners, and add one with the confirmation mail. All three go to the one participants create,
 * `POST /events/{eventCode}/participants`, with its single or stakeholder mode.
 *
 * Guarded to LOCAL DEV: it writes registrations and users through the API, and the notify case
 * sends a real mail (on local dev it lands in the EMS dev mail store, `GET /dev/emails`). The
 * notify case is tagged @sends-real-email so run-playwright-tests.sh excludes it everywhere
 * else. Never tag this file @gate.
 *
 * Fixture: a throwaway event (reserved number range) and two users made by the import mode with
 * status CANCELLED, so the UI add replaces a cancelled registration with a confirmed one. Users
 * use the synthetic test domains and are swept in afterAll.
 */

import { test, expect, type Page } from '@playwright/test';
import {
  readOrganizerToken,
  createRegistrationEvent,
  type RegistrationEvent,
} from '../helpers/event-fixture';
import { cleanupByCode } from '../helpers/test-fixtures-cleanup';
import { waitForAppShell } from '../helpers/app-shell';
import { forceUserProfileLanguage } from '../helpers/mock-user-profile';

const LOCAL_DEV = (process.env.TEST_ENV ?? 'development') === 'development';
const API_URL = process.env.E2E_API_URL || 'http://localhost:8000';
const EMS_URL = 'http://localhost:8002';
const describeLocal = LOCAL_DEV ? test.describe : test.describe.skip;

/** Letters only: CUMS usernames are lower-case first.last. */
const letters = (n: number) =>
  Array.from({ length: n }, () => String.fromCharCode(97 + Math.floor(Math.random() * 26))).join(
    ''
  );

interface Person {
  firstName: string;
  lastName: string;
  email: string;
  username?: string;
}

async function api(token: string, method: string, path: string, body?: unknown) {
  return fetch(`${API_URL}/api/v1${path}`, {
    method,
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
      Accept: 'application/json',
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}

/** Creates the people as users (no login) with a cancelled registration on the event. */
async function importCancelled(token: string, eventCode: string, people: Person[]) {
  const res = await api(token, 'POST', `/events/${eventCode}/participants`, {
    people: people.map((p) => ({ ...p, status: 'CANCELLED' })),
  });
  expect(res.status, 'import fixture people').toBe(200);
  const body = (await res.json()) as { results: Array<{ username?: string; outcome: string }> };
  body.results.forEach((r, i) => {
    expect(r.outcome).toBe('REGISTERED');
    people[i].username = r.username;
  });
}

/** The status of `username`'s registration; `search` (name or e-mail words) narrows the list. */
async function registrationStatus(
  token: string,
  eventCode: string,
  username: string,
  search: string
) {
  const res = await api(
    token,
    'GET',
    `/events/${eventCode}/registrations?limit=100&search=${encodeURIComponent(search)}`
  );
  expect(res.status).toBe(200);
  const body = (await res.json()) as {
    data: Array<{ attendeeUsername: string; status: string }>;
  };
  return body.data.find((r) => r.attendeeUsername === username)?.status;
}

async function sweepUsers(token: string) {
  for (const prefix of ['@e2e.batbern.invalid', '@batbern-test.ch']) {
    await api(token, 'POST', '/admin/test-fixtures/cums/cleanup', {
      entityType: 'users_by_email',
      prefix,
    }).catch(() => undefined);
  }
}

async function openParticipants(page: Page, eventCode: string) {
  await forceUserProfileLanguage(page, 'en');
  await page.goto(`/organizer/events/${eventCode}`);
  await waitForAppShell(page);
  await page.getByTestId('event-tab-registrations').click();
  await expect(page.getByTestId('add-participant-button')).toBeVisible({ timeout: 15_000 });
}

async function addViaDialog(page: Page, person: Person, notify: boolean) {
  await page.getByTestId('add-participant-button').click();
  await page.getByTestId('add-participant-user').fill(`${person.firstName} ${person.lastName}`);
  await page.getByTestId(`user-option-${person.username}`).click();
  const notifyBox = page.getByTestId('add-participant-notify').locator('input');
  if ((await notifyBox.isChecked()) !== notify) {
    await notifyBox.click();
  }
  const created = page.waitForResponse(
    (r) => r.request().method() === 'POST' && r.url().endsWith('/participants')
  );
  await page.getByTestId('add-participant-confirm').click();
  expect((await created).status()).toBe(201);
}

describeLocal('Organizer · Event participants (4.10c)', () => {
  test.describe.configure({ mode: 'serial' });

  let token: string;
  let event: RegistrationEvent;
  const silent: Person = {
    firstName: 'Pwsilent',
    lastName: `Probe${letters(6)}`,
    email: `pw-silent-${Date.now()}@e2e.batbern.invalid`,
  };
  const notified: Person = {
    firstName: 'Pwnotify',
    lastName: `Probe${letters(6)}`,
    email: `pw-notify-${Date.now()}@batbern-test.ch`,
  };

  test.beforeAll(async () => {
    token = readOrganizerToken();
    event = await createRegistrationEvent(token);
    await importCancelled(token, event.eventCode, [silent, notified]);
  });

  test.afterAll(async () => {
    if (event?.eventCode) await cleanupByCode(token, event.eventCode);
    await sweepUsers(token);
  });

  test('adds a participant without mail, and it persists after reload', async ({ page }) => {
    await openParticipants(page, event.eventCode);

    await addViaDialog(page, silent, false);

    await page.reload();
    await waitForAppShell(page);
    await page.getByTestId('event-tab-registrations').click();
    await expect(page.getByText(silent.lastName).first()).toBeVisible({
      timeout: 15_000,
    });
    expect(
      await registrationStatus(token, event.eventCode, silent.username!, silent.lastName)
    ).toBe('CONFIRMED');
  });

  test('enrols the organizers and partners, and they persist after reload', async ({ page }) => {
    await openParticipants(page, event.eventCode);

    const enrolled = page.waitForResponse(
      (r) => r.request().method() === 'POST' && r.url().endsWith('/participants')
    );
    await page.getByTestId('enroll-stakeholders-button').click();
    const response = await enrolled;
    expect(response.status()).toBe(200);
    expect(JSON.parse(response.request().postData() ?? '{}')).toEqual({ stakeholders: true });

    await page.reload();
    await waitForAppShell(page);
    const me = (await (await api(token, 'GET', '/users/me')).json()) as {
      id: string;
      lastName: string;
    };
    expect(await registrationStatus(token, event.eventCode, me.id, me.lastName)).toBe('CONFIRMED');
  });

  test.describe('with the confirmation mail', { tag: '@sends-real-email' }, () => {
    test('adds a participant with notify and exactly one mail goes to them', async ({
      page,
      request,
    }) => {
      await openParticipants(page, event.eventCode);

      await addViaDialog(page, notified, true);

      expect(
        await registrationStatus(token, event.eventCode, notified.username!, notified.lastName)
      ).toBe('CONFIRMED');
      await expect
        .poll(
          async () => {
            const mails = (await (await request.get(`${EMS_URL}/dev/emails`)).json()) as Array<{
              to: string;
            }>;
            return mails.filter((m) => m.to === notified.email).length;
          },
          { timeout: 15_000 }
        )
        .toBe(1);
      // The silent add of the first test sent nothing.
      const mails = (await (await request.get(`${EMS_URL}/dev/emails`)).json()) as Array<{
        to: string;
      }>;
      expect(mails.filter((m) => m.to === silent.email)).toHaveLength(0);
    });
  });
});
