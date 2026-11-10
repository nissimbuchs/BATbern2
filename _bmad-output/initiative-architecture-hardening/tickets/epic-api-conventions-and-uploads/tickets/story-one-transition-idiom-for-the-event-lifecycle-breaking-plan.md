---
title: 'One transition idiom for the event lifecycle (breaking)'
type: 'refactor'
ticket: '9b'
created: '2026-11-10'
status: 'built'
baseline_revision: '1bc1e7abb9fb62408b25224fb92b56da667dac31'
route: 'full'
route_source: 'auto'
risk: 'high'
review: 'quick'
review_source: 'pinned'
lenses_ran: ['quick']
review_loop_iteration: 0
context:
  - '{project-root}/docs/architecture/ADR-013-rest-api-crud-conventions.md'
  - '{project-root}/docs/architecture/coding-standards.md'
  - '{project-root}/docs/architecture/module-map.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** EMS moves an event through its lifecycle in four ways:
- `POST /events/{c}/publish`, which validates and jumps to AGENDA_PUBLISHED but does no CDN invalidation;
- `POST …/publish/{phase}` and `…/unpublish/{phase}`, where the phase is an unvalidated string;
- `POST …/workflow/transition`;
- `workflowState` inside the event PUT/PATCH body, applied with override.

The auto-publish schedule is set with a verb (`POST …/publish/schedule`), and the scheduler ignores it: the 30 and 14 days are hardcoded, and the per-event enabled flags are never read. This violates ADR-013 §1, which asks for one transition idiom per service.

**Approach:** one idiom, the ADR-013 row 4 action sub-resource `POST /{instance}/{verb}`, breaking, with no deprecation period.
- Phases become instances: `POST /events/{eventCode}/phases/{phase}/publish` and `…/unpublish`, where `phase` is an enum of topic, speakers and agenda.
- The workflow becomes `POST /events/{eventCode}/transition` with body `{targetState, overrideValidation, overrideReason}`.
- Publishing state and schedule become one resource, `/events/{eventCode}/publishing`. `GET` returns the status and the schedule; `PATCH` sets the schedule.
- The scheduler honours the stored schedule.
- `POST /events/{c}/publish` folds into publishing the agenda phase, and `workflowState` leaves the event write bodies.

## Boundaries & Constraints

**Always:**
- No deferred work (owner 2026-11-09). A test that fails because a mock is gone, or a defect found on the way, is fixed in the code within this PR.
- Touched tests leave own-code mocks wherever the harness allows.
- Contract first: spec, then `npm run generate:api-types`.
- `@PreAuthorize` stays on every write (ORGANIZER).
- Publishing a phase still evicts the caches and invalidates the CDN for that phase.
- Publishing agenda still generates the structural sessions, never moves the workflow backwards, and fires the events the old paths fired (`SpeakersPhasePublishedEvent`, and `EventPublishedEvent` when the agenda publish moves the event into AGENDA_PUBLISHED).
- The validation of the folded `publishEvent` (date, title, future date unless ARCHIVED) runs on the agenda publish too, alongside the timed-sessions rule. 422 with the existing `PublishValidationError` shape.
- Record before and after counts: Spectral baseline, ratchet.

**Never:**
- No aliases for the old paths.
- Never edit an applied Flyway migration.
- No change to `GET …/workflow/status` (a read, not a transition).
- Never publish to production from a test; Bruno and Playwright run against local dev only.
- Never touch the `mailcenter.notify-instead-of-forward` value.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|---|---|---|---|
| Publish a phase | organizer `POST /events/BATbern60/phases/speakers/publish` | 200 `PublishPhaseResponse`, `currentPublishedPhase=speakers`, CDN invalidated | — |
| Unknown phase | `POST …/phases/foo/publish` | 400 | — |
| Agenda without date or with untimed sessions | `POST …/phases/agenda/publish` | 422 `PublishValidationError` listing the reasons, nothing changed | — |
| Agenda publish moves workflow | event in SLOT_ASSIGNMENT, valid | state AGENDA_PUBLISHED, `publishedAt` set, `EventPublishedEvent` fired | — |
| Unpublish | `POST …/phases/agenda/unpublish` | phase back to speakers, workflow state unchanged | — |
| Transition | `POST /events/{c}/transition {targetState}` | as the old workflow/transition: 200 `WorkflowTransitionResponse`, invalid skip 422 | — |
| Schedule | `PATCH /events/{c}/publishing {phase2Enabled:false}` | stored; GET returns it; the scheduler does not auto-publish speakers for that event | — |
| Old paths | `POST /events/{c}/publish`, `/publish/agenda`, `/unpublish/agenda`, `/publish/schedule`, `GET /publish/status`, `GET /publish/schedule`, `/workflow/transition` | 404 or 405 | — |
| workflowState in PATCH | `PATCH /events/{c} {workflowState:"ARCHIVED"}` | 400 unknown property, or ignored per the spec's additionalProperties rule; the state is unchanged | — |

