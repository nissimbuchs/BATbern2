# Story 15.3: Stable slot assignment — dynamic build, insert/swap, mobile tap-to-place

Status: ready-for-dev

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As an **organizer arranging the agenda**,
I want **to assign speakers to slots by a stable slot identity — inserting between slots (reflowing the times that follow), swapping onto an occupied slot, and placing/swapping by tap on a touch device** —
so that **agenda editing is robust (no fragile HH:MM string matching) and works on a phone, not just by desktop drag**.

This story closes spec items **#7** (stable slot model + insert/swap) and **#6** (mobile tap-to-assign for the speaker pool) from `docs/specs/event-2-feedback-quick-spec.md`. It builds on Story 15.2, which generalized timeline computation.

**Scope boundary (confirmed with PM — read first):** This story is about the **slot-assignment handling and slot computation only**. It does **NOT** change how sessions persist their times: `Session.startTime`/`endTime` remain the stored authority, written exactly as today via the existing timing path. `slotKey` is a **computed, transient** addressing key (stamped by `computeTimeline`, returned on each slot, used by the UI and the assign endpoint) — it is **NOT** persisted on `Session`, there is **no new column/migration for it**, and there is **no legacy reconciliation**. Insert/swap simply recompute and save the affected sessions' start/end times in one operation, the same way a manual time edit does today.

## Acceptance Criteria

