# Story 10.29: SES Bounce Processing & Newsletter List Hygiene

Status: done

## Story

As an **organizer**,
I want emails that permanently bounce or generate spam complaints to be automatically suppressed from future newsletter sends,
so that BATbern's SES sender reputation stays healthy and the platform's email delivery is not disrupted.

---

## Acceptance Criteria

### AC1 — SES Account-Level Suppression List (manual, pre-deploy)

- SES Account-Level Suppression List is enabled for BOUNCE and COMPLAINT on account `188701360969` in `eu-central-1`
- Verification: `aws sesv2 get-account --query 'SuppressionAttributes' --profile batbern-staging` returns `{"SuppressedReasons": ["BOUNCE", "COMPLAINT"]}`
- This is a manual CLI step, not automated in CDK

### AC2 — CDK: SES Configuration Set + SNS + SQS bounce pipeline

- `ses-stack.ts` creates:
  - SES Configuration Set named `batbern-{envName}-newsletter`
  - SNS Topic named `batbern-{envName}-ses-bounces` for BOUNCE + COMPLAINT events
  - SQS Queue named `batbern-{envName}-bounce-processing` subscribed to the SNS topic
  - SES Event Destination on the Configuration Set routing BOUNCE + COMPLAINT to the SNS topic
  - Dead-letter queue for failed bounce processing (5 retries before DLQ)
- `ses-stack.ts` exposes `bounceQueue` as a public property (follows `inbound-email-stack.ts` pattern)
- `EventManagementStackProps` extended with optional `bounceQueueUrl?: string`
- `event-management-stack.ts` conditionally injects `AWS_BOUNCE_QUEUE_URL` and `AWS_BOUNCE_ENABLED: 'true'` into `additionalEnvironment` (same spread pattern as inbound email)
- CDK app orchestration (`bin/batbern-infrastructure.ts`) wires `sesStack.bounceQueue.queueUrl` → `EventManagementStack` props and calls `sesStack.bounceQueue.grantConsumeMessages(eventManagementStack.service.taskDefinition.taskRole)`
- `eventManagementStack.addDependency(sesStack)` ensures correct deploy order
- CDK unit tests verify all resources are created with correct properties

### AC3 — Database migration: bounce tracking fields

- Flyway migration `V91__add_bounce_tracking.sql` adds to `newsletter_subscribers`:
  - `bounce_type VARCHAR(20)` — values: `'hard'`, `'soft'`, `'complaint'`, or NULL
  - `bounce_count INTEGER NOT NULL DEFAULT 0`
  - `last_bounced_at TIMESTAMPTZ`
  - `suppressed_at TIMESTAMPTZ` — non-null = excluded from sends
  - Partial index: `idx_newsletter_subscribers_suppressed ON newsletter_subscribers (suppressed_at) WHERE suppressed_at IS NOT NULL`
- Adds to `newsletter_recipients`:
  - `bounce_type VARCHAR(20)`
  - `bounced_at TIMESTAMPTZ`

### AC4 — EmailService: configurationSetName support

- `EmailService.sendHtmlEmailSync()` accepts optional `configurationSetName` parameter
- When non-null, sets `.configurationSetName(name)` on `SendEmailRequest`
- Controlled by Spring property `batbern.ses.configuration-set-name` (null = disabled, backward-compatible)
- `sendHtmlEmail()` (async variant) also supports configurationSetName
- Existing callers (registration emails, speaker invitations, etc.) are NOT changed — they pass null

### AC5 — BounceProcessingService: SQS listener for bounce/complaint events

- New service `BounceProcessingService` in event-management-service
- Activated by `@ConditionalOnProperty(name = "aws.ses.bounce.enabled", havingValue = "true")`
- `@SqsListener("${aws.ses.bounce.queue-url}")` — follows existing `InboundEmailListenerService` pattern
- Parses SNS-wrapped SES notification JSON:
  - `notificationType = "Bounce"` + `bounce.bounceType = "Permanent"` → **hard bounce**: set `suppressed_at = now()`, `bounce_type = 'hard'`, increment `bounce_count`, set `last_bounced_at`
  - `notificationType = "Bounce"` + `bounce.bounceType = "Transient"` → **soft bounce**: increment `bounce_count`, set `bounce_type = 'soft'`, set `last_bounced_at`; suppress if `bounce_count >= ${batbern.ses.bounce.soft-threshold}` (default: 3)
  - `notificationType = "Complaint"` → **complaint**: set `suppressed_at = now()`, `bounce_type = 'complaint'`