</frozen-after-approval>

## Code Map

- **Spec** `docs/api/events-core-api.openapi.yml`:
  - Operations: `publishEvent` L945, `transitionEventWorkflowState` L1019, `getWorkflowStatus` L1167 (keep), `publishPhase` L2013, `unpublishPhase` L2061, `getPublishingStatus` L2103, `configureAutoPublish`/`getAutoPublishSchedule` L2151.
  - Schemas: `TransitionStateRequest` L3549, `WorkflowTransitionResponse` L3531, `PublishPhaseResponse` L2404, `UnpublishPhaseResponse` L2421, `PublishingStatusResponse` L2483, `PublishValidationError` L2506, `AutoPublishSchedule*` L2527/2540.
  - `PatchEventRequest` and the PUT body carry `workflowState`.
- **EMS controllers** (`services/event-management-service/src/main/java/ch/batbern/events/`):
  - `EventController.publishEvent` L1377-1431 with `validateForPublishing` L1440, and `applyWorkflowStateChange` L1627, which applies PATCH/PUT workflowState with override.
  - `controller/PublishingEngineController` (`PublishingEngineApi`, own 422 handler).
  - `controller/EventWorkflowController` L60.
- **EMS services:**
  - `service/publishing/PublishingService` L68 (`publishPhase`), L132 (`unpublishPhase`), L162 (`configureAutoPublish`).
  - `service/EventWorkflowStateMachine` L64/89.
  - `scheduled/PublishingScheduledService`, which hardcodes 30 and 14 days. Table `publishing_config` (V28 L105): `auto_publish_speakers`, `auto_publish_speakers_days_before`, `auto_publish_agenda`, `auto_publish_agenda_days_before`.
- **EMS tests:**
  - `EventControllerIntegrationTest` L1226/1267, `PublishingEngineControllerIntegrationTest`, `EventWorkflowControllerIntegrationTest` (12 tests), `PublishingScheduledServiceIntegrationTest` (8 tests).
  - `TaskActivationIntegrationTest` L81/95, `AgendaStructuralSessionsIntegrationTest`.
  - `security/OrganizerOnlyEndpointsIntegrationTest` L47 and `security/RemovedEndpointsIntegrationTest` L51: GET `/publish/preview` answers 405 only because of the sibling POST route, so recheck it.
- **Frontend** (`web-frontend/src`):
  - `services/publishingService/publishingService.ts` and its test.
  - `hooks/usePublishing/usePublishing.ts` and its test; users are `components/Publishing/PublishingControls/PublishingControls.tsx` L112/143, `EventPublishingTab.tsx` and `useTabBadges.ts`.
  - `services/workflowService.ts` L55-76: types the response as `Event` while the spec returns `WorkflowTransitionResponse`, so fix it. Callers are `EventForm.tsx` L457 and `EventWorkflowStateSection.tsx` L82, with tests.
  - `types/event.types.ts` L361 `PublishingPhase`, `test/contract/specMatcher.test.ts` L76/90.
- **Bruno:** `bruno-tests/event-full-workflow-api/16-publish-event.bru`, `17a`-`17g-advance-to-*.bru`, `18a`/`18b-invalid-skip-*.bru`.
- **Playwright:** `e2e/workflows/events/event-management-flow.spec.ts` L495-513, `e2e/helpers/event-fixture.ts` `transitionWorkflow()` (~L250), used by `event-lifecycle-e2e.spec.ts` and `speaker-pool-golden-path.spec.ts`.
- **Lint:**
  - `web-frontend/scripts/api-lint-baseline.txt` L45, L46, L48, L49 go, and owner group T1 in `api-lint-owners.txt` L22 goes.
  - New paths must not add lines: the `phase` param is an enum, not `*Id`, so `uuid-path-param` does not apply.
  - `.spectral.yaml` comments L33-40 name the old paths; update them.
- **ADR** `docs/architecture/ADR-013-rest-api-crud-conventions.md` §1: problem 3 text (L69-72) is out of date (`workflow/advance`, PUT). Record the EMS idiom decision there.

## Tasks & Acceptance

