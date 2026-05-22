import { test, expect } from '@playwright/test';

/**
 * Story 11.E.3 (AC10): speaker fills in title + abstract + (optional) bio + (optional)
 * presentation upload, submits, sees confirmation; status transitions to CONTENT_SUBMITTED.
 *
 * Depends on the same test-speaker seed as the respond spec.
 */
test.describe('Speaker portal — content submit', () => {
  test.skip(
    !process.env.SPEAKER_AUTH_TOKEN,
    'SPEAKER_AUTH_TOKEN not set — run setup-test-users first'
  );

  test.fixme('should_submitContent_when_speakerFillsForm', async ({ page }) => {
    // Navigate to content page for a known eventCode (placeholder until seed is wired).
    await page.goto('/speaker-portal/dashboard');
    await expect(page).toHaveURL(/\/speaker-portal\/dashboard/);
  });
});
