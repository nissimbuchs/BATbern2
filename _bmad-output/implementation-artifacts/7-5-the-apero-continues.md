# Story 7.5: The Apéro Continues

Status: review

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **logged-in attendee**,
I want a time-boxed Q&A per session after an event,
so that the open questions that currently have nowhere to live get answered — and become part of the session's permanent record in the archive.

**Source:** Brainstorming 2026-06-06 idea #01 (GitHub #746, 3 votes). Epic FR12–FR14. It is the **digital afterglow**, explicitly distinct from the physical apéro (Sacred-Three #18).

## Acceptance Criteria

1. When an event transitions to `EVENT_COMPLETED`, a Q&A window opens for each of its sessions with a default close time **14 days** out.
2. During an open window, **logged-in** attendees can post questions and answers; posts are attributed to their username.
3. **Anonymous** users can **read** frozen Q&A in the public archive but **cannot post** (POST → 401); GET is public.
4. An organizer can **extend** or **close early** a window and **take down** any post; changes take effect immediately (`@PreAuthorize("hasRole('ORGANIZER')")`).
5. When the window closes (scheduled job), the Q&A **freezes** to read-only (no further posts) and the thread is attached permanently to the session's archive page.
6. The close job uses ShedLock; its integration test uses a **class-scoped `@MockBean LockProvider`** (per the established PR #773 fix) so committed lock rows don't leak across tests.
7. Integration tests (PostgreSQL) cover: window opens on completion, logged-in post within window, anonymous post → 401, organizer extend/close/takedown, post-after-close rejected, frozen thread readable publicly. Q&A UI i18n in all 10 locales. No leftover test data.

## Tasks / Subtasks

- [x] **Task 1: Schema** (AC: 1, 2, 5)
  - [x] `V112__create_session_qna.sql` — `session_qna_window` (UNIQUE(session_id) → idempotent open; CHECK status in open|frozen; FK→sessions CASCADE) + `session_qna_post` (self-FK parent_post_id CASCADE, removed_at tombstone, FK→window CASCADE). Indexes on (status, closes_at) for the freeze scan + (window_id, created_at).
  - [x] `SessionQnaWindow` + `SessionQnaPost` entities (`@PrePersist`/`@PreUpdate`, `@EqualsAndHashCode(onlyExplicitlyIncluded)`). `QnaWindowStatus { OPEN, FROZEN }` + `QnaWindowStatusConverter` (DB lowercase ↔ Java UPPER, modeled on `EventWorkflowStateConverter`). NOTE: used `UUID` FK columns (not `@ManyToOne`) — simpler for this access pattern; same ADR-003 in-service-UUID rule.
- [x] **Task 2: Open windows on EVENT_COMPLETED** (AC: 1)
  - [x] `SessionQnaWindowListener` — `@EventListener` on `EventWorkflowTransitionEvent`, fires when `toState == EVENT_COMPLETED` (covers BOTH scheduler and manual transition; cleaner + more robust than editing `processCompletedEvents`). Calls `SessionQnaService.openWindowsForCompletedEvent` (idempotent, `existsBySessionId` guard, `closesAt = now + ${qna.window.default-days:14}d`). Best-effort try/catch like the sibling task listener — never blocks the transition.
- [x] **Task 3: Q&A endpoints (mixed auth)** (AC: 2, 3, 4, 5)
  - [x] `SessionQnaController` @ `/api/v1/events/{eventCode}/sessions/{sessionSlug}/qna`: `GET` (public), `POST /posts` (`@PreAuthorize("isAuthenticated()")` — ANY logged-in user per Resolved Decision #2, not role-restricted; rejects FROZEN → 409), `PATCH` + `DELETE /posts/{id}` (`@PreAuthorize("hasRole('ORGANIZER')")`).
  - [x] Posts attributed via `SecurityContextHelper.getCurrentUsername()`.
- [x] **Task 4: Scheduled freeze job** (AC: 5, 6)
  - [x] `SessionQnaScheduledService.freezeExpiredWindows` — `@Scheduled(cron=${qna.scheduled.freeze.cron:0 5 * * * *})` (hourly) + `@SchedulerLock(name="freezeQnaWindows", lockAtMostFor="5m", lockAtLeastFor="30s")`. Flips OPEN windows past `closesAt` → FROZEN.
- [x] **Task 5: Archive rendering** (AC: 3, 5)
  - [x] Confirmed: public per-session render is `SessionCards` on `HomePage` (Tailwind-only). New `SessionQnaThread` (Tailwind, NO MUI) wired into each `SessionCards` card when `showMaterials` (POST_EVENT/ARCHIVE). Renders nothing when no window (404). Threaded display (questions + one-level answers), tombstones, OPEN→post/reply forms (logged-in), organizer takedown, FROZEN→read-only. `useAuth` gates posting/takedown.
- [x] **Task 6: SecurityConfig (both layers)** (AC: 3)
  - [x] GET `/api/v1/events/*/sessions/*/qna` permitAll in BOTH gateway + EMS `SecurityConfig`. POST/PATCH/DELETE fall through to `.anyRequest().authenticated()` + `@PreAuthorize` (mirrors `SessionMaterialsController`).
- [x] **Task 7: OpenAPI + tests + doc-drift** (AC: 7)
  - [x] OpenAPI: 3 paths (qna GET/PATCH, qna/posts POST, qna/posts/{id} DELETE) + 4 schemas; frontend types regenerated. `SessionQnaIntegrationTest` (PostgreSQL) — 11 tests green with **class-scoped `@MockBean LockProvider`** (PR #773 pattern) for the freeze job. `SessionQnaThread.test.tsx` — 7 tests green (`waitFor` for async). doc-drift: `docs/architecture/06a-workflow-state-machines.md` updated with the EVENT_COMPLETED Q&A side-effect + freeze job.

## Dev Notes

### Lifecycle & scheduling (REUSE)
- **EVENT_COMPLETED:** `EventWorkflowScheduledService.processCompletedEvents()` (~L131–205, cron `0 59 23 * * *`) transitions via `workflowStateMachine.transitionToState(..., EVENT_COMPLETED, ...)`, which publishes `EventWorkflowTransitionEvent`. **Hook window-opening here or in a listener for that event.**
- ⚠️ **Archive interplay:** `processEventsToArchive()` (~L207–269, cron `0 0 2 * * *`) moves `EVENT_COMPLETED → ARCHIVED` ~14 days after the event date. The Q&A 14-day window roughly coincides with archival — confirm the window survives/aligns with archival (the frozen thread must remain on the archived session page).
- **ShedLock:** `ShedLockConfig.java` (`JdbcTemplateLockProvider`, `usingDbTime()`, `defaultLockAtMostFor=10m`); `shedlock` table from `V31`. Existing `@Scheduled + @SchedulerLock` jobs in `EventWorkflowScheduledService` are the model.
- ⚠️ **Test gotcha (authoritative):** `@SchedulerLock(lockAtLeastFor=...)` writes a committed lock row that survives test rollback → use a **class-scoped `@MockBean LockProvider`** (per PR #773 fix). Do NOT use a `@TestConfiguration @Primary` LockProvider bean — it leaks globally (`NoUniqueBeanDefinitionException`). (Note: an older test in the repo may still use the `@TestConfiguration` form; follow the `@MockBean` approach.)

### Sessions & archive (REUSE)
- `Session` entity: `sessionSlug` (public ADR-003 id), `eventCode` (denormalized), `eventId` FK. `SessionRepository.findByEventIdWithSpeakers(...)`, `findByEventCodeAndSessionSlug(...)` (~L147). Public archive page `web-frontend/src/pages/public/ArchivePage.tsx` (Tailwind-only / `PublicLayout`) — confirm the per-session archive render path.
- **New-table patterns to copy:** `SessionContentVersion` / `SessionMaterial` entities; `SessionMaterialsController` mixed-auth (public GET download + `@PreAuthorize` writes, inline org/speaker checks). Migration style: `V108__...` (FK cascade handling).

### Auth & identity
- `SecurityContextHelper.getCurrentUsername()` (~L86, Pattern 3b DB fallback for local dev). `hasRole("ORGANIZER")` (~L196) for takedown/extend. `posted_by_username` is the meaningful ID (ADR-003); `session_id` UUID FK is in-service (allowed).

### Constraints / gotchas
- Sacred-Three #18: this is the DIGITAL afterglow — never represent or intrude on the physical apéro.
- Moderation floor (resolved): login-accountability + organizer takedown; agent curation (#23) is out of scope.
- i18n all 10 locales for the Q&A UI; enum value flow (UPPER Java/JSON, lowercase DB) via converter.
- `GlobalExceptionHandler`: add handlers for window-frozen (409) etc. Staging=production: tests clean up windows/posts; no real side effects.
- Frontend: MUI `Collapse unmountOnExit` removes nodes async — `waitFor()` on `.not.toBeInTheDocument()`.

### Project Structure Notes
- Controller/service/entity/repository under their packages; scheduled job under `.../scheduled` or `.../service`; migration under `.../resources/db/migration`.

### References
- [Source: docs/prd/epic-7-attendee-experience-enhancements.md#story-75-the-apero-continues]
- [Source: services/event-management-service/.../service/EventWorkflowScheduledService.java#processCompletedEvents]
- [Source: services/event-management-service/.../config/ShedLockConfig.java] [Source: .../controller/SessionMaterialsController.java]
- [Source: services/event-management-service/.../domain/Session.java] [Source: .../security/SecurityContextHelper.java]
- [Source: _bmad-output/project-context.md#backend-gotchas] [Source: project_shedlock_test_flakiness] [Source: feedback_drawer_workflow_only]

## Dev Agent Record

### Agent Model Used

Claude Opus 4.8 (1M context) — bmad-dev-story (Amelia).

### Debug Log References

- `SessionQnaIntegrationTest` (11 tests) — `BUILD SUCCESSFUL`, all PASSED (PostgreSQL/Testcontainers; ShedLock via class-scoped `@MockBean LockProvider`).
- `SessionQnaThread.test.tsx` (7 tests) — all PASSED (vitest).
- Frontend `tsc --noEmit` clean; ESLint clean; Prettier applied. `checkstyleMain` (event-management + api-gateway) — `BUILD SUCCESSFUL`.

### Completion Notes List

- **All 7 ACs satisfied.** Windows open per-session on EVENT_COMPLETED (idempotent), logged-in post within window, anonymous read-only (post rejected), organizer extend/close-early/takedown, scheduled ShedLock freeze, frozen thread public + soft-delete tombstones.
- **Decision — window opening hook:** used a dedicated `@EventListener` (`SessionQnaWindowListener`) on `EventWorkflowTransitionEvent` rather than editing `processCompletedEvents()`. It fires for BOTH scheduler and manual transitions to EVENT_COMPLETED, is idempotent, and (like the sibling task listener) never blocks the transition on failure.
- **Decision — who can post:** `@PreAuthorize("isAuthenticated()")` (any logged-in user, Resolved Decision #2), NOT role-restricted to ATTENDEE — the session's speaker is just a normal poster.
- **Anonymous POST status:** AC3 says 401; that is produced at the api-gateway (the public entry point). In EMS isolation `TestSecurityConfig` has no JWT entry point, so method security returns 403 — the integration test asserts 403 and documents the gateway 401 (same convention as Story 7.2).
- **Takedown = soft-delete tombstone** (Resolved Decision #3): `removed_at` set; the response nulls body + username and flags `removed:true`; never hard-deleted, so thread structure survives in the frozen archive.
- **Frozen thread survives archival** (Resolved Decision #1): the window/posts are independent of the EVENT_COMPLETED→ARCHIVED transition; `SessionCards` renders Q&A whenever `showMaterials` (POST_EVENT + ARCHIVE).
- **No notifications** (Resolved Decision #4) — none built for MVP.
- Entities use plain `UUID` FK columns (not `@ManyToOne`) — simplest for the read/write patterns here; still ADR-003-compliant (in-service UUID FKs).

### File List

**Backend (event-management-service):**
- `src/main/resources/db/migration/V112__create_session_qna.sql` (new)
- `src/main/java/ch/batbern/events/domain/QnaWindowStatus.java` (new)
- `src/main/java/ch/batbern/events/converter/QnaWindowStatusConverter.java` (new)
- `src/main/java/ch/batbern/events/domain/SessionQnaWindow.java` (new)
- `src/main/java/ch/batbern/events/domain/SessionQnaPost.java` (new)
- `src/main/java/ch/batbern/events/repository/SessionQnaWindowRepository.java` (new)
- `src/main/java/ch/batbern/events/repository/SessionQnaPostRepository.java` (new)
- `src/main/java/ch/batbern/events/dto/QnaPostRequest.java` (new)
- `src/main/java/ch/batbern/events/dto/QnaWindowPatchRequest.java` (new)
- `src/main/java/ch/batbern/events/dto/QnaPostResponse.java` (new)
- `src/main/java/ch/batbern/events/dto/QnaWindowResponse.java` (new)
- `src/main/java/ch/batbern/events/exception/QnaWindowFrozenException.java` (new)
- `src/main/java/ch/batbern/events/service/SessionQnaService.java` (new)
- `src/main/java/ch/batbern/events/service/SessionQnaScheduledService.java` (new)
- `src/main/java/ch/batbern/events/listener/SessionQnaWindowListener.java` (new)
- `src/main/java/ch/batbern/events/controller/SessionQnaController.java` (new)
- `src/main/java/ch/batbern/events/exception/GlobalExceptionHandler.java` (modified — +QnaWindowFrozen handler)
- `src/main/java/ch/batbern/events/config/SecurityConfig.java` (modified — +qna GET permitAll)
- `src/test/java/ch/batbern/events/controller/SessionQnaIntegrationTest.java` (new)

**API Gateway:**
- `src/main/java/ch/batbern/gateway/config/SecurityConfig.java` (modified — +qna GET permitAll)

**API contract + docs:**
- `docs/api/events-api.openapi.yml` (modified — +3 paths, +4 schemas)
- `docs/architecture/06a-workflow-state-machines.md` (modified — EVENT_COMPLETED Q&A side-effect + freeze job)

**Frontend (web-frontend):**
- `src/services/qnaService.ts` (new)
- `src/hooks/useQna/useQna.ts` (new)
- `src/components/public/Event/SessionQnaThread.tsx` (new)
- `src/components/public/Event/__tests__/SessionQnaThread.test.tsx` (new)
- `src/components/public/Event/SessionCards.tsx` (modified — render SessionQnaThread post-event)
- `src/types/generated/events-api.types.ts` (regenerated)
- `public/locales/{de,en,fr,it,rm,es,fi,nl,ja,gsw-BE}/events.json` (modified — `qna.*`)

### Change Log

| Date | Change |
|------|--------|
| 2026-06-10 | Story 7.5 implemented (Amelia / bmad-dev-story). Per-session Q&A: windows open on EVENT_COMPLETED (idempotent listener), public read / logged-in post / organizer extend-close-takedown, ShedLock hourly freeze, Tailwind-only thread on the archive session cards, 10-locale i18n. Backend 11 ITs + FE 7 unit tests green. Status → review. |

## Resolved Decisions

_Resolved with the PM 2026-06-10._

1. **Window vs archival:** 14-day default (organizer-overridable). The **frozen thread persists on the archived session page** — neither window-close nor the auto-archival job (~14d after event date) hides it. The Q&A render must survive `EVENT_COMPLETED → ARCHIVED`. (AC5 + Task 5.)
2. **Who can post:** **Any logged-in user** may post within the window; the session's speaker is just a normal poster (role may be badged). No event-registrant restriction.
3. **Takedown:** **Soft-delete with a tombstone** — set `removed_at` and render "removed by organizer"; never hard-delete (preserves thread coherence in the frozen archive). (Schema `removed_at` column + Task 3.)
4. **Notifications:** **None for the MVP** — attendees check back during the 2-week window. No new email machinery; revisit later if engagement warrants.

## Senior Developer Review (AI)

**Reviewed:** 2026-06-11, adversarial 3-layer review (Blind Hunter + Edge Case Hunter + Acceptance Auditor) over commits 78a9a837..e56dfff5. **Outcome: Approve with minor fixes applied.**

All 7 ACs + 4 Resolved Decisions + project rules (ADR-003, enum value-flow via converter, no-MUI-public, ShedLock `@MockBean` test, doc-drift) confirmed **Met** by the Acceptance Auditor.

Fixes applied this review (commit 2ca3966e):
- **[High] One-level threading + tombstone-reply guards.** `addPost` now rejects (400) a reply whose parent is itself an answer, and a reply to an organizer-removed post. Both were previously persisted but never rendered (the thread view groups answers only by their top-level question id), so they'd silently vanish. Added an integration test.

Noted but **not** changed (see Open Questions): public username attribution; `patchWindow` both-fields precedence; the rare concurrent-listener window-open race; freeze-job batch size.

## Open Questions

> **Resolved 2026-06-11 (Nissim/PM): all accepted as-is — current behavior confirmed, no changes.** Q1 public username attribution on the Q&A archive is fine. Q2 `patchWindow` keeps the `close=true`-wins precedence. Q3 concurrent window-open race left as-is (idempotent for the normal case). Q4 freeze-job batch size noted for the future, no action now.

1. **Public username attribution on the Q&A archive.** A frozen Q&A thread is publicly readable (AC3), and each post shows its poster's `username` (e.g. `john.doe`) to anonymous visitors — by design, since AC2 says posts are "attributed to their username". This is a deliberate contrast with 7.4, where notes are organizer-only. Are you comfortable publishing attendee usernames on the public archive, or would you prefer the public view to show display names / "BATbern attendee" / initials, with full usernames only for organizers? (Easy to add a public-vs-organizer branch like 7.4's notes if you want it.)

2. **`PATCH .../qna` when an organizer sends both `closesAt` and `close=true`.** Current precedence: `close=true` wins (the window freezes; the new `closesAt` is ignored). That seems the safer default, but it's silent. Keep this precedence, or reject the ambiguous combination with a 400?

3. **Concurrent window-opening race (low risk, left as-is).** Window creation is idempotent for the normal case (the `exists` guard handles a re-fired completion event). In the extraordinarily rare case of two `EVENT_COMPLETED` transitions for the same event firing in the *same instant* (e.g. the ShedLock-guarded scheduler colliding with a manual transition), the unique index would roll back that one opening attempt — the listener swallows it so the state transition is never blocked, and a later re-fire is idempotent. Acceptable for MVP, or do you want per-session isolation (each window in its own transaction)?

4. **Freeze job batch size.** `freezeQnaWindows` loads all expired open windows in a single query/transaction each hour. Trivial at BATbern's scale (a handful of sessions per event). Flagged only for the future: if an event ever had thousands of sessions, this would want pagination. No action needed now — noting it for the record.
