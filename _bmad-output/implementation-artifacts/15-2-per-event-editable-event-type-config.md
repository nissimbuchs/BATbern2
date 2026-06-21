# Story 15.2: Per-event editable event-type config + dynamic agenda

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As an **organizer planning a specific event**,
I want **to edit a per-event copy of the event-type agenda config (slot/break/lunch/moderation durations, plus apéro-at-end and a second afternoon break) without mutating the shared template**,
so that **I can tailor one event's structure (e.g. an afternoon event with an apéro at the end and two breaks) while every other event and the global template stay exactly as they were**.

**Item #3** from the Event #2 feedback (`docs/specs/event-2-feedback-quick-spec.md`). Decision (Nissim, Q2): keep **exactly the same knobs** as today's preset event types, add an **"Edit event type" action inside slot assignment** that edits a **per-event copy** (copy-on-edit) — never the shared template — and extend the knob set to cover apéro-at-end + 2 afternoon breaks.

**Scope boundary (read first).** The Event #2 spec folds #3 + #7 + #6 into one design; the **epic splits them**:
- **THIS story (15.2)** = the **data model + computation + the "Edit event type" dialog**: new per-event `event_agenda_config` table (copy-on-edit), a resolver (per-event copy if present, else shared template), a generalized `TimetableService.computeTimeline()` that folds an ordered segment list (incl. apéro + count-driven breaks), new GET/PUT agenda-config endpoints, and the frontend dialog that writes the per-event copy.
- **15.3 (separate, depends on this)** = stable `slotKey` addressing, insert-between / swap-on-occupied, and mobile tap-to-assign. **Do NOT implement slotKey addressing or insert/swap here.**

**Why now:** part of the post-Event-#2 hardening so the next live event can carry a non-standard structure (apéro at end / two breaks) without an organizer editing the global template that every event shares.

## Acceptance Criteria

1. **AC1 — algorithm parity guard (apéro OFF reproduces today exactly).** GIVEN any config with `aperitif_slots = 0` and the existing break counts, WHEN `computeTimeline` runs, THEN the output is **byte-identical** to today's hardcoded algorithm. Concretely: a **full_day** event with no override is byte-identical to today (full_day template stays apéro-OFF). The existing full_day `TimetableServiceTest` assertions pass **unchanged**; afternoon/evening assertions are updated to include the new default apéro (see AC8) — that is the only intended change to the existing test expectations.
8. **AC8 — afternoon/evening default to a trailing apéro.** GIVEN an **afternoon** or **evening** event with no per-event override, WHEN the timetable computes, THEN it now includes one APERITIF segment (`aperitif_slots=1`, `aperitif_duration=90`, `aperitif_position=end`) as the final segment **after moderation-end**. Because apéro is appended after moderation-end, **no slot before moderation-end shifts** (speaker-slot start times and any existing assignments are unaffected). **full_day** stays apéro-OFF. Regenerating structural sessions for such an event persists an `aperitif` Session (per AC5).
2. **AC2 — copy-on-edit creates the per-event row, template untouched.** GIVEN an organizer opens "Edit event type" and saves, WHEN the first save happens, THEN an `event_agenda_config` row for that event is created seeded from the resolved template values and then updated with the edits; the shared `event_types` template row is **never** mutated by this flow; a second event's resolution is unaffected.
3. **AC3 — apéro-at-end + 2 breaks compute correctly.** GIVEN an afternoon event whose per-event config sets `aperitif_slots ≥ 1` (`aperitif_position = end`) and a break count of 2, WHEN the timetable computes, THEN the ordered timeline contains both breaks in the speaker block(s) and an APERITIF segment as the **final** segment of the day, **after moderation-end** (i.e. `… → moderation_end → aperitif`), with each slot's start = `event start + Σ preceding segment durations`.
4. **AC4 — config isolation (schema-fitness).** Editing one event's `event_agenda_config` never changes another event's resolved config, the shared template, or any persisted structural session of a different event. A test asserts there is exactly **one** `event_agenda_config` row per event (unique `event_id`) and that resolution for event B is identical before/after editing event A.
5. **AC5 — apéro is a first-class structural slot end-to-end.** WHEN an event with apéro generates structural sessions, THEN an `APERITIF` `TimetableSlot` is produced and persisted as a `Session` with `session_type = 'aperitif'` (the `sessions_session_type_check` constraint admits it and `Session.isStructural()` returns true for it); a free speaker slot is never persisted (parity with today — only non-speaker slots persist).
6. **AC6 — GET/PUT agenda-config endpoints.** `GET /events/{eventCode}/agenda-config` returns the **resolved effective config** (per-event copy if present, else template, with a flag indicating which) for an ORGANIZER; `PUT /events/{eventCode}/agenda-config` upserts the per-event copy (copy-on-edit) for an ORGANIZER and returns the saved config. Validation errors return 400 (via the explicit `MethodArgumentNotValidException` handler), unknown event 404, unauth 401/403.
7. **AC7 — "Edit event type" dialog (frontend).** GIVEN the organizer is on the slot-assignment surface (Speakers & Agenda tab), WHEN they click "Edit event type" in the action toolbar, THEN a dialog opens pre-filled with the resolved config (incl. the new apéro + break-count knobs), saving calls `PUT …/agenda-config` and the timetable re-renders from the new config. New UI strings exist in **all 10 locales**.

