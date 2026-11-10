---
title: 'ArchUnit freeze and first wall fixes (17.5)'
type: 'feature'
ticket: '5'
created: '2026-11-08'
status: 'done'
baseline_revision: 'eb341346514da557f3c28e27c470c01b898d03b0'
route: 'full'
route_source: 'auto'
risk: 'medium'
review: 'quick'
review_source: 'pinned'
lenses_ran: ['quick']
review_loop_iteration: 0
context:
  - '{project-root}/docs/architecture/ADR-016-service-directions-and-service-to-service-calls.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The EMS module walls and the one-direction rule between services (ADR-016) were checked by no build, and EMS code that belongs to the mail center or the platform sits in `service/`, `scheduler/`, `controller/`, `exception/`.

**Approach:** ArchUnit with `FreezingArchRule` in the CUMS and EMS `test` task (built). Then fix in this story every frozen violation that needs no 17.12 design by moving classes to the module the map assigns, so the committed EMS store ends far smaller than the first freeze. What stays frozen names its owning story. *(Owner renegotiation 2026-11-08: "leave the architecture cleaner"; the fix part was added.)*

## Boundaries & Constraints

**Always:** rules run in each service's normal `test` task; store committed with `allowStoreCreation=false`, `freeze.refreeze=false`; `FrozenRuleKeysTest` pins store keys to the declared rules. Moves keep simple class names (Spring bean names unchanged), endpoints, profiles, error codes and behaviour; each moved class's tests move to the mirroring test package.

**Never:** no rule for the full L0–L6 layering (17.12); no ArchUnit in PCS (most downstream, nothing to freeze); no store edit by hand to accept a violation. Not moved here: `NewsletterEmailService`, `SlidesOnlineEmailService` (16.8c retires their old send paths, 17.15 splits mail core), the PCS clients (17.8), the CUMS clients (17.6/17.7).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| develop | code + committed store | `test` passes | none |
| new L6 dependency | a new EMS class outside mail center uses a `mailcenter`/`forwarding` class | the EMS rule fails naming the class | build fails |
| new downstream client | a new CUMS `*EventManagement*Client` / `*Partner*Client` | CUMS direction rule fails | build fails |
| fixed violation | a frozen dependency removed | test passes, the store loses that line | none |
| own text with unknown placeholder / already sent | send through any controller | 400 `UNKNOWN_PLACEHOLDER` / 409 `ALREADY_SENT`, body as today | from the mail-center advice |
| STOMP CONNECT | `/ws` or `/api/v1/watch/ws` with/without valid JWT | authenticated / rejected as today | none |

</frozen-after-approval>

## Code Map

Gate part (built): `architecture/{EmsModuleRules,EmsModuleWallsTest,EmsModuleRulesFixtureTest,FrozenRuleKeysTest}` (EMS), `architecture/{ServiceDirectionRules,ServiceDirectionTest,ServiceDirectionRulesFixtureTest,FrozenRuleKeysTest}` (CUMS), `archunit.properties` + `archunit_store/` per service.

