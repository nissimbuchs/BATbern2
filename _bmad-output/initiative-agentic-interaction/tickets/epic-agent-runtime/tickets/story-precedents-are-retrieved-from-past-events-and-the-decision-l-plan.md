---
title: 'Precedents are retrieved from past events and the decision log'
type: 'feature'
ticket: '6'
created: '2026-11-22'
status: 'built'
baseline_revision: '4be3bb98114ab71c2c70953651242a2b8d6b4858'
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

**Problem:** The gate (story 7) must cite precedents (R6, AD-8), but nothing retrieves them: the decision-log tables for approvals and undos have no reader, runs do not record their principal kind, and past-event history is only reachable by the model.

**Approach:** A `precedent/` package: `PrecedentRetrieval.retrieve(ProposedCall, PublicApi)` returns `Precedents` (approvals, denials, undos, past events), each with a citeable source id. Decision-log precedents come from `agent_approvals`/`agent_undos`; history comes through story 5's `findPastEvents` tool with the run's token. One class, `PrecedentMatchRule`, holds the matching rule (same tool, same principal kind). Runs store the principal kind of their CUMS grant.

## Boundaries & Constraints

**Always:** The rule lives only in `PrecedentMatchRule` (SQL narrows by the rule's candidate tool name; the rule's `matches` decides). The call's principal kind is read from its run row, never taken from the caller. Source ids: `agent_approvals:<uuid>`, `agent_undos:<uuid>`, `event:<eventCode>`. Approval state APPROVED → approval, DENIED → denial; PENDING and EXPIRED are no precedent; undos count whatever their outcome (the organizer's intent to undo is the signal), outcome carried. History: only when the proposed tool declares a past-event query (`AgentTool.pastEventQuery(input)`, default none); read via `FindPastEventsTool` with the API limited to its operations; an API error gives an empty history with status UNAVAILABLE, a mint refusal propagates. No usernames or decider in the precedent objects (ids only). Tests: ITs on Testcontainers PostgreSQL, CUMS and gateway at the wire through `CumsAtTheWire`/`ContractServer`; the model not involved.

**Never:** Gate logic, confidence, thresholds, writing verdicts (story 7). Writers of approvals/undos (epic-oversight-console). Principal-kind filtering of offered tools (deferred, epic-speaker-mail-agent). Vector store, embeddings, fine-tuning (AD-8). Changes outside agent-service except docs. Push, PR.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Full retrieval | ORGANIZER run proposes fixture tool T with a past-event query; log: 2 approved + 1 denied + 1 undo of T by ORGANIZER runs, plus decoys (T by REQUESTER, other tool, PENDING, EXPIRED); gateway has 2 past events | exactly 2 approvals, 1 denial, 1 undo, 2 past events, each with its source id; history READ | none |
| No query | tool without `pastEventQuery` | decision precedents only, no GET, history NOT_ASKED | none |
| API error | gateway answers 500 | decision precedents back, history empty, UNAVAILABLE | none |
| Mint refused | CUMS refuses the read's mint | `AgentCredentialsRefusedException` | propagates |
| Run without kind | run row has no principal kind | `IllegalStateException` | no query |
| Grant answer | CUMS answers principalKind | stored on `agent_runs.principal_kind`; handed-over grant → REQUESTER; unknown value → GRANT_REFUSED / INVALID_CUMS_RESPONSE | as story 4 |

</frozen-after-approval>

## Code Map

- `identity/RunGrant` -- add `PrincipalKind principalKind`; new `identity/PrincipalKind` (REQUESTER, ORGANIZER, ORGANIZER_DELEGATE = cums-internal `AgentGrant.principalKind`). `CumsAgentClient.grant` parses it (enum, else INVALID_CUMS_RESPONSE). `RunCredentials.obtain`: HandedOver → REQUESTER (CUMS `AgentGrantTrigger.EMS_HANDOFF`); expose `principalKind()`.
- `db/agent-migration/V4__agent_run_principal_kind.sql` -- `agent_runs.principal_kind VARCHAR(32)` nullable, check constraint; index `agent_tool_calls (tool_name)`. V1-V3 frozen.
- `run/AgentRunEntity.recordGrant(grantRunId, kind)`, `log/DecisionLog.recordGrant(runId, grantRunId, kind)`, `loop/AgentLoop` passes `credentials.principalKind()`.
- `tools/AgentTool` -- `default Optional<PastEventQuery> pastEventQuery(JsonNode input)`; `tools/read/PastEventQuery` record (year, searchText, limit) with `toInput()` matching `FindPastEventsTool`'s schema.
- New `precedent/`: `ProposedCall(runId, tool, input)`, `PrecedentKind` (APPROVAL, DENIAL, UNDO), `DecisionPrecedent`, `PastEventPrecedent`, `Precedents` (+ `HistoryStatus` NOT_ASKED/READ/UNAVAILABLE, `sourceIds()`), `PrecedentMatchRule`, `DecisionLogPrecedents` (JdbcTemplate reads, no entities: approvals/undos have no writer yet), `PrecedentRetrieval` (`@Service`).
- Reuse: `FindPastEventsTool` (constructed with the `Clock` bean), `PublicApi.limitedTo`, `ToolContext`. Parse the tool's JSON output `events[].eventCode/title/date`.
- Tests: `AgentCredentialsIntegrationTest` (principal_kind per source), `CumsAgentClient` unknown kind, `FlywayBaselineIntegrationTest` (V4), new `precedent/PrecedentRetrievalIntegrationTest`, `precedent/PrecedentMatchRuleTest`. Seed rows with `JdbcTemplate` (runs, steps, tool calls, verdicts, approvals, undos). Fixture tool in test sources, POST `/companies` (BINDING in `TestToolRegistry.TABLE`).
- Docs: module-map agent-service bullet; 06-backend-architecture new precedents paragraph.

