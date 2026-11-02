/**
 * Event Detail → Communications → "+ New mail" → Newsletter: test send and real send through the
 * shared composer (Story 16.8b). Replaces event-newsletter-send.spec.ts, whose old composer the
 * shared one replaced.
 *
 * ⚠️ EMAIL-SAFETY — DEV-ONLY, NEVER @gate. This spec drives the REAL send pipeline end-to-end. On
 * LOCAL DEV (TEST_ENV=development) the Spring `local` profile has no SesClient → every send is
 * intercepted by LocalEmailCapture (/dev/emails) and nothing leaves the box. On staging/production
 * SES is LIVE, so running this there mails the ENTIRE subscriber list. It is therefore hard-guarded
 * to `development` (EMAIL_SAFE_ENV below) AND tagged @sends-real-email so run-playwright-tests.sh
 * excludes it on every non-dev env.
 *
 * INCIDENT 2026-07-01: the predecessor spec was tagged @gate; nightly-e2e.yml ran it against
 * staging (= production) and ~1,050 community members each received 3 real test newsletters. Do
 * NOT add @gate, and do NOT weaken the EMAIL_SAFE_ENV guard.
 *
 * The fixture event is a throwaway (event_number ≥ 10000, swept in teardown).
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

// Hard email-safety gate: only LOCAL DEV intercepts mail (LocalEmailCapture). On any deployed
// env SES is live, so the whole group, INCLUDING the event-creating beforeAll, is skipped.
// describe.skip (not test.skip) guarantees the beforeAll never runs, so no send is ever issued.
const EMAIL_SAFE_ENV = (process.env.TEST_ENV ?? 'development') === 'development';
// Local dev only (guarded above): the gateway and EMS's capture endpoint.
const API_URL = process.env.E2E_API_URL || 'http://localhost:8000';
const EMS_URL = 'http://localhost:8002';
const describeSend = EMAIL_SAFE_ENV ? test.describe : test.describe.skip;

describeSend('Organizer · Mail composer send', { tag: '@sends-real-email' }, () => {
  test.describe.configure({ mode: 'serial' });
  test.setTimeout(3 * 60 * 1000);

  let token: string;
  let event: RegistrationEvent;

  test.beforeAll(async () => {
    token = readOrganizerToken();
    event = await createRegistrationEvent(token);
  });

  test.afterAll(async () => {
    if (event?.eventCode) await cleanupByCode(token, event.eventCode);
  });

  async function openNewsletter(page: Page): Promise<void> {
    await forceUserProfileLanguage(page, 'en');
    await page.goto(`/organizer/events/${event.eventCode}`);
    await waitForAppShell(page);
    await page.getByTestId('event-tab-communications').click();
    await page.getByTestId('new-mail-button').click();
    await page.getByTestId('new-mail-newsletter').click();
    await expect(
      page.getByTestId('mail-composer-NEWSLETTER').getByTitle('Mail preview')
    ).toBeVisible({ timeout: 15_000 });
  }

  test('test send goes to the organizers and the composer stays open', async ({ page }) => {
    await openNewsletter(page);
    const composer = page.getByTestId('mail-composer-NEWSLETTER');

    await composer.getByTestId('composer-test-button').click();

    await expect(page.getByTestId('composer-test-toast')).toContainText('Test sent to', {
      timeout: 30_000,
    });
    await expect(composer).toBeVisible();

    // AC9: the test send is hidden in "What went out" until "show test sends" is on.
    await expect(page.getByTestId('mail-sent-empty')).toBeVisible();
    await page.getByTestId('mail-show-tests').getByRole('switch').check();
    const testRow = page.locator('[data-testid^="mail-row-"]').first();
    await expect(testRow).toContainText('TEST', { timeout: 15_000 });
  });

  test('real send: confirm, composer closes, new row highlighted on top until it finishes', async ({
    page,
  }) => {
    await openNewsletter(page);
    const composer = page.getByTestId('mail-composer-NEWSLETTER');

    await composer.getByTestId('composer-send-button').click();
    await composer.getByTestId('composer-confirm-yes').click();

    await expect(page.getByTestId('compose-panel-newsletter')).toBeHidden({ timeout: 15_000 });
    await expect(page.getByTestId('mail-sent-toast')).toBeVisible();
    const newest = page.locator('[data-testid^="mail-row-"]').first();
    await expect(newest).toHaveAttribute('data-highlighted', 'true', { timeout: 15_000 });
    await expect(newest).toContainText('Newsletter');
    // ~1000 local subscribers at the configured send delay take over a minute. This checks that
    // the send reaches a terminal state, not that it delivered: on the anonymized local snapshot
    // almost every subscriber is refused (reserved domain), so it ends PARTIAL. Accepting PARTIAL
    // can hide a real delivery failure; the owner accepted that trade-off in #1158.
    await expect(newest).not.toContainText('sending', { timeout: 150_000 });
  });

  test('own text to registrants: each gets their own filled mail, the record matches the capture', async ({
    page,
    request,
  }) => {
    const marker = `OwnText ${Date.now()}`;
    await forceUserProfileLanguage(page, 'en');
    await page.goto(`/organizer/events/${event.eventCode}`);
    await waitForAppShell(page);
    await page.getByTestId('event-tab-communications').click();
    await page.getByTestId('new-mail-button').click();
    await page.getByTestId('new-mail-registrants').click();
    const composer = page.getByTestId('mail-composer-REGISTRANTS');
    await expect(composer.getByTitle('Mail preview')).toBeVisible({ timeout: 15_000 });

    await composer.getByTestId('composer-source-own').click();
    await composer.getByTestId('composer-own-subject').fill(`${marker} {{eventNumber}}`);
    await composer.frameLocator('iframe.tox-edit-area__iframe').locator('body').click();
    await page.keyboard.type('Hello {{recipientName}}, see you soon.');
    await expect(composer.getByTestId('composer-subject')).not.toContainText('{{', {
      timeout: 15_000,
    });

    await composer.getByTestId('composer-send-button').click();
    await composer.getByTestId('composer-confirm-yes').click();
    await expect(page.getByTestId('compose-panel-registrants')).toBeHidden({ timeout: 15_000 });
    const newest = page.locator('[data-testid^="mail-row-"]').first();
    await expect(newest).toContainText(marker, { timeout: 15_000 });
    await expect(newest).not.toContainText('sending', { timeout: 120_000 });

    // The record: one mail with its recipients.
    const headers = { Authorization: `Bearer ${token}` };
    const list = await (
      await request.get(`${API_URL}/api/v1/events/${event.eventCode}/mails`, { headers })
    ).json();
    const rows = list.data as Array<{
      subject: string;
      recipientCount: number;
      deliveredCount: number;
      failedCount: number;
    }>;
    const row = rows.find((r) => r.subject.startsWith(marker));
    expect(row, 'the own-text mail is recorded').toBeTruthy();

    // The capture: one mail per registrant, each with the name filled in and no placeholder left.
    const captured = (await (await request.get(`${EMS_URL}/dev/emails`)).json()) as Array<{
      to: string;
      subject: string;
      htmlBody: string;
    }>;
    const mine = captured.filter((m) => m.subject.startsWith(marker));
    expect(mine.length).toBeGreaterThan(0);
    expect(new Set(mine.map((m) => m.to)).size, 'one mail per recipient').toBe(mine.length);
    for (const m of mine) {
      expect(m.subject).toBe(`${marker} ${event.eventCode.replace('BATbern', '')}`);
      expect(m.htmlBody).not.toContain('{{');
    }
    // The name comes from each registration; the shared test organizer has none, so its line is
    // "Hello , ..." and at least one other auto-enrolled organizer must show a real name.
    const names = mine.map((m) => /Hello ([^,<]*), see you soon\./.exec(m.htmlBody)?.[1]);
    expect(
      names.every((n) => n !== undefined),
      'every mail carries the body'
    ).toBe(true);
    expect(
      names.some((n) => (n ?? '').trim().length > 0),
      'a name is filled in'
    ).toBe(true);
    expect(row?.subject).toBe(mine[0].subject);
    // Delivered = what the capture holds. The anonymized local snapshot gives most auto-enrolled
    // registrants a reserved address (refused, counted as failed) or none (skipped), so the
    // registration count is only an upper bound here.
    expect(row?.deliveredCount).toBe(mine.length);
    expect(row!.deliveredCount + row!.failedCount).toBeLessThanOrEqual(row!.recipientCount);
  });

  test('own text to partners: every partner gets the mail on their own (16.9)', async ({
    page,
    request,
  }) => {
    const marker = `PartnerOwn ${Date.now()}`;
    await forceUserProfileLanguage(page, 'en');
    await page.goto(`/organizer/events/${event.eventCode}`);
    await waitForAppShell(page);
    await page.getByTestId('event-tab-communications').click();
    await page.getByTestId('new-mail-button').click();
    await page.getByTestId('new-mail-partners').click();
    const composer = page.getByTestId('mail-composer-PARTNERS');

    await composer.getByTestId('composer-source-own').click();
    await composer.getByTestId('composer-own-subject').fill(marker);
    await composer.frameLocator('iframe.tox-edit-area__iframe').locator('body').click();
    await page.keyboard.type('Dear partners, a short update.');
    await expect(composer.getByTitle('Mail preview')).toBeVisible({ timeout: 15_000 });

    await composer.getByTestId('composer-send-button').click();
    await composer.getByTestId('composer-confirm-yes').click();
    await expect(page.getByTestId('compose-panel-partners')).toBeHidden({ timeout: 15_000 });
    const newest = page.locator('[data-testid^="mail-row-"]').first();
    await expect(newest).toContainText(marker, { timeout: 15_000 });
    await expect(newest).not.toContainText('sending', { timeout: 120_000 });

    const headers = { Authorization: `Bearer ${token}` };
    const list = await (
      await request.get(`${API_URL}/api/v1/events/${event.eventCode}/mails`, { headers })
    ).json();
    const row = (
      list.data as Array<{
        subject: string;
        recipientCount: number;
        deliveredCount: number;
        failedCount: number;
      }>
    ).find((r) => r.subject === marker);
    expect(row, 'the partner mail is recorded').toBeTruthy();
    // A reserved address (most partners on the anonymized local snapshot) is refused before the
    // send and not recorded at all (#1182), so only the captured ones can be compared here.

    // Each captured mail names one recipient on To:, and nobody gets it twice.
    const captured = (await (await request.get(`${EMS_URL}/dev/emails`)).json()) as Array<{
      to: string;
      cc: string | null;
      subject: string;
    }>;
    const mine = captured.filter((m) => m.subject === marker);
    expect(mine.length).toBe(row!.deliveredCount);
    for (const m of mine) {
      expect(m.to).not.toMatch(/[,;]/);
      expect(m.cc ?? '').toBe('');
    }
    expect(new Set(mine.map((m) => m.to)).size).toBe(mine.length);
  });
});
