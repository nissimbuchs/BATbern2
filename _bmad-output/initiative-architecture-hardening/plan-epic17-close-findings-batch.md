---
title: 'Epic 17 close: seven small findings (after-commit mail, module script, drift guard, logo spec, alarm, map wording, CUMS 502)'
type: 'bugfix'
ticket: ''
created: '2026-11-15'
status: 'built'
baseline_revision: '4bdd796811b2475be47f55cbb145ba2821135fba'
route: 'full'
route_source: 'auto'
risk: 'medium'
review: 'quick'
review_source: 'pinned'
lenses_ran: ['quick']
review_loop_iteration: 0
followup_review_recommended: false
context:
  - '{project-root}/CLAUDE.md'
  - '{project-root}/docs/architecture/coding-standards.md'
  - '{project-root}/docs/architecture/module-map.md'
warnings: ['multiple-goals', 'oversized']
deferred:
  - summary: >-
      The deregistration-link mail of DeregistrationService is still sent inside its transaction.
    evidence: |-
      sendDeregistrationLinkOnce and deregisterByEmail call sendDeregistrationLinkEmail inside @Transactional, after the once-per-window claim. A rollback after the send would drop the claim but not the mail. Deferring it needs DeregistrationLinkOnceIntegrationTest (class-level @Transactional, verifies a mocked DeregistrationEmailService) moved onto the SesRecorder first, since a never-committing test transaction would never run an after-commit send.
    location: >-
      services/event-management-service/src/main/java/ch/batbern/events/service/DeregistrationService.java:151,228
    severity: low
  - summary: >-
      CUMS scheduled Cognito jobs catch only CognitoIdentityProviderException.
    evidence: |-
      UserReconciliationService and CognitoConfirmationResendJob let an SdkClientException escape the job run. No HTTP status involved; out of scope of item 7.
    location: >-
      services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/CognitoConfirmationResendJob.java
    severity: low
---

<intent-contract>

## Intent

**Problem:** Seven findings from the Epic 17 retrospective, the EventController story review and the P5 spike: (1) waitlist promotion mails (and the other registration-module mails) are sent inside the transaction, so a rolled-back promotion has already mailed; (2) the EMS module scripts strip `//.*` under `re.S`, so the first `//` (comment or `https://` in a string) drops the rest of the file and every recorded number is undercounted; (3) `EmsModuleNames` is a hand copy of the scripts' naming rules and can drift; (4) the logo e2e spec still expects `logos/{year}/…`; (5) the scheduled-job alarm clears itself 5 minutes after a nightly failure; (6) module-map.md names the dropped 17.15 as owner of the 2 frozen mail-core lines; (7) CUMS answers 500 instead of 502 when the Cognito client itself fails (`SdkClientException`).

**Approach:** One test-first commit per item, on branch `docs/epic17-close`, no push. Send registration-module mails after commit through one small EMS helper; tokenize Java source properly in the scripts; move the naming rules into one JSON file read by both the scripts and `EmsModuleNames`; fix the spec regex from the code; widen the alarm window; reword the map; map every SDK failure in `CognitoIntegrationServiceImpl` to `CognitoOperationException` (502).

## Boundaries & Constraints

**Always:** test first, and record the red run (failing test name + message) before the fix; one conventional commit per item, message ending with the two trailer lines below; rack rules: ONE heavy job at a time (Gradle, commit hooks, Vitest, Playwright, docker), Gradle with `--max-workers=4`, every make/gradlew/git commit wrapped as `set -o pipefail; <cmd> 2>&1 | tee ~/.cache/batbern-test-logs/<name>.log`, long commands in the FOREGROUND (timeout up to 600000 ms), `./gradlew --stop` at the very end; stage paths explicitly; Test fidelity rules (no own-code mocks, positive control for absence, `sesRecorder.awaitAsyncSends()` before asserting a mail or its absence); doc-drift: a change under a mapped path updates a mapped doc in the same commit or carries `[no-doc]` with a reason.

Commit trailer (exact):
```
Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01BkGdx6q4jVDKkQkFEgqfcY
```

