/**
 * Organizer · Session batch import (Story 4.10b): the admin "Import Data" sessions import sends
 * the legacy sessions.json items of an event as `POST /events/{eventCode}/sessions` with
 * `{ imports }` (was `POST …/sessions/batch-import`); the sessions persist, and a second import
 * of the same file skips the duplicates.
 *
 * Guarded to LOCAL DEV (`TEST_ENV=development`, never `@gate`). The JSON is synthetic, built in
 * the test for a fixture event (event number >= 10000, the sweepable test marker): two sessions
 * without speakers and without materials (no CDN fetch, no mail). Teardown deletes the event; its
 * sessions cascade (V2).
 */

import { test, expect, type Page } from '@playwright/test';
import { createRegistrationEvent, readOrganizerToken } from '../helpers/event-fixture';
import { cleanupByCode } from '../helpers/test-fixtures-cleanup';
import { waitForAppShell } from '../helpers/app-shell';

const LOCAL_DEV = (process.env.TEST_ENV ?? 'development') === 'development';
const API_URL = process.env.E2E_API_URL || 'http://localhost:8000';
const describeLocal = LOCAL_DEV ? test.describe : test.describe.skip;

const TITLES = ['E2E Import: Brünnimann über Resilienz', 'E2E Import: O’Neill zu Plattformen'];

describeLocal('Organizer · Session batch import (4.10b)', () => {
  let token: string;
  let eventCode = '';
  let eventNumber = 0;

  test.beforeAll(async () => {
    token = readOrganizerToken();
    ({ eventCode, eventNumber } = await createRegistrationEvent(token));
  });

  test.afterAll(async () => {
    await cleanupByCode(token, eventCode);
  });

  const sessionTitles = async (): Promise<string[]> => {
    const res = await fetch(`${API_URL}/api/v1/events/${eventCode}/sessions?limit=100`, {
      headers: { Authorization: `Bearer ${token}`, Accept: 'application/json' },
    });
    expect(res.status).toBe(200);
    return ((await res.json()) as { data: Array<{ title: string }> }).data.map((s) => s.title);
  };

  const importFile = async (page: Page) => {
    const sessionsJson = JSON.stringify(
      TITLES.map((title) => ({
        bat: eventNumber,
        title,
        abstract: 'Synthetischer Abstract',
        referenten: [],
      }))
    );
    await page.goto('/organizer/admin?tab=1');
    await waitForAppShell(page);
    await page.getByTestId('import-sessions-btn').click();
    await page
      .getByTestId('session-json-dropzone')
      .locator('input[type=file]')
      .setInputFiles({
        name: 'sessions.json',
        mimeType: 'application/json',
        buffer: Buffer.from(sessionsJson, 'utf-8'),
      });
    const request = page.waitForResponse(
      (r) =>
        r.request().method() === 'POST' && r.url().endsWith(`/api/v1/events/${eventCode}/sessions`)
    );
    await page.getByTestId('session-import-submit').click();
    const response = await request;
    expect(response.status()).toBe(200);
    expect(JSON.parse(response.request().postData() ?? '{}')).toHaveProperty('imports');
    return (await response.json()) as { successfullyCreated: number; skipped: number };
  };

  test('should_createTheSessionsOnce_when_organizerImportsTheSameFileTwice', async ({ page }) => {
    const first = await importFile(page);
    expect(first.successfullyCreated).toBe(2);
    await page.reload();
    expect((await sessionTitles()).sort()).toEqual([...TITLES].sort());

    const second = await importFile(page);
    expect(second.successfullyCreated).toBe(0);
    expect(second.skipped).toBe(2);
    expect((await sessionTitles()).sort()).toEqual([...TITLES].sort());
  });
});
