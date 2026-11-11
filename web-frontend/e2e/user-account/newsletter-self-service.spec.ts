/**
 * User account · Newsletter per address (Story 4.10c): on /profile → consent, each address has its
 * own checkbox; a click sends `PATCH /newsletter/my-subscriptions` for that address only, and the
 * state survives a reload. The test flips the signed-in user's primary address and puts it back
 * (in the UI, and once more through the API in a finally), so the account is left as it was.
 *
 * Guarded to LOCAL DEV: it changes the signed-in user's subscription. No mail is sent.
 */

import { test, expect, type Page } from '@playwright/test';
import { readOrganizerToken } from '../helpers/event-fixture';
import { forceUserProfileLanguage } from '../helpers/mock-user-profile';

const LOCAL_DEV = (process.env.TEST_ENV ?? 'development') === 'development';
const API_URL = process.env.E2E_API_URL || 'http://localhost:8000';
const describeLocal = LOCAL_DEV ? test.describe : test.describe.skip;

interface Address {
  email: string;
  primary: boolean;
  subscribed: boolean;
}

async function myAddresses(token: string): Promise<Address[]> {
  const res = await fetch(`${API_URL}/api/v1/newsletter/my-subscriptions`, {
    headers: { Authorization: `Bearer ${token}`, Accept: 'application/json' },
  });
  expect(res.status, 'GET /newsletter/my-subscriptions').toBe(200);
  return ((await res.json()) as { addresses: Address[] }).addresses;
}

async function openConsent(page: Page) {
  await page.goto('/profile');
  await page.getByTestId('consent-tab').click({ timeout: 20_000 });
  await expect(page.getByTestId('newsletter-section')).toBeVisible();
}

async function clickAndWaitForPatch(page: Page, email: string, expected: boolean) {
  const patch = page.waitForResponse(
    (r) => r.request().method() === 'PATCH' && r.url().endsWith('/newsletter/my-subscriptions')
  );
  await page.getByTestId(`newsletter-address-${email}`).click();
  const response = await patch;
  expect(response.status()).toBe(200);
  const sent = JSON.parse(response.request().postData() ?? '{}') as {
    email: string;
    subscribed: boolean;
  };
  expect(sent.email).toBe(email);
  expect(sent.subscribed).toBe(expected);
}

describeLocal('User account · Newsletter per address (4.10c)', () => {
  test('flips the primary address, it persists after reload, and flips back', async ({ page }) => {
    const token = readOrganizerToken();
    const before = await myAddresses(token);
    const primary = before.find((a) => a.primary);
    expect(primary, 'the user has a primary address').toBeDefined();
    const { email, subscribed } = primary!;

    try {
      await forceUserProfileLanguage(page, 'en');
      await openConsent(page);
      const box = page.getByTestId(`newsletter-address-${email}`);
      await expect(box).toBeChecked({ checked: subscribed });

      await clickAndWaitForPatch(page, email, !subscribed);
      await page.reload();
      await openConsent(page);
      await expect(page.getByTestId(`newsletter-address-${email}`)).toBeChecked({
        checked: !subscribed,
      });
      // Only that address changed.
      const during = await myAddresses(token);
      before
        .filter((a) => a.email !== email)
        .forEach((a) =>
          expect(during.find((d) => d.email === a.email)?.subscribed).toBe(a.subscribed)
        );

      await clickAndWaitForPatch(page, email, subscribed);
      await page.reload();
      await openConsent(page);
      await expect(page.getByTestId(`newsletter-address-${email}`)).toBeChecked({
        checked: subscribed,
      });
    } finally {
      await fetch(`${API_URL}/api/v1/newsletter/my-subscriptions`, {
        method: 'PATCH',
        headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, subscribed }),
      });
    }
  });
});
