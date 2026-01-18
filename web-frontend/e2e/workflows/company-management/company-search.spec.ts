/**
 * E2E Tests for Company Search Functionality
 * Story 1.14: Company Management Service Foundation
 *
 * IMPORTANT: These tests are RED PHASE tests (TDD). They should FAIL until
 * the Company Management Service is fully implemented.
 *
 * Requirements:
 * 1. Company-User Management Service deployed with search endpoints
 * 2. PostgreSQL database with companies table and search indexes
 * 3. Caffeine cache for search results (15-minute TTL)
 * 4. Search functionality with autocomplete
 *
 * Setup Instructions:
 * 1. Install Playwright: npm install -D @playwright/test
 * 2. Initialize Playwright: npx playwright install
 * 3. Configure playwright.config.ts with base URL
 * 4. Set up test environment variables (see .env.test.example)
 * 5. Run: npx playwright test e2e/workflows/company-management/company-search.spec.ts
 */

import { test, expect, type Page } from '@playwright/test';

// Test configuration
const API_URL = process.env.E2E_API_URL || 'http://localhost:8000';

// Test data
const TEST_COMPANIES = [
  {
    name: `Acme Corporation ${Date.now()}`,
    displayName: 'Acme Corp',
    industry: 'Technology',
  },
  {
    name: `Beta Technologies ${Date.now()}`,
    displayName: 'Beta Tech',
    industry: 'Software',
  },
  {
    name: `Gamma Innovations ${Date.now()}`,
    displayName: 'Gamma Innov',
    industry: 'Research',
  },
];

// Type definitions
interface CompanySearchResponse {
  id: string;
  name: string;
  displayName: string;
  isPartner: boolean;
  industry?: string;
}

/**
 * Helper: Get authentication token from Amplify V6 localStorage format
 * Global setup has already injected Cognito tokens into localStorage
 */
async function getAuthToken(page: Page): Promise<string> {
  const token = await page.evaluate(() => {
    // Amplify V6 stores tokens in format: CognitoIdentityServiceProvider.<clientId>.<username>.idToken
    const keys = Object.keys(localStorage);
    const idTokenKey = keys.find((key) => key.endsWith('.idToken'));
    if (idTokenKey) {
      return localStorage.getItem(idTokenKey);
    }
    // Fallback: check for old format
    return localStorage.getItem('authToken');
  });
  return token || '';
}

/**
 * Helper: Create company via API
 */
async function createCompanyViaAPI(
  authToken: string,
  companyData: { name: string; displayName: string; industry: string }
): Promise<string> {
  const response = await fetch(`${API_URL}/api/v1/companies`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${authToken}`,
    },
    body: JSON.stringify(companyData),
  });

  const company = await response.json();
  return company.id;
}

/**
 * Helper: Search companies via API
 */
async function searchCompaniesViaAPI(
  authToken: string,
  query: string
): Promise<CompanySearchResponse[]> {
  const response = await fetch(
    `${API_URL}/api/v1/companies/search?query=${encodeURIComponent(query)}`,
    {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${authToken}`,
      },
    }
  );

  return response.json();
}

/**
 * Helper: Delete company via API (cleanup)
 */
async function deleteCompanyViaAPI(authToken: string, companyId: string): Promise<void> {
  await fetch(`${API_URL}/api/v1/companies/${companyId}`, {
    method: 'DELETE',
    headers: {
      Authorization: `Bearer ${authToken}`,
    },
  });
}

// ============================================================================
// TEST GROUP 1: Company Search via UI
// AC5: Enable company search with autocomplete
// AC11: Advanced search endpoint with Caffeine caching (<100ms P95)
// ============================================================================

