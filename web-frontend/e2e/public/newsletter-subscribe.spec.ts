/**
 * Public · Newsletter subscribe (Story 4.10c): an anonymous visitor subscribes in the homepage
 * widget, which posts to `POST /newsletter/subscribers` (was `/newsletter/subscribe`). The
 * subscriber is checked and deleted through the organizer API afterwards.
 *
 * Guarded to LOCAL DEV: it subscribes an address. The address is synthetic, not a reserved
 * domain (those are refused) and unique per run. No mail is sent (no double opt-in, #1288).
 * The gateway allows 5 anonymous subscribe calls per IP in 5 minutes; this spec makes one.
 */

import { test, expect } from '@playwright/test';
import { readOrganizerToken } from '../helpers/event-fixture';

const LOCAL_DEV = (process.env.TEST_ENV ?? 'development') === 'development';
const API_URL = process.env.E2E_API_URL || 'http://localhost:8000';
const describeLocal = LOCAL_DEV ? test.describe : test.describe.skip;

async function findSubscriber(token: string, email: string) {
  const res = await fetch(
    `${API_URL}/api/v1/newsletter/subscribers?search=${encodeURIComponent(email)}`,
    { headers: { Authorization: `Bearer ${token}`, Accept: 'application/json' } }
  );
  expect(res.status).toBe(200);
  const body = (await res.json()) as {
    data: Array<{ id: string; email: string; source: string; unsubscribedAt: string | null }>;
  };
  return body.data.find((s) => s.email === email.toLowerCase());
}

describeLocal('Public · Newsletter subscribe (4.10c)', () => {
  // Anonymous: no signed-in session, and none of the Authorization header the config adds.
  test.use({ storageState: { cookies: [], origins: [] }, extraHTTPHeaders: {} });

  test('an anonymous visitor subscribes on the homepage, and the subscriber is stored', async ({
    page,
  }) => {
    const token = readOrganizerToken();
    const email = `e2e-newsletter+${Date.now()}@batbern.ch`;

    try {
      await page.goto('/');
      // The widget is the homepage's only e-mail form (lazy, below the fold). Language-agnostic:
      // an anonymous visitor gets the German UI by default.
      const form = page.locator('form').filter({ has: page.locator('input[type="email"]') });
      await form.scrollIntoViewIfNeeded({ timeout: 20_000 });
      await form.locator('input[type="email"]').fill(email);

      const post = page.waitForResponse(
        (r) => r.request().method() === 'POST' && r.url().endsWith('/newsletter/subscribers')
      );
      await form.locator('button[type="submit"]').click();
      const response = await post;
      expect(response.status()).toBe(200);
      expect(response.request().headers()['authorization']).toBeUndefined();
      await expect(
        page.getByText(/Danke! Du bist jetzt angemeldet\.|Thank you! You are now subscribed\./)
      ).toBeVisible();

      await page.reload();
      const stored = await findSubscriber(token, email);
      expect(stored, 'subscriber stored').toBeDefined();
      expect(stored!.source).toBe('explicit');
      expect(stored!.unsubscribedAt).toBeNull();
    } finally {
      const stored = await findSubscriber(token, email).catch(() => undefined);
      if (stored) {
        await fetch(`${API_URL}/api/v1/newsletter/subscribers/${stored.id}`, {
          method: 'DELETE',
          headers: { Authorization: `Bearer ${token}` },
        });
      }
    }
  });
});
