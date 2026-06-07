import { test, expect } from '@playwright/test';

/**
 * Story 11.E.3 (AC10): speaker logs in via Cognito (token already in storage state via
 * the `speaker` Playwright project) and lands on the dashboard.
 *
 * Activated by the `SPEAKER_AUTH_TOKEN` env var; the `speaker` project's storage state
 * is written by `e2e/global-setup.ts`. Skips gracefully when no speaker token is set.
 */
test.describe('Speaker portal — dashboard', { tag: '@gate' }, () => {
  test.skip(
    !process.env.SPEAKER_AUTH_TOKEN,
    'SPEAKER_AUTH_TOKEN not set — run setup-test-users first'
  );

  test('should_renderDashboard_when_speakerVisits', async ({ page }) => {
    await page.goto('/speaker-portal/dashboard');
    await expect(page).toHaveURL(/\/speaker-portal\/dashboard/);
    await expect(page.getByTestId('speaker-dashboard')).toBeVisible({ timeout: 15000 });
  });
});
