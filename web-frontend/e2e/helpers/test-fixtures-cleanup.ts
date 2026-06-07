/**
 * Shared test-fixtures cleanup client (plan §A4)
 * docs/plans/playwright-staging-hardening.md
 *
 * Typed client over the server-side `/api/v1/admin/test-fixtures/{cums,ems,pcs}/cleanup`
 * endpoints — the SAME endpoints + canonical prefixes Bruno's `99-posttest-cleanup.bru`
 * hooks use, so the Playwright and Bruno suites share one cleanup contract. This file
 * supersedes the ad-hoc `"E2E"`-title-match deletes in
 * `e2e/workflows/documentation/helpers/api-helpers.ts` (the source of the
 * `"E2E Test Company"` prod residue the Bruno audit found, 2026-05-24).
 *
 * ── IMPORTANT: this mirrors the REAL backend allowlist, not the plan's A4 prose ──────
 * The plan's §A4 narrative says EMS handles "events/sessions/topics/tasks/registrations/
 * uploads", but the deployed `TestFixtureCleanupService` enums only accept:
 *   CUMS: companies, users, additional_emails, users_by_email
 *   EMS : events, sessions, topics, events_by_number
 *   PCS : partners (prefix), meetings (id-allowlist)
 * Sweeping an unsupported entityType returns 400, so SWEEP_TARGETS lists ONLY the real
 * ones. tasks/registrations/uploads have no prefix-sweep path — their slices must clean
 * up by explicit delete (or via the cascade when the parent event is deleted).
 *
 * Auth: every cleanup endpoint requires the caller's JWT to carry ROLE_ORGANIZER
 * (`@PreAuthorize("hasRole('ORGANIZER')")`). Sweeping with a speaker/partner token 403s,
 * so callers must pass an organizer idToken.
 *
 * Tolerance: the server returns 200 (deleted ≥0) or, for the explicit-delete paths,
 * 404 (already gone) / 409 (event in a non-deletable workflow state). All are accepted —
 * cleanup must never throw and fail an otherwise-green test.
 */

const API_BASE_URL = process.env.E2E_API_URL || 'http://localhost:8000';

type CleanupService = 'cums' | 'ems' | 'pcs';

/** One prefix-sweep target: a (service, entityType, bare-prefix) triple the backend accepts. */
interface SweepTarget {
  service: CleanupService;
  entityType: string;
  prefix: string;
}

/**
 * Every bare canonical prefix the backend will sweep, in dependency-safe order
 * (children before parents is irrelevant here — each DELETE cascades its own FKs).
 * Kept in lock-step with the `CleanupEntityType` enums + `CANONICAL_PREFIXES`.
 */
const SWEEP_TARGETS: SweepTarget[] = [
  // CUMS
  { service: 'cums', entityType: 'companies', prefix: 'BRUNOTESTCO' },
  { service: 'cums', entityType: 'users', prefix: 'bruno.test' },
  { service: 'cums', entityType: 'additional_emails', prefix: 'bruno-test-' },
  // CUMS — JIT users from anonymous registrations (issue #725). These carry no
  // `bruno.test` username (e.g. user.brunotest, promote.ee, test.attendee), so the
  // username sweep above misses them; the synthetic EMAIL DOMAIN (suffix-matched) is
  // the only safe discriminator. `prefix` here is the allow-listed domain, NOT a prefix.
  { service: 'cums', entityType: 'users_by_email', prefix: '@e2e.batbern.invalid' },
  { service: 'cums', entityType: 'users_by_email', prefix: '@batbern-test.ch' },
  { service: 'cums', entityType: 'users_by_email', prefix: 'zaproxy@example.com' },
  // EMS — deleting events cascades sessions/registrations/speaker_pool, so events first.
  // events_by_number FIRST: force-deletes test events (event_number >= 10000) regardless of
  // their server-generated BATbern{N} code AND bypasses the real-attendee 409 guard — the only
  // teardown that reaches a registration-fixture event (event-fixture leak fix). `prefix` here
  // is the reserved threshold sentinel "10000", NOT a prefix.
  { service: 'ems', entityType: 'events_by_number', prefix: '10000' },
  { service: 'ems', entityType: 'events', prefix: 'BRUNO-TEST-' },
  { service: 'ems', entityType: 'sessions', prefix: 'bruno-test-session-' },
  { service: 'ems', entityType: 'topics', prefix: 'bruno-test-topic-' },
  // PCS
  { service: 'pcs', entityType: 'partners', prefix: 'brtest' },
];

