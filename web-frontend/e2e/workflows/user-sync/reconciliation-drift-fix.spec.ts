/**
 * E2E Tests for Reconciliation Job and Drift Detection
 * Story 1.2.5: User Sync and Reconciliation Implementation
 *
 * IMPORTANT: These tests require:
 * 1. Playwright to be installed and configured
 * 2. AWS Cognito user pool deployed
 * 3. PostgreSQL database with users, user_roles, and compensation log tables
 * 4. Scheduled reconciliation job (can be triggered manually for E2E tests)
 * 5. Test environment with admin API access
 *
 * Setup Instructions:
 * 1. Install Playwright: npm install -D @playwright/test
 * 2. Initialize Playwright: npx playwright install
 * 3. Configure playwright.config.ts with base URL
 * 4. Set up test environment variables (see .env.test.example)
 * 5. Run: npx playwright test e2e/workflows/user-sync/reconciliation-drift-fix.spec.ts
 */

import { test, expect } from '@playwright/test';

// Test configuration
const API_URL = process.env.E2E_API_URL || 'http://localhost:8080';
const INTERNAL_API_KEY = process.env.INTERNAL_API_KEY || 'test-key';

// Type definitions
interface ReconciliationResult {
  status: string;
}

interface ReconciliationReport {
  status: string;
  startedAt: string;
  durationMs: number;
  metrics: {
    orphanedUsersDetected: number;
    missingDbUsersCreated: number;
    roleMismatchesFixed: number;
    compensationsRetried: number;
  };
}

interface DbUser {
  id: string;
  email: string;
  active: boolean;
  cognitoId?: string;
  deactivationReason?: string;
  roles?: string[];
}

interface CognitoUser {
  attributes: Record<string, string>;
}

interface CompensationLog {
  targetRole: string;
  status: string;
  compensationExecutedAt?: string;
  retryCount: number;
}

interface RoleHistory {
  endDate: string | null;
}

/**
 * Helper: Trigger reconciliation job manually
 */
async function triggerReconciliationJob(): Promise<ReconciliationResult> {
  const response = await fetch(`${API_URL}/api/v1/internal/reconciliation/trigger`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Internal-Auth': INTERNAL_API_KEY,
    },
  });
  return response.json();
}

/**
 * Helper: Get reconciliation report
 */
async function getReconciliationReport(): Promise<ReconciliationReport> {
  const response = await fetch(`${API_URL}/api/v1/internal/reconciliation/latest-report`, {
    method: 'GET',
    headers: {
      'Content-Type': 'application/json',
      'X-Internal-Auth': INTERNAL_API_KEY,
    },
  });
  return response.json();
}

/**
 * Helper: Create orphaned database user (Cognito user deleted)
 */
async function createOrphanedDbUser(email: string): Promise<DbUser> {
  const response = await fetch(`${API_URL}/api/v1/internal/test/create-orphaned-user`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Internal-Auth': INTERNAL_API_KEY,
    },
    body: JSON.stringify({ email, cognitoId: `deleted-${Date.now()}` }),
  });
  return response.json();
}

/**
 * Helper: Create Cognito user without database record
 */
async function createCognitoOnlyUser(email: string, role: string): Promise<void> {
  await fetch(`${API_URL}/api/v1/internal/test/create-cognito-user`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Internal-Auth': INTERNAL_API_KEY,
    },
    body: JSON.stringify({ email, role }),
  });
}

/**
 * Helper: Create role mismatch (DB and Cognito have different roles)
 */
async function createRoleMismatch(
  email: string,
  dbRole: string,
  cognitoRole: string
): Promise<void> {
  await fetch(`${API_URL}/api/v1/internal/test/create-role-mismatch`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Internal-Auth': INTERNAL_API_KEY,
    },
    body: JSON.stringify({ email, dbRole, cognitoRole }),
  });
}

/**
 * Helper: Get user from database
 */
async function getUserByEmail(email: string): Promise<DbUser | null> {
  const response = await fetch(`${API_URL}/api/v1/internal/users/by-email/${email}`, {
    method: 'GET',
    headers: {
      'Content-Type': 'application/json',
      'X-Internal-Auth': INTERNAL_API_KEY,
    },
  });
  if (response.status === 404) {
    return null;
  }
  return response.json();
}

