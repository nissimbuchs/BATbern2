---
title: 'CUMS calls the Mail Center through a contract it owns'
type: 'refactor'
ticket: '7'
created: '2026-11-11'
status: 'done'
baseline_revision: '7815a9c20e392fd560c58839432be70e883ad8e3'
route: 'full'
route_source: 'auto'
risk: 'high'
review: 'quick'
review_source: 'pinned'
lenses_ran: ['quick']
review_loop_iteration: 1
context: ['{project-root}/docs/architecture/ADR-016-service-directions-and-service-to-service-calls.md']
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** CUMS pushed merge and logo requests to EMS by name (`EventManagementMailCenterClient`). The first build replaced that with a pull feed polled every minute; the owner rejected polling on 2026-11-11 (constant idle traffic, delay, a job, cursor and lock per feed).

**Approach:** CUMS owns a Mail Center request contract in `cums-internal.openapi.yml`, implemented by EMS and called synchronously through one CUMS caller that names no service; the implementers are listed in CUMS configuration (ADR-016 decision 4 as amended). The feed, its migrations and the job go. Owner decision 2026-11-11 ("B for all"), which approves this rework.

## Boundaries & Constraints

**Always:** Shared-kernel `ContractCalls` (headers from `S2sHeaders.forCurrentUser()`, one call per configured implementer, error translation) is the one way a service calls a contract it owns; 7b and 8b reuse it. Contract in `cums-internal.openapi.yml`, tested on both sides (`ContractServer` in CUMS, EMS `S2sProviderContractIntegrationTest` against the cums spec). Opening stays inside the filing transaction: an implementer that does not answer fails the filing (502, request not stored), as before 2026-11. Deciding calls after commit; a failure is logged ERROR (`mailcenter.request_contract.failures` counter) and does not undo the decision; deciding in the Mail Center already closes the conversation itself. Keep the first build's fixes: `RequestConversations` close guard (`last_inbound_at <= decidedAt`) and group-id link, close-at-once in `ConversationDetail`, null requester tolerated. No-op `.sql` deletions: CUMS V30 and EMS V153 were never deployed; delete them and clean the local dev DB by hand.

**Never:** touch the primary-email push (7b); send mail; edit an applied migration; change what the organizer sees in a conversation.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|---|---|---|---|
| New request | user files a merge or logo request | conversation open in the Mail Center at once, same subject, requester, note | — |
| Implementer down at filing | EMS not reachable | 502, request not stored, user can retry | as before 2026-11 |
| Decided | approve or decline | conversation DONE, `updated_by` = decider | — |
| Decided, requester replied after the decision | inbound mail newer than the decision | stays OPEN | — |
| Implementer down at decision | EMS not reachable | decision stands; ERROR + counter | logged |
| Repeated call | same open or decision twice | no duplicate, no change | idempotent |

</frozen-after-approval>

## Code Map

- Restore from `9d10da27` and rename: CUMS `client/EventManagementMailCenterClient` + `HttpEventManagementMailCenterClient` → `client/MailCenterRequestContract` (interface, named after the contract) + `HttpMailCenterRequestContract` on `ContractCalls`; the open/close call sites in `ProfileMergeRequestService`, `CompanyLogoRequestService` and the 502 branches in `UserMergeController`, `CompanyLogoRequestController` as they were, the close moved after commit (`TransactionSynchronization` or the controller after the service returns).
- EMS `mailcenter/InternalRequestConversationController` (restore, paths `POST /internal/mail-center-requests` (kind in body) and `POST /internal/mail-center-requests/{requestId}/decision`), calling the current `RequestConversations`.
- Delete: CUMS `MailCenterRequestFeedService`, `InternalMailCenterRequestController`, `V30__mail_center_request_feed_cursor.sql`, feed ITs; EMS `MailCenterRequestFeedClient`, `MailCenterRequestFeedJob`, `V153__feed_cursors.sql`, their tests, the feed default in `CumsContractHarness`, the job entry in `AutomaticTriggerAudienceGuardTest`, the cron property in test properties.
- New shared-kernel `ch.batbern.shared.contract.ContractCalls` (+ unit test): `List<String> implementers` from `batbern.contracts.<name>.implementers`, `post(path, body)` / `get(path, uriVariables, type)` per implementer, 409 → `ContractRefusedException(message)`, other failure → `ContractCallFailedException(implementer, cause)`.
- CUMS `application.yml`: `batbern.contracts.mail-center-requests.implementers: ${EVENT_MANAGEMENT_SERVICE_URL:http://event-management:8080}`; test yml points at the `ContractServer`.
- Specs: `cums-internal` replaces the feed with the two contract operations; `users-api` texts back to "opened/closed at once"; user guide `event-mail.md`, changelog, doc-drift mapping follow.

