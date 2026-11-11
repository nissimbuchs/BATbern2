/**
 * E2E Tests for Additional Email Addresses (Story 10.32)
 *
 * Verifies the new Account-tab section under user settings: organizer adds an
 * additional email, sees the row, reloads, sees it again, then deletes it.
 *
 * Requires:
 *   - Company User Management Service running (V16 migration applied; new
 *     /users/me/additional-emails endpoints live).
 *   - Organizer authenticated via .playwright-auth-chromium.json
 *
 * Story 4.10d: an address is addressed by its id (`AdditionalEmail.id` from GET /users/me), never
 * by the address. The API checks below call make-primary only for the added, UNCONFIRMED row and
 * for an unknown id: make-primary moves the Cognito login, so its happy path runs only in the CUMS
 * IT against InMemoryCognito, never here (this spec also runs against staging = production).
 */

import { test, expect } from '@playwright/test';
import * as factory from '../helpers/test-data-factory';
import { readOrganizerToken } from '../helpers/event-fixture';

const BASE_URL = process.env.E2E_BASE_URL || 'http://localhost:8100';
const API_BASE_URL = process.env.E2E_API_URL || 'http://localhost:8000';
const UNKNOWN_ID = '00000000-0000-4000-8000-000000000000';

function api(path: string, init: RequestInit = {}): Promise<Response> {
  return fetch(`${API_BASE_URL}/api/v1${path}`, {
    ...init,
    headers: {
      Accept: 'application/json',
      Authorization: `Bearer ${readOrganizerToken()}`,
      ...init.headers,
    },
  });
}

/** The id of one of the organizer's additional addresses, from GET /users/me. */
async function additionalEmailId(email: string): Promise<string | undefined> {
  const r = await api('/users/me');
  if (!r.ok) return undefined;
  const me = (await r.json()) as { additionalEmails?: { id: string; email: string }[] };
  return me.additionalEmails?.find((a) => a.email === email.toLowerCase())?.id;
}

// Canonical factory email (`bruno-test-<ts>@e2e.batbern.invalid`) — swept by the
// `cums/additional_emails` prefix (`bruno-test-`) so the global-teardown sweep is the
// belt-and-suspenders backstop behind this spec's own afterEach delete (plan §A4/§A5).
const TEST_EMAIL = factory.email();

test.describe('User Settings — Additional Emails (Story 10.32)', { tag: '@gate' }, () => {
  // Inherit the chromium project's organizer storageState (`.playwright-auth-state.json` from
  // global-setup). The old hardcoded `.playwright-auth-chromium.json` override never existed in
  // the current global-setup output → ENOENT. No per-test override needed.

  test.afterEach(async () => {
    // Best-effort cleanup so re-runs in shared staging don't trip the 5-email cap: delete by the
    // id that GET /users/me lists for the address (none when the test already removed it).
    const id = await additionalEmailId(TEST_EMAIL).catch(() => undefined);
    if (id) {
      await api(`/users/me/additional-emails/${id}`, { method: 'DELETE' }).catch(() => undefined);
    }
  });

  test('organizer can add, persist across reload, and delete an additional email', async ({
    page,
  }) => {
    await page.goto(`${BASE_URL}/account`);

    // Navigate to Settings tab on the account page.
    await page.getByTestId('settings-tab').click();

    // Section heading should render.
    await expect(page.getByTestId('additional-emails-section')).toBeVisible({ timeout: 10_000 });

    // Fill the inline form and submit.
    const emailField = page.getByTestId('additional-email-input').locator('input');
    await emailField.fill(TEST_EMAIL);
    await page.getByTestId('additional-email-add-button').click();

    // Row should appear with delete button visible.
    const rowTestId = `additional-email-row-${TEST_EMAIL}`;
    await expect(page.getByTestId(rowTestId)).toBeVisible({ timeout: 5_000 });

    // Reload — row persists from the server.
    await page.reload();
    await page.getByTestId('settings-tab').click();
    await expect(page.getByTestId(rowTestId)).toBeVisible({ timeout: 10_000 });

    // API (4.10d): the added row is unconfirmed, so make-primary refuses it (409 NOT_VERIFIED);
    // an id that is none of the caller's answers 404. Neither moves the login.
    const id = await additionalEmailId(TEST_EMAIL);
    expect(id).toMatch(/^[0-9a-f-]{36}$/);
    const unconfirmed = await api(`/users/me/additional-emails/${id}/make-primary`, {
      method: 'POST',
    });
    expect(unconfirmed.status).toBe(409);
    expect(((await unconfirmed.json()) as { errorCode?: string }).errorCode).toBe('NOT_VERIFIED');
    const unknown = await api(`/users/me/additional-emails/${UNKNOWN_ID}/make-primary`, {
      method: 'POST',
    });
    expect(unknown.status).toBe(404);

    // Delete via icon → confirm in the MUI dialog (the component uses a MUI confirm dialog,
    // NOT a native window.confirm, so a `page.on('dialog')` handler would never fire).
    await page.getByTestId(`additional-email-delete-${TEST_EMAIL}`).click();
    await expect(page.getByTestId('additional-email-delete-confirm-dialog')).toBeVisible();
    await page.getByTestId('additional-email-delete-confirm').click();

    // Row gone.
    await expect(page.getByTestId(rowTestId)).toHaveCount(0, { timeout: 5_000 });
  });
});
