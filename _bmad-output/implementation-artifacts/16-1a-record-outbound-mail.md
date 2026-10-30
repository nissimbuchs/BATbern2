# Story 16.1a: Record every outbound mail

Status: review

## Story

As an **organizer**,
I want **every mail the platform sends for an event to be recorded with its exact content, recipients and delivery result**,
so that **the team can see what went out without asking each other, and the Communications page (16.3) has something to show**.

> Source: `docs/prd/epic-16-mail-center.md` → **Story 16.1a**. Split from 16.1 by the readiness check 2026-10-30: 16.1a records (no infrastructure change), **16.1b** adds delivery status from SES events (CDK + listener).
> Record only. No UI and no public read API in this story; 16.3 adds both. Deploy 16.1a shortly before 16.3, so the page has data on day one.

## Acceptance Criteria

1. **AC1** — GIVEN any EMS send path that targets an event audience or is triggered by an organizer, WHEN it sends, THEN one `mail_message` row exists for that send action (subject, rendered HTML, template key or `own_text`, audience, event code, sender) and one `mail_recipient` row per recipient carrying the SES Message-ID.
2. **AC2 (send groups)** — Rows are grouped by these rules (one `mail_message` per group):
   - organizer-triggered send (newsletter, registrant notice, speaker bulk mail, venue mail, later own text): one group per request;
   - scheduled per-recipient sends (speaker tiered reminders, task reminders, Q&A digests): one group per scheduler run × event × template key;
   - individual transactional mails (registration confirmation, waitlist promotion, deregistration link, speaker invitation, acceptance, quality review): one group per mail.
3. **AC3** — GIVEN a newsletter send, THEN one `mail_message` row links to the existing `newsletter_sends` row; per-recipient delivery stays in `newsletter_recipients` (no duplication, no behaviour change in `BounceProcessingService`).
4. **AC4** — ADR-015: a send blocked by `TestMarkedEmailException` writes no row. A send that fails at SES writes the recipient as `FAILED` with the SES error code.
5. **AC5** — Inventory guard: a plain JUnit source-scan test walks `services/event-management-service/src/main/java`, finds every class that calls `emailService.send` (regex), and fails unless the class passes a `MailContext` in every such call or is on an explicit allow-list (forwarding, system mails). No new dependency (ArchUnit is not in the build). A new send path cannot silently skip the record.
6. **AC6** — Retention (owner decision 1): a nightly job deletes `mail_message` + children where event date + 12 months has passed, and event-less rows older than 12 months.
7. **AC7** — No test sends real mail. Send-path checks run against local dev (`/dev/mails`), never staging.
8. **AC8 (public id, ADR-003)** — Each `mail_message` has a readable `mail_key` = `{eventCode}-{n}` (or `general-{n}` without an event), `n` from one Postgres sequence `mail_message_seq`. The UUID PK never leaves the service.

## Tasks / Subtasks

- [x] **Task 1 — Schema (additive)** (AC: 1, 3, 6)
  - [x] New EMS migration `V{next}__create_mail_message.sql` (head at drafting time: V130; re-check).
  - [x] `mail_message`: id (UUID PK, internal only), `mail_key` (unique, see AC8; sequence `mail_message_seq`), event_code (nullable), direction (`OUT` now, `IN` reserved for 16.2), audience (`NEWSLETTER|REGISTRANTS|SPEAKERS|VENUE|PARTNERS|ORGANIZERS|SYSTEM`), template_key (nullable), content_source (`TEMPLATE|OWN_TEXT`), subject, body_html, sender_username (nullable = system), newsletter_send_id (nullable FK), recipient_count, is_test (boolean, default false; set by 16.8 organizer test sends), created_at.
  - [x] `mail_recipient`: mail_message_id FK, address, kind (`TO|CC`), ses_message_id (indexed), status (`SENT|DELIVERED|FAILED`; 16.1a writes `SENT` or `FAILED` on SES error, 16.1b updates), failure_reason, body_html (nullable, see Dev Notes), updated_at.
- [x] **Task 2 — Shared kernel hook** (AC: 1, 4)
  - [x] `MailContext` record in shared-kernel (event code, audience, template key, content source, sender, send-group id). Callers pass it through new `EmailService` overloads; existing signatures keep working.
  - [x] `OutboundMailRecorder` SPI in shared-kernel, called by `EmailService` after `SendEmail`/`SendRawEmail` returns, with context, recipients, subject, body and `response.messageId()`. Default no-op bean, so CUMS and partner-coordination stay unchanged.
  - [x] Add SES message tag `mail_group` (send-group id) on every tagged send.
