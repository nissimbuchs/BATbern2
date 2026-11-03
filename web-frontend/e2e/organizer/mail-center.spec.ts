/**
 * The global Mail page (Epic 16, Story 16.7). READ-ONLY apart from the simulated inbound mail.
 *
 * A mail to info@ without an event and a spam-FAIL mail are simulated with the local dev inbound
 * endpoint (`POST /dev/emails/inbound`, EMS profile `local`), which runs the production processing;
 * the clean mail's forwarded copy lands in the local capture, the spam is not forwarded. Then the page
 * must list the clean mail under "No event", the spam only in the collapsed Spam list, and fit a
 * 375 px phone without horizontal scrolling. Nothing is assigned or released here (covered by
 * GlobalMailCenterIntegrationTest and MailCenterPage.test.tsx). The simulated rows stay in the local
 * DB like the event mail spec's (spam goes after 30 days).
 *
 * Guarded to LOCAL DEV: the simulator exists only there. Never tag it @gate.
 */

import { test, expect } from '@playwright/test';
import { waitForAppShell } from '../helpers/app-shell';
import { forceUserProfileLanguage } from '../helpers/mock-user-profile';

const LOCAL_DEV = (process.env.TEST_ENV ?? 'development') === 'development';
const EMS_URL = 'http://localhost:8002';
const describeMailCenter = LOCAL_DEV ? test.describe : test.describe.skip;

describeMailCenter('Organizer · Mail page (read-only)', () => {
  test.describe.configure({ mode: 'serial' });

  const run = Date.now();
  const cleanSubject = `E2E 16.7 question ${run}`;
  const spamSubject = `E2E 16.7 spam ${run}`;

  test.beforeAll(async ({ request }) => {
    const send = (data: object) =>
      request
        .post(`${EMS_URL}/dev/emails/inbound`, { data })
        .then((r) => expect(r.ok()).toBe(true));
    await send({
      from: `e2e.curious.${run}@example.com`,
      fromName: 'E2E Curious',
      to: 'info@batbern.ch',
      subject: cleanSubject,
      body: 'When is the next event?',
    });
    await send({
      from: `e2e.spammer.${run}@example.com`,
      to: 'info@batbern.ch',
      subject: spamSubject,
      body: 'You won',
      headers: { 'X-SES-Spam-Verdict': 'FAIL' },
    });
  });

  test('lists no-event mail and spam in their sections', async ({ page }) => {
    await forceUserProfileLanguage(page, 'en');
    await page.goto('/organizer/events');
    await waitForAppShell(page);

    // AC2/AC8: the navigation entry, after Events
    await page.getByTestId('nav-organizer-mail').click();
    await expect(page).toHaveURL(/\/organizer\/mail/);

    const noEvent = page.getByTestId('mail-center-no-event-list');
    await expect(noEvent.getByText(cleanSubject)).toBeVisible({ timeout: 15_000 });
    // AC3: spam never shows outside the Spam list
    await expect(page.getByText(spamSubject)).toBeHidden();

    await page.getByTestId('mail-center-spam').getByRole('button').first().click();
    await expect(page.getByTestId('mail-center-spam-list').getByText(spamSubject)).toBeVisible();
  });

  test('fits a phone without horizontal scrolling', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 800 });
    await forceUserProfileLanguage(page, 'en');
    await page.goto('/organizer/mail');
    await waitForAppShell(page);
    await expect(page.getByTestId('mail-center')).toBeVisible({ timeout: 15_000 });

    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth - document.documentElement.clientWidth
    );
    expect(overflow).toBeLessThanOrEqual(0);
  });
});
