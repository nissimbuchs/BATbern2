/**
 * E2E: User Reconciliation & Sync Status — slice 3 / users (plan §C)
 * docs/plans/playwright-staging-hardening.md
 *
 * Rewritten 2026-05-30 to the quality bar. Story 1.2.5 (BAT-93/107).
 *
 * Why this API spec SURVIVES the slice (its two user-sync siblings were deleted): the admin
 * reconcile + sync-status endpoints are NOT covered by Bruno's users-api collection — they are
 * unique coverage. (The deleted `role-change-sync` duplicated Bruno's `06-update-user-roles`
 * and `user-registration-sync` duplicated the disabled `09-get-or-create`; both also mutated
 * a RANDOM real user, so they were removed.)
 *
 * Tagging: `@gate` (nightly) only — NOT `@smoke`. All three tests are non-mutating.
 *
 * What is gated (and why NOT the authenticated reconcile trigger):
 *   • `GET /admin/sync-status` (read-only) — full contract assertion. ✅ gated.
 *   • Auth-protection negatives on BOTH admin endpoints (401/403 without a token) — these are
 *     rejected before any work runs, so they neither mutate state nor depend on environment. ✅.
 *   • An AUTHENTICATED `POST /admin/reconcile` is deliberately NOT gated: (a) it MUTATES prod
 *     (deactivates orphaned / creates missing users) and the system already runs it on a
 *     schedule — we don't want the nightly gate re-triggering reconciliation against prod; and
 *     (b) it is not dev-greenable — against local dev the CUMS DB is a partial mirror of staging
 *     Cognito, so reconcile 500s on a Cognito-only user (`User with ID 'bruno.test.N' not
 *     found`). The scheduled job exercises the happy path in production; the auth-negative below
 *     still proves the endpoint exists and is protected.
 *
 * Hardening: resolve the organizer token from the canonical token file (`readOrganizerToken`)
 * instead of `process.env.AUTH_TOKEN`; assert the response CONTRACT (shape + types) rather than
 * wall-clock timing (the old perf/latency assertions were non-E2E and flaky).
 */

import { test, expect } from '@playwright/test';
import { API_URL } from '../../../playwright.config';
import { readOrganizerToken } from '../../helpers/user-fixture';

const jsonHeaders = (token: string) => ({
  Authorization: `Bearer ${token}`,
  'Content-Type': 'application/json',
});

test.describe('User Sync — Reconciliation & Status', { tag: '@gate' }, () => {
  let token: string;

  test.beforeAll(() => {
    token = readOrganizerToken();
  });

  test('should_returnSyncStatus_when_organizerChecksStatus', async () => {
    const res = await fetch(`${API_URL}/api/v1/users/admin/sync-status`, {
      method: 'GET',
      headers: jsonHeaders(token),
    });
    expect(res.status).toBe(200);

    const data = await res.json();
    expect(typeof data.cognitoUserCount).toBe('number');
    expect(typeof data.databaseUserCount).toBe('number');
    expect(typeof data.missingInDatabase).toBe('number');
    expect(typeof data.orphanedInDatabase).toBe('number');
    expect(typeof data.inSync).toBe('boolean');
    expect(typeof data.message).toBe('string');
  });

  test('should_rejectSyncStatus_when_unauthenticated', async () => {
    const res = await fetch(`${API_URL}/api/v1/users/admin/sync-status`, {
      method: 'GET',
      headers: { 'Content-Type': 'application/json' },
    });
    expect([401, 403]).toContain(res.status);
  });

  test('should_rejectReconciliation_when_unauthenticated', async () => {
    const res = await fetch(`${API_URL}/api/v1/users/admin/reconcile`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
    });
    expect([401, 403]).toContain(res.status);
  });
});
