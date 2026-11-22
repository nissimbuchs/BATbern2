---
type: initiative
title: "Organizers and speakers work with BATbern by stating intent; the agent acts and asks only when unsure"
sources:
  - _bmad-output/initiative-agentic-interaction/architecture-agentic-interaction/architecture-agentic-interaction.md
companions:
  - _bmad-output/initiative-agentic-interaction/architecture-agentic-interaction/architecture-agentic-interaction.md
  - docs/architecture/module-map.md
after: []
risk: high
---

# Organizers and speakers work with BATbern by stating intent; the agent acts and asks only when unsure

## Description

BATbern has been a classic three-tier application for a year. This initiative pilots the next way of
working with it: **intent in, confirmation out**. A speaker writes a mail, an organizer asks in a
chat, and an agent carries out the work through the existing public API, acting as that person. It
learns from the history of past events and asks only when it is unsure. People see UI only when a
decision is needed: a one-tap card, or a view generated for one question. The organizer cockpit
shrinks to an oversight console. The core stays as it is. The architecture spine (companion) fixes
how all of this is built (AD-1 to AD-16). The owner decided it at the round-table of 2026-11-21.

## Outcome

At BATbern61 (2027-03-05), the organizers, playing speakers, have prepared their talks through mail
and cards alone, and have run the event from the console and the morning brief. The decision log
shows every agent action, and which ones it took alone and which it asked about.

## Requirements

- R1 (AD-3, AD-10, AD-15): the agent acts only as one named principal of an allowlisted user, with that principal's rights. Switching the agent off stops every agent action within one token lifetime, shown by a live switch-off during a run.
- R2 (AD-4): a pilot speaker mails an abstract and a photo to the agent address, and both land on their session with no organizer step. A mail that fails sender authentication, or comes from an ineligible sender, reaches the organizers as today.
- R3 (AD-5, AD-15): the pilot speaker gets the agent's answer in their profile language, inside their Mail Center conversation, and it is visible in the outbound log.
- R4 (AD-11, AD-12, AD-16): a decision that needs the speaker reaches them as a one-tap card behind a mail link. Opening the link changes nothing; confirming executes only the stored action.
- R5 (AD-6, AD-7, AD-8): the agent carries out reversible actions, and binding actions backed by precedent, on its own, and asks an organizer otherwise. A mail to more than one non-organizer always waits for approval. Every verdict names the past-event precedents it used.
- R6 (round-table, AD-13): organizers receive a morning brief mail for the current event that names what needs attention, each item with an action they can take from it.
- R7 (AD-11, AD-13): an organizer asks a question in chat and gets a generated view (table, chart, cards) built only from catalog components, and can act from it through the gate.
- R8 (AD-13): the oversight console (activity feed, pending approvals, brief, chat) is the pilot organizers' home page. Screens it replaces are hidden for them, and no new organizer CRUD screen is added.
- R9 (AD-7, AD-9): organizers see every agent action with its verdict, can overrule a pending or taken decision, and can undo a reversible action.
- R10 (spine Operational Envelope, round-table): the scripted personas (late speaker, wrong file, title change the day before, surprise co-speaker) each run end to end on production before the event, and their expected outcomes are checked against the decision log.
- R11 (round-table): the pilot runs live at BATbern61 (2027-03-05) with organizers as speakers, and a summary drawn from the decision log is ready to present there.

## Constraints

- C1 (AD-1): the core does not change: the event workflow, `SpeakerWorkflowService` as sole writer, ADR-008 authorization in the services, ADR-016 and the module map. The one named exception is AD-2.
- C2 (CLAUDE.md deployment rule, spine Operational Envelope): every PR to develop deploys to production. One epic is one PR where possible. agent-service ships switched off until the owner switches it on.
- C3 (#1179, AD-7): no automatic mail to an audience. The agent's scheduled runs never send mail without approval.
- C4 (spine Operational Envelope): no post-deploy test (Bruno, Playwright, nightly) reaches the real model or sends mail through the agent.
- C5 (owner 2026-11-21): personal data (name, email, bio, photo) may be processed by the agent. It is not a design constraint.
- C6 (owner 2026-11-21): agent-service is a Java Spring Boot service with its own loop on `anthropic-java`. The model is reached with an Anthropic API key. The generated UI speaks A2UI.
- C7 (CLAUDE.md localization): agent mails are first-class in DE and EN, and other locales fall back to EN. New UI keys go into all 10 locales.
- C8 (owner 2026-11-21, AD-10): only organizers are on the allowlist until the owner decides otherwise.

## Done when

- On production, each scripted persona has completed its scenario through mail and cards alone, with the outcome the scenario expects (R2-R5, R10).
- The pilot organizers have worked from the console, the brief and the chat for a full week without opening a hidden screen (R6-R9).
- Switching the agent off during a live run stopped it, and its mail went to the organizers (R1, R2).
- Every agent action of the pilot is in the decision log with its principal, verdict and precedents (R5, R9).
- The pilot ran at the next BATbern event, and its summary was presented there (R11).

## Boundaries

- Out: real speakers, partners or attendees as agent users. Partner tools. Deleting organizer screens. A vector store or fine-tuning. Voice and Watch channels. Changes to the existing OpenAI features in EMS.
- Touch point: shared-kernel security and api-gateway — the shared decoder and actor resolver. Owner: epic-agent-identity.
- Touch point: CUMS — minting, run grants, the switch, the allowlist and eligibility. Owner: epic-agent-identity.
- Touch point: PCS — adopts the shared decoder, with no other change. Owner: epic-agent-identity.
- Touch point: EMS forwarding and Mail Center — the agent alias kind, the `agent-inbound-mail` contract, and refusing agent tokens on audience sends. Owner: epic-speaker-mail-agent (the audience guard: epic-agent-identity).
- Touch point: infrastructure — the agent-service stack and its secrets. Owner: epic-agent-runtime.
- Must not change: forwarding to organizers for every mail the agent does not take.
- Tracer path: speaker mail → EMS alias, authenticated → contract → agent-service run as the requester → tool on the public API → delegate reply with a card link → speaker confirms the card.

## References

- architecture — `_bmad-output/initiative-agentic-interaction/architecture-agentic-interaction/architecture-agentic-interaction.md`, AD-1 to AD-16 and the Operational Envelope
- architecture — `docs/architecture/module-map.md`, services and the reuse table

## Notes

- Decision (2026-11-21): the pilot event is BATbern61 on 2027-03-05, topic "FinOps in the tension of cloud and AI". The freeze date is set in epic-pilot-run.
- Decision (2026-11-21): two further personal addresses of organizers (Nissim, Andy) act as pilot speakers. They are kept in the allowlist only, never in git.
- Decision (2026-11-21): the organizers decide whether the pilot is a success. There are no predefined measures.
- Assumption: Nissim is the organizer delegate (AD-15) until the owner names someone else.
