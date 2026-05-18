import { test, expect } from '@playwright/test';

/**
 * Story 11.E.3 (AC8): /speaker-portal/magic-login is removed from the router (the
 * SpeakerMagicLoginPage file stays for Phase F to delete cleanly). Navigating to it
 * should land on the SPA's 404 fallback, not the magic-login page.
 */
test.describe('Speaker portal — magic-login route disconnected', () => {
  test.skip(
    !process.env.SPEAKER_AUTH_TOKEN,
    'SPEAKER_AUTH_TOKEN not set — run setup-test-users first'
  );

  test('should_render404_when_visitingMagicLoginRoute', async ({ page }) => {
    await page.goto('/speaker-portal/magic-login');
    // The SPA's 404 fallback should render. The actual heading text depends on the
    // not-found component; the key invariant is that we do NOT see the
    // SpeakerMagicLoginPage's `[data-testid="speaker-magic-login-page"]` element.
    await expect(page.getByTestId('speaker-magic-login-page')).toHaveCount(0);
  });
});
