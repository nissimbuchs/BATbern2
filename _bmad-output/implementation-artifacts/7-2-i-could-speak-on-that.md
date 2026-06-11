# Story 7.2: "I Could Speak on That"

Status: review (ADR-012 amendment implemented 2026-06-11)

> **⚠️ Reopened then re-implemented 2026-06-11 — see "ADR-012 Amendment" at the end of this file.**
> The original implementation stored the proposed talk as content columns on `speaker_pool`
> (V109), which reverses the 11.E.8 normalization. ADR-012 moved the pitch to a new
> `session_proposals` table, carries it into the canonical session at promote, and fixed the
> promote-picker SPEAKER filter. The amendment section is the authoritative spec; AC2/AC7/AC8
> above are superseded where they conflict with it. **All amendment tasks (T1′–T8′) are done and
> green** — see the checklist + Dev Agent Record (ADR-012) at the end.

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **logged-in attendee**,
I want to raise my hand with a session title and abstract once the next event's topic is set and published,
so that BATbern's speaker pipeline becomes pull-and-push instead of only organizer-sourced — without me needing to know an organizer.

**Source:** Brainstorming 2026-06-06 idea #06 (GitHub #751, 4 votes). Epic FR5–FR7. Composes onto the unified speaker workflow (Epic 11 / ADR-009).

## Acceptance Criteria

1. When an event's topic is **set** (`event.topicCode IS NOT NULL`) **and** the event is **published** (`event.publishedAt IS NOT NULL` / `currentPublishedPhase != 'none'`), a logged-in attendee can self-nominate via `POST /api/v1/events/{eventCode}/speakers/self-nominate` with `{ sessionTitle, abstract }`.
2. A self-nomination creates a `speaker_pool` row at state **`IDENTIFIED`** (the default entry state via the add-to-pool save path) tagged `source = 'self_nomination'` with `proposed_by_username` = JWT username. **No** Cognito user, SPEAKER role, or `session_users` row is created.
3. The row's status is **never** set to anything other than the `IDENTIFIED` default at creation; any subsequent status change goes **only** through `SpeakerWorkflowService.transition(...)` (ADR-009 sole-writer rule). The abstract is stored **raw** (no agent pre-screen).
4. Self-nominating when the event topic is not set OR the event is not published is rejected (**409/422**); no row is created.
5. Anonymous callers are rejected **401** (login-gated in BOTH api-gateway and event-management-service `SecurityConfig`).
6. The self-nomination appears in the existing organizer speaker-pool / brainstorming UI alongside organizer-sourced candidates and follows the existing triage → `promote` path unchanged (promotion to `READY` stays organizer-only, with provisioning happening only there).
7. OpenAPI updated; integration tests (PostgreSQL) cover create-at-IDENTIFIED, no-provisioning, topic/published guard, 401, and source tagging. Attendee UI extends 7.1's contribution surface; i18n in all 10 locales.
8. The speaker name is auto-filled from the attendee's profile (attendee does not type it). An attendee may have at most **one** self-nomination per event; a second attempt is rejected (409). The entry point is an "I could speak on that" button on the upcoming-event card, shown only to logged-in users when the event's topic is set + published.

## Tasks / Subtasks

- [x] **Task 1: Schema — add `source` + `proposed_by_username` to speaker_pool** (AC: 2, 8)
  - [x] New forward migration **`V109__add_self_nomination_to_speaker_pool.sql`** (V108 was the highest). `ADD COLUMN source VARCHAR(30) NOT NULL DEFAULT 'organizer_added'`, `proposed_by_username VARCHAR(100)`, **plus** `proposed_session_title VARCHAR(255)` + `proposed_abstract TEXT` (see decision below). CHECK `source IN ('organizer_added','self_nomination')`.
  - [x] **One-per-event dedupe:** partial unique index `ux_speaker_pool_self_nom ON speaker_pool(event_id, proposed_by_username) WHERE source = 'self_nomination'`.
  - [x] Added fields to `SpeakerPool` entity. `source` carries `@Builder.Default` — `SpeakerPool.builder()` is used across the test suite and would otherwise persist a null `source` into the NOT NULL column.
