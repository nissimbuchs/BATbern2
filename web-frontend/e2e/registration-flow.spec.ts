/**
 * E2E: Public Event Registration Flow — slice 7 (plan §C)
 * docs/plans/playwright-staging-hardening.md
 *
 * Rewritten 2026-05-30 to reality + the quality bar (testid-only locators, factory data,
 * mandatory cleanup). The prior spec asserted a STALE flow: submit → /registration-
 * confirmation with a QR code, plus a 3rd "session selection" step. Reality:
 *   • The wizard is 2-step (Personal Details → Confirm) — there is no session step.
 *   • Submit is double-opt-in: it shows an INLINE "email sent — click the link to confirm
 *     (valid 48h)" success view (RegistrationWizard success view) and stays on
 *     /register/:eventCode. The QR code only exists AFTER the emailed confirmation link is
 *     clicked, which an E2E run cannot do — so the old QR / confirmation-URL assertions
 *     were unreachable. They, the "loading state" test, and the "calendar export" test
 *     (also post-confirmation) were DELETED (logged in the plan's PR 8 notes).
 *
 * Prod-safety (plan risk #1): a public registration creates a real `registrations` row
 * PLUS, out-of-band, a CUMS `user_profiles` + `companies` row (getOrCreate, cognitoSync=
 * false). Registrations have NO prefix-sweep path, so we never register against the live
 * "current" event (un-deletable). Each run creates a throwaway CREATED event via the API,
 * registers against IT, asserts the inline success view, then in afterAll: DELETE the event
 * (FK cascade removes the registration) + delete the cross-service CUMS user & company.
 * The `bruno.test*` username is also caught by the global-teardown sweep; the company
 * (lowercased slug vs. the uppercase `BRUNOTESTCO%` case-sensitive sweep) is NOT, so it is
 * explicit-deleted by slug. See e2e/helpers/event-fixture.ts.
 *
 * Anonymous context: the chromium project is authenticated (organizer); the public funnel
 * shows a one-click panel for logged-in users, so we force a fresh anonymous storageState
 * to exercise the real 2-step public wizard.
 */

import { test, expect, type Page } from '@playwright/test';
import { BASE_URL } from '../playwright.config';
import * as factory from './helpers/test-data-factory';
import {
  readOrganizerToken,
  createRegistrationEvent,
  companySlug,
  type RegistrationEvent,
} from './helpers/event-fixture';
import { cleanupByCode, cleanupById } from './helpers/test-fixtures-cleanup';

// Canonical-prefix attendee identity: first/last "bruno"/"test" → CUMS username
// `bruno.test[.N]`, which the global `bruno.test%` user sweep also catches.
const ATTENDEE = { firstName: 'bruno', lastName: 'test' };

// The company the @smoke submits — generated once per worker so afterAll can delete its
// CUMS row by slug. Only the @smoke submits, so this is the only company ever persisted.
// (The company's lowercased slug is NOT reachable by the uppercase `BRUNOTESTCO%` sweep,
// so it must be explicit-deleted; the `bruno.test*` user IS swept and needs no explicit
// delete.)
const SMOKE_COMPANY = factory.companyName();

interface Step1Fields {
  firstName?: string;
  lastName?: string;
  email?: string;
  company?: string;
  role?: string;
}

