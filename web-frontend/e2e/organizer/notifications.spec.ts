/**
 * Organizer · Notifications (Story 4.10b): the inbox is the signed-in organizer's own
 * (`GET /notifications` without a username), one notification is marked read with
 * `PATCH /notifications/{id}` and deleted with `DELETE /notifications/{id}`; both persist after a
 * reload.
 *
 * Guarded to LOCAL DEV (`TEST_ENV=development`, never `@gate`): it creates a fixture event (event
 * number >= 10000, the sweepable test marker), whose domain event gives every organizer an in-app
 * notification, and changes notifications. Teardown deletes the event, which removes its
 * notifications (#1068). It sends no mail. Another organizer's id answering 404 needs a second
 * organizer and is covered by NotificationControllerIntegrationTest.
 */

import { test, expect } from '@playwright/test';
import { createRegistrationEvent, readOrganizerToken } from '../helpers/event-fixture';
import { cleanupByCode } from '../helpers/test-fixtures-cleanup';
import { waitForAppShell } from '../helpers/app-shell';

const LOCAL_DEV = (process.env.TEST_ENV ?? 'development') === 'development';
const API_URL = process.env.E2E_API_URL || 'http://localhost:8000';
const describeLocal = LOCAL_DEV ? test.describe : test.describe.skip;

interface InboxItem {
  id: string;
  eventCode: string | null;
  status: string;
}

async function inbox(token: string): Promise<InboxItem[]> {
  const res = await fetch(`${API_URL}/api/v1/notifications?page=1&limit=100`, {
    headers: { Authorization: `Bearer ${token}`, Accept: 'application/json' },
  });
  expect(res.status).toBe(200);
  return ((await res.json()) as { data: InboxItem[] }).data;
}

describeLocal('Organizer · Notifications (4.10b)', () => {
  let token: string;
  let eventCode = '';

  test.beforeAll(async () => {
    token = readOrganizerToken();
    ({ eventCode } = await createRegistrationEvent(token));
  });

  test.afterAll(async () => {
    await cleanupByCode(token, eventCode);
  });

  test('should_persistReadAndDelete_when_organizerUsesTheInbox', async ({ page }) => {
    // The listener writes the notifications after the create commits: wait for two of them
    // (the create may emit more than one domain event), at least one unread.
    let own: InboxItem[] = [];
    await expect
      .poll(
        async () => {
          own = (await inbox(token)).filter((n) => n.eventCode === eventCode);
          return own.filter((n) => n.status === 'UNREAD').length;
        },
        { timeout: 20_000 }
      )
      .toBeGreaterThan(0);
    const target = own.find((n) => n.status === 'UNREAD')!;

    await page.goto('/organizer/notifications');
    await waitForAppShell(page);
    const item = page.getByTestId(`notification-item-${target.id}`);
    await expect(item).toHaveAttribute('data-unread', 'true');

    await page.getByTestId(`notification-mark-read-${target.id}`).click();
    await expect(item).toHaveAttribute('data-unread', 'false');
    await page.reload();
    await expect(page.getByTestId(`notification-item-${target.id}`)).toHaveAttribute(
      'data-unread',
      'false'
    );
    expect((await inbox(token)).find((n) => n.id === target.id)?.status).toBe('READ');

    await page.getByTestId(`notification-delete-${target.id}`).click();
    await expect(page.getByTestId(`notification-item-${target.id}`)).toHaveCount(0);
    await page.reload();
    await expect(page.getByTestId('notifications-page')).toBeVisible();
    await expect(page.getByTestId(`notification-item-${target.id}`)).toHaveCount(0);
    expect((await inbox(token)).some((n) => n.id === target.id)).toBe(false);
  });
});
