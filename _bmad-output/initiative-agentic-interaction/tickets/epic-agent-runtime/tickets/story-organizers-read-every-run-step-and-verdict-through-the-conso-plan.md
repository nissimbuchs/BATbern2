---
title: 'Organizers read every run, step and verdict through the console API'
type: 'feature'
ticket: '9'
created: '2026-11-22'
status: 'built'
baseline_revision: '986fd666f90b444f782f67718f9bdb336a2130ae'
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

**Problem:** The decision log (stories 2-8) records every run, model call, step, tool call, verdict and approval, but organizers can read only the run list (`GET /agent/runs`, id, status and times); nothing shows why a run ended, what the model was sent, what the tools did or which precedents a verdict cited (R8, AD-9).

**Approach:** Grow `agent-api.openapi.yml` spec-first with run detail and four paged sub-collections of a run (steps, model calls, tool calls, verdicts with resolved cited precedents and their approval) plus one model-call detail that carries the full prompt and answer; agent-service serves them read-only to Cognito organizer tokens, refusing other roles (403) and any token carrying `act`.

## Boundaries & Constraints

**Always:** Spec first; generated controller interface and DTOs (contract first, ADR-006). Paging per ADR-013 §6: 0-based `page`, `limit` 1-100 default 20, shared `PaginationMetadata`, deterministic order (chronological ascending inside a run, id as tie-breaker). Every 4xx is the shared `ErrorResponse`; unknown run or model call answers 404 `ERR_NOT_FOUND`. Every operation `@PreAuthorize("hasRole('ORGANIZER') and !carriesAct")` plus the filter chain rule for `GET /api/v1/agent/**`. Read-only transactions; no writes, no mail, no model call. App log carries no prompt, tool input/output or inbound text (#1202). Integration tests on Testcontainers, the production chain, real `JwtAuthenticationConverter`, no own-code mocks; every 2xx passes the response-conformance gate.

**Never:** UI, approve/deny/undo actions (epic-oversight-console), a cross-run approvals inbox (same epic), card tokens (epic-generated-cards), changes to V1-V6 or to what the loop writes, changes outside agent-service except the spec, generated frontend types, Bruno, docs; push, PR.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Scripted run read | organizer; run of 3 steps with a RUN, a PENDING_APPROVAL and a REFUSED verdict, seeded approvals and a past event as precedents | detail with end reason/detail, totals and inbound message; every step, model call (tokens), tool call (input, output, verdict id) and verdict (class, outcome, reason, confidence, inputs, cited precedents resolved, approval state) | none |
| Cap reached | run ended CAP_REACHED | `endReason` CAP_REACHED, `endDetail` the limit (e.g. STEPS) | none |
| Refused grant | run ended GRANT_REFUSED | `endDetail` the CUMS code | none |
| Full prompt | organizer, model-call detail | prompt and response JSON as stored | none |
| Paging | limit 1 over 3 steps | page metadata, step order 1,2,3 across pages | limit 101 → 400 |
| Unknown run / model call of another run | random uuid | 404 `ERR_NOT_FOUND` | shared ErrorResponse |
| Speaker / partner | Cognito token, role SPEAKER | 403 on every new operation | none |
| Agent token for an organizer | `iss batbern-agent` token | 401 on every new operation (decoder refuses the kind) | none |
| Token carrying `act` past the decoder | ORGANIZER role + `act` claim | 403 | none |
| Anonymous | no token | 401 on every new operation | none |

</frozen-after-approval>

## Code Map

- `docs/api/agent-api.openapi.yml` -- existing `listAgentRuns`; add `getAgentRun` (`/agent/runs/{runId}`), `listAgentRunSteps`, `listAgentRunModelCalls`, `getAgentRunModelCall` (`/agent/runs/{runId}/model-calls/{modelCallId}`), `listAgentRunToolCalls`, `listAgentRunVerdicts`; one tag `Agent Runs` keeps one generated interface `AgentRunsApi`. Extend `AgentRun` with `trigger`, `endReason`, `endDetail`, `principalKind` (detail by composition, no `allOf`). Shared `page`/`limit`/`runId` in `components.parameters`.
- `services/agent-service/build.gradle` -- generator (`interfaceOnly`, `skipDefaultInterface`: the controller must implement every operation); response-conformance spec already listed.
- `run/AgentRunController`, `run/AgentRunQueryService` -- extend; new `run/RunLogQueries` (JdbcTemplate reads of steps, model calls, tool calls, verdicts + approvals, precedent resolution), `run/RunViews` (rows to DTOs), `run/AgentRunNotFound` handling via shared `NotFoundException` (`ERR_NOT_FOUND`).
- `exception/GlobalExceptionHandler` -- add `NotFoundException` → 404 `ErrorResponse`.
- `config/SecurityConfig` -- `/api/v1/agent/runs` literal becomes `GET /api/v1/agent/**` organizer; new `security/ConsoleAccess` bean (`carriesAct` via shared `Actors.carriesAct`).
- Tables: V2 (`agent_steps`, `agent_model_calls`, `agent_tool_calls`, `agent_verdicts`, `agent_approvals`, `agent_undos`, `agent_inbound_messages`), V3-V6 columns `end_reason`, `end_detail`, `principal_kind`, `grant_run_id`. Verdict reason and cap live in `inputs ->> 'reason'`, `inputs ->> 'cap'`; cited precedents are source ids `agent_approvals:<id>`, `agent_undos:<id>`, `event:<code>` (`precedent/DecisionPrecedent`, `PastEventPrecedent`).
- Tests: extend `run/AgentRunControllerIntegrationTest` pattern (`tokenWithRole`, `TestTokens.agentToken`, TRUNCATE CASCADE); new `run/RunLogReadIntegrationTest` seeds rows by JdbcTemplate (a scripted run's shape), `run/RunLogAccessIntegrationTest` parameterised over every new operation.
- Frontend: `web-frontend/package.json` has no agent script; add `generate:api-types:agent` to `generate:api-types` → `src/types/generated/agent-api.types.ts`.
- DAST: `scripts/ci/access-control-check.py` globs `docs/api/*.openapi.yml`; new paths must not be on `access-control-public.txt`.
- Bruno: new production collection `bruno-tests/agent-api/` (read-only GETs), registered in `scripts/ci/run-bruno-tests.sh` `collections`.
- Docs (doc-drift): `docs/architecture/module-map.md`, `docs/architecture/06-backend-architecture.md` (agent-service), `docs/api/README.md` (+ `docs/prd-enhanced.md` mapped to docs/api, check), `docs/testing/e2e-testing-guide.md` (bruno-tests).

## Tasks & Acceptance

**Execution:**
- [x] `docs/api/agent-api.openapi.yml` -- six operations, schemas, 400/401/403/404 ErrorResponse -- contract first.
- [x] `run/*`, `security/ConsoleAccess`, `config/SecurityConfig`, `exception/GlobalExceptionHandler` -- serve them.
- [x] `run/RunLogReadIntegrationTest`, `run/RunLogAccessIntegrationTest`, existing controller test -- matrix rows.
- [x] `web-frontend/package.json`, `src/types/generated/agent-api.types.ts` -- generated types (own `chore` commit, `[no-doc]`: no user-facing change).
- [x] `bruno-tests/agent-api/*.bru`, `scripts/ci/run-bruno-tests.sh` -- organizer 200, speaker 403 on `/agent/runs`, comments only in `docs {}`.
- [x] docs; deferred-work entries.

**Acceptance Criteria:**
- Given `python3 scripts/ci/access-control-check.py --list`, when run, then it lists every new GET path as probed and `--check-allowlist` passes.
- Given `:services:agent-service:test`, when it runs, then every test passes and the response-conformance gate reports no new baseline line.
- Given `npm run type-check` and `npm run lint:api`, when run after generation, then both pass.

## Implementation Notes

- Gates (unattended, owner go for the epic): one goal (the read API; spec, service, types and Bruno ship together); plan about 2,000 tokens, kept whole. No open questions; checkpoint approved without a human per the owner's go. Implemented directly in this session (rack rule: one heavy build at a time), as in stories 2-8.

- 2026-11-22 (build): implemented directly (no coding subagent), as stories 2-8. `run/RunLogQueries` + `run/RunViews` became one `run/RunLogReadService` (SQL rows straight to the generated DTOs; a second mapping layer per type added code, no safety). Spec: free JSON columns `content`/`input` are untyped (any JSON; a model can send a non-object input), `approval` is `oneOf [AgentApproval, null]` (DTO nulls are serialized), `AgentRun` gains `grantRunId` and story 1's wrong `id` description (`act.run`) is corrected: `act.run` is the CUMS grant's run id, not `agent_runs.id`.
- 2026-11-22 (build): `npm run lint:api` flagged six `uuid-path-param` lines. Accepted under the ADR-003 amendment 2026-11 (owner's go for the epic): the run is an unnamed top-level aggregate whose id never leaves agent-service (X4), the model call an owned child loaded with `run_id AND id` (X1, pinned by the 404 test). Baseline +6 lines and the X1/X4 owner groups in `web-frontend/scripts/api-lint-*.txt`, outside the Code Map: recorded here and in the owners file comment. Types are generated into `agent-api.types.ts` with the new `generate:api-types:agent` script.
- 2026-11-22 (build): a mutation check (ConsoleAccess.carriesAct forced false) made the act test fail, then restored. First full verification: 202 tests, 0 failures, 0 errors, 0 skipped; checkstyle clean; response conformance 23 responses, baseline 0; `npm run type-check`, `lint:api`, api-lint vitest (51) pass; DAST `--list` probes all 7 agent GETs, `--check-allowlist` 0 stale; Bruno collection guard passes, the three `.bru` parse (bru run against local dev: connection refused, no skipped file); `make check-test-ratchet` no RAISED or STALE.

## Plan Change Log

## Review Triage Log

Pass 1 (quick lens): high 0, medium 1, low 3, false 0, maybe-false 0; 1 rejected (plan edit).

| # | Finding | Verdict | Route | Evidence / action |
|---|---|---|---|---|
| 1 | 401/403 carry no `ErrorResponse` body though the spec and the plan say every 4xx does | medium | patch | Real: no entry point or access-denied handler (pre-existing for `/agent/runs` since story 1). New `security/SecurityErrorWriter` on the production chain and the resource server (`ERR_UNAUTHORIZED`, `ERR_FORBIDDEN`, `WWW-Authenticate: Bearer` kept); access test asserts the body on every refusal. Other services' same gap deferred |
| 2 | Detail and page/total read in separate transactions/snapshots; a running run can mix states | low | patch | Real under READ COMMITTED. Service is `REPEATABLE READ` per method; `detail(runId)` reads run, totals and messages in one transaction |
| 3 | Story-1 Bruno deferred entry not closed, no new deferred entries | low | patch | Appended a closure entry and this story's entries (append-only file) |
| 4 | api-lint baseline/owners changed outside the Code Map, split of RunLogQueries/RunViews unrecorded | low | reject | Fix is a plan edit; recorded in Implementation Notes (agent-owned) instead |
| 5 | module-map bullet continuation lines lost their indent | low | patch | Re-indented |

Verification after patches (2026-11-22): `:services:agent-service:test` + `checkstyleMain` + `checkstyleTest` BUILD SUCCESSFUL, 202 tests, 0 failures, 0 errors, 0 skipped (JUnit XML); `make check-test-ratchet` no RAISED or STALE.

## Design Notes

Architecture note: modules touched — agent-service (`run`, `security`, `config`, `exception`), spec, generated frontend types, Bruno · new dependencies — none · new endpoints — six under `/agent/runs/{runId}`; nearest existing `GET /agent/runs` (same spec), extended by sub-resources because steps, calls and verdicts are children of one run (ADR-013 §5 UUID sub-resources) · reused — `DeterministicPaging`, shared `PaginationMetadata`, `ErrorResponse`, `NotFoundException`, `Actors.carriesAct`, test fixtures · deviation — none.

Decisions (unattended, owner go for the epic):
- **Full prompt exposed, on the detail only:** AD-9 says every exchange is logged in full and the console reads it; organizers already see the inbound mails and speaker data the prompt holds. List items carry status, model, stop reason, chosen tools and the four token counts; `getAgentRunModelCall` adds `prompt` and `response` as stored (free JSON). Keeps pages small (a prompt grows with every step).
- **Cited precedents resolved:** each verdict lists `{sourceId, source APPROVAL|UNDO|PAST_EVENT, resolved, toolName, toolCallId, runId, approvalState, undoOutcome, at, eventCode}`, so the organizer sees what the gate leaned on without a second call; resolved per page in two batched queries.
- **Pending approvals per run:** embedded in the verdict (`approval {id, state, createdAt, decidedAt, decidedBy}`) and counted in the run detail; the cross-run inbox belongs to epic-oversight-console with its actions.
- **`act` refused twice:** the decoder refuses agent tokens (401, story 1); method security refuses any authenticated token carrying `act` (403), so a future decoder change cannot open the log to the agent.
- **Bruno:** a production collection fits: two GETs, read-only, no comms.
- Epic 17 rule: archunit store 0 at start and end; the literal `/agent/runs` chain rule becomes the prefix rule (one rule for the whole read API).

## Verification

**Commands:**
- `set -o pipefail; ./gradlew --max-workers=4 :services:agent-service:test :services:agent-service:checkstyleMain :services:agent-service:checkstyleTest 2>&1 | tee ~/.cache/batbern-test-logs/agent-service-test-1-9.log` -- BUILD SUCCESSFUL; then `./gradlew --stop`
- `cd web-frontend && npm run generate:api-types && npm run type-check && npm run lint:api` -- pass
- `python3 scripts/ci/access-control-check.py --list | grep agent`; `python3 scripts/ci/access-control-check.py --check-allowlist`; `scripts/ci/run-bruno-tests.sh --check-collections`
- `make check-test-ratchet` -- no RAISED or STALE line