## Tasks & Acceptance

**Execution:**
- [x] Spec `cums-internal`: contract operations (MailCenterRequestOpened, MailCenterRequestDecided), feed removed.
- [x] shared-kernel `ContractCalls` + tests.
- [x] CUMS: contract caller, services, controllers, config; delete feed + V30; ITs (`ProfileMergeRequestIntegrationTest`, `CompanyLogoRequestIntegrationTest`) assert what the `ContractServer` received, and the 502 at filing; contract test.
- [x] EMS: controller restored on new paths; delete feed client, job, V153 and their tests; provider contract test against the cums spec; ratchet baseline.
- [x] Docs as in Code Map; ADR-016 cost line states open fails, decision logs.
- [x] Local dev DB: drop V30/V153 objects and their `flyway_schema_history_*` rows.

**Acceptance Criteria:**
- Given local dev, when a test user files a merge and a logo request, then both conversations are in the Mail Center at once; when an organizer declines them outside the Mail Center, then both are DONE at once.
- Given EMS stopped on local dev, when a merge request is filed, then the user gets the error and no request is stored.
- Given the CUMS and EMS suites, then they pass, the CUMS store has 2 lines (user-merge pair, 7b), and no feed class, job or migration remains.

## Plan Change Log

- 2026-11-11, owner decision (not a review finding): polling rejected, alternative B (upstream-owned push contract) chosen for 7, 7b and 8b. Amended: intent, boundaries, matrix, code map, tasks, design. Avoids: a job, cursor table and lock per feed, one-minute delay, idle traffic. KEEP: `RequestConversations` close guard and group-id link, `ConversationDetail` close-at-once and its MSW test, LEFT-JOIN lesson (an open without an address is skipped, a decision always applies), the docs cleanups of the first build.

## Implementation Notes

Rework (contract), built 2026-11-11 on `feature/17-7b-user-reference-contract`, not committed, no push, no PR. Not reviewed yet.

