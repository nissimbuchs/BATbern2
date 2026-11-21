---
type: epic
title: "A pilot speaker prepares their talk by mail"
parent: initiative-agentic-interaction
covers: ["R2", "R3"]
sources: []
companions: []
after:
  - epic-agent-runtime
refined: false
risk: high
---

# A pilot speaker prepares their talk by mail

## Description

The agent alias kind, MailAuthentication check and agent-inbound-mail contract hand authenticated mail to agent-service; the speaker tools and the delegate reply put materials on the session and answer in the speaker's language; everything else is forwarded to organizers as today.

## Outcome

The agent alias kind, MailAuthentication check and agent-inbound-mail contract hand authenticated mail to agent-service. It is live in production and switched off until the owner switches it on.

## Done when

- A pilot speaker mails abstract and photo to the agent address and both land on their session with no organizer step (R2).
- A mail failing MailAuthentication, from an ineligible sender, or arriving with the switch off reaches the organizers as today (R2).
- The speaker gets the answer in their profile language in their Mail Center conversation, visible in the outbound log (R3).
- The agent never answers an Auto-Submitted mail or one of our own addresses.
- Deployed to production, verified with an organizer persona.

## Boundaries

- Out: cards (epic-generated-cards); partner mail.
- Must not change: forwarding of every other alias.

## References

- parent — _bmad-output/initiative-agentic-interaction/initiative-agentic-interaction.md, Requirements R2, R3
- architecture — _bmad-output/initiative-agentic-interaction/architecture-agentic-interaction/architecture-agentic-interaction.md, AD-4, AD-5, AD-15

## Notes

- Waits on epic-agent-runtime because: the loop, gate and decision log.
