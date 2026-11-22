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