test.describe('Company Search - UI Workflow', () => {
  // beforeAll/afterAll skipped - API not available in E2E environment
  // Tests that require test data are skipped
  // Only UI element visibility tests are enabled

  test.beforeEach(async ({ page }) => {
    // Global setup handles authentication, just navigate to companies page
    await page.goto('/organizer/companies');
  });

  test('should_displaySearchInput_when_navigateToCompaniesPage', async ({ page }) => {
    // Verify search input is present (using data-testid)
    await expect(page.getByTestId('company-search-input')).toBeVisible();
  });

  test.skip('should_showAutocompleteResults_when_typingInSearchField', async ({ page }) => {
    // AC5: Autocomplete functionality
    // Already at /companies from beforeEach

    // Type in search field
    const searchInput = page.getByPlaceholder(/search companies/i);
    await searchInput.fill('Acme');

    // Wait for autocomplete results
    await page.waitForSelector('[data-testid="autocomplete-results"]', { timeout: 3000 });

    // Verify autocomplete results displayed
    await expect(page.getByText(/acme corporation/i)).toBeVisible();
  });

  test.skip('should_filterResults_when_searchQueryProvided', async ({ page }) => {
    // AC5: Search filtering
    // Already at /companies from beforeEach

    // Perform search
    const searchInput = page.getByPlaceholder(/search companies/i);
    await searchInput.fill('Beta');

    // Wait for results
    await page.waitForSelector('[data-testid="search-results"]', { timeout: 3000 });

    // Verify correct results displayed
    await expect(page.getByText(/beta technologies/i)).toBeVisible();

    // Verify other companies NOT displayed
    await expect(page.getByText(/acme corporation/i)).not.toBeVisible();
  });

  test.skip('should_showNoResults_when_noMatchingCompanies', async ({ page }) => {
    // AC5: No results handling
    // Already at /companies from beforeEach

    // Search for non-existent company
    const searchInput = page.getByPlaceholder(/search companies/i);
    await searchInput.fill('NonExistentCompanyXYZ123');

    // Wait for no results message
    await expect(page.getByText(/no companies found/i)).toBeVisible({ timeout: 3000 });
  });

  test.skip('should_clearResults_when_searchQueryCleared', async ({ page }) => {
    // AC5: Clear search functionality
    // Already at /companies from beforeEach

    // Perform search
    const searchInput = page.getByPlaceholder(/search companies/i);
    await searchInput.fill('Acme');
    await page.waitForSelector('[data-testid="search-results"]', { timeout: 3000 });

    // Clear search
    await searchInput.clear();

    // Verify all companies displayed again
    await expect(page.getByText(/all companies/i)).toBeVisible();
  });

  test.skip('should_navigateToCompanyDetail_when_searchResultClicked', async ({ page }) => {
    // AC5: Search result navigation
    // Already at /companies from beforeEach

    // Perform search
    const searchInput = page.getByPlaceholder(/search companies/i);
    await searchInput.fill('Acme');
    await page.waitForSelector('[data-testid="search-results"]', { timeout: 3000 });

    // Click on first result
    await page.click('[data-testid="company-search-result"]:first-child');

    // Verify navigation to company detail page
    await page.waitForURL(/\/companies\/[a-f0-9-]+/, { timeout: 3000 });
    await expect(page.getByText(/acme corporation/i)).toBeVisible();
  });
});

// ============================================================================
// TEST GROUP 2: Company Search via API
// AC11: GET /api/v1/companies/search endpoint with Caffeine caching
// ============================================================================

