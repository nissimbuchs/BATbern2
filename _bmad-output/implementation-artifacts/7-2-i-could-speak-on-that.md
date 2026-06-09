# Story 7.2: "I Could Speak on That"

Status: ready-for-dev

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

## Tasks / Subtasks

- [ ] **Task 1: Schema — add `source` + `proposed_by_username` to speaker_pool** (AC: 2)
  - [ ] New forward migration (current highest in event-management-service is **V108**; use next free number at implementation time). `ALTER TABLE speaker_pool ADD COLUMN source VARCHAR(30) NOT NULL DEFAULT 'organizer_added'`, `ADD COLUMN proposed_by_username VARCHAR(100)`. CHECK `source IN ('organizer_added','self_nomination')` (extendable).
  - [ ] Add fields to `SpeakerPool` entity.
- [ ] **Task 2: Self-nomination endpoint** (AC: 1, 2, 3, 4)
  - [ ] New `SelfNominationController` (or add to an existing speaker controller) `POST /api/v1/events/{eventCode}/speakers/self-nominate`, `@PreAuthorize("hasRole('ATTENDEE')")`.
  - [ ] Load event via `eventRepository.findByEventCode(eventCode)`; **guard**: `topicCode != null` AND published — else throw a 409/422 exception (add handler).
  - [ ] Create the `speaker_pool` row via the **existing add-to-pool creation path** (entity defaults `status = IDENTIFIED`); set `speakerName` (from JWT/user lookup), `source = self_nomination`, `proposed_by_username = username`, `assignedOrganizerId = null`. Do NOT call `transition(...)` to create.
  - [ ] Username from `SecurityContextHelper.getCurrentUsername()` (String — NO `SecurityPrincipal`).
- [ ] **Task 3: SecurityConfig (both layers)** (AC: 5)
  - [ ] api-gateway: endpoint `authenticated()`. event-management-service `SecurityConfig`: `.requestMatchers(HttpMethod.POST, "/api/v1/events/*/speakers/self-nominate").hasRole("ATTENDEE")` (prod chain); local/test permitAll confirmed.
- [ ] **Task 4: OpenAPI** (AC: 7)
  - [ ] Add the endpoint to `docs/api/speakers-api.openapi.yml` (security: ATTENDEE; body sessionTitle+abstract; 201 → SpeakerPoolResponse). Regenerate + commit types.
- [ ] **Task 5: Organizer visibility** (AC: 6)
  - [ ] Confirm self-nominations render in the existing speaker-pool/brainstorming UI; surface the `source` so organizers can see "self-nominated". No new triage flow.
- [ ] **Task 6: Attendee frontend** (AC: 7)
  - [ ] Extend 7.1's contribution surface with an "I could speak on that" form (title + abstract), shown only when the current/next event's topic is set + published. Service call → `POST .../self-nominate`. i18n in all 10 locales.
- [ ] **Task 7: Tests (TDD)** (AC: 1–6)
  - [ ] Integration (PostgreSQL): create → row at `IDENTIFIED`, `source='self_nomination'`, username set, NO session_users/role/Cognito; topic-unset/unpublished → 409/422; anonymous → 401. Assert `SpeakerWorkflowService` was NOT used to create.
  - [ ] Verify promote path still works from the self-nominated entry (IDENTIFIED → CONTACTED → READY via existing endpoints).

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

### Debug Log References

### Completion Notes List

### File List

## Open Questions

1. **What name/company do we store for a self-nominee at IDENTIFIED?** `speaker_pool.speaker_name` is required. We can pull the attendee's name from their user profile (via the JWT username → user lookup) so the organizer sees a real name in the pool. Please confirm we should auto-fill name (and optionally company) from the logged-in profile rather than asking the attendee to re-type it.
2. **Should an attendee be limited to one self-nomination per event?** Nothing stops a keen attendee from submitting several session ideas. A soft limit (e.g. one open self-nomination per attendee per event) keeps the organizer pool clean, but maybe multiple distinct talk ideas are fine. Recommendation: allow a small number, dedupe obvious repeats at triage — confirm the desired limit.
3. **Exactly which event does the attendee nominate for — the "next" event, or any open one?** The window is "topic set + published," but if two events are simultaneously in that state, the UI needs to know which one to target. Usually there's a single upcoming event; please confirm we can assume "the next published event with a topic" and surface its code, rather than letting the attendee pick from a list.
