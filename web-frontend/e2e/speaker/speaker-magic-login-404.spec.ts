import { test, expect } from '@playwright/test';

/**
 * Story 11.E.3 (AC8): /speaker-portal/magic-login is removed from the router (the
 * SpeakerMagicLoginPage file stays for Phase F to delete cleanly). Navigating to it
 * should land on the SPA's 404 fallback, not the magic-login page.
 *
 * Code review 2026-05-18 (P19): split into authenticated and unauthenticated variants.
 * The unauthenticated case is the realistic field path — a stranger clicks an old email
 * magic-link from a public machine.
 */
test.describe('Speaker portal — magic-login route disconnected', () => {
  test.describe('authenticated speaker', () => {
    test.skip(
      !process.env.SPEAKER_AUTH_TOKEN,
      'SPEAKER_AUTH_TOKEN not set — run setup-test-users first'
    );

    test('should_render404_when_visitingMagicLoginRoute', async ({ page }) => {
      await page.goto('/speaker-portal/magic-login');
      // The SPA's 404 fallback should render. The key invariant is that we do NOT see the
      // SpeakerMagicLoginPage's `[data-testid="speaker-magic-login-page"]` element.
      await expect(page.getByTestId('speaker-magic-login-page')).toHaveCount(0);
    });
  });

  test.describe('unauthenticated visitor', () => {
    // Code review 2026-05-18 (P19): runs without storageState so the page acts as if no
    // Cognito session is present (cached SPA bundle following an old email link from a
    // public machine).
    test.use({ storageState: { cookies: [], origins: [] } });

    test('should_render404_or_login_when_visitingMagicLoginRoute_unauthenticated', async ({
      page,
    }) => {
      await page.goto('/speaker-portal/magic-login');
      // Either the SPA 404 OR a redirect to /login is acceptable — both are correct
      // behaviours for a deleted route hit by an unauthenticated visitor. The invariant
      // is that the deprecated SpeakerMagicLoginPage does NOT render.
      await expect(page.getByTestId('speaker-magic-login-page')).toHaveCount(0);
    });
  });
});
