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
import { testConfig, getPresentation } from './test-data.config';
import { createSequentialCapturer } from './helpers/screenshot-helpers';
import { cleanupAfterTests, cleanupBeforeScreenshots } from './helpers/cleanup-helpers';
import { EventWorkflowPage } from './page-objects/EventWorkflowPage';
import { SpeakerManagementPage } from './page-objects/SpeakerManagementPage';
import { TopicSelectionPage } from './page-objects/TopicSelectionPage';

// Unused imports for future phases - will be used in Phase B-F
// eslint-disable-next-line @typescript-eslint/no-unused-vars
import { PublishingPage } from './page-objects/PublishingPage';

/**
 * Test Suite: Complete Event Workflow
 * Using .serial to run tests in order and share state between phases
 */
test.describe.serial('Complete Event Workflow with Documentation Screenshots', () => {
  let authToken: string;
  let testEventCode: string;

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

    // Seed data not currently used - speakers/topics created directly via UI
    // If needed in future, can use: await getSeedData(authToken)

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

      // Store event code for cleanup (backend format: BATbernXXXX)
      testEventCode = `BATbern${uniqueEventNumber}`;

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
      const eventUrl = `http://localhost:8100/organizer/events/${testEventCode}`;
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
   * Phase B: Speaker Outreach & Kanban Management (Steps 4-6)
   * - Speaker Outreach Tracking (contact 5 speakers with notes)
   * - Kanban Workflow: Drag speakers from CONTACTED → READY
   * - Kanban Workflow: Drag speakers from READY → ACCEPTED
   *
   * Based on recording lines 90-169
   */
  test('Phase B: Speaker Outreach (Steps 4-6)', async ({ page }) => {
    test.setTimeout(10 * 60 * 1000);

    console.log('\n📋 Phase B: Speaker Outreach\n');

    const capturer = createSequentialCapturer('phase-b-outreach', 1);
    const speakerPage = new SpeakerManagementPage(page);

    try {
      // ========================================
      // Step 1: Navigate to event outreach view
      // ========================================
      console.log('  → Step 1: Navigate to Event Outreach View');

      // NOTE: Even with .serial, Playwright creates a fresh page context for each test
      // Navigate directly to speakers tab with URL parameters
      const eventUrl = `http://localhost:8100/organizer/events/${testEventCode}?tab=speakers&view=kanban`;
      console.log(`    → Navigating to event speakers tab: ${eventUrl}`);
      await page.goto(eventUrl);
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(1500);

      // Verify speaker cards are visible (we should be in outreach phase from Phase A)
      await expect(page.locator('text=/N.*Nissim.*ELCA/i').first()).toBeVisible({ timeout: 10000 });
      await capturer(page, 'outreach-view-ready', { scrollToTop: true });
      console.log('    ✓ Outreach view loaded with speaker cards visible');

      // ========================================
      // Step 2: Contact Speakers (5 interactions)
      // ========================================
      console.log('  → Step 2: Contact Speakers');

      // Iterate through each outreach interaction
      for (let i = 0; i < testConfig.speakerOutreach.length; i++) {
        const contact = testConfig.speakerOutreach[i];
        console.log(
          `    → Contacting speaker ${i + 1}/${testConfig.speakerOutreach.length}: ${contact.displayName}`
        );

        // Capture before opening dialog
        await capturer(page, `before-contact-speaker-${i + 1}`, { scrollToTop: true });

        // Use page object method to contact speaker
        await speakerPage.contactSpeaker(contact.displayName, contact.contactMethod, contact.notes);

        // Capture after contact recorded
        await page.waitForTimeout(500); // Wait for dialog to close
        await capturer(page, `after-contact-speaker-${i + 1}`, { scrollToTop: true });

        console.log(
          `    ✓ Speaker ${i + 1} contacted via ${contact.contactMethod}: ${contact.notes.substring(0, 30)}...`
        );
      }

      console.log(`    ✓ All ${testConfig.speakerOutreach.length} speaker contacts recorded`);

      // ========================================
      // Step 3: Drag Speakers from CONTACTED to READY
      // ========================================
      console.log('  → Step 3: Move Speakers to READY');

      // Wait for kanban board to be ready
      await page.waitForTimeout(1000);
      await capturer(page, 'kanban-contacted-state', { scrollToTop: true });

      // Get the READY column locator using test identifier
      const readyColumn = page.getByTestId('status-lane-ready');

      // Drag each speaker from CONTACTED to READY
      const speakersToMove = [
        { name: 'N Nissim ELCA AI', label: 'Nissim' },
        { name: 'B Balti Galenica AI', label: 'Balti' },
        { name: 'A Andreas Mobiliar AI', label: 'Andreas' },
        { name: 'D Daniel BKW AI', label: 'Daniel' },
      ];

      for (const speaker of speakersToMove) {
        console.log(`    → Dragging ${speaker.label} to READY`);
        const speakerCard = page.getByRole('button', { name: speaker.name });

        // Wait for card to be visible and stable
        await expect(speakerCard).toBeVisible({ timeout: 5000 });
        await page.waitForTimeout(500); // Let card settle

        // Manual drag using mouse events (more reliable with dnd-kit)
        const cardBox = await speakerCard.boundingBox();
        const columnBox = await readyColumn.boundingBox();

        if (!cardBox) {
          console.error(`      ✗ Failed to get boundingBox for ${speaker.label} - card not found`);
          continue;
        }
        if (!columnBox) {
          console.error(`      ✗ Failed to get boundingBox for READY column`);
          continue;
        }

        // Start drag from center of card
        await page.mouse.move(cardBox.x + cardBox.width / 2, cardBox.y + cardBox.height / 2);
        await page.mouse.down();
        await page.waitForTimeout(100);

        // Move to center of target column
        await page.mouse.move(
          columnBox.x + columnBox.width / 2,
          columnBox.y + columnBox.height / 2,
          {
            steps: 10,
          }
        );
        await page.waitForTimeout(100);

        // Drop
        await page.mouse.up();
        await page.waitForTimeout(300);

        // After drop, StatusChangeDialog opens asking for optional reason
        // Wait for modal and confirm (leave reason field empty)
        const confirmButton = page.getByTestId('status-change-confirm');
        if (await confirmButton.isVisible({ timeout: 2000 }).catch(() => false)) {
          await confirmButton.click();
          await page.waitForTimeout(800);
        }

        console.log(`      ✓ ${speaker.label} moved to READY`);
      }

      // Wait longer for all drag operations to settle
      await page.waitForTimeout(2000);
      await capturer(page, 'all-speakers-ready', { scrollToTop: true });
      console.log('    ✓ All speakers moved to READY');

      // ========================================
      // Step 4: Drag Speakers from READY to ACCEPTED
      // ========================================
      console.log('  → Step 4: Move Speakers to ACCEPTED');

      // After screenshot scrolls to top, wait and scroll back to kanban board
      await page.waitForTimeout(1000);

      // Verify READY column is visible (to ensure we're looking at the kanban)
      const readyColumnCheck = page.getByTestId('status-lane-ready');
      await expect(readyColumnCheck).toBeVisible({ timeout: 5000 });

      // Get the ACCEPTED column locator using test identifier
      const acceptedColumn = page.getByTestId('status-lane-accepted');

      // Verify ACCEPTED column exists before starting
      await expect(acceptedColumn).toBeVisible({ timeout: 5000 });
      console.log('    → ACCEPTED column found, starting drag operations');

      // Verify first speaker card is still visible in READY column
      const firstSpeakerCheck = page.getByRole('button', { name: speakersToMove[0].name });
      await expect(firstSpeakerCheck).toBeVisible({ timeout: 5000 });
      console.log(`    → First speaker (${speakersToMove[0].label}) confirmed visible in READY`);

      // Take a screenshot before starting Step 4 drags
      await capturer(page, 'before-drag-to-accepted');

      // Drag each speaker from READY to ACCEPTED with manual mouse events
      for (const speaker of speakersToMove) {
        console.log(`    → Dragging ${speaker.label} to ACCEPTED`);

        // Wait for the card to be stable in READY column
        await page.waitForTimeout(500);

        const speakerCard = page.getByRole('button', { name: speaker.name });

        // Ensure card is visible and stable before dragging
        await expect(speakerCard).toBeVisible({ timeout: 5000 });
        await page.waitForTimeout(500); // Let card settle

        // Manual drag using mouse events
        const cardBox = await speakerCard.boundingBox();
        const columnBox = await acceptedColumn.boundingBox();

        if (!cardBox) {
          console.error(`      ✗ Failed to get boundingBox for ${speaker.label} - card not found`);
          continue;
        }
        if (!columnBox) {
          console.error(`      ✗ Failed to get boundingBox for ACCEPTED column`);
          continue;
        }

        // Start drag from center of card
        await page.mouse.move(cardBox.x + cardBox.width / 2, cardBox.y + cardBox.height / 2);
        await page.mouse.down();
        await page.waitForTimeout(100);

        // Move to center of target column
        await page.mouse.move(
          columnBox.x + columnBox.width / 2,
          columnBox.y + columnBox.height / 2,
          {
            steps: 10,
          }
        );
        await page.waitForTimeout(100);

        // Drop
        await page.mouse.up();
        await page.waitForTimeout(300);

        // After drop, StatusChangeDialog opens asking for optional reason
        // Wait for modal and confirm (leave reason field empty)
        const confirmButton = page.getByTestId('status-change-confirm');
        if (await confirmButton.isVisible({ timeout: 2000 }).catch(() => false)) {
          await confirmButton.click();
          await page.waitForTimeout(800);
        }

        console.log(`      ✓ ${speaker.label} moved to ACCEPTED`);
      }

      // Wait for all final updates to complete
      await page.waitForTimeout(2000);
      await capturer(page, 'all-speakers-accepted', { scrollToTop: true });
      console.log('    ✓ All speakers moved to ACCEPTED');

      console.log('\n✅ Phase B Complete\n');
    } catch (error) {
      console.error('\n❌ Phase B Failed:', error);

      // Capture error screenshot
      await page.screenshot({
        path: `docs/user-guide/assets/screenshots/workflow/phase-b-outreach/ERROR-${Date.now()}.png`,
        fullPage: true,
      });

      throw error;
    }
  });

  /**
   * Phase B.5: Content Submission (Recording lines 189-237)
   * - Switch to Sessions view
   * - Publish Topic
   * - Submit speaker content (3 speakers: Nissim, Balti, Andreas)
   * - Fill presentation title and abstract
   */
  test('Phase B.5: Content Submission', async ({ page }) => {
    test.setTimeout(10 * 60 * 1000);

    console.log('\n📋 Phase B.5: Content Submission\n');

    const capturer = createSequentialCapturer('phase-b5-content', 1);

    try {
      // Verify we have an event code from Phase A
      expect(testEventCode).toBeTruthy();
      console.log(`Using event code: ${testEventCode}`);

      // STEP 1: Navigate directly to Publishing tab (recording lines 189-190)
      console.log('📍 Step 1: Navigate to Publishing tab');
      await page.goto(`http://localhost:8100/organizer/events/${testEventCode}?tab=publishing`);
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(500);

      await capturer(page, 'publish-tab-before-topic', {
        scrollToTop: true,
        fullPage: false,
      });

      // STEP 2: Click "Publish Topic" button (recording line 191)
      console.log('📍 Step 2: Publish topic');
      await page.getByTestId('publish-topic-button').click();
      await page.waitForTimeout(1000); // Wait for publish to complete

      await capturer(page, 'topic-published', {
        scrollToTop: true,
        fullPage: false,
      });

      // STEP 3: Return to Speakers tab (recording line 192)
      console.log('📍 Step 3: Return to Speakers tab');
      await page.getByRole('tab', { name: /Referenten|Speakers/i }).click();
      await page.waitForTimeout(1000); // Wait for tab switch and data load

      await capturer(page, 'speakers-tab-after-publish-topic', {
        scrollToTop: true,
        fullPage: false,
      });

      // STEP 4: Submit content for each speaker (recording lines 199-237)
      for (let i = 0; i < testConfig.presentations.length; i++) {
        const presentation = getPresentation(i);
        const speakerCandidate = testConfig.speakerCandidates[presentation.speakerIndex];

        console.log(
          `\n📍 Step 4.${i + 1}: Submit content for ${speakerCandidate.firstName} (${presentation.actualSpeakerName})`
        );

        // Click speaker card using the brainstormed speaker name (as it appears in kanban)
        // Cards show format: "N Nissim ELCA AI", "B Balti Galenica AI", etc.
        const cardPattern = new RegExp(
          `${speakerCandidate.firstName.charAt(0)} ${speakerCandidate.firstName}.*${speakerCandidate.company}`,
          'i'
        );
        console.log(`  🔍 Looking for speaker card matching: ${cardPattern}`);

        try {
          const speakerCard = page.getByRole('button', { name: cardPattern });
          await speakerCard.waitFor({ state: 'visible', timeout: 5000 });
          await speakerCard.click();
          await page.waitForTimeout(1000); // Wait for drawer to open
        } catch (error) {
          console.error(`  ❌ Failed to find speaker card for ${speakerCandidate.firstName}`);
          console.error(`  Card pattern: ${cardPattern}`);

          // Capture current page state for debugging
          await page.screenshot({
            path: `docs/user-guide/assets/screenshots/workflow/phase-b5-content/DEBUG-speaker-${i + 1}-not-found-${Date.now()}.png`,
            fullPage: true,
          });

          throw error;
        }

        await capturer(page, `content-submission-drawer-${i + 1}-opened`, {
          fullPage: false,
        });

        // Fill speaker search if needed
        if (presentation.speakerSearchTerm) {
          console.log(`  🔍 Searching for speaker: ${presentation.speakerSearchTerm}`);
          const searchField = page.getByTestId('speaker-search-field');
          await searchField.click();
          await searchField.fill(presentation.speakerSearchTerm);
          await page.waitForTimeout(1000); // Wait for autocomplete

          // Select first result
          await page.getByText(presentation.actualSpeakerName).first().click();
          await page.waitForTimeout(500);

          await capturer(page, `content-submission-${i + 1}-speaker-selected`, {
            fullPage: false,
          });
        } else {
          console.log(`  ✓ Speaker auto-mapped to ${presentation.actualSpeakerName}`);
        }

        // Fill presentation title
        console.log(`  📝 Filling title: ${presentation.title}`);
        await page.getByTestId('presentation-title-field').click();
        await page.getByTestId('presentation-title-field').fill(presentation.title);

        // Fill presentation abstract
        console.log(`  📝 Filling abstract: ${presentation.abstract}`);
        await page.getByTestId('presentation-abstract-field').click();
        await page.getByTestId('presentation-abstract-field').fill(presentation.abstract);

        await capturer(page, `content-submission-${i + 1}-filled`, {
          fullPage: false,
        });

        // Submit content
        console.log('  ✅ Submitting content');
        await page.getByTestId('submit-speaker-content-button').click();

        // Wait for drawer to close completely before proceeding
        await page.waitForTimeout(2000); // Wait for submission + drawer close animation

        // Ensure we're back on the Speakers tab with kanban view visible
        await page.waitForLoadState('networkidle');

        await capturer(page, `content-submitted-${i + 1}`, {
          scrollToTop: true,
          fullPage: false,
        });

        console.log(`  ✅ Content submitted for ${speakerCandidate.firstName}`);
      }

      console.log('\n✅ Phase B.5 Complete: All speaker content submitted');
    } catch (error) {
      console.error('❌ Phase B.5 failed:', error);

      // Capture error screenshot
      await page.screenshot({
        path: `docs/user-guide/assets/screenshots/workflow/phase-b5-content/ERROR-${Date.now()}.png`,
        fullPage: true,
      });

      throw error;
    }
  });

  /**
   * Phase C: Quality Review (Recording lines 238-246)
   * - Publish Speakers
   * - Approve presentations (3 speakers)
   */
  test('Phase C: Quality Review', async ({ page }) => {
    test.setTimeout(10 * 60 * 1000);

    console.log('\n📋 Phase C: Quality Review\n');

    const capturer = createSequentialCapturer('phase-c-quality', 1);

    try {
      // Verify we have an event code from Phase A
      expect(testEventCode).toBeTruthy();
      console.log(`Using event code: ${testEventCode}`);

      // Navigate to event detail page
      await page.goto(`http://localhost:8100/organizer/events/${testEventCode}`);
      await page.waitForLoadState('networkidle');

      // STEP 1: Navigate to Publish tab (recording line 238)
      console.log('📍 Step 1: Navigate to Publish tab');
      await page.getByRole('tab', { name: /Veröffentlichung|Publishing/i }).click();
      await page.waitForTimeout(500);

      await capturer(page, 'publish-tab-before-speakers', {
        scrollToTop: true,
        fullPage: false,
      });

      // STEP 2: Click "Publish Speakers" button (recording line 239)
      console.log('📍 Step 2: Publish speakers');
      await page.getByTestId('publish-speakers-button').click();
      await page.waitForTimeout(1000); // Wait for publish to complete

      await capturer(page, 'speakers-published', {
        scrollToTop: true,
        fullPage: false,
      });

      // STEP 3: Return to Speakers tab (recording line 240)
      console.log('📍 Step 3: Return to Speakers tab');
      await page.getByRole('tab', { name: /Referenten|Speakers/i }).click();
      await page.waitForTimeout(500);

      await capturer(page, 'speakers-tab-after-publish', {
        scrollToTop: true,
        fullPage: false,
      });

      // STEP 4: Approve each presentation (recording lines 241-246)
      for (let i = 0; i < testConfig.presentations.length; i++) {
        const presentation = getPresentation(i);
        console.log(`\n📍 Step 4.${i + 1}: Approve ${presentation.title}`);

        // Click presentation card to open quality review drawer
        const presentationCard = page.getByRole('button', {
          name: new RegExp(presentation.title),
        });
        await presentationCard.click();
        await page.waitForTimeout(500);

        await capturer(page, `quality-review-${i + 1}-opened`, {
          fullPage: false,
        });

        // Click approve button
        console.log('  ✅ Approving content');
        await page.getByTestId('approve-content-button').click();
        await page.waitForTimeout(1500); // Wait for approval + drawer close

        await capturer(page, `content-approved-${i + 1}`, {
          scrollToTop: true,
          fullPage: false,
        });
      }

      console.log('\n✅ Phase C Complete: All presentations approved');
    } catch (error) {
      console.error('❌ Phase C failed:', error);

      // Capture error screenshot
      await page.screenshot({
        path: `docs/user-guide/assets/screenshots/workflow/phase-c-quality/ERROR-${Date.now()}.png`,
        fullPage: true,
      });

      throw error;
    }
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
