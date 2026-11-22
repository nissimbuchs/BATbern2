---
type: epic
title: "The console is the pilot organizers' home"
parent: initiative-agentic-interaction
covers: ["R8", "R9"]
sources: []
companions: []
after:
  - epic-generated-cards
refined: false
risk: medium
---

# The console is the pilot organizers' home

## Description

Activity feed, pending approvals executed under the organizer's own login, overrule and undo, as the home page of pilot organizers; replaced screens hidden for them.

## Outcome

Activity feed, pending approvals executed under the organizer's own login, overrule and undo, as the home page of pilot organizers. It is live in production and switched off until the owner switches it on.

## Done when

- Pilot organizers land on the console: activity feed, pending approvals, brief and chat placeholder (R8).
- An approval executes under the approving organizer's own login; overrule and undo of a reversible action work from the feed (R9).
- Screens the console replaces are hidden for pilot organizers and still reachable for everyone else (R8).
- No new organizer CRUD screen was added.
- Deployed to production; UI keys in all 10 locales.

## Boundaries

- Out: brief mail and chat answers (epic-brief-and-chat).
- Must not change: the organizer screens for organizers not on the allowlist.

## References

- parent — _bmad-output/initiative-agentic-interaction/initiative-agentic-interaction.md, Requirements R8, R9
- architecture — _bmad-output/initiative-agentic-interaction/architecture-agentic-interaction/architecture-agentic-interaction.md, AD-7, AD-9, AD-13, AD-15

## Notes

- Waits on epic-generated-cards because: the renderer for console views.
