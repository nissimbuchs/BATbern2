/**
 * E2E Tests for Progressive Publishing Workflow
 * Story BAT-11 (5.7): Slot Assignment & Progressive Publishing (AC14-29)
 *
 * IMPORTANT: These tests are RED PHASE tests (TDD). They should FAIL until
 * the Progressive Publishing functionality is fully implemented.
 *
 * Requirements:
 * 1. Event Management Service with publishing endpoints deployed
 * 2. PostgreSQL database with publishing_versions, publishing_config tables (Migration V28)
 * 3. PublishingTimeline component
 * 4. ValidationDashboard component with session timing validation
 * 5. PublishingControls component
 * 6. VersionControl component with rollback capability
 * 7. CloudFront CDN integration
 * 8. AWS SES integration for newsletter distribution
 * 9. API endpoints: POST /api/v1/events/{eventCode}/publish/{phase}
 *
 * Setup Instructions:
 * 1. Ensure migration V28 is applied: publishing_versions, publishing_config tables
 * 2. Ensure Event Management Service is running with publishing endpoints
 * 3. Configure CloudFront distribution ID and SES verified domain
 * 4. Run: npx playwright test e2e/workflows/progressive-publishing/progressive-publishing-workflow.spec.ts
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

  await page.waitForURL(`${BASE_URL}/organizer/events`);
}

/**
 * Helper: Create event ready for publishing
 */
async function createEventReadyForPublishing(page: Page): Promise<string> {
  await page.goto(`${BASE_URL}/organizer/events`);
  await page.click('button:has-text("New Event")');

  await page.fill('input[name="title"]', `E2E Publishing Test ${Date.now()}`);
  await page.fill('input[name="eventNumber"]', '996');
  await page.fill('input[name="eventDate"]', '2025-06-15');
  await page.fill('input[name="venueName"]', 'Test Venue');
  await page.fill('input[name="venueAddress"]', 'Kornhausplatz 18, 3011 Bern');
  await page.fill('input[name="venueCapacity"]', '100');

  await page.click('[data-testid="event-type-selector"]');
  await page.click('[role="option"]:has-text("Evening Event")');

  await page.click('button[type="submit"]:has-text("Create Event")');

  await page.waitForSelector('[data-testid="event-card"]', { timeout: 5000 });
  const eventCode = await page.locator('[data-testid="event-code"]').first().textContent();

  // Setup event with confirmed speakers and assigned timings
  await setupEventWithTimings(eventCode || 'BATbern996');

  return eventCode || 'BATbern996';
}

/**
 * Helper: Setup event with sessions and timing assignments via API
 */
async function setupEventWithTimings(eventCode: string) {
  const speakers = [
    {
      username: 'john.doe',
      company: 'TechCorp',
      topic: 'Cloud Architecture',
      time: '18:00',
      room: 'Main Hall',
    },
    {
      username: 'jane.smith',
      company: 'DataInc',
      topic: 'Machine Learning',
      time: '19:00',
      room: 'Main Hall',
    },
    {
      username: 'bob.wilson',
      company: 'DevOps AG',
      topic: 'Kubernetes',
      time: '20:00',
      room: 'Main Hall',
    },
  ];

  for (const speaker of speakers) {
    // Create session with timing assigned
    await fetch(`${API_URL}/api/v1/events/${eventCode}/sessions`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        title: `${speaker.username} - ${speaker.company}`,
        description: speaker.topic,
        sessionType: 'presentation',
        startTime: `2025-06-15T${speaker.time}:00Z`,
        endTime: `2025-06-15T${speaker.time.split(':')[0]}:45:00Z`, // 45 min duration
        room: speaker.room,
        speakers: [speaker.username],
      }),
    });
  }
}

