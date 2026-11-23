---
title: 'agent-service acts with a CUMS-minted token, minted again before every write'
type: 'feature'
ticket: '4'
created: '2026-11-22'
status: 'built'
baseline_revision: '0172b8cf09827d8f7ab7c35830685d003b5d9fc3'
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

**Problem:** The loop (stories 2, 3) runs tools with no identity: agent-service has no CUMS client, no caller secret and no token, so no tool can call the public API, and nothing stops a run whose grant or token CUMS refuses (R2, AD-3, AD-15).

**Approach:** A CUMS client for `POST /internal/agent-grants` and `POST /internal/agent-tokens` with agent-service's caller secret (Secrets Manager → `AGENT_CUMS_CALLER_SECRET`). Before its first model call a run obtains its grant and mints a token; tools reach the public API only through a per-run gateway client that reuses the token for reads and mints a fresh one before every write. A refused grant or mint ends the run HANDED_TO_ORGANIZERS with the CUMS code logged, no mail.

## Boundaries & Constraints

**Always:** Grant sources: `ORGANIZER_LOGIN` (organizer's Cognito JWT forwarded as `Authorization` on the grant call only), `LINKED_RUN` (`linkedRunId` + `linkedRunGrantSecret`), `SCHEDULER_JOB` (`jobName`), plus a grant handed over by EMS (run id + secret, no grant call; trigger INBOUND_MAIL). The run's trigger follows from its grant source. Request bodies exactly as `cums-internal.openapi.yml`. Writes = every non-GET; a write never reuses a token. Reads reuse the token until 60 s before `expiresAt`. The CUMS `details.code` (or `HTTP_<status>`, `CUMS_UNREACHABLE`, `CALLER_SECRET_MISSING`) is stored on the run as `end_detail` (pattern `^[A-Z][A-Z0-9_]{0,63}$`, anything else `UNKNOWN_CODE`) and logged with the run id; never a username, token, grant secret or caller secret in a log, exception message or `toString`. The CUMS grant run id is stored on the run (`grant_run_id`), so `agent:<run>` in other services' audit columns leads to `agent_runs`. Tests: ContractServer (the repo's S2S harness) on agent-service's `RestTemplate`, each request validated against the CUMS internal and companies specs; no own-code mocks. CDK grants the caller secret to the agent-service task in addition to the Anthropic key.

**Never:** Any change to CUMS behaviour or its spec (a CUMS bug is fixed test-first in its own commit). Real intent tools, the registry, operation classes (story 5). Gate, caps, read API, alarms (stories 7-10). Mail. A call to any `/internal/**` other than the two above. `/api/v1` literals in a `client` package. Push, PR.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Read | scheduler grant 201, mint 200; tool GETs `/api/v1/companies` | GET carries `Bearer <minted>`; run DONE, `grant_run_id` set | none |
| Two writes | tool POSTs twice | three mints (start + one per write), each write with its own token | none |
| Read near expiry | token expires in < 60 s | next read mints first | none |
| Grant refused | 403 `PRINCIPAL_NOT_ELIGIBLE` (or 401, 400) | HANDED_TO_ORGANIZERS, `GRANT_REFUSED`, detail = code, no model call | log: run id + code |
| First mint refused | 403 `GRANT_INVALID` / 503 `AGENT_SIGNING_KEY_MISSING` | HANDED_TO_ORGANIZERS, `TOKEN_REFUSED`, detail = code, no model call | same |
| Mint before a write refused | mint 403 mid-run | tool call FAILED, run HANDED_TO_ORGANIZERS `TOKEN_REFUSED`, no further model call, write not sent | even if the tool swallows the exception |
| CUMS down | transport error / 5xx without code | `GRANT_REFUSED`/`TOKEN_REFUSED`, `CUMS_UNREACHABLE` / `HTTP_<status>` | no retry |
| No caller secret | blank or `none` | `GRANT_REFUSED`, `CALLER_SECRET_MISSING`, CUMS not called | startup warning |
| Organizer login | Cognito JWT | grant call carries it as Bearer; mint call carries none | none |
| Handed-over grant | EMS run id + secret | no grant call, mint with it, trigger INBOUND_MAIL | none |

</frozen-after-approval>

## Code Map

