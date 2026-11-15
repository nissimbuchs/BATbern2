---
title: '#1315 Registrations: six pre-existing flaws from the 17.13 review'
type: 'bugfix'
ticket: ''
created: '2026-11-15'
status: 'built'
baseline_revision: '9f1faf0de0c8dddbef796ceeb1e6eb3bb1ab3771'
route: 'full'
route_source: 'auto'
risk: 'medium'
review: 'quick'
review_source: 'pinned'
lenses_ran: [quick]
review_loop_iteration: 0
followup_review_recommended: false
context:
  - '{project-root}/docs/architecture/module-map.md'
  - '{project-root}/docs/architecture/coding-standards.md'
warnings: ['multiple-goals', 'oversized']
deferred: []
---

<intent-contract>

## Intent

**Problem:** Issue #1315 (owner: "fix the 6 bugs in 1315", proposals approved, decisions settled by the caller): an old confirmation link revives cancelled/waitlisted registrations; PATCH status takes any string (500 on bad values, no capacity, no waitlist upkeep); organizer DELETE frees a place without waitlist promotion; controller writes through the repository in separate transactions; the pending confirmation mail is always German and a repeated pending registration gets a mail and then 409; three copies of the confirmation-mail sending.

**Approach:** All registration writes of `RegistrationController` move into `@Transactional` `RegistrationService` methods governed by one explicit transition table; one shared confirmation-mail sender (preferred language) serves create, organizer resend and the daily resend job; spec gets a lowercase status enum and documented 409s; tests on the existing integration harness, red first.

## Boundaries & Constraints

**Always:** test first (red then green) per item; ITs extend `AbstractIntegrationTest`, real `EmailService` into `SesRecorder`, CUMS through `CumsContractHarness`, no own-code mocks, assert what SES received (`sesRecorder.awaitAsyncSends()` before asserting presence or absence; absence needs a positive control); new exceptions extend `DomainException` in `exception/` (no handler, no `@ResponseStatus`) and get a row in `DomainExceptionResponseTest`; paths, `@PreAuthorize` rules and every transition the frontend or past operations send stay working; docs in the same commit (doc-drift); `make check-test-ratchet` clean; Flyway untouched; conventional commits on the current branch `fix/1315-registration-flaws`, each message ending with the two lines `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` and `Claude-Session: https://claude.ai/code/session_01BkGdx6q4jVDKkQkFEgqfcY`; commits through the hooks (no `--no-verify`), output teed to a log.

**Never:** push, open a PR, touch staging or production; bare `git stash`; edit an applied migration or the frozen ArchUnit store; parallel heavy jobs (Gradle, git commit hooks, Vitest, Playwright); background a suite; invent an after-commit mail mechanism (the promotion code sends inside the transaction via `@Async`; follow that); change the frontend UI beyond what the regenerated types force.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Confirm link, pending | status `registered` | 200 "Registration confirmed successfully!", `confirmed`, one confirmed mail | none |
| Confirm link, repeat | status `confirmed` | 200 "Registration already confirmed", no mail | none |
| Confirm link, processed | `cancelled`, `waitlist`, `attended`, `no_show` | 409, status unchanged, no mail | `RegistrationAlreadyProcessedException` (code `REGISTRATION_ALREADY_PROCESSED`) |
| PATCH bad value | `"CONFIRMED"`, `"foo"` | 400, row unchanged | Jackson enum mismatch -> existing 400 handler |
| PATCH same status / no status | `confirmed`->`confirmed`, `{}` | 200, no write, no mail | none |
| PATCH registered->confirmed | capacity C, holders > C (this row counted) | 409, unchanged; at or below C: 200 `confirmed` | `RegistrationCapacityExceededException` (new message, no "force" hint) |
| PATCH waitlist->confirmed | waitlisted row | same as `POST .../promote`: status `registered`, position cleared, promotion mail, no capacity check; 200 body `REGISTERED` | none |
| PATCH waitlist->cancelled | positions 1,2,3, cancel 2 | `cancelled`, position null, others renumbered 1,2; nobody promoted | none |
| PATCH registered/confirmed->cancelled | event full with waitlist | `cancelled` and waitlist promotion in ONE transaction (as today: no deadline rule) | none |
| PATCH attended/no_show/registered/confirmed transitions | see table in Design Notes | 200 | none |
| PATCH not in table | `cancelled`->any, any->`waitlist`, `confirmed`->`registered` | 409, unchanged, no mail | `RegistrationStatusTransitionException` (code `REGISTRATION_TRANSITION_NOT_ALLOWED`) |
| DELETE place holder | `registered`/`confirmed`, waitlist present | row gone, first waiting promoted (promotion mail) | none |
| DELETE waitlisted | position 1 of 1,2 | row gone, other renumbered to 1, nobody promoted | none |
| Anonymous repeat, pending | second POST same person while `registered` | 200 "You are already registered for this event. A new confirmation email has been sent.", one new confirmation mail, one row | none |
| Anonymous repeat, confirmed | second POST while `confirmed` | 409 as today, no mail | existing |
| Confirmation mail language | CUMS settings language `en` / `de` / none | English / German / German (rule of `notifyRegistrationConfirmed`) on create, organizer resend, daily job | none |

