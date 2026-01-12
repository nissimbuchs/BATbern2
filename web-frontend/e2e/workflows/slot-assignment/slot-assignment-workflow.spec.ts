/**
 * E2E Tests for Slot Assignment Workflow
 * Story BAT-11 (5.7): Slot Assignment & Progressive Publishing (AC1-13)
 *
 * IMPORTANT: These tests are RED PHASE tests (TDD). They should FAIL until
 * the Slot Assignment functionality is fully implemented.
 *
 * Requirements:
 * 1. Event Management Service with session timing endpoints deployed
 * 2. PostgreSQL database with session_timing_history table (Migration V28)
 * 3. SlotAssignmentPage component with drag-and-drop
 * 4. ConflictDetectionAlert modal
 * 5. SpeakerPreferencePanel component
 * 6. API endpoints: PATCH /api/v1/events/{eventCode}/sessions/{sessionSlug}/timing
 *
 * Setup Instructions:
 * 1. Ensure migration V28 is applied: session_timing_history, speaker_slot_preferences tables
 * 2. Ensure Event Management Service is running with slot assignment endpoints
 * 3. Run: npx playwright test e2e/workflows/slot-assignment/slot-assignment-workflow.spec.ts
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

/**
 * Helper: Create event with confirmed speakers (ready for slot assignment)
 */
async function createEventWithConfirmedSpeakers(page: Page): Promise<string> {
  await page.goto(`${BASE_URL}/organizer/events`);
  await page.click('button:has-text("New Event")');

  // Fill event form
  await page.fill('input[name="title"]', `E2E Slot Test ${Date.now()}`);
  await page.fill('input[name="eventNumber"]', '997');
  await page.fill('input[name="eventDate"]', '2025-06-15');
  await page.fill('input[name="venueName"]', 'Test Venue');
  await page.fill('input[name="venueAddress"]', 'Test Address, Bern');
  await page.fill('input[name="venueCapacity"]', '100');

  // Select event type (creates placeholder sessions)
  await page.click('[data-testid="event-type-selector"]');
  await page.click('[role="option"]:has-text("Evening Event")');

  await page.click('button[type="submit"]:has-text("Create Event")');

  // Extract event code
  await page.waitForSelector('[data-testid="event-card"]', { timeout: 5000 });
  const eventCode = await page.locator('[data-testid="event-code"]').first().textContent();

  // Add speakers and move to SLOT_ASSIGNMENT state
  // (This would normally be done through the speaker workflow)
  // For E2E, we use API to set up state
  await setupConfirmedSpeakers(eventCode || 'BATbern997');

  return eventCode || 'BATbern997';
}

/**
 * Helper: Setup confirmed speakers via API (bypasses speaker workflow for testing)
 */
async function setupConfirmedSpeakers(eventCode: string) {
  // Create placeholder sessions with confirmed speakers
  const speakers = [
    { username: 'john.doe', company: 'TechCorp', topic: 'Cloud Architecture' },
    { username: 'jane.smith', company: 'DataInc', topic: 'Machine Learning' },
    { username: 'bob.wilson', company: 'DevOps AG', topic: 'Kubernetes Best Practices' },
  ];

  for (const speaker of speakers) {
    await fetch(`${API_URL}/api/v1/events/${eventCode}/sessions`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        title: `${speaker.username} - ${speaker.company}`,
        description: speaker.topic,
        sessionType: 'presentation',
        // startTime: null, endTime: null (placeholder session)
        speakers: [speaker.username],
      }),
    });
  }
}

