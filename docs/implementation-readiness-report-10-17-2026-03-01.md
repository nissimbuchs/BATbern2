---
stepsCompleted:
  - step-01-document-discovery
  - step-02-prd-analysis
  - step-03-epic-coverage-validation
  - step-04-ux-alignment
  - step-05-epic-quality-review
  - step-06-final-assessment
story: "10.17 - Email Reply-Based Unsubscribe & Deregistration"
date: "2026-03-01"
documentsUsed:
  prd: "docs/prd/epic-10-additional-stories.md"
  architecture:
    - "docs/architecture/01-system-overview.md"
    - "docs/architecture/06-backend-architecture.md"
    - "docs/architecture/06d-notification-system.md"
    - "docs/architecture/03-data-architecture.md"
    - "docs/architecture/04-api-event-management.md"
  storyArtifact: "_bmad-output/implementation-artifacts/10-17-email-reply-based-unsubscribe-deregistration.md"
  ux: "none (story artifact covers UX intent)"
---

# Implementation Readiness Assessment Report

**Date:** 2026-03-01
**Project:** BATbern
**Story:** 10.17 — Email Reply-Based Unsubscribe & Deregistration

---

## PRD Analysis

### Functional Requirements

FR1: SES receiving rule set for `replies@batbern.ch` — all inbound replies route to this address
FR2: S3 bucket `batbern-inbound-emails-{env}` stores raw inbound emails (7-day lifecycle expiry)
FR3: SQS queue `batbern-inbound-email-{env}` receives S3 event notifications for new emails
FR4: IAM — EMS task role receives `sqs:ReceiveMessage` + `sqs:DeleteMessage` on inbound queue + `s3:GetObject` on inbound bucket
FR5: CDK `InboundEmailStack` encapsulates all inbound email AWS infrastructure
FR6: `InboundEmailConfig.java` — reads `${AWS_INBOUND_EMAIL_QUEUE_URL}`; SQS listener bean is conditional (disabled locally)
FR7: `InboundEmailListenerService.java` — SQS listener; fetches raw email from S3; MIME-parses From header, Subject, and first non-quoted plain-text body line
FR8: `InboundEmailRouter.java` — routes parsed email to action based on body content:
  - Body contains "unsubscribe" / "abmelden" / "désinscription" → `NewsletterSubscriberService.unsubscribeByEmail(senderEmail)`
  - Body contains "cancel" / "deregister" / "abmelden" / "absagen" + event code from subject → `DeregistrationService.deregisterByEmail(senderEmail, eventCode)`
  - Unrecognized → log WARN + discard; no action; no error thrown
