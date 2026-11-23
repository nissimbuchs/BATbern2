---
title: 'Audience sends refuse agent tokens'
type: 'feature'
ticket: '7'
created: '2026-11-22'
status: done
baseline_revision: 'a650ec78d02443a23f24e289fef18b9b442d988b'
route: 'full'
route_source: 'auto'
risk: 'medium'
review: 'quick'
review_source: 'pinned'
lenses_ran: ['quick']
review_loop_iteration: 0
followup_review_recommended: false
context:
  - '{project-root}/docs/architecture/coding-standards.md'
warnings: ['oversized']
deferred: []
---

<intent-contract>

## Intent

**Problem:** Since 0.1/0.6 an agent token for an organizer passes `hasRole('ORGANIZER')`, so it could mail a whole audience (Mail Center composer send and newsletter retry in EMS, partner meeting invite in PCS). AD-15 and the epic (R7, decision 2026-11-21) require audience sends to refuse any token carrying `act` with 403.

**Approach:** One shared-kernel guard `ch.batbern.shared.security.AudienceGuard.refuseAgentTokens(String operation)` that throws Spring's `AccessDeniedException` when the current authentication's token carries `act` (detected through story 2's `Actors.carriesAct`). It is called first thing at the service choke points, so every caller is covered: EMS `MailComposerService.send` (all five AudienceAdapters and the organizer test send) and `retry`, plus the bulk entry points under them (`NewsletterEmailService.sendNewsletter`, `sendNewsletterOwnText`, `retryFailedRecipients`; `SlidesOnlineEmailService.sendRegistrantNotice`, `sendRegistrantOwnText`) as defence in depth; PCS `PartnerMeetingService.sendInvite` and `deleteMeeting` when an invite was already sent (it mails a METHOD:CANCEL to every partner contact and organizer).

## Boundaries & Constraints

**Always:** Detection by the `act` claim (`Actors.carriesAct(SecurityContextHolder…getAuthentication())`), not by issuer. The guard runs before any recipient lookup, render, DB write or SES call, so a refused call leaves no `mail_message`, no `newsletter_sends`, no meeting change and nothing in `SesRecorder`. Without an authentication (scheduler, async thread, SQS) and for Cognito, Watch and mock users it does nothing. Both services already map `AccessDeniedException` to 403 in their `GlobalExceptionHandler`; message: `Audience sends refuse agent tokens (AD-15): <operation>`, no username in it. Specs updated first (descriptions of the 403 responses), frontend types regenerated. TDD (guard unit test RED first, then ITs RED before the service calls). Tests: Testcontainers, production chain, real agent tokens from `TestTokens.agentToken(sub, username, run, "organizer")`, CUMS/EMS at the wire (`CumsContractHarness` in EMS, `ContractServer` in PCS), no own-code mocks, no class-level `@Transactional`, synthetic `@simulator.amazonses.com`/`@example.com` addresses only, cleanup in `@AfterEach`. Checkstyle clean. One Gradle module at a time, `--max-workers=4`, logs `set -o pipefail; … 2>&1 | tee ~/.cache/batbern-test-logs/0-7-<name>.log`.

**Never:** No guard on single-recipient sends (speaker invitation, conversation reply, registration confirmation resend, add participant): they stay open. No guard on side-effect mails of binding writes (event cancellation mailing registrants, waitlist promotion on capacity save): recorded as an open question in the epic Notes, owner decides. No change to `@PreAuthorize` expressions, no Spring AOP/meta-annotation (Spring Security refuses two `@PreAuthorize` on one method). No git push/PR, no `make test`, no real mail.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Agent composer send | organizer agent token, `POST /api/v1/events/{code}/mails`, each audience NEWSLETTER, REGISTRANTS, SPEAKERS, VENUE, PARTNERS, target RECIPIENTS | 403; no `mail_message` row for the event, `SesRecorder` empty | `AccessDeniedException` → 403 |
| Agent test send | same, target ORGANIZERS | 403, nothing recorded | as above |
| Agent retry | organizer agent token, `POST …/mails/{mailKey}/retry` on a FAILED newsletter | 403; send row status unchanged, recorder empty | as above |
| Person send | same requests with an organizer Cognito-shaped `jwt()` (sub, `custom:username`, `custom:role=ORGANIZER`, real `JwtAuthenticationConverter`) | 202 and the mail reaches the recorder | — |
| Agent PCS invite | organizer agent token, `POST /api/v1/partner-meetings/{id}/send-invite` | 403; `invite_sent_at`, `invite_sequence` unchanged; recorder empty | as above |
| Agent PCS delete after invite | agent token, `DELETE …/{id}`, `invite_sent_at` set | 403; meeting still there, no cancel mail | as above |
| Agent PCS delete before invite | agent token, no invite sent | 204, nothing mailed (no audience) | — |
| No authentication | async/scheduler call of a guarded method | no refusal | — |
| `act` malformed | token with `act` but no `run` (built directly as a `Jwt` in the unit test) | refused (carriesAct is true) | — |

</intent-contract>

## Code Map

- `shared-kernel/src/main/java/ch/batbern/shared/security/Actors.java` -- `carriesAct(Authentication)` (story 0.2); reuse, do not change. New sibling `AudienceGuard` (final, static, private ctor), Javadoc names AD-15, the act-claim rule and that single-recipient sends stay open.
- `shared-kernel/src/test/java/ch/batbern/shared/security/ActorsTest.java` -- pattern for building real `Jwt` objects and setting `SecurityContextHolder` in a unit test.
- EMS `services/event-management-service/src/main/java/ch/batbern/events/mailcenter/compose/MailComposerService.java:164` `send(...)`, `:244` `retry(...)` -- guard as the first statement. Only callers: `mailcenter/EventMailController.java:203,212`.
- EMS `service/NewsletterEmailService.java:218,229,387`, `service/SlidesOnlineEmailService.java:146,193` (the two `public QueuedSend send…` methods) -- guard as first statement; their only callers are the adapters (checked), their `@Async execute…` methods run without a security context and are not guarded.
- PCS `services/partner-coordination-service/src/main/java/ch/batbern/partners/service/PartnerMeetingService.java:146` `sendInvite`, `:211` `deleteMeeting` (guard inside `if (meeting.getInviteSentAt() != null)` before any lookup) ; controller `controller/PartnerMeetingController.java` unchanged.
- Specs: `docs/api/event-mail-api.openapi.yml` (sendEventMail `403` ~:190 and its `**Security**` line; retryEventMail ~:1167 likewise), `docs/api/partner-meetings-api.openapi.yml` (sendPartnerMeetingInvite `403` ~:271, deletePartnerMeeting `403` ~:231): add "or the caller's token carries `act` (an agent token, AD-15)". Then `cd web-frontend && npm run generate:api-types` and commit any change.
- EMS test harness: `src/test/java/ch/batbern/events/config/CumsContractHarness.java` (`usersWithRole`, `user`, `userWithLanguage`, `reset`); `security/AgentAttributionIntegrationTest.java` (role_assignments stub, seeding user with sub, `bearer()`); `security/ProductionChainIntegrationTest.java:97-106` (Cognito-shaped `jwt()` with real converter); `mailcenter/MailComposerSendIntegrationTest.java` (fixtures per audience: subscriber, registration, venue config via `AdminSettingsService.setSetting`, partners via CUMS role PARTNER, speakers via session + `session_users` PRIMARY_SPEAKER as `controller/ParticipantsControllerIntegrationTest.java:765-781`; request bodies; `awaitUntil`; `sesRecorder.awaitAsyncSends()`). Retry fixture: a `newsletter_sends` row in status FAILED + a `mail_message` row with `newsletter_send_id`, audience NEWSLETTER, as the existing retry test does (grep `retry` in `mailcenter/*IntegrationTest.java`).
- PCS test harness: `src/test/java/ch/batbern/partners/security/AgentAttributionIntegrationTest.java` (stub tables, `grant`, peers bound to EMS internal + CUMS internal); `controller/PartnerMeetingControllerIntegrationTest.java:66-90` (mail recorder `emsInternal` ContractServer bound to `HttpOutboundMailRecorder`'s own RestTemplate, `POST /internal/mails` 202); `GET /internal/users?role=PARTNER|ORGANIZER` answered per spec (page JSON as `CumsInternalUsers.page`).

## Tasks & Acceptance

**Execution:**
- [x] `shared-kernel/src/test/java/ch/batbern/shared/security/AudienceGuardTest.java` -- RED first: agent token (well-formed and `act` without `run`) refused with `AccessDeniedException` naming the operation; Cognito-shaped Jwt, `UsernamePasswordAuthenticationToken`, no authentication pass.
- [x] `shared-kernel/src/main/java/ch/batbern/shared/security/AudienceGuard.java` -- implement -- GREEN; then `:shared-kernel:publishToMavenLocal` if the build needs it.
- [x] Specs + regenerated types -- as in Code Map.
- [x] `services/event-management-service/src/test/java/ch/batbern/events/mailcenter/AudienceGuardIntegrationTest.java` -- RED first: for each of the five audiences (RECIPIENTS) and one ORGANIZERS test send, agent token → 403, no `mail_message` for the event, `SesRecorder.mails()` empty after `awaitAsyncSends`; same request with the organizer's Cognito-shaped token → 202 and the recorder holds the mail (positive control, per audience); retry: agent → 403 and send row unchanged, Cognito → 202.
- [x] EMS guard calls -- as in Code Map -- GREEN.
- [x] `services/partner-coordination-service/src/test/java/ch/batbern/partners/security/AudienceGuardIntegrationTest.java` -- RED first: invite agent → 403, meeting unchanged, recorder empty; Cognito → 202, partner contact got the mail; delete after invite agent → 403 meeting kept, Cognito → 204 and cancel mail recorded; delete before invite agent → 204.
- [x] PCS guard calls -- GREEN.
- [x] Docs: `docs/architecture/module-map.md` reuse row "Mail to an audience" names `AudienceGuard` and the rule; doc-drift mapped docs for touched paths (`.github/doc-drift-mappings.yml`); epic Notes: Open question line on side-effect mails (event cancellation, waitlist promotion).

**Acceptance Criteria:**
- Given an organizer's agent token, when it sends any composer audience, a test send, a newsletter retry, a partner meeting invite, or deletes an invited partner meeting, then the answer is 403 and no mail is recorded or sent.
- Given the same organizer signed in with a Cognito token, when the same requests run, then the mails go out as before (existing mail suites green without assertion edits).
- Given a single-recipient send (speaker invitation) with an agent token, when called, then it is not refused by the guard (no code change there; verified by grep that `AudienceGuard` appears only at the listed choke points).

## Implementation Notes

- Guard: `shared-kernel/.../security/AudienceGuard.java`, called at exactly the nine choke points of the Code Map (grep checked; no single-recipient send calls it).
- RED observed before each GREEN: unit test failed to compile without the class; EMS IT 7/7 failed with 202 instead of 403; PCS IT 2/3 failed with 202/204 instead of 403 (the delete-before-invite case passes by design).
- EMS IT asserts the guard's message in the 403 body. The newsletter/registrant service-level guards are defence in depth behind the composer guard and are not reached separately by an IT.
- Docs: module map reuse row, 06-backend-architecture ("Audience sends refuse agent tokens"), 06d (#1179 section), new doc-drift mapping for `AudienceGuard.java`, epic Notes open question on side-effect mails.

## Plan Change Log

- 2026-11-22: PCS's `GlobalExceptionHandler` answers every `AccessDeniedException` with the generic message "Access denied" (it never echoes the exception). Left unchanged (changing it would expose the messages of every other PCS refusal); the PCS IT asserts the 403 only and tells the guard apart from a role refusal by the positive control. EMS returns the guard's message as planned.

## Review Triage Log

### 2026-11-22 — Review pass
- verdicts: 6 findings — high 0, medium 1, low 5, false 0, maybe-false 0
- findings:
  - `[low]` `[patch]` EMS IT test-send and retry agent cases asserted only 403, so a 403 from another cause would pass — fixed: both assert the guard's message (`mail-center send`, `mail-center retry`).
  - `[low]` `[patch]` PCS IT delete-after-invite did not assert that no mail record was posted to EMS — fixed: agent case asserts `recordedMails` empty, person case asserts a record arrived.
  - `[medium]` `[patch]` EMS IT positive controls polled `SesRecorder` with a Thread.sleep loop, against coding-standards "Production setup in tests" → Mail — fixed: `sesRecorder.awaitAsyncSends()` then direct assertions; helper removed.
  - `[low]` `[patch]` PCS `deleteMeeting` logged "Deleting partner meeting" before the guard, so a refused agent delete logged a deletion — fixed: log line moved to just before `meetingRepository.delete`.
  - `[low]` `[reject]` the defence-in-depth guards in `NewsletterEmailService`/`SlidesOnlineEmailService` are not reached by a test of their own — every caller goes through the composer guard (checked), so nobody meets the gap in use, and the fix adds test scaffolding for unreachable paths; the grep in Implementation Notes is their check.
  - `[low]` `[patch]` `deletePartnerMeeting` 403 description claimed every agent token is refused, though only a meeting whose invite was sent is — fixed: description names the condition; frontend types regenerated.

## Design Notes

Architecture note: modules touched -- shared-kernel security (L0), EMS mailcenter/compose + newsletter/slides-online services, PCS partner meeting service, two specs (descriptions), generated frontend types, module map. New dependencies -- none. New endpoints -- none. Reused -- `Actors.carriesAct`, existing `AccessDeniedException` → 403 handlers, `TestTokens`, `CumsContractHarness`, `ContractServer`, `SesRecorder`. Deviations -- none; story 4's `!@securityContextHelper.carriesAgentAct()` in `@PreAuthorize` is the controller-level form, the service-level guard is chosen here because one choke point covers every adapter and any future caller.

```java
public SendResult send(String eventCode, ComposeRequest request, Target target, String organizerUsername) {
    AudienceGuard.refuseAgentTokens("mail-center send");
    ...
```

## Verification

**Commands:**
- `set -o pipefail; ./gradlew :shared-kernel:test :shared-kernel:publishToMavenLocal --max-workers=4 2>&1 | tee ~/.cache/batbern-test-logs/0-7-shared-kernel.log` -- expected: BUILD SUCCESSFUL
- `set -o pipefail; ./gradlew :services:event-management-service:test --max-workers=4 2>&1 | tee ~/.cache/batbern-test-logs/0-7-ems.log` -- expected: BUILD SUCCESSFUL
- `set -o pipefail; ./gradlew :services:partner-coordination-service:test --max-workers=4 2>&1 | tee ~/.cache/batbern-test-logs/0-7-pcs.log` -- expected: BUILD SUCCESSFUL
- `set -o pipefail; ./gradlew checkstyleMain checkstyleTest --max-workers=4 2>&1 | tee ~/.cache/batbern-test-logs/0-7-checkstyle.log` -- expected: BUILD SUCCESSFUL
- `make check-test-ratchet` -- expected: no RAISED line

## Auto Run Result

**Summary:** Shared-kernel `AudienceGuard.refuseAgentTokens(operation)` refuses a token carrying `act` (via `Actors.carriesAct`) with `AccessDeniedException` → 403, as the first statement of every audience send: EMS `MailComposerService.send` (all five adapters and the organizer test send) and `retry`, plus defence in depth on the newsletter and registrant bulk sends; PCS `PartnerMeetingService.sendInvite` and `deleteMeeting` of an invited meeting (its METHOD:CANCEL mails every partner contact and organizer). Single-recipient sends stay open. No auth, Cognito, Watch and mock users pass.

**Files:** shared-kernel `AudienceGuard` + `AudienceGuardTest`; EMS `MailComposerService`, `NewsletterEmailService`, `SlidesOnlineEmailService`, `AudienceGuardIntegrationTest`; PCS `PartnerMeetingService`, `AudienceGuardIntegrationTest`; specs `event-mail-api`, `partner-meetings-api` (403 descriptions) and regenerated frontend types; docs module-map, 06-backend-architecture, 06d-notification-system, doc-drift mapping; epic Notes open question.

**Review:** quick lens, 6 findings: 5 patched (1 medium, 4 low), 0 deferred, 1 rejected (low: defence-in-depth guards behind the composer guard have no separate test; unreachable in use).

**Follow-up review recommended:** false (one medium patched, test-only).

**Verification (after the patches):** shared-kernel 810/0 (before patches; patches touched no shared-kernel code); PCS 321/0; EMS 3158/0 (6 skipped); checkstyle main+test clean; `make check-test-ratchet` no RAISED line. RED observed first: EMS IT 7/7 failed with 202, PCS 2/3 failed with 202/204. An earlier EMS full run had one failure in `AiAssistControllerIntegrationTest` (random event number collision), unrelated; green on rerun and in the final full run.

**Residual risks:** no run against local dev; side-effect mails of binding writes (event cancellation, waitlist promotion on capacity save) not guarded, open question in the epic; PCS 403 body carries its generic "Access denied", not the guard's reason; an agent invite to an unknown meeting now gets 403 instead of 404.