Fix part, EMS root `services/event-management-service/src/main/java/ch/batbern/events/` (store at start: mail center 73, watch 2, usermerge 0, notification 0, PCS clients 4; CUMS 4):
- `watch/JwtStompInterceptor` -> `security/`: guards every STOMP CONNECT (`/ws` used by `web-frontend/src/services/notificationWebSocketClient.ts`, and the Watch endpoint), imports nothing from `watch`; platform L0. `config/WebSocketConfig` stays.
- `config/InboundEmailConfig`, `service/InboundEmailListenerService` (SQS, guard-test key `InboundEmailListenerService#handleS3Notification` is by simple name), `service/InboundEmailRouter` (uses `client.PartnerMeetingRsvpClient`: stays frozen under 17.8), `service/CalendarDeclineService`, `controller/DevEmailController` (`@Profile("local")`), `scheduler/EmailForwardingLogRetentionScheduler` -> `forwarding/` (inbound pipeline, beside `EmailForwardingListener`).
- `service/InboundReplyProcessor`, `scheduler/MailMessageRetentionScheduler` -> `mailcenter/inbound/`.
- `forwarding/ModeratorReplyAddress` (22 lines, imports only `domain.Event`) -> `mailcenter/render/` (mail core, excepted from the wall; used by 5 speaker mail services).
- `exception/GlobalExceptionHandler` handlers `handleUnknownPlaceholderException`, `handleAlreadySentException` (lines ~1398–1440) -> new `mailcenter/MailCenterExceptionHandler` (`@RestControllerAdvice`, `@Order(Ordered.HIGHEST_PRECEDENCE)`, bodies unchanged); the global advice has no `@Order` and a catch-all, so precedence is required.
- `service/CalendarInviteUid` keeps a Javadoc `{@link}` to `CalendarDeclineService`: make it fully qualified (no import).
- Tests to move with their class (same package): `service/{InboundEmailListenerServiceTest,InboundEmailRouterTest,InboundReplyProcessorSwitchTest,CalendarDeclineServiceIntegrationTest}`, `controller/DevEmailControllerReplyTest`, `scheduler/EmailForwardingLogRetentionSchedulerTest`, `watch/JwtStompInterceptorTest`. Others only change imports.
- `CalendarDeclineServiceIntegrationTest` has class-level `@Transactional` (ratchet `be-transactional-it`): remove it, clean up in `@AfterEach`. Its `@MockitoBean DeregistrationEmailService` and `UserApiClient` stay until 17.0b (comment says so).
- Error-code coverage already exists: `mailcenter/OwnTextComposerIntegrationTest` (`ALREADY_SENT` line 236, `UNKNOWN_PLACEHOLDER` line 308).

## Tasks & Acceptance

**Execution:**
- [x] Gate part (ArchUnit 1.4.1 in CUMS + EMS, rules, fixture tests, stores, `FrozenRuleKeysTest`, docs) -- see Implementation Notes.
- [x] `security/JwtStompInterceptor` (+ test) -- move from `watch/`.
- [x] `forwarding/` -- move `InboundEmailConfig`, `InboundEmailListenerService`, `InboundEmailRouter`, `CalendarDeclineService`, `DevEmailController`, `EmailForwardingLogRetentionScheduler` (+ tests); fix `CalendarInviteUid` Javadoc link.
- [x] `mailcenter/inbound/` -- move `InboundReplyProcessor`, `MailMessageRetentionScheduler` (+ tests).
- [x] `mailcenter/render/ModeratorReplyAddress` -- move; update the 5 speaker mail services and tests.
- [x] `mailcenter/MailCenterExceptionHandler` -- new advice with the two handlers; remove them and their imports from `GlobalExceptionHandler`.
- [x] `CalendarDeclineServiceIntegrationTest` -- drop class-level `@Transactional`, add `@AfterEach` cleanup; update the ratchet baseline (moved path + lower count).
- [x] Run the EMS architecture tests: fixed lines leave the store; commit the smaller store. Re-run `scripts/architecture/ems-module-deps.py` before/after.
- [x] `docs/architecture/module-map.md` Gates line + `source-tree.md` EMS packages if they name moved classes; Implementation Notes: store before/after per rule, owner story per remaining group.

**Acceptance Criteria:**
- Given develop's code, when each service's `test` runs, then the ArchUnit tests pass with the committed store, and store sizes are recorded beside the script's numbers (difference explained).
- Given the fix part, when the EMS store is regenerated by the run, then watch is 0, the mail center wall holds only `NewsletterEmailService`/`SlidesOnlineEmailService` lines, and every remaining group names its owning story.
- Given the full EMS suite, when it runs after the moves, then it passes with no test deleted or weakened.

## Design Notes

Architecture note: modules: EMS `forwarding`, `mailcenter`, `security`, `exception`, `service`, `scheduler`, `controller`, `config`; CUMS/EMS test sources · new dependencies: none (ArchUnit 1.4.1 test, built) · endpoints: none changed · reused: `MailPipeline` mail-core exception, existing ITs for error codes · deviations: `ModeratorReplyAddress` goes into `mailcenter/render` (mail core stays inside the mailcenter package until 17.15 extracts it).

`forwarding` is part of the L6 mail center (module map), so the inbound pipeline joins it; the old "forwarding = L1" was wrong (Implementation Notes, rework).

## Verification

