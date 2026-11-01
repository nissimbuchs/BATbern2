/**
 * Event Detail → Communications → "+ New mail": the shared composer (Story 16.8b). READ-ONLY.
 *
 * Opens the composer for every audience the shared composer serves and checks its steps: template,
 * recipients, preview from the server, both send buttons. It previews only; no send button is
 * clicked in this spec (the sending spec is event-mail-composer-send.spec.ts, @sends-real-email).
 *
 * Guarded to LOCAL DEV like the other mail specs: the fixture event is created through the API,
 * and on staging (= production) the AWS account is live (ADR-015). Never tag it @gate.
 *
 * A fresh fixture event has no speakers, so that audience shows the server's "nobody" message.
 * It does have registrants: creating an event auto-enrols the organizers as participants, so the
 * registrants composer steps through them with ‹ ›.
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
const describeComposer = LOCAL_DEV ? test.describe : test.describe.skip;

describeComposer('Organizer · Mail composer (read-only)', () => {
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

  async function openComposer(page: Page, audience: string): Promise<void> {
    await page.getByTestId('new-mail-button').click();
    await page.getByTestId(`new-mail-${audience}`).click();
    await expect(page.getByTestId(`compose-panel-${audience}`)).toBeVisible();
  }

  async function gotoMailPage(page: Page): Promise<void> {
    await forceUserProfileLanguage(page, 'en');
    await page.goto(`/organizer/events/${event.eventCode}`);
    await waitForAppShell(page);
    await page.getByTestId('event-tab-communications').click();
    await expect(page.getByTestId('mail-sent-list')).toBeVisible({ timeout: 15_000 });
  }

  test('newsletter: template preselected, real subscriber previewed, both send buttons ready', async ({
    page,
  }) => {
    await gotoMailPage(page);
    await openComposer(page, 'newsletter');

    const composer = page.getByTestId('mail-composer-NEWSLETTER');
    await expect(composer.getByTestId('composer-template-select')).toContainText(
      'newsletter-event'
    );
    await expect(composer.getByTestId('composer-source-own')).toBeDisabled();
    await expect(composer.getByTitle('Mail preview')).toBeVisible({ timeout: 15_000 });
    await expect(composer.getByTestId('composer-recipient-count')).toContainText('recipient');
    await expect(composer.getByTestId('composer-subject')).toContainText('Subject: ');
    await expect(composer.getByTestId('composer-recipient')).toContainText('@');
    await expect(composer.getByTestId('composer-unknown-placeholders')).toHaveCount(0);
    await expect(composer.getByTestId('composer-test-button')).toBeEnabled();
    await expect(composer.getByTestId('composer-send-button')).toBeEnabled();

    // The language switch fetches a new preview; the newsletter is the same for everyone.
    await composer.getByTestId('composer-locale-en').click();
    await expect(composer.getByTestId('composer-same-text')).toBeVisible();
    await expect(composer.getByTestId('composer-next')).toHaveCount(0);

    // Send asks first; Back leaves nothing sent.
    await composer.getByTestId('composer-send-button').click();
    await expect(composer.getByTestId('composer-confirm')).toBeVisible();
    await composer.getByTestId('composer-confirm-back').click();
    await expect(composer.getByTestId('composer-confirm')).toHaveCount(0);

    await page.keyboard.press('Escape');
    await expect(page.getByTestId('compose-panel-newsletter')).toBeHidden();
  });

  test('registrants: personalised preview steps through the recipients with ‹ ›', async ({
    page,
  }) => {
    await gotoMailPage(page);
    await openComposer(page, 'registrants');
    const composer = page.getByTestId('mail-composer-REGISTRANTS');
    await expect(composer.getByTitle('Mail preview')).toBeVisible({ timeout: 15_000 });
    await expect(
      composer.getByText('Each recipient gets the mail in their own language.')
    ).toBeVisible();
    await expect(composer.getByTestId('composer-locale-en')).toHaveCount(0);

    const position = composer.getByTestId('composer-position');
    await expect(position).toContainText('(1/');
    await expect(composer.getByRole('button', { name: 'Previous recipient' })).toBeDisabled();
    const first = await composer.getByTestId('composer-recipient').textContent();

    await composer.getByRole('button', { name: 'Next recipient' }).click();
    await expect(position).toContainText('(2/');
    await expect(composer.getByTestId('composer-recipient')).not.toHaveText(first ?? '');
    await expect(composer.getByTestId('composer-send-button')).toBeEnabled();

    await page.keyboard.press('Escape');
    await expect(page.getByTestId('compose-panel-registrants')).toBeHidden();
  });

  test('speakers of a fresh event: the server says nobody would get it', async ({ page }) => {
    await gotoMailPage(page);
    await openComposer(page, 'speakers');
    const composer = page.getByTestId('mail-composer-SPEAKERS');
    await expect(composer.getByTestId('composer-error')).toContainText('Nobody', {
      timeout: 15_000,
    });
    await expect(composer.getByTestId('composer-send-button')).toBeDisabled();
    await expect(composer.getByTestId('composer-test-button')).toBeDisabled();
    await page.keyboard.press('Escape');
    await expect(page.getByTestId('compose-panel-speakers')).toBeHidden();
  });

  test('speakers: Automatic plus the six reminder templates', async ({ page }) => {
    await gotoMailPage(page);
    await openComposer(page, 'speakers');
    await page.getByTestId('mail-composer-SPEAKERS').getByRole('combobox').click();
    const options = page.getByRole('listbox').getByRole('option');
    await expect(options).toHaveCount(7);
    await expect(options.first()).toContainText('Automatic');
    await page.keyboard.press('Escape');
  });

  test('venue: one mail to the chosen roles, previewed', async ({ page }) => {
    await gotoMailPage(page);
    await openComposer(page, 'venue');
    const composer = page.getByTestId('mail-composer-VENUE');
    await expect(composer.getByTitle('Mail preview')).toBeVisible({ timeout: 15_000 });
    await expect(composer.getByTestId('composer-recipient-count')).toContainText('1 recipient');

    // No role: nothing to preview, nothing to send.
    await composer.getByTestId('venue-coord-send-venue').locator('input').uncheck();
    await composer.getByTestId('venue-coord-send-catering').locator('input').uncheck();
    await expect(composer.getByTestId('composer-send-button')).toBeDisabled();
  });

  test('phone width: full-screen dialog, no horizontal scroll, footer buttons visible', async ({
    page,
  }) => {
    await page.setViewportSize({ width: 375, height: 800 });
    await gotoMailPage(page);
    await openComposer(page, 'newsletter');
    const dialog = page.getByRole('dialog');
    await expect(dialog.getByTitle('Mail preview')).toBeVisible({ timeout: 15_000 });

    const overflow = await dialog.evaluate((el) => {
      const scroller = el.querySelector('.MuiDialog-paper') ?? el;
      return scroller.scrollWidth - scroller.clientWidth;
    });
    expect(overflow).toBeLessThanOrEqual(0);
    // Sticky footer: the buttons are in the viewport without scrolling the dialog.
    await expect(dialog.getByTestId('composer-send-button')).toBeInViewport();
    await expect(dialog.getByTestId('composer-test-button')).toBeInViewport();

    await page.keyboard.press('Escape');
    await expect(page.getByTestId('new-mail-button')).toBeFocused();
  });
});
