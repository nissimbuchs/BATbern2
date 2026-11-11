---
title: 'Refactor sweep'
type: 'refactor'
ticket: 'sweep'
created: '2026-11-11'
status: 'in-progress'
baseline_revision: '92abe9cbdd3e13c098ad1add4cb57648843c9fe8'
route: 'full'
route_source: 'auto'
risk: 'medium'
review: ''
review_source: ''
lenses_ran: []
review_loop_iteration: 0
context:
  - '{project-root}/docs/architecture/ADR-016-service-directions-and-service-to-service-calls.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

Owner go 2026-11-11 (overnight queue 7b → 8b → sweep → PR; decide with a recommendation instead of asking).

**Problem:** The epic's stories left three findings open (`deferred-work.md` and review triage logs of 7 and 7b): a failed request filing can leave an orphan Mail Center conversation; PCS records no merge log, so a merge cannot be undone by hand for its three columns; a Mail Center decision stops at the first failing implementer although every implementer should be told.

**Approach:** Close all three, each with a test: a compensating "withdrawn" decision when the CUMS filing rolls back after the open went out; a PCS merge log with the moved row ids; decisions sent with `ContractCalls.postToEvery`. Everything else the epic left is listed below as checked and closed, or moved.

## Boundaries & Constraints

**Always:** Same branch, local commits, no push until the whole queue is done (then the owner's go covers push and one PR). One heavy job at a time on rack; logs under `~/.cache/batbern-test-logs/sweep/`. New migrations only. Tests on real PostgreSQL, `ContractServer` at the wire. Spec first for the contract change.

**Never:** No outbox, no retry job, no polling (owner 2026-11-11). No production data change. No change to `mailcenter.notify-instead-of-forward`.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Filing rolls back after open | open delivered, CUMS transaction rolls back (commit failure, later failure) | EMS receives decision `WITHDRAWN`, conversation DONE by `system:mail-center-request` | a failed withdrawal is logged ERROR + counted |
| Filing fails before open | EMS refused or unreachable | nothing to withdraw, no call | — |
| Withdrawn conversation gets a reply later | requester answers | conversation reopens (existing inbound rule) | — |
| PCS merge | rows moved | one `partner_user_merge_log` row with source, target, moved row ids per column | — |
| PCS merge repeated, nothing moved | second run | no second log row | — |
| Decision, two implementers, first down | `postToEvery` | second still told; one ERROR + counter | — |

</frozen-after-approval>

## Code Map

- CUMS `service/ProfileMergeRequestService.java`, `CompanyLogoRequestService.java` `request(...)` -- after `mailCenter.opened(...)` succeeds, register `TransactionSynchronization.afterCompletion(STATUS_ROLLED_BACK)` that sends the decision `WITHDRAWN` (decidedBy `system:mail-center-request`, decidedAt now) through `MailCenterRequestDecisions` (same failure counter).
- CUMS `client/HttpMailCenterRequestContract.decided` -- `calls.post` → `calls.postToEvery` (7b added it).
- `docs/api/internal/cums-internal.openapi.yml` decision schema -- status enum gains `WITHDRAWN`; EMS `InternalRequestConversationController` and `RequestConversations.close` accept it (same close; no decided-at guard needed beyond the existing one).
- PCS `usermerge/UserReferenceService.java` -- write the log after the updates (`UPDATE … RETURNING id`), skip when nothing moved; new `V12__partner_user_merge_log.sql` (`id uuid, source_username, target_username, moved jsonb {"table.column": [ids]}, performed_at timestamptz default now()`).
- Tests: CUMS request ITs (commit failure simulated after open → `ContractServer` received WITHDRAWN); EMS IT for WITHDRAWN; PCS `UserReferenceIntegrationTest` (log row, no row on empty repeat); `HttpMailCenterRequestContractTest` (two implementers, first down).
- `deferred-work.md` -- mark the two entries closed (append a closing line each; never edit the entries).

## Tasks & Acceptance

**Execution:**
- [ ] Spec: `WITHDRAWN` in the cums-internal decision schema.
- [ ] CUMS: withdrawal on rollback, `postToEvery` for decisions, tests.
- [ ] EMS: accept `WITHDRAWN`, test.
- [ ] PCS: V12 + log write, tests.
- [ ] Docs: `06-backend-architecture.md` (withdrawal, PCS log), `06b-user-lifecycle-sync.md` (undo by hand now covers PCS), changelog.
- [ ] Epic closing record in Implementation Notes: every leftover of plans 6, 6b, 7, 7b, 8, 8b with closed / moved (and where).

**Acceptance Criteria:**
- Given the CUMS, EMS and PCS suites, then they pass with the new tests.
- Given local dev, when a PCS row moves in a merge, then `partner_user_merge_log` holds its id.
- Given the epic's leftovers, then each is closed in code or named with the story that owns it.

## Design Notes

**Architecture note.** Modules: CUMS request services and contract caller; EMS mail center; PCS `usermerge`. New dependency: none. New endpoint: none (the decision operation gains a status). Reused: `MailCenterRequestDecisions`, `ContractCalls.postToEvery`, `RequestConversations.close`. Deviation: none.

Withdrawal instead of a pre-commit open: opening after commit would store a request without its conversation when EMS is down, which the owner's rule (filing fails) excludes. A read timeout where EMS commits after the withdrawal arrived stays possible; the withdrawal then finds no conversation and EMS answers 204 with nothing to do — recorded as residual.

**Epic 17 rule (C15).** Start: deferred entries of this epic 2 (end 0); ArchUnit stores CUMS 0, EMS service-direction 0 (end 0).

## Verification

**Commands:**
- `set -o pipefail; ./gradlew --max-workers=4 :shared-kernel:test`, then CUMS, EMS, PCS (one at a time) -- green.
- `cd web-frontend && npm run lint:api-internal && npm run check:api-types && cd .. && make check-test-ratchet` -- pass.

**Manual checks:**
- Local dev: a merge moving a PCS partner note writes one `partner_user_merge_log` row with the note's id.
