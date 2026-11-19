---
title: 'The public event list says whether self-nomination is open'
type: 'bugfix'
ticket: '2'
created: '2026-11-19'
status: 'built'
baseline_revision: 'a74c6e631fb87293dc9c8b2320c8dea57e0696af'
route: 'oneshot'
route_source: 'auto'
risk: 'low'
review: 'quick'
review_source: 'pinned'
lenses_ran: ['quick']
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Since #1072 the homepage cards are fed by `GET /public/events?scope=upcoming`. Its items (`Event` schema) carry no `currentPublishedPhase`, so the frontend gate `canOfferSelfNomination` is always false and the self-nomination button has been invisible since 2026-10-07 (#1263 AC0).

**Approach:** One rule in one place. `Event.isSelfNominationOpen()` (topic set and published phase `topic`) on the entity is used by `SpeakerPoolService.selfNominate` (which enforces it) and by `PublicEventShaper`. The shaper sets a new `selfNominationOpen` boolean on every public list item and public detail. Spec first (`Event` schema, so `EventDetail` inherits it), then the types are regenerated. Organizer endpoints leave it unset (NON_NULL omits it). The frontend switches to the flag in story 3.3.

</frozen-after-approval>

## Implementation Notes

Oneshot: one entity method, one shaper line per shape, a spec property, regenerated types, and integration tests (about 60 lines). I chose a server-computed flag over adding `currentPublishedPhase` to the list. Otherwise the rule would live twice: in the backend guard and in the frontend gate.

## Verification

**Commands:**
- `./gradlew :services:event-management-service:test --tests '*PublicSessionVisibility*' --tests '*SpeakerPool*'` -- expected: the flag is true for a topic-phase event with a topic, and false for the speakers phase and for an event without a topic, on both the list and the detail
- full EMS suite, checkstyle, frontend type generation, tsc -- expected: green

## Review Triage Log

Quick review, 4 findings: 4 real (2 medium, 2 low), 0 false. All patched.
- medium, patch: no case separated the phase half of the rule. BATbern9060 now has a topic in the speakers phase and must be false.
- medium, patch: false was never asserted on the detail or on `/public/events/current`. Both are asserted now.
- low, patch: nothing pinned that organizer endpoints omit the flag. `doesNotExist` is now asserted on `/events/{code}` and `/events`.
- low, patch: the spec description named `/public/events/*` only. It now names the list too and says the flag is false for past events.
Also: 24 EventController capture goldens updated. The only difference is the new `selfNominationOpen` field.
