/**
 * Event fixture helper (plan §A5 / §C slice 7)
 * docs/plans/playwright-staging-hardening.md
 *
 * Slice 7 registers against a REAL event through the public UI. A public registration
 * creates a row in `registrations` that has NO prefix-sweep cleanup path — the EMS
 * `TestFixtureCleanupService` enum is `EVENTS|SESSIONS|TOPICS` only (see
 * test-fixtures-cleanup.ts). The only teardown for a registration is the FK cascade when
 * its parent event is DELETEd. We therefore must NOT register against the live "current"
 * event (un-deletable); instead each run creates a dedicated throwaway event via the API,
 * registers against IT through the UI, then explicit-DELETEs the event in afterAll
 * (`cleanupByCode`) — the cascade removes the pending registration. This mirrors Bruno's
 * `00b-fixture-event.bru` → `13-create-registration.bru` → `98-delete-fixture-event.bru`.
 *
 * Findings that make this safe & deterministic:
 * • Public registration succeeds in ANY workflow state (no backend state gate) — so the
 *   fixture event stays in CREATED; no workflow walk / cron dependency (vs. C-bis).
 * • A CREATED, unpublished, future-dated event is NOT visible in any public listing
 *   (archive shows ARCHIVED; current-event shows the published upcoming event), so the
 *   throwaway event has no public blast radius.
 * • Turnstile is disabled on staging (`turnstile.enabled` defaults false; the gateway
 *   fail-opens on a null token), so the UI submit needs no Cloudflare interaction.
 *
 * Event CODE is server-generated (`BATbern{eventNumber}`); we pass a deliberately
 * high, out-of-range eventNumber so the throwaway never collides with or consumes a real
 * sequence number (real events are 1..~80). The title carries the canonical
 * `BATPW-E2E` token (factory `eventTitle()`) so a human / future title-backstop can spot
 * an orphan even though the code can't be prefix-swept.
 */

import fs from 'fs';
import os from 'os';
import path from 'path';
import { eventTitle, EVENT_TITLE_TOKEN, topicCode as factoryTopicCode } from './test-data-factory';

const API_BASE_URL = process.env.E2E_API_URL || 'http://localhost:8000';

/** A handle the spec captures and feeds to `cleanupByCode` in afterAll. */
export interface RegistrationEvent {
  eventCode: string;
  eventNumber: number;
  title: string;
}

/**
 * Resolve the ORGANIZER idToken the same way `global-teardown.ts` does:
 * `~/.batbern/{TEST_ENV}-organizer.json` → legacy `{TEST_ENV}.json`. The fixture
 * create/delete calls require ROLE_ORGANIZER, so this token is mandatory for slice 7 —
 * callers throw (loudly, not skip) when it is absent, per the plan's "no empty tests" bar.
 */
export function readOrganizerToken(): string {
  const testEnv = process.env.TEST_ENV || 'development';
  // Beta is a frontend canary on the PRODUCTION backend → its tokens live in the staging files.
  const tokenEnv = testEnv === 'beta' ? 'staging' : testEnv;
  const batbernDir = path.join(os.homedir(), '.batbern');
  const candidates = [
    path.join(batbernDir, `${tokenEnv}-organizer.json`),
    path.join(batbernDir, `${tokenEnv}.json`),
  ];
  const tokenFile = candidates.find((f) => fs.existsSync(f));
  if (!tokenFile) {
    throw new Error(
      `[event-fixture] No organizer token file (${candidates.join(' | ')}). ` +
        `Slice 7 needs an organizer token to create+delete its throwaway event. ` +
        `Run scripts/auth/get-token.sh (or sync-users-from-cognito.sh locally).`
    );
  }
  const idToken = (JSON.parse(fs.readFileSync(tokenFile, 'utf8')).idToken as string) || '';
  if (!idToken) {
    throw new Error(`[event-fixture] ${tokenFile} has no idToken`);
  }
  return idToken;
}

/**
 * Decode the organizer username from the idToken's claims. The Event entity enforces a
 * non-blank `organizer_username` at persist time (stricter than CreateEventRequest's DTO
 * validation), so the fixture must supply it. Prefers the app's `custom:username` claim,
 * falling back to `cognito:username` / `email`. Pure JWT base64url decode — no signature
 * check (the server already validated the token; we only read it).
 */
