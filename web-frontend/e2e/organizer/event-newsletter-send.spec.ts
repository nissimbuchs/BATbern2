/**
 * Event Detail → Communications → Newsletter send (organizer).
 *
 * Closes a confirmed E2E gap (API-consolidation phase-7 manual pass, 2026-06-30): the
 * newsletter send flow had ZERO Playwright coverage, yet it maps onto the freshly
 * consolidated NewsletterController (preview/send/status — the 16-op `NewsletterApi` wire,
 * incl. the `testMode` field whose omission would have silently mailed all subscribers).
 *
 * Email-safety: runs against LOCAL DEV (TEST_ENV=development). The Spring `local` profile
 * has no SesClient → every send is intercepted by LocalEmailCapture (/dev/emails), so the
 * send pipeline executes end-to-end but nothing leaves the box. The fixture event is a
 * throwaway (event_number ≥ 10000, swept in teardown).
 *
 * Locale: pinned to EN via forceUserProfileLanguage so the role-name button selectors
 * ("Send Newsletter" / "Confirm") are stable regardless of the organizer's profile language.
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

test.describe('Organizer · Event newsletter send', { tag: '@gate' }, () => {
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

  async function gotoNewsletter(page: Page): Promise<void> {
    await forceUserProfileLanguage(page, 'en');
    await page.goto(`/organizer/events/${event.eventCode}`);
    await waitForAppShell(page);
    await page.getByTestId('event-tab-communications').click();
    // Newsletter is the default communications subtab; click it defensively if rendered.
    const subtab = page.getByTestId('comms-subtab-newsletter');
    if (await subtab.count()) await subtab.click();
  }

  test('previews then sends a newsletter to completion', async ({ page }) => {
    await gotoNewsletter(page);

    // Default template 'newsletter-event' (de→auto) is preselected; no selection needed.
    const sendButton = page.getByRole('button', { name: 'Send Newsletter' });
    await expect(sendButton).toBeVisible({ timeout: 15_000 });

    // Preview renders without disabling the send action.
    await page.getByRole('button', { name: 'Preview' }).click();
    await expect(sendButton).toBeEnabled();

    // Send → confirm dialog → confirm. (Safe in dev: LocalEmailCapture intercepts.)
    await sendButton.click();
    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();
    await dialog.getByRole('button', { name: 'Confirm' }).click();

    // The component polls the send status; a terminal COMPLETED state shows this alert.
    await expect(page.getByTestId('newsletter-send-completed')).toBeVisible({ timeout: 45_000 });
  });
});