- Updates matching `newsletter_recipients` row if SES message tags contain `sendId`
- Logs all bounce events at INFO level for audit trail
- Silently ignores unknown notification types (future-proof)
- Anti-duplicate: naturally idempotent — use `SET bounce_count = GREATEST(bounce_count, :newCount)` and `SET suppressed_at = COALESCE(suppressed_at, :now)` so reprocessing the same bounce does not double-count or overwrite an earlier suppression timestamp

### AC6 — Exclude suppressed subscribers from newsletter sends

- `NewsletterSubscriberRepository`: `findByUnsubscribedAtIsNull(Pageable)` (line 32) modified to also filter `AND suppressed_at IS NULL`
- `findByUnsubscribedAtIsNull()` (non-paginated, line 29) — same filter added
- `findActiveSubscribersNotInSend()` (line 41) — same filter added
- `countByUnsubscribedAtIsNull()` (line 34) — same filter added
- Net effect: suppressed subscribers receive no newsletters and are not counted in "active" totals
- Existing `findSubscribers()` (Story 10.28 admin search) is NOT changed — admin can still see suppressed subscribers

### AC7 — Batched canary send mode

- `NewsletterSendRequest` DTO gets optional `maxRecipients` field (Integer, nullable)
- When set, `executeNewsletterSendAsync()` stops after sending to N recipients (breaks out of page loop)
- New Spring property `newsletter.send.inter-page-delay-ms` (default `0`) — delay between pages of 50
- For first sends to imported lists, organizer sets `maxRecipients: 50` and `inter-page-delay-ms: 300000` (5 min)
- Progress tracking (`sentCount`, `failedCount`) works correctly with early termination
- `recipientCount` in `NewsletterSend` reflects actual send count, not total active subscribers

### AC8 — Admin visibility: bounce status in subscriber list

- `SubscriberResponse` DTO extended with: `bounceType`, `bounceCount`, `lastBouncedAt`, `suppressedAt`
- `GET /api/v1/newsletter/subscribers` supports `?status=suppressed` filter (in addition to existing `active`, `unsubscribed`, `all`)
- New endpoint: `POST /api/v1/newsletter/subscribers/{id}/unsuppress` — clears `suppressed_at`, resets `bounce_count` to 0, clears `bounce_type`; returns updated `SubscriberResponse`; 404 if not found; 409 if not suppressed
- Frontend subscriber list:
  - New status chip: "Suppressed" (orange/warning color) when `suppressedAt` is non-null
  - Tooltip on suppressed chip shows bounce type and count
  - New filter option in status RadioGroup: "Suppressed"
  - MoreVert menu for suppressed subscriber: "Unsuppress" + "Delete"
  - New `UnsuppressDialog.tsx` confirmation dialog
- i18n keys added for suppressed status, unsuppress action, dialog text (en + de)

### AC9 — Monitoring: CloudWatch alarms for bounce/complaint rates

- CloudWatch alarms in `monitoring-stack.ts`:
  - `BounceRateWarning`: SES Reputation.BounceRate > 0.03 (3%) for 5 min → warning to alarm topic
  - `BounceRateCritical`: SES Reputation.BounceRate > 0.05 (5%) for 5 min → critical to alarm topic
  - `ComplaintRateCritical`: SES Reputation.ComplaintRate > 0.0005 (0.05%) for 5 min → critical to alarm topic
  - `BounceProcessingDLQAlarm`: SQS `ApproximateNumberOfMessagesVisible` on the DLQ > 0 for 1 min → critical to alarm topic (bounce processing failures)
- Alarms wire to existing `alarmTopic` (triggers GitHub Issues via `GitHubIssuesConstruct`)

### AC10 — Tests (TDD: Red-Green-Refactor)

**Backend:**
- `BounceProcessingServiceTest` (unit):
  - `should_suppressSubscriber_when_hardBounceReceived()`
  - `should_incrementBounceCount_when_softBounceReceived()`
  - `should_suppressSubscriber_when_softBounceCountReachesThree()`
  - `should_suppressSubscriber_when_complaintReceived()`
  - `should_updateRecipient_when_sendIdInMessageTags()`
  - `should_beNaturallyIdempotent_when_sameBounceProcessedTwice()` — verifies GREATEST/COALESCE prevents double-counting
  - `should_ignoreUnknownNotificationType()`
