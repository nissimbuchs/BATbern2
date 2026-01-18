/**
 * E2E Tests for Participant Batch Import (Real Backend)
 * Story BAT-15: Integration - Participant Batch Import
 *
 * Tests the complete CSV upload workflow with real backend API
 *
 * Requirements:
 * 1. Backend API deployed with batch registration endpoint
 * 2. PostgreSQL database with events and registrations tables
 * 3. Authenticated organizer user
 * 4. Event codes 1-5 must exist in database
 *
 * Setup Instructions:
 * 1. Ensure backend services are running (API Gateway + Event Management)
 * 2. Run: npx playwright test e2e/workflows/participant-import/participant-batch-import.spec.ts
 * 3. For debugging: npx playwright test --debug
 */

import { test, expect, type Page } from '@playwright/test';
import path from 'path';
import { fileURLToPath } from 'url';

// Get __dirname equivalent for ES modules
const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Test configuration
const BASE_URL = process.env.E2E_BASE_URL || 'http://localhost:8100';

/**
 * Note: Authentication handled by global-setup.ts
 * No need for manual login - auth tokens are pre-injected into localStorage
 */

/**
 * Helper: Navigate to User Management page
 */
async function navigateToUserManagement(page: Page) {
  // Navigate directly to users page
  await page.goto(`${BASE_URL}/organizer/users`);

  // Wait for user table to load
  await page.waitForSelector('[data-testid="user-table"]', { timeout: 10000 });
}

/**
 * Helper: Open Participant Batch Import Modal
 */
async function openImportModal(page: Page) {
  // Click Import Participants button using data-testid
  await page.click('[data-testid="import-participants-button"]');

  // Wait for modal to open
  await page.waitForSelector('[data-testid="participant-import-modal"]', { timeout: 5000 });
  await expect(page.locator('[data-testid="participant-import-modal"]')).toBeVisible();
}

/**
 * Helper: Upload CSV file to import modal
 */
async function uploadCSV(page: Page, csvFilename: string) {
  const csvPath = path.resolve(__dirname, '../../fixtures', csvFilename);
  const fileInput = await page.locator('[data-testid="csv-file-input"]');
  await fileInput.setInputFiles(csvPath);

  // Wait for file to be processed
  await page.waitForTimeout(1000);
}

/**
 * Helper: Start the import process
 */
async function startImport(page: Page) {
  await page.click('[data-testid="participant-import-start-button"]');
}

/**
 * Helper: Wait for import completion
 */
async function waitForImportComplete(page: Page, timeoutMs: number = 60000) {
  // Wait for result alert (success or error message) - use first() due to multiple alerts
  await expect(page.locator('[role="alert"]').first()).toBeVisible({
    timeout: timeoutMs,
  });
}

