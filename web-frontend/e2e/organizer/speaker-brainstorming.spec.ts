/**
 * E2E Tests for Speaker Brainstorming Panel
 * Story 5.2: Topic Selection & Speaker Brainstorming (AC9-13, AC15)
 *
 * IMPORTANT: These tests are RED PHASE tests (TDD). They should FAIL until
 * the Speaker Brainstorming functionality is fully implemented.
 *
 * Requirements:
 * 1. Event Management Service with speaker pool endpoints deployed
 * 2. PostgreSQL database with speaker_pool table (Migration V14)
 * 3. SpeakerBrainstormingPanel component
 * 4. Speaker pool list with assignment tracking
 * 5. SpeakerWorkflowService integration from Story 5.1a
 *
 * Setup Instructions:
 * 1. Ensure migration V14 is applied: speaker_pool table
 * 2. Ensure Event Management Service is running
 * 3. Run: npx playwright test e2e/organizer/speaker-brainstorming.spec.ts
 */

import { test, expect, type Page } from '@playwright/test';

// Test configuration
const BASE_URL = process.env.E2E_BASE_URL || 'http://localhost:8100';
const API_URL = process.env.E2E_API_URL || 'http://localhost:8080';

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

// Type definitions for Story 5.2 API responses
interface SpeakerPoolResponse {
  id: string;
  speakerName: string;
  status: string;
}

/**
 * Helper: Create a test event
 */
async function createTestEvent(page: Page): Promise<string> {
  await page.goto(`${BASE_URL}/organizer/events`);

  // Wait for dashboard to load (Quick Actions sidebar must be visible)
  await page.waitForSelector('[data-testid="quick-actions"]', { timeout: 10000 });

  await page.click('[data-testid="new-event-button"]');

  // Wait for event form modal to open
  await page.waitForSelector('[role="dialog"]', { timeout: 5000 });

  // Fill event form
  await page.fill('input[name="title"]', `E2E Speaker Test ${Date.now()}`);
  await page.fill('input[name="eventNumber"]', '999');
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

  return eventCode || 'BATbern999';
}