- [x] **Task 2: Self-nomination endpoint** (AC: 1, 2, 3, 4)
  - [x] New `SelfNominationController` `POST /api/v1/events/{eventCode}/speakers/self-nominate`, `@PreAuthorize("hasRole('ATTENDEE')")`.
  - [x] Loads event via `eventRepository.findByEventCode`; **guard** `topicCode != null && published` — else `SelfNominationNotAllowedException` → 409 (`SELF_NOMINATION_NOT_ALLOWED`).
  - [x] Creates the row via the add-to-pool save path (entity default `status = IDENTIFIED`); `speakerName` **auto-filled from the profile** (`UserApiClient.getUserByUsername` → firstName+lastName, company = companyId; falls back to the username if CUMS is down), `source = self_nomination`, `proposed_by_username = username`, `proposed_session_title`/`proposed_abstract` from the body, `assignedOrganizerId = null`. Never calls `transition(...)`.
  - [x] **One-per-event:** friendly `existsBy…` pre-check + `saveAndFlush` so the partial unique index surfaces the race deterministically → `DuplicateSelfNominationException` → 409 (`DUPLICATE_SELF_NOMINATION`).
  - [x] Username from `SecurityContextHelper.getCurrentUsername()` (String — no `SecurityPrincipal`).
- [x] **Task 3: SecurityConfig (both layers)** (AC: 5)
  - [x] Login-gating is enforced by `anyRequest().authenticated()` in BOTH the api-gateway and event-management-service prod chains (self-nominate is not in any permitAll list); the **role** gate is method-level `@PreAuthorize("hasRole('ATTENDEE')")`. This matches the established convention — every speaker endpoint is method-gated, not listed in `SecurityConfig` (see the `GET …/speakers` comment). A redundant `requestMatchers` line was deliberately NOT added.
- [x] **Task 4: OpenAPI** (AC: 7)
  - [x] Added the path + `SelfNominateSpeakerRequest` schema and the new `source`/`proposedByUsername`/`proposedSessionTitle`/`proposedAbstract` fields on `SpeakerPoolResponse` to **`docs/api/events-api.openapi.yml`** (the pool endpoints live there, not `speakers-api.openapi.yml`). Regenerated + committed frontend types (`npm run generate:api-types`).
- [x] **Task 5: Organizer visibility** (AC: 6)
  - [x] `SpeakerStatusLanes` kanban card shows a "Self-nominated" `Chip` when `source === 'self_nomination'` and renders the proposed talk title + abstract. No new triage flow — the row follows the unchanged promote path.
- [x] **Task 6: Attendee frontend** (AC: 7) — entry point = button on upcoming-event cards
  - [x] `SpeakerSelfNominatePanel` renders an "I could speak on that" button on `UpcomingEventsSection`'s `EventCard`s, login-gated (`useAuth`) + only when topic is set + a publishing phase is active. Clicking opens a Tailwind Dialog (title + abstract) that nominates for that card's `eventCode`. Service: `speakerNominationApi.selfNominateSpeaker`.
  - [x] Already-nominated handling: success and a 409 both flip to the already-done state. i18n `attendee.selfNominate.*` in all 10 locales + organizer `speakerCard.selfNominated` in all 10.
  - [x] Bundle boundary: the card renders on the **public Tailwind-only homepage**, so the whole panel (button + Dialog form) is Tailwind-only — built from `@/components/public/ui/*`, no MUI. The form lives in a Radix Dialog (portal) so its clicks don't bubble to the card's `<Link>`; the panel is rendered as a sibling outside the `<Link>` to avoid nested interactives.
- [x] **Task 7: Tests (TDD)** (AC: 1–6)
  - [x] `SelfNominationIntegrationTest` (PostgreSQL, 11 tests): create → row at `IDENTIFIED`, `source='self_nomination'`, username set, name auto-filled from profile, **no session / no status-history row / `provisionUserWithRole` never called**; topic-unset & unpublished → 409 (no row); duplicate → 409; unknown field / missing field → 400; not-found → 404; wrong role → 403; unauthenticated → rejected (403 at the EMS layer in isolation; the 401 from AC5 is produced at the api-gateway — documented in the test).
  - [x] `should_allowPromotePath_from_selfNomination`: self-nominate → IDENTIFIED → CONTACTED (status endpoint) → READY (promote endpoint, provisioning here only).
  - [x] Frontend `SpeakerSelfNominatePanel.test.tsx` (6 tests): login gate, open form, submit-disabled validation, submit→success, 409→already-done, generic error.

