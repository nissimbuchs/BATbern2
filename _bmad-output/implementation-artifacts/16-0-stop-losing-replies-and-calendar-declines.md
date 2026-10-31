# Story 16.0: Stop losing replies and calendar declines (#1160, #1161, #1162)

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As an **organizer**,
I want **human replies to our automated mails to reach the team, and a registrant's calendar "Declined" to free their seat**,
so that **no speaker question or newsletter reply is silently dropped, and people on the waitlist get the seats of people who already said no**.

> Source: `docs/prd/epic-16-mail-center.md` → **Story 16.0**. GitHub issues **#1160** and **#1161**.
> Stand-alone fixes, deployable before the rest of Epic 16. Three parts, **three PRs**: Part A closes #1160, Part B closes #1161, Part C closes #1162. Part A first, it is live data loss. Part C touches the same router as Part A; do it right after A.

## Acceptance Criteria

**Part A — Replies reach a person (#1160)**

1. **AC1 (speaker Reply-To)** — GIVEN an event with number N, WHEN the platform sends a speaker-facing mail (invitation, acceptance, tiered reminder, quality review), THEN its Reply-To is `batbern{N}-moderator@batbern.ch`, not `replies@`. Owner decision 2026-10-29, recorded on #1160.
2. **AC2 (`-speaker@` broadcasts)** — GIVEN an organizer mails `batbern{N}-speaker@`, WHEN the forwarder rewrites it, THEN Reply-To is `batbern{N}-moderator@batbern.ch` instead of the organizer's own address. Other aliases keep Reply-To = original sender. Owner decision 4, 2026-10-30.
3. **AC3 (no silent discard)** — GIVEN a mail to `replies@` that matches no keyword and is not an iCal REPLY, WHEN `InboundEmailRouter` processes it, THEN it is forwarded to the organizers (same recipients and same rewrite as `info@`) instead of being discarded. Covers newsletter, registrant and any remaining replies (#1160 option 1, owner decision 5).
4. **AC4 (no loops, no noise)** — Auto-replies (`Auto-Submitted` other than `no`, out-of-office patterns already known to `MailClassifier`), bounces and mail from our own domain are NOT forwarded under AC3. Each forward and each skip is counted in `ForwardingMetrics` and written to `email_forwarding_log`.
5. **AC5 (keyword paths unchanged)** — unsubscribe / cancel / accept keywords and partner-meeting iCal REPLY behave exactly as today (existing tests stay green).

**Part B — Calendar declines release the seat (#1161)**

6. **AC6 (decline → confirmation mail)** — GIVEN a registrant with an active registration who declines the registration calendar invite, WHEN the iMIP `METHOD:REPLY` with `PARTSTAT=DECLINED` reaches `calendar@`, THEN the registrant receives the existing deregistration-link mail (`DeregistrationEmailService.sendDeregistrationLinkEmail`). The registration is NOT cancelled until they click the link (#1161 option 1, the issue's recommendation, with its safeguard).
7. **AC7 (matching)** — New registration invites carry a stable UID that identifies the registration (see Dev Notes). Older invites with a random UID are matched by sender address + event (SUMMARY/DTSTART against events within ±1 day). No match, or more than one, → no action, counted as `CalendarDeclineUnmatched`.
8. **AC8 (other replies)** — `ACCEPTED` / `TENTATIVE` replies to `calendar@` stay dropped, as today. Partner-meeting replies are untouched.
9. **AC9 (one mail only)** — A second decline for the same registration within 7 days does not send a second mail.
10. **AC10 (de-dup storage)** — The 7-day check of AC9 uses a new nullable column `registrations.decline_link_sent_at` (additive migration, same shape as the existing `confirmation_resent_at`). Set when the mail is sent; AC9 compares against it.

**Part C — Keyword replies ask before acting (#1162)**

11. **AC11** — GIVEN a reply to `replies@` whose first line contains `cancel`, `deregister` or `absagen` and whose subject has an event code, WHEN routed, THEN the registration is NOT cancelled; the sender gets the deregistration-link mail (as in AC6, de-duplicated via AC10).
12. **AC12** — Keywords act only when the first line is essentially the keyword alone (e.g. `cancel`, `Absagen`, `cancel please`, at most 3 words). A longer sentence that merely contains a keyword is treated as a human reply and forwarded per AC3.
13. **AC13** — The same rule (AC12) applies to the unsubscribe (`abmelden`, `unsubscribe`) and accept keywords. Unsubscribe keeps acting immediately (it is reversible and legally required to be easy).

**All parts**

14. **AC14** — No test sends real mail. Send-path checks run against local dev (`/dev/mails`), never staging (= production).

## Tasks / Subtasks

### Part A — #1160 (PR 1, body `Fixes #1160`)

- [x] **Task A1 — Moderator Reply-To on speaker mails** (AC: 1)
  - [x] One helper that builds `batbern{N}-moderator@{forwarding-domain}` from an event, reusing the alias format of `ForwardingAlias` / `DistributionListService` (do not hand-build the string in each service).
  - [x] Pass it as `replyTo` from `SpeakerInvitationEmailService`, `SpeakerAcceptanceEmailService`, `SpeakerReminderEmailService`, `QualityReviewService`. `EmailService.sendHtmlEmailSync(..., replyTo)` already supports the override (line ~167); add an async overload with `replyTo` if a caller needs it.
  - [x] Unit tests per service asserting the Reply-To.
- [x] **Task A2 — `-speaker@` broadcast Reply-To** (AC: 2)
  - [x] `ForwardedMessageRewriter.rewrite` (line ~48 sets Reply-To = sender): add a `replyToOverride` parameter; `EmailForwardingService` passes the moderator alias when the destination alias is `-speaker@`.
  - [x] Test: `-speaker@` → moderator Reply-To; `info@` → sender Reply-To.
- [x] **Task A3 — Forward unrecognized replies** (AC: 3, 4, 5)
  - [x] Replace the discard branch in `InboundEmailRouter` (step 6, "Unrecognized — silent discard") with a forward to the organizer recipients via `EmailForwardingService` (raw MIME from the same S3 object).
  - [x] Auto-reply / bounce / own-domain filter before forwarding; metrics + forwarding log entries.
  - [x] Integration test with a fixture MIME (synthetic, `@example.com`).
- [x] **Task A4 — Docs** — check `.github/doc-drift-mappings.yml` for the changed paths and update the mapped doc in the same commit; if none is mapped, `[no-doc]` in the commit message.

### Part B — #1161 (PR 2, body `Fixes #1161`)

- [x] **Task B0 — Size it first** (no code): count `CalendarRepliesDropped` for the last weeks (CloudWatch metric `BATbern/EmailForwarder`) and, if possible without a DB dump, check a few decline senders against registrations via the organizer API. Write the numbers into Completion Notes. Never download production data.
- [x] **Task B1 — Stable registration UID** (AC: 7)
  - [x] `RegistrationEmailService.generateCalendarFile` currently gets a random UID from `IcsCalendarService.generateIcsFile` (`UUID.randomUUID() + "@batbern.ch"`). `generateIcsFile(...)` has no UID parameter; the stable UID exists only on `IcsCalendarService.IcsEventData` (used by `generateMultiEventIcsFile` / `generateRequestIcsFile`). Add a UID-taking overload of `generateIcsFile` (or switch to the `IcsEventData` path) and use `reg-{registrationCode}@batbern.ch`. Check whether `registrationCode` is safe to expose in an ICS (it is already in the deregistration links). → done with a deviation: the code is a credential, so the UID is `reg-{calendarUid}` (see Completion Notes, Part B).
- [x] **Task B2 — Decline handling** (AC: 6, 7, 8, 9)
  - [x] In `EmailForwardingService.forward`, the calendar-reply branch currently drops everything (`DROPPED_CALENDAR_REPLY`). Before dropping: parse PARTSTAT and UID (reuse `InboundEmailListenerService.extractIcsProperty` or move it to a shared parser); if `DECLINED` and the alias is `calendar@`, hand off to a new `CalendarDeclineService`.
  - [x] `CalendarDeclineService`: match (UID first, fallback per AC7), check active status, de-dup (AC9), send the deregistration-link mail, metrics.
  - [x] Integration tests: matched by UID, matched by fallback, ambiguous, already cancelled, duplicate within 7 days, accepted reply ignored.
- [x] **Task B2b — De-dup column** (AC: 9, 10): migration `V{next}__registrations_decline_link_sent_at.sql` (`ALTER TABLE registrations ADD COLUMN decline_link_sent_at TIMESTAMPTZ`); entity field next to `confirmationResentAt`.
- [x] **Task B3 — Docs** — doc-drift check as in A4, plus a short note in the registration docs that a calendar decline triggers the deregistration-link mail.

### Part C — #1162 (PR 3, body `Fixes #1162`)

- [x] **Task C1 — Short-line rule** (AC: 12, 13): in `InboundEmailRouter.route`, keyword steps 3–5 act only if the normalized first line has at most 3 words; otherwise fall through to the AC3 forward. Table-driven unit test with real-life sentences (DE + EN) that must NOT trigger.
- [x] **Task C2 — Cancel asks first** (AC: 11): replace `registrationService.cancelByEmail` + `sendCancelConfirmation` in step 4 with the deregistration-link mail, reusing the Part B service and the AC10 column.
- [x] **Task C3 — Docs**: as in A4.

## Dev Notes

- **Order:** A, then C (same file, `InboundEmailRouter`), then B. B and C both send the deregistration-link mail; whichever lands second reuses the first one's helper.

- **Reply-To today:** global `app.email.reply-to` = `replies@batbern.ch` (`event-management-service/src/main/resources/application.yml:109`). Only `VenueCoordinationService` overrides it (`.replyToEmail(coordinator.email())`, line ~114). Copy that pattern.
- **`-moderator@`** resolves to `Event.organizerUsername` (the lead organizer) plus their additional emails (`DistributionListService`), not to `SessionUser.MODERATOR`. It is open to any sender (`ForwardingAlias.openToAnyone`), so speakers can write to it.
- **Discard branch:** `InboundEmailRouter.java`, step 6, `log.warn("Unrecognized inbound email body ... discarding")`. CloudWatch keeps EMS logs 30 days; a search on 2026-10-30 found no such line, but a real lost newsletter reply was found in S3 on the same day (see #1160 comment). Inbound S3 keeps raw MIME 7 days.
- **Calendar drop:** `EmailForwardingService.forward` checks `MailClassifier.isCalendarReply` first and returns `DROPPED_CALENDAR_REPLY` for every alias. The registration ICS has ORGANIZER `calendar@batbern.ch` (`app.email.calendar-organizer-email`) and no ATTENDEE line, so the sender address is the only identity in old replies.
- **Measured (#1161, 23–30 Oct):** 14 calendar declines, 13 of them for the event registration; event was full with a waitlist of 32. `CalendarRepliesDropped` per month: May 8, Jun 14, Jul 45, Aug 10, Sep 2, Oct 39.
- **Seat release** is the existing deregistration flow; the waitlist promotion that follows is existing behaviour. Do not add an automatic cancel.
- **PII:** fixtures synthetic only (`@example.com`, SES simulator). The GitHub issues are public: no names in PR text.

### References

- GitHub #1160, #1161 (with comments)
- `docs/prd/epic-16-mail-center.md`
- `services/event-management-service/.../service/InboundEmailRouter.java`
- `services/event-management-service/.../forwarding/EmailForwardingService.java`, `ForwardedMessageRewriter.java`, `ForwardingAlias.java`, `MailClassifier.java`, `ForwardingMetrics.java`
- `services/event-management-service/.../service/RegistrationEmailService.java`, `DeregistrationEmailService.java`, `InboundEmailListenerService.java`
- `shared-kernel/.../service/EmailService.java`, `IcsCalendarService.java`

## Dev Agent Record

### Agent Model Used

Claude Opus 5.5 (Amelia, bmad-dev-story), 2026-10-30.

### Debug Log References

- 2026-10-30 ~13:54: full CDK jest with `--maxWorkers=4` hit rack's 14 GB `user.slice` cap (4 workers × ~2.5 GB + idle Gradle daemon 1.9 GB + 1.5 GB `cdk.out*` on tmpfs). The OOM killer thrashed ~30 min and the session died; no reboot, git state intact. Rerun with `./gradlew --stop`, `/tmp/cdk.out*` cleared, `--maxWorkers=2`: green.
- `SpeakerModeratorReplyToTest` first run: NPE in `Map.entry` because the `@Value` fields of `SpeakerReminderEmailService` are null outside Spring; fixed in the test with `ReflectionTestUtils`, not in production code.

### Completion Notes List

**Part A (#1160), branch `feature/16-0a-replies-reach-a-person`**

- AC1: `ModeratorReplyAddress` (forwarding package) builds `batbern{N}-moderator@{forwarding domain}` via `ForwardingAlias.moderatorAddress`; returns null for event codes that are not `BATbern{N}`, which falls back to the global `replies@`. Passed as Reply-To by `SpeakerInvitationEmailService`, `SpeakerAcceptanceEmailService`, `SpeakerReminderEmailService`, `QualityReviewService`. To keep their async behaviour, `EmailService` got one async overload `sendHtmlEmail(to, cc, subject, html, replyTo)` (shared kernel; story allowed it).
- AC2: `ForwardedMessageRewriter.rewrite` has a `replyToOverride` overload; `EmailForwardingService` passes the moderator alias of the first `-speaker@` alias in a broadcast. All other aliases keep Reply-To = sender (existing `info@` test unchanged).
- AC3: `InboundEmailRouter.route` now returns `RouteResult` (HANDLED, RATE_LIMITED, UNRECOGNIZED) instead of discarding. `InboundEmailListenerService` forwards UNRECOGNIZED mail via the new `EmailForwardingService.forwardUnrecognizedReply(s3Key, raw)`: all organizers, one private copy each, same rewrite as `info@`. Design deviation from the task text: the router does not forward itself (it has no raw MIME); it returns the result and the listener, which holds the S3 bytes, forwards. Same behaviour, router stays free of S3.
- AC3, extension: a cancel/accept keyword **without** an event code in the subject used to be dropped too; it now counts as UNRECOGNIZED and is forwarded, since nothing could act on it.
- AC3, retry: the forward runs outside the listener's catch-all. A failure before the first send (e.g. CUMS down) is rethrown so the replies queue retries and dead-letters, instead of being lost. Safe because the router ran no action for that mail.
- AC4: not forwarded and logged: own domain / forwarded marker (`dropped_loop`), bounces (`MailClassifier.isBounce`: `MAILER-DAEMON@`/`postmaster@`, `multipart/report`, `Return-Path: <>`; `dropped_bounce`), automatic replies (existing `isAutoReply`; `dropped_auto_reply`), no single From (`dropped_malformed`). Metrics `RepliesForwarded`, `BouncesDropped`, plus the existing `LoopsDropped`/`AutoRepliesDropped`. Migration **V131** extends the outcome CHECK constraint. Redelivery of the same S3 key is a no-op via the existing claim.
- AC5: keyword paths unchanged; all existing router tests stay green (three now also assert the result).
- AC14 / local dev: `DevEmailController` reply simulation forwards UNRECOGNIZED replies through the same service, so the organizer copy lands in `/dev/mails`. **Verified live on local dev 2026-10-30** (profile `local`, no SES client): registered an SES-simulator address on test event BATbern89608, simulated a human reply → 4 organizer copies captured, Reply-To = the registrant, `X-BATbern-Forwarded: 1`, log row `forwarded | replies | 4 | 0`, V131 applied. Simulated `accept` → acceptance action ran, nothing forwarded. Found and fixed on the way: the simulator wrote the UTF-8 subject unencoded (displayed as `bestÃ¤tigen`); it now uses RFC 2047. Test rows deleted from the local DB afterwards.
- Speaker-facing mails audited: the four listed services plus `QnaNotificationService` (Q&A digest to presenters). The digest keeps `replies@`; a reply to it is now forwarded to the organizers by AC3, so nothing is lost. Not changed, out of the AC1 list.
- Owner request 2026-10-30 (in Part A): CloudWatch alarm `batbern-{env}-replies-dlq` on the replies DLQ (`InboundEmailStack`), with SNS alarm + OK actions like the forwarding DLQ. Before, the replies DLQ had no alarm, so a reply dead-lettered by the new retry would have sat there unseen. CDK unit test added. Makes the Part A PR a layer-based infra deploy. CI on #1164 then failed `incident-response-docs.test.ts` (every declared alarm needs a runbook entry); I had run only the stack's own test locally. Fixed with a `replies-dlq` entry in `docs/operations/runbooks.md`; full infra suite 483 passed / 19 skipped, run with `--maxWorkers=2` (a 4-worker run OOM-killed the session on rack, see Debug Log).
- A4: `docs/architecture/06-backend-architecture.md` and `docs/user-guide/features/notifications.md` updated (the user guide said "silently discarded").

**Part C (#1162), branch `feature/16-0c-keyword-replies-ask-first` (cut from Part A)**

- Order A → C → B as the Dev Notes say, so Part C introduces the shared pieces and Part B reuses them: migration **V132** (`registrations.decline_link_sent_at`, Task B2b done here), `Registration.declineLinkSentAt`, `RegistrationRepository.claimDeregistrationLink` (one conditional UPDATE, so two concurrent requests cannot both send), `DeregistrationService.sendDeregistrationLinkOnce(Registration)` and `requestDeregistrationLinkByReply(email, eventCode)` returning SENT / ALREADY_SENT / NOT_FOUND.
- AC11: router step 4 calls `requestDeregistrationLinkByReply` instead of `cancelByEmail` + cancel confirmation. Nothing is cancelled until the link is clicked. Sender never learns the outcome (anti-enumeration).
- AC12/AC13: router acts on keywords only when the normalized first line has at most 3 words; otherwise UNRECOGNIZED → forwarded (Part A). Applies to unsubscribe, cancel and accept; unsubscribe still acts at once. Table-driven tests: 10 short forms that act, 8 real-life DE/EN sentences that must not.
- Dead code removed: `RegistrationService.cancelByEmail` and `InboundEmailConfirmationEmailService.sendCancelConfirmation` had no callers left. The `cancel-confirmation-{de,en}` templates are still seeded into the admin template store but are no longer sent; left in place (removing seeded templates is a separate decision).
- Tests: `DeregistrationLinkOnceIntegrationTest` (Testcontainers, 4 cases: first sends + records, second within 7 days does not, after 8 days sends again, cancelled/unknown sends nothing); router 38/38. Full EMS suite 2162 tests, 0 failures; checkstyle clean.
- **Verified live on local dev 2026-10-30**: V132 applied; reply "Bitte absagen" → one deregistration-link mail captured, registration still `registered`, `decline_link_sent_at` set; second reply "cancel" → ALREADY_SENT, no mail; "Ich muss leider absagen, kann ich den Platz weitergeben?" → forwarded to 4 organizers. Test rows deleted afterwards.
- C3: `docs/user-guide/features/notifications.md` (CANCEL bullet, short-line rule) and `docs/architecture/06-backend-architecture.md`.

**Part B (#1161), branch `feature/16-0b-calendar-declines-free-seats` (cut from Part C)**

- B0 (sizing, read-only CloudWatch, no prod data downloaded): `CalendarRepliesDropped` weekly sums 2026-10-16..22: 1, 10-23..29: 38. That metric counts all calendar RSVPs (accept/decline/tentative). The decline count (14 in 23–30 Oct, 13 for the event registration) comes from #1161's S3 inspection. Not repeated: cross-checking decline senders against registrations would need reading raw production mail.
- B1, **deviation from the task text**: the UID is NOT `reg-{registrationCode}`. The story asked to check whether the code is safe in an ICS: it is not. `SecurityConfig` permits `GET /events/*/registrations/{code}` anonymously ("the code is the credential"), and a calendar UID travels with every forwarded or shared entry; a hash of the code is brute-forceable (short random suffix, known prefix). Instead a new non-credential column `registrations.calendar_uid` (V133, random UUID, unique, default for existing rows); invite UID `reg-{calendarUid}@batbern.ch` via `CalendarInviteUid` (one class for writing and parsing). Shared kernel: `IcsCalendarService.generateIcsFile(IcsEventData, organizerEmail, organizerName)` overload (the 8-param variant violated Checkstyle `ParameterNumber`); the legacy 7-arg variant delegates with a random UID. Side effect: a re-sent invite now updates the same calendar entry.
- B2: `EmailForwardingService.forward` hands a `PARTSTAT=DECLINED` addressed to `calendar@` to `CalendarDeclineService` (Result `CALENDAR_DECLINE`); accepts, tentatives and replies to other aliases are dropped exactly as before (AC8). Matching (AC7): UID first, else sender + event date within ±1 day of DTSTART, only when exactly one active registration fits; otherwise `CalendarDeclineUnmatched`. Then `DeregistrationService.sendDeregistrationLinkOnce` (Part C helper, AC9/AC10 de-dup). Nothing is cancelled. New `CalendarReply` parser (multipart, folded lines, PARTSTAT anywhere in ATTENDEE, UTC/TZID/date DTSTART). `InboundEmailListenerService`'s own ICS parsing for partner-meeting RSVPs on `replies@` is left untouched (different queue, different fields); not merged into the new parser to keep partner RSVPs out of this change.
- Tests: `CalendarReplyTest` 3, `CalendarInviteUidTest` 2, `CalendarDeclineServiceIntegrationTest` 6 (Testcontainers: UID match incl. reply from another address, fallback match, ambiguous, no event near the date, already cancelled, second decline within 7 days), `EmailForwardingServiceTest` +4, `RegistrationEmailServiceTest` asserts the UID and that the registration code is not in it, shared-kernel `IcsCalendarServiceTest` +2. Full suites: shared-kernel 449, EMS 2177, 0 failures; checkstyle clean.
- **Verified live on local dev 2026-10-30**: V133 applied over existing rows; registration confirmed via `accept` reply → captured `event.ics` has `UID:reg-224cdf5a-…@batbern.ch` equal to the row's `calendar_uid`, ORGANIZER `calendar@batbern.ch`. **Not verifiable locally**: the decline itself, because the forwarding SQS listener does not run on local dev and there is no dev simulator for it; covered by the Testcontainers and unit tests only. First real check after deploy: metric `CalendarDeclineLinkSent` in `BATbern/EmailForwarder`.
- B3: `docs/user-guide/features/notifications.md` (declining the invite), `docs/architecture/06-backend-architecture.md`.

### File List

**Part A**
- `shared-kernel/src/main/java/ch/batbern/shared/service/EmailService.java` (M)
- `shared-kernel/src/test/java/ch/batbern/shared/service/EmailServiceTest.java` (M)
- `services/event-management-service/src/main/java/ch/batbern/events/forwarding/ModeratorReplyAddress.java` (A)
- `services/event-management-service/src/main/java/ch/batbern/events/forwarding/ForwardingAlias.java` (M)
- `services/event-management-service/src/main/java/ch/batbern/events/forwarding/ForwardedMessageRewriter.java` (M)
- `services/event-management-service/src/main/java/ch/batbern/events/forwarding/MailClassifier.java` (M)
- `services/event-management-service/src/main/java/ch/batbern/events/forwarding/ForwardingMetrics.java` (M)
- `services/event-management-service/src/main/java/ch/batbern/events/forwarding/EmailForwardingService.java` (M)
- `services/event-management-service/src/main/java/ch/batbern/events/domain/EmailForwardingOutcome.java` (M)
- `services/event-management-service/src/main/java/ch/batbern/events/service/InboundEmailRouter.java` (M)
- `services/event-management-service/src/main/java/ch/batbern/events/service/InboundEmailListenerService.java` (M)
- `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerInvitationEmailService.java` (M)
- `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerAcceptanceEmailService.java` (M)
- `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerReminderEmailService.java` (M)
- `services/event-management-service/src/main/java/ch/batbern/events/service/QualityReviewService.java` (M)
- `services/event-management-service/src/main/java/ch/batbern/events/controller/DevEmailController.java` (M)
- `services/event-management-service/src/main/resources/db/migration/V131__email_forwarding_log_dropped_outcomes.sql` (A)
- `services/event-management-service/src/test/java/ch/batbern/events/forwarding/EmailForwardingRepliesIntegrationTest.java` (A)
- `services/event-management-service/src/test/java/ch/batbern/events/forwarding/EmailForwardingServiceTest.java` (M)
- `services/event-management-service/src/test/java/ch/batbern/events/forwarding/ForwardedMessageRewriterTest.java` (M)
- `services/event-management-service/src/test/java/ch/batbern/events/forwarding/ForwardingAliasTest.java` (M)
- `services/event-management-service/src/test/java/ch/batbern/events/forwarding/MailClassifierTest.java` (M)
- `services/event-management-service/src/test/java/ch/batbern/events/service/InboundEmailRouterTest.java` (M)
- `services/event-management-service/src/test/java/ch/batbern/events/service/InboundEmailListenerServiceTest.java` (M)
- `services/event-management-service/src/test/java/ch/batbern/events/service/SpeakerInvitationEmailServiceTest.java` (M)
- `services/event-management-service/src/test/java/ch/batbern/events/service/SpeakerAcceptanceEmailServiceTest.java` (M)
- `services/event-management-service/src/test/java/ch/batbern/events/service/SpeakerModeratorReplyToTest.java` (A)
- `services/event-management-service/src/test/java/ch/batbern/events/controller/DevEmailControllerReplyTest.java` (M)
- `services/event-management-service/src/test/resources/mail/human-reply.eml` (A)
- `services/event-management-service/src/test/resources/mail/ooo-reply.eml` (A)
- `infrastructure/lib/stacks/inbound-email-stack.ts` (M)
- `infrastructure/test/unit/inbound-email-stack.test.ts` (M)
- `docs/architecture/08-operations-security.md` (M)
- `docs/operations/runbooks.md` (M)
- `docs/architecture/06-backend-architecture.md` (M)
- `docs/user-guide/features/notifications.md` (M)
- `_bmad-output/implementation-artifacts/16-0-stop-losing-replies-and-calendar-declines.md` (M)
- `_bmad-output/implementation-artifacts/sprint-status.yaml` (M)

**Part B**
- `shared-kernel/src/main/java/ch/batbern/shared/service/IcsCalendarService.java` (M)
- `shared-kernel/src/test/java/ch/batbern/shared/service/IcsCalendarServiceTest.java` (M)
- `services/event-management-service/src/main/resources/db/migration/V133__registrations_calendar_uid.sql` (A)
- `services/event-management-service/src/main/java/ch/batbern/events/domain/Registration.java` (M)
- `services/event-management-service/src/main/java/ch/batbern/events/repository/RegistrationRepository.java` (M)
- `services/event-management-service/src/main/java/ch/batbern/events/service/CalendarInviteUid.java` (A)
- `services/event-management-service/src/main/java/ch/batbern/events/service/CalendarDeclineService.java` (A)
- `services/event-management-service/src/main/java/ch/batbern/events/service/RegistrationEmailService.java` (M)
- `services/event-management-service/src/main/java/ch/batbern/events/forwarding/CalendarReply.java` (A)
- `services/event-management-service/src/main/java/ch/batbern/events/forwarding/EmailForwardingService.java` (M)
- `services/event-management-service/src/main/java/ch/batbern/events/forwarding/ForwardingMetrics.java` (M)
- `services/event-management-service/src/test/java/ch/batbern/events/forwarding/CalendarReplyTest.java` (A)
- `services/event-management-service/src/test/java/ch/batbern/events/forwarding/EmailForwardingServiceTest.java` (M)
- `services/event-management-service/src/test/java/ch/batbern/events/service/CalendarInviteUidTest.java` (A)
- `services/event-management-service/src/test/java/ch/batbern/events/service/CalendarDeclineServiceIntegrationTest.java` (A)
- `services/event-management-service/src/test/java/ch/batbern/events/service/RegistrationEmailServiceTest.java` (M)
- `docs/user-guide/features/notifications.md` (M)
- `docs/architecture/06-backend-architecture.md` (M)

**Part C**
- `services/event-management-service/src/main/resources/db/migration/V132__registrations_decline_link_sent_at.sql` (A)
- `services/event-management-service/src/main/java/ch/batbern/events/domain/Registration.java` (M)
- `services/event-management-service/src/main/java/ch/batbern/events/repository/RegistrationRepository.java` (M)
- `services/event-management-service/src/main/java/ch/batbern/events/service/DeregistrationService.java` (M)
- `services/event-management-service/src/main/java/ch/batbern/events/service/InboundEmailRouter.java` (M)
- `services/event-management-service/src/main/java/ch/batbern/events/service/RegistrationService.java` (M)
- `services/event-management-service/src/main/java/ch/batbern/events/service/InboundEmailConfirmationEmailService.java` (M)
- `services/event-management-service/src/test/java/ch/batbern/events/service/DeregistrationLinkOnceIntegrationTest.java` (A)
- `services/event-management-service/src/test/java/ch/batbern/events/service/InboundEmailRouterTest.java` (M)
- `docs/user-guide/features/notifications.md` (M)
- `docs/architecture/06-backend-architecture.md` (M)

## Change Log

- 2026-10-30: Story drafted from #1160 and #1161 (John, PM).
- 2026-10-30: Readiness fixes: de-dup storage named (AC10); Part C for #1162 added.
- 2026-10-30: Part A (#1160) implemented on `feature/16-0a-replies-reach-a-person` (Amelia).
- 2026-10-30: Part A PR #1164 opened (owner go). Part C (#1162) implemented on `feature/16-0c-keyword-replies-ask-first`, incl. the shared de-dup column for Part B (Amelia).
- 2026-10-30: Part B (#1161) implemented on `feature/16-0b-calendar-declines-free-seats`. All tasks done; status review (Amelia).