**Never:** push, PR, staging or production; bare `git stash`, `git add -A`/`.`; touching the CDN-invalidation side finding of `docs/plans/pre-deploy-gate-plan.md` (owner decision); rewriting history in archived docs (`docs/archive/**`, old numbers stay, corrected ones are added beside them); editing an applied Flyway migration; editing the ArchUnit store to accept a violation; running two heavy jobs at once.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Capacity PATCH commits | healthy event, 1 waiting | waiter promoted, exactly 1 `waitlist-promotion` mail | none |
| Capacity PATCH/PUT rolls back | failing capacity hook | waiter stays `waitlist`, `SesRecorder` has no mail to the waiter | 4xx/5xx answer |
| Promotion path in a rolled-back tx | `manuallyPromote`, `changeStatus` cancel, `deleteByOrganizer`, `cancelRegistration` inside a `TransactionTemplate` marked rollback-only | no mail; the same call committed sends one (positive control) | none |
| `confirmByEmail` rolled back | pending registration | no `registration-confirmed` mail; committed: one | none |
| No transaction (unit test) | mocked repositories | send runs immediately (existing `WaitlistPromotionServiceTest` stays green) | none |
| Script: `"https://x"` then later code | Java line with a URL string | tokens after it are still counted | none |
| Script: `// a` / `/* b */` / `"// not a comment"` / text block / `'"'` | comment vs literal | comments removed, literals kept | none |
| Rules drift | change a keyword in `ems-modules.json` | scripts and `EmsModuleNames` change together; the shared examples table checked by both a Python test and a JUnit test | test fails on mismatch |
| Cognito client failure | `SdkClientException` on `adminCreateUser` via `POST /internal/users/provision` | 502, standard `ErrorResponse` (status 502, error "Bad Gateway", message, correlationId) | user row rolled back |
| Cognito service failure | `InternalErrorException` | 502 as before | unchanged |

</intent-contract>

## Code Map

Item 1, after-commit mail (EMS):
- `services/event-management-service/src/main/java/ch/batbern/events/service/WaitlistPromotionService.java` -- `promoteFromWaitlist` (l.72) and `manuallyPromote` (l.104) call `waitlistPromotionEmailService.sendPromotionEmail` (`@Async`) inside `@Transactional`. Every path (PATCH status cancel, organizer DELETE, capacity PATCH/PUT via `RegistrationEventAdapter.capacitySaved`, promote endpoint, attendee cancel) goes through these two methods.
- `.../service/RegistrationService.java` -- in-transaction sends at l.147, 235 (`sendWaitlistConfirmationEmail`), l.818 `notifyRegistrationConfirmed` (used by `confirmRegistration` and `confirmByEmail` l.840), l.1031 `sendOrganizerAddedConfirmation`, l.1147, and `sendRegistrationConfirmation` if present: all registration-module mails go after commit (audit-the-class within the module).
- Existing after-commit pattern, copied 4 times: `config/CacheConfig.clearAfterCommit` (l.51), `SpeakerWorkflowService.evictEventReadCacheAfterCommit` (l.231), `AgendaConfigService` (l.100, runs before AND after on purpose), shared-kernel `UploadService` l.446. New helper `config/AfterCommit.run(Runnable)` (platform, L0): sync active → `registerSynchronization(afterCommit)`, else run now. Replace the identical copies in `CacheConfig` and `SpeakerWorkflowService` with it; leave `AgendaConfigService` (different semantics) and shared-kernel alone.
- Tests: `src/test/java/ch/batbern/events/controller/EventCapacityRollbackIntegrationTest.java` (commits like production; add mail assertions to both tests: healthy -> 1 promotion mail to `success+wanda-waiting@simulator.amazonses.com`, `sesRecorder.reset()`, failing -> none). New `src/test/java/ch/batbern/events/service/RegistrationMailAfterCommitIntegrationTest.java` (not `@Transactional`, extends `AbstractIntegrationTest`, `TransactionTemplate` with `setRollbackOnly()` vs commit, for `manuallyPromote`, `changeStatus(...,"cancelled")`, `deleteByOrganizer`, `confirmByEmail`; mirror the fixture setup of `EventCapacityRollbackIntegrationTest` incl. `cums.user(...)`). `WaitlistPromotionServiceTest` (Mockito unit test, no tx) must stay green unchanged.