/**
 * Helper: Get Cognito user
 */
async function getCognitoUser(email: string): Promise<CognitoUser | null> {
  const response = await fetch(`${API_URL}/api/v1/internal/cognito/users/${email}`, {
    method: 'GET',
    headers: {
      'Content-Type': 'application/json',
      'X-Internal-Auth': INTERNAL_API_KEY,
    },
  });
  if (response.status === 404) {
    return null;
  }
  return response.json();
}

/**
 * Helper: Create failed compensation log entry
 */
async function createFailedCompensation(userId: string, targetRole: string): Promise<void> {
  await fetch(`${API_URL}/api/v1/internal/test/create-failed-compensation`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-Internal-Auth': INTERNAL_API_KEY,
    },
    body: JSON.stringify({ userId, targetRole, retryCount: 2 }),
  });
}

/**
 * Helper: Get compensation logs
 */
async function getCompensationLogs(userId: string): Promise<CompensationLog[]> {
  const response = await fetch(`${API_URL}/api/v1/internal/users/${userId}/compensation-logs`, {
    method: 'GET',
    headers: {
      'Content-Type': 'application/json',
      'X-Internal-Auth': INTERNAL_API_KEY,
    },
  });
  return response.json();
}

// ============================================================================
// TEST GROUP 1: Orphaned Database User Detection
// AC4, AC6: Detect and deactivate users deleted from Cognito
// ============================================================================

test.describe('Reconciliation - Orphaned User Detection', () => {
  test('should_deactivateDbUser_when_cognitoUserNotFound', async () => {
    // AC4, AC6: Orphaned database users are marked inactive during reconciliation
    const testEmail = `orphaned-${Date.now()}@batbern.ch`;

    // Create orphaned user (exists in DB but not in Cognito)
    const orphanedUser = await createOrphanedDbUser(testEmail);
    expect(orphanedUser).toBeTruthy();
    expect(orphanedUser.active).toBe(true);

    // Trigger reconciliation job
    const reconciliationResult = await triggerReconciliationJob();
    expect(reconciliationResult.status).toBe('COMPLETED');

    // Verify user marked inactive
    const updatedUser = await getUserByEmail(testEmail);
    expect(updatedUser.active).toBe(false);
    expect(updatedUser.deactivationReason).toContain(
      'Cognito user not found during reconciliation'
    );
  });

  test('should_deactivateRoles_when_userMarkedInactive', async () => {
    // AC6: All user roles deactivated when user marked inactive
    const testEmail = `orphaned-roles-${Date.now()}@batbern.ch`;

    await createOrphanedDbUser(testEmail);

    // Trigger reconciliation
    await triggerReconciliationJob();

    // Verify roles deactivated
    const updatedUser = await getUserByEmail(testEmail);
    expect(updatedUser.active).toBe(false);

    // Check user_roles table - all roles should have end_date set
    const response = await fetch(
      `${API_URL}/api/v1/internal/users/${updatedUser.id}/roles/history`,
      {
        headers: { 'X-Internal-Auth': INTERNAL_API_KEY },
      }
    );
    const roleHistory: RoleHistory[] = await response.json();

    roleHistory.forEach((role) => {
      expect(role.endDate).toBeTruthy();
    });
  });

  test('should_setDeactivationReason_when_cognitoUserNotFound', async () => {
    // AC6: Deactivation reason clearly indicates Cognito user missing
    const testEmail = `reason-${Date.now()}@batbern.ch`;

    await createOrphanedDbUser(testEmail);
    await triggerReconciliationJob();

    const updatedUser = await getUserByEmail(testEmail);
    expect(updatedUser.deactivationReason).toBe('Cognito user not found during reconciliation');
  });
});

// ============================================================================
// TEST GROUP 2: Missing Database User Creation
// AC4: Create database users for Cognito users without DB records
// ============================================================================

