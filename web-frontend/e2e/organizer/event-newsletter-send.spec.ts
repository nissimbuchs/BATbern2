/**
 * Event Detail → Communications → Newsletter send (organizer).
 *
 * Closes a confirmed E2E gap (API-consolidation phase-7 manual pass, 2026-06-30): the
 * newsletter send flow had ZERO Playwright coverage, yet it maps onto the freshly
 * consolidated NewsletterController (preview/send/status — the 16-op `NewsletterApi` wire,
 * incl. the `testMode` field whose omission would have silently mailed all subscribers).
 *
 * ⚠️ EMAIL-SAFETY — DEV-ONLY, NEVER @gate. This spec drives the REAL newsletter send
 * pipeline end-to-end. On LOCAL DEV (TEST_ENV=development) the Spring `local` profile has
 * no SesClient → every send is intercepted by LocalEmailCapture (/dev/emails) and nothing
 * leaves the box. On staging/production SES is LIVE, so running this here mails the ENTIRE
 * subscriber list. It is therefore hard-guarded to `development` (see EMAIL_SAFE_ENV below)
 * AND tagged @sends-real-email so run-playwright-tests.sh excludes it on every non-dev env.
 *
 * INCIDENT 2026-07-01: this spec was tagged @gate; nightly-e2e.yml runs the full @gate suite
 * against staging (= production), and CI retries=2 caused beforeAll to run 3×. Result: ~1,050
 * community members each received 3 real "BATPW-E2E" test newsletters (3,175 SES sends). Do
 * NOT re-add @gate, and do NOT weaken the EMAIL_SAFE_ENV guard.
 *
 * The fixture event is a throwaway (event_number ≥ 10000, swept in teardown).
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

// Hard email-safety gate: only LOCAL DEV intercepts mail (LocalEmailCapture). On any deployed
// env SES is live, so the whole group — INCLUDING the event-creating beforeAll — is skipped.
// describe.skip (not test.skip) guarantees the beforeAll never runs, so no send is ever issued.
const EMAIL_SAFE_ENV = (process.env.TEST_ENV ?? 'development') === 'development';
const describeNewsletter = EMAIL_SAFE_ENV ? test.describe : test.describe.skip;

describeNewsletter('Organizer · Event newsletter send', { tag: '@sends-real-email' }, () => {
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
    const sendButton = page.getByTestId('newsletter-send-button');
    await expect(sendButton).toBeVisible({ timeout: 15_000 });

    // Preview renders without disabling the send action.
    await page.getByTestId('newsletter-preview-button').click();
    await expect(sendButton).toBeEnabled();

    // Send → confirm dialog → confirm. (Safe in dev: LocalEmailCapture intercepts.)
    await sendButton.click();
    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();
    await dialog.getByTestId('newsletter-confirm-send-button').click();

    // The component polls the send status; a terminal COMPLETED state shows this alert.
    await expect(page.getByTestId('newsletter-send-completed')).toBeVisible({ timeout: 45_000 });
  });
});