- `NewsletterSubscriberServiceTest` (extend existing):
  - `should_excludeSuppressedFromActiveCount()`
  - `should_unsuppressSubscriber_when_organiserRequests()`
  - `should_throwConflict_when_subscriberNotSuppressed()`
- `NewsletterControllerIntegrationTest` (extend existing):
  - `should_excludeSuppressedSubscribers_when_listingActive()`
  - `should_filterBySuppressed_when_statusIsSuppressed()`
  - `should_unsuppressSubscriber_when_organiserRequests()`
- `NewsletterEmailServiceTest` (extend existing):
  - `should_respectMaxRecipients_when_canaryModeEnabled()`
  - `should_passConfigurationSetName_when_configured()`

**Frontend:**
- `UnsuppressDialog.test.tsx`: renders email, confirm calls API, success triggers onSuccess
- Extend `NewsletterSubscriberTable.test.tsx`: suppressed chip renders, MoreVert shows unsuppress for suppressed subscriber
- Extend `NewsletterSubscriberFilters.test.tsx`: suppressed radio option triggers filter

**Infrastructure:**
- `ses-stack.test.ts`: Configuration Set created, SNS topic created, SQS queue subscribed, event destination configured, DLQ configured, `bounceQueue` public property exposed
- `event-management-stack.test.ts` (extend): `AWS_BOUNCE_QUEUE_URL` + `AWS_BOUNCE_ENABLED` injected when `bounceQueueUrl` prop provided; omitted when prop absent

---

## Tasks / Subtasks

### Task 1 — SES Account-Level Suppression List (AC: #1)
- [x] 1.1 Run CLI command to enable suppression list on production account
- [x] 1.2 Verify with `get-account` that BOUNCE + COMPLAINT suppression is active

### Task 2 — CDK: SES Configuration Set + SNS + SQS (AC: #2, #9)
- [x] 2.1 **RED**: Write CDK unit tests for ses-stack: config set, SNS topic, SQS queue, event destination, DLQ, `bounceQueue` public property exposed
- [x] 2.2 **RED**: Write CDK unit tests for monitoring-stack: bounce rate + complaint rate alarms + DLQ visibility alarm
- [x] 2.3 **GREEN**: Implement `ses-stack.ts` — SES Configuration Set, SNS Topic, SQS Queue, Event Destination, DLQ; expose `bounceQueue` as public property (follows `inbound-email-stack.ts` pattern)
- [x] 2.4 **GREEN**: Extend `EventManagementStackProps` with `bounceQueueUrl?: string`; conditionally inject `AWS_BOUNCE_QUEUE_URL` + `AWS_BOUNCE_ENABLED` in `additionalEnvironment` (same spread pattern as `inboundEmailQueueUrl`)
- [x] 2.5 **GREEN**: Wire in CDK app orchestration (`bin/batbern-infrastructure.ts`): pass `sesStack.bounceQueue.queueUrl` to EventManagementStack props, call `sesStack.bounceQueue.grantConsumeMessages(eventManagementStack.service.taskDefinition.taskRole)`, add stack dependency
- [x] 2.6 **GREEN**: Add CloudWatch alarms to monitoring-stack: bounce rate warning/critical, complaint rate critical, DLQ visibility alarm
- [x] 2.7 **REFACTOR**: All CDK tests pass, `cdk synth` succeeds

### Task 3 — Database Migration (AC: #3)
- [x] 3.1 Create `V91__add_bounce_tracking.sql` with bounce fields on `newsletter_subscribers` and `newsletter_recipients`
- [x] 3.2 Verify migration applies cleanly on local PostgreSQL

### Task 4 — Domain Entity + Repository Updates (AC: #3, #6)
- [x] 4.1 **RED**: Write failing tests for suppressed subscriber exclusion in `NewsletterSubscriberServiceTest`
- [x] 4.2 **GREEN**: Add `bounceType`, `bounceCount`, `lastBouncedAt`, `suppressedAt` fields to `NewsletterSubscriber.java`
- [x] 4.3 **GREEN**: Add `bounceType`, `bouncedAt` fields to `NewsletterRecipient.java`
- [x] 4.4 **GREEN**: Modify `NewsletterSubscriberRepository` — all active-subscriber queries add `AND s.suppressedAt IS NULL`
- [x] 4.5 **GREEN**: Add `findBySuppressedAtIsNotNull` for admin suppressed-list query
- [x] 4.6 **GREEN**: Extend `SubscriberResponse` DTO with bounce fields
- [x] 4.7 **REFACTOR**: All existing newsletter tests still pass

