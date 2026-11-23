---
title: 'Alarms watch run failures, budget breaches and refused mints'
type: 'feature'
ticket: '10'
created: '2026-11-22'
status: 'built'
baseline_revision: '8e6b6821ea98fbc5e3f641e7d4e25352a1fc6a96'
route: 'full'
route_source: 'auto'
risk: 'low'
review: 'quick'
review_source: 'pinned'
lenses_ran: ['quick']
review_loop_iteration: 0
context:
  - '{project-root}/docs/architecture/module-map.md'
  - '{project-root}/_bmad-output/initiative-agentic-interaction/tickets/epic-agent-runtime/epic-agent-runtime.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** agent-service has only the generic ECS alarms: a run ending FAILED, a run stopped by a cap or by the daily budget, and a refused CUMS grant or mint are visible only in its tables and log, and nobody is told (R10, Operational Envelope "Observability"). The #1202 rule is proven by one log test with a hand-made tool, not with a speaker read through the real read tool.

**Approach:** One place in agent-service (`run/RunAlarms`) writes one marker line per run end that an alarm watches: `AGENT_RUN_FAILED`, `AGENT_RUN_CAP_REACHED` (every cap) plus `AGENT_DAILY_BUDGET_BREACHED` (the daily budget), `AGENT_CREDENTIALS_REFUSED` (grant or mint). A CDK construct `AgentRunAlarms` puts log metric filters on agent-service's log group (namespace `BATbern/AgentRuns`) and three alarms on the shared alarm topic, the mechanism of `EcsServiceAlarms` alarms 4 and 5 (no service exports Micrometer).

## Boundaries & Constraints

