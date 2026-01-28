/**
 * E2E Tests for Speaker Submitted Content Display on Organizer Dashboard
 * Story 6.3: Speaker Content Self-Submission Portal
 *
 * Tests that organizers can see submitted presentation content:
 * 1. Submitted title and abstract preview in speaker status lanes
 * 2. Content status chip (SUBMITTED, APPROVED, REVISION_NEEDED)
 * 3. Full content details in speaker details drawer
 *
 * Setup Requirements:
 * 1. E2E test tokens must be available (generated via /api/v1/e2e-test/tokens/generate-e2e-set)
 * 2. Backend services running with content submission data
 * 3. Authenticated organizer session
 *
 * Note: Tests use the E2E token system to create speakers with submitted content.
 */

import { test, expect, type Page } from '@playwright/test';

// Test configuration
const BASE_URL = process.env.E2E_BASE_URL || 'http://localhost:8100';
const API_URL = process.env.E2E_API_URL || 'http://localhost:8000';

// Test data for content submission
const TEST_CONTENT = {
  title: 'E2E Test: Cloud-Native Architecture Patterns',
  abstract:
    'This presentation explores modern cloud-native architecture patterns including microservices, event-driven design, and containerization strategies for enterprise applications.',
};

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

  // Wait for redirect to dashboard
  await page.waitForURL(/organizer/, { timeout: 15000 });
}

/**
 * Helper: Generate E2E test tokens via API
 * Returns tokens for a speaker with content submission capability
 */
async function generateE2ETokens(page: Page): Promise<{
  eventCode: string;
  speakerId: string;
  speakerName: string;
  tokens: { respond: string; view: string; submit: string };
} | null> {
  try {
    const response = await page.request.post(`${API_URL}/api/v1/e2e-test/tokens/generate-e2e-set`);

    if (!response.ok()) {
      console.log('Failed to generate E2E tokens:', response.status());
      return null;
    }

    const data = await response.json();
    return {
      eventCode: data.eventCode,
      speakerId: data.speakerId,
      speakerName: data.speakerName,
      tokens: {
        respond: data.respondToken,
        view: data.viewToken,
        submit: data.submitToken,
      },
    };
  } catch (error) {
    console.log('Error generating E2E tokens:', error);
    return null;
  }
}

/**
 * Helper: Submit content for a speaker via API
 */
async function submitSpeakerContent(
  page: Page,
  token: string,
  title: string,
  abstract: string
): Promise<boolean> {
  try {
    const response = await page.request.post(
      `${API_URL}/api/v1/speaker-portal/content/submit?token=${token}`,
      {
        data: { title, abstract },
        headers: { 'Content-Type': 'application/json' },
      }
    );

    return response.ok();
  } catch (error) {
    console.log('Error submitting content:', error);
    return false;
  }
}

