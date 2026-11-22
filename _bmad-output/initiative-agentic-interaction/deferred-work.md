- source_plan: `_bmad-output/initiative-agentic-interaction/tickets/epic-agent-runtime/tickets/story-agent-service-runs-dark-in-production-behind-the-gateway-plan.md`
  summary: deploy-staging.yml "Prepare deployment" never image-checks a changed service on the selective path, because it greps the repo name (agent-service, event-management-service, ...) in CHANGED_SERVICES, which holds short names (agent, event-management, ...).
  evidence: Pre-existing for EMS, PCS and CUMS; agent-service inherits it. Only api-gateway is ever validated on that path.
- source_plan: `_bmad-output/initiative-agentic-interaction/tickets/epic-agent-runtime/tickets/story-agent-service-runs-dark-in-production-behind-the-gateway-plan.md`
  summary: Add a post-deploy Bruno check for GET /api/v1/agent/runs (organizer 200, speaker 403); story 9 owns the per-role read API tests.
  evidence: Today only the DAST probe (anonymous 401) and the smoke health check reach the deployed agent-service.
- source_plan: `_bmad-output/initiative-agentic-interaction/tickets/epic-agent-runtime/tickets/story-agent-service-runs-dark-in-production-behind-the-gateway-plan.md`
  summary: scripts/gate/build-images.sh and docker-compose.gate.yml (parked P5 spike) and docker-compose.yml do not include agent-service.
  evidence: Their gateway would route /api/v1/agent/** to a missing service.
- source_plan: `_bmad-output/initiative-agentic-interaction/tickets/epic-agent-runtime/tickets/story-agent-service-runs-dark-in-production-behind-the-gateway-plan.md`
  summary: AD-9 says agent_* tables belong to agent-service, but CUMS already owns agent_settings, agent_grants and agent_allowlist in the shared database.
  evidence: grep of CUMS migrations; agent-service names its tables agent_run* so far. The rule needs a sharper name convention before story 2 adds more tables.
- source_plan: `_bmad-output/initiative-agentic-interaction/tickets/epic-agent-runtime/tickets/story-agent-service-runs-dark-in-production-behind-the-gateway-plan.md`
  summary: First-deploy auto-rollback reports agent-service as failed when the post-deploy gate fails (no staging-stable, no previous task definition).
  evidence: rollback-deployment.sh fallback branch; rejected as low in review, recorded for the sweep.
- source_plan: `_bmad-output/initiative-agentic-interaction/tickets/epic-agent-runtime/tickets/story-the-decision-log-and-the-loop-run-a-scripted-model-end-to-en-plan.md`
  summary: AgentLoop maps stop reason PAUSE_TURN to MODEL_STOPPED (handed to the organizers); the Messages API means "send the response back to continue". Story 3 should either keep it (no server tools) or continue the turn.
  evidence: Review finding 5 of story 2; harmless while no server tools are offered, but the Anthropic adapter inherits it.
- source_plan: `_bmad-output/initiative-agentic-interaction/tickets/epic-agent-runtime/tickets/story-the-decision-log-and-the-loop-run-a-scripted-model-end-to-en-plan.md`
  summary: agent_runs.trigger is NOT NULL without default (V2); safe only because agent_runs has never had a writer and V1 has not been deployed. Confirm before the epic's one deploy that no other branch wrote runs.
  evidence: V2 ALTER TABLE adds a NOT NULL column; story 1 (V1) was never pushed.
- source_plan: `_bmad-output/initiative-agentic-interaction/tickets/epic-agent-runtime/tickets/story-the-decision-log-and-the-loop-run-a-scripted-model-end-to-en-plan.md`
  summary: ScriptedLlm lives in test sources; story 3 decides whether local dev needs a scripted adapter in main (Operational Envelope says local dev uses it unless a key is set).
  evidence: Plan decision of story 2; production default port is UnavailableLlm (always throws, run handed to the organizers).
- source_plan: `_bmad-output/initiative-agentic-interaction/tickets/epic-agent-runtime/tickets/story-the-anthropic-adapter-answers-through-llmport-with-the-key-f-plan.md`
  summary: anthropic-java 3.12.0 sends the key as `Authorization: Bearer`, not `x-api-key`; whether the real API accepts it is unverified until the owner installs the key.
  evidence: Measured in the contract test against a local server; C2 forbids a test against the real API. Settled by the first model call after the key script (no 401, startup log "Model adapter anthropic").
- source_plan: `_bmad-output/initiative-agentic-interaction/tickets/epic-agent-runtime/tickets/story-the-anthropic-adapter-answers-through-llmport-with-the-key-f-plan.md`
  summary: An unhandled response content block (server-tool blocks only) ends the call as MODEL_UNAVAILABLE and drops its billed usage from agent_model_calls; story 8's token caps would under-count it.
  evidence: Review finding 3 of story 3, rejected as unreachable today (no server tools declared). Revisit if a server tool is ever offered.
- source_plan: `_bmad-output/initiative-agentic-interaction/tickets/epic-agent-runtime/tickets/story-the-anthropic-adapter-answers-through-llmport-with-the-key-f-plan.md`
  summary: The contract fixture is hand-built from the documented Messages response shape, not recorded from the live API.
  evidence: Recording needs a key and a real call (C2). Re-record once the key exists, outside CI, if the owner wants byte-level fidelity.
- source_plan: `_bmad-output/initiative-agentic-interaction/tickets/epic-agent-runtime/tickets/story-agent-service-acts-with-a-cums-minted-token-minted-again-bef-plan.md`
  summary: Local dev runs of agent-service are all handed over with CALLER_SECRET_MISSING unless AGENT_CUMS_CALLER_SECRET equals CUMS's AGENT_CALLERS_AGENT_SERVICE_SECRET; start-all-native.sh sets neither.
  evidence: Both default to blank (agent-service application.yml, CUMS application.yml); .env.example documents the pair, the dev scripts do not generate one.
- source_plan: `_bmad-output/initiative-agentic-interaction/tickets/epic-agent-runtime/tickets/story-agent-service-acts-with-a-cums-minted-token-minted-again-bef-plan.md`
  summary: The grant call can return 5xx from Service Connect/Envoy (502/503) although cums-internal.openapi.yml declares no 5xx for /internal/agent-grants; agent-service maps it to HTTP_<status>, the spec does not say so.
  evidence: Test should_useHttpStatus_when_grantRefusalHasNoCode answers outside the declared statuses (harness declared-status check bypassed); CUMS behaviour unchanged by this story, so the spec was left alone.
- source_plan: `_bmad-output/initiative-agentic-interaction/tickets/epic-agent-runtime/tickets/story-agent-service-acts-with-a-cums-minted-token-minted-again-bef-plan.md`
  summary: No end-to-end proof that a CUMS-minted token passes the Spring gateway and a service on a real route from agent-service; the gateway call is proven against the companies spec only.
  evidence: Epic 0 tests accept agent tokens per service; story 4 tests stub CUMS and the gateway at the RestTemplate (ContractServer). Settled after deploy once the switch is on and a tool runs.
- source_plan: `_bmad-output/initiative-agentic-interaction/tickets/epic-agent-runtime/tickets/story-intent-tools-carry-a-class-derived-from-the-operation-table-plan.md`
  summary: Registry entries do not carry principal kinds yet (Consistency Conventions: "the registry entry carries the principal kind(s)"); every run is offered every tool. Add the kinds and filter by the run's grant principal with the first tool not open to every principal (epic-speaker-mail-agent).
  evidence: The three shipped tools call only permitAll GETs in EMS (SecurityConfig), so no principal is over-served today; agent-service does not yet read principalKind from the CUMS grant answer.
- source_plan: `_bmad-output/initiative-agentic-interaction/tickets/epic-agent-runtime/tickets/story-intent-tools-carry-a-class-derived-from-the-operation-table-plan.md`
  summary: PublicApi.limitedTo matches by path template, so a tool declaring GET /events/{eventCode} could also call a literal sibling such as GET /events/current; the ADR-003 argument checks of the tools are what stops it today.
  evidence: Operation.matches treats {x} as any one segment and does not resolve against the most specific spec path; no shipped tool declares a template with a literal sibling (checked against the three table entries).
