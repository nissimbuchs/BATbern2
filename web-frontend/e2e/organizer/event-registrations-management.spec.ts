/**
 * Event Detail → Registrations tab (organizer) — management coverage.
 *
 * Closes a confirmed E2E gap surfaced by the API-consolidation phase-7 manual test
 * pass (2026-06-30): the organizer registrations management surface had ZERO Playwright
 * coverage, yet it maps onto the consolidated/partially-deferred `RegistrationsApi`
 * (`updateRegistrationStatus` for cancel, `DELETE …/registrations/{code}`, and the typed
 * `export.xlsx` / `export.docx` reporting ops). A wire regression here would otherwise be
 * invisible.
 *
 * Email-safety: runs against LOCAL DEV (TEST_ENV=development → localhost). On the Spring
 * `local` profile there is no SesClient — every send is intercepted by LocalEmailCapture
 * (browsable at /dev/emails), so nothing leaves the box even though creating the fixture
 * event auto-enrolls stakeholders. See shared-kernel EmailService / LocalEmailCapture.
 *
 * Fixture model (verified 2026-06-30):
 *  • createRegistrationEvent() leaves a CREATED, future-dated throwaway event that already
 *    auto-enrolls the organizer/partner stakeholders as CONFIRMED registrations.
 *  • We additionally seed two PUBLIC registrations via the no-auth POST (mirrors Bruno's
 *    13-create-registration.bru). They land as status REGISTERED and are visible in the
 *    list — deterministic, named, non-stakeholder rows to cancel/delete.
 *  • Teardown: the event_number ≥ 10000 marker means the global-teardown sweep force-deletes
 *    it (a direct DELETE returns 409 by the real-attendee guard); cleanupByCode is 409-tolerant.
 *    The cross-service company row (getOrCreateCompany) is explicit-deleted by slug.
 */

import { test, expect, type Page } from '@playwright/test';
import {
  readOrganizerToken,
  createRegistrationEvent,
  companySlug,
  type RegistrationEvent,
} from '../helpers/event-fixture';
import { cleanupByCode, cleanupById } from '../helpers/test-fixtures-cleanup';
import { waitForAppShell } from '../helpers/app-shell';

const API_BASE_URL = process.env.E2E_API_URL || 'http://localhost:8000';
/** Unique display name → unique slug, so the leaked CUMS company is safe to delete in teardown. */
const SEED_COMPANY = 'PwRegMgmt Co';

/** Seed one PUBLIC registration via the no-auth POST (mirrors Bruno 13-create-registration). */
async function seedRegistration(eventCode: string, first: string, last: string): Promise<void> {
  const email = `${first}.${last}.${Date.now()}@e2e.batbern.invalid`.toLowerCase();
  const res = await fetch(`${API_BASE_URL}/api/v1/events/${eventCode}/registrations`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      firstName: first,
      lastName: last,
      email,
      company: SEED_COMPANY,
      role: 'Engineer',
      termsAccepted: true,
      communicationPreferences: { newsletterSubscribed: false, eventReminders: false },
      specialRequests: '',
    }),
  });
  if (!res.ok) {
    throw new Error(
      `[reg-mgmt] seed registration ${first} ${last} failed: ${res.status} ${await res.text()}`
    );
  }
}

/** Navigate to the event's Registrations tab and wait for the participant filters to render. */
async function gotoRegistrations(page: Page, eventCode: string): Promise<void> {
  await page.goto(`/organizer/events/${eventCode}`);
  await waitForAppShell(page);
  await page.getByTestId('event-tab-registrations').click();
  await expect(page.getByTestId('participant-status-filter')).toBeVisible({ timeout: 15_000 });
  // Default to the unfiltered view so REGISTERED seeds are present regardless of default filter.
  await page.getByTestId('participant-status-all').click();
}