- CUMS (read only): `InternalAgentController`, `dto/AgentGrantRequest|AgentGrantResponse|AgentTokenRequest|AgentTokenResponse`; spec `docs/api/internal/cums-internal.openapi.yml` 1127-1480, schemas 1789-1890 (`additionalProperties: false`: send only the trigger's fields), header param `AgentCallerSecret`.
- `services/agent-service/src/main/java/ch/batbern/agent/loop/AgentLoop.java` -- `run()`: open credentials after `startRun`, inside the try; after each tool batch check the credentials' refusal. `AgentRunRequest`: `RunTrigger trigger` → `GrantSource grant` (`trigger()` derived).
- `.../tools/AgentTool.java` -- `execute(JsonNode input, ToolContext context)`; `ToolContext(UUID runId, PublicApi api)`.
- `.../run/RunEndReason.java` -- add `GRANT_REFUSED`, `TOKEN_REFUSED` (HANDED_TO_ORGANIZERS); `AgentRunEntity` + `grantRunId`, `endDetail`; `log/DecisionLog` `recordGrant`, `endRun(runId, reason, detail)`.
- New `db/agent-migration/V3__agent_run_grants.sql` (V1/V2 untouched): columns, unique `grant_run_id`, end_reason check widened, end_detail pattern check.
- New package `ch.batbern.agent.identity` (`CumsAgentClient`, `GrantSource` sealed, `RunGrant`, `AgentToken`, `AgentCredentialsRefusedException`, `RunCredentials`) and `ch.batbern.agent.gateway` (`PublicApi`, `ApiResponse`); `config/RestClientConfig` (one `RestTemplate`, 5 s connect, 30 s read, no logging of bodies). Not named `client` (S2S URL guard scans `client` dirs).
- `AgentProperties` + `Cums(baseUrl, callerSecret)` masked, `Gateway(baseUrl)`; `application.yml`: `${COMPANY_USER_MANAGEMENT_SERVICE_URL:http://company-user-management:8080}`, `${AGENT_GATEWAY_URL:${API_BASE_URL:http://api-gateway:8080}}`, `${AGENT_CUMS_CALLER_SECRET:}`; test yml secret `test-only-agent-caller-secret`.
- Tests to adapt: `loop/AgentLoopIntegrationTest`, `AgentLoopLogIntegrationTest`, `AgentSwitchOffIntegrationTest`, `TestTools`, `migration/FlywayBaselineIntegrationTest`, `config/AgentPropertiesTest`. Harness: shared-kernel `ContractServer` (`bindTo(rt, CUMS_INTERNAL, COMPANIES)`, `respondPerRequest`, `times(...)`, `close()` after each test); companies spec has `GET/POST /companies`.
- Infra: `agent-service-stack.ts` (`cumsCallerSecret` prop → `AGENT_CUMS_CALLER_SECRET`, env `AGENT_GATEWAY_URL=http://api-gateway:8080`), `bin/batbern-infrastructure.ts` (pass `secretsStack.agentServiceCallerSecret`), tests `agent-service-stack.test.ts` (flip "no CALLER_SECRET"), `agent-signing-key.test.ts` (`referencedBy(AGENT_SERVICE_CALLER)` = CUMS + agent).
- Docs: module-map agent-service bullet, `06-backend-architecture.md` agent-service paragraph, `docs/operations/runbooks.md` caller-secret row, `.env.example`; check `.github/doc-drift-mappings.yml` for `infrastructure/lib/stacks/agent-service-stack.ts`.

## Tasks & Acceptance

**Execution:**
- [x] `V3__agent_run_grants.sql`, `RunEndReason`, `AgentRunEntity`, `DecisionLog` -- grant run id, refusal reasons, end detail.
- [x] `identity/*`, `gateway/*`, `config/RestClientConfig`, `AgentProperties`, `application.yml` -- client, credentials, gateway.
- [x] `AgentLoop`, `AgentRunRequest`, `AgentTool`, `ToolContext` -- grant before step 1; refusal ends the run.
- [x] `src/test/.../identity/CumsAtTheWire.java` -- test helper on ContractServer: grants and mints answered per request (distinct tokens), refusals with a code.
- [x] `src/test/.../identity/AgentCredentialsIntegrationTest.java` -- every matrix row; requests asserted (caller secret header, body, Bearer), rows via `JdbcTemplate`; log capture for the refusal line without the test username (positive control: run id present).
- [x] Adapt existing loop and migration tests.
- [x] Infra + CDK tests; docs.

**Acceptance Criteria:**
- Given `:services:agent-service:test`, when it runs, then every test passes and every CUMS and gateway request was validated against its spec.
- Given the touched CDK tests, when they run, then the caller secret reaches CUMS and agent-service only, injected into agent-service as `AGENT_CUMS_CALLER_SECRET`.

## Implementation Notes

- Gates (unattended, owner go for the epic): one goal (a run acts with a minted token; client, credentials and loop end ship together). Plan ~1,900 tokens, kept whole. No open questions; checkpoint approved without a human per the owner's go.
- Rack rules: one heavy build at a time; Gradle `--max-workers=4`, then `./gradlew --stop`; output via `set -o pipefail; ... 2>&1 | tee ~/.cache/batbern-test-logs/<name>.log`; never `make test`; explicit `git add`.

## Plan Change Log

- 2026-11-22 (implementation): a 2xx CUMS answer that is not its contract (no run id, no token,
  unparsable `expiresAt`) ends the run with the refusal reason and `INVALID_CUMS_RESPONSE`; the
  matrix had no row for it. `AgentRunResult` carries the end detail. `loop/TestTools` became public
  so the identity test reuses its recording tool. The grant's run id is recorded before the first
  mint, so a run whose first mint is refused still leads to its grant. A refused mint stops the
  rest of the tool batch at once (those tools are not run), not only after the batch. Doc-drift
  mapping added: `agent-service-stack.ts` → runbooks.

## Review Triage Log

Pass 1 (quick lens): high 0, medium 1, low 4, false 0, maybe-false 0.

| # | Finding | Verdict | Route | Evidence / action |
|---|---|---|---|---|
| 1 | A grant run id stored twice (handed-over grant delivered again) hits `uq_agent_runs_grant_run_id`; the run ended FAILED/INTERNAL_ERROR instead of handed over | medium | patch | `recordGrant` sat in a try catching only refusals. Now checked first (`existsByGrantRunId`, flush), the violation is caught, run ends GRANT_REFUSED / `GRANT_ALREADY_USED`, no mint, no model call; IT added |
| 2 | Startup warning without caller secret tested by calling the listener directly | low | reject | Standard `@EventListener(ApplicationReadyEvent)` on a component; the fix is an extra context test, and a broken listener changes no run outcome (runs are still refused) |
| 3 | `PublicApi.uri` let `%2e`/`%2f` dot segments and backslashes through | low | patch | Guard now rejects backslash and `%2e`, `%2f`, `%5c` in any case; `PublicApiPathTest` 10 paths, no request and no mint |
| 4 | No test for a refused re-mint before a mid-run read | low | patch | Same `mint()` path as the write; IT added: TOKEN_REFUSED, GET not sent, no further model call |
| 5 | Matrix row "CUMS down" covered for 2 of 4 combinations | low | patch | ITs added: mint transport error (TOKEN_REFUSED / CUMS_UNREACHABLE), grant 502 without body (GRANT_REFUSED / HTTP_502) |

Verification after patches (2026-11-22): `:services:agent-service:test` + checkstyle 106 tests, 0 failures, 0 errors, 0 skipped (JUnit XML); CDK jest 4 suites 40 tests, 0 failures (before patches; no infra file changed by them); `tsc --noEmit` clean; `make check-test-ratchet`, `make check-s2s-client-urls` no RAISED/STALE.

## Design Notes

Architecture note: modules touched — agent-service (`identity`, `gateway` new; `loop`, `tools`, `run`, `log`, `config`), infrastructure (AgentServiceStack, bin) · new dependencies — agent-service → CUMS `/internal/agent-grants`, `/internal/agent-tokens` (the module map names exactly these) and → Spring gateway public API · new endpoints — none (consumer only) · reused — ContractServer, CUMS specs, `ErrorResponse.details.code`, placeholder-free generated caller secret of SecretsStack · deviation — headers are not built with `S2sHeaders.forCurrentUser()`: agent-service never forwards a user's JWT except the organizer login on the grant call; it acts with its own minted token, which the map requires.

Decisions (taken unattended, recorded here):
- Harness: ContractServer (MockRestServiceServer + spec validation), the repo's S2S standard; no WireMock or MockWebServer in the repo.
- Grant and first mint happen before the first model call, so a refused grant costs no model tokens and the run is handed over at once.
- A handed-over EMS grant is accepted now (no grant call) so INBOUND_MAIL keeps a source; the inbound contract that delivers it is epic speaker-mail-agent.
- Gateway HTTP errors (4xx/5xx) go back to the tool as `ApiResponse(status, body)`; the tool decides. Only credential refusals end the run.
- Refusal is detected by the loop through `RunCredentials.refusal()` after each tool batch, not by trusting the exception to reach it.
- Epic 17 rule: archunit store 0 at start and end (agent-service has none); test-ratchet baseline unchanged.

## Verification

**Commands:**
- `set -o pipefail; ./gradlew --max-workers=4 :services:agent-service:test 2>&1 | tee ~/.cache/batbern-test-logs/agent-service-test-1-4.log` -- BUILD SUCCESSFUL; then `./gradlew --stop`
- `set -o pipefail; cd infrastructure && npx jest test/unit/agent-service-stack.test.ts test/unit/agent-signing-key.test.ts test/unit/anthropic-api-key.test.ts test/unit/secrets-stack.test.ts 2>&1 | tee ~/.cache/batbern-test-logs/cdk-1-4.log` -- all pass
- `make check-test-ratchet`, `make check-s2s-client-urls` -- no RAISED line