## Tasks & Acceptance

**Execution:**
- [x] identity + V4 + run/log/loop -- principal kind recorded per run.
- [x] `tools/AgentTool`, `tools/read/PastEventQuery` -- tool-declared history query.
- [x] `precedent/*` -- retrieval, rule, decision-log reader.
- [x] tests per matrix row; adapt credentials and Flyway tests.
- [x] docs; deferred-work entry for the principal-kind partial.

**Acceptance Criteria:**
- Given the seeded log and gateway, when `retrieve` runs, then exactly the matching precedents come back, each naming its source row or event, and decoys do not.
- Given `:services:agent-service:test`, when it runs, then every test passes.

## Implementation Notes

- Gates (unattended, owner go for the epic): one goal; plan about 1,500 tokens. No open questions; checkpoint approved without a human per the owner's go. Implemented directly in this session (rack rule: one heavy build at a time; the session holds the investigation), as in stories 2-5.

- 2026-11-22 (build): `PrincipalKind` lives in `run/` (the entity stores it; `identity` already depends on `run`, the reverse would be a cycle). `PastEventQuery` lives in `tools/` (it is in `AgentTool`'s signature; `tools/read` depends on `tools`). V4 also adds `chk_agent_runs_principal_kind_with_grant` (a grant iff a kind), so story 4's V3 Flyway test inserts a kind with its grant. `DecisionLogPrecedents` is a `@Component`, not a `@Repository`: exception translation turned its `IllegalStateException` into `InvalidDataAccessApiUsageException` (caught by the matrix test on its first run). An off-contract grant answer (unknown or missing kind) is tested with a plain `MockRestServiceServer` (`CumsAgentClientGrantAnswerTest`), because `ContractServer` refuses to send a body that breaks the spec. First verification: 140 tests, 0 failures; checkstyle main and test clean; `make check-test-ratchet` and `make check-s2s-client-urls` exit 0, no RAISED or STALE line.

## Plan Change Log

- 2026-11-22 (review fixes): an unreachable gateway (a `RestClientException` out of `PublicApi`) gives history UNAVAILABLE like an HTTP error; a refused mint still propagates. A `pastEventQuery` that throws `IllegalArgumentException` (bounds of `PastEventQuery`, built from model input) asks for no past events, so the `AgentTool` promise is enforced in one place. `DecisionLogPrecedents.candidates(runId, toolName)` reads the kind, approvals and undos in one REPEATABLE READ transaction; the three reads are private.

## Review Triage Log

Pass 1 (quick lens): high 0, medium 2, low 3, false 0, maybe-false 0.

| # | Finding | Verdict | Route | Evidence / action |
|---|---|---|---|---|
| 1 | Story 5's deferred-work entry still says agent-service does not read principalKind | low | reject | The workflow forbids editing existing entries; the appended story 6 entry states the partial settlement |
| 2 | Unreachable gateway: `ResourceAccessException` escapes `retrieve`, the decision precedents are lost | medium | patch | `PublicApi.exchange` catches only `RestClientResponseException`. Now caught in `withHistory`, UNAVAILABLE; test `should_markHistoryUnavailable_when_gatewayUnreachable` |
| 3 | A tool's `pastEventQuery` can throw on model input (blank or long topic) | medium | patch | `PastEventQuery` ctor throws; the fixture shows it. Retrieval treats it as no query; test `should_notReadHistory_when_toolBuildsUnusableQuery` |
| 4 | An `api` already limited to the proposed tool drops `GET /events`, throwing `IllegalStateException` | low | reject | No caller yet; `@param api` says the run's API. Story 7 builds the call site and passes the loop's run-level `PublicApi` |
| 5 | Kind, approvals and undos read in three transactions, not one snapshot | low | patch | Class-level `@Transactional` gave each its own. One REPEATABLE READ method `candidates` |

Verification after patches (2026-11-22): `:services:agent-service:test` + `checkstyleMain` + `checkstyleTest` BUILD SUCCESSFUL, 142 tests, 0 failures, 0 errors, 0 skipped (JUnit XML); `make check-test-ratchet` exit 0, no RAISED or STALE line.

## Design Notes

Architecture note: modules touched — agent-service only (`precedent` new; `identity`, `run`, `log`, `loop`, `tools`) · new dependencies — none (history via the existing `GET /events` read) · new endpoints — none · reused — `FindPastEventsTool`, `PublicApi.limitedTo`, `CumsAtTheWire`, `ContractServer` · deviation — none; `precedent/` is the spine's structural seed.

Decisions (unattended):
- Principal kind from the grant answer (CUMS is authoritative), not re-derived from the trigger.
- History "same tool": the past events the proposed tool itself asks for; read with the run's token, so it is what that principal may see. Story 7 counts decision precedents; history is cited context.
- The current run's own decided rows count too (an approval earlier in the same run is a precedent).
- No cap on decision candidates: a cap could hide a denial the gate must see.
- Epic 17 rule: archunit store 0 at start and end (agent-service has none); the story removes the "agent-service does not read principalKind" gap from deferred-work; test-ratchet baseline unchanged.

## Verification

**Commands:**
- `set -o pipefail; ./gradlew --max-workers=4 :services:agent-service:test :services:agent-service:checkstyleMain :services:agent-service:checkstyleTest 2>&1 | tee ~/.cache/batbern-test-logs/agent-service-test-1-6.log` -- BUILD SUCCESSFUL; then `./gradlew --stop`
- `make check-test-ratchet` -- no RAISED or STALE line
