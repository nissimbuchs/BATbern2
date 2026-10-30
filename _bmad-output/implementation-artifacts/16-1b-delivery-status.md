# Story 16.1b: Delivery status from SES events

Status: ready-for-dev

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As an **organizer**,
I want **each recorded mail to show which recipients got it and which failed, and why**,
so that **I can fix a wrong address or call a speaker whose mail bounced, instead of assuming it arrived**.

> Source: `docs/prd/epic-16-mail-center.md` → **Story 16.1b**. Split from 16.1 by the readiness check 2026-10-30. Depends on 16.1a (the `mail_recipient` table and SES Message-IDs).
> Contains an infrastructure change: a PR to `develop` deploys the CDK stack to production. Keep the PR infra-clean (see "Infra PR rules" below).

## Acceptance Criteria

1. **AC1** — GIVEN a `delivery` event on the `transactional` configuration set, WHEN it is processed, THEN the `mail_recipient` row with that SES Message-ID becomes `DELIVERED`.
2. **AC2** — GIVEN a `bounce`, `complaint` or `reject` event, THEN the row becomes `FAILED` with `failure_reason` = bounce type/subtype, `complaint`, or the reject reason.
3. **AC3** — Unknown Message-IDs (system mails, forwarding, mails sent before 16.1a) are ignored without error and counted in a metric.
4. **AC4** — Newsletter suppression is untouched: transactional bounces never reach `BounceProcessingService` (deliberate, see the comment in `ses-stack.ts`).
5. **AC5** — Messages that fail processing go to a DLQ after 3 attempts; the existing alarm pattern for DLQs applies.
6. **AC6** — A status never moves backwards: `FAILED` is not overwritten by a later `DELIVERED` for the same recipient (SES can send both for multi-recipient mails; per-recipient matching uses the event's recipient list).

## Tasks / Subtasks

- [ ] **Task 1 — Infrastructure** (AC: 5)
  - [ ] In `infrastructure/lib/stacks/ses-stack.ts`: new SQS queue + DLQ subscribed to `TransactionalEventsTopic` (raw message delivery off, same as the bounce queue pattern). Keep the logger Lambda subscription.
  - [ ] Grant the EMS task role `sqs:ReceiveMessage/DeleteMessage` on the new queue; pass the URL as an env var (follow how `aws.ses.bounce.queue-url` is wired).
  - [ ] CDK unit test for the queue, the subscription and the grant; update the doc mapped for `infrastructure/` in `.github/doc-drift-mappings.yml` in the same PR.
- [ ] **Task 2 — Listener** (AC: 1–4, 6)
  - [ ] EMS `@SqsListener("${aws.ses.transactional-events.queue-url}")` in the `mailcenter` package: parse SNS envelope → SES event, match recipients by `mail.messageId` + address.
  - [ ] Status transition rule (AC6) in one place, unit-tested.
  - [ ] Property toggle so local dev runs without the queue (as the bounce listener does).
- [ ] **Task 3 — Tests** (AC: all)
  - [ ] Integration test with sample SES delivery/bounce/complaint/reject JSON (synthetic addresses, SES simulator domains only).

## Dev Notes

- **Infra PR rules:** a PR to `develop` deploys immediately. Do not move or rename existing CDK resources with physical names (collision on deploy). Add, don't change.
- **Where events go today:** transactional set → CloudWatch metrics + SNS `batbern-{env}-ses-transactional-events` → log-only Lambda `batbern-{env}-transactional-event-logger`. Newsletter set → bounce/complaint SNS → SQS → `BounceProcessingService`.
- Partner-coordination sends use the same configuration set; whether their Message-IDs are known depends on the partner recording decision in the epic (Story 16.9).

### References

- `infrastructure/lib/stacks/ses-stack.ts` (~lines 95–170)
- `services/event-management-service/.../service/BounceProcessingService.java` (listener pattern)
- `_bmad-output/implementation-artifacts/16-1a-record-outbound-mail.md`

## Dev Agent Record

### Agent Model Used

### Debug Log References

### Completion Notes List

### File List

## Change Log

- 2026-10-30: Split from 16.1 (readiness check), John (PM).
