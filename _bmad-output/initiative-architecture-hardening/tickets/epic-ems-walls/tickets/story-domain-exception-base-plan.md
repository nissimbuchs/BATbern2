---
title: '17.14 Domain exception base'
type: 'refactor'
ticket: '14'
created: '2026-11-15'
status: 'done'
baseline_revision: '25ac9e6468f3ed6bc57884fff4d0c755cf44efbc'
route: 'full'
route_source: 'pinned'
risk: 'medium'
review: 'thorough'
review_source: 'pinned'
lenses_ran: [blind-hunter, edge-case-hunter, verification-gap, intent-alignment]
review_loop_iteration: 0
followup_review_recommended: false
context:
  - '{project-root}/docs/architecture/module-map.md'
  - '{project-root}/docs/architecture/coding-standards.md'
warnings: ['oversized']
deferred:
  - summary: >-
      Bruno notifications-api/05-mark-many-read expects markedCount 1, but 03 already marked that notification read and the controller counts only changed rows (0).
    evidence: |-
      Fails deterministically on local dev (full run and a single-collection rerun, 2026-11-15). Neither the notification package nor bruno-tests/notifications-api changed in 17.14 (git diff 25ac9e646 empty for both); last touched by 4254d93b6 (#1289). Not reproduced on the baseline jar (inference from the unchanged code, not measured). Fix: a second fixture notification for 05, or expect 0 and document why.
    location: >-
      bruno-tests/notifications-api/05-mark-many-read.bru
    severity: low
  - summary: >-
      EMS exceptions outside ch.batbern.events.exception (mailcenter AlreadySentException, UserMergeService.MergeBlockedException, PublishingService.PhaseConflictException and PublishValidationException, InboundMailStore.DuplicateInboundMailException, MailDeliveryStatusService.UnreadableEventException) do not extend DomainException and are mapped by module or controller-local handlers.
    evidence: |-
      The ticket scopes the base to the exception package; docs now state the scope. Moving them onto the base is a follow-up, not a defect of this story.
    severity: low
---

<intent-contract>

## Intent

**Problem:** EMS `GlobalExceptionHandler` (1486 lines, 57 `@ExceptionHandler`) maps each domain exception by hand, so every new exception edits the platform advice and the module script counts 13 platform -> domain references from it (L0 -> L2..L4). Domain exceptions carry their status in four different ways (`@ResponseStatus`, shared `NotFoundException`/`ValidationException`/`BATbernException`, `UploadException`, `IllegalStateException`), and two (`AgendaFullException`, `InvalidSlotAssignmentException`) have no handler at all.

**Approach:** An abstract `DomainException` (platform, `ch.batbern.events.exception`) carries HTTP status, an optional code (rendered as `details.code`) and severity; every exception of that package extends it; `GlobalExceptionHandler` maps `DomainException` once. Exceptions whose body has extra fields keep an explicit handler in a module advice (`@Order(HIGHEST_PRECEDENCE)`, the `MailCenterExceptionHandler` model). A characterization test written first pins every exception's response.

## Boundaries & Constraints

**Always:** status, `error`, `message`, `severity`, `details` and the set of top-level fields of every error response unchanged (the capture test is the judge); `docs/api` unchanged; `ClientErrorContract.verify` green; non-domain handlers (Spring, JPA, shared-kernel types) unchanged in `GlobalExceptionHandler`; catch sites that relied on an old superclass audited (`catch (NotFoundException|ValidationException|IllegalStateException|ResponseStatusException)`: the 2026-11-15 audit found none that receives one of these exceptions); one heavy build at a time, logs under `~/.cache/batbern-test-logs/1714/`.

**Never:** a new field in any error body (no top-level `errorCode`); edits to the frozen ArchUnit store; package moves; push, PR, staging.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Plain domain exception | e.g. `ThanksRateLimitedException` | 429, `Too Many Requests`, own message, LOW, `details.code` | base mapping |
| No code | e.g. `EventNotFoundException(uuid)` | 404, message `Event with ID '<uuid>' not found`, LOW, no `details` | base mapping |
| Extra fields | `RegistrationClosedException` with/without deadline | 409, `details.code` + `deadline` only when set | registrations advice |
| Empty context | `WorkflowValidationException("x")` | 422, HIGH, `"details": {}` (empty object kept) | workflow advice |
| Dynamic status | `UserServiceException` with 400 / 503 / none / 302 | 400 MEDIUM / 503 HIGH / 502 HIGH / 502 HIGH | base mapping, status computed in constructor |
| Formerly unmapped | `AgendaFullException`, `InvalidSlotAssignmentException` | captured as 500 CRITICAL first; then fixed to 409 LOW / 400 MEDIUM in their own commit (their `@ResponseStatus` intent) | deliberate deviation, recorded |

</intent-contract>

## Code Map

- `services/event-management-service/src/main/java/ch/batbern/events/exception/GlobalExceptionHandler.java` -- domain handlers to remove: 72-130, 228-249, 276-449, 523-551, 577-598, 624-651, 677-698, 725-748, 851-881, 919-1372 (keep `ValidationException`, `PropertyReferenceException`, MANV, CVE, `EntityNotFoundException`, `NoSuchElementException`, `NotFoundException`, IAE, ISE, `AccessDenied`, `AuthorizationDenied`, `InvalidStateTransitionException`, MATME, HMNR incl. the `SpeakerWorkflowState` branch (shared-kernel type), optimistic lock, no-handler, 405, `ResponseStatusException`, catch-all).
- `.../exception/*.java` (41 classes) -- current status/severity/details per class: see the handler; superclasses that change: `NotFoundException` (EventNotFound, EventPhotoNotFound, MaterialNotFound, RegistrationNotFound, SpeakerAssignmentNotFound, TeaserImageNotFound), `ValidationException` (BusinessValidation), `BATbernException` (AgendaFull, EventHasRealRegistrations, StructuralSessionsAlreadyExist), `UploadException` (TeaserImageLimitExceeded), `IllegalStateException` (RegistrationNotOnWaitlist). Dead (never thrown): `SpeakerNotFoundException`, `WorkflowException`, `InvalidUploadIdException`.
- `.../exception/IncompleteProfileException.java` -- thrown only by `RegistrationService`; the module script classifies it `speakers` (override in `ems-module-deps.py`), so a registrations advice using it would add a registrations -> speakers edge: rename to `RegistrationProfileIncompleteException`.
- `.../mailcenter/MailCenterExceptionHandler.java` -- advice model (`@Order(Ordered.HIGHEST_PRECEDENCE)`).
- `shared-kernel/.../exception/BATbernException.java` -- reuse its `Severity` enum; `ErrorResponse` (shared dto) is `NON_NULL`, so `details` null is omitted and an empty map is written as `{}`.
- `services/event-management-service/src/test/java/ch/batbern/events/exception/GlobalExceptionHandlerClientErrorTest.java` -- `ClientErrorContract.verify`; the new capture test sits beside it.
- `.../test/.../architecture/EmsModuleRules.java`, `EmsEndpointPlacementTest.java`, `EmsModuleRulesFixtureTest.java`, `fixtures/` -- rule factory + fixtures pattern (17.13); `FrozenRuleKeysTest` pins only `EmsModuleWallsTest`.
- `scripts/architecture/ems-module-{deps,target}.py` -- run from `src/main/java/ch/batbern/events`; script tokens include string literals ("Event", "Session", "Topic" in log text count).

## Tasks & Acceptance

**Execution:**
- [x] `exception/{SpeakerNotFound,Workflow,InvalidUploadId}Exception.java` + their GEH handler -- delete (never thrown); rename `IncompleteProfileException` -> `RegistrationProfileIncompleteException` (RegistrationService, its test) -- no response change, done before the capture so the capture stays unchanged.
- [x] `test/.../exception/DomainExceptionResponseTest.java` -- NEW, first: parameterized over every constructor shape of every exception in the package, thrown by a probe controller through `MockMvcBuilders.standaloneSetup` with every `@RestControllerAdvice` found by classpath scan of `ch.batbern.events` (so new module advices join without a test edit); asserts status, top-level field set, `error`, `message`, `severity`, `details` exactly, no `errorCode`, `path`; a guard fails when a concrete exception class of the package has no row. Green on the old handler.
- [x] `exception/DomainException.java`, `exception/DomainErrorResponse.java` -- NEW base (status, code, severity, message, cause) and the one body builder used by the global and module advices.
- [x] all `exception/*Exception.java` -- extend `DomainException`, drop `@ResponseStatus`, same messages; `UserServiceException` computes status/severity in its constructor.
- [x] `exception/SpeakerExceptionHandler.java` (AlreadyResponded, InvalidPromotionState, ReadyRequiresPromote, SlotCapacityReached), `exception/RegistrationExceptionHandler.java` (RegistrationCapacityExceeded, RegistrationClosed, RegistrationProfileIncomplete; INFO log kept), `exception/EventWorkflowExceptionHandler.java` (WorkflowValidation) -- NEW module advices, `@Order(HIGHEST_PRECEDENCE)`.
- [x] `exception/GlobalExceptionHandler.java` -- one `@ExceptionHandler(DomainException.class)` logging status, path, correlation id, class, message; remove the domain handlers.
- [x] Capture-test rows for AgendaFull / InvalidSlotAssignment + their classes -- separate `fix(ems)` commit: 409 LOW / 400 MEDIUM.
- [x] `test/.../architecture/EmsModuleRules.java`, NEW `EmsExceptionMappingTest.java`, fixtures `fixtures/exceptionmapping{,clean}/`, `EmsModuleRulesFixtureTest.java` -- rule: `GlobalExceptionHandler` depends on no class under the root but `DomainException` and `DomainErrorResponse`; fixture tests fire and pass.
- [x] Tests that assert an old superclass of a changed exception -- assert the concrete class.
- [x] `docs/architecture/module-map.md` (EMS modules + Gates), `docs/architecture/06-backend-architecture.md` (exceptions, Global Exception Handler) -- document the base and the rule.

**Acceptance Criteria:**
- Given every domain exception, when it is thrown through the real resolver chain, then `DomainExceptionResponseTest` (written before the change, unchanged after except the two fixed rows) passes.
- Given the EMS main classes, when `EmsExceptionMappingTest` runs, then it passes; given a fixture handler using a domain exception, the rule reports it; a clean fixture passes.
- Given the full EMS test task (`--rerun-tasks`), when it runs, then it is green, `FrozenRuleKeysTest` and `GlobalExceptionHandlerClientErrorTest` included.
- Given the module script, when re-run, then no `GlobalExceptionHandler` reference remains and no new violating edge appears; numbers recorded here.
- Given local dev with EMS rebuilt, when Bruno runs, then it passes as before.

## Implementation Notes

- Commits on chore/epic5-replan (not pushed): 8380e0a6d plan; 149076373 drop 3 dead exceptions (SpeakerNotFound, Workflow, InvalidUploadId; never thrown), rename IncompleteProfileException -> RegistrationProfileIncompleteException (and drop its now-stale override in ems-module-deps.py); b46ae69b4 capture test (green on the old handler); 9efa90dff DomainException base, DomainErrorResponse, 3 module advices, GEH -31 domain handlers (+1 for the base; 1486 -> 627 lines); f69744c65 fix AgendaFull 500 -> 409 LOW, InvalidSlotAssignment 500 -> 400 MEDIUM (only these 2 capture rows changed; new IT in SlotAssignmentControllerIntegrationTest); 8f419cee3 ArchUnit rule + fixtures; b6281e772 docs; review fixes c154dd273 (second rule: every exception extends the base, no @ResponseStatus; FQN-based mapping rule), 7886dfb25 (docs scope, stale Javadoc).
- Capture test unchanged between b46ae69b4 and 9efa90dff (git diff empty for the test), so every response held through the refactor.
- 422: Spring 7 `HttpStatus.valueOf(422)` reads "Unprocessable Content"; BusinessValidation and WorkflowValidation use the deprecated UNPROCESSABLE_ENTITY constant so the body keeps "Unprocessable Entity". UserServiceException keeps `HttpStatus.resolve` of the CUMS status as before; a status Spring cannot resolve (e.g. 499) now answers 502 instead of failing inside the handler.
- Log changes only (no response change): one generic WARN line for base-mapped exceptions (status, correlation id, path, class, message); DeregistrationClosed INFO -> WARN; the INFO lines of the three registration refusals kept in their advice.
- Module script (run from the EMS source root; before on 25ac9e646, after on HEAD): 26 pairs / 84 refs -> 23 pairs / 71 refs; GlobalExceptionHandler 13 refs -> 0 (platform -> speakers 4, sessions 4, topics_ai 2 gone; platform -> event_core 7 -> 5; platform -> registrations 2 -> 1, the remaining one is PersonAddresses). No new violating edge. The after run gives the same numbers with the original and the edited ems-module-deps.py.

## Plan Change Log

## Review Triage Log

### 2026-11-15 — Review pass
- verdicts: 31 findings — high 0, medium 1, low 13, false 11, maybe-false 0 (6 rejected as plan edits / outside the diff counted under false or low as noted)
- findings:
  - `[false]` `reject` (intent) capture test only seen in its final form, standalone not real context — commit b46ae69b4 holds the capture, unchanged through 9efa90dff; the full IT suite (real context, security chain) ran green on top.
  - `[low]` `patch` (intent) row guard counts rows, not constructors — guard now requires max(1, public constructors) per class and the Javadoc states what it counts (c154dd273).
  - `[false]` `reject` (intent) ClientErrorContract says little about domain mapping — it is the required guard for the catch-all, not claimed as domain coverage; the capture test covers the mapping.
  - `[false]` `reject` (intent) after numbers not recorded — fix is a plan edit; recorded in Implementation Notes.
  - `[false]` `reject` (intent) module script edited between before and after — after run with the original and the edited script gives identical 23/71 and 0 GEH refs.
  - `[false]` `reject` (intent) catch sites untested — audited: every NotFoundException/ValidationException/IllegalStateException/ResponseStatusException catch receives a shared or JDK exception, none of the changed classes (also confirmed by the verification-gap lens).
  - `[low]` `reject` (intent) log changes not asserted — log text only, recorded in Implementation Notes; asserting log lines adds a log-capture harness for no client-visible behaviour.
  - `[false]` `reject` (intent) Bruno/production not in the diff — Bruno run on local dev (Verification); production is excluded by instruction for this run.
  - `[false]` `reject` (blind) plan still a template — plan edit; filled at finalize.
  - `[low]` `patch` (blind) docs overclaim (no @ResponseStatus anywhere; MailCenterExceptionHandler built with DomainErrorResponse) — scoped to exception/ and corrected (7886dfb25); the exceptions outside the package are deferred (out of the ticket's scope).
  - `[medium]` `patch` (blind) no rule that every exception extends the base without @ResponseStatus — same root as the verification-gap finding; second unfrozen rule + fixtures (c154dd273).
  - `[low]` `reject` (blind) advice @Order not checked — dropping @Order from Registration/Speaker advices fails the capture test (scan order puts Global first); the remaining case needs a new rule for an unlikely edit.
  - `[low]` `patch` (blind) guard counts only — same fix as intent row 2.
  - `[low]` `patch` (blind) mapping rule exempts by name substring and simple-name allow list — now FQN-based, only the handler and its nested classes exempt (c154dd273).
  - `[low]` `reject` (blind) AgendaFull 409 has no endpoint IT — the throw is pinned by SlotReorderServiceTest and the mapping by the capture test through the real resolver chain; InvalidSlotAssignment on the same controller path has an IT.
  - `[low]` `patch` (blind) stale Javadoc on BusinessValidation, EventNotFound, SpeakerAssignmentNotFound — corrected (7886dfb25).
  - `[low]` `reject` (blind) DeregistrationClosed logs WARN — deliberate per Design Notes; a log level in the base adds a field for one log line.
  - `[false]` `reject` (blind) base handler drops the cause from the log — the old per-class handlers logged only the message as well; unchanged.
  - `[low]` `patch` (blind) misleading "names no user" comment — reworded (7886dfb25).
  - `[low]` `patch` (blind) capture test needs no-arg advice constructors — constraint documented in the test Javadoc (c154dd273).
  - `[low]` `reject` (blind) import grouping in three exceptions — cosmetic, checkstyle clean.
  - `[low]` `reject` (edge) module advice now matches a wrapped domain exception by cause before the catch-all — Spring resolves causes per advice; no code path wraps these exceptions; the outcome (proper 4xx instead of 500) is not harmful.
  - `[false]` `reject` (edge) NPE on null previousResponse — identical dereference in the old handler; every throw site passes a non-null state.
  - `[false]` `reject` (edge) NPE on null currentState/eventId — identical code in the old handler; throw sites pass non-null values.
  - `[false]` `reject` (edge) null status/severity in base — every subclass passes constants or a computed non-null status.
  - `[low]` `patch` (edge) allow list by simple name — same fix as blind row 6.
  - `[low]` `patch` (edge) class with only non-public constructors needs no row — guard now requires at least one row per class.
  - `[low]` `patch` (edge) no-arg constructor requirement — same as blind row 12.
  - `[low]` `patch` (edge) guard claim "every constructor" — same as intent row 2.
  - `[medium]` `patch` (verification-gap) no gate that exceptions in exception/ extend DomainException and carry no @ResponseStatus — `EmsModuleRules.exceptionsExtendDomainException` in EmsExceptionMappingTest, firing and clean fixtures (c154dd273).
  - `[low]` `patch` (verification-gap, other) stale Javadoc — same fix as blind row 8.

## Design Notes

Architecture note: modules touched: platform (exception base, `GlobalExceptionHandler`), speakers, registrations, event core (their advices; exceptions stay in `exception/`). New dependencies: module advices -> platform base only (down). Endpoints: none. Reused: `MailCenterExceptionHandler` model, shared `ErrorResponse`, `BATbernException.Severity`, `CorrelationIdGenerator`. Deviation: modules are not packages in EMS (owner dropped package moves 2026-11-14), so module advices sit in `exception/` named after their module.

"Error code" today is the `error` text plus `details.code`; the top-level `errorCode` field stays unset (adding it would change every body). Base log line replaces the per-class messages (log level of `DeregistrationClosedException` INFO -> WARN; the three INFO registration cases keep INFO in their advice).

Baseline (2026-11-15, 25ac9e646): 26 violating pairs / 84 refs; `GlobalExceptionHandler` 13 refs (platform -> speakers 4, sessions 4, topics_ai 2, event_core 2, registrations 1).

## Verification

**Commands:**
- `./gradlew :services:event-management-service:test --tests '*DomainExceptionResponseTest' --max-workers=4` on the baseline -- expected: green before any main change
- `./gradlew :services:event-management-service:test --rerun-tasks --max-workers=4` (tee `~/.cache/batbern-test-logs/1714/ems-test.log`) -- expected: BUILD SUCCESSFUL, no STALE conformance line
- `./gradlew :services:event-management-service:checkstyleMain :services:event-management-service:checkstyleTest` -- expected: clean
- `make check-test-ratchet` -- expected: no RAISED; STALE only with `--update`
- `python3 scripts/architecture/ems-module-deps.py . <dir>/emsdeps.json && python3 scripts/architecture/ems-module-target.py <dir>` from the EMS source root -- expected: 0 `GlobalExceptionHandler` refs
- `./scripts/ci/run-bruno-tests.sh development` against local dev after `make dev-native-restart-service SERVICE=event-management` -- expected: as before, no `Skipping invalid file`

**Operational rules (rack, owner):** one heavy build at a time (no parallel Gradle, git commit/push hooks, Vitest); Gradle from the repo root with `--max-workers=4`, `./gradlew --stop` before any node test run; every Gradle/make/git commit run as `set -o pipefail; <cmd> 2>&1 | tee ~/.cache/batbern-test-logs/1714/<name>.log`, then grep the log; run long commands in the foreground (timeout up to 600000 ms), never end a turn waiting on a background command. Commit in steps with conventional messages (`refactor(ems): ...`, `test(ems): ...`, `fix(ems): ...`), each ending with the two lines `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` and `Claude-Session: https://claude.ai/code/session_01BkGdx6q4jVDKkQkFEgqfcY`. Never stage `docs/archive/plans/architecture-review-2026-11.html`. No push, no PR, nothing against staging or production. Bruno and the module script are run by the orchestrator, not the implementer.

## Auto Run Result

- Status: built. Commits on chore/epic5-replan, not pushed (see Implementation Notes).
- Change: abstract `DomainException` (status, severity, optional code rendered as `details.code`) and `DomainErrorResponse` (one body builder) in `exception/`; all 37 exceptions of the package extend it; `GlobalExceptionHandler` maps the base once (57 -> 22 `@ExceptionHandler`, 1486 -> 627 lines) and uses no EMS class but the base; extra-field responses in `SpeakerExceptionHandler`, `RegistrationExceptionHandler`, `EventWorkflowExceptionHandler` (`@Order(HIGHEST_PRECEDENCE)`); 3 dead exceptions deleted; `IncompleteProfileException` renamed `RegistrationProfileIncompleteException`.
- Files: exception/* (base, builder, 3 advices, 37 exceptions, GEH), RegistrationService, ems-module-deps.py (stale override removed), tests: DomainExceptionResponseTest + probe controller (ch.batbern.testprobe.ems), EmsExceptionMappingTest, EmsModuleRules, EmsModuleRulesFixtureTest + fixtures, SlotAssignmentControllerIntegrationTest, RegistrationServiceAuthenticatedTest; docs module-map.md, 06-backend-architecture.md.
- Behaviour: every error response byte-equivalent in status, error, message, severity, details and field set (capture test, 57 cases at the time of the refactor, unchanged through it), except the deliberate fix: AgendaFullException 500 CRITICAL -> 409 LOW, InvalidSlotAssignmentException 500 CRITICAL -> 400 MEDIUM (their @ResponseStatus and the sessions spec already said so). UserServiceException with an unresolvable status (e.g. 499) now 502 instead of an exception inside the handler.
- Review: 31 findings; 15 patch rows (6 distinct fixes, 2 of them medium: the second rule), 0 deferred from review, 16 rejected (11 false, 5 low unlikely or deliberate). Deferred (separate): Bruno notifications-api 05 pre-existing failure; exceptions outside the package. Follow-up review: false (no high patched; the two medium rows are one root cause, a test-only rule, covered by the green full run).
- Verification (all run 2026-11-15): full EMS test task with checkstyleMain/Test and --rerun-tasks on the final HEAD: BUILD SUCCESSFUL, 2873 tests, 0 failures, 0 errors, 6 skipped, response conformance 976 responses, 0 baseline lines; `make check-test-ratchet`: no RAISED, no STALE; module script 26/84 -> 23/71, GEH 13 -> 0; local dev: EMS jar rebuilt (9efa90dff+f69744c65 code, later commits comment-only in main) and restarted, live 404 probe shows unchanged body and the new log line; Bruno development, all 23 collections: 22 passed, notifications-api 1 assertion failed (05-mark-many-read, pre-existing, deferred), no `Skipping invalid file`.
- Residual risk: log text changed for every base-mapped exception (alarms on specific log strings would miss them; none found by inspection, not measured); the advice-order invariant rests on `@Order(HIGHEST_PRECEDENCE)` and the capture test. Not done: PR and production rollout (by instruction).

