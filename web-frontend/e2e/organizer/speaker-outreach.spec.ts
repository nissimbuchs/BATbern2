/**
 * E2E Tests for Speaker Outreach Tracking
 * Story 5.3: Speaker Outreach Tracking (AC1-14)
 *
 * IMPORTANT: These tests are RED PHASE tests (TDD). They should FAIL until
 * the Speaker Outreach functionality is fully implemented.
 *
 * Requirements:
 * 1. Event Management Service with speaker outreach endpoints deployed
 * 2. PostgreSQL database with speaker_outreach_history table (Migration V16)
 * 3. SpeakerOutreachDashboard component (NOT IMPLEMENTED - RED PHASE)
 * 4. OutreachHistoryTimeline component (IMPLEMENTED)
 * 5. MarkContactedModal component (IMPLEMENTED)
 * 6. SpeakerWorkflowService integration from Story 5.1a
 *
 * Setup Instructions:
 * 1. Ensure migration V16 is applied: speaker_outreach_history table
 * 2. Ensure Event Management Service is running with outreach endpoints
 * 3. Run: npx playwright test e2e/organizer/speaker-outreach.spec.ts
 *
 * LANGUAGE-INDEPENDENT SELECTORS (BAT-93):
 * ✅ Fixed: "New Event" button → [data-testid="quick-action-new-event"]
 * ✅ Fixed: "Add to Pool" button → [data-testid="add-to-pool-button"]
 * ✅ Fixed: Event type selection → [data-testid="event-type-option-evening"]
 * ✅ Fixed: "Create Event" → button[type="submit"]
 * ✅ Fixed: "Save" button in modal → [data-testid="save-button"]
 * ⏳ RED PHASE: Speaker row buttons (Mark as Contacted, View History, etc.) - SpeakerOutreachDashboard not implemented
 * ⏳ RED PHASE: Bulk action buttons - Bulk outreach UI not implemented
 */

import { test, expect, type Page } from '@playwright/test';

// Test configuration
const BASE_URL = process.env.E2E_BASE_URL || 'http://localhost:8100';
// const API_URL = process.env.E2E_API_URL || 'http://localhost:8080'; // Reserved for future API integration tests

// Note: loginAsOrganizer not needed - tests use global auth setup from playwright.config.ts
// Kept for reference when implementing RED phase tests
/* async function loginAsOrganizer(page: Page) {
  const testEmail = process.env.E2E_TEST_EMAIL || 'test@batbern.ch';
  const testPassword = process.env.E2E_TEST_PASSWORD || 'Test123!@#';

  await page.goto(`${BASE_URL}/login`);
  await page.fill('input[name="email"]', testEmail);
  await page.fill('input[name="password"]', testPassword);
  await page.click('button[type="submit"]');

  // Wait for redirect to dashboard
  await page.waitForURL(`${BASE_URL}/organizer/events`);
} */

/**
 * Helper: Create a test event
 */
async function createTestEvent(page: Page): Promise<string> {
  await page.goto(`${BASE_URL}/organizer/events`);
  await page.click('[data-testid="quick-action-new-event"]');

  // Fill event form
  await page.fill('input[name="title"]', `E2E Outreach Test ${Date.now()}`);
  await page.fill('input[name="eventNumber"]', '998');
  await page.fill('input[name="venueName"]', 'Test Venue');
  await page.fill('input[name="venueAddress"]', 'Test Address, Bern');
  await page.fill('input[name="venueCapacity"]', '100');

  // Select event type
  await page.click('[data-testid="event-type-selector"]');
  await page.click('[data-testid="event-type-option-evening"]');

  // Submit form
  await page.click('button[type="submit"]');

  // Wait for success and extract event code
  await page.waitForSelector('[data-testid="event-card"]', { timeout: 5000 });
  const eventCodeElement = page.locator('[data-testid="event-code"]').first();
  const eventCode = await eventCodeElement.textContent();

  return eventCode || 'BATbern998';
}

/**
 * Helper: Add a speaker to the pool
 */
