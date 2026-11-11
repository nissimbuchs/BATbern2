---
title: 'Refactor sweep'
type: 'refactor'
ticket: 'sweep'
created: '2026-11-11'
status: 'in-review'
baseline_revision: '92abe9cbdd3e13c098ad1add4cb57648843c9fe8'
route: 'full'
route_source: 'auto'
risk: 'medium'
review: 'quick'
review_source: 'pinned'
lenses_ran: ['quick']
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
- [x] Spec: `WITHDRAWN` in the cums-internal decision schema.
- [x] CUMS: withdrawal on rollback, `postToEvery` for decisions, tests.
- [x] EMS: accept `WITHDRAWN`, test.
- [x] PCS: V12 + log write, tests.
- [x] Docs: `06-backend-architecture.md` (withdrawal, PCS log), `06b-user-lifecycle-sync.md` (undo by hand now covers PCS), changelog.
- [x] Epic closing record in Implementation Notes: every leftover of plans 6, 6b, 7, 7b, 8, 8b with closed / moved (and where).

**Acceptance Criteria:**
- Given the CUMS, EMS and PCS suites, then they pass with the new tests.
- Given local dev, when a PCS row moves in a merge, then `partner_user_merge_log` holds its id.
- Given the epic's leftovers, then each is closed in code or named with the story that owns it.

## Implementation Notes

Built 2026-11-11 on `feature/17-7b-user-reference-contract`, commit `b42cdb76`, local only, no push, no PR. Not reviewed yet.

- **Spec.** `cums-internal.openapi.yml` `MailCenterRequestDecided.status` enum `[APPROVED, DECLINED, WITHDRAWN]` with a description; the decision operation's text says every implementer is told and what WITHDRAWN means. Frontend types unaffected (`check:api-types` clean, internal specs are not generated).
- **CUMS.** `MailCenterRequestDecisions.open(Opened)` replaces the direct `mailCenter.opened(...)` in `ProfileMergeRequestService` and `CompanyLogoRequestService` (their `MailCenterRequestContract` field is gone). Before every open call it registers `afterCompletion(STATUS_ROLLED_BACK)` that sends `WITHDRAWN` / `system:mail-center-request` / now through the existing `decided(...)` (every implementer via `postToEvery`; ERROR + `mailcenter.request_contract.failures` on failure). **Deviation from the matrix row "Filing fails before open: no call"** (review 2026-11-11, coordinator): the withdrawal is sent for every open attempt, whatever its outcome, because with several implementers an earlier one may have opened before a later one failed, and a timed-out open may still be committed. An implementer without the conversation answers 204 and changes nothing. A filing refused before the open (409 rules, unknown candidate) registers nothing. `HttpMailCenterRequestContract.decided` uses `ContractCalls.postToEvery`.
- **EMS.** `InternalRequestConversationController.Status` gains `WITHDRAWN`; `RequestConversations.close` unchanged (same DONE, same `last_inbound_at <= decidedAt` guard, so a later reply reopens through the existing inbound rule).
- **PCS.** `V12__partner_user_merge_log.sql` (id, source_username, target_username, performed_by, trigger, moved jsonb, performed_at; indexes on source and target). Plan listed five columns; `performed_by` and `trigger` added because the merge call carries them and an undo by hand needs to know who merged. `UserReferenceService.merge` uses `UPDATE ... RETURNING id::text`, writes the row only when something moved.
- **Tests.** CUMS `ProfileMergeRequestIntegrationTest` +4 (incl. a refused open -> WITHDRAWN; review fix) (commit failure via a throwing `beforeCommit` in an enclosing transaction; open timeout -> 502 + WITHDRAWN; rollback with the withdrawal refused -> counter +1), `CompanyLogoRequestIntegrationTest` +1 (later failure in an enclosing transaction), `HttpMailCenterRequestContractTest` +1 (two implementers, first down, second told, exception names only the first). The existing "Mail Center down" tests (connection refused) now expect the withdrawal. RED checked for the two first merge ITs (failed without the registration). EMS `InternalRequestConversationIntegrationTest` +2 (WITHDRAWN closes, `updated_by` system; a reply stored afterwards through `InboundMailStore` reopens it). PCS `UserReferenceIntegrationTest` +2 (one log row with the exact ids after merge + repeat; no row for a person without rows).
- **Docs.** ADR-016 costs, `06-backend-architecture.md` (Withdrawn bullet, decided to every implementer, PCS log, undo log line), `06b-user-lifecycle-sync.md` (PCS moves, Undo), `03-data-architecture.md` (merge line, PCS related tables), changelog entry. `deferred-work.md`: both entries closed with a `resolved:` line.
- **Verification (2026-11-11).** shared-kernel and CUMS 988 tests 0 failures (22 skipped); EMS 2779, 0 failures (15 skipped); PCS 290, 0 failures; checkstyle main+test for all three; `lint:api-internal`, `check:api-types`, `make check-test-ratchet` (no RAISED), `check-s2s-client-urls` (0) pass. Logs `~/.cache/batbern-test-logs/sweep/`.
- **Local dev (AC 2), 23:26 CET.** PCS, CUMS, EMS restarted on the new jars; V12 applied (`partner_user_merge_log` present). A partner note under `sweep.source`, `POST :8004/internal/user-references/merge` to `sweep.target` twice: first 200, second 200 with all counts 0; exactly one log row `{"partner_notes.author_username": ["d1929bb9-…"]}`, `performed_by` orga.sweep, ORGANIZER. Test rows removed. The withdrawal was not provoked on local dev (needs a rollback after the open); covered by the ITs.
- **Epic 17 rule (C15).** Deferred entries of this epic 2 -> 0. ArchUnit stores CUMS 0, EMS service-direction 0 (unchanged).

