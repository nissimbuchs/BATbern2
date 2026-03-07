# Story 10.17: Email Reply-based Unsubscribe & Deregistration (Inbound Email Handler)

Status: ready-for-dev

<!-- Prerequisites: Story 10.12 (DeregistrationService.deregisterByEmail()) and Story 10.7 (NewsletterSubscriberService) MUST be done first -->

## Story

As a **subscriber or attendee**,
I want to unsubscribe from the newsletter or cancel my event registration simply by replying "UNSUBSCRIBE" or "CANCEL" to any email from BATbern,
so that I can manage my preferences without clicking any links — especially useful on mobile.

## Acceptance Criteria

1. **AC1 — AWS InboundEmailStack deploys cleanly**: CDK `InboundEmailStack` creates: (a) SES receiving rule set for `replies@batbern.ch`, (b) S3 bucket `batbern-inbound-emails-{env}`, (c) standard SQS queue `batbern-inbound-email-{env}` (standard queue — not FIFO; idempotent handlers make FIFO unnecessary). **Stack is deployed to `eu-west-1`** (AWS SES inbound email receiving is only supported in `us-east-1`, `us-west-2`, and `eu-west-1`; BATbern's primary region `eu-central-1` is not supported for inbound). Stack instantiation in `batbern-infrastructure.ts` includes `env: { region: 'eu-west-1' }` and `crossRegionReferences: true`. `cdk synth` passes.

2. **AC2 — Reply-To header on all outbound emails**: `EmailService.java` in `shared-kernel` adds `Reply-To: replies@batbern.ch` to ALL outbound emails (both `sendHtmlEmail()` and `sendHtmlEmailWithAttachments()`). Existing emails are unaffected in functionality; only the header is added.

3. **AC3 — Unsubscribe via reply**: Replying "unsubscribe" (case-insensitive, any language variant: unsubscribe/abmelden/désinscription) to any BATbern email → subscriber is unsubscribed; `unsubscribe-confirmation-{locale}` email sent to sender.

4. **AC4 — Deregistration via reply**: Replying "cancel" (variants: cancel/deregister/abmelden/absagen) to a registration confirmation email (subject contains event code pattern "BATbernXX") → registration cancelled; `WaitlistPromotionService.promoteFromWaitlist()` fires; `cancel-confirmation-{locale}` email sent.

5. **AC5 — Unknown reply → silent discard**: Unrecognized reply body → log `WARN` with sender email (truncated) and discard. No error thrown. No email sent to sender.

6. **AC6 — Anti-abuse rate limiter**: >10 inbound emails from same sender address within 1 hour → excess messages discarded and logged. Implemented with Caffeine in-memory rate limiter (`LoadingCache<String, AtomicInteger>`).

7. **AC7 — Confirmation email templates seeded**: `unsubscribe-confirmation-de.html`, `unsubscribe-confirmation-en.html`, `cancel-confirmation-de.html`, `cancel-confirmation-en.html` exist as classpath HTML files; `EmailTemplateSeedService` auto-seeds them into the DB on startup. Templates editable in the admin Email Templates tab.

8. **AC8 — TDD compliance**: `InboundEmailRouterTest.java` covers all routing paths (unit, with Mockito). `InboundEmailListenerServiceTest.java` covers message parsing (unit, with mocked S3Client). All tests pass; Checkstyle passes; CDK synth passes.

9. **AC9 — `unsubscribeByEmail()` added to `NewsletterSubscriberService`**: New public method that looks up subscriber by email and sets `unsubscribedAt = now()`. Silent no-op if not found or already unsubscribed (anti-enumeration).

---

## Tasks / Subtasks

### Phase 0: Dependency — Verify Prerequisites (Before Starting)

- [ ] **T0 — Confirm Story 10.12 is complete** (AC: #4)
  - [ ] T0.1 — Verify `DeregistrationService.java` exists in `services/event-management-service/src/main/java/ch/batbern/events/service/`
  - [ ] T0.2 — Verify `deregisterByEmail(String email, String eventCode)` method exists **with exactly this signature** (2 String params). If method exists but with a different signature (e.g., 3 params with a `reason` arg), update the `InboundEmailRouter.java` call in T13.4 accordingly.
  - [ ] T0.3 — If missing: pause and complete Story 10.12 first (it provides the deregistration service)

### Phase 1: Infrastructure — CDK InboundEmailStack (TDD First)

- [ ] **T1 — Write CDK unit test FIRST (RED phase)** (AC: #1, #8)
  - [ ] T1.1 — Create `infrastructure/test/unit/inbound-email-stack.test.ts`
  - [ ] T1.2 — Test: SES ReceiptRuleSet resource exists with name `batbern-inbound-{env}`
  - [ ] T1.3 — Test: SES ReceiptRule routes to S3 + SQS actions
  - [ ] T1.4 — Test: S3 bucket `batbern-inbound-emails-{env}` exists with server-side encryption
  - [ ] T1.5 — Test: SQS queue `batbern-inbound-email-{env}` exists
  - [ ] T1.6 — Test: S3 bucket policy allows SES to put objects
  - [ ] T1.7 — Test: SQS queue policy allows SES to send messages
  - [ ] T1.8 — Run to confirm RED: `cd infrastructure && npm test -- inbound-email-stack.test.ts 2>&1 | tee /tmp/test-10-17-cdk-red.log`

- [ ] **T2 — Create `InboundEmailStack`** (AC: #1)
  - [ ] T2.1 — Create `infrastructure/lib/stacks/inbound-email-stack.ts`
  - [ ] T2.2 — Interface `InboundEmailStackProps extends cdk.StackProps { config: EnvironmentConfig; emsTaskRole: iam.IRole; }`
  - [ ] T2.3 — Create S3 bucket for raw inbound emails:
    ```typescript
    const inboundBucket = new s3.Bucket(this, 'InboundEmailBucket', {
      bucketName: `batbern-inbound-emails-${envName}`,
      encryption: s3.BucketEncryption.S3_MANAGED,
      blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
      lifecycleRules: [{ expiration: cdk.Duration.days(7) }], // auto-purge raw emails after 7 days
    });
    ```
  - [ ] T2.4 — Grant SES permission to write to S3 bucket (bucket policy):
    ```typescript
    inboundBucket.addToResourcePolicy(new iam.PolicyStatement({
      principals: [new iam.ServicePrincipal('ses.amazonaws.com')],
      actions: ['s3:PutObject'],
      resources: [`${inboundBucket.bucketArn}/*`],
      conditions: { StringEquals: { 'aws:Referer': this.account } },
    }));
    ```
  - [ ] T2.5 — Create SQS queue:
    ```typescript
    const inboundQueue = new sqs.Queue(this, 'InboundEmailQueue', {
      queueName: `batbern-inbound-email-${envName}`,
      visibilityTimeout: cdk.Duration.seconds(300),
      retentionPeriod: cdk.Duration.days(1),
      deadLetterQueue: { queue: dlq, maxReceiveCount: 3 },
    });
    ```
  - [ ] T2.6 — Create DLQ (dead-letter queue) for failed messages
  - [ ] T2.7 — Grant SES permission to send messages to SQS (queue policy)
  - [ ] T2.8 — Create SES ReceiptRuleSet (S3-only action; SQS notified via S3 event notification — no SNS hop needed):
    ```typescript
    const ruleSet = new ses.ReceiptRuleSet(this, 'InboundRuleSet', {
      receiptRuleSetName: `batbern-inbound-${envName}`,
    });
    ruleSet.addRule('RouteReplies', {
      recipients: [`replies@batbern.ch`],
      actions: [
        new sesActions.S3({ bucket: inboundBucket, objectKeyPrefix: 'emails/' }),
      ],
    });
    // S3 event notification routes directly to SQS (no SNS intermediary needed)
    inboundBucket.addEventNotification(
      s3.EventType.OBJECT_CREATED,
      new s3n.SqsDestination(inboundQueue),
    );
    ```
    *(Import `s3n` from `aws-cdk-lib/aws-s3-notifications`.)*
  - [ ] T2.9 — Export `inboundQueue.queueUrl` as output for EMS env var injection
  - [ ] T2.10 — Export `inboundBucket.bucketName` as output for EMS env var injection
  - [ ] T2.11 — Grant EMS task role permissions (if `props.emsTaskRole` provided):
    ```typescript
    inboundQueue.grantConsumeMessages(props.emsTaskRole);
    inboundBucket.grantRead(props.emsTaskRole);
    ```
  - [ ] T2.12 — Apply environment tags to all resources

- [ ] **T3 — Wire InboundEmailStack into `bin/batbern-infrastructure.ts`** (AC: #1)
  - [ ] T3.1 — Import `InboundEmailStack` in the bin file
  - [ ] T3.2 — Create stack instance ONLY in `CLOUD` mode (staging/production), with **`eu-west-1` region** and `crossRegionReferences: true`:
    ```typescript
    new InboundEmailStack(app, `${stackPrefix}-InboundEmail`, {
      config,
      env: { account: env.account, region: 'eu-west-1' }, // SES inbound requires eu-west-1
      crossRegionReferences: true, // required: SQS queue in eu-west-1 referenced by EMS in eu-central-1
      emsTaskRole: eventManagementStack.service.taskDefinition.taskRole,
    });
    ```
  - [ ] T3.3 — `inboundEmailStack.addDependency(sesStack)` (SES must be set up first)
  - [ ] T3.4 — `inboundEmailStack.addDependency(eventManagementStack)` (EMS task role must exist)
  - [ ] T3.5 — Pass `inboundQueue.queueUrl` as env var `AWS_INBOUND_EMAIL_QUEUE_URL` to EMS (update `EventManagementStack` `additionalEnvironment`)
  - [ ] T3.6 — Pass `inboundBucket.bucketName` as env var `AWS_INBOUND_EMAIL_BUCKET_NAME` to EMS
  - [ ] T3.7 — Run: `cd infrastructure && npm run synth:staging 2>&1 | tee /tmp/synth-10-17.log && grep -i "error\|Error" /tmp/synth-10-17.log | head -20`

- [ ] **T4 — CDK tests GREEN** (AC: #8)
  - [ ] T4.1 — Run: `cd infrastructure && npm test -- inbound-email-stack.test.ts 2>&1 | tee /tmp/test-10-17-cdk-green.log`
  - [ ] T4.2 — All tests pass

### Phase 2: Spring Dependency — Add spring-cloud-aws-starter-sqs

**CRITICAL**: `spring-cloud-aws` is NOT currently in this project. It must be added.

- [ ] **T5 — Add `spring-cloud-aws` to event-management-service** (AC: #1)
  - [ ] T5.1 — Determine current Spring Boot 3.x compatible version:
    - Spring Cloud AWS 3.3.x is compatible with Spring Boot 3.3.x
    - Spring Cloud AWS 3.2.x is compatible with Spring Boot 3.2.x
    - Check `./gradlew :services:event-management-service:dependencyInsight --dependency spring-boot 2>&1 | head -20` to find Boot version
  - [ ] T5.2 — Add BOM import to `services/event-management-service/build.gradle`:
    ```groovy
    // Spring Cloud AWS BOM (for SQS listener — Story 10.17)
    implementation platform('io.awspring.cloud:spring-cloud-aws-dependencies:3.3.0')
    implementation 'io.awspring.cloud:spring-cloud-aws-starter-sqs'
    ```
  - [ ] T5.3 — Verify dependency resolves: `./gradlew :services:event-management-service:dependencies --configuration runtimeClasspath 2>&1 | tee /tmp/deps-10-17.log && grep "awspring\|spring-cloud-aws" /tmp/deps-10-17.log | head -10`
  - [ ] T5.4 — Ensure no version conflict with existing `software.amazon.awssdk:bom:2.39.0` (AWS SDK v2 BOM). Both should coexist — awspring uses AWS SDK v2 internally.
  - [ ] T5.5 — **Prevent integration test startup failure**: `spring-cloud-aws-starter-sqs` auto-configures `SqsAsyncClient` on startup; in test/local environments (no AWS credentials, no real SQS) this causes all integration tests to fail. Add a no-op mock bean to prevent this:
    - Open `services/event-management-service/src/test/java/ch/batbern/events/AbstractIntegrationTest.java`
    - Add at class level: `@MockitoBean SqsAsyncClient sqsAsyncClient;`
    - Run ALL integration tests to confirm they still pass: `./gradlew :services:event-management-service:test 2>&1 | tee /tmp/test-10-17-integration-check.log && grep -E "FAILED|tests|errors" /tmp/test-10-17-integration-check.log | tail -5`
    - **DO NOT proceed to Phase 3+ until integration tests pass**

### Phase 3: Shared Kernel — Reply-To Header

- [ ] **T6 — Update `EmailService.java` to add Reply-To header** (AC: #2)
  - [ ] T6.1 — Read `shared-kernel/src/main/java/ch/batbern/shared/service/EmailService.java` fully first
  - [ ] T6.2 — Add `@Value("${app.email.reply-to:replies@batbern.ch}") private String replyToEmail;`
  - [ ] T6.3 — In `sendHtmlEmail()`: update `SendEmailRequest` to include `replyToAddresses(List.of(replyToEmail))`:
    ```java
    SendEmailRequest request = SendEmailRequest.builder()
        .source(String.format("%s <%s>", fromName, fromEmail))
        .replyToAddresses(replyToEmail)
        .destination(Destination.builder().toAddresses(to).build())
        // ... rest unchanged
    ```
  - [ ] T6.4 — In `sendHtmlEmailWithAttachments()`: add `Reply-To` MIME header to the `MimeMessage`:
    ```java
    message.setReplyTo(InternetAddress.parse(replyToEmail));
    ```
    (after `message.setSubject(...)`)
  - [ ] T6.5 — Rebuild shared-kernel: `./gradlew :shared-kernel:build publishToMavenLocal 2>&1 | tee /tmp/sk-10-17.log && grep -i "error\|fail" /tmp/sk-10-17.log | head -10`

### Phase 4: NewsletterSubscriberService — Add `unsubscribeByEmail()`

- [ ] **T7 — Write unit test FIRST for `unsubscribeByEmail()`** (AC: #8, #9)
  - [ ] T7.1 — Locate `NewsletterSubscriberServiceTest.java` (create if it doesn't exist alongside `NewsletterSubscriberService.java`)
  - [ ] T7.2 — Add test: `unsubscribeByEmail() with known active email → sets unsubscribedAt`
  - [ ] T7.3 — Add test: `unsubscribeByEmail() with unknown email → silent no-op, no exception`
  - [ ] T7.4 — Add test: `unsubscribeByEmail() with already-unsubscribed email → silent no-op`
  - [ ] T7.5 — Run to confirm RED: `./gradlew :services:event-management-service:test --tests "*.NewsletterSubscriberServiceTest" 2>&1 | tee /tmp/test-10-17-newsletter-red.log`

- [ ] **T8 — Implement `unsubscribeByEmail()`** (AC: #9)
  - [ ] T8.1 — Add to `NewsletterSubscriberService.java`:
    ```java
    /**
     * Unsubscribe a subscriber by email address (Story 10.17 — inbound email).
     * Silent no-op if email not found or already unsubscribed (anti-enumeration).
     */
    @Transactional
    public void unsubscribeByEmail(String email) {
        subscriberRepository.findByEmail(email)
            .filter(sub -> sub.getUnsubscribedAt() == null)
            .ifPresent(sub -> {
                sub.setUnsubscribedAt(Instant.now());
                subscriberRepository.save(sub);
                log.info("Unsubscribed newsletter subscriber via inbound email: {}", sub.getEmail());
            });
    }
    ```
  - [ ] T8.2 — Run tests GREEN: `./gradlew :services:event-management-service:test --tests "*.NewsletterSubscriberServiceTest" 2>&1 | tee /tmp/test-10-17-newsletter-green.log`

### Phase 5: Backend — Inbound Email Processing (TDD)

- [ ] **T9 — Write `InboundEmailRouterTest.java` (RED phase)** (AC: #8)
  - [ ] T9.1 — Create `services/event-management-service/src/test/java/ch/batbern/events/service/InboundEmailRouterTest.java`
  - [ ] T9.2 — Unit test (Mockito — no Spring context needed):
    ```java
    @ExtendWith(MockitoExtension.class)
    class InboundEmailRouterTest {
        @Mock NewsletterSubscriberService newsletterSubscriberService;
        @Mock DeregistrationService deregistrationService;
        @Mock InboundEmailConfirmationEmailService confirmationEmailService;
        @Mock InboundEmailRateLimiter rateLimiter;
        @InjectMocks InboundEmailRouter router;
    ```
  - [ ] T9.3 — Test: body "unsubscribe" → `newsletterSubscriberService.unsubscribeByEmail(email)` called; confirmation sent
  - [ ] T9.4 — Test: body "UNSUBSCRIBE" (uppercase) → same (case-insensitive)
  - [ ] T9.5 — Test: body "abmelden" → same
  - [ ] T9.6 — Test: body "désinscription" → same
  - [ ] T9.7 — Test: body "cancel" + subject "Re: BATbern42 Registration Confirmation" → `deregistrationService.deregisterByEmail(email, "BATbern42")` called
  - [ ] T9.8 — Test: body "absagen" + subject with event code → deregistration called
  - [ ] T9.9 — Test: body "hello world" → nothing called (silent discard)
  - [ ] T9.10 — Test: rate limiter returns false → nothing called; `rateLimiter.isAllowed(email)` checked first
  - [ ] T9.11 — Run to confirm RED: `./gradlew :services:event-management-service:test --tests "*.InboundEmailRouterTest" 2>&1 | tee /tmp/test-10-17-router-red.log`

- [ ] **T10 — Write `InboundEmailListenerServiceTest.java` (RED phase)** (AC: #8)
  - [ ] T10.1 — Create `services/event-management-service/src/test/java/ch/batbern/events/service/InboundEmailListenerServiceTest.java`
  - [ ] T10.2 — Unit test with mocked `S3Client` and `InboundEmailRouter`:
    - Test: valid S3 notification JSON → MIME parsed → `router.route()` called with correct ParsedEmail
    - Test: S3 fetch failure → exception logged; no router call; message discarded gracefully
    - Test: MIME parse failure → exception logged; no router call
    - Test: empty S3 object key → early return; no action
  - [ ] T10.3 — Run to confirm RED: `./gradlew :services:event-management-service:test --tests "*.InboundEmailListenerServiceTest" 2>&1 | tee /tmp/test-10-17-listener-red.log`

- [ ] **T11 — Create `InboundEmailConfig.java`** (AC: #1)
  - [ ] T11.1 — Create `services/event-management-service/src/main/java/ch/batbern/events/config/InboundEmailConfig.java`
  - [ ] T11.2 — `@Configuration` that configures S3Client bean for inbound bucket (reuse existing AwsS3Config pattern if one exists, or inject from AWS auto-config)
  - [ ] T11.3 — Map env var `AWS_INBOUND_EMAIL_QUEUE_URL` to property `aws.inbound-email.queue-url` in `application.yml`:
    ```yaml
    aws:
      inbound-email:
        queue-url: ${AWS_INBOUND_EMAIL_QUEUE_URL:}
        enabled: ${AWS_INBOUND_EMAIL_ENABLED:false}
    ```
  - [ ] T11.4 — Annotate `InboundEmailListenerService` with `@ConditionalOnProperty` to prevent it from loading when inbound email is not configured (local dev, CI):
    ```java
    @Service
    @RequiredArgsConstructor
    @Slf4j
    @ConditionalOnProperty(name = "aws.inbound-email.enabled", havingValue = "true", matchIfMissing = false)
    public class InboundEmailListenerService {
    ```
  - [ ] T11.5 — Set `aws.inbound-email.enabled=true` in staging and production `application.yml` (or via ECS environment variable `AWS_INBOUND_EMAIL_ENABLED=true` injected by CDK). Local dev and test environments default to `false` — listener never starts.
    **NOTE**: Do NOT use `@SqsListener("${aws.inbound-email.queue-url:#{null}}")` — Spring Cloud AWS 3.x does not support `null` as a queue identifier and will throw at startup.

- [ ] **T12 — Create `InboundEmailRateLimiter.java`** (AC: #6)
  - [ ] T12.1 — Create `services/event-management-service/src/main/java/ch/batbern/events/service/InboundEmailRateLimiter.java`
  - [ ] T12.2 — `@Component` using Caffeine `LoadingCache<String, AtomicInteger>`:
    ```java
    // Expire entries after 1 hour (sliding window per sender)
    private final LoadingCache<String, AtomicInteger> senderCount = Caffeine.newBuilder()
        .expireAfterWrite(1, TimeUnit.HOURS)
        .build(key -> new AtomicInteger(0));
    ```
  - [ ] T12.3 — `public boolean isAllowed(String senderEmail)`: increment counter for sender; return `count <= 10`
  - [ ] T12.4 — Caffeine is already in the project (`com.github.ben-manes.caffeine:caffeine:3.2.3`) — no new dependency needed

- [ ] **T13 — Create `InboundEmailRouter.java`** (AC: #3, #4, #5, #6)
  - [ ] T13.1 — Create `services/event-management-service/src/main/java/ch/batbern/events/service/InboundEmailRouter.java`
  - [ ] T13.2 — `@Service @RequiredArgsConstructor @Slf4j`; inject `NewsletterSubscriberService`, `DeregistrationService`, `InboundEmailConfirmationEmailService`, `InboundEmailRateLimiter`
  - [ ] T13.3 — `public record ParsedEmail(String senderEmail, String subject, String bodyFirstLine) {}`
  - [ ] T13.4 — `public void route(ParsedEmail email)`:
    ```java
    // 1. Rate limit check
    if (!rateLimiter.isAllowed(email.senderEmail())) {
        log.warn("Rate limit exceeded for sender: {}***", email.senderEmail().substring(0, Math.min(5, email.senderEmail().length())));
        return;
    }
    // 2. Normalize body
    String body = email.bodyFirstLine().toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9éàèêëîïôùûü]", " ").strip();
    // 3. Route: unsubscribe variants
    if (body.contains("unsubscribe") || body.contains("abmelden") || body.contains("d sinscription")) {
        newsletterSubscriberService.unsubscribeByEmail(email.senderEmail());
        confirmationEmailService.sendUnsubscribeConfirmation(email.senderEmail());
        log.info("Processed unsubscribe reply from: {}***", email.senderEmail().substring(0, 5));
        return;
    }
    // 4. Route: deregistration variants (must have event code in subject)
    if (body.contains("cancel") || body.contains("deregister") || body.contains("abmelden") || body.contains("absagen")) {
        String eventCode = extractEventCode(email.subject()); // extract from "Re: BATbernXX ..."
        if (eventCode != null) {
            deregistrationService.deregisterByEmail(email.senderEmail(), eventCode);
            confirmationEmailService.sendCancelConfirmation(email.senderEmail(), eventCode);
            log.info("Processed deregistration reply from: {}*** for event: {}", email.senderEmail().substring(0, 5), eventCode);
        } else {
            log.warn("Deregistration keyword but no event code in subject: {}", email.subject());
        }
        return;
    }
    // 5. Unrecognized — silent discard
    log.warn("Unrecognized inbound email body from: {}*** — discarding", email.senderEmail().substring(0, 5));
    ```
  - [ ] T13.5 — `private String extractEventCode(String subject)`: regex `BATbern\d+` on subject line; return matched string or null

- [ ] **T14 — Create `InboundEmailListenerService.java`** (AC: #1, #3, #4, #5)
  - [ ] T14.1 — Create `services/event-management-service/src/main/java/ch/batbern/events/service/InboundEmailListenerService.java`
  - [ ] T14.2 — `@Service @RequiredArgsConstructor @Slf4j`
  - [ ] T14.3 — Inject `S3Client s3Client`, `InboundEmailRouter router`, `@Value("${AWS_INBOUND_EMAIL_BUCKET_NAME:}") String bucketName`
  - [ ] T14.4 — SQS listener (class is only loaded when `@ConditionalOnProperty` is satisfied — see T11.4):
    ```java
    @SqsListener("${aws.inbound-email.queue-url}")
    public void handleS3Notification(String messageBody) {
        // messageBody is S3 event notification JSON
        // 1. Parse JSON to extract bucket + key
        // 2. Fetch raw email bytes from S3
        // 3. Parse MIME with jakarta.mail.internet.MimeMessage
        // 4. Extract: From header, Subject header, first non-quoted plain text line
        // 5. Call router.route(new ParsedEmail(from, subject, bodyFirstLine))
    }
    ```
    *(No `:#{null}` default needed — the `@ConditionalOnProperty` on the class prevents this bean from being registered when the property is not set.)*
  - [ ] T14.5 — JSON parsing for S3 event notification:
    ```java
    // S3 notification format:
    // { "Records": [{ "s3": { "bucket": { "name": "..." }, "object": { "key": "..." } } }] }
    // Use Jackson ObjectMapper (already in Spring context)
    ```
  - [ ] T14.6 — MIME parsing pattern (reuse existing `jakarta.mail` already on classpath from `sendHtmlEmailWithAttachments`):
    ```java
    Session session = Session.getInstance(new Properties());
    MimeMessage message = new MimeMessage(session, rawEmailInputStream);
    String from = InternetAddress.parse(message.getHeader("From")[0])[0].getAddress();
    String subject = message.getSubject();
    String bodyText = extractFirstPlainTextLine(message);
    ```
  - [ ] T14.7 — `extractFirstPlainTextLine()`: recursively walk multipart; find first `text/plain` part; get content as String; split by `\n`; find first non-empty non-`>` line (skip quoted lines starting with `>`)
  - [ ] T14.8 — Wrap everything in try-catch; log errors, do NOT rethrow (prevents SQS message from being redelivered infinitely for parse errors)

- [ ] **T15 — Create `InboundEmailConfirmationEmailService.java`** (AC: #7)
  - [ ] T15.1 — Create `services/event-management-service/src/main/java/ch/batbern/events/service/InboundEmailConfirmationEmailService.java`
  - [ ] T15.2 — `@Service @RequiredArgsConstructor @Slf4j`
  - [ ] T15.3 — Inject `EmailTemplateService emailTemplateService`, `EmailService emailService`
  - [ ] T15.4 — `sendUnsubscribeConfirmation(String email)`: load `unsubscribe-confirmation` template (locale detected from subscriber record or default `en`); send via `emailService.sendHtmlEmail()`
  - [ ] T15.5 — `sendCancelConfirmation(String email, String eventCode)`: load `cancel-confirmation` template; inject `eventCode` variable; send
  - [ ] T15.6 — If template not found → log warning, do NOT throw (confirmation is best-effort)

- [ ] **T16 — Run backend tests GREEN** (AC: #8)
  - [ ] T16.1 — `./gradlew :services:event-management-service:test --tests "*.InboundEmailRouterTest" 2>&1 | tee /tmp/test-10-17-router-green.log`
  - [ ] T16.2 — `./gradlew :services:event-management-service:test --tests "*.InboundEmailListenerServiceTest" 2>&1 | tee /tmp/test-10-17-listener-green.log`
  - [ ] T16.3 — `./gradlew :services:event-management-service:test --tests "*.NewsletterSubscriberServiceTest" 2>&1 | tee /tmp/test-10-17-newsletter-final.log`
  - [ ] T16.4 — Run full EMS test suite: `./gradlew :services:event-management-service:test 2>&1 | tee /tmp/test-10-17-full.log && grep -E "FAILED|PASSED|tests|errors" /tmp/test-10-17-full.log | tail -10`

### Phase 6: Email Templates

- [ ] **T17 — Create confirmation email templates** (AC: #7)
  - [ ] T17.1 — Create `services/event-management-service/src/main/resources/email-templates/unsubscribe-confirmation-de.html`:
    ```html
    <!-- subject: Newsletter Abmeldung bestätigt -->
    <p>Ihre Newsletter-Abmeldung wurde erfolgreich verarbeitet.</p>
    <p>Sie werden keine weiteren E-Mails von BATbern erhalten.</p>
    <p>Falls Sie sich irrtümlich abgemeldet haben, können Sie sich jederzeit unter <a href="{{baseUrl}}">batbern.ch</a> wieder anmelden.</p>
    ```
  - [ ] T17.2 — Create `unsubscribe-confirmation-en.html`:
    ```html
    <!-- subject: Newsletter Unsubscribe Confirmed -->
    <p>Your newsletter unsubscription has been successfully processed.</p>
    <p>You will no longer receive emails from BATbern.</p>
    <p>If you unsubscribed by mistake, you can re-subscribe at <a href="{{baseUrl}}">batbern.ch</a> at any time.</p>
    ```
  - [ ] T17.3 — Create `cancel-confirmation-de.html`:
    ```html
    <!-- subject: Abmeldung für {{eventCode}} bestätigt -->
    <p>Ihre Anmeldung für <strong>{{eventCode}}</strong> wurde erfolgreich storniert.</p>
    <p>Ihr Platz wurde an die Warteliste weitergegeben.</p>
    ```
  - [ ] T17.4 — Create `cancel-confirmation-en.html`:
    ```html
    <!-- subject: Deregistration for {{eventCode}} Confirmed -->
    <p>Your registration for <strong>{{eventCode}}</strong> has been successfully cancelled.</p>
    <p>Your spot has been released to the waiting list.</p>
    ```
  - [ ] T17.5 — Templates use the `batbern-default` layout (same pattern as other content templates — `EmailTemplateSeedService` handles the layout wrapping automatically on render)
  - [ ] T17.6 — Verify seeding works: start EMS locally and check DB for new template keys

### Phase 7: Full Suite Validation

- [ ] **T18 — Checkstyle and build** (AC: #8)
  - [ ] T18.1 — `./gradlew :services:event-management-service:checkstyleMain 2>&1 | tee /tmp/checkstyle-10-17.log && grep -i "violation\|error" /tmp/checkstyle-10-17.log | head -20`
  - [ ] T18.2 — `./gradlew :shared-kernel:checkstyleMain 2>&1 | tee /tmp/checkstyle-sk-10-17.log`
  - [ ] T18.3 — `./gradlew :services:event-management-service:build 2>&1 | tee /tmp/build-10-17.log && grep -E "BUILD|FAILED" /tmp/build-10-17.log`
  - [ ] T18.4 — CDK synth: `cd infrastructure && npm run synth:staging 2>&1 | tee /tmp/synth-staging-10-17.log && grep -i "error" /tmp/synth-staging-10-17.log | head -20`

---

## Dev Notes

### Architecture Overview

This story introduces the first **inbound email processing** capability to BATbern. The data flow is:

```
User replies to email
    → SES receives at replies@batbern.ch (SES receipt rule)
    → Raw email stored to S3 (batbern-inbound-emails-{env}/emails/{messageId})
    → S3 event notification → SQS queue (batbern-inbound-email-{env})
    → Spring @SqsListener in EMS polls SQS
    → Fetch raw email from S3
    → MIME parse: extract From, Subject, first plain-text body line
    → Rate limit check (Caffeine, 10/sender/hour)
    → Route: unsubscribe keywords → NewsletterSubscriberService.unsubscribeByEmail()
    → Route: cancel keywords + event code → DeregistrationService.deregisterByEmail()
    → Route: unknown → silent discard
    → Send confirmation email (best-effort)
```

### Critical Constraints

1. **`spring-cloud-aws-starter-sqs` is NOT in the project** — must be added to `event-management-service/build.gradle`. Use version `3.3.0` (compatible with Spring Boot 3.3.x). Check actual Boot version first.

2. **No new DB migrations** — V73-V75 are allocated to stories 10.12-10.16 (not yet applied). Story 10.17 has no schema changes.

3. **Prerequisite: Story 10.12 must be complete** — `DeregistrationService.deregisterByEmail(email, eventCode)` must exist. The method in story 10.12 is called `deregisterByEmail()` — story 10.17 will call it directly.

4. **`EmailTemplateSeedService` auto-discovers** templates from `classpath*:email-templates/*.html` using filename convention `{key}-{locale}.html`. New templates just need to be added as HTML files — no code changes needed.

5. **Caffeine is already in the project** (`com.github.ben-manes.caffeine:caffeine:3.2.3`). Use it directly for rate limiting without Spring's `@Cacheable` abstraction layer.

6. **`jakarta.mail` already on classpath** — both `shared-kernel` (EmailService) and `event-management-service` (Thymeleaf) include it. Can use `MimeMessage` for parsing in `InboundEmailListenerService`.

7. **`@SqsListener` conditional loading** — Use `@ConditionalOnProperty(name = "aws.inbound-email.enabled", havingValue = "true", matchIfMissing = false)` on `InboundEmailListenerService` class. This prevents the SQS listener from registering in local/test environments. **Do NOT use** `@SqsListener("${aws.inbound-email.queue-url:#{null}}")` — Spring Cloud AWS 3.x does not support `null` as a queue identifier and throws at startup.

8. **AWS SES receipt rule prerequisite**: SES domain `batbern.ch` must already be verified (it is — used for outbound). The `replies@batbern.ch` address just needs an inbound receipt rule.

9. **Integration test startup fix (CRITICAL)**: Adding `spring-cloud-aws-starter-sqs` triggers `SqsAsyncClient` auto-configuration. Without a mock, ALL integration tests in EMS will fail to start. Add `@MockitoBean SqsAsyncClient sqsAsyncClient;` to `AbstractIntegrationTest.java` as part of T5.5.

10. **Cross-region deployment (CRITICAL)**: `InboundEmailStack` **must** be deployed with `env: { region: 'eu-west-1' }` and `crossRegionReferences: true` in `batbern-infrastructure.ts`. AWS SES inbound receiving is not available in `eu-central-1` (BATbern's primary region).

### Project Structure Notes — New Files

```
infrastructure/lib/stacks/
└── inbound-email-stack.ts                          ← NEW: SES→S3→SQS pipeline

infrastructure/test/unit/
└── inbound-email-stack.test.ts                     ← NEW: CDK unit tests

shared-kernel/src/main/java/ch/batbern/shared/service/
└── EmailService.java                               ← MODIFIED: add Reply-To header

services/event-management-service/
├── build.gradle                                    ← MODIFIED: add spring-cloud-aws-starter-sqs
├── src/main/java/ch/batbern/events/
│   ├── config/
│   │   └── InboundEmailConfig.java                 ← NEW: SQS listener conditional configuration
│   └── service/
│       ├── InboundEmailListenerService.java         ← NEW: @SqsListener, S3 fetch, MIME parse
│       ├── InboundEmailRouter.java                  ← NEW: routes parsed email to action
│       ├── InboundEmailRateLimiter.java             ← NEW: Caffeine rate limiter (10/hr/sender)
│       ├── InboundEmailConfirmationEmailService.java ← NEW: sends confirmation after action
│       └── NewsletterSubscriberService.java         ← MODIFIED: add unsubscribeByEmail()
└── src/main/resources/email-templates/
    ├── unsubscribe-confirmation-de.html             ← NEW
    ├── unsubscribe-confirmation-en.html             ← NEW
    ├── cancel-confirmation-de.html                  ← NEW
    └── cancel-confirmation-en.html                  ← NEW
└── src/test/java/ch/batbern/events/service/
    ├── InboundEmailRouterTest.java                  ← NEW: unit tests for routing logic
    └── InboundEmailListenerServiceTest.java         ← NEW: MIME parsing unit tests

infrastructure/bin/batbern-infrastructure.ts        ← MODIFIED: wire InboundEmailStack
infrastructure/lib/stacks/event-management-stack.ts ← MODIFIED: new env vars + IAM grants
```

### Architecture Compliance

- **Patterns to follow**:
  - `@SqsListener` follows same `@SqsListener` annotation pattern as Spring Cloud AWS 3.x docs
  - CDK stack pattern: follows `ses-stack.ts` (simple, tags, single responsibility)
  - Rate limiter pattern: follows `CaffeineCacheConfig.java` and existing Caffeine usage
  - Email service pattern: follows `RegistrationEmailService.java` (inject `EmailTemplateService` + `EmailService`)
  - Test class pattern: follows `NewsletterControllerIntegrationTest.java` for integration tests

- **DO NOT**:
  - Add Redis or external rate limiting service (Caffeine is the project standard)
  - Use H2 for integration tests (always Testcontainers PostgreSQL via `AbstractIntegrationTest`)
  - Expose new REST endpoints (this is fully event-driven via SQS, no HTTP endpoints)
  - Hardcode `replies@batbern.ch` in multiple places — use `@Value("${app.email.reply-to:replies@batbern.ch}")` from shared config

### CDK SES Receiving Region Note

**IMPORTANT**: AWS SES email receiving is only supported in `us-east-1`, `us-west-2`, and `eu-west-1`. BATbern's primary region is `eu-central-1` which does NOT support inbound receiving.

**Decision**: Deploy `InboundEmailStack` to `eu-west-1` with `crossRegionReferences: true`. The SQS queue lives in `eu-west-1`; EMS (in `eu-central-1`) polls it cross-region. CDK handles the cross-region SSM parameter exports automatically when `crossRegionReferences: true`.

- Outbound SES remains in `eu-central-1` (unchanged — no impact on existing email sending)
- Inbound SES receipt rule in `eu-west-1` — `replies@batbern.ch` receiving
- SES domain `batbern.ch` must be verified in `eu-west-1` as well (verify via SES console or CDK `ses.EmailIdentity` in `eu-west-1` if not already done)

This is already reflected in T3.2 above.

### Scope Decisions (Intentional Simplifications)

1. **`In-Reply-To` / `References` headers not extracted**: The PRD scope listed these headers for matching the original email, but this story intentionally omits them. Routing is based solely on body keyword + event code extracted from the subject line — this is sufficient for all use cases and simpler to implement and test.

2. **No WireMock / integration test for SES/SQS**: The PRD Definition of Done mentioned "WireMock for SES in integration test." This story uses Mockito unit tests for `InboundEmailRouterTest` and `InboundEmailListenerServiceTest` instead. Unit tests cover all routing paths and MIME parsing scenarios — a WireMock integration test would add complexity without meaningful additional coverage at this scale.

3. **Standard SQS queue (not FIFO)**: FIFO would prevent duplicate processing, but `unsubscribeByEmail()` and `deregisterByEmail()` are already idempotent — a duplicate message causes no harm. Standard queue is sufficient and avoids FIFO's `.fifo` name suffix and throughput limits.

### References

- SES Receiving setup: AWS docs [inbound email receiving](https://docs.aws.amazon.com/ses/latest/dg/receiving-email.html)
- Spring Cloud AWS SQS: [awspring.io/spring-cloud-aws/3.3](https://awspring.io/spring-cloud-aws/3.3/docs/reference/html/)
- Caffeine `LoadingCache`: `com.github.ben-manes.caffeine:caffeine:3.2.3` (already in `build.gradle`)
- Existing MIME assembly (for parse reference): `EmailService.sendHtmlEmailWithAttachments()` [Source: shared-kernel/.../service/EmailService.java#L118-L191]
- EmailTemplateSeedService auto-discovery: [Source: services/event-management-service/.../service/EmailTemplateSeedService.java#L43-L57]
- Existing IAM grant pattern: [Source: infrastructure/lib/stacks/event-management-stack.ts#L119-L151]
- CDK stack wiring: [Source: infrastructure/bin/batbern-infrastructure.ts]

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

### Completion Notes List

### File List
