/**
 * E2E Tests for Partner Create/Edit Workflow
 * Story 2.8.3: Partner Create/Edit Modal
 *
 * Slice 11 (partners) — rewrite-to-reality + canonical cleanup (plan §C).
 * Runs in the 'chromium' (organizer) project.
 *
 * THE SLICE'S `@smoke`: organizer creates a partnership through the UI (company
 * autocomplete → tier → save → land on the detail page) and tears it down. Deterministic,
 * no Cognito provisioning, no promote — a safe per-deploy blocking gate path.
 *
 * Prod-safety crux (why this slice was flagged): the OLD spec named its fixture company
 * `tc-${random}` (NOT swept by any canonical prefix) and cleaned up via ad-hoc
 * `deletePartnerViaAPI`/`deleteCompanyViaAPI` — a residue source. Now:
 *   • the fixture company is named via `factory.partnerName()` → `brtest<6>` (≤12 chars, the
 *     PCS `company_name VARCHAR(12)` bound), so the PARTNER row is swept by the `pcs/partners`
 *     `brtest` prefix (global-teardown backstop);
 *   • teardown is the canonical `cleanupById('partners', name)` then `cleanupById('companies',
 *     name)` — the `brtest`-named COMPANY is NOT reached by the `cums/companies` `BRUNOTESTCO`
 *     sweep, so it MUST be deleted explicitly (partner first — the company FK blocks otherwise).
 *
 * Tests DELETED vs the old spec (rewrite-to-reality):
 *   • "AC7 date range" — the start/end pickers enforce `maxDate=today` / `minDate=startDate`,
 *     so an invalid range CANNOT be entered through the UI; the old test typed a locale date
 *     string straight into the input (brittle, locale-coupled). Range validation is a
 *     unit-test concern.
 *   • "AC10 unsaved-changes" — asserted only inside `if (dialogShown)`, so it asserted nothing
 *     when the native confirm didn't fire; flaky and effectively empty.
 *   • "AC12 date formatting" — asserted a locale-specific date regex; low value and
 *     locale-coupled. The default-date presence is covered by the @smoke (AC5).
 *
 * Run: cd web-frontend && npx playwright test --project=chromium \
 *   e2e/workflows/partner-management/partner-create-edit.spec.ts
 */

import { test, expect } from '@playwright/test';
import { BASE_URL, API_URL } from '../../../playwright.config';
import * as factory from '../../helpers/test-data-factory';
import { cleanupById } from '../../helpers/test-fixtures-cleanup';

interface TestCompany {
  name: string;
  displayName: string;
  industry: string;
}

/** Canonical fixture company: `brtest<6>` — ≤12 chars; the partner row is `brtest`-swept. */
function makeTestCompany(): TestCompany {
  const name = factory.partnerName();
  return { name, displayName: `Partner Co ${name}`, industry: 'Technology' };
}

function getAuthToken(): string {
  const token = process.env.AUTH_TOKEN;
  if (!token) {
    throw new Error(
      'AUTH_TOKEN not found in environment. Run: ./scripts/auth/get-token.sh staging <email> <password>'
    );
  }
  return token;
}

async function createCompanyViaAPI(token: string, company: TestCompany): Promise<void> {
  const res = await fetch(`${API_URL}/api/v1/companies`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify(company),
  });
  if (!res.ok && res.status !== 409) {
    throw new Error(`Failed to create company (${res.status}): ${await res.text()}`);
  }
}

async function createPartnerViaAPI(
  token: string,
  companyName: string,
  level: string
): Promise<void> {
  const res = await fetch(`${API_URL}/api/v1/partners`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({
      companyName,
      partnershipLevel: level,
      partnershipStartDate: new Date().toISOString().split('T')[0],
    }),
  });
  if (!res.ok) {
    throw new Error(`Failed to create partner (${res.status}): ${await res.text()}`);
  }
}

/** Canonical teardown: delete the partner, then the (brtest-named) company. Never throws. */
async function cleanupPartnerAndCompany(token: string, companyName: string): Promise<void> {
  await cleanupById(token, 'partners', companyName);
  await cleanupById(token, 'companies', companyName);
}

