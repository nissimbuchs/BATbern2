---
title: 'EMS pulls Mail Center requests from CUMS'
type: 'refactor'
ticket: '7'
created: '2026-11-11'
status: 'in-progress'
baseline_revision: '9d10da27cb011b4a8604cb0c016cd11080483ca8'
route: 'full'
route_source: 'auto'
risk: 'high'
review: ''
review_source: ''
lenses_ran: []
review_loop_iteration: 0
context: ['{project-root}/docs/architecture/ADR-016-service-directions-and-service-to-service-calls.md']
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** CUMS pushes merge and logo requests to EMS (`/internal/mail-center/{merge,logo}-requests[/{id}/done]`), an upstream→downstream call ADR-016 forbids. A failed open rolls back the user's request (502), and a failed close is logged and lost, so the conversation stays OPEN for good.

**Approach:** CUMS exposes `GET /internal/mail-center-requests?since=<cursor>&limit=` over both request tables, ordered by a change cursor. An EMS ShedLock job pulls it every minute, opens or closes the conversations through the existing `RequestConversations`, and stores its cursor. The push endpoints, the CUMS client pair and the 502 paths are deleted.

## Boundaries & Constraints

**Always:** the feed is in `cums-internal.openapi.yml` and contract-tested on both sides (`ContractServer`/`CumsContractHarness`, CUMS `S2sProviderContractIntegrationTest`); the EMS client uses `S2sHeaders.forCurrentUser()` (no JWT from the job); processing is idempotent (open finds an existing conversation by `keyOf`, close skips DONE); new migrations only (CUMS V30, EMS V153); the job is registered in `AutomaticTriggerAudienceGuardTest` as "no mail"; tests follow the fidelity rules (CUMS request ITs lose their `@MockitoBean` of the client).

**Never:** touch the primary-email push (`EventManagementUserMergeClient`, `/internal/users/primary-email`, `PrimaryEmailService`; owner 2026-11-11, moves in 7b); send mail from the job; edit V23/V25/V28; change what the organizer sees in a conversation (subject, requester, note).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|---|---|---|---|
| New request | OPEN merge or logo row, not yet pulled | next poll opens a conversation with today's subject, requester name/email, note | — |
| Decided | APPROVED/DECLINED row with an open conversation | next poll sets it DONE, `updated_by` = `decidedBy` | — |
| Opened and decided within one poll | decided row, no conversation | opened, then closed in the same pass (audit kept) | — |
| Old decided row on first poll | decided before the Mail Center, no conversation | not opened (no history backfill) | skip |
| Replay | cursor moved back, rows re-read | no duplicate conversation, no status change | idempotent |
| Concurrent commit | row committed with an older change time than the cursor | still delivered | settle window (Design Notes) |
| CUMS down | feed call fails | cursor unchanged, error logged + metric, next poll retries | no exception escapes the job |
| User files request while EMS down | POST merge/logo request | 201, no 502 | — |

</frozen-after-approval>

## Code Map