Rack rules: one heavy job at a time; Gradle `--max-workers=4`, `./gradlew --stop` before Node work; logs to `~/.cache/batbern-test-logs/`; before each run `grep anon /sys/fs/cgroup/dev.slice/memory.stat`, stop above 12 GB (cap 20 GB).

**Commands:**
- `set -o pipefail; ./gradlew --max-workers=4 :services:event-management-service:test --tests 'ch.batbern.events.architecture.*' 2>&1 | tee ~/.cache/batbern-test-logs/175-ems.log` -- expected: pass; store smaller.
- `set -o pipefail; ./gradlew --max-workers=4 :services:event-management-service:test :services:event-management-service:checkstyleMain :services:event-management-service:checkstyleTest 2>&1 | tee ~/.cache/batbern-test-logs/175-ems-full.log` -- expected: BUILD SUCCESSFUL.
- `set -o pipefail; ./gradlew --max-workers=4 :services:company-user-management-service:test --tests 'ch.batbern.companyuser.architecture.*' 2>&1 | tee ~/.cache/batbern-test-logs/175-cums.log` -- expected: pass.
- `./gradlew --stop; make check-test-ratchet` -- expected: OK after the baseline update.
- `git status` after a second architecture run -- expected: no store file changed.

## Implementation Notes

Plan approved by the coordinator under the owner's standing go for autonomous overnight work (2026-11-07); no open questions.

### Result (2026-11-07)

**Store sizes** (one line per member-level dependency, line numbers stored as `:0`, so moving code does not churn):

| Rule (store key) | Lines | Origin classes | Class pairs | Script `ems-module-deps.py` (same modules) |
|---|---|---|---|---|
| mailcenter wall (mail core excepted) | 63 | 7 | 18 | `platform -> mail` 2 (GlobalExceptionHandler, same 2 pairs). Rest not visible to the script, see below |
| watch wall | 2 | 1 | 1 | `platform -> watch` 1 (WebSocketConfig -> JwtStompInterceptor): identical |
| usermerge wall | 0 | 0 | 0 | no edge into usermerge: identical |
| notification + forwarding -> L6 | 29 | 2 | 8 | notification: 0 edges into mail/watch/usermerge, identical. forwarding: not visible (see below) |
| EMS: no `*Partner*Client*` | 4 | 4 | – | n/a (script has no direction check) |
| CUMS: no `*EventManagement*Client` / `*Partner*Client` | 4 | 4 | – | n/a |

Why the mail numbers differ: the script assigns modules by class name, ArchUnit by package. The script's
`mail` module is `mailcenter` + `forwarding` + every class named `*Mail*`, `*Email*`, `*Newsletter*`, `*Inbound*`
etc. So `AliasMailProcessor`, `EmailForwardingService`, `InboundReplyProcessor`, `NewsletterEmailService`,
`SlidesOnlineEmailService`, `MailMessageRetentionScheduler` are inside `mail` for the script (no violation)
but outside the `mailcenter` package for ArchUnit (16 of the 18 pairs). Conversely the script's
`registrations -> mail` 26, `speakers -> mail` 25, `sessions -> mail` 3, `tasks -> mail` 3 are almost all uses
of `MailPipeline` / `MailVariable*`, which this rule excepts (below).

**Deviation, needs owner/architect eyes: mail core excepted from the mailcenter wall.** Built literally, the
mailcenter rule froze 397 lines / 84 class pairs, 66 of them `*EmailService -> mailcenter.render.MailPipeline`
and `mailcenter.variables.*`. Those packages are the mail core, L1 in the module map, and the map's reuse table
tells every new mail sender to use `MailPipeline`. Under the literal rule every new mail-sending method (not
only new classes) would fail the build, so the gate would contradict the map on day one. The rule therefore
treats `mailcenter.render..` and `mailcenter.variables..` as mail core (`EmsModuleRules.MAIL_CORE_IN_MAILCENTER`,
fixture test `should_allow_when_classUsesMailCoreInsideMailcenter`). The I/O-matrix row "a class outside
`mailcenter` uses a `mailcenter` class fails" holds for every other mailcenter class (mutation check below
used `MailCenterSwitch`). 17.12/17.15 move mail core out of the package; then the exception goes.

