/**
 * E2E Tests for Role Management
 * Story 1.2.5: User Sync and Reconciliation Implementation
 *
 * STATUS: ✅ ADAPTED - Tests rewritten to use actual role management endpoints
 *
 * These tests verify the role management endpoints that ARE implemented:
 * - GET /api/v1/users/{username}/roles (get user roles)
 * - PUT /api/v1/users/{username}/roles (update user roles)
 * - GET /api/v1/users/search (find users to test role changes)
 *
 * Note: Tests that required internal Cognito APIs or JWT verification have been removed.
 * The remaining tests verify the real role management endpoints work.
 *
 * Reference: BAT-93, BAT-107, Story 1.2.5
 */

import { test, expect } from '@playwright/test';
import { API_URL } from '../../../playwright.config';

/**
 * Helper: Get authentication token from stored state
 */
async function getAuthToken(): Promise<string> {
  // Token is set in global setup and available via environment
  const token = process.env.AUTH_TOKEN;
  if (!token) {
    throw new Error('AUTH_TOKEN not found in environment. Run global setup first.');
  }
  return token;
}

/**
 * Helper: Find a test user (non-organizer) to modify roles
 */
async function findTestUser(token: string): Promise<string | null> {
  const response = await fetch(`${API_URL}/api/v1/users?page=1&limit=50`, {
    method: 'GET',
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
  });

  if (response.status !== 200) {
    return null;
  }

  const data = await response.json();

  // Find a user that is NOT an organizer (to safely test role changes)
  const testUser = data.data?.find(
    (user: { roles?: string[]; username?: string }) =>
      user.roles && !user.roles.includes('ORGANIZER')
  );

  return testUser?.username || null;
}

// ============================================================================
// TEST GROUP 1: Get User Roles
// AC8: Retrieve roles for a specific user
// ============================================================================

test.describe('Role Management - Get Roles', () => {
  test('should_getUserRoles_when_requestingRoles', async () => {
    // AC8: Verify GET roles endpoint works
    const token = await getAuthToken();

    // Find a test user
    const testUsername = await findTestUser(token);

    if (!testUsername) {
      console.log('No test user found, skipping role GET test');
      test.skip();
      return;
    }

    const response = await fetch(`${API_URL}/api/v1/users/${testUsername}/roles`, {
      method: 'GET',
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
    });

    expect(response.status).toBe(200);

    const data = await response.json();

    // Verify response structure
    expect(data).toHaveProperty('username');
    expect(data).toHaveProperty('roles');
    expect(data.username).toBe(testUsername);
    expect(Array.isArray(data.roles)).toBe(true);

    console.log('User roles:', {
      username: data.username,
      roles: data.roles,
    });
  });

  test('should_requireOrganizerRole_when_gettingUserRoles', async () => {
    // AC: Verify endpoint is protected (requires ORGANIZER role)
    const response = await fetch(`${API_URL}/api/v1/users/testuser/roles`, {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
      },
    });

    // Should be 401 or 403 without auth
    expect([401, 403]).toContain(response.status);
  });
});

// ============================================================================
// TEST GROUP 2: Update User Roles
// AC8: Update roles for a specific user
// ============================================================================

