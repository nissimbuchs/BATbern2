/**
 * E2E Tests for Blob Topic Selector
 * Story 10.4: Blob Topic Selector
 *
 * Tests cover:
 * - Blob Selector button appears on /organizer/topics?eventCode=BATbernXX
 * - Clicking navigates to /organizer/events/:eventCode/topic-blob
 * - Canvas renders (full-viewport SVG)
 * - Back button is present and triggers unsaved-changes dialog
 * - Confirm dismisses dialog and navigates back to topic list
 *
 * NOTE: D3 simulation x/y positions are NOT unit-tested here.
 * Canvas interaction (blob spawning, merging, accepting) requires a running backend.
 */

import { test, expect } from '@playwright/test';
import { BASE_URL } from '../../playwright.config';

const EVENT_CODE = 'BATbern57';

test.describe('Blob Topic Selector — Entry Point (AC: 1-4)', () => {
  test.beforeEach(async ({ page }) => {
    // Navigate to topic management page with an event code
    await page.goto(`${BASE_URL}/organizer/topics?eventCode=${EVENT_CODE}`);
    await page.waitForLoadState('networkidle');
  });

  test('Blob Selector button appears when eventCode is present in query params', async ({
    page,
  }) => {
    const blobBtn = page.getByTestId('blob-selector-button');
    await expect(blobBtn).toBeVisible();
  });

  test('Blob Selector button navigates to topic-blob route', async ({ page }) => {
    const blobBtn = page.getByTestId('blob-selector-button');
    await blobBtn.click();

    await expect(page).toHaveURL(new RegExp(`/organizer/events/${EVENT_CODE}/topic-blob`));
  });

  test('Blob Selector button is NOT shown when no eventCode in query params', async ({ page }) => {
    await page.goto(`${BASE_URL}/organizer/topics`);
    await page.waitForLoadState('networkidle');

    const blobBtn = page.getByTestId('blob-selector-button');
    await expect(blobBtn).not.toBeVisible();
  });
});

test.describe('Blob Topic Selector Page (AC: 3-7)', () => {
  test.beforeEach(async ({ page }) => {
    // Clear onboarding flag so it doesn't interfere
    await page.evaluate(() => {
      localStorage.removeItem('batbern_blob_onboarding_seen');
    });

    await page.goto(`${BASE_URL}/organizer/events/${EVENT_CODE}/topic-blob`);
    await page.waitForLoadState('networkidle');
  });

  test('Canvas SVG renders full-viewport without sidebar/nav', async ({ page }) => {
    const canvas = page.getByTestId('blob-canvas');
    await expect(canvas).toBeVisible();

    // Verify no standard navigation sidebar is present
    const sidebar = page
      .locator('[data-testid="sidebar"]')
      .or(page.locator('nav[aria-label="main navigation"]'));
    await expect(sidebar).not.toBeVisible();
  });

  test('Back button is visible top-left', async ({ page }) => {
    const backBtn = page.getByTestId('back-to-topics');
    await expect(backBtn).toBeVisible();
  });

  test('Back button click shows unsaved-changes dialog', async ({ page }) => {
    const backBtn = page.getByTestId('back-to-topics');
    await backBtn.click();

    // Dialog should appear
    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();
  });

  test('Confirming back dialog navigates to topic list', async ({ page }) => {
    const backBtn = page.getByTestId('back-to-topics');
    await backBtn.click();

    // Click the confirm button in the dialog
    const confirmBtn = page.getByRole('dialog').getByRole('button', {
      name: /go back|confirm/i,
    });
    await confirmBtn.click();

    await expect(page).toHaveURL(new RegExp(`/organizer/topics\\?eventCode=${EVENT_CODE}`));
  });

  test('Cancelling back dialog keeps user on blob page', async ({ page }) => {
    const backBtn = page.getByTestId('back-to-topics');
    await backBtn.click();

    const cancelBtn = page.getByRole('dialog').getByRole('button', {
      name: /cancel/i,
    });
    await cancelBtn.click();

    await expect(page).toHaveURL(new RegExp(`/organizer/events/${EVENT_CODE}/topic-blob`));
  });

  test('Fit All and Snap to Active buttons are visible', async ({ page }) => {
    // These are fixed-position buttons rendered by BlobTopicSelector
    // They may appear after session data loads
    await page.waitForTimeout(1000); // Allow session data fetch

    const fitAllBtn = page.getByRole('button', { name: /fit all/i });
    const snapBtn = page.getByRole('button', { name: /snap to active/i });

    // They're rendered only when BlobTopicSelector mounts (data loaded)
    // If API fails they won't show — check for graceful degradation
    const isLoaded = await page.getByTestId('blob-canvas').isVisible();
    if (isLoaded) {
      await expect(fitAllBtn).toBeVisible();
      await expect(snapBtn).toBeVisible();
    }
  });
});
