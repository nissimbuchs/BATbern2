---
type: epic
title: "The pilot runs at the next event and is presented there"
parent: initiative-agentic-interaction
covers: ["R10", "R11"]
sources: []
companions: []
after:
  - epic-speaker-mail-agent
  - epic-brief-and-chat
refined: false
risk: medium
---

# The pilot runs at the next event and is presented there

## Description

Scripted personas with expected outcomes run on production, evaluation against the decision log, freeze, live run with organizers as speakers, and the summary for the stage.

## Outcome

Scripted personas with expected outcomes run on production, evaluation against the decision log, freeze, live run with organizers as speakers, and the summary for the stage. It is live in production and switched off until the owner switches it on.

## Done when

- Each scripted persona (late speaker, wrong file, title change the day before, surprise co-speaker) ran end to end on production and its expected outcome matches the decision log (R10).
- The switch was turned off during a live run and the mail reached the organizers (R1).
- The pilot ran at BATbern61 on 2027-03-05 with organizers as speakers, and the decision-log summary was presented (R11).
- The organizers recorded their decision on the pilot.

## Boundaries

- Out: opening the pilot to real speakers, which is an organizer decision after the event.

## References

- parent — _bmad-output/initiative-agentic-interaction/initiative-agentic-interaction.md, Requirements R10, R11
- architecture — _bmad-output/initiative-agentic-interaction/architecture-agentic-interaction/architecture-agentic-interaction.md, Operational Envelope, AD-10

## Notes

- Waits on epic-speaker-mail-agent because: the speaker path.
- Waits on epic-brief-and-chat because: the organizer path.