export function organizerUsername(token: string): string {
  try {
    const payload = JSON.parse(Buffer.from(token.split('.')[1], 'base64').toString('utf8'));
    return (
      payload['custom:username'] || payload['cognito:username'] || payload.email || 'playwright-e2e'
    );
  } catch {
    return 'playwright-e2e';
  }
}

/**
 * Create a throwaway, registration-open event via the API and return its handle. The
 * event is left in CREATED state (registration works in any state) with a future date
 * and a finite capacity. Tear it down with `cleanupByCode(token, handle.eventCode)`.
 */
export async function createRegistrationEvent(token: string): Promise<RegistrationEvent> {
  const title = eventTitle(); // "BATPW-E2E <ts>"
  // Reserved TEST event-number range [10000, 99999] (event-fixture leak fix, 2026-06-01).
  // Real events are 1..~80 and the conference will never reach 10 000 editions, so
  // event_number >= 10000 is an unambiguous test marker that the server-side
  // `ems/events_by_number` sweep force-deletes (bypassing the real-attendee 409 guard that
  // makes a registered fixture event undeletable via DELETE /events/{code}). RANDOM, not
  // time-derived: the per-deploy @smoke and the nightly @gate can fire concurrently against
  // the same staging, and a time-based number would collide on the unique event_number
  // column for same-millisecond creates. 90k-wide range → negligible birthday collision.
  const eventNumber = 10000 + Math.floor(Math.random() * 90000);
  // 60 days out — comfortably future for any date validation; never auto-transitions.
  const date = new Date(Date.now() + 60 * 24 * 60 * 60 * 1000).toISOString();
  const registrationDeadline = new Date(Date.now() + 53 * 24 * 60 * 60 * 1000).toISOString();

  // The Event entity enforces (NotNull/NotBlank) more than CreateEventRequest's DTO does:
  // registrationDeadline, venueName/Address, venueCapacity, and organizerUsername are all
  // required at persist time — supply them all or the save throws ConstraintViolation (500).
  const res = await fetch(`${API_BASE_URL}/api/v1/events`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify({
      title,
      eventNumber,
      date,
      registrationDeadline,
      eventType: 'EVENING',
      workflowState: 'CREATED',
      registrationCapacity: 50,
      venueName: 'Playwright E2E Venue',
      venueAddress: 'Bern, Switzerland',
      venueCapacity: 100,
      organizerUsername: organizerUsername(token),
      description: 'Playwright slice-7 registration fixture — auto-deleted in afterAll.',
    }),
  });

  if (!res.ok) {
    const body = await res.text().catch(() => '');
    throw new Error(`[event-fixture] create event failed: ${res.status} ${res.statusText} ${body}`);
  }

  const data = (await res.json()) as { eventCode?: string; eventNumber?: number };
  if (!data.eventCode) {
    throw new Error(
      `[event-fixture] create event response had no eventCode: ${JSON.stringify(data)}`
    );
  }
  console.log(`[event-fixture] ✓ created ${data.eventCode} ("${title}")`);
  return { eventCode: data.eventCode, eventNumber: data.eventNumber ?? eventNumber, title };
}

/**
 * Replicate CUMS `SlugGenerationService.generateCompanyName`: lowercase, strip to
 * alphanumeric, truncate to 12 chars. A public registration creates a CUMS `companies`
 * row out-of-band via `getOrCreateCompany`, which is CROSS-SERVICE (the event-delete
 * cascade does NOT remove it) and stored as a LOWERCASED slug — so the global uppercase
 * `BRUNOTESTCO%` sweep misses it (the same gap that leaks Bruno's `testco`). The spec
 * therefore explicit-deletes it by slug. Because `getOrCreateCompany` REUSES a company by
 * this exact slug (findByName hit → no collision suffix), computing it client-side from the
 * known display name yields the actually-stored slug, which `DELETE /api/v1/companies/{slug}`
 * accepts. (404-tolerant if a future suffix ever diverges.)
 */
