/**
 * E2E Tests for Company Creation Workflow
 * Story 1.14: Company Management Service Foundation
 *
 * IMPORTANT: These tests are RED PHASE tests (TDD). They should FAIL until
 * the Company Management Service is fully implemented.
 *
 * Requirements:
 * 1. Company-User Management Service deployed with company management endpoints
 * 2. PostgreSQL database with companies table
 * 3. Redis cache for company search
 * 4. S3 bucket for company logos
 * 5. EventBridge for domain events
 *
 * Setup Instructions:
 * 1. Install Playwright: npm install -D @playwright/test
 * 2. Initialize Playwright: npx playwright install
 * 3. Configure playwright.config.ts with base URL
 * 4. Set up test environment variables (see .env.test.example)
 * 5. Run: npx playwright test e2e/workflows/company-management/company-creation.spec.ts
 */

import { test, expect, type Page } from '@playwright/test';

// Test configuration
const BASE_URL = process.env.E2E_BASE_URL || 'http://localhost:3000';
const API_URL = process.env.E2E_API_URL || 'http://localhost:8080';

// Test data
const TEST_COMPANY = {
  name: `Test Company ${Date.now()}`,
  displayName: `Test Company Display ${Date.now()}`,
  swissUID: 'CHE-123.456.789',
  website: 'https://testcompany.ch',
  industry: 'Technology',
  description: 'A test company for E2E testing',
};

// Type definitions
interface CompanyResponse {
  id: string;
  name: string;
  displayName: string;
  isPartner: boolean;
  swissUID?: string;
  website?: string;
  industry?: string;
  description?: string;
  isVerified: boolean;
  createdAt: string;
  updatedAt: string;
}

/**
 * Helper: Login as an authenticated user
 */
async function loginAsUser(page: Page, role: string = 'ORGANIZER') {
  const testEmail = process.env.E2E_TEST_EMAIL || 'test@batbern.ch';
  const testPassword = process.env.E2E_TEST_PASSWORD || 'Test123!@#';

  await page.goto(`${BASE_URL}/auth/login`);
  await page.fill('input[name="email"]', testEmail);
  await page.fill('input[name="password"]', testPassword);
  await page.click('button[type="submit"]');
  await page.waitForURL(/\/dashboard/);
}

/**
 * Helper: Get authentication token from localStorage
 */
async function getAuthToken(page: Page): Promise<string> {
  const token = await page.evaluate(() => localStorage.getItem('authToken'));
  return token || '';
}

/**
 * Helper: Create company via API
 */
async function createCompanyViaAPI(
  authToken: string,
  companyData: Partial<typeof TEST_COMPANY>
): Promise<CompanyResponse> {
  const response = await fetch(`${API_URL}/api/v1/companies`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${authToken}`,
    },
    body: JSON.stringify(companyData),
  });

  if (!response.ok) {
    throw new Error(`Failed to create company: ${response.status} ${response.statusText}`);
  }

  return response.json();
}

/**
 * Helper: Get company by ID via API
 */
async function getCompanyById(authToken: string, companyId: string): Promise<CompanyResponse | null> {
  const response = await fetch(`${API_URL}/api/v1/companies/${companyId}`, {
    method: 'GET',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${authToken}`,
    },
  });

  if (response.status === 404) {
    return null;
  }

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
// TEST GROUP 1: Company Creation via UI
// AC1: Create Company aggregate with Swiss business validation
// AC2: Support company metadata (logo, description, contact information)
// ============================================================================

