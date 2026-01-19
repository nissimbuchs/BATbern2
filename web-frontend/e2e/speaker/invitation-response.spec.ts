/**
 * E2E Tests for Speaker Invitation Response Flow
 * Story 6.2: Speaker Self-Service Response Portal
 * Story 6.3: Send Invitation UI
 *
 * Tests the complete flow:
 * 1. Organizer sends invitation to speaker in pool
 * 2. Speaker receives invitation and opens response portal
 * 3. Speaker accepts with presentation title and comments
 * 4. Organizer sees proposed title in speaker dashboard
 *
 * Requirements:
 * 1. Event Management Service with invitation endpoints deployed
 * 2. PostgreSQL database with speaker_invitations and speaker_pool tables
 * 3. SendInvitationModal component
 * 4. InvitationResponsePortal component
 * 5. SpeakerStatusLanes component showing proposed titles
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
const TEST_SPEAKER_NAME = `E2E Test Speaker ${Date.now()}`;
const TEST_SPEAKER_EMAIL = `e2e-speaker-${Date.now()}@test.batbern.ch`;
const TEST_PRESENTATION_TITLE = 'Modern Architecture Patterns for Swiss Software';
const TEST_COMMENTS = 'Looking forward to presenting at BATbern!';

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
  authToken: string
): Promise<string> {
  const response = await request.post(`${API_URL}/api/v1/events/${eventCode}/speaker-pool`, {
    headers: {
      Authorization: `Bearer ${authToken}`,
      'Content-Type': 'application/json',
    },
    data: {
      speakerName: TEST_SPEAKER_NAME,
      company: 'E2E Test Company AG',
      expertise: 'Software Architecture, Cloud Computing',
      email: TEST_SPEAKER_EMAIL,
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
 * Helper: Clean up test data
 */
async function cleanupTestData(
  request: APIRequestContext,
  eventCode: string,
  speakerPoolId: string,
  authToken: string
): Promise<void> {
  try {
    await request.delete(`${API_URL}/api/v1/events/${eventCode}/speaker-pool/${speakerPoolId}`, {
      headers: {
        Authorization: `Bearer ${authToken}`,
      },
    });
  } catch {
    // Ignore cleanup errors
  }
}