test.describe('Progressive Publishing Workflow (Story BAT-11)', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/organizer/events');
  });

  test('should publish Phase 3 (Agenda) after validation passes (AC14-AC17, AC21)', async ({
    page,
  }) => {
    const eventCode = await createEventReadyForPublishing(page);

    // Navigate to Publishing tab
    await page.goto(`${BASE_URL}/organizer/events/${eventCode}/publishing`);

    // Verify publishing timeline visualization
    await expect(page.locator('[data-testid="publishing-timeline"]')).toBeVisible();
    await expect(page.locator('[data-testid="phase-topic"]')).toBeVisible();
    await expect(page.locator('[data-testid="phase-speakers"]')).toBeVisible();
    await expect(page.locator('[data-testid="phase-agenda"]')).toBeVisible();

    // Verify validation dashboard
    await expect(page.locator('[data-testid="validation-dashboard"]')).toBeVisible();

    // Check Topic validation (should be ready)
    await expect(page.locator('[data-testid="validation-topic"]')).toHaveClass(/validation-passed/);

    // Check Speakers validation (should be ready)
    await expect(page.locator('[data-testid="validation-speakers"]')).toHaveClass(
      /validation-passed/
    );
    await expect(page.locator('[data-testid="validation-speakers-status"]')).toContainText(
      'Ready (3 speakers confirmed)'
    );

    // Check Session Timings validation (should be ready - blocks Agenda phase)
    await expect(page.locator('[data-testid="validation-session-timings"]')).toHaveClass(
      /validation-passed/
    );
    await expect(page.locator('[data-testid="validation-session-timings-status"]')).toContainText(
      'Ready (3/3 sessions assigned)'
    );

    // Phase 3 (Agenda) publish button should be enabled
    await expect(page.locator('[data-testid="publish-agenda-button"]')).toBeEnabled();

    // Click publish Agenda
    await page.click('[data-testid="publish-agenda-button"]');

    // Confirmation modal should appear
    await expect(page.locator('[data-testid="publish-confirmation-modal"]')).toBeVisible();
    await expect(page.locator('[data-testid="publish-phase-name"]')).toHaveText('agenda');
    await expect(page.locator('[data-testid="notify-subscribers-checkbox"]')).toBeChecked(); // Default true

    // Confirm publish
    await page.click('button:has-text("Confirm Publish")');

    // Wait for publishing to complete
    await expect(page.locator('[data-testid="publishing-in-progress"]')).toBeVisible();
    await expect(page.locator('[data-testid="cdn-invalidation-status"]')).toContainText(
      'CDN cache invalidating'
    );

    // Wait for success
    await expect(page.locator('[data-testid="publish-success-toast"]')).toBeVisible({
      timeout: 10000,
    });
    await expect(page.locator('[data-testid="publish-success-toast"]')).toContainText(
      'Agenda published successfully'
    );

    // Verify CDN invalidation completed
    await expect(page.locator('[data-testid="cdn-invalidation-status"]')).toContainText(
      'Cache invalidation completed'
    );

    // Verify newsletter notification sent
    await expect(page.locator('[data-testid="newsletter-sent-status"]')).toContainText(
      'Newsletter sent to'
    );

    // Verify version created
    await expect(page.locator('[data-testid="version-history"]')).toBeVisible();
    const latestVersion = page.locator('[data-testid="version-row"]').first();
    await expect(latestVersion).toContainText('agenda');
    await expect(latestVersion).toContainText('Version 1');
  });

  test('should block agenda publish if session timings incomplete (AC21)', async ({ page }) => {
    const eventCode = await createEventReadyForPublishing(page);

    // Remove timing from one session to make validation fail
    await fetch(`${API_URL}/api/v1/events/${eventCode}/sessions`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        title: 'Unscheduled Session',
        description: 'No timing assigned',
        sessionType: 'presentation',
        // startTime: null, endTime: null (placeholder)
        speakers: ['test.speaker'],
      }),
    });

    await page.goto(`${BASE_URL}/organizer/events/${eventCode}/publishing`);

    // Session timings validation should fail
    await expect(page.locator('[data-testid="validation-session-timings"]')).toHaveClass(
      /validation-failed/
    );
    await expect(page.locator('[data-testid="validation-session-timings-status"]')).toContainText(
      'Incomplete (3/4 sessions)'
    );

    // [Assign Timings] button should be visible
    await expect(page.locator('[data-testid="assign-timings-button"]')).toBeVisible();

    // Phase 3 (Agenda) publish button should be DISABLED
    await expect(page.locator('[data-testid="publish-agenda-button"]')).toBeDisabled();

    // Clicking should show validation error
    await page.click('[data-testid="publish-agenda-button"]', { force: true });
    await expect(page.locator('[data-testid="validation-error-toast"]')).toBeVisible();
    await expect(page.locator('[data-testid="validation-error-toast"]')).toContainText(
      'Not all sessions have timing assigned'
    );
  });

  test('should preview published content before publishing (AC20, AC29)', async ({ page }) => {
    const eventCode = await createEventReadyForPublishing(page);
    await page.goto(`${BASE_URL}/organizer/events/${eventCode}/publishing`);

    // Click Preview button
    await page.click('[data-testid="preview-button"]');

    // Live preview modal should appear
    await expect(page.locator('[data-testid="live-preview-modal"]')).toBeVisible();

    // Device toggles
    await expect(page.locator('[data-testid="device-desktop"]')).toBeVisible();
    await expect(page.locator('[data-testid="device-mobile"]')).toBeVisible();
    await expect(page.locator('[data-testid="device-print"]')).toBeVisible();

    // Default to desktop view
    await expect(page.locator('[data-testid="preview-iframe"]')).toHaveAttribute(
      'data-device',
      'desktop'
    );

    // Toggle to mobile view
    await page.click('[data-testid="device-mobile"]');
    await expect(page.locator('[data-testid="preview-iframe"]')).toHaveAttribute(
      'data-device',
      'mobile'
    );

    // Preview should show agenda content
    const previewFrame = page.frameLocator('[data-testid="preview-iframe"]');
    await expect(previewFrame.locator('text=BATbern 996')).toBeVisible();
    await expect(previewFrame.locator('text=Cloud Architecture')).toBeVisible();
    await expect(previewFrame.locator('text=18:00')).toBeVisible(); // Session time
  });

  test('should auto-schedule publish at configured intervals (AC19)', async ({ page }) => {
    const eventCode = await createEventReadyForPublishing(page);
    await page.goto(`${BASE_URL}/organizer/events/${eventCode}/publishing`);

    // Open publishing configuration
    await page.click('[data-testid="publishing-config-button"]');

    // Configuration modal should show
    await expect(page.locator('[data-testid="publishing-config-modal"]')).toBeVisible();

    // Verify default auto-publish settings
    await expect(page.locator('[data-testid="auto-publish-speakers-enabled"]')).toBeChecked();
    await expect(page.locator('[data-testid="auto-publish-speakers-days"]')).toHaveValue('30');

    await expect(page.locator('[data-testid="auto-publish-agenda-enabled"]')).toBeChecked();
    await expect(page.locator('[data-testid="auto-publish-agenda-days"]')).toHaveValue('14');

    // Modify auto-publish schedule
    await page.fill('[data-testid="auto-publish-agenda-days"]', '21'); // 3 weeks instead of 2
    await page.click('button:has-text("Save Configuration")');

    // Verify schedule updated
    await expect(page.locator('[data-testid="auto-publish-schedule"]')).toContainText(
      'Agenda auto-publish: 21 days before event'
    );
  });

  test('should rollback to previous version (AC26-AC28)', async ({ page }) => {
    const eventCode = await createEventReadyForPublishing(page);
    await page.goto(`${BASE_URL}/organizer/events/${eventCode}/publishing`);

    // Publish Phase 3 (Agenda) to create version 1
    await page.click('[data-testid="publish-agenda-button"]');
    await page.click('button:has-text("Confirm Publish")');
    await expect(page.locator('[data-testid="publish-success-toast"]')).toBeVisible({
      timeout: 10000,
    });

    // Make a change and publish again to create version 2
    await page.goto(`${BASE_URL}/organizer/events/${eventCode}/sessions`);
    await page.locator('[data-testid="session-card"]').first().click();
    await page.fill(
      '[data-testid="session-description"]',
      'Updated description for testing rollback'
    );
    await page.click('button:has-text("Save")');

    await page.goto(`${BASE_URL}/organizer/events/${eventCode}/publishing`);
    await page.click('[data-testid="publish-agenda-button"]');
    await page.click('button:has-text("Confirm Publish")');
    await expect(page.locator('[data-testid="publish-success-toast"]')).toBeVisible({
      timeout: 10000,
    });

    // Now we have 2 versions - rollback to version 1
    await page.click('[data-testid="version-control-tab"]');

    // Verify version history
    await expect(page.locator('[data-testid="version-row"]')).toHaveCount(2);

    // Click rollback on version 1
    await page
      .locator('[data-testid="version-row"]')
      .nth(1)
      .locator('button:has-text("Rollback")')
      .click();

    // Rollback confirmation modal
    await expect(page.locator('[data-testid="rollback-confirmation-modal"]')).toBeVisible();
    await page.fill(
      '[data-testid="rollback-reason"]',
      'Testing rollback functionality - incorrect session description in v2'
    );
    await page.click('button:has-text("Confirm Rollback")');

    // Wait for rollback to complete
    await expect(page.locator('[data-testid="rollback-success-toast"]')).toBeVisible({
      timeout: 10000,
    });
    await expect(page.locator('[data-testid="rollback-success-toast"]')).toContainText(
      'Version rolled back successfully'
    );

    // Verify CDN re-invalidated
    await expect(page.locator('[data-testid="cdn-invalidation-status"]')).toContainText(
      'Cache invalidation completed'
    );

    // Verify new version 3 created (marking rollback)
    await expect(page.locator('[data-testid="version-row"]')).toHaveCount(3);
    const latestVersion = page.locator('[data-testid="version-row"]').first();
    await expect(latestVersion).toContainText('Version 3');
    await expect(latestVersion).toContainText('Rolled back to v1');

    // Verify content restored
    await page.goto(`${BASE_URL}/organizer/events/${eventCode}/sessions`);
    const sessionDesc = await page
      .locator('[data-testid="session-card"]')
      .first()
      .locator('[data-testid="session-description"]')
      .textContent();
    expect(sessionDesc).not.toContain('Updated description for testing rollback'); // Should be original
  });

  test('should track all post-publish updates in change log (AC28)', async ({ page }) => {
    const eventCode = await createEventReadyForPublishing(page);
    await page.goto(`${BASE_URL}/organizer/events/${eventCode}/publishing`);

    // Publish Phase 3 (Agenda)
    await page.click('[data-testid="publish-agenda-button"]');
    await page.click('button:has-text("Confirm Publish")');
    await expect(page.locator('[data-testid="publish-success-toast"]')).toBeVisible({
      timeout: 10000,
    });

    // Make multiple changes after publishing
    await page.goto(`${BASE_URL}/organizer/events/${eventCode}/sessions`);
    await page.locator('[data-testid="session-card"]').first().click();
    await page.fill('[data-testid="session-title"]', 'Updated Session Title');
    await page.click('button:has-text("Save")');

    // Navigate to change log
    await page.goto(`${BASE_URL}/organizer/events/${eventCode}/publishing`);
    await page.click('[data-testid="change-log-tab"]');

    // Verify change log entries
    await expect(page.locator('[data-testid="change-log-entry"]')).toHaveCount.greaterThan(0);
    const latestChange = page.locator('[data-testid="change-log-entry"]').first();
    await expect(latestChange).toContainText('Session updated');
    await expect(latestChange).toContainText('Updated Session Title');
    await expect(latestChange).toContainText(new Date().toISOString().split('T')[0]); // Today's date
  });

  test('should show correct publishing mode options (AC18, AC20)', async ({ page }) => {
    const eventCode = await createEventReadyForPublishing(page);
    await page.goto(`${BASE_URL}/organizer/events/${eventCode}/publishing`);

    // Publishing mode controls should be visible
    await expect(page.locator('[data-testid="publishing-mode-controls"]')).toBeVisible();

    // Verify mode options
    await expect(page.locator('[data-testid="mode-draft"]')).toBeVisible();
    await expect(page.locator('[data-testid="mode-progressive"]')).toBeVisible();
    await expect(page.locator('[data-testid="mode-complete"]')).toBeVisible();

    // Default should be progressive
    await expect(page.locator('[data-testid="mode-progressive"]')).toBeChecked();

    // Select draft mode
    await page.click('[data-testid="mode-draft"]');

    // Draft mode description
    await expect(page.locator('[data-testid="mode-description"]')).toContainText(
      'Preview only, not live'
    );

    // Publish button should change to "Preview Draft"
    await expect(page.locator('[data-testid="publish-agenda-button"]')).toHaveText('Preview Draft');
  });
});
