---
type: epic
title: "Services call in one direction over /internal"
parent: initiative-architecture-hardening
covers: [R11, R12, R13]
sources: []
companions: []
after: []
refined: true
risk: high
---

# Services call in one direction over /internal

## Description

Takes machine calls out of the public API and removes the two cycles. The public S2S calls (10 call sites on 2026-11-10, see Notes) move to `/internal` behind one shared-kernel token helper, and the machine-only operations leave the public specs (17.6: entries 6, 6b). CUMS owns a user-reference merge contract that EMS and PCS implement, EMS pulls merge and logo requests from a CUMS feed instead of being pushed, and primary-email changes stay a synchronous push inside that contract (17.7: entries 7, 7b; owner 2026-11-11). EMS stops calling PCS: the frontend loads partner topics itself, and PCS pulls calendar RSVPs from an EMS feed (17.8: entries 8, 8b). ADR-016 is the rule.

## Outcome

The public specs carry only what users call, and no service depends on a downstream one. The signal is that no client calls another service's `/api/v1` URL, the ArchUnit stores hold zero CUMS→EMS and EMS→PCS client lines, and the Spectral baseline loses the moved operations.

## Requirements

- R1 (parent R11): no EMS or PCS client calls another service's `/api/v1` URL; a guard over the client packages of all three services (ArchUnit or source scan) fails on one. The machine-only operations `POST /users/provision`, `POST /users/{username}/issue-invitation-credentials`, `PATCH /users/{username}/profile` and `GET /events/attendance-summary` are gone from the public specs, described in the provider's `docs/api/internal/*-internal.openapi.yml` and contract-tested on both sides; the Spectral baseline loses lines G01 and G02. Operations users also call (`GET /companies/{name}`, `GET /users/me`) stay public; their S2S callers move to `/internal` reads. A client method no production code calls is deleted, not moved.
- R2 (parent R11): one shared-kernel helper builds the headers of every S2S call: it forwards the user's JWT when there is one and sends none otherwise. Every client uses it. Each moved call succeeds with and without a user JWT (an integration test per call, both cases), and a moved write records the acting user or `system:<job>`.
- R3 (parent R11): the Spring gateway still denies `/internal/**` from outside (`ProductionSecurityFilterChainTest`, access-control DAST after deploy).
- R4 (parent R12): CUMS owns `/internal/user-references/preview` and `/internal/user-references/merge` in its internal spec; the holders are listed in CUMS configuration. EMS implements the contract in place of `/internal/users/merge-preview` and `/internal/users/merge`; PCS implements it for `topic_suggestions.suggested_by`, partner notes `author_username`, partner meetings `created_by` and `partner_contacts.username`. A merge preview shows counts per holder, a merge rewrites every holder's usernames, and a refusing holder blocks the merge (a CUMS test with two configured holders, one refusing). Before PCS joins, a read-only production query counts PCS rows that reference a former merge source; the result is recorded.
- R5 (parent R12): CUMS owns a Mail Center request contract in `cums-internal.openapi.yml` (a merge or logo request opened, decided), implemented by EMS and called through a CUMS caller named after the contract, its implementers in CUMS configuration (owner 2026-11-11, replaces the pull feed). A filed request appears in the Mail Center at once and its conversation closes when decided; `/internal/mail-center/{merge,logo}-requests[/{id}/done]` and the 3.7 feed (`GET /internal/mail-center-requests`, `feed_cursors`, the feed job) are gone. A primary-email change stays a synchronous push and moves upcoming registrations at once (owner 2026-11-11); 7b makes it an operation of the CUMS-owned user-reference contract (R4), so `/internal/users/primary-email` goes in 7b.
- R6 (parent R12): no CUMS class references EMS by name: the CUMS `ServiceDirectionTest` store is empty.
- R7 (parent R13): the topic canvas loads partner topics from PCS `GET /partners/topics` through the frontend service layer and the clusters from one EMS batch classify operation; it shows partner topics with clusters and company logos as before (Vitest for the hook, Playwright on the canvas). `TopicSessionDataService` makes no PCS call, and `PartnerApiClient` is gone.
- R8 (parent R13): EMS owns a calendar reply contract in `ems-internal.openapi.yml` (an iMIP RSVP reply received), implemented by PCS and called by `InboundEmailRouter` through an EMS caller named after the contract, its implementers in EMS configuration (owner 2026-11-11, replaces the pull feed; no ShedLock in PCS). An accept or decline reaches the PCS meeting at once (EMS contract test against `ContractServer`, PCS provider integration test, local-dev check with a sample iMIP reply). `PartnerMeetingRsvpClient(Impl)` and PCS `POST /internal/partner-meetings/rsvps` are gone, and the EMS `ServiceDirectionTest` store holds no PCS client line.

## Constraints