## Tasks / Subtasks

> **Phasing (each phase independently prod-deployable; staging IS prod).**
> **P1** = backend: per-event table + resolver (template-fallback) + generalized `computeTimeline` (apéro + count-driven breaks) + GET/PUT endpoints + apéro structural-session plumbing. **full_day with no override stays byte-identical to today (AC1).** **afternoon/evening now gain a default trailing 90-min apéro (AC8)** — an intended change, but apéro is appended after moderation-end so nothing before it shifts (no speaker-slot/assignment desync). Algorithm is parity-safe (apéro OFF ⇒ legacy output).
> **P2** = frontend: "Edit event type" dialog writing the per-event copy + apéro/break knobs; verify on `beta.batbern.ch`.

### P1 — Backend: data model + resolver (additive, inert until first edit) (AC: 1,2,4,5)

- [x] **Verify migration head** before writing any migration: `ls services/event-management-service/src/main/resources/db/migration/ | sort -V | tail` — head is **V120** at authoring time; use the next free `V121…`. NEVER edit an applied migration (staging = prod).
- [x] **Migration — per-event config table** (`V121__create_event_agenda_config.sql`, additive): `event_agenda_config` with `id UUID PK DEFAULT gen_random_uuid()`, `event_id UUID NOT NULL UNIQUE REFERENCES events(id) ON DELETE CASCADE` (within-service UUID FK — same pattern as `V111__create_organizer_thanks.sql:22`), **all knob columns mirroring the CURRENT `EventTypeConfiguration` entity** (NOT just the V10 set — duration columns were added in `V58__add_structural_timing_fields.sql`): `min_slots, max_slots, slot_duration, theoretical_slots_am, break_slots, lunch_slots, default_capacity, moderation_start_duration, moderation_end_duration, break_duration, lunch_duration, typical_start_time, typical_end_time`, plus the **new knobs** `aperitif_slots INTEGER NOT NULL DEFAULT 0`, `aperitif_duration INTEGER NOT NULL DEFAULT 60`, `aperitif_position VARCHAR(10) NOT NULL DEFAULT 'end' CHECK (aperitif_position IN ('start','end'))`, + `created_at/updated_at TIMESTAMPTZ`.
- [x] **Migration — extend the shared template too** (same or next migration): add `aperitif_slots/aperitif_duration/aperitif_position` columns to `event_types`, then **reseed the three template rows with these intended defaults (explicit `UPDATE` per type)**:
  - `full_day`  → `aperitif_slots=0` (OFF), `aperitif_duration=90`, `aperitif_position='end'`
  - `afternoon` → `aperitif_slots=1` (ON), `aperitif_duration=90`, `aperitif_position='end'`
  - `evening`   → `aperitif_slots=1` (ON), `aperitif_duration=90`, `aperitif_position='end'`
  Column DEFAULT for `aperitif_slots` may be `0`, but the seed `UPDATE` sets the real per-type values. ⚠️ This is an **intended behaviour change** for afternoon/evening (AC8) — they gain a trailing 90-min apéro. [Source: spec §3 point 2 + Nissim 2026-06-21.]