export function companySlug(displayName: string): string {
  return displayName
    .toLowerCase()
    .replace(/[^a-z0-9]/g, '')
    .slice(0, 12);
}

/**
 * Create a topic (title = a sweepable `bruno-test-topic-<ts>` slug, so `topic_code` lands in
 * the `ems/topics` prefix sweep) and select it for `eventCode` — the realistic setup step the
 * speaker-pool golden path performs after creating the event. Selecting a topic also advances
 * the event CREATED → (TOPIC_SELECTION) workflow. Returns the server `topicCode`. Requires an
 * organizer token. Throws loudly on any non-2xx.
 */
export async function createAndSelectTopic(token: string, eventCode: string): Promise<string> {
  const title = factoryTopicCode(); // already a valid slug → topic_code === title → swept
  const createRes = await fetch(`${API_BASE_URL}/api/v1/topics`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({
      title,
      description: 'Playwright golden-path topic fixture — deleted via topic prefix sweep.',
      category: 'technical',
    }),
  });
  if (createRes.status !== 201) {
    const body = await createRes.text().catch(() => '');
    throw new Error(`[event-fixture] create topic failed: ${createRes.status} ${body}`);
  }
  const { topicCode } = (await createRes.json()) as { topicCode?: string };
  if (!topicCode) throw new Error('[event-fixture] create topic response had no topicCode');

  const selectRes = await fetch(`${API_BASE_URL}/api/v1/events/${eventCode}/topics`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ topicCode, justification: null }),
  });
  if (selectRes.status !== 200) {
    const body = await selectRes.text().catch(() => '');
    throw new Error(
      `[event-fixture] select topic ${topicCode} failed: ${selectRes.status} ${body}`
    );
  }
  console.log(`[event-fixture] ✓ topic ${topicCode} created + selected for ${eventCode}`);
  return topicCode;
}

/* ────────────────────────────────────────────────────────────────────────────────────
 * Workflow-walk fixtures (plan §C slice 10 — event lifecycle / §C-bis OQ-3)
 *
 * The 8-state lifecycle (CREATED → TOPIC_SELECTION → SPEAKER_IDENTIFICATION → SLOT_ASSIGNMENT
 * → AGENDA_PUBLISHED → EVENT_LIVE → EVENT_COMPLETED → ARCHIVED, see EventWorkflowState.java)
 * is mostly driven by AUTOMATIC transitions in production — event listeners (topic selected,
 * speaker accepted, sessions timed) and cron jobs (EVENT_LIVE at 00:01, EVENT_COMPLETED at
 * 23:59 Bern time). A pure-UI walk would stall on cron and depend on flaky DnD/content flows.
 *
 * Per OQ-3 (resolved: hybrid), the gate force-advances state via the same test-override the
 * organizer UI exposes — `PUT /events/{code}/workflow/transition` with
 * `overrideValidation:true` — which `EventWorkflowStateMachine.transitionToState` honours by
 * SKIPPING ALL validation (so any target state is reachable, incl. non-adjacent jumps and the
 * cron-only EVENT_LIVE/EVENT_COMPLETED). The walk is API-driven, UI-asserted: after each
 * transition the spec reloads the event overview and asserts the `workflow-status-badge`'s
 * `data-workflow-state` attribute reflects the new state. No cron, no DnD, fully deterministic.
 * ──────────────────────────────────────────────────────────────────────────────────── */

/**
 * The forward workflow states in linear order, excluding the initial CREATED (which a freshly
 * created fixture event already occupies — it's a starting state, never a transition target).
 * The spec walks these in order, force-advancing + UI-asserting each.
 */
export const WORKFLOW_FORWARD_STATES = [
  'TOPIC_SELECTION',
  'SPEAKER_IDENTIFICATION',
  'SLOT_ASSIGNMENT',
  'AGENDA_PUBLISHED',
  'EVENT_LIVE',
  'EVENT_COMPLETED',
  'ARCHIVED',
] as const;

/** Every workflow state including the initial CREATED. */
export type WorkflowState = 'CREATED' | (typeof WORKFLOW_FORWARD_STATES)[number];

