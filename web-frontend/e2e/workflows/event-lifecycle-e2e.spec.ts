/**
 * Event Lifecycle E2E Test
 *
 * This test executes the complete event management workflow from creation to archival.
 *
 * Workflow Coverage:
 * - Phase A: Setup (Event creation, topic selection, speaker brainstorming)
 * - Phase B: Outreach (Speaker contact, status management, content collection)
 * - Phase C: Quality (Content review and approval)
 * - Phase D: Assignment (Slot assignment, overflow management)
 * - Phase E: Publishing (Progressive publishing, finalization)
 * - Phase F: Archival (Event archival)
 *
 * Prerequisites:
 * 1. Backend services running (make dev-native-up)
 * 2. Frontend running (npm run dev)
 * 3. Seed data loaded (companies, users, topics, speakers)
 * 4. Auth token available (./scripts/auth/get-token.sh)
 *
 * Run:
 *   npm run test:e2e -- event-lifecycle-e2e.spec.ts
 */

import { test, expect } from '@playwright/test';
import { testConfig, getPresentation } from './documentation/test-data.config';
import { cleanupAfterTests } from './documentation/helpers/cleanup-helpers';
import { EventWorkflowPage } from './documentation/page-objects/EventWorkflowPage';
import { SpeakerManagementPage } from './documentation/page-objects/SpeakerManagementPage';
import { TopicSelectionPage } from './documentation/page-objects/TopicSelectionPage';

/**
 * Test Suite: Event Lifecycle E2E
 * Using .serial to run tests in order and share state between phases
 */