- [x] **Migration — widen `sessions_session_type_check`** to admit `'aperitif'`: copy the `DROP CONSTRAINT IF EXISTS sessions_session_type_check; ADD CONSTRAINT … CHECK (session_type IN (…, 'aperitif'))` pattern from `V59__add_moderation_session_type.sql` (preserve every existing value; append `'aperitif'`).
- [x] **`EventTypeConfiguration` entity** (`entity/EventTypeConfiguration.java`): add `aperitifSlots`, `aperitifDuration`, `aperitifPosition` fields. Per-type values come from the migration seed (afternoon/evening ON @90min/end, full_day OFF); entity/builder defaults can be `aperitifSlots=0, aperitifDuration=90, aperitifPosition="end"`.
- [x] **New `EventAgendaConfig` entity** (`entity/` or `domain/`) mirroring all `EventTypeConfiguration` knob fields + the 3 apéro fields + `eventId` (UUID, unique). UUID PK. Add `EventAgendaConfigRepository extends JpaRepository<…, UUID>` with `Optional<EventAgendaConfig> findByEventId(UUID eventId)`.
- [x] **Effective-config abstraction** to decouple `computeTimeline` from the source: introduce a common read interface (e.g. `AgendaConfig` with getters for every knob incl. apéro) implemented by BOTH `EventTypeConfiguration` and `EventAgendaConfig`, OR a resolved value record. Change `TimetableService.computeTimeline(AgendaConfig, LocalDate)` to take it. (This ripples to `StructuralSessionService:94` and `SessionTimingService:216` and `TimetableServiceTest` — expected; keep parity.)
- [x] **Resolver**: a method (e.g. `AgendaConfigResolver.resolve(Event)` or on `TimetableService`/a new service) returning the per-event `EventAgendaConfig` if a row exists, else the shared `EventTypeConfiguration` for `event.getEventType()`. **All three computeTimeline callers route through the resolver** so the override takes effect everywhere (read, structural-session generation, auto-assign).
- [x] **Generalize `computeTimeline()`** (`service/TimetableService.java:78-213`) to fold an **ordered structural-segment list derived from the knobs**: `[moderation_start, aperitif? (position=start), (AM speaker slots), break×n distributed, lunch?, (PM speaker slots), break×n, moderation_end, aperitif? (position=end)]`. **Apéro-at-end is the FINAL segment, AFTER moderation-end** (`… → moderation_end → aperitif`); apéro-at-start sits right after moderation-start. Each slot start = `event start + Σ preceding durations`. **Make break placement count-driven** (honour the break count across the speaker block(s)) instead of the current hardcoded single-break-after-half. **Break distribution rule (RESOLVED): even split** — distribute N breaks evenly across the speaker slots of the block (e.g. 2 breaks → after ~1/3 and ~2/3 of the slots). **PARITY IS NON-NEGOTIABLE (AC1):** feeding today's template values (apéro OFF, the existing break counts) MUST reproduce today's exact timeline — the existing `TimetableServiceTest` is the oracle and must pass unchanged. (Verify the even-split formula degenerates to today's single-mid-break output for the existing 1-break / AM+PM 2-break cases; if it can't perfectly reproduce a parity case, special-case the legacy path so AC1 holds.)
- [x] **Apéro structural plumbing**: add `APERITIF` to `dto/TimetableSlot.Type`; add `"aperitif"` to `StructuralSessionService.STRUCTURAL_TYPES` (line 43) and a `case APERITIF -> "aperitif"` in `toSessionService.toSessionType` (line 163); add `"aperitif"` to `Session.STRUCTURAL_SESSION_TYPES` (`domain/Session.java`, used by `isStructural()` ~line 43-66). Apéro gets no moderator (only `MODERATION` does).
- [x] **Tests (Testcontainers PostgreSQL, extend `AbstractIntegrationTest`, `@Transactional`)**:
  - [x] **Parity unit tests**: `TimetableServiceTest` (pure `computeTimeline`, `@ExtendWith(MockitoExtension)`) — the **full_day** case passes unchanged; **afternoon/evening cases are updated to include the new trailing 90-min apéro (AC8)**; add a dedicated apéro-OFF case asserting byte-identical legacy output (the parity guarantee), plus count-driven 2-breaks + apéro-at-end + apéro-at-start derived start-time cases.
  - [x] **Resolver IT**: no row → template used (identical output); row present → per-event copy used.
  - [x] **Isolation IT (AC4)**: editing event A's config leaves event B's resolution and the template byte-identical; unique-`event_id` enforced.
  - [x] **Structural-session IT (AC5)**: event with apéro → an `aperitif` Session persists (CHECK admits it, `isStructural()` true); free speaker slots not persisted.

### P1 — Backend: API contract (contract-first, ADR-006) (AC: 6)

