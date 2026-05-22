import { test, expect } from '@playwright/test';

/**
 * Story 11.E.3 (AC10): speaker accepts an invitation, sees confirmation; declines another
 * with a reason. Requires the test speaker to have at least one INVITED pool row.
 *
 * Marked todo at story-creation time because the test-speaker seed depends on Story 11.E.2
 * provisioning. The shape below is the contract this spec will assert once that seed lands.
 */
test.describe('Speaker portal — respond', () => {
  test.skip(
    !process.env.SPEAKER_AUTH_TOKEN,
    'SPEAKER_AUTH_TOKEN not set — run setup-test-users first'
  );

  test.fixme('should_acceptInvitation_when_speakerClicksAccept', async ({ page }) => {
    await page.goto('/speaker-portal/dashboard');
    // Click into the first INVITED card → respond page → ACCEPT.
    // Implementation deferred until a test event with an INVITED pool row is provisioned
    // in staging for the test speaker (Story 11.E.2 dependency).
    await expect(page).toHaveURL(/\/speaker-portal\/dashboard/);
  });
});