**Always:** Marker lines carry the marker, run id, end reason and end detail (a `RunLimit` name or a CUMS code) only (#1202). Markers are Java constants; the CDK test reads them from the Java source, so a rename fails the build. No marker contains another (literal filters). Alarms carry alarm and OK actions on the shared topic (#956 auto-close) and `-Agent-` in their name (the issue Lambda maps it to the agent log group). Every alarm name appears in `docs/operations/runbooks.md` (`incident-response-docs.test.ts`).

**Never:** Micrometer/EMF export, mail to organizers, a change to how runs end, editing V1-V6, push, PR.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Fault | run ends INTERNAL_ERROR (RuntimeException or Error) | one `AGENT_RUN_FAILED` line | Error still rethrown |
| Per-run cap | CAP_REACHED, STEPS / RUN_TOKENS / REPLIES | `AGENT_RUN_CAP_REACHED` only | none |
| Daily budget | CAP_REACHED, DAILY_TOKENS | `AGENT_RUN_CAP_REACHED` and `AGENT_DAILY_BUDGET_BREACHED` | none |
| Refused | GRANT_REFUSED or TOKEN_REFUSED | `AGENT_CREDENTIALS_REFUSED` with the CUMS code | none |
| Not alarmed | COMPLETED, MODEL_UNAVAILABLE, MODEL_MAX_TOKENS, MODEL_STOPPED | no marker | none |
| Speaker data | run reads a speaker (name, e-mail, postal address) and ends by each marker path | markers present, none of the speaker's data in the log | none |

</frozen-after-approval>

## Code Map

- `services/agent-service/src/main/java/ch/batbern/agent/loop/AgentLoop.java` -- `run()` ends runs at two places (normal end after `decisionLog.endRun(runId, reason, detail)` line ~131; the `catch (Error e)` path). Call `RunAlarms.record(runId, reason, detail)` at both. Do not touch the existing log lines (tests assert them).
- New `.../run/RunAlarms.java` -- `public static final String` markers; `markers(reason, detail)` (pure, unit-testable) and `record(...)` logging `"{marker} Agent run {} ended {} ({})"` at WARN (ERROR for FAILED).
- `.../run/RunEndReason.java`, `RunLimit.java` -- read only.
- Tests: new `run/RunAlarmsTest` (every `RunEndReason` × detail → markers); new `loop/RunAlarmLogIntegrationTest` with the shipped `listSessionSpeakers` tool, gateway at the wire via `CumsAtTheWire.bindTo(restTemplate, SESSIONS)` (pattern of `tools/read/ReadToolsIntegrationTest`: `respondWith(OK, body)` with the spec example's speaker replaced by `ProductionShapedPeople` data), plus an inbound mail carrying the speaker's name and address; `OutputCaptureExtension` like `AgentLoopLogIntegrationTest`.
- New `infrastructure/lib/constructs/agent-run-alarms.ts` -- exported marker and namespace constants; `logs.MetricFilter` (`FilterPattern.literal('"MARKER"')`, value 1, default 0) ×4; alarms `batbern-${env}-Agent-Run-Failures` (5 min, >0), `-Agent-Refused-Mints` (5 min, >0), `-Agent-Daily-Budget-Breaches` (1 h × 24, 1 to alarm, as Scheduled-Job-Failures: the budget is per day). Metric `RunCapsReached-Agent` has no alarm.
- `infrastructure/lib/stacks/agent-service-stack.ts` -- instantiate beside `EcsServiceAlarms` with `domainService.logGroup`.
- New `infrastructure/test/unit/agent-run-alarms.test.ts` -- builds `AgentServiceStack` like `domain-event-listener-alarm.test.ts`; reads markers from `RunAlarms.java`; asserts filter ↔ alarm namespace/metric equality and topic actions.
- Loop checked: `lambda/github-issues-integration/index.ts` `logGroupForAlarm` already maps `-Agent-`; no alarm list in workflows. Docs: `runbooks.md` (new section), `08-operations-security.md` alarm table, `06-backend-architecture.md` agent-service paragraph, `module-map.md` agent-service bullet if it lists packages.

## Tasks & Acceptance

**Execution:**
- [x] `run/RunAlarms.java`, `AgentLoop.java` -- markers at run end.
- [x] `RunAlarmsTest`, `RunAlarmLogIntegrationTest` -- matrix rows.
- [x] `agent-run-alarms.ts`, `agent-service-stack.ts`, `agent-run-alarms.test.ts` -- filters and alarms.
- [x] docs (runbooks, 08, 06); deferred-work.

**Acceptance Criteria:**
- Given the CDK test, when it synthesizes `AgentServiceStack`, then three alarms exist on the shared topic (alarm and OK actions) whose namespace and metric name equal those of a metric filter on `/aws/ecs/BATbern-staging/agent` matching the marker read from `RunAlarms.java`.
- Given `:services:agent-service:test`, when it runs, then every test passes, and the log test finds each marker and none of the speaker's name, username, e-mail or postal address.

## Implementation Notes

- Gates (unattended, owner go for the epic): one goal; plan about 1,700 tokens, kept whole (code markers and the alarms that match them are one change). No open questions; checkpoint approved without a human. Implemented directly in this session (rack rule: one heavy build at a time), as in stories 2-9.
- 2026-11-22 (build): `RunAlarms` is a static utility (no bean): `AgentLoop` calls it at both run ends (normal end, and the `Error` path before rethrow). The FAILED marker logs at ERROR, the others at WARN. The log test uses the shipped `listSessionSpeakers` (no tools installed, so `TestToolRegistry` offers the shipped registry) against the sessions spec at the wire; the speaker's e-mail and postal address sit in the inbound mail, the prompt, the model text and the API's `bio` (not projected to the model); the refused-mint case proves the data is in the run through `agent_inbound_messages`. Mutation check: renaming the Java marker made 2 of 8 CDK tests fail (restored). Checkstyle wanted the `@TestPropertySource` array on its own lines. The issue Lambda's `getLabels` gives the agent alarms `severity:low` (its `budget` check is lowercase, for the cost budget); left as is.
- Verification: `:services:agent-service:test` + checkstyle main/test BUILD SUCCESSFUL, 219 tests, 0 failures, 0 errors, 0 skipped (JUnit XML; `RunAlarmLogIntegrationTest` 4, `RunAlarmsTest` 13); CDK jest (agent-run-alarms, agent-service-stack, incident-response-docs, metric-filter-validity, domain-event-listener-alarm, scheduled-job-failure-alarm) 6 suites, 48 tests passed; `make check-test-ratchet` exit 0, no RAISED or STALE line. Archunit store not touched (0 at start and end).

## Plan Change Log

## Review Triage Log

Pass 1 (quick lens): high 0, medium 2, low 4, false 0, maybe-false 0.

| # | Finding | Verdict | Route | Evidence / action |
|---|---|---|---|---|
| 1 | The `Error` branch's marker is untested; no test checks one line, not several | medium | patch | Real: only `AgentLoopIntegrationTest` drives the Error path, without output capture. Added `should_logFailureMarker_when_errorEndsARunAboutASpeaker`; `assertMarkers` now requires exactly one line per marker |
| 2 | Marker written after `decisionLog.endRun`: a failing DB write silences the failure alarm | medium | patch | Real: `endRun` is its own REQUIRES_NEW write and throws on a DB fault, so `record` never ran. `RunAlarms.record` now runs before `endRun` on both paths |
| 3 | `RunAlarms` logs the raw detail, not the `END_DETAIL`-checked code | low | reject | Every producer today is a `RunLimit` name, a constant or `CumsAgentClient.codeOf` (cleaned), as the reviewer confirmed; the fix adds a guard for a case no code reaches |
| 4 | CDK test does not check `DatapointsToAlarm: 1` on the budget alarm | low | patch | Real: deleting it would make the alarm 24 of 24. The test now checks 1 for the budget alarm and absence for the 5-minute alarms |
| 5 | Runbook says the console API shows the failing step's exception class | low | patch | Real: the API stores only a model call's error. Step 2 now points to the `failed in step` line before the marker |
| 6 | Log test: the refused path does not read the speaker via the tool; per-run cap not run in the log test | low | patch (cap) / reject (refused after a read) | Added the STEPS cap case (`AGENT_RUN_CAP_REACHED` without the budget marker). A refusal after a read needs a write tool beside the shipped read tool; the speaker's data is in that run through the inbound mail and prompt, and the marker line is the same code path, so not added |

Verification after patches (2026-11-22): `:services:agent-service:test` + checkstyle BUILD SUCCESSFUL, 221 tests, 0 failures, 0 errors, 0 skipped (`RunAlarmLogIntegrationTest` 6, `RunAlarmsTest` 13); CDK jest 6 suites, 48 tests passed; `make check-test-ratchet` exit 0, no RAISED or STALE line.

## Design Notes

Architecture note: modules touched — agent-service `run`, `loop`; infrastructure agent stack + new construct · new dependencies — none · new endpoints — none · reused — log-metric-filter alarm pattern of `EcsServiceAlarms` (alarms 4, 5), `CumsAtTheWire`, `ScriptedLlm`, `ProductionShapedPeople` · deviation — none.

Decisions (unattended, recorded):
- **Log metric filters, not Micrometer/EMF:** no service exports Micrometer to CloudWatch; the existing app alarms are literal-marker filters, and a filter on a Java constant read by the CDK test is the proven path that fires.
- **Budget breaches = the daily budget only:** a per-run cap is the cap working; the run is handed over and visible in the console. Its metric `RunCapsReached-Agent` is emitted (distinguishable from `DailyBudgetBreaches-Agent`) without an alarm. The daily budget stops every run until midnight, which an operator must know.
- **Model unreachable: no own alarm, no marker.** Handed-over runs are expected (key `none` until the owner installs it, Anthropic outages are external and transient), and the agent is dark. Revisit when epic-speaker-mail-agent hands runs to organizers by mail; recorded in deferred-work.
- **Refused grant and refused mint share one alarm:** both mean the agent cannot act (missing caller secret, missing signing key, revoked principal); the CUMS code in the line tells them apart.
- Epic 17 rule: archunit store 0 at start and end; run-end observability sits in one class instead of scattered log lines.

## Verification

**Commands:**
- `set -o pipefail; ./gradlew --max-workers=4 :services:agent-service:test :services:agent-service:checkstyleMain :services:agent-service:checkstyleTest 2>&1 | tee ~/.cache/batbern-test-logs/agent-service-test-1-10.log` -- BUILD SUCCESSFUL; then `./gradlew --stop`
- `cd infrastructure && set -o pipefail; npx jest --maxWorkers=4 test/unit/agent-run-alarms.test.ts test/unit/agent-service-stack.test.ts test/unit/incident-response-docs.test.ts test/unit/metric-filter-validity.test.ts test/unit/domain-event-listener-alarm.test.ts 2>&1 | tee ~/.cache/batbern-test-logs/cdk-1-10.log` -- all pass
- `make check-test-ratchet` -- no RAISED or STALE line
