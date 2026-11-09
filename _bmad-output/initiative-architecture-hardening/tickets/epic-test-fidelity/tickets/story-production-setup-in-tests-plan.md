---
title: 'Production setup in tests (17.0b3)'
type: 'refactor'
ticket: '0b3'
created: '2026-11-08'
status: done
baseline_revision: '5e7d04c11de57423a71eec5c30c37728db0d6808'
route: 'full'
route_source: 'auto'
risk: 'medium'
review: 'quick'
review_source: 'pinned'
lenses_ran: ['quick']
review_loop_iteration: 0
context:
  - '{project-root}/docs/plans/test-fidelity-proposal-2026-11.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Integration tests do not run what production runs. EMS, PCS and CUMS ITs use a permit-all security chain (#1012, #1134, #1145 passed tests). Every IT context holds a Mockito `EmailService` (`TestAwsConfig` in all three services, invisible to the ratchet), and `EmailService` checks addresses only when a `SesClient` exists, so a joined To: line (#1176) passes every test and local dev. 129 class-level `@Transactional` ITs roll back without a flush, so constraints and triggers never fire (#1104).

**Approach:** The production chain becomes the only chain, so every IT runs it. `EmailService` validates addresses before the `sesClient == null` branch and runs for real in every IT, on a recording mock `SesClient`. A listener on `AbstractIntegrationTest` flushes before every rollback. Each test, fixture or production defect these expose is fixed in this story.

## Boundaries & Constraints

**Always:** Production behaviour of the chains is unchanged: same matchers, same decoder in every non-`local` profile. The `local` chain stays. Tests assert the effect (the recorded SES request: parsed To:, Cc, subject), never `verify(emailService)`. A production defect exposed by the flush or the real chain gets a red test first, then the fix, and goes in Implementation Notes. The ratchet ends lower than its start (be-mock-in-process 55, be-transactional-it 129) and has no RAISED line.

**Never:** No test sends real mail, and no `SesClient` bean is created outside the recorder. Do not weaken `ReservedEmailDomain`, `assertNotTestMarked` or any production matcher to make a test pass. Do not convert the 129 class-level `@Transactional` ITs to non-transactional; the listener covers them. No change to the gateway chain.

**Decisions (owner, 2026-11-08):** one story, not split (plan length accepted). Tests that assert a delivered mail send to `success+<name>@simulator.amazonses.com` (AWS mailbox simulator); `@example.com` stays for data that must not receive mail.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Anonymous write | IT, no auth, `POST` organizer endpoint (EMS `/api/v1/events`, PCS `/api/v1/partners`, CUMS `/api/v1/companies`) | 401 | none |
| Attendee on organizer endpoint | real JWT conversion, `custom:role=ATTENDEE` | 403 | none |
| Joined To: | `sendHtmlEmailSync("a@x.ch,b@y.ch", …)`, no `SesClient` and mock `SesClient` | `IllegalArgumentException`, nothing recorded or sent | before any SES call |
| Group mail | `sendGroupHtmlEmailSync([a, b])` | one request, To: a and b | none |
| Unflushed violation | `@Transactional` IT leaves an entity that breaks a constraint | test fails with the DB error | skipped when the test already failed or the transaction is aborted (SQLState 25P02) |

</frozen-after-approval>

## Code Map