**PCS: no rule, no dependency.** PCS is the most downstream service (ADR-016: CUMS upstream of all, EMS
upstream of PCS), so there is no service it could call downstream and no store to freeze. The ArchUnit
dependency was left out of its `build.gradle` rather than added unused (task 1 named PCS; task 4 and the
Boundaries say no rule). Recorded in `module-map.md` → Gates.

**Rule notes.** Top-level classes only (nested exceptions inside the client interfaces are not clients).
Production rules import main classes only (`DoNotIncludeTests`), so the fixture packages under
`architecture/fixtures` never reach the frozen rules. Rule descriptions are the store keys: renaming one
orphans its store file.

**Verification run (rack, one job at a time, logs in `~/.cache/batbern-test-logs/175-*.log`):**
- EMS `ch.batbern.events.architecture.*` + `checkstyleTest`: pass (5 frozen rules, 6 fixture tests). CUMS
  `ch.batbern.companyuser.architecture.*` + `checkstyleTest`: pass. PCS: nothing to run.
- Mutation: throwaway `ch.batbern.events.service.ArchUnitMutationProbe` taking a `MailCenterSwitch`:
  `MAILCENTER_IS_EDGE FAILED`, message names `ArchUnitMutationProbe.probe(...)`. Probe deleted.
- Missing store (CUMS store moved away): `StoreInitializationFailedException: Creating new violation store is
  disabled`. Store restored byte-identical.
- Fixed violation shrinks the store and a new one fails: proven in `should_failOnNewViolation_and_shrinkStore_when_frozen`
  with an in-memory store (the committed store is never written by tests).
- After all runs, sha1 of every store file unchanged.
- Not run: the full EMS / CUMS suites (only the architecture tests and checkstyle); the rules are pure test
  code with no Spring context, so no interaction expected, but CI's full `test` is the first full run.


### Rework after review (2026-11-07 00:00, finished 2026-11-08 after the rack reboot)

- **`forwarding` is mail center (L6), not L1.** `module-map.md` lists forwarding under the L6 mail center;
  the Code Map's "forwarding = mail core, L1" was wrong. The mailcenter wall now covers
  `mailcenter` + `forwarding` as one module (they may use each other). The old "notification +
  forwarding -> L6" rule froze 29 lines, all `forwarding -> mailcenter`, i.e. intra-module: false
  violations.
- **`notification` keeps its own rule**: uses no L6 module (mail center, watch, usermerge), mail core
  excepted. Store empty: the script also finds 0 such edges.
- **`FrozenRuleKeysTest` (EMS, CUMS)**: ArchUnit's `allowStoreCreation=false` refuses only a missing
  store folder; a new or renamed rule description is stored on first run with all current violations
  accepted. The test pins the declared rule descriptions to the keys of `stored.rules`. Proven: renaming
  the usermerge rule fails it, naming the new key (log `175-ems-keys-mutation.log`).
- **EMS client pattern back to `*Partner*Client*`** (the rework had narrowed it to `*Partner*Client`,
  dropping the two `…ClientImpl` from the store, 4 -> 2).
- EMS store regenerated (never committed before, so no accepted violation was lost or added beyond
  the rule change itself).

**Store sizes after rework:**

| Rule | Lines | Note |
|---|---|---|
| mail center wall (`mailcenter` + `forwarding`, mail core excepted) | 73 | 31 into `mailcenter` (as before minus `forwarding ->`), 42 into `forwarding`: `DevEmailController`, `InboundReplyProcessor`, `CalendarDeclineService`, the two retention schedulers, and 5 speaker mail services via `ModeratorReplyAddress` |
| watch wall | 2 | unchanged |
| usermerge wall | 0 | unchanged |
| notification uses no L6 | 0 | new key |
| EMS no `*Partner*Client*` | 4 | unchanged |
| CUMS direction | 4 | unchanged |

**Verification 2026-11-08 (rack, one job at a time, anon memory < 3.1 GB throughout):** EMS
`EmsModule*` + `FrozenRuleKeysTest` + `checkstyleTest`: 13 tests pass (`175-ems.log`); CUMS
architecture tests + `checkstyleTest`: 3 pass (`175-cums.log`); sha1 of both stores unchanged by the
runs.

**Open, owner decision:** `forwarding.ModeratorReplyAddress` is used by every speaker mail service
to set the reply-to; under the wall, each new speaker-mail method that sets it fails the build (same
trap as `MailPipeline`). Options in the session summary.

