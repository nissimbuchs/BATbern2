/**
 * E2E Tests for Speaker Account Linking Flow
 * Story 6.3: Speaker Account Creation and Linking
 *
 * Tests the complete speaker account linking flow:
 * 1. Account creation CTA displayed after invitation acceptance
 * 2. Manual linking endpoint for organizers
 * 3. Idempotent linking behavior
 * 4. Error handling for already-linked speakers
 *
 * Requirements:
 * 1. Event Management Service with speaker pool and linking endpoints deployed
 * 2. PostgreSQL database with speaker_pool table (with username column)
 * 3. SpeakerResponsePage component with account creation CTA
 *
 * Note: Uses global auth state from global-setup.ts - no manual login needed
 */

import { test, expect, type APIRequestContext } from '@playwright/test';
import * as fs from 'fs';

// Test configuration
const BASE_URL = process.env.E2E_BASE_URL || 'http://localhost:8100';
const API_URL = process.env.E2E_API_URL || 'http://localhost:8000';

// Test data - uses environment variable or fallback
const TEST_EVENT_CODE = process.env.E2E_TEST_EVENT_CODE || 'BATbern999';
const TEST_SPEAKER_NAME = `E2E Link Test Speaker ${Date.now()}`;
const TEST_SPEAKER_EMAIL = `e2e-link-speaker-${Date.now()}@test.batbern.ch`;
const TEST_USERNAME = 'e2e.test.user';

/**
 * Helper: Get auth token from the saved auth state
 */
function getAuthTokenFromState(): string {
  try {
    const authState = JSON.parse(fs.readFileSync('.playwright-auth-state.json', 'utf-8'));
    // Look for Cognito token in localStorage
    for (const item of authState.origins?.[0]?.localStorage || []) {
      if (item.name.includes('accessToken') || item.name.includes('idToken')) {
        return item.value;
      }
      // Amplify V6 format stores tokens in a complex structure
      if (item.name.includes('CognitoIdentityServiceProvider') && item.name.includes('idToken')) {
        return item.value;
      }
    }
    // Try to find token in the first CognitoIdentityServiceProvider entry
    for (const item of authState.origins?.[0]?.localStorage || []) {
      if (item.name.includes('CognitoIdentityServiceProvider')) {
        try {
          const parsed = JSON.parse(item.value);
          if (parsed.idToken) return parsed.idToken;
          if (parsed.accessToken) return parsed.accessToken;
        } catch {
          // Not JSON, check if it's directly a token
          if (item.value.startsWith('eyJ')) {
            return item.value;
          }
        }
      }
    }
  } catch (e) {
    console.log('Could not read auth state:', e);
  }
  return '';
}

/**
 * Helper: Add speaker to pool via API
 */
async function addSpeakerToPool(
  request: APIRequestContext,
  eventCode: string,
  authToken: string,
  speakerName: string = TEST_SPEAKER_NAME,
  email: string = TEST_SPEAKER_EMAIL
): Promise<string> {
  const response = await request.post(`${API_URL}/api/v1/events/${eventCode}/speakers/pool`, {
    headers: {
      Authorization: `Bearer ${authToken}`,
      'Content-Type': 'application/json',
    },
    data: {
      speakerName: speakerName,
      company: 'E2E Test Company AG',
      expertise: 'Software Architecture, Cloud Computing',
      email: email,
    },
  });

  if (!response.ok()) {
    console.log('Failed to add speaker to pool:', response.status(), await response.text());
  }
  expect(response.ok()).toBeTruthy();
  const speakerPool = await response.json();
  return speakerPool.id;
}

/**
 * Helper: Send invitation via API and get response token
 */
async function sendInvitation(
  request: APIRequestContext,
  eventCode: string,
  speakerPoolId: string,
  authToken: string
): Promise<string> {
  const response = await request.post(`${API_URL}/api/v1/events/${eventCode}/invitations`, {
    headers: {
      Authorization: `Bearer ${authToken}`,
      'Content-Type': 'application/json',
    },
    data: {
      speakerPoolId: speakerPoolId,
      personalMessage: 'We would love to have you speak at BATbern!',
      expirationDays: 14,
    },
  });

  if (!response.ok()) {
    console.log('Failed to send invitation:', response.status(), await response.text());
  }
  expect(response.ok()).toBeTruthy();
  const invitation = await response.json();
  return invitation.responseToken;
}

