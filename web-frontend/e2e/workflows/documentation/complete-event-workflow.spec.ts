/**
 * Complete Event Workflow E2E Test with Documentation Screenshots
 *
 * This test executes the complete event management workflow from creation to archival,
 * capturing comprehensive screenshots for user guide documentation.
 *
 * Workflow Coverage:
 * - Phase A: Setup (Event creation, topic selection, speaker brainstorming)
 * - Phase B: Outreach (Speaker contact, status management, content collection)
 * - Phase C: Quality (Content review and approval)
 * - Phase D: Assignment (Slot assignment, overflow management)
 * - Phase E: Publishing (Progressive publishing, finalization)
 * - Phase F: Communication & Archival (Post-event, archival)
 *
 * Prerequisites:
 * 1. Backend services running (make dev-native-up)
 * 2. Frontend running (npm run dev)
 * 3. Seed data loaded (companies, users, topics, speakers)
 * 4. Auth token available (./scripts/auth/get-token.sh)
 *
 * Run:
 *   npm run test:e2e:docs
 *   npm run test:e2e:docs:ui  (for interactive mode)
 */

import { test, expect } from '@playwright/test';
import { testConfig } from './test-data.config';
import { createSequentialCapturer } from './helpers/screenshot-helpers';
import { getSeedData } from './helpers/api-helpers';
import { cleanupAfterTests, cleanupBeforeScreenshots } from './helpers/cleanup-helpers';
import { EventWorkflowPage } from './page-objects/EventWorkflowPage';
import { SpeakerManagementPage } from './page-objects/SpeakerManagementPage';
import { TopicSelectionPage } from './page-objects/TopicSelectionPage';

// Unused imports for future phases - will be used in Phase B-F
// eslint-disable-next-line @typescript-eslint/no-unused-vars
import { PublishingPage } from './page-objects/PublishingPage';

/**
 * Test Suite: Complete Event Workflow
 */
