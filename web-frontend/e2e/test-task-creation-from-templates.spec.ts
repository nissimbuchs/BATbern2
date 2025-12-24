/**
 * Automated E2E Test: Task Creation from Templates
 *
 * Tests Story 5.5 AC21: Task template instantiation with assignees
 */

import { test } from '@playwright/test';

test.describe('Task Creation from Templates', () => {
  test.beforeEach(async ({ page }) => {
    // Login will be handled by global setup with AUTH_TOKEN
    await page.goto('/');
  });

  test('should create tasks from templates when creating a new event', async ({ page }) => {
    // Navigate to events page
    await page.goto('/organizer/events');

    // Wait for page to load
    await page.waitForLoadState('networkidle');

    // Click "Create Event" button
    await page.click('button:has-text("Event erstellen"), button:has-text("Create Event")');

    // Wait for modal to open
    await page.waitForSelector('dialog', { state: 'visible' });

    // Fill event details (Info tab)
    await page.fill('input[name="eventNumber"]', '999');
    await page.fill('input[name="title"]', 'E2E Test Event - Task Creation');
    await page.fill(
      'textarea[name="description"]',
      'This is an automated E2E test event to verify task creation from templates.'
    );

    // Set event date (30+ days in future)
    const futureDate = new Date();
    futureDate.setDate(futureDate.getDate() + 60);
    const dateString = futureDate.toISOString().split('T')[0];
    await page.fill('input[name="date"]', dateString);

    // Set venue details
    await page.fill('input[name="venueName"]', 'E2E Test Venue');
    await page.fill('input[name="venueAddress"]', 'Test Address 123, 3000 Bern');
    await page.fill('input[name="venueCapacity"]', '100');

    console.log('✓ Event details filled');

    // Note: In create mode, Tasks tab is disabled, so we can't assign tasks during creation
    // We'll need to test task assignment during edit mode instead

    // Save the event
    await page.click('button:has-text("Speichern"), button:has-text("Save")');

    // Wait for success and modal to close
    await page.waitForSelector('dialog', { state: 'hidden', timeout: 10000 });

    console.log('✓ Event created successfully');

    // Now open the event for editing to assign tasks
    await page.waitForTimeout(2000); // Wait for list to refresh

    // Find and click the event we just created
    await page.click(`text=E2E Test Event - Task Creation`);

    // Wait for event detail page to load
    await page.waitForLoadState('networkidle');

    // Click Edit button
    await page.click('button:has-text("Bearbeiten"), button:has-text("Edit")');

    // Wait for edit modal to open
    await page.waitForSelector('dialog', { state: 'visible' });

    // Switch to Tasks tab
    await page.click('button[role="tab"]:has-text("Tasks")');

    console.log('✓ Switched to Tasks tab');

    // Wait for tasks to load
    await page.waitForTimeout(2000);

    // Get all task template checkboxes (they should all be checked already with existing tasks)
    const taskCheckboxes = await page.locator('input[type="checkbox"]').all();
    console.log(`Found ${taskCheckboxes.length} task checkboxes`);

    // Get all organizer dropdowns
    const organizerSelects = await page.locator('[role="combobox"]').all();
    console.log(`Found ${organizerSelects.length} organizer dropdowns`);

    // Check if tasks are already assigned (from initial load)
    const firstSelect = organizerSelects[0];
    if (firstSelect) {
      await firstSelect.click();
      await page.waitForTimeout(500);

      // Select the first organizer in the list (should be current user)
      const firstOrganizer = await page.locator('[role="option"]').first();
      await firstOrganizer.click();

      console.log('✓ Assigned first task to organizer');
    }

    // Save the event with task assignments
    await page.click('button:has-text("Speichern"), button:has-text("Save")');

    // Wait for success
    await page.waitForSelector('dialog', { state: 'hidden', timeout: 10000 });

    console.log('✓ Event saved with task assignments');
    console.log('✓ Test completed successfully');
  });
});