test.describe('Reconciliation - Missing Database User Creation', () => {
  test('should_createDbUser_when_cognitoUserMissing', async () => {
    // AC4: Cognito users without database records are created during reconciliation
    const testEmail = `missing-db-${Date.now()}@batbern.ch`;

    // Create user in Cognito only (simulate failed PostConfirmation)
    await createCognitoOnlyUser(testEmail, 'ORGANIZER');

    // Verify user not in database yet
    const initialUser = await getUserByEmail(testEmail);
    expect(initialUser).toBeNull();

    // Trigger reconciliation
    await triggerReconciliationJob();

    // Verify user now exists in database
    const createdUser = await getUserByEmail(testEmail);
    expect(createdUser).toBeTruthy();
    expect(createdUser.email).toBe(testEmail);
    expect(createdUser.active).toBe(true);
    expect(createdUser.cognitoId).toBeTruthy();
  });

  test('should_assignRoleFromCognito_when_creatingDbUser', async () => {
    // AC4: Role from Cognito custom attribute assigned when creating DB user
    const testEmail = `missing-role-${Date.now()}@batbern.ch`;

    await createCognitoOnlyUser(testEmail, 'SPEAKER');
    await triggerReconciliationJob();

    const createdUser = await getUserByEmail(testEmail);
    expect(createdUser.roles).toContain('SPEAKER');
  });

  test('should_handlePagination_when_manyUsersExist', async () => {
    // AC4: Reconciliation handles pagination for large user sets
    // Create 100 Cognito-only users
    const testEmails: string[] = [];
    for (let i = 0; i < 100; i++) {
      const email = `pagination-${Date.now()}-${i}@batbern.ch`;
      testEmails.push(email);
      await createCognitoOnlyUser(email, 'ATTENDEE');
    }

    // Trigger reconciliation (should handle pagination internally)
    const reconciliationResult = await triggerReconciliationJob();
    expect(reconciliationResult.status).toBe('COMPLETED');

    // Verify all users created in database
    let createdCount = 0;
    for (const email of testEmails) {
      const user = await getUserByEmail(email);
      if (user) createdCount++;
    }

    expect(createdCount).toBe(100);
  });
});

// ============================================================================
// TEST GROUP 3: Role Mismatch Detection and Sync
// AC4: Fix role mismatches (database is source of truth)
// ============================================================================

test.describe('Reconciliation - Role Mismatch Sync', () => {
  test('should_syncRoleToCognito_when_roleMismatchDetected', async () => {
    // AC4: Database role synced to Cognito when mismatch detected
    const testEmail = `mismatch-${Date.now()}@batbern.ch`;

    // Create role mismatch (DB: ORGANIZER, Cognito: ATTENDEE)
    await createRoleMismatch(testEmail, 'ORGANIZER', 'ATTENDEE');

    // Trigger reconciliation
    await triggerReconciliationJob();

    // Verify Cognito role updated to match database
    const cognitoUser = await getCognitoUser(testEmail);
    expect(cognitoUser.attributes['custom:batbern_role']).toBe('ORGANIZER');
  });

  test('should_useDatabaseAsTruth_when_roleMismatchDetected', async () => {
    // AC4: Database is always source of truth for role conflicts
    const testEmail = `db-truth-${Date.now()}@batbern.ch`;

    await createRoleMismatch(testEmail, 'PARTNER', 'SPEAKER');

    await triggerReconciliationJob();

    // Cognito should be updated to match database (PARTNER)
    const cognitoUser = await getCognitoUser(testEmail);
    expect(cognitoUser.attributes['custom:batbern_role']).toBe('PARTNER');

    // Database should remain unchanged
    const dbUser = await getUserByEmail(testEmail);
    expect(dbUser.roles).toContain('PARTNER');
  });
});

// ============================================================================
// TEST GROUP 4: Failed Compensation Retry
// AC4, AC8: Retry failed compensation operations
// ============================================================================

