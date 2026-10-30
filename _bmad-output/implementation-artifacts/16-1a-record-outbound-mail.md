# Story 16.1a: Record every outbound mail

Status: ready-for-dev

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

- [ ] **Task 1 — Schema (additive)** (AC: 1, 3, 6)
  - [ ] New EMS migration `V{next}__create_mail_message.sql` (head at drafting time: V130; re-check).
  - [ ] `mail_message`: id (UUID PK, internal only), `mail_key` (unique, see AC8; sequence `mail_message_seq`), event_code (nullable), direction (`OUT` now, `IN` reserved for 16.2), audience (`NEWSLETTER|REGISTRANTS|SPEAKERS|VENUE|PARTNERS|ORGANIZERS|SYSTEM`), template_key (nullable), content_source (`TEMPLATE|OWN_TEXT`), subject, body_html, sender_username (nullable = system), newsletter_send_id (nullable FK), recipient_count, is_test (boolean, default false; set by 16.8 organizer test sends), created_at.
  - [ ] `mail_recipient`: mail_message_id FK, address, kind (`TO|CC`), ses_message_id (indexed), status (`SENT|DELIVERED|FAILED`; 16.1a writes `SENT` or `FAILED` on SES error, 16.1b updates), failure_reason, body_html (nullable, see Dev Notes), updated_at.
- [ ] **Task 2 — Shared kernel hook** (AC: 1, 4)
  - [ ] `MailContext` record in shared-kernel (event code, audience, template key, content source, sender, send-group id). Callers pass it through new `EmailService` overloads; existing signatures keep working.
  - [ ] `OutboundMailRecorder` SPI in shared-kernel, called by `EmailService` after `SendEmail`/`SendRawEmail` returns, with context, recipients, subject, body and `response.messageId()`. Default no-op bean, so CUMS and partner-coordination stay unchanged.
  - [ ] Add SES message tag `mail_group` (send-group id) on every tagged send.
- [ ] **Task 3 — EMS recorder + callers** (AC: 1, 2, 3, 5, 8)
  - [ ] `mailcenter` package in EMS: JPA entities, repository, `JpaOutboundMailRecorder`. One `mail_message` per send-group id, rows appended per recipient (the newsletter loop and speaker reminders send one call per recipient).
  - [ ] Pass `MailContext` from the 16 EMS send services (list in Dev Notes). Allow-list the system ones.
  - [ ] Inventory guard test (AC5): source scan, allow-list in the test class with a one-line reason per entry.
- [ ] **Task 4 — Retention** (AC: 6)
  - [ ] `MailMessageRetentionScheduler`, same shape as `EmailForwardingLogRetentionScheduler` (ShedLock, cron property, Europe/Zurich).
- [ ] **Task 5 — Tests** (AC: all)
  - [ ] Integration (Testcontainers): recorder writes rows per send-group rule; retention deletes the right rows; blocked test-marked send writes nothing; `mail_key` sequence.
- [ ] **Task 6 — Privacy note** (MC-NFR1): add the retention rule (event date + 12 months) to the privacy/data-handling documentation (find the current location; `docs/compliance/` if it exists, otherwise the GDPR section the docs index points to).
  - [ ] Local dev check: trigger a speaker reminder for a local event, confirm rows and `/dev/mails` content match.

## Dev Notes

- **Where sending happens today:** `shared-kernel/.../service/EmailService.java`. All public send methods return `void`; the SES Message-ID is only logged (lines ~220, 355, 440, 498). `@Async` variants delegate to the sync ones, so the hook belongs in the sync path.
- **EMS send services (16):** DeregistrationEmailService, EmailForwardingService (allow-list: forwarding is recorded by 16.2, not here), InboundEmailConfirmationEmailService, NewsletterController, NewsletterEmailService, NotificationService, QnaNotificationService, QualityReviewService, RegistrationEmailService, SlidesOnlineEmailService, SpeakerAcceptanceEmailService, SpeakerInvitationEmailService, SpeakerReminderEmailService, TaskReminderEmailService, VenueCoordinationService, WaitlistPromotionEmailService. Outside EMS: `PartnerInviteEmailService` (partner-coordination) and `AdditionalEmailVerificationEmailService` (CUMS) are out of scope here; partner mails are recorded by Story 16.9 through this story's SPI (HTTP recorder → EMS `/internal/mail-messages`), so keep the SPI payload self-contained (no EMS entities).
- **Body storage:** store the rendered HTML once on `mail_message`. Store a per-recipient body only for personalized audiences (speakers, registrants, venue), where the variables differ. The newsletter differs per recipient only in the unsubscribe link, so one body is enough. Postgres TOAST compresses the HTML. Revisit if the table grows past a few hundred MB.
- **Send guards (ADR-015):** `TestMarkedEmailException` is thrown before the SES call; the recorder runs after a successful call, so AC4's first half holds by construction. Test it anyway.
- **PII:** mail bodies and addresses are PII. Fixtures use `@example.com` and the SES simulator only. No real data in screenshots or issues.
- **Authorization:** no endpoint in this story. 16.3 adds the read endpoint with `hasRole('ORGANIZER')`.

### References

- `docs/prd/epic-16-mail-center.md`
- `docs/architecture/ADR-015-test-data-markers-and-email-send-guards.md`
- `services/event-management-service/.../service/BounceProcessingService.java`
- `services/event-management-service/.../scheduler/EmailForwardingLogRetentionScheduler.java`

## Dev Agent Record

### Agent Model Used

### Debug Log References

### Completion Notes List

### File List

## Change Log

- 2026-10-30: Story drafted from Epic 16 (John, PM).
- 2026-10-30: Split into 16.1a/16.1b; is_test flag reserved for 16.8 test sends; send-group rules, `mail_key`, guard tool and privacy note added (readiness check).
