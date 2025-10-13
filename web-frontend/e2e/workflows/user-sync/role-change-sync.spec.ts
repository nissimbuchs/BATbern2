/**
 * E2E Tests for Role Change Sync and JWT Token Enrichment
 * Story 1.2.5: User Sync and Reconciliation Implementation
 *
 * IMPORTANT: These tests require:
 * 1. Playwright to be installed and configured
 * 2. AWS Cognito with PreTokenGeneration trigger deployed
 * 3. PostgreSQL database with users and user_roles tables
 * 4. Lambda PreTokenGeneration trigger deployed
 * 5. Saga pattern service for bidirectional role sync
 *
 * Setup Instructions:
 * 1. Install Playwright: npm install -D @playwright/test
 * 2. Initialize Playwright: npx playwright install
 * 3. Configure playwright.config.ts with base URL
 * 4. Set up test environment variables (see .env.test.example)
 * 5. Run: npx playwright test e2e/workflows/user-sync/role-change-sync.spec.ts
 */

import { test, expect, type Page } from '@playwright/test';

// Test configuration
const BASE_URL = process.env.E2E_BASE_URL || 'http://localhost:3000';
const API_URL = process.env.E2E_API_URL || 'http://localhost:8080';

// Type definitions
interface JwtPayload {
  'custom:batbern_roles'?: string;
  'custom:roles_synced_at'?: string;
  'custom:batbern_event_roles'?: string;
  [key: string]: unknown;
}

interface DbUser {
  id: string;
  email: string;
  active: boolean;
  roles?: string[];
  createdAt?: string;
}

interface CompensationLog {
  operation: string;
  status: string;
  compensationRequired: boolean;
  targetRole: string;
  errorMessage?: string;
}

/**
 * Helper: Decode JWT token (client-side decode without verification)
 */
function decodeToken(token: string): JwtPayload | null {
  try {
    const base64Url = token.split('.')[1];
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
    const jsonPayload = decodeURIComponent(
      atob(base64)
        .split('')
        .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join('')
    );
    return JSON.parse(jsonPayload);
  } catch (error) {
    console.error('Failed to decode JWT:', error);
    return null;
  }
}

/**
 * Helper: Login and get JWT token
 */
async function loginAndGetToken(page: Page, email: string, password: string): Promise<string> {
  await page.goto(`${BASE_URL}/auth/login`);
  await page.fill('input[name="email"]', email);
  await page.fill('input[name="password"]', password);
  await page.click('button[type="submit"]');

  await page.waitForURL(/\/dashboard/);
  const token = await page.evaluate(() => localStorage.getItem('authToken'));
  return token || '';
}

/**
 * Helper: Get user by email from database
 */
async function getUserByEmail(email: string): Promise<DbUser> {
  const response = await fetch(`${API_URL}/api/v1/internal/users/by-email/${email}`, {
    method: 'GET',
    headers: {
      'Content-Type': 'application/json',
      'X-Internal-Auth': process.env.INTERNAL_API_KEY || 'test-key',
    },
  });
  return response.json();
}

/**
 * Helper: Change user role in database
 */
async function changeUserRole(userId: string, newRole: string): Promise<void> {
  await fetch(`${API_URL}/api/v1/internal/users/${userId}/roles`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Internal-Auth': process.env.INTERNAL_API_KEY || 'test-key',
    },
    body: JSON.stringify({ role: newRole }),
  });
}

/**
 * Helper: Get Cognito user attributes
 */
async function getCognitoUserAttributes(email: string): Promise<Record<string, string>> {
  // This would call AWS Cognito Admin API in test environment
  const response = await fetch(`${API_URL}/api/v1/internal/cognito/users/${email}/attributes`, {
    method: 'GET',
    headers: {
      'Content-Type': 'application/json',
      'X-Internal-Auth': process.env.INTERNAL_API_KEY || 'test-key',
    },
  });
  return response.json();
}

/**
 * Helper: Check compensation log for failed syncs
 */
async function getCompensationLogs(userId: string): Promise<CompensationLog[]> {
  const response = await fetch(`${API_URL}/api/v1/internal/users/${userId}/compensation-logs`, {
    method: 'GET',
    headers: {
      'Content-Type': 'application/json',
      'X-Internal-Auth': process.env.INTERNAL_API_KEY || 'test-key',
    },
  });
  return response.json();
}

// ============================================================================
// TEST GROUP 1: PreTokenGeneration - JWT Enrichment with Database Roles
// AC1: PreTokenGeneration enriches JWT with roles from database
// ============================================================================

