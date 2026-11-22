---
title: 'The Anthropic adapter answers through LlmPort with the key from Secrets Manager'
type: 'feature'
ticket: '3'
created: '2026-11-22'
status: 'built'
baseline_revision: '2bde3179543f5d0d846e60e2d1ade20b602a4ea2'
route: 'full'
route_source: 'auto'
risk: 'high'
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

**Problem:** `LlmPort` (story 2) has only `UnavailableLlm`, so every run is handed over; there is no production model adapter, no key secret and no way for the owner to install a key (R3, AD-14).

**Approach:** An `AnthropicLlm` adapter on `com.anthropic:anthropic-java` 3.12.0 (non-beta `client.messages()`, own loop, no tool runner) maps `LlmRequest`/`LlmResponse` 1:1. A config class picks the port at startup: key set → Anthropic; no key and profile `local` → a canned local adapter; otherwise `UnavailableLlm`. CDK adds the key secret (placeholder `none`) to agent-service only; a script lets the owner write the real key and restart the tasks.

## Boundaries & Constraints

**Always:** Key and model id are configuration (`agent.anthropic.*`, key from env `AGENT_ANTHROPIC_API_KEY`, model default `claude-sonnet-5-5`). Blank or `none` (trimmed, any case) means no key. The key never appears in a log, a `toString`, an exception message or test output. Startup logs the adapter and model once. Every SDK failure becomes `LlmUnavailableException` whose message carries status and error type only, never the response body. Thinking blocks the model returns go back unchanged on the next request. Tests never reach the real API (C2): contract test against a local JDK `HttpServer` serving a fixture in the documented Messages response shape.

**Never:** Beta endpoint, tool runner, server tools, server-side refusal fallback, streaming. CUMS caller secret (story 4). Caps, budget (story 8). Alarms (story 10). Running the key script. Push, PR.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Tool use round trip | fixture: thinking + text + tool_use, stop tool_use, usage incl. cache fields | `LlmResponse` with blocks in order, TOOL_USE, 4 token counts, model id; request body carries model, system, max_tokens, tools with input_schema, messages with tool_use/tool_result/is_error and the thinking block | none |
| Stop reasons | end_turn, max_tokens, stop_sequence, pause_turn, refusal, an unknown value | mapped enum; unknown → UNKNOWN | none |
| API error | HTTP 400/401/429/500/529 | `LlmUnavailableException` with status + type, no body text | SDK retries 429/5xx per config, then throws |
| Unreachable | connection refused / timeout | `LlmUnavailableException` | none |
| Key `none` / blank | production profile | `UnavailableLlm`, startup log "adapter none" | service starts |
| Key `none`, profile local | local dev | local canned adapter, end_turn | none |
| Key set | any profile | `AnthropicLlm`, log names adapter + model, not the key | none |

</frozen-after-approval>

## Code Map

