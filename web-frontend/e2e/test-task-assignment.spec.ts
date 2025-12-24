/**
 * Manual Test Script for Task Assignment
 *
 * This script opens a browser window for manual testing:
 * 1. Navigates to login page
 * 2. User logs in manually
 * 3. User creates event and assigns tasks
 * 4. Browser stays open for inspection
 */

import { test } from '@playwright/test';

test('Manual task assignment test', async ({ page }) => {
  // Set a long timeout for manual testing
  test.setTimeout(600000); // 10 minutes

  // Navigate to login page
  await page.goto('http://localhost:8100/login');

  console.log('\n=== Manual Test Session Started ===');
  console.log('1. Please login with your credentials');
  console.log('2. Create a new event and fill all attributes');
  console.log('3. Navigate to Tasks tab');
  console.log('4. Assign all tasks to yourself');
  console.log('5. Save the event');
  console.log('6. Check backend logs for task creation');
  console.log('=====================================\n');

  // Wait for user to login (detect navigation away from login page)
  await page.waitForURL((url) => !url.pathname.includes('/login'), {
    timeout: 300000, // 5 minutes for login
  });

  console.log('✓ Login detected, continuing...');

  // Wait for a very long time to allow manual testing
  // The test will stay open until you close the browser or hit the timeout
  await page.waitForTimeout(600000); // Wait 10 minutes or until manual close
});