- The parent's C1 (named guard), C2 (architecture note), C5 (a PR deploys to production), C6 (owner go per story) and C14 (17.6 after 17.0a and 17.0b) bind every story.
- C8 (ADR-016): forward the user's JWT when present, never require it; `/internal` authorizes by operation; services call only upstream, and the profile merge is the one upstream→downstream call.
- C4: an operation that leaves a public spec changes the spec, the generated types, any frontend caller, Bruno and Playwright in the same PR.
- C3 binds 7, 8 and 8b: they remove EMS violations and re-run `scripts/architecture/ems-module-deps.py` and `ems-module-target.py`, before and after recorded.
- C12 binds 7 and 8b: Mail Center and inbound mail are verified on local dev (`/dev/mails`), no real mail from a test.
- C13 binds 7 and 8b: new feed tables come in new migrations; no applied migration is edited.
- C15 (leave the architecture cleaner) binds every story: a client or listener a story touches moves to the package `module-map.md` assigns, and a baseline or store committed at the end of a story is smaller than at its start, or the story says why.

## Done when

- No client calls another service's `/api/v1` URL (the R1 guard passes), and the 4 machine-only operations are gone from the public specs and specified in the internal specs.
- The merge preview counts EMS and PCS rows, and a merge rewrites both.
- Merge and logo requests reach the Mail Center by pull, and the topic canvas and calendar RSVPs behave as before.
- The CUMS ArchUnit store loses its 4 client lines (7, 7b) and the EMS store its 4 PCS client lines (8, 8b); the Spectral baseline loses G01 and G02 (6, 6b); before and after are recorded per story.
- Each story reached production, and the access-control DAST passes after deploy.

## Boundaries

- In: each story fixes the in-service violations its change exposes (C15): a client or listener it touches moves to the package the module map assigns, and an operation it moves to `/internal` leaves the Spectral baseline with it.
- In: touch point frontend topic canvas (`blobTopicService`, `partnerTopicsApi`), owned here by 8.
- Out: API convention changes to operations that stay public (epic 4, which waits on 6 and 7); they are breaking changes with their own C4 scope.
- Out: direct table reads across services (ADR-016 decision 6); they go to the database review.
- Out: PCS→EMS `GET /internal/events/{eventCode}/schedule` and PCS→EMS `/internal/mails`; already `/internal` and contract-tested (17.0b).
- Must not change: speaker invitation and provisioning, content submission, the partner attendance dashboard and export, partner contact self-service, the profile merge and the Mail Center requests behave as before for the user.

## References

- parent — `_bmad-output/initiative-architecture-hardening/initiative-architecture-hardening.md`, section Requirements (R11, R12, R13), Constraints, Notes (Decision lines)
- prd — `docs/prd/epic-17-architecture-hardening.md`, Rules for every story, Story order, Stories 17.6, 17.7, 17.8
- architecture — `docs/architecture/ADR-016-service-directions-and-service-to-service-calls.md`
- architecture — `docs/architecture/module-map.md`, sections "Services: who may call whom", "Gates"
- spec — `docs/api/internal/` (cums, ems, pcs internal specs, 17.0b2)

## Notes