**Execution:**
- [x] Set `baseline_revision`, then record the counts.
- [x] RED tests:
  - one IT per new operation (phase publish/unpublish with CDN invalidation at the S3/CloudFront boundary as the existing tests do, unknown phase 400, agenda validation 422, transition, publishing GET/PATCH);
  - a 404/405 IT for every old path;
  - a `PublishingScheduledService` IT that a disabled or different-days schedule is honoured.
- [x] Spec: new operations with examples, old ones removed, `workflowState` removed from the event write bodies. Regenerate types and the EMS build.
- [x] EMS:
  - fold `publishEvent` into agenda publish (merged validation, `publishedAt`, `EventPublishedEvent`);
  - move transition to `POST /events/{c}/transition`;
  - publishing resource GET/PATCH;
  - scheduler reads `publishing_config` per event (enabled flag and days, defaults when no row);
  - remove `applyWorkflowStateChange` and its path.
  - Check every caller of the removed `publishEvent` and of PATCH `workflowState` (Bruno event-types, fixtures).
- [x] Frontend: services, hooks and callers; `workflowService` return type fixed; tests on the MSW harness.
- [x] Bruno 16/17*/18*, Playwright helper and specs.
- [x] Lint baseline and owners, `.spectral.yaml` comments, ADR-013 §1, doc-drift mapped docs (06-backend publishing section, workflow docs, user guide if it names the operations).
- [x] Full EMS suite, frontend suite and gates, then local dev Bruno `event-full-workflow-api` and the Playwright lifecycle/publishing specs.

**Acceptance Criteria:**
- Given the specs, when `npm run lint:api` runs, then the four baseline lines and group T1 are gone, there is no new line, and nothing is UNOWNED or STALE.
- Given local dev, when an organizer publishes and unpublishes topic, speakers and agenda, transitions the workflow and sets the schedule from the UI, then the public event page shows the published phase and the changes persist.
- Given the EMS suite with the response conformance gate, when it runs, then it is green.

## Implementation Notes

Counts (baseline 1bc1e7ab → after):
- Spectral `lint:api`: 109 → 105 findings, `no-verb-segment` 43 → 39; the four baseline lines and
  owner group T1 removed; no new line, nothing UNOWNED or STALE.
- Test ratchet: fe-mock-components −4, fe-mock-hooks −3, fe-mock-services −2, fe-mock-i18n −2
  (EventPublishingTab, PublishingControls, workflowService, EventWorkflowStateSection on the MSW
  harness); be-transactional-it unchanged (the new CDN IT has no class-level `@Transactional`).
- Operations in the public specs: 302 → 300 (7 operations became 5) (the specMatcher/examples positive controls now say
  "more than 290").
- EMS suite: 2695 tests, 0 failures, response conformance baseline 0 lines, all still produced.
- Vitest: 460 files, 5876 passed.

Verified on local dev: Bruno `event-full-workflow-api` 32/32 requests, 59/59 tests, 0 skipped
files; `events-crud-api` 17/17; Playwright `event-lifecycle-e2e` and `speaker-pool-golden-path`
13/13; old paths 404 through the gateway; a throwaway UI walk (not committed) published topic,
speakers and agenda from the Publishing tab, saw the public event at each phase (AGENDA after the
agenda publish, SPEAKERS after the revert), set and reloaded the schedule, and moved the workflow
from Details → Settings.

## Plan Change Log

- `workflowState` in PATCH/PUT answers 400 (unknown property: Jackson rejects unknown fields in
  EMS), the matrix's first option; the state is unchanged.
- The future-date rule of the folded `publishEvent` applies when the agenda publish moves the
  workflow into AGENDA_PUBLISHED. Re-publishing the agenda of a published, live or archived event
  does not need it (organizers correct the agenda on the event day; `TaskActivationIntegrationTest`
  republishes for a live event). Title and date are always checked, timing as before.
- The title rule is unreachable through the API (the entity refuses a blank title); kept as the
  intent asks, not tested by an IT.
- The acceptance criterion "sets the schedule from the UI" had no UI: `AutoPublishScheduleForm`
  added to the Publishing tab (i18n in 10 locales), the timeline reads the stored schedule.
- Defect found: `PublishingControls` showed the 422 reasons of the tab's own `usePublishing`
  instance, whose mutation never runs, so a refused publish showed no reason. It now shows those of
  its own publish (the prop is gone).
- Defect found: the auto-publish job ran without ShedLock; one `runDailyAutoPublish` job under the
  lock calls the two (still public) jobs. The former `GET …/publish/schedule` answered 500 without a
  stored row; `GET …/publishing` returns the defaults.
- `specMatcher.test.ts` L76/90 use the matcher's own fixture specs, not `docs/api/`: left as they
  are.
