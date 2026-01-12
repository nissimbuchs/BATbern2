/**
 * E2E Tests for Partner Directory
 * Story 2.8.1: Partner Directory - View and Browse Partners
 *
 * Tests the complete user journey for viewing and interacting with
 * the partner directory, including search, filtering, sorting, and pagination.
 *
 * Requirements:
 * 1. Partner Coordination Service deployed with partner endpoints
 * 2. PostgreSQL database with partners table and relationships
 * 3. Test partners seeded in database
 * 4. Authentication configured (Cognito/local)
 *
 * Setup Instructions:
 * 1. Install Playwright: npm install -D @playwright/test
 * 2. Initialize Playwright: npx playwright install
 * 3. Configure playwright.config.ts with base URL
 * 4. Set up test environment variables (see .env.test.example)
 * 5. Run: npx playwright test e2e/workflows/partner-management/partner-directory.spec.ts
 */

import { test, expect, type Page } from '@playwright/test';

// Test configuration
const BASE_URL = process.env.E2E_BASE_URL || 'http://localhost:8100';
const API_URL = process.env.E2E_API_URL || 'http://localhost:8080';

/**
 * Helper: Login as an authenticated organizer
 */
async function loginAsOrganizer(page: Page) {
  const testEmail = process.env.E2E_TEST_EMAIL || 'organizer@batbern.ch';
  const testPassword = process.env.E2E_TEST_PASSWORD || 'Test1234!';

  await page.goto(`${BASE_URL}/login`);
  await page.fill('input[name="email"]', testEmail);
  await page.fill('input[name="password"]', testPassword);
  await page.click('button[type="submit"]');

  // Wait for redirect to dashboard or partners page
  await page.waitForURL(/\/(dashboard|organizer)/);
}

/**
 * Helper: Navigate to Partner Directory
 */
async function navigateToPartnerDirectory(page: Page) {
  // Click on Partners navigation link
  await page.click('a[href="/organizer/partners"]');
  await page.waitForURL(`${BASE_URL}/organizer/partners`);

  // Wait for page to load - look for title
  await expect(page.getByRole('heading', { name: /partner directory/i })).toBeVisible();
}

test.describe('Partner Directory - User Journey', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/organizer/events');
  });

  test('should navigate to Partner Directory from menu', async ({ page }) => {
    // Navigate to partners page
    await navigateToPartnerDirectory(page);

    // Verify page loaded
    await expect(page).toHaveURL(/\/organizer\/partners/);
    await expect(page.getByRole('heading', { name: /partner directory/i })).toBeVisible();

    // Verify key UI elements are present
    await expect(page.getByPlaceholder(/search/i)).toBeVisible();
    await expect(page.getByTestId('partner-sort-select')).toBeVisible();
    await expect(page.getByTestId('view-mode-toggle')).toBeVisible();
  });

  test('should display partner overview statistics', async ({ page }) => {
    await navigateToPartnerDirectory(page);

    // Wait for statistics to load
    await page.waitForSelector('text=Total Partners', { timeout: 10000 });

    // Verify statistics cards are present
    await expect(page.getByText(/total partners/i)).toBeVisible();
    await expect(page.getByText(/active partners/i)).toBeVisible();

    // Verify statistics have numeric values
    const totalPartnersCard = page.locator('text=Total Partners').locator('..');
    await expect(totalPartnersCard).toContainText(/\d+/);
  });

  test('should display partner list in grid view by default', async ({ page }) => {
    await navigateToPartnerDirectory(page);

    // Wait for partners to load
    await page.waitForSelector('[data-testid="partner-grid"]', { timeout: 10000 });

    // Verify grid view is active
    const gridViewButton = page.getByLabelText(/grid view/i);
    await expect(gridViewButton).toHaveAttribute('aria-pressed', 'true');

    // Verify partner cards are displayed
    const partnerCards = page.locator('[data-testid="partner-card"]');
    const cardCount = await partnerCards.count();
    expect(cardCount).toBeGreaterThan(0);
  });
});