- `services/agent-service/build.gradle` -- add `implementation 'com.anthropic:anthropic-java:3.12.0'` (pulls Jackson 2.19 + OkHttp; check no conflict with Spring Boot 4 managed versions).
- `.../agent/loop/LlmContent.java` -- sealed; add `Thinking(thinking, signature)` and `RedactedThinking(data)` so the loop's `LlmMessage.assistant(response.content())` round-trips them. Only `log/LlmJson.block` switches over it in main.
- `.../agent/loop/UnavailableLlm.java` -- drop `@Component`; becomes a bean chosen by config. `ScriptedLlm` (test, `@Primary @Component`) still wins in tests.
- `.../agent/loop/AgentLoop.java` -- stop-reason switch: PAUSE_TURN, STOP_SEQUENCE, REFUSAL, UNKNOWN stay MODEL_STOPPED (decision below); add comment. No schema change (end_reason check constraint in V2 is frozen).
- `.../agent/config/AgentProperties.java` -- add nested `Anthropic(apiKey, model, effort, timeout, maxRetries, baseUrl)` with masked `toString`; raise `loop.maxOutputTokens` default 4096→16000 (adaptive thinking spends output tokens; Anthropic's non-streaming default).
- `.../agent/config/AgentSwitchLog.java` -- pattern for the startup log (ApplicationReadyEvent).
- `src/main/resources/application.yml` -- `agent.anthropic.*` from env.
- Infra: `infrastructure/lib/stacks/secrets-stack.ts` (placeholder pattern of `AgentSigningPrivateKey`, AWS-managed key), `agent-service-stack.ts` (`additionalSecrets` like CUMS), `bin/batbern-infrastructure.ts` (pass the secret), tests `test/unit/agent-service-stack.test.ts` (flip the story-1 "no ANTHROPIC" assertion), `secrets-stack.test.ts` (resource count 5→6), new `test/unit/anthropic-api-key.test.ts` modelled on `agent-signing-key.test.ts` (`synthStagingApp`, `referencedBy`).
- Script model: `scripts/deploy/create-agent-signing-key.sh` (profile forcing, placeholder check, `--force`, `file://` from umask-077 temp dir).
- Egress: domain-service SG already allows 443 to any IPv4 via NAT (checked) — no infra change.
- Docs/mappings: module-map, 06-backend-architecture, 08-operations-security, 02-infrastructure-deployment, `docs/operations/runbooks.md` (new section after "Agent signing key"), `.github/doc-drift-mappings.yml` (new script → runbooks), `.env.example` (commented key line).

## Tasks & Acceptance

**Execution:**
- [x] `services/agent-service/build.gradle` -- SDK dependency.
- [x] `.../loop/LlmContent.java`, `.../log/LlmJson.java` -- thinking variants and their log JSON.
- [x] `.../llm/AnthropicLlm.java` -- adapter: build `MessageCreateParams` (model, system, maxTokens, tools, messages, `OutputConfig` effort), map response, map exceptions.
- [x] `.../llm/LocalDevLlm.java` -- canned end_turn answer, model `local-scripted`, zero usage.
- [x] `.../llm/LlmConfig.java` -- `@Bean LlmPort` selection + startup log line `Model adapter {anthropic|local-scripted|none}, model {id}`.
- [x] `.../loop/UnavailableLlm.java`, `AgentLoop.java`, `config/AgentProperties.java`, `application.yml` -- as Code Map.
- [x] Tests: `llm/AnthropicLlmContractTest` (JDK HttpServer, fixture under `src/test/resources/anthropic/`; request body assertions, every matrix row), `llm/LlmConfigTest` (selection per key/profile, startup log names adapter+model and not the key, positive control), `config/AgentPropertiesTest` (masked toString), `loop/AgentLoopIntegrationTest` (thinking block returned to the model on step 2).
- [x] Infra + tests as Code Map.
- [x] `scripts/deploy/put-anthropic-api-key.sh` -- key from stdin (`read -rs` on a tty), never echoed, `none`-check, `--force`, `--dry-run`, `--no-restart`; writes via `file://`; restarts the agent service (`ecs update-service --force-new-deployment`); prints the log check.
- [x] Docs as Code Map.

**Acceptance Criteria:**
- Given the recorded-shape fixture, when `AnthropicLlm.complete` runs against the local server, then tool use, thinking and the four token counts arrive through `LlmPort` and the request body is the Messages API shape.
- Given `npx jest` on the touched CDK tests, when it runs, then the key secret is referenced by the agent-service template only and injected as `AGENT_ANTHROPIC_API_KEY`.
- Given `:services:agent-service:test`, when it runs, then every test passes and no test opens a connection to api.anthropic.com.

## Implementation Notes

- Gates (unattended, owner go for the epic): one goal (adapter + its secret; neither ships alone). Plan ~1,700 tokens, kept whole. No open questions; checkpoint approved without a human per the owner's go.
- SDK names (anthropic-java 3.12.0, non-beta `com.anthropic.models.messages`): verify every class and builder method against the downloaded jar (`jar tf` / `javap`), never from memory; Sonnet 5.5 rejects `thinking: disabled` and forced `tool_choice` any/tool, so send neither. Do not commit; the orchestrator commits. Do not run the owner key script against AWS (only `--dry-run`).
- Rack rules: one heavy build at a time; Gradle `--max-workers=4`, then `./gradlew --stop`; output via `set -o pipefail; ... | tee ~/.cache/batbern-test-logs/<name>.log`.

## Plan Change Log

- 2026-11-22 (build): anthropic-java 3.12.0 sends the API key as `Authorization: Bearer <key>`
  (`AnthropicBackend.Credential.ApiKey` -> `StaticAuthHeaders`), not `x-api-key`; the contract test
  asserts the bearer header as measured. Not verified against the real API (C2, no key).
- 2026-11-22 (build): the SDK's `model_context_window_exceeded` stop reason maps to UNKNOWN
  (MODEL_STOPPED), like any value the port does not name.
- 2026-11-22 (build): a response block the adapter does not handle (only server-tool blocks, none
  declared) raises `LlmUnavailableException` instead of being dropped, which would break the
  append-only history.
- 2026-11-22 (build): the startup line is logged from the `@Bean` factory in `LlmConfig` (once per
  context) rather than on ApplicationReadyEvent, so `LlmConfigTest` can assert it through
  `ApplicationContextRunner`.
- 2026-11-22 (build): `docs/architecture/tech-stack.md` gets the anthropic-java row (the "Stack
  table" of the design note); `application-test.yml` pins `agent.anthropic.api-key: none` so a key
  in a developer's environment never builds a client in a test.

## Review Triage Log

Pass 1 (quick lens): high 0, medium 1, low 3, false 0, maybe-false 0.

| # | Finding | Verdict | Route | Evidence / action |
|---|---|---|---|---|
| 1 | `AnthropicLlm.complete`: response mapping outside the try; a lazily validated field throws `AnthropicInvalidDataException`, the run ends FAILED and the received value reaches `agent_model_calls` | medium | patch | SDK validates `JsonField` on read; `AgentLoop` catches it as RuntimeException and `failModelCall` stores the message. Mapping moved inside the try; class name only |
| 2 | Contract test lacks a malformed-200 case | low | patch | Same root as 1; test added |
| 3 | Unhandled content block: billed answer logged as a failed call, usage lost, end reason MODEL_UNAVAILABLE | low | reject | Only server-tool blocks can arrive and agent-service declares none, so it cannot occur in use; the fix adds a branch. Recorded here |
| 4 | `agent-service-stack.test.ts`: `not.toContain('CALLERS')` checks nothing | low | patch | Direct correction: assert exactly one ANTHROPIC-named entry on the container |

Also added after review (same class as the signing-key script test): `infrastructure/test/unit/anthropic-api-key-script.test.ts`, 13 cases, stub `aws`.

Verification after patches (2026-11-22): `:services:agent-service:test` 69 tests, 0 failures; CDK jest 7 suites (agent-service-stack, secrets-stack, anthropic-api-key, anthropic-api-key-script, agent-signing-key, agent-signing-key-script, domain-event-listener-alarm) 67 tests, 0 failures; `tsc --noEmit` clean; `make check-test-ratchet` no RAISED/STALE. shellcheck not installed on rack.

## Design Notes

Architecture note: modules touched — agent-service (`llm` new package for adapters, `loop`, `log`, `config`), infrastructure (SecretsStack, AgentServiceStack, bin) · new dependencies — `com.anthropic:anthropic-java` 3.12.0 (Stack table, AD-14) · new endpoints — none (outbound only, to api.anthropic.com) · reused — `AgentProperties`, startup-log pattern of `AgentSwitchLog`, placeholder-secret pattern and script shape of the agent signing key, `synthStagingApp` · deviation — none; the key is in Secrets Manager only (Consistency Conventions).

Decisions (taken unattended, recorded here):
- Port selection in one `@Configuration`, not `@ConditionalOnProperty` spread over classes: one place states the rule and logs it.
- Local dev without a key gets `LocalDevLlm` (canned end_turn), not `UnavailableLlm`, so the loop can be clicked through locally (Operational Envelope "scripted adapter"); production without a key keeps `UnavailableLlm` (runs handed over). It lives in main because local dev has no test classpath.
- PAUSE_TURN: agent-service declares no server tools, so the API does not pause; the loop keeps it as MODEL_STOPPED (handed over) rather than resuming blind. STOP_SEQUENCE (no stop sequences sent), REFUSAL and UNKNOWN: MODEL_STOPPED; the exact stop reason is already in `agent_model_calls`. A distinct end reason would need a migration; not worth it now.
- No server-side refusal fallback: a refusal goes to the organizers, and the logged model stays the configured one (deterministic, auditable). The fallback model could not read Sonnet 5.5 thinking blocks anyway.
- Thinking: adaptive (the model's default, not sent), effort from config, default `medium` (Anthropic's starting point for multistep tool use on Sonnet 5.5). Thinking blocks are kept in the port so the history stays append-only (preserved-thinking check).
- SDK retries (default 2, 429/5xx/connection) stay on as transport behaviour; the loop itself still never retries. Timeout default 5 min.
- Contract fixture is hand-built from the documented response shape: recording against the real API is forbidden (C2) and there is no key.

Epic 17 rule: archunit/violations store 0 at start and end (new service); test-ratchet baseline unchanged expected.

## Verification

**Commands:**
- `set -o pipefail; ./gradlew --max-workers=4 :services:agent-service:test 2>&1 | tee ~/.cache/batbern-test-logs/agent-service-test-1-3.log` -- BUILD SUCCESSFUL; then `./gradlew --stop`
- `set -o pipefail; cd infrastructure && npx jest test/unit/agent-service-stack.test.ts test/unit/secrets-stack.test.ts test/unit/anthropic-api-key.test.ts test/unit/agent-signing-key.test.ts 2>&1 | tee ~/.cache/batbern-test-logs/cdk-1-3.log` -- all pass
- `bash -n scripts/deploy/put-anthropic-api-key.sh && shellcheck scripts/deploy/put-anthropic-api-key.sh` (if installed); `--dry-run` run with a fake key on stdin shows no key in output
- `make check-test-ratchet` -- no RAISED line
