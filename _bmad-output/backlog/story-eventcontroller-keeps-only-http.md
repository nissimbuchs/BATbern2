---
id: 4
type: story
title: "EventController keeps only HTTP; event logic lives in services"
parent: none
covers: []
sources:
  - scripts/architecture/ems-module-target.py
  - _bmad-output/initiative-architecture-hardening/tickets/epic-ems-walls/epic-ems-walls.md
companions: []
after: []
refined: false
hitl: true
risk: medium
---

# EventController keeps only HTTP; event logic lives in services

## Description

Follow-up of Epic 17 (owner go 2026-11-15, from the close-out review). 17.13 took the registration
endpoints out of `EventController`; the controller still has 1703 lines for its 12 event endpoints,
because it carries business logic that reaches into six other EMS modules. It causes 16 of the 71
references the EMS module script still reports (measured 2026-11-15, script run on `develop`
74dee295e).

Measured (`ems-module-target.py`, references from `EventController` only):

| Pair | Refs | Where in `EventController` |
|---|---|---|
| event_core -> sessions | 5 | `getEvent` include expansion: `buildSessionMapBatch`, materials, `SessionService.toSessionResponse` |
| event_core -> publishing | 3 | `publicDetail` / `listPublicEvents`: `PublicEventShaper`, `PublicSessionVisibility*` |
| event_core -> registrations | 2 | capacity change in `updateEvent` / `patchEvent` calls `WaitlistPromotionService`; counts in `enrichWithRegistrationCounts`; delete check |
| event_core -> topics_ai | 2 | `expandTopic`, `selectTopicForEvent` |
| event_core -> media | 2 | `requireThemeImage`, `associateThemeImage`, `enrichWithTeaserImages` |
| event_core -> speakers | 1 | `expandMetricsToDTO` counts the speaker pool |
| event_core -> analytics | 1 | `getEventAnalytics` |

Direction, to be confirmed in the plan (`bmad-build`):
- the `include=` expansion (lines ~200-620: batch responses, session map, metrics, topic, venue) moves
  to one event read service that composes the other modules through their services;
- create, update and patch move to an event write service; the capacity change promotes from the
  waitlist inside the same transaction (same pattern as #1316);
- theme and teaser image handling moves behind the media module's service;
- the public view shaping stays in publishing and is called through one service method;
- analytics and topic selection either move to their own controllers implementing their generated
  interfaces (`EventReportingApi` is already a separate interface) or call one service each.

## Done when

- `EventController` holds no repository and no service of another EMS module; it calls event-core
  services only. An ArchUnit rule (not frozen, with fixture tests, like `EmsEndpointPlacementTest`)
  holds it.
- The module script shows no `EventController` reference, and the totals are recorded before and
  after (baseline 23 pairs / 71 references).
- Behaviour is unchanged: a capture test of every `EventController` response, written green on the
  current code first (the 17.14 pattern), is identical afterwards; full EMS suite, Bruno events
  collections and Playwright event flows on local dev green.
- Any write path touched gets its tests onto the integration harness (touch rule).

## Out

- The other 53 references (event core needs its sessions, workflow orchestration, mail core):
  accepted by the owner's 2026-11-14 decision against the EMS modularization.
