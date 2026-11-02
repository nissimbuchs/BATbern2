/**
 * Event Detail → Communications: the mail page (Epic 16, Stories 16.3 + 16.4 + 16.2a). READ-ONLY.
 *
 * Opens "What went out", "Handled automatically" and every "+ New mail" composer (inline on desktop,
 * full-screen dialog on mobile), and closes each again without sending anything. No compose action
 * is confirmed in this spec.
 *
 * Story 16.2a: "Needs an answer". A speaker's mail is simulated with the local dev inbound endpoint
 * (`POST /dev/emails/inbound`, EMS profile `local`), which runs the production processing; the
 * forwarded copy lands in the local capture. Conversations of the deleted fixture event stay in the
 * local DB until retention, like its mail.
 *
 * Guarded to LOCAL DEV like the newsletter spec: the fixture event is created through the API,
 * and on staging (= production) the AWS account is live (ADR-015). Never tag it @gate.
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

const LOCAL_DEV = (process.env.TEST_ENV ?? 'development') === 'development';
const EMS_URL = 'http://localhost:8002';
const describeMailPage = LOCAL_DEV ? test.describe : test.describe.skip;

describeMailPage('Organizer · Event mail page (read-only)', () => {
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

  test('shows what went out and opens every composer without sending', async ({ page }) => {
    await forceUserProfileLanguage(page, 'en');
    await page.goto(`/organizer/events/${event.eventCode}`);
    await waitForAppShell(page);
    await page.getByTestId('event-tab-communications').click();

    // A fresh event: nothing went out yet, one-line empty state.
    await expect(page.getByTestId('mail-sent-empty')).toBeVisible({ timeout: 15_000 });
    await page.getByTestId('mail-filter-SPEAKERS').click();
    await expect(page.getByTestId('mail-sent-empty')).toBeVisible();

    // "Handled automatically" is collapsed and shows counts only.
    await page.getByTestId('mail-automatic').getByRole('button').first().click();
    await expect(page.getByTestId('mail-automatic-failed')).toBeVisible();

    for (const audience of ['newsletter', 'registrants', 'speakers', 'venue', 'partners']) {
      await page.getByTestId('new-mail-button').click();
      await page.getByTestId(`new-mail-${audience}`).click();
      await expect(page.getByTestId(`compose-panel-${audience}`)).toBeVisible();
      // Desktop: inline above the list, not a modal.
      await expect(page.getByRole('dialog')).toHaveCount(0);
      await page.keyboard.press('Escape');
      await expect(page.getByTestId(`compose-panel-${audience}`)).toBeHidden();
      await expect(page.getByTestId('new-mail-button')).toBeFocused();
    }
  });

  test('needs an answer: a speaker mail appears, opens inline, status and deep link work', async ({
    page,
    request,
  }) => {
    await forceUserProfileLanguage(page, 'en');
    await page.goto(`/organizer/events/${event.eventCode}`);
    await waitForAppShell(page);
    await page.getByTestId('event-tab-communications').click();

    // A fresh event: one line, no table; "+ New mail" sits in this section's header.
    await expect(page.getByTestId('needs-answer-empty')).toHaveText('Nothing open.', {
      timeout: 15_000,
    });
    await expect(page.getByTestId('needs-answer').getByTestId('new-mail-button')).toBeVisible();

    const subject = `Beamer question ${Date.now()}`;
    const simulated = await request.post(`${EMS_URL}/dev/emails/inbound`, {
      data: {
        from: 'success+speaker@simulator.amazonses.com',
        fromName: 'A Speaker',
        to: `${event.eventCode.toLowerCase()}-moderator@batbern.ch`,
        subject,
        body: 'Do I need HDMI or USB-C?',
      },
    });
    expect(simulated.ok()).toBeTruthy();

    await page.reload();
    await waitForAppShell(page);
    await page.getByTestId('event-tab-communications').click();
    const row = page.locator('[data-testid^="conversation-row-"]').filter({ hasText: subject });
    await expect(row).toBeVisible({ timeout: 15_000 });
    await expect(row).toHaveAttribute('data-status', 'OPEN');
    await expect(page.getByTestId('needs-answer-badge')).toHaveText('1');
    const key = ((await row.getAttribute('data-testid')) ?? '').replace('conversation-row-', '');

    // Opens inline below the row, with the speaker's text in the sandboxed frame.
    await row.getByTestId(`conversation-open-${key}`).click();
    const inline = page.getByTestId(`conversation-inline-${key}`);
    await expect(inline.getByTestId('conversation-detail')).toBeVisible();
    await expect(
      inline.frameLocator('iframe').first().getByText('Do I need HDMI or USB-C?')
    ).toBeVisible();
    await expect(page).toHaveURL(new RegExp(`conversation=${key}`));

    // Done: greyed, no longer counted.
    await inline.getByRole('combobox', { name: 'Status' }).click();
    await page.getByTestId('conversation-status-DONE').click();
    await expect(row).toHaveAttribute('data-status', 'DONE', { timeout: 15_000 });
    await expect(page.getByTestId('needs-answer-badge')).toHaveCount(0);

    // A second click closes it; the deep link opens it again.
    await row.getByTestId(`conversation-open-${key}`).click();
    await expect(inline).toBeHidden();
    await page.goto(`/organizer/events/${event.eventCode}?tab=communications&conversation=${key}`);
    await waitForAppShell(page);
    await expect(page.getByTestId(`conversation-inline-${key}`)).toBeVisible({ timeout: 15_000 });
  });

  test('has no horizontal scroll at phone width', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 800 });
    await forceUserProfileLanguage(page, 'en');
    await page.goto(`/organizer/events/${event.eventCode}`);
    await waitForAppShell(page);
    await page.getByTestId('event-tab-communications').click();
    await expect(page.getByTestId('mail-sent-list')).toBeVisible({ timeout: 15_000 });

    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth - document.documentElement.clientWidth
    );
    expect(overflow).toBeLessThanOrEqual(0);

    // Mobile: "+ New mail" opens the composer as a full-screen dialog, closed with Esc.
    await page.getByTestId('new-mail-button').click();
    await page.getByTestId('new-mail-speakers').click();
    const dialog = page.getByRole('dialog');
    await expect(dialog.getByTestId('compose-panel-speakers')).toBeVisible();
    const box = await dialog.boundingBox();
    expect(box?.width).toBe(375);
    await page.keyboard.press('Escape');
    await expect(page.getByTestId('compose-panel-speakers')).toBeHidden();
    await expect(page.getByTestId('new-mail-button')).toBeFocused();

    // Story 16.2a: a conversation opens as a full-screen dialog, still without horizontal scroll.
    const row = page.locator('[data-testid^="conversation-open-"]').first();
    await row.click();
    const conversation = page.getByRole('dialog');
    await expect(conversation.getByTestId('conversation-detail')).toBeVisible();
    const dialogOverflow = await conversation.evaluate((el) => {
      const paper = el.querySelector('.MuiDialog-paper') ?? el;
      return paper.scrollWidth - paper.clientWidth;
    });
    expect(dialogOverflow).toBeLessThanOrEqual(0);
  });
});
