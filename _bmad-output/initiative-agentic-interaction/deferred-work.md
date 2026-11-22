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
- source_plan: `_bmad-output/initiative-agentic-interaction/tickets/epic-agent-runtime/tickets/story-precedents-are-retrieved-from-past-events-and-the-decision-l-plan.md`
  summary: Partly settles the principal-kind entry of story 5: agent-service now reads principalKind from the CUMS grant answer and stores it on agent_runs; registry entries still carry no kinds and every run is still offered every tool (epic-speaker-mail-agent).
  evidence: Story 6 needed the run's kind for precedent matching (V4, RunCredentials.principalKind); tool filtering was out of scope.
- source_plan: `_bmad-output/initiative-agentic-interaction/tickets/epic-agent-runtime/tickets/story-precedents-are-retrieved-from-past-events-and-the-decision-l-plan.md`
  summary: No shipped tool declares a past-event query yet (the epic ships read tools only); the first non-read tool (epic-speaker-mail-agent) decides what past events it is judged against.
  evidence: AgentTool.pastEventQuery defaults to empty; proven with a test-only fixture tool.
- source_plan: `_bmad-output/initiative-agentic-interaction/tickets/epic-agent-runtime/tickets/story-one-autonomy-gate-decides-every-non-read-call-and-cites-its-plan.md`
  summary: Nothing executes an approved call yet, and a model that proposes the same held call again gets a second PENDING approval; the console (epic-oversight-console) decides how an approved call runs (re-gated or not) and whether repeats are folded into one approval.
  evidence: Story 7 writes PENDING approvals only; the loop tells the model not to repeat, but nothing enforces it beyond the step cap.
- source_plan: `_bmad-output/initiative-agentic-interaction/tickets/epic-agent-runtime/tickets/story-one-autonomy-gate-decides-every-non-read-call-and-cites-its-plan.md`
  summary: The matching rule (same tool, same principal kind) ignores the call's input, so two approvals of a binding tool for one case clear it for any other case; tighten PrecedentMatchRule before the first real binding mail tool (epic-speaker-mail-agent).
  evidence: Epic assumption set by Sofia, not asked; with read tools only nothing is cleared in production today.
- source_plan: `_bmad-output/initiative-agentic-interaction/tickets/epic-agent-runtime/tickets/story-one-autonomy-gate-decides-every-non-read-call-and-cites-its-plan.md`
  summary: The advisory lock that serializes mail verdicts across concurrent runs (GateLedger) has no concurrency test.
  evidence: All gate tests run one run at a time; a two-thread test would settle that two concurrent first mails to different people cannot both run alone.
- source_plan: `_bmad-output/initiative-agentic-interaction/tickets/epic-agent-runtime/tickets/story-one-autonomy-gate-decides-every-non-read-call-and-cites-its-plan.md`
  summary: GateRules.REQUIRED_APPROVALS (2) and the one-recipient limit are constants, not configuration like story 8's caps.
  evidence: Owner fixed the values 2026-11-22; changing them is a code change today.
- source_plan: `_bmad-output/initiative-agentic-interaction/tickets/epic-agent-runtime/tickets/story-caps-and-the-daily-budget-stop-a-run-and-hand-it-to-the-orga-plan.md`
  summary: Resolved by story 8, not open: the story-7 entry above (GateRules constants) is settled, both values are now agent.limits.binding-approvals and agent.limits.non-organizer-recipients.
  evidence: GateRules.decide takes GateRules.Thresholds from AgentProperties.Limits; existing entries are append-only, so this line records the closure.
- source_plan: `_bmad-output/initiative-agentic-interaction/tickets/epic-agent-runtime/tickets/story-caps-and-the-daily-budget-stop-a-run-and-hand-it-to-the-orga-plan.md`
  summary: The daily budget can be overshot by model calls in flight in concurrent runs (each run checks before its call, no lock across runs); there is no concurrency test, and story 10's budget alarm should filter end_detail DAILY_TOKENS.
  evidence: Accepted by design (checked before each call, documented in 06-backend-architecture); a two-run test would show the overshoot is at most one call per concurrent run.
