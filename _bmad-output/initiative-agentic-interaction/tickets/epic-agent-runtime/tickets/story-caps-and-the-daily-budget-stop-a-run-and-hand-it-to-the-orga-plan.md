---
title: 'Caps and the daily budget stop a run and hand it to the organizers'
type: 'feature'
ticket: '8'
created: '2026-11-22'
status: 'built'
baseline_revision: '89f47087edfefe5b63cd292c578355bc8ed0d5a4'
route: 'full'
route_source: 'auto'
risk: 'medium'
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

**Problem:** A run is bounded only by `agent.loop.max-steps` (story 2): nothing stops it on model tokens, on `reply` mails or on a daily token budget (R9, Operational Envelope "Limits"), the gate's caps hook (story 7) is empty, and the gate's two owner values (2 approvals, 1 non-organizer recipient) are code constants (deferred by story 7).

**Approach:** One Spring configuration block `agent.limits` holds the four caps (run steps 25, run tokens 200k, run replies 2, daily tokens 2M) and the two gate values (binding approvals 2, non-organizer recipients 1). The loop checks steps, run tokens and the daily budget before every model call; the reply cap is a `GateCaps` bean. Any reached limit ends the run HANDED_TO_ORGANIZERS with reason `CAP_REACHED` and the limit in `end_detail`, logged without personal data.

## Boundaries & Constraints

**Always:** Token counts come only from `agent_model_calls` (input + output + cache-creation + cache-read, all four, so caching does not change what counts); the day is Europe/Zurich, as the gate's. A limit reached ends the run with no further model call and no further tool of that answer; a status only, no mail. App log: run id, limit name, used and configured value; nothing else (#1202). Current values stay the defaults. Integration tests on Testcontainers with `ScriptedLlm` and `CumsAtTheWire`, no own-code mocks.

**Never:** Alarms or metrics (story 10), read API changes (story 9), mail to organizers (epic-speaker-mail-agent), editing V1-V5, changes outside agent-service except docs, push, PR.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Step cap | model keeps calling a read tool | after `run-steps` model calls: HANDED_TO_ORGANIZERS, CAP_REACHED, `STEPS` | none |
| Run token cap | each call 15 tokens, model keeps calling tools | stops before the call after the run's tokens reach `run-tokens`; `RUN_TOKENS` | none |
| Reply cap | model sends replies to one requester | `run-replies` replies run; the next is REFUSED (CAP_REACHED, cap REPLIES), not executed, run ends `REPLIES` | none |
| Daily budget | other runs used tokens today; a call of yesterday | stops before the call once today's tokens reach `daily-tokens`; yesterday's not counted | none |
| Budget already spent | today's tokens >= `daily-tokens` at start | zero model calls, `DAILY_TOKENS` | none |
| Config moved | larger values | each stop point moves accordingly | none |
| Gate values | `binding-approvals`, `non-organizer-recipients` set | gate uses them; verdict inputs record them | invalid value fails startup |

</frozen-after-approval>

## Code Map

- `config/AgentProperties` -- new nested `Limits` (`runSteps` 25, `runTokens` 200000, `runReplies` 2, `dailyTokens` 2000000, `bindingApprovals` 2, `nonOrganizerRecipients` 1; validated); `Loop` keeps only `maxOutputTokens`. `application.yml` `agent.limits.*` (`AGENT_LIMITS_*`); no CDK sets `AGENT_LOOP_MAX_STEPS` (checked).
- `run/RunEndReason` -- `STEP_LIMIT` becomes `CAP_REACHED`; new `run/RunLimit` enum (STEPS, RUN_TOKENS, REPLIES, DAILY_TOKENS) is the `end_detail` value; `DAY_ZONE` moves from `GateLedger` here.
- `db/agent-migration/V6__agent_limits.sql` -- STEP_LIMIT rows to CAP_REACHED/STEPS, end_reason constraint swaps STEP_LIMIT for CAP_REACHED, CAP_REACHED requires a `RunLimit` detail, index `agent_model_calls (started_at)`.
- `loop/AgentLoop` -- limit checks at the top of the while (where the step check is); `runTools` reports a CAP_REACHED verdict and stops the answer's remaining tools; new `loop/TokenLedger` (JdbcTemplate: run sum, day sum).
- `gate/GateCaps` returns `Optional<RunLimit>`; `gate/ReplyCap` (`@Component`): REPLY effective class and the run's RUN reply verdicts >= `runReplies`. `Verdict.cap` typed. `GateLedger` passes `GateRules.Thresholds` from properties; inputs record `requiredApprovals`, `maxRecipients`.
- `gate/GateRules` -- `decide(facts, thresholds)`, constants removed; `GateRulesTest` (13 calls) updated.
- Tests: `loop/RunLimitsScenarios` (abstract) + two subclasses with different `agent.limits`; existing `agent.loop.max-steps=` in 5 test classes becomes `agent.limits.run-steps=`; `AgentLoopIntegrationTest` step test asserts CAP_REACHED/STEPS; `GateFixtureTool` public; `FlywayBaselineIntegrationTest` V6; `AgentPropertiesTest`.
- Docs: module-map agent-service bullet; 06-backend-architecture loop and gate paragraphs.

## Tasks & Acceptance

