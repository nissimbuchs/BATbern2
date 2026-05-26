# event-full-workflow-api

Bruno collection covering the EMS event workflow state machine end-to-end.
Section C of `docs/plans/bruno-staging-hardening.md`.

## What's in the run

Bruno orders by `seq:` in `meta { }`. The order below is the actual execution
order, not the filename order.

| seq | File | Purpose |
|-----|------|---------|
| 0 | `00-pretest-cleanup.bru` | BRUNO-TEST-* event sweep (no-op for BATbern* codes, safety net) |
| 0.5 | `00b-fixture-event.bru` | Event #1 — for the registrations seed tests |
| 0.6 | `00c-fixture-workflow-event.bru` | Event #2 — for the 8-state walk |
| 0.7 | `00d-fixture-invalid-transition-event.bru` | Event #3 — held in CREATED for rejection tests |
| 12 | `12-list-registrations.bru` | Seed: list registrations on event #1 |
| 13 | `13-create-registration.bru` | Seed: create a registration on event #1 |
| 13a | `13a-create-anonymous-registration.bru.disabled` | Seed (disabled) |
| 16 | `16-publish-event.bru` | Seed: publish event #1 via `/publish` shortcut (expects 200 or 422 since the event has no sessions) |
| 17.1 | `17a-advance-to-topic-selection.bru` | CREATED → TOPIC_SELECTION (override) |
| 17.2 | `17b-advance-to-speaker-identification.bru` | TOPIC_SELECTION → SPEAKER_IDENTIFICATION (override) |
| 17.3 | `17c-advance-to-slot-assignment.bru` | SPEAKER_IDENTIFICATION → SLOT_ASSIGNMENT (override) |
| 17.4 | `17d-advance-to-agenda-published.bru` | SLOT_ASSIGNMENT → AGENDA_PUBLISHED (override) |
| 17.5 | `17e-advance-to-event-live.bru` | AGENDA_PUBLISHED → EVENT_LIVE (override) |
| 17.6 | `17f-advance-to-event-completed.bru` | EVENT_LIVE → EVENT_COMPLETED (override) |
| 17.7 | `17g-advance-to-archived.bru` | EVENT_COMPLETED → ARCHIVED (override) |
| 18.1 | `18a-invalid-skip-to-archived.bru` | CREATED → ARCHIVED without override — 400/422 |
| 18.2 | `18b-invalid-skip-to-event-live.bru` | CREATED → EVENT_LIVE without override — 400/422 |
| 97.1 | `97a-delete-workflow-fixture-event.bru` | Cleanup event #2 (accepts 204/404/409) |
| 97.2 | `97b-delete-invalid-transition-fixture-event.bru` | Cleanup event #3 (accepts 204/404) |
| 98 | `98-delete-fixture-event.bru` | Cleanup event #1 |
| 99 | `99-posttest-cleanup.bru` | Safety net |

## Why three fixture events

The seed tests (12/13/16) mutate event #1 — registration is created and the
event is moved to PUBLISHED. The state-machine walk (17a→17g) advances
event #2 through all 8 states. The invalid-transition tests (18a/18b) need
an event held in CREATED to verify rejection. Trying to share fixtures
across these test paths would cause state-pollution failures, so each path
gets its own event.

## Override mode

The state-machine walk uses `overrideValidation: true` to bypass business
rules (needs-minimum-speakers for SLOT_ASSIGNMENT, etc.). Those rules are
exercised by their owning collections (`sessions-api`, `speaker-pool-api`).
The walk verifies state-machine wiring and ordering — that each transition
returns 200 with the correct `workflowState` in the response — not the
business-rule guards. The audit log captures every override at WARN level
per `EventWorkflowStateMachine.transitionToState`.

## Future scope

- Registrations CRUD against an event in `REGISTRATION_OPEN` state
  (PR 2c if needed) — exercise the auto-publishing → registration-open
  side of the workflow.
- Negative-path expansion for invalid transitions (e.g. backward jumps,
  same-state idempotency check).