test.describe('Speaker Brainstorming Panel (Story 5.2)', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/organizer/events');
  });

  test.describe('AC9: Speaker Pool Management', () => {
    test('should display speaker brainstorming panel in event workflow', async ({ page }) => {
      const eventCode = await createTestEvent(page);

      // Navigate to event details
      await page.goto(`${BASE_URL}/organizer/events/${eventCode}`);

      // Click "Brainstorm Speakers" workflow button
      await page.click('button:has-text("Brainstorm Speakers")');

      // Verify Speaker Brainstorming Panel loads
      await expect(page.locator('h2')).toContainText('Speaker Brainstorming');
      await expect(page.locator('[data-testid="speaker-brainstorming-panel"]')).toBeVisible();
    });

    test('should add potential speaker to pool with name, company, expertise', async ({ page }) => {
      const eventCode = await createTestEvent(page);
      await page.goto(`${BASE_URL}/organizer/events/${eventCode}/speakers/brainstorm`);

      // Fill speaker form
      await page.fill('input[name="speakerName"]', 'Dr. Thomas Müller');
      await page.fill('input[name="company"]', 'Google Zürich');
      await page.fill(
        'textarea[name="expertise"]',
        'Machine Learning, AI Ethics, Cloud Architecture'
      );

      // Click Add Speaker button
      await page.click('button:has-text("Add to Pool")');

      // Verify speaker appears in pool list
      const speakerCard = page.locator(
        '[data-testid="speaker-pool-card"]:has-text("Dr. Thomas Müller")'
      );
      await expect(speakerCard).toBeVisible();
      await expect(speakerCard).toContainText('Google Zürich');
      await expect(speakerCard).toContainText('Machine Learning');
    });

    test('should validate speaker name is required', async ({ page }) => {
      const eventCode = await createTestEvent(page);
      await page.goto(`${BASE_URL}/organizer/events/${eventCode}/speakers/brainstorm`);

      // Try to add speaker without name
      await page.fill('input[name="company"]', 'Test Company');
      await page.click('button:has-text("Add to Pool")');

      // Verify validation error
      await expect(page.locator('[role="alert"]')).toContainText('Speaker name is required');
    });
  });

  test.describe('AC10: Speaker Notes', () => {
    test('should save notes for each potential speaker', async ({ page }) => {
      const eventCode = await createTestEvent(page);
      await page.goto(`${BASE_URL}/organizer/events/${eventCode}/speakers/brainstorm`);

      // Add speaker with notes
      await page.fill('input[name="speakerName"]', 'Anna Schmidt');
      await page.fill('input[name="company"]', 'Microsoft Schweiz');
      await page.fill('textarea[name="expertise"]', 'DevOps, Azure, CI/CD');
      await page.fill(
        'textarea[name="notes"]',
        'Excellent speaker, presented at last 3 events. Very engaging.'
      );

      await page.click('button:has-text("Add to Pool")');

      // Click on speaker card to view details
      await page.click('[data-testid="speaker-pool-card"]:has-text("Anna Schmidt")');

      // Verify notes are displayed
      const notesSection = page.locator('[data-testid="speaker-notes"]');
      await expect(notesSection).toBeVisible();
      await expect(notesSection).toContainText('Excellent speaker');
      await expect(notesSection).toContainText('Very engaging');
    });

    test('should allow editing notes after speaker is added', async ({ page }) => {
      const eventCode = await createTestEvent(page);
      await page.goto(`${BASE_URL}/organizer/events/${eventCode}/speakers/brainstorm`);

      // Add speaker
      await page.fill('input[name="speakerName"]', 'Peter Weber');
      await page.fill('textarea[name="notes"]', 'Initial notes');
      await page.click('button:has-text("Add to Pool")');

      // Click edit button
      await page.click('[data-testid="edit-speaker-notes"]');

      // Update notes
      await page.fill('textarea[name="notes"]', 'Updated notes - confirmed availability');
      await page.click('button:has-text("Save")');

      // Verify updated notes
      await expect(page.locator('[data-testid="speaker-notes"]')).toContainText(
        'confirmed availability'
      );
    });
  });

  test.describe('AC11-12: Assignment Strategy & Contact Distribution', () => {
    test('should assign speaker to specific organizer for outreach', async ({ page }) => {
      const eventCode = await createTestEvent(page);
      await page.goto(`${BASE_URL}/organizer/events/${eventCode}/speakers/brainstorm`);

      // Add speaker
      await page.fill('input[name="speakerName"]', 'Sarah Johnson');
      await page.fill('input[name="company"]', 'AWS');

      // Select organizer for assignment
      await page.click('[data-testid="assigned-organizer-selector"]');
      await page.click('[role="option"]:first'); // Select first organizer from list

      await page.click('button:has-text("Add to Pool")');

      // Verify assignment is displayed
      const speakerCard = page.locator(
        '[data-testid="speaker-pool-card"]:has-text("Sarah Johnson")'
      );
      await expect(speakerCard.locator('[data-testid="assigned-organizer"]')).toBeVisible();
    });

    test('should track contact distribution across organizers', async ({ page }) => {
      const eventCode = await createTestEvent(page);
      await page.goto(`${BASE_URL}/organizer/events/${eventCode}/speakers/brainstorm`);

      // Add multiple speakers assigned to different organizers
      const speakers = [
        { name: 'Speaker 1', organizer: 0 },
        { name: 'Speaker 2', organizer: 0 },
        { name: 'Speaker 3', organizer: 1 },
      ];

      for (const speaker of speakers) {
        await page.fill('input[name="speakerName"]', speaker.name);
        await page.click('[data-testid="assigned-organizer-selector"]');
        await page.click(`[role="option"]:nth-child(${speaker.organizer + 1})`);
        await page.click('button:has-text("Add to Pool")');
      }

      // Check assignment summary
      const summary = page.locator('[data-testid="assignment-summary"]');
      await expect(summary).toBeVisible();

      // Verify distribution is tracked
      await expect(summary).toContainText('2 speakers'); // Organizer 0 has 2 speakers
      await expect(summary).toContainText('1 speaker'); // Organizer 1 has 1 speaker
    });
  });

  test.describe('AC13: Speaker Status - Initial OPEN State', () => {
    test('should set initial status to IDENTIFIED when speaker added to pool', async ({ page }) => {
      const eventCode = await createTestEvent(page);
      await page.goto(`${BASE_URL}/organizer/events/${eventCode}/speakers/brainstorm`);

      // Add speaker
      await page.fill('input[name="speakerName"]', 'Lisa Brown');
      await page.click('button:has-text("Add to Pool")');

      // Verify status badge shows IDENTIFIED
      const statusBadge = page.locator(
        '[data-testid="speaker-pool-card"]:has-text("Lisa Brown") [data-testid="status-badge"]'
      );
      await expect(statusBadge).toContainText('Identified');
      await expect(statusBadge).toHaveCSS('background-color', /rgb\(209, 213, 219\)/); // Gray for identified state
    });

    test('should persist status in database', async ({ page, request }) => {
      const eventCode = await createTestEvent(page);
      await page.goto(`${BASE_URL}/organizer/events/${eventCode}/speakers/brainstorm`);

      // Add speaker
      await page.fill('input[name="speakerName"]', 'Mark Davis');
      await page.click('button:has-text("Add to Pool")');

      // Wait for API call to complete
      await page.waitForResponse(
        (response) =>
          response.url().includes(`/events/${eventCode}/speakers/pool`) && response.status() === 201
      );

      // Fetch speaker pool from API
      const response = await request.get(`${API_URL}/api/v1/events/${eventCode}/speakers/pool`, {
        headers: {
          Authorization: `Bearer ${process.env.E2E_TEST_TOKEN}`,
        },
      });

      expect(response.status()).toBe(200);

      const pool = await response.json();
      const speaker = pool.find((s: SpeakerPoolResponse) => s.speakerName === 'Mark Davis');

      expect(speaker).toBeDefined();
      expect(speaker.status).toBe('identified');
    });
  });

  test.describe('AC15: SpeakerWorkflowService Integration', () => {
    test('should call SpeakerWorkflowService when speaker added to pool', async ({ page }) => {
      const eventCode = await createTestEvent(page);

      // Set up request interception to verify workflow service call
      let workflowServiceCalled = false;
      await page.route('**/api/v1/events/*/speakers/pool', (route) => {
        workflowServiceCalled = true;
        route.continue();
      });

      await page.goto(`${BASE_URL}/organizer/events/${eventCode}/speakers/brainstorm`);

      // Add speaker
      await page.fill('input[name="speakerName"]', 'John Smith');
      await page.click('button:has-text("Add to Pool")');

      // Verify workflow service was called
      await page.waitForTimeout(1000); // Wait for API call
      expect(workflowServiceCalled).toBe(true);
    });
  });

  test.describe('Speaker Pool Display', () => {
    test('should display all speakers in pool for event', async ({ page }) => {
      const eventCode = await createTestEvent(page);
      await page.goto(`${BASE_URL}/organizer/events/${eventCode}/speakers/brainstorm`);

      // Add 3 speakers
      const speakers = ['Speaker A', 'Speaker B', 'Speaker C'];
      for (const speaker of speakers) {
        await page.fill('input[name="speakerName"]', speaker);
        await page.click('button:has-text("Add to Pool")');
      }

      // Verify all speakers are displayed
      for (const speaker of speakers) {
        await expect(
          page.locator(`[data-testid="speaker-pool-card"]:has-text("${speaker}")`)
        ).toBeVisible();
      }
    });

    test('should show empty state when no speakers in pool', async ({ page }) => {
      const eventCode = await createTestEvent(page);
      await page.goto(`${BASE_URL}/organizer/events/${eventCode}/speakers/brainstorm`);

      // Verify empty state message
      await expect(page.locator('[data-testid="speaker-pool-empty"]')).toBeVisible();
      await expect(page.locator('[data-testid="speaker-pool-empty"]')).toContainText(
        'No speakers in pool yet'
      );
    });
  });
});