- The historical batch import (`useEventBatchImport`) PATCHed `workflowState: ARCHIVED`; it now
  strips it from the PATCH (it only lifted the client-side future-date check) and keeps it for the
  create. An existing event that the import updates no longer has its state forced to ARCHIVED.
- `EventMapper.applyUpdateRequest/applyPatchRequest` are used by tests only (the controller has its
  own); only their `workflowState` lines were removed.

## Review Triage Log

Pass 1 (quick lens, 2026-11-10): high 2, medium 4, low 1, false 0. All patches (no-defer rule).
- high, patch: `event-full-workflow-api` runs post-deploy against production, and 16/16b/16d now really publish the agenda there (CDN, organizer notification). Fix: move those files to a dev-only collection.
- high, patch: unpublishing a phase that is not the current one publishes another, so speakers go public. Fix: 409 unless the phase is current; publishing a lower phase also gets 409.
- medium, patch: nullable `publishing_config` columns cause an NPE (500) and abort the scheduler loop. Fix: per-field defaults, and the per-event try covers all per-event work.
- medium, patch: an agenda schedule earlier than speakers, or speakers disabled, never fires. Fix: 400 on PATCH, the same rule in the form, spec text corrected.
- medium, patch: e2e `should_replaceEvent_when_putRequested` still PUTs `workflowState` and now gets 400. Fix: remove it and sweep all callers.
- medium, patch (implementer's own finding): auto-publish failures are only logged. Fix: failure metric (and alarm).
- low, patch: the 400 transition example leaks a Java class name. Fix: a client message with the allowed states.

## Design Notes

Work rules on rack:
- One heavy job at a time: never run a Gradle suite, Vitest, Playwright or a `git commit`/`git push` hook in parallel.
- Gradle runs with `--max-workers=4`, Vitest with `--maxWorkers=4`.
- Logs go to `~/.cache/batbern-test-logs/` via `tee`, with `set -o pipefail`.
- Commit on the current branch in conventional-commit steps (WIP commits early: rack can reboot), never `--no-verify`, never push.
- Services are managed only with `make dev-native-*`.

Architecture note:
- **Modules:** EMS event core (L2, workflow), publishing (L5), scheduled publishing.
- **New dependencies:** none.
- **Endpoints:**
  - `POST /events/{eventCode}/phases/{phase}/publish|unpublish`. The nearest existing operations are the replaced `publish/{phase}` ones, which cannot be extended because the verb sits before the instance.
  - `POST /events/{eventCode}/transition`. The nearest is `POST /events/{eventCode}/publish`, the same action shape.
  - `GET/PATCH /events/{eventCode}/publishing`. The nearest are `GET publish/status` and `publish/schedule`, folded into one resource.
- **Reused:** `EventWorkflowStateMachine`, `PublishingService`, `CdnInvalidationService`, `PublishValidationError`.
- **Deviations:** none.

The idiom is ADR-013 row 4: `POST /{instance}/{verb}`. It is already the most common EMS shape (speakers review/promote, registrations promote, mails retry).

Decisions by the agent under the owner's go (2026-11-10, "Yes" to 4.9b):
- The phase is the addressed instance (enum).
- The schedule is a PATCH of the publishing resource.
- `workflowState` leaves the event write bodies, so there is one way to change state.
- The scheduler is fixed to honour the stored schedule: a defect found here, which the no-defer rule says to fix.

## Verification

**Commands:**
- `set -o pipefail; ./gradlew :services:event-management-service:test --max-workers=4 2>&1 | tee ~/.cache/batbern-test-logs/49b2-ems.log`: expected BUILD SUCCESSFUL.
- `cd web-frontend && npm run type-check && npm run lint:api && npx vitest run --maxWorkers=4`: expected green.
- `make check-test-ratchet check-i18n-keys check-api-type-copies check-frontend-api-paths`: expected green.
- `./scripts/ci/run-bruno-tests.sh development --collection event-full-workflow-api` against local dev: expected PASS.

Final verification (2026-11-10, after review pass 1):
- Full suites green: shared-kernel, EMS, CUMS, PCS, gateway. Frontend type-check, vitest, lint:api, lint:api-internal, lint:ci, check:api-types, ratchet, i18n, type-copies and api-paths are green, and so are infrastructure jest, prettier and eslint (`~/.cache/batbern-test-logs/v49b2/`).
- Local dev: Bruno, all 16 collections green (including the new dev-only `event-publishing-api`). Playwright lifecycle, speaker-pool golden path, event-management flow and settings: 36 passed, 10 skipped (quarantined or skipped by design).
