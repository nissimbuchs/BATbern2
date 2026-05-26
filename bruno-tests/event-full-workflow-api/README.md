# event-full-workflow-api (seed + stub — PR 2b)

Net-new collection created by PR 2 (events-api split). Seeded with three
tests from the old `events-api/` directory:

- `12-list-registrations.bru`
- `13-create-registration.bru`
- `13a-create-anonymous-registration.bru.disabled`
- `16-publish-event.bru`

Plus the canonical `00-pretest-cleanup.bru` + `99-posttest-cleanup.bru`
hooks (BRUNO-TEST-* event sweep — cascade clears registrations).

## Planned scope (per `docs/plans/bruno-staging-hardening.md` §C)

PR 2b will add net-new tests that advance an event through every workflow
state in order:

```
CREATED → TOPIC_SELECTION → SPEAKER_IDENTIFICATION → SLOT_ASSIGNMENT
→ AGENDA_PUBLISHED → EVENT_LIVE → EVENT_COMPLETED → ARCHIVED
```

Plus full registrations CRUD against an event in `AGENDA_PUBLISHED` or
later. This is its own design problem (8 transitions × at-least-one
test each + happy path + invalid-transition rejections) and was carved
out of PR 2's mechanical scope on 2026-05-26.

## Today's state

Auth: organizer. The 3 seeded tests run with the existing seq numbers
(12, 13, 16) so they execute in order between the pretest (seq 0) and
posttest (seq 99) hooks.
