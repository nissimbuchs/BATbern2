---
title: 'Intent tools carry a class derived from the operation table, and read tools see past events'
type: 'feature'
ticket: '5'
created: '2026-11-22'
status: 'built'
baseline_revision: 'a409d67b265ff3de3dde90d877039e848ce4b578'
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

**Problem:** The loop takes whatever tools its caller hands it; nothing gives a tool a risk class, nothing stops a tool from understating it or calling an operation nobody classified, and there is no real tool, so the agent cannot read the past events the gate's precedents need (R4, AD-6, AD-8).

**Approach:** A `ToolRegistry` in `tools/` with one `OperationClassTable` (method + spec path → `read` < `reversible` < `reply` < `binding` < `audience`), a `ToolClassCheck` that the registry runs at startup and a plain unit test runs in the suite, and three read tools (`findPastEvents`, `listEventSessions`, `listSessionSpeakers`) on the public API through story 4's `PublicApi`. The loop offers the registry's tools; each tool's `PublicApi` is limited to the operations it declares.

## Boundaries & Constraints

**Always:** A tool declares its class, its operations and, when `reversible`, its undo operation. Check violations: declared class ≠ highest class of its operations (below is the defect the epic names; above is refused too, so a class is always derived), an operation or undo operation missing from the table, a `reversible` tool without an undo operation or a non-reversible tool with one, a name not camelCase verb + object, a duplicate name. Every table operation exists in `docs/api/*.openapi.yml` (test). Tool arguments are ADR-003 ids (`eventCode` `^BATbern[0-9]+$`, `sessionSlug` as the spec); invalid input is an error outcome without a call. Reads reuse the run's token (story 4). Tools never call the model (AD-14). Tool output is a deterministic projection of the API answer: no e-mail, picture, bio or material URL to the model. Tests: build check plain JUnit with fixture tools in test sources only; ITs stub CUMS and the gateway at the wire with `ContractServer` validating against the events-core and sessions specs.

**Never:** A non-read tool in main sources. Gate, precedents, principal-kind filtering, caps. Any change outside agent-service except docs. Push, PR.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Past events | model calls `findPastEvents {year: 2024}` | GET `/api/v1/events` with `filter` date < now and year, `includeArchived=true`, `sort=-date`, Bearer = run token; projected list back | none |
| Sessions + speakers | `listEventSessions {eventCode}` then `listSessionSpeakers {eventCode, sessionSlug}` | two GETs, one mint for the run (reads reuse) | none |
| Bad id | `eventCode: "56"` | error tool_result, no GET | none |
| API 404 | gateway answers 404 | error tool_result with status, run goes on | none |
| Undeclared call | a tool calls an operation it did not declare | tool call FAILED, nothing sent | `IllegalStateException` |
| Understated fixture | declared `read`, calls a `binding` op | check names the tool and both classes | registry refuses to start |
| Unlisted fixture | calls an op not in the table | check names the op | same |
| Reversible without undo | fixture | check names the tool | same |

</frozen-after-approval>

## Code Map

