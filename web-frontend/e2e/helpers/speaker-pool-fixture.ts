/**
 * Speaker-pool fixture helper (plan §C slice 8 — speaker pool / organizer)
 * docs/plans/playwright-staging-hardening.md
 *
 * API-driven seeding of speaker-pool rows + status transitions, the deterministic setup the
 * organizer kanban specs consume. Replaces the inline per-spec helpers that authed with
 * `process.env.E2E_TEST_TOKEN` (which the runner never sets → `Bearer undefined` → 401) and
 * created events through the UI with `Date.now()` titles + no cleanup. These use an explicit
 * organizer token (`readOrganizerToken()` from event-fixture) + `fetch`, matching event-fixture.
 *
 * Cleanup contract: speaker_pool rows are children of the event (`speaker_pool.event_id` FK,
 * ON DELETE CASCADE), so deleting the throwaway fixture event (`cleanupByCode`) removes them —
 * there is no speaker_pool prefix sweep. Promoting a speaker to READY provisions a CUMS user
 * out-of-band (cross-service, NOT cascaded by the event delete); callers therefore promote with
 * the canonical `Bruno`/`Test` names so the resulting `bruno.test(.N)` username is reached by
 * the global-teardown `cums/users` sweep (`LIKE bruno.test%`).
 */

const API_BASE_URL = process.env.E2E_API_URL || 'http://localhost:8000';

function authHeaders(token: string): Record<string, string> {
  return { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` };
}

/** Add a placeholder (IDENTIFIED) speaker to the event's pool. Returns the server speaker id. */
export async function seedSpeaker(token: string, eventCode: string, name: string): Promise<string> {
  const res = await fetch(`${API_BASE_URL}/api/v1/events/${eventCode}/speakers/pool`, {
    method: 'POST',
    headers: authHeaders(token),
    body: JSON.stringify({ speakerName: name }),
  });
  if (res.status !== 201) {
    const body = await res.text().catch(() => '');
    throw new Error(
      `[speaker-fixture] seed speaker failed: ${res.status} ${res.statusText} ${body}`
    );
  }
  const data = (await res.json()) as { id?: string };
  if (!data.id) {
    throw new Error(`[speaker-fixture] seed speaker response had no id: ${JSON.stringify(data)}`);
  }
  return data.id;
}

/** Transition a pool speaker to `newStatus` (UPPER_CASE, e.g. CONTACTED). Expects 200. */
export async function setSpeakerStatus(
  token: string,
  eventCode: string,
  speakerId: string,
  newStatus: string
): Promise<void> {
  const res = await fetch(
    `${API_BASE_URL}/api/v1/events/${eventCode}/speakers/${speakerId}/status`,
    {
      method: 'PUT',
      headers: authHeaders(token),
      body: JSON.stringify({ newStatus, reason: 'E2E setup' }),
    }
  );
  if (res.status !== 200) {
    const body = await res.text().catch(() => '');
    throw new Error(
      `[speaker-fixture] status→${newStatus} failed: ${res.status} ${res.statusText} ${body}`
    );
  }
}

/**
 * Variant tolerating the slot-capacity gate: READY→INVITED returns 409 once
 * `acceptedCount + invitedCount >= maxSlots`. Returns the HTTP status so callers can branch.
 */
export async function setSpeakerStatusAllowGate(
  token: string,
  eventCode: string,
  speakerId: string,
  newStatus: string
): Promise<number> {
  const res = await fetch(
    `${API_BASE_URL}/api/v1/events/${eventCode}/speakers/${speakerId}/status`,
    {
      method: 'PUT',
      headers: authHeaders(token),
      body: JSON.stringify({ newStatus, reason: 'E2E setup' }),
    }
  );
  if (![200, 409].includes(res.status)) {
    const body = await res.text().catch(() => '');
    throw new Error(
      `[speaker-fixture] status→${newStatus} (gate) unexpected: ${res.status} ${res.statusText} ${body}`
    );
  }
  return res.status;
}

/**
 * Promote a CONTACTED speaker to READY (provisions a CUMS user). Pass the canonical
 * `Bruno`/`Test` names so the created username (`bruno.test(.N)`) is swept by `cums/users`.
 * Accepts 200/201 (create-or-update user behind the scenes).
 */
export async function promoteSpeaker(
  token: string,
  eventCode: string,
  speakerId: string,
  opts: { email: string; firstName?: string; lastName?: string }
): Promise<void> {
  const res = await fetch(
    `${API_BASE_URL}/api/v1/events/${eventCode}/speakers/${speakerId}/promote`,
    {
      method: 'POST',
      headers: authHeaders(token),
      body: JSON.stringify({
        email: opts.email,
        firstName: opts.firstName ?? 'Bruno',
        lastName: opts.lastName ?? 'Test',
      }),
    }
  );
  if (![200, 201].includes(res.status)) {
    const body = await res.text().catch(() => '');
    throw new Error(`[speaker-fixture] promote failed: ${res.status} ${res.statusText} ${body}`);
  }
}

/**
 * Read a single pool speaker's status (UPPER_CASE-normalised), or undefined if absent.
 * The authoritative signal a mutating @smoke asserts on (e.g. INVITED after send-invitation).
 */
export async function getSpeakerStatus(
  token: string,
  eventCode: string,
  speakerId: string
): Promise<string | undefined> {
  const res = await fetch(`${API_BASE_URL}/api/v1/events/${eventCode}/speakers/pool`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) {
    const body = await res.text().catch(() => '');
    throw new Error(`[speaker-fixture] get pool failed: ${res.status} ${res.statusText} ${body}`);
  }
  const entries = (await res.json()) as Array<{ id?: string; status?: string }>;
  const entry = Array.isArray(entries) ? entries.find((e) => e.id === speakerId) : undefined;
  return entry?.status?.toUpperCase();
}
