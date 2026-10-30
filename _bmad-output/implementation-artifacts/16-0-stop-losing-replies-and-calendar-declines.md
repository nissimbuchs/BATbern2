# Story 16.0: Stop losing replies and calendar declines (#1160, #1161, #1162)

Status: in-progress

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

- [ ] **Task B0 — Size it first** (no code): count `CalendarRepliesDropped` for the last weeks (CloudWatch metric `BATbern/EmailForwarder`) and, if possible without a DB dump, check a few decline senders against registrations via the organizer API. Write the numbers into Completion Notes. Never download production data.
- [ ] **Task B1 — Stable registration UID** (AC: 7)
  - [ ] `RegistrationEmailService.generateCalendarFile` currently gets a random UID from `IcsCalendarService.generateIcsFile` (`UUID.randomUUID() + "@batbern.ch"`). `generateIcsFile(...)` has no UID parameter; the stable UID exists only on `IcsCalendarService.IcsEventData` (used by `generateMultiEventIcsFile` / `generateRequestIcsFile`). Add a UID-taking overload of `generateIcsFile` (or switch to the `IcsEventData` path) and use `reg-{registrationCode}@batbern.ch`. Check whether `registrationCode` is safe to expose in an ICS (it is already in the deregistration links).
- [ ] **Task B2 — Decline handling** (AC: 6, 7, 8, 9)
  - [ ] In `EmailForwardingService.forward`, the calendar-reply branch currently drops everything (`DROPPED_CALENDAR_REPLY`). Before dropping: parse PARTSTAT and UID (reuse `InboundEmailListenerService.extractIcsProperty` or move it to a shared parser); if `DECLINED` and the alias is `calendar@`, hand off to a new `CalendarDeclineService`.
  - [ ] `CalendarDeclineService`: match (UID first, fallback per AC7), check active status, de-dup (AC9), send the deregistration-link mail, metrics.
  - [ ] Integration tests: matched by UID, matched by fallback, ambiguous, already cancelled, duplicate within 7 days, accepted reply ignored.
- [ ] **Task B2b — De-dup column** (AC: 9, 10): migration `V{next}__registrations_decline_link_sent_at.sql` (`ALTER TABLE registrations ADD COLUMN decline_link_sent_at TIMESTAMPTZ`); entity field next to `confirmationResentAt`.
- [ ] **Task B3 — Docs** — doc-drift check as in A4, plus a short note in the registration docs that a calendar decline triggers the deregistration-link mail.

### Part C — #1162 (PR 3, body `Fixes #1162`)

- [ ] **Task C1 — Short-line rule** (AC: 12, 13): in `InboundEmailRouter.route`, keyword steps 3–5 act only if the normalized first line has at most 3 words; otherwise fall through to the AC3 forward. Table-driven unit test with real-life sentences (DE + EN) that must NOT trigger.
- [ ] **Task C2 — Cancel asks first** (AC: 11): replace `registrationService.cancelByEmail` + `sendCancelConfirmation` in step 4 with the deregistration-link mail, reusing the Part B service and the AC10 column.
- [ ] **Task C3 — Docs**: as in A4.

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
- A4: `docs/architecture/06-backend-architecture.md` and `docs/user-guide/features/notifications.md` updated (the user guide said "silently discarded").

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
- `docs/architecture/06-backend-architecture.md` (M)
- `docs/user-guide/features/notifications.md` (M)
- `_bmad-output/implementation-artifacts/16-0-stop-losing-replies-and-calendar-declines.md` (M)
- `_bmad-output/implementation-artifacts/sprint-status.yaml` (M)

## Change Log

- 2026-10-30: Story drafted from #1160 and #1161 (John, PM).
- 2026-10-30: Readiness fixes: de-dup storage named (AC10); Part C for #1162 added.
- 2026-10-30: Part A (#1160) implemented on `feature/16-0a-replies-reach-a-person` (Amelia).