- CUMS `client/EventManagementMailCenterClient.java`, `client/HttpEventManagementMailCenterClient.java` -- delete (sync push, 502 on open, lossy close).
- CUMS `service/ProfileMergeRequestService.java` L73, L122-134; `service/CompanyLogoRequestService.java` L89, L142-155 -- drop the open/close calls; build the subject in the feed mapper instead (same text).
- CUMS `controller/UserMergeController.java` L224-229, `CompanyLogoRequestController.java` L99-103 -- remove the `MailCenterCallFailedException` → 502 branches.
- CUMS tables `profile_merge_requests` (V23), `company_logo_requests` (V25): no change column. V30 adds `changed_at timestamptz NOT NULL`, backfilled `coalesce(decided_at, created_at)`, set by a trigger to `clock_timestamp()` on insert and on status/decision update; index `(changed_at, id)` on each.
- CUMS `controller/InternalCompanyController.java` (GET with params pattern), `SecurityConfig` L81/L130 permit `/internal/**` -- new `InternalMailCenterRequestController` + feed service/repository query (UNION of both tables).
- `docs/api/internal/cums-internal.openapi.yml` (`/internal/email-owners` L212-240 as pattern) -- add the feed; `ems-internal.openapi.yml` L312-427, schemas ~L651-694, header L8 -- remove the 4 push operations.
- `docs/api/users-api.openapi.yml` L1409, L1450 -- remove the logo 502 and its text; regenerate `web-frontend/src/types/generated/user-api.types.ts` (no frontend caller).
- EMS `mailcenter/InternalRequestConversationController.java` -- delete; `mailcenter/RequestConversations.java` (`open` 65-86, `close` 89-94) -- keep, called by the new job.
- EMS `client/impl/UserApiClientImpl.java` `getEmailOwners` L770-788 -- query pattern; new client `mailcenter/MailCenterRequestFeedClient` (mail center module, not `client/`, so the request handling stays in L6).
- EMS `mailcenter/inbound/MailMessageRetentionScheduler.java` L41-47 -- ShedLock/cron pattern; `config/ShedLockConfig.java`.
- EMS test `config/CumsContractHarness.java` `ANSWERABLE` L77-82, `defaults()` L120 -- add the feed with a default empty page, so every IT context tolerates the job.
- Tests to rewrite: CUMS `ProfileMergeRequestIntegrationTest`, `CompanyLogoRequestIntegrationTest` (remove client mocks and 502 cases; assert the feed), delete `HttpEventManagementMailCenterClientContractTest`; EMS `InternalMergeRequestIntegrationTest`, `InternalLogoRequestIntegrationTest` → one `MailCenterRequestFeedJobIntegrationTest`; EMS `S2sProviderContractIntegrationTest` L130-133, L270-283 -- remove; CUMS `S2sProviderContractIntegrationTest` `@CsvSource` L96-106 -- add.
- `mailcenter/AutomaticTriggerAudienceGuardTest.java` L42-76 -- register the job.
- CUMS archunit store `e75ed5e2-...` lines 1, 3 -- drop out on the run; `docs/architecture/module-map.md` L17-20, L63-67 -- mail-center exception gone, CUMS store 4 → 2.

## Tasks & Acceptance

**Execution:**
- [x] `services/company-user-management-service/src/main/resources/db/migration/V30__mail_center_request_feed_cursor.sql` -- `changed_at`, trigger, backfill, indexes -- cursor without editing V23/V25.
- [x] `docs/api/internal/cums-internal.openapi.yml` -- `GET /internal/mail-center-requests` (`since` opaque string, optional; `limit` 1-200, default 100), item `{kind: MERGE|LOGO, requestId, status, requesterName, requesterEmail (idn-email), subject, note (nullable), decidedBy (nullable), changedAt}`, page `{items, nextCursor}`, examples -- contract first.
- [x] CUMS feed controller/service/repository -- settle window, keyset `(changed_at, id) > cursor`, requester name/email from the user profile -- the feed.
- [x] CUMS services, controllers, client pair -- remove push, 502 branches and the subject builders' push use -- CUMS no longer names EMS for requests.
- [x] `services/event-management-service/src/main/resources/db/migration/V153__feed_cursors.sql` -- `feed_cursors(feed varchar PRIMARY KEY, cursor text NOT NULL, updated_at timestamptz NOT NULL)` -- reused by 8b's pattern.
- [x] EMS `mailcenter/MailCenterRequestFeedClient`, `mailcenter/MailCenterRequestFeedJob` (`@Scheduled(cron="${batbern.mail-center-request-feed.cron:0 * * * * *}")`, `@SchedulerLock`, loop pages until empty, per item open/close, save cursor after each page, catch-all with log + Micrometer counter `mailcenter.request_feed.failures`) -- the pull.
- [x] EMS: delete `InternalRequestConversationController`, the 4 ems-internal operations and their schemas; update `RequestConversations` only if the job needs a combined open-then-close; `AutomaticTriggerAudienceGuardTest` entry.
- [x] `docs/api/users-api.openapi.yml`, generated types -- remove the 502.
- [x] Tests (fidelity rules): CUMS feed IT (order, settle window, cursor resume, limit, both kinds, decided fields) and provider contract; CUMS request ITs on the real path, asserting the feed row instead of a mocked client; EMS client contract test (with and without JWT); EMS job IT on `CumsContractHarness`: open, close, open+close in one pass, old decided skipped, replay idempotent, cursor resume, CUMS 500 leaves the cursor.
- [x] `docs/architecture/module-map.md`, `ADR-016` costs line, `docs/architecture/06-backend-architecture.md` (Mail Center requests section, per doc-drift mappings) -- docs in the same commit.

