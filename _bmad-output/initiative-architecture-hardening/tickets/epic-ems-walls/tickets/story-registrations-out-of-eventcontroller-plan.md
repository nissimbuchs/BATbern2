---
title: '17.13 Registrations out of EventController'
type: 'refactor'
ticket: '13'
created: '2026-11-15'
status: 'in-progress'
baseline_revision: 'f5f052e6aece2dee8a9b5aec7ca015e502c0ce24'
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

**Problem:** `EventController` (2334 lines) hand-maps the 10 registration endpoints and holds registration counting, so every registration change edits the event controller; the module script counts `event_core -> registrations` 12 refs, 10 of them in `EventController`.

**Approach:** A new `RegistrationController implements RegistrationsApi` (the generated interface, orphaned until now) takes the 10 endpoints; the registration counting helpers move into `RegistrationService`; an unfrozen ArchUnit rule, proven by a fixture test, fails a registration path mapped in (or inherited by) `EventController`.

## Boundaries & Constraints

**Always:** paths, payloads (wire JSON), `@PreAuthorize` rules and status codes of valid requests unchanged; Flyway untouched; `docs/api` unchanged (Spectral and `make check-frontend-api-paths` unchanged); test fidelity rules for the tests moved or rewritten; one heavy build at a time, logs under `~/.cache/batbern-test-logs/1713/`.

**Never:** behaviour fixes of the registration flow itself (record them as findings); edits to the frozen ArchUnit store; de-mocking test files the story does not otherwise touch; push, PR, staging.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| All 10 ops, valid input | as today | same status and body as before | none |
| Unknown but well-formed event/registration | `BATbern99999`, `BATbern99999-reg-ABC123` | 404 as before | `EventNotFoundException` / `RegistrationNotFoundException` |
| Registration of another event (PATCH/DELETE) | code of event B under event A | 404 (message no longer names the other event, like promote/resend) | `RegistrationNotFoundException` |
| eventCode violating the spec pattern | `NONEXISTENT`, `BATbern10k` | 400 (spec `^BATbern[0-9]+$`; was 404 or accepted) | `ConstraintViolationException` -> existing 400 handler |
| limit > 100 or page < 1 on list | `limit=500` | 400 (spec max 100; was clamped) | same |

</intent-contract>

## Code Map

- `services/event-management-service/src/main/java/ch/batbern/events/controller/EventController.java` -- endpoints at ~1616-2257 (promote, resend, my-registration x3, create, list, confirm, patch, delete) move out; `enrichWithRegistrationCounts` (~1490), include=registrations (~343 batch loop, ~543), `deleteEvent` real-attendee guard (~1348) call `RegistrationService` instead. Keep `waitlistPromotionService.promoteFromWaitlist` in update/patch (capacity change). Drop now-unused fields (`registrationRepository`, `confirmationTokenService`, `registrationEmailService`, `userApiClient` if unused, `@Value appBaseUrl` if unused).
- `build/generated-registrations/.../RegistrationsApi.java` -- `@Validated`; `listRegistrations` returns `ListRegistrations200Response` (data: `RegistrationResponse` via schemaMapping, `PaginationMetadata`), `createMyRegistration` returns `CreateMyRegistration201Response`, `createRegistration` returns generated `CreateRegistrationResponse` (message, email, companyUpdated), param order status, search, companyId, page, limit.
- `controller/ParticipantsController.java`, `controller/DeregistrationController.java` -- style models (bare override params, `@RequestMapping("/api/v1")`).
- `service/RegistrationService.java` -- `listForAttendee` (4.10b) is the model for a new `listForEvent`; add counts methods next to `EnrollmentSummary` record.
- `repository/RegistrationRepository.java` -- `countByEventIdAndStatusIn`, `countRealAttendees`; add one grouped count for the archive batch (fixes the per-event N+1 noted at ~344).
- `test/.../architecture/EmsModuleRules.java`, `EmsModuleRulesFixtureTest.java`, `fixtures/`, `EmsDataOwnershipTest.java` -- rule factory + fixture pattern; `FrozenRuleKeysTest` pins only `EmsModuleWallsTest`, so a new unfrozen test class leaves it green.
- `test/.../controller/EventControllerIntegrationTest.java` 1284-1372 -- 5 registration tests move to `RegistrationControllerIntegrationTest` (952 lines, mocks `UserApiClient` + `RegistrationEmailService`, class `@Transactional`).
- `test/.../config/CumsContractHarness.java`, `SesRecorder` -- harness for the de-mocked controller test (`ParticipantsControllerIntegrationTest` is the model).
- `RegistrationCapacityIntegrationTest` (codes `BATbern10k*`), `SecurityConfigIntegrationTest` (`NON_EXISTENT_EVENT`) -- fail the spec pattern; switch to well-formed codes.
- `scripts/architecture/ems-module-{deps,target}.py` -- run from `src/main/java/ch/batbern/events`.

## Tasks & Acceptance

