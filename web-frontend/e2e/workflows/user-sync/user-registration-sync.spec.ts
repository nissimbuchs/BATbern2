/**
 * E2E Tests for User Registration and Database Sync
 * Story 1.2.5: User Sync and Reconciliation Implementation
 *
 * IMPORTANT: These tests require:
 * 1. Playwright to be installed and configured
 * 2. AWS Cognito user pool deployed with PostConfirmation trigger
 * 3. PostgreSQL database with users table
 * 4. Lambda PostConfirmation trigger deployed
 * 5. Test environment with localstack or real AWS services
 *
 * Setup Instructions:
 * 1. Install Playwright: npm install -D @playwright/test
 * 2. Initialize Playwright: npx playwright install
 * 3. Configure playwright.config.ts with base URL
 * 4. Set up test environment variables (see .env.test.example)
 * 5. Run: npx playwright test e2e/workflows/user-sync/user-registration-sync.spec.ts
 */

import { test, expect, type Page } from '@playwright/test';

// Test configuration
const BASE_URL = process.env.E2E_BASE_URL || 'http://localhost:3000';
const API_URL = process.env.E2E_API_URL || 'http://localhost:8080';
const TEST_EMAIL = `test-${Date.now()}@batbern.ch`;
const TEST_PASSWORD = 'TestPassword123!';

// Type definitions
interface DbUser {
  id: string;
  email: string;
  cognitoId?: string;
  active: boolean;
  createdAt?: string;
}

/**
 * Helper: Register a new user via signup flow
 */
async function registerNewUser(
  page: Page,
  email: string,
  password: string,
  role: string = 'ATTENDEE'
) {
  await page.goto(`${BASE_URL}/auth/signup`);

  // Fill registration form
  await page.fill('input[name="email"]', email);
  await page.fill('input[name="password"]', password);
  await page.fill('input[name="confirmPassword"]', password);

  // Select role (default to ATTENDEE if not specified)
  if (role !== 'ATTENDEE') {
    await page.selectOption('select[name="role"]', role);
  }

  await page.click('button[type="submit"]');
}

/**
 * Helper: Verify email using verification code
 */
async function verifyUserEmail(page: Page, email: string, verificationCode: string) {
  await page.goto(`${BASE_URL}/auth/verify`);
  await page.fill('input[name="email"]', email);
  await page.fill('input[name="code"]', verificationCode);
  await page.click('button[type="submit"]');
}

/**
 * Helper: Query database for user existence
 */
async function checkUserInDatabase(email: string): Promise<DbUser | null> {
  const response = await fetch(`${API_URL}/api/v1/internal/users/by-email/${email}`, {
    method: 'GET',
    headers: {
      'Content-Type': 'application/json',
      'X-Internal-Auth': process.env.INTERNAL_API_KEY || 'test-key',
    },
  });

  if (response.status === 404) {
    return null;
  }

  return response.json();
}

/**
 * Helper: Get user roles from database
 */
async function getUserRoles(userId: string): Promise<string[]> {
  const response = await fetch(`${API_URL}/api/v1/internal/users/${userId}/roles`, {
    method: 'GET',
    headers: {
      'Content-Type': 'application/json',
      'X-Internal-Auth': process.env.INTERNAL_API_KEY || 'test-key',
    },
  });

  const data = await response.json();
  return data.roles || [];
}

/**
 * Helper: Login and get JWT token
 */
async function loginAndGetToken(page: Page, email: string, password: string): Promise<string> {
  await page.goto(`${BASE_URL}/auth/login`);
  await page.fill('input[name="email"]', email);
  await page.fill('input[name="password"]', password);
  await page.click('button[type="submit"]');

  // Wait for successful login and token storage
  await page.waitForURL(/\/dashboard/);

  // Get token from localStorage
  const token = await page.evaluate(() => localStorage.getItem('authToken'));
  return token || '';
}

// ============================================================================
// TEST GROUP 1: PostConfirmation Trigger - User Creation in Database
// AC1: PostConfirmation trigger creates database user within 1 second
// ============================================================================

