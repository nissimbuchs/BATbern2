---
title: '17.14 Domain exception base'
type: 'refactor'
ticket: '14'
created: '2026-11-15'
status: 'in-progress'
baseline_revision: '25ac9e6468f3ed6bc57884fff4d0c755cf44efbc'
route: 'full'
route_source: 'pinned'
risk: 'medium'
review: ''
review_source: ''
lenses_ran: []
review_loop_iteration: 0
followup_review_recommended: false
context:
  - '{project-root}/docs/architecture/module-map.md'
  - '{project-root}/docs/architecture/coding-standards.md'
warnings: ['oversized']
deferred: []
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
- [ ] `exception/{SpeakerNotFound,Workflow,InvalidUploadId}Exception.java` + their GEH handler -- delete (never thrown); rename `IncompleteProfileException` -> `RegistrationProfileIncompleteException` (RegistrationService, its test) -- no response change, done before the capture so the capture stays unchanged.
- [ ] `test/.../exception/DomainExceptionResponseTest.java` -- NEW, first: parameterized over every constructor shape of every exception in the package, thrown by a probe controller through `MockMvcBuilders.standaloneSetup` with every `@RestControllerAdvice` found by classpath scan of `ch.batbern.events` (so new module advices join without a test edit); asserts status, top-level field set, `error`, `message`, `severity`, `details` exactly, no `errorCode`, `path`; a guard fails when a concrete exception class of the package has no row. Green on the old handler.
- [ ] `exception/DomainException.java`, `exception/DomainErrorResponse.java` -- NEW base (status, code, severity, message, cause) and the one body builder used by the global and module advices.
- [ ] all `exception/*Exception.java` -- extend `DomainException`, drop `@ResponseStatus`, same messages; `UserServiceException` computes status/severity in its constructor.
- [ ] `exception/SpeakerExceptionHandler.java` (AlreadyResponded, InvalidPromotionState, ReadyRequiresPromote, SlotCapacityReached), `exception/RegistrationExceptionHandler.java` (RegistrationCapacityExceeded, RegistrationClosed, RegistrationProfileIncomplete; INFO log kept), `exception/EventWorkflowExceptionHandler.java` (WorkflowValidation) -- NEW module advices, `@Order(HIGHEST_PRECEDENCE)`.
- [ ] `exception/GlobalExceptionHandler.java` -- one `@ExceptionHandler(DomainException.class)` logging status, path, correlation id, class, message; remove the domain handlers.
- [ ] Capture-test rows for AgendaFull / InvalidSlotAssignment + their classes -- separate `fix(ems)` commit: 409 LOW / 400 MEDIUM.
- [ ] `test/.../architecture/EmsModuleRules.java`, NEW `EmsExceptionMappingTest.java`, fixtures `fixtures/exceptionmapping{,clean}/`, `EmsModuleRulesFixtureTest.java` -- rule: `GlobalExceptionHandler` depends on no class under the root but `DomainException` and `DomainErrorResponse`; fixture tests fire and pass.
- [ ] Tests that assert an old superclass of a changed exception -- assert the concrete class.
- [ ] `docs/architecture/module-map.md` (EMS modules + Gates), `docs/architecture/06-backend-architecture.md` (exceptions, Global Exception Handler) -- document the base and the rule.

**Acceptance Criteria:**
- Given every domain exception, when it is thrown through the real resolver chain, then `DomainExceptionResponseTest` (written before the change, unchanged after except the two fixed rows) passes.
- Given the EMS main classes, when `EmsExceptionMappingTest` runs, then it passes; given a fixture handler using a domain exception, the rule reports it; a clean fixture passes.
- Given the full EMS test task (`--rerun-tasks`), when it runs, then it is green, `FrozenRuleKeysTest` and `GlobalExceptionHandlerClientErrorTest` included.
- Given the module script, when re-run, then no `GlobalExceptionHandler` reference remains and no new violating edge appears; numbers recorded here.
- Given local dev with EMS rebuilt, when Bruno runs, then it passes as before.

## Implementation Notes

## Plan Change Log

## Review Triage Log

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

**Operational rules (rack, owner):** one heavy build at a time (no parallel Gradle, git commit/push hooks, Vitest); Gradle from the repo root with `--max-workers=4`, `./gradlew --stop` before any node test run; every Gradle/make/git commit run as `set -o pipefail; <cmd> 2>&1 | tee ~/.cache/batbern-test-logs/1714/<name>.log`, then grep the log; run long commands in the foreground (timeout up to 600000 ms), never end a turn waiting on a background command. Commit in steps with conventional messages (`refactor(ems): ...`, `test(ems): ...`, `fix(ems): ...`), each ending with the two lines `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` and `Claude-Session: https://claude.ai/code/session_01BkGdx6q4jVDKkQkFEgqfcY`. Never stage `docs/plans/architecture-review-2026-11.html`. No push, no PR, nothing against staging or production. Bruno and the module script are run by the orchestrator, not the implementer.
