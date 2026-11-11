---
title: 'EMS calls PCS calendar replies through a contract it owns'
type: 'refactor'
ticket: '8b'
created: '2026-11-11'
status: 'built'
baseline_revision: '29c260f314d16c9c4d6f35a7c4bd0ac4853e3b1a'
route: 'full'
route_source: 'auto'
risk: 'medium'
review: ''
review_source: ''
lenses_ran: []
review_loop_iteration: 0
context:
  - '{project-root}/docs/architecture/ADR-016-service-directions-and-service-to-service-calls.md'
  - '{project-root}/_bmad-output/initiative-architecture-hardening/tickets/epic-one-service-direction/tickets/story-ems-pulls-mail-center-requests-from-cums-plan.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

Owner decision 2026-11-11 ("B for all": no polling, upstream-owned contracts), which approves this plan.

**Problem:** EMS calls PCS by name (`PartnerMeetingRsvpClient` → PCS `POST /internal/partner-meetings/rsvps`) when an iMIP RSVP reply arrives: the last EMS class that names PCS, and a client that builds its own headers.

**Approach:** EMS owns a calendar reply contract in `ems-internal.openapi.yml`, `POST /internal/calendar-replies`, implemented by PCS and called by `InboundEmailRouter` through one EMS caller on shared-kernel `ContractCalls` that names no service; implementers in EMS configuration. The client pair and the PCS endpoint go.

## Boundaries & Constraints

**Always:** Branch `feature/17-7b-user-reference-contract` (8b follows 7 and 7b there), commit locally, no push or PR without owner go. Verify on local dev only. One heavy job at a time on rack; Gradle `--max-workers=4`, logs under `~/.cache/batbern-test-logs/v8b/`. Spec first. Inbound processing never fails because of the call: any failure is logged (as today) and counted (`forwarding.calendar_reply_contract.failures`). Tests: EMS `InboundEmailRouterTest` and a contract test against `ContractServer` (no mock of the caller); PCS provider test validates against the EMS spec, with and without a JWT. ArchUnit EMS store `98b035c9` ends empty.

**Never:** No polling, no ShedLock, no new table. No change to the public partner-meetings API or the RSVP panel. No real mail.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Accept | iMIP REPLY, UID `<meetingUuid>@batbern.ch`, PARTSTAT ACCEPTED | PCS meeting shows the attendee ACCEPTED at once | — |
| Accept then decline | two replies | PCS ends DECLINED | — |
| Unknown meeting | UUID not in PCS | PCS answers 404; EMS logs WARN, inbound continues | not counted as failure |
| Bad PARTSTAT | e.g. `NEEDS-ACTION` | PCS answers 400; EMS logs WARN | — |
| PCS down | call fails | inbound continues; ERROR + counter | — |
| Rate limit / bad UID | as today | nothing called | — |

</frozen-after-approval>

## Code Map

- EMS `forwarding/InboundEmailRouter.java:174` `routeIcsReply` -- replace `PartnerMeetingRsvpClient` with the new caller; rate limit and UID checks stay.
- EMS `client/PartnerMeetingRsvpClient.java`, `client/impl/PartnerMeetingRsvpClientImpl.java`, `client/impl/PartnerMeetingRsvpClientImplContractTest.java` -- delete; their error handling (404 WARN, other ERROR, never rethrow) moves into the caller.
- EMS new `forwarding/CalendarReplyContract.java` + `HttpCalendarReplyContract.java` on `ContractCalls` (`batbern.contracts.calendar-replies.implementers: ${PARTNER_COORDINATION_SERVICE_URL:http://partner-coordination:8080}`, `application.yml` ~L143 has the URL).
- EMS `archunit_store/98b035c9-…` -- 2 lines → 0.
- PCS `controller/InternalRsvpController.java` -- path becomes `/internal/calendar-replies`; body `{meetingId, attendeeEmail, partStat}` unchanged; 404 for an unknown meeting, 400 for an unknown PARTSTAT. `PartnerMeetingRsvpControllerIntegrationTest` (internal cases), `S2sProviderContractIntegrationTest`, `TestFixtureCleanupControllerIntegrationTest` follow.
- `docs/api/internal/ems-internal.openapi.yml` (add contract), `pcs-internal.openapi.yml` L18 (remove `recordInternalRsvp`).
- `api-gateway/.../ProductionSecurityFilterChainTest.java:177` -- replace the denied example path with `/internal/calendar-replies`.

