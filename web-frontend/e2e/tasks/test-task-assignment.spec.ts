/**
 * Automated E2E Test: Task Assignment
 *
 * Tests that tasks can be assigned to organizers during event creation/editing.
 * Uses language-independent selectors (data-testid) following BAT-93 standards.
 */

import { test, expect } from '@playwright/test';
import { EventWorkflowPage } from '../workflows/documentation/page-objects/EventWorkflowPage';

test.describe('Task Assignment', () => {
  test('should assign tasks to organizers when editing an event', async ({ page }) => {
    test.setTimeout(120000); // 2 minutes

    const eventPage = new EventWorkflowPage(page);

    console.log('\n📋 Task Assignment E2E Test\n');

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

    const uniqueEventNumber = 9000 + Math.floor(Math.random() * 999);
    const testEventCode = `BATbern${uniqueEventNumber}`;

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
      title: 'E2E Test - Task Assignment',
      description: 'Testing task assignment workflow',
      date: eventDateString,
      registrationDeadline: deadlineString,
      eventType: 'EVENING',
      venueName: 'Test Venue',
      venueAddress: 'Test Address 123, 3000 Bern',
      venueCapacity: 200,
    });

    await page.waitForTimeout(500);

    // Submit event creation form
    await eventPage.submitEventForm();

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

    // ========================================
    // Step 5: Assign Tasks to Organizers
    // ========================================
    console.log('\nStep 5: Assign Tasks to Organizers');

    // Get all task template checkboxes (should be pre-checked)
    const taskCheckboxes = await page.locator('input[type="checkbox"]').all();
    console.log(`  → Found ${taskCheckboxes.length} task templates`);

    // Define task assignments
    const taskAssignments = [
      { taskName: 'Venue Booking', assignee: 'Nissim Buchs' },
      { taskName: 'Partner Meeting', assignee: 'Daniel Kühni' },
      { taskName: 'Moderator Assignment', assignee: 'Andreas Grütter' },
    ];

    // Assign each task
    for (const { taskName, assignee } of taskAssignments) {
      console.log(`  → Assigning "${taskName}" to ${assignee}`);

      const taskRow = page.getByRole('listitem').filter({ hasText: taskName });
      const assigneeSelect = taskRow.getByRole('combobox');

      await assigneeSelect.scrollIntoViewIfNeeded();
      await assigneeSelect.click();
      await page.waitForTimeout(300);

      await page.getByRole('option', { name: assignee }).first().click();
      await page.waitForTimeout(300);

      console.log(`    ✓ Assigned to ${assignee}`);
    }

    console.log(`  ✓ All ${taskAssignments.length} tasks assigned`);

    // ========================================
    // Step 6: Save Task Assignments
    // ========================================
    console.log('\nStep 6: Save Task Assignments');
    const saveButton = page.getByTestId('save-event-button');
    await saveButton.click();
    await page.waitForTimeout(1000);

    await expect(page.getByRole('dialog')).not.toBeVisible({ timeout: 5000 });
    console.log('  ✓ Modal closed, tasks saved');

    console.log('\n✅ Task Assignment Test Complete\n');
  });
});