- [x] **Task 3 — EMS recorder + callers** (AC: 1, 2, 3, 5, 8)
  - [x] `mailcenter` package in EMS: JPA entities, repository, `JpaOutboundMailRecorder`. One `mail_message` per send-group id, rows appended per recipient (the newsletter loop and speaker reminders send one call per recipient).
  - [x] Pass `MailContext` from the 16 EMS send services (list in Dev Notes). Allow-list the system ones.
  - [x] Inventory guard test (AC5): source scan, allow-list in the test class with a one-line reason per entry.
- [x] **Task 4 — Retention** (AC: 6)
  - [x] `MailMessageRetentionScheduler`, same shape as `EmailForwardingLogRetentionScheduler` (ShedLock, cron property, Europe/Zurich).
- [x] **Task 5 — Tests** (AC: all)
  - [x] Integration (Testcontainers): recorder writes rows per send-group rule; retention deletes the right rows; blocked test-marked send writes nothing; `mail_key` sequence.
- [x] **Task 6 — Privacy note** (MC-NFR1): add the retention rule (event date + 12 months) to the privacy/data-handling documentation (find the current location; `docs/compliance/` if it exists, otherwise the GDPR section the docs index points to).
  - [x] Local dev check: trigger a speaker reminder for a local event, confirm rows and `/dev/mails` content match.
    (Done with the deregistration-link mail instead: every local speaker has an anonymized
    `@example.invalid` address, which `EmailService` refuses before capture, so the reminder
    correctly recorded nothing. See Completion Notes.)

## Dev Notes

- **Where sending happens today:** `shared-kernel/.../service/EmailService.java`. All public send methods return `void`; the SES Message-ID is only logged (lines ~220, 355, 440, 498). `@Async` variants delegate to the sync ones, so the hook belongs in the sync path.
- **EMS send services (16):** DeregistrationEmailService, EmailForwardingService (allow-list: forwarding is recorded by 16.2, not here), InboundEmailConfirmationEmailService, NewsletterController, NewsletterEmailService, NotificationService, QnaNotificationService, QualityReviewService, RegistrationEmailService, SlidesOnlineEmailService, SpeakerAcceptanceEmailService, SpeakerInvitationEmailService, SpeakerReminderEmailService, TaskReminderEmailService, VenueCoordinationService, WaitlistPromotionEmailService. Outside EMS: `PartnerInviteEmailService` (partner-coordination) and `AdditionalEmailVerificationEmailService` (CUMS) are out of scope here; partner mails are recorded by Story 16.9 through this story's SPI (HTTP recorder → EMS `/internal/mail-messages`), so keep the SPI payload self-contained (no EMS entities).
- **Body storage:** store the rendered HTML once on `mail_message`. Store a per-recipient body only for personalized audiences (speakers, registrants, venue), where the variables differ. The newsletter differs per recipient only in the unsubscribe link, so one body is enough. Postgres TOAST compresses the HTML. Revisit if the table grows past a few hundred MB.
- **Send guards (ADR-015):** `TestMarkedEmailException` is thrown before the SES call; the recorder runs after a successful call, so AC4's first half holds by construction. Test it anyway.
- **Credentials are redacted before storing (dev deviation, 2026-10-30).** The rendered bodies
  carry bearer secrets: the speaker invitation a live Cognito temporary password (14 days; Story
  11.E.2 deliberately keeps it out of storage), registration mails the registration code (it reads
  the registration through a permitAll endpoint) and `?token=` links (confirmation JWT,
  deregistration, unsubscribe). Stored as-is they would be readable by every organizer for 12 months.
  `MailRedaction` (shared kernel) masks every `token=` parameter and the values a caller names with
  `MailContext.withSecrets`, before the recorder sees the mail, so 16.9's HTTP recorder never carries
  them either. Consequence for 16.4 AC4 ("stored body equals the preview, byte-equal"): compare after
  redaction, or against mails without secrets.
- **PII:** mail bodies and addresses are PII. Fixtures use `@example.com` and the SES simulator only. No real data in screenshots or issues.
- **Authorization:** no endpoint in this story. 16.3 adds the read endpoint with `hasRole('ORGANIZER')`.

### References

- `docs/prd/epic-16-mail-center.md`
- `docs/architecture/ADR-015-test-data-markers-and-email-send-guards.md`
- `services/event-management-service/.../service/BounceProcessingService.java`
- `services/event-management-service/.../scheduler/EmailForwardingLogRetentionScheduler.java`

## Dev Agent Record

### Agent Model Used

Claude Opus 5.5 (Amelia, dev agent)

### Debug Log References

