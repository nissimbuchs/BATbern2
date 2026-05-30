/**
 * E2E: Company Search — slice 2 / companies (plan §C)
 * docs/plans/playwright-staging-hardening.md
 *
 * Rewritten 2026-05-30 to reality + the quality bar. Story 1.14.
 *
 * What was here (all DELETED + logged, per the quality bar "no empty tests"):
 *   • 5 skipped UI tests (autocomplete / filter / no-results / clear / navigate-to-detail)
 *     asserting `getByPlaceholder(/search companies/i)` + `autocomplete-results` /
 *     `search-results` / `company-search-result` testids that the real CompanyFilters +
 *     CompanyList do not render, plus seeded `Acme/Beta/Gamma` data that never existed in the
 *     E2E environment. Speculative against an idealized DOM — not gate-worthy.
 *   • 4 API describe groups (search endpoint, Caffeine caching, performance, advanced query
 *     patterns). These were the SECOND big prod-residue source: each group's `beforeAll`
 *     created `Acme Corporation <ts>` / `Beta Technologies <ts>` / `Gamma Innovations <ts>`
 *     (non-canonical names, NOT swept by `BRUNOTESTCO%`) and "cleaned up" via
 *     `deleteCompanyViaAPI(authToken, company.id)` — but Story 1.16.2 keys companies by NAME
 *     and the create response carries no `id`, so the delete hit `/companies/undefined` and
 *     every run leaked three companies. The search-endpoint contract is covered by
 *     api-integration/companies-api-integration.spec.ts (kept + hardened) + the Bruno
 *     companies collection; caching/perf are integration/non-E2E concerns.
 *
 * What remains: the one real, read-only UI assertion — the search control renders on the
 * company management screen. No mutation → no cleanup needed → `@gate` (no `@smoke`; the
 * slice's mutating `@smoke` is company creation).
 */

import { test, expect } from '@playwright/test';

test.describe('Company Search', { tag: '@gate' }, () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/organizer/companies');
  });

  test('should_displaySearchInput_when_navigateToCompaniesPage', async ({ page }) => {
    await expect(page.getByTestId('company-search-input')).toBeVisible();
    await expect(page.getByTestId('company-list-view')).toBeVisible();
  });
});