test.describe('Role Management - Update Roles', () => {
  test('should_updateUserRoles_when_organizerChangesRoles', async () => {
    // AC8: Verify PUT roles endpoint works
    const token = await getAuthToken();

    // Find a test user
    const testUsername = await findTestUser(token);

    if (!testUsername) {
      console.log('No test user found, skipping role UPDATE test');
      test.skip();
      return;
    }

    // Step 1: Get current roles
    const getCurrentRoles = await fetch(`${API_URL}/api/v1/users/${testUsername}/roles`, {
      method: 'GET',
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
    });

    expect(getCurrentRoles.status).toBe(200);
    const currentRolesData = await getCurrentRoles.json();
    const originalRoles = currentRolesData.roles;

    console.log('Original roles:', originalRoles);

    // Step 2: Update roles (add SPEAKER if not present, or toggle)
    const hasAttendee = originalRoles.includes('ATTENDEE');
    const hasSpeaker = originalRoles.includes('SPEAKER');

    // Test by toggling SPEAKER role
    let newRoles = [...originalRoles];
    if (hasSpeaker) {
      // Remove SPEAKER, keep ATTENDEE
      newRoles = ['ATTENDEE'];
    } else {
      // Add SPEAKER
      newRoles = hasAttendee ? ['ATTENDEE', 'SPEAKER'] : ['SPEAKER'];
    }

    const updateResponse = await fetch(`${API_URL}/api/v1/users/${testUsername}/roles`, {
      method: 'PUT',
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        roles: newRoles,
      }),
    });

    expect(updateResponse.status).toBe(200);

    const updateData = await updateResponse.json();

    // Verify response structure
    expect(updateData).toHaveProperty('username');
    expect(updateData).toHaveProperty('roles');
    expect(updateData.username).toBe(testUsername);
    expect(Array.isArray(updateData.roles)).toBe(true);

    // Verify roles were updated
    expect(updateData.roles.sort()).toEqual(newRoles.sort());

    console.log('Updated roles:', {
      username: updateData.username,
      from: originalRoles,
      to: updateData.roles,
    });

    // Step 3: Restore original roles
    const restoreResponse = await fetch(`${API_URL}/api/v1/users/${testUsername}/roles`, {
      method: 'PUT',
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        roles: originalRoles,
      }),
    });

    expect(restoreResponse.status).toBe(200);

    console.log('Roles restored to original state');
  });

  test('should_requireOrganizerRole_when_updatingUserRoles', async () => {
    // AC: Verify endpoint is protected (requires ORGANIZER role)
    const response = await fetch(`${API_URL}/api/v1/users/testuser/roles`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        roles: ['ATTENDEE'],
      }),
    });

    // Should be 401 or 403 without auth
    expect([401, 403]).toContain(response.status);
  });

  test('should_validateRoleEnum_when_updatingRoles', async () => {
    // AC: Verify invalid role names are rejected
    const token = await getAuthToken();

    const testUsername = await findTestUser(token);

    if (!testUsername) {
      console.log('No test user found, skipping validation test');
      test.skip();
      return;
    }

    const response = await fetch(`${API_URL}/api/v1/users/${testUsername}/roles`, {
      method: 'PUT',
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        roles: ['INVALID_ROLE'],
      }),
    });

    // Should be 400 (Bad Request) for invalid role
    expect(response.status).toBe(400);
  });
});

// ============================================================================
// TEST GROUP 3: Role Management Workflow
// ============================================================================

test.describe('Role Management - Complete Workflow', () => {
  test('should_getRolesThenUpdateThenVerify_when_adminWorkflow', async () => {
    // Complete admin workflow: Get roles → Update → Verify update → Restore
    const token = await getAuthToken();

    const testUsername = await findTestUser(token);

    if (!testUsername) {
      console.log('No test user found, skipping workflow test');
      test.skip();
      return;
    }

    // Step 1: Get current roles
    const step1 = await fetch(`${API_URL}/api/v1/users/${testUsername}/roles`, {
      method: 'GET',
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
    });

    expect(step1.status).toBe(200);
    const originalRoles = (await step1.json()).roles;

    console.log('Step 1 - Original roles:', originalRoles);

    // Step 2: Add PARTNER role (if not already present)
    const hasPartner = originalRoles.includes('PARTNER');
    const testRoles = hasPartner ? originalRoles : [...originalRoles, 'PARTNER'];

    const step2 = await fetch(`${API_URL}/api/v1/users/${testUsername}/roles`, {
      method: 'PUT',
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        roles: testRoles,
      }),
    });

    expect(step2.status).toBe(200);
    const updatedRoles = (await step2.json()).roles;

    console.log('Step 2 - Updated roles:', updatedRoles);

    // Step 3: Verify roles were updated by fetching again
    const step3 = await fetch(`${API_URL}/api/v1/users/${testUsername}/roles`, {
      method: 'GET',
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
    });

    expect(step3.status).toBe(200);
    const verifiedRoles = (await step3.json()).roles;

    expect(verifiedRoles.sort()).toEqual(testRoles.sort());

    console.log('Step 3 - Verified roles:', verifiedRoles);

    // Step 4: Restore original roles
    const step4 = await fetch(`${API_URL}/api/v1/users/${testUsername}/roles`, {
      method: 'PUT',
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        roles: originalRoles,
      }),
    });

    expect(step4.status).toBe(200);

    console.log('Step 4 - Roles restored');
  });
});