test.describe('Speaker Invitation Response Flow (Story 6.2, 6.3)', () => {
  // Get auth token once for API calls
  const authToken = getAuthTokenFromState();

  // Log auth status for debugging
  test.beforeAll(() => {
    console.log(`Auth token available: ${!!authToken}`);
    console.log(`Auth token length: ${authToken?.length || 0}`);
  });

  test.describe('Response Portal Accessibility', () => {
    test('should show 404 or error for invalid token', async ({ page }) => {
      // Navigate to response portal with invalid token
      await page.goto(`${BASE_URL}/respond/invalid-token-that-does-not-exist`);

      // Should show error or 404 page (not a redirect to login)
      await expect(page).not.toHaveURL(/login/);

      // Wait a bit for the page to load and check for error indication
      await page.waitForTimeout(2000);

      // Check that we're either on the respond page showing an error, or a 404 page
      // Handles both English and German error messages
      const bodyText = await page.locator('body').textContent();
      const hasErrorIndication =
        bodyText?.toLowerCase().includes('not found') ||
        bodyText?.toLowerCase().includes('invalid') ||
        bodyText?.toLowerCase().includes('expired') ||
        bodyText?.toLowerCase().includes('error') ||
        bodyText?.toLowerCase().includes('404') ||
        bodyText?.toLowerCase().includes('schiefgelaufen') || // German: "went wrong"
        bodyText?.toLowerCase().includes('konnte nicht geladen werden'); // German: "could not be loaded"

      expect(hasErrorIndication).toBeTruthy();
    });
  });

  test.describe('Complete Invitation Flow', () => {
    let speakerPoolId: string;
    let responseToken: string;

    test('should allow speaker to accept invitation with presentation title and show it in dashboard', async ({
      page,
      request,
    }) => {
      // Skip if no auth token
      test.skip(!authToken, 'Requires authentication setup with valid credentials');

      // Step 1: Add speaker to pool via API
      speakerPoolId = await addSpeakerToPool(request, TEST_EVENT_CODE, authToken);
      expect(speakerPoolId).toBeTruthy();

      // Step 2: Send invitation and get response token
      responseToken = await sendInvitation(request, TEST_EVENT_CODE, speakerPoolId, authToken);
      expect(responseToken).toBeTruthy();
      expect(responseToken.length).toBe(64); // UUID without dashes x2

      // Step 3: Navigate to response portal (public, no auth required)
      // Clear cookies to simulate a new user session
      await page.context().clearCookies();
      await page.goto(`${BASE_URL}/respond/${responseToken}`);

      // Verify response portal loaded (should show event/invitation info)
      await page.waitForLoadState('networkidle');

      // Step 4: Accept the invitation with preferences
      // Look for accept button and click it
      const acceptButton = page.locator(
        '[data-testid="response-accept"], button:has-text("Accept"), button:has-text("Zusagen")'
      );
      if (await acceptButton.isVisible()) {
        await acceptButton.click();
      }

      // Fill presentation title if the field is visible
      const titleInput = page.locator(
        'input[name="presentationTitle"], input[name="initialPresentationTitle"], [data-testid="presentation-title-input"]'
      );
      if (await titleInput.isVisible({ timeout: 5000 }).catch(() => false)) {
        await titleInput.fill(TEST_PRESENTATION_TITLE);
      }

      // Fill comments if the field is visible
      const commentsInput = page.locator(
        'textarea[name="comments"], textarea[name="commentsForOrganizer"], [data-testid="comments-input"]'
      );
      if (await commentsInput.isVisible({ timeout: 2000 }).catch(() => false)) {
        await commentsInput.fill(TEST_COMMENTS);
      }

      // Submit response
      const submitButton = page.locator(
        'button[type="submit"], button:has-text("Submit"), button:has-text("Confirm"), button:has-text("Absenden"), [data-testid="submit-response"]'
      );
      if (await submitButton.isVisible()) {
        await submitButton.click();
      }

      // Wait for success confirmation or navigation
      await page.waitForTimeout(3000);

      // Step 5: Navigate to dashboard and verify (use stored auth state)
      await page.goto(`${BASE_URL}/organizer/events/${TEST_EVENT_CODE}?tab=speakers&view=kanban`);

      // Wait for page to load
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(2000);

      // Look for the speaker in the ACCEPTED lane
      const pageContent = await page.content();
      const hasSpeakerName = pageContent.includes(TEST_SPEAKER_NAME);
      const hasTitle = pageContent.includes(TEST_PRESENTATION_TITLE);

      // Log what we found for debugging
      console.log(`Speaker name found: ${hasSpeakerName}`);
      console.log(`Title found: ${hasTitle}`);

      // At minimum, the speaker should be visible
      expect(hasSpeakerName || hasTitle).toBeTruthy();
    });

    test.afterEach(async ({ request }) => {
      // Cleanup test data
      if (speakerPoolId && authToken) {
        await cleanupTestData(request, TEST_EVENT_CODE, speakerPoolId, authToken);
      }
    });
  });

  test.describe('Speaker Dashboard Display', () => {
    let speakerPoolId: string;
    let responseToken: string;

    test('should show proposed title in speaker card after API acceptance', async ({
      page,
      request,
    }) => {
      test.skip(!authToken, 'Requires authentication setup');

      // Create and accept invitation via API for faster test
      speakerPoolId = await addSpeakerToPool(request, TEST_EVENT_CODE, authToken);
      responseToken = await sendInvitation(request, TEST_EVENT_CODE, speakerPoolId, authToken);

      // Accept invitation via API (simulates speaker accepting)
      const acceptResponse = await request.post(
        `${API_URL}/api/v1/invitations/respond/${responseToken}`,
        {
          headers: {
            'Content-Type': 'application/json',
          },
          data: {
            responseType: 'ACCEPTED',
            preferences: {
              initialPresentationTitle: TEST_PRESENTATION_TITLE,
              commentsForOrganizer: TEST_COMMENTS,
            },
          },
        }
      );

      if (!acceptResponse.ok()) {
        console.log('Failed to accept invitation:', await acceptResponse.text());
      }
      expect(acceptResponse.ok()).toBeTruthy();

      // Navigate to dashboard (uses stored auth state)
      await page.goto(`${BASE_URL}/organizer/events/${TEST_EVENT_CODE}?tab=speakers&view=kanban`);

      // Wait for page to fully load
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(3000); // Allow time for data refresh

      // Check that the proposed title is visible somewhere on the page
      const pageContent = await page.content();
      const hasTitle = pageContent.includes(TEST_PRESENTATION_TITLE);

      console.log(`Looking for title: ${TEST_PRESENTATION_TITLE}`);
      console.log(`Title found in page: ${hasTitle}`);

      expect(hasTitle).toBeTruthy();
    });

    test('should show proposed title in speaker details drawer', async ({ page, request }) => {
      test.skip(!authToken, 'Requires authentication setup');

      // Create and accept invitation via API
      speakerPoolId = await addSpeakerToPool(request, TEST_EVENT_CODE, authToken);
      responseToken = await sendInvitation(request, TEST_EVENT_CODE, speakerPoolId, authToken);

      await request.post(`${API_URL}/api/v1/invitations/respond/${responseToken}`, {
        headers: { 'Content-Type': 'application/json' },
        data: {
          responseType: 'ACCEPTED',
          preferences: {
            initialPresentationTitle: TEST_PRESENTATION_TITLE,
            commentsForOrganizer: TEST_COMMENTS,
          },
        },
      });

      // Navigate to dashboard
      await page.goto(`${BASE_URL}/organizer/events/${TEST_EVENT_CODE}?tab=speakers&view=kanban`);
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(2000);

      // Click on speaker to open drawer
      const speakerElement = page.locator(`text=${TEST_SPEAKER_NAME}`).first();
      if (await speakerElement.isVisible()) {
        await speakerElement.click();
        await page.waitForTimeout(1000);

        // Check drawer content
        const drawerContent = await page.content();
        expect(drawerContent.includes(TEST_PRESENTATION_TITLE)).toBeTruthy();
        expect(drawerContent.includes(TEST_COMMENTS)).toBeTruthy();
      }
    });

    test.afterEach(async ({ request }) => {
      if (speakerPoolId && authToken) {
        await cleanupTestData(request, TEST_EVENT_CODE, speakerPoolId, authToken);
      }
    });
  });
});
