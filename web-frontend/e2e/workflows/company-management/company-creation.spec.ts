/**
 * E2E: Company Creation — slice 2 / companies (plan §C)
 * docs/plans/playwright-staging-hardening.md
 *
 * Rewritten 2026-05-30 to reality + the quality bar (testid-only locators, factory data,
 * mandatory cleanup, no empty tests). Story 1.14 / 2.5.1.
 *
 * Reality check (verified in CompanyManagementScreen.tsx + CompanyForm.tsx):
 *   • Creation is a MODAL (CompanyForm) opened from `create-company-button` on
 *     /organizer/companies — NOT a `/companies/create` route. The four prior `test.skip`s
 *     navigated to that non-existent route and used `input[name=…]` selectors → DELETED.
 *   • The name field enforces `^[A-Za-z0-9]+$` (alphanumeric, no spaces/hyphens). The old
 *     `should_createCompany` was skipped ("dialog not closing") precisely because its
 *     `TestCompany-${Date.now()}` name has a hyphen → validation blocked submit → the dialog
 *     never closed. The canonical `factory.companyName()` (`BRUNOTESTCO<ts>`, pure
 *     alphanumeric) passes validation AND is swept by `BRUNOTESTCO%`, so the happy path now
 *     works and cleans up. It is this slice's mutating `@smoke`.
 *   • On a successful create the mutation resolves and CompanyForm calls `onClose()` — the
 *     dialog closing is therefore an exact success signal (a failed create throws → apiError
 *     Alert shown → dialog stays open). No fixed sleeps.
 *
 * API-layer coverage (POST/GET/400/404 contract) lives in
 * api-integration/companies-api-integration.spec.ts (the dedicated API spec, also slice 2)
 * + the Bruno companies collection — the prior in-file "Company Creation - API Endpoints"
 * group duplicated it with ad-hoc `Date.now()` data + name-based deletes, so it was removed.
 * The EventBridge and latency `test.skip`s asserted non-E2E concerns (async events,
 * perf benchmarking) → DELETED.
 *
 * Prod-safety (plan risk #1): the `@smoke` creates a real CUMS `companies` row on staging
 * (= prod). Teardown is explicit `cleanupById(token, 'companies', name)` in afterEach, with
 * the canonical `BRUNOTESTCO%` global-teardown sweep as backstop. This retires the
 * `"E2E Test Company"` / `TestCompany-…` non-canonical residue the Bruno audit found.
 */

import { test, expect } from '@playwright/test';
import * as factory from '../../helpers/test-data-factory';
import { readOrganizerToken } from '../../helpers/event-fixture';
import { cleanupById } from '../../helpers/test-fixtures-cleanup';

test.describe('Company Creation', { tag: '@gate' }, () => {
  let token: string;
  // Names created via the UI in this run — explicit-deleted in afterEach (sweep backstop).
  const createdNames: string[] = [];

  test.beforeAll(() => {
    token = readOrganizerToken();
  });

  test.beforeEach(async ({ page }) => {
    await page.goto('/organizer/companies');
    await expect(page.getByTestId('create-company-button')).toBeVisible();
  });

  test.afterEach(async () => {
    while (createdNames.length > 0) {
      const name = createdNames.pop();
      if (name) {
        await cleanupById(token, 'companies', name);
      }
    }
  });

  test('should_displayCompanyCreationForm_when_openCreateModal', async ({ page }) => {
    await page.getByTestId('create-company-button').click();

    await expect(page.getByTestId('company-form-dialog')).toBeVisible();
    await expect(page.getByTestId('company-name-field')).toBeVisible();
    await expect(page.getByTestId('company-display-name-field')).toBeVisible();
    await expect(page.getByTestId('company-swiss-uid-field')).toBeVisible();
    await expect(page.getByTestId('company-website-field')).toBeVisible();
    await expect(page.getByTestId('company-industry-field')).toBeVisible();
    await expect(page.getByTestId('company-description-field')).toBeVisible();
    await expect(page.getByTestId('submit-company-button')).toBeVisible();
  });

  test(
    'should_createCompany_when_validDataProvided',
    { tag: ['@smoke', '@gate'] },
    async ({ page }) => {
      const name = factory.companyName(); // BRUNOTESTCO<ts> — alphanumeric, swept
      createdNames.push(name); // register for cleanup before the network call

      await page.getByTestId('create-company-button').click();
      await expect(page.getByTestId('company-form-dialog')).toBeVisible();

      await page.getByTestId('company-name-field').fill(name);
      await page.getByTestId('submit-company-button').click();

      // Success ⇔ the create mutation resolved and CompanyForm called onClose() (a failed
      // create throws → an apiError Alert renders → the dialog stays open). So the dialog
      // closing IS the persistence proof; afterEach then explicit-deletes the real row
      // (cleanupById → 204 confirms it existed). No search-result assertion: the
      // `company-search-input` testid sits on the MUI FormControl wrapper, not the <input>,
      // and a search round-trip adds debounce/cache timing flake for no extra signal.
      await expect(page.getByTestId('company-form-dialog')).toBeHidden({ timeout: 15_000 });
    }
  );
});