/**
 * Force-advance `eventCode` to `targetState`, bypassing all business-rule validation
 * (`overrideValidation:true`). Returns the server-confirmed new state. Throws loudly on any
 * non-2xx (per the plan's fail-loud / no-empty-tests bar). Requires an organizer token.
 */
export async function transitionWorkflow(
  token: string,
  eventCode: string,
  targetState: WorkflowState,
  overrideReason = 'Playwright slice-10 lifecycle walk — force-advance (test override)'
): Promise<string> {
  const res = await fetch(`${API_BASE_URL}/api/v1/events/${eventCode}/workflow/transition`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ targetState, overrideValidation: true, overrideReason }),
  });
  if (!res.ok) {
    const body = await res.text().catch(() => '');
    throw new Error(
      `[event-fixture] transition ${eventCode} → ${targetState} failed: ${res.status} ${res.statusText} ${body}`
    );
  }
  const data = (await res.json()) as { workflowState?: string };
  if (data.workflowState !== targetState) {
    throw new Error(
      `[event-fixture] transition ${eventCode} → ${targetState} returned ${data.workflowState}`
    );
  }
  return data.workflowState;
}

/**
 * Read the authoritative current workflow state via `GET /events/{code}/workflow/status` — the
 * server-side signal the @smoke verifies against (stronger than the UI badge alone). Requires an
 * organizer token (the status endpoint requires authentication).
 */
export async function getWorkflowState(token: string, eventCode: string): Promise<string> {
  const res = await fetch(`${API_BASE_URL}/api/v1/events/${eventCode}/workflow/status`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) {
    const body = await res.text().catch(() => '');
    throw new Error(
      `[event-fixture] workflow status ${eventCode} failed: ${res.status} ${res.statusText} ${body}`
    );
  }
  const data = (await res.json()) as { currentState?: string };
  if (!data.currentState) {
    throw new Error(
      `[event-fixture] workflow status ${eventCode} had no currentState: ${JSON.stringify(data)}`
    );
  }
  return data.currentState;
}

/* ────────────────────────────────────────────────────────────────────────────────────
 * Slot-assignment fixtures (plan §C slice 6 — sessions/slot-assignment)
 *
 * The slot-assignment page consumes "unassigned" (placeholder) sessions: non-structural
 * sessions with `startTime IS NULL`. In the real product these are born timing-less via the
 * speaker workflow. But the REST `POST /events/{code}/sessions` endpoint REQUIRES startTime/
 * endTime (`CreateSessionRequest` `@NotNull`), so a session cannot be created timing-less
 * directly. We reproduce the unassigned state deterministically WITHOUT a cron-driven workflow
 * walk: create each session WITH throwaway timing, then `DELETE /sessions/timing` to clear ALL
 * timings — leaving them unassigned (they then appear in `GET /sessions/unassigned` and the
 * slot-assignment speaker-pool sidebar). `sessionType` is a non-structural value so the
 * unassigned filter (which excludes moderation/break/lunch) keeps them.
 *
 * Cleanup rides the event-delete cascade (`cleanupByCode`): `sessions.event_id` is
 * `ON DELETE CASCADE` (V2), and `session_timing_history.session_id` cascades too (V28). The
 * server-generated `sessionSlug` (slugified from title) is NOT reachable by the
 * `bruno-test-session-` prefix sweep, so the parent-event delete is the only teardown — same
 * server-generated-code caveat the plan §A5 calls out.
 * ──────────────────────────────────────────────────────────────────────────────────── */

/** A speaker session created on the fixture event (its parent event's delete cascades it away). */
export interface SlotSession {
  sessionSlug: string;
  title: string;
}

/** Shape of the session-creation response we read the server-generated slug from. */
interface SessionCreateResponse {
  sessionSlug?: string;
  title?: string;
}

/**
 * Create `count` placeholder (unassigned) speaker sessions on `eventCode` and return their
 * handles. Each session is created with throwaway timing (required by the API) and then ALL
 * timings are cleared in one call, so every returned session is unassigned. Throws loudly on
 * any failure (per the plan's "no empty tests" / fail-loud bar). Requires an organizer token.
 */
