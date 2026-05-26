# event-types-api (stub — PR 2a)

Net-new collection created by PR 2 (events-api split). Test content is
deferred to PR 2a per session decision 2026-05-26 (mechanical-only PR 2).

## Planned scope (per `docs/plans/bruno-staging-hardening.md` §C)

- `GET /event-types` — list seeded event types (PR 2a)
- `PUT /event-types/{id}` — idempotent update (PR 2a)

There is **no** create/delete endpoint for event_types — this is an API
gap, not a test-coverage gap. PR 2a's tests document the gap explicitly
and assert that POST/DELETE return 405 (or are absent from the contract).

## Cleanup

No `00-pretest-cleanup.bru` / `99-posttest-cleanup.bru` files yet — the
collection has no test fixtures to clean. PR 2a will add them only if it
introduces any mutating tests.

## Why this directory is empty today

Without the directory the runner's `--collection event-types-api` would
error. The directory + this README make the collection discoverable for
the PR 2a follow-up and signal scope to anyone browsing the tree.