- `tools/AgentTool.java` -- add `operationClass()`, `operations()` (`Set<Operation>`), `undoOperation()` (`Optional`, default empty).
- New in `tools/`: `OperationClass` (enum, ordered), `Operation` (record method + spec path, `matches(method, concretePath)`), `OperationClassTable` (`standard()` = the 3 GETs; `with(...)` for tests), `ToolClassCheck` (`violations(tools, table)` → list of messages), `ToolRegistry` (`@Component`-built via `ToolConfig`; ctor throws on violations; `tools()`, `standardTools(ObjectMapper, Clock)`), `ToolInput` (id validation helpers), `JsonProjection`.
- New `tools/read/`: `FindPastEventsTool` (GET `/events`; args `year`, `titleContains`, `limit` 1-50 default 10; filter JSON percent-encoded), `ListEventSessionsTool` (GET `/events/{eventCode}/sessions?limit=100`), `ListSessionSpeakersTool` (GET `/events/{eventCode}/sessions/{sessionSlug}/speakers`).
- `gateway/PublicApi.java` -- `limitedTo(Set<Operation>)` (same credentials; undeclared call throws before the token); dot/slash guard on the path part only, so an encoded `/` in a query value passes.
- `loop/AgentLoop.java` -- inject `ToolRegistry`; specs from `registry.tools()`; per tool call `ToolContext` with `api.limitedTo(tool.operations())`. `loop/AgentRunRequest.java` -- drop `tools`.
- Tests to adapt (21 `new AgentRunRequest(` sites): `AgentLoopIntegrationTest`, `AgentLoopLogIntegrationTest`, `AgentSwitchOffIntegrationTest`, `AgentCredentialsIntegrationTest`; `TestTools` gets declarations and camelCase names (`lookupEvent`, `listCompanies`, …). New test `@Primary @Component TestToolRegistry` (pattern of `ScriptedLlm`): `install(tools...)` replaces the offered set (checked against the standard table plus GET/POST `/companies`), `reset()` back to the standard tools. `CumsAtTheWire.bindTo(rt, extraSpecs...)` to add the events specs.
- Specs: `docs/api/events-core-api.openapi.yml` 42 (`listEvents`), `event-sessions-api.openapi.yml` 316 (`listSessions`), 1458 (`listSessionSpeakers`); all three `permitAll` GETs in EMS, so every principal kind may use them. `OpenApiContract.load(spec).operation(method, path)` for the table-exists test (paths include `/api/v1`).
- Docs: module-map agent-service bullet, 06-backend-architecture agent-loop paragraph.

## Tasks & Acceptance

**Execution:**
- [x] `tools/{OperationClass,Operation,OperationClassTable,ToolClassCheck,ToolRegistry,ToolInput,JsonProjection}.java`, `AgentTool` -- registry, table, check.
- [x] `tools/read/*Tool.java` + `config/ToolConfig` -- the three read tools, registry bean.
- [x] `PublicApi`, `ToolContext`, `AgentLoop`, `AgentRunRequest` -- wiring, per-tool limit.
- [x] `src/test/.../tools/ToolClassCheckTest.java` -- the build check: real registry passes; fixtures (understated, overstated, unlisted op, unlisted undo, reversible without undo, read with undo, bad name, duplicate) fail with named messages; `ToolRegistry` ctor throws.
- [x] `src/test/.../tools/OperationClassTableSpecTest.java` -- every table operation exists in docs/api.
- [x] `src/test/.../tools/RealToolRegistryCoverageTest` -- every concrete `AgentTool` in main `ch.batbern.agent` is in `standardTools` (classpath scan).
- [x] `src/test/.../tools/read/ReadToolsIntegrationTest.java` -- matrix rows 1-5 through the loop with `ScriptedLlm`; the model's `LlmRequest` offered the three tools; requests asserted (query, Bearer = first token, one mint).
- [x] `PublicApiPathTest` -- encoded slash in query passes; `limitedTo` refuses an undeclared call without minting.
- [x] Adapt existing tests; docs.

**Acceptance Criteria:**
- Given `:services:agent-service:test`, when it runs, then every test passes, including the build check on the real registry and every gateway request validated against its spec.
- Given a planted understated or unlisted-operation tool in the registry, when the context or the check runs, then it fails naming the tool.

## Implementation Notes

- Gates (unattended, owner go for the epic): one goal (classified tools reaching past events; registry without a tool or tools without the registry ship nothing). Plan ~1,900 tokens, kept whole. No open questions; checkpoint approved without a human per the owner's go.
- Rack rules: one heavy build at a time; Gradle `--max-workers=4`, then `./gradlew --stop`; output via `set -o pipefail; ... 2>&1 | tee ~/.cache/batbern-test-logs/<name>.log`; never `make test`; explicit `git add`.

## Plan Change Log