- Full EMS suite 2186 tests, 0 failures, 18 skipped (2026-10-30, after the signature migration).
- Full shared-kernel suite 464 tests, 0 failures.

### Completion Notes List

- **Hook:** `MailContext` + `MailTrigger` + `OutboundMail` + `OutboundMailRecorder` + `MailRedaction` in
  `shared-kernel/.../mail`. Context-first overloads on `EmailService` (async HTML, sync HTML, with
  attachments, sync with attachments); every old signature delegates with a null context, so CUMS and
  partner-coordination are unchanged. The recorder is optional (`@Autowired(required = false)`) instead
  of a no-op bean: same effect, one bean less. Recording never throws into the send.
- **Send groups:** `MailTrigger.system()` per scheduler run or event listener, `organizer(username)` per
  request, `forSend(sendId, user)` for newsletter and registrant notices (a retry joins the group);
  `MailContext.individual(...)` per transactional mail. Group id = `{runId}_{eventCode}_{templateKey}`,
  sanitised to the SES tag alphabet.
- **Deviation, credentials redacted** (see Dev Notes): token links and caller-named secrets are masked
  before the recorder sees the mail. Speaker invitation names the temporary password; registration,
  waitlist, deregistration and registrant-notice mails name the registration code.
- **Sender:** venue mail, slides-online / registrant notice, newsletter and manual speaker reminders
  carry the organizer. Invitation, acceptance and quality review stay `system` (the organizer is not
  passed down to the mail services; 16.3 can show the workflow actor if wanted).
- **Audiences:** notifications (`NotificationService`) map `SPEAKER_ESCALATION` to ORGANIZERS and the
  rest to REGISTRANTS; their template key is `notification-{type}`. Quality review has no stored
  template; its key is the logical `quality-review-revision`.
- **Allow-list (AC5):** `EmailForwardingService` (inbound forward, Story 16.2) and
  `InboundEmailConfirmationEmailService` (newsletter unsubscribe ack: no event, recipient-initiated).
- **Retention:** deletes once the event date *and* the mail are older than 12 months, so a mail sent
  today about an old event is not deleted tonight. Event-less or orphaned mail by creation date.
- **Tests that verified old signatures were migrated, not only the failing ones:** 85 stub/verify sites
  in 13 test classes moved to the context-first signatures, including `never()` checks that would
  otherwise have kept passing against a method no longer called.
- **Local dev check (2026-10-30), V134 + V135 applied:** synthetic registration on local BATbern60
  (`success+mc161a@simulator.amazonses.com`), `POST /registrations/deregister/by-email` → `mail_message`
  `BATbern60-1` (REGISTRANTS, deregistration-link, system, 1 recipient, TO, SENT,
  `ses_message_id = local-{capture id}`). Recorded subject equals the `/dev/emails` capture; recorded
  body equals the captured body with the token masked, byte for byte; neither the token nor the
  registration code is in the record. A manual speaker reminder on the only accepted local speaker
  went to an anonymized `@example.invalid` address, was refused as a reserved domain and recorded
  nothing (AC4 behaviour). All local test rows deleted afterwards.
- **Not verified:** a real SES send with a real Message-ID and the `mail_group` tag on SES (production
  only; first signal after deploy: rows with non-`local-` `ses_message_id`).
- Also on this branch: removal of the unused `cancel-confirmation` templates (V134), follow-up of 16.0.

### File List

