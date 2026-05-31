/**
 * E2E tests for Partner Attendance Dashboard
 * Story 8.1: AC1, AC2, AC4
 *
 * Runs in the 'partner' Playwright project using .playwright-auth-partner.json storage state.
 * Requires PARTNER_AUTH_TOKEN env var (set via make setup-test-users / run-playwright-tests.sh).
 *
 * Read-only + fully MOCKED (no real backend data created) → tagged `@gate` only. The
 * dashboard exposes a partner's OWN company analytics; there is no safe deterministic
 * mutation here, so the slice's per-deploy `@smoke` lives in partner-create-edit instead.
 *
 * Reality (rewrite-to-reality, slice 11, plan §C):
 * - The dashboard renders TWO Recharts `ComposedChart`s, NOT a `<table>`. The old
 *   `attendance-table` / `tbody tr` assertions were FICTIONAL — replaced by the real
 *   `kpi-attendance-rate` / `kpi-cost-per-attendee` cards + the `chart-*` section testids.
 * - The old AC6 "403 for another company" test MOCKED the very 403 it asserted (it could
 *   not exercise the real backend authz: playwright.config injects the ORGANIZER bearer via
 *   global `extraHTTPHeaders` for ALL projects, so a real `page.request` here runs AS
 *   ORGANIZER — which CAN read any company — not 403). Deleted; partner cross-company
 *   analytics authz is an API-layer concern owned by Bruno's partner collection.
 *
 * Run: cd web-frontend && PARTNER_AUTH_TOKEN=$(jq -r .idToken ~/.batbern/staging-partner.json) \
 *   npx playwright test --project=partner e2e/partner/analytics-dashboard.spec.ts
 */

import { test, expect } from '@playwright/test';
import { BASE_URL } from '../../playwright.config';

const ANALYTICS_URL = `${BASE_URL}/partners/analytics`;

// Deterministic dashboard payload: 18 company / 180 total attendees → 10.0% rate.
const DASHBOARD_PAYLOAD = {
  attendanceSummary: [
    {
      eventCode: 'BATbern57',
      eventTitle: 'BATbern 57',
      eventDate: '2024-06-01T00:00:00Z',
      totalAttendees: 100,
      companyAttendees: 10,
    },
    {
      eventCode: 'BATbern56',
      eventTitle: 'BATbern 56',
      eventDate: '2023-06-01T00:00:00Z',
      totalAttendees: 80,
      companyAttendees: 8,
    },
  ],
  costPerAttendee: 555.56,
};

test.describe('Partner Attendance Dashboard @gate', () => {
  test.beforeEach(async ({ page }) => {
    // Resolve a companyName for the partner user. AuthContext calls GET /partners/me when the
    // JWT carries no company (the local-dev partner user has none → the page would otherwise
    // render the `no-company-linked-alert` instead of the dashboard). Mocking it makes the
    // spec self-contained on dev AND staging.
    await page.route('**/api/v1/partners/me', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ companyName: 'GoogleZH' }),
      });
    });

    // Force EN so LanguageSync doesn't flip to the partner user's backend German preference.
    await page.route('**/api/v1/users/me*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: 'test-partner',
          email: 'partner@example.com',
          companyName: 'GoogleZH',
          preferences: { language: 'en' },
        }),
      });
    });

    // Mock the analytics dashboard API so the spec is deterministic and independent of
    // whatever events the partner's real company has historically attended on staging.
    await page.route('**/api/v1/partners/*/analytics/dashboard*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(DASHBOARD_PAYLOAD),
      });
    });

    await page.goto(ANALYTICS_URL);
  });

  // ─── AC1: Dashboard + KPI cards ─────────────────────────────────────────────

  test('should_renderKpiCards_whenDashboardLoads (AC1)', async ({ page }) => {
    await expect(page.getByTestId('attendance-dashboard')).toBeVisible({ timeout: 15000 });

    // 18 company / 180 total = 10.0% ; costPerAttendee 555.56 (both derived from the mock).
    await expect(page.getByTestId('kpi-attendance-rate')).toContainText('10.0%');
    await expect(page.getByTestId('kpi-cost-per-attendee')).toContainText('555.56');
  });

  // ─── AC1: Both charts render ────────────────────────────────────────────────

  test('should_renderBothCharts_whenDataPresent (AC1)', async ({ page }) => {
    await expect(page.getByTestId('attendance-dashboard')).toBeVisible({ timeout: 15000 });

    await expect(page.getByTestId('chart-attendance-per-event')).toBeVisible();
    await expect(page.getByTestId('chart-yoy-headcount')).toBeVisible();
  });

  // ─── AC2: Range toggle (default Last 5 Years, switch to All History) ─────────

  test('should_defaultToLast5Years_andSwitchToAllHistory (AC2)', async ({ page }) => {
    await expect(page.getByTestId('range-toggle')).toBeVisible({ timeout: 15000 });
    await expect(page.getByTestId('range-5years')).toBeVisible();
    await expect(page.getByTestId('range-allhistory')).toBeVisible();

    // Switching range re-queries the (mocked) API; the dashboard stays rendered.
    await page.getByTestId('range-allhistory').click();
    await expect(page.getByTestId('attendance-dashboard')).toBeVisible();
    await expect(page.getByTestId('chart-attendance-per-event')).toBeVisible();
  });

  // ─── AC4: XLSX export download ──────────────────────────────────────────────

  test('should_triggerXlsxDownload_whenExportClicked (AC4)', async ({ page }) => {
    await expect(page.getByTestId('export-button')).toBeVisible({ timeout: 15000 });

    // Mock the export endpoint to return minimal valid XLSX bytes (PKzip header).
    await page.route('**/api/v1/partners/*/analytics/export*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        headers: {
          'Content-Disposition': 'attachment; filename="attendance-export.xlsx"',
        },
        body: Buffer.from('PK\x03\x04', 'binary'),
      });
    });

    const downloadPromise = page.waitForEvent('download');
    await page.getByTestId('export-button').click();

    const download = await downloadPromise;
    expect(download.suggestedFilename()).toContain('.xlsx');
  });
});