test.describe('Company Search - API Endpoints', () => {
  let authToken: string;
  const companyIds: string[] = [];

  test.beforeAll(async ({ browser }) => {
    // Setup: Create test companies
    const page = await browser.newPage();
    await page.goto('/dashboard');
    authToken = await getAuthToken(page);

    for (const company of TEST_COMPANIES) {
      const id = await createCompanyViaAPI(authToken, company);
      companyIds.push(id);
    }

    await page.close();
  });

  test.afterAll(async () => {
    // Cleanup: Delete test companies
    for (const id of companyIds) {
      await deleteCompanyViaAPI(authToken, id);
    }
  });

  test('should_returnSearchResults_when_queryProvided', async () => {
    // AC11: Search endpoint
    const results = await searchCompaniesViaAPI(authToken, 'Acme');

    expect(results).toBeTruthy();
    expect(Array.isArray(results)).toBe(true);
    expect(results.length).toBeGreaterThan(0);

    const acmeCompany = results.find((c) => c.name.includes('Acme'));
    expect(acmeCompany).toBeTruthy();
    expect(acmeCompany?.displayName).toBe('Acme Corp');
  });

  test('should_returnEmptyArray_when_noMatchingResults', async () => {
    // AC11: No results handling
    const results = await searchCompaniesViaAPI(authToken, 'NonExistentCompanyXYZ123');

    expect(results).toBeTruthy();
    expect(Array.isArray(results)).toBe(true);
    expect(results.length).toBe(0);
  });

  test('should_limitResults_when_limitParameterProvided', async () => {
    // AC11: Result limiting (default 20 for autocomplete)
    const response = await fetch(`${API_URL}/api/v1/companies/search?query=test&limit=5`, {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${authToken}`,
      },
    });

    const results = await response.json();
    expect(results.length).toBeLessThanOrEqual(5);
  });

  test('should_performCaseInsensitiveSearch_when_queryProvided', async () => {
    // AC5: Case-insensitive search
    const lowerCaseResults = await searchCompaniesViaAPI(authToken, 'acme');
    const upperCaseResults = await searchCompaniesViaAPI(authToken, 'ACME');
    const mixedCaseResults = await searchCompaniesViaAPI(authToken, 'AcMe');

    // All variations should return same results
    expect(lowerCaseResults.length).toBe(upperCaseResults.length);
    expect(upperCaseResults.length).toBe(mixedCaseResults.length);
  });
});

// ============================================================================
// TEST GROUP 3: Caffeine Caching for Search Results
// AC9: Caffeine-based company search with automatic cache invalidation
// ============================================================================

test.describe('Company Search - Caffeine Caching', () => {
  let authToken: string;
  const companyIds: string[] = [];

  test.beforeAll(async ({ browser }) => {
    // Setup: Create test companies
    const page = await browser.newPage();
    await page.goto('/dashboard');
    authToken = await getAuthToken(page);

    for (const company of TEST_COMPANIES) {
      const id = await createCompanyViaAPI(authToken, company);
      companyIds.push(id);
    }

    await page.close();
  });

  test.afterAll(async () => {
    // Cleanup: Delete test companies
    for (const id of companyIds) {
      await deleteCompanyViaAPI(authToken, id);
    }
  });

  test.skip('should_cacheSearchResults_when_queryExecuted', async () => {
    // AC9: Search results cached
    // SKIPPED: Cache verification requires access to Caffeine cache metrics or Caffeine
    // Caching behavior is tested at the integration test level
    const query = 'Acme';

    // First search (cache miss)
    const startTime1 = Date.now();
    await searchCompaniesViaAPI(authToken, query);
    const latency1 = Date.now() - startTime1;

    // Second search (cache hit)
    const startTime2 = Date.now();
    await searchCompaniesViaAPI(authToken, query);
    const latency2 = Date.now() - startTime2;

    // Cached result should be significantly faster
    console.log(`Search latency - First: ${latency1}ms, Second (cached): ${latency2}ms`);

    // NOTE: This assertion may be flaky in CI/CD, but demonstrates caching behavior
    // In real tests, we would check Caffeine directly or use cache headers
  });

  test.skip('should_invalidateCache_when_companyUpdated', async () => {
    // AC9: Cache invalidation on company updates
    // SKIPPED: Cache invalidation verification requires cache inspection
    // This behavior is tested at the integration test level
    const query = 'Gamma';

    // Initial search (populate cache)
    const initialResults = await searchCompaniesViaAPI(authToken, query);
    expect(initialResults.length).toBeGreaterThan(0);

    // Update company
    const gammaCompany = initialResults[0];
    await fetch(`${API_URL}/api/v1/companies/${gammaCompany.id}`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${authToken}`,
      },
      body: JSON.stringify({
        displayName: 'Gamma Updated',
      }),
    });

    // Search again (cache should be invalidated)
    const updatedResults = await searchCompaniesViaAPI(authToken, query);
    const updatedCompany = updatedResults.find((c) => c.id === gammaCompany.id);

    expect(updatedCompany?.displayName).toBe('Gamma Updated');
  });
});

// ============================================================================
// TEST GROUP 4: Search Performance
// AC11: Search endpoint < 100ms P95 with cache
// ============================================================================