</intent-contract>

## Code Map

- `services/event-management-service/src/main/java/ch/batbern/events/controller/RegistrationController.java` -- `createRegistration` (repeat handling ~L88-118: today mails then throws 409 for a pending repeat), `confirmRegistration` (~L127, writes via repository), `updateRegistration` (~L206), `deleteRegistration` (~L231), `deleteMyRegistration` (~L312, repository read), `registrationOfEvent` (~L342), `sendConfirmation` (~L352, `Locale.GERMAN`). End state: no `RegistrationRepository`, `ConfirmationTokenService` stays (token parsing), `RegistrationEmailService`/`UserApiClient`/`@Value appBaseUrl` gone.
- `services/event-management-service/src/main/java/ch/batbern/events/service/RegistrationService.java` -- add the transition table and transactional writes; `notifyRegistrationConfirmed` (~L612) is the language rule to copy (null or `de*` -> German, else English); do NOT change `notifyRegistrationConfirmed`, `confirmByEmail`, `cancelRegistration` (their mock tests would fall under the touch rule). `createRegistration` repeat branches ~L124-158 (service returns the existing `registered` row; throws `IllegalStateException` 409 for confirmed/others).
- `service/WaitlistPromotionService.java` -- `promoteFromWaitlist(eventId)` (fills free places, `@Transactional`, joins), `manuallyPromote(code)` (waitlist -> `registered`, promotion mail, no capacity check). Reuse, do not duplicate.
- `service/RegistrationResendService.java` -- daily job; `resendFor` is copy #3 of token + deregistration URL + send; keep job semantics (skip when no address, count/timestamp, batch continues on error, `enabled` flag).
- `service/RegistrationEmailService.java` -- `sendRegistrationConfirmation(..., Locale)` is `@Async`; the shared sender must be a separate bean (self-invocation would bypass `@Async`).
- `domain/Registration.java` -- `CAPACITY_STATUSES` (registered, confirmed), statuses lower case; DB check `registrations_status_check` allows registered, confirmed, waitlist, cancelled, attended, no_show.
- `repository/RegistrationRepository.java` -- `findWaitlistByEventIdOrdered`, `countByEventIdAndStatusIn`, `findByRegistrationCode`, `findByEventIdAndAttendeeUsername`, `findResendEligible` (createdAt window). No unique index on `waitlist_position`.
- `exception/` -- `DomainException` base; models `RegistrationNotOnWaitlistException`, `RegistrationCapacityExceededException` (+ its handler in `RegistrationExceptionHandler`, details activeCount/capacity); test `exception/DomainExceptionResponseTest` needs a row per new exception/constructor.
- `docs/api/event-registrations-api.openapi.yml` -- `createRegistration` 200 (~L208), `updateRegistration` (~L670), `deleteRegistration` (~L745), `confirmRegistration` (~L895), schema `UpdateRegistrationStatusRequest` (~L1644, free string today). Generator (`build.gradle` `openApiGenerateRegistrations`) makes `UpdateRegistrationStatusRequest.StatusEnum`; use `getValue()`.
- `web-frontend/src/services/api/eventRegistrationService.ts` `updateRegistrationStatus` -- sends lower case already; only `cancelled` is sent by the UI (`RegistrationActionsMenu.tsx`: cancel shown for every status but CANCELLED). `RegistrationWizard.tsx` ~L195-250: 200/201 -> success screen, 409 "already registered" -> error. `ConfirmRegistrationPage.tsx`: any error -> generic "failed" text (409 lands there; acceptable, no UI change).
- Tests: `controller/RegistrationControllerIntegrationTest.java` (harness model: `cums.user(...)`, `cums.userWithLanguage(username, lang)` gives `<username>@test.com`, `sesRecorder.mailsTo`, `createTestRegistration`, `cappedOtherEvent`, explicit cleanup, no class `@Transactional`); existing `should_return409_when_duplicateRegistrationAttempted` becomes the 200 case; `RegistrationCapacityIntegrationTest` (PATCH cancel promotion tests ~L425-460 must stay green); `service/RegistrationResendServiceTest.java` (5 Mockito mocks) is replaced by `service/RegistrationResendServiceIntegrationTest.java`; `architecture/EmsEndpointPlacementTest.java` (unfrozen rules).
- Docs: `docs/architecture/06-backend-architecture.md` "Registration, Waitlist and Capacity" (~L1486-1540); `docs/user-guide/appendix/changelog.md` section "Unreleased — after BATbern60"; plan status lines in `_bmad-output/initiative-architecture-hardening/tickets/epic-ems-walls/tickets/story-registrations-out-of-eventcontroller-plan.md` and `story-domain-exception-base-plan.md`.

