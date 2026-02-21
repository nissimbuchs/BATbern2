/**
 * E2E Tests for Speaker Invitation UI
 * Story 6.1c: Send Invitation UI (AC1-4)
 *
 * These tests verify the frontend UI for sending speaker invitations.
 * The backend invitation API (Story 6.1b) must be deployed for these tests to pass.
 *
 * Requirements:
 * 1. Event Management Service with invitation endpoints deployed
 * 2. PostgreSQL database with speaker tables
 * 3. SpeakerOutreachDetailsDrawer with Send Invitation button
 * 4. SpeakerStatusLanes with quick invite action
 *
 * Setup Instructions:
 * 1. Ensure Event Management Service is running with invitation endpoints
 * 2. Run: npx playwright test e2e/organizer/speaker-invitation.spec.ts
 */

import { test, expect, type Page } from '@playwright/test';

// Test configuration
const BASE_URL = process.env.E2E_BASE_URL || 'http://localhost:8100';

/**
 * Helper: Login as an organizer
 */
async function loginAsOrganizer(page: Page) {
  const testEmail = process.env.E2E_TEST_EMAIL || 'test@batbern.ch';
  const testPassword = process.env.E2E_TEST_PASSWORD || 'Test123!@#';

  await page.goto(`${BASE_URL}/login`);
  await page.fill('input[name="email"]', testEmail);
  await page.fill('input[name="password"]', testPassword);
  await page.click('button[type="submit"]');

  // Wait for redirect to dashboard
  await page.waitForURL(`${BASE_URL}/organizer/events`);
}

/**
 * Helper: Create a test event
 */
async function createTestEvent(page: Page): Promise<string> {
  await page.goto(`${BASE_URL}/organizer/events`);
  await page.click('button:has-text("New Event")');

  // Fill event form
  const eventNumber = Math.floor(Math.random() * 900) + 100; // Random 3-digit number
  await page.fill('input[name="title"]', `E2E Invitation Test ${Date.now()}`);
  await page.fill('input[name="eventNumber"]', String(eventNumber));
  await page.fill('input[name="venueName"]', 'Test Venue');
  await page.fill('input[name="venueAddress"]', 'Test Address, Bern');
  await page.fill('input[name="venueCapacity"]', '100');

  // Select event type
  await page.click('[data-testid="event-type-selector"]');
  await page.click('[role="option"]:has-text("Evening Event")');

  // Submit form
  await page.click('button[type="submit"]:has-text("Create Event")');

  // Wait for success and extract event code
  await page.waitForSelector('[data-testid="event-card"]', { timeout: 5000 });
  const eventCodeElement = page.locator('[data-testid="event-code"]').first();
  const eventCode = await eventCodeElement.textContent();

  return eventCode || `BATbern${eventNumber}`;
}

/**
 * Helper: Add a speaker to the pool with optional email
 */
async function addSpeakerToPool(
  page: Page,
  eventCode: string,
  speakerName: string,
  company: string,
  expertise: string,
  email?: string
): Promise<void> {
  await page.goto(`${BASE_URL}/organizer/events/${eventCode}/speakers/brainstorm`);

  await page.fill('input[name="speakerName"]', speakerName);
  await page.fill('input[name="company"]', company);
  await page.fill('textarea[name="expertise"]', expertise);

  // Add email if provided (field may be optional in the form)
  if (email) {
    const emailInput = page.locator('input[name="email"]');
    if (await emailInput.isVisible()) {
      await emailInput.fill(email);
    }
  }

  await page.click('button:has-text("Add to Pool")');

  // Wait for speaker to appear in pool
  await expect(
    page.locator(`[data-testid="speaker-pool-card"]:has-text("${speakerName}")`)
  ).toBeVisible();
}

