/**
 * E2E Tests for Partner Create/Edit Workflow
 * Story 2.8.3: Partner Create/Edit Modal
 *
 * Test Scenarios:
 * - Create partnership workflow (AC1, AC3, AC4, AC5, AC6, AC8)
 * - Edit partnership workflow (AC2)
 * - Form validation (AC7)
 * - Company autocomplete (AC3)
 *
 * Requirements:
 * 1. Partner Coordination Service deployed with partner CRUD endpoints
 * 2. Company Management Service for company autocomplete
 * 3. PostgreSQL database with partners table
 * 4. API Gateway for authentication
 *
 * Setup Instructions:
 * 1. Ensure services are running: make dev-native-up
 * 2. Run: npx playwright test e2e/workflows/partner-management/partner-create-edit.spec.ts
 */

import { test, expect, type Page } from '@playwright/test';

// Test configuration
const BASE_URL = process.env.E2E_BASE_URL || 'http://localhost:8100';
const API_URL = process.env.E2E_API_URL || 'http://localhost:8080';

// Test data
const TEST_PARTNER = {
  companyName: `test-partner-${Date.now()}`,
  partnershipLevel: 'GOLD',
  partnershipStartDate: new Date().toISOString().split('T')[0],
};

const TEST_COMPANY = {
  name: `test-company-${Date.now()}`,
  displayName: 'Test Partner Company',
  industry: 'Technology',
};

// Type definitions
interface PartnerResponse {
  companyName: string;
  companyDisplayName: string;
  companyLogoUrl: string | null;
  partnershipLevel: 'BRONZE' | 'SILVER' | 'GOLD' | 'PLATINUM' | 'STRATEGIC';
  partnershipStartDate: string;
  partnershipEndDate: string | null;
}

/**
 * Helper: Login as organizer user
 */