FR9: Event code extraction from reply subject — regex `BATbern\d+` on subject line
FR10: Anti-abuse rate limiter — max 10 inbound emails per sender per hour (Caffeine `LoadingCache`)
FR11: Confirmation email sent after successful action — `unsubscribe-confirmation-{locale}` or `cancel-confirmation-{locale}`
FR12: Reply-To header `replies@batbern.ch` added to ALL outbound emails (both `sendHtmlEmail()` and `sendHtmlEmailWithAttachments()`)
FR13: `NewsletterSubscriberService.unsubscribeByEmail(email)` — silent no-op if not found or already unsubscribed (anti-enumeration)
FR14: Four confirmation email templates: `unsubscribe-confirmation-de.html`, `unsubscribe-confirmation-en.html`, `cancel-confirmation-de.html`, `cancel-confirmation-en.html` — auto-seeded via `EmailTemplateSeedService` on startup
FR15: `spring-cloud-aws-starter-sqs` added as new dependency to `event-management-service/build.gradle`
FR16: TDD: `InboundEmailRouterTest.java` (Mockito unit, all routing paths) + `InboundEmailListenerServiceTest.java` (mocked S3Client, MIME parsing paths)
FR17: Deregistration via reply triggers `WaitlistPromotionService.promoteFromWaitlist()` (via Story 10.12's `DeregistrationService`)

**Total FRs: 17**

---

### Non-Functional Requirements

NFR1: **Privacy** — sender email truncated in all WARN/INFO log output (first 5 chars + `***`) to prevent PII in logs
NFR2: **Resilience** — S3 fetch failures, MIME parse failures, and JSON parse failures are caught and logged; no exception is rethrown (prevents infinite SQS redelivery)
NFR3: **Security** — rate limiter (10/sender/hour) prevents email-based abuse of unsubscribe/deregistration endpoints
NFR4: **Local dev safety** — SQS listener must NOT start when `AWS_INBOUND_EMAIL_QUEUE_URL` is empty; prevents startup failures in local/test environments
NFR5: **Zero DB schema impact** — no new Flyway migrations; story 10.17 has no schema changes
NFR6: **Idempotency** — unsubscribeByEmail is idempotent (already-unsubscribed = no-op); confirmation email is best-effort (if template not found, log warning, don't throw)
NFR7: **Cross-region deployment** — AWS SES inbound receiving only supported in `us-east-1`, `us-west-2`, `eu-west-1`; BATbern runs in `eu-central-1`; `InboundEmailStack` must be deployed to `eu-west-1` with `crossRegionReferences: true`
NFR8: **TDD compliance** — all new code follows Red-Green-Refactor; Checkstyle passes; CDK synth passes
NFR9: **Confirmation email is best-effort** — failure to send confirmation must not propagate to the SQS message handler (prevents infinite redelivery)

**Total NFRs: 9**

---

### Additional Requirements / Constraints

CONST1: **Story 10.12 prerequisite** — `DeregistrationService.deregisterByEmail(String email, String eventCode)` must exist before implementing Phase 5+. If missing, pause and complete 10.12.
CONST2: **Story 10.7 prerequisite** — `NewsletterSubscriberService` must exist with subscriber lookup by email.
CONST3: **Caffeine already on classpath** — `com.github.ben-manes.caffeine:caffeine:3.2.3` is already a dependency; no new dep needed for rate limiter.
CONST4: **jakarta.mail already on classpath** — available via shared-kernel and Thymeleaf; MimeMessage parsing available without new dependency.
CONST5: **EmailTemplateSeedService auto-discovery** — scans `classpath*:email-templates/*.html` by filename convention `{key}-{locale}.html`; new templates only need HTML files added, no code changes.
CONST6: **SES domain `batbern.ch` already verified** — for outbound from `eu-central-1`; inbound rule at `replies@batbern.ch` is additive.
CONST7: **No new REST endpoints** — fully event-driven via SQS; no HTTP API changes.

---

### PRD Completeness Assessment

The PRD for story 10.17 is **well-structured and sufficiently detailed** for a story of this complexity. The user story is clear, scope is enumerated, and the DoD provides concrete acceptance criteria.

**One minor inconsistency detected between PRD and story artifact:**
- PRD references `DeregistrationService.cancelByEmail()` but story artifact (correctly) calls `DeregistrationService.deregisterByEmail()` — aligning with Story 10.12's actual method name. The story artifact takes precedence here.

---

## Epic Coverage Validation

### Coverage Matrix

| FR | Requirement | Story 10.17 Coverage | Status |
|----|-------------|----------------------|--------|
| FR1 | SES receipt rule for `replies@batbern.ch` | AC1, T2.8 | ✅ Covered |
| FR2 | S3 bucket `batbern-inbound-emails-{env}` (7-day lifecycle) | AC1, T2.3 | ✅ Covered |
| FR3 | SQS queue `batbern-inbound-email-{env}` | AC1, T2.5 | ⚠️ Partial — AC1 says "FIFO queue" but T2.5 creates standard queue (no `fifo:true`) |
| FR4 | IAM grants — EMS task role → SQS + S3 | T2.11 | ✅ Covered |
| FR5 | CDK `InboundEmailStack` | AC1, Phase 1 tasks | ✅ Covered |
| FR6 | `InboundEmailConfig.java` — conditional SQS listener | T11 | ✅ Covered |
| FR7 | MIME parse: From, Subject, body first line | T14 | ⚠️ Partial — PRD also requires `In-Reply-To`/`References` header extraction; story artifact omits these |
| FR8 | Routing logic (unsubscribe / cancel / discard) | AC3–5, T13 | ✅ Covered |
| FR9 | Event code regex extraction from subject | T13.5 | ✅ Covered |
| FR10 | Rate limiter 10/sender/hour (Caffeine) | AC6, T12 | ✅ Covered |
| FR11 | Confirmation email after successful action | AC3, AC4, T15 | ✅ Covered |
| FR12 | Reply-To header on all outbound emails | AC2, T6 | ✅ Covered |
| FR13 | `unsubscribeByEmail()` — silent no-op if not found/already unsubscribed | AC9, T7–T8 | ✅ Covered |
| FR14 | 4 confirmation email templates seeded by `EmailTemplateSeedService` | AC7, T17 | ✅ Covered |
| FR15 | `spring-cloud-aws-starter-sqs` dependency | T5 | ✅ Covered |
| FR16 | TDD: `InboundEmailRouterTest` + `InboundEmailListenerServiceTest` | AC8, T9, T10 | ⚠️ Partial — PRD DoD specifies "WireMock for SES in integration test"; story uses Mockito unit tests only |
| FR17 | Deregistration triggers `WaitlistPromotionService` via Story 10.12 | AC4, T0 | ✅ Covered (via prerequisite dependency on 10.12) |

### Missing / Partial Requirements

#### ⚠️ Issue 1 — FIFO vs Standard SQS Queue (FR3)
- AC1 specifies "SQS FIFO queue" but CDK task T2.5 creates a standard SQS queue (no `fifo: true`, no `.fifo` name suffix)
- **Impact**: FIFO queues ensure exactly-once processing per message group — important for preventing duplicate unsubscribes/deregistrations
- **Recommendation**: Clarify intent. For inbound email processing at BATbern's scale a standard queue + idempotent handlers is sufficient. Update AC1 to say "standard SQS queue" OR update T2.5 to create a FIFO queue. Either is acceptable; just needs alignment.

#### ⚠️ Issue 2 — In-Reply-To / References Headers Not Extracted (FR7)
- PRD scope says: "Extract: sender email (From: header), **In-Reply-To / References headers** (to match original email)..."
- Story artifact: no task or AC mentions extracting these headers
- **Impact**: Without `In-Reply-To`, the router cannot determine *which* original email was replied to — e.g., to confirm that a cancel reply was for a registration email vs a newsletter email
- **Current mitigation**: Story relies on body keyword + event code in subject — which works without header matching. `In-Reply-To` extraction may have been intentionally dropped in favour of the simpler approach.
- **Recommendation**: Explicitly acknowledge this scope reduction in the story artifact dev notes. Add a task comment: "In-Reply-To/References headers are intentionally not used — subject line event code extraction is sufficient."

#### ⚠️ Issue 3 — WireMock Integration Test Missing from Story (FR16)
- PRD DoD: "TDD: `InboundEmailRouterTest` with mocked S3/SQS; **WireMock for SES in integration test**"
- Story artifact: Mockito unit tests only; no integration test or WireMock usage planned
- **Impact**: Lower than a full integration test, but story tests are comprehensive Mockito unit tests which verify the same paths
- **Recommendation**: Low severity. Either add a note in the story that WireMock/integration test is deferred (or not needed given the unit test coverage), or explicitly remove from PRD DoD to avoid the discrepancy.

### Coverage Statistics

- Total PRD FRs: 17
- FRs fully covered: 14 (82%)
- FRs partially covered / with gaps: 3 (FR3, FR7, FR16)
- FRs missing: 0
- **Overall coverage: 82% full / 100% addressed** (all FRs have some form of coverage; 3 need clarification)

---

## UX Alignment Assessment

### UX Document Status

**Not Found — and Not Required.**

Story 10.17 is a **fully backend + infrastructure story** with zero frontend changes. All functionality is event-driven:
- AWS CDK infrastructure (SES → S3 → SQS)
- Spring `@SqsListener` processing
- Email sending via existing `EmailService`

The only user-facing element is the **reply email itself** (the user types "UNSUBSCRIBE" in their email client and hits send) — this is not a designed UI, it's free-form text input via any email client.

### Alignment Issues

None. No UX document is expected or needed for this story.

### Warnings

ℹ️ **Email template content** (`unsubscribe-confirmation-de/en.html`, `cancel-confirmation-de/en.html`) has copywriting implications. The proposed template copy in the story artifact is a reasonable starting point, but the content (especially German copy and tone) should be reviewed by a native speaker before going to production. This is not a blocking readiness issue but worth flagging.

---

## Epic Quality Review

### Story Structure Validation

**User Value Focus** ✅
The user story is clearly user-centric: "As a subscriber or attendee, I want to unsubscribe / cancel my registration by replying to a BATbern email… especially useful on mobile." Tangible benefit for end-users who prefer reply-based actions over clicking links.

**Independence** ✅
All prerequisites (Stories 10.12, 10.7) are backward dependencies on already-completed or in-progress work. No forward dependencies to future stories.

**Story Sizing** ⚠️ Minor
The story spans CDK infrastructure, shared-kernel modification, new Spring dependency, 5 new Java classes, 4 email templates, and TDD tests. This is at the upper bound of a typical sprint story. It could be split (10.17a: CDK + Reply-To; 10.17b: backend routing + templates) but is self-contained and deployable as a single unit. Flag for awareness, not blocking.

---

### Acceptance Criteria Review

| AC | Specific | Testable | Error Coverage | Verdict |
|----|----------|----------|----------------|---------|
| AC1 — InboundEmailStack deploys | ✅ | ✅ | ✅ (CDK synth passes) | ✅ |
| AC2 — Reply-To header | ✅ | ✅ | ✅ (both send methods covered) | ✅ |
| AC3 — Unsubscribe via reply | ✅ | ✅ | ✅ (case-insensitive variants) | ✅ |
| AC4 — Deregistration via reply | ✅ | ✅ | ✅ (waitlist promotion, event code) | ✅ |
| AC5 — Unknown reply → discard | ✅ | ✅ | ✅ | ✅ |
| AC6 — Rate limiter | ✅ | ✅ | ✅ | ✅ |
| AC7 — Templates seeded | ✅ | ✅ | ✅ | ✅ |
| AC8 — TDD compliance | ✅ | ✅ | partial (see Issue 3 below) | ⚠️ |
| AC9 — unsubscribeByEmail() | ✅ | ✅ | ✅ (anti-enumeration) | ✅ |

---

### Quality Issues Found

#### 🔴 Critical: Cross-Region SES Constraint Not in Any AC (Issue 4)
- AWS SES inbound email receiving is **not supported** in `eu-central-1` (BATbern's primary region). It requires `eu-west-1`, `us-east-1`, or `us-west-2`.
- This constraint is buried in Dev Notes but absent from all ACs and tasks.
- **AC1 says**: "Stack deploys to staging without errors" — a developer could deploy `InboundEmailStack` to `eu-central-1` (the default) and satisfy AC1 while the feature silently fails (no inbound email ever arrives).
- **Recommendation**: AC1 must be updated to include: *"InboundEmailStack is deployed with `env: { region: 'eu-west-1' }` and `crossRegionReferences: true`; CDK synth confirms cross-region stack."* Also add T2.x explicitly: *"Set `env: { region: 'eu-west-1' }` on InboundEmailStack instantiation in bin file."*

#### 🔴 Critical: `spring-cloud-aws-starter-sqs` Will Break Integration Tests Without Mitigation Task (Issue 5)
- Adding `spring-cloud-aws-starter-sqs` to EMS causes Spring auto-configuration to initialize `SqsAsyncClient` on startup.
- In all local and test environments (no AWS credentials, no real SQS), this causes startup failure.
- **The Dev Notes mention this** but there is **no explicit task** to add a `@MockitoBean SqsAsyncClient sqsAsyncClient` to `AbstractIntegrationTest` or `TestAwsConfig`.
- Without this, **all EMS integration tests will fail** the moment T5 is merged — including `RegistrationCapacityIntegrationTest`, `WaitlistPromotionServiceTest`, and every other integration test.
- **Recommendation**: Add an explicit task after T5:
  > T5.5 — Add `@MockitoBean SqsAsyncClient sqsAsyncClient;` to `AbstractIntegrationTest.java` OR create/update `TestAwsConfig.java` with a `@Primary` no-op `SqsAsyncClient` bean. Run integration tests to confirm they still pass.

#### 🟠 Major: `@SqsListener` with Empty Queue URL — Null SpEL Approach Won't Work (Issue 6)
- T14.4 shows: `@SqsListener("${aws.inbound-email.queue-url:#{null}}")`
- Spring Cloud AWS 3.x's `@SqsListener` does NOT accept `null` as a valid queue identifier — it will attempt to resolve the queue and fail with an exception at startup.
- T11 has the correct approach (`@ConditionalOnProperty` on the bean) but T14 contradicts it.
- **Recommendation**: Remove the SpEL null approach from T14.4. The correct pattern is: make `InboundEmailListenerService` a `@ConditionalOnProperty(name = "aws.inbound-email.enabled", havingValue = "true", matchIfMissing = false)` bean — and only set that property to `true` in staging/prod `application.yml` where `AWS_INBOUND_EMAIL_QUEUE_URL` is defined.

#### 🟠 Major: T2.8 CDK Pattern Ambiguity — SNS vs S3 Event Notification (Issue 7)
- T2.8 first describes using `SES → SNS → SQS`, then mid-task switches to recommending `S3 event notifications → SQS directly`. The task ends with "RECOMMENDED: Use S3 event notifications" but the SES receipt rule code snippet still shows `sesActions.Sns({...})` rather than the S3 notification approach.
- **Result**: A developer following the task sequentially will first implement the SNS approach, then need to undo it — wasting time and potentially leaving conflicting code.
- **Recommendation**: Restructure T2.8 to present only the recommended approach (S3 event notifications). Remove the SNS sub-pattern or move it to a rejected-alternatives note.

#### 🟡 Minor: `DeregistrationService` Method Name Verification (Issue 8)
- T13 calls `deregistrationService.deregisterByEmail(email.senderEmail(), eventCode)` — but Story 10.12 (currently in progress or upcoming) must define exactly this method signature.
- T0 checks for `deregisterByEmail(String email, String eventCode)` — this is good, but if 10.12 uses a different signature (e.g., `deregisterByEmail(String email, String eventCode, String reason)`), story 10.17's router will fail to compile.
- **Recommendation**: Low risk given T0's explicit verification, but worth noting. T0 should also verify the exact method signature.

#### 🟡 Minor: PRD mentions `In-Reply-To`/`References` header extraction; story omits it (Issue 2, cross-reference)
- Already flagged in FR7. The omission is likely intentional (subject line extraction is simpler and sufficient). Should be documented in dev notes as an explicit scope decision rather than an accidental omission.

---

### Dependency Analysis

- **Within-Epic**: Story 10.17 is a leaf node — no other epic-10 story depends on it.
- **Cross-Story**: Correctly documents 10.12 + 10.7 as prerequisites with explicit verification tasks (T0).
- **Cross-Service**: `shared-kernel` modification (Reply-To header) affects all services that use `EmailService`. T6.5 rebuilds and publishes shared-kernel — correct approach.

### Best Practices Compliance Checklist

- [x] Story delivers clear user value
- [x] Story can function independently (prerequisites are backward, not forward)
- [x] Story sized appropriately (upper bound but acceptable)
- [x] No forward dependencies
- [x] No new DB schema (intentional — no Flyway migrations)
- [x] Acceptance criteria specific and testable
- [x] Traceability to FRs maintained
- [ ] ❌ AC1 does not specify `eu-west-1` cross-region deployment ← **BLOCKING**
- [ ] ❌ No explicit task to fix integration test startup failure after adding spring-cloud-aws ← **BLOCKING**
- [ ] ⚠️ T14.4 `@SqsListener` null SpEL approach will fail at runtime ← **MAJOR**
- [ ] ⚠️ T2.8 SNS vs S3 notification ambiguity ← **MAJOR**

---

## Summary and Recommendations

### Overall Readiness Status

**⚠️ NEEDS WORK — 2 blocking issues must be resolved before development starts**

Story 10.17 is well-conceived and architecturally sound. The story artifact is detailed and thorough, with solid TDD structure and good phase sequencing. However, two critical implementation gaps will cause the story to fail silently or break existing tests if not addressed first.

---

### Critical Issues Requiring Immediate Action

**🔴 BLOCKER 1 — Cross-Region Deployment Missing from AC1 and Tasks**
The `InboundEmailStack` must be deployed to `eu-west-1` (not `eu-central-1`). This is documented only in Dev Notes, not in any AC or CDK task. A developer following the tasks will deploy to the wrong region and the entire inbound email pipeline will silently not work.
- **Fix**: Update AC1 to specify `eu-west-1` region. Add an explicit CDK task (T2.x): *"Set `env: { region: 'eu-west-1' }` on `InboundEmailStack` in `batbern-infrastructure.ts`."*

**🔴 BLOCKER 2 — No Task to Prevent Integration Test Breakage After Adding spring-cloud-aws**
Adding `spring-cloud-aws-starter-sqs` triggers Spring auto-configuration of `SqsAsyncClient`. Without a no-op client in test context, ALL EMS integration tests will fail to start.
- **Fix**: Add explicit task after T5: *"T5.5 — Add `@MockitoBean SqsAsyncClient sqsAsyncClient;` to `AbstractIntegrationTest.java` (or equivalent). Verify all integration tests still pass: `./gradlew :services:event-management-service:test 2>&1 | tee /tmp/test-10-17-integration-check.log`."*

---

### Major Issues (Address Before Implementation)

**🟠 MAJOR 1 — `@SqsListener` with `#{null}` SpEL Won't Work at Runtime**
T14.4 suggests `@SqsListener("${aws.inbound-email.queue-url:#{null}}")` — Spring Cloud AWS 3.x does not support null queue identifiers and will throw on startup. The correct approach (already in T11) is `@ConditionalOnProperty` on the listener service bean. T14.4 must be updated to align with T11's conditional approach.

**🟠 MAJOR 2 — T2.8 Implementation Path Ambiguity**
Task T2.8 presents two conflicting approaches (SES→SNS→SQS vs S3 event notifications→SQS) and tells the developer to pick the recommended one midway through. This wastes implementation time and risks leaving unused SNS resources. Restructure T2.8 to present only the S3 event notification approach.

---

### Minor Issues (Can Address During Implementation)

- **MINOR 1**: AC1 says "FIFO queue" but tasks create a standard SQS queue. Clarify intent in AC1 (standard queue is sufficient for this workload).
- **MINOR 2**: `In-Reply-To`/`References` header extraction present in PRD scope but absent in story tasks. Add a dev note explicitly scoping this out.
- **MINOR 3**: PRD DoD specifies "WireMock for SES integration test" but story uses Mockito unit tests only. Either add an integration test or update the PRD DoD to match the story's test approach.
- **MINOR 4**: German email template copy should be reviewed by a native speaker before production.
- **MINOR 5**: T0's prerequisite check should also verify the exact method signature of `DeregistrationService.deregisterByEmail(String, String)`.

---

### Recommended Next Steps

1. **Fix BLOCKER 1**: Update AC1 and add CDK task for eu-west-1 region — update `_bmad-output/implementation-artifacts/10-17-email-reply-based-unsubscribe-deregistration.md`
2. **Fix BLOCKER 2**: Add T5.5 (AbstractIntegrationTest MockitoBean) to the story artifact tasks
3. **Fix MAJOR 1**: Remove null SpEL pattern from T14.4; align with T11's `@ConditionalOnProperty` approach
4. **Fix MAJOR 2**: Rewrite T2.8 to present only the S3 event notification approach; remove the conflicting SNS path
5. **Clarify Minor Issues 1–2** in story dev notes during implementation — not blocking

---

### Final Note

This assessment identified **6 issues** across **3 categories** (2 critical/blocking, 2 major, 2 minor). The story architecture is sound and the implementation plan is thorough. Address the 2 blockers and 2 major issues before development starts to avoid wasted effort and broken test suites.

**Assessment produced by:** Winston (Architect Agent)
**Date:** 2026-03-01
**Report file:** `docs/implementation-readiness-report-10-17-2026-03-01.md`