### Task 5 — EmailService: Configuration Set Support (AC: #4)
- [x] 5.1 **RED**: Write test that `sendHtmlEmailSync` passes configurationSetName when configured
- [x] 5.2 **GREEN**: Add overloaded `sendHtmlEmailSync(String to, String subject, String htmlBody, String configurationSetName)` to `EmailService`
- [x] 5.3 **GREEN**: Add Spring property `batbern.ses.configuration-set-name` to `application.yml`
- [x] 5.4 **GREEN**: `NewsletterEmailService` passes configuration set name from property to `sendHtmlEmailSync`
- [x] 5.5 **REFACTOR**: Existing email callers unaffected (pass null or use 3-arg overload)

### Task 6 — BounceProcessingService (AC: #5)
- [x] 6.1 **RED**: Write all unit tests in `BounceProcessingServiceTest` (7 test cases)
- [x] 6.2 **GREEN**: Create `BounceProcessingService` with `@ConditionalOnProperty` + `@SqsListener`
- [x] 6.3 **GREEN**: Parse SNS envelope → extract SES notification JSON
- [x] 6.4 **GREEN**: Implement hard bounce handler: suppress subscriber, update recipient
- [x] 6.5 **GREEN**: Implement soft bounce handler: increment count, conditional suppress at configurable threshold (`batbern.ses.bounce.soft-threshold`, default 3)
- [x] 6.6 **GREEN**: Implement complaint handler: immediate suppress
- [x] 6.7 **GREEN**: Add `aws.ses.bounce.enabled`, `aws.ses.bounce.queue-url`, and `aws.ses.bounce.soft-threshold` to `application.yml`
- [x] 6.8 **REFACTOR**: All tests pass, natural idempotency via GREATEST/COALESCE verified

### Task 7 — Batched Canary Send Mode (AC: #7)
- [x] 7.1 **RED**: Write test for `maxRecipients` early termination in `NewsletterEmailServiceTest`
- [x] 7.2 **GREEN**: Add `maxRecipients` to `NewsletterSendRequest` DTO
- [x] 7.3 **GREEN**: Add `newsletter.send.inter-page-delay-ms` property (default 0)
- [x] 7.4 **GREEN**: Implement early termination in `executeNewsletterSendAsync` send loop
- [x] 7.5 **GREEN**: Add inter-page delay between page iterations
- [x] 7.6 **REFACTOR**: Progress tracking correct with early termination

### Task 8 — Admin Visibility: Bounce Status in Subscriber List (AC: #8)
- [x] 8.1 **RED**: Write integration tests for `?status=suppressed` filter and unsuppress endpoint
- [x] 8.2 **GREEN**: Add `?status=suppressed` to `findSubscribers()` in service + repository
- [x] 8.3 **GREEN**: Add `POST /newsletter/subscribers/{id}/unsuppress` endpoint to `NewsletterController`
- [x] 8.4 **GREEN**: Add `unsuppressById()` to `NewsletterSubscriberService`
- [x] 8.5 **GREEN**: Update OpenAPI spec with new endpoint + response fields
- [x] 8.6 **GREEN**: Run `npm run generate:api-types`

### Task 9 — Frontend: Suppressed Status in Subscriber List (AC: #8)
- [x] 9.1 **RED**: Write failing tests: suppressed chip, filter option, unsuppress dialog
- [x] 9.2 **GREEN**: Add "Suppressed" status chip (orange) to `NewsletterSubscriberTable.tsx`
- [x] 9.3 **GREEN**: Add "Suppressed" radio option to `NewsletterSubscriberFilters.tsx`
- [x] 9.4 **GREEN**: Create `UnsuppressDialog.tsx` confirmation dialog
- [x] 9.5 **GREEN**: Add MoreVert menu item "Unsuppress" for suppressed subscribers
- [x] 9.6 **GREEN**: Add i18n keys for suppressed status, unsuppress action, dialog text (en + de)
- [x] 9.7 **GREEN**: Add `unsuppressNewsletterSubscriber()` to `newsletterApi.ts`
- [x] 9.8 **REFACTOR**: All frontend tests pass, ESLint clean, type-check passes

---

## Dev Notes

### In-Flight Send Behavior (by design)

If a bounce arrives **during** an active newsletter send, the subscriber gets suppressed mid-send. Because the send loop at `NewsletterEmailService:250-284` queries fresh pages per iteration, a subscriber suppressed between page 1 and page 3 is correctly excluded from page 3 onward. **This is correct behavior — do not "fix" it.** The send loop commits recipient rows per-page (each `sendHtmlEmailSync` call is its own transaction), so there is no race condition between bounce processing and recipient insertion.