/**
 * Helper: Accept invitation via API
 */
async function acceptInvitation(request: APIRequestContext, responseToken: string): Promise<void> {
  const response = await request.post(`${API_URL}/api/v1/invitations/respond/${responseToken}`, {
    headers: {
      'Content-Type': 'application/json',
    },
    data: {
      responseType: 'ACCEPTED',
      preferences: {
        initialPresentationTitle: 'E2E Test Presentation',
        commentsForOrganizer: 'Looking forward to it!',
      },
    },
  });

  if (!response.ok()) {
    console.log('Failed to accept invitation:', response.status(), await response.text());
  }
  expect(response.ok()).toBeTruthy();
}

/**
 * Helper: Link speaker pool entry to user via API
 */
async function linkSpeakerToUser(
  request: APIRequestContext,
  eventCode: string,
  speakerPoolId: string,
  username: string,
  authToken: string
): Promise<{ ok: boolean; status: number; body: unknown }> {
  const response = await request.post(
    `${API_URL}/api/v1/events/${eventCode}/speakers/pool/${speakerPoolId}/link`,
    {
      headers: {
        Authorization: `Bearer ${authToken}`,
        'Content-Type': 'application/json',
      },
      data: {
        username: username,
      },
    }
  );

  return {
    ok: response.ok(),
    status: response.status(),
    body: await response.json().catch(() => null),
  };
}

/**
 * Helper: Get speaker pool entry via API
 */
async function getSpeakerPoolEntry(
  request: APIRequestContext,
  eventCode: string,
  speakerPoolId: string,
  authToken: string
): Promise<{ username: string | null; speakerName: string }> {
  const response = await request.get(`${API_URL}/api/v1/events/${eventCode}/speakers/pool`, {
    headers: {
      Authorization: `Bearer ${authToken}`,
    },
  });

  expect(response.ok()).toBeTruthy();
  const pool = await response.json();
  const entry = pool.find((s: { id: string }) => s.id === speakerPoolId);
  return entry || { username: null, speakerName: '' };
}

/**
 * Helper: Clean up test data
 */
async function cleanupTestData(
  request: APIRequestContext,
  eventCode: string,
  speakerPoolId: string,
  authToken: string
): Promise<void> {
  try {
    await request.delete(`${API_URL}/api/v1/events/${eventCode}/speakers/pool/${speakerPoolId}`, {
      headers: {
        Authorization: `Bearer ${authToken}`,
      },
    });
  } catch {
    // Ignore cleanup errors
  }
}