**Acceptance Criteria:**
- Given a running local dev, when a user files a profile-merge request and an organizer files nothing else, then the request shows in the Mail Center within about 1.5 minutes, and after the organizer approves it in CUMS the conversation turns DONE within the same delay.
- Given the same for a company logo request, then the same holds.
- Given a primary-email change on local dev, when it is saved, then an upcoming registration shows the new address at once (push unchanged).
- Given the CUMS and EMS full suites, when they run, then the CUMS archunit store holds only the 2 user-merge lines and `ems-internal.openapi.yml` has no `/internal/mail-center/` path.

## Implementation Notes

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

## Plan Change Log

## Review Triage Log

## Design Notes

**Architecture note.** Modules: CUMS request services, controllers, new internal feed; EMS mail center (L6) job and client, `RequestConversations`. New dependency: EMS mail center → CUMS `/internal` (downstream→upstream, allowed); CUMS→EMS mail-center edge removed. New endpoint: `GET /internal/mail-center-requests` (nearest: `GET /internal/email-owners`, a lookup by addresses, not a change feed; no existing feed to extend). Reused: `RequestConversations`, `S2sHeaders`, `CumsContractHarness`, ShedLock. Deviation: the feed client lives in `mailcenter/`, not `client/`, because only the mail center uses it (module map L6 placement beats the generic package).

**Cursor and settle window.** Wall-clock `changed_at` can commit out of order. The feed returns only rows with `changed_at <= clock_timestamp() - interval '30 seconds'` (request transactions are short), and the cursor is the opaque `(changed_at, id)` of the last item. Worst-case delay: one cron minute + 30 s. A sequence would have the same commit-order gap; this is simpler and testable with a settle of 0 in tests via property.

**First poll.** With no `feed_cursors` row, the job seeds the cursor just before the oldest OPEN request's `changed_at` (or at now minus the settle window when none is open). Open requests are picked up; decided history before that point was handled by the old push and is not backfilled. From then on, a decided item without a conversation (opened and decided within one poll) is opened, then closed, so the audit stays.

**Epic 17 rule (C15).** Fixed here: the reverse-direction client pair, the user-facing 502 on an internal outage, the lost close. Sizes at start (`9d10da27`): CUMS archunit store 4 lines, end 2. EMS store unchanged (its lines belong to 8b and 17.15). Spectral baseline unchanged (internal spec is not linted by it). Module scripts re-run before and after; the mail-center edge count recorded.

## Verification

**Commands:** (rack: one heavy job at a time, never two Gradle/Vitest/Playwright runs or a commit hook in parallel; logs to `~/.cache/batbern-test-logs/v37/`; `./gradlew --stop` at the end; no push, no PR)
- `set -o pipefail; ./gradlew --max-workers=4 :services:company-user-management-service:test 2>&1 | tee ~/.cache/batbern-test-logs/v37/cums.log` -- expected: BUILD SUCCESSFUL, store file 2 lines.
- `set -o pipefail; ./gradlew --max-workers=4 :services:event-management-service:test 2>&1 | tee ~/.cache/batbern-test-logs/v37/ems.log` -- expected: BUILD SUCCESSFUL.
- `cd web-frontend && npm run check:api-types && npm run lint:api-internal && npm run lint:api && cd .. && make check-test-ratchet` -- expected: all pass (ratchet STALE → `--update` and commit).
- `python3 scripts/architecture/ems-module-deps.py` and `ems-module-target.py` per their usage, before and after -- recorded in Implementation Notes.

**Manual checks:**
- Local dev (`make dev-native-up`, default one-minute cron): file a merge request and a logo request as a test user, see both in the Mail Center within ~1.5 min, decide both, see them DONE; change a primary e-mail, see the registration move at once. Mail only in `/dev/mails`.