test.describe('Speaker Content Display on Organizer Dashboard (Story 6.3)', () => {
  test.describe('Content Display in Speaker Status Lanes', () => {
    test('should display submitted title in speaker card', async ({ page }) => {
      // Generate E2E tokens
      const testData = await generateE2ETokens(page);
      if (!testData) {
        test.skip(true, 'E2E token generation not available');
        return;
      }

      // Submit content for the speaker
      const submitted = await submitSpeakerContent(
        page,
        testData.tokens.submit,
        TEST_CONTENT.title,
        TEST_CONTENT.abstract
      );

      if (!submitted) {
        test.skip(true, 'Content submission failed - speaker may not have session assigned');
        return;
      }

      // Login as organizer
      await loginAsOrganizer(page);

      // Navigate to speaker status dashboard
      await page.goto(`${BASE_URL}/organizer/events/${testData.eventCode}/speakers/status`);
      await page.waitForLoadState('networkidle');

      // Find the speaker card by test ID or by speaker name
      const speakerCard = page
        .locator(`[data-testid^="speaker-card-"]`)
        .filter({ hasText: testData.speakerName });

      // Verify submitted title is displayed
      await expect(speakerCard).toContainText(TEST_CONTENT.title, { timeout: 10000 });
    });

    test('should display abstract preview (truncated) in speaker card', async ({ page }) => {
      const testData = await generateE2ETokens(page);
      if (!testData) {
        test.skip(true, 'E2E token generation not available');
        return;
      }

      const submitted = await submitSpeakerContent(
        page,
        testData.tokens.submit,
        TEST_CONTENT.title,
        TEST_CONTENT.abstract
      );

      if (!submitted) {
        test.skip(true, 'Content submission failed');
        return;
      }

      await loginAsOrganizer(page);
      await page.goto(`${BASE_URL}/organizer/events/${testData.eventCode}/speakers/status`);
      await page.waitForLoadState('networkidle');

      const speakerCard = page
        .locator(`[data-testid^="speaker-card-"]`)
        .filter({ hasText: testData.speakerName });

      // Verify abstract preview is shown (first part of abstract)
      // The abstract should be truncated to 2 lines, so check for the beginning
      const abstractStart = TEST_CONTENT.abstract.substring(0, 30);
      await expect(speakerCard).toContainText(abstractStart, { timeout: 10000 });
    });

    test('should display content status chip (SUBMITTED)', async ({ page }) => {
      const testData = await generateE2ETokens(page);
      if (!testData) {
        test.skip(true, 'E2E token generation not available');
        return;
      }

      const submitted = await submitSpeakerContent(
        page,
        testData.tokens.submit,
        TEST_CONTENT.title,
        TEST_CONTENT.abstract
      );

      if (!submitted) {
        test.skip(true, 'Content submission failed');
        return;
      }

      await loginAsOrganizer(page);
      await page.goto(`${BASE_URL}/organizer/events/${testData.eventCode}/speakers/status`);
      await page.waitForLoadState('networkidle');

      const speakerCard = page
        .locator(`[data-testid^="speaker-card-"]`)
        .filter({ hasText: testData.speakerName });

      // Verify content status chip is displayed
      const statusChip = speakerCard.locator('.MuiChip-root').filter({ hasText: /SUBMITTED/i });
      await expect(statusChip).toBeVisible({ timeout: 10000 });
    });

    test('should NOT display content section for speakers without submitted content', async ({
      page,
    }) => {
      const testData = await generateE2ETokens(page);
      if (!testData) {
        test.skip(true, 'E2E token generation not available');
        return;
      }

      // Don't submit content - just check the speaker appears without content section
      await loginAsOrganizer(page);
      await page.goto(`${BASE_URL}/organizer/events/${testData.eventCode}/speakers/status`);
      await page.waitForLoadState('networkidle');

      const speakerCard = page
        .locator(`[data-testid^="speaker-card-"]`)
        .filter({ hasText: testData.speakerName });

      // Speaker card should exist
      await expect(speakerCard).toBeVisible({ timeout: 10000 });

      // But should NOT contain the test content title (no content submitted)
      await expect(speakerCard).not.toContainText(TEST_CONTENT.title);
    });
  });

  test.describe('Content Display in Speaker Details Drawer', () => {
    test('should display full submitted content in details drawer', async ({ page }) => {
      const testData = await generateE2ETokens(page);
      if (!testData) {
        test.skip(true, 'E2E token generation not available');
        return;
      }

      const submitted = await submitSpeakerContent(
        page,
        testData.tokens.submit,
        TEST_CONTENT.title,
        TEST_CONTENT.abstract
      );

      if (!submitted) {
        test.skip(true, 'Content submission failed');
        return;
      }

      await loginAsOrganizer(page);
      await page.goto(`${BASE_URL}/organizer/events/${testData.eventCode}/speakers/status`);
      await page.waitForLoadState('networkidle');

      // Find and click the speaker card to open details drawer
      const speakerCard = page
        .locator(`[data-testid^="speaker-card-"]`)
        .filter({ hasText: testData.speakerName });
      await speakerCard.click();

      // Wait for drawer to open
      const drawer = page.locator('[data-testid="speaker-details-drawer"], .MuiDrawer-root');
      await expect(drawer).toBeVisible({ timeout: 10000 });

      // Verify "Submitted Content" section header exists
      await expect(drawer.locator('text=/Submitted Content/i')).toBeVisible();

      // Verify full title is displayed
      await expect(drawer).toContainText(TEST_CONTENT.title);

      // Verify full abstract is displayed (not truncated)
      await expect(drawer).toContainText(TEST_CONTENT.abstract);
    });

    test('should display submission timestamp in details drawer', async ({ page }) => {
      const testData = await generateE2ETokens(page);
      if (!testData) {
        test.skip(true, 'E2E token generation not available');
        return;
      }

      const submitted = await submitSpeakerContent(
        page,
        testData.tokens.submit,
        TEST_CONTENT.title,
        TEST_CONTENT.abstract
      );

      if (!submitted) {
        test.skip(true, 'Content submission failed');
        return;
      }

      await loginAsOrganizer(page);
      await page.goto(`${BASE_URL}/organizer/events/${testData.eventCode}/speakers/status`);
      await page.waitForLoadState('networkidle');

      const speakerCard = page
        .locator(`[data-testid^="speaker-card-"]`)
        .filter({ hasText: testData.speakerName });
      await speakerCard.click();

      const drawer = page.locator('[data-testid="speaker-details-drawer"], .MuiDrawer-root');
      await expect(drawer).toBeVisible({ timeout: 10000 });

      // Verify timestamp is displayed (format: "Submitted on" or similar)
      await expect(drawer.locator('text=/Submitted on|submitted at/i')).toBeVisible();
    });

    test('should NOT display content section in drawer when no content submitted', async ({
      page,
    }) => {
      const testData = await generateE2ETokens(page);
      if (!testData) {
        test.skip(true, 'E2E token generation not available');
        return;
      }

      // Don't submit content
      await loginAsOrganizer(page);
      await page.goto(`${BASE_URL}/organizer/events/${testData.eventCode}/speakers/status`);
      await page.waitForLoadState('networkidle');

      const speakerCard = page
        .locator(`[data-testid^="speaker-card-"]`)
        .filter({ hasText: testData.speakerName });
      await speakerCard.click();

      const drawer = page.locator('[data-testid="speaker-details-drawer"], .MuiDrawer-root');
      await expect(drawer).toBeVisible({ timeout: 10000 });

      // "Submitted Content" section should NOT be visible
      await expect(drawer.locator('text=/Submitted Content/i')).not.toBeVisible();
    });
  });

  test.describe('Content Status Display', () => {
    test('should display appropriate color for SUBMITTED status', async ({ page }) => {
      const testData = await generateE2ETokens(page);
      if (!testData) {
        test.skip(true, 'E2E token generation not available');
        return;
      }

      const submitted = await submitSpeakerContent(
        page,
        testData.tokens.submit,
        TEST_CONTENT.title,
        TEST_CONTENT.abstract
      );

      if (!submitted) {
        test.skip(true, 'Content submission failed');
        return;
      }

      await loginAsOrganizer(page);
      await page.goto(`${BASE_URL}/organizer/events/${testData.eventCode}/speakers/status`);
      await page.waitForLoadState('networkidle');

      const speakerCard = page
        .locator(`[data-testid^="speaker-card-"]`)
        .filter({ hasText: testData.speakerName });

      // Find the status chip and verify it has info/blue color class
      const statusChip = speakerCard.locator('.MuiChip-root').filter({ hasText: /SUBMITTED/i });
      await expect(statusChip).toBeVisible({ timeout: 10000 });

      // Verify the chip has the info color (MUI adds colorInfo class)
      await expect(statusChip).toHaveClass(/MuiChip-colorInfo|MuiChip-filledInfo/);
    });
  });

  test.describe('API Integration', () => {
    test('should include submitted content in speaker pool API response', async ({ page }) => {
      const testData = await generateE2ETokens(page);
      if (!testData) {
        test.skip(true, 'E2E token generation not available');
        return;
      }

      const submitted = await submitSpeakerContent(
        page,
        testData.tokens.submit,
        TEST_CONTENT.title,
        TEST_CONTENT.abstract
      );

      if (!submitted) {
        test.skip(true, 'Content submission failed');
        return;
      }

      await loginAsOrganizer(page);

      // Intercept API response
      const responsePromise = page.waitForResponse(
        (response) =>
          response.url().includes('/speaker-pool') && response.request().method() === 'GET'
      );

      await page.goto(`${BASE_URL}/organizer/events/${testData.eventCode}/speakers/status`);

      const response = await responsePromise;
      expect(response.status()).toBe(200);

      const speakers = await response.json();
      expect(Array.isArray(speakers)).toBe(true);

      // Find the test speaker in the response
      const testSpeaker = speakers.find(
        (s: { id: string; speakerName: string }) =>
          s.id === testData.speakerId || s.speakerName === testData.speakerName
      );

      expect(testSpeaker).toBeDefined();
      expect(testSpeaker.submittedTitle).toBe(TEST_CONTENT.title);
      expect(testSpeaker.submittedAbstract).toBe(TEST_CONTENT.abstract);
    });
  });
});