- source_plan: `_bmad-output/initiative-agentic-interaction/tickets/epic-agent-runtime/tickets/story-caps-and-the-daily-budget-stop-a-run-and-hand-it-to-the-orga-plan.md`
  summary: Day-boundary tests (daily budget, gate per-day count) use the real system clock, so a run across midnight Europe/Zurich could flake.
  evidence: Review finding on RunLimitsScenarios.should_stop_when_dailyBudgetReached; the window is the test's seconds around midnight; a fixed test Clock bean would settle it.
- source_plan: `_bmad-output/initiative-agentic-interaction/tickets/epic-agent-runtime/tickets/story-organizers-read-every-run-step-and-verdict-through-the-conso-plan.md`
  summary: Resolved by story 9, not open: the story-1 entry "Add a post-deploy Bruno check for GET /api/v1/agent/runs (organizer 200, speaker 403)" is delivered by bruno-tests/agent-api (01 organizer 200, 02 speaker 403, 03 unknown run 404), registered as a production collection.
  evidence: Entries are append-only, so this line records the closure. The collection was parse-checked with bru (no "Skipping invalid file"); it has not run against a live service yet (local dev was down, staging runs old code until the epic's deploy).
- source_plan: `_bmad-output/initiative-agentic-interaction/tickets/epic-agent-runtime/tickets/story-organizers-read-every-run-step-and-verdict-through-the-conso-plan.md`
  summary: No cross-run approvals inbox (GET of every PENDING approval across runs); pending approvals are readable per run only (embedded in each verdict, counted in the run detail). epic-oversight-console designs the inbox with its approve and deny actions.
  evidence: Plan decision of story 9; the actions on approvals are out of epic 1 (epic Boundaries).
- source_plan: `_bmad-output/initiative-agentic-interaction/tickets/epic-agent-runtime/tickets/story-organizers-read-every-run-step-and-verdict-through-the-conso-plan.md`
  summary: The run detail returns all inbound messages of a run unpaged; fine while a run gets one message, revisit if a chat run ever holds many.
  evidence: RunLogReadService.inboundMessages reads every row of the run; sub-collections are paged, this list is not.
- source_plan: `_bmad-output/initiative-agentic-interaction/tickets/epic-agent-runtime/tickets/story-organizers-read-every-run-step-and-verdict-through-the-conso-plan.md`
  summary: Only agent-service answers 401/403 with the shared ErrorResponse (security/SecurityErrorWriter); EMS, CUMS and PCS still answer filter-chain refusals with an empty body although their specs declare ErrorResponse.
  evidence: grep finds no AuthenticationEntryPoint or AccessDeniedHandler in the other services (2026-11-22); EMS maps only method-level AccessDeniedException in its advice.
- source_plan: `_bmad-output/initiative-agentic-interaction/tickets/epic-agent-runtime/tickets/story-alarms-watch-run-failures-budget-breaches-and-refused-mints-plan.md`
  summary: A run handed to the organizers because the model was unreachable (MODEL_UNAVAILABLE) has no marker and no alarm; revisit once epic-speaker-mail-agent tells organizers about handed-over runs, or if a sustained Anthropic outage should open an issue (e.g. 3+ in an hour).
  evidence: Story 10 decision: until the owner installs the key every run ends MODEL_UNAVAILABLE, and an outage is external; in epic 1 a handed-over run is only a status in the read API, so an outage stays silent until someone looks.
- source_plan: `_bmad-output/initiative-agentic-interaction/tickets/epic-agent-runtime/tickets/story-alarms-watch-run-failures-budget-breaches-and-refused-mints-plan.md`
  summary: The three agent-run alarms have never fired against real CloudWatch: the literal filters are proven only by synthesis tests (markers read from Java), not by a live log line in the agent log group.
  evidence: The service is dark and not yet deployed with story 10; after the epic's deploy, a `aws logs put-log-events`-free check is to switch the agent on with no caller secret once (every run GRANT_REFUSED, CALLER_SECRET_MISSING) and watch Agent-Refused-Mints go to ALARM, or use `aws logs test-metric-filter` with a sample line.