### Fix part (2026-11-08)

Moves done as the Code Map lists them (simple names, endpoints, profiles, error codes unchanged; each
moved class's own test moved with it, the rest only changed imports). New
`mailcenter/MailCenterExceptionHandler` (`@Order(Ordered.HIGHEST_PRECEDENCE)`) holds the two handlers
byte-for-byte; `GlobalExceptionHandler` lost them and its two `mailcenter` imports.
`CalendarInviteUid` links `CalendarDeclineService` fully qualified. `CalendarDeclineServiceIntegrationTest`
has no class-level `@Transactional`; an `@AfterEach` deletes the registrations and events it created.

**Store before/after (EMS; regenerated by the run, not by hand):**

| Rule | Before | After | What remains, owner |
|---|---|---|---|
| mail center wall | 73 | 3 | `NewsletterEmailService.prepare` and `SlidesOnlineEmailService.processSend` -> `compose.OwnText`, `SlidesOnlineEmailService.sendRegistrantOwnText` -> `compose.AlreadySentException`: 16.8c (old send paths) / 17.15 (mail core split) |
| watch wall | 2 | 0 | – |
| usermerge wall | 0 | 0 | – |
| notification uses no L6 | 0 | 0 | – |
| EMS no `*Partner*Client*` | 4 | 4 | `PartnerApiClient(+Impl)`, `PartnerMeetingRsvpClient(+Impl)`: 17.8 |
| CUMS direction | 4 | 4 | unchanged: 17.6 / 17.7 |

**Script `ems-module-deps.py`** (before on `eb341346`, after on the working tree): `platform -> mail` 2 -> 0
(the two `GlobalExceptionHandler` handlers), `platform -> watch` 1 -> 0 (`WebSocketConfig ->
JwtStompInterceptor`); every other edge count identical (only the order of equal counts changed). Module
sizes: platform 33 -> 34 (`JwtStompInterceptor`), watch 27 -> 26, mail 117 -> 118
(`InboundEmailConfig` is now in `forwarding`). The script's other `mail` moves were already `mail` by name.

**Deviation: `ModeratorReplyAddress` is not "imports only `domain.Event`".** It used
`EmailForwardingProperties` and `ForwardingAlias` from its own package (no import line, so the Code Map
missed it). In `mailcenter/render` it now imports both from `forwarding`: mail core (L1 in the map)
depends on the mail center (L6). The wall does not see it (render is inside the `mailcenter` package),
and it is no new edge for the script (both are `mail`). Left for 17.15, which extracts mail core and has
to cut this edge too (e.g. the moderator alias domain as a mail-core setting).

**Correction to the notes above:** call/constructor lines in the store keep their real line numbers
(e.g. `NewsletterEmailService.java:740`); only member-type lines carry `:0`. ArchUnit's default line
matcher ignores numbers when comparing, so code moving inside a file does not fail the rule; the file
is rewritten only when the set of violations changes.

**Verification (rack, one job at a time, anon memory < 4.1 GB):**
- `:services:event-management-service:test --tests 'ch.batbern.events.architecture.*'`: pass; store 73 -> 3,
  watch 2 -> 0 (`175-ems.log`).
- Full EMS `test` + `checkstyleMain` + `checkstyleTest`: BUILD SUCCESSFUL, 2501 tests, 0 failures, 0 errors,
  18 skipped (`175-ems-full.log`; all moved tests ran in their new packages, `OwnTextComposerIntegrationTest`
  covers `UNKNOWN_PLACEHOLDER` / `ALREADY_SENT` through the new advice, 8/8).
- CUMS architecture tests + EMS architecture tests again: pass; sha1 of every store file unchanged by
  this second run (`175-cums.log`).
- `make check-test-ratchet`: after `--update`, OK. Baseline diff: the two mock entries moved to the
  `forwarding/` path (counts 1, unchanged until 17.0b), `be-transactional-it` 131 -> 130 files.
- Not run: the full CUMS suite (no CUMS code changed in the fix part), Bruno/Playwright (no endpoint,
  security rule or STOMP behaviour changed; `JwtStompInterceptorTest` 5/5 covers CONNECT with and without
  a valid JWT). No live STOMP check against a running service.


### Follow-up in step 3 (coordinator, 2026-11-08)

- **Hidden upward edge removed.** After the move, `mailcenter/render/ModeratorReplyAddress` imported `forwarding.EmailForwardingProperties` and `ForwardingAlias`: mail core (L1) on the mail center (L6), invisible to the wall because render sits inside `mailcenter`. Fixed with the module map's inversion pattern: `ModeratorReplyAddress` is now an interface in mail core, `forwarding/ForwardingModeratorReplyAddress` implements it (`@Component`); senders keep injecting the interface. Three tests build the implementation.
- **New frozen rule** `EmsModuleRules.mailCoreUsesNoMailCenter`: mail core (`mailcenter.render`, `.variables`) uses no other mail-center class. Store 2 lines (`MailPipeline.render` -> `compose.MailRenderer`, `MailPipeline.renderNotice` -> `compose.PlaceholderScanner`), owner 17.15. Fixture `fixtures/mailcenter/variables/FixtureMailVariable` + test `should_fail_when_mailCoreUsesMailCenter`.
- Story references removed from the new code and comments (build.gradle, archunit.properties, architecture tests, `MailCenterExceptionHandler`).
- Script re-run (`ems-module-*.py`, `eb341346` vs working tree): violating edges 28 -> 26, refs 99 -> 94; `GlobalExceptionHandler` 16 -> 14; mail core -> mail center 11 -> 9 (now in the PRD, 17.14/17.15).
- Verification: full EMS `test` 2503 tests, 0 failures, 18 skipped (`175-ems-full2.log`; its checkstyleTest failed on 2 unused imports, fixed); re-run of EMS architecture + `Speaker*` + `OwnTextComposerIntegrationTest` + checkstyleMain/Test + CUMS architecture + checkstyleTest: BUILD SUCCESSFUL (`175-recheck.log`); `make check-test-ratchet` OK.

**Final store sizes:** mail center wall 73 -> 3 (16.8c, 17.15) · watch 2 -> 0 · usermerge 0 · notification 0 · mail core uses no mail center 2 (17.15, new) · EMS -> PCS clients 4 (17.8) · CUMS 4 (17.6/17.7).

## Plan Change Log

## Review Triage Log

**Pass 1 (quick lens, 2026-11-08), loop 0.** Verdicts: high 0, medium 1, low 6, false 0, maybe-false 0. All routed to patch, applied by the step-3 implementer (`175-review-fix.log`), then full verification re-run.

| # | Finding | Verdict | Route | Evidence / action |
|---|---|---|---|---|
| 1 | `module-map.md` Gates omits the mail-core rule and its 2 frozen lines | medium | patch | the map is where developers read what remains and who owns it; rule + 2 lines (17.15) added |
| 2 | CUMS client pattern ends at `Client`, EMS at `Client*`: a CUMS `…ClientImpl` would pass | low | patch | widened to `…Client[^.]*`; fixture `EventManagementFixtureClientImpl` asserted; old negative fixture `PartnerFixtureClientConfig` would now match, replaced by `PartnerFixtureService`; CUMS store unchanged (4) |
| 3 | Story numbers in code comments (`EmsModuleRules`, `ServiceDirectionTest`; the latter named only 17.7) | low | patch | reworded without story numbers; owners stay in module-map.md |
| 4 | Bean name changed to `forwardingModeratorReplyAddress`, against "bean names unchanged" | low | patch | no by-name injection found (grep); `@Component("moderatorReplyAddress")` restores the name |
| 5 | New files only intent-to-add in the index | low | patch | stage everything at commit (`git add -A` on the touched paths), checked in step 5 |
| 6 | `docs/watch-app/architecture.md` lists `JwtStompInterceptor` under `watch/` | low | patch | moved to `security/` in both trees |
| 7 | `should_fail_when_mailCoreUsesMailCenter`'s `noneMatch(FixtureMailPipeline)` is vacuous | low | patch | `FixtureMailPipeline.resolve(FixtureMailVariable)` gives a real mail-core-to-mail-core edge |

## Production verification

2026-11-10: live since #1258 (merged 75a2d315). Its gate ran as a required check, green, in the later production PRs #1281 and #1286, and its baseline only shrank there. Checked in the git history and the PR checks, not by a separate live probe (the gate runs in CI, not in production).
