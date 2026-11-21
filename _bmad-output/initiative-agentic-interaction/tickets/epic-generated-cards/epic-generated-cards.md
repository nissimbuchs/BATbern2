---
type: epic
title: "A decision reaches a speaker as a one-tap card"
parent: initiative-agentic-interaction
covers: ["R4"]
sources: []
companions: []
after:
  - epic-agent-runtime
refined: false
risk: medium
---

# A decision reaches a speaker as a one-tap card

## Description

Opens with the A2UI spike (AD-16); then the BATbern catalog, the one Tailwind renderer, the card store with signed links, read-only GET and POST of the stored action, and DAST coverage of the card route.

## Outcome

Opens with the A2UI spike (AD-16). It is live in production and switched off until the owner switches it on.

## Done when

- The A2UI spike proved one custom component and one action round trip, and the proven protocol version is recorded in AD-16.
- A decision reaches a pilot speaker as a card behind a signed mail link; GET changes nothing, POST executes only the stored action with the declared input fields (R4).
- The renderer is Tailwind only and builds from the catalog JSON; agent-service rejects any message outside the catalog.
- The access-control DAST probe covers the card route.
- Deployed to production, verified with an organizer persona.

## Boundaries

- Out: console views (epic-oversight-console) and chat views (epic-brief-and-chat), which reuse the renderer.

## References

- parent — _bmad-output/initiative-agentic-interaction/initiative-agentic-interaction.md, Requirements R4
- architecture — _bmad-output/initiative-agentic-interaction/architecture-agentic-interaction/architecture-agentic-interaction.md, AD-11, AD-12, AD-16

## Notes

- Waits on epic-agent-runtime because: the gate and the decision log for card actions.
