---
type: epic
title: "agent-service runs a gated, logged agent loop in production"
parent: initiative-agentic-interaction
covers: ["R5", "R9"]
sources:
  - _bmad-output/initiative-agentic-interaction/initiative-agentic-interaction.md
  - _bmad-output/initiative-agentic-interaction/architecture-agentic-interaction/architecture-agentic-interaction.md
companions:
  - docs/architecture/module-map.md
after:
  - epic-agent-identity
refined: true
risk: high
---

# agent-service runs a gated, logged agent loop in production

## Description

The new Java service is deployed dark with its stack and secret: agent loop behind LlmPort with a scripted adapter for tests, tool registry with the operation class table, the autonomy gate with precedent retrieval from past events, the decision log, caps and budget, alarms, and the console read API.

## Outcome

The new Java service is deployed dark with its stack and secret: agent loop behind LlmPort with a scripted adapter for tests, tool registry with the operation class table, the autonomy gate with precedent retrieval from past events, the decision log, caps and budget, alarms, and the console read API. It is live in production and switched off until the owner switches it on.

## Requirements

- R1 (parent R5, AD-9, AD-14, Operational Envelope): agent-service runs in production as its own ECS service with its own CDK stack, ECR repository, Flyway history, CI build, local-dev start and gateway route `/api/v1/agent/**`, shipped dark (`agent.enabled` off); shown by the deployed service answering an organizer on `/api/v1/agent/runs` and refusing anonymous calls, and by the DAST probe covering the route.
- R2 (parent R5, AD-3, AD-15): agent-service acts only with a CUMS-minted delegated token: it obtains a run grant and mints through `/internal/agent-grants` and `/internal/agent-tokens` with its own caller secret, mints again before every write, and a refused grant or mint ends the run and hands it to the organizers; shown by an integration test per path against CUMS at the wire.
- R3 (parent R5, AD-14): only the agent loop calls the model, through one port `LlmPort`; the production adapter uses `anthropic-java` 3.12.0 with the key from Secrets Manager and the model id from configuration; with the key secret holding `none` the adapter is off and the service still starts; tests use a scripted adapter; shown by loop tests with the scripted adapter and the startup log naming the adapter and model.
- R4 (parent R5, AD-6): the agent's only capability is deterministic intent tools; one registry table gives the class of each public operation (`read` < `reversible` < `reply` < `binding` < `audience`); a tool's class is the highest of its operations, and the build fails for a tool declared lower or calling an operation missing from the table; each `reversible` tool names the operation that undoes it (parent R9); shown by the build check failing on a planted understated tool.
- R5 (parent R5, AD-7): one gate decides before every non-read call: `reversible` and `reply` run alone within the caps; `binding` runs alone only when confidence clears the threshold, else it becomes a pending approval; `audience` is always a pending approval; scheduled runs never send mail without approval; above one distinct non-organizer recipient per run or per day every further mail is `audience`; neither the model's statement nor the inbound text clears the threshold alone; shown by gate tests with the scripted adapter.
- R6 (parent R5, AD-8): precedents come only from past-event history read through `read` tools on the public API and from the decision log (approvals, denials, undos); every verdict cites the precedents it used; shown by a verdict whose cited precedents are the rows the test seeded.
- R7 (parent R9, AD-9, AD-14): every run, inbound message, model call (prompt, tool choice, tokens), step, tool call and gate verdict with its inputs and outcome is stored in agent-service's own `agent_*` tables, which no other service reads; shown by a scripted run whose every step is found in the tables.
- R8 (parent R9, AD-9): organizers read runs, steps, tool calls and verdicts with their precedents through `docs/api/agent-api.openapi.yml` at `/api/v1/agent/**`; a Cognito organizer token is accepted, any other role answers 403 and a token carrying `act` is refused; shown by integration tests per role.
- R9 (parent R5, Operational Envelope): per-run caps on steps, model tokens and `reply` mails and a daily token budget, all agent-service configuration; reaching one stops the run, logs which limit, and hands the run to the organizers; shown by a scripted run stopped at each cap.
- R10 (parent R5, Operational Envelope): CloudWatch alarms on run failures, budget breaches and refused mints follow the other services' pattern, and agent-service logs carry no personal data (#1202 rule); shown by the CDK tests and a log test.