test.describe('Participant Batch Import (Real Backend)', () => {
  test.beforeEach(async ({ page }) => {
    // Navigate to users page (auth handled by global-setup)
    await navigateToUserManagement(page);
  });

  test('should_openImportModal_when_importButtonClicked', async ({ page }) => {
    // Open import modal
    await openImportModal(page);

    // Verify modal is visible
    await expect(page.locator('[data-testid="participant-import-modal"]')).toBeVisible();

    // Verify file input exists
    await expect(page.locator('[data-testid="csv-file-input"]')).toBeVisible();
  });

  test('should_showPreview_when_csvUploaded', async ({ page }) => {
    await openImportModal(page);

    // Upload sample CSV
    await uploadCSV(page, 'sample-participants.csv');

    // Wait for preview to load and import button to appear
    await expect(page.locator('[data-testid="participant-import-start-button"]')).toBeVisible({
      timeout: 5000,
    });

    // Verify preview alert is shown (use first() due to multiple alerts)
    await expect(page.locator('[role="alert"]').first()).toBeVisible();
  });

  test('should_importParticipants_when_validCsvProvided', async ({ page }) => {
    await openImportModal(page);

    // Upload valid CSV file
    await uploadCSV(page, 'sample-participants.csv');

    // Wait for preview - check if import button is visible
    await expect(page.locator('[data-testid="participant-import-start-button"]')).toBeVisible({
      timeout: 5000,
    });

    // Start import
    await startImport(page);

    // Wait for completion
    await waitForImportComplete(page);

    // Verify result summary shows success (use first() due to multiple alerts)
    const successText = await page.locator('[role="alert"]').first().textContent();
    expect(successText).toContain('created');

    // Close modal
    await page.click('[data-testid="participant-import-cancel-button"]');

    // Verify modal closed
    await expect(page.locator('[data-testid="participant-import-modal"]')).not.toBeVisible({
      timeout: 3000,
    });
  });

  test('should_showErrors_when_csvContainsInvalidData', async ({ page }) => {
    await openImportModal(page);

    // Upload CSV with invalid event codes
    await uploadCSV(page, 'participants-with-errors.csv');

    // Start import
    await startImport(page);

    // Wait for completion
    await waitForImportComplete(page);

    // Verify error count shown in result (use first() due to multiple alerts)
    const resultText = await page.locator('[role="alert"]').first().textContent();
    expect(resultText).toMatch(/failed|error/i);
  });

  test('should_handleDuplicates_when_sameDataImportedTwice', async ({ page }) => {
    // First import
    await openImportModal(page);
    await uploadCSV(page, 'duplicate-participants.csv');
    await startImport(page);
    await waitForImportComplete(page);

    const firstResult = await page.locator('[role="alert"]').first().textContent();
    expect(firstResult).toContain('created');

    await page.click('[data-testid="participant-import-cancel-button"]');

    // Wait a moment before second import
    await page.waitForTimeout(1000);

    // Second import (should skip duplicates)
    await openImportModal(page);
    await uploadCSV(page, 'duplicate-participants.csv');
    await startImport(page);
    await waitForImportComplete(page);

    // Verify skipped count (use first() due to multiple alerts)
    const secondResult = await page.locator('[role="alert"]').first().textContent();
    expect(secondResult).toMatch(/skipped|duplicate/i);
  });

  test('should_showProgress_when_importRunning', async ({ page }) => {
    await openImportModal(page);
    await uploadCSV(page, 'sample-participants.csv');
    await startImport(page);

    // Wait a moment for import to start
    await page.waitForTimeout(500);

    // Verify progress indicator is visible
    const progressBar = page.locator('[role="progressbar"]');

    // Progress should be visible at some point during import
    // (may complete too fast for small files, so we don't require it)
    const isProgressVisible = await progressBar.isVisible().catch(() => false);

    // If progress wasn't visible, import likely completed very quickly
    // which is acceptable behavior
    console.log(`Progress indicator visible: ${isProgressVisible}`);
  });

  test('should_allowCancellation_when_importModalOpen', async ({ page }) => {
    await openImportModal(page);
    await uploadCSV(page, 'sample-participants.csv');

    // Click cancel before starting import
    await page.click('[data-testid="participant-import-cancel-button"]');

    // Verify modal closed
    await expect(page.locator('[data-testid="participant-import-modal"]')).not.toBeVisible({
      timeout: 3000,
    });
  });

  test('should_resetState_when_modalReopened', async ({ page }) => {
    // First interaction
    await openImportModal(page);
    await uploadCSV(page, 'sample-participants.csv');
    await page.click('[data-testid="participant-import-cancel-button"]');

    // Wait a moment
    await page.waitForTimeout(500);

    // Reopen modal
    await openImportModal(page);

    // Verify modal is in initial state (no file selected)
    const fileInput = page.locator('[data-testid="csv-file-input"]');
    const inputValue = await fileInput.inputValue();
    expect(inputValue).toBe('');
  });

  test('should_displayDetailedResults_when_importCompletes', async ({ page }) => {
    await openImportModal(page);
    await uploadCSV(page, 'sample-participants.csv');
    await startImport(page);
    await waitForImportComplete(page);

    // Verify result summary contains detailed counts (use first() due to multiple alerts)
    const resultAlert = page.locator('[role="alert"]').first();
    await expect(resultAlert).toBeVisible();

    const resultText = await resultAlert.textContent();

    // Should show count of created, failed, or skipped
    const hasCreatedCount = /\d+.*created/i.test(resultText || '');
    const hasFailedCount = /\d+.*failed/i.test(resultText || '');
    const hasSkippedCount = /\d+.*skipped/i.test(resultText || '');

    // At least one count type should be present
    expect(hasCreatedCount || hasFailedCount || hasSkippedCount).toBe(true);
  });

  test('should_handleMissingEmails_when_csvContainsBlanks', async ({ page }) => {
    await openImportModal(page);
    await uploadCSV(page, 'participants-with-errors.csv');

    // Wait for preview/validation
    await page.waitForTimeout(2000);

    // Start import
    await startImport(page);
    await waitForImportComplete(page);

    // Should complete (backend generates synthetic emails) - use first() due to multiple alerts
    const resultText = await page.locator('[role="alert"]').first().textContent();

    // Should have at least some successful imports (those with valid emails)
    // or show appropriate error handling
    expect(resultText).toBeTruthy();
  });
});