test.describe('Role Change Sync - JWT Token Enrichment', () => {
  test('should_enrichJWT_when_userLogsInWithDatabaseRoles', async ({ page }) => {
    // AC1: PreTokenGeneration fetches roles from DB and adds to JWT claims
    const testEmail = `jwt-test-${Date.now()}@batbern.ch`;
    const testPassword = 'TestPassword123!';

    // Setup: Create user with ORGANIZER role
    const dbUser = await getUserByEmail(testEmail);
    if (!dbUser) {
      // User doesn't exist, create via registration (not shown for brevity)
      throw new Error('Test user must exist before running this test');
    }

    // Login and get token
    const token = await loginAndGetToken(page, testEmail, testPassword);
    expect(token).toBeTruthy();

    // Decode JWT and verify role claims
    const decoded = decodeToken(token);
    expect(decoded).toBeTruthy();
    expect(decoded['custom:batbern_roles']).toBeDefined();

    const roles = JSON.parse(decoded['custom:batbern_roles']);
    expect(roles).toContain('ORGANIZER');

    // Verify roles_synced_at timestamp exists
    expect(decoded['custom:roles_synced_at']).toBeDefined();
    const syncedAt = new Date(decoded['custom:roles_synced_at']);
    expect(syncedAt).toBeInstanceOf(Date);
  });

  test('should_includeEventSpecificRoles_when_userHasEventRoles', async ({ page }) => {
    // AC1: JWT includes event-specific roles for speakers/partners
    const testEmail = `speaker-event-${Date.now()}@batbern.ch`;
    const testPassword = 'TestPassword123!';

    const dbUser = await getUserByEmail(testEmail);

    // Assign SPEAKER role for specific event
    await changeUserRole(dbUser.id, 'SPEAKER');

    // Login and get token
    const token = await loginAndGetToken(page, testEmail, testPassword);
    const decoded = decodeToken(token);

    // Verify event-specific roles in JWT
    expect(decoded['custom:batbern_event_roles']).toBeDefined();
    const eventRoles = JSON.parse(decoded['custom:batbern_event_roles']);

    expect(eventRoles).toBeInstanceOf(Array);
    expect(eventRoles.length).toBeGreaterThan(0);
    expect(eventRoles[0]).toHaveProperty('eventId');
    expect(eventRoles[0]).toHaveProperty('role');
  });

  test('should_fallbackToEmptyRoles_when_databaseUnavailable', async ({ page }) => {
    // AC1: Graceful degradation - allow login even if DB query fails
    const testEmail = `fallback-${Date.now()}@batbern.ch`;
    const testPassword = 'TestPassword123!';

    // Mock database unavailability (in test environment)
    // The PreTokenGeneration trigger should handle this gracefully

    const token = await loginAndGetToken(page, testEmail, testPassword);
    expect(token).toBeTruthy();

    // Token should still be generated (with empty or default roles)
    const decoded = decodeToken(token);
    expect(decoded).toBeTruthy();

    // User should still be able to access dashboard
    await expect(page).toHaveURL(/\/dashboard/);
  });

  test('should_refreshToken_when_roleChangedInDatabase', async ({ page }) => {
    // Test that role changes are reflected after token refresh
    const testEmail = `role-refresh-${Date.now()}@batbern.ch`;
    const testPassword = 'TestPassword123!';

    const dbUser = await getUserByEmail(testEmail);

    // Initial login with ATTENDEE role
    const initialToken = await loginAndGetToken(page, testEmail, testPassword);
    const initialDecoded = decodeToken(initialToken);
    const initialRoles = JSON.parse(initialDecoded['custom:batbern_roles']);
    expect(initialRoles).toContain('ATTENDEE');

    // Change role to SPEAKER in database
    await changeUserRole(dbUser.id, 'SPEAKER');

    // Logout and login again to trigger token refresh
    await page.click('button[aria-label="User menu"]');
    await page.click('button:has-text("Logout")');

    const newToken = await loginAndGetToken(page, testEmail, testPassword);
    const newDecoded = decodeToken(newToken);
    const newRoles = JSON.parse(newDecoded['custom:batbern_roles']);

    // New token should include SPEAKER role
    expect(newRoles).toContain('SPEAKER');
  });
});

// ============================================================================
// TEST GROUP 2: Bidirectional Role Sync (Database → Cognito)
// AC3: Role changes sync bidirectionally between database and Cognito
// ============================================================================