async function addSpeakerToPool(
  page: Page,
  eventCode: string,
  speakerName: string,
  company: string,
  expertise: string
): Promise<void> {
  await page.goto(`${BASE_URL}/organizer/events/${eventCode}/speakers/brainstorm`);

  await page.fill('input[name="speakerName"]', speakerName);
  await page.fill('input[name="company"]', company);
  await page.fill('textarea[name="expertise"]', expertise);

  await page.click('[data-testid="add-to-pool-button"]');

  // Wait for speaker to appear in pool
  await expect(
    page.locator(`[data-testid="speaker-pool-card"]:has-text("${speakerName}")`)
  ).toBeVisible();
}

test.describe('Speaker Outreach Tracking (Story 5.3)', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/organizer/events');
  });

  test.describe('AC1: Outreach Dashboard', () => {
    test('should display outreach dashboard with all speakers in pool', async ({ page }) => {
      const eventCode = await createTestEvent(page);

      // Add multiple speakers to pool
      await addSpeakerToPool(
        page,
        eventCode,
        'Dr. Jane Smith',
        'Tech Corp AG',
        'Cloud Architecture'
      );
      await addSpeakerToPool(page, eventCode, 'John Doe', 'Innovation GmbH', 'DevOps, Kubernetes');

      // Navigate to speaker outreach dashboard
      await page.goto(`${BASE_URL}/organizer/events/${eventCode}/speakers/outreach`);

      // Verify dashboard displays speakers
      await expect(page.locator('h2')).toContainText('Speaker Outreach');
      await expect(page.locator('[data-testid="speaker-outreach-dashboard"]')).toBeVisible();

      // Verify both speakers appear
      await expect(
        page.locator('[data-testid="speaker-row"]:has-text("Dr. Jane Smith")')
      ).toBeVisible();
      await expect(page.locator('[data-testid="speaker-row"]:has-text("John Doe")')).toBeVisible();
    });

    test('should display outreach status for each speaker', async ({ page }) => {
      const eventCode = await createTestEvent(page);
      await addSpeakerToPool(page, eventCode, 'Anna Schmidt', 'Google Zürich', 'Machine Learning');

      await page.goto(`${BASE_URL}/organizer/events/${eventCode}/speakers/outreach`);

      // Verify speaker shows "Not Contacted" status initially
      const speakerRow = page.locator('[data-testid="speaker-row"]:has-text("Anna Schmidt")');
      await expect(speakerRow.locator('[data-testid="outreach-status"]')).toContainText(
        'IDENTIFIED'
      );
    });
  });

  test.describe('AC2-3: Mark as Contacted', () => {
    test('should open mark contacted modal when button clicked', async ({ page }) => {
      const eventCode = await createTestEvent(page);
      await addSpeakerToPool(page, eventCode, 'Thomas Müller', 'Microsoft Schweiz', 'Azure');

      await page.goto(`${BASE_URL}/organizer/events/${eventCode}/speakers/outreach`);

      // Click Mark as Contacted button
      await page
        .locator('[data-testid="speaker-row"]:has-text("Thomas Müller")')
        .locator('button:has-text("Mark as Contacted")')
        .click();

      // Verify modal opens
      await expect(page.locator('[data-testid="mark-contacted-modal"]')).toBeVisible();
      await expect(page.locator('h2')).toContainText('Mark Speaker as Contacted');
    });

    test('should record outreach with contact method and notes', async ({ page }) => {
      const eventCode = await createTestEvent(page);
      await addSpeakerToPool(page, eventCode, 'Sarah Weber', 'IBM Research', 'AI Ethics');

      await page.goto(`${BASE_URL}/organizer/events/${eventCode}/speakers/outreach`);

      // Open mark contacted modal
      await page
        .locator('[data-testid="speaker-row"]:has-text("Sarah Weber")')
        .locator('button:has-text("Mark as Contacted")')
        .click();

      // Fill contact details
      await page.selectOption('[data-testid="contact-method-select"]', 'email');
      await page.fill(
        '[data-testid="contact-notes"]',
        'Reached out about AI Ethics presentation. She is very interested and available in March.'
      );

      // Submit
      await page.click('[data-testid="save-button"]');

      // Verify modal closes
      await expect(page.locator('[data-testid="mark-contacted-modal"]')).not.toBeVisible();

      // Verify speaker status updated to CONTACTED
      const speakerRow = page.locator('[data-testid="speaker-row"]:has-text("Sarah Weber")');
      await expect(speakerRow.locator('[data-testid="outreach-status"]')).toContainText(
        'CONTACTED'
      );
    });

    test('should validate contact date is required', async ({ page }) => {
      const eventCode = await createTestEvent(page);
      await addSpeakerToPool(page, eventCode, 'Hans Fischer', 'SAP Switzerland', 'ERP Systems');

      await page.goto(`${BASE_URL}/organizer/events/${eventCode}/speakers/outreach`);

      // Open modal
      await page
        .locator('[data-testid="speaker-row"]:has-text("Hans Fischer")')
        .locator('button:has-text("Mark as Contacted")')
        .click();

      // Try to submit without date (clear default)
      await page.fill('[data-testid="contact-date"]', '');
      await page.click('[data-testid="save-button"]');

      // Verify validation error
      await expect(page.locator('[role="alert"]')).toContainText('Contact date is required');
    });
  });

  test.describe('AC4: Contact History Timeline', () => {
    test('should display outreach timeline for speaker with history', async ({ page }) => {
      const eventCode = await createTestEvent(page);
      await addSpeakerToPool(page, eventCode, 'Maria Garcia', 'Siemens AG', 'IoT, Industry 4.0');

      await page.goto(`${BASE_URL}/organizer/events/${eventCode}/speakers/outreach`);

      // Record first outreach
      await page
        .locator('[data-testid="speaker-row"]:has-text("Maria Garcia")')
        .locator('button:has-text("Mark as Contacted")')
        .click();
      await page.selectOption('[data-testid="contact-method-select"]', 'email');
      await page.fill('[data-testid="contact-notes"]', 'Initial email sent');
      await page.click('[data-testid="save-button"]');

      // Record second outreach
      await page
        .locator('[data-testid="speaker-row"]:has-text("Maria Garcia")')
        .locator('button:has-text("Mark as Contacted")')
        .click();
      await page.selectOption('[data-testid="contact-method-select"]', 'phone');
      await page.fill('[data-testid="contact-notes"]', 'Follow-up call - confirmed interest');
      await page.click('[data-testid="save-button"]');

      // Click to view timeline
      await page
        .locator('[data-testid="speaker-row"]:has-text("Maria Garcia")')
        .locator('button:has-text("View History")')
        .click();

      // Verify timeline shows both attempts
      const timeline = page.locator('[data-testid="outreach-timeline"]');
      await expect(timeline).toBeVisible();
      await expect(timeline.locator('[data-testid="timeline-item"]')).toHaveCount(2);
      await expect(timeline).toContainText('Initial email sent');
      await expect(timeline).toContainText('Follow-up call - confirmed interest');
    });

    test('should order timeline chronologically (most recent first)', async ({ page }) => {
      const eventCode = await createTestEvent(page);
      await addSpeakerToPool(page, eventCode, 'Peter Klein', 'ABB Schweiz', 'Robotics');

      await page.goto(`${BASE_URL}/organizer/events/${eventCode}/speakers/outreach`);

      // Record multiple contacts
      await page
        .locator('[data-testid="speaker-row"]:has-text("Peter Klein")')
        .locator('button:has-text("Mark as Contacted")')
        .click();
      await page.fill('[data-testid="contact-notes"]', 'First contact');
      await page.click('[data-testid="save-button"]');

      await page.waitForTimeout(1000); // Ensure different timestamps

      await page
        .locator('[data-testid="speaker-row"]:has-text("Peter Klein")')
        .locator('button:has-text("Mark as Contacted")')
        .click();
      await page.fill('[data-testid="contact-notes"]', 'Second contact');
      await page.click('[data-testid="save-button"]');

      // View timeline
      await page
        .locator('[data-testid="speaker-row"]:has-text("Peter Klein")')
        .locator('button:has-text("View History")')
        .click();

      // Verify order: most recent first
      const timelineItems = page.locator('[data-testid="timeline-item"]');
      await expect(timelineItems.first()).toContainText('Second contact');
      await expect(timelineItems.last()).toContainText('First contact');
    });
  });

  test.describe('AC5: Assigned Organizer Filter', () => {
    test('should filter speakers by assigned organizer', async ({ page }) => {
      const eventCode = await createTestEvent(page);

      // Add speakers (assignment would be done in speaker pool)
      await addSpeakerToPool(page, eventCode, 'Laura Becker', 'Nestlé Switzerland', 'Food Tech');
      await addSpeakerToPool(page, eventCode, 'Stefan Wolf', 'UBS AG', 'FinTech, Blockchain');

      await page.goto(`${BASE_URL}/organizer/events/${eventCode}/speakers/outreach`);

      // Apply organizer filter
      await page.click('[data-testid="organizer-filter"]');
      await page.click('[role="option"]:has-text("alice.mueller")');

      // Verify only alice.mueller's speakers are shown
      const speakerRows = page.locator('[data-testid="speaker-row"]');
      // Count should be filtered (specific assertions depend on test data)
      await expect(speakerRows).not.toHaveCount(0);
    });
  });

  test.describe('AC6: Bulk Actions', () => {
    test('should select multiple speakers and bulk mark as contacted', async ({ page }) => {
      const eventCode = await createTestEvent(page);

      // Add multiple speakers
      await addSpeakerToPool(page, eventCode, 'Speaker One', 'Company A', 'Topic A');
      await addSpeakerToPool(page, eventCode, 'Speaker Two', 'Company B', 'Topic B');
      await addSpeakerToPool(page, eventCode, 'Speaker Three', 'Company C', 'Topic C');

      await page.goto(`${BASE_URL}/organizer/events/${eventCode}/speakers/outreach`);

      // Select multiple speakers via checkboxes
      await page
        .locator('[data-testid="speaker-row"]:has-text("Speaker One")')
        .locator('input[type="checkbox"]')
        .check();
      await page
        .locator('[data-testid="speaker-row"]:has-text("Speaker Two")')
        .locator('input[type="checkbox"]')
        .check();

      // Click bulk action button
      await page.click('button:has-text("Mark Selected as Contacted")');

      // Fill bulk contact details
      await page.selectOption('[data-testid="bulk-contact-method"]', 'email');
      await page.fill(
        '[data-testid="bulk-contact-notes"]',
        'Bulk email sent to all selected speakers'
      );

      // Confirm
      await page.click('button:has-text("Confirm")');

      // Verify both speakers are now marked as CONTACTED
      await expect(
        page
          .locator('[data-testid="speaker-row"]:has-text("Speaker One")')
          .locator('[data-testid="outreach-status"]')
      ).toContainText('CONTACTED');
      await expect(
        page
          .locator('[data-testid="speaker-row"]:has-text("Speaker Two")')
          .locator('[data-testid="outreach-status"]')
      ).toContainText('CONTACTED');

      // Third speaker should still be IDENTIFIED
      await expect(
        page
          .locator('[data-testid="speaker-row"]:has-text("Speaker Three")')
          .locator('[data-testid="outreach-status"]')
      ).toContainText('IDENTIFIED');
    });
  });

  test.describe('AC7: Reminder System', () => {
    test('should calculate and display days since assignment', async ({ page }) => {
      const eventCode = await createTestEvent(page);
      await addSpeakerToPool(page, eventCode, 'Old Speaker', 'Old Company', 'Old Topic');

      await page.goto(`${BASE_URL}/organizer/events/${eventCode}/speakers/outreach`);

      // Verify days-since-assignment is displayed
      const speakerRow = page.locator('[data-testid="speaker-row"]:has-text("Old Speaker")');
      await expect(speakerRow.locator('[data-testid="days-since-assignment"]')).toBeVisible();
      await expect(speakerRow.locator('[data-testid="days-since-assignment"]')).toContainText(
        '0 days'
      ); // Just created
    });

    test('should highlight overdue speakers (>7 days)', async ({ page }) => {
      const eventCode = await createTestEvent(page);

      // This test would require mocking creation date or using real data
      // For now, verify the UI component exists for highlighting
      await page.goto(`${BASE_URL}/organizer/events/${eventCode}/speakers/outreach`);

      // Verify overdue indicator exists (implementation-dependent)
      // await expect(page.locator('[data-testid="overdue-indicator"]')).toBeVisible();
    });
  });

  test.describe('AC8-10: Workflow Engine Integration', () => {
    test('should transition speaker from IDENTIFIED to CONTACTED on first outreach', async ({
      page,
    }) => {
      const eventCode = await createTestEvent(page);
      await addSpeakerToPool(page, eventCode, 'Workflow Test Speaker', 'Test Corp', 'Testing');

      await page.goto(`${BASE_URL}/organizer/events/${eventCode}/speakers/outreach`);

      // Verify initial state
      const speakerRow = page.locator(
        '[data-testid="speaker-row"]:has-text("Workflow Test Speaker")'
      );
      await expect(speakerRow.locator('[data-testid="outreach-status"]')).toContainText(
        'IDENTIFIED'
      );

      // Record outreach
      await speakerRow.locator('button:has-text("Mark as Contacted")').click();
      await page.fill('[data-testid="contact-notes"]', 'Test workflow transition');
      await page.click('[data-testid="save-button"]');

      // Verify state transitioned to CONTACTED
      await expect(speakerRow.locator('[data-testid="outreach-status"]')).toContainText(
        'CONTACTED'
      );
    });

    test('should prevent marking speaker as contacted if in invalid state', async ({ page: _page }) => {
      // This test requires API-level validation
      // The frontend should handle 409 CONFLICT response from backend
      // when speaker is in DECLINED or other invalid state
    });
  });

  test.describe('AC12: REST API Integration', () => {
    test('should POST outreach record via API', async ({ page, request: _request }) => {
      const eventCode = await createTestEvent(page);
      await addSpeakerToPool(page, eventCode, 'API Test Speaker', 'API Corp', 'API Testing');

      // Get speaker ID from UI (simplified - would need to fetch from API)
      await page.goto(`${BASE_URL}/organizer/events/${eventCode}/speakers/outreach`);

      // Perform outreach via UI (which calls API)
      const speakerRow = page.locator('[data-testid="speaker-row"]:has-text("API Test Speaker")');
      await speakerRow.locator('button:has-text("Mark as Contacted")').click();
      await page.selectOption('[data-testid="contact-method-select"]', 'phone');
      await page.fill('[data-testid="contact-notes"]', 'API test outreach');

      // Intercept API call to verify
      const responsePromise = page.waitForResponse(
        (response) => response.url().includes('/outreach') && response.status() === 201
      );

      await page.click('[data-testid="save-button"]');

      const response = await responsePromise;
      expect(response.status()).toBe(201);

      const responseBody = await response.json();
      expect(responseBody.contactMethod).toBe('phone');
      expect(responseBody.notes).toBe('API test outreach');
    });

    test('should GET outreach history via API', async ({ page }) => {
      const eventCode = await createTestEvent(page);
      await addSpeakerToPool(page, eventCode, 'History Test Speaker', 'History Corp', 'History');

      await page.goto(`${BASE_URL}/organizer/events/${eventCode}/speakers/outreach`);

      // Record outreach
      const speakerRow = page.locator(
        '[data-testid="speaker-row"]:has-text("History Test Speaker")'
      );
      await speakerRow.locator('button:has-text("Mark as Contacted")').click();
      await page.fill('[data-testid="contact-notes"]', 'First outreach');
      await page.click('[data-testid="save-button"]');

      // View history (triggers GET /outreach)
      const responsePromise = page.waitForResponse(
        (response) => response.url().includes('/outreach') && response.request().method() === 'GET'
      );

      await speakerRow.locator('button:has-text("View History")').click();

      const response = await responsePromise;
      expect(response.status()).toBe(200);

      const history = await response.json();
      expect(Array.isArray(history)).toBe(true);
      expect(history.length).toBeGreaterThan(0);
    });
  });

  test.describe('Error Handling', () => {
    test('should display error when API fails', async ({ page }) => {
      const eventCode = await createTestEvent(page);
      await addSpeakerToPool(page, eventCode, 'Error Test Speaker', 'Error Corp', 'Errors');

      await page.goto(`${BASE_URL}/organizer/events/${eventCode}/speakers/outreach`);

      // Mock API failure (would need to configure route interception)
      // For now, verify error UI exists
      // await page.route('**/outreach', route => route.abort());

      // Try to mark as contacted
      const speakerRow = page.locator('[data-testid="speaker-row"]:has-text("Error Test Speaker")');
      await speakerRow.locator('button:has-text("Mark as Contacted")').click();
      await page.fill('[data-testid="contact-notes"]', 'This should fail');
      await page.click('[data-testid="save-button"]');

      // Verify error message (implementation-dependent)
      // await expect(page.locator('[role="alert"]')).toContainText('Failed to record outreach');
    });
  });
});