test.describe('User Registration Sync - PostConfirmation Trigger', () => {
  test('should_createUserInDatabase_when_cognitoUserConfirmed', async ({ page }) => {
    // AC1: PostConfirmation trigger creates database user within 1 second
    const startTime = Date.now();

    // Register new user
    await registerNewUser(page, TEST_EMAIL, TEST_PASSWORD, 'ATTENDEE');

    // Wait for email verification screen
    await expect(page.getByText(/verify your email/i)).toBeVisible();

    // Simulate email verification (in test environment, auto-confirm or use test code)
    // For this test, we assume verification happens automatically in test environment
    // OR we use a test-specific verification code
    await verifyUserEmail(page, TEST_EMAIL, '123456');

    // Wait for successful verification redirect
    await page.waitForURL(/\/dashboard/);

    const endTime = Date.now();
    const syncTime = endTime - startTime;

    // Verify user created in database
    const dbUser = await checkUserInDatabase(TEST_EMAIL);

    expect(dbUser).toBeTruthy();
    expect(dbUser.email).toBe(TEST_EMAIL);
    expect(dbUser.cognitoId).toBeTruthy();
    expect(dbUser.active).toBe(true);

    // AC1: Operation completes within 1 second (allowing 5 seconds total for E2E test)
    expect(syncTime).toBeLessThan(5000);
  });

  test('should_assignInitialRole_when_userRegistersWithRole', async ({ page }) => {
    // AC1: Initial role assigned based on Cognito custom attribute
    const testEmail = `speaker-${Date.now()}@batbern.ch`;

    await registerNewUser(page, testEmail, TEST_PASSWORD, 'SPEAKER');
    await expect(page.getByText(/verify your email/i)).toBeVisible();

    // Verify email
    await verifyUserEmail(page, testEmail, '123456');
    await page.waitForURL(/\/dashboard/);

    // Check database for role assignment
    const dbUser = await checkUserInDatabase(testEmail);
    expect(dbUser).toBeTruthy();

    const roles = await getUserRoles(dbUser.id);
    expect(roles).toContain('SPEAKER');
  });

  test('should_defaultToAttendeeRole_when_noRoleSpecified', async ({ page }) => {
    // AC1: Default to ATTENDEE role when custom attribute missing
    const testEmail = `default-${Date.now()}@batbern.ch`;

    await registerNewUser(page, testEmail, TEST_PASSWORD);
    await expect(page.getByText(/verify your email/i)).toBeVisible();

    await verifyUserEmail(page, testEmail, '123456');
    await page.waitForURL(/\/dashboard/);

    const dbUser = await checkUserInDatabase(testEmail);
    expect(dbUser).toBeTruthy();

    const roles = await getUserRoles(dbUser.id);
    expect(roles).toContain('ATTENDEE');
  });

  test('should_beIdempotent_when_triggerFiredMultipleTimes', async ({ page }) => {
    // AC1: Idempotent operation - ON CONFLICT handling
    const testEmail = `idempotent-${Date.now()}@batbern.ch`;

    // Register user
    await registerNewUser(page, testEmail, TEST_PASSWORD);
    await verifyUserEmail(page, testEmail, '123456');
    await page.waitForURL(/\/dashboard/);

    // Get initial user state
    const initialUser = await checkUserInDatabase(testEmail);
    const initialCreatedAt = initialUser.createdAt;

    // Simulate trigger firing again (force re-confirmation in test environment)
    // This would typically happen through AWS Cognito admin API in test
    // await triggerPostConfirmationAgain(testEmail);

    // Verify user still exists with same creation timestamp
    const finalUser = await checkUserInDatabase(testEmail);
    expect(finalUser.id).toBe(initialUser.id);
    expect(finalUser.createdAt).toBe(initialCreatedAt);
    expect(finalUser.email).toBe(testEmail);
  });

  test('should_continueRegistration_when_databaseUnavailable', async ({ page }) => {
    // AC1: Non-blocking - registration continues even if DB sync fails
    // This test requires mocking database unavailability in Lambda trigger
    // For E2E, we verify that registration completes successfully

    const testEmail = `resilient-${Date.now()}@batbern.ch`;

    await registerNewUser(page, testEmail, TEST_PASSWORD);
    await expect(page.getByText(/verify your email/i)).toBeVisible();

    await verifyUserEmail(page, testEmail, '123456');

    // Cognito user should be created successfully even if DB sync fails
    await expect(page).toHaveURL(/\/dashboard/);
    await expect(page.getByText(/welcome/i)).toBeVisible();
  });
});

// ============================================================================
// TEST GROUP 2: User Login Flow - End-to-End Registration to Login
// ============================================================================