const ACCEPTED_SWEEP_STATUSES = [200, 204, 404];
const ACCEPTED_DELETE_STATUSES = [204, 404, 409];

async function authedFetch(
  path: string,
  token: string,
  options: RequestInit = {}
): Promise<Response> {
  return fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
      ...options.headers,
    },
  });
}

/** POST one prefix-sweep request. Never throws; logs the outcome. */
async function sweepOne(token: string, target: SweepTarget): Promise<void> {
  const { service, entityType, prefix } = target;
  try {
    const res = await authedFetch(`/api/v1/admin/test-fixtures/${service}/cleanup`, token, {
      method: 'POST',
      body: JSON.stringify({ entityType, prefix }),
    });
    if (!ACCEPTED_SWEEP_STATUSES.includes(res.status)) {
      console.warn(
        `[cleanup] ⚠️  ${service}/${entityType} prefix=${prefix} → unexpected ${res.status}`
      );
      return;
    }
    if (res.status === 200) {
      const body = (await res.json().catch(() => null)) as {
        deletionCounts?: Record<string, number>;
      } | null;
      const counts = body?.deletionCounts ? JSON.stringify(body.deletionCounts) : '{}';
      console.log(`[cleanup] ✓ ${service}/${entityType} prefix=${prefix} → ${counts}`);
    } else {
      console.log(`[cleanup] ✓ ${service}/${entityType} prefix=${prefix} → ${res.status}`);
    }
  } catch (error) {
    console.warn(
      `[cleanup] ⚠️  ${service}/${entityType} prefix=${prefix} threw:`,
      error instanceof Error ? error.message : String(error)
    );
  }
}

/**
 * Final belt-and-suspenders sweep across every canonical prefix on all three services
 * (plan §A3/§A4 — the role Bruno's `99-posttest-cleanup.bru` plays). Catches residue from
 * any spec that crashed before its own teardown. Requires an ORGANIZER idToken.
 */
export async function sweepAllPrefixes(token: string): Promise<void> {
  if (!token) {
    console.warn('[cleanup] ⚠️  No organizer token — skipping prefix sweep');
    return;
  }
  console.log('[cleanup] Running final prefix sweep across cums/ems/pcs…');
  for (const target of SWEEP_TARGETS) {
    await sweepOne(token, target);
  }
  console.log('[cleanup] Prefix sweep complete');
}

/**
 * Explicit DELETE of an event by its (possibly server-generated) `BATbern{N}` code —
 * the only teardown path for UI-created events whose code can't be prefix-swept
 * (plan §A5 "Critical caveat"). Accepts 204/404/409. Mirrors Bruno's
 * `98-delete-fixture-event.bru`.
 */
export async function cleanupByCode(token: string, eventCode: string): Promise<void> {
  if (!token || !eventCode) return;
  try {
    const res = await authedFetch(`/api/v1/events/${encodeURIComponent(eventCode)}`, token, {
      method: 'DELETE',
    });
    if (ACCEPTED_DELETE_STATUSES.includes(res.status)) {
      console.log(`[cleanup] ✓ event ${eventCode} → ${res.status}`);
    } else {
      console.warn(`[cleanup] ⚠️  event ${eventCode} → unexpected ${res.status}`);
    }
  } catch (error) {
    console.warn(
      `[cleanup] ⚠️  delete event ${eventCode} threw:`,
      error instanceof Error ? error.message : String(error)
    );
  }
}