- **Contract** in `cums-internal.openapi.yml`: `POST /internal/mail-center-requests` (`MailCenterRequestOpened`: kind, requestId, requesterName nullable, requesterEmail with the #1102 pattern, subject, note) and `POST /internal/mail-center-requests/{requestId}/decision` (`MailCenterRequestDecided`: kind, status, decidedBy, decidedAt), both 204/400, tagged `contract`. `decidedAt` carries the close guard (`last_inbound_at <= decidedAt`) over to the push.
- **shared-kernel `ContractCalls`** (+ `ContractRefusedException`, `ContractCallFailedException`): implementers bound with Spring `Binder` (YAML list or comma-separated, trailing `/` dropped, none when unset), `post(path, body, uriVariables...)` and `get(path, uriVariables, type)` per implementer in order, stop at the first failure, headers from `S2sHeaders.forCurrentUser()`. Unit test `ContractCallsTest` (8).
- **CUMS.** `client/MailCenterRequestContract` + `HttpMailCenterRequestContract` (2 s / 10 s, maps both contract exceptions to `MailCenterCallFailedException`, so `UserMergeController` keeps one 502 handler and 7b can map refusals to 409 there without a clash). Open inside the filing transaction as at `9d10da27`; the decision goes through `service/MailCenterRequestDecisions` (transaction synchronization `afterCommit`, ERROR `MAIL_CENTER_REQUEST decision not delivered` + counter `mailcenter.request_contract.failures`), called from `decide(...)` in both services. Controllers' 502 handlers restored unchanged. Config `batbern.contracts.mail-center-requests.implementers: ${EVENT_MANAGEMENT_SERVICE_URL:...}`; test yml `http://mail-center-contract.invalid` (unbound call fails as 502).
- **CUMS tests.** `ProfileMergeRequestIntegrationTest` and `CompanyLogoRequestIntegrationTest` no longer `@Transactional` (the decision is after commit), rows removed by SQL before and after each test; the `ContractServer` (`client/MailCenterContract`) asserts every open and decision body, plus new: merge 502 at filing, decision failure keeps the decision and raises the counter. `HttpMailCenterRequestContractTest` (5). Ratchet `be-transactional-it` fell by 2, baseline updated. Feed IT, feed service, controller and V30 deleted; `S2sProviderContractIntegrationTest` and `application-test.yml` back to `9d10da27` plus the contract property.
- **EMS.** `InternalRequestConversationController` on the two contract paths (kind enum in body, 204); `RequestConversations.close` now always takes `decidedAt` (null branch and unused `exists` removed). `InternalRequestConversationIntegrationTest` (11): open, IDN, Unicode local part 400, idempotent open, unlinked link, close with decider and idempotent, reply after decision stays open, decision without conversation, invalid 400. Provider test runs both operations against the cums spec with and without a forwarded JWT, request bodies validated against the spec first. Feed client, job, V153 and their tests deleted; `CumsContractHarness`, `AutomaticTriggerAudienceGuardTest`, `application-test.properties` back to `9d10da27`.
- **users-api.** Texts back to "opened at once / closed after the commit"; 502 restored on the logo request and added on the merge request (the controller always answered it, the spec never said so); types regenerated.
- **Docs.** `06-backend-architecture.md` (internal table, logo section, "Mail Center requests" rewritten), `06b` row E, `module-map.md` (`ContractCalls`), ADR-016 (cost line: open fails, decision logs; enforcement line for `ContractCalls`), changelog entry replaced, `event-mail.md` back to `9d10da27`, doc-drift mappings for `shared/contract/` and the EMS controller, `ConversationDetail` comments. Not touched: `docs/prd/epic-17-architecture-hardening.md` entry 7 still describes the pull feed (not in the Code Map).
- **Verification (2026-11-11).** shared-kernel 649 and CUMS 974 tests, EMS 2776, 0 failures; checkstyle main+test of the three modules; `lint:api`, `lint:api-internal`, `check:s2s-client-urls`, `check:api-paths`, ratchet, `type-check`, communications Vitest (112) green; `check:api-types` passes once the regenerated types are committed. CUMS archunit store 2 lines (user-merge pair, 7b).
- **Local dev.** V30 and V153 objects and their history rows dropped by hand; CUMS and EMS restarted on the new build (Flyway up to date at 29 / 152). AC 1, 21:03 UTC: as `batbern.speaker` (company set to `5dev` and a login-less "Batbern Speaker" candidate added for the test; a real logo upload through presigned URL, MinIO and confirm), merge and logo request filed through the gateway: both conversations OPEN in the same second, subject and requester as before. Declined as `batbern.organizer` through the API: both DONE within 60 ms, `updated_by` = the organizer. AC 2: EMS stopped, merge request filed: 502 `MERGE_REQUEST_NOT_DELIVERED`, 0 rows. Test rows, the candidate, the company link and the MinIO objects removed afterwards; EMS restarted.

First build (pull feed), superseded by the 2026-11-11 rework; kept as history.

Built 2026-11-11 on `feature/17-3-7-mailcenter-request-feed`, no commit, no push.

- **Seed lives in CUMS.** The first poll sends no `since`; CUMS starts the feed just before the oldest OPEN request (now minus the settle window when none is open) and always returns a `nextCursor`, also on an empty page. "Old decided row on first poll" is therefore tested in the CUMS feed IT (`firstPoll_startsAtOldestOpen`, `firstPoll_withoutOpenRequest_startsNow`); the EMS job IT checks that the first call carries no `since` and that the start cursor is stored.
- **Job.** `MailCenterRequestFeedJob.poll()` (cron, ShedLock `lockAtLeastFor` PT10S) calls `pull()`, which the IT calls directly. Every item goes through `RequestConversations.open` (idempotent); a decided one then through `close`. `RequestConversations` unchanged. A page shorter than 100 ends the run; at most 50 pages per run. Any exception (CUMS down, a bad item) stops the run with the cursor of the last completed page, ERROR log `MAIL_CENTER_REQUEST_FEED failed` and counter `mailcenter.request_feed.failures`. A poison item would therefore block the feed with an ERROR every minute instead of being skipped silently (deliberate, nothing is lost).
- **Cron off in tests** (`batbern.mail-center-request-feed.cron=-` in EMS `application-test.properties`, like the Q&A digest): every cached context would otherwise write the shared `feed_cursors` row. The harness still answers the feed with an empty page by default.
- **CUMS 500 in the job IT** is simulated by the harness answer throwing `HttpServerErrorException` 500 (what `RestTemplate` throws for a real 500); the spec declares no 500.
- **CUMS settle window** `batbern.mail-center-request-feed.settle-seconds` (default 30, 0 in `application-test.yml`).
- **Feed IT not `@Transactional`** (test ratchet `be-transactional-it`): rows commit and are deleted by id; the one test that closes every open request runs in a rolled-back `TransactionTemplate`.
- **Module scripts** (`ems-module-deps.py`, `ems-module-target.py`), before and after: mail center 96 → 97 classes (controller out, client and job in), violating edges 27 / refs 91 unchanged, mail-center outgoing edges unchanged. CUMS archunit store 4 → 2 lines (the user-merge pair).
- **Extra docs:** user guide `event-mail.md` (delay of about 1.5 min), changelog entry, doc-drift mapping for `mailcenter/MailCenterRequestFeed*`.
- **Ratchet:** `be-mock-s2s-client` fell by 2 (CUMS request ITs), baseline updated.
- **Review fixes (2026-11-11):** a decision read again closes an existing conversation only when `last_inbound_at <= changedAt` (a requester's reply keeps it open; one opened in the same pass closes regardless); the feed LEFT JOINs the profile and sends name/address null when it is gone (EMS then opens nothing but still closes); a dedicated `InvalidLimitException` instead of mapping every `IllegalArgumentException` to 400; `RequestConversations.open` links a stored but unlinked conversation by its group id; deciding in the Mail Center PATCHes the conversation DONE at once (`ConversationDetail`, MSW test); users-api approve/decline texts say who closes the conversation.

- Review pass 1 patches in `74b9b31c`. Full verify after them (2026-11-11): CUMS 979 tests and EMS 2780, 0 failures; frontend type-check, the communications Vitest folder (112), i18n keys, `check:api-types`, `lint:api`, `lint:api-internal`, the ratchet, the S2S guard and the API paths all green. Story references removed from the new code comments.
- Local dev (AC 1, 2), 2026-11-11 18:49-18:52 UTC. V30 and V153 applied on restart. First poll: cursor stored, no history opened. The two OPEN rows (merge and logo) were inserted by SQL, because neither test account has a merge candidate or a company; the trigger, feed, job and `RequestConversations` ran unchanged. Both conversations opened 33 s after insert, with the subject and requester as before. Declined through the API as organizer (`POST /users/merge-requests/{id}/decline`, `/users/company-logo-requests/{id}/decline`): both DONE 51 s later, `updated_by` = the organizer. Test rows removed afterwards.
- AC 3 (primary-email push, unchanged code): not run on local dev, where make-primary needs the production Cognito pool. Covered by the existing integration tests, which pass. The close-at-once from the Mail Center is covered by `ConversationDetail.decision.test.tsx`, not by a browser run.


## Review Triage Log

Pass 1 (quick lens), 2026-11-11: 0 high, 3 medium, 4 low, 0 false.

| # | Finding | Verdict | Route | Evidence / action |
|---|---------|---------|-------|-------------------|
| 1 | A re-delivered decided item closes a conversation the requester reopened by replying | medium | patch | Verified: `InboundMailStore.conversationFor` L166 sets OPEN on a new inbound mail; `close` sets DONE whenever status is not DONE. The first production poll starts at the oldest OPEN request and re-delivers every decision after it. Fix: close only when no inbound mail arrived after the decision (`last_inbound_at <= changedAt`), except for a conversation opened in the same pass. |
| 2 | Inner join on `user_profiles` drops decided rows of a deleted requester, so the close is lost | medium | patch | Verified: FEED joins `user_profiles` for every row; nothing rewrites `requester_username` on delete or merge. Fix: LEFT JOIN, deliver every row, requester name/email nullable in the spec; EMS opens only with an address and always applies the close. |
| 3 | Every `IllegalArgumentException` in the controller becomes 400 | low | patch | Verified: handler lists `IllegalArgumentException`; only `limit` is intended. Direct correction: a dedicated exception for the limit. |
| 4 | `RequestConversations.open` is not atomic: a crash between store and link leaves an unlinked conversation that every later poll fails to link | low | patch | Verified at L77-84: the duplicate branch returns `keyOf`, which stays null. Pre-existing, but the job retries routinely; owner rule "fix what you find". Fix: in the duplicate branch, link the conversation found by its group id. |
| 5 | users-api approve/decline descriptions (L1235, L1551) still say the conversation is closed at once | low | patch | Verified. Align text with the pull (closed by the Mail Center, see 6). |
| 6 | After a decision in the Mail Center the conversation stays OPEN in the lists until the next poll | medium | patch | Verified: `ProfileMergeRequestPanel`/`CompanyLogoRequestPanel` `settle` only refetches; `ConversationDetail` L113/L120 `onDecided` only refetches. The existing conversation PATCH (`save({status})`, L136) can set DONE right away; the feed close stays the backstop for decisions made elsewhere. |
| 7 | ACs 1-3 have no recorded local-dev evidence | low | (verification) | Verified. Not a code fix: run after the patches, recorded in Implementation Notes. |

Pass 2 (quick lens, contract rework), 2026-11-11: 0 high, 1 medium, 4 low, 0 false.

| # | Finding | Verdict | Route | Evidence / action |
|---|---------|---------|-------|-------------------|
| 1 | A filing that fails after EMS committed the open (read timeout, CUMS commit failure) leaves an OPEN conversation for a request that does not exist; a retry opens a second one | medium | defer | Verified: `opened` runs inside the CUMS transaction, EMS commits on its own. Same as the pre-November push (`9d10da27`), so not caused by this change; needs a compensating call or an outbox, which the owner ruled out for now. Deferred-work entry written. |
| 2 | `ContractCalls.post` stops at the first failing implementer, so a decision does not reach later implementers | low | reject | Verified, but this contract has exactly one implementer; stop-at-first is what open and 7b's merge need. Revisit when a contract gets a second best-effort implementer. |
| 3 | An empty implementer list makes calls silently do nothing | low | patch | Verified (`filter(!isEmpty)`, no log). Fix: WARN at construction. |
| 4 | The CUMS example check has no rows for the two contract operations | low | patch | Verified: `should_haveValidExamples_forInternalOperations` rows. Fix: add the rows. |
| 5 | 06-backend still says callers send no JWT | low | patch | Verified at ~L189. Fix: sentence follows ADR-016 decision 3. |
| 6 | Implementation Notes say the PRD was not touched; the PRD edit also covers 17.8 and 7b's query result | low | reject | The fix is editing this plan; the PRD edit is the orchestrator's owner-decision change (committed with the decision docs), and the query result is recorded in the 7b plan. |

## Design Notes

**Architecture note.** Modules: shared-kernel `contract`; CUMS `client` (contract caller), request services and controllers; EMS mail center (`InternalRequestConversationController`, `RequestConversations`). New dependency: CUMS → its own contract (no EMS name), EMS → none. Endpoints: `POST /internal/mail-center-requests` and `.../{requestId}/decision` on EMS, owned by CUMS's spec; nearest existing are the 4 removed `/internal/mail-center/{merge,logo}-requests` operations, merged into two by putting the kind in the body. Reused: `RequestConversations`, `S2sHeaders`, `ContractServer`, provider contract test. Deviation: none (ADR-016 decision 4 as amended 2026-11-11).

**Epic 17 rule (C15).** Start (`7815a9c2`): CUMS store 2 lines (user-merge pair, 7b), EMS store unchanged; the first build already took the mail-center pair out. End: same store sizes, plus 2 migrations, 4 feed classes and a job fewer than the first build.

## Verification

**Commands:** (one heavy job at a time; logs `~/.cache/batbern-test-logs/v37b/`; `./gradlew --stop` at the end; no push, no PR)
- `set -o pipefail; ./gradlew --max-workers=4 :shared-kernel:test :services:company-user-management-service:test 2>&1 | tee …/cums.log` -- BUILD SUCCESSFUL.
- `set -o pipefail; ./gradlew --max-workers=4 :services:event-management-service:test 2>&1 | tee …/ems.log` -- BUILD SUCCESSFUL.
- `cd web-frontend && npm run check:api-types && npm run lint:api-internal && npm run lint:api && cd .. && make check-test-ratchet` -- pass.

**Manual checks:**
- Local dev: as in the ACs. Mail only in `/dev/mails`.