## Tasks & Acceptance

**Execution (one commit per group, this order; red test first in each):**
- [x] C1 item 1 -- `RegistrationService.confirmRegistration(eventCode, registrationId)` `@Transactional` (lookup, event-code check with today's exceptions/messages, `registered`->`confirmed` + `notifyRegistrationConfirmed`, `confirmed` -> "already" result without mail, else new `exception/RegistrationAlreadyProcessedException` 409); controller only validates the token and maps the result. Spec: 409 on confirm, idempotency text. Doc 06 + changelog.
- [x] C2 item 2 -- spec enum + table text + 409 on PATCH; `cd web-frontend && npm run generate:api-types`, commit `src/types/generated/event-registrations-api.types.ts`; `RegistrationService.changeStatus(Event, registrationCode, String target)` `@Transactional` with the table (Design Notes); new `exception/RegistrationStatusTransitionException`; capacity refusal via a new `RegistrationCapacityExceededException` constructor/factory with a confirm-specific message (handler unchanged). ITs for every matrix PATCH row incl. 400s. Doc 06 (the table) + changelog.
- [x] C3 item 3 -- `RegistrationService.deleteByOrganizer(Event, code)` `@Transactional`: place holder -> delete + `promoteFromWaitlist`; waitlisted -> delete + renumber. Spec delete description. Doc 06 + changelog.
- [x] C4 item 4 -- remaining controller reads (`registrationOfEvent`, `deleteMyRegistration` lookup) via `RegistrationService` (`@Transactional(readOnly = true)` where it reads); `RegistrationRepository` leaves the controller; add unfrozen ArchUnit rule in `EmsEndpointPlacementTest`: `RegistrationController` does not depend on `RegistrationRepository` (fails on an empty "that"). Doc: module-map Gates bullet of `EmsEndpointPlacementTest`.
- [x] C5 item 6 -- new `service/RegistrationConfirmationSender` (`@Service`): `Optional<UserResponse> send(Registration, Event)` = fresh token + deregistration URL (`app.base-url`) + attendee from `UserApiClient` + locale from `getPreferredLanguage` (rule above) + `RegistrationEmailService.sendRegistrationConfirmation`; empty when the attendee has no address (nothing sent, warn log). Used by create, organizer resend (empty -> 409 `ResendConfirmationResponse` "The attendee has no e-mail address"), `RegistrationResendService` (constructor drops token service, email service, user client, base URL). Delete `RegistrationResendServiceTest`, add `RegistrationResendServiceIntegrationTest` (set `created_at` back via `JdbcTemplate`; call `resendUnconfirmedRegistrations()`; assert mail to the attendee with a working confirm link, resend count/timestamp; disabled instance built with real beans sends nothing (positive control); `cums.unknownUser` row fails, the other is still mailed; filter by own addresses/rows, the DB is shared). Update ratchet baseline (STALE only). Doc 06 "Confirmation Links and Resend Jobs" if it names the job's internals.
- [x] C6 item 5 -- English/German mail ITs (create, organizer resend, daily job) via `cums.userWithLanguage`; pending anonymous repeat -> 200 with the message, exactly one new confirmation mail, one row; existing 409 duplicate test turned into this case plus a confirmed-repeat 409 test without mail. Spec 200 description + `alreadyRegistered` example. Doc 06 diagram already says "resend the confirmation mail": add the 200; changelog.
- [x] C7 -- `docs(epic-17)`: `status: 'built'` -> `'done'` in the two plan files named in the Code Map; nothing else.

**Acceptance Criteria:**
- Given any request in the I/O matrix, when sent through MockMvc on the production chain, then status, body message, DB row and SES mails match the matrix row.
- Given `RegistrationController`, when `EmsEndpointPlacementTest` runs, then it has no dependency on `RegistrationRepository`.
- Given a cancel or delete that frees a place, when it runs, then the status change and the promotion happen inside one `@Transactional` `RegistrationService` method (the promotion joins it); the IT shows both effects after the request, and the Implementation Notes say that a failing promotion cannot be provoked without an own-code mock, so the rollback is shown by the transaction boundary, not by a test.
- Given the full EMS suite and `make check-test-ratchet`, when run, then 0 failures and no RAISED/STALE line.

## Implementation Notes

- Commits on `fix/1315-registration-flaws`: C1 cf0ca2beb, C2 5fa850eb2, C3 04b74a806, C4 83d28f1fc,
  C5 86f0728e2, C6 60deb9b72, C7 21bdcb2ab; plus fe2b6359e `fix(hooks)` (found along the way, below).
- Rollback (AC 3): cancel (`changeStatus`) and organizer delete (`deleteByOrganizer`) write the status
  or delete and call `promoteFromWaitlist`, which joins the same `@Transactional` method. A failing
  promotion cannot be provoked without an own-code mock (it only reads and writes registrations), so the
  rollback is shown by the transaction boundary, not by a test; the ITs show both effects after one request.
- The registration's confirmation mail is now sent from `RegistrationService.createRegistration`
  (inside its transaction, `@Async` like the waitlist mail). A CUMS failure of the attendee lookup now
  rolls the new registration back instead of leaving it without a mail.
- C6 language ITs passed on first run: the sender of C5 already carries the language rule (C5 before
  C6 per the plan order). Red in C6 was the pending-repeat 200.
- `RegistrationResendServiceIntegrationTest` expires the ShedLock row before each run
  (`lock_until` = 2000-01-01): `lockAtLeastFor=PT1M` skipped the second call silently; deleting the row
  does not work (the provider only updates rows it inserted), and `now() - 1s` did not unlock either
  (`lock_until` is `TIMESTAMP` without zone; measured, cause believed to be the session time zone).
- Pre-commit hook: `check:api-types` passed every drift in a linked worktree (GIT_DIR exported to hooks,
  cwd taken as work-tree top). C1 was committed without its regenerated types (carried by C2). Fixed in
  fe2b6359e with `env -u GIT_DIR`, measured old exit 0 / new exit 1 on the same drift.
- `RegistrationExceptionHandler.handleCapacityExceeded` left unchanged as planned; its log line still
  says "Add-participant refused" for the confirm refusal too.

## Plan Change Log

## Review Triage Log

### 2026-11-15 — Review pass
- verdicts: 5 findings — high 0, medium 1, low 4, false 0, maybe-false 0
- findings:
  - `[low]` `[reject]` No test for the no-address path (resend job skip, organizer 409) — CUMS `UserResponse.email` is required in the spec (users-api `required: [.., email]`) and the contract harness validates answers, so the path is defensive and not reachable with a spec-valid CUMS; a test would need contract-violating data. Not met in everyday use.
  - `[medium]` `[defer]` `confirmByEmail` (ACCEPT reply) still confirms waitlist/attended/no_show — pre-existing, not caused by this change; recorded in `deferred` and reported to the owner. Resolved on coordinator request by 5941b9aab (same rule as the link, tests on the harness); removed from `deferred`.
  - `[low]` `[reject]` Repeated pending registration answers "a new confirmation email has been sent" even when CUMS has no address — same unreachable no-address precondition as the first row; fixing it adds a branch.
  - `[low]` `[patch]` Test Javadoc names section "Status changes" — corrected to "Organizer status changes (#1315)".
  - `[low]` `[patch]` `notifyRegistrationConfirmed` Javadoc said the confirmation is already persisted — reworded: called inside the caller's transaction, never throws so the confirmation is not rolled back.

## Design Notes

**Architecture note.** Modules: EMS L3 registrations (controller, `RegistrationService`, `WaitlistPromotionService`, `RegistrationResendService`, new `RegistrationConfirmationSender`), platform `exception/`. New dependencies: `RegistrationService` and `RegistrationResendService` -> `RegistrationConfirmationSender` (same module); none cross-module or cross-service. Endpoints: none new; changed behaviour of `createRegistration`, `confirmRegistration`, `updateRegistration`, `deleteRegistration` (nearest: `promoteFromWaitlist`, reused by the waitlist->confirmed transition). Reused: `WaitlistPromotionService`, `MailPipeline`/`EmailService` through `RegistrationEmailService`, `DomainException`, `CumsContractHarness`, `SesRecorder`. Deviations: none; lowercase request enum is an owner decision (status is lowercase on this request already; responses stay UPPER_CASE).

**Transition table (PATCH; owner proposal plus adjustments from evidence).**

| From \ To | registered | waitlist | confirmed | cancelled | attended | no_show |
|---|---|---|---|---|---|---|
| registered | no-op | 409 | capacity check | promote | yes* | yes* |
| waitlist | 409 | no-op | = promote endpoint (ends `registered`, mail) | leave + renumber | 409 | 409 |
| confirmed | 409 | 409 | no-op | promote | yes | yes |
| attended | 409 | 409 | yes (correction) | yes* (no promote) | no-op | yes |
| no_show | 409 | 409 | yes (correction) | yes* (no promote) | yes | no-op |
| cancelled | 409 | 409 | 409 | no-op | 409 | 409 |

`*` = adjustment, evidence: on 2026-11-09 BATbern60 attendance was recorded through this PATCH, 1 `registered`->`attended` and 8 `registered`->`no_show` (memory `bb60-no-shows-2026-11-09`); the organizer menu offers Cancel for every status except CANCELLED (`RegistrationActionsMenu.tsx` `showCancelButton`), so attended/no_show -> cancelled is sendable. Promote = `promoteFromWaitlist(eventId)` only when the row held a place (registered, confirmed). Capacity check registered->confirmed: the row already counts; refuse only when `countByEventIdAndStatusIn(CAPACITY_STATUSES) > capacity`. Corrections from attended/no_show to confirmed take no capacity check (post-event).

Implement the table as one static map (from -> allowed targets) next to a switch for side effects; a test iterates all 36 pairs against the DB check (no 500 anywhere).

## Verification

Rack rules: one heavy job at a time; Gradle `--max-workers=4`; foreground; `set -o pipefail; <cmd> 2>&1 | tee ~/.cache/batbern-test-logs/1315-<name>.log`, then grep the log.

**Commands:**
- `./gradlew :services:event-management-service:test --max-workers=4` -- expected: BUILD SUCCESSFUL, 0 failures, checkstyle clean, response conformance no STALE.
- `make check-test-ratchet` -- expected: no RAISED, no STALE.
- `cd web-frontend && npm run type-check && npm run lint:api && npx vitest run --maxWorkers=4 src/components/public/Registration src/pages/public/__tests__/ConfirmRegistrationPage.test.tsx src/services/api/eventRegistrationService.test.ts src/components/organizer/EventPage` -- expected: green.
- `make check-frontend-api-paths` -- expected: green.
- Local dev only (`make dev-native-up` / `dev-native-restart-service SERVICE=event-management`): Bruno `event-full-workflow-api`, `participants-api`, `events-crud-api` via `./scripts/ci/run-bruno-tests.sh` (or `bru run` per collection with the development env), no `Skipping invalid file`; Playwright `e2e/registration-flow.spec.ts`, `e2e/organizer/event-registrations-management.spec.ts`, `e2e/organizer/event-participants.spec.ts` with `TEST_ENV=development` -- expected: green, never staging.

## Auto Run Result

- **Summary:** all six #1315 flaws fixed: confirm link only `registered`->`confirmed` (409 otherwise), PATCH status by an explicit transition table with lowercase enum (400 for bad values), organizer DELETE promotes/renumbers, all controller reads/writes through transactional `RegistrationService` methods (ArchUnit-guarded), one `RegistrationConfirmationSender` in the attendee's language for create/organizer resend/daily job, repeated pending registration answers 200 with one new link.
- **Files:** `RegistrationController` (thin), `RegistrationService` (table, changeStatus, deleteByOrganizer, confirmRegistration, reads), `RegistrationConfirmationSender` (new), `RegistrationResendService` (uses sender), two new `DomainException`s + `RegistrationCapacityExceededException.forConfirmation`, spec + regenerated types, 06-backend-architecture, module-map, changelog, ITs (`RegistrationControllerIntegrationTest`, new `RegistrationResendServiceIntegrationTest`, deleted mock unit test), ArchUnit rule + fixtures, ratchet baseline, pre-commit hook fix for linked worktrees.
- **Review:** 2 low patches (Javadoc), 1 medium deferred (`confirmByEmail`), 2 low rejected (unreachable no-address path).
- **Follow-up review recommended:** false (patched: 2 low, 0 medium, 0 high).
- **Verification:** full EMS suite 2945 tests, 0 failures (RegistrationControllerIntegrationTest 122, RegistrationResendServiceIntegrationTest 7, EmsEndpointPlacementTest 2, DomainExceptionResponseTest 61, RegistrationCapacityIntegrationTest 18, all after the last code commit); checkstyle re-run after the review patches (comments only); `make check-test-ratchet` and `make check-frontend-api-paths` clean; type-check, lint:api, Vitest 530/530; local-dev Bruno 24+18+17 requests green; Playwright 11/12 in a 2-worker run, the failing cancel test 6/6 alone (parallel `@e2e.batbern.invalid` user sweep by participant-import/event-participants specs, inferred from code, not proven).
- **Residual risks:** confirmation mail now sent inside the create transaction (a CUMS failure rolls the new registration back); rollback of status+promotion shown by the transaction boundary, not a test; refused PATCH confirmation logs "Add-participant refused".
- **Follow-up (coordinator request, 2026-11-15):** 5941b9aab `confirmByEmail` follows the link rule (refusal silent and logged, as for an unmatched reply; `RegistrationServiceConfirmationTest` replaced by `RegistrationConfirmationIntegrationTest`, ratchet -1); cc24d0c63 the capacity refusal log names method and path. Full EMS suite 2951 tests, 0 failures, 6 skipped; checkstyle and `make check-test-ratchet` clean.
