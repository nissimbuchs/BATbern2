/**
 * Event Detail → Communications: the mail page (Epic 16, Stories 16.3 + 16.4). READ-ONLY.
 *
 * Opens "What went out", "Handled automatically" and every "+ New mail" composer (inline on desktop,
 * full-screen dialog on mobile), and closes each again without sending anything. No compose action
 * is confirmed in this spec.
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
  });
});