async function loginAsOrganizer(page: Page) {
  const testEmail = process.env.E2E_ORGANIZER_EMAIL || 'organizer@batbern.ch';
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
 * Helper: Create company via API for testing
 */
async function createCompanyViaAPI(
  authToken: string,
  companyData: typeof TEST_COMPANY
): Promise<void> {
  const response = await fetch(`${API_URL}/companies`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${authToken}`,
    },
    body: JSON.stringify(companyData),
  });

  if (!response.ok) {
    throw new Error(`Failed to create company: ${response.statusText}`);
  }
}

/**
 * Helper: Delete partner via API for cleanup
 */
async function deletePartnerViaAPI(authToken: string, companyName: string): Promise<void> {
  await fetch(`${API_URL}/partners/${companyName}`, {
    method: 'DELETE',
    headers: {
      Authorization: `Bearer ${authToken}`,
    },
  });
}

/**
 * Helper: Delete company via API for cleanup
 */
async function deleteCompanyViaAPI(authToken: string, companyName: string): Promise<void> {
  await fetch(`${API_URL}/companies/${companyName}`, {
    method: 'DELETE',
    headers: {
      Authorization: `Bearer ${authToken}`,
    },
  });
}

test.describe('Partner Create/Edit Modal - E2E Tests', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/organizer/events');
  });

  test('AC1, AC8: Create partnership workflow - full flow', async ({ page }) => {
    // Setup: Create a test company
    const authToken = await getAuthToken(page);
    await createCompanyViaAPI(authToken, TEST_COMPANY);

    try {
      // Navigate to Partner Directory
      await page.goto(`${BASE_URL}/organizer/partners`);
      await expect(page.locator('h1')).toContainText('Partner Directory');

      // AC1: Click [+ Add Partner] button to open create modal
      await page.click('button:has-text("Add Partner")');

      // Verify modal opened with create title
      await expect(page.locator('h2')).toContainText('Create Partnership');

      // AC3: Company Autocomplete - Search for company
      const companyInput = page.locator('input[aria-label*="Company"]');
      await companyInput.fill(TEST_COMPANY.displayName);

      // Wait for autocomplete results
      await page.waitForSelector(`text=${TEST_COMPANY.displayName}`);
      await page.click(`text=${TEST_COMPANY.displayName}`);

      // AC4: Partnership Tier Dropdown - Select tier
      await page.click('label:has-text("Partnership Tier")');
      await page.click('li:has-text("🥇 Gold")');

      // AC5: Partnership Date Pickers - Set start date (default is today)
      const startDateInput = page.locator('input[aria-label*="Start Date"]');
      await expect(startDateInput).toHaveValue(/.+/); // Should have default value (today)

      // AC6: Tier Benefits Preview - Verify benefits are displayed
      await expect(page.locator('text=Logo placement on website')).toBeVisible();
      await expect(page.locator('text=Newsletter mentions')).toBeVisible();
      await expect(page.locator('text=Priority event access')).toBeVisible();

      // AC8: Submit form to create partnership
      await page.click('button:has-text("Save")');

      // Verify success: Modal closed and redirected to partner detail
      await page.waitForURL(/\/partners\/.+/);
      await expect(page.locator('h1')).toContainText(TEST_COMPANY.displayName);

      // Verify partnership details displayed
      await expect(page.locator('text=🥇 Gold')).toBeVisible();
    } finally {
      // Cleanup: Delete created partner and company
      await deletePartnerViaAPI(authToken, TEST_COMPANY.name);
      await deleteCompanyViaAPI(authToken, TEST_COMPANY.name);
    }
  });

  test('AC2: Edit partnership workflow', async ({ page }) => {
    // Setup: Create company and partnership
    const authToken = await getAuthToken(page);
    await createCompanyViaAPI(authToken, TEST_COMPANY);

    // Create partnership via API
    await fetch(`${API_URL}/partners`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${authToken}`,
      },
      body: JSON.stringify({
        companyName: TEST_COMPANY.name,
        partnershipLevel: 'BRONZE',
        partnershipStartDate: TEST_PARTNER.partnershipStartDate,
      }),
    });

    try {
      // Navigate to partner detail page
      await page.goto(`${BASE_URL}/organizer/partners/${TEST_COMPANY.name}`);

      // AC2: Click [Edit Partner] button to open edit modal
      await page.click('button:has-text("Edit Partner")');

      // Verify modal opened with edit title
      await expect(page.locator('h2')).toContainText('Edit Partnership');

      // Verify company is displayed (read-only)
      await expect(page.locator('text=' + TEST_COMPANY.displayName)).toBeVisible();

      // Verify current tier is pre-selected
      await expect(page.locator('text=🥉 Bronze')).toBeVisible();

      // Change tier from Bronze to Platinum
      await page.click('label:has-text("Partnership Tier")');
      await page.click('li:has-text("💎 Platinum")');

      // Verify benefits updated for Platinum tier
      await expect(page.locator('text=Quarterly strategic meetings')).toBeVisible();

      // Submit form
      await page.click('button:has-text("Save")');

      // Verify success: Modal closed and tier updated
      await page.waitForTimeout(500); // Wait for modal to close
      await expect(page.locator('text=💎 Platinum')).toBeVisible();
    } finally {
      // Cleanup
      await deletePartnerViaAPI(authToken, TEST_COMPANY.name);
      await deleteCompanyViaAPI(authToken, TEST_COMPANY.name);
    }
  });

  test('AC7: Form validation - required fields', async ({ page }) => {
    await page.goto(`${BASE_URL}/organizer/partners`);

    // Open create modal
    await page.click('button:has-text("Add Partner")');

    // Try to submit without filling required fields
    await page.click('button:has-text("Save")');

    // Verify validation errors displayed
    await expect(page.locator('text=/Company.*required/i')).toBeVisible();
  });

  test('AC7: Form validation - date range', async ({ page }) => {
    const authToken = await getAuthToken(page);
    await createCompanyViaAPI(authToken, TEST_COMPANY);

    try {
      await page.goto(`${BASE_URL}/organizer/partners`);
      await page.click('button:has-text("Add Partner")');

      // Fill in company
      const companyInput = page.locator('input[aria-label*="Company"]');
      await companyInput.fill(TEST_COMPANY.displayName);
      await page.waitForSelector(`text=${TEST_COMPANY.displayName}`);
      await page.click(`text=${TEST_COMPANY.displayName}`);

      // Set end date before start date
      const startDateInput = page.locator('input[aria-label*="Start Date"]');
      await startDateInput.fill('2024-12-15');

      const endDateInput = page.locator('input[aria-label*="End Date"]');
      await endDateInput.fill('2024-12-10'); // Before start date

      // Try to submit
      await page.click('button:has-text("Save")');

      // Verify validation error
      await expect(page.locator('text=/End date.*after.*start date/i')).toBeVisible();
    } finally {
      await deleteCompanyViaAPI(authToken, TEST_COMPANY.name);
    }
  });

  test('AC3: Company autocomplete - search and selection', async ({ page }) => {
    const authToken = await getAuthToken(page);
    await createCompanyViaAPI(authToken, TEST_COMPANY);

    try {
      await page.goto(`${BASE_URL}/organizer/partners`);
      await page.click('button:has-text("Add Partner")');

      // Test autocomplete search
      const companyInput = page.locator('input[aria-label*="Company"]');
      await companyInput.fill('Test'); // Partial search

      // Wait for results
      await page.waitForSelector(`text=${TEST_COMPANY.displayName}`);

      // Verify company appears with logo and industry
      const companyOption = page.locator(`text=${TEST_COMPANY.displayName}`);
      await expect(companyOption).toBeVisible();

      // Select company
      await companyOption.click();

      // Verify company selected
      await expect(companyInput).toHaveValue(TEST_COMPANY.name);
    } finally {
      await deleteCompanyViaAPI(authToken, TEST_COMPANY.name);
    }
  });

  test('AC10: Modal UX - unsaved changes warning', async ({ page }) => {
    const authToken = await getAuthToken(page);
    await createCompanyViaAPI(authToken, TEST_COMPANY);

    try {
      await page.goto(`${BASE_URL}/organizer/partners`);
      await page.click('button:has-text("Add Partner")');

      // Make changes to form
      const companyInput = page.locator('input[aria-label*="Company"]');
      await companyInput.fill(TEST_COMPANY.displayName);
      await page.waitForSelector(`text=${TEST_COMPANY.displayName}`);
      await page.click(`text=${TEST_COMPANY.displayName}`);

      // Setup dialog listener
      page.on('dialog', async (dialog) => {
        expect(dialog.message()).toContain('unsaved changes');
        await dialog.dismiss(); // Cancel close
      });

      // Try to close modal with unsaved changes
      await page.click('button[aria-label="Close"]');

      // Verify modal is still open
      await expect(page.locator('h2:has-text("Create Partnership")')).toBeVisible();
    } finally {
      await deleteCompanyViaAPI(authToken, TEST_COMPANY.name);
    }
  });

  test('AC11: Accessibility - keyboard navigation', async ({ page }) => {
    await page.goto(`${BASE_URL}/organizer/partners`);
    await page.click('button:has-text("Add Partner")');

    // Test Tab navigation through form fields
    await page.keyboard.press('Tab'); // Focus on company input
    const companyInput = page.locator('input[aria-label*="Company"]');
    await expect(companyInput).toBeFocused();

    await page.keyboard.press('Tab'); // Focus on tier select
    const tierSelect = page.locator('label:has-text("Partnership Tier")');
    await expect(tierSelect).toBeVisible();

    // Test Escape to close modal
    await page.keyboard.press('Escape');
    await expect(page.locator('h2:has-text("Create Partnership")')).not.toBeVisible();
  });

  test('AC12: Date formatting based on locale', async ({ page }) => {
    await page.goto(`${BASE_URL}/organizer/partners`);
    await page.click('button:has-text("Add Partner")');

    // Verify date input exists and has locale-based format
    const startDateInput = page.locator('input[aria-label*="Start Date"]');
    await expect(startDateInput).toBeVisible();

    // Verify date is formatted (DD.MM.YYYY for German or MM/DD/YYYY for English)
    const dateValue = await startDateInput.inputValue();
    expect(dateValue).toMatch(/\d{2}[./]\d{2}[./]\d{4}/);
  });
});