- `docs/architecture/06-backend-architecture.md`
- `docs/architecture/08-operations-security.md`
- `services/event-management-service/src/main/java/ch/batbern/events/domain/MailMessage.java`
- `services/event-management-service/src/main/java/ch/batbern/events/domain/MailRecipient.java`
- `services/event-management-service/src/main/java/ch/batbern/events/mailcenter/JpaOutboundMailRecorder.java`
- `services/event-management-service/src/main/java/ch/batbern/events/notification/DeadlineReminderJob.java`
- `services/event-management-service/src/main/java/ch/batbern/events/notification/NotificationRequest.java`
- `services/event-management-service/src/main/java/ch/batbern/events/notification/NotificationService.java`
- `services/event-management-service/src/main/java/ch/batbern/events/repository/MailMessageRepository.java`
- `services/event-management-service/src/main/java/ch/batbern/events/repository/MailRecipientRepository.java`
- `services/event-management-service/src/main/java/ch/batbern/events/scheduler/MailMessageRetentionScheduler.java`
- `services/event-management-service/src/main/java/ch/batbern/events/scheduler/TaskDeadlineReminderScheduler.java`
- `services/event-management-service/src/main/java/ch/batbern/events/service/DeregistrationEmailService.java`
- `services/event-management-service/src/main/java/ch/batbern/events/service/NewsletterEmailService.java`
- `services/event-management-service/src/main/java/ch/batbern/events/service/QnaNotificationService.java`
- `services/event-management-service/src/main/java/ch/batbern/events/service/QualityReviewService.java`
- `services/event-management-service/src/main/java/ch/batbern/events/service/RegistrationEmailService.java`
- `services/event-management-service/src/main/java/ch/batbern/events/service/SlidesOnlineEmailService.java`
- `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerAcceptanceEmailService.java`
- `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerInvitationEmailService.java`
- `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerReminderEmailService.java`
- `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerReminderService.java`
- `services/event-management-service/src/main/java/ch/batbern/events/service/TaskReminderEmailService.java`
- `services/event-management-service/src/main/java/ch/batbern/events/service/VenueCoordinationService.java`
- `services/event-management-service/src/main/java/ch/batbern/events/service/WaitlistPromotionEmailService.java`
- `services/event-management-service/src/main/resources/db/migration/V135__create_mail_message.sql`
- `services/event-management-service/src/test/java/ch/batbern/events/controller/SpeakerInvitationControllerIntegrationTest.java`
- `services/event-management-service/src/test/java/ch/batbern/events/controller/SpeakerReminderControllerIntegrationTest.java`
- `services/event-management-service/src/test/java/ch/batbern/events/controller/VenueCoordinationControllerIntegrationTest.java`
- `services/event-management-service/src/test/java/ch/batbern/events/mailcenter/MailRecordingIntegrationTest.java`
- `services/event-management-service/src/test/java/ch/batbern/events/mailcenter/OutboundMailInventoryGuardTest.java`
- `services/event-management-service/src/test/java/ch/batbern/events/notification/NotificationServiceTest.java`
- `services/event-management-service/src/test/java/ch/batbern/events/scheduler/TaskDeadlineReminderSchedulerTest.java`
- `services/event-management-service/src/test/java/ch/batbern/events/service/NewsletterEmailServiceTest.java`
- `services/event-management-service/src/test/java/ch/batbern/events/service/NewsletterSendIterationIntegrationTest.java`
- `services/event-management-service/src/test/java/ch/batbern/events/service/QnaNotificationServiceIntegrationTest.java`
- `services/event-management-service/src/test/java/ch/batbern/events/service/RegistrationEmailServiceTest.java`
- `services/event-management-service/src/test/java/ch/batbern/events/service/SlidesOnlineEmailServiceTest.java`
- `services/event-management-service/src/test/java/ch/batbern/events/service/SlidesOnlineIntegrationTest.java`
- `services/event-management-service/src/test/java/ch/batbern/events/service/SpeakerAcceptanceEmailServiceTest.java`
- `services/event-management-service/src/test/java/ch/batbern/events/service/SpeakerInvitationEmailServiceTest.java`
- `services/event-management-service/src/test/java/ch/batbern/events/service/SpeakerModeratorReplyToTest.java`
- `services/event-management-service/src/test/java/ch/batbern/events/service/SpeakerReminderServiceTest.java`
- `services/event-management-service/src/test/java/ch/batbern/events/service/TaskReminderEmailServiceTest.java`
- `shared-kernel/src/main/java/ch/batbern/shared/mail/MailContext.java`
- `shared-kernel/src/main/java/ch/batbern/shared/mail/MailRedaction.java`
- `shared-kernel/src/main/java/ch/batbern/shared/mail/MailTrigger.java`
- `shared-kernel/src/main/java/ch/batbern/shared/mail/OutboundMail.java`
- `shared-kernel/src/main/java/ch/batbern/shared/mail/OutboundMailRecorder.java`
- `shared-kernel/src/main/java/ch/batbern/shared/service/EmailService.java`
- `shared-kernel/src/test/java/ch/batbern/shared/mail/MailContextTest.java`
- `shared-kernel/src/test/java/ch/batbern/shared/mail/MailRedactionTest.java`
- `shared-kernel/src/test/java/ch/batbern/shared/service/EmailServiceRecordingTest.java`
- services/event-management-service/src/main/resources/db/migration/V134__drop_cancel_confirmation_templates.sql (commit 8b2be693, with the removed `cancel-confirmation-{de,en}.html`)

## Change Log

- 2026-10-30: Story drafted from Epic 16 (John, PM).
- 2026-10-30: Split into 16.1a/16.1b; is_test flag reserved for 16.8 test sends; send-group rules, `mail_key`, guard tool and privacy note added (readiness check).
- 2026-10-30: Implemented (Amelia). Credentials redacted before storing (deviation, see Dev Notes). Status review.