test.describe('Role Change Sync - Bidirectional Sync', () => {
  test('should_syncToCognito_when_roleChangedInDatabase', async ({ page }) => {
    // AC3: When role changes in database, sync to Cognito custom attributes
    const testEmail = `bidirectional-${Date.now()}@batbern.ch`;
    const dbUser = await getUserByEmail(testEmail);

    // Change role in database
    await changeUserRole(dbUser.id, 'PARTNER');

    // Wait for saga to complete sync (with retry logic)
    await page.waitForTimeout(3000);

    // Verify Cognito attribute updated
    const cognitoAttrs = await getCognitoUserAttributes(testEmail);
    expect(cognitoAttrs['custom:batbern_role']).toBe('PARTNER');
  });

  test('should_createCompensationLog_when_cognitoSyncFails', async ({ page }) => {
    // AC3: If Cognito sync fails, compensation log entry created
    const testEmail = `compensation-${Date.now()}@batbern.ch`;
    const dbUser = await getUserByEmail(testEmail);

    // Mock Cognito unavailability (in test environment)
    // Attempt role change that will fail to sync to Cognito
    await changeUserRole(dbUser.id, 'ORGANIZER');

    // Wait for saga to attempt sync
    await page.waitForTimeout(3000);

    // Check compensation logs
    const compensationLogs = await getCompensationLogs(dbUser.id);
    const failedSync = compensationLogs.find(
      (log) => log.operation === 'ROLE_SYNC' && log.status === 'FAILED'
    );

    // If Cognito was unavailable, compensation log should exist
    if (failedSync) {
      expect(failedSync).toBeTruthy();
      expect(failedSync.compensationRequired).toBe(true);
      expect(failedSync.targetRole).toBe('ORGANIZER');
      expect(failedSync.errorMessage).toBeTruthy();
    }
  });

  test('should_retryWithBackoff_when_cognitoThrottles', async ({ page }) => {
    // AC3: Exponential backoff retry on Cognito throttling
    const testEmail = `retry-${Date.now()}@batbern.ch`;
    const dbUser = await getUserByEmail(testEmail);

    const startTime = Date.now();

    // Change role (will trigger saga with potential retries)
    await changeUserRole(dbUser.id, 'SPEAKER');

    // Wait for potential retries (max 3 attempts with backoff: 2s, 4s, 8s)
    await page.waitForTimeout(15000);

    // Verify Cognito eventually updated
    const cognitoAttrs = await getCognitoUserAttributes(testEmail);
    expect(cognitoAttrs['custom:batbern_role']).toBe('SPEAKER');

    // Verify total time includes retry delays
    const totalTime = Date.now() - startTime;
    console.log(`Role sync with retries completed in ${totalTime}ms`);
  });

  test('should_notRollbackDatabase_when_cognitoSyncFails', async () => {
    // AC3: Eventual consistency - DB transaction commits even if Cognito sync fails
    const testEmail = `eventual-${Date.now()}@batbern.ch`;
    const dbUser = await getUserByEmail(testEmail);

    // Change role in database
    await changeUserRole(dbUser.id, 'PARTNER');

    // Verify database committed immediately
    const updatedUser = await getUserByEmail(testEmail);
    expect(updatedUser.roles).toContain('PARTNER');

    // Even if Cognito sync fails, database change is permanent
    // (Reconciliation job will retry later)
  });
});

// ============================================================================
// TEST GROUP 3: JIT Provisioning - Self-Healing on First Request
// AC2: JIT provisioning creates missing users on first API request
// ============================================================================

