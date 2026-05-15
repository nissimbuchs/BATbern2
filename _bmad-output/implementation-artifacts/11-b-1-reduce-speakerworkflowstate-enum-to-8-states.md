# Story 11.B.1: Reduce `SpeakerWorkflowState` enum to 8 states; remove `TENTATIVE` response

Status: ready-for-dev

<!-- Validation is optional — run validate-create-story for quality check before dev-story. -->

## Story

**As a** developer working with the speaker workflow,
**I want** the shared-kernel enums to expose exactly the 8 states and 2 response types that ADR-009 endorses,
**So that** downstream services cannot accidentally reference removed states and the public API rejects deprecated values at the contract boundary.

**Phase:** B (State-machine consolidation) — first code-touching story of the refactor; lands BEFORE 11.B.2 (workflow-service single-writer) and 11.B.3 (Flyway migration + API tighten).
**Dependencies:** None — pure shared-kernel changes. Compile errors will surface in `event-management-service` and `web-frontend` after this story merges; those are intentional signals for 11.B.2 / 11.B.3 to act on.
**Scope:** `shared-kernel/` only. **Do NOT** touch `services/event-management-service/`, OpenAPI specs, or frontend code — those are scoped to the next stories. **Do NOT** touch docs/ (Story 11.A.1 owns documentation alignment).

---

## Acceptance Criteria

The AC are pinned to ADR-009 §0.1 (8-state list), §0.6 (TENTATIVE removed), §0.7 (OVERFLOW/WITHDREW removed), and Epic 11 PRD Story 11.B.1 (lines 512-553). Each AC names exact files and the expected post-change shape.

### AC1 — `SpeakerWorkflowState` enum has exactly 8 values

**Given** `shared-kernel/src/main/java/ch/batbern/shared/types/SpeakerWorkflowState.java` is opened,
**When** I read the enum body,
**Then** the enum has exactly these 8 values in this declaration order:
  1. `IDENTIFIED`
  2. `CONTACTED`
  3. `READY`
  4. `INVITED`
  5. `ACCEPTED`
  6. `CONTENT_SUBMITTED`
  7. `QUALITY_REVIEWED`
  8. `DECLINED`

**And** `SLOT_ASSIGNED`, `CONFIRMED`, `OVERFLOW`, `WITHDREW` are absent (no leftover enum constants, no commented-out lines).

**And** the file-level Javadoc is rewritten to reference ADR-009 (replacing the current "Story 5.3" reference) and accurately describe the new 5 workflow phases:
  1. Brainstorming (`IDENTIFIED`, `CONTACTED`)
  2. Provisioning gate (`READY`)
  3. Invitation & response (`INVITED`, `ACCEPTED`, `DECLINED`)
  4. Content lifecycle (`CONTENT_SUBMITTED`, `QUALITY_REVIEWED`)
  5. Terminal (`DECLINED` reachable from every non-terminal state)

**And** the existing `@see` comment `ch.batbern.speakers.converter.SpeakerWorkflowStateConverter` is preserved (the converter lives in `event-management-service` and uses lowercase_snake_case DB storage per CLAUDE.md; the converter itself will be updated in 11.B.2 — but the reference in this Javadoc is still useful for the next dev).

### AC2 — Per-value Javadoc reflects ADR-009 semantics

**Given** I read each enum constant's Javadoc in the rewritten file,
**Then** each block matches the table in ADR-009 §0.1 + plan §0.2 / §0.4 / §0.5:

- **`IDENTIFIED`** — "Initial state. Name on the brainstorm list. May be a candidate, a lead, or a contact the organizer plans to ask. No User exists. `speaker_pool.username` is NULL. No Cognito user."
- **`CONTACTED`** — "**Still brainstorming.** Organizer is reaching out — to the candidate, to partners, to network contacts — to figure out who will actually speak. All conversations logged via `OutreachHistory`. No User exists. `speaker_pool.username` is NULL. No Cognito user."
- **`READY`** — "**Provisioning gate.** The real speaker has been identified. Organizer has a name + email and has committed to inviting this specific person. User provisioning happens at the transition INTO this state: User lookup-or-create + Cognito user provisioning (with FORCE_CHANGE_PASSWORD) + SPEAKER role grant + persisting `username` on `speaker_pool`. Reached only via `POST /api/v1/events/{code}/speakers/{speakerId}/promote` (Phase D — Story 11.D.1)."
- **`INVITED`** — "Formal invitation sent (email contains login link + temporary password). Speaker can authenticate via Cognito. `READY → INVITED` is blocked when `count(ACCEPTED) + count(INVITED) >= max_slots` for the event (slot-capacity gate replaces removed `OVERFLOW` — see ADR-009 §0.7)."
- **`ACCEPTED`** — "Speaker committed via the portal."
- **`CONTENT_SUBMITTED`** — "Title + abstract submitted to `content_submissions`. Either organizer-on-behalf or speaker-self submission — both flows traverse the same `ContentSubmissionService` per ADR-009 §0.4."
- **`QUALITY_REVIEWED`** — "Moderator approved content. **Terminal happy state.** `is_publishable` is derived as `QUALITY_REVIEWED AND slot_assigned`. `is_slot_assigned` is derived from `session.start_time IS NOT NULL`. Neither is persisted."
- **`DECLINED`** — "The single terminal 'not happening' state. Reachable from ANY non-terminal state — covers a lead that didn't pan out (from `IDENTIFIED`/`CONTACTED`), a refusal to an invitation (from `INVITED`), and a speaker who accepted then dropped out (from `ACCEPTED`/`CONTENT_SUBMITTED`/`QUALITY_REVIEWED`). The status-history row records the previous state and reason. Replaces the removed `WITHDREW` state — see ADR-009 §0.7."

**And** each Javadoc names ADR-009 (`@see ADR-009 §0.1`) instead of the legacy `Story 5.X` references currently in the file.

### AC3 — `SpeakerResponseType` enum has exactly 2 values

**Given** `shared-kernel/src/main/java/ch/batbern/shared/types/SpeakerResponseType.java` is opened,
**When** I read the enum body,
**Then** the enum has exactly 2 values: `ACCEPT`, `DECLINE`,
**And** `TENTATIVE` is absent (no leftover constant, no commented-out lines),
**And** the file-level Javadoc is rewritten to:
  - Remove the "Story 6.2a" reference and replace with ADR-009 §0.6 reference.
  - Remove all "tentative" language from the Response Behavior block.
  - State the new ACCEPT-only / DECLINE-only model: a speaker who is unsure simply doesn't respond yet (reminder/escalation handles delays); a speaker who has already pressed ACCEPT but later changes their mind transitions through `DECLINED` (with a reason recorded in `speaker_status_history` — replaces the removed `WITHDREW` path).
