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
import { BASE_URL, API_URL } from '../../playwright.config';

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

// ─── Story 11.D.1: Promote-to-speaker UI + slot-capacity surfacing ─────────────

test.describe('Promote to speaker (Story 11.D.1)', () => {
  test('should promote a CONTACTED speaker to READY via the brainstorming-panel modal', async ({
    page,
    request,
  }) => {
    const eventCode = await createTestEvent(page);

    // Seed: add a speaker via UI, then transition to CONTACTED via API to bypass the
    // outreach modal flow (covered separately by speaker-outreach.spec.ts).
    await page.goto(`${BASE_URL}/organizer/events/${eventCode}/speakers/brainstorm`);
    await page.fill('input[name="speakerName"]', 'Promote E2E Speaker');
    await page.click('button:has-text("Add to Pool")');

    // Capture the speaker UUID via the same GET the panel uses.
    const poolResp = await request.get(`${API_URL}/api/v1/events/${eventCode}/speakers/pool`, {
      headers: { Authorization: `Bearer ${process.env.E2E_TEST_TOKEN}` },
    });
    const pool: SpeakerPoolResponse[] = await poolResp.json();
    const seeded = pool.find((s) => s.speakerName === 'Promote E2E Speaker');
    expect(seeded).toBeDefined();
    const speakerId = seeded!.id;

    // Promote to CONTACTED via API (outreach UI is owned by speaker-outreach.spec.ts).
    const putResp = await request.put(
      `${API_URL}/api/v1/events/${eventCode}/speakers/${speakerId}/status`,
      {
        data: { newStatus: 'CONTACTED', reason: 'E2E setup' },
        headers: { Authorization: `Bearer ${process.env.E2E_TEST_TOKEN}` },
      }
    );
    expect(putResp.status()).toBe(200);

    // Reload to pick up the new status, then click the promote button.
    await page.reload();
    await page.click(`[data-testid="promote-button-${speakerId}"]`);

    // Modal opens — fill in email and submit.
    await page.fill('[data-testid="promote-email-field"]', 'promote-e2e@batbern-test.ch');
    await page.click('[data-testid="promote-submit-button"]');

    // Speaker's chip should transition to READY without a manual reload (TanStack
    // Query invalidation in usePromoteSpeakerToReady). Scope to the speaker's row
    // so we don't false-match a column header or sibling chip with "READY" text.
    await expect(page.locator(`[data-testid="promote-button-${speakerId}"]`)).not.toBeVisible();
    const speakerRow = page.locator(`[data-testid="speaker-pool-card"]`, {
      has: page.locator(`text=Promote E2E Speaker`),
    });
    await expect(speakerRow.locator('[data-testid="status-badge"]')).toContainText(/ready/i);
  });

  test('should surface slot-capacity error when sending invitation past capacity', async ({
    page,
    request,
  }) => {
    // AC9 Playwright case for Story 11.D.1. Seed an event with the SLOT_CAPACITY_REACHED
    // gate pre-tripped (1 ACCEPTED + 1 READY against maxSlots=1), then click "Send
    // invitation" on the READY speaker and assert the localized toast surfaces while the
    // speaker remains in READY.
    const eventCode = await createTestEvent(page);
    const authHeaders = { Authorization: `Bearer ${process.env.E2E_TEST_TOKEN}` };

    const seedAccepted = await request.post(`${API_URL}/api/v1/events/${eventCode}/speakers/pool`, {
      data: { speakerName: 'Slot Filler Accepted' },
      headers: authHeaders,
    });
    expect(seedAccepted.status()).toBe(201);
    const acceptedId = (await seedAccepted.json()).id as string;

    // Walk the speaker through IDENTIFIED → CONTACTED → READY → INVITED → ACCEPTED
    // using PUT /status. /status rejects READY so we route through /promote there.
    for (const step of ['CONTACTED'] as const) {
      const resp = await request.put(
        `${API_URL}/api/v1/events/${eventCode}/speakers/${acceptedId}/status`,
        { data: { newStatus: step, reason: 'E2E setup' }, headers: authHeaders }
      );
      expect(resp.status()).toBe(200);
    }
    const promoteAccepted = await request.post(
      `${API_URL}/api/v1/events/${eventCode}/speakers/${acceptedId}/promote`,
      { data: { email: 'slot-accepted@batbern-test.ch' }, headers: authHeaders }
    );
    expect(promoteAccepted.status()).toBe(200);
    for (const step of ['INVITED', 'ACCEPTED'] as const) {
      const resp = await request.put(
        `${API_URL}/api/v1/events/${eventCode}/speakers/${acceptedId}/status`,
        { data: { newStatus: step, reason: 'E2E setup' }, headers: authHeaders }
      );
      expect(resp.status()).toBe(200);
    }

    // Seed a second speaker, promote to READY. Capacity is already saturated.
    const seedReady = await request.post(`${API_URL}/api/v1/events/${eventCode}/speakers/pool`, {
      data: { speakerName: 'Slot Candidate Ready' },
      headers: authHeaders,
    });
    expect(seedReady.status()).toBe(201);
    const readyId = (await seedReady.json()).id as string;
    const ctxResp = await request.put(
      `${API_URL}/api/v1/events/${eventCode}/speakers/${readyId}/status`,
      { data: { newStatus: 'CONTACTED', reason: 'E2E setup' }, headers: authHeaders }
    );
    expect(ctxResp.status()).toBe(200);
    const promoteCandidate = await request.post(
      `${API_URL}/api/v1/events/${eventCode}/speakers/${readyId}/promote`,
      { data: { email: 'slot-ready@batbern-test.ch' }, headers: authHeaders }
    );
    expect(promoteCandidate.status()).toBe(200);

    // Navigate and click Send Invitation on the candidate — should surface 409 toast.
    await page.goto(`${BASE_URL}/organizer/events/${eventCode}/speakers/brainstorm`);
    await page.click(`[data-testid="send-invitation-${readyId}"]`);

    await expect(page.getByRole('alert')).toContainText(/slot[\s-]capacity/i);

    // Candidate stays in READY (no state change).
    const finalPool = await request.get(`${API_URL}/api/v1/events/${eventCode}/speakers/pool`, {
      headers: authHeaders,
    });
    const finalList = await finalPool.json();
    const stillReady = finalList.find((s: SpeakerPoolResponse) => s.id === readyId);
    expect(stillReady?.status?.toLowerCase()).toBe('ready');
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