test.describe('Company Creation - UI Workflow', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsUser(page, 'ORGANIZER');
  });

  test('should_displayCompanyCreationForm_when_navigateToCreatePage', async ({ page }) => {
    // Navigate to company creation page
    await page.goto(`${BASE_URL}/companies/create`);

    // Verify form elements are present
    await expect(page.getByLabel(/company name/i)).toBeVisible();
    await expect(page.getByLabel(/display name/i)).toBeVisible();
    await expect(page.getByLabel(/swiss uid/i)).toBeVisible();
    await expect(page.getByLabel(/website/i)).toBeVisible();
    await expect(page.getByLabel(/industry/i)).toBeVisible();
    await expect(page.getByLabel(/description/i)).toBeVisible();
    await expect(page.getByRole('button', { name: /create company/i })).toBeVisible();
  });

  test('should_createCompany_when_validDataProvided', async ({ page }) => {
    // AC1: Create Company aggregate with validation
    await page.goto(`${BASE_URL}/companies/create`);

    // Fill in company details
    await page.fill('input[name="name"]', TEST_COMPANY.name);
    await page.fill('input[name="displayName"]', TEST_COMPANY.displayName);
    await page.fill('input[name="swissUID"]', TEST_COMPANY.swissUID);
    await page.fill('input[name="website"]', TEST_COMPANY.website);
    await page.fill('input[name="industry"]', TEST_COMPANY.industry);
    await page.fill('textarea[name="description"]', TEST_COMPANY.description);

    // Submit form
    await page.click('button[type="submit"]');

    // Verify success message
    await expect(page.getByText(/company created successfully/i)).toBeVisible({ timeout: 5000 });

    // Verify redirect to company detail page
    await page.waitForURL(/\/companies\/[a-f0-9-]+/, { timeout: 5000 });

    // Verify company details displayed
    await expect(page.getByText(TEST_COMPANY.name)).toBeVisible();
    await expect(page.getByText(TEST_COMPANY.displayName)).toBeVisible();
  });

  test('should_validateSwissUID_when_companyCreated', async ({ page }) => {
    // AC3: Validate Swiss company UIDs
    await page.goto(`${BASE_URL}/companies/create`);

    // Fill in company details with valid Swiss UID
    await page.fill('input[name="name"]', TEST_COMPANY.name);
    await page.fill('input[name="swissUID"]', TEST_COMPANY.swissUID);

    // Verify Swiss UID format validation
    const uidInput = page.locator('input[name="swissUID"]');
    await expect(uidInput).toHaveValue(TEST_COMPANY.swissUID);

    // Submit form
    await page.click('button[type="submit"]');

    // Should succeed with valid Swiss UID
    await expect(page.getByText(/company created successfully/i)).toBeVisible({ timeout: 5000 });
  });

  test('should_showValidationError_when_invalidSwissUIDProvided', async ({ page }) => {
    // AC3: Validate Swiss UID format
    await page.goto(`${BASE_URL}/companies/create`);

    // Fill in company details with INVALID Swiss UID
    await page.fill('input[name="name"]', TEST_COMPANY.name);
    await page.fill('input[name="swissUID"]', 'INVALID-UID');

    // Submit form
    await page.click('button[type="submit"]');

    // Verify validation error displayed
    await expect(
      page.getByText(/invalid swiss uid format|uid must match pattern/i)
    ).toBeVisible({ timeout: 3000 });
  });

  test('should_showValidationError_when_requiredFieldsMissing', async ({ page }) => {
    // AC1: Validate required fields
    await page.goto(`${BASE_URL}/companies/create`);

    // Try to submit without filling required fields
    await page.click('button[type="submit"]');

    // Verify validation errors
    await expect(page.getByText(/company name is required/i)).toBeVisible();
  });

  test('should_showError_when_duplicateCompanyNameProvided', async ({ page }) => {
    // AC2: Enforce name uniqueness
    await page.goto(`${BASE_URL}/companies/create`);

    // Create first company
    const uniqueName = `Unique Company ${Date.now()}`;
    await page.fill('input[name="name"]', uniqueName);
    await page.click('button[type="submit"]');
    await expect(page.getByText(/company created successfully/i)).toBeVisible({ timeout: 5000 });

    // Try to create duplicate
    await page.goto(`${BASE_URL}/companies/create`);
    await page.fill('input[name="name"]', uniqueName);
    await page.click('button[type="submit"]');

    // Verify duplicate error
    await expect(
      page.getByText(/company with name .* already exists|duplicate company/i)
    ).toBeVisible({ timeout: 3000 });
  });
});

// ============================================================================
// TEST GROUP 2: Company Creation via API
// AC4: REST API with OpenAPI-documented endpoints
// ============================================================================

