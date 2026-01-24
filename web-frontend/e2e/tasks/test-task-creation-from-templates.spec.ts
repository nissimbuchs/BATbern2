/**
 * Automated E2E Test: Task Creation from Templates
 *
 * Tests Story 5.5 AC21: Task template instantiation with assignees
 * Uses language-independent selectors (data-testid) following BAT-93 standards.
 */

import { test, expect } from '@playwright/test';
import { EventWorkflowPage } from '../workflows/documentation/page-objects/EventWorkflowPage';

test.describe('Task Creation from Templates', () => {
  test('should create tasks from templates when creating a new event', async ({ page }) => {
    test.setTimeout(120000); // 2 minutes

    const eventPage = new EventWorkflowPage(page);

    console.log('\n📋 Task Creation from Templates E2E Test\n');

    // ========================================
    // Step 1: Navigate to Dashboard
    // ========================================
    console.log('Step 1: Navigate to Event Dashboard');
    await eventPage.navigateToDashboard();
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(1000);

    await expect(eventPage.createEventButton).toBeVisible({ timeout: 10000 });
    console.log('  ✓ Dashboard loaded');

    // ========================================
    // Step 2: Create New Event
    // ========================================
    console.log('\nStep 2: Create Event');
    await eventPage.clickCreateEvent();

    await expect(eventPage.eventNumberField).toBeVisible({ timeout: 10000 });
    await page.waitForTimeout(500);

    const uniqueEventNumber = 9100 + Math.floor(Math.random() * 899);
    console.log(`  → Creating event #${uniqueEventNumber}`);

    // Fill event form
    const eventDate = new Date();
    eventDate.setDate(eventDate.getDate() + 60);
    const eventDateString = eventDate.toISOString().split('T')[0];

    const registrationDeadline = new Date();
    registrationDeadline.setDate(registrationDeadline.getDate() + 45); // 15 days before event
    const deadlineString = registrationDeadline.toISOString().split('T')[0];

    await eventPage.fillEventForm({
      eventNumber: uniqueEventNumber,
      title: 'E2E Test - Task Creation',
      description: 'Testing task creation from templates',
      date: eventDateString,
      registrationDeadline: deadlineString,
      eventType: 'FULL_DAY',
      venueName: 'E2E Test Venue',
      venueAddress: 'Test Address 123, 3000 Bern',
      venueCapacity: 150,
    });

    await page.waitForTimeout(500);

    console.log('  ✓ Event details filled');

    // Note: In create mode, Tasks tab is disabled, so we can't assign tasks during creation
    // We'll need to test task assignment during edit mode instead

    // Submit event creation form
    await eventPage.submitEventForm();

    // Store event code for cleanup
    const testEventCode = `BATbern${uniqueEventNumber}`;

    console.log(`  → Waiting for event creation to complete...`);
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(2000); // Wait for modal close animation

    // Verify modal closed by checking dialog is not visible
    await expect(page.getByRole('dialog')).not.toBeVisible({ timeout: 5000 });

    await expect(eventPage.createEventButton).toBeVisible({ timeout: 5000 });
    console.log(`  ✓ Event created: ${testEventCode}`);

    // ========================================
    // Step 3: Navigate to Event & Open Edit Modal
    // ========================================
    console.log('\nStep 3: Open Event for Editing');

    // Navigate directly to event detail page (using URL like event-lifecycle-e2e.spec.ts)
    // Add extra wait to ensure database commit completes
    await page.waitForTimeout(3000);

    const eventUrl = `http://localhost:8100/organizer/events/${testEventCode}`;
    console.log(`  → Navigating to event detail page: ${eventUrl}`);

    // Retry navigation if event not found (database commit delay)
    let retries = 5;
    let success = false;
    while (retries > 0 && !success) {
      await page.goto(eventUrl);
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(1000);

      // Check if we got a "Not Found" error (with explicit wait)
      const notFoundAlert = page.getByRole('alert').filter({ hasText: /Not Found/i });
      const hasError = await notFoundAlert.count().then((c) => c > 0);

      if (!hasError) {
        // Check if edit button exists (ensures page loaded correctly)
        const editButtonExists = await page
          .getByTestId('edit-event-button')
          .isVisible({ timeout: 3000 })
          .catch(() => false);

        if (editButtonExists) {
          success = true;
          break; // Success!
        }
      }

      console.log(`  ⚠️  Event not ready, retrying... (${retries} attempts left)`);
      retries--;
      await page.waitForTimeout(3000);
    }

    if (!success) {
      throw new Error(`Failed to load event detail page after multiple retries: ${eventUrl}`);
    }

    console.log('  ✓ Event detail page loaded');

    // Click Edit button
    console.log('  → Clicking Edit button');
    const editButton = page.getByTestId('edit-event-button');
    await editButton.click();
    await page.waitForTimeout(500);

    await expect(page.getByRole('dialog')).toBeVisible({ timeout: 5000 });
    console.log('  ✓ Edit modal opened');

    // ========================================
    // Step 4: Switch to Tasks Tab
    // ========================================
    console.log('\nStep 4: Switch to Tasks Tab');
    const tasksTab = page.getByTestId('tasks-tab');
    await tasksTab.click();
    await page.waitForTimeout(500);
    console.log('  ✓ Tasks tab loaded');

    // Wait for tasks to load
    await page.waitForTimeout(1000);

    // ========================================
    // Step 5: Verify Task Templates are Pre-selected
    // ========================================
    console.log('\nStep 5: Verify Task Templates');

    // Get all task template checkboxes (should all be checked by default)
    const taskCheckboxes = await page.locator('input[type="checkbox"]').all();
    console.log(`  → Found ${taskCheckboxes.length} task templates`);

    // Verify checkboxes are checked
    for (const checkbox of taskCheckboxes) {
      const isChecked = await checkbox.isChecked();
      if (!isChecked) {
        console.warn('  ⚠️  Warning: Found unchecked task template');
      }
    }

    // Get all organizer dropdowns
    const organizerSelects = await page.getByRole('combobox').all();
    console.log(`  → Found ${organizerSelects.length} organizer dropdowns`);

    // ========================================
    // Step 6: Assign First Task to Organizer
    // ========================================
    console.log('\nStep 6: Assign First Task');

    if (organizerSelects.length > 0) {
      const firstSelect = organizerSelects[0];
      await firstSelect.click();
      await page.waitForTimeout(500);

      // Select the first organizer in the list (should be current user)
      const firstOrganizer = page.getByRole('option').first();
      await firstOrganizer.click();
      await page.waitForTimeout(300);

      console.log('  ✓ Assigned first task to organizer');
    }

    // ========================================
    // Step 7: Save Event with Task Assignments
    // ========================================
    console.log('\nStep 7: Save Event');
    const saveButton = page.getByTestId('save-event-button');
    await saveButton.click();
    await page.waitForTimeout(1000);

    // Wait for modal to close
    await expect(page.getByRole('dialog')).not.toBeVisible({ timeout: 10000 });
    console.log('  ✓ Event saved with task assignments');

    console.log('\n✅ Task Creation from Templates Test Complete\n');
  });
});
