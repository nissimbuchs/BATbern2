---
title: 'Refactor sweep'
type: 'chore'
ticket: '11'
created: '2026-11-22'
status: 'built'
baseline_revision: 'ccf4a0341cdbd4120a34b968316b923764042f79'
route: 'full'
route_source: 'auto'
risk: 'medium'
review: 'quick'
review_source: 'pinned'
lenses_ran: ['quick']
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/initiative-agentic-interaction/tickets/epic-agent-runtime/epic-agent-runtime.md'
  - '{project-root}/_bmad-output/initiative-agentic-interaction/deferred-work.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Stories 1-10 left deferred findings in `deferred-work.md` and in their review triage: local dev cannot run an agent run (caller secret pair unset), the selective deploy path never image-checks a changed service, docker-compose and the gate spike lack agent-service, the CUMS internal spec omits proxy 5xx, day-boundary tests use the real clock, the mail lock has no concurrency test, and some docs may drift from the code.

**Approach:** Triage every deferred item once: fix cleanup, small bugs, test and doc gaps here; record behaviour changes, later-epic scope and owner decisions in the epic's Notes as `Recorded (refactor sweep, 2026-11-22):` lines. Then run every touched module's full suite as the epic's closing check.

## Boundaries & Constraints

**Always:** Deploy workflow edits keep today's behaviour except the image check now runs for the changed services it was meant to cover; the YAML logic is tested. Local secrets are generated per instance, never committed. Tests mock only at the process boundary (the clock counts). One heavy build at a time on rack.

**Never:** No new feature, no behaviour change of the agent loop, gate or tools; no push, no PR; no edit of applied Flyway migrations; no `make test`.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Local dev, no secret set | `.env` without `AGENT_CUMS_CALLER_SECRET` | `.env.native.N` holds one generated value for both `AGENT_CUMS_CALLER_SECRET` and `AGENT_CALLERS_AGENT_SERVICE_SECRET`, reused on the next start | none |
| Local dev, secret set | `.env` sets `AGENT_CUMS_CALLER_SECRET` | both variables take that value | none |
| Selective deploy | CHANGED_SERVICES=" agent company-management" | image check runs for agent-service and company-user-management-service only | missing image fails the step as on the full path |
| Proxy 5xx on grant | CUMS behind Envoy answers 502/503 | declared in the spec; the agent test answers through the declared-status path | run handed over `HTTP_502` |
| Two concurrent first mails | two runs, two addresses, same moment | one RUN, one PENDING_APPROVAL (RECIPIENTS_ABOVE_ONE) | none |

</frozen-after-approval>

## Code Map

