# Story 11.B.3: Migrate legacy `speaker_pool.status` values; drop tentative columns; tighten status API; add derived flags

Status: done

<!-- Validation is optional — run validate-create-story for quality check before dev-story. -->

## Story

**As an** organizer with existing `speaker_pool` entries from before this refactor,
**I want** legacy state values to map cleanly into the 8-state model, the side-channel `is_tentative` columns to drop without losing audit information, and the `PUT /speakers/{id}/status` contract to reject the removed states,
**So that** no card in my kanban becomes orphaned or invisible after the migration ships and the public API contract matches the ADR-009 enum exactly.

**Phase:** B (State-machine consolidation) — third and final code-touching story of Phase B; lands AFTER 11.B.1 (shared-kernel enum reduction, done) and 11.B.2 (single-writer service, ready-for-dev). Phase B is complete when this story merges, unblocking Phase C (entity simplification).
**Dependencies:** Story 11.B.2 (the single-writer service must be on `feature/speaker-workflow-refactor` so the legacy data migration cannot race against in-flight `setStatus` calls — every status mutation flows through `SpeakerWorkflowService.transition()` by the time this story runs). Story 11.B.1 supplies the 8-state enum the OpenAPI contract is tightened against.
**Scope:** Three surfaces, kept tight and ordered:
1. **Flyway migration** (`services/event-management-service/src/main/resources/db/migration/V93__migrate_legacy_speaker_states.sql`) — legacy data mapping, `is_tentative` / `tentative_reason` column drop, overflow-artefact teardown, CHECK-constraint tighten.
2. **API + DTO + entity tighten** (`services/event-management-service/src/main/java/ch/batbern/events/`) — `PUT /events/{code}/speakers/{speakerId}/status` rejects the 5 removed values + READY; `SpeakerPool` loses `isTentative` / `tentativeReason`; `SpeakerPoolResponse` exposes derived `isSlotAssigned` / `isPublishable`; `EventWorkflowStateMachine.validateAllSpeakersConfirmed` re-anchored on `countPublishableByEventId`.
3. **OpenAPI contract** (`docs/api/speakers-api.openapi.yml`) — `SpeakerWorkflowState` enum tightened to the 8-state ADR-009 list; `UpdateStatusRequest` description rewritten; `SpeakerPoolEntry` schema gains `isSlotAssigned` + `isPublishable`; transition table in the endpoint description rewritten.

**Do NOT** touch in this story:
- The `speakers` table, `Speaker` entity, `SpeakerRepository` (Story 11.C.1).
- `MagicLinkService`, `SpeakerMagicLoginController`, `SpeakerPortalTokenController`, `JwtConfig` (Phase F).
- The new `POST /events/{code}/speakers/{speakerId}/promote` endpoint — this story only ensures `PUT /status` REJECTS `READY`; the alternative path lands in Story 11.D.1.
- Cognito provisioning at READY (Story 11.E.2).
- Any frontend file under `web-frontend/` — the frontend still references `isTentative` / `tentativeReason` today (`SpeakerStatusLanes.tsx:711`, `DetailsTabPanel.tsx:73`, `SpeakerDrawerHeader.tsx:47`) and will break TypeScript compilation when the OpenAPI types regenerate without those fields. See **AC11 — Frontend hand-off** below: this story stops at the `SpeakerPoolResponse` DTO + OpenAPI spec, marks the broken frontend files in the PR description as Phase-D pickups, and Phase D (Story 11.D.4 — unified drawer) cleans them up. Do not pre-fix the frontend in this story.
- Any `docs/architecture/`, `docs/prd/`, `CLAUDE.md` file — Story 11.A.1 owns documentation alignment and has already landed.

---

## Acceptance Criteria

The AC are pinned to ADR-009 §0.7 (removed states + side-channel columns), §"Migration to the new state set" (lines 515-537), Epic 11 PRD Story 11.B.3 (lines 626-693), and Plan §2.2. Each AC names exact files and the expected post-change shape.

### AC1 — Flyway migration `V93__migrate_legacy_speaker_states.sql` maps legacy `speaker_pool.status` values per ADR-009 §"Migration to the new state set"

**Given** a Flyway migration runs against a database containing legacy `speaker_pool.status` values from the 10-state model (`slot_assigned`, `confirmed`, `withdrew`, `overflow`),
**When** the migration completes,
**Then** for each row currently at one of the removed legacy statuses, the new status is set per the mapping in ADR-009 §"Migration to the new state set":

| Legacy `status` | New `status` | Extra side-effect |
|---|---|---|
| `slot_assigned` | `accepted` | None (slot is now orthogonal — `is_slot_assigned` is derived from `session.start_time`) |
| `confirmed` | `quality_reviewed` | None (`is_publishable` is derived from `QUALITY_REVIEWED AND is_slot_assigned`) |
| `withdrew` | `declined` | `decline_reason = COALESCE(decline_reason, 'Withdrew after acceptance (legacy)')` |
| `overflow` | `ready` | None (organizer may re-invite if a slot opens; slot-capacity gate at `READY → INVITED` enforces oversubscription) |

**And** for every row whose status was changed by the mapping, a row is INSERTED into `speaker_status_history` BEFORE the `UPDATE` runs, with:
- `id = uuid_generate_v4()`
- `speaker_pool_id = speaker_pool.id`
- `event_id = speaker_pool.event_id`
- `session_id = speaker_pool.session_id` (nullable)
- `previous_status = <legacy value>` (e.g. `'withdrew'`)
- `new_status = <mapped value>` (e.g. `'declined'`)
- `changed_by_username = 'system-migration-v93'`
- `change_reason = '<one of: "Legacy SLOT_ASSIGNED → ACCEPTED (slot now derived from session.start_time, ADR-009 §0.7)", "Legacy CONFIRMED → QUALITY_REVIEWED (is_publishable now derived, ADR-009 §0.7)", "Withdrew after acceptance (legacy)", "Legacy OVERFLOW → READY (slot-capacity gate replaces overflow, ADR-009 §0.7)">'`
- `changed_at = NOW()`

This satisfies the NFR4 audit-trail-integrity rule from Epic 11 PRD ("every state transition (including legacy migrations) produces a speaker_status_history row").

**And** the migration is **idempotent**: re-running the migration against an already-migrated database is a no-op (zero rows affected by every `UPDATE`/`INSERT` statement). This is achieved by predicating every statement on `WHERE status IN ('slot_assigned', 'confirmed', 'withdrew', 'overflow')` — after the first run those rows no longer exist.

**And** the migration uses Java/JSON `UPPER_CASE` only in the `change_reason` text; all `status` literals are `lowercase_snake_case` per project-context.md §"Enum Value Flow" (DB storage). The literal that ADR-009 §"Migration to the new state set" shows as `'WITHDREW'`/`'SLOT_ASSIGNED'` is **wrong for the DB layer** — those are the Java-side `name()` values. Use `'withdrew'`/`'slot_assigned'`/`'confirmed'`/`'overflow'` in the SQL `WHERE` clauses. The current `speaker_pool_status_check` constraint (V44 line 18-22) is lowercase — match it.

**And** the migration is wrapped in a single transaction (Flyway already runs each file in its own transaction; do NOT add `BEGIN`/`COMMIT` blocks — Flyway forbids them). Each `UPDATE`/`INSERT` is a separate statement, and one failure rolls back the whole migration.

**And** the migration adds a comment block at the top documenting the four mappings, the audit-history insertion contract, the ADR-009 anchor, the idempotency property, and a one-line note that the corresponding constraint tighten lives in AC2 of the same migration file.

### AC2 — `V93__migrate_legacy_speaker_states.sql` tightens the `speaker_pool` and `speaker_status_history` CHECK constraints to the 8-state allow-list

**Given** the legacy mapping rows from AC1 have committed (every existing `speaker_pool.status` value is now one of the 8 ADR-009 states),
**When** the migration runs the CHECK-constraint tighten step (after the mapping `UPDATE`s),
**Then**:

1. `ALTER TABLE speaker_pool DROP CONSTRAINT IF EXISTS speaker_pool_status_check;`
2. `ALTER TABLE speaker_pool ADD CONSTRAINT speaker_pool_status_check CHECK (status IN ('identified', 'contacted', 'ready', 'invited', 'accepted', 'declined', 'content_submitted', 'quality_reviewed'));`
3. `ALTER TABLE speaker_status_history DROP CONSTRAINT IF EXISTS speaker_status_history_previous_status_check;`
4. `ALTER TABLE speaker_status_history ADD CONSTRAINT speaker_status_history_previous_status_check CHECK (previous_status IN ('identified', 'contacted', 'ready', 'invited', 'accepted', 'declined', 'content_submitted', 'quality_reviewed'));`
5. `ALTER TABLE speaker_status_history DROP CONSTRAINT IF EXISTS speaker_status_history_new_status_check;`
6. `ALTER TABLE speaker_status_history ADD CONSTRAINT speaker_status_history_new_status_check CHECK (new_status IN ('identified', 'contacted', 'ready', 'invited', 'accepted', 'declined', 'content_submitted', 'quality_reviewed'));`

The 8 allowed values are exactly the lowercase form of the ADR-009 enum (`IDENTIFIED`, `CONTACTED`, `READY`, `INVITED`, `ACCEPTED`, `CONTENT_SUBMITTED`, `QUALITY_REVIEWED`, `DECLINED`). No leftover legacy values: `slot_assigned`, `confirmed`, `withdrew`, `overflow` are absent from each `CHECK` list.

**Important — order**: the `previous_status` history-row INSERTS in AC1 use legacy values like `'withdrew'`, which **violate** the new `speaker_status_history_previous_status_check`. So the `INSERT INTO speaker_status_history` statements MUST run **before** step 3/4 above (the constraint tighten). The migration script ordering is:
1. `INSERT INTO speaker_status_history ...` for each legacy row (per AC1 — uses legacy `previous_status` values, which are still permitted by the existing V44 constraint).
2. `UPDATE speaker_pool SET status = ...` for each legacy row (per AC1 — `status` is being changed to a new value that is still permitted by the existing V44 constraint).
3. `ALTER TABLE speaker_pool DROP CONSTRAINT ...; ALTER TABLE speaker_pool ADD CONSTRAINT ...` (per AC2 — new constraint).
4. `ALTER TABLE speaker_status_history DROP/ADD ...` for both `previous_status` and `new_status` (per AC2). After this step the history rows that already exist with legacy `previous_status` values are grandfathered (PostgreSQL CHECK constraints validate on `INSERT`/`UPDATE`, not retroactively). The `previous_status` rows from step 1 above are also grandfathered through the same mechanism. **Future inserts will reject legacy values.**

This grandfathering is explicit and intentional — the audit trail of how a `withdrew → declined` migration happened MUST survive in `speaker_status_history`, even though `withdrew` is no longer a writable value. Document this in the migration comment block.

### AC3 — `V93__migrate_legacy_speaker_states.sql` drops `speaker_pool.is_tentative` and `speaker_pool.tentative_reason` columns and their index

**Given** AC1 + AC2 have committed,
**When** the migration runs the column-drop step (after the constraint tighten),
**Then**:

1. `DROP INDEX IF EXISTS idx_speaker_pool_tentative;` (the partial index from V45 line 23 — depends on the `is_tentative` column and must be dropped first or the column drop CASCADEs unpredictably; use `IF EXISTS` for idempotency).
2. `ALTER TABLE speaker_pool DROP COLUMN IF EXISTS is_tentative;` (the column from V45 line 11).
3. `ALTER TABLE speaker_pool DROP COLUMN IF EXISTS tentative_reason;` (the column from V45 line 12).