export async function addUnassignedSessions(
  token: string,
  eventCode: string,
  count: number
): Promise<SlotSession[]> {
  // Throwaway timing — valid ISO instants; cleared immediately below, never asserted on.
  const start = new Date(Date.now() + 60 * 24 * 60 * 60 * 1000);
  const end = new Date(start.getTime() + 45 * 60 * 1000);

  const sessions: SlotSession[] = [];
  for (let i = 0; i < count; i++) {
    // Unique title → unique server-derived slug (the backend also de-dupes with a -N suffix).
    const title = `${EVENT_TITLE_TOKEN} Slot Session ${i + 1} ${Date.now()}-${i}`;
    const res = await fetch(`${API_BASE_URL}/api/v1/events/${eventCode}/sessions`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify({
        title,
        description: 'Playwright slice-6 slot-assignment fixture session — cascade-deleted.',
        sessionType: 'presentation', // non-structural → stays in the unassigned list
        startTime: start.toISOString(),
        endTime: end.toISOString(),
      }),
    });
    if (!res.ok) {
      const body = await res.text().catch(() => '');
      throw new Error(
        `[event-fixture] create session failed: ${res.status} ${res.statusText} ${body}`
      );
    }
    const data = (await res.json()) as SessionCreateResponse;
    if (!data.sessionSlug) {
      throw new Error(
        `[event-fixture] create session response had no sessionSlug: ${JSON.stringify(data)}`
      );
    }
    sessions.push({ sessionSlug: data.sessionSlug, title });
  }

  // Clear ALL timings → every session above becomes unassigned (startTime IS NULL).
  const clearRes = await fetch(`${API_BASE_URL}/api/v1/events/${eventCode}/sessions/timing`, {
    method: 'DELETE',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
  });
  if (!clearRes.ok) {
    const body = await clearRes.text().catch(() => '');
    throw new Error(
      `[event-fixture] clear timings failed: ${clearRes.status} ${clearRes.statusText} ${body}`
    );
  }
  console.log(`[event-fixture] ✓ ${count} unassigned session(s) on ${eventCode}`);
  return sessions;
}

/**
 * Count the event's unassigned (placeholder) sessions via the same endpoint the page reads —
 * the authoritative signal the slot-assignment `@smoke` asserts on (0 after a successful
 * auto-assign). Requires an organizer token.
 */
export async function getUnassignedSessionCount(token: string, eventCode: string): Promise<number> {
  const res = await fetch(`${API_BASE_URL}/api/v1/events/${eventCode}/sessions/unassigned`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) {
    const body = await res.text().catch(() => '');
    throw new Error(
      `[event-fixture] get unassigned failed: ${res.status} ${res.statusText} ${body}`
    );
  }
  const data = (await res.json()) as unknown[];
  return Array.isArray(data) ? data.length : 0;
}

/**
 * Fetch the configured max speaker slots for an event type (EVENING/AFTERNOON/FULL_DAY) — the
 * same `event_types.max_slots` the backend exposes as `speakerStatusSummary.maxSlotsAllowed` and
 * the kanban uses for the slot-capacity gate (the drawer "accept on behalf" action hides once
 * accepted+invited >= maxSlots). Read it from the LIVE config so scenarios adapt to organizer-
 * tuned caps instead of hard-coding a number that silently drifts (e.g. EVENING was 4 at seed,
 * tightened to 3 in staging — which deterministically broke the golden path's phase 6).
 */
export async function getEventTypeMaxSlots(token: string, type: string): Promise<number> {
  const res = await fetch(`${API_BASE_URL}/api/v1/events/types/${type}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) {
    const body = await res.text().catch(() => '');
    throw new Error(
      `[event-fixture] get event type ${type} failed: ${res.status} ${res.statusText} ${body}`
    );
  }
  const data = (await res.json()) as { maxSlots?: number };
  if (typeof data.maxSlots !== 'number' || data.maxSlots < 1) {
    throw new Error(
      `[event-fixture] event type ${type} returned invalid maxSlots: ${JSON.stringify(data)}`
    );
  }
  return data.maxSlots;
}