Item 2, module script:
- `scripts/architecture/ems-module-deps.py` l.34 and `ems-module-target.py` l.28: `re.sub(r'//.*|/\*.*?\*/','',src,flags=re.S)`. Under `re.S`, `//.*` eats the rest of the FILE. Replace by a small tokenizer `strip_comments(src)` in a new importable `scripts/architecture/ems_modules.py` (import it via `sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))` so it works under `python3 -I`). It skips `"…"` (with escapes), `'…'`, `"""…"""` text blocks, and removes `//…\n` (keep the newline) and `/*…*/`. String contents stay (the scripts count capitalized tokens in literals on purpose).
- Test: `scripts/architecture/test_ems_modules.py` (unittest, run `python3 -I scripts/architecture/test_ems_modules.py`).
- Numbers: run the fixed scripts (from `services/event-management-service/src/main/java/ch/batbern/events`: `python3 -I <repo>/scripts/architecture/ems-module-deps.py . <dir>/emsdeps.json && python3 -I <repo>/scripts/architecture/ems-module-target.py <dir>`) on HEAD, on `fdf9b25b1` (before the EventController story) and on `89b3618f` (the 2026-11-07 baseline), the old trees via `git archive <rev> services/event-management-service/src/main/java | tar -x -C <scratch>`. Old script on HEAD today: 20 pairs / 56 refs. Recorded old numbers: baseline 28/100 (2026-11-07), closing 23/71, EventController story 23/71 -> 20/56 (plan also has a full-line-strip estimate 29/96 -> 26/76).
- Record the corrected numbers beside the old ones, saying the old ones came from the buggy script, in: `_bmad-output/backlog/story-eventcontroller-keeps-only-http-plan.md` (bullet "Module script, before/after" ~l.120, "Script quirk" l.126, summary l.214, deferred item ~l.30: mark resolved), `docs/architecture/module-map.md` l.4 ("100 known places (measured 2026-11-07)"), `_bmad-output/initiative-architecture-hardening/initiative-architecture-hardening.md` l.80 and closing note l.123. Not `docs/archive/**`.

Item 3, drift guard:
- `services/event-management-service/src/test/java/ch/batbern/events/architecture/EmsModuleNames.java` -- hand port (RULES, OVERRIDES, TARGET, renames mail->mail_center, notification->org_notify). Used by `EmsModuleRules` l.253; pinned by `EmsModuleRulesFixtureTest` l.259ff (13 names).
- Chosen design: one data file `scripts/architecture/ems-modules.json` (ordered `rules`: module + `packages` prefixes + `startsWith` + `contains`; `overrides`; ordered `target` regex groups; `renames`; `examples`: `"<firstPackage>/<SimpleName>": "<module>"`, seeded with the 13 names of the fixture test). Both scripts read it (via `ems_modules.py`); `EmsModuleNames` reads it with Jackson, locating it from `user.dir` upwards like `shared-kernel/src/testFixtures/.../contract/OpenApiContract.locate`. A JUnit test and the Python test each assert every `examples` entry, so the two interpreters cannot disagree on the shared table. Simpler than parsing Python from Java: no parser, nothing to keep in step but ~20 lines of matching code checked by the shared examples. Declare the JSON as an input of the EMS `test` task in `services/event-management-service/build.gradle` (so Gradle does not reuse a stale result).
- Red: before switching `EmsModuleNames` to the file, write the JUnit test that reads `examples` from the JSON and add one example the hand copy gets wrong only if the file says otherwise (e.g. temporarily change one keyword in the JSON); record red, then make `EmsModuleNames` read the file and restore. Script output after the refactor must be identical to item 2's numbers.