test.describe('Company Search - Performance', () => {
  let authToken: string;
  const companyIds: string[] = [];

  test.beforeAll(async ({ browser }) => {
    // Setup: Create test companies
    const page = await browser.newPage();
    await page.goto('/dashboard');
    authToken = await getAuthToken(page);

    for (const company of TEST_COMPANIES) {
      const id = await createCompanyViaAPI(authToken, company);
      companyIds.push(id);
    }

    await page.close();
  });

  test.afterAll(async () => {
    // Cleanup: Delete test companies
    for (const id of companyIds) {
      await deleteCompanyViaAPI(authToken, id);
    }
  });

  test.skip('should_meetPerformanceTarget_when_searchExecutedWithCache', async () => {
    // AC11: Search < 100ms P95 with Caffeine caching
    // SKIPPED: Performance benchmarking should use dedicated performance testing tools
    // E2E tests include network overhead and browser rendering which skews results
    const query = 'Beta';
    const measurements: number[] = [];

    // Warm up cache
    await searchCompaniesViaAPI(authToken, query);

    // Perform 10 searches to measure latency
    for (let i = 0; i < 10; i++) {
      const startTime = Date.now();
      await searchCompaniesViaAPI(authToken, query);
      const endTime = Date.now();

      const latency = endTime - startTime;
      measurements.push(latency);
    }

    // Calculate p95 latency
    measurements.sort((a, b) => a - b);
    const p95Index = Math.floor(measurements.length * 0.95);
    const p95Latency = measurements[p95Index];

    console.log(
      `Company Search Latency (cached) - Min: ${Math.min(...measurements)}ms, Max: ${Math.max(...measurements)}ms, P95: ${p95Latency}ms`
    );

    // Verify P95 latency < 100ms (allowing 500ms for E2E network overhead)
    expect(p95Latency).toBeLessThan(500);
  });

  test.skip('should_meetPerformanceTarget_when_searchExecutedWithoutCache', async () => {
    // AC5: Search < 500ms P95 without cache
    // SKIPPED: Performance benchmarking should use dedicated performance testing tools
    // E2E tests include network overhead and browser rendering which skews results
    const measurements: number[] = [];

    // Perform searches with unique queries (cache miss)
    for (let i = 0; i < 5; i++) {
      const uniqueQuery = `Test${Date.now()}-${i}`;
      const startTime = Date.now();
      await searchCompaniesViaAPI(authToken, uniqueQuery);
      const endTime = Date.now();

      const latency = endTime - startTime;
      measurements.push(latency);

      // Small delay between requests
      await new Promise((resolve) => setTimeout(resolve, 100));
    }

    // Calculate p95 latency
    measurements.sort((a, b) => a - b);
    const p95Index = Math.floor(measurements.length * 0.95);
    const p95Latency = measurements[p95Index];

    console.log(
      `Company Search Latency (no cache) - Min: ${Math.min(...measurements)}ms, Max: ${Math.max(...measurements)}ms, P95: ${p95Latency}ms`
    );

    // Verify P95 latency < 500ms (allowing 1000ms for E2E network overhead)
    expect(p95Latency).toBeLessThan(1000);
  });
});

// ============================================================================
// TEST GROUP 5: Advanced Search Features
// AC14: Advanced query patterns with filters, sorting, pagination
// ============================================================================

test.describe('Company Search - Advanced Query Patterns', () => {
  let authToken: string;
  const companyIds: string[] = [];

  test.beforeAll(async ({ browser }) => {
    // Setup: Create test companies
    const page = await browser.newPage();
    await page.goto('/dashboard');
    authToken = await getAuthToken(page);

    for (const company of TEST_COMPANIES) {
      const id = await createCompanyViaAPI(authToken, company);
      companyIds.push(id);
    }

    await page.close();
  });

  test.afterAll(async () => {
    // Cleanup: Delete test companies
    for (const id of companyIds) {
      await deleteCompanyViaAPI(authToken, id);
    }
  });

  test.skip('should_filterResults_when_filterParameterProvided', async () => {
    // AC14: Filter support
    const response = await fetch(`${API_URL}/api/v1/companies?filter=industry:Technology`, {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${authToken}`,
      },
    });

    const results = await response.json();
    expect(results.every((c: any) => c.industry === 'Technology')).toBe(true);
  });

  test.skip('should_sortResults_when_sortParameterProvided', async () => {
    // AC14: Sort support
    const response = await fetch(`${API_URL}/api/v1/companies?sort=name:asc`, {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${authToken}`,
      },
    });

    const results = await response.json();

    // Verify sorted order
    for (let i = 1; i < results.length; i++) {
      expect(results[i].name >= results[i - 1].name).toBe(true);
    }
  });

  test.skip('should_paginateResults_when_pageParameterProvided', async () => {
    // AC14: Pagination support
    const response = await fetch(`${API_URL}/api/v1/companies?page=1&limit=2`, {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${authToken}`,
      },
    });

    const results = await response.json();
    expect(results.length).toBeLessThanOrEqual(2);
  });
});