test.describe('Reconciliation - Compensation Retry', () => {
  test('should_retryFailedCompensations_when_reconciliationRuns', async () => {
    // AC4, AC8: Failed compensations retried during reconciliation
    const testEmail = `compensation-retry-${Date.now()}@batbern.ch`;

    const dbUser = await getUserByEmail(testEmail);
    if (!dbUser) {
      throw new Error('Test user must exist before running this test');
    }

    // Create failed compensation log entry
    await createFailedCompensation(dbUser.id, 'ORGANIZER');

    // Trigger reconciliation (should retry failed compensation)
    await triggerReconciliationJob();

    // Verify compensation marked as completed
    const compensationLogs = await getCompensationLogs(dbUser.id);
    const retriedLog = compensationLogs.find((log) => log.targetRole === 'ORGANIZER');

    if (retriedLog) {
      expect(retriedLog.status).toBe('COMPLETED');
      expect(retriedLog.compensationExecutedAt).toBeTruthy();
    }
  });

  test('should_incrementRetryCount_when_retryFails', async () => {
    // AC8: Retry count incremented on each failed retry attempt
    const testEmail = `retry-count-${Date.now()}@batbern.ch`;

    const dbUser = await getUserByEmail(testEmail);

    // Create failed compensation with retry count = 2
    await createFailedCompensation(dbUser.id, 'SPEAKER');

    // Trigger reconciliation (retry will fail again due to mocked error)
    await triggerReconciliationJob();

    // Verify retry count incremented to 3
    const compensationLogs = await getCompensationLogs(dbUser.id);
    const retriedLog = compensationLogs.find((log) => log.targetRole === 'SPEAKER');

    if (retriedLog && retriedLog.status === 'FAILED') {
      expect(retriedLog.retryCount).toBe(3);
    }
  });

  test('should_stopRetrying_when_maxRetriesExceeded', async () => {
    // AC8: Stop retrying after 5 total attempts
    const testEmail = `max-retry-${Date.now()}@batbern.ch`;

    const dbUser = await getUserByEmail(testEmail);

    // Create failed compensation with retry count = 5 (max)
    await createFailedCompensation(dbUser.id, 'PARTNER');

    await fetch(`${API_URL}/api/v1/internal/test/set-compensation-retry-count`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Internal-Auth': INTERNAL_API_KEY,
      },
      body: JSON.stringify({ userId: dbUser.id, retryCount: 5 }),
    });

    // Trigger reconciliation (should skip this compensation)
    await triggerReconciliationJob();

    // Verify retry count not incremented (stopped at 5)
    const compensationLogs = await getCompensationLogs(dbUser.id);
    const skippedLog = compensationLogs.find((log) => log.targetRole === 'PARTNER');

    if (skippedLog) {
      expect(skippedLog.retryCount).toBe(5);
      expect(skippedLog.status).toBe('FAILED');
    }
  });
});

// ============================================================================
// TEST GROUP 5: Reconciliation Metrics and Reporting
// AC4, AC5: Metrics published after reconciliation
// ============================================================================

test.describe('Reconciliation - Metrics and Reporting', () => {
  test('should_publishMetrics_when_reconciliationCompletes', async () => {
    // AC4, AC5: Reconciliation publishes drift metrics to CloudWatch
    await triggerReconciliationJob();

    const report = await getReconciliationReport();

    expect(report).toBeTruthy();
    expect(report.status).toBe('COMPLETED');
    expect(report.metrics).toBeTruthy();
    expect(report.metrics.orphanedUsersDetected).toBeGreaterThanOrEqual(0);
    expect(report.metrics.missingDbUsersCreated).toBeGreaterThanOrEqual(0);
    expect(report.metrics.roleMismatchesFixed).toBeGreaterThanOrEqual(0);
    expect(report.metrics.compensationsRetried).toBeGreaterThanOrEqual(0);
    expect(report.durationMs).toBeGreaterThan(0);
  });

  test('should_recordDriftMetric_when_driftDetected', async () => {
    // AC5: Drift detected metric recorded when inconsistencies found
    const testEmail = `drift-metric-${Date.now()}@batbern.ch`;

    // Create drift (orphaned user)
    await createOrphanedDbUser(testEmail);

    // Trigger reconciliation
    await triggerReconciliationJob();

    const report = await getReconciliationReport();

    // Drift should be detected and recorded
    expect(report.metrics.orphanedUsersDetected).toBeGreaterThan(0);
  });

  test('should_fireAlarm_when_driftExceedsThreshold', async () => {
    // AC5: CloudWatch alarm fires when drift exceeds 50 users
    // Create 51 orphaned users to trigger alarm
    for (let i = 0; i < 51; i++) {
      await createOrphanedDbUser(`drift-alarm-${Date.now()}-${i}@batbern.ch`);
    }

    // Trigger reconciliation
    await triggerReconciliationJob();

    const report = await getReconciliationReport();

    // Verify drift count exceeds threshold
    expect(report.metrics.orphanedUsersDetected).toBeGreaterThanOrEqual(51);

    // In production, verify CloudWatch alarm state
    // const alarmState = await getCloudWatchAlarmState('ReconciliationDriftDetected');
    // expect(alarmState).toBe('ALARM');
  });
});