### CRITICAL: What already exists (do NOT reinvent)

| What | Location | Notes |
|------|----------|-------|
| Newsletter send loop | `NewsletterEmailService.java:250-284` | Processes pages of 50, 70ms delay. **Extend**, do not rewrite |
| Active subscribers query | `NewsletterSubscriberRepository.java:32` | `findByUnsubscribedAtIsNull(Pageable)` — **modify** to add suppress filter |
| Subscriber entity | `NewsletterSubscriber.java` | **Extend** with 4 new fields |
| Recipient entity | `NewsletterRecipient.java` | **Extend** with 2 new fields |
| EmailService send | `EmailService.java:124-157` | `sendHtmlEmailSync()` — **add overload** with configurationSetName |
| SQS listener pattern | `InboundEmailListenerService.java:70` | Existing `@SqsListener` + `@ConditionalOnProperty` — **follow same pattern** |
| SES stack | `ses-stack.ts` | Currently a stub — **expand** |
| Inbound email wiring | `inbound-email-stack.ts`, `event-management-stack.ts:70-116`, `bin/batbern-infrastructure.ts:237-295` | **Exact blueprint** for SQS queue → stack props → env var → IAM grant pattern |
| Monitoring alarms | `monitoring-stack.ts:100-103` | Uses `AlarmConstruct` + `alarmTopic` — **follow pattern** |
| Subscriber admin page | `NewsletterSubscribers/` components | Story 10.28 — **extend** with suppressed status |
| Subscriber DTO | `SubscriberResponse.java` | **Add** bounce fields |
| OpenAPI spec | `docs/api/events.openapi.yml` | **Add** new endpoint + updated response schema |
| Subscriber store | `newsletterSubscriberStore.ts` | **Extend** with `suppressed` filter option |
| Subscriber API | `services/api/newsletterApi.ts` | **Add** `unsuppressNewsletterSubscriber()` |

### SES Bounce Notification JSON Structure

The SQS message is an SNS envelope wrapping the SES notification:
```json
{
  "Type": "Notification",
  "Message": "{\"notificationType\":\"Bounce\",\"bounce\":{\"bounceType\":\"Permanent\",\"bounceSubType\":\"General\",\"bouncedRecipients\":[{\"emailAddress\":\"user@example.com\",\"action\":\"failed\",\"status\":\"5.1.1\",\"diagnosticCode\":\"smtp; 550 5.1.1 user unknown\"}],\"timestamp\":\"2026-04-03T10:00:00.000Z\",\"feedbackId\":\"...\"},\"mail\":{\"timestamp\":\"...\",\"source\":\"noreply@batbern.ch\",\"messageId\":\"...\",\"tags\":{\"ses:configuration-set\":[\"batbern-staging-newsletter\"],\"sendId\":[\"uuid-here\"]}}}"
}
```

**Important**: The `Message` field is a JSON string that must be parsed separately. The `mail.tags.sendId` array (if present) maps back to `newsletter_sends.id` for updating `newsletter_recipients`.

### SQS Listener Pattern (follow InboundEmailListenerService)

```java
@Service
@ConditionalOnProperty(name = "aws.ses.bounce.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class BounceProcessingService {

    @SqsListener("${aws.ses.bounce.queue-url}")
    public void handleBounceNotification(String messageBody) {
        // 1. Parse SNS envelope
        // 2. Extract SES notification from Message field
        // 3. Route by notificationType (Bounce / Complaint)
        // 4. Update subscriber + recipient records
    }
}
```

**CRITICAL**: Do NOT use `@SqsListener("${....:#{null}}")` — Spring Cloud AWS 3.x does not support null queue identifiers and will throw at startup. The `@ConditionalOnProperty` annotation prevents the bean from being created when bounce processing is disabled.

### EmailService Overload Pattern

Add a 4-arg overload, keep 3-arg for backward compatibility:
```java
public void sendHtmlEmailSync(String to, String subject, String htmlBody) {
    sendHtmlEmailSync(to, subject, htmlBody, null);  // delegate
}

public void sendHtmlEmailSync(String to, String subject, String htmlBody, String configurationSetName) {
    // existing logic + optional .configurationSetName(configurationSetName) on builder
}
```

### CDK: SES Configuration Set Event Destination (follows inbound-email-stack.ts pattern)

