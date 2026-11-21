---
type: epic
title: "Every service knows when the agent acts, and for whom"
parent: initiative-agentic-interaction
covers: ["R1"]
sources: []
companions: []
after: []
refined: false
risk: high
---

# Every service knows when the agent acts, and for whom

## Description

One shared-kernel decoder accepts Cognito, Watch and agent tokens and records agent:<run> for <username>; CUMS mints RS256 delegated tokens against run grants and owns the switch, the allowlist and the organizer delegate; gateway, CUMS, EMS and PCS adopt it; EMS audience sends refuse agent tokens; the module map names the one exception. Live, switched off.

## Outcome

One shared-kernel decoder accepts Cognito, Watch and agent tokens and records agent:<run> for <username>. It is live in production and switched off until the owner switches it on.

## Done when

- A delegated token minted by CUMS is accepted by the gateway, CUMS, EMS and PCS through the one shared-kernel decoder, and each records the actor as agent:<run> for <username> (R1).
- With the switch off or the user not on the allowlist, CUMS refuses every grant and token, proven by an integration test per refusal path (R1).
- EMS audience sends refuse any token carrying act (AD-15).
- The module map names the one /internal exception (AD-2).
- Deployed to production with the switch off; existing logins and the Watch app work unchanged.

## Boundaries

- Out: agent-service itself (epic-agent-runtime), the inbound alias (epic-speaker-mail-agent).
- Must not change: Cognito and Watch token validation for existing users.

## References

- parent — _bmad-output/initiative-agentic-interaction/initiative-agentic-interaction.md, Requirements R1
- architecture — _bmad-output/initiative-agentic-interaction/architecture-agentic-interaction/architecture-agentic-interaction.md, AD-2, AD-3, AD-10, AD-15