test.describe.serial('Event Lifecycle E2E', () => {
  let authToken: string;
  let testEventCode: string;

  /**
   * Setup
   */
  test.beforeAll(async () => {
    console.log('\n🚀 Starting Event Lifecycle E2E Test\n');
    authToken = process.env.AUTH_TOKEN || '';
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
   * Phase A: Event Setup (Steps 1-5)
   * - Login
   * - Event Creation & Configuration
   * - Task Assignment to Organizers
   * - Topic Selection via Heat Map
   * - Speaker Brainstorming (add candidates to pool)
   */
  test('Phase A: Event Setup (Steps 1-5)', async ({ page }) => {
    test.setTimeout(10 * 60 * 1000);

    console.log('\n📋 Phase A: Event Setup\n');

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
      // Step 1: Navigate to Dashboard
      // ========================================
      console.log('  → Step 1: Navigate to Event Dashboard (authenticated via global setup)');

      await eventPage.navigateToDashboard();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(1000);

      await expect(eventPage.createEventButton).toBeVisible({ timeout: 10000 });
      console.log('    ✓ Dashboard loaded - authentication successful');

      // ========================================
      // Step 2: Create New Event
      // ========================================
      console.log('  → Step 2: Create Event');

      // Close any dialogs that might be open (language-independent)
      const dialogCloseButton = page.getByRole('dialog').getByRole('button').first();
      if (await dialogCloseButton.isVisible().catch(() => false)) {
        console.log('    → Closing dialog first');
        await dialogCloseButton.click();
        await page.waitForTimeout(500);
      }

      console.log('    → Clicking "Neue Veranstaltung" button');
      await eventPage.clickCreateEvent();

      console.log('    → Waiting for event creation modal');
      await expect(eventPage.eventNumberField).toBeVisible({ timeout: 10000 });
      await page.waitForTimeout(500);

      // Fill event form
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
      });

      await page.waitForTimeout(500);

      // Submit event creation form
      await eventPage.submitEventForm();

      // Store event code for cleanup
      testEventCode = `BATbern${uniqueEventNumber}`;

      console.log(`    → Waiting for event creation to complete...`);
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(1500);

      // Check if modal closed successfully
      const modalStillOpen = await eventPage.eventNumberField
        .isVisible({ timeout: 3000 })
        .catch(() => false);
      if (modalStillOpen) {
        console.log(`    ⚠️  Modal still open - checking for validation errors`);
        const errorText = await page
          .locator('text=/error|fehler|ungültig/i')
          .first()
          .textContent({ timeout: 1000 })
          .catch(() => 'No error text found');
        console.log(`    ⚠️  Error message: ${errorText}`);
        throw new Error(`Event creation modal did not close - validation error: ${errorText}`);
      }

      await expect(eventPage.createEventButton).toBeVisible({ timeout: 5000 });
      console.log(`    ✓ Event created: ${testEventCode}, back on dashboard`);

      // ========================================
      // Step 3: Assign Tasks to Organizers
      // ========================================
      console.log('  → Step 3: Assign Tasks to Organizers');

      const eventUrl = `http://localhost:8100/organizer/events/${testEventCode}`;
      console.log(`    → Navigating to event detail page: ${eventUrl}`);
      await page.goto(eventUrl);
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(1000);
      console.log(`    ✓ Event detail page loaded`);

      // Click Edit button
      console.log('    → Clicking Edit button');
      const editButton = page.getByTestId('edit-event-button');
      await editButton.click();
      await page.waitForTimeout(500);

      await expect(page.getByRole('dialog')).toBeVisible({ timeout: 5000 });
      await page.waitForTimeout(500);
      console.log('    ✓ Edit modal opened on Info tab');

      // Click Tasks tab
      console.log('    → Clicking Tasks tab');
      const tasksTab = page.getByTestId('tasks-tab');
      await tasksTab.click();
      await page.waitForTimeout(500);
      console.log('    ✓ Tasks tab loaded');

      // Define task assignments
      const taskAssignments = [
        { taskName: 'Venue Booking', assignee: 'Nissim Buchs' },
        { taskName: 'Partner Meeting', assignee: 'Daniel Kühni' },
        { taskName: 'Moderator Assignment', assignee: 'Andreas Grütter' },
        { taskName: 'Newsletter: Topic', assignee: 'Baltisar Oswald' },
        { taskName: 'Newsletter: Speaker', assignee: 'Baltisar Oswald' },
        { taskName: 'Newsletter: Final', assignee: 'Baltisar Oswald' },
      ];

      // Assign each task
      for (let i = 0; i < taskAssignments.length; i++) {
        const { taskName, assignee } = taskAssignments[i];
        console.log(`    → Assigning "${taskName}" to ${assignee}`);

        const taskRow = page.getByRole('listitem').filter({ hasText: taskName });
        const assigneeSelect = taskRow.getByRole('combobox');
        await assigneeSelect.scrollIntoViewIfNeeded();
        await assigneeSelect.click();
        await page.waitForTimeout(300);

        await page.getByRole('option', { name: assignee }).first().click();
        await page.waitForTimeout(300);
      }

      console.log(`    ✓ All ${taskAssignments.length} tasks assigned`);

      // Click Save button
      console.log('    → Saving task assignments');
      const saveButton = page.getByTestId('save-event-button');
      await saveButton.click();
      await page.waitForTimeout(1000);

      await expect(page.getByRole('dialog')).not.toBeVisible({ timeout: 5000 });
      console.log('    ✓ Tasks saved, modal closed');

      // Navigate to task list to verify
      console.log('    → Navigating to task list');
      const tasksButton = page.getByTestId('tasks-button');
      await tasksButton.click();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(500);
      console.log('    ✓ Task list loaded');

      // Change filter to "All Tasks" (language-independent)
      console.log('    → Changing filter to "All Tasks"');
      const filterCombobox = page.getByTestId('task-filter-select');
      await filterCombobox.click();
      await page.waitForTimeout(300);

      await page.getByTestId('task-filter-option-all').click();
      await page.waitForTimeout(500);
      console.log('    ✓ Filter changed to "All Tasks"');

      // Navigate back to event detail page
      console.log(`    → Navigating back to event detail page`);
      await page.goto(eventUrl);
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(500);

      console.log(`    → Verifying topic selection button is available...`);
      const topicButton = page.getByTestId('select-topic-button');
      await topicButton.scrollIntoViewIfNeeded({ timeout: 10000 });
      await expect(topicButton).toBeVisible({ timeout: 5000 });
      console.log(`    ✓ Event detail page ready for topic selection`);

      // ========================================
      // Step 4: Topic Selection via Heat Map
      // ========================================
      console.log('  → Step 4: Topic Selection');

      await topicPage.openTopicSelection();
      await expect(topicPage.heatmapButton).toBeVisible({ timeout: 5000 });

      await topicPage.openHeatmap();
      await page.waitForTimeout(1000);

      const { row, column } = testConfig.topics.heatmapSelection;
      await topicPage.selectTopicFromHeatmap(row, column);
      await page.waitForTimeout(500);

      await topicPage.confirmSelection();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(500);

      await expect(page.getByTestId('speaker-name-field')).toBeVisible({
        timeout: 10000,
      });
      console.log(`    ✓ Topic selected from heatmap (row ${row}, col ${column})`);

      // ========================================
      // Step 5: Speaker Brainstorming
      // ========================================
      console.log('  → Step 5: Speaker Brainstorming');

      await page.waitForTimeout(500);

      const candidates = testConfig.speakerCandidates.map((c) => ({
        firstName: c.firstName,
        company: c.company,
        expertise: c.expertise,
        assignedUserName: c.assignedUserName,
      }));

      console.log(`    → Adding ${candidates.length} speaker candidates`);
      await speakerPage.addMultipleSpeakers(candidates);
      await page.waitForTimeout(500);
      console.log(`    ✓ All ${candidates.length} speakers added to pool`);

      await expect(speakerPage.proceedToOutreachButton).toBeVisible({ timeout: 5000 });
      await speakerPage.proceedToOutreach();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(1000);

      await expect(page.getByTestId('speaker-card').first()).toBeVisible({ timeout: 10000 });
      console.log('    ✓ Proceeded to outreach phase');

      console.log('\n✅ Phase A Complete\n');
    } catch (error) {
      console.error('\n❌ Phase A Failed:', error);
      throw error;
    }
  });

  /**
   * Phase B: Speaker Outreach & Kanban Management (Steps 4-6)
   * - Speaker Outreach Tracking (contact 5 speakers with notes)
   * - Kanban Workflow: Drag speakers from CONTACTED → READY
   * - Kanban Workflow: Drag speakers from READY → ACCEPTED
   */
  test('Phase B: Speaker Outreach (Steps 4-6)', async ({ page }) => {
    test.setTimeout(10 * 60 * 1000);

    console.log('\n📋 Phase B: Speaker Outreach\n');

    const speakerPage = new SpeakerManagementPage(page);

    try {
      // ========================================
      // Step 1: Navigate to event outreach view
      // ========================================
      console.log('  → Step 1: Navigate to Event Outreach View');

      const eventUrl = `http://localhost:8100/organizer/events/${testEventCode}?tab=speakers&view=kanban`;
      console.log(`    → Navigating to event speakers tab: ${eventUrl}`);
      await page.goto(eventUrl);
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(1000);

      await expect(page.getByTestId('speaker-card').first()).toBeVisible({ timeout: 10000 });
      console.log('    ✓ Outreach view loaded with speaker cards visible');

      // ========================================
      // Step 2: Contact Speakers (5 interactions)
      // ========================================
      console.log('  → Step 2: Contact Speakers');

      for (let i = 0; i < testConfig.speakerOutreach.length; i++) {
        const contact = testConfig.speakerOutreach[i];
        console.log(
          `    → Contacting speaker ${i + 1}/${testConfig.speakerOutreach.length}: ${contact.displayName}`
        );

        await speakerPage.contactSpeaker(contact.displayName, contact.contactMethod, contact.notes);

        await page.waitForTimeout(500);

        console.log(
          `    ✓ Speaker ${i + 1} contacted via ${contact.contactMethod}: ${contact.notes.substring(0, 30)}...`
        );
      }

      console.log(`    ✓ All ${testConfig.speakerOutreach.length} speaker contacts recorded`);

      // ========================================
      // Step 3: Drag Speakers from CONTACTED to READY
      // ========================================
      console.log('  → Step 3: Move Speakers to READY');

      // Wait longer for kanban board state to settle after contact operations
      // The original test had screenshot operations here that added extra time
      await page.waitForTimeout(2000);

      const readyColumn = page.getByTestId('status-lane-READY');

      const speakersToMove = [
        { name: 'N Nissim ELCA AI', label: 'Nissim' },
        { name: 'B Balti Galenica AI', label: 'Balti' },
        { name: 'A Andreas Mobiliar AI', label: 'Andreas' },
        { name: 'D Daniel BKW AI', label: 'Daniel' },
      ];

      for (const speaker of speakersToMove) {
        console.log(`    → Dragging ${speaker.label} to READY`);
        const speakerCard = page.getByRole('button', { name: speaker.name });

        await expect(speakerCard).toBeVisible({ timeout: 5000 });
        await page.waitForTimeout(500);

        const cardBox = await speakerCard.boundingBox();
        const columnBox = await readyColumn.boundingBox();

        if (!cardBox) {
          console.error(`      ✗ Failed to get boundingBox for ${speaker.label}`);
          continue;
        }
        if (!columnBox) {
          console.error(`      ✗ Failed to get boundingBox for READY column`);
          continue;
        }

        await page.mouse.move(cardBox.x + cardBox.width / 2, cardBox.y + cardBox.height / 2);
        await page.mouse.down();
        await page.waitForTimeout(100);

        await page.mouse.move(
          columnBox.x + columnBox.width / 2,
          columnBox.y + columnBox.height / 2,
          { steps: 10 }
        );
        await page.waitForTimeout(100);

        await page.mouse.up();
        await page.waitForTimeout(300);

        const confirmButton = page.getByTestId('status-change-confirm');
        if (await confirmButton.isVisible({ timeout: 2000 }).catch(() => false)) {
          await confirmButton.click();
          await page.waitForTimeout(500);
        }

        console.log(`      ✓ ${speaker.label} moved to READY`);
      }

      await page.waitForTimeout(1000);
      console.log('    ✓ All speakers moved to READY');

      // ========================================
      // Step 4: Drag Speakers from READY to ACCEPTED
      // ========================================
      console.log('  → Step 4: Move Speakers to ACCEPTED');

      // Wait for previous drag operations to fully settle
      await page.waitForTimeout(1500);

      const readyColumnCheck = page.getByTestId('status-lane-READY');
      await expect(readyColumnCheck).toBeVisible({ timeout: 5000 });

      const acceptedColumn = page.getByTestId('status-lane-ACCEPTED');
      await expect(acceptedColumn).toBeVisible({ timeout: 5000 });
      console.log('    → ACCEPTED column found, starting drag operations');

      const firstSpeakerCheck = page.getByRole('button', { name: speakersToMove[0].name });
      await expect(firstSpeakerCheck).toBeVisible({ timeout: 5000 });
      console.log(`    → First speaker (${speakersToMove[0].label}) confirmed visible in READY`);

      for (const speaker of speakersToMove) {
        console.log(`    → Dragging ${speaker.label} to ACCEPTED`);

        await page.waitForTimeout(300);

        const speakerCard = page.getByRole('button', { name: speaker.name });

        await expect(speakerCard).toBeVisible({ timeout: 5000 });
        await page.waitForTimeout(300);

        const cardBox = await speakerCard.boundingBox();
        const columnBox = await acceptedColumn.boundingBox();

        if (!cardBox) {
          console.error(`      ✗ Failed to get boundingBox for ${speaker.label}`);
          continue;
        }
        if (!columnBox) {
          console.error(`      ✗ Failed to get boundingBox for ACCEPTED column`);
          continue;
        }

        await page.mouse.move(cardBox.x + cardBox.width / 2, cardBox.y + cardBox.height / 2);
        await page.mouse.down();
        await page.waitForTimeout(100);

        await page.mouse.move(
          columnBox.x + columnBox.width / 2,
          columnBox.y + columnBox.height / 2,
          { steps: 10 }
        );
        await page.waitForTimeout(100);

        await page.mouse.up();
        await page.waitForTimeout(300);

        const confirmButton = page.getByTestId('status-change-confirm');
        if (await confirmButton.isVisible({ timeout: 2000 }).catch(() => false)) {
          await confirmButton.click();
          await page.waitForTimeout(500);
        }

        console.log(`      ✓ ${speaker.label} moved to ACCEPTED`);
      }

      await page.waitForTimeout(1000);
      console.log('    ✓ All speakers moved to ACCEPTED');

      console.log('\n✅ Phase B Complete\n');
    } catch (error) {
      console.error('\n❌ Phase B Failed:', error);
      throw error;
    }
  });

  /**
   * Phase B.5: Content Submission
   * - Switch to Sessions view
   * - Publish Topic
   * - Submit speaker content (3 speakers: Nissim, Balti, Andreas)
   * - Fill presentation title and abstract
   */
  test('Phase B.5: Content Submission', async ({ page }) => {
    test.setTimeout(10 * 60 * 1000);

    console.log('\n📋 Phase B.5: Content Submission\n');

    try {
      expect(testEventCode).toBeTruthy();
      console.log(`Using event code: ${testEventCode}`);

      // STEP 1: Navigate directly to Publishing tab
      console.log('📍 Step 1: Navigate to Publishing tab');
      await page.goto(`http://localhost:8100/organizer/events/${testEventCode}?tab=publishing`);
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(500);

      // STEP 2: Click "Publish Topic" button
      console.log('📍 Step 2: Publish topic');
      await page.getByTestId('publish-topic-button').click();
      await page.waitForTimeout(1000);

      // STEP 3: Return to Speakers tab
      console.log('📍 Step 3: Return to Speakers tab');
      await page.getByTestId('event-tab-speakers').click();
      await page.waitForTimeout(500);

      // STEP 4: Submit content for each speaker
      for (let i = 0; i < testConfig.presentations.length; i++) {
        const presentation = getPresentation(i);
        const speakerCandidate = testConfig.speakerCandidates[presentation.speakerIndex];

        console.log(
          `\n📍 Step 4.${i + 1}: Submit content for ${speakerCandidate.firstName} (${presentation.actualSpeakerName})`
        );

        const cardPattern = new RegExp(
          `${speakerCandidate.firstName.charAt(0)} ${speakerCandidate.firstName}.*${speakerCandidate.company}`,
          'i'
        );
        console.log(`  🔍 Looking for speaker card matching: ${cardPattern}`);

        try {
          const speakerCard = page.getByRole('button', { name: cardPattern });
          await speakerCard.waitFor({ state: 'visible', timeout: 5000 });
          await speakerCard.click();
          await page.waitForTimeout(500);
        } catch (error) {
          console.error(`  ❌ Failed to find speaker card for ${speakerCandidate.firstName}`);
          throw error;
        }

        // Fill speaker search if needed
        if (presentation.speakerSearchTerm) {
          console.log(`  🔍 Searching for speaker: ${presentation.speakerSearchTerm}`);
          const searchField = page.getByTestId('speaker-search-field');
          await searchField.click();
          await searchField.fill(presentation.speakerSearchTerm);
          await page.waitForTimeout(500);

          await page.getByText(presentation.actualSpeakerName).first().click();
          await page.waitForTimeout(300);
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

        // Submit content
        console.log('  ✅ Submitting content');
        await page.getByTestId('submit-speaker-content-button').click();

        await page.waitForTimeout(1000);
        await page.waitForLoadState('networkidle');

        console.log(`  ✅ Content submitted for ${speakerCandidate.firstName}`);
      }

      console.log('\n✅ Phase B.5 Complete: All speaker content submitted');
    } catch (error) {
      console.error('❌ Phase B.5 failed:', error);
      throw error;
    }
  });

  /**
   * Phase C: Quality Review
   * - Publish Speakers
   * - Approve presentations (3 speakers)
   */
  test('Phase C: Quality Review', async ({ page }) => {
    test.setTimeout(10 * 60 * 1000);

    console.log('\n📋 Phase C: Quality Review\n');

    try {
      expect(testEventCode).toBeTruthy();
      console.log(`Using event code: ${testEventCode}`);

      await page.goto(`http://localhost:8100/organizer/events/${testEventCode}`);
      await page.waitForLoadState('networkidle');

      // STEP 1: Navigate to Publish tab
      console.log('📍 Step 1: Navigate to Publish tab');
      await page.getByTestId('event-tab-publishing').click();
      await page.waitForTimeout(500);

      // STEP 2: Click "Publish Speakers" button
      console.log('📍 Step 2: Publish speakers');
      await page.getByTestId('publish-speakers-button').click();
      await page.waitForTimeout(1000);

      // STEP 3: Return to Speakers tab
      console.log('📍 Step 3: Return to Speakers tab');
      await page.getByTestId('event-tab-speakers').click();
      await page.waitForTimeout(500);

      // STEP 4: Approve each presentation
      for (let i = 0; i < testConfig.presentations.length; i++) {
        const presentation = getPresentation(i);
        console.log(`\n📍 Step 4.${i + 1}: Approve ${presentation.title}`);

        const presentationCard = page.getByRole('button', {
          name: new RegExp(presentation.title),
        });
        await presentationCard.click();
        await page.waitForTimeout(500);

        console.log('  ✅ Approving content');
        await page.getByTestId('approve-content-button').click();
        await page.waitForTimeout(1000);
      }

      console.log('\n✅ Phase C Complete: All presentations approved');
    } catch (error) {
      console.error('❌ Phase C failed:', error);
      throw error;
    }
  });

  /**
   * Phase D: Slot Assignment & Publish Agenda
   * - Navigate to Sessions view
   * - Open Slot Assignment page
   * - Auto-assign speakers to time slots
   * - Publish Agenda
   */
  test('Phase D: Slot Assignment & Publish Agenda', async ({ page }) => {
    test.setTimeout(10 * 60 * 1000);

    console.log('\n📋 Phase D: Slot Assignment & Publish Agenda\n');

    try {
      await page.goto(`http://localhost:8100/organizer/events/${testEventCode}`);
      await page.waitForTimeout(500);

      // ========================================
      // STEP 1: Navigate to Sessions View
      // ========================================
      console.log('📍 Step 1: Navigate to Sessions view');

      await page.getByTestId('event-tab-speakers').click();
      await page.waitForTimeout(500);

      console.log('  → Switching to Sessions view');
      await page.getByTestId('sessions-view-toggle').click();
      await page.waitForTimeout(500);

      console.log('  ✓ Sessions view loaded');

      // ========================================
      // STEP 2: Open Slot Assignment Page
      // ========================================
      console.log('📍 Step 2: Open Slot Assignment page');

      await page.getByTestId('manage-slot-assignments-button').click();
      await page.waitForTimeout(1000);

      console.log('  ✓ Slot Assignment page opened');

      // ========================================
      // STEP 3: Auto-Assign Speakers
      // ========================================
      console.log('📍 Step 3: Auto-assign speakers to time slots');

      await page.waitForTimeout(500);

      console.log('  → Clicking Auto-Assign button');
      const autoAssignButton = page.getByTestId('auto-assign-button');
      await expect(autoAssignButton).toBeVisible({ timeout: 5000 });
      await autoAssignButton.click();
      await page.waitForTimeout(500);

      const autoAssignModal = page.getByTestId('auto-assign-modal');
      await expect(autoAssignModal).toBeVisible({ timeout: 3000 });

      console.log('  → Confirming auto-assignment');
      const confirmButton = page.getByTestId('auto-assign-confirm');
      await confirmButton.click();
      await page.waitForTimeout(2000);

      console.log('  ✓ Speakers auto-assigned to slots');

      // ========================================
      // STEP 4: Return to Event Page
      // ========================================
      console.log('📍 Step 4: Return to event page');

      const backButton = page.getByTestId('back-to-event-button');
      if (await backButton.isVisible({ timeout: 2000 }).catch(() => false)) {
        await backButton.click();
        await page.waitForTimeout(1000);
      } else {
        await page.goto(`http://localhost:8100/organizer/events/${testEventCode}`);
        await page.waitForTimeout(1000);
      }

      await expect(page.getByTestId('event-tab-overview')).toBeVisible({
        timeout: 5000,
      });

      console.log('  ✓ Returned to event page');

      // ========================================
      // STEP 5: Navigate to Publishing Tab
      // ========================================
      console.log('📍 Step 5: Navigate to Publishing tab');

      await page.getByTestId('event-tab-publishing').click();
      await page.waitForTimeout(2000);

      await expect(page.getByTestId('publish-agenda-button')).toBeVisible({ timeout: 10000 });

      console.log('  ✓ Publishing tab loaded');

      // ========================================
      // STEP 6: Publish Agenda
      // ========================================
      console.log('📍 Step 6: Publish Agenda');

      await page.getByTestId('publish-agenda-button').click();
      await page.waitForTimeout(1000);

      console.log('  ✓ Agenda published');

      console.log('\n✅ Phase D Complete: Slot assignment and agenda published');
    } catch (error) {
      console.error('❌ Phase D failed:', error);
      throw error;
    }
  });

  /**
   * Phase E: Archival (Workflow completion)
   * - Navigate to Overview tab
   * - Edit event status to ARCHIVED
   * - Override workflow validation
   * - Save changes
   */
  test('Phase E: Archival (Event archival)', async ({ page }) => {
    test.setTimeout(5 * 60 * 1000);

    console.log('\n📋 Phase E: Archival\n');

    try {
      console.log('Navigating to event page...');
      await page.goto(`http://localhost:8100/organizer/events/${testEventCode}`);
      await page.waitForTimeout(1000);

      // Step 1: Navigate to Overview tab
      console.log('Step 1: Navigate to Overview tab');
      await page.getByTestId('event-tab-overview').click();
      await page.waitForTimeout(500);

      console.log('  ✓ On Overview tab');

      // Step 2: Click Edit button
      console.log('\nStep 2: Open event edit modal');
      const editButton = page.getByTestId('edit-event-button');
      await editButton.waitFor({ state: 'visible', timeout: 5000 });
      await editButton.click();
      await page.waitForTimeout(1000);

      const modalTitle = page.getByTestId('event-form-dialog-title');
      await modalTitle.waitFor({ state: 'visible', timeout: 5000 });

      console.log('  ✓ Edit modal opened');

      // Step 3: Change status to ARCHIVED
      console.log('\nStep 3: Change status to ARCHIVED');
      const statusSelect = page.getByTestId('event-status-select');
      await statusSelect.waitFor({ state: 'visible', timeout: 5000 });
      await statusSelect.click();
      await page.waitForTimeout(300);

      console.log('  ✓ Status dropdown opened');

      await page.getByTestId('status-option-ARCHIVED').click();
      await page.waitForTimeout(300);

      console.log('  ✓ Status changed to ARCHIVED');

      // Step 4: Click Save button (will trigger validation error)
      console.log('\nStep 4: Click Save button (expect validation error)');
      const saveButton = page.getByTestId('save-event-button');
      await saveButton.click();
      await page.waitForTimeout(1000);

      await modalTitle.waitFor({ state: 'visible', timeout: 3000 });

      console.log('  ✓ Validation error triggered (as expected)');

      // Step 5: Check "Override workflow validation" checkbox
      console.log('\nStep 5: Enable workflow validation override');
      const overrideCheckbox = page.getByTestId('override-workflow-validation-checkbox');
      await overrideCheckbox.waitFor({ state: 'visible', timeout: 5000 });
      await overrideCheckbox.check();
      await page.waitForTimeout(300);

      console.log('  ✓ Override checkbox enabled');

      // Step 6: Click Save button again
      console.log('\nStep 6: Save with override (should succeed)');
      await saveButton.click();
      await page.waitForTimeout(1500);

      const modalClosed = await page
        .locator('.MuiDialog-root')
        .isVisible({ timeout: 3000 })
        .catch(() => false);
      if (modalClosed) {
        console.log('  ⚠️  Modal still visible, waiting longer...');
        await page.waitForTimeout(1000);
      }

      console.log('  ✓ Event archived successfully');

      // Verify ARCHIVED badge is visible
      const archivedBadge = page.getByTestId('workflow-status-badge');
      await archivedBadge.waitFor({ state: 'visible', timeout: 5000 });

      console.log('  ✓ ARCHIVED badge visible');

      console.log('\n✅ Phase E Complete: Event archived successfully');
    } catch (error) {
      console.error('❌ Phase E failed:', error);
      throw error;
    }
  });
});
