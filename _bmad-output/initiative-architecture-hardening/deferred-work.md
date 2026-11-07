- source_plan: `_bmad-output/initiative-architecture-hardening/tickets/epic-dead-surface-and-gates/tickets/story-frontend-path-contract-check-in-ci-plan.md`
  summary: CLAUDE.md (API Development) says CI checks "every frontend HTTP call"; the 17.3 check covers only the API client, axios and `*Client` receivers, not `fetch`.
  evidence: Review finding 6 of 17.3. The fix edits an agent-context file, so it is deferred to the owner; suggested wording "every frontend call through the API client, axios or a *Client". module-map.md and the CI guide were corrected in 17.3.
- source_plan: `_bmad-output/initiative-architecture-hardening/tickets/epic-dead-surface-and-gates/tickets/story-frontend-path-contract-check-in-ci-plan.md`
  summary: `GET /api/v1/config` (api-gateway ConfigController, fetched by web-frontend/src/config/runtime-config.ts:59) is in no OpenAPI spec.
  evidence: Pre-existing spec gap found by the 17.3 review (grep of docs/api for /config finds nothing). Fits 17.4 (Spectral) or a small spec addition; not caused by 17.3.
