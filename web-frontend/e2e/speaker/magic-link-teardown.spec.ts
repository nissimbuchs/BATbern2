import { test, expect } from '@playwright/test';

/**
 * Story 11.F.1 (AC4): magic-link teardown verification.
 *
 * After Phase F deletes the speaker-magic-login backend controllers and the frontend
 * SpeakerMagicLoginPage, no response on the speaker-portal happy path should set a
 * `speaker_jwt` cookie — that cookie was the legacy magic-link session marker. This
 * spec drives the speaker dashboard under Cognito Bearer auth and asserts that:
 *
 *   1. No `Set-Cookie: speaker_jwt=...` header appears on any response.
 *   2. No request fetches the deleted `/api/v1/auth/speaker-magic-login` or
 *      `/api/v1/speaker-portal/validate-token` endpoints.
 *
 * Runs under the `speaker` Playwright project (activated by SPEAKER_AUTH_TOKEN). The
 * spec is intentionally a standalone smoke test — it does not need the test-fixme
 * specs' staging Cognito test-speaker seed (those assert positive Cognito flows; this
 * one asserts absence of magic-link artefacts).
 */
test.describe('Story 11.F.1 — magic-link teardown smoke', () => {
  test.skip(
    !process.env.SPEAKER_AUTH_TOKEN,
    'SPEAKER_AUTH_TOKEN not set — run setup-test-users first'
  );

  test('should_notSetSpeakerJwtCookie_when_navigatingSpeakerPortal', async ({ page }) => {
    const offendingCookieHeaders: string[] = [];
    const offendingRequestUrls: string[] = [];

    page.on('response', (response) => {
      const setCookie = response.headers()['set-cookie'];
      if (setCookie && /speaker_jwt\s*=/.test(setCookie)) {
        offendingCookieHeaders.push(`${response.status()} ${response.url()} -> ${setCookie}`);
      }
    });

    page.on('request', (request) => {
      const url = request.url();
      if (
        /\/api\/v1\/auth\/speaker-magic-login/.test(url) ||
        /\/api\/v1\/speaker-portal\/validate-token/.test(url)
      ) {
        offendingRequestUrls.push(`${request.method()} ${url}`);
      }
    });

    await page.goto('/speaker-portal/dashboard');
    await expect(page).toHaveURL(/\/speaker-portal\/dashboard/);
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible({ timeout: 15000 });

    expect(
      offendingCookieHeaders,
      `Found speaker_jwt cookie issuance after teardown:\n${offendingCookieHeaders.join('\n')}`
    ).toEqual([]);
    expect(
      offendingRequestUrls,
      `Found magic-link request after teardown:\n${offendingRequestUrls.join('\n')}`
    ).toEqual([]);
  });
});