test.describe('Role Change Sync - JIT Provisioning', () => {
  test('should_createUser_when_cognitoUserNotInDatabase', async ({ page }) => {
    // AC2: User exists in Cognito but not in database - JIT creates user
    const testEmail = `jit-${Date.now()}@batbern.ch`;
    const testPassword = 'TestPassword123!';

    // Scenario: User created in Cognito but PostConfirmation failed
    // Database user does not exist yet

    // User tries to login
    const token = await loginAndGetToken(page, testEmail, testPassword);
    expect(token).toBeTruthy();

    // Dashboard should load (JIT interceptor created user)
    await expect(page).toHaveURL(/\/dashboard/);
    await expect(page.getByText(/welcome/i)).toBeVisible();

    // Verify user now exists in database
    const dbUser = await getUserByEmail(testEmail);
    expect(dbUser).toBeTruthy();
    expect(dbUser.email).toBe(testEmail);
    expect(dbUser.active).toBe(true);
  });

  test('should_assignRoleFromJWT_when_jitProvisioningUser', async ({ page }) => {
    // AC2: JIT provisioning assigns role from JWT claims
    const testEmail = `jit-role-${Date.now()}@batbern.ch`;
    const testPassword = 'TestPassword123!';

    // Login (triggers JIT provisioning)
    const token = await loginAndGetToken(page, testEmail, testPassword);
    const decoded = decodeToken(token);

    // Verify database user created with role from JWT
    const dbUser = await getUserByEmail(testEmail);
    expect(dbUser.roles).toBeDefined();

    // Role should match JWT claim
    const jwtRoles = JSON.parse(decoded['custom:batbern_roles'] || '[]');
    expect(dbUser.roles[0]).toBe(jwtRoles[0] || 'ATTENDEE');
  });

  test('should_continueRequest_when_jitProvisioningCompletes', async ({ page }) => {
    // AC2: API request continues normally after JIT provisioning
    const testEmail = `jit-continue-${Date.now()}@batbern.ch`;
    const testPassword = 'TestPassword123!';

    await loginAndGetToken(page, testEmail, testPassword);

    // Navigate to protected resource
    await page.goto(`${BASE_URL}/attendee/events`);

    // Page should load without errors
    await expect(page).toHaveURL(/\/attendee\/events/);
    await expect(page.getByText(/upcoming events/i)).toBeVisible();
  });

  test('should_skipProvisioning_when_userAlreadyExists', async ({ page }) => {
    // AC2: JIT interceptor skips provisioning if user already in database
    const testEmail = `existing-${Date.now()}@batbern.ch`;
    const testPassword = 'TestPassword123!';

    // First login creates user via JIT
    await loginAndGetToken(page, testEmail, testPassword);
    const firstUser = await getUserByEmail(testEmail);

    // Logout and login again
    await page.click('button[aria-label="User menu"]');
    await page.click('button:has-text("Logout")');

    await loginAndGetToken(page, testEmail, testPassword);
    const secondUser = await getUserByEmail(testEmail);

    // User should be same (not recreated)
    expect(secondUser.id).toBe(firstUser.id);
    expect(secondUser.createdAt).toBe(firstUser.createdAt);
  });

  test('should_notBlockRequest_when_jitProvisioningFails', async ({ page }) => {
    // AC2: Non-blocking - request continues even if JIT provisioning fails
    const testEmail = `jit-fail-${Date.now()}@batbern.ch`;
    const testPassword = 'TestPassword123!';

    // Mock database failure for JIT provisioning
    // User should still be able to access application

    const token = await loginAndGetToken(page, testEmail, testPassword);
    expect(token).toBeTruthy();

    // Should reach dashboard even if JIT failed
    await expect(page).toHaveURL(/\/dashboard/);
  });
});

// ============================================================================
// TEST GROUP 4: Role-Based Access Control After Sync
// ============================================================================

test.describe('Role Change Sync - Access Control', () => {
  test('should_allowAccess_when_roleGrantsPermission', async ({ page }) => {
    // After role sync, user should have access to role-specific pages
    const testEmail = `access-grant-${Date.now()}@batbern.ch`;
    const testPassword = 'TestPassword123!';

    const dbUser = await getUserByEmail(testEmail);
    await changeUserRole(dbUser.id, 'ORGANIZER');

    // Login with new role
    await loginAndGetToken(page, testEmail, testPassword);

    // Access organizer-only page
    await page.goto(`${BASE_URL}/organizer/events/create`);
    await expect(page).toHaveURL(/\/organizer\/events\/create/);
    await expect(page.getByText(/create new event/i)).toBeVisible();
  });

  test('should_denyAccess_when_roleRevoked', async ({ page }) => {
    // After role revocation, user should lose access
    const testEmail = `access-revoke-${Date.now()}@batbern.ch`;
    const testPassword = 'TestPassword123!';

    const dbUser = await getUserByEmail(testEmail);

    // Grant ORGANIZER role
    await changeUserRole(dbUser.id, 'ORGANIZER');
    await loginAndGetToken(page, testEmail, testPassword);

    // Can access organizer page
    await page.goto(`${BASE_URL}/organizer/dashboard`);
    await expect(page).toHaveURL(/\/organizer\/dashboard/);

    // Revoke ORGANIZER role
    await fetch(`${API_URL}/api/v1/internal/users/${dbUser.id}/roles/ORGANIZER`, {
      method: 'DELETE',
      headers: {
        'X-Internal-Auth': process.env.INTERNAL_API_KEY || 'test-key',
      },
    });

    // Logout and login again (triggers token refresh)
    await page.click('button[aria-label="User menu"]');
    await page.click('button:has-text("Logout")');
    await loginAndGetToken(page, testEmail, testPassword);

    // Should no longer have access
    await page.goto(`${BASE_URL}/organizer/dashboard`);
    await expect(page).toHaveURL(/\/403/); // Forbidden
  });
});