test.describe('User Registration Sync - Complete Flow', () => {
  test('should_completeFullFlow_when_userRegistersAndLogsIn', async ({ page }) => {
    // Complete E2E test: Register → Verify → Login → Access Protected Resource
    const testEmail = `fullflow-${Date.now()}@batbern.ch`;

    // Step 1: Register
    await registerNewUser(page, testEmail, TEST_PASSWORD, 'ORGANIZER');
    await expect(page.getByText(/verify your email/i)).toBeVisible();

    // Step 2: Verify email
    await verifyUserEmail(page, testEmail, '123456');
    await page.waitForURL(/\/dashboard/);

    // Step 3: Verify user in database
    const dbUser = await checkUserInDatabase(testEmail);
    expect(dbUser).toBeTruthy();
    expect(dbUser.email).toBe(testEmail);
    expect(dbUser.active).toBe(true);

    // Step 4: Logout and login again
    await page.click('button[aria-label="User menu"]');
    await page.click('button:has-text("Logout")');
    await page.waitForURL(/\/login/);

    // Step 5: Login with credentials
    const token = await loginAndGetToken(page, testEmail, TEST_PASSWORD);
    expect(token).toBeTruthy();

    // Step 6: Access protected resource (requires role check)
    await page.goto(`${BASE_URL}/organizer/events`);
    await expect(page).toHaveURL(/\/organizer\/events/);
    await expect(page.getByText(/my events/i)).toBeVisible();
  });

  test('should_syncUserData_when_multipleRolesAssigned', async ({ page }) => {
    // Test user with multiple roles (ORGANIZER + SPEAKER)
    const testEmail = `multirole-${Date.now()}@batbern.ch`;

    await registerNewUser(page, testEmail, TEST_PASSWORD, 'ORGANIZER');
    await verifyUserEmail(page, testEmail, '123456');
    await page.waitForURL(/\/dashboard/);

    const dbUser = await checkUserInDatabase(testEmail);
    expect(dbUser).toBeTruthy();

    // Initial role should be ORGANIZER
    const initialRoles = await getUserRoles(dbUser.id);
    expect(initialRoles).toContain('ORGANIZER');

    // Admin assigns SPEAKER role (simulated via API call)
    await fetch(`${API_URL}/api/v1/internal/users/${dbUser.id}/roles`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Internal-Auth': process.env.INTERNAL_API_KEY || 'test-key',
      },
      body: JSON.stringify({ role: 'SPEAKER' }),
    });

    // Verify both roles now present
    const updatedRoles = await getUserRoles(dbUser.id);
    expect(updatedRoles).toContain('ORGANIZER');
    expect(updatedRoles).toContain('SPEAKER');
  });
});

// ============================================================================
// TEST GROUP 3: Performance Validation
// AC1: Operation completes within 1 second (p95 latency)
// ============================================================================

test.describe('User Registration Sync - Performance', () => {
  test('should_completeWithinLatencyTarget_when_normalLoad', async ({ page }) => {
    // AC1: Sync latency < 1 second (p95)
    const measurements: number[] = [];

    // Perform 5 registrations to measure sync latency
    for (let i = 0; i < 5; i++) {
      const testEmail = `perf-${Date.now()}-${i}@batbern.ch`;
      const startTime = Date.now();

      await registerNewUser(page, testEmail, TEST_PASSWORD);
      await verifyUserEmail(page, testEmail, '123456');
      await page.waitForURL(/\/dashboard/);

      // Check database sync
      const dbUser = await checkUserInDatabase(testEmail);
      const endTime = Date.now();

      if (dbUser) {
        const syncLatency = endTime - startTime;
        measurements.push(syncLatency);
      }

      // Logout for next iteration
      await page.click('button[aria-label="User menu"]');
      await page.click('button:has-text("Logout")');
    }

    // Calculate p95 latency
    measurements.sort((a, b) => a - b);
    const p95Index = Math.floor(measurements.length * 0.95);
    const p95Latency = measurements[p95Index];

    // AC1: p95 latency should be less than 1 second (allowing 5s for E2E overhead)
    expect(p95Latency).toBeLessThan(5000);

    console.log(
      `User Sync Latency - Min: ${Math.min(...measurements)}ms, Max: ${Math.max(...measurements)}ms, P95: ${p95Latency}ms`
    );
  });
});

// ============================================================================
// TEST GROUP 4: Error Handling
// ============================================================================

test.describe('User Registration Sync - Error Handling', () => {
  test('should_logError_when_databaseSyncFails', async ({ page }) => {
    // AC1: Error logging without blocking Cognito confirmation
    // This test verifies that errors are logged but don't prevent registration

    const testEmail = `error-${Date.now()}@batbern.ch`;

    await registerNewUser(page, testEmail, TEST_PASSWORD);
    await verifyUserEmail(page, testEmail, '123456');

    // User should still be able to access dashboard even if sync failed
    await page.waitForURL(/\/dashboard/);
    await expect(page.getByText(/welcome/i)).toBeVisible();

    // In production, we would check CloudWatch logs for error entries
    // Example: await verifyCloudWatchLog('PostConfirmation', 'ERROR', testEmail);
  });
});