Item 4, logo spec:
- `web-frontend/e2e/companies/company-create-logo.spec.ts:93` regex `/logos/\d{4}/companies/<slug>/logo-.+\.png$`. Current key: `CompanyLogoUploadHandler.logoKey` (CUMS) = `logos/companies/{name}/logo-{uploadId}.{ext}` (Story 17.11a, no year). New regex `/logos/companies/<slug>/logo-.+\.png$`. Local dev is up (`make dev-native-status`): check which checkout the CUMS process runs (`readlink /proc/<pid>/cwd`); if it is not this worktree, say so (that checkout's `logoKey` decides the result). Run `cd web-frontend && TEST_ENV=development npx playwright test e2e/companies/company-create-logo.spec.ts --project=chromium` once before (red) and once after (green), via tee.

Item 5, alarm:
- `infrastructure/lib/constructs/ecs-service-alarms.ts` l.322-340 (alarm 5): period 5 min, 1 evaluation period, `NOT_BREACHING`, `addOkAction`. The job runs daily at 01:00, so the next empty period clears it and the OK action closes the GitHub issue (#956 lambda). Fix: period 1 hour, `evaluationPeriods: 24`, `datapointsToAlarm: 1`, `NOT_BREACHING` kept (the window now spans the job interval), and drop `addOkAction` for this alarm only (a skipped item does not recover by time passing; a human closes the issue). Keep alarm 4 unchanged.
- Test: `infrastructure/test/unit/scheduled-job-failure-alarm.test.ts` test `should_alarmOnAnyFailureInFiveMinutes_when_metricFilterMatches` -> rename and assert `Period: 3600`, `EvaluationPeriods: 24`, `DatapointsToAlarm: 1`, `TreatMissingData: 'notBreaching'`, `OKActions: Match.absent()`. Run `cd infrastructure && npx jest test/unit/scheduled-job-failure-alarm.test.ts`.
- Docs (`.github/doc-drift-mappings.yml`: `infrastructure/` -> `docs/architecture/08-operations-security.md`): table row l.435; also `docs/operations/runbooks.md` l.133ff "Scheduled job failures" (window and that the issue stays open).

Item 6, map wording:
- `docs/architecture/module-map.md` l.97-98: "mail core 2 lines, `MailPipeline` using `compose.MailRenderer` and `PlaceholderScanner` (17.15)" -> the 2 lines stay frozen as an owner-accepted exception (owner 2026-11-14, no EMS modularization; 17.15 dropped). Docs-only commit `[no-doc]` not needed (it is a doc). Can share no commit with item 2's module-map number change: separate commits.

Item 7, CUMS 502:
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/CognitoIntegrationServiceImpl.java` -- every method catches `CognitoIdentityProviderException` only (l.98, 117, 136, 159, 171, 181, 192). Change the generic catch to `software.amazon.awssdk.core.exception.SdkException` (parent of the service and the client exceptions), after the specific branches. `GlobalExceptionHandler.handleCognitoOperationException` (l.690) already answers 502 with the standard body. CUMS has no `DomainException`; the existing exception + handler fit.
- Harness: `src/test/java/ch/batbern/companyuser/config/InMemoryCognito.java` `failNext(Operation, CognitoIdentityProviderException)` and its `failures` map -> accept `SdkException` (or `RuntimeException`).
- Red test: MockMvc `POST /internal/users/provision` (see `InternalUserController` l.116 and an existing IT that calls it for the request shape/auth) with `failNext(ADMIN_CREATE_USER, SdkClientException.create("Unable to load credentials"))` -> expect 502 and body fields; plus the same with `InMemoryCognito.internalError(...)` as control; and extend `UserProvisioningCognitoRollbackIntegrationTest` with the client-exception case (row rolled back).
- Not in scope, report only: `UserReconciliationService`, `CognitoConfirmationResendJob` also catch only `CognitoIdentityProviderException` (scheduled jobs, no HTTP status).

## Tasks & Acceptance

**Execution (one commit each, in this order):**
- [x] `docs/architecture/module-map.md` -- item 6 wording -- `docs(module-map): …`
- [x] `scripts/architecture/ems_modules.py`, `test_ems_modules.py`, both scripts, number docs -- item 2 -- `fix(scripts): …`
- [x] `scripts/architecture/ems-modules.json`, scripts, `EmsModuleNames.java`, new JUnit test, EMS `build.gradle` -- item 3 -- `test(ems): …`
- [x] EMS `config/AfterCommit.java`, `WaitlistPromotionService`, `RegistrationService`, `CacheConfig`, `SpeakerWorkflowService`, tests -- item 1 -- `fix(registrations): …` (EMS mapped docs: update `docs/architecture/06-backend-architecture.md` where it describes waitlist promotion mail, or `[no-doc]` with reason)
- [x] CUMS `CognitoIntegrationServiceImpl`, `InMemoryCognito`, tests -- item 7 -- `fix(cums): …` (`docs/architecture/06b-user-lifecycle-sync.md` if it states the 502 mapping, else `[no-doc]`)
- [x] `infrastructure/lib/constructs/ecs-service-alarms.ts`, its test, docs -- item 5 -- `fix(infra): …`
- [x] `web-frontend/e2e/companies/company-create-logo.spec.ts` -- item 4 -- `test(e2e): …`

**Acceptance Criteria:**
- Given a capacity PATCH or PUT whose transaction rolls back, when the request finishes and async sends are awaited, then the waiter got no mail, while the same committed request mails the waiter once.
- Given any promotion path or `confirmByEmail` run in a rolled-back transaction, when async sends are awaited, then `SesRecorder` holds no mail; committed, it holds exactly one.
- Given a Java file with `https://` in a string before other code, when the module scripts run, then the later tokens are counted (Python test green under `python3 -I`), and the corrected numbers are recorded beside the old ones in the three docs.
- Given a change to the naming rules in `ems-modules.json`, when the EMS tests and the Python test run, then both use it; the shared examples pass in both.
- Given the logo spec on local dev, when run, then it passes with the yearless key (or the report says why it could not run).
- Given the CDK template, when synthesized, then the scheduled-job alarm has period 3600, 24 evaluation periods, 1 datapoint to alarm, `notBreaching`, no OK action.
- Given a Cognito client failure on provision, when called over HTTP, then CUMS answers 502 with the standard error body.

## Implementation Notes

Commits on `docs/epic17-close` (not pushed): ba95ea2d5 (item 6), 63b6fe220 (item 2), 3b85db2db (item 3),
c621a588e (item 1), daea9167d (item 7), 9326858f0 (item 5), 7960f1169 (item 4).

- Item 2 numbers, pairs / refs, old script -> fixed: `89b3618f` with the 2026-11-07 rules 28/100 -> 36/151
  (old reproduced exactly), with today's rules 27/94 -> 34/144; `fdf9b25b1` 23/71 -> 29/96; HEAD 20/56 -> 26/76.
- Item 1: `AfterCommit.run` wraps the send, not the lookups: CUMS user, language and confirmation token
  still run in the transaction (their failures still roll back). Covered via `RegistrationConfirmationSender`
  too (the daily resend job has no transaction, so it sends right away). Not deferred: the
  deregistration-link mail of `DeregistrationService`.
- Item 4: CUMS on local dev runs this worktree's jar (`/proc/37508/cwd`).
- Verification: EMS full 2980 tests / 0 failures / 6 skipped; CUMS full 962 / 0 / 16; alarm unit
  suites 80 passed; Python 13 OK; logo spec 1 passed on local dev; `make check-test-ratchet` clean.

## Plan Change Log

- Item 5: the nightly live guards `infrastructure/test/e2e/monitoring/alert-scenarios.spec.ts` and
  `incident-response.spec.ts` require an OK action on every deployed alarm (#956). Dropping it for the
  scheduled-job alarm would turn them red after the deploy, so both name `*-Scheduled-Job-Failures` as
  their one exception (same commit). Not run against AWS (need `TEST_E2E=true`).
- Item 3: the 13-name test in `EmsModuleRulesFixtureTest` moved into the JSON `examples`
  (`EmsModuleNamesSharedRulesTest`), so the names exist once.

## Review Triage Log

### 2026-11-15 — Review pass
- verdicts: 5 findings — high 0, medium 1, low 3, false 0, maybe-false 0, rejected-as-plan-edit 1
- findings:
  - `[low]` `[patch]` module-map.md pairs the old 100 (2026-11-07 rules) with 144 (today's rules) — patched: states 151 (same rules) and 144 (today's rules), commit 05f2640a9.
  - `[low]` `[patch]` 06-backend-architecture.md says "every" registration mail is deferred, then names an exception — patched wording ("but one", with the reason), commit 05f2640a9; the deregistration-link mail itself deferred (see frontmatter).
  - `[medium]` `[patch]` the Python side of the drift guard runs in no gate — patched: `make check-ems-module-rules`, step in the CI `test-ratchet` job, commit 0bea81871; ran green locally (13 tests).
  - `[reject]` plan checklist not ticked for items 1 and 7 — fix edits this build's plan; ticked as bookkeeping at finalize.
  - `[low]` `[patch]` backlog plan summary still says "script bug, deferred" — patched wording, commit 05f2640a9.

## Design Notes

Architecture note (5 lines):
- Modules touched: EMS registrations (L3) + platform `config` (L0 helper `AfterCommit`), EMS architecture tests, CUMS identity (Cognito integration), infrastructure monitoring construct, e2e spec, scripts.
- New dependencies: `EmsModuleNames` (test code) reads `scripts/architecture/ems-modules.json`; registrations -> platform `AfterCommit` (downward, allowed).
- New or changed endpoints: none (nearest considered: none needed; `POST /internal/users/provision` only changes 500 -> 502 on client failure, as the spec's error mapping already intends).
- Components reused: the existing after-commit synchronization pattern (now one helper), `CognitoOperationException` + its 502 handler, `SesRecorder`, `InMemoryCognito`, `CumsContractHarness`, `OpenApiContract.locate`-style lookup.
- Deviations: none. Epic 17 "leave it cleaner": two copies of the after-commit idiom collapse into the helper; the script rules stop existing twice.

## Verification

**Commands (each via `set -o pipefail; … 2>&1 | tee ~/.cache/batbern-test-logs/<name>.log`, one at a time):**
- `python3 -I scripts/architecture/test_ems_modules.py` -- expected: OK
- `./gradlew :services:event-management-service:test --max-workers=4 --tests '*EventCapacityRollbackIntegrationTest' --tests '*RegistrationMailAfterCommitIntegrationTest' --tests '*WaitlistPromotionServiceTest' --tests '*EmsModule*'` -- expected: green (red first, recorded)
- `./gradlew :services:company-user-management-service:test --max-workers=4 --tests '*Cognito*'` plus the new IT -- expected: green (red first)
- `cd infrastructure && npx jest test/unit/scheduled-job-failure-alarm.test.ts` -- expected: green (red first)
- `cd web-frontend && TEST_ENV=development npx playwright test e2e/companies/company-create-logo.spec.ts --project=chromium` -- expected: green on local dev
- Full suites once at the end: `./gradlew :services:event-management-service:test --max-workers=4` then `./gradlew :services:company-user-management-service:test --max-workers=4` -- expected: BUILD SUCCESSFUL; record test counts from the XML results
- `make check-test-ratchet` -- expected: no RAISED; STALE -> `cd web-frontend && npm run check:test-ratchet -- --update` and commit
- `./gradlew --stop` at the very end

## Auto Run Result

- Summary: seven findings fixed test first, one commit each (ba95ea2d5 map wording, 63b6fe220 script tokenizer, 3b85db2db shared naming rules, c621a588e mails after commit, daea9167d CUMS 502, 9326858f0 alarm, 7960f1169 logo spec), plus review fixes 05f2640a9 (docs) and 0bea81871 (CI step for the Python test).
- Review: quick lens, 5 findings: 4 patched (1 medium, 3 low), 1 rejected (plan edit), 2 items deferred (frontmatter).
- Follow-up review recommended: false (one medium patched, docs/CI only).
- Verification: EMS full suite 2980 tests, 0 failures, 6 skipped; CUMS full suite 962, 0 failures, 16 skipped; alarm Jest 80 passed; Python 13 OK; logo spec on local dev 1 passed; check-test-ratchet clean. After the EMS full run one checkstyle line-length fix in RegistrationService (pre-commit checkstyle green, suite not re-run). The two live alarm guards (alert-scenarios, incident-response) were edited and compiled, not run (need AWS).
- Residual risks: an exception inside an after-commit action reaches the caller with the data already committed (actions only queue @Async sends); deregistration-link mail still in-transaction (deferred).