test.describe('Public Event Registration Flow', { tag: '@gate' }, () => {
  // Serial: the whole describe shares ONE throwaway fixture event (beforeAll/afterAll run
  // once, not per-worker), so we create+tear-down a single event and a single CUMS company
  // — no parallel-worker race on the shared teardown.
  test.describe.configure({ mode: 'serial' });

  // Force a fresh anonymous browser context (no organizer auth) for the public funnel.
  test.use({ storageState: { cookies: [], origins: [] } });

  let token: string;
  let event: RegistrationEvent;

  test.beforeAll(async () => {
    token = readOrganizerToken();
    event = await createRegistrationEvent(token);
  });

  test.afterAll(async () => {
    if (!event?.eventCode) return;
    // 1) Delete the throwaway event → FK cascade removes its registration row(s).
    await cleanupByCode(token, event.eventCode);
    // 2) Delete the CUMS company the @smoke created out-of-band (sweep-unreachable slug).
    //    Tolerant of 404 (no @smoke ran in this worker / already gone).
    await cleanupById(token, 'companies', companySlug(SMOKE_COMPANY));
    //    The `bruno.test*` user is removed by the global-teardown `bruno.test%` sweep.
  });

  /** Fill step 1 (testid-only). Company typing opens an autocomplete popover — dismiss it. */
  async function fillStep1(page: Page, f: Step1Fields): Promise<void> {
    if (f.firstName !== undefined)
      await page.getByTestId('registration-first-name-input').fill(f.firstName);
    if (f.lastName !== undefined)
      await page.getByTestId('registration-last-name-input').fill(f.lastName);
    if (f.email !== undefined) await page.getByTestId('registration-email-input').fill(f.email);
    if (f.role !== undefined) await page.getByTestId('registration-role-input').fill(f.role);
    if (f.company !== undefined) {
      await page.getByTestId('registration-company-input').fill(f.company);
      await page.keyboard.press('Escape'); // close the popover so it can't eat the Next click
    }
  }

  test(
    'submits a public registration and shows the email-confirmation success view',
    { tag: ['@smoke', '@gate'] },
    async ({ page }) => {
      const email = factory.email();

      await page.goto(`${BASE_URL}/register/${event.eventCode}`);
      await expect(page.getByTestId('registration-first-name-input')).toBeVisible();

      await fillStep1(page, { ...ATTENDEE, email, company: SMOKE_COMPANY, role: 'Engineer' });
      await page.getByTestId('registration-wizard-next-btn').click();

      // Step 2: accept terms, then submit.
      const terms = page.getByTestId('terms-checkbox');
      await expect(terms).toBeVisible();
      await terms.check();
      await page.getByTestId('registration-wizard-submit-btn').click();

      // Real success state: the inline "email sent" view (NOT a QR confirmation page),
      // echoing back the registered email.
      await expect(page.getByTestId('registration-success')).toBeVisible();
      await expect(page.getByTestId('registration-success-email')).toHaveText(email);
    }
  );

  test('shows validation errors when submitting an empty step 1', async ({ page }) => {
    await page.goto(`${BASE_URL}/register/${event.eventCode}`);
    await expect(page.getByTestId('registration-first-name-input')).toBeVisible();

    await page.getByTestId('registration-wizard-next-btn').click();

    // FormMessage renders only when a field has an error → its visibility IS the assertion.
    await expect(page.getByTestId('registration-first-name-error')).toBeVisible();
    // And we did not advance to step 2 (terms checkbox stays absent).
    await expect(page.getByTestId('terms-checkbox')).toHaveCount(0);
  });

  test('shows an email-format error for an invalid email', async ({ page }) => {
    await page.goto(`${BASE_URL}/register/${event.eventCode}`);
    await expect(page.getByTestId('registration-email-input')).toBeVisible();

    await fillStep1(page, {
      ...ATTENDEE,
      email: 'not-an-email',
      company: factory.companyName(),
      role: 'Engineer',
    });
    await page.getByTestId('registration-wizard-next-btn').click();

    await expect(page.getByTestId('registration-email-error')).toBeVisible();
  });

  test('preserves entered data when navigating back from step 2', async ({ page }) => {
    await page.goto(`${BASE_URL}/register/${event.eventCode}`);
    await expect(page.getByTestId('registration-first-name-input')).toBeVisible();

    await fillStep1(page, {
      ...ATTENDEE,
      email: factory.email(),
      company: factory.companyName(),
      role: 'Engineer',
    });
    await page.getByTestId('registration-wizard-next-btn').click();

    const back = page.getByTestId('registration-wizard-back-btn');
    await expect(back).toBeVisible();
    await back.click();

    // Step 1 data survives the round-trip.
    await expect(page.getByTestId('registration-first-name-input')).toHaveValue(ATTENDEE.firstName);
  });
});
