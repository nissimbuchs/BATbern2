// #958: tagged @quarantine, not @gate. This file carried NO tag at all, so it matched none of
// the CI scopes (`--grep @smoke` per-deploy, `--grep @gate` nightly, `--grep @quarantine` nightly
// re-test) and therefore never executed anywhere — it looked like coverage and was not.
//
// @quarantine rather than @gate deliberately: these specs assert heavily on translated UI copy,
// so promoting them straight into the gate would very likely turn the nightly red on day one,
// which is the mistake #1008 called out. @quarantine runs them nightly in the promotion re-test
// where they cannot break the deploy gate, and the existing machinery promotes them once they
// settle green. Retag to @gate at that point.

/**
 * E2E smoke — "Continue with Google" SSO button (Story 12.9, SSO Phase 5)
 *
 * SCOPE & CAVEAT (per docs/plans/sso-oidc-federation.md §6):
 * A full Google OAuth round-trip (consent → idpresponse → Cognito → /auth/callback) CANNOT be
 * automated in CI without a managed test Google identity. So this spec covers only what is
 * automatable: with `features.sso` ON, the login page renders the button and clicking it
 * INITIATES the Cognito hosted-UI redirect (navigation toward the `/oauth2/authorize` host).
 * It asserts NO backend mutation and completes NO sign-in.
 *
 * The link-existing-user and brand-new-user (ATTENDEE) paths are the MANUAL prod smoke
 * (Story 12.9 AC8), gated before flipping the flag on. MEMORY rule: staging IS production —
 * this spec must never trigger real outbound comms or complete a real federation.
 *
 * The button only renders when the runtime config (GET /api/v1/config) reports
 * `features.sso === true`. In an environment where the flag is OFF, the button is absent and
 * the redirect assertions are skipped (mirrors the env-guard in forgot-password.spec.ts).
 */

import { test, expect } from '@playwright/test';
import { BASE_URL } from '../../playwright.config';

const GOOGLE_BUTTON = /continue with google|mit google fortfahren/i;

test.describe('SSO — Continue with Google button', { tag: '@quarantine' }, () => {
  test('renders the button and initiates the Cognito hosted-UI redirect when features.sso is on', async ({
    page,
  }) => {
    await page.goto(`${BASE_URL}/login`);

    // The button renders only when features.sso is enabled in the target env. If it's off
    // (e.g. the flag has not been flipped in this environment), skip — there is nothing to drive.
    const googleButton = page.getByRole('button', { name: GOOGLE_BUTTON });
    const visible = await googleButton.isVisible().catch(() => false);
    test.skip(!visible, 'features.sso is OFF in this environment — no Google button to test');

    // Clicking initiates Amplify signInWithRedirect → the browser navigates to the Cognito
    // hosted UI's /oauth2/authorize endpoint. We assert the redirect STARTS (no Google creds
    // entered, no /auth/callback completion, no backend mutation).
    await googleButton.click();

    await page.waitForURL(/\/oauth2\/authorize/, { timeout: 15000 });
    expect(page.url()).toMatch(/amazoncognito\.com\/oauth2\/authorize/);
    expect(page.url()).toContain('identity_provider=Google');
  });
});
