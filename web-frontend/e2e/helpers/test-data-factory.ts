/**
 * Canonical test-data factory (plan §A5)
 * docs/plans/playwright-staging-hardening.md
 *
 * Emits the SAME canonical prefixes the Bruno API-contract suite uses so both suites
 * share ONE cleanup contract. The bare prefixes here are exactly the literals the
 * server-side `/admin/test-fixtures/{cums,ems,pcs}/cleanup` endpoints validate against
 * (see the `CleanupEntityType` enums in each service's `TestFixtureCleanupService`).
 * `test-fixtures-cleanup.ts` sweeps with the bare prefix; the factory emits the bare
 * prefix + a unique suffix so each test row is distinct yet still matches `LIKE prefix%`.
 *
 * Quality bar (plan §"Quality bar" #2): every spec consumes this factory — NO inline
 * `Date.now()` titles, NO `"E2E Test …"` strings. Replacing those is what retires the
 * `"E2E Test Company"` prod-residue source the Bruno audit found.
 *
 * ── Server-side allowlist this factory is bound to (do NOT drift) ──────────────────
 *   CUMS  companies         prefix  BRUNOTESTCO            (regex ^BRUNOTESTCO$)
 *   CUMS  users             prefix  bruno.test / bruno.test.   (regex ^bruno\.test\.?$)
 *   CUMS  additional_emails prefix  bruno-test-            (regex ^bruno-test-$|^bruno-additional-$)
 *   EMS   events            prefix  BRUNO-TEST-            (regex ^BRUNO-TEST-$)  — by event_code
 *   EMS   sessions          prefix  bruno-test-session-    (regex ^bruno-test-session-$) — by session_slug
 *   EMS   topics            prefix  bruno-test-topic-      (regex ^bruno-test-topic-$)   — by topic_code
 *   PCS   partners          prefix  brtest                 (regex ^brtest$) — company_name VARCHAR(12)!
 *   PCS   meetings          id-allowlist (no prefix)
 *
 * ── Caveats baked in here ──────────────────────────────────────────────────────────
 * • Server-generated codes (plan §A5 "Critical caveat"): `eventCode` (server derives
 *   `BATbern{N}`) and `sessionSlug` (slugified from title) are NOT chosen by the client
 *   for events/sessions created through the real UI flow, so a prefix sweep is a NO-OP
 *   for them. Teardown for those is explicit DELETE by the captured code/id
 *   (`cleanupByCode` in test-fixtures-cleanup.ts). The `eventTitleToken` below seeds a
 *   recognizable token into UI-created event titles so a human (or a future title-based
 *   backstop) can spot orphans — note the current EMS cleanup matches `event_code`, not
 *   title, so this token is recognition-only today.
 * • Partner `company_name` is `VARCHAR(12)` (PCS V2 schema). `brtest` (6) + a 6-char
 *   suffix = exactly 12. `partnerName()` enforces that bound.
 * • Username must satisfy the CUMS CHECK `^[a-z]+\.[a-z]+(\.[0-9]+)?$`, so the suffix
 *   for usernames is NUMERIC only.
 */

/** Bare canonical prefixes — the literals the cleanup endpoints validate. */
export const CANONICAL_PREFIXES = {
  company: 'BRUNOTESTCO',
  /** Broad sweep form (`LIKE bruno.test%`) catches both `bruno.test` and `bruno.test.N`. */
  username: 'bruno.test',
  additionalEmail: 'bruno-test-',
  eventCode: 'BRUNO-TEST-',
  sessionSlug: 'bruno-test-session-',
  topicCode: 'bruno-test-topic-',
  partner: 'brtest',
} as const;

/** Human-recognizable token seeded into UI-created event titles (recognition-only). */
export const EVENT_TITLE_TOKEN = 'BATPW-E2E';

/** Monotonic-ish unique numeric stamp (ms). Numeric so it satisfies the username CHECK. */
function ts(): number {
  return Date.now();
}

/** Short base36 suffix derived from the ms clock — used where length is constrained. */
function shortId(len = 6): string {
  return ts().toString(36).slice(-len);
}

/** Company name: `BRUNOTESTCO<ts>` — swept by cums/companies. */
export function companyName(): string {
  return `${CANONICAL_PREFIXES.company}${ts()}`;
}

/** Username: `bruno.test.<ts>` — swept by cums/users; numeric suffix satisfies the CHECK. */
export function username(): string {
  return `${CANONICAL_PREFIXES.username}.${ts()}`;
}

/** Email: `bruno-test-<ts>@e2e.batbern.invalid` — additional-emails swept by cums/additional_emails. */
export function email(): string {
  return `${CANONICAL_PREFIXES.additionalEmail}${ts()}@e2e.batbern.invalid`;
}

/** Topic code: `bruno-test-topic-<ts>` — swept by ems/topics. */
export function topicCode(): string {
  return `${CANONICAL_PREFIXES.topicCode}${ts()}`;
}

/**
 * Session slug: `bruno-test-session-<ts>` — swept by ems/sessions ONLY when the slug is
 * client-chosen. UI-created sessions slugify from the title (server-generated) and must
 * be torn down by explicit delete instead. Use this when a test sets the slug directly.
 */
export function sessionSlug(): string {
  return `${CANONICAL_PREFIXES.sessionSlug}${ts()}`;
}

/** Partner company name: `brtest<6>` — exactly ≤12 chars for the PCS VARCHAR(12) column. */
export function partnerName(): string {
  return `${CANONICAL_PREFIXES.partner}${shortId(6)}`;
}

/**
 * Event title for UI-created events: `BATPW-E2E <ts>`. The event CODE is server-generated
 * (`BATbern{N}`), so capture the code from the create response and feed it to
 * `cleanupByCode` in afterAll — the title token is a human/orphan-spotting aid only.
 */
export function eventTitle(): string {
  return `${EVENT_TITLE_TOKEN} ${ts()}`;
}

/** Upload filename: `bruno-test-<ts>.png`. (No EMS cleanup entityType reaches uploads today.) */
export function uploadFileName(ext = 'png'): string {
  return `${CANONICAL_PREFIXES.additionalEmail}${ts()}.${ext}`;
}

/** A minimal but VALID 1×1 PNG (real IHDR/IDAT/IEND chunks) — sharp/image pipelines accept it. */
const ONE_BY_ONE_PNG_BASE64 =
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAC0lEQVR4nGNgYGAAAAAEAAH2FzhVAAAAAElFTkSuQmCC';

/**
 * In-memory PNG upload payload for Playwright `setInputFiles`. Returns the canonical
 * `bruno-test-<ts>.png` filename + a real PNG buffer so the photo-upload `@smoke` exercises
 * the genuine ADR-002 3-phase S3 flow without needing a fixture file on disk. Uploads have
 * no prefix-sweep path, so the spec tears down by explicit `DELETE /users/me/picture`.
 */
export function uploadPngFile(): { name: string; mimeType: string; buffer: Buffer } {
  return {
    name: uploadFileName('png'),
    mimeType: 'image/png',
    buffer: Buffer.from(ONE_BY_ONE_PNG_BASE64, 'base64'),
  };
}
