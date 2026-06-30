/**
 * User fixture helper (plan §A5 / §C slice 3)
 * docs/plans/playwright-staging-hardening.md
 *
 * Slice 3 (users) exercises the user-management UI against deployed staging (= prod). The
 * specs that need a user to operate on (delete it, edit its roles, search for it) create that
 * user via the API here — NOT through the create-user form — because:
 *   • API create is fast and deterministic (no form-fill flake), and
 *   • it returns the server-derived `username` (= the row's id), which the spec captures and
 *     feeds to `cleanupById(token, 'users', username)` for a RACE-FREE targeted teardown.
 *     A broad `bruno.test%` users sweep in an afterAll would delete a sibling spec's
 *     just-created user mid-run when the chromium project runs with workers > 1 (local
 *     `fullyParallel`), so we always delete by the exact captured username instead. The
 *     `global-teardown` `bruno.test%` sweep remains the belt-and-suspenders backstop.
 *
 * The ONE flow that genuinely tests the create FORM (`user-creation.spec.ts` @smoke) creates
 * through the UI and then resolves the username via `findUsernameByEmail` (search-by-email)
 * for the same targeted cleanup.
 *
 * Naming: firstName/lastName come from the factory (`USER_FIRST_NAME`/`USER_LAST_NAME` =
 * `Bruno`/`Test`) so the server derives a `bruno.test`(.N) username the sweep reaches; the
 * email is unique per run (`factory.email()`). Bruno's `04-create-user.bru` proves this exact
 * pattern + that the `cums/users` cleanup endpoint removes both the DB row AND the Cognito
 * user, so no Cognito residue leaks on staging.
 *
 * Auth: create + search require ROLE_ORGANIZER — pass the organizer idToken
 * (`readOrganizerToken()`, re-exported from event-fixture).
 */

import * as factory from './test-data-factory';
import { readOrganizerToken } from './event-fixture';

export { readOrganizerToken };

const API_BASE_URL = process.env.E2E_API_URL || 'http://localhost:8000';

/** A handle the spec captures and feeds to `cleanupById(token, 'users', username)`. */
export interface TestUser {
  username: string;
  email: string;
}

async function authedJson(path: string, token: string, init: RequestInit = {}): Promise<Response> {
  return fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
      Authorization: `Bearer ${token}`,
      ...init.headers,
    },
  });
}

/**
 * Create a throwaway CUMS user via the API and return its handle. The username is
 * server-derived (`bruno.test`(.N)); the email is unique (`factory.email()`). Throws loudly
 * on a non-201 (per the plan's "no empty tests / fail loud, don't skip" bar). Tear down with
 * `cleanupById(token, 'users', handle.username)`.
 *
 * Concurrency note: every user-management spec seeds a `Bruno Test` user in its `beforeAll`,
 * and the suite runs `fullyParallel` with multiple local workers. CUMS derives the username
 * with a check-then-insert (`ensureUniqueUsername` + save), which is not atomic — two workers
 * that both observe `bruno.test` as free race to INSERT it and the loser gets a 500
 * ("A data integrity error occurred", unique constraint `user_profiles_username_key"). We
 * absorb that here with a bounded retry: on the next attempt the winner's row is committed and
 * visible, so the server derives `bruno.test.N` and succeeds. (A fresh email per attempt keeps
 * the email column unique too.) See docs/plans/playwright-staging-hardening.md.
 */
export async function createTestUser(
  token: string,
  initialRoles: string[] = ['ATTENDEE']
): Promise<TestUser> {
  const MAX_ATTEMPTS = 5;
  let lastBody = '';
  let lastStatus = 0;
  for (let attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
    const email = factory.email();
    const res = await authedJson('/api/v1/users', token, {
      method: 'POST',
      body: JSON.stringify({
        email,
        firstName: factory.USER_FIRST_NAME,
        lastName: factory.USER_LAST_NAME,
        initialRoles,
        bio: 'Playwright slice-3 users fixture — auto-deleted by cleanupById/afterAll.',
      }),
    });

    if (res.status === 201) {
      const data = (await res.json()) as { id?: string; email?: string };
      if (!data.id) {
        throw new Error(`[user-fixture] create user response had no id: ${JSON.stringify(data)}`);
      }
      console.log(`[user-fixture] ✓ created user ${data.id} (${email})`);
      return { username: data.id, email: data.email ?? email };
    }

    lastStatus = res.status;
    lastBody = await res.text().catch(() => '');

    // Retry only the username-derivation race (500 data-integrity / 409 conflict); fail fast on
    // anything else (auth, validation) so genuine errors still surface loudly.
    const isUsernameRace =
      (res.status === 500 && lastBody.includes('data integrity')) || res.status === 409;
    if (!isUsernameRace || attempt === MAX_ATTEMPTS) {
      break;
    }
    // Small jittered backoff so the racing winner commits before we re-derive.
    await new Promise((r) => setTimeout(r, 100 * attempt + Math.floor(Math.random() * 100)));
    console.log(
      `[user-fixture] username-derivation race (HTTP ${res.status}); retry ${attempt}/${MAX_ATTEMPTS - 1}`
    );
  }

  throw new Error(`[user-fixture] create user failed: ${lastStatus} ${lastBody}`);
}

/**
 * Resolve the server-derived username for a given email via the search endpoint
 * (`GET /users/search?query=`). Used by the create-FORM @smoke to capture the username it
 * could not know up-front (the form has no username field) so teardown can delete by exact id.
 * Returns null if not found (the caller falls back to the global `bruno.test%` sweep).
 */
export async function findUsernameByEmail(token: string, email: string): Promise<string | null> {
  try {
    const res = await authedJson(`/api/v1/users/search?query=${encodeURIComponent(email)}`, token, {
      method: 'GET',
    });
    if (res.status !== 200) return null;
    const body = (await res.json()) as
      | { data?: Array<{ id?: string; email?: string }> }
      | Array<{
          id?: string;
          email?: string;
        }>;
    const list = Array.isArray(body) ? body : (body.data ?? []);
    const match = list.find((u) => u.email?.toLowerCase() === email.toLowerCase());
    return match?.id ?? null;
  } catch {
    return null;
  }
}
