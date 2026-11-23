---
title: 'The decision log and the loop run a scripted model end to end'
type: 'feature'
ticket: '2'
created: '2026-11-22'
status: 'built'
baseline_revision: '3d52d90da04564cacce0cdcb6c7b924995641375'
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

**Problem:** agent-service (story 1) has only an empty `agent_runs` table; there is no loop, no model port and no decision log, so nothing the agent does could be traced (R7) and the Anthropic adapter (story 3) has no port to implement (R3).

**Approach:** Add the `agent_*` decision-log tables (V2) and an `AgentLoop` that calls the model only through `LlmPort`, runs the tools the model chooses, records every model call (prompt, tool choice, tokens), step and tool call as it happens, and ends each run DONE, FAILED or HANDED_TO_ORGANIZERS. Tests drive it with a scripted `LlmPort` (the model is a process boundary).

## Boundaries & Constraints

**Always:** `LlmPort` is the only way to the model and `AgentLoop` its only caller (AD-14). Its types map 1:1 onto anthropic-java 3.12.0 Messages tool use: system text, messages of role user/assistant holding text, tool_use (id, name, JSON input) and tool_result (tool_use id, content, is_error) blocks, tools with a JSON input schema, stop reason, usage (input, output, cache-creation, cache-read tokens), model id. Each log row is committed when it happens (a crashed run still leaves its trace). Application logs carry ids, counts, tool names and outcomes only: never prompt text, tool input/output or exception messages (#1202). The loop refuses to start while `agent.enabled` is false. Integration tests on Testcontainers PostgreSQL, no own-code mocks.

**Never:** Anthropic adapter, key secret, model-id config (story 3). CUMS grant/mint (story 4). Tool registry, operation class table, real intent tools (story 5). Precedents, gate logic (stories 6, 7). Token caps, reply cap, daily budget (story 8). Read API changes (story 9). Metrics, alarms (story 10). Mail of any kind. Push, PR.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Three-step run | script: tool_use A; tool_use B and C; end_turn text | run DONE; 3 steps, 3 model calls with tokens and tool choice, 3 tool calls with input/output | none |
| Model unreachable | port throws `LlmUnavailableException` | run HANDED_TO_ORGANIZERS, reason MODEL_UNAVAILABLE, the failed model call row carries the error | no retry |
| Unknown tool | model asks for a tool the run does not offer | tool call UNKNOWN_TOOL, error tool_result back to the model, loop continues | none |
| Tool throws | tool raises | tool call FAILED with error, error tool_result back to the model | exception class only in app log |
| Output cut off | stop reason MAX_TOKENS | HANDED_TO_ORGANIZERS, reason MODEL_MAX_TOKENS | none |
| Model refuses / other stop | stop reason REFUSAL or unknown | HANDED_TO_ORGANIZERS, reason MODEL_STOPPED | none |
| Step ceiling | model keeps calling tools past `agent.loop.max-steps` | HANDED_TO_ORGANIZERS, reason STEP_LIMIT | none |
| Bug in the loop | unexpected runtime exception | run FAILED, reason INTERNAL_ERROR | logged by class |
| Switch off | `agent.enabled=false` | `AgentSwitchedOffException`, no run row | none |

</frozen-after-approval>

## Code Map

- `services/agent-service/src/main/java/ch/batbern/agent/run/{AgentRunEntity,AgentRunStatus,AgentRunRepository,AgentRunQueryService}.java` -- story 1; extend the entity (trigger, end reason), keep `started`/`end` factory style; status stays upper-case `EnumType.STRING` (agent-service convention since V1, check constraint upper-case).
- `.../config/AgentProperties.java` -- record `agent.*`; add nested `loop` (`maxSteps` 25, `maxOutputTokens` 4096) with defaults.
- `src/main/resources/db/agent-migration/V1__agent_runs.sql` -- frozen (applied in prod? not yet deployed, but treat as frozen); new `V2__agent_decision_log.sql`.
- `src/main/resources/application.yml` -- add `agent.loop.*`.
- `src/test/resources/application-test.yml` -- shadows shared test yml; add nothing unless needed.
- Tests to model on: `run/AgentRunControllerIntegrationTest` (extends `AbstractIntegrationTest`, cleans in `@BeforeEach/@AfterEach`), `security/LogEncoderMaskingTest`, shared `ProductionShapedPeople` for PII shapes.
- jsonb columns: `@JdbcTypeCode(SqlTypes.JSON)` as in EMS `MailMessage`; Jackson 2 (`com.fasterxml`) via `spring-boot-jackson2` (root build.gradle).
- CUMS owns `agent_settings`, `agent_grants`, `agent_allowlist`: new table names must not collide (checked: none of the new names exist).
- Docs (doc-drift mapping `services/agent-service/`): `docs/architecture/module-map.md` (agent-service bullet), `docs/architecture/06-backend-architecture.md` (agent-service paragraph).

## Tasks & Acceptance

**Execution:**
- [x] `services/agent-service/src/main/resources/db/agent-migration/V2__agent_decision_log.sql` -- `agent_runs` + `trigger`, `end_reason`; new `agent_inbound_messages`, `agent_steps`, `agent_model_calls`, `agent_tool_calls`, `agent_verdicts`, `agent_approvals`, `agent_undos` with FKs to the run (and step/tool call), check constraints on enums, indexes on `run_id` -- R7 schema.
- [x] `.../agent/loop/` -- `LlmPort`, `LlmRequest`, `LlmResponse`, `LlmMessage`, `LlmContent` (sealed: `Text`, `ToolUse`, `ToolResult`), `LlmToolSpec`, `LlmUsage`, `LlmStopReason`, `LlmUnavailableException`, `UnavailableLlm` (default `@Component`: always throws; story 3 replaces it), `AgentLoop`, `AgentRunRequest`, `AgentRunResult`, `RunEndReason`, `AgentSwitchedOffException` -- the loop.
- [x] `.../agent/tools/AgentTool.java` (+ `ToolOutcome`) -- minimal interface the loop runs (name, description, input schema, execute); story 5 adds classes and the registry.
- [x] `.../agent/log/` -- entities + repositories for inbound message, step, model call, tool call; `DecisionLog` service, one `@Transactional` method per write -- the log.
- [x] `.../config/AgentProperties.java`, `ClockConfig.java`, `application.yml` -- loop settings, `Clock` bean.
- [x] `src/test/java/.../loop/ScriptedLlm.java` -- `@Primary @Component` scripted `LlmPort` (queue of responses or exceptions, records requests, `reset()`); test tools in `loop/TestTools.java`.
- [x] `src/test/java/.../loop/AgentLoopIntegrationTest.java` -- every matrix row; the three-step run asserts the rows through `JdbcTemplate` (not the entities) incl. token counts, tool choice, prompt jsonb, step numbers, tool input/output; positive control that the run row exists before asserting no tool call where relevant.
- [x] `src/test/java/.../loop/AgentLoopLogIntegrationTest.java` -- a run whose prompt and tool output hold a `ProductionShapedPeople` name and address: captured output has neither, but has the run id (positive control).
- [x] `src/test/java/.../loop/AgentSwitchOffIntegrationTest.java` -- default `agent.enabled=false` context refuses, no row.
- [x] `src/test/java/.../loop/UnavailableLlmTest.java` -- default adapter throws `LlmUnavailableException`.
- [x] Docs -- module-map and 06-backend-architecture name the decision-log tables, `LlmPort`/`AgentLoop` and the end statuses.

**Acceptance Criteria:**
- Given the scripted three-step run, when the IT reads the `agent_*` tables, then it finds the run DONE, each model call with its input/output token counts and chosen tools, each step and each tool call.
- Given `./gradlew :services:agent-service:test`, when it runs, then every test passes, including story 1's Flyway baseline test with V2 applied.
- Given a run ending in a model failure, when the run row is read, then status is HANDED_TO_ORGANIZERS with its reason and no mail is sent (agent-service has no mail client).

## Implementation Notes

- Gates (unattended build, owner go for the whole epic): one goal (loop + its log; neither ships alone); plan ~2,000 tokens, kept whole. No open questions. Checkpoint approved without a human per the owner's go.
- Rack rules: one heavy build at a time; Gradle `--max-workers=4`, then `./gradlew --stop`; output via `set -o pipefail; ... 2>&1 | tee ~/.cache/batbern-test-logs/<name>.log`; never `make test`; never `git add -A`; never bare `git stash`.

## Plan Change Log

- 2026-11-22 (build): `RunEndReason` and the new `RunTrigger` live in `run/`, not `loop/`: `AgentRunEntity` stores both, and each reason fixes its end status (`COMPLETED`→DONE, `INTERNAL_ERROR`→FAILED, the rest→HANDED_TO_ORGANIZERS), so status and reason cannot disagree. `AgentRunEntity.started(id, trigger, at)` / `end(reason, at)`. Trigger values follow the AD-3 grant sources: INBOUND_MAIL, ORGANIZER, SCHEDULED, LINKED_RUN.
- 2026-11-22 (build): `InboundMessage` / `InboundChannel` (MAIL, CHAT, CARD_ACTION) in `log/`; the loop records `AgentRunRequest.inbound` when given, so the inbound entity has a writer.
- 2026-11-22 (build): model and tool calls are written before they run (STARTED) and completed after; tool call statuses SUCCEEDED, ERRORED (the tool's own `ToolOutcome.error`), FAILED (threw), UNKNOWN_TOOL. DecisionLog writes are `REQUIRES_NEW`. jsonb columns hold JSON text in the Messages API shape (`LlmJson`). A `tool_use` stop without a tool_use block ends MODEL_STOPPED. An unknown tool's name is not written to the application log (it is model text).
- 2026-11-22 (build): story 1's `FlywayBaselineIntegrationTest` now expects V2 and the eight tables; `AgentRunControllerIntegrationTest` cleans with `TRUNCATE agent_runs CASCADE`.
- 2026-11-22 (review fixes): an `Error` ends the run FAILED/INTERNAL_ERROR and is rethrown; a port fault other than `LlmUnavailableException` marks the model call FAILED; U+0000 is stored as U+FFFD in every decision-log string (`LlmJson.storable`, jsonb and text); `tool_use_id`/`tool_name` are TEXT; the four token columns are BIGINT, no narrowing.

## Review Triage Log

Pass 1 (quick lens): high 0, medium 2, low 3, false 0, maybe-false 0.

| # | Finding | Verdict | Route | Evidence / action |
|---|---|---|---|---|
| 1 | `AgentLoop.run` caught only RuntimeException; an Error left the run RUNNING | medium | patch | Catch path was the only route to `endRun`. Now Error ends FAILED/INTERNAL_ERROR then rethrows; a non-unavailable port exception marks the model call FAILED. Tests added |
| 2 | NUL in model/tool/inbound text: PostgreSQL rejects it in jsonb/text, run fails, Hibernate may log the JSON fragment (#1202) | medium | patch | `LlmJson.storable` replaces U+0000 with U+FFFD in every stored string; test with NUL in output, input and model text, run DONE |
| 3 | Unknown tool name over 128 chars fails the insert, run FAILED instead of UNKNOWN_TOOL | low | patch | `tool_name`, `tool_use_id` now TEXT (V2 unapplied anywhere); 300-char test |
| 4 | INTEGER token columns + `Math.toIntExact` narrow the port's long usage | low | patch | Columns BIGINT, entity Long |
| 5 | `PAUSE_TURN` treated as a final stop (MODEL_STOPPED) | low | defer | Only server tools produce pause_turn; none exist in story 2. Recorded for story 3's adapter in deferred-work.md |

Verification after patches: `:services:agent-service:test` 37 tests, 0 failures (2026-11-22); `make check-test-ratchet` clean.

## Design Notes

Architecture note: modules touched — agent-service only (`loop`, `log`, `tools`, `run`, `config`) · new dependencies — none (no new library; anthropic-java arrives in story 3) · new endpoints — none (read API grows in story 9) · reused — `AbstractIntegrationTest`, `ProductionShapedPeople`, Jackson 2 `ObjectMapper`, `@JdbcTypeCode(SqlTypes.JSON)` · deviation — none from the map; tables stay in agent-service's own Flyway history.

Decisions (taken unattended, recorded here):
- Step = one model call plus the tool calls it asked for; `agent_model_calls.step_id` and `agent_tool_calls.step_id` are NOT NULL. The full prompt (system, messages, tools) is stored per model call as jsonb (honest log; ≤25 steps keeps the duplication small).
- Tool errors go back to the model as an `is_error` tool_result (standard tool use); the gate (story 7) decides what may be retried. Unknown tools likewise.
- Model unreachable → HANDED_TO_ORGANIZERS, not FAILED (Operational Envelope: model down goes to the organizers); FAILED is kept for agent-service's own faults.
- A step ceiling (`agent.loop.max-steps`, 25) ships now so no loop is unbounded; story 8 adds the token, reply and daily caps and owns the configuration story.
- `agent_verdicts`, `agent_approvals`, `agent_undos` get tables only (columns from AD-7/AD-8/R9: verdict class, outcome, confidence, inputs, cited precedents; approval state and decider; undo operation and outcome); their entities arrive with their writers (stories 7, epic-oversight-console), so no unused code ships.
- `ScriptedLlm` lives in test sources; story 3 decides whether local dev needs a scripted adapter in main.
- The loop takes the tools per run (`AgentRunRequest.tools`), no registry yet.

Epic 17 rule: violations/archunit store size 0 at start and end (new service); test-ratchet baseline unchanged.

## Verification

**Commands:**
- `set -o pipefail; ./gradlew --max-workers=4 :services:agent-service:test 2>&1 | tee ~/.cache/batbern-test-logs/agent-service-test-1-2.log` -- BUILD SUCCESSFUL, then `./gradlew --stop`
- `make check-test-ratchet` -- no RAISED line