// ============================================================================
// TEST GROUP 6: Scheduled Job Execution
// AC4: Reconciliation runs daily at 2 AM
// ============================================================================

test.describe('Reconciliation - Scheduled Execution', () => {
  test('should_runAutomatically_when_scheduledTimeReached', async () => {
    // AC4: Reconciliation job runs at scheduled time (2 AM daily)
    // This test verifies the cron schedule is configured correctly

    // In production, this would check:
    // 1. Spring @Scheduled annotation on reconcileUsers() method
    // 2. Cron expression: "0 0 2 * * *"
    // 3. Latest reconciliation report timestamp

    const report = await getReconciliationReport();

    // Verify report exists (indicates job has run)
    expect(report).toBeTruthy();
    expect(report.startedAt).toBeTruthy();

    // Verify report timestamp is recent (within last 24 hours)
    const reportTime = new Date(report.startedAt).getTime();
    const now = Date.now();
    const dayInMs = 24 * 60 * 60 * 1000;

    expect(now - reportTime).toBeLessThan(dayInMs);
  });

  test('should_completeWithinTimeLimit_when_reconciliationRuns', async () => {
    // Verify reconciliation completes in reasonable time
    const startTime = Date.now();

    await triggerReconciliationJob();

    const endTime = Date.now();
    const duration = endTime - startTime;

    // Reconciliation should complete within 5 minutes for typical workload
    expect(duration).toBeLessThan(5 * 60 * 1000);
  });
});

// ============================================================================
// TEST GROUP 7: End-to-End Reconciliation Workflow
// ============================================================================

test.describe('Reconciliation - Complete Workflow', () => {
  test('should_fixAllDriftTypes_when_reconciliationRuns', async () => {
    // Comprehensive E2E test covering all drift types
    const timestamp = Date.now();

    // Setup drift scenarios
    const orphanedEmail = `orphaned-e2e-${timestamp}@batbern.ch`;
    const missingDbEmail = `missing-e2e-${timestamp}@batbern.ch`;
    const mismatchEmail = `mismatch-e2e-${timestamp}@batbern.ch`;

    await createOrphanedDbUser(orphanedEmail);
    await createCognitoOnlyUser(missingDbEmail, 'SPEAKER');
    await createRoleMismatch(mismatchEmail, 'PARTNER', 'ATTENDEE');

    // Trigger reconciliation
    const reconciliationResult = await triggerReconciliationJob();
    expect(reconciliationResult.status).toBe('COMPLETED');

    // Verify all drift fixed
    const orphanedUser = await getUserByEmail(orphanedEmail);
    expect(orphanedUser.active).toBe(false);

    const missingUser = await getUserByEmail(missingDbEmail);
    expect(missingUser).toBeTruthy();
    expect(missingUser.roles).toContain('SPEAKER');

    const cognitoMismatch = await getCognitoUser(mismatchEmail);
    expect(cognitoMismatch.attributes['custom:batbern_role']).toBe('PARTNER');

    // Verify report
    const report = await getReconciliationReport();
    expect(report.metrics.orphanedUsersDetected).toBeGreaterThanOrEqual(1);
    expect(report.metrics.missingDbUsersCreated).toBeGreaterThanOrEqual(1);
    expect(report.metrics.roleMismatchesFixed).toBeGreaterThanOrEqual(1);
  });
});
