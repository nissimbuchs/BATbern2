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