test.describe('Complete Event Workflow with Documentation Screenshots', () => {
  let authToken: string;
  let testEventCode: string;
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  let seedData: Record<string, unknown> | null;

  /**
   * Setup: Clean old screenshots
   */
  test.beforeAll(async () => {
    console.log('\n🚀 Starting Complete Event Workflow Test\n');

    // Clean up old screenshots from previous runs
    cleanupBeforeScreenshots([
      'phase-a-setup',
      'phase-b-outreach',
      'phase-c-quality',
      'phase-d-assignment',
      'phase-e-publishing',
      'phase-f-communication',
    ]);

    // Get authentication token from environment (optional for autologin)
    authToken = process.env.AUTH_TOKEN || '';

    // Try to fetch seed data (optional - Phase A doesn't need it)
    try {
      if (authToken) {
        seedData = await getSeedData(authToken);
        console.log('✓ Seed data loaded');
      }
    } catch {
      console.log('⚠️  Seed data not available (not needed for Phase A)');
      seedData = null;
    }

    console.log('\n✅ Setup complete\n');
  });

  /**
   * Cleanup: Delete test event and orphaned data
   */
  test.afterAll(async () => {
    console.log('\n🧹 Cleaning up test data...\n');

    if (authToken) {
      await cleanupAfterTests(authToken, testEventCode);
    }

    console.log('\n✅ Test complete\n');
  });

  /**
   * Phase A: Event Setup (Steps 1-3)
   * - Login
   * - Event Creation & Configuration
   * - Topic Selection via Heat Map
   * - Speaker Brainstorming (add candidates to pool)
   *
   * Based on recording lines 1-56
   */
  test('Phase A: Event Setup (Steps 1-3)', async ({ page }) => {
    test.setTimeout(10 * 60 * 1000); // 10 minute timeout

    console.log('\n📋 Phase A: Event Setup\n');

    const capturer = createSequentialCapturer('phase-a-setup', 1);
    const eventPage = new EventWorkflowPage(page);
    const topicPage = new TopicSelectionPage(page);
    const speakerPage = new SpeakerManagementPage(page);

    // Enable network logging
    page.on('request', (request) => {
      console.log(`→ ${request.method()} ${request.url()}`);
    });
    page.on('response', (response) => {
      const status = response.status();
      const url = response.url();
      if (status >= 400 || url.includes('/api/')) {
        console.log(`← ${status} ${url}`);
      }
    });
    page.on('requestfailed', (request) => {
      console.log(
        `✗ FAILED: ${request.method()} ${request.url()} - ${request.failure()?.errorText}`
      );
    });

    try {
      // ========================================
      // Step 1: Navigate to Dashboard (already authenticated via storage state)
      // ========================================
      console.log('  → Step 1: Navigate to Event Dashboard (authenticated via global setup)');

      // Navigate directly to events dashboard
      // Auth tokens already injected by global-setup.ts in Amplify V6 format
      await eventPage.navigateToDashboard();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(2000); // Wait for app to recognize auth state

      // Verify we're on the dashboard
      await expect(eventPage.createEventButton).toBeVisible({ timeout: 10000 });
      await capturer(page, 'event-dashboard', { scrollToTop: true, fullPage: false });
      console.log('    ✓ Dashboard loaded - authentication successful');

      // ========================================
      // Step 2: Create New Event
      // ========================================
      console.log('  → Step 2: Create Event');

      // Close any permission error dialogs that might be open
      const abbrechen = page.locator('button:has-text("ABBRECHEN")');
      if (await abbrechen.isVisible()) {
        console.log('    → Closing permission dialog first');
        await abbrechen.click();
        await page.waitForTimeout(500);
      }

      console.log('    → Clicking "Neue Veranstaltung" button');
      await eventPage.clickCreateEvent();

      // Wait for modal to appear
      console.log('    → Waiting for event creation modal');
      await expect(eventPage.eventNumberField).toBeVisible({ timeout: 10000 });
      await page.waitForTimeout(500); // Wait for modal animation
      await capturer(page, 'event-creation-modal', { scrollModal: true, fullPage: false });

      // Fill event form with data from testConfig
      const uniqueEventNumber = testConfig.event.eventNumber + Math.floor(Math.random() * 1000);
      console.log(`    → Creating event #${uniqueEventNumber}`);

      await eventPage.fillEventForm({
        eventNumber: uniqueEventNumber,
        title: testConfig.event.title,
        description: testConfig.event.description,
        date: testConfig.event.date,
        registrationDeadline: testConfig.event.registrationDeadline,
        eventType: testConfig.event.eventType as 'EVENING' | 'AFTERNOON' | 'FULL_DAY',
        venueName: testConfig.event.venue.name,
        venueAddress: testConfig.event.venue.address,
        // venueImagePath: testConfig.event.venueImagePath  // Skip image upload for now
      });

      await page.waitForTimeout(800); // Wait for form to settle
      await capturer(page, 'event-form-filled', {
        scrollModal: true,
        fullPage: false,
        delay: 1000,
      });

      // Submit event creation form
      await eventPage.submitEventForm();

      // Store event code for cleanup
      testEventCode = `BAT-${uniqueEventNumber}`;

      // After creation, modal closes and we're back on dashboard
      console.log(`    → Waiting for event creation to complete...`);
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(2000); // Wait for modal to close and events list to refresh

      // Check if modal closed (event created successfully) or if there's an error
      const modalStillOpen = await eventPage.eventNumberField
        .isVisible({ timeout: 3000 })
        .catch(() => false);
      if (modalStillOpen) {
        console.log(`    ⚠️  Modal still open - checking for validation errors`);
        await page.screenshot({
          path: `docs/user-guide/assets/screenshots/workflow/phase-a-setup/ERROR-modal-still-open-${Date.now()}.png`,
          fullPage: true,
        });

        // Try to find error message
        const errorText = await page
          .locator('text=/error|fehler|ungültig/i')
          .first()
          .textContent({ timeout: 1000 })
          .catch(() => 'No error text found');
        console.log(`    ⚠️  Error message: ${errorText}`);
        throw new Error(`Event creation modal did not close - validation error: ${errorText}`);
      }

      // Verify we're back on dashboard (modal closed)
      await expect(eventPage.createEventButton).toBeVisible({ timeout: 5000 });
      await capturer(page, 'event-created-back-to-dashboard', {
        scrollToTop: true,
        fullPage: false,
      });
      console.log(`    ✓ Event created: ${testEventCode}, back on dashboard`);

      // Navigate directly to the event detail page
      const eventCodeUrl = testEventCode.replace('BAT-', 'BATbern'); // BAT-1203 → BATbern1203 (backend format)
      const eventUrl = `http://localhost:8100/organizer/events/${eventCodeUrl}`;
      console.log(`    → Navigating to event detail page: ${eventUrl}`);
      await page.goto(eventUrl);
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(1500); // Wait for components to mount

      // Verify event detail page loaded and find topic selection button
      console.log(`    → Looking for topic selection button...`);
      const topicButton = page.getByTestId('select-topic-button');

      // Scroll the button into view (it's at the bottom of the page)
      await topicButton.scrollIntoViewIfNeeded({ timeout: 10000 });
      await expect(topicButton).toBeVisible({ timeout: 5000 });
      await capturer(page, 'event-detail-page-opened');
      console.log(`    ✓ Event detail page opened with topic selection button visible`);

      // ========================================
      // Step 4: Topic Selection via Heat Map
      // ========================================
      console.log('  → Step 4: Topic Selection');

      // Open topic selection
      await topicPage.openTopicSelection();
      await expect(topicPage.heatmapButton).toBeVisible({ timeout: 5000 });
      await capturer(page, 'topic-selection-opened');

      // Switch to heatmap view
      await topicPage.openHeatmap();
      await page.waitForTimeout(1500); // Wait for heatmap to render
      await capturer(page, 'topic-heatmap');

      // Select topic from heatmap
      const { row, column } = testConfig.topics.heatmapSelection;
      await topicPage.selectTopicFromHeatmap(row, column);
      await page.waitForTimeout(500);
      await capturer(page, 'topic-selected-from-heatmap');

      // Confirm selection
      await topicPage.confirmSelection();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(1000);

      // Verify topic selection succeeded (should see speaker form)
      await expect(page.getByRole('textbox', { name: /Referent Name/i })).toBeVisible({
        timeout: 10000,
      });
      await capturer(page, 'topic-selection-confirmed', { scrollToTop: true });
      console.log(`    ✓ Topic selected from heatmap (row ${row}, col ${column})`);

      // ========================================
      // Step 5: Speaker Brainstorming
      // ========================================
      console.log('  → Step 5: Speaker Brainstorming');

      // Capture initial speaker brainstorming form
      await page.waitForTimeout(500);
      await capturer(page, 'speaker-brainstorming-form', { scrollToTop: true });

      const candidates = testConfig.speakerCandidates.map((c) => ({
        firstName: c.firstName,
        company: c.company,
        expertise: c.expertise,
        assignedUserName: c.assignedUserName,
      }));

      console.log(`    → Adding ${candidates.length} speaker candidates`);
      await speakerPage.addMultipleSpeakers(candidates);
      await page.waitForTimeout(1000); // Wait for all speakers to be added
      await capturer(page, 'all-speakers-added', { scrollToTop: true });
      console.log(`    ✓ All ${candidates.length} speakers added to pool`);

      // Proceed to outreach phase
      await expect(speakerPage.proceedToOutreachButton).toBeVisible({ timeout: 5000 });
      await speakerPage.proceedToOutreach();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(1500); // Wait for outreach view to load

      // Verify we're in outreach phase (speaker cards should be visible)
      await expect(page.locator('text=/N.*Nissim.*ELCA/i').first()).toBeVisible({ timeout: 10000 });
      await capturer(page, 'proceed-to-outreach', { scrollToTop: true });
      console.log('    ✓ Proceeded to outreach phase');

      console.log('\n✅ Phase A Complete\n');
    } catch (error) {
      console.error('\n❌ Phase A Failed:', error);

      // Capture error screenshot for debugging
      await page.screenshot({
        path: `docs/user-guide/assets/screenshots/workflow/phase-a-setup/ERROR-${Date.now()}.png`,
        fullPage: true,
      });

      throw error; // Re-throw to fail the test
    }
  });

  /**
   * Phase B: Outreach (Steps 4-6)
   * - Speaker Outreach Tracking
   * - Speaker Status Management
   * - Content Collection
   *
   * This test case will be implemented after recording the workflow
   */
  test('Phase B: Speaker Outreach (Steps 4-6)', async () => {
    test.setTimeout(10 * 60 * 1000);

    console.log('\n📋 Phase B: Speaker Outreach\n');
    console.log('⏭️  Phase B implementation pending workflow recording');
    test.skip();
  });

  /**
   * Phase C: Quality Control (Steps 7-8)
   * - Content Quality Review
   * - Minimum Threshold Validation
   *
   * This test case will be implemented after recording the workflow
   */
  test('Phase C: Quality Control (Steps 7-8)', async () => {
    test.setTimeout(10 * 60 * 1000);

    console.log('\n📋 Phase C: Quality Control\n');
    console.log('⏭️  Phase C implementation pending workflow recording');
    test.skip();
  });

  /**
   * Phase D: Slot Assignment (Steps 9-10)
   * - Overflow Management (if applicable)
   * - Drag-and-Drop Slot Assignment
   *
   * This test case will be implemented after recording the workflow
   */
  test('Phase D: Slot Assignment (Steps 9-10)', async () => {
    test.setTimeout(10 * 60 * 1000);

    console.log('\n📋 Phase D: Slot Assignment\n');
    console.log('⏭️  Phase D implementation pending workflow recording');
    test.skip();
  });

  /**
   * Phase E: Progressive Publishing (Steps 11-12)
   * - Progressive Publishing (4 phases)
   * - Finalization with Dropout Handling
   *
   * This test case will be implemented after recording the workflow
   */
  test('Phase E: Progressive Publishing (Steps 11-12)', async () => {
    test.setTimeout(10 * 60 * 1000);

    console.log('\n📋 Phase E: Progressive Publishing\n');
    console.log('⏭️  Phase E implementation pending workflow recording');
    test.skip();
  });

  /**
   * Phase F: Communication & Archival (Steps 13-16)
   * - Newsletter Distribution (if implemented)
   * - Moderator Assignment (if implemented)
   * - Catering Coordination (if implemented)
   * - Event Archival
   *
   * This test case will be implemented after recording the workflow
   */
  test('Phase F: Communication & Archival (Steps 13-16)', async () => {
    test.setTimeout(10 * 60 * 1000);

    console.log('\n📋 Phase F: Communication & Archival\n');
    console.log('⏭️  Phase F implementation pending workflow recording');
    test.skip();
  });
});
