# Fix Summary — Organizer workflows & lifecycle automation (Epic 5)
**Fixed:** 2026-03-09

## Changes made

- **M1**: Changed "9 States" → "8 States" throughout (header, workflow diagram, table). Removed `AGENDA_FINALIZED` from the state machine diagram (`CREATED → … → AGENDA_PUBLISHED → EVENT_LIVE → EVENT_COMPLETED → ARCHIVED`). Updated story mapping table: Story 5.1a "All 9 states" → "All 8 states"; Story 5.8 event states column no longer lists `AGENDA_FINALIZED`. Updated task template triggers from `AGENDA_FINALIZED` → `AGENDA_PUBLISHED` for Newsletter: Final and Catering templates (since the state no longer exists). Added explanatory note in Story 5.11 Workflow Engine Integration that `AGENDA_FINALIZED` was removed.

- **M2**: State transitions updated — removed `AGENDA_PUBLISHED → AGENDA_FINALIZED: Manual` line; changed `AGENDA_FINALIZED → EVENT_LIVE: Automatic (event day)` to `AGENDA_PUBLISHED → EVENT_LIVE: Automatic (on event day, via scheduler)`.

- **M3**: Speaker workflow diagram redrawn to remove the parallel `slot_assigned` branch. `slot_assigned` removed from Key States list. `confirmed` description updated to: "Auto-confirmed when `QUALITY_REVIEWED` AND `session.startTime` is set (slot assignment sets `session.startTime`, not speaker pool status)." Story 5.5 parallel workflow AC16-17 updated accordingly. Story 5.7 table entry speaker states column changed from `slot_assigned` to `-`. Implementation checklist line updated.

- **M4**: `EVENT_LIVE → EVENT_COMPLETED` changed from "Manual (after event)" to "Automatic — triggered when all completeable sessions (keynote, presentation, workshop, panel_discussion) are ended via the Watch feature. Break, lunch, and networking sessions are excluded."

- **M5**: Story 5.2 AC13 initial speaker status changed from `"OPEN" (not yet contacted)` to `"IDENTIFIED" (added to pool, not yet contacted)`. Speaker Key States list already says `identified` as initial; added clarification `(initial status)`.

- **M6**: All speaker auto-publish "1 month" references changed to "30 days": Story 5.10 user story, Phase 2 acceptance criteria (AC2, AC6), Scheduled Jobs AC14, Scheduled Tasks AC18, Definition of Done, Story 5.12 Template 2 description.

- **N1**: `AGENDA_PUBLISHED → EVENT_LIVE` scheduler note already covered by M2 fix in the state transitions section.

- **N2**: `EVENT_COMPLETED` automatic trigger details with session type specifics covered by M4 fix in the state transitions section.

- **N3**: Added `GET /api/v1/events/current — Two-Phase Selection Algorithm` section between Story 5.10 and Story 5.11 documenting: Phase 1 (nearest upcoming event with `currentPublishedPhase IS NOT NULL`), Phase 2 fallback (most recent `EVENT_COMPLETED` within 14 days), 404 if older than 14 days, and the `currentPublishedPhase = null` exclusion rule.

- **N4 + N5**: Added `Task Lifecycle` note to the Task System (Configurable) section documenting: pending → todo lifecycle on triggerState, idempotent creation, and that completion records `completedByUsername`, `completedDate`, and optional notes.

- **N6**: Added to Story 5.8 Overflow Detection AC1: "The overflow check (`checkForOverflow`) counts both `ACCEPTED` and `CONFIRMED` speakers together against the event's maximum slot count."

- **N7**: Added DECLINED note to Speaker Workflow Key States: "A speaker can transition to `DECLINED` from any non-terminal state (including `CONFIRMED`). Once `DECLINED`, no further transitions are possible."

## Skipped — needs manual decision

- **U1**: `INVITED` state present in `StatusTransitionValidatorTest` but not documented — the doc explains when `IDENTIFIED` is used (initial pool state) but not when/how a speaker transitions to `INVITED` vs `IDENTIFIED` in the organizer workflow path. No test for `IDENTIFIED → INVITED` in organizer context. Needs a test or explicit documentation of the `INVITED` state's role in the organizer-driven flow.

- **U2**: Story 5.7 AC threshold validation — `SPEAKER_IDENTIFICATION → SLOT_ASSIGNMENT` threshold test only checks "at least 1 ACCEPTED" speaker, not the per-event-type minimums (full-day: 6, afternoon: 6, evening: 3) documented in Story 5.7 AC1. Needs end-to-end test for the per-type minimums, or doc should be downgraded to reflect the weaker validation.

- **U3**: `WITHDREW → ACCEPTED` re-acceptance path — the implementation allows this (`SpeakerWorkflowServiceIntegrationTest#should_allowReAcceptanceFromWithdrew`) but the doc does not mention it. Low risk but could be added to the speaker workflow Key States as a recovery path note.

- **U4**: Story 5.14 catering task due date = "1 month before event" — no scheduler test asserts the 30-day offset for the catering template specifically. Default templates are seeded in DB migration; their due dates are not asserted in tests. "1 month" wording retained in Story 5.14 as the user-facing description; actual offset to be verified against DB migration or confirmed with a dedicated test.