**Execution:**
- [x] `config/*`, `application.yml` -- limits block.
- [x] `run/*`, `V6` -- end reason and limit.
- [x] `loop/*` -- checks before each model call, cap stop after a refused reply.
- [x] `gate/*` -- reply cap, thresholds from configuration.
- [x] tests per matrix row; existing tests moved to the new keys.
- [x] docs; deferred-work.

**Acceptance Criteria:**
- Given each limit, when a scripted run reaches it, then the run row is HANDED_TO_ORGANIZERS, `end_reason` CAP_REACHED, `end_detail` the limit, and the app log names the limit and no personal data.
- Given larger `agent.limits` values, when the same scripts run, then the stop points move.
- Given `:services:agent-service:test`, when it runs, then every test passes.

## Implementation Notes

- Gates (unattended, owner go for the epic): one goal; plan about 1,700 tokens, kept whole (caps, config and gate values are one configuration story). No open questions; checkpoint approved without a human per the owner's go. Implemented directly in this session (rack rule: one heavy build at a time), as in stories 2-7.
- 2026-11-22 (build): `GateLedger.inputs` became an instance method (reads the thresholds field) to stay within checkstyle's 7 parameters; `runTools` returns a `ToolRound(results, capReached)` for the same reason. `GateLedger.DAY_ZONE` now aliases `RunLimit.DAY_ZONE`, so the gate's day and the budget's day cannot drift. The new test `should_nameTheLimit_when_V6Applied` caught a real hole: `end_detail IN (...)` lets NULL through a CHECK; V6 now requires `end_detail IS NOT NULL`. `GateFixtureTool` (test) is public so the loop-package limit tests can use the reply fixture. `GateReason.RECIPIENTS_ABOVE_ONE` keeps its name (javadoc says the limit is configurable, one by default). First full verification: 193 tests, 0 failures, 0 errors, 0 skipped (JUnit XML; 176 before + 17); checkstyle main and test clean; `make check-test-ratchet` exit 0, no RAISED or STALE line.

## Plan Change Log

## Review Triage Log

Pass 1 (quick lens): high 0, medium 0, low 4, false 0, maybe-false 0.

| # | Finding | Verdict | Route | Evidence / action |
|---|---|---|---|---|
| 1 | Reply-cap log line lacks the used value the plan requires | low | patch | Real: only REPLIES bypassed `logged()`. Now goes through `logged(run, REPLIES, configured)` (used equals the cap when the gate refuses); tests assert the full `(used N, configured N)` text for REPLIES and STEPS |
| 2 | Story-7 deferred entry on gate constants still open though settled | low | patch | Entries are append-only; appended a closure entry in deferred-work.md |
| 3 | Inputs key `maxNonOrganizerRecipients` differs from the Code Map's `maxRecipients` | low | reject | The fix would edit the plan; code, docs and test agree on `maxNonOrganizerRecipients` (the clearer name), recorded here |
| 4 | Daily-budget test can flake across midnight Europe/Zurich (system clock) | low | defer | Window of a few seconds per day, same pattern as story 7's day tests; a fixed test clock is more than a direct fix. Appended to deferred-work.md |

Verification after patches (2026-11-22): `:services:agent-service:test` + `checkstyleMain` + `checkstyleTest` BUILD SUCCESSFUL, 193 tests, 0 failures, 0 errors, 0 skipped (JUnit XML); `make check-test-ratchet` exit 0, no RAISED or STALE line.

## Design Notes

Architecture note: modules touched — agent-service only (`config`, `run`, `loop`, `gate`) · new dependencies — none · new endpoints — none · reused — `GateCaps` hook, `ScriptedLlm`, `CumsAtTheWire`, `GateFixtureTool`, gate's Europe/Zurich day · deviation — none.

Decisions (unattended, recorded):
- **Before, not after:** limits are checked before every model call against tokens already recorded; the call that crosses a cap completes (its answer's tools run, gated), and no further call follows. A run can overshoot by at most one call (its input plus `max-output-tokens`); the daily budget additionally by calls in flight in concurrent runs. Projecting the next call's size was rejected: not deterministic, and it stops runs early on a guess.
- **One reason, the limit in the detail:** `STEP_LIMIT` folds into `CAP_REACHED` + `end_detail` `STEPS`, so all four limits read alike (story 10's alarm on budget breaches filters `DAILY_TOKENS`).
- **Reply cap:** `run-replies` replies run; the next reply call reaches the cap (as with steps: the 26th step is refused). Counted are the run's RUN verdicts of effective class `reply`.
- **Same configuration:** gate values live in `agent.limits` beside the caps (`binding-approvals` >= 1: 0 would let binding run with no precedent; `non-organizer-recipients` >= 0).
- Epic 17 rule: archunit store 0 at start and end; the step limit stops being a special case.

## Verification

**Commands:**
- `set -o pipefail; ./gradlew --max-workers=4 :services:agent-service:test :services:agent-service:checkstyleMain :services:agent-service:checkstyleTest 2>&1 | tee ~/.cache/batbern-test-logs/agent-service-test-1-8.log` -- BUILD SUCCESSFUL; then `./gradlew --stop`
- `make check-test-ratchet` -- no RAISED or STALE line