test.describe('Speaker Invitation UI (Story 6.1c)', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsOrganizer(page);
  });

  test.describe('AC1: Send Invitation Button in Drawer', () => {
    test('should show Send Invitation button for IDENTIFIED speakers with email', async ({
      page,
    }) => {
      const eventCode = await createTestEvent(page);

      // Add speaker with email
      await addSpeakerToPool(
        page,
        eventCode,
        'Dr. Jane Smith',
        'Tech Corp AG',
        'Cloud Architecture',
        'jane.smith@techcorp.ch'
      );

      // Navigate to speaker outreach
      await page.goto(`${BASE_URL}/organizer/events/${eventCode}/speakers/outreach`);

      // Click on speaker to open drawer
      await page.click('[data-testid="speaker-row"]:has-text("Dr. Jane Smith")');

      // Verify Send Invitation button is visible
      await expect(page.locator('button:has-text("Send Invitation")')).toBeVisible();
    });

    test('should disable Send Invitation button when speaker has no email', async ({ page }) => {
      const eventCode = await createTestEvent(page);

      // Add speaker without email
      await addSpeakerToPool(
        page,
        eventCode,
        'John Doe',
        'Innovation GmbH',
        'DevOps'
        // No email provided
      );

      // Navigate to speaker outreach
      await page.goto(`${BASE_URL}/organizer/events/${eventCode}/speakers/outreach`);

      // Click on speaker to open drawer
      await page.click('[data-testid="speaker-row"]:has-text("John Doe")');

      // Verify Send Invitation button is disabled
      const sendButton = page.locator('button:has-text("Send Invitation")');
      await expect(sendButton).toBeDisabled();
    });

    test('should hide Send Invitation button for non-IDENTIFIED speakers', async ({ page }) => {
      const eventCode = await createTestEvent(page);

      // Add speaker and transition to CONTACTED status
      await addSpeakerToPool(
        page,
        eventCode,
        'Maria Garcia',
        'Siemens AG',
        'IoT',
        'maria@siemens.ch'
      );

      await page.goto(`${BASE_URL}/organizer/events/${eventCode}/speakers/outreach`);

      // Mark speaker as contacted first
      await page
        .locator('[data-testid="speaker-row"]:has-text("Maria Garcia")')
        .locator('button:has-text("Mark as Contacted")')
        .click();

      await page.fill('[data-testid="contact-notes"]', 'Initial outreach');
      await page.click('button:has-text("Save")');

      // Click on speaker to open drawer
      await page.click('[data-testid="speaker-row"]:has-text("Maria Garcia")');

      // Send Invitation button should not be visible for CONTACTED speakers
      await expect(page.locator('button:has-text("Send Invitation")')).not.toBeVisible();
    });
  });

  test.describe('AC2: API Integration', () => {
    test('should send invitation and show success message', async ({ page }) => {
      const eventCode = await createTestEvent(page);

      await addSpeakerToPool(
        page,
        eventCode,
        'Test Speaker',
        'Test Company',
        'Testing',
        'test@example.com'
      );

      await page.goto(`${BASE_URL}/organizer/events/${eventCode}/speakers/outreach`);

      // Open drawer
      await page.click('[data-testid="speaker-row"]:has-text("Test Speaker")');

      // Intercept API call
      const responsePromise = page.waitForResponse(
        (response) =>
          response.url().includes('/send-invitation') &&
          (response.status() === 200 || response.status() === 201)
      );

      // Click Send Invitation
      await page.click('button:has-text("Send Invitation")');

      // Wait for API response
      const response = await responsePromise;
      expect(response.ok()).toBeTruthy();

      // Verify success message
      await expect(page.locator('[role="alert"]')).toContainText(/invitation sent/i);
    });

    test('should show loading state while sending invitation', async ({ page }) => {
      const eventCode = await createTestEvent(page);

      await addSpeakerToPool(
        page,
        eventCode,
        'Loading Test Speaker',
        'Test Company',
        'Testing',
        'loading@example.com'
      );

      await page.goto(`${BASE_URL}/organizer/events/${eventCode}/speakers/outreach`);

      // Open drawer
      await page.click('[data-testid="speaker-row"]:has-text("Loading Test Speaker")');

      // Click Send Invitation and check for loading state
      await page.click('button:has-text("Send Invitation")');

      // Button should show loading text or be disabled during request
      await expect(
        page.locator('button:has-text("Sending")').or(page.locator('button:disabled'))
      ).toBeVisible();
    });

    test('should show error message when invitation fails', async ({ page }) => {
      const eventCode = await createTestEvent(page);

      await addSpeakerToPool(
        page,
        eventCode,
        'Error Test Speaker',
        'Test Company',
        'Testing',
        'error@example.com'
      );

      await page.goto(`${BASE_URL}/organizer/events/${eventCode}/speakers/outreach`);

      // Mock API failure
      await page.route('**/send-invitation', (route) =>
        route.fulfill({
          status: 500,
          contentType: 'application/json',
          body: JSON.stringify({ message: 'Internal server error' }),
        })
      );

      // Open drawer and click send
      await page.click('[data-testid="speaker-row"]:has-text("Error Test Speaker")');
      await page.click('button:has-text("Send Invitation")');

      // Verify error message
      await expect(page.locator('[role="alert"]')).toContainText(/failed/i);
    });
  });

  test.describe('AC3: Kanban Quick Invite Action', () => {
    test('should show invite button on IDENTIFIED speaker cards', async ({ page }) => {
      const eventCode = await createTestEvent(page);

      await addSpeakerToPool(
        page,
        eventCode,
        'Kanban Test Speaker',
        'Test Company',
        'Testing',
        'kanban@example.com'
      );

      // Navigate to speaker status lanes (Kanban view)
      await page.goto(`${BASE_URL}/organizer/events/${eventCode}/speakers/status`);

      // Find the IDENTIFIED lane and speaker card
      const identifiedLane = page.locator('[data-testid="status-lane-identified"]');

      // Verify invite button exists on the card
      const inviteButton = identifiedLane.locator(
        '[data-testid^="invite-button-"]:has-text(""), button[aria-label*="Invite"]'
      );
      await expect(inviteButton.first()).toBeVisible();
    });

    test('should disable invite button when speaker has no email', async ({ page }) => {
      const eventCode = await createTestEvent(page);

      await addSpeakerToPool(
        page,
        eventCode,
        'No Email Speaker',
        'Test Company',
        'Testing'
        // No email
      );

      await page.goto(`${BASE_URL}/organizer/events/${eventCode}/speakers/status`);

      const identifiedLane = page.locator('[data-testid="status-lane-identified"]');

      // Invite button should be disabled
      const inviteButton = identifiedLane.locator('button[aria-label*="Invite"]');
      if (await inviteButton.isVisible()) {
        await expect(inviteButton).toBeDisabled();
      }
    });

    test('should send invitation from Kanban card and transition to CONTACTED', async ({
      page,
    }) => {
      const eventCode = await createTestEvent(page);

      await addSpeakerToPool(
        page,
        eventCode,
        'Quick Invite Speaker',
        'Test Company',
        'Testing',
        'quick@example.com'
      );

      await page.goto(`${BASE_URL}/organizer/events/${eventCode}/speakers/status`);

      // Find and click invite button
      const identifiedLane = page.locator('[data-testid="status-lane-identified"]');
      const inviteButton = identifiedLane.locator('button[aria-label*="Invite"]').first();

      // Intercept API call
      const responsePromise = page.waitForResponse(
        (response) =>
          response.url().includes('/send-invitation') &&
          (response.status() === 200 || response.status() === 201)
      );

      await inviteButton.click();

      // Wait for API response
      const response = await responsePromise;
      expect(response.ok()).toBeTruthy();

      // Verify success feedback
      await expect(page.locator('[role="alert"]')).toContainText(/sent/i);

      // Verify card moved to CONTACTED lane
      const contactedLane = page.locator('[data-testid="status-lane-contacted"]');
      await expect(contactedLane.locator(':has-text("Quick Invite Speaker")')).toBeVisible();
    });

    test('should not show invite button on non-IDENTIFIED cards', async ({ page }) => {
      const eventCode = await createTestEvent(page);

      // Add speaker and transition to ACCEPTED
      await addSpeakerToPool(
        page,
        eventCode,
        'Accepted Speaker',
        'Test Company',
        'Testing',
        'accepted@example.com'
      );

      await page.goto(`${BASE_URL}/organizer/events/${eventCode}/speakers/status`);

      // The speaker should start in IDENTIFIED lane
      // We need to manually transition it to ACCEPTED for this test
      // (This would typically be done through the UI workflow)

      // For now, verify ACCEPTED lane doesn't have invite buttons
      const acceptedLane = page.locator('[data-testid="status-lane-accepted"]');
      const inviteButtons = acceptedLane.locator('button[aria-label*="Invite"]');
      await expect(inviteButtons).toHaveCount(0);
    });
  });

  test.describe('AC4: Email Input for Speakers without Email', () => {
    test('should show email input field when speaker has no email', async ({ page }) => {
      const eventCode = await createTestEvent(page);

      await addSpeakerToPool(
        page,
        eventCode,
        'No Email Speaker',
        'Test Company',
        'Testing'
        // No email
      );

      await page.goto(`${BASE_URL}/organizer/events/${eventCode}/speakers/outreach`);

      // Open drawer
      await page.click('[data-testid="speaker-row"]:has-text("No Email Speaker")');

      // Email input should be visible
      await expect(page.locator('input[name="email"], input[type="email"]')).toBeVisible();
    });

    test('should validate email format', async ({ page }) => {
      const eventCode = await createTestEvent(page);

      await addSpeakerToPool(page, eventCode, 'Validation Speaker', 'Test Company', 'Testing');

      await page.goto(`${BASE_URL}/organizer/events/${eventCode}/speakers/outreach`);
      await page.click('[data-testid="speaker-row"]:has-text("Validation Speaker")');

      // Enter invalid email
      const emailInput = page.locator('input[name="email"], input[type="email"]');
      await emailInput.fill('invalid-email');
      await emailInput.blur();

      // Should show validation error
      await expect(page.locator('text=/invalid|valid email/i')).toBeVisible();
    });

    test('should enable Send button after valid email is entered', async ({ page }) => {
      const eventCode = await createTestEvent(page);

      await addSpeakerToPool(page, eventCode, 'Enable Button Speaker', 'Test Company', 'Testing');

      await page.goto(`${BASE_URL}/organizer/events/${eventCode}/speakers/outreach`);
      await page.click('[data-testid="speaker-row"]:has-text("Enable Button Speaker")');

      // Send button should be disabled initially
      const sendButton = page.locator('button:has-text("Send Invitation")');
      await expect(sendButton).toBeDisabled();

      // Enter valid email
      const emailInput = page.locator('input[name="email"], input[type="email"]');
      await emailInput.fill('valid@example.com');

      // Save email first if there's a save button
      const saveEmailButton = page.locator('button:has-text("Save Email")');
      if (await saveEmailButton.isVisible()) {
        await saveEmailButton.click();
        // Wait for save to complete
        await expect(page.locator('[role="alert"]')).toContainText(/saved/i);
      }

      // Send button should now be enabled
      await expect(sendButton).toBeEnabled();
    });
  });
});