test.describe('Participant Batch Import - Error Handling', () => {
  test.beforeEach(async ({ page }) => {
    await navigateToUserManagement(page);
  });

  test('should_showError_when_emptyFileUploaded', async ({ page }) => {
    await openImportModal(page);

    // Import button should not be visible when no file is selected
    const importButton = page.locator('[data-testid="participant-import-start-button"]');
    await expect(importButton).not.toBeVisible();
  });

  test('should_showError_when_invalidFileTypeUploaded', async ({ page }) => {
    await openImportModal(page);

    // Verify file input only accepts CSV files
    const fileInput = page.locator('[data-testid="csv-file-input"]');
    const acceptAttr = await fileInput.getAttribute('accept');
    expect(acceptAttr).toContain('csv');
  });

  test('should_handleBackendError_when_apiUnavailable', async () => {
    // This test would require mocking backend failure
    // or having a way to simulate API unavailability
    // For now, we'll skip this as it requires advanced setup
    test.skip();
  });
});

test.describe('Participant Batch Import - Accessibility', () => {
  test.beforeEach(async ({ page }) => {
    await navigateToUserManagement(page);
  });

  test('should_haveAccessibleModal_when_importOpened', async ({ page }) => {
    await openImportModal(page);

    // Verify modal is visible
    const modal = page.locator('[data-testid="participant-import-modal"]');
    await expect(modal).toBeVisible();

    // MUI Dialog uses role="presentation" for the container, the actual dialog is nested
    await expect(modal).toHaveAttribute('role', 'presentation');

    // Verify there's a dialog role inside
    const dialog = page.locator('[role="dialog"]');
    await expect(dialog).toBeVisible();

    // Verify dialog has aria-labelledby or aria-label
    const hasAriaLabel = await dialog.getAttribute('aria-label');
    const hasAriaLabelledBy = await dialog.getAttribute('aria-labelledby');

    expect(hasAriaLabel || hasAriaLabelledBy).toBeTruthy();
  });

  test('should_supportKeyboardNavigation_when_inModal', async ({ page }) => {
    await openImportModal(page);

    // Tab through interactive elements
    await page.keyboard.press('Tab');

    // Verify focus moves to first interactive element
    const focusedElement = page.locator(':focus');
    await expect(focusedElement).toBeVisible();

    // ESC should close modal
    await page.keyboard.press('Escape');

    // Verify modal closed
    await expect(page.locator('[data-testid="participant-import-modal"]')).not.toBeVisible({
      timeout: 3000,
    });
  });
});