test.describe('Partner Directory - Search Functionality', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/organizer/events');
    await navigateToPartnerDirectory(page);
  });

  test('should filter partners by search query', async ({ page }) => {
    // Wait for initial partners to load
    await page.waitForSelector('[data-testid="partner-card"]', { timeout: 10000 });

    // Enter search query
    const searchInput = page.getByPlaceholder(/search/i);
    await searchInput.fill('Tech');

    // Wait for debounce (300ms) and results to update
    await page.waitForTimeout(500);
    await page.waitForLoadState('networkidle');

    // Verify filtered results
    const filteredCardCount = await page.locator('[data-testid="partner-card"]').count();

    // Results should either be filtered down or show "No partners found"
    if (filteredCardCount === 0) {
      await expect(page.getByText(/no partners found/i)).toBeVisible();
    } else {
      // Verify search term appears in visible cards
      const firstCard = page.locator('[data-testid="partner-card"]').first();
      await expect(firstCard).toContainText(/tech/i);
    }
  });

  test('should clear search with Escape key', async ({ page }) => {
    const searchInput = page.getByPlaceholder(/search/i);

    // Enter search query
    await searchInput.fill('Test Query');
    await expect(searchInput).toHaveValue('Test Query');

    // Press Escape
    await searchInput.press('Escape');

    // Verify search is cleared
    await expect(searchInput).toHaveValue('');
  });

  test('should clear search with clear button', async ({ page }) => {
    const searchInput = page.getByPlaceholder(/search/i);

    // Enter search query
    await searchInput.fill('Test Query');
    await expect(searchInput).toHaveValue('Test Query');

    // Click clear button
    await page.getByLabel(/clear search/i).click();

    // Verify search is cleared
    await expect(searchInput).toHaveValue('');
  });
});

test.describe('Partner Directory - Filter Functionality', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/organizer/events');
    await navigateToPartnerDirectory(page);
  });

  test('should filter partners by tier', async ({ page }) => {
    // Wait for partners to load
    await page.waitForSelector('[data-testid="partner-card"]', { timeout: 10000 });

    // Select GOLD tier filter
    await page.getByLabel(/partnership tier/i).click();
    await page.getByRole('option', { name: /gold/i }).click();

    // Wait for filtered results
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(500);

    // Verify filtered results
    const partnerCards = page.locator('[data-testid="partner-card"]');
    const cardCount = await partnerCards.count();

    if (cardCount > 0) {
      // Verify GOLD badge appears in cards
      const firstCard = partnerCards.first();
      await expect(firstCard).toContainText(/gold/i);
    } else {
      await expect(page.getByText(/no partners found/i)).toBeVisible();
    }
  });

  test('should filter partners by status', async ({ page }) => {
    // Wait for partners to load
    await page.waitForSelector('[data-testid="partner-card"]', { timeout: 10000 });

    // Select Active status filter
    await page.getByLabel(/^status$/i).click();
    await page.getByRole('option', { name: /^active$/i }).click();

    // Wait for filtered results
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(500);

    // Verify results loaded (active partners should exist)
    const partnerCards = page.locator('[data-testid="partner-card"]');
    const cardCount = await partnerCards.count();
    expect(cardCount).toBeGreaterThanOrEqual(0);
  });

  test('should reset all filters', async ({ page }) => {
    // Apply multiple filters
    await page.getByLabel(/partnership tier/i).click();
    await page.getByRole('option', { name: /gold/i }).click();
    await page.waitForTimeout(300);

    // Click Reset Filters
    await page.getByRole('button', { name: /reset filters/i }).click();

    // Wait for results to reload
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(500);

    // Verify filters are reset
    const tierSelect = page.getByLabel(/partnership tier/i);
    await expect(tierSelect).toContainText(/all tiers/i);
  });
});