**Execution:**
- [x] `controller/RegistrationController.java` -- new, implements `RegistrationsApi`, same `@PreAuthorize` per op; private helpers `registrationOfEvent` (one 404 rule, cross-event hidden) and `sendConfirmation` (token + deregistration URL + mail, used by create and resend) -- moves the code, removes FQNs and duplication.
- [x] `service/RegistrationService.java` + `RegistrationRepository.java` -- `listForEvent`, `countsFor(eventId)` (record), `activeCounts(Collection<UUID>)` (one grouped query), `requireNoRealAttendees(eventCode, eventId)` -- registration logic leaves event core.
- [x] `controller/EventController.java` -- remove endpoints and registration repository use; call the new service methods.
- [x] `test/.../architecture/EmsModuleRules.java`, new `EmsEndpointPlacementTest.java`, fixtures under `fixtures/eventcore*`, `EmsModuleRulesFixtureTest.java` -- rule `noRegistrationEndpointIn(root, "EventController")` checking mapping annotations of the class and its interfaces for `/registrations` or `/my-registration`; fixture tests: fires on hand mapping, fires via an implemented interface, passes on a clean controller.
- [x] `test/.../controller/RegistrationControllerIntegrationTest.java` -- receive the 5 moved tests; replace `@MockitoBean UserApiClient`/`RegistrationEmailService` with `CumsContractHarness` + `SesRecorder`, drop class `@Transactional` (explicit cleanup); add 400 malformed-code and 404 cross-event cases.
- [x] `RegistrationCapacityIntegrationTest`, `SecurityConfigIntegrationTest`, `EventControllerIntegrationTest` -- well-formed codes; remove the moved tests.
- [x] `web-frontend/scripts/test-ratchet-baseline.txt` -- update for the STALE lines only (`npm run check:test-ratchet -- --update`).
- [x] `docs/architecture/module-map.md` (Gates), `docs/architecture/01-system-overview.md` (~316), `docs/architecture/06-backend-architecture.md` if it names the controller -- document new controller and rule.

**Acceptance Criteria:**
- Given the EMS main classes, when `EmsEndpointPlacementTest` runs, then it passes and `EventController` has no `/registrations` or `/my-registration` mapping and implements no `RegistrationsApi`.
- Given a fixture `EventController` with such a mapping (direct or inherited), when the rule is evaluated, then it reports that class; a clean fixture passes.
- Given the full EMS test task, when it runs, then it is green, `FrozenRuleKeysTest` included, response conformance with no STALE line.
- Given the module script, when re-run after the change, then `event_core -> registrations` is below 12 and `EventController` below 10, numbers recorded here.
- Given local dev, when Bruno (event-full-workflow-api, events-crud-api, participants-api) and Playwright `registration-flow.spec.ts` + `organizer/event-participants.spec.ts` run, then they pass.

## Implementation Notes

- Module script after the change (2026-11-15, working tree on f5f052e6a + this story): 26 violating pairs / 84 refs (was 92); `event_core -> registrations` 4 (was 12): EventController 2 (`RegistrationService`, `WaitlistPromotionService`, the accepted L2 -> L3 deviation), ArchivalBestEffortSteps 1, Event 1.
- `RegistrationController implements RegistrationsApi`; `createMyRegistration` now answers the generated `CreateMyRegistration201Response` (message, email): the former hand DTO also wrote `"companyUpdated": null`, which is gone from that one response. The hand `dto/CreateRegistrationResponse` is deleted (its only user was EventController).
- Anonymous create: the CUMS user lookup and the confirmation token now happen only when a confirmation mail is sent (not for a waitlist registration, which never used them). The confirmation log line no longer logs a token prefix.
- `include=registrations`: archive list one grouped query (`countPerEventByStatusIn`), detail the same method with one id.
- `RegistrationControllerIntegrationTest`: on `CumsContractHarness` + `SesRecorder`, no class `@Transactional` (cleanup by event code); the 7 formerly `@Disabled` list/filter tests run again (denormalized attendee fields seeded), the 2 QR stubs removed; new: 400 on spec pattern/page/limit, 404 cross-event PATCH/DELETE, 404 unknown, 400 malformed registration code. Event dates relative to now.
- Verified: EMS test task green (2804 tests, 0 failures, 6 skipped, no STALE line), checkstyleMain/Test clean, test ratchet 3 STALE lines removed by `--update` (no RAISED). Bruno and Playwright left to the parent (local dev).

## Plan Change Log

## Review Triage Log

## Design Notes

Architecture note: modules touched: event core (`EventController`), registrations (`RegistrationController`, `RegistrationService`, repository). New dependencies: none across modules or services; `RegistrationController` uses event core (down, allowed). Endpoints: none new; the 10 existing ops move, nearest is `ParticipantsController` (same spec, contract-first). Reused: generated `RegistrationsApi`, `RegistrationService.listForAttendee` pattern, `CumsContractHarness`, `SesRecorder`. Deviation: `EventController` keeps `RegistrationService` (counts, delete guard) and `WaitlistPromotionService` (capacity change), an L2 -> L3 edge the dropped 17.16 would have inverted.

Implementing the generated interface brings its declared bean validation (eventCode/registrationCode patterns, `page>=1`, `limit<=100`) to these ops: malformed input answers 400 instead of 404/clamping. The patterns were checked against all 8190 stored codes (`SpecPatternsAcceptProducedValuesIntegrationTest`); the frontend never sends `limit>100`. Valid requests are unchanged.

Baseline (2026-11-15, f5f052e6a): 26 violating pairs / 92 refs; `event_core -> registrations` 12 (EventController 10, ArchivalBestEffortSteps 1, Event 1).

## Verification

**Commands:**
- `./gradlew :services:event-management-service:test --max-workers=4` (tee to `~/.cache/batbern-test-logs/1713/ems-test.log`) -- expected: BUILD SUCCESSFUL
- `./gradlew :services:event-management-service:checkstyleMain checkstyleTest` -- expected: no violation
- `make check-test-ratchet` -- expected: no RAISED, no STALE after update
- `python3 scripts/architecture/ems-module-target.py` (after `ems-module-deps.py`) -- expected: reduced numbers
- `./scripts/ci/run-bruno-tests.sh` against local dev -- expected: registration requests pass, no `Skipping invalid file`
- `cd web-frontend && npx playwright test e2e/registration-flow.spec.ts e2e/organizer/event-participants.spec.ts --workers=1` against local dev -- expected: pass
