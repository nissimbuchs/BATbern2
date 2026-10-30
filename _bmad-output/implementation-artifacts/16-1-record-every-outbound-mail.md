# Story 16.1: Record every outbound mail

Status: ready-for-dev

## Story

As an **organizer**,
I want **every mail the platform sends for an event to be recorded with its exact content, recipients and delivery result**,
so that **the team can see what went out without asking each other, and the Communications page (16.3) has something to show**.

> Source: `docs/prd/epic-16-mail-center.md` → **Story 16.1**. First wave (16.1 → 16.3 → 16.4), owner decision 3, 2026-10-30.
> Record only. No UI and no public read API in this story; 16.3 adds both.

## Acceptance Criteria

1. **AC1** — GIVEN any EMS send path that targets an event audience or is triggered by an organizer, WHEN it sends, THEN one `mail_message` row exists for that send action (subject, rendered HTML, template key or `own_text`, audience, event code, sender) and one `mail_recipient` row per recipient carrying the SES Message-ID.
2. **AC2** — GIVEN a bounce, complaint or reject on the `transactional` configuration set, WHEN SES publishes it, THEN the matching `mail_recipient` row (by SES Message-ID) is set to `FAILED` with the bounce/complaint type. A `delivery` event sets `DELIVERED`.
3. **AC3** — GIVEN a newsletter send, THEN one `mail_message` row links to the existing `newsletter_sends` row; per-recipient delivery stays in `newsletter_recipients` (no duplication, no behaviour change in `BounceProcessingService`).
4. **AC4** — ADR-015: a send blocked by `TestMarkedEmailException` writes no row. A send that fails at SES writes the recipient as `FAILED` with the SES error code.
5. **AC5** — Inventory guard: a test enumerates every EMS class that calls `EmailService.send*` and fails unless the class is context-tagged or on an explicit allow-list of system mails (e.g. verification codes, forwarding). A new send path cannot silently skip the record.
6. **AC6** — Retention (owner decision 1): a nightly job deletes `mail_message` + children where event date + 12 months has passed, and event-less rows older than 12 months.
7. **AC7** — No test sends real mail. Send-path checks run against local dev (`/dev/mails`), never staging.

## Tasks / Subtasks

- [ ] **Task 1 — Schema (additive)** (AC: 1, 3, 6)
  - [ ] New EMS migration `V{next}__create_mail_message.sql` (head at drafting time: V130; re-check).
  - [ ] `mail_message`: id (UUID, internal only), `mail_key` (public, ADR-003 meaningful-ID convention to be checked), event_code (nullable), direction (`OUT` now, `IN` reserved for 16.2), audience (`NEWSLETTER|REGISTRANTS|SPEAKERS|VENUE|PARTNERS|ORGANIZERS|SYSTEM`), template_key (nullable), content_source (`TEMPLATE|OWN_TEXT`), subject, body_html, sender_username (nullable = system), newsletter_send_id (nullable FK), recipient_count, created_at.
  - [ ] `mail_recipient`: mail_message_id FK, address, kind (`TO|CC`), ses_message_id (indexed), status (`SENT|DELIVERED|FAILED`), failure_reason, body_html (nullable, see Dev Notes), updated_at.
- [ ] **Task 2 — Shared kernel hook** (AC: 1, 4)
  - [ ] `MailContext` record in shared-kernel (event code, audience, template key, content source, sender, send-group id). Callers pass it through new `EmailService` overloads; existing signatures keep working.
  - [ ] `OutboundMailRecorder` SPI in shared-kernel, called by `EmailService` after `SendEmail`/`SendRawEmail` returns, with context, recipients, subject, body and `response.messageId()`. Default no-op bean, so CUMS and partner-coordination stay unchanged.
  - [ ] Add SES message tag `mail_group` (send-group id) on every tagged send.
- [ ] **Task 3 — EMS recorder + callers** (AC: 1, 3, 5)
  - [ ] `mailcenter` package in EMS: JPA entities, repository, `JpaOutboundMailRecorder`. One `mail_message` per send-group id, rows appended per recipient (the newsletter loop and speaker reminders send one call per recipient).
  - [ ] Pass `MailContext` from the 16 EMS send services (list in Dev Notes). Allow-list the system ones.
  - [ ] Inventory guard test (AC5), modelled on `EndpointAuthorizationGuardTest`'s classpath scan.
- [ ] **Task 4 — Delivery status** (AC: 2)
  - [ ] Infra: subscribe a new SQS queue to `TransactionalEventsTopic` in `infrastructure/lib/stacks/ses-stack.ts` (keep the logger Lambda). DLQ like the bounce queue. CDK test + doc-drift mapped doc in the same PR.
  - [ ] EMS `@SqsListener` updates `mail_recipient` by `ses_message_id`. Unknown IDs are ignored (system mails, pre-16.1 mails).
  - [ ] Must not touch newsletter suppression: transactional bounces are deliberately kept out of `BounceProcessingService` (comment in `ses-stack.ts`).
- [ ] **Task 5 — Retention** (AC: 6)
  - [ ] `MailMessageRetentionScheduler`, same shape as `EmailForwardingLogRetentionScheduler` (ShedLock, cron property, Europe/Zurich).
- [ ] **Task 6 — Tests** (AC: all)
  - [ ] Integration (Testcontainers): recorder writes rows; SQS delivery/bounce updates status; retention deletes the right rows; blocked test-marked send writes nothing.
  - [ ] Local dev check: trigger a speaker reminder for a local event, confirm rows and `/dev/mails` content match.

## Dev Notes

- **Where sending happens today:** `shared-kernel/.../service/EmailService.java`. All public send methods return `void`; the SES Message-ID is only logged (lines ~220, 355, 440, 498). `@Async` variants delegate to the sync ones, so the hook belongs in the sync path.
- **EMS send services (16):** DeregistrationEmailService, EmailForwardingService (allow-list: forwarding is recorded by 16.2, not here), InboundEmailConfirmationEmailService, NewsletterController, NewsletterEmailService, NotificationService, QnaNotificationService, QualityReviewService, RegistrationEmailService, SlidesOnlineEmailService, SpeakerAcceptanceEmailService, SpeakerInvitationEmailService, SpeakerReminderEmailService, TaskReminderEmailService, VenueCoordinationService, WaitlistPromotionEmailService. Outside EMS: `PartnerInviteEmailService` (partner-coordination) and `AdditionalEmailVerificationEmailService` (CUMS) are out of scope; partner meeting invites are a follow-up.
- **Body storage:** store the rendered HTML once on `mail_message`. Store a per-recipient body only for personalized audiences (speakers, registrants, venue), where the variables differ. The newsletter differs per recipient only in the unsubscribe link, so one body is enough. Postgres TOAST compresses the HTML. Revisit if the table grows past a few hundred MB.
- **Send guards (ADR-015):** `TestMarkedEmailException` is thrown before the SES call; the recorder runs after a successful call, so AC4's first half holds by construction. Test it anyway.
- **PII:** mail bodies and addresses are PII. Fixtures use `@example.com` and the SES simulator only. No real data in screenshots or issues.
- **Authorization:** no endpoint in this story. 16.3 adds the read endpoint with `hasRole('ORGANIZER')`.

### References

- `docs/prd/epic-16-mail-center.md`
- `docs/architecture/ADR-015-test-data-markers-and-email-send-guards.md`
- `infrastructure/lib/stacks/ses-stack.ts` (configuration sets and destinations)
- `services/event-management-service/.../service/BounceProcessingService.java`
- `services/event-management-service/.../scheduler/EmailForwardingLogRetentionScheduler.java`
