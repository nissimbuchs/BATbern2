/**
 * Event Detail → Communications → "Needs an answer": an organizer answers a speaker, and the
 * speaker's reply comes back into the same conversation (Story 16.6 AC1–AC4).
 *
 * ⚠️ EMAIL-SAFETY — DEV-ONLY, NEVER @gate. Clicking "Send answer" drives the real send path. On LOCAL
 * DEV (TEST_ENV=development) the Spring `local` profile has no SesClient, so the answer is captured
 * in /dev/emails and nothing leaves the box. On staging (= production) SES is live. Hard-guarded to
 * `development` and tagged @sends-real-email so run-playwright-tests.sh excludes it everywhere else.
 * The speaker's mail and reply are simulated with `POST /dev/emails/inbound` (EMS profile `local`),
 * which runs the production processing. Synthetic SES simulator address only.
 */

import { test, expect } from '@playwright/test';
import {
  readOrganizerToken,
  createRegistrationEvent,
  type RegistrationEvent,
} from '../helpers/event-fixture';
import { cleanupByCode } from '../helpers/test-fixtures-cleanup';
import { waitForAppShell } from '../helpers/app-shell';
import { forceUserProfileLanguage } from '../helpers/mock-user-profile';

const EMAIL_SAFE_ENV = (process.env.TEST_ENV ?? 'development') === 'development';
const EMS_URL = 'http://localhost:8002';
const SPEAKER = 'success+speaker@simulator.amazonses.com';
const describeReply = EMAIL_SAFE_ENV ? test.describe : test.describe.skip;

describeReply('Organizer · Answer a conversation', { tag: '@sends-real-email' }, () => {
  test.describe.configure({ mode: 'serial' });

  let token: string;
  let event: RegistrationEvent;

  test.beforeAll(async () => {
    token = readOrganizerToken();
    event = await createRegistrationEvent(token);
  });

  test.afterAll(async () => {
    if (event?.eventCode) await cleanupByCode(token, event.eventCode);
  });

  test('answer goes out as "via BATbern", status Waiting; the reply lands in the same conversation', async ({
    page,
    request,
  }) => {
    const subject = `Slides question ${Date.now()}`;
    expect(
      (
        await request.post(`${EMS_URL}/dev/emails/inbound`, {
          data: {
            from: SPEAKER,
            fromName: 'A Speaker',
            to: `${event.eventCode.toLowerCase()}-moderator@batbern.ch`,
            subject,
            body: 'When do you need my slides?',
          },
        })
      ).ok()
    ).toBeTruthy();
    await request.delete(`${EMS_URL}/dev/emails`);

    await forceUserProfileLanguage(page, 'en');
    await page.goto(`/organizer/events/${event.eventCode}`);
    await waitForAppShell(page);
    await page.getByTestId('event-tab-communications').click();
    const row = page.locator('[data-testid^="conversation-row-"]').filter({ hasText: subject });
    await expect(row).toBeVisible({ timeout: 15_000 });
    const key = ((await row.getAttribute('data-testid')) ?? '').replace('conversation-row-', '');
    await row.getByTestId(`conversation-open-${key}`).click();
    const inline = page.getByTestId(`conversation-inline-${key}`);

    const send = inline.getByTestId('conversation-reply-send');
    await expect(send).toBeDisabled();
    await inline
      .getByTestId('conversation-reply-text')
      .locator('textarea')
      .first()
      .fill('By Friday, thanks.');
    await send.click();
    await inline.getByTestId('conversation-reply-confirm-yes').click();
    await expect(page.getByTestId('conversation-reply-sent')).toBeVisible({ timeout: 15_000 });
    await expect(row).toHaveAttribute('data-status', 'WAITING', { timeout: 15_000 });

    // The answer was captured locally: one mail to the speaker, "Re:" subject, "via BATbern".
    const captured = (await (await request.get(`${EMS_URL}/dev/emails`)).json()) as Array<{
      id: string;
      to: string;
      subject: string;
      fromName: string;
    }>;
    const answer = captured.find((m) => m.subject === `Re: ${subject}`);
    expect(answer?.to).toBe(SPEAKER);
    expect(answer?.fromName).toMatch(/ via BATbern$/);

    // The speaker replies to the answer: same conversation, back to Open. The token is in the
    // conversation's Reply-To; the reply names our Message-ID too, so header threading also works.
    expect(
      (
        await request.post(`${EMS_URL}/dev/emails/inbound`, {
          data: {
            from: SPEAKER,
            fromName: 'A Speaker',
            to: 'replies@batbern.ch',
            subject: `Re: Re: ${subject}`,
            body: 'Accept',
            inReplyToCaptureId: answer?.id,
          },
        })
      ).ok()
    ).toBeTruthy();
    await page.reload();
    await waitForAppShell(page);
    await page.getByTestId('event-tab-communications').click();
    await expect(row).toHaveAttribute('data-status', 'OPEN', { timeout: 15_000 });
    await row.getByTestId(`conversation-open-${key}`).click();
    await expect(inline.locator('[data-testid^="conversation-message-"]')).toHaveCount(3);
  });
});