test.describe('Speaker Account Linking (Story 6.3)', () => {
  // Get auth token once for API calls
  const authToken = getAuthTokenFromState();

  // Log auth status for debugging
  test.beforeAll(() => {
    console.log(`Auth token available: ${!!authToken}`);
    console.log(`Auth token length: ${authToken?.length || 0}`);
  });

  test.describe('Account Creation CTA Display', () => {
    let speakerPoolId: string;
    let responseToken: string;

    test('should display account creation CTA after accepting invitation', async ({
      page,
      request,
    }) => {
      test.skip(!authToken, 'Requires authentication setup with valid credentials');

      // Step 1: Add speaker to pool via API
      speakerPoolId = await addSpeakerToPool(request, TEST_EVENT_CODE, authToken);
      expect(speakerPoolId).toBeTruthy();

      // Step 2: Send invitation and get response token
      responseToken = await sendInvitation(request, TEST_EVENT_CODE, speakerPoolId, authToken);
      expect(responseToken).toBeTruthy();

      // Step 3: Navigate to response portal (public, no auth required)
      await page.context().clearCookies();
      await page.goto(`${BASE_URL}/respond/${responseToken}`);
      await page.waitForLoadState('networkidle');

      // Step 4: Accept the invitation
      const acceptButton = page.locator(
        '[data-testid="response-accept"], button:has-text("Accept"), button:has-text("Zusagen")'
      );
      if (await acceptButton.isVisible()) {
        await acceptButton.click();
      }

      // Fill presentation title if visible
      const titleInput = page.locator(
        'input[name="presentationTitle"], input[name="initialPresentationTitle"], [data-testid="presentation-title-input"]'
      );
      if (await titleInput.isVisible({ timeout: 3000 }).catch(() => false)) {
        await titleInput.fill('E2E Test Presentation');
      }

      // Submit response
      const submitButton = page.locator(
        'button[type="submit"], button:has-text("Submit"), button:has-text("Confirm"), button:has-text("Absenden")'
      );
      if (await submitButton.isVisible()) {
        await submitButton.click();
      }

      // Wait for success page
      await page.waitForTimeout(3000);

      // Step 5: Verify account creation CTA is displayed
      // Look for the CTA card with UserPlus icon or create account text
      const ctaVisible = await page
        .locator(
          '[data-testid="create-account-cta"], ' +
            'text=Create an account, ' +
            'text=Create Account, ' +
            'text=Konto erstellen'
        )
        .isVisible({ timeout: 5000 })
        .catch(() => false);

      // Also check for the registration link with pre-filled email
      const registrationLink = page.locator(
        `a[href*="/auth/register"][href*="email="], a[href*="flow=speaker"]`
      );
      const hasRegistrationLink = await registrationLink
        .isVisible({ timeout: 3000 })
        .catch(() => false);

      console.log(`CTA visible: ${ctaVisible}`);
      console.log(`Registration link visible: ${hasRegistrationLink}`);

      // At least one of these should be true
      expect(ctaVisible || hasRegistrationLink).toBeTruthy();
    });

    test('should pre-fill email in registration link', async ({ page, request }) => {
      test.skip(!authToken, 'Requires authentication setup with valid credentials');

      const uniqueEmail = `e2e-prefill-${Date.now()}@test.batbern.ch`;

      // Create speaker with specific email
      speakerPoolId = await addSpeakerToPool(
        request,
        TEST_EVENT_CODE,
        authToken,
        `Prefill Test Speaker ${Date.now()}`,
        uniqueEmail
      );
      responseToken = await sendInvitation(request, TEST_EVENT_CODE, speakerPoolId, authToken);

      // Accept invitation via API (faster)
      await acceptInvitation(request, responseToken);

      // Navigate to success page by visiting response portal after acceptance
      await page.context().clearCookies();
      await page.goto(`${BASE_URL}/respond/${responseToken}`);
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(2000);

      // Check that registration link contains the email
      const pageContent = await page.content();
      const hasEmailInLink = pageContent.includes(encodeURIComponent(uniqueEmail));

      console.log(`Email in link: ${hasEmailInLink}`);
      console.log(`Looking for: ${encodeURIComponent(uniqueEmail)}`);

      expect(hasEmailInLink).toBeTruthy();
    });

    test.afterEach(async ({ request }) => {
      if (speakerPoolId && authToken) {
        await cleanupTestData(request, TEST_EVENT_CODE, speakerPoolId, authToken);
      }
    });
  });

  test.describe('Manual Linking API Endpoint', () => {
    let speakerPoolId: string;

    test('should successfully link speaker pool entry to user', async ({ request }) => {
      test.skip(!authToken, 'Requires authentication setup');

      // Create speaker pool entry
      speakerPoolId = await addSpeakerToPool(request, TEST_EVENT_CODE, authToken);

      // Link to user
      const linkResult = await linkSpeakerToUser(
        request,
        TEST_EVENT_CODE,
        speakerPoolId,
        TEST_USERNAME,
        authToken
      );

      expect(linkResult.ok).toBeTruthy();
      expect(linkResult.status).toBe(200);

      // Verify the link persisted
      const entry = await getSpeakerPoolEntry(request, TEST_EVENT_CODE, speakerPoolId, authToken);
      expect(entry.username).toBe(TEST_USERNAME);
    });

    test('should be idempotent when linking same user twice', async ({ request }) => {
      test.skip(!authToken, 'Requires authentication setup');

      // Create speaker pool entry
      speakerPoolId = await addSpeakerToPool(request, TEST_EVENT_CODE, authToken);

      // Link to user first time
      const firstLink = await linkSpeakerToUser(
        request,
        TEST_EVENT_CODE,
        speakerPoolId,
        TEST_USERNAME,
        authToken
      );
      expect(firstLink.ok).toBeTruthy();

      // Link to same user second time (should succeed without error)
      const secondLink = await linkSpeakerToUser(
        request,
        TEST_EVENT_CODE,
        speakerPoolId,
        TEST_USERNAME,
        authToken
      );
      expect(secondLink.ok).toBeTruthy();
      expect(secondLink.status).toBe(200);

      // Verify username is still correct
      const entry = await getSpeakerPoolEntry(request, TEST_EVENT_CODE, speakerPoolId, authToken);
      expect(entry.username).toBe(TEST_USERNAME);
    });

    test('should reject linking to different user when already linked', async ({ request }) => {
      test.skip(!authToken, 'Requires authentication setup');

      // Create speaker pool entry
      speakerPoolId = await addSpeakerToPool(request, TEST_EVENT_CODE, authToken);

      // Link to first user
      const firstLink = await linkSpeakerToUser(
        request,
        TEST_EVENT_CODE,
        speakerPoolId,
        'first.user',
        authToken
      );
      expect(firstLink.ok).toBeTruthy();

      // Try to link to different user (should fail)
      const secondLink = await linkSpeakerToUser(
        request,
        TEST_EVENT_CODE,
        speakerPoolId,
        'different.user',
        authToken
      );
      expect(secondLink.ok).toBeFalsy();
      expect(secondLink.status).toBe(400);

      // Verify original link is preserved
      const entry = await getSpeakerPoolEntry(request, TEST_EVENT_CODE, speakerPoolId, authToken);
      expect(entry.username).toBe('first.user');
    });

    test('should return 404 for non-existent speaker pool entry', async ({ request }) => {
      test.skip(!authToken, 'Requires authentication setup');

      const fakeId = '00000000-0000-0000-0000-000000000000';
      const result = await linkSpeakerToUser(
        request,
        TEST_EVENT_CODE,
        fakeId,
        TEST_USERNAME,
        authToken
      );

      expect(result.ok).toBeFalsy();
      // Could be 400 or 404 depending on implementation
      expect([400, 404]).toContain(result.status);
    });

    test('should return 404 for non-existent event', async ({ request }) => {
      test.skip(!authToken, 'Requires authentication setup');

      // Create speaker pool entry in valid event
      speakerPoolId = await addSpeakerToPool(request, TEST_EVENT_CODE, authToken);

      // Try to link using wrong event code
      const result = await linkSpeakerToUser(
        request,
        'NONEXISTENT999',
        speakerPoolId,
        TEST_USERNAME,
        authToken
      );

      expect(result.ok).toBeFalsy();
      expect(result.status).toBe(404);
    });

    test.afterEach(async ({ request }) => {
      if (speakerPoolId && authToken) {
        await cleanupTestData(request, TEST_EVENT_CODE, speakerPoolId, authToken);
      }
    });
  });

  test.describe('Speaker Pool Response includes username', () => {
    let speakerPoolId: string;

    test('should include username field in speaker pool response after linking', async ({
      request,
    }) => {
      test.skip(!authToken, 'Requires authentication setup');

      // Create and link speaker
      speakerPoolId = await addSpeakerToPool(request, TEST_EVENT_CODE, authToken);
      await linkSpeakerToUser(request, TEST_EVENT_CODE, speakerPoolId, TEST_USERNAME, authToken);

      // Get speaker pool list
      const response = await request.get(
        `${API_URL}/api/v1/events/${TEST_EVENT_CODE}/speakers/pool`,
        {
          headers: {
            Authorization: `Bearer ${authToken}`,
          },
        }
      );

      expect(response.ok()).toBeTruthy();
      const pool = await response.json();

      // Find our speaker
      const speaker = pool.find((s: { id: string }) => s.id === speakerPoolId);
      expect(speaker).toBeDefined();
      expect(speaker.username).toBe(TEST_USERNAME);
    });

    test('should have null username for unlinked speaker', async ({ request }) => {
      test.skip(!authToken, 'Requires authentication setup');

      // Create speaker without linking
      speakerPoolId = await addSpeakerToPool(request, TEST_EVENT_CODE, authToken);

      // Get speaker pool list
      const response = await request.get(
        `${API_URL}/api/v1/events/${TEST_EVENT_CODE}/speakers/pool`,
        {
          headers: {
            Authorization: `Bearer ${authToken}`,
          },
        }
      );

      expect(response.ok()).toBeTruthy();
      const pool = await response.json();

      // Find our speaker
      const speaker = pool.find((s: { id: string }) => s.id === speakerPoolId);
      expect(speaker).toBeDefined();
      expect(speaker.username).toBeNull();
    });

    test.afterEach(async ({ request }) => {
      if (speakerPoolId && authToken) {
        await cleanupTestData(request, TEST_EVENT_CODE, speakerPoolId, authToken);
      }
    });
  });
});