- [x] **OpenAPI** (`docs/api/events-api.openapi.yml`): add `GET /events/{eventCode}/agenda-config` (200 → resolved config + a `source: TEMPLATE|EVENT_OVERRIDE` discriminator field) and `PUT /events/{eventCode}/agenda-config` (request body → 200 saved config; 400/401/403/404 documented). Add schemas `EventAgendaConfigResponse` (the existing `EventSlotConfigurationResponse` knobs + the 3 apéro fields + `source`) and `UpdateEventAgendaConfigRequest`. Place near the existing `/events/{eventCode}/timetable` path (~line 2459) and `EventSlotConfigurationResponse` schema (~line 9358).
- [x] **Controller**: implement the endpoints. **Decide the codegen path consistently** — the timetable controller (`TimetableController`) is **hand-written** (does not implement a generated `*Api`), the event-type controller (`EventTypeController`) **implements** the generated `EventTypesApi`. Follow the surrounding convention for whichever controller you extend; if hand-written, mirror `TimetableController`. (Story 15.1 set the precedent that hand-written is acceptable where the neighbouring code is hand-written.)
- [x] **`GlobalExceptionHandler`** must have the explicit `@ExceptionHandler(MethodArgumentNotValidException.class)` so the apéro/break-count validation returns 400 not 500 (project-context gotcha) — verify it already exists; do not regress it.
- [x] **Security**: `GET`/`PUT …/agenda-config` require `ORGANIZER` in BOTH the EMS `SecurityConfig` and the api-gateway `SecurityConfig` (these are organizer-only — NOT public, unlike 15.1's live-timing GET).
- [x] **Regenerate** backend (`./gradlew :services:event-management-service:openApiGenerate`) if implementing via a generated interface; always run frontend type-gen (next phase). Commit generated frontend types.
- [ ] **Bruno** contract tests — **DEFERRED (deliberate).** A Bruno `PUT …/agenda-config` would create an `event_agenda_config` override row on a **prod** event with **no delete endpoint** to clean it up → violates the staging-IS-prod "no test data left behind" rule. GET/PUT are fully covered by `AgendaConfigControllerIntegrationTest` (Testcontainers: GET template/override, PUT upsert, 400/404/403, isolation). Revisit if a delete/reset-config endpoint is added.

### P2 — Frontend: "Edit event type" dialog (AC: 7)

- [x] **Regenerate types**: `cd web-frontend && npm run generate:api-types` → `EventAgendaConfigResponse`/`UpdateEventAgendaConfigRequest` land in committed `src/types/generated/events-api.types.ts`.
- [x] **Service**: add `getAgendaConfig(eventCode)` + `updateAgendaConfig(eventCode, req)` to a frontend service (extend `timetableService` or a new `agendaConfigService`; do NOT reuse `eventTypeService` — that targets the GLOBAL template, not the per-event copy).
- [x] **Dialog**: add an "Edit event type" button to the slot-assignment action toolbar (`components/SlotAssignment/DragDropSlotAssignment/DragDropSlotAssignment.tsx`, the `role="toolbar"` Paper at ~lines 407-475, next to Generate/Auto-Assign/Clear All). Open an MUI `Dialog` following the existing pattern in `components/organizer/Admin/EventTypesTab.tsx:109-129` wrapping a react-hook-form + zod form modelled on `components/organizer/EventTypeConfigurationForm/EventTypeConfigurationForm.tsx` — but extended with **all three apéro knobs (slots/count, duration, position)** and **break-count**, and pointed at the per-event endpoints. On save, invalidate the timetable query so the grid re-renders.
- [x] **Assignment-desync warning (RESOLVED: warn, don't block)**: if the event already has assigned speaker sessions (any timetable `SPEAKER_SLOT` with `assignedSessionSlug`), show a non-blocking warning in the dialog before save ("N speakers are assigned at fixed times and may need re-placing after this change") and still allow saving. No re-timing here (15.3). Add the warning string to all 10 locales.
- [x] **i18n**: add keys (e.g. `slotAssignment.actions.editEventType`, dialog title/labels for apéro/break-count) to the `events` namespace in **all 10 locales** (`de, en, fr, it, rm, es, fi, nl, ja, gsw-BE`) — EN+DE first-class, others may be straight translations. SlotAssignment uses `useTranslation('events')`.
- [x] **Tests** (Vitest + RTL, `msw` for HTTP): dialog opens pre-filled from `GET …/agenda-config`, save posts the per-event payload, timetable invalidates. Confirm SlotAssignment stays an MUI organizer surface (not a public Tailwind page).
- [ ] **Verify on beta** — PENDING (manual, touches prod CloudFront — not run autonomously): publish to `beta.batbern.ch`, open an event's Speakers & Agenda tab, edit the event type (set apéro-at-end + 2 breaks), confirm the timetable re-renders correctly and other events are unaffected. Local type-check + Vitest cover the dialog; beta click-through is the pre-promote gate.

## Dev Notes

### Chosen approach (and why)

- **Copy-on-edit, resolver-fronted (the whole point of AC1/AC2).** A new `event_agenda_config` row exists ONLY after the first edit. Until then the resolver returns the shared `EventTypeConfiguration` template, so the generalized algorithm — fed identical values — must produce identical output. This makes 15.2 ship dark (zero visible change) and independently deployable.
- **One algorithm, three consumers.** `computeTimeline()` is the single source of truth feeding (1) `TimetableService.getTimetable` (read), (2) `StructuralSessionService.generateStructuralSessions` (persists structural DB sessions, `:94`), (3) `SessionTimingService` auto-assign (`:216`). Generalizing it (apéro + count-driven breaks) and routing all three through the resolver is what makes the override take effect everywhere. Do not special-case one caller.
- **Effective-config interface** (`AgendaConfig`) decouples `computeTimeline` from whether the knobs came from the template entity or the per-event copy — both implement it; the resolver returns it. Lowest-churn way to add the override without forking the algorithm.
- **Apéro is a structural slot, not a speaker slot.** It persists as a `Session` (`session_type='aperitif'`) exactly like break/lunch/moderation; only speaker slots stay virtual/droppable. Hence the `TimetableSlot.Type.APERITIF` + `STRUCTURAL_TYPES` + `toSessionType` + `Session.STRUCTURAL_SESSION_TYPES` + CHECK-constraint changes all move together.

### ⚠️ Critical integration risk — assignment desync (the 15.2 ↔ 15.3 seam)

Today, a session is "assigned to a slot" purely by its **`Session.startTime` Instant matching a computed `SPEAKER_SLOT.startTime`** — there is **no slot table and no slot key** (15.3 introduces `slotKey`). So if an organizer edits the per-event config in a way that **shifts slot start times** (different durations, an inserted apéro/second break) on an event that **already has assigned speaker sessions**, those sessions' stored `startTime`s will **no longer match any computed slot** → they silently fall into `unassignedSessions`. This is exactly why the epic says **"15.2 depends on / pairs with 15.3"**.

**Mitigation in 15.2 (RESOLVED — warn, don't block):** the "Edit event type" dialog must **detect existing speaker-slot assignments and warn** before saving a structure-changing edit ("N speakers are assigned at fixed times and may need re-placing after this change") — but it still lets the organizer save. Do NOT silently re-time sessions here, and do NOT hard-block (that reflow is 15.3's `slotKey` job). Editing config on an event with **no** assignments yet (the normal flow — shape the agenda, then assign) is fully safe and needs no warning.

### Current-state facts pinned during analysis (cite when implementing)

**Data model:**
- `EventTypeConfiguration` entity → table **`event_types`** (NOT `event_type_configuration`); keyed by `EventType` enum (`FULL_DAY/AFTERNOON/EVENING` ↔ `full_day/afternoon/evening` via `EventTypeConverter`). Repo `EventTypeRepository.findByType`. Cached 1h in `EventTypeService`. [Source: entity/EventTypeConfiguration.java; migration V10.]
- **Knob columns are split across migrations:** V10 created `min/max_slots, slot_duration, theoretical_slots_am, break_slots, lunch_slots, default_capacity, typical_start/end_time`; **V58 (`V58__add_structural_timing_fields.sql`) added `moderation_start_duration, moderation_end_duration, break_duration, lunch_duration`.** Mirror the **entity** (all 14 knobs), not the V10 schema. [Source: migration V10, V58, entity/EventTypeConfiguration.java.]
- Template seed defaults — full_day: 6/8/45, AM=true, break_slots=2, lunch_slots=1, cap=300, 09:00–16:00. afternoon: 6/8/45, AM=false, break_slots=1, lunch_slots=0, cap=200, 13:00–19:00. evening: 3/4/45, AM=false, break_slots=1, lunch_slots=0, cap=200, 18:00–19:00. Durations (from V58): moderation 5/5, break 20, lunch 60. [Source: V10:32-36, V58.]
- `Event.eventType` is the only event→type link (`@Convert EventTypeConverter`, `event_type VARCHAR(20)`, `domain/Event.java:137-140`); **no per-event override columns exist today** (this story adds the first). [Source: domain/Event.java, V11.]

**Computation:**
- `TimetableService.computeTimeline(EventTypeConfiguration, LocalDate)` (`service/TimetableService.java:78-213`) — pure function, no DB, anchored to `Europe/Zurich`. Current hardcoded order: moderation-start → (AM/PM split if `theoreticalSlotsAM && lunchSlots>0`: AM speaker slots with one mid-break, lunch, PM speaker slots with one mid-break; else linear with one mid-break) → moderation-end. SPEAKER_SLOT gets a 1-based global `slotIndex`; structural slots get a `title`. Start times via a `ZonedDateTime` cursor. [Source: service/TimetableService.java.]
- `getTimetable(eventCode)` (`:233`) enriches virtual slots by matching `Session.startTime` → `SPEAKER_SLOT.startTime`, fills `assignedSessionSlug`; unassigned = sessions with `startTime == null`. [Source: TimetableService.java:233-307.]
- `dto/TimetableSlot`: `Type {MODERATION, BREAK, LUNCH, SPEAKER_SLOT}`, `startTime/endTime` (Instant UTC), `title`, `slotIndex`, `sessionSlug`, `assignedSessionSlug`. **No slotKey today.** [Source: dto/TimetableSlot.java.]
- `StructuralSessionService.generateStructuralSessions` persists every non-SPEAKER_SLOT timeline slot as a `Session` (`STRUCTURAL_TYPES = ["moderation","break","lunch"]`, `toSessionType` switch, moderator added only for MODERATION). [Source: service/StructuralSessionService.java:43,94,100-113,163-171.]
- `sessions.session_type` has a CHECK constraint (created V2:52, widened V59 to add `'moderation'`) — add `'aperitif'` with the same DROP/ADD pattern. `Session.isStructural()` checks `STRUCTURAL_SESSION_TYPES` (`domain/Session.java:~43-66`). [Source: V2, V59, domain/Session.java.]

**API / codegen:**
- OpenAPI events spec: `/events/{eventCode}/timetable` GET (~l.2459), `TimetableSlot` schema (~l.7547), `TimetableResponse` (~l.7600), `EventType` enum (~l.9347), `EventSlotConfigurationResponse` (~l.9358), existing `/events/types` + `/events/types/{type}` GET/PUT (~l.4211-4330). [Source: docs/api/events-api.openapi.yml.]
- Backend gen: `openApiGenerate` (spring, interfaceOnly) → `ch.batbern.events.api.generated` (`build.gradle:100-147`). `EventTypeController implements EventTypesApi`; `TimetableController` + `LiveTimingController` are **hand-written** (no generated interface). [Source: build.gradle, controllers.]
- Frontend gen: `npm run generate:api-types:events` (openapi-typescript) → `src/types/generated/events-api.types.ts` (committed). [Source: web-frontend/package.json:38-49.]

**Frontend:**
- `DragDropSlotAssignment.tsx` — slots come entirely from `useTimetable(eventCode)` → `timetableService.getTimetable` → `GET …/timetable`; slots currently keyed by `toTimeStr(startTime)` "HH:MM" (the desync hazard). Action toolbar at ~l.407-475 (`role="toolbar"`). `useTranslation('events')`. [Source: components/SlotAssignment/DragDropSlotAssignment/DragDropSlotAssignment.tsx.]
- Reuse patterns: dialog+form `components/organizer/Admin/EventTypesTab.tsx:109-129` + `EventTypeConfigurationForm.tsx` (react-hook-form + zod). `eventTypeService.ts` updates the GLOBAL template (`PUT /events/types/{type}`) — **don't reuse it for per-event**. [Source: those files.]
- SlotAssignment is a protected **MUI** organizer route (not a public Tailwind page). [Source: App.tsx organizer routes.]

### Project Structure Notes

- Backend layered arch: hand-written controller (mirror `TimetableController`/`LiveTimingController`) → service → repository → entity. ADR-003: public path uses `eventCode`, never UUID. ADR-004: enrich user data via `UserApiClient`, no cross-service JPQL (not relevant here — agenda config is intra-service).
- Within-service UUID FK to `events(id)` is correct (same service) — `ON DELETE CASCADE`, unique `event_id` (one row per event). [Pattern: V111__create_organizer_thanks.sql:22.]
- Flyway: head **V120**; verify before writing; NEVER edit an applied migration; exclude `**/db/migration/**` from any repo-wide find/replace.
- i18n: new UI keys in all 10 locales (events namespace). Email templates: N/A (no email in this story).
- Doc-drift (`.github/doc-drift-mappings.yml`): changes to `services/event-management-service/`, `docs/api/`, and `db/migration/` map to `docs/architecture/06-backend-architecture.md`, `docs/architecture/03-data-architecture.md` (EventType/EventSlotConfiguration model — update to add `event_agenda_config` + apéro knobs), and `docs/user-guide/entity-management/events.md`. Update in the same commit or tag `[no-doc]` if genuinely none.

### Testing standards summary

- TDD red-green-refactor. Integration tests extend `AbstractIntegrationTest` (`shared-kernel/src/testFixtures/java/ch/batbern/shared/test/AbstractIntegrationTest.java`, Testcontainers PostgreSQL 16 — never H2), `@Transactional`. Unit tests for the pure algorithm use `@ExtendWith(MockitoExtension.class)` (see `TimetableServiceTest`). Names: `should_<behavior>_when_<condition>`.
- **AC1 parity is the headline test**: existing `TimetableServiceTest` cases pass unchanged after generalization (the regression oracle).
- Coverage: unit ≥90% business logic, integration ≥80% APIs.
- Frontend: Vitest + RTL + `msw`. Bruno: GET/PUT under events collection, `docs{}`-only prose, cleanup the created row (staging IS prod).
- Pipe gradle/make through `tee /tmp/<name>.log` with `set -o pipefail`; grep the file. Pre-push runs EMS unit tests only (`-PskipIntegration`); CI runs the full suite.

### References

- [Source: docs/prd/epic-15-post-event-2-hardening.md#Story 15.2] — scope, ACs, "depends on/pairs with 15.3", phasing.
- [Source: docs/specs/event-2-feedback-quick-spec.md#3 + 7] — item #3 decision (copy-on-edit, same knobs + apéro/2-breaks), current-state pointers, P1/P2/P3 phasing.
- [Source: services/event-management-service/src/main/java/ch/batbern/events/service/TimetableService.java#L78-L213] — `computeTimeline` to generalize (parity oracle).
- [Source: services/event-management-service/src/main/java/ch/batbern/events/service/StructuralSessionService.java#L43-L171] — structural-session persistence; APERITIF plumbing.
- [Source: services/event-management-service/src/main/java/ch/batbern/events/service/slotassignment/SessionTimingService.java#L216] — auto-assign consumer of `getTimetable`.
- [Source: services/event-management-service/src/main/java/ch/batbern/events/entity/EventTypeConfiguration.java] + [migration V10] + [V58__add_structural_timing_fields.sql] — knob set to mirror.
- [Source: services/event-management-service/src/main/resources/db/migration/V59__add_moderation_session_type.sql] — CHECK-widen pattern for `'aperitif'`.
- [Source: services/event-management-service/src/main/resources/db/migration/V111__create_organizer_thanks.sql#L22] — within-service UUID FK + unique-index pattern.
- [Source: services/event-management-service/src/main/java/ch/batbern/events/domain/Session.java] — `isStructural()` / `STRUCTURAL_SESSION_TYPES`.
- [Source: docs/api/events-api.openapi.yml#L2459,#L7547,#L9358] — where timetable/config schemas + new agenda-config endpoints go.
- [Source: web-frontend/src/components/SlotAssignment/DragDropSlotAssignment/DragDropSlotAssignment.tsx#L407-L475] — toolbar for "Edit event type".
- [Source: web-frontend/src/components/organizer/Admin/EventTypesTab.tsx#L109-L129] + [EventTypeConfigurationForm.tsx] — dialog+form pattern to clone.
- [Source: _bmad-output/implementation-artifacts/15-1-websockets-to-rest-polling-live-agenda.md] — sibling-story conventions (hand-written controller precedent, additive-then-cutover, beta verification).
- [Source: _bmad-output/project-context.md] — ADR-003/004/006, layered arch, Flyway never-edit-applied, Testcontainers, i18n 10-locale, MethodArgumentNotValidException handler.

## Resolved Decisions

- **Copy-on-edit, never mutate the shared template.** First edit seeds a per-event `event_agenda_config` row from the resolved template; thereafter the event reads its own copy. (Nissim, Event #2 feedback Q2.)
- **Same knobs as today + 3 new ones** (`aperitif_slots`, `aperitif_duration`, `aperitif_position` default `end`) + count-driven break placement. Simple knobs, NOT a freeform ordered segment list. (Nissim Q2.)
- **Apéro/break-count knobs added to BOTH the shared template and the per-event copy.** (spec §3 point 2, option A.)
- **(Resolved 2026-06-21, Nissim) Template apéro defaults: afternoon + evening = ON, 90 min, position `end`; full_day = OFF.** This is an intended behaviour change (AC8): afternoon/evening events with no override now show a trailing 90-min apéro after moderation-end. The algorithm stays parity-safe (apéro OFF ⇒ legacy output), and because apéro is the last segment nothing before moderation-end shifts — so existing speaker assignments are NOT affected.
- **15.2 = data model + computation + dialog; 15.3 = slotKey addressing + insert/swap + mobile tap.** Do not pull 15.3 work into 15.2. (epic split.)
- **Generalize the single `computeTimeline` and route all three consumers through the resolver** rather than branching per consumer — guarantees the override applies to read, structural-session generation, and auto-assign uniformly. (architecture analysis.)
- **(Resolved 2026-06-21, Nissim) Ship the dialog in 15.2 — warn, don't block.** The "Edit event type" dialog ships now; when the event already has assigned speakers it shows a non-blocking warning before save but still allows it. No re-timing/reflow here — 15.3's `slotKey` removes the desync hazard. (Not gated, not hard-blocked.)
- **(Resolved 2026-06-21, Nissim) Break placement = even split.** N breaks distribute evenly across the speaker block(s) (2 breaks → ~1/3 and ~2/3). Existing event types stay parity-locked; the even-split must reproduce today's output for the legacy cases (special-case if needed).
- **(Resolved 2026-06-21, Nissim) Dialog exposes all three apéro knobs** — slots/count, duration, and position (start/end) — all editable per event.

## Open Questions

_All open questions resolved 2026-06-21 (Nissim) — see the last three entries under Resolved Decisions._

## Review Findings (code review 2026-06-21, 3-layer adversarial)

### Decision needed
- [x] [Review][Decision] **i18n: 7 locales carried English placeholders** — RESOLVED 2026-06-21 (Nissim): **hand-translated all 7** (`fr/it/es/nl/fi/rm/ja`) — `slotAssignment.editEventType.*` + `actions.editEventType` + the `aperitif` slot label. All 10 locales now properly populated (de/en/gsw-BE were already first-class).

### Patch (all fixed 2026-06-21)
- [x] [Review][Patch] **`aperitif` added to auto-assign structural set** — `SessionTimingService.java:46`. (HIGH)
- [x] [Review][Patch] **`aperitif` added to newsletter structural set** — `NewsletterEmailService.java:103`. (MED)
- [x] [Review][Patch] **`aperitif` added to cockpit speaker-count filters** — `cockpit/MetricTiles.tsx:54` + `cockpit/useCockpitCards.ts:56`. (MED)
- [x] [Review][Patch] **Malformed time now → 400** — `AgendaConfigService.parseTime` wraps `DateTimeParseException` → `IllegalArgumentException`; IT `should_return400_when_malformedTime`. (MED)
- [x] [Review][Patch] **maxSlots<minSlots now → 400** — `AgendaConfigService.upsert` validates the cross-field rule → `IllegalArgumentException`; IT `should_return400_when_maxSlotsLessThanMinSlots`. (MED)

### Deferred
- [x] [Review][Defer] **Even-split silently emits fewer breaks than requested** when `breakSlots ≥ maxSlots` or positions collide [TimetableService] — acceptable-by-design (can't fit more breaks than gaps); realistic values (1–2) unaffected; parity preserved.
- [x] [Review][Defer] **Admin template live-preview not apéro-aware** — `EventTypeConfigurationForm/SchedulePreview.tsx` + `scheduleTimeline.ts` mirror the old algorithm; the global-template editor preview won't show apéro/even-split. Authoritative backend timetable is correct; secondary surface.
- [x] [Review][Defer] **Number field cleared → NaN** — clearing a knob in the dialog yields a confusing zod error instead of defaulting; minor UX.
- [x] [Review][Defer] **Doc-drift** — only `03-data-architecture.md` updated; `06-backend-architecture.md` + `user-guide/entity-management/events.md` (mapped) not touched and no `[no-doc]`; may trip the weekly auditor.

## Dev Agent Record

### Agent Model Used

Amelia (claude-opus-4-8[1m]) — BMad dev-story.

### Debug Log References

- Parity oracle (pre-change → post-change): `TimetableServiceTest` + `StructuralSessionServiceTest` pass **unchanged** against the generalized algorithm (the AM/PM-split branch is verbatim; only the linear branch became count-driven even-split, which reproduces today for breakSlots∈{0,1}).
- New backend tests: `TimetableServiceTest` 28/28 (incl. apéro-at-end/start, 2-break even-split, apéro-OFF parity); `AgendaConfigControllerIntegrationTest` 8/8 (GET template/override, PUT upsert+update-in-place, 400/404/403, AC4 isolation, AC5 apéro persistence).
- Full EMS suite (Testcontainers): **BUILD SUCCESSFUL**, 0 failures (10m).
- Frontend: type-check clean; `EditEventTypeDialog.test.tsx` 3/3; SlotAssignment suite 72 pass / 4 skipped; eslint clean on changed files.

### Completion Notes List

- **P1 backend complete.** New per-event `event_agenda_config` table (V121, copy-on-edit) + apéro columns on the shared `event_types` template + `sessions_session_type_check` widened for `'aperitif'` (V122). `AgendaConfig` interface unifies template + override; `AgendaConfigResolver` fronts all three `computeTimeline` consumers (read, structural-session generation, auto-assign-via-getTimetable). `computeTimeline` generalized: apéro segment (position start/end) + count-driven even-split breaks. Hand-written `AgendaConfigController` + `AgendaConfigService` + DTO records (matches the `TimetableController`/watch precedent); OpenAPI authored for FE type-gen + docs.
- **AC8 (intended behaviour change):** afternoon + evening templates now default apéro ON @ 90 min, position=end (full_day stays OFF). Because apéro is appended **after** moderation-end, no slot before it shifts — existing speaker assignments are unaffected.
- **P2 frontend complete.** `timetableService` + `useAgendaConfig`/`useUpdateAgendaConfig` (invalidates the timetable query); `EditEventTypeDialog` (MUI + react-hook-form + zod) wired into the SlotAssignment toolbar with the non-blocking assignment-desync warning; APERITIF rendered as a structural slot type. i18n keys added to **all 10 locales** — EN+DE first-class, gsw-BE mirrors DE, and fr/it/es/nl/fi/rm/ja hand-translated during code review.
- **Bruno: deliberately deferred** — a PUT would leave an un-cleanable override row on a prod event (no delete endpoint); GET/PUT fully covered by Testcontainers IT. **Beta verify: pending** (manual, touches prod CloudFront).
- **Scope honoured:** no slotKey addressing / insert-swap / mobile-tap here — that is 15.3.

### File List

**Backend (event-management-service):**
- `src/main/resources/db/migration/V121__create_event_agenda_config.sql` (A)
- `src/main/resources/db/migration/V122__add_aperitif_session_type.sql` (A)
- `src/main/java/ch/batbern/events/entity/AgendaConfig.java` (A)
- `src/main/java/ch/batbern/events/entity/EventAgendaConfig.java` (A)
- `src/main/java/ch/batbern/events/entity/EventTypeConfiguration.java` (M) — apéro fields + implements AgendaConfig
- `src/main/java/ch/batbern/events/repository/EventAgendaConfigRepository.java` (A)
- `src/main/java/ch/batbern/events/service/AgendaConfigResolver.java` (A)
- `src/main/java/ch/batbern/events/service/AgendaConfigService.java` (A)
- `src/main/java/ch/batbern/events/service/TimetableService.java` (M) — AgendaConfig signature, resolver, apéro, even-split breaks
- `src/main/java/ch/batbern/events/service/StructuralSessionService.java` (M) — resolver, aperitif structural type
- `src/main/java/ch/batbern/events/dto/TimetableSlot.java` (M) — APERITIF type
- `src/main/java/ch/batbern/events/dto/EventAgendaConfigResponse.java` (A)
- `src/main/java/ch/batbern/events/dto/UpdateEventAgendaConfigRequest.java` (A)
- `src/main/java/ch/batbern/events/controller/AgendaConfigController.java` (A)
- `src/main/java/ch/batbern/events/domain/Session.java` (M) — STRUCTURAL_SESSION_TYPES += aperitif
- `src/test/java/ch/batbern/events/service/TimetableServiceTest.java` (M) — resolver mock + apéro/even-split tests
- `src/test/java/ch/batbern/events/service/StructuralSessionServiceTest.java` (M) — resolver mock
- `src/test/java/ch/batbern/events/controller/AgendaConfigControllerIntegrationTest.java` (A)

**API contract:**
- `docs/api/events-api.openapi.yml` (M) — agenda-config GET/PUT paths + EventAgendaConfigResponse/UpdateEventAgendaConfigRequest/AgendaConfigSource schemas

**web-frontend:**
- `src/types/generated/events-api.types.ts` (M) — regenerated
- `src/services/timetableService/timetableService.ts` (M) — get/updateAgendaConfig
- `src/hooks/useAgendaConfig/useAgendaConfig.ts` (A)
- `src/components/SlotAssignment/EditEventTypeDialog/EditEventTypeDialog.tsx` (A)
- `src/components/SlotAssignment/EditEventTypeDialog/EditEventTypeDialog.test.tsx` (A)
- `src/components/SlotAssignment/DragDropSlotAssignment/DragDropSlotAssignment.tsx` (M) — Edit-event-type button, dialog wiring, APERITIF rendering
- `web-frontend/public/locales/{de,en,fr,it,rm,es,fi,nl,ja,gsw-BE}/events.json` (M) — Story 15.2 i18n keys

**Docs:**
- `docs/architecture/03-data-architecture.md` (M) — EventAgendaConfig + apéro knobs

### Change Log

- 2026-06-21 — Story created (ready-for-dev); apéro/break/dialog decisions resolved.
- 2026-06-21 — P1 backend (table + resolver + generalized computeTimeline + endpoints + apéro plumbing) and P2 frontend (dialog + hook + i18n) implemented. Parity preserved; full EMS suite + FE tests green. Status → review.
- 2026-06-21 — Code review (3-layer adversarial): 5 patches applied (aperitif added to SessionTimingService/NewsletterEmailService/cockpit structural-type lists; malformed-time + maxSlots<minSlots now → 400 with ITs), and all 7 placeholder locales hand-translated. 4 items deferred (logged in deferred-work.md). Status → done.