- Decision: 2026-11-08, owner. Epic 17 exists to make the architecture cleaner, not to document or freeze it (initiative C15). This epic's stories remove the cross-service groups 17.5 left frozen: CUMS clients toward EMS (7, 7b) and EMS clients toward PCS (8, 8b). Applied in the Requirements and Constraints.
- Source conflict: R11 / PRD 17.6 / ADR-016 count "12 public S2S URLs". Code on 2026-11-10 (`feature/17-overnight-2026-11-10`) has 10 call sites on 9 operations. EMS→CUMS (`UserApiClientImpl`): `POST /users/provision`, `PATCH /users/{username}/profile`, `POST /users/{username}/issue-invitation-credentials`, `GET /companies?page&limit`, `GET /companies/{name}`. EMS→PCS (`PartnerApiClientImpl`): `GET /partners/topics`, `GET /partners?include=company&size=200`. PCS→CUMS: `GET /companies/{name}` (`CompanyServiceClientImpl`), `GET /users/me` (`UserServiceClientImpl`). PCS→EMS (`EventManagementClientImpl`): `GET /events/attendance-summary`. 17.0b moved PCS's `GET /events/{code}?include=sessions` to `/internal/events/{eventCode}/schedule`; CUMS's clients and EMS's `PartnerMeetingRsvpClient` already call `/internal`.
- Decision: 2026-11-10, decided by agent under owner's blanket go. The epic works from the measured 10, not 12: 6 moves the 4 EMS→CUMS calls in use and deletes the 5th (`getAllCompanies`, only a contract test calls it); 6b moves the 3 PCS calls; 8 deletes the 2 EMS→PCS calls with `PartnerApiClient`, so 6 does not touch `PartnerApiClientImpl`.
- Decision: 2026-11-10, decided by agent under owner's blanket go. `GET /companies/{name}` and `GET /users/me` stay public (users call them); EMS and PCS read a new `GET /internal/companies/{name}` and the existing `GET /internal/users/{username}`. Only the 4 machine-only operations leave the public specs.
- Source conflict: the earlier Done when attributed the 4 CUMS store lines to 17.6 and 17.7. The store holds `EventManagementMailCenterClient`, `HttpEventManagementMailCenterClient`, `EventManagementUserMergeClient`, `HttpEventManagementUserMergeClient`, all called only to `/internal`; 17.6 removes none.
- Decision: 2026-11-10, decided by agent under owner's blanket go. 7 removes the mail-center client pair, 7b the user-merge client pair; Done when updated.
- Source conflict: PRD 17.7's scope does not name CUMS→EMS `POST /internal/users/primary-email` (#1244); `module-map.md` assigns it to 17.7, and ADR-016 decision 5 allows no upstream→downstream call but the merge.
- Decision: 2026-11-10, decided by agent under owner's blanket go. Per ADR-016 decision 4, primary-email changes travel the CUMS feed that 7 builds, and `/internal/users/primary-email` goes in 7. Latency up to one poll interval is accepted, as the ADR accepts it for requests.
- Source conflict: PRD 17.8 says PCS→EMS "event summary, attendance-summary" stays via `/internal` after 17.6; the event summary is already `/internal/events/{eventCode}/schedule`.
- Decision: 2026-11-10, decided by agent under owner's blanket go. Only `GET /events/attendance-summary` moves, in 6b.
- Decision: 2026-11-10, decided by agent under owner's blanket go. Refined from the initiative's lines, the PRD, ADR-016, the module map and the code; no UX, architecture or PM pass is needed.
- Decision: 2026-11-10, decided by agent under owner's blanket go. Each PRD story splits in two entries sized for one session, ids following the PRD: 6 (helper, EMS→CUMS), 6b (PCS calls); 7 (CUMS feed pulled by EMS), 7b (user-reference contract); 8 (topic canvas), 8b (RSVP feed). The feed comes before the contract because 4.11 waits on the logo-request push leaving, and because 7 takes `primaryEmailChanged` out of the user-merge client that 7b then replaces.
- Decision: 2026-11-10, decided by agent under owner's blanket go. The tracer bullet is 6: shared-kernel helper, CUMS internal spec and controllers, EMS client, public spec and Spectral baseline, contract tests both sides, production. Someone sees a speaker invitation on local dev provision the speaker and issue credentials over `/internal`. 7 follows as the least certain story (the first pull feed in the codebase).
- Decision: 2026-11-10, decided by agent under owner's blanket go. Waits that are code, not order: 6b and 7 after 6 (they use its helper; 6b reads 6's `/internal/companies/{name}`); 7b after 7 (same CUMS client package and `UserMergeService`); 8b after 6b (PCS client package), 7 (the feed cursor pattern) and 8 (both shrink the same EMS `ServiceDirectionTest` store). 8 waits on 6 for the client-package guard whose baseline it shrinks.
- Decision: 2026-11-10, set check. Entries with no dependency path still share files: 6b and 7 both edit `ems-internal.openapi.yml` and the EMS provider contract test; 7b and 8b both edit `pcs-internal.openapi.yml`; 6b and 7b both touch `ems-internal`; 8 and 6b shrink 6's client-package guard baseline; 7 and 8 both re-run and record the EMS module scripts. No `after` added: the edits are additive in different paths, C5 keeps one PR open at a time, and each branch rebases on `develop` before its PR. The later story re-records the baseline and script numbers.
- Decision: 2026-11-10, set check. The module-map edits are owned by 7 (CUMS→EMS exceptions, store count) and 7b (user-reference contract as the one upstream→downstream call).
- Decision: 2026-11-10, decided by agent under owner's blanket go. Primary-email changes and the requests share one poll interval, which 7's plan sets and states (ADR-016 accepts the poll latency). Upcoming registrations therefore follow a primary-email change up to that interval later, not at once. The owner may still overrule this product-behaviour change before 7 starts.
- Decision: 2026-11-11, owner. Overrules the two 2026-11-10 agent decisions on primary-email: it stays a synchronous push, not the feed, because a registration mail must not go to the old address during a poll interval. To keep R6 (no CUMS class names EMS), 7b adds it to the CUMS-owned user-reference contract as `/internal/user-references/primary-email`, implemented by every holder, beside preview and merge; ADR-016 decision 5 is amended. 7 leaves `primaryEmailChanged` on `EventManagementUserMergeClient`, and 7b removes it with that client.
- Decision: 2026-11-10, decided by agent under owner's blanket go. 7b is `hitl`: the read-only production query of PCS rows runs with the owner's go.
- Decision: 2026-11-10, decided by agent under owner's blanket go. The epic closes with a refactor sweep; no closing end-to-end suite (the source has no test plan for one).
- Decision: 2026-11-11, owner. No polling between services: ADR-016 decision 4 is amended from pull feeds to contracts owned by the upstream service, called synchronously, implementers in its configuration (alternative B of four: pull on view, upstream-owned push contract, SQS, read where the data lives). CUMS and EMS are almost never down, so no outbox and no retry job; a call to a down implementer fails the triggering action, as before 2026-11. Entry 7, built with a feed and not deployed, is reworked to the CUMS Mail Center request contract; 8b becomes the EMS calendar reply contract; 7b is unchanged.