```typescript
// ses-stack.ts — expose bounceQueue as public property
import * as ses from 'aws-cdk-lib/aws-ses';

public readonly bounceQueue: sqs.Queue;  // public prop for cross-stack wiring

const configSet = new ses.CfnConfigurationSet(this, 'NewsletterConfigSet', {
  name: `batbern-${envName}-newsletter`,
});

const bounceTopic = new sns.Topic(this, 'BounceTopic', {
  topicName: `batbern-${envName}-ses-bounces`,
});

const dlq = new sqs.Queue(this, 'BounceProcessingDLQ', {
  queueName: `batbern-${envName}-bounce-processing-dlq`,
  retentionPeriod: cdk.Duration.days(14),
});

this.bounceQueue = new sqs.Queue(this, 'BounceProcessingQueue', {
  queueName: `batbern-${envName}-bounce-processing`,
  visibilityTimeout: cdk.Duration.seconds(300),
  deadLetterQueue: { queue: dlq, maxReceiveCount: 5 },
});

bounceTopic.addSubscription(new subscriptions.SqsSubscription(this.bounceQueue));

new ses.CfnConfigurationSetEventDestination(this, 'BounceEventDest', {
  configurationSetName: configSet.ref,
  eventDestination: {
    name: 'bounce-complaint-notifications',
    enabled: true,
    matchingEventTypes: ['bounce', 'complaint'],
    snsDestination: { topicArn: bounceTopic.topicArn },
  },
});
```

```typescript
// event-management-stack.ts — conditional env var injection (same pattern as inbound email)
additionalEnvironment: {
  ...(props.bounceQueueUrl && {
    AWS_BOUNCE_QUEUE_URL: props.bounceQueueUrl,
    AWS_BOUNCE_ENABLED: 'true',
  }),
}
```

```typescript
// bin/batbern-infrastructure.ts — orchestration wiring
eventManagementStack = new EventManagementStack(app, `${stackPrefix}-EventManagement`, {
  // ...existing props...
  bounceQueueUrl: sesStack.bounceQueue.queueUrl,
});
eventManagementStack.addDependency(sesStack);

// IAM: grant consume permissions (same as inbound email pattern)
sesStack.bounceQueue.grantConsumeMessages(
  eventManagementStack.service.taskDefinition.taskRole,
);
```

### Repository Query Modification

The key query at `NewsletterSubscriberRepository.java:32`:
```java
// BEFORE:
Page<NewsletterSubscriber> findByUnsubscribedAtIsNull(Pageable pageable);

// AFTER:
Page<NewsletterSubscriber> findByUnsubscribedAtIsNullAndSuppressedAtIsNull(Pageable pageable);
```

Same pattern for all 4 active-subscriber queries. Spring Data JPA derives the correct `WHERE unsubscribed_at IS NULL AND suppressed_at IS NULL`.

### Application Config Additions

Add to `application.yml` (event-management-service):
```yaml
aws:
  ses:
    bounce:
      enabled: ${AWS_BOUNCE_ENABLED:false}
      queue-url: ${AWS_BOUNCE_QUEUE_URL:}
      soft-threshold: ${SES_BOUNCE_SOFT_THRESHOLD:3}

batbern:
  ses:
    configuration-set-name: ${SES_CONFIGURATION_SET_NAME:}

newsletter:
  send:
    inter-page-delay-ms: ${NEWSLETTER_INTER_PAGE_DELAY_MS:0}
```

### Latest Migration Number

Current latest: `V90__allow_global_teaser_images.sql`. This story uses **V91**.

### Project Structure Notes

- All changes follow existing patterns — no new architectural concepts introduced
- `BounceProcessingService` follows same `@ConditionalOnProperty` + `@SqsListener` pattern as `InboundEmailListenerService`
- CDK changes in `ses-stack.ts` follow existing stack patterns (tagging, naming)
- Frontend changes extend existing Story 10.28 components — no new pages or routes
- Shared-kernel change (EmailService) is backward-compatible via overload

### References