- 2026-11-22 (implementation): `Operation` lives in `gateway/`, not `tools/`: `PublicApi.limitedTo` needs it and `tools` already depends on `gateway`, so `tools/` would have made a package cycle. The shipped tool list is `tools/StandardTools.create(Clock)` (the read tools need only the clock), shared by `config/ToolConfig` and the tests; the coverage test is `StandardToolsCoverageTest`. Implemented directly in this session, not through an implementation subagent (the session held the investigation; rack rule of one heavy build at a time). A wrong fixture operation (`PUT` where the sessions spec has `PATCH`) was caught by `OperationClassTableSpecTest` on its first run, which is the evidence that test bites.
- 2026-11-22 (review fixes): `PublicApi.limitedTo` only narrows (intersection with an existing limit). The limited context is built inside the tool call's `try`. `findPastEvents` argument renamed `titleContains` → `searchText` with a description matching EMS's archive search (every word in title, description, code, a session title or abstract, or a speaker name). The coverage test tells main from test classes by class root.

## Review Triage Log

Pass 1 (quick lens): high 0, medium 1, low 4, false 0, maybe-false 0, rejected 1.

| # | Finding | Verdict | Route | Evidence / action |
|---|---|---|---|---|
| 1 | A tool could call `context.api().limitedTo(...)` and widen its own API | medium | patch | `limitedTo` built from the argument alone. Now intersects with the current limit; `PublicApiPathTest` asserts a widened API still refuses |
| 2 | Principal-kind deferral not in deferred-work.md | low | patch | Entry appended (plus the template-match note) |
| 3 | Code Map deviations (`Operation` in gateway, `StandardTools`, test name) not logged | low | reject | Fix is a plan edit; recorded in the Plan Change Log above for the reader anyway, no behaviour change |
| 4 | `titleContains` description misleads: EMS searches title, description, code, sessions, speakers | low | patch | Checked `EventSearchService` CONTAINS on `title`. Renamed to `searchText`, description says what EMS does; doc updated |
| 5 | Coverage test splits classes by `"/test/"` in the URL; a checkout under a `test` dir breaks it | low | patch | Now compares each class's code-source root with `StandardTools`' and `FixtureTool`'s; javadoc states the scan sees top-level and static nested classes only |
| 6 | `api.limitedTo(tool.operations())` outside the tool call's `try`: a throwing `operations()` leaves a STARTED row and fails the run | low | patch | Moved inside the `try` |

Verification after patches (2026-11-22): `:services:agent-service:test` + `checkstyleMain` + `checkstyleTest` BUILD SUCCESSFUL, 127 tests, 0 failures, 0 errors, 0 skipped (JUnit XML); `make check-test-ratchet` and `make check-s2s-client-urls` exit 0, no RAISED or STALE line (run before the patches, which touched no test-ratchet-relevant pattern).

## Design Notes

Architecture note: modules touched — agent-service only (`tools`, `tools/read` new; `gateway`, `loop`, `config`) · new dependencies — agent-service → EMS public reads `GET /events`, `/events/{eventCode}/sessions`, `/events/{eventCode}/sessions/{sessionSlug}/speakers` through the gateway (the map: everything except CUMS agent-* through the public API) · new endpoints — none (consumer only) · reused — `PublicApi`/`RunCredentials` (story 4), `ContractServer`, `OpenApiContract`, `ScriptedLlm` · deviation — none.

Decisions (taken unattended):
- Fixtures prove the check against a test table (standard table plus write operations), because the shipped table holds only the three reads; the real registry is checked against the real table.
- Overstated class is a violation too: the class is derived, never chosen (AD-6 "derived risk class").
- Runtime limit (`PublicApi.limitedTo`) makes the declared operation set binding, not a claim; template match `{x}` = one segment.
- Principal kinds on registry entries (Consistency Conventions) are deferred to the first tool not open to every principal (epic-speaker-mail-agent); all three reads are public GETs. Recorded in deferred-work.md.
- Projections minimise personal data to the model: speakers carry username, names, company display name, role, confirmation, presentation title.
- Epic 17 rule: archunit store 0 at start and end (agent-service has none); the story removes the "tools per run" seam story 2 left; test-ratchet baseline unchanged.

## Verification

**Commands:**
- `set -o pipefail; ./gradlew --max-workers=4 :services:agent-service:test 2>&1 | tee ~/.cache/batbern-test-logs/agent-service-test-1-5.log` -- BUILD SUCCESSFUL; then `./gradlew --stop`
- `make check-test-ratchet`, `make check-s2s-client-urls` -- no RAISED line