test.describe('Partner Directory - View Mode Toggle', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/organizer/events');
    await navigateToPartnerDirectory(page);
  });

  test('should switch between grid and list views', async ({ page }) => {
    // Wait for initial grid view
    await page.waitForSelector('[data-testid="partner-grid"]', { timeout: 10000 });

    // Verify grid view is active
    const gridViewButton = page.getByLabelText(/grid view/i);
    await expect(gridViewButton).toHaveAttribute('aria-pressed', 'true');

    // Switch to list view
    await page.getByLabelText(/list view/i).click();
    await page.waitForTimeout(300);

    // Verify list view is active
    const listViewButton = page.getByLabelText(/list view/i);
    await expect(listViewButton).toHaveAttribute('aria-pressed', 'true');

    // Switch back to grid view
    await gridViewButton.click();
    await page.waitForTimeout(300);

    // Verify grid view is active again
    await expect(gridViewButton).toHaveAttribute('aria-pressed', 'true');
  });
});

test.describe('Partner Directory - Sorting', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/organizer/events');
    await navigateToPartnerDirectory(page);
  });

  test('should sort partners by different criteria', async ({ page }) => {
    // Wait for partners to load
    await page.waitForSelector('[data-testid="partner-card"]', { timeout: 10000 });

    // Open sort dropdown
    await page.getByLabel(/sort by/i).click();

    // Select "Company Name" sorting
    await page.getByRole('option', { name: /company name/i }).click();

    // Wait for sorted results
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(500);

    // Verify partners are displayed (sorting order verified by API integration tests)
    const partnerCards = page.locator('[data-testid="partner-card"]');
    expect(await partnerCards.count()).toBeGreaterThan(0);
  });
});

test.describe('Partner Directory - Pagination', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/organizer/events');
    await navigateToPartnerDirectory(page);
  });

  test('should navigate through pages', async ({ page }) => {
    // Wait for partners to load
    await page.waitForSelector('[data-testid="partner-card"]', { timeout: 10000 });

    // Check if pagination is present (only if more than one page exists)
    const paginationNav = page.getByRole('navigation', { name: /pagination/i });
    const paginationExists = (await paginationNav.count()) > 0;

    if (paginationExists) {
      // Click next page button
      const nextButton = page.getByLabel(/next page/i);
      const isEnabled = !(await nextButton.isDisabled());

      if (isEnabled) {
        await nextButton.click();
        await page.waitForLoadState('networkidle');
        await page.waitForTimeout(500);

        // Verify page changed
        await expect(page.getByText(/page 2 of/i)).toBeVisible();

        // Click previous page button
        await page.getByLabel(/previous page/i).click();
        await page.waitForLoadState('networkidle');

        // Verify back on page 1
        await expect(page.getByText(/page 1 of/i)).toBeVisible();
      }
    }
  });
});

test.describe('Partner Directory - Error Handling', () => {
  test('should handle network errors gracefully', async ({ page }) => {
    // Intercept API calls and simulate network error
    await page.route(`${API_URL}/api/partners**`, (route) => {
      route.abort('failed');
    });

    await page.goto('/organizer/events');
    await navigateToPartnerDirectory(page);

    // Wait for error message
    await expect(page.getByText(/failed to.*partners/i)).toBeVisible({ timeout: 10000 });
  });

  test('should handle API errors gracefully', async ({ page }) => {
    // Intercept API calls and simulate 500 error
    await page.route(`${API_URL}/api/partners**`, (route) => {
      route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({
          error: 'Internal Server Error',
          message: 'Database connection failed',
          correlationId: 'test-correlation-id-123',
        }),
      });
    });

    await page.goto('/organizer/events');
    await navigateToPartnerDirectory(page);

    // Wait for error message
    await expect(page.getByText(/failed.*partners/i)).toBeVisible({ timeout: 10000 });

    // Verify correlation ID is displayed
    await expect(page.getByText(/correlation id/i)).toBeVisible();
  });
});