test.describe('Speaker Pool API Contract Tests (Story 5.2)', () => {
  test.describe('POST /api/v1/events/{eventCode}/speakers/pool', () => {
    test('should add speaker to pool with IDENTIFIED status', async ({ request }) => {
      const eventCode = process.env.E2E_TEST_EVENT_CODE || 'BATbern999';

      const response = await request.post(`${API_URL}/api/v1/events/${eventCode}/speakers/pool`, {
        data: {
          speakerName: 'API Test Speaker',
          company: 'Test Company',
          expertise: 'Testing, QA, Automation',
          assignedOrganizerId: 'test-organizer',
          notes: 'Test notes',
        },
        headers: {
          Authorization: `Bearer ${process.env.E2E_TEST_TOKEN}`,
        },
      });

      expect(response.status()).toBe(201);

      const speaker = await response.json();
      expect(speaker.id).toBeDefined();
      expect(speaker.speakerName).toBe('API Test Speaker');
      expect(speaker.status).toBe('identified'); // AC13, AC15
      expect(speaker.company).toBe('Test Company');
      expect(speaker.expertise).toBe('Testing, QA, Automation');
    });

    test('should return 400 when speaker name missing', async ({ request }) => {
      const eventCode = process.env.E2E_TEST_EVENT_CODE || 'BATbern999';

      const response = await request.post(`${API_URL}/api/v1/events/${eventCode}/speakers/pool`, {
        data: {
          company: 'Test Company',
        },
        headers: {
          Authorization: `Bearer ${process.env.E2E_TEST_TOKEN}`,
        },
      });

      expect(response.status()).toBe(400);
    });
  });

  test.describe('GET /api/v1/events/{eventCode}/speakers/pool', () => {
    test('should return all speakers in pool for event', async ({ request }) => {
      const eventCode = process.env.E2E_TEST_EVENT_CODE || 'BATbern999';

      const response = await request.get(`${API_URL}/api/v1/events/${eventCode}/speakers/pool`, {
        headers: {
          Authorization: `Bearer ${process.env.E2E_TEST_TOKEN}`,
        },
      });

      expect(response.status()).toBe(200);

      const pool = await response.json();
      expect(Array.isArray(pool)).toBe(true);

      if (pool.length > 0) {
        const speaker = pool[0];
        expect(speaker.id).toBeDefined();
        expect(speaker.speakerName).toBeDefined();
        expect(speaker.status).toBeDefined();
        expect(['identified', 'contacted', 'ready', 'accepted', 'declined']).toContain(
          speaker.status
        );
      }
    });
  });
});