## Constraints

- C1 (parent C2): epic 1 is one PR and one production deploy, shipped dark with the agent switch off.
- C2 (parent C4, Operational Envelope): no post-deploy test (Bruno, Playwright, nightly) reaches the real model or sends mail through the agent; the model is a process boundary, so the scripted adapter is within the mock rule.
- C3 (parent C3): no automatic mail to an audience; scheduled runs never send mail without approval.
- C4 (parent C6, C1): agent-service is Java 21 and Spring Boot 4.0.9 with its own loop on `anthropic-java` 3.12.0, no beta tool runner; the core services change only where the module map already names agent-service.

## Done when

- agent-service runs in production, switched off, with its own stack, secret, alarms and Flyway history, and answers an organizer on `/api/v1/agent/runs` through the gateway (R1, R10).
- The gate classifies tool calls by the operation class table and decides reversible, binding and audience as AD-7 says, proven with the scripted model adapter (R4, R5).
- Verdicts cite precedents read from past events through the public API with a CUMS-minted token (R2, R6).
- Every run, model call, tool call and verdict is in the decision log and readable through the console read API (R7, R8).
- Per-run caps and the daily budget stop a run and hand it to the organizers (R9).
- After the owner installs the key, the startup log names the Anthropic adapter and the configured model (R3).

## Boundaries

- Out: speaker tools and the inbound contract (epic-speaker-mail-agent), cards (epic-generated-cards), console UI, overrule and undo as organizer actions (epic-oversight-console).
- Out: an organizer dry-run trigger (later epic, owner 2026-11-22).

## References

- parent — _bmad-output/initiative-agentic-interaction/initiative-agentic-interaction.md, Requirements R5, R9
- architecture — _bmad-output/initiative-agentic-interaction/architecture-agentic-interaction/architecture-agentic-interaction.md, AD-6, AD-7, AD-8, AD-9, AD-14, Operational Envelope
- module map — docs/architecture/module-map.md (the agent service calls only CUMS `/internal/agent-*`, everything else through the public API)

## Notes

- Waits on epic-agent-identity because: delegated tokens and run grants from CUMS.
- Decision (2026-11-22): no organizer dry-run trigger in epic 1; it moves to a later epic, so epic 1 is proven by the scripted adapter and the deployed dark service.
- Decision (2026-11-22): CDK creates the Anthropic key secret holding `none` (adapter off); the owner puts the real key in by script after deploy.
- Decision (2026-11-22): default model `claude-sonnet-5-5`, as configuration.
- Decision (2026-11-22): starting caps 25 steps, 200k tokens and 2 `reply` mails per run, 2M tokens per day, all configuration.
- Decision (2026-11-22): a `binding` call clears the threshold with at least 2 matching approved precedents and no matching denial.
- Decision (2026-11-22): of parent R9, epic 1 owns seeing every action with its verdict (read API) and the undo operation per reversible tool; overrule and undo as organizer actions stay in epic-oversight-console.
- Decision (2026-11-22): the ok@ forward fix (SES v2, mail up to 40 MB) rides on this epic's branch as its own commit.
- Decision (2026-11-22): tracer bullet is story 1, the dark service through gateway, Flyway, CDK and the API; the least certain is story 3 (Anthropic adapter), which follows story 2 because it needs LlmPort; then the CUMS mint, tools, precedents, gate, caps, read API and alarms; a refactor sweep closes the epic; no closing end-to-end suite (C2: no post-deploy test reaches the model).
- Decision (2026-11-22): "handed to the organizers" in this epic is a run status shown in the read API and counted by the alarms; no mail to the organizers in this epic (that is epic-speaker-mail-agent).
- Assumption: a precedent matches a proposed call when it has the same tool and the same principal kind; the matching rule sits in one place in the gate so the owner can tighten it (set by Sofia 2026-11-22 under the owner's go to build, not asked).
