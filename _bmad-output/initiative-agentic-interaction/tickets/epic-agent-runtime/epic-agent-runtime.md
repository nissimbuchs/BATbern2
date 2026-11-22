---
type: epic
title: "agent-service runs a gated, logged agent loop in production"
parent: initiative-agentic-interaction
covers: ["R5", "R9"]
sources: []
companions: []
after:
  - epic-agent-identity
refined: false
risk: high
---

# agent-service runs a gated, logged agent loop in production

## Description

The new Java service is deployed dark with its stack and secret: agent loop behind LlmPort with a scripted adapter for tests, tool registry with the operation class table, the autonomy gate with precedent retrieval from past events, the decision log, caps and budget, alarms, and the console read API.

## Outcome

The new Java service is deployed dark with its stack and secret: agent loop behind LlmPort with a scripted adapter for tests, tool registry with the operation class table, the autonomy gate with precedent retrieval from past events, the decision log, caps and budget, alarms, and the console read API. It is live in production and switched off until the owner switches it on.

## Done when

- agent-service runs in production, switched off, with its own stack, secret, alarms and Flyway history.
- The gate classifies tool calls by the operation class table and decides reversible, binding and audience as AD-7 says, proven with the scripted model adapter (R5).
- Verdicts cite precedents read from past events through the public API (R5).
- Every run, model call, tool call and verdict is in the decision log and readable through the console read API (R9).
- Per-run caps and the daily budget stop a run and hand it to the organizers.

## Boundaries

- Out: speaker tools and the inbound contract (epic-speaker-mail-agent), cards (epic-generated-cards), console UI (epic-oversight-console).

## References

- parent — _bmad-output/initiative-agentic-interaction/initiative-agentic-interaction.md, Requirements R5, R9
- architecture — _bmad-output/initiative-agentic-interaction/architecture-agentic-interaction/architecture-agentic-interaction.md, AD-6, AD-7, AD-8, AD-9, AD-14, Operational Envelope

## Notes

- Waits on epic-agent-identity because: delegated tokens and run grants from CUMS.