test.describe('Partner Create/Edit Modal', () => {
  // ─── @smoke: the slice's one mutating + cleanup happy path ────────────────────

  test('should_createPartnership_whenValidData @smoke (AC1, AC3, AC8)', async ({ page }) => {
    const token = getAuthToken();
    const company = makeTestCompany();
    await createCompanyViaAPI(token, company);

    try {
      await page.goto(`${BASE_URL}/organizer/partners`);
      await expect(page.getByTestId('partner-directory-screen')).toBeVisible({ timeout: 10000 });

      // AC1: open create modal
      await page.getByTestId('add-partner-button').click();
      await expect(page.getByTestId('partner-create-edit-modal')).toBeVisible();

      // AC3: company autocomplete — type the technical name, select the keyed option
      await page.getByTestId('company-autocomplete').locator('input').fill(company.name);
      await page.getByTestId(`company-option-${company.name}`).click();

      // AC4: tier dropdown → GOLD (testid-keyed MenuItem, locale-independent)
      await page.getByTestId('partnership-tier-select').click();
      await page.getByTestId('tier-select-option-GOLD').click();

      // AC5: start date defaults to today (non-empty)
      await expect(page.getByTestId('partnershipStartDate')).toHaveValue(/.+/);

      // AC8: submit → land on the detail page (route is /organizer/partners/:companyName)
      await page.getByTestId('save-partner-button').click();
      await page.waitForURL(`${BASE_URL}/organizer/partners/${company.name}`, { timeout: 10000 });
      await expect(page.getByTestId('partner-detail-header')).toBeVisible();
    } finally {
      await cleanupPartnerAndCompany(token, company.name);
    }
  });

  // ─── @gate: edit an existing partnership's tier ───────────────────────────────

  test('should_editPartnerTier_whenEditModalSaved @gate (AC2)', async ({ page }) => {
    const token = getAuthToken();
    const company = makeTestCompany();
    await createCompanyViaAPI(token, company);
    await createPartnerViaAPI(token, company.name, 'BRONZE');

    try {
      await page.goto(`${BASE_URL}/organizer/partners/${company.name}`);
      await expect(page.getByTestId('partner-detail-header')).toBeVisible({ timeout: 10000 });

      await page.getByTestId('edit-partner-button').click();
      await expect(page.getByTestId('partner-create-edit-modal')).toBeVisible();

      // Company is read-only in edit mode; change tier BRONZE → PLATINUM
      await page.getByTestId('partnership-tier-select').click();
      await page.getByTestId('tier-select-option-PLATINUM').click();

      await page.getByTestId('save-partner-button').click();
      await expect(page.getByTestId('partner-create-edit-modal')).toBeHidden();
      await expect(page.getByTestId('partner-detail-header')).toBeVisible();
    } finally {
      await cleanupPartnerAndCompany(token, company.name);
    }
  });

  // ─── @gate: required-field validation keeps the modal open ────────────────────

  test('should_blockSubmit_whenRequiredFieldsMissing @gate (AC7)', async ({ page }) => {
    await page.goto(`${BASE_URL}/organizer/partners`);
    await expect(page.getByTestId('partner-directory-screen')).toBeVisible({ timeout: 10000 });

    await page.getByTestId('add-partner-button').click();
    await expect(page.getByTestId('partner-create-edit-modal')).toBeVisible();

    // Submit with no company selected → modal stays open (validation blocked submit)
    await page.getByTestId('save-partner-button').click();
    await expect(page.getByTestId('partner-create-edit-modal')).toBeVisible();
    await expect(page.getByTestId('company-autocomplete')).toBeVisible();
  });

  // ─── @gate: company autocomplete search + selection ───────────────────────────

  test('should_searchAndSelectCompany_inAutocomplete @gate (AC3)', async ({ page }) => {
    const token = getAuthToken();
    const company = makeTestCompany();
    await createCompanyViaAPI(token, company);

    try {
      await page.goto(`${BASE_URL}/organizer/partners`);
      await expect(page.getByTestId('partner-directory-screen')).toBeVisible({ timeout: 10000 });

      await page.getByTestId('add-partner-button').click();
      await expect(page.getByTestId('partner-create-edit-modal')).toBeVisible();

      const input = page.getByTestId('company-autocomplete').locator('input');
      await input.fill(company.name);

      const option = page.getByTestId(`company-option-${company.name}`);
      await expect(option).toBeVisible();
      await option.click();

      // CompanyAutocomplete's getOptionLabel renders `displayName || name`, so once selected
      // the input shows the display name (the option is still keyed by the technical name).
      await expect(input).toHaveValue(company.displayName);
    } finally {
      await cleanupById(token, 'companies', company.name);
    }
  });

  // ─── @gate: Escape closes a pristine modal without a confirm prompt ───────────

  test('should_closeModalOnEscape_whenNoChanges @gate (AC11)', async ({ page }) => {
    await page.goto(`${BASE_URL}/organizer/partners`);
    await expect(page.getByTestId('partner-directory-screen')).toBeVisible({ timeout: 10000 });

    await page.getByTestId('add-partner-button').click();
    await expect(page.getByTestId('partner-create-edit-modal')).toBeVisible();

    // Pristine form → no unsaved-changes confirm → Escape closes immediately
    await page.keyboard.press('Escape');
    await expect(page.getByTestId('partner-create-edit-modal')).toBeHidden();
  });
});