- Chains: EMS `events/config/SecurityConfig.java:64` (`filterChain`, `@Profile("!test")`), `:194` `jwtDecoder` (`!test`); test `events/config/TestSecurityConfig.java` (permit-all bean; 3 tests `@Import` it). PCS `partners/config/SecurityConfig.java` `productionFilterChain` (`!local & !test`), `testFilterChain` (`test`, main code), `jwtDecoder` (`!test`); test `TestSecurityConfig` (method security only). CUMS `companyuser/config/SecurityConfig.java:118` (`!test & !local`), `:178` `testFilterChain`, `:245` `jwtDecoder`.
- Measured (this session, `~/.cache/batbern-test-logs/0b3-exp-*`): production chain under `test` fails EMS 32/2542 (25 expect 403, get 401), PCS 9/247 (5 the same; 4 anonymous `GET /api/v1/partners/{name}` expect 200, production and gateway answer 401), CUMS 0/861. Only change needed: `spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://jwks.example.invalid/keys` in each `application-test.*`.
- Existing probe chain tests stay: EMS `ch/batbern/securitychain/EmsProductionSecurityFilterChainTest`, CUMS `config/ProductionSecurityFilterChainTest`. They cover the permit list, but not 403.
- `shared-kernel/.../service/EmailService.java`: `sendSyncTo` 230, `sendWithAttachmentsTo` 380, `sendHtmlEmailSyncWithAttachments` 515, `sendComposed` 619, `sendRaw` 757. Each has `if (sesClient == null)` before `deliveryForms`/`toDeliveryForm`; `assertSendable` (reserved domain) already runs before it. `deliverableRecipients` 1026 is the group-mail model.
- Global mocks: `TestAwsConfig.emailService()` EMS :100, CUMS :118, PCS :26 (`@Primary`). `@MockitoBean EmailService` in 9 EMS files: MailComposerSend, OwnTextComposer, SpeakerInvitationController, NewsletterSendIteration, EmailForwardingReplies, MailComposerController, MailGolden, EmailCaseInsensitiveMatching, SlidesOnline.
- Measured with real `EmailService` plus mock `SesClient`: EMS 8 fail (VenueCoordination stubs the mock in `setUp`; "Mail across events" counts 2 vs 4, now recorded), PCS 2 (`PartnerMeetingControllerIntegrationTest` recording), CUMS 58, all `NotAMockException` in 5 classes (AdditionalEmailVerification, ProfileMerge #1183, CompanyLogoRequest, ProfileMergeRequest, #1244 merge). CUMS and PCS lack `software.amazon.awssdk:ses` on the test compile path (shared-kernel declares it `implementation`).
- `shared-kernel/src/testFixtures/.../test/AbstractIntegrationTest.java` -- single base of every IT. Fixtures have no JPA: add `testFixturesImplementation 'org.springframework.boot:spring-boot-starter-data-jpa'`. No deferred constraints in any migration.
- Measured flush-before-rollback: EMS 10, CUMS 2, PCS 1 fail. Three tests (`PartnerRepositoryTest`, CUMS `CompanyRepository`, `UserController` case-email) provoke a violation themselves, leaving an aborted transaction. The rest: `EventControllerIntegrationTest` ×3 (`Event` bean validation: `venueCapacity`, `organizerUsername` missing on create/replace), `SpeakerWorkflowServiceIntegrationTest` ×5 (transient `Session` from `SessionUser`, DECLINED path), `SpeakerStatusController` (null `abstract` in `session_content_history`), `SpeakerStatusHistoryRepository` (`events_pkey`).
- Ratchet `web-frontend/scripts/test-ratchet.ts`: does not count `mock(OwnType.class)` in test `@Configuration`/`@TestConfiguration` classes (this is how the 3 `EmailService` mocks escaped).

## Tasks & Acceptance

**Execution:**
- [x] EMS, PCS, CUMS `SecurityConfig` and test configs -- one production chain and decoder in every profile but `local`; delete the permit-all beans and EMS `TestSecurityConfig`'s chain (keep method security); `jwk-set-uri` in test properties -- every IT runs the production chain.
- [x] Per service, a `ProductionChainIntegrationTest` (full context, real `JwtAuthenticationConverter` via `jwt().authorities(converter)` with `custom:role`) -- anonymous write 401, attendee on organizer write 403, organizer reaches the controller.
- [x] The 41 failing ITs -- correct each assertion to production's answer, or authenticate where the test meant an authenticated caller; the 4 PCS anonymous-detail tests assert 401.
- [x] `EmailService` -- validate and convert every To:/Cc address (delivery form, one address per entry) before the `sesClient == null` branch in all five paths; `EmailServiceTest` on a mocked `SesClient`: joined To: throws with and without the client, nothing sent or recorded.
- [x] `shared-kernel/src/testFixtures/.../mail/SesRecorder` -- a `SesClient` mock that answers `sendEmail`/`sendRawEmail` with a message id and returns recorded mails parsed (To, Cc, Reply-To, subject, html, attachment names); `reset()`. Self-test.
- [x] `TestAwsConfig` ×3 -- `EmailService` mock replaced by the `SesRecorder` bean; `testImplementation 'software.amazon.awssdk:ses'` in CUMS and PCS.
- [x] The 9 `@MockitoBean EmailService` files and the 9 measured failing classes -- assert on `SesRecorder`; recipients that must receive mail use `success+<name>@simulator.amazonses.com`.
- [x] `AbstractIntegrationTest` + `FlushBeforeRollback` listener (order after `TransactionalTestExecutionListener`'s rollback, skips failed tests and aborted transactions); fix the 10 remaining failures: test data, or a production defect test-first.
- [x] `test-ratchet.ts` + fixtures -- count `mock(OwnType.class)` inside test configuration classes under `be-mock-in-process`; baseline `--update`.
- [x] `coding-standards.md` "Test fidelity rules", `project-context.md` testing section, `CLAUDE.md` Authentication-for-testing section -- production chain in ITs, `SesRecorder`, flush listener.

**Acceptance Criteria:**
- Given EMS, PCS and CUMS, when `ProductionChainIntegrationTest` runs, then an anonymous write answers 401 and an attendee on an organizer endpoint answers 403.
- Given the old `EmailService` (validation behind the branch), when the joined-To: test runs, then it fails; with the change it passes.
- Given the full suites of shared-kernel, EMS, PCS, CUMS and the gateway, when run, then all pass, and `make check-test-ratchet` shows `be-mock-in-process` below 55 with no RAISED line.
- Given the repo, when grepping test sources for `mock(EmailService.class)` and `@MockitoBean` of `EmailService`, then nothing is found.

## Design Notes

Architecture note: modules: shared-kernel (`EmailService`, test fixtures), security config of EMS/PCS/CUMS, their tests, ratchet script · new dependencies: test-only (`ses` in CUMS/PCS tests, JPA in fixtures) · endpoints: none · reused: `jwt()` post-processor, `EmailAddresses`, `deliverableRecipients` · deviations: none.

Violations exposed and fixed here: permit-all chains in main code (PCS, CUMS), the global `EmailService` mocks, the ratchet gap. Frozen: the 129 class-level `@Transactional` ITs stay (listener makes them flush; conversion under the touch rule). Ratchet start: be-mock-in-process 55 (39 files), be-transactional-it 129.

## Verification

Rack rules: one Gradle job at a time, `--max-workers=4`, logs in `~/.cache/batbern-test-logs/0b3-*`, check `grep anon /sys/fs/cgroup/dev.slice/memory.stat` < 12 GB before each run.

**Commands:**
- `./gradlew :shared-kernel:test` then `:services:partner-coordination-service:test`, `:services:company-user-management-service:test`, `:services:event-management-service:test`, `:api-gateway:test` (each `--rerun`, tee to log) -- expected: 0 failures.
- `./gradlew checkstyleMain checkstyleTest` for touched modules -- expected: clean.
- `cd web-frontend && npx vitest run --maxWorkers=4 scripts/test-ratchet.test.ts && cd .. && make check-test-ratchet` -- expected: pass, no RAISED.
- Red check: old `EmailService` swapped in, joined-To: test fails; restored.

## Implementation Notes

- **Production defect, test first:** `QualityReviewService.rejectContent` synthesizes a v1
  `session_content_history` row when no version exists (organizer-path content). A session with a
  title and no abstract wrote `abstract = NULL` into a NOT NULL column, so the reject failed with a
  500 at commit. Red: `SpeakerStatusControllerIntegrationTest.should_rejectContent_when_rejected`
  (flush listener plus an explicit assertion on the stored version); fix: empty abstract. The other
  two `SessionContentVersion` builders already guard null.
- **Test data, not defects:** `EventControllerIntegrationTest` ×3 sent create/replace bodies that
  production answers 400 (entity requires venue, deadline, organizer; the spec marks them optional,
  see Findings). `SpeakerWorkflowServiceIntegrationTest` ×5: the fixture saved `SessionUser` without
  the `Session` side of the association, so deleting the session on DECLINED left a dangling
  reference in the test's persistence context only (a loaded session cascades). Fixed with
  `session.addSpeaker(su)`. `SpeakerStatusHistoryRepositoryIntegrationTest` re-flushed a session
  that had thrown; it now clears the EntityManager.
- **Mail:** besides the 9 `@MockitoBean` files, the two `@MockitoSpyBean EmailService` ITs
  (`ConversationReplyIntegrationTest`, `QnaNotificationServiceIntegrationTest`), the PCS
  `PartnerMeetingInviteFailureIntegrationTest` spy and the two unit tests with
  `mock(EmailService.class)` (`AdditionalEmailVerificationEmailServiceTest`, `PreviewHighlighterTest`)
  were moved to the `SesRecorder` / real `EmailService`. PCS meeting-mail records are asserted at the
  wire: a `ContractServer` on `HttpOutboundMailRecorder`'s RestTemplate against `ems-internal`.
  `AwsSesConfig.testSesClient` (a null `SesClient` bean in the `test` profile) was removed.
  `GlobalMailCenterIntegrationTest` now starts from an empty mail log (other ITs' sends are
  recorded now). `SpeakerInvitationControllerIntegrationTest` cannot observe its `@Async` mail (the
  class is `@Transactional`; the async thread cannot see the uncommitted speaker); its content is
  pinned by `MailGoldenTest`.
- **Chain:** four tests that were `@Disabled` because the permit-all chain made 401 untestable
  (EMS `SlotAssignmentControllerIntegrationTest`, `PublishingEngineControllerIntegrationTest`, EMS and
  PCS `TestFixtureCleanupControllerIntegrationTest`) now run. PCS anonymous partner detail: 401;
  the four tests also assert the authenticated answer.
- **Ratchet:** be-mock-in-process 55 → 47 (34 files); the new config-class rule adds one frozen line,
  CUMS `TestAwsConfig` mocking `CognitoIntegrationService`. be-transactional-it unchanged at 129.

**Coordinator verification (2026-11-08, rerun on rack, logs `~/.cache/batbern-test-logs/0b3-verify*`):**
shared-kernel 534/0, PCS 252/0, CUMS 864/0 (25 skipped), gateway 328/0 (26 skipped), checkstyle clean,
ratchet tests 50/50, `make check-test-ratchet` OK (be-mock-in-process 34 files/47, start 39/55;
be-transactional-it 129 unchanged). EMS 2545 with 1 failure: `EventControllerIntegrationTest.
should_replaceEvent_when_putRequested` got `ARCHIVED`. Cause: `eventRepository.findAll().get(0)`
(21 sites in that file) picks any of the three seeded events, one ARCHIVED; `findAll()` has no order.
Pre-existing order dependence, not a 0b3 defect. Fixed: `seededAgendaPublishedEvent()` looks the seeded
event up by title; class rerun 3x, 106/106 each. The 6 other `findAll().get(0)` sites in the repo assert a
single row first or follow the test's only insert; left as is.

**After the review patches (coordinator, 2026-11-08, logs `0b3-verify2-*`, `0b3-verify3-*`):** shared-kernel
536/0, CUMS 864/0 (25 skipped), EMS 2545/0 (15 skipped, full run after the `findAll()` fix), gateway 328/0
(26 skipped), PCS 252/0, checkstyle clean in all five modules, ratchet tests 50/50, `make check-test-ratchet`
OK (be-mock-in-process 34 files/47, start 39/55). The coordinator also moved the PCS organizer case of
`ProductionChainIntegrationTest` to the full body and 201 (CUMS behind `ContractServer`), the same flaw as
review finding 1 in EMS and CUMS.

## Findings (not fixed here)

- `events-core-api` `CreateEventRequest` requires only title, date, eventType; the `Event` entity
  rejects a create without venueName, venueAddress, venueCapacity, registrationDeadline and
  organizerUsername (400 via the constraint handler). Spec drift: a client following the spec gets
  400. Making the fields required is an API contract change (generated types, frontend).
- `GET /api/v1/events/{code}/analytics` has no `@PreAuthorize`: any authenticated user reads
  registration and attendance counts. Aggregate numbers, no personal data; role rule is an owner call.
- 13 unit tests still use `@Mock EmailService` with `verify(emailService)` (PCS
  `PartnerInviteEmailServiceTest`, EMS `*EmailServiceTest`, ...); not counted by the ratchet.


## Review Triage Log

Quick review, pass 1 (2026-11-08): 9 findings. Verdicts: medium 5, low 3, maybe-false 1 (plus 1 process gap). Routes: patch 7, defer 1, process 1. No intent_gap or bad_plan.

| # | Finding | Verdict | Route | Evidence / action |
|---|---|---|---|---|
| 1 | Organizer case of EMS/CUMS `ProductionChainIntegrationTest` posts `{}`, gets 400 from `@Valid` before `@PreAuthorize` runs | medium | patch | Spring resolves and validates arguments before invoking the proxied method; no chain role rule on those paths. Organizer sends a body the entity accepts and expects 2xx; `VALID_BODY` comment corrected; `08-operations-security.md` claim aligned. |
| 2 | No full EMS run after the `findAll().get(0)` fix | process | verification | Full EMS rerun after the patches. |
| 3 | `EmailService` Javadoc says SES client is null in tests, marker wall does not fire in tests | low | patch | Verified: `SesRecorder` makes `isLiveDelivery()` true in ITs. Docs corrected. |
| 4 | Broadcast `sendRawMessage(context, …)` with an undeliverable address throws `IllegalArgumentException` before SES, no record | medium | patch | Verified at `EmailService` sendRawMessage: only `EmailSendException`/`SesException` is recorded. Record the refusal (`InvalidParameterValue`, what SES answered before) and rethrow; test. |
| 5 | Record and local capture list Cc entries SES never receives | medium | patch | Pre-existing (`record(…, ccClean, …)`), fixed per owner rule "fix what you find": record and capture only deliverable Cc; test asserts the record. |
| 6 | `@Async` sends can land after the next test's `sesRecorder.reset()` | maybe-false | defer | Real executor in tests (`AsyncConfig` ThreadPoolTaskExecutor); not observed in two full EMS runs. Settles with a repeated-run stress of the mail ITs. |
| 7 | Gateway `CognitoConfig` defines a real, unused `SesClient` bean | low | patch | Pre-existing; no gateway main code uses it. Deleted (owner rule). |
| 8 | `PartnerMeetingInviteFailureIntegrationTest` POSTs for real to `localhost:8082` via unbound `HttpOutboundMailRecorder` | medium | patch | Verified: recorder builds its own RestTemplate; test binds only the client's. Bind it as `PartnerMeetingControllerIntegrationTest` does and assert the failure record; audit other PCS mail ITs. |
| 9 | Ratchet fixture header names 2 counted mocks, test counts 3 | low | patch | Comment corrected. |