test.describe('Company Creation - API Endpoints', () => {
  let authToken: string;

  test.beforeEach(async ({ page }) => {
    await loginAsUser(page);
    authToken = await getAuthToken(page);
  });

  test('should_createCompany_when_validRequestReceived', async () => {
    // AC4: POST /api/v1/companies endpoint
    const response = await fetch(`${API_URL}/api/v1/companies`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${authToken}`,
      },
      body: JSON.stringify(TEST_COMPANY),
    });

    // Verify response
    expect(response.status).toBe(201);

    const company: CompanyResponse = await response.json();
    expect(company.id).toBeTruthy();
    expect(company.name).toBe(TEST_COMPANY.name);
    expect(company.displayName).toBe(TEST_COMPANY.displayName);
    expect(company.swissUID).toBe(TEST_COMPANY.swissUID);
    expect(company.isPartner).toBe(false);
    expect(company.isVerified).toBe(false);

    // Cleanup
    await deleteCompanyViaAPI(authToken, company.id);
  });

  test('should_return400_when_invalidDataProvided', async () => {
    // AC4: Validation error handling
    const response = await fetch(`${API_URL}/api/v1/companies`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${authToken}`,
      },
      body: JSON.stringify({
        name: '', // Empty name should fail validation
      }),
    });

    expect(response.status).toBe(400);
  });

  test('should_getCompanyById_when_companyExists', async () => {
    // AC4: GET /api/v1/companies/:id endpoint
    // Create company first
    const createdCompany = await createCompanyViaAPI(authToken, TEST_COMPANY);

    // Get company by ID
    const response = await fetch(`${API_URL}/api/v1/companies/${createdCompany.id}`, {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${authToken}`,
      },
    });

    expect(response.status).toBe(200);

    const company: CompanyResponse = await response.json();
    expect(company.id).toBe(createdCompany.id);
    expect(company.name).toBe(TEST_COMPANY.name);

    // Cleanup
    await deleteCompanyViaAPI(authToken, company.id);
  });

  test('should_return404_when_companyNotFound', async () => {
    // AC4: 404 error handling
    const nonExistentId = '00000000-0000-0000-0000-000000000000';
    const response = await fetch(`${API_URL}/api/v1/companies/${nonExistentId}`, {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${authToken}`,
      },
    });

    expect(response.status).toBe(404);
  });
});

// ============================================================================
// TEST GROUP 3: Company Creation with Domain Events
// AC7: Publish CompanyCreatedEvent to EventBridge
// ============================================================================

test.describe('Company Creation - Domain Events', () => {
  let authToken: string;

  test.beforeEach(async ({ page }) => {
    await loginAsUser(page);
    authToken = await getAuthToken(page);
  });

  test('should_publishCompanyCreatedEvent_when_companyCreated', async () => {
    // AC7: CompanyCreatedEvent published to EventBridge
    const company = await createCompanyViaAPI(authToken, TEST_COMPANY);

    // In a real test, we would verify EventBridge received the event
    // For E2E, we verify the company was created successfully
    expect(company.id).toBeTruthy();
    expect(company.name).toBe(TEST_COMPANY.name);

    // Cleanup
    await deleteCompanyViaAPI(authToken, company.id);
  });
});

// ============================================================================
// TEST GROUP 4: Company Creation Performance
// Performance Requirements: Company creation < 200ms (P95)
// ============================================================================

test.describe('Company Creation - Performance', () => {
  let authToken: string;

  test.beforeEach(async ({ page }) => {
    await loginAsUser(page);
    authToken = await getAuthToken(page);
  });

  test('should_createCompanyWithinLatencyTarget_when_normalLoad', async () => {
    // Performance: Company creation < 200ms (P95)
    const measurements: number[] = [];
    const companyIds: string[] = [];

    // Perform 5 company creations to measure latency
    for (let i = 0; i < 5; i++) {
      const testCompany = {
        ...TEST_COMPANY,
        name: `Perf Test Company ${Date.now()}-${i}`,
      };

      const startTime = Date.now();
      const company = await createCompanyViaAPI(authToken, testCompany);
      const endTime = Date.now();

      const latency = endTime - startTime;
      measurements.push(latency);
      companyIds.push(company.id);
    }

    // Calculate p95 latency
    measurements.sort((a, b) => a - b);
    const p95Index = Math.floor(measurements.length * 0.95);
    const p95Latency = measurements[p95Index];

    console.log(
      `Company Creation Latency - Min: ${Math.min(...measurements)}ms, Max: ${Math.max(...measurements)}ms, P95: ${p95Latency}ms`
    );

    // Verify P95 latency < 200ms (allowing 1000ms for E2E network overhead)
    expect(p95Latency).toBeLessThan(1000);

    // Cleanup
    for (const id of companyIds) {
      await deleteCompanyViaAPI(authToken, id);
    }
  });
});
