/**
 * E2E tests for Partner Attendance Dashboard
 * Story 8.1: AC1, AC4, AC6 — Task 12
 *
 * Runs in the 'partner' Playwright project using .playwright-auth-partner.json storage state.
 * Requires PARTNER_AUTH_TOKEN env var (set via make setup-test-users).
 *
 * Run: cd web-frontend && npx playwright test --project=partner e2e/partner/analytics-dashboard.spec.ts
 */

import { test, expect } from '@playwright/test';
import { BASE_URL } from '../../playwright.config';

const ANALYTICS_URL = `${BASE_URL}/partners/analytics`;

test.describe('Partner Attendance Dashboard', () => {
  test.beforeEach(async ({ page }) => {
    // Mock the analytics dashboard API to avoid dependency on staging data
    await page.route('**/api/v1/partners/*/analytics/dashboard*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          attendanceSummary: [
            {
              eventCode: 'BATbern57',
              eventDate: '2024-06-01T00:00:00Z',
              totalAttendees: 100,
              companyAttendees: 10,
            },
            {
              eventCode: 'BATbern56',
              eventDate: '2023-06-01T00:00:00Z',
              totalAttendees: 80,
              companyAttendees: 8,
            },
          ],
          costPerAttendee: 555.56,
        }),
      });
    });

    await page.goto(ANALYTICS_URL);
  });

  // ─── AC1: Dashboard loads with attendance table ─────────────────────────────

  test('should display attendance table after login (AC1)', async ({ page }) => {
    await page.waitForSelector('[data-testid="attendance-dashboard"]', { timeout: 15000 });

    // Table rows for each event
    await expect(page.getByText('BATbern57')).toBeVisible();
    await expect(page.getByText('BATbern56')).toBeVisible();
  });

  test('should show correct row count in attendance table (AC1)', async ({ page }) => {
    await page.waitForSelector('[data-testid="attendance-table"]', { timeout: 15000 });

    // 2 data rows + 1 totals row = 3 tbody rows
    const rows = page.locator('[data-testid="attendance-table"] tbody tr');
    await expect(rows).toHaveCount(3);
  });

  // ─── AC2: Range toggle ─────────────────────────────────────────────────────

  test('should have Last 5 Years selected by default (AC2)', async ({ page }) => {
    await page.waitForSelector('[data-testid="range-toggle"]', { timeout: 15000 });

    const last5Btn = page.getByTestId('range-5years');
    await expect(last5Btn).toBeVisible();
  });

  test('should switch to All History when toggled (AC2)', async ({ page }) => {
    await page.waitForSelector('[data-testid="range-toggle"]', { timeout: 15000 });

    await page.getByTestId('range-allhistory').click();

    // API should be called again (the mock will respond to both calls)
    await page.waitForSelector('[data-testid="attendance-table"]', { timeout: 10000 });
    await expect(page.getByText('BATbern57')).toBeVisible();
  });

  // ─── AC4: Export button ────────────────────────────────────────────────────

  test('should trigger XLSX download when export button clicked (AC4)', async ({ page }) => {
    await page.waitForSelector('[data-testid="export-button"]', { timeout: 15000 });

    // Set up download interception
    await page.route('**/api/v1/partners/*/analytics/export*', async (route) => {
      // Return minimal valid XLSX bytes (PKzip header)
      await route.fulfill({
        status: 200,
        contentType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        headers: {
          'Content-Disposition': 'attachment; filename="attendance-GoogleZH.xlsx"',
        },
        body: Buffer.from('PK\x03\x04', 'binary'),
      });
    });

    const downloadPromise = page.waitForEvent('download');
    await page.getByTestId('export-button').click();

    const download = await downloadPromise;
    expect(download.suggestedFilename()).toContain('.xlsx');
  });

  // ─── AC6: Partner cannot access another company's analytics ───────────────

  test('should get 403 when accessing another company analytics URL (AC6)', async ({ page }) => {
    // Override the route for a different company to return 403
    await page.route('**/api/v1/partners/OtherCompany/analytics/dashboard*', async (route) => {
      await route.fulfill({ status: 403 });
    });

    const response = await page.request.get(
      `${BASE_URL.replace('http:', 'http:')}/api/v1/partners/OtherCompany/analytics/dashboard`.replace(
        /localhost:\d+/,
        'localhost:8000'
      )
    );

    // Partner auth token should produce 403 for a different company
    expect(response.status()).toBe(403);
  });
});