## Tasks & Acceptance

**Execution:**
- [x] Specs: contract in `ems-internal`, removal in `pcs-internal`.
- [x] EMS caller + config + contract test; `InboundEmailRouter`; delete client pair; `InboundEmailRouterTest` off the mock; archunit store emptied.
- [x] PCS controller path, tests, provider contract test against the EMS spec.
- [x] Gateway test path.
- [x] Docs: ADR-016 enforcement (exception `PartnerMeetingRsvpClientImpl` gone), `module-map.md` frozen list (EMS `*Partner*Client*` 0), `06-backend-architecture.md`, `03-data-architecture.md`, `01-system-overview.md`, changelog, doc-drift mappings.

**Acceptance Criteria:**
- Given local dev and a partner meeting, when a sample iMIP ACCEPTED then DECLINED reply goes through the inbound processor, then the PCS RSVP list shows ACCEPTED, then DECLINED, each at once.
- Given PCS stopped, when a reply arrives, then inbound processing completes and EMS logs one ERROR.
- Given the EMS, PCS and gateway suites, then they pass and the EMS archunit store is empty.

## Implementation Notes

Built 2026-11-11 on `feature/17-7b-user-reference-contract`, committed locally, no push, no PR. Not reviewed yet.

- **Contract** in `ems-internal.openapi.yml`: `POST /internal/calendar-replies` (`recordCalendarReply`, tag `contract`), body `CalendarReply` {meetingId uuid, attendeeEmail with the #1102 pattern, partStat string}, answers 204 / 400 / 404 (ErrorResponse with examples). The answer changed from 200 + `RsvpDTO` to 204: EMS never read the body, and an EMS-owned spec should not carry PCS's model. `partStat` stays a free string (not an enum) so that EMS may send `NEEDS-ACTION` and PCS answers 400, as the matrix says. `pcs-internal.openapi.yml` keeps the file with `paths: {}` and an info text naming the contracts PCS implements (the file is listed by `check-api-paths.test.ts` and `ContractServerTest`).
- **EMS.** `forwarding/CalendarReplyContract` (interface, `NAME = calendar-replies`, record `Reply`) and `forwarding/HttpCalendarReplyContract` on `ContractCalls` (own `RestTemplate`, 2 s / 10 s). Never throws: 404 and 400 → WARN `CALENDAR_REPLY refused`, not counted; anything else → ERROR `CALENDAR_REPLY not delivered` + Micrometer counter `forwarding.calendar_reply_contract.failures`. `InboundEmailRouter.routeIcsReply` calls it after the unchanged rate-limit and UID checks. Config `batbern.contracts.calendar-replies.implementers: ${PARTNER_COORDINATION_SERVICE_URL:http://partner-coordination:8080}`; the now unused `partner-service.base-url` block removed. Test profile: `http://calendar-reply-contract.invalid`. Deleted `PartnerMeetingRsvpClient`, `PartnerMeetingRsvpClientImpl`, `PartnerMeetingRsvpClientImplContractTest`.
- **EMS tests.** `HttpCalendarReplyContractTest` (5): body and URL at the configured implementer, no JWT; 404/400 from the spec examples discarded and not counted; connection refused and 5xx counted, no exception. `InboundEmailRouterTest` builds the router with the real `HttpCalendarReplyContract` on a `ContractServer` bound to the EMS spec (exact body asserted, 404 case added; every no-call case fails on any request). Ratchet `be-mock-s2s-client` InboundEmailRouterTest 1 → 0, baseline updated. ArchUnit store `98b035c9` 2 lines → 0 (emptied by the test run).
- **PCS.** `InternalRsvpController` on `/internal/calendar-replies`, 204; unknown PARTSTAT now throws `IllegalArgumentException("Unknown partStat: …")`, so the global handler answers 400 with the shared ErrorResponse (the old body-less 400 deviation is gone). Unknown meeting 404 unchanged (`PartnerNotFoundException`). `PartnerMeetingRsvpControllerIntegrationTest` follows (204, recorded state read through the public GET). `S2sProviderContractIntegrationTest` validates each request against the EMS spec, runs ACCEPTED → DECLINED (repository state after each), NEEDS-ACTION 400, unknown meeting 404, invalid body 400, with and without a forwarded JWT; responses checked against the EMS spec. `TestFixtureCleanupControllerIntegrationTest` needed no change (it never used the internal path).
- **Gateway.** `ProductionSecurityFilterChainTest` denies `/internal/calendar-replies` instead of the old path.
- **Docs.** ADR-016 (enforcement: no header exception left; cost line names the calendar reply counter), `module-map.md` (EMS `*Partner*Client*` 0), `06-backend-architecture.md` (diagram, internal table row, contract paragraph), `03-data-architecture.md`, `01-system-overview.md`, ADR-008 example path, `partner-meetings-api.openapi.yml` description of the RSVP list (text only; partner-meetings types regenerated, 4 comment lines), changelog entry, doc-drift mappings for the two EMS contract classes and the PCS controller. Not touched: PRD texts (`epic-8`, `epic-17` entry 8b) and `api-consolidation-plan.md`, which describe history or the plan.
- **Module scripts** (before = baseline `29c260f3` + nothing, after = this build): deps mail 119 → 121, platform 33 → 31, mail → platform refs 15 → 14; target mail_center 96 → 98, platform 33 → 31; edges 52 / 27 / 91 unchanged. (The plan's "mail 120" was measured on `820c6803`; today's baseline already read 119/96.)
- **Verification (2026-11-11).** EMS suite 2777 tests, 0 failures (15 skipped); PCS 288, 0 failures; gateway 338, 0 failures; shared-kernel `ContractServerTest` 34 green (pcs-internal now has no operation, so its example check is empty); checkstyle main+test EMS and PCS; `lint:api-internal`, `lint:api`, `check:s2s-client-urls` (0), `check:api-paths`, `make check-test-ratchet` pass. Logs in `~/.cache/batbern-test-logs/v8b/`.
- **Local dev (AC 1, 2), 23:04 CET.** EMS and PCS restarted on the new jars. Through `POST /dev/emails/inbound` (to `replies@`, Content-Type `text/calendar; method=REPLY`, so the production `InboundReplyProcessor` path) for meeting `92fec106…` (BATbern60) and `partner.test8b@example.com`: ACCEPTED recorded 70 ms after the post, DECLINED replaced it 20 ms after the second post; `GET /api/v1/partner-meetings/{id}/rsvps` through the gateway as organizer showed DECLINED. Unknown meeting → WARN answer=404; NEEDS-ACTION → WARN answer=400. PCS stopped: inbound answered 200, exactly one ERROR `CALENDAR_REPLY not delivered` (plus the `ContractCalls` WARN), RSVP unchanged. PCS restarted (healthy). Test RSVP row and the 5 `mail_automatic_event` CALENDAR_* rows removed. The counter's value was not read live (actuator metrics endpoint not exposed locally); it is covered by `HttpCalendarReplyContractTest`.

## Design Notes

**Architecture note.** Modules: EMS mail center (`forwarding`), PCS `controller`. New dependency: none (ShedLock not needed). Endpoint: `POST /internal/calendar-replies` on PCS, owned by the EMS spec; nearest existing is PCS `POST /internal/partner-meetings/rsvps`, which it replaces (the contract belongs to EMS, so the path is named after EMS's event, not PCS's model). Reused: `ContractCalls`, `ContractServer`, provider contract test, `PartnerMeetingRsvpService.upsertRsvp`. Deviation: none.

**Epic 17 rule (C15).** Start: EMS archunit store 2 lines in `98b035c9` (end 0); ratchet `be-mock-s2s-client` InboundEmailRouterTest 1 (should fall). Module scripts before (`820c6803`): mail 120, platform 33, edges 52/27/91; re-run after and record.

## Verification

**Commands:**
- `set -o pipefail; ./gradlew --max-workers=4 :services:event-management-service:test`, then `:services:partner-coordination-service:test`, then `:api-gateway:test` (one at a time, logs to `~/.cache/batbern-test-logs/v8b/`) -- green.
- `cd web-frontend && npm run lint:api-internal && npm run lint:api && cd .. && make check-test-ratchet` -- pass.
- Module scripts from `services/event-management-service/src/main/java/ch/batbern/events` (`python3 …/ems-module-deps.py . <dir>/emsdeps.json`, then `ems-module-target.py <dir>`) -- before/after recorded.

**Manual checks:**
- Local dev: post a sample iMIP REPLY through the dev inbound path (`DevEmailController`), then `GET /api/v1/partner-meetings/{id}/rsvps` shows the status at once.