test.describe('Organizer · Event Registrations management', { tag: '@gate' }, () => {
  test.describe.configure({ mode: 'serial' });
  test.setTimeout(3 * 60 * 1000);

  let token: string;
  let event: RegistrationEvent;

  test.beforeAll(async () => {
    token = readOrganizerToken();
    event = await createRegistrationEvent(token);
    await seedRegistration(event.eventCode, 'Pwcancel', 'Targetrow');
    await seedRegistration(event.eventCode, 'Pwdelete', 'Targetrow');
  });

  test.afterAll(async () => {
    if (event?.eventCode) await cleanupByCode(token, event.eventCode);
    await cleanupById(token, 'companies', companySlug(SEED_COMPANY));
  });

  // ROOT-CAUSED 2026-08-11 (issue #955) — un-quarantined; the cause was in the assertion,
  // not the product.
  //
  // This test, and its `cancels a registration` sibling, failed on all three retries in
  // every Nightly E2E run from 2026-07-02 (last green 2026-06-27) — 41 consecutive red
  // nights, and the only @gate failure in the suite. The 2026-08-09 note here recorded it
  // as UNDIAGNOSED and suspected the seedRegistration() POSTs or the teardown sweep. Both
  // suspicions were wrong.
  //
  // The nightly failure screenshot shows the organizer UI rendered in GERMAN — "ANMELDUNGEN",
  // "Veranstaltungsteilnehmer", "ALLE STATUS", "ABGESAGT" — with "9 / 50 bestätigt", i.e. the
  // seeded rows were present and the page was fine. The assertions were the problem:
  //   • /of\s+[1-9]/ can never match the German summary ("… von 9").
  //   • getByLabel('Cancel Registration') can never match a German aria-label, which is why
  //     the sibling test's click timed out after 180s.
  // The rest of the suite passes because it locates by data-testid, which is locale-neutral.
  // The spec was added 2026-07-01 in aed45d2f, which is exactly why the nightly went red
  // between 06-27 and 07-02: it never passed against a German UI.
  //
  // Rule going forward: assert on testids and numbers, never on translated UI copy.
  test('lists seeded and auto-enrolled registrations', async ({ page }) => {
    await gotoRegistrations(page, event.eventCode);

    await expect(page.getByRole('row', { name: /Pwcancel/ })).toBeVisible();
    await expect(page.getByRole('row', { name: /Pwdelete/ })).toBeVisible();
    // Result-count summary renders a non-zero total. Locale-neutral: match the digits only,
    // never the surrounding copy ("Showing X–Y of Z" / "Zeige X–Y von Z").
    await expect(page.getByTestId('participants-result-count')).toContainText(/[1-9]\d*/);
  });

  test('status filter narrows the list', async ({ page }) => {
    await gotoRegistrations(page, event.eventCode);

    // Seeded rows are REGISTERED → hidden under the CONFIRMED filter, shown again under all.
    await page.getByTestId('participant-status-CONFIRMED').click();
    await expect(page.getByRole('row', { name: /Pwcancel/ })).toBeHidden();

    await page.getByTestId('participant-status-all').click();
    await expect(page.getByRole('row', { name: /Pwcancel/ })).toBeVisible();
  });

  test('exports participants as XLSX', async ({ page }) => {
    await gotoRegistrations(page, event.eventCode);

    const downloadPromise = page.waitForEvent('download');
    await page.getByTestId('participants-export-xlsx').click();
    const download = await downloadPromise;
    expect(download.suggestedFilename()).toMatch(/\.xlsx$/i);
  });

  test('exports participants as DOCX', async ({ page }) => {
    await gotoRegistrations(page, event.eventCode);

    const downloadPromise = page.waitForEvent('download');
    await page.getByTestId('participants-export-docx').click();
    const download = await downloadPromise;
    expect(download.suggestedFilename()).toMatch(/\.docx$/i);
  });

  test('cancels a registration (status → CANCELLED)', async ({ page }) => {
    await gotoRegistrations(page, event.eventCode);

    const row = page.getByRole('row', { name: /Pwcancel/ });
    await expect(row).toBeVisible();

    // Cancel is immediate (no confirm dialog) → PATCH /registrations/{code} {status:cancelled}.
    // Await the PATCH so the assertion can't race the React-Query invalidation.
    const patch = page.waitForResponse(
      (r) => /\/registrations\/[^/]+$/.test(r.url()) && r.request().method() === 'PATCH'
    );
    // Locate by testid, not by accessible name: the nightly renders the organizer UI in
    // German, so getByLabel('Cancel Registration') never resolved (issue #955).
    await row.getByTestId('registration-action-cancel').click();
    expect((await patch).status()).toBe(200);

    // The list invalidates and the status cell updates in place. Assert on the raw enum in
    // data-status, not the rendered label — that label is localised ("Abgesagt" in the
    // nightly's German UI), which is what made this spec unrunnable there (issue #955).
    await expect(row.getByTestId('participant-status-chip')).toHaveAttribute(
      'data-status',
      'CANCELLED'
    );

    // …and the row appears under the CANCELLED filter.
    await page.getByTestId('participant-status-CANCELLED').click();
    await expect(page.getByRole('row', { name: /Pwcancel/ })).toBeVisible();
  });

  test('deletes a registration (row removed)', async ({ page }) => {
    await gotoRegistrations(page, event.eventCode);

    const row = page.getByRole('row', { name: /Pwdelete/ });
    await expect(row).toBeVisible();
    await row.getByTestId('registration-action-delete').click();

    // Confirmation dialog → confirm with the destructive action. Located by testid because
    // the button reads "Löschen" in the nightly's German UI, not "Delete" (issue #955).
    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();
    await dialog.getByTestId('registration-delete-confirm').click();

    await expect(page.getByRole('row', { name: /Pwdelete/ })).toBeHidden();
  });

  // Known bug #824.5: the registered-count display does not refresh after a cancel/delete
  // without a manual reload. Enable once that is fixed.
  test.fixme('registered count refreshes live after cancel/delete (#824)', async () => {});
});