### Epic closing record (plans 6, 6b, 7, 7b, 8, 8b)

| Plan | Leftover | State |
|---|---|---|
| 6 | none deferred; review findings 1-5 patched in the story | closed |
| 6b | gateway GHSA-qhqv matcher listing `/api/v1/events/attendance-summary` ("out of scope" in the notes) | closed by 6b review #8: no `attendance-summary` in `api-gateway/src/main` (grep 2026-11-11) |
| 7 | pass 2 #1 orphan conversation on a failed filing (deferred) | closed here (withdrawal), residual named above |
| 7 | pass 2 #2 decision stops at the first failing implementer (rejected) | closed here (`postToEvery`) |
| 7 | PRD entry 7 still described the pull feed | closed: `epic-17-architecture-hardening.md` L483 says no polling |
| 7 | AC 3 primary-email push not run on local dev | closed by decision: make-primary needs the production Cognito pool; covered by `PrimaryEmailIntegrationTest` |
| 7b | #4 PCS keeps no undo record (deferred) | closed here (V12) |
| 7b | #2 make-primary not run end to end (rejected) | closed by decision, same reason as above |
| 8 | manual DevTools click-through not done | closed by decision: Playwright run and gateway log cover it |
| 8b | #1 a 404 from a first implementer stops the next (rejected) | closed by decision: one implementer (PCS); revisit with a second implementer, no story needed now |
| 8b | counter not read live (actuator not exposed locally) | closed: covered by `HttpCalendarReplyContractTest` |

Nothing moved to another story.

## Review Triage Log

Pass 1 (quick lens), 2026-11-11: 0 high, 1 medium, 5 low, 0 false.

| # | Finding | Verdict | Route | Evidence / action |
|---|---------|---------|-------|-------------------|
| 1 | With several implementers, a later implementer's failure leaves the first one's open conversation without a withdrawal | low | patch | Verified (`opened` uses `post`, withdrawal only on success/timeout); one implementer today. Fix: withdraw after every open attempt, to every implementer. |
| 2 | On a read timeout the withdrawal likely reaches EMS before the open commits, so the timeout case stays orphaned | medium | defer | Plausible from the timing (withdrawal sent at once after a 10 s timeout); not measured. Fix needs a withdrawal marker in EMS that a late open respects. Deferred-work entry written. |
| 3 | Closing record marks items "closed by decision" instead of code or an owning story | low | reject | The fix is editing this plan; the items are verification limits (production Cognito pool, manual click-through) and one single-implementer case, each recorded with its reason. |
| 4 | Matrix row "withdrawn conversation gets a reply later" has no test | low | patch | Verified. EMS test added. |
| 5 | `MailCenterRequestDecisions` Javadoc and counter text describe decisions only | low | patch | Verified. |
| 6 | Plan says "Deviation: none" while Implementation Notes record two | low | reject | Fix edits this plan; the deviations are recorded in Implementation Notes. |

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