## Review Findings

_Code review 2026-06-10 (bmad-code-review, Claude Opus 4.8 1M) — 3 adversarial layers (Blind Hunter, Edge Case Hunter, Acceptance Auditor). All 8 ACs verified IMPLEMENTED by the Auditor. 1 decision-needed (RESOLVED → patch), 5 patch (ALL APPLIED), 1 defer, 2 dismissed._

### Decision-needed — RESOLVED

- [x] [Review][Decision→Patch] Self-nomination advances the EVENT workflow state (TOPIC_SELECTION → SPEAKER_IDENTIFICATION) — `SpeakerPoolService.selfNominate` republishes the same `SpeakerAddedToPoolEvent` as the organizer add-path (SpeakerPoolService.java:240-250); its `@Async @EventListener` advances CREATED/TOPIC_SELECTION → SPEAKER_IDENTIFICATION (listener:67-79). Publishing the **topic** phase does NOT change `workflowState`, so `(published topic, TOPIC_SELECTION)` is the eligible state and the first self-nom drives the transition. **RESOLVED 2026-06-10 (Nissim, option 2): keep the transition — a speaker entering the pool should advance the event regardless of source — and add a test.** Patch applied: new IT `should_publishSpeakerAddedToPoolEvent_advancingWorkflow_from_topicSelection` (`@RecordApplicationEvents`) asserts a self-nom from a `TOPIC_SELECTION` event publishes the load-bearing `SpeakerAddedToPoolEvent` (deterministic; doesn't race the async listener). The pool ROW stays IDENTIFIED (AC3 safe).

### Patches — ALL APPLIED

- [x] [Review][Patch] Frontend conflates the two 409 codes — a `SELF_NOMINATION_NOT_ALLOWED` 409 was misrendered as "you've already nominated" [web-frontend/src/components/attendee/SpeakerSelfNominatePanel.tsx]. **Fixed:** added `isDuplicateSelfNomination(err)` helper keying on `details.code === 'DUPLICATE_SELF_NOMINATION'`; `onError`/`isDuplicateError` now use it, so a not-allowed 409 shows the generic error. New test `shows a generic error (NOT already-done) for a 409 SELF_NOMINATION_NOT_ALLOWED`.
- [x] [Review][Patch] Frontend publish-gate treated phase `"NONE"` as published [web-frontend/src/components/public/EventCard.tsx]. **Fixed:** whitelist `['TOPIC','SPEAKERS','AGENDA'].includes(event.currentPublishedPhase ?? '')` (the declared union omits the runtime `'NONE'` sentinel, so a string compare; mirrors the backend guard).
- [x] [Review][Patch] Organizer kanban showed the raw company **slug** for self-nominations [web-frontend/src/components/organizer/SpeakerStatus/SpeakerStatusLanes.tsx]. **Fixed:** added `companyDisplayName?` to `SpeakerPoolEntry` and render `companyDisplayName || company` (the list endpoint already resolves the display name via the `getCompanyDisplayName` overlay).
- [x] [Review][Patch] Backend/contract min-length was weaker than the UI [dto/SelfNominateSpeakerRequest.java + docs/api/events-api.openapi.yml]. **Fixed:** `@Size(min=5)` on sessionTitle + `@Size(min=10)` on abstract; OpenAPI `minLength` raised to 5/10 to match `MIN_TITLE`/`MIN_ABSTRACT`. (Unknown-field IT still 400 — Jackson rejects at deserialization before bean validation.)

### Deferred

- [x] [Review][Defer] `@PreAuthorize("hasRole('ATTENDEE')")` is the single load-bearing runtime assumption [SelfNominationController.java:50] — deferred, verify-in-prod. If logged-in attendees do not carry an explicit `ROLE_ATTENDEE` claim/assignment, every nomination 403s and the feature is dead-on-arrival. `JwtRolesConverter` maps `ROLE_ATTENDEE` from `custom:role`/`role_assignments`; JIT defaults ATTENDEE + Pattern 3b DB-fallback should cover it, but the integration tests use `@WithMockUser(roles={"ATTENDEE"})` and cannot catch a missing real-world claim. Confirm a real registered/federated attendee's token carries ATTENDEE before sign-off.

### Dismissed (noise / false positive)

- `proposedByUsername` PII added to the shared `SpeakerPoolResponse` — only populated on IDENTIFIED self-nomination rows, which surface only through ORGANIZER-gated listings (all `SpeakerStatusController` endpoints `@PreAuthorize ORGANIZER`); the self-nominee receives only their own username in the 201. Public speaker listings show only READY+ rows. Not a leak.
- Empty-JWT-claim users dedupe on the Cognito UUID rather than the human username (`getCurrentUsername` fallback) — dormant in staging-prod (the JWT always carries `custom:username`); dedupe still functions (consistent UUID) and `speakerName` is independently profile-filled. Pre-existing identity-resolution behavior, not introduced by this change.

## Dev Notes

### CRITICAL service-location correction
`SpeakerWorkflowService`, `speaker_pool`, the promote endpoint, and `SpeakerStatusController` are all in **`event-management-service`** (the speaker workflow was unified there in Epic 11 / ADR-009) — **NOT** `speaker-coordination-service`. `events` and `speaker_pool` share one DB, so the topic/published check is a **local repository read**, no cross-service HTTP call.

### How entries are created vs transitioned (do not get this wrong)
- `SpeakerWorkflowService.transition(UUID speakerPoolId, SpeakerWorkflowState target, String username, TransitionPayload payload)` (`.../service/SpeakerWorkflowService.java` ~L152) **mutates an existing row** — it `findById`s and throws if absent. It does NOT create.
- New pool rows are created at `IDENTIFIED` (the entity default, `SpeakerPool` ~L86) via the add-to-pool save path (existing `POST /events/{eventCode}/speakers/pool`). **Self-nomination reuses this creation path**, then tags `source`/`proposed_by_username`.
- ADR-009 sole-writer rule: never `speakerPool.setStatus(...)` beyond relying on the IDENTIFIED default at construction; all later changes via `transition(...)`.

### READY provisioning is organizer-only (must NOT trigger here)
- `SpeakerStatusController` `POST /{speakerId}/promote` (~L100, `@PreAuthorize("hasRole('ORGANIZER')")`) is the ONLY path that provisions (User + SPEAKER role + `PRIMARY_SPEAKER` `session_users` row + auto-registration) — and it requires the row to be in `CONTACTED` first. Self-nomination lands at `IDENTIFIED`; organizers move it forward as usual.

### Event "topic set + published" signals (local reads)
- `Event` entity (`.../domain/Event.java`): `topicCode` (null ⇒ topic not set), `publishedAt`, `currentPublishedPhase` ('none'/'topic'/'speakers'/'agenda'), `workflowState`. Guard = `topicCode != null && (publishedAt != null || !"none".equals(currentPublishedPhase))`. Confirm exact field names at implementation.

### Identity / enum flow
- `SpeakerWorkflowState` stored `lowercase_snake_case` in DB (`'identified'`), `UPPER_CASE` in Java/JSON — converter handles it. `source` is a new simple string; store consistently (recommend lowercase to match `speaker_pool.status`). Username is the meaningful ID (ADR-003); no `SecurityPrincipal` (use `SecurityContextHelper.getCurrentUsername()`).

### Project Structure Notes
- `speaker_pool` dropped `username`/`email` columns in V103; identity post-READY is via `PrimarySpeakerResolver`. Pre-READY rows (IDENTIFIED) have no session_users row — do not assume one.

### References
- [Source: docs/prd/epic-7-attendee-experience-enhancements.md#story-72-i-could-speak-on-that]
- [Source: services/event-management-service/.../service/SpeakerWorkflowService.java#transition]
- [Source: services/event-management-service/.../controller/SpeakerStatusController.java#promote]
- [Source: services/event-management-service/.../domain/SpeakerPool.java] [Source: .../domain/Event.java]
- [Source: _bmad-output/project-context.md#architecture-unified-speaker-workflow-adr-009]

## Dev Agent Record

### Agent Model Used

Claude Opus 4.8 (1M context) — bmad-dev-story, 2026-06-10.

### Debug Log References

- `SelfNominationIntegrationTest`: 11/11 PASS (`/tmp/ems-selfnom-test3.log`).
- Speaker-pool backend regression (`SpeakerPoolWorkflowIntegrationTest`, `SpeakerPromoteControllerIntegrationTest`, `SpeakerPoolRepositoryIntegrationTest`, `SpeakerStatusControllerIntegrationTest`, all `dto.*`): PASS (`/tmp/ems-regress.log`).
- Frontend `SpeakerSelfNominatePanel.test.tsx`: 6/6 PASS. Regression (`EventCard`, `SpeakerStatusLanes`, public + organizer suites): 268/268 PASS (`/tmp/fe-regress.log`). `type-check` + `eslint` clean.
- One iteration: the AC5 anonymous test first asserted 401; `@WithAnonymousUser` / no-auth under the EMS `TestSecurityConfig` (method-security only, no JWT resource-server) yields 403, so the test now asserts the accurate EMS-layer 403 and documents that the 401 is produced at the api-gateway.

### Completion Notes List

- **Service location**: everything is in `event-management-service` (speaker workflow unified there per Epic 11 / ADR-009), NOT speaker-coordination. No api-gateway `DomainRouter` change was needed — `/api/v1/events/*/speakers/self-nominate` already falls through to the events route (contrast Story 7.1, which needed a new `/attendees/topics` route).
- **Where `sessionTitle` + `abstract` are stored (decision)**: the story's Task 1 named only `source` + `proposed_by_username`, but AC1/AC6 require persisting + showing the proposed talk. I added two explicit columns — `proposed_session_title` + `proposed_abstract` — rather than overloading `expertise`/`notes`. This is structured, organizer-visible, and keeps the pre-READY pool row honest (there is no `sessions.title` yet at IDENTIFIED; `initial_presentation_title` was dropped in V102). See Open Questions for PM confirmation.
- **No provisioning** is the load-bearing invariant: the row is created via `new SpeakerPool()` + setters at the IDENTIFIED default, never via `transition(...)`. The test asserts no session, no `speaker_status_history` row, and `provisionUserWithRole` never called. Promotion to READY (and all provisioning) stays organizer-only and is proven still reachable from a self-nominated row.
- **Identity auto-fill**: name + company come from `UserApiClient.getUserByUsername`; on a `UserServiceException` (CUMS down) it degrades to the username. The request body cannot set the name (`@JsonIgnoreProperties(ignoreUnknown=false)` → unknown field 400), so identity can't be spoofed.
- **AC5 status nuance**: anonymous → 401 is the api-gateway's behavior (no JWT). Within EMS in isolation the same call is a 403 under method security (TestSecurityConfig has no JWT entry-point). Both reject; documented in the test.
- **i18n**: `attendee.selfNominate.*` (common.json) + `speakerCard.selfNominated` (organizer.json) populated in all 10 locales; EN/DE first-class, other 8 straight translations.

### File List

**Backend (event-management-service)**
- `src/main/resources/db/migration/V109__add_self_nomination_to_speaker_pool.sql` (new)
- `src/main/java/ch/batbern/events/domain/SpeakerPool.java` (4 fields)
- `src/main/java/ch/batbern/events/dto/SelfNominateSpeakerRequest.java` (new)
- `src/main/java/ch/batbern/events/dto/SpeakerPoolResponse.java` (4 fields + accessors + fromEntity mapping)
- `src/main/java/ch/batbern/events/exception/SelfNominationNotAllowedException.java` (new)
- `src/main/java/ch/batbern/events/exception/DuplicateSelfNominationException.java` (new)
- `src/main/java/ch/batbern/events/exception/GlobalExceptionHandler.java` (2 handlers → 409)
- `src/main/java/ch/batbern/events/repository/SpeakerPoolRepository.java` (existsBy… dedupe method)
- `src/main/java/ch/batbern/events/service/SpeakerPoolService.java` (`selfNominate` + source constants)
- `src/main/java/ch/batbern/events/controller/SelfNominationController.java` (new)
- `src/test/java/ch/batbern/events/controller/SelfNominationIntegrationTest.java` (new, 11 tests)

**Contract**
- `docs/api/events-api.openapi.yml` (self-nominate path + `SelfNominateSpeakerRequest` schema + 4 `SpeakerPoolResponse` fields)
- `web-frontend/src/types/generated/*` (regenerated)

**Frontend (web-frontend)**
- `src/services/api/speakerNominationApi.ts` (new)
- `src/components/attendee/SpeakerSelfNominatePanel.tsx` (new)
- `src/components/attendee/__tests__/SpeakerSelfNominatePanel.test.tsx` (new, 6 tests)
- `src/components/public/EventCard.tsx` (`enableSelfNomination` prop + panel render)
- `src/components/public/UpcomingEventsSection.tsx` (passes `enableSelfNomination`)
- `src/components/organizer/SpeakerStatus/SpeakerStatusLanes.tsx` (Self-nominated badge + proposed talk)
- `src/types/speakerPool.types.ts` (4 fields on `SpeakerPoolEntry`)
- `public/locales/*/common.json` (10 locales: `attendee.selfNominate.*`)
- `public/locales/*/organizer.json` (10 locales: `speakerCard.selfNominated`)

## Change Log

| Date | Change |
|------|--------|
| 2026-06-10 | Implemented Story 7.2 "I Could Speak on That" — attendee speaker self-nomination. Backend (V109 migration + entity/DTO/service/controller/exceptions in event-management-service), OpenAPI + regenerated types, attendee Tailwind panel on upcoming-event cards, organizer self-nominated badge + proposed talk, i18n in 10 locales. 11 backend + 6 frontend tests; no regressions. Status → review. |
| 2026-06-11 | **Reopened (status done → in progress).** ADR-012 ratified: the proposed talk must NOT live as content columns on `speaker_pool` (reverses 11.E.8). Move pitch to new `session_proposals` table; rewrite V109 (dev-only exception, unpushed); fix promote-picker SPEAKER filter; carry proposal into the canonical session at READY; add schema-fitness guard. See "ADR-012 Amendment" section + `docs/sprint-change-proposal-2026-06-11.md`. Handed to Amelia. |

## Open Questions

1. **Where the proposed talk is stored.** The story listed only `source` + `proposed_by_username` as new columns, but the attendee's `sessionTitle` + `abstract` need a home that organizers can see. I added two explicit, organizer-visible columns (`proposed_session_title`, `proposed_abstract`) rather than overloading `expertise`/`notes`. If the PM would rather these flow into `expertise`/`notes` (so they reuse the existing kanban display with zero new fields), that's a small change — but the explicit columns keep the data structured for any later "pre-fill the session on promote" feature. Please confirm the column approach is acceptable.

2. **Frontend "published" gate.** The card shows the button when a topic object is present AND a publishing phase is active (`currentPublishedPhase` truthy). The backend is authoritative (409 otherwise), so this is only a UX hint. Confirm that "any active publishing phase" is the right visibility threshold, or whether it should be specifically the `topic` phase and beyond.

## Resolved Decisions

_Resolved with the PM 2026-06-10._

1. **Name/company:** **Auto-fill from the attendee's profile** (via `UserApiClient` lookup) — the attendee only types title + abstract; no re-typing of identity.
2. **Limit:** **Exactly one** self-nomination per attendee per event (partial unique index + 409 on repeat).
3. **Target event:** No standalone picker. The entry point is an **"I could speak on that" button on the upcoming-event card** (logged-in users only, when the event's topic is set + published); the card supplies the `eventCode`, so the nomination targets that specific event. Naturally handles multiple simultaneous open events (each card has its own button).

---

## ADR-012 Amendment (2026-06-11)

_Ratified with the architect (Winston) + PO (Nissim). Authoritative for the remaining work; see
`docs/architecture/ADR-012-self-nomination-proposals.md` and
`docs/sprint-change-proposal-2026-06-11.md`. Branch is unpushed; nothing deployed._

**Why:** V109 stored the proposed talk as `proposed_session_title` / `proposed_abstract` columns
on `speaker_pool` — content on the workflow-state table, reversing 11.E.8 / V103. A self-nomination
is an *application/pitch*, distinct from speaker (workflow) and session (content); it gets its own
table. Also fixes a live promote bug (non-SPEAKER self-nominee not selectable).

### Revised acceptance criteria (supersede AC2 / AC7 / AC8 where they conflict)

- **AC2′**: A self-nomination creates (a) a `speaker_pool` row at `IDENTIFIED` with **`source =
  'self_nomination'`** and **no other new columns**, and (b) a `session_proposals` row holding
  `proposed_title` + `proposed_abstract` + `proposed_by_username`. No Cognito user / SPEAKER role /
  `session_users` row. `speaker_pool` carries **no** `proposed_*` content columns.
- **AC8′**: One self-nomination per attendee per event — enforced by `UNIQUE(event_id,
  proposed_by_username)` on `session_proposals` (replaces the `speaker_pool` partial unique index).
  Repeat → 409 `DUPLICATE_SELF_NOMINATION` (unchanged behavior).
- **AC-PROMOTE-1 (new)**: In `PromoteSpeakerSubView.tsx`, the user picker shows **all users** (drop
  `role="SPEAKER"` on `UserAutocomplete` **and** the `roles?.includes('SPEAKER')` prefill filter,
  ~line 89). This is wanted **regardless** of the proposals redesign — a non-SPEAKER self-nominee
  (ATTENDEE) must be selectable. `provisionUserWithRole` already grants SPEAKER to an existing user
  idempotently by email (no duplicate). For `source = 'self_nomination'` rows, auto-prefill the
  picker from `proposed_by_username` (via `getUserByUsername`) so the organizer doesn't search.
- **AC-PROMOTE-2 (new)**: At the `CONTACTED → READY` hook
  (`SpeakerWorkflowService.provisionSessionAndPrimarySpeaker`), look up `session_proposals` by
  `speaker_pool_id`; when present, seed `sessions.title = proposed_title` (instead of the
  placeholder name) and the **first content submission** = `proposed_abstract`. The proposal row
  then becomes immutable audit (no further mutation). Session is canonical post-READY.
- **AC-GUARD (new)**: A schema-fitness test asserts `speaker_pool` has **no** title/abstract/
  materials content columns. `session_proposals` has **no status column** — acceptance is derived
  from the linked `speaker_pool` workflow state (no second state machine).

### Revised / new tasks (for Amelia)

- [x] **T1′ Migration (rewrite V109 — dev-only exception).** Roll back V109 on the dev DB
  (`flywayRepair`/manual drop is fine — it exists on **one dev DB only**, unpushed). Re-author
  **`V109__self_nomination_source_and_proposals.sql`** to: (a) `ALTER TABLE speaker_pool ADD COLUMN
  source VARCHAR(30) NOT NULL DEFAULT 'organizer_added'` + the `chk_speaker_pool_source` CHECK;
  (b) `CREATE TABLE session_proposals (id uuid PK, speaker_pool_id uuid NOT NULL REFERENCES
  speaker_pool(id) ON DELETE CASCADE, event_id uuid NOT NULL, proposed_by_username varchar(100) NOT
  NULL, proposed_title varchar(255) NOT NULL, proposed_abstract text NOT NULL, created_at
  timestamptz NOT NULL DEFAULT now(), UNIQUE(event_id, proposed_by_username))`. **Do NOT** add
  `proposed_*` columns to `speaker_pool`; **do NOT** add the `ux_speaker_pool_self_nom` partial
  index. (Once this branch is pushed, V109 is frozen forever per the standard Flyway rule.)
- [x] **T2′ Entity/repo.** New `SessionProposal` entity + `SessionProposalRepository`
  (`findBySpeakerPoolId`, `existsByEventIdAndProposedByUsername`). Remove the `proposed*` fields
  from `SpeakerPool` (keep `source`).
- [x] **T3′ Self-nominate write path.** `SpeakerPoolService.selfNominate`: create the IDENTIFIED
  pool row (source=self_nomination) **and** a `SessionProposal` row in the same transaction;
  dedupe via `existsByEventIdAndProposedByUsername` + the unique constraint (race → 409).
- [x] **T4′ Organizer kanban read.** Surface the proposed talk by joining `session_proposals` on
  `speaker_pool_id` (e.g. enrich `SpeakerPoolResponse.proposedTitle/proposedAbstract` from the
  proposal, not from `speaker_pool`). Self-nominated badge unchanged (keys on `source`).
- [x] **T5′ Promote picker fix** (`PromoteSpeakerSubView.tsx`): per AC-PROMOTE-1.
- [x] **T6′ Carry-into-session** (`SpeakerWorkflowService`): per AC-PROMOTE-2 (seed title +
  first content submission from the proposal; idempotent on re-run).
- [x] **T7′ Guard test**: schema-fitness assertion (AC-GUARD) + update the existing
  `SelfNominationIntegrationTest` to assert the proposal row + no `speaker_pool` content columns,
  and a promote IT proving title/abstract land on the session and an existing ATTENDEE is granted
  SPEAKER without a duplicate user.
- [x] **T8′ OpenAPI/types**: no change to the self-nominate request body; regenerate only if the
  organizer-facing `SpeakerPoolResponse` field source changes shape.

### Superseded original artifacts

- V109's four-column `speaker_pool` shape and `ux_speaker_pool_self_nom` index (T1 original).
- `SpeakerPool.proposedSessionTitle` / `proposedAbstract` / `proposedByUsername` fields.
- The original AC8 "partial unique index on `speaker_pool`".

### Dev Agent Record — ADR-012 implementation (Amelia, 2026-06-11)

**Model:** Claude Opus 4.8 (1M). All tasks T1′–T8′ done; tests green; dev DB reconciled.

**Backend (`event-management-service`):**
- `V109__add_self_nomination_to_speaker_pool.sql` — rewritten: adds only `source` to
  `speaker_pool` + `CHECK`; creates `session_proposals` (FK→speaker_pool + events, `UNIQUE(event_id,
  proposed_by_username)`, `proposed_title VARCHAR(200)`).
- New `domain/SessionProposal.java` + `repository/SessionProposalRepository.java`.
- `domain/SpeakerPool.java` — dropped `proposedByUsername`/`proposedSessionTitle`/`proposedAbstract`
  (kept `source`). `repository/SpeakerPoolRepository.java` — removed the old dedupe method.
- `service/SpeakerPoolService.java` — `selfNominate` writes the pool row + a `session_proposals`
  row (saveAndFlush → 409 on the unique constraint); dedupe via the proposal table; kanban list
  (`getSpeakerPool`) batch-loads proposals and enriches via `SpeakerPoolResponse.applyProposal`.
- `dto/SpeakerPoolResponse.java` — `fromEntity` no longer reads proposed_* from the entity; new
  `applyProposal(SessionProposal)` layers the pitch onto read paths + the 201.
- `service/SpeakerWorkflowService.java` — READY hook seeds `sessions.title` + the first
  `session_content_history` row from the proposal (`seedContentFromProposal`); placeholder only
  when there's no proposal.
- `dto/SelfNominateSpeakerRequest.java` + `docs/api/events-api.openapi.yml` — `sessionTitle` max
  200 (matches content title; pitch carried verbatim at promote).

**Frontend:**
- `components/organizer/SpeakerDrawer/PromoteSpeakerSubView.tsx` — dropped `role="SPEAKER"` on the
  picker + the SPEAKER-only prefill filter; for `source==='self_nomination'` auto-prefills via
  `getUserByUsername(proposedByUsername)`. (`provisionUserWithRole` grants SPEAKER to the existing
  user idempotently by email — no duplicate.)
- `components/attendee/SpeakerSelfNominatePanel.tsx` — `MAX_TITLE` 255→200.

**Tests (all green):** `SelfNominationIntegrationTest` (proposal row + no speaker_pool content
cols + carry-into-session at READY), `SpeakerPoolSchemaFitnessTest` (guard: no content columns on
speaker_pool; `session_proposals` shape, no status col), unit/IT regressions
(`SpeakerPoolServiceTest`, `SpeakerWorkflowServiceTest`, `SpeakerPromoteControllerIntegrationTest`,
`SpeakerStatusControllerIntegrationTest`, `SpeakerWorkflowServiceIntegrationTest`),
`PromoteSpeakerSubView.test.tsx` (new), `SpeakerSelfNominatePanel.test.tsx`. `tsc` + eslint clean.

**Dev DB:** V109 (which existed on the one dev DB only) reconciled — `session_proposals` created,
the one test self-nomination row migrated, the three `proposed_*` columns dropped; `flywayRepair`
realigned the V109 checksum; `flywayValidate` clean; event-management restarted and booted clean
(107 migrations validated).
