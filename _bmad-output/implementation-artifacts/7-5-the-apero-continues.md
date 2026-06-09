# Story 7.5: The Apéro Continues

Status: ready-for-dev

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

- [ ] **Task 1: Schema** (AC: 1, 2, 5)
  - [ ] New forward migration (current highest **V108**; next free at implementation). `session_qna_window(id UUID PK, session_id UUID NOT NULL FK→sessions ON DELETE CASCADE, event_code VARCHAR(50), status VARCHAR(20) NOT NULL DEFAULT 'open', opens_at TIMESTAMPTZ, closes_at TIMESTAMPTZ)`. `session_qna_post(id UUID PK, window_id UUID NOT NULL FK→session_qna_window ON DELETE CASCADE, parent_post_id UUID NULL, posted_by_username VARCHAR(100) NOT NULL, body TEXT NOT NULL, removed_at TIMESTAMPTZ NULL, created_at TIMESTAMPTZ DEFAULT now())`.
  - [ ] `SessionQnaWindow` + `SessionQnaPost` entities (model on `SessionContentVersion`/`SessionMaterial`: `@ManyToOne(LAZY)` FK, `@PrePersist/@PreUpdate`, excluded from `@ToString`). Status enum `QnaWindowStatus { OPEN, FROZEN }` + converter (DB lowercase, Java UPPER).
- [ ] **Task 2: Open windows on EVENT_COMPLETED** (AC: 1)
  - [ ] Hook the completion path in `EventWorkflowScheduledService.processCompletedEvents()` (~L131–205) / the `EventWorkflowTransitionEvent` listener: for each session of the completed event, create an OPEN `session_qna_window` with `closes_at = now + 14d` (configurable default).
- [ ] **Task 3: Q&A endpoints (mixed auth)** (AC: 2, 3, 4, 5)
  - [ ] `SessionQnaController`: `GET /api/v1/events/{eventCode}/sessions/{sessionSlug}/qna` → **permitAll** (public read, returns open or frozen thread). `POST .../qna/posts` → **authenticated** (ATTENDEE) — reject if window FROZEN (409). Organizer: `PATCH .../qna` (extend/close-early) and `DELETE .../qna/posts/{id}` (takedown) → `@PreAuthorize("hasRole('ORGANIZER')")`.
  - [ ] Attribute posts via `SecurityContextHelper.getCurrentUsername()`.
- [ ] **Task 4: Scheduled freeze job** (AC: 5, 6)
  - [ ] `@Scheduled` + `@SchedulerLock(name="freezeQnaWindows", lockAtMostFor="5m", lockAtLeastFor="30s")` job: set windows with `closes_at <= now` and `status=open` → `FROZEN`. Model on existing jobs in `EventWorkflowScheduledService`; `ShedLockConfig` already provides the `LockProvider`.
- [ ] **Task 5: Archive rendering** (AC: 3, 5)
  - [ ] Render the frozen thread on the public session archive view. **Confirm whether the session archive page is Tailwind-only (public)** — if so, the Q&A render there is Tailwind-only; the live (open-window) posting UI behind `<MuiLayout>` may use MUI.
- [ ] **Task 6: SecurityConfig (both layers)** (AC: 3)
  - [ ] GET permitAll + POST authenticated + organizer-only PATCH/DELETE in BOTH gateway and event-management-service `SecurityConfig`. Mirror the `SessionMaterialsController` mixed-auth pattern (public GET download + authenticated writes).
- [ ] **Task 7: OpenAPI + tests + doc-drift** (AC: 7)
  - [ ] Spec the endpoints; regenerate/commit types. Integration tests per AC7 with **class-scoped `@MockBean LockProvider`**. Frontend tests use `waitFor()` for MUI `Collapse`/async DOM. Update scheduler/state-machine docs per `.github/doc-drift-mappings.yml`.

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

### Debug Log References

### Completion Notes List

### File List

## Open Questions

1. **Is 14 days the right window, and should it align with auto-archival?** Events auto-archive ~14 days after the event date, which is almost the same as the Q&A window. We should confirm the window length (14 days default, organizer-overridable per the resolved decision) and make sure a frozen thread stays visible on the session page even after the event is archived — archival shouldn't hide the Q&A.
2. **Who can answer — any logged-in attendee, or only attendees of that event + the speaker?** The brainstorming said "speakers/attendees answer." Letting any logged-in attendee answer is simplest and most open; restricting to event registrants + the session's speaker is tighter but adds checks. Recommendation: any logged-in user may post within the window; speakers are just normal posters (their role can be badged). Please confirm.
3. **What's the takedown UX and does a removed post leave a tombstone?** When an organizer removes a post, do we hard-delete it or soft-delete (show "removed by organizer")? Soft-delete (the `removed_at` column) preserves thread coherence and is friendlier in a frozen archive. Confirm soft-delete is acceptable.
4. **Do we notify anyone about Q&A activity?** A speaker might want to know a question was asked, or an asker might want to know it was answered. The MVP assumes no notifications (attendees check back during the 2-week window). Confirm that's acceptable, or whether a minimal "you have a new answer" email is in scope (it would lean on Story 7.3's email machinery).
