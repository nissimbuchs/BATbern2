# Story 16.1b: Delivery status from SES events

Status: done

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

- [x] **Task 1 — Infrastructure** (AC: 5)
  - [x] In `infrastructure/lib/stacks/ses-stack.ts`: new SQS queue + DLQ subscribed to `TransactionalEventsTopic` (raw message delivery off, same as the bounce queue pattern). Keep the logger Lambda subscription.
  - [x] Grant the EMS task role `sqs:ReceiveMessage/DeleteMessage` on the new queue; pass the URL as an env var (follow how `aws.ses.bounce.queue-url` is wired).
  - [x] CDK unit test for the queue, the subscription and the grant; update the doc mapped for `infrastructure/` in `.github/doc-drift-mappings.yml` in the same PR.
- [x] **Task 2 — Listener** (AC: 1–4, 6)
  - [x] EMS `@SqsListener("${aws.ses.transactional-events.queue-url}")` in the `mailcenter` package: parse SNS envelope → SES event, match recipients by `mail.messageId` + address.
  - [x] Status transition rule (AC6) in one place, unit-tested.
  - [x] Property toggle so local dev runs without the queue (as the bounce listener does).
- [x] **Task 3 — Tests** (AC: all)
  - [x] Integration test with sample SES delivery/bounce/complaint/reject JSON (synthetic addresses, SES simulator domains only).

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

Claude Opus 5.5 (Amelia, dev-story), 2026-10-30

### Debug Log References

- CDK: `npx jest --maxWorkers=2 test/unit`: 472 tests, 0 failures (after adding the runbook entry the
  `incident-response-docs` guard asked for).
- EMS: new tests 5 (rule) + 8 (PostgreSQL) + 2 (metrics). Full suite with checkstyle: 2201 tests,
  0 failures, 18 skipped.

### Completion Notes List

- **Infra (AC5):** `SesStack` adds `batbern-{env}-mail-delivery-status` + `-dlq` (maxReceiveCount 3,
  visibility 60 s) as a second subscriber on `TransactionalEventsTopic`, SNS envelope kept. The
  logger Lambda stays. Only additions: no existing resource renamed or moved.
- **Deviation, wiring:** `EventManagementStack` takes the queue itself (`mailDeliveryStatusQueue`)
  instead of a URL, injects `AWS_SES_DELIVERY_STATUS_ENABLED/_QUEUE_URL` and grants consume on the
  task role inside the stack. The bounce queue is granted in `bin/`, where no test reaches it; this
  way the grant is unit-tested.
- **Deviation, property name:** `aws.ses.delivery-status.{enabled,queue-url}` instead of the
  suggested `aws.ses.transactional-events.queue-url`, matching the queue name.
- **DLQ alarm:** `batbern-{env}-mail-delivery-status-dlq` in `MonitoringStack`, same pattern as the
  bounce DLQ alarm (notBreaching, ALARM + OK actions). Runbook entry added.
- **Listener (AC1, AC2, AC6):** thin `MailDeliveryStatusListener` (`@SqsListener`, property-gated)
  delegates to `MailDeliveryStatusService`, which the tests drive without SQS. Matching by
  `mail.messageId` + address, case-insensitive. Reject has no recipient list, so it fails every
  recipient of the message. `DeliveryStatusRule` (SENT → DELIVERED → FAILED, FAILED final) is
  applied in the UPDATE's `WHERE status IN (...)`, so concurrent events cannot move a status back.
- **AC3:** unknown Message-IDs increment `BATbern/MailCenter DeliveryEventsUnmatched`
  (`MailCenterMetrics`, same pattern as `ForwardingMetrics`). The task role may publish only in that
  namespace. The shared `CloudWatchClient` bean now also exists when only the delivery status
  listener is enabled.
- **AC4:** separate queue and service; nothing calls `BounceProcessingService`. CDK test asserts the
  transactional topic has no subscription to the bounce queue.
- **AC5:** an unreadable message throws `UnreadableEventException`; SQS redelivers and moves it to
  the DLQ after 3 receives.
- **Not verified:** a real SES event end to end. Local dev has no queue (listener off) and SES events
  only exist in production. First signal after deploy: `mail_recipient` rows leaving `SENT`, and the
  DLQ staying empty.

### File List

- `infrastructure/lib/stacks/ses-stack.ts`
- `infrastructure/lib/stacks/monitoring-stack.ts`
- `infrastructure/lib/stacks/event-management-stack.ts`
- `infrastructure/bin/batbern-infrastructure.ts`
- `infrastructure/test/unit/ses-stack.test.ts`
- `infrastructure/test/unit/monitoring-stack.test.ts`
- `infrastructure/test/unit/event-management-iam.test.ts`
- `services/event-management-service/src/main/java/ch/batbern/events/mailcenter/DeliveryStatusRule.java` (new)
- `services/event-management-service/src/main/java/ch/batbern/events/mailcenter/MailCenterMetrics.java` (new)
- `services/event-management-service/src/main/java/ch/batbern/events/mailcenter/MailDeliveryStatusService.java` (new)
- `services/event-management-service/src/main/java/ch/batbern/events/mailcenter/MailDeliveryStatusListener.java` (new)
- `services/event-management-service/src/main/java/ch/batbern/events/repository/MailRecipientRepository.java`
- `services/event-management-service/src/main/java/ch/batbern/events/forwarding/EmailForwardingConfig.java`
- `services/event-management-service/src/main/resources/application.yml`
- `services/event-management-service/src/test/java/ch/batbern/events/mailcenter/DeliveryStatusRuleTest.java` (new)
- `services/event-management-service/src/test/java/ch/batbern/events/mailcenter/MailDeliveryStatusServiceTest.java` (new)
- `services/event-management-service/src/test/java/ch/batbern/events/mailcenter/MailDeliveryStatusIntegrationTest.java` (new)
- `docs/architecture/06-backend-architecture.md`
- `docs/architecture/08-operations-security.md`
- `docs/operations/runbooks.md`
- `_bmad-output/implementation-artifacts/16-1b-delivery-status.md`
- `_bmad-output/implementation-artifacts/sprint-status.yaml`

## Change Log

- 2026-10-30: Split from 16.1 (readiness check), John (PM).
- 2026-10-30: Implemented (queue, DLQ alarm, listener, status rule, metric, docs). Status review. Amelia (dev).