**And** each remaining constant's Javadoc:
  - `ACCEPT`: drops the "token is consumed" line (no more tokens under ADR-009 — Cognito session is the auth) but keeps the workflow-transition semantics (`workflow_state transitions to ACCEPTED`, `accepted_at` timestamp set, optional preferences stored).
  - `DECLINE`: drops the "token is consumed" line and keeps the workflow-transition semantics (`workflow_state transitions to DECLINED` terminal, `declined_at` timestamp set, `decline_reason` required and stored).

### AC4 — New event `SpeakerPromotedToReadyEvent` exists with the agreed payload

**Given** I look at `shared-kernel/src/main/java/ch/batbern/shared/events/`,
**Then** a new file `SpeakerPromotedToReadyEvent.java` exists,
**And** the class extends `DomainEvent<UUID>` (same pattern as `SpeakerInvitationSentEvent` and `SpeakerResponseReceivedEvent`),
**And** the class is annotated with `@Getter` and `@NoArgsConstructor(access = AccessLevel.PROTECTED)` (matching the existing speaker-event idioms in `SpeakerInvitationSentEvent`),
**And** the payload has at minimum these fields (all `@JsonProperty`-annotated, non-null where the value semantically can't be null):
  - `speakerPoolId : UUID` — the `speaker_pool` row that was promoted (also serves as `aggregateId`)
  - `eventCode : String` — meaningful event identifier per ADR-003 (e.g., `BATbern56`)
  - `username : String` — the canonical username that was persisted to `speaker_pool.username` (whether looked-up or newly created)
  - `email : String` — the email the speaker was promoted with (used downstream by Phase E invitation email)
  - `promotedAt : Instant` — defaults to `Instant.now()` in the constructor if null
  - `promotedByUsername : String` — the authenticated principal who triggered the promotion (the `actor` argument to `SpeakerWorkflowService.transition()`); maps to `DomainEvent.userId` via the `super()` call

**And** the constructor calls `super(speakerPoolId, "SpeakerPromotedToReadyEvent", promotedByUsername)` (matching the pattern used in `SpeakerResponseReceivedEvent`),
**And** the constructor throws `NullPointerException` with a clear field-name message for any null required field (matching the defensive pattern in `SpeakerInvitationSentEvent`),
**And** `getEventName()` returns `"SpeakerPromotedToReadyEvent"` and is annotated `@JsonIgnore`,
**And** the class Javadoc includes a usage example (matching the example block in `SpeakerInvitationSentEvent`) and explicitly notes: "Published by `SpeakerWorkflowService.transition()` on the `CONTACTED → READY` transition. Consumed by Phase E (Story 11.E.2) to send the Cognito invitation email containing login link + temporary password. **Idempotency note:** if `transition()` is called for a speaker already in `READY`, no event is emitted — Phase B Story 11.B.2 will enforce this on the workflow-service side."
**And** a fluent builder is provided (`SpeakerPromotedToReadyEvent.builder()...build()`) via Lombok **`@Builder` on the constructor** — following the pattern in `SpeakerResponseReceivedEvent` (line 47-73 of that file). This is the right precedent because both events' `super(...)` arguments derive cleanly from constructor inputs (`speakerPoolId` and an actor-username). Per ADR-006 §"Builder Pattern for Generated DTOs", builder pattern is the project's standard convention for fluent object construction across the codebase.

### AC5 — Existing speaker domain events remain compatible

**Given** I read the four existing speaker domain-event classes — `SpeakerInvitationSentEvent`, `SpeakerResponseReceivedEvent`, `SpeakerAcceptedEvent`, `SpeakerInvitedEvent`,
**Then** their payloads still describe valid post-ADR-009 transitions (the speaker still gets invited, still accepts, still responds — these events are not removed),
**And** none of them reference a removed enum constant directly,
**And** none of them reference `TENTATIVE` — `SpeakerResponseReceivedEvent` continues to carry a `SpeakerResponseType responseType` field, but the *value space* is now {ACCEPT, DECLINE} only (the field type doesn't change; the enum's value set narrows).
**And** `SpeakerWorkflowStateChangeEvent.fromState` / `toState` continue to be typed as `SpeakerWorkflowState` — no field-type changes — but the legal value space narrows from 12 to 8.

> **Note for the dev**: AC5 is a *non-change* check. You do not edit these four event classes. You confirm via a quick grep that they don't import removed constants.

### AC6 — Unit tests assert enum value count and event payload shape

**Given** the shared-kernel test suite runs (`./gradlew :shared-kernel:test`),
**Then** a new unit test file `shared-kernel/src/test/java/ch/batbern/shared/unit/types/SpeakerWorkflowStateTest.java` exists,
**And** it has tests asserting:
  - The enum has exactly 8 values (`assertThat(SpeakerWorkflowState.values()).hasSize(8)`).
  - The enum contains each of the 8 expected constants by name (use `Enum.valueOf` or `EnumSet.allOf` + `.containsExactlyInAnyOrder` from AssertJ).
  - The enum does NOT contain any of `SLOT_ASSIGNED`, `CONFIRMED`, `OVERFLOW`, `WITHDREW` (test these via `assertThatThrownBy(() -> SpeakerWorkflowState.valueOf("WITHDREW")).isInstanceOf(IllegalArgumentException.class)` — one test per removed value).

**And** a new unit test file `shared-kernel/src/test/java/ch/batbern/shared/unit/types/SpeakerResponseTypeTest.java` exists,
**And** it has tests asserting:
  - The enum has exactly 2 values.
  - The enum contains `ACCEPT` and `DECLINE` only.
  - `assertThatThrownBy(() -> SpeakerResponseType.valueOf("TENTATIVE")).isInstanceOf(IllegalArgumentException.class)`.

**And** a new unit test file `shared-kernel/src/test/java/ch/batbern/shared/unit/events/SpeakerPromotedToReadyEventTest.java` exists,
**And** it follows the existing pattern in `SpeakerInvitedEventTest` (same package layout, same Jackson `ObjectMapper` + `JavaTimeModule` setup, same naming convention `should_<expectedBehavior>_when_<condition>`),
**And** it has tests asserting:
  - `should_createSpeakerPromotedToReadyEvent_when_validFieldsProvided` — builder path produces a fully populated event with the expected getters returning their input values.
  - `should_serializeToJSON_when_publishingEvent` — round-trip JSON serialisation includes `speakerPoolId`, `eventCode`, `username`, `email`, `promotedAt`, `promotedByUsername`, `eventType = "SpeakerPromotedToReadyEvent"`.
  - `should_throwNullPointerException_when_speakerPoolIdIsNull` — and analogous tests for each required field.
  - `should_returnSpeakerPoolIdAsAggregateId_when_eventCreated` — `getAggregateId()` returns the `speakerPoolId` (matching the `SpeakerResponseReceivedEvent` pattern at line 76-78 of that file).
  - `should_returnSpeakerPromotedToReadyEvent_when_getEventNameCalled`.
  - `should_defaultPromotedAtToNow_when_notProvided` — constructor falls back to `Instant.now()` when `promotedAt` arg is null.

**And** all new tests follow project conventions per CLAUDE.md `Quality Standards`: AssertJ assertions, `@DisplayName` annotations, `should_<verb>_when_<context>` naming.

### AC7 — CHANGELOG entry

**Given** I read `shared-kernel/CHANGELOG.md`,
**Then** a new section is added at the top of the changelog body (above the existing `## [1.0.0] - 2024-12-20` block), following Keep-a-Changelog conventions and the project's existing semantic-versioning approach:

```markdown
## [Unreleased] — speaker-workflow-refactor

### Removed
- `SpeakerWorkflowState.SLOT_ASSIGNED` — replaced by derived flag `is_slot_assigned := session.start_time IS NOT NULL` per ADR-009 §0.1.
- `SpeakerWorkflowState.CONFIRMED` — replaced by derived flag `is_publishable := quality_reviewed AND is_slot_assigned` per ADR-009 §0.1.
- `SpeakerWorkflowState.OVERFLOW` — capacity enforced at the invitation step (slot-capacity gate replaces overflow parking lane) per ADR-009 §0.7.
- `SpeakerWorkflowState.WITHDREW` — collapsed into `DECLINED` with reason recorded in `speaker_status_history` per ADR-009 §0.7.
- `SpeakerResponseType.TENTATIVE` — speakers respond ACCEPT or DECLINE only per ADR-009 §0.6.

### Added
- `SpeakerPromotedToReadyEvent` — signals the `CONTACTED → READY` provisioning gate (User created/looked-up + SPEAKER role granted + Cognito provisioning in Phase E). Consumed by Phase E (Story 11.E.2) to send the Cognito invitation email.

### Changed
- Javadoc on `SpeakerWorkflowState.CONTACTED` now describes "still brainstorming" semantics.
- Javadoc on `SpeakerWorkflowState.READY` now describes the provisioning gate.
- Javadoc on `SpeakerResponseType.ACCEPT` / `DECLINE` no longer references magic-link token consumption (Cognito session is the auth from Phase E onward).

### Migration notes
- Downstream services (`event-management-service`, `web-frontend`) WILL fail to compile against this version of shared-kernel until they are updated. The compile failures are intentional signals for Story 11.B.2 (workflow-service single-writer) and Story 11.B.3 (Flyway migration + OpenAPI tighten).
- DB-level migration of legacy `speaker_pool.status` values is owned by Story 11.B.3.
- See `docs/architecture/ADR-009-unified-speaker-workflow.md` for the full target state model.
```

**And** the `[Unreleased]` link target is added at the bottom of the changelog if such links exist (current changelog uses inline format so this may be a no-op — verify against the actual file before editing).

### AC8 — Build succeeds for shared-kernel; downstream is allowed to fail

**Given** I run `./gradlew :shared-kernel:build` from the repo root,
**Then** the build succeeds (compile + test + jar) with no warnings caused by this change,
**And** `./gradlew :shared-kernel:publishToMavenLocal` succeeds (so downstream services can resolve the new version locally),
**And** all new and existing shared-kernel unit tests pass.

**Given** I run `./gradlew build` against the whole repo,
**Then** `event-management-service` and any other downstream Java module are *expected* to fail compilation on references to removed enum constants — this is the intentional "compile error in a downstream service" signal per the Epic 11 PRD line 547,
**And** the dev agent does NOT fix those downstream compile errors in this story. They are the entry point for Story 11.B.2.

**Given** the dev agent commits this work,
**Then** the commit message names which downstream modules are now expected to break (so the reviewer doesn't think it's a regression),
**And** the commit message references ADR-009 (e.g., `feat(shared-kernel): reduce SpeakerWorkflowState to 8 states per ADR-009`).

### AC9 — No out-of-scope changes

**Given** `git diff --name-only develop...HEAD` is run after the dev completes the work,
**Then** the diff contains ONLY files under:
  - `shared-kernel/src/main/java/ch/batbern/shared/types/SpeakerWorkflowState.java`
  - `shared-kernel/src/main/java/ch/batbern/shared/types/SpeakerResponseType.java`
  - `shared-kernel/src/main/java/ch/batbern/shared/events/SpeakerPromotedToReadyEvent.java` (new)
  - `shared-kernel/src/test/java/ch/batbern/shared/unit/types/SpeakerWorkflowStateTest.java` (new)
  - `shared-kernel/src/test/java/ch/batbern/shared/unit/types/SpeakerResponseTypeTest.java` (new)
  - `shared-kernel/src/test/java/ch/batbern/shared/unit/events/SpeakerPromotedToReadyEventTest.java` (new)
  - `shared-kernel/CHANGELOG.md`
  - `_bmad-output/implementation-artifacts/sprint-status.yaml` (status flip 11-b-1-… → review, handled by code-review workflow)

**And** the diff does NOT contain:
  - any file under `services/` — those are 11.B.2 / 11.B.3 territory
  - `docs/api/speakers-api.openapi.yml` — that OpenAPI enum tightening belongs to Story 11.B.3 (see Dev Notes "Why the OpenAPI spec is NOT updated in this story")
  - any file under `web-frontend/` — frontend cleanup is later (Phase D Stories 11.D.* and Phase E Story 11.E.3)
  - any `docs/` file — documentation alignment is Story 11.A.1's job
  - `services/event-management-service/.../validator/StatusTransitionValidator.java` deletion or modification — that is 11.B.2's job
  - `services/event-management-service/src/main/resources/db/migration/*.sql` — Flyway migration is 11.B.3's job

---

## Tasks / Subtasks

- [ ] **Task 1 — Establish baseline** (AC: all)
  - [ ] 1.1 Read `docs/architecture/ADR-009-unified-speaker-workflow.md` §0.1 (state table), §0.6 (TENTATIVE removed), §0.7 (OVERFLOW/WITHDREW removed), and §"`SpeakerWorkflowService.transition()` skeleton" lines 406-481 (canonical enum declaration).
  - [ ] 1.2 Read `docs/prd/epic-11-speaker-workflow-refactor.md` Story 11.B.1 section (lines 512-553) — confirms AC come from PRD.
  - [ ] 1.3 Read all five files this story will modify or create against:
    - `shared-kernel/src/main/java/ch/batbern/shared/types/SpeakerWorkflowState.java` (current 12-value enum)
    - `shared-kernel/src/main/java/ch/batbern/shared/types/SpeakerResponseType.java` (current 3-value enum)
    - `shared-kernel/src/main/java/ch/batbern/shared/events/SpeakerInvitationSentEvent.java` (pattern reference for new event)
    - `shared-kernel/src/main/java/ch/batbern/shared/events/SpeakerResponseReceivedEvent.java` (pattern reference — uses builder + `getAggregateId()`)
    - `shared-kernel/src/main/java/ch/batbern/shared/events/SpeakerAcceptedEvent.java` (older event — read for context only; do NOT copy its hand-written builder; follow the `SpeakerResponseReceivedEvent` Lombok `@Builder` pattern instead)
  - [ ] 1.4 Read `shared-kernel/src/test/java/ch/batbern/shared/unit/events/SpeakerInvitedEventTest.java` (the only existing speaker-event test — use as the test pattern template).
  - [ ] 1.5 Read `shared-kernel/CHANGELOG.md` to confirm format and locate the insertion point.

- [ ] **Task 2 — Update `SpeakerWorkflowState`** (AC1, AC2)
  - [ ] 2.1 Open the file and rewrite the file-level Javadoc to reference ADR-009 and the 5 workflow phases (see AC1 for the phase list).
  - [ ] 2.2 Delete the four enum constants `SLOT_ASSIGNED`, `CONFIRMED`, `WITHDREW`, `OVERFLOW` along with their Javadoc blocks.
  - [ ] 2.3 Reorder the remaining constants if needed to match the canonical ADR-009 declaration order: `IDENTIFIED, CONTACTED, READY, INVITED, ACCEPTED, CONTENT_SUBMITTED, QUALITY_REVIEWED, DECLINED`. (Note: the current file order is `IDENTIFIED, INVITED, CONTACTED, READY, ACCEPTED, DECLINED, CONTENT_SUBMITTED, QUALITY_REVIEWED, SLOT_ASSIGNED, CONFIRMED, WITHDREW, OVERFLOW` — so `INVITED` needs to move from position 2 to position 4, after `READY`. Be careful: enum reordering can affect ordinal-based logic; verify with a quick grep `grep -rn "ordinal()" shared-kernel/` — currently no ordinal-based logic exists in shared-kernel, so reorder is safe.)
  - [ ] 2.4 Rewrite each constant's Javadoc per AC2 to match ADR-009 §0.1 + §0.2 + §0.4 + §0.5 + §0.7.
  - [ ] 2.5 Verify no Checkstyle violations (project uses NeedBraces, OperatorWrap, MemberName, UnusedImports — Javadoc edits shouldn't trip these but run `./gradlew :shared-kernel:checkstyleMain` to be sure).

- [ ] **Task 3 — Update `SpeakerResponseType`** (AC3)
  - [ ] 3.1 Open the file and rewrite the file-level Javadoc to reference ADR-009 §0.6 and remove all "tentative" language.
  - [ ] 3.2 Delete the `TENTATIVE` enum constant along with its Javadoc block.
  - [ ] 3.3 Rewrite the `ACCEPT` Javadoc to drop the "Token is consumed" line and keep workflow-transition semantics.
  - [ ] 3.4 Rewrite the `DECLINE` Javadoc to drop the "Token is consumed" line and keep workflow-transition semantics.
  - [ ] 3.5 Update the `@see` reference at the file foot — it currently points to `ch.batbern.events.service.SpeakerResponseService`; leave the reference but note that the service itself will be heavily refactored in 11.B.2.

- [ ] **Task 4 — Create `SpeakerPromotedToReadyEvent`** (AC4)
  - [ ] 4.1 Create `shared-kernel/src/main/java/ch/batbern/shared/events/SpeakerPromotedToReadyEvent.java`.
  - [ ] 4.2 Model the class after `SpeakerResponseReceivedEvent` (Lombok `@Builder` on the constructor, `@Getter`, `@NoArgsConstructor(access = PROTECTED)`, `getAggregateId()` override). Payload mirrors `SpeakerInvitationSentEvent` (speakerPoolId + eventCode + username + email + timestamp + actor) — but with `promotedAt` and `promotedByUsername` instead of `sentAt` and `invitedBy`.
  - [ ] 4.3 Required fields per AC4: `speakerPoolId`, `eventCode`, `username`, `email`, `promotedAt`, `promotedByUsername`.
  - [ ] 4.4 Constructor calls `super(speakerPoolId, "SpeakerPromotedToReadyEvent", promotedByUsername)`.
  - [ ] 4.5 Constructor validates non-null required fields with `NullPointerException("X is marked non-null but is null")` messages (mirror the pattern in `SpeakerInvitationSentEvent` lines 84-95).
  - [ ] 4.6 Default `promotedAt` to `Instant.now()` if null.
  - [ ] 4.7 Annotate the constructor with Lombok `@Builder` following `SpeakerResponseReceivedEvent` lines 47-55 (annotation immediately above the public constructor). Add `import lombok.Builder;`. The generated `SpeakerPromotedToReadyEvent.builder()` will expose one fluent setter per constructor argument and a `build()` method. The constructor body's `super(...)` call + null checks + `Instant.now()` fallback all execute as written — Lombok only generates the outer builder class, it does not interfere with the constructor body. Do NOT mark the class itself with `@Builder` (that would conflict with the `@NoArgsConstructor`); the annotation goes on the constructor only.
  - [ ] 4.8 Override `getAggregateId()` to return the `speakerPoolId` (mirror `SpeakerResponseReceivedEvent.getAggregateId()` at line 76-78).
  - [ ] 4.9 Override `getEventName()` returning `"SpeakerPromotedToReadyEvent"`, annotated `@JsonIgnore`.
  - [ ] 4.10 Add class-level Javadoc with the usage example block and the idempotency note from AC4.

- [ ] **Task 5 — Add `SpeakerWorkflowStateTest`** (AC6)
  - [ ] 5.1 Create `shared-kernel/src/test/java/ch/batbern/shared/unit/types/SpeakerWorkflowStateTest.java`.
  - [ ] 5.2 Test 1: `should_haveExactly8Values_when_enumInspected` — `assertThat(SpeakerWorkflowState.values()).hasSize(8)`.
  - [ ] 5.3 Test 2: `should_containAll8ExpectedStates_when_enumInspected` — assert by name that each of the 8 states is present using `EnumSet.allOf(SpeakerWorkflowState.class)` + AssertJ `.containsExactlyInAnyOrder`.
  - [ ] 5.4 Test 3-6: One test each for each removed constant — `should_throwIllegalArgumentException_when_valueOfCalled_with{RemovedConstantName}`.
  - [ ] 5.5 Use `@DisplayName` annotations and `should_<verb>_when_<context>` naming per CLAUDE.md test conventions.

- [ ] **Task 6 — Add `SpeakerResponseTypeTest`** (AC6)
  - [ ] 6.1 Create `shared-kernel/src/test/java/ch/batbern/shared/unit/types/SpeakerResponseTypeTest.java`.
  - [ ] 6.2 Test 1: `should_haveExactly2Values_when_enumInspected`.
  - [ ] 6.3 Test 2: `should_containAcceptAndDecline_when_enumInspected`.
  - [ ] 6.4 Test 3: `should_throwIllegalArgumentException_when_valueOfCalled_withTentative`.

- [ ] **Task 7 — Add `SpeakerPromotedToReadyEventTest`** (AC6)
  - [ ] 7.1 Create `shared-kernel/src/test/java/ch/batbern/shared/unit/events/SpeakerPromotedToReadyEventTest.java`.
  - [ ] 7.2 Copy the `@BeforeEach` Jackson setup from `SpeakerInvitedEventTest` (line 17-23).
  - [ ] 7.3 Write the six tests listed in AC6 (creation, JSON round-trip, null-validation per required field, `getAggregateId()`, `getEventName()`, `promotedAt` default).
  - [ ] 7.4 Use AssertJ assertions throughout; do NOT use raw JUnit `assertEquals`.

- [ ] **Task 8 — Update `CHANGELOG.md`** (AC7)
  - [ ] 8.1 Open `shared-kernel/CHANGELOG.md`.
  - [ ] 8.2 Insert the new `## [Unreleased] — speaker-workflow-refactor` section above the existing `## [1.0.0]` block.
  - [ ] 8.3 Use the exact content blocks from AC7 (Removed / Added / Changed / Migration notes).

- [ ] **Task 9 — Build verification** (AC8)
  - [ ] 9.1 From repo root, run `./gradlew :shared-kernel:build | tee /tmp/sk-build.log` (per CLAUDE.md tee convention).
  - [ ] 9.2 `grep -i "error\|FAIL" /tmp/sk-build.log` — must return empty.
  - [ ] 9.3 Run `./gradlew :shared-kernel:publishToMavenLocal | tee /tmp/sk-publish.log` and confirm `BUILD SUCCESSFUL`.
  - [ ] 9.4 (Optional verification) Run `./gradlew build | tee /tmp/full-build.log` — `event-management-service` is expected to FAIL with references to removed `SpeakerWorkflowState` / `SpeakerResponseType` constants. Confirm the failures are exactly those — no other regressions. Capture the failure list into the commit body so the next dev (11.B.2) has a concrete starting point.

- [ ] **Task 10 — Out-of-scope sweep** (AC9)
  - [ ] 10.1 Run `git status --short | grep -v "^\?\? _bmad-output\|^\?\? docs/implementation-readiness-report"` — the only entries should be under `shared-kernel/`. The pre-existing untracked files in `_bmad-output/` and `docs/implementation-readiness-report*.md` are unrelated to this story and must NOT be added or committed.
  - [ ] 10.2 Run `git diff --name-only` — must only show files under `shared-kernel/`.
  - [ ] 10.3 Confirm no edits to `services/`, `web-frontend/`, `infrastructure/`, `docs/`, or any OpenAPI spec.

- [ ] **Task 11 — Commit + handoff** (AC8)
  - [ ] 11.1 Stage only the files in AC9's allowed list (`git add shared-kernel/src/... shared-kernel/CHANGELOG.md`).
  - [ ] 11.2 Commit with message format:
    ```
    feat(shared-kernel): reduce SpeakerWorkflowState to 8 states + drop TENTATIVE per ADR-009

    - Remove SLOT_ASSIGNED, CONFIRMED, WITHDREW, OVERFLOW from SpeakerWorkflowState
    - Remove TENTATIVE from SpeakerResponseType
    - Add SpeakerPromotedToReadyEvent for CONTACTED→READY provisioning signal
    - Update Javadoc on CONTACTED (still brainstorming) and READY (provisioning gate)
    - CHANGELOG entry for the unreleased speaker-workflow-refactor

    Downstream compile errors are EXPECTED in event-management-service —
    they are the entry point for Story 11.B.2 (workflow-service single-writer).
    Affected files (compile-fail list):
    <paste grep output from /tmp/full-build.log here>

    Story: 11-b-1
    Refs: ADR-009 §0.1, §0.6, §0.7
    ```
  - [ ] 11.3 Do NOT push — leave that for the user. The code-review workflow will flip status to `review` in sprint-status.yaml.

---

## Dev Notes

### Why this story exists (and why it goes second)

Story 11.A.1 (Phase A) lands the documentation alignment first so reviewers have a coherent target. Story 11.B.1 is the **first code-touching story**: it narrows the contract surface in `shared-kernel`, the foundation that every service depends on. By publishing the trimmed enums and the new `SpeakerPromotedToReadyEvent` first:

1. The downstream compile-error storm in `event-management-service` is **fully visible** at the start of 11.B.2 — the dev for that story knows exactly which call sites need rewiring through `SpeakerWorkflowService.transition()`.
2. The new `SpeakerPromotedToReadyEvent` is in place before 11.B.2 needs to wire the transition hook that publishes it.
3. The contract change is reviewable in isolation — small diff, no business-logic risk, no DB risk.
4. Subsequent phases (C, D, E, F) cannot accidentally re-introduce a removed state because the shared-kernel constant simply doesn't exist anymore.

### What this story is NOT doing (scope guard)

The scope is **shared-kernel only**. The following are explicitly out of scope and belong to later stories:

| Out of scope here | Owned by |
|---|---|
| Delete `StatusTransitionValidator` | 11.B.2 |
| Make `SpeakerWorkflowService` the sole status writer | 11.B.2 |
| Stub provisioning side-effect hook | 11.B.2 |
| Slot-capacity precondition on `READY → INVITED` | 11.B.2 |
| Refactor `SpeakerResponseService.processAcceptResponse` / `processDeclineResponse` to delegate | 11.B.2 |
| Delete `SpeakerResponseService.processTentativeResponse` | 11.B.2 |
| Flyway migration mapping legacy values | 11.B.3 |
| Drop `speaker_pool.is_tentative` + `tentative_reason` columns | 11.B.3 |
| Drop `speaker_selection_votes` table | 11.B.3 |
| Update `speakers-api.openapi.yml` SpeakerWorkflowState enum | 11.B.3 (see "Why the OpenAPI spec is NOT updated" below) |
| Update `PUT /api/v1/events/{code}/speakers/{speakerId}/status` API to reject removed values | 11.B.3 |
| Drop the `speakers` table | 11.C.1 |
| Promote-to-READY endpoint | 11.D.1 |
| Cognito provisioning at READY | 11.E.2 |
| Magic-link teardown | 11.F.1 |
| Frontend cleanup of removed enum references | 11.D.2 / 11.D.3 / 11.E.3 |

### Why the OpenAPI spec is NOT updated in this story

`docs/api/speakers-api.openapi.yml` currently lists `SpeakerWorkflowState` with values `IDENTIFIED, CONTACTED, READY, DECLINED, ACCEPTED, CONTENT_SUBMITTED, QUALITY_REVIEWED, CONFIRMED` (line 1064-1084). It does NOT include `SLOT_ASSIGNED`, `OVERFLOW`, `WITHDREW`, `TENTATIVE` already, but it DOES include `CONFIRMED`. It is tempting to fix this here.

**Don't.** Three reasons:

1. **The OpenAPI enum generates a *separate* `StatusEnum` inside `SpeakerPoolResponse.java`**, not a reference to the shared-kernel enum. Removing `CONFIRMED` from the OpenAPI would regenerate the DTO and cause its OWN compile errors in `event-management-service`, layered on top of the shared-kernel compile errors. That conflates two clean signals into one noisy one.
2. **Story 11.B.3 explicitly owns "tighten status API" per the PRD** (AR24): tightening the PUT-status endpoint to reject `SLOT_ASSIGNED|CONFIRMED|OVERFLOW|WITHDREW|TENTATIVE`. That story is the right place for the OpenAPI enum to align — it lands alongside the matching controller-level validation.
3. **Documentation alignment (11.A.1)** already updates the human-readable API description in `docs/architecture/04-api-speaker-coordination.md`. The OpenAPI schema lag is acceptable for one story.

### Why a *separate* event class instead of overloading `SpeakerInvitationSentEvent`

ADR-009 §0.2 distinguishes two moments:
- **`CONTACTED → READY`** — provisioning gate. User created/found. Cognito user provisioned. SPEAKER role granted. *No email goes out at this point.*
- **`READY → INVITED`** — invitation email sent. (Subject to slot-capacity gate.)

`SpeakerInvitationSentEvent` already represents the second moment ("invitation email sent"). The PRD explicitly asks for a NEW event for the first moment:

> "Add a new event `SpeakerPromotedToReadyEvent` to signal the provisioning moment to other services." (plan §2.1)

Conflating both into one event would obscure the provisioning-vs-invitation distinction that the whole ADR is built around.

### Builder pattern — Lombok `@Builder` on the constructor

Per **ADR-006 §"Builder Pattern for Generated DTOs"** (lines 739-760 of that ADR), builder pattern is the BATbern standard convention for fluent object construction across the codebase. The ADR documents the rationale explicitly:

- ✅ Immutable object construction
- ✅ Null safety (missing required fields caught at build time)
- ✅ Better IDE autocomplete
- ✅ More readable code (fluent API)

The existing speaker events use the convention with two stylistic variants:

| Event | Builder approach | Notes |
|---|---|---|
| `SpeakerInvitationSentEvent` | No builder | Pre-dates the ADR; not a counter-example to take guidance from |
| `SpeakerResponseReceivedEvent` | Lombok `@Builder` on constructor | **The right precedent** — `super(speakerPoolId, "...", "speaker-portal")` derives cleanly from constructor inputs. Same shape as `SpeakerPromotedToReadyEvent` |
| `SpeakerAcceptedEvent` | Hand-written builder | Older code; the multi-arg-super complexity was previously misread as a Lombok incompatibility. Not the pattern to follow |
| `SpeakerInvitedEvent` | Lombok `@Builder` | Similar to `SpeakerResponseReceivedEvent` |

**Decision: Lombok `@Builder` on the constructor**, following `SpeakerResponseReceivedEvent`:

```java
@Builder
public SpeakerPromotedToReadyEvent(
        UUID speakerPoolId,
        String eventCode,
        String username,
        String email,
        Instant promotedAt,
        String promotedByUsername) {
    super(speakerPoolId, "SpeakerPromotedToReadyEvent", promotedByUsername);
    // null checks
    // promotedAt = promotedAt != null ? promotedAt : Instant.now();
    // assignments
}
```

Why this works:
- Lombok `@Builder` on a **constructor** (not the class) generates a builder class with one fluent setter per constructor argument and a `build()` method that invokes the constructor.
- The constructor body runs as written, including the `super(...)` call and any defensive checks. Lombok does not interfere with the constructor body.
- Adding `@NoArgsConstructor(access = AccessLevel.PROTECTED)` on the class for Jackson is compatible with `@Builder` on the public constructor — Lombok generates one builder per `@Builder` annotation site.

Why **not** hand-written builder:
- ADR-006 endorses the fluent builder pattern; hand-written builders duplicate boilerplate Lombok handles for free.
- The `SpeakerResponseReceivedEvent` precedent shows the multi-arg-super pattern works cleanly with Lombok `@Builder`. `SpeakerAcceptedEvent`'s hand-written builder is older code that doesn't need to be copied.
- Less code to maintain; fewer opportunities to drift between the constructor signature and the builder.

### Critical "what NOT to break"

- ❌ **Do NOT delete or modify `SpeakerInvitationSentEvent`, `SpeakerResponseReceivedEvent`, `SpeakerAcceptedEvent`, `SpeakerWorkflowStateChangeEvent`, `SpeakerInvitedEvent`, or `SpeakerAddedToPoolEvent`.** They survive the refactor. Their semantics are preserved per AC5.
- ❌ **Do NOT remove the `SpeakerWorkflowState` import from any existing event class.** `SpeakerWorkflowStateChangeEvent.fromState` / `.toState` continue to be typed `SpeakerWorkflowState`; only the value space narrows.
- ❌ **Do NOT modify `SpeakerWorkflowStateConverter` in event-management-service.** It is referenced by Javadoc but lives in another module; that converter handles `lowercase_snake_case` DB storage (per CLAUDE.md "Enum Value Flow") and will be updated in 11.B.2.
- ❌ **Do NOT touch any `EventWorkflowState` constants** — those are the EVENT workflow (9 states), an entirely separate state machine unaffected by Epic 11.
- ❌ **Do NOT reorder enum constants in a way that violates an existing ordinal-based contract.** Quick grep confirms shared-kernel has no `ordinal()` usage today, but if any new code in this PR adds one, that becomes a regression source.
- ❌ **Do NOT change the package of `SpeakerPromotedToReadyEvent`.** It MUST live in `ch.batbern.shared.events` to be discoverable alongside the other speaker events.

### Decision points pre-resolved in ADR-009

- **Exactly 8 states**: `IDENTIFIED → CONTACTED → READY → INVITED → ACCEPTED → CONTENT_SUBMITTED → QUALITY_REVIEWED`, plus `DECLINED` from any non-terminal state. No others. (ADR-009 §0.1)
- **`is_slot_assigned` and `is_publishable` are derived flags**, computed at read time from `session.start_time IS NOT NULL` and `QUALITY_REVIEWED AND slot_assigned` respectively. No persisted column.
- **TENTATIVE response gone**. ACCEPT or DECLINE only. (ADR-009 §0.6)
- **DECLINED replaces WITHDREW**. Reason stored in `speaker_status_history`. (ADR-009 §0.7)
- **OVERFLOW replaced by slot-capacity gate at `READY → INVITED`**. Enforced in `SpeakerWorkflowService` (11.B.2 hook), not via a separate state. (ADR-009 §0.7)
- **`SpeakerPromotedToReadyEvent`** is the new event for the `CONTACTED → READY` moment, separate from `SpeakerInvitationSentEvent` (which is `READY → INVITED`).

### Files being modified — current state summary

I read each file before drafting these AC. Here's what's there today:

| File | Current state | What this story changes |
|------|---------------|-------------------------|
| `shared-kernel/.../types/SpeakerWorkflowState.java` (110 lines) | 12-value enum with Story 5.X / 6.X references; `INVITED` is at position 2 (between `IDENTIFIED` and `CONTACTED`) which is the wrong logical order under ADR-009 | Cut to 8 values; reorder INVITED to position 4 (after READY); rewrite all Javadoc to reference ADR-009 |
| `shared-kernel/.../types/SpeakerResponseType.java` (52 lines) | 3-value enum with TENTATIVE; file-level Javadoc references Story 6.2a tokens | Cut to 2 values; rewrite Javadoc to reference ADR-009 §0.6; drop "token consumed" semantics |
| `shared-kernel/.../events/SpeakerPromotedToReadyEvent.java` | DOES NOT EXIST | NEW FILE — model after `SpeakerResponseReceivedEvent` (Lombok `@Builder` on constructor per ADR-006 §"Builder Pattern for Generated DTOs") with the field set from `SpeakerInvitationSentEvent` |
| `shared-kernel/.../events/SpeakerInvitationSentEvent.java` | Existing speaker-invitation event with `speakerPoolId, eventCode, username, email, sentAt, context` fields and a non-null-checked 5-arg constructor | NOT MODIFIED — use as pattern reference for new event |
| `shared-kernel/.../events/SpeakerResponseReceivedEvent.java` | Existing response-received event with Lombok `@Builder`, `getAggregateId()` override | NOT MODIFIED — use as pattern reference for `getAggregateId()` override |
| `shared-kernel/.../events/SpeakerAcceptedEvent.java` | Older event with hand-written builder | NOT MODIFIED — and do NOT copy its builder style; ADR-006 + `SpeakerResponseReceivedEvent` are the live convention |
| `shared-kernel/src/test/.../unit/events/SpeakerInvitedEventTest.java` | Test pattern: Jackson setup + builder test + serialise test, AssertJ assertions, `should_X_when_Y` naming, `@DisplayName` | NOT MODIFIED — use as pattern reference for new tests |
| `shared-kernel/CHANGELOG.md` | Single `## [1.0.0] - 2024-12-20` section | Add `## [Unreleased] — speaker-workflow-refactor` block above existing version |

### Why removing CONFIRMED is more subtle than the others

Three of the four removed states (`SLOT_ASSIGNED`, `OVERFLOW`, `WITHDREW`) have NO references in this story's scope — they were already orphaned in shared-kernel. Their removal is a straightforward source-code delete.

`CONFIRMED` is different: per the current Javadoc it represents "Speaker is confirmed in the final published agenda — no more changes expected" (a Story 5.12 concept). The CURRENT speakers-api.openapi.yml still lists it. **Why we can remove it from the Java enum anyway:**

- The semantic of `CONFIRMED` is `QUALITY_REVIEWED AND slot_assigned`, which becomes the derived `is_publishable` flag (ADR-009 §0.1 second paragraph).
- The PRD migration rule (Story 11.B.3 AC) explicitly maps `confirmed → quality_reviewed` in `speaker_pool.status`, with `is_publishable` taking over the "ready for agenda publication" predicate.
- `EventWorkflowStateMachine.validateAllSpeakersConfirmed` (the AGENDA_PUBLISHED gate) will change its predicate to "all accepted speakers are publishable" — but that change is owned by 11.B.3, not this story.

The dev for this story should not try to refactor `validateAllSpeakersConfirmed` — they'll see it as a compile error in `event-management-service` (since it likely references `SpeakerWorkflowState.CONFIRMED`), and that compile error is the entry point for 11.B.3 work. **Leave it broken.**

### Test approach

This story is **shared-kernel only**, so testing is unit-only — no integration tests, no Testcontainers, no Bruno, no Playwright. The test surface is:

- Enum value count + presence of each expected constant (3 tests across two enums).
- Removed-constant absence (5 tests — 4 for `SpeakerWorkflowState`, 1 for `SpeakerResponseType`).
- New event creation + JSON serialisation + null validation + accessor overrides (6 tests for `SpeakerPromotedToReadyEvent`).

Coverage targets per CLAUDE.md:
- New enum tests: 100% (enums are trivial to fully cover).
- New event class: ≥ 90% line coverage on `SpeakerPromotedToReadyEvent` (constructor, builder, getters, `getAggregateId`, `getEventName`).

Test execution:
```bash
./gradlew :shared-kernel:test | tee /tmp/sk-test.log
grep -E "FAIL|BUILD FAIL" /tmp/sk-test.log    # must return empty
grep "Tests run:" /tmp/sk-test.log             # confirm test count includes new tests
```

### Project Structure Notes

- All files are under `shared-kernel/` — no other module is touched.
- Package layout follows the existing convention:
  - Production: `ch.batbern.shared.types.*`, `ch.batbern.shared.events.*`
  - Tests: `ch.batbern.shared.unit.types.*`, `ch.batbern.shared.unit.events.*`
- The shared-kernel artefact must be republished to Maven Local (`./gradlew :shared-kernel:publishToMavenLocal`) before any downstream service build will see the new enum/event shape. The dev workflow rule in CLAUDE.md applies: **always rebuild + publish shared-kernel before testing downstream services**.

### References

- [Source: docs/architecture/ADR-009-unified-speaker-workflow.md §0.1] — canonical 8-state list and per-state semantics.
- [Source: docs/architecture/ADR-009-unified-speaker-workflow.md §0.2] — transition rules (DECLINED reachable from every non-terminal; READY is provisioning gate; INVITED has slot-capacity precondition).
- [Source: docs/architecture/ADR-009-unified-speaker-workflow.md §0.6] — TENTATIVE response removed.
- [Source: docs/architecture/ADR-009-unified-speaker-workflow.md §0.7] — OVERFLOW and WITHDREW removed (collapsed into slot-capacity gate + DECLINED).
- [Source: docs/architecture/ADR-009-unified-speaker-workflow.md lines 406-481] — `SpeakerWorkflowService.transition()` skeleton showing canonical enum declaration.
- [Source: docs/plans/speaker-workflow-refactor.md §2.1] — explicit instruction to add `SpeakerPromotedToReadyEvent` to shared-kernel.
- [Source: docs/plans/speaker-workflow-refactor.md §2.2] — migration table showing the legacy → new state mapping (informs Javadoc and CHANGELOG migration notes).
- [Source: docs/prd/epic-11-speaker-workflow-refactor.md lines 512-553] — original AC for Story 11.B.1.
- [Source: shared-kernel/src/main/java/ch/batbern/shared/events/SpeakerInvitationSentEvent.java] — pattern template for the new event (`@Getter`, `@NoArgsConstructor(access = PROTECTED)`, non-null checks in constructor, `super(...)` call, `@JsonIgnore` on `getEventName()` and `getAggregateType()`).
- [Source: docs/architecture/ADR-006-openapi-contract-first-code-generation.md §"Builder Pattern for Generated DTOs" lines 739-760] — project-wide convention to use fluent builder pattern; rationale (immutability, null safety, IDE autocomplete, readability).
- [Source: shared-kernel/src/main/java/ch/batbern/shared/events/SpeakerResponseReceivedEvent.java lines 47-78] — pattern template for **Lombok `@Builder` on the constructor** and `getAggregateId()` override.
- [Source: shared-kernel/src/test/java/ch/batbern/shared/unit/events/SpeakerInvitedEventTest.java] — test pattern (Jackson setup, `should_X_when_Y` naming, `@DisplayName`, AssertJ).
- [Source: shared-kernel/CHANGELOG.md] — format reference (Keep a Changelog v1.0.0).
- [Source: CLAUDE.md §"Critical Development Standards" + §"Type Sharing"] — shared-kernel-first rule; all shared types defined here and imported from here.
- [Source: _bmad-output/project-context.md §"Enum Value Flow (Critical — agents always get this wrong)"] — UPPER_CASE in Java + JSON, lowercase_snake_case in DB; do NOT add `@JsonValue`/`@JsonProperty` to enum constants.
- [Source: _bmad-output/project-context.md §"Gradle — Critical Rules"] — all Gradle commands from repo root; publish shared-kernel to Maven Local before dependent services.

### Testing Standards (for a shared-kernel enum + event story)

- **Unit only.** No Testcontainers (shared-kernel test layer is unit-only for type/event classes).
- **JUnit 5 + AssertJ.** Per CLAUDE.md.
- **Naming**: `should_<expectedBehavior>_when_<condition>` with `@DisplayName` annotations.
- **Coverage**: ≥ 90% line coverage on `SpeakerPromotedToReadyEvent`; 100% on enum tests (trivial).
- **No mocking**. Enums are value types; events are POJOs. Mocking adds no value here.
- **JSON serialisation tests** for the new event should use a fresh `ObjectMapper` with `JavaTimeModule` (mirror `SpeakerInvitedEventTest.@BeforeEach`).

---

## Dev Agent Record

### Agent Model Used

_To be filled by dev agent (e.g., claude-opus-4-7 [1m])_

### Debug Log References

_To be filled by dev agent_

### Completion Notes List

_To be filled by dev agent_

### File List

_To be filled by dev agent. Expected:_

- `shared-kernel/src/main/java/ch/batbern/shared/types/SpeakerWorkflowState.java` (MODIFIED)
- `shared-kernel/src/main/java/ch/batbern/shared/types/SpeakerResponseType.java` (MODIFIED)
- `shared-kernel/src/main/java/ch/batbern/shared/events/SpeakerPromotedToReadyEvent.java` (NEW)
- `shared-kernel/src/test/java/ch/batbern/shared/unit/types/SpeakerWorkflowStateTest.java` (NEW)
- `shared-kernel/src/test/java/ch/batbern/shared/unit/types/SpeakerResponseTypeTest.java` (NEW)
- `shared-kernel/src/test/java/ch/batbern/shared/unit/events/SpeakerPromotedToReadyEventTest.java` (NEW)
- `shared-kernel/CHANGELOG.md` (MODIFIED)
- `_bmad-output/implementation-artifacts/sprint-status.yaml` (MODIFIED — status flip handled by code-review workflow)

---

## Open Questions (for clarification before merge)

These were surfaced during story drafting; the dev agent may proceed with the inferred decision but should flag in PR description if a different call is preferred.

1. **Enum reorder** — Current declaration order is `IDENTIFIED, INVITED, CONTACTED, READY, ACCEPTED, DECLINED, CONTENT_SUBMITTED, QUALITY_REVIEWED, SLOT_ASSIGNED, CONFIRMED, WITHDREW, OVERFLOW`. ADR-009 §0.1 lists them in workflow order `IDENTIFIED, CONTACTED, READY, INVITED, ACCEPTED, CONTENT_SUBMITTED, QUALITY_REVIEWED, DECLINED`. **Inferred decision**: reorder to match ADR-009 workflow order. Grep confirms no `.ordinal()` usage in shared-kernel today, so the reorder is safe. Flag if a downstream service is suspected of relying on ordinal positions (e.g., via DB integer column).

2. **`SpeakerInvitationSentEvent.context` map** — Currently embeds `speakerPoolId`, `eventCode`, `username`, `invitedBy` as a context map alongside the typed fields (redundant). The new `SpeakerPromotedToReadyEvent` does NOT mirror this pattern — it relies on typed accessors only. **Inferred decision**: don't add a redundant context map to the new event. The dev for 11.B.2 can add one later if a consumer needs it. Flag if consistency with the existing pattern is a hard requirement.

3. **Lombok `@Builder` vs hand-written builder** — **RESOLVED 2026-05-15 (Nissim)**: use Lombok `@Builder` on the constructor per ADR-006 §"Builder Pattern for Generated DTOs" and following the `SpeakerResponseReceivedEvent` precedent. AC4, Task 4.7, the Dev Notes "Builder pattern" section, and References have been updated. `SpeakerAcceptedEvent`'s hand-written builder is older code — not the pattern to copy.

4. **Compile-error blast radius in `event-management-service`** — A grep for `SpeakerWorkflowState.SLOT_ASSIGNED|CONFIRMED|WITHDREW|OVERFLOW` across `services/event-management-service/` shows ~28 hits across production code, validators, tests, and one listener. **Inferred decision**: leave all of them broken; capture the compile-fail list into the commit message so 11.B.2 has a concrete worklist. Flag if a phased "branch builds green at every commit" policy applies to this refactor (per CLAUDE.md branching strategy, `feature/` branches can have build-broken intermediate commits as long as the final merge is green).

5. **`speakers-api.openapi.yml` `CONFIRMED` enum value** — Still present in the OpenAPI spec (line 1074). **Inferred decision**: out of scope here; 11.B.3 owns the OpenAPI tightening. See "Why the OpenAPI spec is NOT updated in this story" in Dev Notes. Flag if leaving the OpenAPI spec ahead of the Java enum creates a CI lint failure (verify with `./gradlew :services:event-management-service:openApiGenerateSpeakers`).

---

_Story created via `bmad-create-story` skill on 2026-05-15. Authored by PM (Nissim) with comprehensive context-engine analysis. Ready for `bmad-dev-story` execution._