/**
 * Explicit DELETE of the CALLER's own profile picture (`DELETE /users/me/picture`) —
 * the only teardown path for the uploads slice. Profile pictures have NO prefix-sweep
 * entityType (the EMS `uploads` enum the §A4 prose imagined was never built), so the
 * photo-upload `@smoke` must restore its own account to photo-less by explicit delete in
 * afterAll. The token's own identity is the target (no id needed). Accepts 204/404 (404 =
 * the account already has no picture, the common case). Never throws — cleanup must not
 * fail an otherwise-green test.
 */
export async function cleanupOwnProfilePicture(token: string): Promise<void> {
  if (!token) return;
  try {
    const res = await authedFetch('/api/v1/users/me/picture', token, { method: 'DELETE' });
    if ([200, 204, 404].includes(res.status)) {
      console.log(`[cleanup] ✓ own profile picture → ${res.status}`);
    } else {
      console.warn(`[cleanup] ⚠️  own profile picture → unexpected ${res.status}`);
    }
  } catch (error) {
    console.warn(
      `[cleanup] ⚠️  delete own profile picture threw:`,
      error instanceof Error ? error.message : String(error)
    );
  }
}

/** REST collection paths for the explicit-delete-by-id path. */
const ID_DELETE_PATHS: Record<string, string> = {
  companies: '/api/v1/companies',
  users: '/api/v1/users',
  topics: '/api/v1/topics',
  partners: '/api/v1/partners',
};

/**
 * Explicit DELETE of a single resource by id when a prefix sweep won't reach it (e.g. a
 * row created with a non-canonical name). Accepts 204/404/409. Use sparingly — prefer
 * the canonical-prefix factory + `sweepAllPrefixes` so cleanup stays declarative.
 */
export async function cleanupById(
  token: string,
  type: keyof typeof ID_DELETE_PATHS,
  id: string
): Promise<void> {
  if (!token || !id) return;
  const base = ID_DELETE_PATHS[type];
  if (!base) {
    console.warn(`[cleanup] ⚠️  no delete path for type=${type}`);
    return;
  }
  try {
    const res = await authedFetch(`${base}/${encodeURIComponent(id)}`, token, { method: 'DELETE' });
    if (ACCEPTED_DELETE_STATUSES.includes(res.status)) {
      console.log(`[cleanup] ✓ ${type} ${id} → ${res.status}`);
    } else {
      console.warn(`[cleanup] ⚠️  ${type} ${id} → unexpected ${res.status}`);
    }
  } catch (error) {
    console.warn(
      `[cleanup] ⚠️  delete ${type} ${id} threw:`,
      error instanceof Error ? error.message : String(error)
    );
  }
}

/**
 * Delete partner meetings by explicit id allowlist (PCS `entityType=meetings` —
 * `partner_meetings` rows carry no Bruno prefix). Capped server-side at 200 ids.
 */
export async function cleanupMeetings(token: string, meetingIds: string[]): Promise<void> {
  if (!token || meetingIds.length === 0) return;
  try {
    const res = await authedFetch('/api/v1/admin/test-fixtures/pcs/cleanup', token, {
      method: 'POST',
      body: JSON.stringify({ entityType: 'meetings', meetingIds }),
    });
    if (ACCEPTED_SWEEP_STATUSES.includes(res.status)) {
      console.log(`[cleanup] ✓ pcs/meetings ids=${meetingIds.length} → ${res.status}`);
    } else {
      console.warn(
        `[cleanup] ⚠️  pcs/meetings ids=${meetingIds.length} → unexpected ${res.status}`
      );
    }
  } catch (error) {
    console.warn(
      `[cleanup] ⚠️  delete meetings threw:`,
      error instanceof Error ? error.message : String(error)
    );
  }
}