1. **AC1 — Insert between slots (desktop drag).** GIVEN a desktop organizer viewing the agenda grid, WHEN they drop a speaker between two slots, THEN the speaker occupies that position and every *following* assigned speaker session shifts down one position; the start/end times of the dropped session and all shifted sessions are recomputed (event start + Σ preceding durations) and saved in one operation.
2. **AC2 — Swap onto occupied (desktop drag).** WHEN the organizer drops a speaker onto an already-occupied speaker slot, THEN the two session assignments swap positions — each takes the other's slot time, persisted atomically; no assignment is silently dropped.
3. **AC3 — Mobile tap (assign + swap).** GIVEN a touch device, WHEN the organizer taps a speaker in the **unassigned pool** then taps an **empty** slot, THEN it is assigned; AND WHEN they tap an **assigned** slot to pick it up then tap another slot, THEN the two swap. No drag gesture required. Desktop keeps HTML5 drag.
4. **AC4 — Insert overflow guard.** WHEN an insert/assign would require a speaker slot beyond the last computed `SPEAKER_SLOT` (agenda full), THEN the operation is rejected with **409** and the UI shows a toast ("Agenda is full — add a slot in Edit event type first"); nothing is partially moved.
5. **AC5 — Stable addressing.** Slots are addressed by a deterministic **`slotKey`** (segment-type + 1-based ordinal among that type, e.g. `SPEAKER_SLOT-3`) computed in the timeline and returned on every `TimetableSlot`. The frontend grid and the assign endpoint use `slotKey`, **not** an HH:MM/`Instant` string match. Session↔slot binding in `getTimetable` continues to work because insert/swap save exact computed times (binding stays time-based; only the *addressing/UI* moves to `slotKey`).
6. **AC6 — Atomic + resilient.** Insert (shift) and swap each persist as **one** server transaction over all affected sessions (no partial reorders visible). The frontend applies an optimistic update and **rolls back** on error, reusing the existing 409-conflict rollback in `useSlotAssignment`.
7. **AC7 — No regression.** Structural rendering (moderation / break / lunch / apéro), auto-assign, bulk-timing, clear-timing, conflict detection, and the public/presenter/live-control agenda surfaces keep working. The **15.2 "assignment may move" warning STAYS** (a config/event-type edit can still re-time the agenda and orphan assignments — that is deliberately out of this story's scope).

## Tasks / Subtasks

- [ ] **Task 1 — Backend: compute a deterministic `slotKey` (transient) in the timeline (AC5)**
  - [ ] In `TimetableService.computeTimeline(...)`, compute a `slotKey` for **every** slot as `"{Type}-{ordinal}"`, ordinal 1-based **per type** in computed order (`MODERATION-1`, optional `APERITIF-1`, `SPEAKER_SLOT-1..N` interleaved with `BREAK-1..`/`LUNCH-1`, `MODERATION-2`, optional `APERITIF-1` at end). Stamp it in the single `addSlot(...)` helper. Keep `slotIndex` (1-based for SPEAKER_SLOT display) as-is.
  - [ ] Add `String slotKey` to `TimetableSlot` (`dto/TimetableSlot.java`, `@Value @Builder`). It is a **computed response field** — no DB persistence, no `Session` change.
  - [ ] Keep the frontend mirror `scheduleTimeline.buildTimeline` time-derivation identical (it does not need `slotKey`; do not let it diverge from backend timing).
- [ ] **Task 2 — Backend: the one assign/insert/swap path (AC1, AC2, AC4, AC6)**
  - [ ] Add a single endpoint: `POST /api/v1/events/{eventCode}/sessions/{sessionSlug}/slot` body `{ targetSlotKey, mode: ASSIGN | INSERT | SWAP }`, in `SlotAssignmentController`, delegating to `SessionTimingService` (or a new sibling `SlotReorderService` under `service/slotassignment/`). `@PreAuthorize("hasRole('ORGANIZER')")` + `@CacheEvict(value = CacheConfig.EVENT_WITH_INCLUDES_CACHE, allEntries = true)` like the existing timing endpoint.
  - [ ] Resolve the current order of assigned speaker sessions from the computed timeline: sort assigned non-structural sessions by `startTime`; they map 1:1 onto `SPEAKER_SLOT-1..k`. (No persisted ordering needed — derive it each request from `computeTimeline` + current `startTime`s.)
    - **ASSIGN** (target empty): set the dropped session's `startTime`/`endTime` to the target slot's computed times.
    - **INSERT** (drop between): dropped session → target ordinal's computed times; every assigned session at ordinal ≥ target → bumped to the next ordinal's computed times. **Reject 409 if the bump would exceed the last computed `SPEAKER_SLOT-N`** (AC4).
    - **SWAP** (target occupied): exchange the dragged session's and the occupant's `startTime`/`endTime`.
  - [ ] Persist **all affected sessions in one transaction**, reusing the existing `SessionTimingService.assignTiming(...)` write semantics for each (clears actual-execution data on time change, writes `SessionTimingHistory`, publishes `SessionTimingAssignedEvent`). Keep conflict detection (`detectRoomOverlap`/`detectSpeakerDoubleBooking`) on the resulting times → 409.
  - [ ] Keep the existing `PATCH …/sessions/{slug}/timing` for direct time edits, unchanged.
- [ ] **Task 3 — Backend: contract-first OpenAPI + regenerate types (AC5, AC6)**
  - [ ] Update `docs/api/*events*.openapi.yml`: add `slotKey` to `TimetableSlot`; add the new slot-assign endpoint + request schema (`mode` enum). Document 400/401/403/404/409.
  - [ ] Regenerate: backend (`./gradlew :services:event-management-service:openApiGenerate…`) and frontend (`cd web-frontend && npm run generate:api-types`). Commit the frontend `src/types/generated/events-api.types.ts` change.
- [ ] **Task 4 — Frontend: extract `useTapToAssign` + drive the pool on touch (AC3)**
  - [ ] Extract the inline tap logic in `DragDropSlotAssignment` (`selectedSessionSlug` state, `handleTraySelect`, `handleSlotTap`, armed-slot highlight) into a new hook `web-frontend/src/hooks/useTapToAssign/useTapToAssign.ts` (+ test). **Extract, do not rebuild.**
  - [ ] Extend the hook to support **tap-to-swap**: an armed slot can be an *assigned* slot (pick it up) or a *pool* speaker; tapping a second slot resolves to ASSIGN (empty target) or SWAP (occupied target).
  - [ ] On touch viewports, drive the **unassigned speaker pool** (`UnassignedSpeakersList`) selection from `useTapToAssign` (today the pool only exposes HTML5 `draggable`, dead on touch — that's #6). Desktop keeps drag.
  - [ ] New mobile hint i18n keys go in the **`events`** namespace (`slotAssignment.*` lives in `public/locales/{locale}/events.json`) — populate **all 10 locales** (de, en, fr, it, rm, es, fi, nl, ja, gsw-BE). EN+DE first-class.
- [ ] **Task 5 — Frontend: address by slotKey + dispatch insert/swap with rollback (AC1, AC2, AC5, AC6)**
  - [ ] Replace the HH:MM identity in `DragDropSlotAssignment` (`toTimeStr`-keyed `TIME_SLOTS`, `structuralSlotsByTime`, and `assignSessionToSlot`'s `toTimeStr(...) === time` match) with `slot.slotKey`. Droppability + assigned-session lookup key off `slotKey`.
  - [ ] `handleDrop` / `handleSlotTap` call the new slot endpoint via `slotAssignmentService` with `{ targetSlotKey, mode }`. Derive `mode`: empty target → `ASSIGN`; drop on the gap between slots → `INSERT`; drop on an occupied slot → `SWAP`.
  - [ ] Reuse `useSlotAssignment`'s optimistic-update + 409-rollback (the `assignTiming` path today): on error roll back and surface the existing conflict toast; on the AC4 "agenda full" 409 show the full-agenda message.
- [ ] **Task 6 — Tests (TDD, all ACs)**
  - [ ] Backend integration (`AbstractIntegrationTest`, Testcontainers PostgreSQL, `@Transactional`): `computeTimeline` stamps correct `slotKey`s; ASSIGN to empty; INSERT shifts following sessions + saves recomputed times; INSERT past last slot → 409; SWAP exchanges times; one-transaction atomicity; conflict detection still fires. Naming `should_…_when_…`.
  - [ ] Frontend unit (Vitest + RTL + MSW): `useTapToAssign` (assign + swap); `DragDropSlotAssignment` dispatches correct `{targetSlotKey, mode}` for drag-between / drag-onto-occupied / tap; optimistic rollback on mocked 409.
  - [ ] Playwright (organizer `chromium`, + touch emulation): drag-insert reflows, drag-swap, tap-to-assign, tap-to-swap. Staging-safe: clean up created assignments; no real outbound comms.

## Dev Notes

### The core change in one sentence
Today the slot-assignment UI identifies slots by an **HH:MM string match** (`toTimeStr(slot.startTime) === time`) and the backend has no slot handle beyond `startTime`. This story adds a deterministic **computed `slotKey`** (segment-type + ordinal) so the UI and a new assign endpoint address slots by a stable key, enabling clean **insert-between (+reflow)**, **swap**, and **mobile tap**. **Session persistence is unchanged** — `startTime`/`endTime` stay the stored authority; insert/swap just recompute and save those times for the affected sessions in one transaction. No `slotKey` column, no migration, no reconciliation.

### What is explicitly OUT of scope (PM-confirmed)
- Persisting `slotKey` on `Session` / inverting `startTime` authority — **no**.
- A V123 (or any) migration for this story — **none needed**.
- Legacy data reconciliation — **none**; existing assignments already carry valid `startTime`s and bind as today.
- Removing the 15.2 "assignment may move" warning — **keep it**; config-edit re-timing can still orphan assignments and that is a separate concern.

### Current state of the files you will touch (read these before coding)

**Backend — `services/event-management-service`:**
- `service/TimetableService.java`
  - `computeTimeline(AgendaConfig, LocalDate)` (≈ 78–196) — single timeline algorithm; slots built via `addSlot(...)` helper (≈ 185–196) stamping `type/startTime/endTime/title/slotIndex`. **Add `slotKey` in `addSlot`.**
  - `getTimetable(eventCode)` (≈ 215–292) builds `Map<Instant, Session>` (`speakerByStart`/`structuralByStart`) keyed on `Session.getStartTime()` and enriches via `byStart.get(slot.getStartTime())` (≈ 234–276). **Leave the time-based binding as-is** (sessions still hold exact computed times) — just carry the new `slotKey` through into the enriched slots so the frontend receives it.
- `dto/TimetableSlot.java` — `@Value @Builder`; `Type` enum = `MODERATION, BREAK, LUNCH, APERITIF, SPEAKER_SLOT`; fields `type, startTime(Instant), endTime(Instant), title, slotIndex, sessionSlug, assignedSessionSlug`. **Add `String slotKey`** (and carry it through the rebuild branches in `getTimetable` that re-`builder()` the slot).
- `service/slotassignment/SessionTimingService.java`
  - `assignTiming(sessionSlug, startTime, endTime, room, changeReason, changedBy)` (≈ 59–115) — the reusable write (sets times, clears actual-execution on change, writes `SessionTimingHistory`, publishes event). **Reuse for each affected session** in the new path.
  - `autoAssignTimings(event, changedBy)` (≈ 205–243) — sequential fill of free SPEAKER_SLOTs; unchanged (already time-based, parity preserved).
- `controller/SlotAssignmentController.java` — `PATCH /{sessionSlug}/timing` (≈ 150–212), request record `TimingAssignmentRequest` (≈ 345–352), conflict 409 via `conflictDetectionService`; also `POST …/auto-assign`, `POST …/bulk-timing`, `DELETE …/timing`. **Add `POST …/{sessionSlug}/slot` here.**
- `domain/Session.java` — `STRUCTURAL_SESSION_TYPES = {moderation, break, lunch, networking, aperitif}` (≈ 56–58); `startTime/endTime` `Instant` columns; `isStructuralSlot()`. **No change to this entity.**
- `service/AgendaConfigResolver.java` — `resolve(Event) → AgendaConfig`; the single override-else-template fallback all `computeTimeline` consumers route through. Do not change.
- Migration head = **V122** — **this story adds no migration.**

**Frontend — `web-frontend/src`:**
- `components/SlotAssignment/DragDropSlotAssignment/DragDropSlotAssignment.tsx`
  - `toTimeStr()` (≈ 94), `TIME_SLOTS` from unique time strings (≈ 196–204), `structuralSlotsByTime` keyed by time string (≈ 207–216) — **re-key by `slotKey`.**
  - `assignSessionToSlot(session, time, room)` (≈ 286–346) matches via `toTimeStr(...) === time` (≈ 303–305) → `assignTiming(...)`. **Drive by `slotKey` + `mode`.**
  - Drag: `handleDragStart` (≈ 264), `handleDragOver` (≈ 271), `handleDrop` (≈ 348). Tap (14.G.3): `selectedSessionSlug` (≈ 148), `handleTraySelect` (≈ 361), `handleSlotTap` (≈ 365), armed guard `isMobile && !assignedSession && !!selectedSessionSlug` (≈ 678). **Extract tap → `useTapToAssign`; extend for tap-to-swap.**
  - 15.2 warning: `hasAssignments` (≈ 135) → `warning={…assignmentWarning…}` to `EventTypeConfigurationForm` (≈ 515–521). **KEEP unchanged.**
  - Pool drag: `UnassignedSpeakersList.tsx` Card `draggable` (≈ 197) — dead on touch (#6); drive via the new hook on touch.
- `hooks/useSlotAssignment/useSlotAssignment.ts` — `assignTiming(sessionSlug, timing)` (≈ 86–119): optimistic remove-from-unassigned (≈ 96), 409-rollback (≈ 108–114). **Reuse this rollback pattern for the slot path.**
- `services/slotAssignmentService/slotAssignmentService.ts` — `assignSessionTiming(...)` → `PATCH …/timing`. **Add the new slot method.**
- `components/organizer/EventTypeConfigurationForm/scheduleTimeline.ts` — `buildTimeline(config)` (preview only). Keep timing identical to backend.
- `types/generated/events-api.types.ts` — `TimetableSlot` (≈ 3586–3627) has `slotIndex`, **no `slotKey`** — added via Task 3 regen.
- **`useTapToAssign` does NOT exist yet** — create it.

### slotKey semantics (be exact)
- `"{Type}-{ordinal}"`, ordinal 1-based **per type** in computed order. Moderation start/end → `MODERATION-1`/`MODERATION-2`. Apéro is start XOR end (15.2) → a single `APERITIF-1`.
- INSERT target = the SPEAKER_SLOT ordinal the dropped session should occupy; sessions at ordinal ≥ target shift +1 (recompute + save their times). SWAP = exchange the two sessions' times. ASSIGN = empty target.
- Current assigned order is derived per-request: sort assigned speaker sessions by `startTime` → maps to `SPEAKER_SLOT-1..k`. All target times come from `computeTimeline`.

### Critical regression guardrails
- Grep for the time-match coupling you are replacing: `toTimeStr`, `getStartTime()` map keys, `startTime ===`. The behavioural binding in `getTimetable` stays time-based; only the *frontend addressing* and the *insert/swap position model* move to `slotKey`.
- Structural session types are enumerated in ~12 places (15.2 lesson), but this story **adds no new type**, so leave them alone.
- Contract-first: change `docs/api/*events*.openapi.yml` BEFORE implementing; regenerate + commit frontend types.
- `@CacheEvict(EVENT_WITH_INCLUDES_CACHE)` on the new endpoint or the agenda read goes stale after insert/swap.
- ADR-003/004: address by `sessionSlug`/`eventCode`; enrich user data via cached HTTP, never cross-service JPQL; no UUIDs in the API.
- This is an organizer surface (behind the MUI boundary) — MUI is fine; do not leak any of it into public agenda components.
- No new migration — do not touch `db/migration/`.

### Testing standards
- TDD red→green→refactor. Backend IT extend `AbstractIntegrationTest` (Testcontainers PostgreSQL, `@Transactional`, real Flyway). Frontend: `screen` queries, `userEvent`, `waitFor`, MSW; test `useTapToAssign` in isolation. Coverage: logic ≥90%, APIs ≥80%; every AC ≥1 test (insert/swap/overflow each their own). Pipe gradle/make output through `tee` with `set -o pipefail`; grep the file.

### Project Structure Notes
- New hook: `web-frontend/src/hooks/useTapToAssign/useTapToAssign.ts` (+ `.test.ts`).
- New endpoint in existing `SlotAssignmentController`; reorder logic in `SessionTimingService` or a new `service/slotassignment/SlotReorderService.java`.
- i18n: `slotAssignment.*` keys live in the **`events`** namespace (`public/locales/{locale}/events.json`).
- No migration files.

### References
- [Source: docs/prd/epic-15-post-event-2-hardening.md#Story 15.3] — scope, ACs, hand-off block.
- [Source: docs/prd/epic-15-post-event-2-hardening.md#Story 15.2 (As-built)] — slotKey-less state, computeTimeline generalization, V122 head, the warning.
- [Source: _bmad-output/implementation-artifacts/15-2-per-event-editable-event-type-config.md] — AgendaConfigResolver, apéro structural type.
- [Source: docs/specs/event-2-feedback-quick-spec.md] — items #6 (mobile tap) + #7 (stable slots).
- [Source: _bmad-output/project-context.md] — ADR-003/004, Flyway never-edit, contract-first, i18n 10-locale, no-MUI-on-public, TDD/Testcontainers.
- Code: `TimetableService.{computeTimeline,getTimetable}`, `dto/TimetableSlot`, `SessionTimingService`, `SlotAssignmentController`, `domain/Session`; `DragDropSlotAssignment.tsx`, `useSlotAssignment.ts`, `slotAssignmentService.ts`, `scheduleTimeline.ts`.

## Decisions (resolved with PM, 2026-06-21)

1. **Insert overflow → reject with 409 + toast.** If the agenda is full and there is no free trailing speaker slot to shift into, the insert is blocked with a clear "Agenda is full — add a slot in Edit event type first" message rather than silently growing `maxSlots`. Adding capacity stays an explicit Edit-event-type action.
2. **Session time persistence is unchanged.** This story only touches slot-assignment handling and slot computation. Sessions continue to store `startTime`/`endTime` as the authority, written exactly as today. `slotKey` is a computed, transient addressing key — never persisted on a session.
3. **No reconciliation.** Because nothing about session persistence changes, there is no legacy-data migration or reconciliation step. Existing assignments keep their stored times and bind as they do today.
4. **Mobile includes tap-to-swap.** On touch, tapping a pool speaker then an empty slot assigns it, and tapping an assigned slot then another slot swaps them — full parity with desktop drag, not just tap-to-empty.
