---
type: epic
title: "Organizers run the event from the brief and the chat"
parent: initiative-agentic-interaction
covers: ["R6", "R7"]
sources: []
companions: []
after:
  - epic-oversight-console
refined: false
risk: medium
---

# Organizers run the event from the brief and the chat

## Description

The morning brief mail for the current event with actions per item, and the organizer chat answering with generated views from the catalog that can act through the gate.

## Outcome

The morning brief mail for the current event with actions per item, and the organizer chat answering with generated views from the catalog that can act through the gate. It is live in production and switched off until the owner switches it on.

## Done when

- Pilot organizers receive a morning brief mail for BATbern61 naming what needs attention, each item with an action (R6).
- An organizer question in chat returns a generated view from catalog components only, and acting from it passes the gate (R7).
- Scheduled brief runs send no mail to non-organizers (C3).
- Deployed to production, verified by an organizer for a week.

## Boundaries

- Out: partner chat; voice.

## References

- parent — _bmad-output/initiative-agentic-interaction/initiative-agentic-interaction.md, Requirements R6, R7
- architecture — _bmad-output/initiative-agentic-interaction/architecture-agentic-interaction/architecture-agentic-interaction.md, AD-7, AD-11, AD-13

## Notes

- Waits on epic-oversight-console because: the console that hosts the chat and the approvals.