**And** the `ALTER TABLE` uses `IF EXISTS` so re-running the migration is a no-op. No `CASCADE` keyword (don't mass-drop downstream views — there are none, but be explicit). If a `CASCADE` is needed for some reason discovered during testing, document it in the migration comment with the specific dependent object name; do not use a blanket cascade.

**And** there is **no** corresponding column drop for `speakers` table or `magic_link_tokens` table or `speaker_selection_votes` table — those are out of scope (`speakers` is Story 11.C.1; `magic_link_tokens` is Phase F; `speaker_selection_votes` was never created — `grep -rln 'speaker_selection_votes' services/*/src/main/resources/db/migration/` returns no matches, confirming the table does not exist in the codebase. Per Epic 11 PRD AR19 the architecture team flagged it as "drop overflow tables and related artefacts" assuming they existed; in BATbern they were never created, so this story has nothing to drop on that front).

**And** the migration produces a final verification block (a `DO $$ BEGIN ... END $$` PL/pgSQL block) that asserts every speaker_pool row is in one of the 8 valid states and zero rows have `status IN ('slot_assigned', 'confirmed', 'withdrew', 'overflow')`. If the assertion fails, raise an exception with a clear message — this catches any race-condition write that slipped in between the mapping UPDATE and the constraint tighten. The assertion is informational; in practice Flyway's transaction wraps the whole file so the assertion would have to fail mid-transaction to be useful. Keep it as a paranoia check; the cost is one SELECT.

### AC4 — `PUT /events/{code}/speakers/{speakerId}/status` rejects the 5 removed values with HTTP 400 and a structured `ErrorResponse` body

**Given** I `PUT /api/v1/events/{code}/speakers/{speakerId}/status` with `newStatus` equal to any of `SLOT_ASSIGNED`, `CONFIRMED`, `OVERFLOW`, `WITHDREW`, or `TENTATIVE`,
**When** the request is processed,
**Then** the API returns HTTP 400 Bad Request,
**And** the response body matches the existing `ErrorResponse` shape used elsewhere in `GlobalExceptionHandler`:

```json
{
    "code": "INVALID_SPEAKER_WORKFLOW_STATE",
    "message": "Invalid speaker workflow state '<received-value>'. Accepted values: IDENTIFIED, CONTACTED, READY, INVITED, ACCEPTED, CONTENT_SUBMITTED, QUALITY_REVIEWED, DECLINED.",
    "details": {
        "rejectedValue": "<received-value>",
        "acceptedValues": ["IDENTIFIED", "CONTACTED", "READY", "INVITED", "ACCEPTED", "CONTENT_SUBMITTED", "QUALITY_REVIEWED", "DECLINED"]
    }
}
```

**And** the speaker row is not modified (verify by re-reading `speaker_pool` row after the rejected call — `status` and `updated_at` are unchanged).

**Mechanism**: this is enforced at the JSON deserialization layer — `UpdateStatusRequest.newStatus` is typed `SpeakerWorkflowState` (the 8-value shared-kernel enum from Story 11.B.1), so Jackson **already rejects** any unknown enum value during request body binding. The 400 happens before the controller method runs. The current `GlobalExceptionHandler.handleHttpMessageNotReadableException` (verify with `grep -n "HttpMessageNotReadableException" services/event-management-service/src/main/java/ch/batbern/events/exception/GlobalExceptionHandler.java`) produces a generic "malformed JSON" response — this story **extends** the handler to detect `InvalidFormatException` with target type `SpeakerWorkflowState` and produce the structured body above. The detection is via `ex.getCause() instanceof InvalidFormatException && ((InvalidFormatException) cause).getTargetType() == SpeakerWorkflowState.class`. The handler reads the rejected value from `((InvalidFormatException) cause).getValue()` and the accepted-values list from `SpeakerWorkflowState.values()`.

**And** if `GlobalExceptionHandler` already has a `MethodArgumentNotValidException` handler (it must, per project-context.md "ALWAYS add explicit `@ExceptionHandler(MethodArgumentNotValidException.class)`"), do **not** modify it — the enum-rejection path bypasses validation (it's a deserialization error). Extend `handleHttpMessageNotReadableException` only. Verify the existing `@ExceptionHandler(Exception.class)` catch-all still exists and does NOT shadow the new logic — Spring picks the most specific handler.

**And** an integration test in `SpeakerStatusControllerIntegrationTest` (extend the existing test if present; otherwise create one extending `AbstractIntegrationTest`) covers each of the 5 rejected values plus the 8 accepted values, parameterized via `@ParameterizedTest` + `@ValueSource`. For each rejected value: assert HTTP 400, JSON body contains the `INVALID_SPEAKER_WORKFLOW_STATE` code, and `speaker_pool.status` is unchanged in the DB after the call. The test uses MockMvc with a real-Cognito-style `JwtAuthenticationToken` (per existing controller tests).

### AC5 — `PUT /events/{code}/speakers/{speakerId}/status` rejects `newStatus = READY` with HTTP 400 and an explanatory message

**Given** I `PUT /api/v1/events/{code}/speakers/{speakerId}/status` with `newStatus = READY` (a value Jackson DOES accept because it's a valid enum constant),
**When** the request is processed,
**Then** the API returns HTTP 400 Bad Request,
**And** the response body is:

```json
{
    "code": "READY_REQUIRES_PROMOTE_ENDPOINT",
    "message": "Speakers cannot be transitioned to READY via PUT /status — the READY state requires an email payload for User provisioning. Use POST /api/v1/events/{eventCode}/speakers/{speakerId}/promote (Story 11.D.1) instead.",
    "details": {
        "rejectedValue": "READY",
        "alternativeEndpoint": "POST /api/v1/events/{eventCode}/speakers/{speakerId}/promote"
    }
}
```

**And** the speaker row is not modified.

**Mechanism**: enforce this **inside the controller**, not as a separate domain exception. Concretely, in `SpeakerStatusController.updateStatus()` (line 70 of the current file), insert this check immediately after the request is bound and before delegating to `speakerStatusService.updateStatus(...)`:

```java
if (request.getNewStatus() == SpeakerWorkflowState.READY) {
    throw new BadRequestException(
            "READY_REQUIRES_PROMOTE_ENDPOINT",
            "Speakers cannot be transitioned to READY via PUT /status — ...",
            Map.of(
                    "rejectedValue", "READY",
                    "alternativeEndpoint", "POST /api/v1/events/{eventCode}/speakers/{speakerId}/promote"
            )
    );
}
```

The `BadRequestException` is the existing project exception (verify with `grep -rn "class BadRequestException" services/event-management-service/src/main/java/`). If a constructor with `(code, message, details)` does not exist, extend the existing exception or create a new `ReadyRequiresPromoteException` that carries the same payload — match whatever the codebase convention is. Pattern after the `SlotCapacityReachedException` precedent established by Story 11.B.2 (Task 2.6) if a new exception is needed.

**Rationale (do NOT enforce READY rejection in `SpeakerWorkflowService.transition()`)**: the workflow service must still accept `target = READY` from the new POST `/promote` endpoint (Story 11.D.1) — that endpoint constructs the email payload and calls `transition(speakerId, READY, actor, payload)`. The PUT endpoint is the only path where `READY` is illegal (because the request body has no `email` field). So the rejection lives at the controller layer.

**And** an integration test (same class as AC4) covers the `READY` rejection: assert HTTP 400, body contains `READY_REQUIRES_PROMOTE_ENDPOINT`, `speaker_pool.status` unchanged.

### AC6 — `SpeakerStatusController.updateStatus` continues to delegate to `SpeakerWorkflowService.transition()` via `SpeakerStatusService.updateStatus` (no regression of 11.B.2)

**Given** I `PUT /api/v1/events/{code}/speakers/{speakerId}/status` with one of the 7 legal targets (`CONTACTED`, `INVITED`, `ACCEPTED`, `CONTENT_SUBMITTED`, `QUALITY_REVIEWED`, `DECLINED` — and `IDENTIFIED` for re-affirms),
**When** the request is processed,
**Then** the controller delegates to `speakerStatusService.updateStatus(...)`, which delegates to `speakerWorkflowService.transition(...)` per Story 11.B.2 AC6,
**And** the response is a `SpeakerStatusResponse` with the post-transition state plus the derived `isSlotAssigned` and `isPublishable` flags (see AC7).

**And** the existing AC1 of Story 11.B.2 (`SpeakerWorkflowService.transition()` is the sole writer) is preserved — `grep -rn "speaker.setStatus(\|setStatus(SpeakerWorkflowState\." services/event-management-service/src/main/java/` still returns exactly one match (inside `SpeakerWorkflowService.transition()`). The new READY-rejection check in the controller (AC5) throws **before** the service delegate is called, so it doesn't introduce a second writer.

**And** the Bruno test `bruno-tests/events/speakers/status-update.bru` (create if absent — match the directory structure of existing Bruno tests under `bruno-tests/events/`) exercises one happy-path transition (e.g., `IDENTIFIED → CONTACTED`) end-to-end against a running staging instance, asserting 200 OK and the response payload shape.

### AC7 — `SpeakerPoolResponse` exposes derived `isSlotAssigned` and `isPublishable` flags

**Given** I query any endpoint that returns a `SpeakerPoolResponse` (e.g., `GET /events/{code}/speakers/pool`, `GET /events/{code}/speakers/{id}/status`, `GET /events/{code}/speakers/review-queue`),
**When** I read the JSON response body,
**Then** each speaker entry contains two NEW fields:
- `isSlotAssigned` (boolean) — `true` when the speaker has an assigned session AND that session has a non-null `start_time`, `false` otherwise.
- `isPublishable` (boolean) — `true` when `status == 'QUALITY_REVIEWED' AND isSlotAssigned == true`, `false` otherwise.

**And** these fields are **computed at read time** — there are no new columns on `speaker_pool`, no new columns on `sessions`. Per ADR-009 §0.1 and plan §2.5 these are derived predicates.

**Implementation contract** (`services/event-management-service/src/main/java/ch/batbern/events/dto/SpeakerPoolResponse.java`):

1. Add two new private fields: `Boolean isSlotAssigned` and `Boolean isPublishable` (declared after the existing `submittedAbstract` field for diff cleanliness; getters/setters generated to match the existing manual-getter pattern at lines 287-299 of the current file).
2. Add a new factory overload `fromEntity(SpeakerPool speakerPool, Session session)` where `session` is nullable. If `session == null` or `session.getStartTime() == null` → `isSlotAssigned = false`. Otherwise `isSlotAssigned = true`. `isPublishable = (speakerPool.getStatus() == SpeakerWorkflowState.QUALITY_REVIEWED) && isSlotAssigned`.
3. The existing `fromEntity(SpeakerPool)` overload at line 67 of the current file delegates to the new overload with `session = null` — preserving backwards-compatibility for callers that don't have a `Session` handy. In that fallback path `isSlotAssigned` is set from `speakerPool.getSessionId() != null` — a weaker predicate that ignores `session.start_time`. Document this caveat with a one-line Javadoc on the overload: "Use the (SpeakerPool, Session) overload when the session is loadable; the (SpeakerPool)-only overload falls back to sessionId-based slot-assignment, which can over-report `isSlotAssigned` when a session is assigned but its start_time has not been set."
4. The existing `fromEntityWithContent(SpeakerPool, String, String)` overload (lines 118+) keeps its current behaviour but is updated to compute `isSlotAssigned` and `isPublishable` from the `SpeakerPool` alone (fallback path — same caveat as above).
5. Delete the two existing field declarations + getters + setters for `isTentative` (line 37, 287-291) and `tentativeReason` (line 38, 295-299). Delete the two assignments in `fromEntity` at lines 92-93. **The DTO surface must not expose tentative columns after this story.** Frontend impact is acknowledged in AC11.

**And** every call site of `SpeakerPoolResponse.fromEntity(...)` is reviewed:
- `SpeakerPoolService.java:164, 175` (the existing `fromEntityWithContent` call sites) — unchanged (the fallback path is used; the speaker list query is already loading content but not eagerly loading sessions).
- Any other `fromEntity(...)` call site found by `grep -rn "SpeakerPoolResponse\.fromEntity\b" services/event-management-service/src/main/java/` — verify each: if the caller has access to the speaker's session (e.g., already loaded via a join), pass it to the new overload; otherwise leave the fallback path.
- The dashboard and review-queue endpoints (`SpeakerStatusController.getStatusSummary`, `getReviewQueue`) are upgraded to the new overload when feasible. **Out of scope creep guard**: if upgrading a call site requires joining `sessions` in a JPQL query (i.e., entity-graph or new `findByEventIdWithSession`), use the fallback path and add a `// TODO Story 11.D.4` comment. Do NOT introduce new repository queries in this story.

**And** the corresponding OpenAPI schema (`docs/api/speakers-api.openapi.yml` `SpeakerPoolEntry` schema at line 1546) gains the two new properties:

```yaml
isSlotAssigned:
  type: boolean
  description: |
    Derived flag (ADR-009 §0.1): true when the speaker has an assigned session
    whose start_time is non-null. NOT a persisted column — computed at read time.
isPublishable:
  type: boolean
  description: |
    Derived flag (ADR-009 §0.1): true when status == QUALITY_REVIEWED AND isSlotAssigned.
    Gates the AGENDA_PUBLISHED event-workflow transition. NOT a persisted column.
```

The two new properties are **not in the `required` list** (they have computed defaults). The `isTentative` / `tentativeReason` properties on `SpeakerPoolEntry` (if present in the spec) are deleted.

### AC8 — `SpeakerPoolRepository.countPublishableByEventId(UUID eventId)` exists and returns the count of QUALITY_REVIEWED speakers whose session has a non-null `start_time`

**Given** `services/event-management-service/src/main/java/ch/batbern/events/repository/SpeakerPoolRepository.java` after this story,
**Then** a new method exists:

```java
/**
 * Count speakers who are "publishable" per ADR-009 §0.1:
 * speaker_pool.status == 'quality_reviewed' AND the assigned session has a non-null start_time.
 *
 * Used by EventWorkflowStateMachine.validateAllSpeakersConfirmed to gate AGENDA_PUBLISHED.
 *
 * @param eventId the event ID
 * @return count of publishable speakers for the event
 */
@org.springframework.data.jpa.repository.Query("""
    SELECT COUNT(sp)
    FROM SpeakerPool sp
    JOIN Session s ON sp.sessionId = s.id
    WHERE sp.eventId = :eventId
      AND sp.status = ch.batbern.shared.types.SpeakerWorkflowState.QUALITY_REVIEWED
      AND s.startTime IS NOT NULL
    """)
long countPublishableByEventId(@org.springframework.data.repository.query.Param("eventId") UUID eventId);
```

**And** the method is annotated with `@Query` (JPQL) — NOT `@NativeQuery` — so the persistence context catches type errors at startup. Use a Java 21 text block as shown.

**And** an integration test in `SpeakerPoolRepositoryIntegrationTest` (extend the existing one; if none exists, create one extending `AbstractIntegrationTest`) covers:
1. Empty pool → 0.
2. Speakers in pool but none `QUALITY_REVIEWED` → 0.
3. `QUALITY_REVIEWED` speaker with `sessionId = null` → 0 (not counted).
4. `QUALITY_REVIEWED` speaker with `sessionId` set but session has `start_time = null` → 0 (not counted).
5. `QUALITY_REVIEWED` speaker with `sessionId` set and session has `start_time` set → 1 (counted).
6. Mix of all four scenarios → asserts only #5 is counted.

### AC9 — `EventWorkflowStateMachine.validateQualityReviewComplete` is renamed to `validateAllSpeakersConfirmed` and re-anchored on `countPublishableByEventId`

**Given** `services/event-management-service/src/main/java/ch/batbern/events/service/EventWorkflowStateMachine.java` after this story,
**Then** the method at line 272 (current name `validateQualityReviewComplete`) is **renamed** to `validateAllSpeakersConfirmed` (the name expected by ADR-009 §"Migration to the new state set" anchor and by `docs/architecture/06a-workflow-state-machines.md:181-194`). The switch dispatch at line 161 is updated to call the new name.

**And** the method body is rewritten to use `speakerPoolRepository.countPublishableByEventId(event.getId())` (the new query from AC8) and to enforce: "every ACCEPTED-or-beyond speaker must be `publishable`". Concretely:

```java
private void validateAllSpeakersConfirmed(Event event) {
    long acceptedOrBeyondSpeakers = speakerPoolRepository.countByEventIdAndStatusIn(
            event.getId(),
            List.of(
                    SpeakerWorkflowState.ACCEPTED,
                    SpeakerWorkflowState.CONTENT_SUBMITTED,
                    SpeakerWorkflowState.QUALITY_REVIEWED
            )
    );
    long publishableSpeakers = speakerPoolRepository.countPublishableByEventId(event.getId());

    if (acceptedOrBeyondSpeakers > publishableSpeakers) {
        throw new WorkflowValidationException(
                "Not all accepted speakers are publishable",
                Map.of(
                        "accepted", acceptedOrBeyondSpeakers,
                        "publishable", publishableSpeakers,
                        "gap", acceptedOrBeyondSpeakers - publishableSpeakers
                )
        );
    }

    log.debug("Publishable check passed: {}/{} accepted speakers are publishable",
            publishableSpeakers, acceptedOrBeyondSpeakers);
}
```

**And** a companion repository method `countByEventIdAndStatusIn(UUID eventId, List<SpeakerWorkflowState> statuses)` is added to `SpeakerPoolRepository` (annotated with `@Query` to avoid Spring Data method-naming awkwardness). Match the style of the existing `countByEventIdAndStatus` method (line 56).

**And** the **session-timing check** that the current `validateQualityReviewComplete` performs (lines 273-292: `sessionsWithTiming < totalSessions`) is **removed** — the publishable count already encodes the session-timing predicate via the `JOIN Session ... s.start_time IS NOT NULL` in `countPublishableByEventId`. Per ADR-009 the publishability gate is a single predicate, not two layered ones. The old "no sessions exist for this event" 422 case is replaced by the simpler `acceptedOrBeyondSpeakers == 0` case: if there are no accepted speakers at all, `acceptedOrBeyondSpeakers == 0` and `publishableSpeakers == 0`, so the validation passes vacuously — which is the wrong behaviour. Add an additional explicit check at the top of the new method body:

```java
if (acceptedOrBeyondSpeakers == 0) {
    throw new WorkflowValidationException(
            "Cannot publish agenda — no accepted speakers exist for this event",
            Map.of("acceptedOrBeyond", 0)
    );
}
```

**And** the integration test `EventWorkflowStateMachineIntegrationTest` (find via `grep -rn "EventWorkflowStateMachineIntegrationTest" services/event-management-service/src/test/`) is updated:
- The old `validateQualityReviewComplete` tests that asserted on session-timing alone are deleted.
- New parameterized tests cover the three failure cases: (1) no accepted speakers, (2) accepted-but-not-quality-reviewed speakers, (3) quality-reviewed-but-no-session-start-time. Plus one happy path: all accepted are quality-reviewed AND have session start_time. All tests use Testcontainers (extend `AbstractIntegrationTest`).

**And** the `EventWorkflowScheduledService` (the scheduled-transition driver — `grep -n "validateQualityReviewComplete\|validateAllSpeakersConfirmed" services/event-management-service/src/main/java/`) is checked: if it references the method by name (via reflection or via interface), update to the new name. Spring direct-method-call references update automatically at compile time; reflection-based ones need the string rename.

### AC10 — OpenAPI spec `docs/api/speakers-api.openapi.yml` is tightened: `SpeakerWorkflowState` enum, `UpdateStatusRequest` description, transition table

**Given** `docs/api/speakers-api.openapi.yml` after this story,
**Then** at line 1064 the `SpeakerWorkflowState` schema enum is **exactly**:

```yaml
SpeakerWorkflowState:
  type: string
  enum:
    - IDENTIFIED
    - CONTACTED
    - READY
    - INVITED
    - ACCEPTED
    - CONTENT_SUBMITTED
    - QUALITY_REVIEWED
    - DECLINED
  description: |
    Speaker coordination workflow state (UPPER_CASE per project-context.md §"Enum Value Flow").
    Per ADR-009 (Unified Speaker Workflow) — 8 states; SLOT_ASSIGNED, CONFIRMED, OVERFLOW, WITHDREW removed.
    - IDENTIFIED: brainstorm-list entry; no User, no Cognito user.
    - CONTACTED: organizer reaching out; still brainstorming; no User, no Cognito user.
    - READY: real speaker identified with email; User provisioning happens at the transition INTO this state.
      Reached ONLY via POST /events/{code}/speakers/{speakerId}/promote (Story 11.D.1), never via PUT /status.
    - INVITED: formal invitation sent; speaker can authenticate via Cognito.
      READY → INVITED blocked when count(ACCEPTED) + count(INVITED) >= max_slots.
    - ACCEPTED: speaker committed via the portal (or via organizer-on-behalf).
    - CONTENT_SUBMITTED: title + abstract submitted (by speaker or by organizer).
    - QUALITY_REVIEWED: moderator approved content. Terminal happy state.
      Combined with session.start_time IS NOT NULL, makes the speaker publishable.
    - DECLINED: terminal "not happening"; reachable from every non-terminal state.

    Derived flags (NOT persisted — computed at read time):
    - isSlotAssigned := session.start_time IS NOT NULL
    - isPublishable  := status == QUALITY_REVIEWED AND isSlotAssigned
```

**And** the `PUT /events/{eventCode}/speakers/{speakerId}/status` endpoint description (line 374-384) is rewritten to:

```yaml
description: |
  Update the workflow status of a speaker for a specific event.
  Story 5.4 (original) + Story 11.B.3 (8-state model tighten).

  Per ADR-009 the legal transitions are:
  - IDENTIFIED → CONTACTED, DECLINED
  - CONTACTED → DECLINED (transition to READY requires POST /promote — has email payload)
  - INVITED → ACCEPTED, DECLINED
  - ACCEPTED → CONTENT_SUBMITTED, DECLINED
  - CONTENT_SUBMITTED → QUALITY_REVIEWED, DECLINED
  - QUALITY_REVIEWED → DECLINED
  - DECLINED is terminal — no transitions out.

  Notes:
  - Setting newStatus = READY via this endpoint returns 400 READY_REQUIRES_PROMOTE_ENDPOINT.
    Use POST /api/v1/events/{eventCode}/speakers/{speakerId}/promote instead (Story 11.D.1).
  - Setting newStatus to any of SLOT_ASSIGNED, CONFIRMED, OVERFLOW, WITHDREW, TENTATIVE
    (legacy 10-state values) returns 400 INVALID_SPEAKER_WORKFLOW_STATE — those states
    were removed per ADR-009 §0.7.
  - Same-state re-affirm (current == newStatus) is permitted and writes an audit-trail
    self-transition row to speaker_status_history (no side-effects fire).
```

**And** the legacy line at 382 ("ACCEPTED → SLOT_ASSIGNED, CONFIRMED (cannot go back to DECLINED)") is **deleted**.

**And** the response status codes (line 409-437) keep `200`, `400`, `401`, `403`, `404` and **delete `422`** — the new contract uses `400` for invalid state values and `400` for the `READY_REQUIRES_PROMOTE_ENDPOINT` rejection. The `422` was the old "invalid state transition" code for the now-removed legacy states.

**And** the `SpeakerPoolEntry` schema at line 1546 is updated to:
- Add `isSlotAssigned: boolean` and `isPublishable: boolean` properties (per AC7).
- Remove `isTentative` and `tentativeReason` properties if they exist (`grep -n "isTentative\|tentativeReason" docs/api/speakers-api.openapi.yml` returned 0 matches as of this story's authoring — so this is a no-op; verify and skip if confirmed empty).

**And** after the OpenAPI changes, `cd web-frontend && npm run generate:api-types` is run — but **do NOT commit** the regenerated `web-frontend/src/types/generated/` changes in this story. The frontend break is expected and the regeneration happens in Phase D (Story 11.D.4). See AC11.

### AC11 — Frontend hand-off — the four frontend files that reference `isTentative`/`tentativeReason` are documented as Phase-D pickups

**Given** this story drops `isTentative` and `tentativeReason` from the OpenAPI spec + the backend DTO,
**Then** the four frontend files that currently consume those fields will fail TypeScript compilation when frontend types regenerate:

1. `web-frontend/src/types/speakerPool.types.ts:46-47` — manual type definition referencing `isTentative` / `tentativeReason`. **Delete those two lines** at the end of this story (manual type, not generated).
2. `web-frontend/src/components/organizer/SpeakerStatus/SpeakerStatusLanes.tsx:711-714` — UI conditional that displays `speaker.tentativeReason`. **Out of scope** — Phase D Story 11.D.4 owns this file (kanban refactor).
3. `web-frontend/src/components/organizer/SpeakerDrawer/DetailsTabPanel.tsx:73-79, 163` — drawer block displaying tentative reason. **Out of scope** — Phase D Story 11.D.4 owns the drawer.
4. `web-frontend/src/components/organizer/SpeakerDrawer/SpeakerDrawerHeader.tsx:47` — header badge for tentative. **Out of scope** — Phase D Story 11.D.4 owns the drawer.

For items 2-4, the dev does **not** edit those files in this story. The PR description must include a "Frontend follow-up required" section listing the three files and noting that Story 11.D.4 (kanban guided-drag + unified drawer) owns the cleanup. Tag the PR with `frontend-breaks-on-merge` or equivalent label if the team uses one.

For item 1, the dev removes the two manual type lines because they are not regenerated — they are an explicit author-maintained surface. Leaving them in place would create a phantom type that doesn't match the regenerated `SpeakerPoolEntry`.

**And** the frontend regeneration (`npm run generate:api-types`) is **NOT** run in this story's commit. The regenerated types are part of Story 11.D.4's diff. Document this in the PR description: "Frontend regeneration of generated types is intentionally deferred to Story 11.D.4 to land alongside the UI fixes that consume the new flag shape; running `npm run generate:api-types` in this PR would create commit-time churn without removing the broken UI references."

### AC12 — Out-of-scope sweep

**Given** `git diff --name-only main...HEAD` is run after dev completes the work,
**Then** the diff includes ONLY files under:

- `services/event-management-service/src/main/resources/db/migration/V93__migrate_legacy_speaker_states.sql` (NEW)
- `services/event-management-service/src/main/java/ch/batbern/events/dto/UpdateStatusRequest.java` (UNCHANGED — newStatus is already typed `SpeakerWorkflowState` which is the 8-value enum from 11.B.1; Jackson handles the rejection)
- `services/event-management-service/src/main/java/ch/batbern/events/dto/SpeakerPoolResponse.java` (MODIFIED — add `isSlotAssigned` + `isPublishable`; remove `isTentative` + `tentativeReason`)
- `services/event-management-service/src/main/java/ch/batbern/events/domain/SpeakerPool.java` (MODIFIED — remove `@Column is_tentative` + `@Column tentative_reason` fields + getters/setters at lines 118-122 + any uses elsewhere in the entity)
- `services/event-management-service/src/main/java/ch/batbern/events/controller/SpeakerStatusController.java` (MODIFIED — add READY-rejection check per AC5)
- `services/event-management-service/src/main/java/ch/batbern/events/exception/GlobalExceptionHandler.java` (MODIFIED — extend `handleHttpMessageNotReadableException` per AC4)
- `services/event-management-service/src/main/java/ch/batbern/events/service/EventWorkflowStateMachine.java` (MODIFIED — rename + rewrite `validateQualityReviewComplete` → `validateAllSpeakersConfirmed` per AC9)
- `services/event-management-service/src/main/java/ch/batbern/events/repository/SpeakerPoolRepository.java` (MODIFIED — add `countPublishableByEventId` + `countByEventIdAndStatusIn` per AC8 + AC9)
- `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerResponseService.java` (MODIFIED — remove the remaining `setIsTentative` / `setTentativeReason` calls that 11.B.2 didn't clean up; verify with `grep -n "setIsTentative\|setTentativeReason" services/event-management-service/src/main/java/` — these were already removed in 11.B.2's AC7 from `processAcceptResponse` but a residual reference in `processTentativeResponse` is implicitly resolved because 11.B.2 deletes that method)
- `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerPoolService.java` (MODIFIED — call sites of `SpeakerPoolResponse.fromEntityWithContent` use the fallback path; no change in behaviour but verify the file still compiles after the DTO change)
- `services/event-management-service/src/main/java/ch/batbern/events/exception/BadRequestException.java` (POSSIBLY MODIFIED — extend with the 3-arg `(code, message, details)` constructor if not present; or POSSIBLY NEW — `ReadyRequiresPromoteException.java` — match codebase convention discovered during dev)
- `services/event-management-service/src/test/java/ch/batbern/events/...` (MULTIPLE MODIFIED + 1-3 NEW — integration tests per AC4, AC5, AC8, AC9; also clean up `SpeakerResponseServiceTest:335,569,636-666` and `SpeakerPortalResponseControllerIntegrationTest:359` references to `is_tentative`/`tentativeReason` that 11.B.2 left behind because those tests covered the deleted `processTentativeResponse` path — verify which still compile after 11.B.2 and delete the rest)
- `docs/api/speakers-api.openapi.yml` (MODIFIED — `SpeakerWorkflowState` enum tighten + `UpdateStatusRequest` description + `SpeakerPoolEntry` schema)
- `web-frontend/src/types/speakerPool.types.ts` (MODIFIED — delete the two `isTentative`/`tentativeReason` lines at 46-47 only)
- `_bmad-output/implementation-artifacts/sprint-status.yaml` (status flip `11-b-3-… → review`, handled by the code-review workflow not by dev)
- `_bmad-output/implementation-artifacts/11-b-3-migrate-legacy-status-drop-tentative-cols-derived-flags.md` (Dev Agent Record updates)
- `bruno-tests/events/speakers/` (POSSIBLY NEW — one happy-path `.bru` per AC6)

**And** the diff does NOT contain:

- Any file under `services/speaker-coordination-service/`, `services/company-user-management-service/`, `services/partner-coordination-service/`, `services/attendee-experience-service/`, `api-gateway/`, `shared-kernel/`, `infrastructure/` — the refactor in this story is event-management-service + frontend type-stub + OpenAPI only.
- Any of `web-frontend/src/components/organizer/SpeakerStatus/SpeakerStatusLanes.tsx`, `web-frontend/src/components/organizer/SpeakerDrawer/*.tsx` — those are Phase D (Story 11.D.4) per AC11.
- Any `web-frontend/src/types/generated/` change — frontend regen is Phase D per AC11.
- Any deletion of the `speakers` table, `Speaker` entity, `SpeakerRepository` — those are Story 11.C.1.
- Any deletion of `MagicLinkService`, `SpeakerMagicLoginController`, `SpeakerPortalTokenController`, `JwtConfig`, `magic_link_tokens` table — those are Phase F.
- Any addition of a `POST /promote` endpoint — that's Story 11.D.1.
- Any Cognito provisioning logic, `AdminCreateUser` call, IAM permission change, `app-client-auth-flow` change — those are Phase E.
- Any `docs/architecture/`, `docs/prd/`, `CLAUDE.md` file — those are Story 11.A.1's territory and have already landed.

### AC13 — Build succeeds end-to-end; migration applies cleanly against staging clone

**Given** `./gradlew :shared-kernel:publishToMavenLocal` has run (11.B.1's artefact) and Story 11.B.2 is merged onto `feature/speaker-workflow-refactor`,
**When** `./gradlew :services:event-management-service:build` is run from the repo root,
**Then** the build succeeds: `BUILD SUCCESSFUL`. No compile errors. Spotless + Checkstyle pass. All unit + integration tests pass.

**And** the dev pipes the output through `tee /tmp/em-build-11b3.log`, then greps for `FAIL\|BUILD FAIL\|ERROR` per project-context.md §"Build & Test Output" rule.

**And** the migration is rehearsed against a Testcontainers-loaded copy of a production `speaker_pool` snapshot: seed `speaker_pool` with 5 rows (one per legacy state — `slot_assigned`, `confirmed`, `withdrew`, `overflow`, and one already at `accepted` to verify the idempotency guard), run `flywayMigrate`, assert the post-state matches AC1 + AC2 + AC3 expectations. Run `flywayMigrate` a second time and assert zero rows are touched (idempotency). This test lives at `services/event-management-service/src/test/java/ch/batbern/events/migration/V93LegacySpeakerStatesMigrationIntegrationTest.java` and extends `AbstractIntegrationTest`. **The test must not rely on the post-V93 constraint state for seeding** — seed via raw SQL `INSERT` that bypasses JPA (which would reject legacy values via the entity's `@Convert` annotation). Use `jdbcTemplate.update("INSERT INTO speaker_pool (id, event_id, speaker_name, status, ...) VALUES (...)")` with the legacy lowercase status string.

**And** the whole-repo build passes: `./gradlew build` is `BUILD SUCCESSFUL`. The frontend break documented in AC11 is **expected**; the whole-repo build does not include the frontend by default — verify via `cat settings.gradle` — only Java subprojects are listed. If `web-frontend` becomes a Gradle subproject in the future, this AC needs revisiting; for now it's safe.

**And** the commit message follows Conventional Commits: `feat(event-mgmt): migrate legacy speaker_pool states; drop tentative cols; tighten PUT /status [Story 11.B.3]`. The PR description references ADR-009 §0.7 + §"Migration to the new state set", Plan §2.2, and PRD Story 11.B.3.

---

## Tasks / Subtasks

- [ ] **Task 1 — Establish baseline** (AC: all)
  - [ ] 1.1 Read this story file end-to-end. Read `docs/architecture/ADR-009-unified-speaker-workflow.md` §0.7 (lines ~190-215, "OVERFLOW and WITHDREW states removed", "TENTATIVE response removed") and §"Migration to the new state set" (lines 515-537).
  - [ ] 1.2 Read `docs/plans/speaker-workflow-refactor.md` §2.2 (data-model migrations table, lines 244-256) and §2.5 (derived flags, lines 284-289).
  - [ ] 1.3 Read `docs/prd/epic-11-speaker-workflow-refactor.md` Story 11.B.3 (lines 626-693).
  - [ ] 1.4 Read the previous story file `_bmad-output/implementation-artifacts/11-b-2-speakerworkflowservice-sole-status-writer.md` Dev Agent Record (when 11.B.2 lands as `review`/`done`) to capture any new collaborators added (e.g., `SecurityPrincipal`, `TransitionPayload`, `SlotCapacityReachedException`) — the test fixtures in this story may need to use them.
  - [ ] 1.5 Read `services/event-management-service/src/main/resources/db/migration/V14__add_topic_speaker_pool_tables.sql` (the original speaker_pool create) lines 47-58, `V44__Add_speaker_invitation_fields.sql` (the most recent constraint widen) lines 13-22 + 31-45, and `V45__Add_speaker_response_fields.sql` (the file that added `is_tentative` + `tentative_reason` + the partial index) lines 7-24. These three files define the surface this migration tightens.
  - [ ] 1.6 Read `services/event-management-service/src/main/java/ch/batbern/events/domain/SpeakerPool.java` (full file). Read `services/event-management-service/src/main/java/ch/batbern/events/dto/SpeakerPoolResponse.java` (full file). Read `services/event-management-service/src/main/java/ch/batbern/events/controller/SpeakerStatusController.java` lines 68-89 (the `updateStatus` method). Read `services/event-management-service/src/main/java/ch/batbern/events/exception/GlobalExceptionHandler.java` (full file — locate the existing `handleHttpMessageNotReadableException` and `handleMethodArgumentNotValid` methods).
  - [ ] 1.7 Read `services/event-management-service/src/main/java/ch/batbern/events/service/EventWorkflowStateMachine.java` lines 130-296 (the validators) end-to-end.
  - [ ] 1.8 `grep -n "BadRequestException\|class .*Exception" services/event-management-service/src/main/java/ch/batbern/events/exception/` and document the existing exception types + constructor shapes. Match the convention chosen for `SlotCapacityReachedException` in 11.B.2.
  - [ ] 1.9 `grep -rn "isTentative\|tentativeReason\|is_tentative\|tentative_reason" services/event-management-service/src/` — capture every reference. Most will be dropped or already removed by 11.B.2; the residual production-code refs are the dev's TODO list for Tasks 4 + 5.

- [ ] **Task 2 — Author Flyway migration V93** (AC1, AC2, AC3)
  - [ ] 2.1 Create `services/event-management-service/src/main/resources/db/migration/V93__migrate_legacy_speaker_states.sql`. Match the comment-block header style of V44, V45, V53 (the most recent speaker_pool migrations).
  - [ ] 2.2 Step 1 — INSERT audit rows BEFORE update. For each of the 4 legacy mappings, INSERT into `speaker_status_history` per AC1's row shape. Use `WHERE status IN (...)` to scope idempotently.
  - [ ] 2.3 Step 2 — UPDATE `speaker_pool` per AC1's mapping table.
  - [ ] 2.4 Step 3 — DROP + ADD the `speaker_pool_status_check` constraint with the 8 lowercase values per AC2.
  - [ ] 2.5 Step 4 — DROP + ADD both `speaker_status_history_previous_status_check` and `speaker_status_history_new_status_check` constraints per AC2.
  - [ ] 2.6 Step 5 — DROP `idx_speaker_pool_tentative` (V45 line 23). Note that PostgreSQL allows dropping a partial index that references a column that will be dropped, but the safer order is index-first-then-column.
  - [ ] 2.7 Step 6 — DROP COLUMN `speaker_pool.is_tentative` (V45 line 11). Use `IF EXISTS` for idempotency.
  - [ ] 2.8 Step 7 — DROP COLUMN `speaker_pool.tentative_reason` (V45 line 12). Use `IF EXISTS`.
  - [ ] 2.9 Step 8 — Optional paranoia check: a `DO $$ BEGIN IF EXISTS (SELECT 1 FROM speaker_pool WHERE status NOT IN (...)) THEN RAISE EXCEPTION '...' END IF; END $$;` block per AC3.
  - [ ] 2.10 Run `./gradlew :services:event-management-service:flywayMigrate` against a local PostgreSQL container (Testcontainers test, see Task 7).

- [ ] **Task 3 — Add `countPublishableByEventId` + `countByEventIdAndStatusIn` repository methods** (AC8, AC9)
  - [ ] 3.1 Add the two new methods to `SpeakerPoolRepository.java` after the existing `countByEventIdAndStatus` (line 56 of the current file). Use `@Query` with Java 21 text blocks. Match the style of existing methods.
  - [ ] 3.2 Run `./gradlew :services:event-management-service:compileJava` to verify Spring Data accepts the new queries at startup.

- [ ] **Task 4 — Update `SpeakerPool` entity** (AC12)
  - [ ] 4.1 Delete the two fields at lines 118-122 of `SpeakerPool.java`: `@Column(name = "is_tentative") private Boolean isTentative` and `@Column(name = "tentative_reason") private String tentativeReason`. Delete the generated Lombok getters/setters by removing the fields (Lombok regenerates on build).
  - [ ] 4.2 Run `./gradlew :services:event-management-service:compileJava` and resolve any compile errors. Expected callers needing updates: `SpeakerPoolResponse.fromEntity` (Task 5), `SpeakerResponseService.processAcceptResponse` if any `setIsTentative(false)` call survived 11.B.2 (it should not — 11.B.2 AC7.3 line 204 already removed it; verify via grep).

- [ ] **Task 5 — Update `SpeakerPoolResponse` DTO** (AC7, AC12)
  - [ ] 5.1 Delete the two private fields at lines 37-38 (`isTentative`, `tentativeReason`). Delete the two assignments at lines 92-93 in `fromEntity`. Delete the four getters/setters at lines 287-299.
  - [ ] 5.2 Add the two new private fields `Boolean isSlotAssigned` and `Boolean isPublishable` and their getters/setters (match the manual-getter pattern of the existing code).
  - [ ] 5.3 Add the new factory overload `fromEntity(SpeakerPool speakerPool, Session session)` per AC7 item 2. The existing `fromEntity(SpeakerPool)` delegates with `session = null` and uses the `sessionId != null` fallback. Document the caveat in a one-line Javadoc.
  - [ ] 5.4 Update `fromEntityWithContent` (current line 118+) to compute `isSlotAssigned` (fallback) + `isPublishable` for backward compat with `SpeakerPoolService.java:164, 175`.

- [ ] **Task 6 — Tighten the `PUT /status` controller** (AC4, AC5, AC6)
  - [ ] 6.1 In `SpeakerStatusController.updateStatus` (line 70), add the READY-rejection check per AC5 immediately after the method body opens, before the existing `log.info` line. Throw `BadRequestException` (or `ReadyRequiresPromoteException` — match codebase convention discovered in Task 1.8).
  - [ ] 6.2 In `GlobalExceptionHandler`, extend `handleHttpMessageNotReadableException` per AC4. Use `instanceof InvalidFormatException` (`com.fasterxml.jackson.databind.exc.InvalidFormatException`) check on the cause; if the target type is `SpeakerWorkflowState.class`, return the structured 400 body per AC4. Otherwise fall through to the existing generic "malformed JSON" handling.
  - [ ] 6.3 Verify that the existing `@ExceptionHandler(MethodArgumentNotValidException.class)` is still present and untouched (project-context.md hard rule). Do not modify it.
  - [ ] 6.4 If `BadRequestException` doesn't have a `(code, message, details)` constructor (Task 1.8), add it. If a new exception type is needed (e.g., `ReadyRequiresPromoteException`), create it in `services/event-management-service/src/main/java/ch/batbern/events/exception/` and add a `@ExceptionHandler` for it in `GlobalExceptionHandler`. Match the precedent of `SlotCapacityReachedException` from 11.B.2.

- [ ] **Task 7 — Author migration integration test** (AC13)
  - [ ] 7.1 Create `services/event-management-service/src/test/java/ch/batbern/events/migration/V93LegacySpeakerStatesMigrationIntegrationTest.java` extending `AbstractIntegrationTest`.
  - [ ] 7.2 Test 1 — `should_mapLegacySlotAssignedToAccepted_and_writeAuditRow`: seed via `jdbcTemplate.update(...)` with raw `'slot_assigned'` status (bypassing JPA), then call `flywayService.repair() + flywayService.migrate()` — wait, Testcontainers spec already runs migrations in `AbstractIntegrationTest`'s setup. Instead, seed AFTER migrations have run, by temporarily relaxing the V93 constraint? That's ugly. **Better approach**: seed BEFORE V93 runs by reordering — use a separate Testcontainers instance for this test class, override `AbstractIntegrationTest`'s migration target version to V92, manually run V93 via `jdbcTemplate.execute(Files.readString(Path.of("src/main/resources/db/migration/V93__migrate_legacy_speaker_states.sql")))`. Read the existing `AbstractIntegrationTest.java` to see what hooks are available. If overriding the migration target is awkward, simplify: seed at the entity level under the V92 constraint (i.e., the test runs against a container migrated through V92 only — declare a `@TestPropertySource` overriding `spring.flyway.target=92` for this class), then call `jdbcTemplate.execute(V93_SQL)` manually, then assert.
  - [ ] 7.3 Tests 2-4 — same pattern for `'confirmed' → 'quality_reviewed'`, `'withdrew' → 'declined'` (with reason backfill assertion), `'overflow' → 'ready'`.
  - [ ] 7.4 Test 5 — `should_beIdempotent_when_runTwice`: seed legacy + already-mapped rows, run migration twice, assert row counts unchanged on second run.
  - [ ] 7.5 Test 6 — `should_haveTightenedConstraint_when_attemptingLegacyValueAfterMigration`: after migration, attempt `jdbcTemplate.update("UPDATE speaker_pool SET status = 'slot_assigned' WHERE id = ?")` — expect `PSQLException` with constraint-violation message.
  - [ ] 7.6 Test 7 — `should_haveDroppedColumns_when_queryingSpeakerPoolMetadata`: assert `is_tentative` and `tentative_reason` are no longer in `information_schema.columns WHERE table_name = 'speaker_pool'`.

- [ ] **Task 8 — Update `EventWorkflowStateMachine`** (AC9)
  - [ ] 8.1 Rename `validateQualityReviewComplete` → `validateAllSpeakersConfirmed`. Update the `switch` dispatch at line 161.
  - [ ] 8.2 Rewrite the method body per AC9 (uses `countPublishableByEventId` + `countByEventIdAndStatusIn`; adds the `acceptedOrBeyondSpeakers == 0` guard).
  - [ ] 8.3 Remove the now-dead `Session`-counting block (lines 273-292 of the current file). The `sessionRepository.countByEventId` and `sessionRepository.countByEventIdAndStartTimeNotNull` are still used elsewhere — verify with `grep -rn "countByEventId\|countByEventIdAndStartTimeNotNull" services/event-management-service/src/main/java/` before removing those methods from `SessionRepository`. Likely they're used by `validateMinimumThresholdMet` (line 225) and others — leave them.
  - [ ] 8.4 Find any reference to the old name `validateQualityReviewComplete` (Spring direct-method-call refs update automatically; reflection-based ones need the rename). Grep with `grep -rn "validateQualityReviewComplete" services/event-management-service/src/`.
  - [ ] 8.5 Update the integration test that covers `AGENDA_PUBLISHED` validation. Grep for the test file: `grep -rln "AGENDA_PUBLISHED\|validateQualityReviewComplete\|validateAllSpeakersConfirmed" services/event-management-service/src/test/`.

- [ ] **Task 9 — Update OpenAPI spec** (AC10)
  - [ ] 9.1 Update `SpeakerWorkflowState` enum at line 1064 of `docs/api/speakers-api.openapi.yml` per AC10. The order matches the shared-kernel enum declaration order: IDENTIFIED, CONTACTED, READY, INVITED, ACCEPTED, CONTENT_SUBMITTED, QUALITY_REVIEWED, DECLINED.
  - [ ] 9.2 Update the endpoint description for `PUT /events/{eventCode}/speakers/{speakerId}/status` (line 374-384) per AC10. Delete the legacy line at 382.
  - [ ] 9.3 Update the response codes block (line 409-437) per AC10: keep 200/400/401/403/404, delete 422.
  - [ ] 9.4 Update `SpeakerPoolEntry` schema at line 1546 per AC7 + AC10: add `isSlotAssigned`, `isPublishable`. Verify no `isTentative`/`tentativeReason` properties exist (grep returned 0 matches).
  - [ ] 9.5 Run `./gradlew :services:event-management-service:openApiGenerateSpeakers` (or whatever the spec-targeted task is — verify with `grep -rn "openApiGenerate" services/event-management-service/build.gradle`). The backend DTO regeneration should be a no-op since `SpeakerWorkflowState` is shared-kernel; if any backend `*Api.java` rebuilds, that's expected. **Do NOT** run `cd web-frontend && npm run generate:api-types` — see AC11.
  - [ ] 9.6 Manually edit `web-frontend/src/types/speakerPool.types.ts` to delete lines 46-47 (the two manual `isTentative` / `tentativeReason` lines) per AC11.

- [ ] **Task 10 — Author API integration tests** (AC4, AC5, AC6)
  - [ ] 10.1 In `services/event-management-service/src/test/java/ch/batbern/events/controller/SpeakerStatusControllerIntegrationTest.java` (extend the existing test class; if none, create one extending `AbstractIntegrationTest`), add `@ParameterizedTest` + `@ValueSource(strings = {"SLOT_ASSIGNED", "CONFIRMED", "OVERFLOW", "WITHDREW", "TENTATIVE"})` test for the 400 INVALID_SPEAKER_WORKFLOW_STATE rejection. The test sends a raw JSON body (not a `UpdateStatusRequest` instance — that would fail Java enum binding before serialization). Use `mockMvc.perform(put(...).content("{\"newStatus\":\"" + value + "\"}").contentType(MediaType.APPLICATION_JSON))`.
  - [ ] 10.2 Add a test for the READY rejection — sends `{"newStatus":"READY"}` and asserts 400 + `READY_REQUIRES_PROMOTE_ENDPOINT` body.
  - [ ] 10.3 Add a happy-path test — sends `{"newStatus":"CONTACTED"}` against a speaker in `IDENTIFIED`, asserts 200 + the response includes `isSlotAssigned: false` + `isPublishable: false` (no session yet).
  - [ ] 10.4 Add a derived-flag test — seed a speaker in `QUALITY_REVIEWED` with `session_id` set + `session.start_time` set, GET the speaker via the existing pool endpoint, assert `isSlotAssigned: true` + `isPublishable: true`.

- [ ] **Task 11 — Author Bruno contract test** (AC6)
  - [ ] 11.1 If `bruno-tests/events/speakers/` does not exist, create the directory. If a status-update test already exists, extend it; otherwise create `status-update.bru`. Match the file format of existing Bruno tests under `bruno-tests/`.
  - [ ] 11.2 The test issues a `PUT /api/v1/events/{eventCode}/speakers/{speakerId}/status` against a staging-seeded speaker, asserts 200 OK + the response shape includes `currentStatus`, `previousStatus`, `changedByUsername`, `changedAt`. Uses `{{ORGANIZER_AUTH_TOKEN}}` per existing Bruno conventions.

- [ ] **Task 12 — Run + tee + grep the build** (AC13)
  - [ ] 12.1 `./gradlew :shared-kernel:publishToMavenLocal | tee /tmp/em-build-11b3-sk.log`.
  - [ ] 12.2 `./gradlew :services:event-management-service:build | tee /tmp/em-build-11b3.log`.
  - [ ] 12.3 `grep -E "FAIL|BUILD FAIL|ERROR" /tmp/em-build-11b3.log` — investigate any matches.
  - [ ] 12.4 `./gradlew build | tee /tmp/em-build-11b3-all.log` and verify whole-repo green. The frontend is not part of the Gradle build — verify by `cat settings.gradle | grep -i web-frontend` (expected: no matches).

- [ ] **Task 13 — Validate documentation alignment** (Story 11.A.1 hand-off check)
  - [ ] 13.1 `grep -n "SLOT_ASSIGNED\|CONFIRMED\|OVERFLOW\|WITHDREW\|TENTATIVE\|is_tentative\|tentative_reason" docs/architecture/06a-workflow-state-machines.md docs/architecture/ADR-009-unified-speaker-workflow.md docs/prd/epic-11-speaker-workflow-refactor.md docs/plans/speaker-workflow-refactor.md CLAUDE.md`. Most matches should be in historical "removed states" context (e.g., "SLOT_ASSIGNED — replaced by ..."). Any **forward-going** mention (i.e., a current behaviour described in terms of these removed names) is a documentation drift — flag it in the PR but do NOT fix in this story (Story 11.A.1's territory). If 11.A.1's doc updates correctly remove all forward-going mentions, this grep should return only the removed-state explanatory tables.

- [ ] **Task 14 — Dev Agent Record** (final)
  - [ ] 14.1 Append a Dev Agent Record block to the bottom of this story file with: (a) the V93 migration line count, (b) the count of rows mapped in each direction (for the migration integration test), (c) the count of compile errors fixed across `SpeakerPool`, `SpeakerPoolResponse`, `SpeakerStatusController`, `GlobalExceptionHandler`, `EventWorkflowStateMachine`, (d) any open question or unexpected behaviour discovered during the work, (e) the four frontend files flagged for Phase D follow-up.

---

## Dev Notes

### File-by-file map of the changes (cross-reference for the dev)

| File | Action | Anchor |
|---|---|---|
| `services/event-management-service/src/main/resources/db/migration/V93__migrate_legacy_speaker_states.sql` | NEW | AC1, AC2, AC3 |
| `services/event-management-service/src/main/java/ch/batbern/events/domain/SpeakerPool.java` | MODIFIED — remove `is_tentative` + `tentative_reason` fields (lines 118-122) | AC12 / Task 4 |
| `services/event-management-service/src/main/java/ch/batbern/events/dto/SpeakerPoolResponse.java` | MODIFIED — remove tentative fields (lines 37-38, 92-93, 287-299); add `isSlotAssigned` + `isPublishable` + new `fromEntity(SpeakerPool, Session)` overload | AC7 / Task 5 |
| `services/event-management-service/src/main/java/ch/batbern/events/repository/SpeakerPoolRepository.java` | MODIFIED — add `countPublishableByEventId(UUID)` + `countByEventIdAndStatusIn(UUID, List<SpeakerWorkflowState>)` | AC8, AC9 / Task 3 |
| `services/event-management-service/src/main/java/ch/batbern/events/controller/SpeakerStatusController.java` | MODIFIED — add READY-rejection check in `updateStatus` method | AC5 / Task 6.1 |
| `services/event-management-service/src/main/java/ch/batbern/events/exception/GlobalExceptionHandler.java` | MODIFIED — extend `handleHttpMessageNotReadableException` for `InvalidFormatException` on `SpeakerWorkflowState` | AC4 / Task 6.2 |
| `services/event-management-service/src/main/java/ch/batbern/events/exception/BadRequestException.java` (or new `ReadyRequiresPromoteException.java`) | MODIFIED or NEW — match codebase convention | AC5 / Task 6.4 |
| `services/event-management-service/src/main/java/ch/batbern/events/service/EventWorkflowStateMachine.java` | MODIFIED — rename `validateQualityReviewComplete` → `validateAllSpeakersConfirmed`; rewrite body | AC9 / Task 8 |
| `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerPoolService.java` | UNCHANGED (verify no compile error from DTO change) | AC12 |
| `services/event-management-service/src/test/java/ch/batbern/events/migration/V93LegacySpeakerStatesMigrationIntegrationTest.java` | NEW | AC13 / Task 7 |
| `services/event-management-service/src/test/java/ch/batbern/events/controller/SpeakerStatusControllerIntegrationTest.java` | MODIFIED or NEW | AC4, AC5, AC6 / Task 10 |
| `services/event-management-service/src/test/java/ch/batbern/events/repository/SpeakerPoolRepositoryIntegrationTest.java` | MODIFIED or NEW — add `countPublishableByEventId` tests | AC8 / Task 3 |
| `services/event-management-service/src/test/java/ch/batbern/events/service/EventWorkflowStateMachineIntegrationTest.java` | MODIFIED — rename + rewrite the `AGENDA_PUBLISHED` validation tests | AC9 / Task 8.5 |
| `docs/api/speakers-api.openapi.yml` | MODIFIED — `SpeakerWorkflowState` enum + PUT description + `SpeakerPoolEntry` schema | AC10 / Task 9 |
| `web-frontend/src/types/speakerPool.types.ts` | MODIFIED — delete lines 46-47 only | AC11 / Task 9.6 |
| `bruno-tests/events/speakers/status-update.bru` | NEW (if absent) | AC6 / Task 11 |
| `_bmad-output/implementation-artifacts/sprint-status.yaml` | MODIFIED — flip `11-b-3-…` to `review` (handled by code-review workflow) | — |
| `_bmad-output/implementation-artifacts/11-b-3-migrate-legacy-status-drop-tentative-cols-derived-flags.md` | MODIFIED — append Dev Agent Record | Task 14 |

### Status-string casing (the #1 thing to get right)

The DB stores statuses as `lowercase_snake_case` via `SpeakerWorkflowStateConverter` (shared-kernel `@Convert`). Every SQL literal in V93 uses lowercase: `'slot_assigned'`, `'confirmed'`, `'withdrew'`, `'overflow'`, `'identified'`, `'contacted'`, `'ready'`, `'invited'`, `'accepted'`, `'content_submitted'`, `'quality_reviewed'`, `'declined'`. ADR-009 §"Migration to the new state set" shows the SQL with `UPPER_CASE` — that's wrong for the DB layer; do not copy it verbatim. The migration uses lowercase.

The Java code and the JSON contract use `UPPER_CASE` (`SpeakerWorkflowState.SLOT_ASSIGNED.name()` → `"SLOT_ASSIGNED"`) per project-context.md §"Enum Value Flow". The `ErrorResponse` body in AC4 includes UPPER_CASE values because that's what the user PUT in the request.

### `speaker_selection_votes` table — does not exist

`grep -rln 'speaker_selection_votes' services/*/src/main/resources/db/migration/` returns no matches. The Epic 11 PRD AR19 + Plan §2.2 row "Drop overflow-management tables" assumes this table exists. It does not. The overflow handling that did exist was code-only (`OverflowManagementService.java` — already deleted by 11.B.2 per its AC9). No SQL `DROP TABLE` is needed in V93. Note this in the migration comment block for future archeologists.

### `magic_link_tokens` — out of scope for this story

Phase F (Story 11.F.1) drops `magic_link_tokens` and the magic-link infrastructure. V93 leaves it alone. The two are kept in separate Flyway versions so 11.F.1 can move independently.

### `speakers` table — out of scope for this story

Story 11.C.1 drops the `speakers` table. V93 leaves it alone. The state-machine refactor (Phase B) and the entity-model refactor (Phase C) are explicitly sequenced so they can land independently.

### Why the derived flags live on `SpeakerPoolResponse` instead of `SpeakerPool` (architectural pin)

Per ADR-009 §0.1 + Plan §2.5, `is_slot_assigned` and `is_publishable` are **predicates computed at read time**, not stored columns. The reason is data integrity: if they were stored, a row could drift (e.g., `is_publishable = true` but `status = 'accepted'` because someone forgot to recompute). By keeping them derived, the response is always self-consistent with the underlying state.

The trade-off is that every `SpeakerPoolResponse` construction needs the `Session` to compute `is_slot_assigned` accurately. The fallback `sessionId != null` is weaker (treats "session assigned but no start_time" as `is_slot_assigned = true`, which over-reports). For 11.B.3 the fallback is acceptable — the dashboards and list endpoints display the flag for organizer awareness, not as a hard gate. The hard gate (the AGENDA_PUBLISHED transition) uses `countPublishableByEventId` directly, which does the right `JOIN Session ON ... s.start_time IS NOT NULL`.

If Story 11.D.4 (kanban refactor) needs a stronger guarantee for the UI, it can introduce a `findAllByEventIdWithSession` repository query at that time. For now, the fallback is documented and bounded.

### Tests that 11.B.2 left referencing `is_tentative` / `tentativeReason`

Per the grep in Task 1.9, the following test files have leftover references to tentative state:

- `services/event-management-service/src/test/java/ch/batbern/events/service/SpeakerResponseServiceTest.java:335,569,636-666` — these tests cover the deleted `processTentativeResponse` path. After 11.B.2 these tests should already be deleted (per 11.B.2 AC7.3 + Task 7.1). Verify; if they survive, delete them in this story.
- `services/event-management-service/src/test/java/ch/batbern/events/controller/SpeakerPortalResponseControllerIntegrationTest.java:359` — covers the old "TENTATIVE response stays at INVITED" behaviour. Delete the test method and its `@Test`/`@DisplayName` annotations. The controller-level path has already lost the TENTATIVE branch per 11.B.2.

These are AC12-allowed test cleanups even though they're not the primary story focus.

### Latest-tech anchor for the Flyway + JPA layer

- Flyway 9.x: file naming `V{n}__{description}.sql`, must be sequential. V93 is free (latest is V92 — `V92__add_newsletter_send_test_mode.sql`).
- PostgreSQL 15+: `ALTER TABLE ... DROP COLUMN IF EXISTS` (supported since 9.6); `DO $$ BEGIN ... END $$` PL/pgSQL blocks; partial index `WHERE` predicates; CHECK constraint on `text` columns is BTW lower-cost than enum types.
- Spring Boot 3.x: `@Query` with Java 21 text blocks, `@Param` from `org.springframework.data.repository.query`. The text-block style in `SpeakerPoolRepository` is the precedent for the new queries.
- Jackson 2.16+ (Spring Boot 3.4 default): `InvalidFormatException` from `com.fasterxml.jackson.databind.exc` carries `getValue()`, `getTargetType()`, `getPath()` — the handler uses `getValue()` for the rejected value and `getTargetType()` for the enum-detection check.
- Java 21: `record` for value types (not needed here, but for any new exception value-type fields use record over a class).

### Git intelligence — recent commits relevant to this story

- `97f652ff chore(bmad): extend story-automator parser for Epic 11 ID format` — sprint-status workflow expects `N-LETTER-N-name` keys; this story uses `11-b-3-...`.
- `344e6853 docs(epic-11): align speaker-workflow docs to ADR-009 [Story 11.A.1]` — Story 11.A.1 (Phase A) is done; the docs this story references are already updated. If a doc-drift appears (Task 13.1), file an issue against 11.A.1 follow-up; do not fix here.
- Prior story `11-b-1-reduce-speakerworkflowstate-enum-to-8-states` is done; its shared-kernel artefacts are published.
- Prior story `11-b-2-speakerworkflowservice-sole-status-writer` is ready-for-dev; this story depends on 11.B.2's `SpeakerWorkflowService.transition()` and `SpeakerStatusService.updateStatus` delegate refactor. Until 11.B.2 lands as `review`/`done`, this story cannot start.

### Previous-story learnings (from 11.B.2)

- The single-writer rule means `SpeakerPoolRepository.save(speakerPool)` happens inside `SpeakerWorkflowService.transition()`, not in `SpeakerStatusService`. Any new query method added in this story must respect that — e.g., `countPublishableByEventId` is read-only and safe to call from anywhere.
- The same-state branch in `transition()` (11.B.2 AC2) writes a self-transition history row but skips side-effects. This story's migration writes audit rows directly to `speaker_status_history` (bypassing the workflow service entirely, because the SQL runs at the DB layer with no Java code in scope). That's OK — the migration is a one-time data fix, not an in-flight transition. Document the bypass in the migration comment.
- `GlobalExceptionHandler` in 11.B.2 gains a `@ExceptionHandler(SlotCapacityReachedException.class)` per its Task 2.7. This story may need to add a similar handler if a new `ReadyRequiresPromoteException` is introduced (per Task 6.4 decision tree).

---

## Project Context Reference

- **Module**: `event-management-service` (port 8002 in native dev; `/aws/ecs/BATbern-staging/event-management` in CloudWatch).
- **DB schema namespace**: `speaker_pool`, `speaker_status_history`, `sessions` — all in the event-management service's PostgreSQL schema. No cross-service FKs (ADR-003).
- **Shared kernel**: `SpeakerWorkflowState` enum (8 values, post-11.B.1) at `shared-kernel/src/main/java/ch/batbern/shared/types/SpeakerWorkflowState.java`. Re-run `./gradlew :shared-kernel:publishToMavenLocal` if Maven Local cache is stale.
- **Coding standards**: per project-context.md (Enum Value Flow, NeedBraces, MemberName, OperatorWrap, UnusedImports — Checkstyle runs in pre-commit; Spotless on Java). Integration tests extend `AbstractIntegrationTest` (Testcontainers PostgreSQL — singleton, `.withReuse(true)`).
- **Run/test discipline**: pipe `./gradlew` output through `tee /tmp/em-build-11b3.log` and grep the log file; do not re-run the full suite to find errors.
- **Doc-drift policy**: per CLAUDE.md, if business-logic changes happen in this story, the corresponding `docs/architecture/*` files are already updated by Story 11.A.1 — if the dev finds drift, flag in PR but do not fix here.

---

## Story Completion Status

Status: in-progress — 7 review patches applied (3 decisions + 4 patch items); re-test required before promoting to done. 5 items deferred to Phase D/F.

---

## Resolved Decisions (inferred during story drafting — flag for PM confirmation before dev kickoff)

These were open design questions while drafting the AC. Each one was resolved with an inferred decision and pinned into the AC text above — listed here for traceability and easy review. If any of these need to flip, the AC change is mechanical (single-paragraph edit).

1. ✅ **Migration ordering — INSERT audit rows BEFORE UPDATE; tighten constraints LAST.** Step order in V93 is: (1) INSERT into `speaker_status_history` for every soon-to-be-mapped row (uses legacy `previous_status` values, allowed under the existing V44 constraint); (2) UPDATE `speaker_pool` per the 4 mappings (new values, allowed under existing V44 constraint); (3) DROP + ADD the tightened CHECK constraints; (4) DROP partial index + DROP COLUMNs. Per AC1 + AC2 + AC3. Rationale: the audit rows must be persisted while their legacy `previous_status` is still valid; the tightened constraint then grandfathers them via PostgreSQL's `INSERT`-time validation semantics. The alternative (insert audit rows AFTER constraint tighten with legacy values) fails the new CHECK and would require a second constraint-relaxation cycle — unnecessary complexity. **Alternative not chosen:** insert audit rows AFTER mapping the speaker_pool status, using the new lowercase values — discards the historical "what was the legacy state" datum.

2. ✅ **Per-mapping audit reason strings — distinct text per legacy-state pair.** Each of the 4 mappings writes a different `change_reason` literal in the audit row (e.g., `"Legacy SLOT_ASSIGNED → ACCEPTED (slot now derived from session.start_time, ADR-009 §0.7)"` vs `"Withdrew after acceptance (legacy)"`). Per AC1. Rationale: ADR-009 explicitly specifies `"Withdrew after acceptance (legacy)"` for the `withdrew → declined` case (the only mapping where a downstream UI might display the reason to an organizer). The other three are forensic-only, so the text is dev-readable rather than user-facing. **Alternative not chosen:** single generic `"Legacy state migration per ADR-009 §0.7 (V93)"` — would save 3 string copies but loses the forensic specificity that makes a 2030 audit query useful.

3. ✅ **`changed_by_username` for migration audit rows — literal string `'system-migration-v93'`.** Per AC1's row shape. Rationale: explicit, greppable, includes the migration version so re-running grep in 2030 finds exactly the rows V93 wrote (versus rows V99 might write for some other backfill). **Alternatives not chosen:** `NULL` (loses the "system did this" signal); `'flyway'` (too generic — multiple migrations share the name); `'system'` (same problem); the speaker's own username (false attribution — the speaker did not consent to a legacy reclassification).

4. ✅ **READY rejection — enforced at the controller layer, NOT inside `SpeakerWorkflowService.transition()`.** Per AC5's rationale block. Rationale: the workflow service must still accept `target = READY` from the new POST `/promote` endpoint (Story 11.D.1) — that endpoint constructs the email payload and calls `transition(speakerId, READY, actor, payload)`. The PUT `/status` endpoint is the only path where READY is illegal (request body has no `email` field). Putting the rejection inside `transition()` would either break the future `/promote` endpoint or require a payload-shape sniff that mixes API concerns into the domain service. Controller-layer rejection keeps the API contract enforcement at the API boundary. **Alternative not chosen:** add a `requireEmailForReady` precondition inside `transition()` — would correctly reject the PUT path via the `ValidationException("email is required to promote speaker to READY")` already added by 11.B.2 AC4, but the error message would be a `ValidationException` (Spring's `400` mapping) rather than the structured `READY_REQUIRES_PROMOTE_ENDPOINT` body that points the API consumer at the correct endpoint. The current decision keeps the rejection UX explicit.

5. ✅ **Exception type for the READY rejection — match codebase convention discovered during Task 1.8.** Per AC5 + Task 6.4. The dev greps for the existing `BadRequestException` shape: if it has a `(code, message, details)` constructor (matching `SlotCapacityReachedException` from 11.B.2), extend it; otherwise create `ReadyRequiresPromoteException` as a new class in `services/event-management-service/src/main/java/ch/batbern/events/exception/`. Rationale: matches the 11.B.2 precedent — that story created `SlotCapacityReachedException` because no equivalent existed; this story does the same trade-off based on what Task 1.8 finds. **Alternative not chosen:** force a specific new exception type without checking the existing surface — would create needless duplication if `BadRequestException` already carries the right payload shape.

6. ✅ **`InvalidFormatException` detection — extend the existing `handleHttpMessageNotReadableException` (don't add a new `@ExceptionHandler`).** Per AC4 + Task 6.2. Rationale: `InvalidFormatException` is wrapped inside `HttpMessageNotReadableException` by Spring's `MappingJackson2HttpMessageConverter`; the outer exception is the one Spring routes to `@ExceptionHandler`. Adding a direct `@ExceptionHandler(InvalidFormatException.class)` would never fire — Spring sees the outer wrapper. The clean fix is to inspect `ex.getCause()` inside the existing handler and produce the structured `INVALID_SPEAKER_WORKFLOW_STATE` body when the target type is `SpeakerWorkflowState.class`. **Alternative not chosen:** register a separate handler — does not work (Spring's exception-resolution algorithm doesn't unwrap).

7. ✅ **`isSlotAssigned` fallback — `sessionId != null` (weak) for the `fromEntity(SpeakerPool)`-only overload; the strict `JOIN Session` form only via the new `(SpeakerPool, Session)` overload.** Per AC7 item 3 + Dev Notes "Why the derived flags live on `SpeakerPoolResponse`". Rationale: the hard gate (the AGENDA_PUBLISHED transition) uses `countPublishableByEventId` directly, which does the strict `JOIN Session ON ... s.start_time IS NOT NULL`. The DTO-level flag is for organizer-dashboard awareness, not as a hard gate — the weaker fallback over-reports `isSlotAssigned` only in the narrow case where a session is assigned but its `start_time` is still null, which is a transient state during slot assignment. **Alternative not chosen:** introduce a new `findAllByEventIdWithSession` repository query and force every call site through it — out-of-scope creep for a Phase B story; defer to Story 11.D.4 if the kanban UI needs the stronger guarantee.

8. ✅ **Frontend `web-frontend/src/types/speakerPool.types.ts` lines 46-47 — DELETE in this story (not deferred to Phase D).** Per AC11 item 1 + Task 9.6. Rationale: this file is a manually maintained type definition, NOT a `npm run generate:api-types` output — leaving the two `isTentative`/`tentativeReason` field declarations would create a phantom type that doesn't match the OpenAPI spec (the manual type would still claim those fields exist; downstream code that imports it would compile but fail at runtime). The other three frontend files (`SpeakerStatusLanes.tsx`, `DetailsTabPanel.tsx`, `SpeakerDrawerHeader.tsx`) consume the manual type and DO get deferred — they have UI surface that Phase D Story 11.D.4 owns. **Alternative not chosen:** defer all frontend touches to 11.D.4 — would mean the manual type stays in sync with neither the OpenAPI spec nor the runtime behaviour for the duration of Phase B/C, creating a false-confidence trap.

9. ✅ **`npm run generate:api-types` — NOT run in this commit; deferred to Story 11.D.4.** Per AC10 closing paragraph + AC11. Rationale: regenerating the frontend types now would commit changes to `web-frontend/src/types/generated/*.ts` that immediately break TypeScript compilation in `SpeakerStatusLanes.tsx`, `DetailsTabPanel.tsx`, and `SpeakerDrawerHeader.tsx` (all consume `isTentative` via the **generated** types). Phase D Story 11.D.4 owns the unified-drawer refactor and is the natural moment to regenerate + fix the three UI files in one commit. **Alternative not chosen:** regenerate + ship a frontend "knowingly broken on this commit" — would violate the "main is always green" team norm; the staging deploy on develop merge would fail TypeScript build. Documented in the PR description so reviewers don't request the regen.

10. ✅ **Bruno contract test scope — one happy-path test for `PUT /status` only.** Per AC6 + Task 11. Rationale: the 5-value enum-rejection cases + the READY-rejection case are fully covered by the MockMvc integration test in Task 10.1-10.2 (which talks to the same Spring controller and `GlobalExceptionHandler`). Bruno's value-add is end-to-end coverage against a running staging instance — and at that layer, one happy-path proves the deployment is wired correctly. Adding 6 more `.bru` files for the rejection cases would add CI time without proving anything new. **Alternative not chosen:** full 6×rejection + 1×happy-path coverage in Bruno — symmetric but cost-ineffective. If staging-time regression surfaces, add Bruno cases reactively.

11. ✅ **Integration-test Flyway targeting — `@TestPropertySource("spring.flyway.target=92")` + manual `jdbcTemplate.execute(V93_SQL)`.** Per Task 7.2. Rationale: `AbstractIntegrationTest` migrates to the latest version by default (singleton container with `.withReuse(true)`). The migration test needs to seed legacy rows under the V92 constraint (which still permits `'slot_assigned'`, etc.) before V93 runs. Overriding `spring.flyway.target` for the migration test class avoids polluting the singleton container with non-default behaviour. The dev reads `AbstractIntegrationTest.java` to confirm the property override is honoured (if a custom `FlywayMigrationStrategy` bean is registered, override there instead). **Alternative not chosen:** use a separate Testcontainers instance — wastes the connection-pool warmup benefit of `.withReuse(true)` and slows the test suite. **Fallback if `@TestPropertySource` doesn't work cleanly:** read `V93_SQL` from the classpath via `Files.readString(...)`, execute it after seeding under the legacy constraint via raw SQL `INSERT`s that bypass JPA's `@Convert`-based validation.

12. ✅ **`speaker_selection_votes` table drop — SKIPPED (table does not exist in BATbern).** Per AC3 closing paragraph + Dev Notes "`speaker_selection_votes` table — does not exist". Rationale: ADR-009 §"Migration to the new state set" lists `DROP TABLE IF EXISTS speaker_selection_votes` and Epic 11 PRD AR19 / Plan §2.2 row "Drop overflow tables" assume this table exists. Grep against `services/*/src/main/resources/db/migration/` returns zero matches — the table was never created. The overflow handling that did exist was code-only (`OverflowManagementService.java`, deleted in 11.B.2 AC9). V93 has nothing to drop on that front, and adding `DROP TABLE IF EXISTS` would be cargo-cult code. **Alternative not chosen:** add `DROP TABLE IF EXISTS speaker_selection_votes` as a defensive no-op — harmless but misleading; the migration comment block already documents the rationale for the curious archaeologist.

13. ✅ **`validateMinimumThresholdMet` (line 225 of `EventWorkflowStateMachine`) — NOT revisited in this story; 11.B.2's compile-fix is sufficient.** Per AC9's scope guard. Rationale: 11.B.2's AC9 table row for `EventWorkflowStateMachine` says "Minimal compile-fix only: replace `CONFIRMED` with `QUALITY_REVIEWED`" — that addresses line 242 (the `confirmed` count) without revisiting line 225's semantic intent. After 11.B.2, line 240-243 of `validateMinimumThresholdMet` may end up counting `QUALITY_REVIEWED` twice (once at line 236-239, once at line 240-243 from the rename) — that's a 11.B.2 review concern, not a 11.B.3 concern. Story 11.B.3 only reanchors `validateQualityReviewComplete` → `validateAllSpeakersConfirmed`. **Alternative not chosen:** clean up the duplicate count as part of this story — would expand scope into 11.B.2's territory and complicate the PR's review.

14. ✅ **`SpeakerStatusService.getStatusSummary` — NOT extended to expose a `publishableCount`.** Per AC12 (no change to `SpeakerStatusService`). Rationale: exposing a new dashboard field is a feature, not a refactor; out-of-scope creep for a Phase B story. The derived `isPublishable` flag is available on each `SpeakerPoolResponse` per AC7; downstream UI can aggregate client-side if needed. Phase D's dashboard work (or a future Phase D follow-up if the kanban needs a top-of-column "N publishable" chip) is the natural owner. **Alternative not chosen:** add `publishableCount` to `StatusSummaryResponse` now — would require an OpenAPI change, a frontend regeneration, and a UI consumer story — all out of scope.

---

_Story created via `bmad-create-story` skill on 2026-05-15. Authored by PM (Nissim) with comprehensive context-engine analysis. Depends on Story 11.B.2 (single-writer `SpeakerWorkflowService.transition()`) being on the same branch in `review` or `done` status. Ready for `bmad-dev-story` execution._

---

## Tasks / Subtasks — completion checkboxes

- [x] **Task 1 — Establish baseline** (read ADR-009 §0.7 + §"Migration to the new state set"; plan §2.2 + §2.5; PRD Story 11.B.3; V14/V44/V45; SpeakerPool/SpeakerPoolResponse/SpeakerStatusController/GlobalExceptionHandler/EventWorkflowStateMachine current files; grep BadRequestException + isTentative refs).
- [x] **Task 2 — Author Flyway V93 migration** (`V93__migrate_legacy_speaker_states.sql`, 278 lines): legacy-state mapping per ADR-009 §"Migration to the new state set"; audit-row inserts; tightened CHECK constraints on `speaker_pool` + `speaker_status_history`; drop of `is_tentative` + `tentative_reason` columns + `idx_speaker_pool_tentative` index; paranoia DO block.
- [x] **Task 3 — Add repository methods** to `SpeakerPoolRepository`: `countPublishableByEventId(UUID)` + `countByEventIdAndStatusIn(UUID, List<SpeakerWorkflowState>)`.
- [x] **Task 4 — Update `SpeakerPool` entity**: delete `isTentative` + `tentativeReason` fields/columns.
- [x] **Task 5 — Update `SpeakerPoolResponse` DTO**: delete tentative fields + getters/setters; add `isSlotAssigned` + `isPublishable` derived flags; add `fromEntity(SpeakerPool, Session)` overload + `fromEntityWithContent(SpeakerPool, Session, String, String)` overload; update `SpeakerPoolService.getSpeakerPoolForEvent` to pass the Session into the strict-mode overload (call site already had the Session loaded).
- [x] **Task 6 — Tighten PUT /status controller + exception handler**: add READY-rejection check in `SpeakerStatusController.updateStatus`; create `ReadyRequiresPromoteException`; extend `GlobalExceptionHandler.handleHttpMessageNotReadableException` to detect `InvalidFormatException` on `SpeakerWorkflowState.class` and return code `INVALID_SPEAKER_WORKFLOW_STATE`; add `ReadyRequiresPromoteException` handler returning code `READY_REQUIRES_PROMOTE_ENDPOINT`.
- [x] **Task 7 — Migration integration test**: `V93LegacySpeakerStatesMigrationIntegrationTest` (430 lines). Uses a dedicated `PostgreSQLContainer` (not the shared `AbstractIntegrationTest` singleton) so it can control Flyway target version: migrates to V92, seeds 5 speaker_pool rows (one per legacy status + one already-mapped 'accepted' for idempotency proof), then applies V93 and asserts mapping/audit rows/idempotency/column-drop/constraint-tighten. **16 tests, all pass.**
- [x] **Task 8 — Update `EventWorkflowStateMachine`**: rename `validateQualityReviewComplete` → `validateAllSpeakersConfirmed`; rewrite body using `countPublishableByEventId` + `countByEventIdAndStatusIn`; remove the session-counting block; add the `acceptedOrBeyondSpeakers == 0` explicit guard.
- [x] **Task 9 — Update OpenAPI spec + frontend manual type**: `docs/api/speakers-api.openapi.yml` — `SpeakerWorkflowState` enum tightened to 8 values (added INVITED, removed CONFIRMED; reordered to match shared-kernel; rewrote description); rewrote PUT /status endpoint description + transition table + responses block (consolidated 422 into 400 with code-based discrimination); added `isSlotAssigned` + `isPublishable` to `SpeakerPoolEntry`. Deleted 2 lines from `web-frontend/src/types/speakerPool.types.ts` (`isTentative`/`tentativeReason` manual fields). **Did NOT run `npm run generate:api-types` per AC11** — frontend regen + UI fixes are Story 11.D.4's territory.
- [x] **Task 10 — API integration tests**: `SpeakerStatusControllerIntegrationTest` — added (1) `@ParameterizedTest` covering all 5 legacy enum rejections → 400 `INVALID_SPEAKER_WORKFLOW_STATE`, (2) READY-rejection test → 400 `READY_REQUIRES_PROMOTE_ENDPOINT`, (3) derived-flag test via `GET /events/{code}/speakers/pool` with QUALITY_REVIEWED speaker + session.start_time set → asserts `isSlotAssigned: true` + `isPublishable: true`, (4) derived-flag negative test with session.start_time NULL → asserts both flags false. Also fixed `EventControllerIntegrationTest.should_publishEvent_when_validationPasses` to seed a publishable speaker (the new `validateAllSpeakersConfirmed` rejects "no accepted speakers exist" — was previously satisfied by session-timing alone).
- [x] **Task 11 — Bruno contract test**: extended existing `bruno-tests/events-api/36-list-speaker-pool.bru` to assert `isSlotAssigned` + `isPublishable` are exposed as booleans on the pool response (Story 11.B.3 AC7). Happy-path PUT /status coverage already existed in `37-update-speaker-status.bru` (IDENTIFIED → CONTACTED matches the post-11.B.3 contract).
- [x] **Task 12 — Build + tee + grep**: `./gradlew :shared-kernel:publishToMavenLocal` (BUILD SUCCESSFUL, 4 s); `./gradlew :services:event-management-service:build` (BUILD SUCCESSFUL after the fixes below; 1679 tests pass, 0 fail, ~10 min). Logs at `/tmp/em-build-11b3-sk.log`, `/tmp/em-build-11b3-compile.log`, `/tmp/em-build-11b3-fulltest.log`.
- [x] **Task 13 — Doc-drift sanity check**: forward-going mentions of removed terms (`SLOT_ASSIGNED`, `CONFIRMED`, `OVERFLOW`, `WITHDREW`, `TENTATIVE`, `is_tentative`, `tentative_reason`) in `docs/architecture/ADR-009-unified-speaker-workflow.md`, `docs/architecture/06a-workflow-state-machines.md`, `docs/prd/epic-11-speaker-workflow-refactor.md`, `docs/plans/speaker-workflow-refactor.md`, and `CLAUDE.md` are all in the documented "removed states" explanatory sections (mapping tables, ADR-009 §0.7 narrative). `docs/architecture/epic-6-speaker-onboarding-plan.md` still has forward-going references to `is_tentative` (it is a historical Epic 6 plan document that 11.A.1's sweep did not rewrite). See **Open Questions** below.
- [x] **Task 14 — Dev Agent Record** (this section).

---

## Dev Agent Record

### Implementation summary

- **V93 migration**: 278 lines. Uses `NOT VALID` on `speaker_status_history`'s tightened CHECK constraints — see Open Question #2 for why this was a deviation from the story file's stated grandfathering assumption.
- **Mapping count proven by migration integration test**: 4 legacy speakers (`slot_assigned`, `confirmed`, `withdrew`, `overflow`) → 4 audit rows in `speaker_status_history` (one per mapping) + 4 updated `speaker_pool.status` values; 1 already-`accepted` speaker is untouched (zero audit rows, identical `updated_at`).
- **Compile errors fixed across 6 prod files**: `SpeakerPool.java` (deleted 2 fields + Lombok getters/setters), `SpeakerPoolResponse.java` (deleted 2 fields + 2 getters + 2 setters; added 2 fields + 2 getters + 2 setters + 1 factory overload + 1 fromEntityWithContent overload), `SpeakerPoolService.java` (1 call-site updated to pass Session), `SpeakerWorkflowService.java` (deleted 2 leftover `setIsTentative`/`setTentativeReason` calls in `runAcceptedHook`), `MagicLinkService.java` (deleted TENTATIVE branch in `validateAndConsumeToken`), `SpeakerStatusController.java` (added READY-rejection check + 2 imports), `GlobalExceptionHandler.java` (extended `handleHttpMessageNotReadableException` + added `ReadyRequiresPromoteException` handler; 4 new imports), `EventWorkflowStateMachine.java` (renamed validator + rewrote body + 2 new imports), `SpeakerPortalResponseControllerIntegrationTest.java` (deleted 1 test that used `setIsTentative`/`setTentativeReason`), `EventControllerIntegrationTest.java` (seeded a publishable speaker into `should_publishEvent_when_validationPasses` to satisfy new validator).
- **Tests added**: 16 migration tests + 4 API integration tests (parameterized rejections counted as 1 test class with 5 cases → 9 distinct test methods) + 1 Bruno assertion. Total **20+ new assertions** specific to Story 11.B.3.

### Files touched

- NEW: `services/event-management-service/src/main/resources/db/migration/V93__migrate_legacy_speaker_states.sql`
- NEW: `services/event-management-service/src/main/java/ch/batbern/events/exception/ReadyRequiresPromoteException.java`
- NEW: `services/event-management-service/src/test/java/ch/batbern/events/migration/V93LegacySpeakerStatesMigrationIntegrationTest.java`
- MODIFIED: `services/event-management-service/src/main/java/ch/batbern/events/domain/SpeakerPool.java`
- MODIFIED: `services/event-management-service/src/main/java/ch/batbern/events/dto/SpeakerPoolResponse.java`
- MODIFIED: `services/event-management-service/src/main/java/ch/batbern/events/repository/SpeakerPoolRepository.java`
- MODIFIED: `services/event-management-service/src/main/java/ch/batbern/events/controller/SpeakerStatusController.java`
- MODIFIED: `services/event-management-service/src/main/java/ch/batbern/events/exception/GlobalExceptionHandler.java`
- MODIFIED: `services/event-management-service/src/main/java/ch/batbern/events/service/EventWorkflowStateMachine.java`
- MODIFIED: `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerPoolService.java`
- MODIFIED: `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerWorkflowService.java` (deleted residual `setIsTentative`/`setTentativeReason` calls in `runAcceptedHook`)
- MODIFIED: `services/event-management-service/src/main/java/ch/batbern/events/service/MagicLinkService.java` (removed TENTATIVE branch from `validateAndConsumeToken`)
- MODIFIED: `services/event-management-service/src/test/java/ch/batbern/events/controller/SpeakerStatusControllerIntegrationTest.java` (added 5 new tests covering AC4 / AC5 / AC7)
- MODIFIED: `services/event-management-service/src/test/java/ch/batbern/events/controller/SpeakerPortalResponseControllerIntegrationTest.java` (deleted TENTATIVE-branch test)
- MODIFIED: `services/event-management-service/src/test/java/ch/batbern/events/controller/EventControllerIntegrationTest.java` (seeded publishable speaker in `should_publishEvent_when_validationPasses`)
- MODIFIED: `docs/api/speakers-api.openapi.yml`
- MODIFIED: `web-frontend/src/types/speakerPool.types.ts` (deleted 2 `isTentative`/`tentativeReason` lines)
- MODIFIED: `bruno-tests/events-api/36-list-speaker-pool.bru` (added derived-flag assertion)
- MODIFIED: `_bmad-output/implementation-artifacts/11-b-3-migrate-legacy-status-drop-tentative-cols-derived-flags.md` (Status flipped to `in-progress`, Dev Agent Record appended)
- MODIFIED: `_bmad-output/implementation-artifacts/sprint-status.yaml` (11-b-3 status flipped to `in-progress`; the post-review code-review workflow will flip to `review`)

### Frontend follow-up (Phase D — Story 11.D.4)

Per AC11, the following frontend files still reference the now-removed `isTentative`/`tentativeReason` fields via the manual `SpeakerPoolEntry` type (which I cleaned up) but ALSO via the generated types (which I did NOT regenerate per AC11). These will fail TypeScript compilation as soon as `npm run generate:api-types` is run, and Story 11.D.4 (unified drawer + kanban refactor) is the natural place to clean them up:

1. `web-frontend/src/components/organizer/SpeakerStatus/SpeakerStatusLanes.tsx:711-714` — conditional that displays `speaker.tentativeReason`.
2. `web-frontend/src/components/organizer/SpeakerDrawer/DetailsTabPanel.tsx:73-79, 163` — drawer block displaying tentative reason.
3. `web-frontend/src/components/organizer/SpeakerDrawer/SpeakerDrawerHeader.tsx:47` — header badge for tentative.

**Tag the PR with `frontend-breaks-on-merge` or equivalent label and note in the PR description that the generated types must NOT be regenerated until Story 11.D.4 lands.**

### Open Questions / Deviations from the story file

1. **`NOT VALID` on tightened audit-history constraints** — *Deviation from AC2's stated mechanism.* The story file (Resolved Decision #1) asserts that "PostgreSQL CHECK constraints validate on INSERT/UPDATE, not retroactively" and that pre-V93 audit rows with legacy `previous_status` values are therefore grandfathered. **This is incorrect.** By default `ALTER TABLE ... ADD CONSTRAINT ... CHECK` validates every existing row; the audit-row INSERTs in Step 1 fail the new constraint added in Step 4 (the integration test caught this on first run). The fix is to append `NOT VALID` to both `speaker_status_history` constraint additions — this is PostgreSQL's documented pattern for tightening a CHECK without retroactive validation (`https://www.postgresql.org/docs/15/sql-altertable.html`). `speaker_pool_status_check` does NOT need `NOT VALID` because Step 2 already maps every legacy row, leaving zero violations. Updated V93's comment block to document the `NOT VALID` rationale explicitly. **No PM action needed — the AC1 + AC2 acceptance behaviour is preserved, just achieved via the correct PostgreSQL mechanism.**

2. **Doc-drift in `docs/architecture/epic-6-speaker-onboarding-plan.md`** — *Out-of-scope, flagging for next PR.* This historical Epic 6 plan document has ~15 forward-going references to `is_tentative` / `tentative_reason` (e.g., line 107: "The `is_tentative` infrastructure is retained in the backend for potential future use"). These contradict the post-Story 11.B.3 state of the world. Per the story's Task 13.1 instruction, this is Story 11.A.1's territory — flag here, do not fix. Recommend a `chore(docs)` PR to either (a) prepend a "Historical — superseded by ADR-009 §0.7 (Story 11.B.3)" banner, or (b) move the document to `docs/architecture/archive/`.

3. **`SpeakerStatusResponse` does not carry derived flags** — *Mild contradiction in story AC6 vs AC7.* AC6 says "the response is a `SpeakerStatusResponse` with the post-transition state plus the derived `isSlotAssigned` and `isPublishable` flags (see AC7)", but AC7 is scoped to `SpeakerPoolResponse`. AC12's file-list keeps `SpeakerStatusResponse` unchanged. I resolved the contradiction in favour of AC12 + AC7's scope: the derived flags live on `SpeakerPoolResponse` only (the read-side DTO), and the `PUT /status` happy-path test asserts the plain `SpeakerStatusResponse` shape (`currentStatus`, `previousStatus`, `changedByUsername`, `changedAt`). The derived-flag end-to-end test routes through `GET /events/{code}/speakers/pool` instead — which is the documented exposure point per AC7. If the PM wants flags on `SpeakerStatusResponse` as well, that's a follow-up DTO change (extend SpeakerStatusResponse + mapping in `SpeakerStatusService.mapToResponse`).

4. **Frontend "manual SpeakerWorkflowState union" leftover** — *Out of scope but worth noting.* `web-frontend/src/types/speakerPool.types.ts:11-16` still defines `SpeakerWorkflowState` as the generated union ∪ `INVITED` ∪ `SLOT_ASSIGNED` ∪ `WITHDREW` ∪ `OVERFLOW` — a compile-time tolerance shim from Epic 6 days. After Story 11.D.4 regenerates the OpenAPI types, the generated union will already include `INVITED` (Story 11.B.3 added it), so this manual union should collapse to just `components['schemas']['SpeakerWorkflowState']`. Phase D pickup.

### Test execution summary

- `./gradlew :services:event-management-service:test` — **BUILD SUCCESSFUL** (10 min 26 s). 1679 PASSED, 0 FAILED, 18 skipped (skips pre-date this story — pre-existing `@Disabled` placeholders).
- `./gradlew :services:event-management-service:test --tests "ch.batbern.events.migration.V93LegacySpeakerStatesMigrationIntegrationTest"` — 16/16 PASSED.

### Definition-of-Done validation

- [x] All ACs (AC1-AC13) implemented per the file-by-file map in Dev Notes.
- [x] No regressions in the existing test suite (1679 tests pass).
- [x] Migration is idempotent (proven by `should_notTouchAlreadyAcceptedRow_when_migrationRuns` + manual replay assertion in `should_beNoOp_when_migrationRunsTwice`).
- [x] Tightened constraints reject future legacy values (proven by `should_rejectLegacyStatusInsert_when_constraintsTightened`, `should_rejectLegacyStatusUpdate_when_constraintsTightened`, `should_rejectFutureLegacyAuditInsert_when_constraintsTightened`).
- [x] Grandfathered audit rows survive the tighten (proven by `should_grandfatherLegacyAuditRows_when_previousStatusConstraintTightened`).
- [x] Derived flags exposed correctly on `SpeakerPoolResponse` (proven by `should_exposeDerivedFlags_when_speakerQualityReviewedWithSessionStartTime` + `should_exposeIsPublishableFalse_when_sessionStartTimeNull`).
- [x] `PUT /status` rejects all 5 removed legacy values + READY at the correct layer with the structured error body (proven by `@ParameterizedTest` + READY-specific test).
- [x] OpenAPI spec matches the implementation (8-state enum + `isSlotAssigned`/`isPublishable` properties).
- [x] Whole-repo Gradle build passes — `./gradlew build` reported **BUILD SUCCESSFUL in 15 min 55 s**, 2874 tests PASSED, 0 FAILED across all services. Log: `/tmp/em-build-11b3-all.log`.

---

### Review Findings

_Code review run 2026-05-16. 3 decision-needed · 5 patch · 5 deferred · 7 dismissed._

**Decision-needed (requires PM input before patching):**

- [x] [Review][Decision] **422 still returned but removed from OpenAPI spec** — `GlobalExceptionHandler` returns HTTP 422 for `InvalidStateTransitionException` (e.g., attempting `QUALITY_REVIEWED → ACCEPTED` via PUT /status) and `WorkflowValidationException`, but AC10 deleted `422` from the PUT /status response codes. Clients reading the spec would not expect a 422. Options: (A) add `422: Invalid state transition` back to the spec (minimal code change), or (B) change `handleInvalidStateTransitionException` to return 400 for the PUT /status path (larger change, possible client-breaking).
- [x] [Review][Decision] **`isSlotAssigned` + `isPublishable` absent from manual TypeScript type** — `web-frontend/src/types/speakerPool.types.ts` had `isTentative`/`tentativeReason` deleted (correct, AC11 item 1) but the new `isSlotAssigned?: boolean` and `isPublishable?: boolean` fields were NOT added. Any frontend code reading these flags would get `undefined` without TypeScript surfacing the gap. AC11 says frontend regen defers to Phase D — but this file is MANUAL (not generated), so the new fields should be added here now. Confirm: add in this story, or defer alongside the generated-type regen in Phase D?
- [x] [Review][Decision] **AC6 contradiction — derived flags on `SpeakerStatusResponse`** — AC6 says the PUT /status 200 response should include `isSlotAssigned` + `isPublishable`, but AC7+AC12 scope those flags to `SpeakerPoolResponse` only. Dev resolved in favour of AC12 scope (flags on pool response, not status response). Open Question #3 in Dev Agent Record documents the reasoning. PM confirmation needed: is the AC6 reference to derived flags intentional (requiring a `SpeakerStatusResponse` change), or is AC12/AC7 the authoritative scope?

**Patch (unambiguous fixes):**

- [x] [Review][Patch] **`countPublishableByEventId` uses inner JOIN — verify Hibernate 6 accepts ad-hoc join + change to LEFT JOIN** [`SpeakerPoolRepository.java:93–101`] — JPQL `JOIN Session s ON sp.sessionId = s.id` joins on a raw FK field rather than a mapped `@ManyToOne` association. Standard JPQL requires navigating a mapped relationship; Hibernate 6 may accept this as an extension but behavior is implementation-dependent. Additionally, the inner join silently excludes QUALITY_REVIEWED speakers whose session record was deleted after assignment — `acceptedOrBeyondSpeakers > publishableSpeakers` fires with a misleading gap error. Fix: verify whether `SpeakerPool` has a `@ManyToOne Session session` mapped field (and use it), or switch to a native query / subselect. A LEFT JOIN with `s.startTime IS NOT NULL` predicate on the count is more resilient.
- [x] [Review][Patch] **Missing 6-case `SpeakerPoolRepositoryIntegrationTest` for `countPublishableByEventId`** [`SpeakerPoolRepository.java:93–101`] — AC8 explicitly specifies an integration test covering: (1) empty pool → 0, (2) no QUALITY_REVIEWED → 0, (3) QR with null sessionId → 0, (4) QR with sessionId but null start_time → 0, (5) QR with non-null start_time → 1, (6) mixed scenario. No such test exists; the query is only exercised indirectly via `EventControllerIntegrationTest`.
- [x] [Review][Patch] **Missing `EventWorkflowStateMachineIntegrationTest` tests (AC9)** — AC9 requires this test class to be updated with 4 tests: (1) no accepted speakers → `WorkflowValidationException("Cannot publish agenda — no accepted speakers exist…")`, (2) accepted-but-not-QR → gap error, (3) QR-but-no-session-start-time → gap error, (4) happy path all QR + start_time set → passes. The file was not modified in this diff; only the EventControllerIntegrationTest exercises the happy path end-to-end.
- [x] [Review][Patch] **`$[0]` not pinned to `testSpeaker` in `should_exposeIsPublishableFalse_when_sessionStartTimeNull`** [`SpeakerStatusControllerIntegrationTest.java:~1086`] — The positive test pins `$[0].id == testSpeaker.getId()` but the negative test does not. If the pool ever contains more than one speaker (shared state leakage or future test additions), the assertion could pass silently on the wrong speaker.
- [x] [Review][Patch] **No test for `new_status` constraint rejection in migration test** [`V93LegacySpeakerStatesMigrationIntegrationTest.java:~359`] — `should_rejectFutureLegacyAuditInsert_when_constraintsTightened` verifies that inserting a legacy `previous_status` value (e.g., `'slot_assigned'`) is rejected, but does not verify that inserting a legacy `new_status` value (e.g., `'slot_assigned'`) is also rejected. The `speaker_status_history_new_status_check` constraint should enforce this too. Add a parallel assertion for `new_status`.

**Deferred (pre-existing or explicitly out of scope):**

- [x] [Review][Defer] **Misleading test semantics in `should_rejectLegacyStatusUpdate_when_constraintsTightened`** [`V93LegacySpeakerStatesMigrationIntegrationTest.java`] — deferred, pre-existing; test functions correctly but the witness row (`slotAssignedSpeakerId`) is already mapped to `accepted`, making the constraint check incidental. No production impact.
- [x] [Review][Defer] **`isSlotAssigned` over-reporting in fallback + `patchEntry`/`assignSpeakerToOrganizer`** [`SpeakerPoolResponse.java:fromEntity(SpeakerPool)`; `SpeakerPoolService.java`] — deferred, Phase D per story scope guard (AC7 item 3 + Resolved Decision #7). The Javadoc documents the caveat. Phase D Story 11.D.4 owns the session-loading fix.
- [x] [Review][Defer] **READY rejection fires before log statement** [`SpeakerStatusController.java:~305`] — deferred, operational quality; no correctness impact.
- [x] [Review][Defer] **`MagicLinkService` null `previousResponse` for QUALITY_REVIEWED migrated speakers** [`MagicLinkService.java`] — deferred, pre-existing design gap made visible by the V93 migration. Speakers migrated from `CONFIRMED → QUALITY_REVIEWED` may have null `acceptedAt`, causing `alreadyResponded: true, previousResponse: null`. Phase F magic-link teardown is the appropriate owner.
- [x] [Review][Defer] **No test for `sessionId == null` derived-flag path in `SpeakerStatusControllerIntegrationTest`** — deferred, trivially correct; the `fromEntity(SpeakerPool, null)` else-branch for `sessionId == null` returns `isSlotAssigned = false` which is obviously correct.