test.describe('Slot Assignment Workflow (Story BAT-11)', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/organizer/events');
  });

  test('should assign session timing via drag-and-drop (AC5-AC9)', async ({ page }) => {
    const eventCode = await createEventWithConfirmedSpeakers(page);

    // Navigate from Speakers tab to Slot Assignment page
    await page.goto(`${BASE_URL}/organizer/events/${eventCode}/speakers`);
    await expect(page.locator('[data-testid="assign-timings-button"]')).toBeVisible();
    await page.click('[data-testid="assign-timings-button"]');

    // Should navigate to slot assignment page
    await page.waitForURL(`${BASE_URL}/organizer/events/${eventCode}/slot-assignment`);

    // Verify three-column layout
    await expect(page.locator('[data-testid="speaker-pool-sidebar"]')).toBeVisible();
    await expect(page.locator('[data-testid="session-timeline-grid"]')).toBeVisible();
    await expect(page.locator('[data-testid="quick-actions-panel"]')).toBeVisible();

    // Verify unassigned speakers count
    const progressText = await page.locator('[data-testid="assignment-progress"]').textContent();
    expect(progressText).toContain('0 of 3 assigned');

    // Drag first speaker to a time slot
    const speakerCard = page.locator('[data-testid="speaker-card"]').first();
    const slotDropZone = page.locator('[data-testid="slot-dropzone"][data-time="09:00"]').first();

    await speakerCard.dragTo(slotDropZone);

    // Verify assignment success
    await expect(page.locator('[data-testid="assignment-success-toast"]')).toBeVisible();
    await expect(page.locator('[data-testid="session-timeline-grid"]')).toContainText('john.doe');

    // Verify progress updated
    const updatedProgress = await page.locator('[data-testid="assignment-progress"]').textContent();
    expect(updatedProgress).toContain('1 of 3 assigned');
  });

  test('should detect and resolve timing conflicts (AC9)', async ({ page }) => {
    const eventCode = await createEventWithConfirmedSpeakers(page);
    await page.goto(`${BASE_URL}/organizer/events/${eventCode}/slot-assignment`);

    // Assign first speaker to 09:00-09:45
    const speaker1 = page.locator('[data-testid="speaker-card"]').first();
    const slot1 = page.locator('[data-testid="slot-dropzone"][data-time="09:00"]').first();
    await speaker1.dragTo(slot1);
    await page.waitForTimeout(500); // Wait for assignment to complete

    // Try to assign second speaker to overlapping time (same room, overlapping time)
    const speaker2 = page.locator('[data-testid="speaker-card"]').nth(1);
    const slot2 = page.locator('[data-testid="slot-dropzone"][data-time="09:15"]').first(); // Overlaps with 09:00-09:45

    await speaker2.dragTo(slot2);

    // Conflict detection modal should appear
    await expect(page.locator('[data-testid="conflict-detection-modal"]')).toBeVisible();
    await expect(page.locator('[data-testid="conflict-type"]')).toHaveText('room_overlap');
    await expect(page.locator('[data-testid="conflict-message"]')).toContainText(
      'Session timing conflicts with existing schedule'
    );

    // Verify resolution options
    await expect(page.locator('button:has-text("Find Alternative Slot")')).toBeVisible();
    await expect(page.locator('button:has-text("Change Room")')).toBeVisible();
    await expect(page.locator('button:has-text("Reassign Other Session")')).toBeVisible();
    await expect(page.locator('button:has-text("Cancel")')).toBeVisible();

    // Choose "Change Room" resolution
    await page.click('button:has-text("Change Room")');
    await page.selectOption('[data-testid="room-selector"]', 'Room B');
    await page.click('button:has-text("Confirm Assignment")');

    // Verify conflict resolved and assignment successful
    await expect(page.locator('[data-testid="conflict-detection-modal"]')).not.toBeVisible();
    await expect(page.locator('[data-testid="assignment-success-toast"]')).toBeVisible();
  });

  test('should show speaker time preferences during drag (AC7-AC8, AC11)', async ({ page }) => {
    const eventCode = await createEventWithConfirmedSpeakers(page);
    await page.goto(`${BASE_URL}/organizer/events/${eventCode}/slot-assignment`);

    // Click on speaker card to view preferences
    await page.click('[data-testid="speaker-card"]').first();
    await page.click('[data-testid="view-preferences-button"]');

    // Speaker preference panel should slide in from right
    await expect(page.locator('[data-testid="speaker-preference-panel"]')).toBeVisible();

    // Verify preference sections
    await expect(page.locator('[data-testid="time-preferences-section"]')).toBeVisible();
    await expect(page.locator('[data-testid="av-requirements-section"]')).toBeVisible();
    await expect(page.locator('[data-testid="room-setup-section"]')).toBeVisible();

    // Start dragging speaker - should highlight matching slots
    const speakerCard = page.locator('[data-testid="speaker-card"]').first();
    await speakerCard.hover();
    await page.mouse.down();

    // Verify preference match highlighting (green for good match)
    const morningSlot = page.locator('[data-testid="slot-dropzone"][data-time="09:00"]').first();
    await expect(morningSlot).toHaveClass(/preference-match-high/); // Green highlight

    const eveningSlot = page.locator('[data-testid="slot-dropzone"][data-time="18:00"]').first();
    await expect(eveningSlot).toHaveClass(/preference-match-low/); // Red highlight

    await page.mouse.up();
  });

  test('should use bulk auto-assignment based on preferences (AC13)', async ({ page }) => {
    const eventCode = await createEventWithConfirmedSpeakers(page);
    await page.goto(`${BASE_URL}/organizer/events/${eventCode}/slot-assignment`);

    // Click Auto-Assign All button
    await page.click('[data-testid="auto-assign-all-button"]');

    // Auto-assignment modal should appear
    await expect(page.locator('[data-testid="bulk-auto-assignment-modal"]')).toBeVisible();

    // Step 1: Select algorithm
    await page.click('[data-testid="algorithm-balanced"]'); // Balanced approach
    await page.click('button:has-text("Next")');

    // Step 2: Preview assignments
    await expect(page.locator('[data-testid="assignment-preview-list"]')).toBeVisible();
    await expect(page.locator('[data-testid="preview-item"]')).toHaveCount(3); // 3 speakers

    // Verify match scores are shown
    await expect(page.locator('[data-testid="match-score"]').first()).toBeVisible();

    // Step 3: Confirm and apply
    await page.click('button:has-text("Apply Assignments")');

    // Wait for bulk assignment to complete
    await expect(page.locator('[data-testid="bulk-assignment-success-toast"]')).toBeVisible();
    await expect(page.locator('[data-testid="bulk-auto-assignment-modal"]')).not.toBeVisible();

    // Verify all speakers assigned
    const progressText = await page.locator('[data-testid="assignment-progress"]').textContent();
    expect(progressText).toContain('3 of 3 assigned (100%)');

    // Verify success banner appears
    await expect(page.locator('[data-testid="assignment-complete-banner"]')).toBeVisible();
    await expect(page.locator('[data-testid="assignment-complete-banner"]')).toContainText(
      'All timings assigned!'
    );
    await expect(page.locator('a:has-text("Go to Publishing Tab")')).toBeVisible();
  });

  test('should navigate to publishing tab after completing assignments', async ({ page }) => {
    const eventCode = await createEventWithConfirmedSpeakers(page);
    await page.goto(`${BASE_URL}/organizer/events/${eventCode}/slot-assignment`);

    // Complete all assignments (using auto-assign for speed)
    await page.click('[data-testid="auto-assign-all-button"]');
    await page.click('[data-testid="algorithm-balanced"]');
    await page.click('button:has-text("Next")');
    await page.click('button:has-text("Apply Assignments")');

    // Wait for completion banner
    await expect(page.locator('[data-testid="assignment-complete-banner"]')).toBeVisible();

    // Click "Go to Publishing Tab" link
    await page.click('a:has-text("Go to Publishing Tab")');

    // Should navigate to publishing tab
    await page.waitForURL(`${BASE_URL}/organizer/events/${eventCode}/publishing`);
    await expect(page.locator('[data-testid="publishing-timeline"]')).toBeVisible();

    // Verify session timings validation shows as complete
    await expect(page.locator('[data-testid="validation-session-timings"]')).toHaveClass(
      /validation-passed/
    );
    await expect(page.locator('[data-testid="validation-session-timings-status"]')).toContainText(
      'Ready (3/3 sessions assigned)'
    );
  });
});