- `scripts/dev/lib/native-services.sh`, `scripts/dev/start-all-native.sh` (`create_env_native`, heredoc ~L198-312) -- add `agent_caller_secret` resolver (env > existing `.env.native.N` > `openssl rand -hex 32`) and write the pair into `.env.native.N`. CUMS reads `AGENT_CALLERS_AGENT_SERVICE_SECRET` (`AgentCallerSecrets`), agent-service `AGENT_CUMS_CALLER_SECRET` (application.yml L94). The EMS pair uses one name on both sides, so it is consistent by construction.
- `.github/workflows/deploy-staging.yml` "Prepare deployment" L527-532 -- `grep -q "$SERVICE"` compares repo names with short names from the `changes` step L172-215; add `service_short_name()` mapping and a word match. Base detection is identical in build.yml `should-build`, so every short name in CHANGED_SERVICES had its image built.
- `infrastructure/test/unit/` -- new jest test that extracts the mapping function from the workflow and checks every repo name maps to the short name the `changes` step emits (pattern: `rollback-covers-promoted-services.test.ts`).
- `docker-compose.yml`, `services/agent-service/Dockerfile.dev` (new, copy of PCS's), `docker-compose.gate.yml`, `scripts/gate/build-images.sh`, `scripts/gate/run-gate.sh` header -- add agent-service (port 8083 in compose, `AGENT_SERVICE_URL` on the gateway, caller secret pair from one env var).
- `docs/api/internal/cums-internal.openapi.yml` `/internal/agent-grants`, `/internal/agent-tokens` -- add `502`/`503` without body (proxy); `CumsAtTheWire.grantAnswersWithoutCode` switches to `respondWithStatus` (declared-status check).
- `services/agent-service/src/test/.../MiddayClock` (new test config) -- `@Primary` ticking clock anchored at 12:00 Europe/Zurich of a fixed day; `@Import` on `RunLimitsScenarios` and `AutonomyGateIntegrationTest`, with a positive check that the clock is the anchored one.
- `services/agent-service/src/test/.../gate/GateLedgerConcurrencyIntegrationTest` (new) -- test holds `MAIL_LOCK_KEY`, two threads call `decideAndRecord` for two runs/addresses, wait until both block in `pg_locks`, release; expect one RUN, one PENDING.
- `security/SecurityErrorWriter`, `exception/GlobalExceptionHandler` -- `Instant.now()` to the injected `Clock` (the decision log's clock).
- Docs: `docs/operations/runbooks.md` (first-deploy rollback note, post-deploy checks), `docs/architecture/module-map.md` / `06-backend-architecture.md` (AD-9 table-name rule: agent-service owns `agent_run*`, `agent_steps`, ... in `flyway_schema_history_agent`; CUMS owns `agent_settings`, `agent_grants`, `agent_allowlist`), `docs/guides/local-development-setup.md`, `.env.example`, plus the doc-consistency findings.
- Epic file `## Notes` -- `Recorded (refactor sweep, 2026-11-22):` lines.

## Tasks & Acceptance

**Execution:**
- [x] `scripts/dev/*` -- caller-secret pair in `.env.native.N` -- story 4 deferral
- [x] `.github/workflows/deploy-staging.yml` + jest test -- selective image check -- story 1 deferral (existing bug)
- [x] compose, Dockerfile.dev, gate files -- agent-service present -- story 1 deferral
- [x] CUMS internal spec + `CumsAtTheWire` -- declared proxy 5xx -- story 4 deferral
- [x] `MiddayClock` + two test classes -- fixed day -- story 8 deferral
- [x] `GateLedgerConcurrencyIntegrationTest` -- mail lock proven -- story 7 deferral
- [x] clock in error writers -- cleanup
- [x] docs + epic Notes -- consistency and records
- [x] full suites, one at a time

**Acceptance Criteria:**
- Given a fresh `make dev-native-up`, when CUMS and agent-service start, then both read the same caller secret and it survives `make dev-native-restart-service`.
- Given a selective deploy with only agent-service changed, when "Prepare deployment" runs, then it checks `batbern/staging/agent-service:<tag>` and no other repository.
- Given every deferred item, when the sweep ends, then each one is fixed here or has a `Recorded (refactor sweep, 2026-11-22):` line in the epic Notes.
- Given the touched modules, when their full suites run one at a time, then all pass.

## Implementation Notes

- 2026-11-22 (build, implementer subagent): every Execution task done. Additions outside the Code Map: `CumsAtTheWire.withClock(...)` (the gate test's 30 s mint expiry must run on the same anchored clock as the agent), `scripts/gate/lib.sh` generates `GATE_AGENT_CALLER_SECRET` per gate run, `/internal/agent-tokens` already declared 503 (`AGENT_SIGNING_KEY_MISSING`) so it gains only 502 and its 503 text covers the bodiless proxy answer, docs `native-execution.md` and `cicd-pipeline-guide.md` (doc-drift mapping for `.github/workflows/`). agent-service has only `agent_runs`, so the AD-9 rule lists the eight owned tables by name instead of an `agent_run*` prefix.
- Doc consistency check (Explore subagent): fixed module-map (verdicts and approvals are written by `GateLedger`, `agent_undos` has no writer until the console), 06 diagram label, 08 secret injection (CUMS and agent-service), `secrets-stack.ts` comment. 02 "not yet deployed" kept: its column is dated 2026-11-04. No README under services/agent-service; none added (the module map and 06 are the service's docs).
- Epic 17 rule: archunit store 0 at start and end (agent-service has none); test-ratchet unchanged (no RAISED, no STALE line).
- Verification 2026-11-22, one at a time: agent-service 226 tests 0 failures (run twice, the second after the review patches) + checkstyle main/test; api-gateway 338 (26 skipped) 0 failures; shared-kernel 817 0 failures; CUMS 1204 (15 skipped) 0 failures; CDK jest 885 passed, 19 skipped (51 suites, 2 skipped); frontend type-check green, api-lint test 42 passed, `npm run lint:api` 86 findings (baseline holds, exit 0); `make check-test-ratchet` green; every Bruno collection parsed with 0 "Skipping invalid file" (agent-api: 3 requests, local dev down, so they failed at connect).

## Plan Change Log

## Review Triage Log

Pass 1 (quick lens): high 0, medium 1, low 5, false 1, maybe-false 0.

| # | Finding | Verdict | Route | Evidence / action |
|---|---|---|---|---|
| 1 | Docs say a mismatch ends runs `CALLER_SECRET_MISSING`; CUMS answers `CALLER_SECRET_INVALID` | low | patch | `CumsAgentClient` L135 refuses only a blank own secret; CUMS 401 otherwise. Fixed in local-development-setup.md, .env.example, native-services.sh |
| 2 | Missing blank line before "To run fewer services" | low | patch | Added |
| 3 | Story 6 past-event-query deferral neither fixed nor recorded | low | patch | Recorded line added to the epic Notes |
| 4 | "epic 2 refinement" not a findable epic | low | patch | Now "epic 2 (epic-speaker-mail-agent)"; tickets.toml id 2 |
| 5 | Local runs still stop at the mint: no agent signing key in local dev | medium | patch (record) | Real (`AgentTokenSigner` needs `agent.jwt.private-key`). Generating a local key pair changes token handling in every local service (epic 0 area): documented in local-development-setup.md and recorded in the epic Notes |
| 6 | Plan has no verification evidence, boxes open | false | reject | Filled at close (Implementation Notes), the workflow's order |
| 7 | 06 diagram label names a table `agent_run` and omits three | low | patch | Label now "agent_runs and its decision log tables"; the paragraph lists all eight |
| 8 | Gate test inbound timestamp after the anchored day | low | patch | 2026-11-22 to 2026-11-17; agent-service suite rerun green |

Patches applied by the orchestrator directly (doc lines and one test literal), not by re-engaging the implementer.

## Design Notes

Architecture note: modules touched agent-service (tests, two error writers), CUMS internal spec (docs only, no CUMS code), deploy workflow, dev scripts, compose, docs. New dependencies none. New endpoints none (spec gains two statuses on existing operations). Components reused: `ContractServer.respondWithStatus`, `AbstractIntegrationTest`, the workflow-parsing jest pattern. Deviations none.

Epic 17 rule: agent-service has no archunit violations store (start 0, end 0); test-ratchet baseline unchanged (no own-code mocks added).

Triage (decided unattended; recorded in the epic Notes unless fixed):
- Fixed here: caller-secret pair, selective image check, compose/gate, spec 5xx, fixed clock, mail-lock concurrency test, AD-9 naming in docs, doc drift.
- First-deploy rollback label: documented, not fixed. A fix needs the deploy job's first-push list passed across jobs to `rollback-deployment.sh`; it happens at most once, only on a deploy that already failed its gate.
- Recorded (later epic / behaviour / owner): approved-call execution and repeat folding, approvals inbox (oversight console); principal kinds on tools (first non-public tool); precedent matching ignores input (epic 2 refinement); template path guard prefers no literal sibling (first tool with such a template); unhandled server-tool blocks and PAUSE_TURN (only with server tools); daily-budget overshoot by concurrent runs (by design); MODEL_UNAVAILABLE alarm (speaker-mail epic); unpaged inbound list; ErrorResponse on 401/403 in EMS/CUMS/PCS (cross-service behaviour change).
- Post-deploy checks (not testable before deploy): Bearer auth accepted by the real API, first model call after the key, CUMS-minted token through gateway on a real route, the three alarms fire, Bruno agent-api live, V1/V2 never deployed before.

## Verification

**Commands:**
- `./gradlew :services:agent-service:test --max-workers=4` -- full suite green
- `./gradlew :api-gateway:test --max-workers=4`, `:shared-kernel:test`, `:services:company-user-management-service:test` -- green (CUMS: spec changed, provider contract test reads it)
- `cd infrastructure && npx jest --maxWorkers=4` -- green
- `cd web-frontend && npm run type-check && npx vitest run scripts/api-lint.test.ts` -- green
- `make check-test-ratchet` -- no RAISED line
- `bru` parse of `bruno-tests/agent-api` -- no "Skipping invalid file"