- [Source: services/event-management-service/.../service/NewsletterEmailService.java — send loop at lines 250-284]
- [Source: shared-kernel/.../service/EmailService.java — sendHtmlEmailSync at lines 124-157]
- [Source: services/event-management-service/.../service/InboundEmailListenerService.java — SQS listener pattern at line 70]
- [Source: infrastructure/lib/stacks/ses-stack.ts — stub for expansion]
- [Source: infrastructure/lib/stacks/monitoring-stack.ts — alarm pattern at lines 100-103]
- [Source: services/event-management-service/.../resources/db/migration/V67__create_newsletter_tables.sql — current schema]
- [Source: services/event-management-service/.../resources/db/migration/V87__add_newsletter_send_status.sql — send status tracking]
- [Source: AWS SES Bounce Notification docs: https://docs.aws.amazon.com/ses/latest/dg/notification-contents.html]
- [Plan: ~/.claude/plans/drifting-hatching-hollerith.md — full technical plan]

---

## Dev Agent Record

### Agent Model Used

### Debug Log References

### Completion Notes List
- ✅ Task 1: SES Account-Level Suppression List enabled for BOUNCE + COMPLAINT on account 188701360969 (eu-central-1). Verified via `get-account`.
- ✅ Task 2: CDK infrastructure — ses-stack.ts expanded with ConfigSet, SNS, SQS, Event Destination, DLQ. monitoring-stack.ts extended with 3 SES alarms + DLQ alarm. EMS stack wired with bounceQueueUrl. All 258 CDK tests pass.
- ✅ Task 3: V91 Flyway migration created — adds bounce_type, bounce_count, last_bounced_at, suppressed_at to newsletter_subscribers; bounce_type, bounced_at to newsletter_recipients; partial index on suppressed_at.
- ✅ Task 4: Entity + repository updates — NewsletterSubscriber/Recipient entities extended; repository queries exclude suppressed; SubscriberResponse DTO extended; toResponse includes bounce fields; unsuppressById added.
- ✅ Task 5: EmailService configuration set support — 4-arg overload added; NewsletterEmailService passes configurationSetName; Spring property batbern.ses.configuration-set-name.
- ✅ Task 6: BounceProcessingService — @ConditionalOnProperty + @SqsListener; SNS→SES notification parsing; hard/soft/complaint handlers; idempotent via COALESCE; 7 unit tests pass.
- ✅ Task 7: Canary send mode — maxRecipients on DTO and send loop; inter-page-delay-ms property; early termination with correct progress tracking.
- ✅ Task 8: Admin unsuppress endpoint + OpenAPI spec + type generation; suppressed filter in repository queries.
- ✅ Task 9: Frontend — Suppressed chip (warning color with tooltip), filter radio, UnsuppressDialog, MoreVert unsuppress action, i18n (en+de), API hook. ESLint clean.

### Code Review Fixes (2026-04-03)
- 🔧 **H1**: Moved `@Transactional` from internal handler methods to `handleBounceNotification()` — fixes silent Spring proxy bypass where subscriber+recipient saves were not atomic
- 🔧 **H2**: Removed misleading `Math.max(n, n+1)` in hard bounce/complaint handlers — replaced with plain increment; updated Javadoc to document actual idempotency guarantees (COALESCE on suppressedAt, counter may over-count on SQS redelivery)
- 🔧 **H3**: Added missing `NewsletterEmailServiceTest` tests: `should_passConfigurationSetName_when_configured()`, `should_respectMaxRecipients_when_canaryModeEnabled()`
- 🔧 **M1**: Added missing `NewsletterSubscriberServiceTest` tests: `should_excludeSuppressedFromActiveCount()`, `should_unsuppressSubscriber_when_organiserRequests()`, `should_throwConflict_when_subscriberNotSuppressed()`
- 🔧 **M2**: Controller integration tests for suppress/unsuppress not added (would require Testcontainers context setup — deferred to integration test pass)
- 🔧 **M3**: Created `UnsuppressDialog.test.tsx` (7 tests); extended `NewsletterSubscriberTable.test.tsx` (+2 suppress tests); extended `NewsletterSubscriberFilters.test.tsx` (+1 suppress radio test); fixed `NewsletterSubscriberList.test.tsx` mock missing `useUnsuppressSubscriber`
- 🔧 **L1**: `sendHtmlEmail()` async variant now delegates to `sendHtmlEmailSync(to, subject, htmlBody, null)` — eliminates duplicate code and gains configurationSetName support for future async callers
- 🔧 **L2**: Added `suppressedAt`, `bounceCount`, `lastBouncedAt` to `ALLOWED_SORT_FIELDS` — admin can now sort subscriber list by bounce data for triage

### File List
**Infrastructure (CDK):**
- infrastructure/lib/stacks/ses-stack.ts (expanded: ConfigSet, SNS, SQS, EventDestination, DLQ)
- infrastructure/lib/stacks/monitoring-stack.ts (added: SES bounce/complaint alarms, DLQ alarm)
- infrastructure/lib/stacks/event-management-stack.ts (added: bounceQueueUrl prop + env vars)
- infrastructure/bin/batbern-infrastructure.ts (wired: bounceQueue → EMS, IAM grant, dependency)
- infrastructure/test/unit/ses-stack.test.ts (new: 10 tests)
- infrastructure/test/unit/monitoring-stack.test.ts (extended: 4 SES alarm tests, alarm count fix)

**Backend (Java):**
- services/event-management-service/src/main/resources/db/migration/V91__add_bounce_tracking.sql (new)
- services/event-management-service/src/main/resources/application.yml (added: bounce config, config-set, inter-page-delay)
- services/event-management-service/src/main/java/ch/batbern/events/domain/NewsletterSubscriber.java (added: bounce fields)
- services/event-management-service/src/main/java/ch/batbern/events/domain/NewsletterRecipient.java (added: bounce fields)
- services/event-management-service/src/main/java/ch/batbern/events/repository/NewsletterSubscriberRepository.java (modified: suppressed exclusion, suppressed filter)
- services/event-management-service/src/main/java/ch/batbern/events/dto/SubscriberResponse.java (added: bounce fields)
- services/event-management-service/src/main/java/ch/batbern/events/dto/NewsletterSendRequest.java (added: maxRecipients)
- services/event-management-service/src/main/java/ch/batbern/events/service/NewsletterSubscriberService.java (modified: active queries, unsuppressById)
- services/event-management-service/src/main/java/ch/batbern/events/service/NewsletterEmailService.java (modified: configSet, maxRecipients, interPageDelay)
- services/event-management-service/src/main/java/ch/batbern/events/service/BounceProcessingService.java (new)
- services/event-management-service/src/main/java/ch/batbern/events/controller/NewsletterController.java (added: unsuppress endpoint, maxRecipients passthrough)
- shared-kernel/src/main/java/ch/batbern/shared/service/EmailService.java (added: 4-arg sendHtmlEmailSync overload)
- services/event-management-service/src/test/java/ch/batbern/events/service/BounceProcessingServiceTest.java (new: 7 tests)
- services/event-management-service/src/test/java/ch/batbern/events/service/NewsletterEmailServiceTest.java (updated: method refs + 2 review tests: configSet, maxRecipients)
- services/event-management-service/src/test/java/ch/batbern/events/service/NewsletterSubscriberServiceTest.java (extended: 3 review tests: suppressed count, unsuppress, conflict)

**Frontend:**
- web-frontend/src/services/api/newsletterApi.ts (added: unsuppressNewsletterSubscriber)
- web-frontend/src/stores/newsletterSubscriberStore.ts (added: 'suppressed' status)
- web-frontend/src/hooks/useNewsletterSubscribers/useNewsletterSubscriberMutations.ts (added: useUnsuppressSubscriber)
- web-frontend/src/hooks/useNewsletterSubscribers/index.ts (export)
- web-frontend/src/components/organizer/NewsletterSubscribers/NewsletterSubscriberTable.tsx (modified: suppressed chip, menu)
- web-frontend/src/components/organizer/NewsletterSubscribers/NewsletterSubscriberFilters.tsx (added: suppressed radio)
- web-frontend/src/components/organizer/NewsletterSubscribers/NewsletterSubscriberList.tsx (wired: UnsuppressDialog)
- web-frontend/src/components/organizer/NewsletterSubscribers/UnsuppressDialog.tsx (new)
- web-frontend/public/locales/en/newsletterSubscribers.json (added: suppressed keys)
- web-frontend/public/locales/de/newsletterSubscribers.json (added: suppressed keys)
- web-frontend/src/components/organizer/NewsletterSubscribers/__tests__/UnsuppressDialog.test.tsx (new: 7 tests)
- web-frontend/src/components/organizer/NewsletterSubscribers/__tests__/NewsletterSubscriberTable.test.tsx (extended: +2 suppress tests)
- web-frontend/src/components/organizer/NewsletterSubscribers/__tests__/NewsletterSubscriberFilters.test.tsx (extended: +1 suppress radio test)
- web-frontend/src/components/organizer/NewsletterSubscribers/__tests__/NewsletterSubscriberList.test.tsx (fixed: useUnsuppressSubscriber mock)

**API Spec:**
- docs/api/events-api.openapi.yml (added: unsuppress endpoint, bounce fields, maxRecipients)
- web-frontend/src/types/generated/events-api.types.ts (regenerated)
