# Story 16.0: Stop losing replies and calendar declines (#1160, #1161)

Status: ready-for-dev

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As an **organizer**,
I want **human replies to our automated mails to reach the team, and a registrant's calendar "Declined" to free their seat**,
so that **no speaker question or newsletter reply is silently dropped, and people on the waitlist get the seats of people who already said no**.

> Source: `docs/prd/epic-16-mail-center.md` → **Story 16.0**. GitHub issues **#1160** and **#1161**.
> Stand-alone fixes, deployable before the rest of Epic 16. Two parts, **two PRs**: Part A closes #1160, Part B closes #1161. Part A first, it is live data loss.

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
10. **AC10** — No test sends real mail. Send-path checks run against local dev (`/dev/mails`), never staging (= production).

## Tasks / Subtasks

### Part A — #1160 (PR 1, body `Fixes #1160`)

- [ ] **Task A1 — Moderator Reply-To on speaker mails** (AC: 1)
  - [ ] One helper that builds `batbern{N}-moderator@{forwarding-domain}` from an event, reusing the alias format of `ForwardingAlias` / `DistributionListService` (do not hand-build the string in each service).
  - [ ] Pass it as `replyTo` from `SpeakerInvitationEmailService`, `SpeakerAcceptanceEmailService`, `SpeakerReminderEmailService`, `QualityReviewService`. `EmailService.sendHtmlEmailSync(..., replyTo)` already supports the override (line ~167); add an async overload with `replyTo` if a caller needs it.
  - [ ] Unit tests per service asserting the Reply-To.
- [ ] **Task A2 — `-speaker@` broadcast Reply-To** (AC: 2)
  - [ ] `ForwardedMessageRewriter.rewrite` (line ~48 sets Reply-To = sender): add a `replyToOverride` parameter; `EmailForwardingService` passes the moderator alias when the destination alias is `-speaker@`.
  - [ ] Test: `-speaker@` → moderator Reply-To; `info@` → sender Reply-To.
- [ ] **Task A3 — Forward unrecognized replies** (AC: 3, 4, 5)
  - [ ] Replace the discard branch in `InboundEmailRouter` (step 6, "Unrecognized — silent discard") with a forward to the organizer recipients via `EmailForwardingService` (raw MIME from the same S3 object).
  - [ ] Auto-reply / bounce / own-domain filter before forwarding; metrics + forwarding log entries.
  - [ ] Integration test with a fixture MIME (synthetic, `@example.com`).
- [ ] **Task A4 — Docs** — check `.github/doc-drift-mappings.yml` for the changed paths and update the mapped doc in the same commit; if none is mapped, `[no-doc]` in the commit message.

### Part B — #1161 (PR 2, body `Fixes #1161`)

- [ ] **Task B0 — Size it first** (no code): count `CalendarRepliesDropped` for the last weeks (CloudWatch metric `BATbern/EmailForwarder`) and, if possible without a DB dump, check a few decline senders against registrations via the organizer API. Write the numbers into Completion Notes. Never download production data.
- [ ] **Task B1 — Stable registration UID** (AC: 7)
  - [ ] `RegistrationEmailService.generateCalendarFile` currently gets a random UID from `IcsCalendarService.generateIcsFile` (`UUID.randomUUID() + "@batbern.ch"`). `generateIcsFile(...)` has no UID parameter; the stable UID exists only on `IcsCalendarService.IcsEventData` (used by `generateMultiEventIcsFile` / `generateRequestIcsFile`). Add a UID-taking overload of `generateIcsFile` (or switch to the `IcsEventData` path) and use `reg-{registrationCode}@batbern.ch`. Check whether `registrationCode` is safe to expose in an ICS (it is already in the deregistration links).
- [ ] **Task B2 — Decline handling** (AC: 6, 7, 8, 9)
  - [ ] In `EmailForwardingService.forward`, the calendar-reply branch currently drops everything (`DROPPED_CALENDAR_REPLY`). Before dropping: parse PARTSTAT and UID (reuse `InboundEmailListenerService.extractIcsProperty` or move it to a shared parser); if `DECLINED` and the alias is `calendar@`, hand off to a new `CalendarDeclineService`.
  - [ ] `CalendarDeclineService`: match (UID first, fallback per AC7), check active status, de-dup (AC9), send the deregistration-link mail, metrics.
  - [ ] Integration tests: matched by UID, matched by fallback, ambiguous, already cancelled, duplicate within 7 days, accepted reply ignored.
- [ ] **Task B3 — Docs** — doc-drift check as in A4, plus a short note in the registration docs that a calendar decline triggers the deregistration-link mail.

## Dev Notes

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

### Debug Log References

### Completion Notes List

### File List

## Change Log

- 2026-10-30: Story drafted from #1160 and #1161 (John, PM).
