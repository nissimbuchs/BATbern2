# Story 10.26: SES Email Forwarding & Role-Based Distribution Lists

Status: draft

<!-- Prerequisites: Story 10.17 (InboundEmailStack, SES receipt rules, InboundEmailRouter) MUST be complete -->

## Story

As an **organizer**,
I want `ok@batbern.ch`, `info@batbern.ch`, `events@batbern.ch`, `partner@batbern.ch`, `support@batbern.ch`, and `batbern{N}@batbern.ch` to automatically forward emails to the right recipients based on user roles and event registrations,
so that we have zero-cost distribution lists without needing any mailbox provider.

## Background

BATbern currently owns `batbern.ch` on Route 53 and has SES inbound email receiving configured for `replies@batbern.ch` (Story 10.17). The old domain `berner-architekten-treffen.ch` has email addresses that need migrating. Instead of paying for mailboxes, we use SES + Lambda to create serverless forwarding.

**Architecture:**
```
Inbound email → SES receipt rule (batbern.ch) → S3 → Lambda forwarder
                                                        ↓
                                              1. Sender authorization check
                                              2. Resolve recipients by address alias
                                              3. Re-send via SES to all recipients
```

**Existing APIs reused (no new endpoints needed):**
- `GET /api/v1/users?role={role}` — returns paginated users filtered by role (Company User Management Service). Used by /about page and partner contacts.
- `GET /api/v1/events/{eventCode}/registrations?status=registered&status=confirmed` — returns paginated registrations for an event (Event Management Service). Denormalized `attendeeEmail` field available on each registration.

## Acceptance Criteria

### Infrastructure

1. **AC1 — SES receipt rules for forwarding addresses**: The existing `InboundEmailStack` is extended with additional SES receipt rules for: `ok@batbern.ch`, `info@batbern.ch`, `events@batbern.ch`, `partner@batbern.ch`, `support@batbern.ch`, and a catch-all for the `batbern.ch` domain (for `batbern{N}@` dynamic addresses). All route to the same S3 bucket (existing `batbern-inbound-emails-{env}`) with a distinct key prefix (`forwarding/`). CDK synth passes.

2. **AC2 — Forwarder Lambda deploys cleanly**: A new Lambda function `batbern-email-forwarder-{env}` is created in the `InboundEmailStack`. It is triggered by S3 `OBJECT_CREATED` events under the `forwarding/` prefix. Runtime: Node.js 20.x. Memory: 256 MB. Timeout: 60s. Has SES SendEmail + SendRawEmail permissions. Has read access to the S3 inbound bucket. CDK unit tests verify all resources.

3. **AC3 — Lambda reads raw email from S3 and re-sends via SES**: The Lambda function fetches the raw MIME email from S3, parses headers (From, To, Subject, Message-ID), resolves recipients, and re-sends via SES `SendRawEmail`. The `From` header is rewritten to `{original-sender-name} via BATbern <noreply@batbern.ch>`. `Reply-To` is set to the original sender's address. `Subject` is preserved as-is. Body and all attachments are preserved. For large recipient lists (e.g., `batbern{N}@` with ~300 attendees), the Lambda sends emails sequentially with a 70ms delay between sends (matching the existing SES rate limit pattern from newsletter sending). At 300 recipients × 70ms = ~21s, this fits within the 60s Lambda timeout.

### Address Resolution (via existing APIs)

4. **AC4 — Role-based forwarding**: The Lambda resolves recipient addresses by calling existing service APIs (authenticated via API Gateway):
   - `ok@batbern.ch` → `GET /api/v1/users?role=ORGANIZER` → extract email from each active user
   - `info@batbern.ch` → same as `ok@` (alias)
   - `events@batbern.ch` → same as `ok@` (alias)
   - `partner@batbern.ch` → `GET /api/v1/users?role=PARTNER` → extract email from each active user
   - `support@batbern.ch` → configured support contact(s) stored in the database, managed via the Admin Page Settings tab

5. **AC5 — Dynamic event distribution list**: `batbern{N}@batbern.ch` (e.g., `batbern58@batbern.ch`) extracts the event number `N`, looks up the event code (e.g., `BATbern58`), then calls `GET /api/v1/events/BATbern{N}/registrations?status=registered&status=confirmed&limit=500` to get all confirmed registrants. The Lambda paginates through all pages if needed, collecting `attendeeEmail` from each registration. If no event matches, the email is silently discarded (logged at WARN). Sending uses the same 70ms-delay sequential pattern as newsletter sends — no async processing needed since ~300 recipients × 70ms ≈ 21s fits within the 60s Lambda timeout.

### Sender Authorization

6. **AC6 — Sender authorization per address**: The Lambda checks the sender's email against the list of organizer emails (from `GET /api/v1/users?role=ORGANIZER`). Authorization rules:
   - `ok@batbern.ch` → **organizers only**
   - `partner@batbern.ch` → **organizers only**
   - `batbern{N}@batbern.ch` → **organizers only**
   - `info@batbern.ch` → **anyone** (public contact address)
   - `events@batbern.ch` → **anyone** (public contact address, alias of info@)
   - `support@batbern.ch` → **anyone** (external contacts may write to support)

   If an unauthorized sender sends to a restricted address, the email is silently discarded and logged at WARN level.

7. **AC7 — Sender authorization uses caching**: The organizer email list is cached in Lambda memory for 5 minutes (simple TTL cache) to avoid calling the User Management API on every email. Cache is per-Lambda-instance (no external cache needed at this volume).

### Admin UI: Support Email Configuration

8. **AC8 — Support contacts in Admin Settings tab**: A new "Settings" tab is added to the existing Admin Page (`/admin`). The Settings tab contains an "Email Forwarding" section with a text field for configuring the `support@batbern.ch` forwarding recipients (comma-separated email addresses). The setting is stored in a new `app_settings` table (key-value) in the Event Management Service database. Changes take effect immediately (Lambda reads the setting via API on each `support@` email). The setting has a sensible default (empty → falls back to organizer list).

9. **AC9 — Admin Settings API endpoint**: `GET /api/v1/admin/settings/{key}` and `PUT /api/v1/admin/settings/{key}` endpoints on the Event Management Service. Requires `ORGANIZER` role. The Lambda calls `GET /api/v1/admin/settings/email-forwarding.support-contacts` to resolve support recipients.

### Operations

10. **AC10 — CloudWatch metrics and alarms**: Lambda publishes custom CloudWatch metrics: `EmailsForwarded`, `EmailsRejected` (unauthorized sender), `EmailsUnresolved` (no recipients found). An alarm fires when `EmailsRejected` exceeds 20 in 1 hour (possible abuse).

11. **AC11 — Logging**: All forwarding actions are logged with: timestamp, original sender (truncated to 5 chars + `***`), target alias address, number of resolved recipients, outcome (forwarded/rejected/unresolved). No PII beyond the truncated sender prefix.

### Testing

12. **AC12 — CDK unit tests**: `inbound-email-stack.test.ts` extended with tests for: new SES receipt rules, Lambda function resource, IAM permissions, S3 event notification for `forwarding/` prefix. All tests pass.

13. **AC13 — Lambda unit tests**: Jest tests for the forwarder Lambda covering: address resolution logic, sender authorization (organizer check), event distribution list resolution with pagination, email rewriting (From/Reply-To), error handling (API failures → graceful degradation), caching behavior, 70ms rate-limited sending. All tests pass.

14. **AC14 — Admin Settings integration tests**: Integration tests for the new `/api/v1/admin/settings/` endpoints. Tests verify CRUD operations and ORGANIZER-only access. All tests pass via Testcontainers PostgreSQL.

15. **AC15 — Frontend tests**: Vitest unit tests for the Admin Settings tab component. Playwright E2E test verifying the settings tab is visible and support contacts can be saved.

---

## Tasks / Subtasks

### Phase 1: Admin Settings Backend (TDD)

- [ ] **T1 — Database: app_settings table** (AC: #8, #9)
  - [ ] T1.1 — Create Flyway migration for `app_settings` table: `id` (UUID PK), `setting_key` (varchar unique), `setting_value` (text), `updated_at` (timestamp), `updated_by` (varchar)
  - [ ] T1.2 — Create `AppSetting` JPA entity
  - [ ] T1.3 — Create `AppSettingRepository` (Spring Data JPA)

- [ ] **T2 — Admin Settings API** (AC: #9, #14)
  - [ ] T2.1 — Write integration test FIRST (RED): `AdminSettingsControllerIntegrationTest` — test `GET /api/v1/admin/settings/email-forwarding.support-contacts` returns setting value
  - [ ] T2.2 — Test: `PUT /api/v1/admin/settings/email-forwarding.support-contacts` with body `{ "value": "a@b.ch,c@d.ch" }` → stores setting
  - [ ] T2.3 — Test: `GET` returns 200 with empty/default value when setting doesn't exist yet
  - [ ] T2.4 — Test: endpoints require ORGANIZER role (403 for non-organizers)
  - [ ] T2.5 — Implement `AdminSettingsController` with `GET` and `PUT` endpoints
  - [ ] T2.6 — Implement `AdminSettingsService` with get/set methods
  - [ ] T2.7 — Tests GREEN

### Phase 2: Admin Settings Frontend

- [ ] **T3 — Admin Page Settings Tab** (AC: #8, #15)
  - [ ] T3.1 — Add "Settings" tab to the Admin Page tab bar (after existing tabs)
  - [ ] T3.2 — Create `AdminSettingsTab` component with "Email Forwarding" section
  - [ ] T3.3 — Text field for `support@batbern.ch` forwarding recipients (comma-separated)
  - [ ] T3.4 — Save button calls `PUT /api/v1/admin/settings/email-forwarding.support-contacts`
  - [ ] T3.5 — Load current value on mount via `GET`
  - [ ] T3.6 — Success/error toast on save
  - [ ] T3.7 — Vitest unit test for the component
  - [ ] T3.8 — i18n keys for labels (admin namespace)

### Phase 3: CDK Infrastructure (TDD)

- [ ] **T4 — Extend InboundEmailStack with forwarding receipt rules** (AC: #1, #12)
  - [ ] T4.1 — Write CDK unit tests FIRST (RED): new receipt rules for `ok@`, `info@`, `events@`, `partner@`, `support@` as named recipients + catch-all `batbern.ch` domain rule for `batbern{N}@` addresses, all routing to S3 `forwarding/` prefix
  - [ ] T4.2 — Implement: add new `ReceiptRule` entries in `InboundEmailStack`. Named addresses as individual recipients in one rule; catch-all domain rule as a separate lower-priority rule.
  - [ ] T4.3 — Ensure receipt rule ordering: `replies@` rule first (existing), named forwarding addresses second, catch-all domain third
  - [ ] T4.4 — Tests GREEN

- [ ] **T5 — Create Forwarder Lambda resource in CDK** (AC: #2, #10, #12)
  - [ ] T5.1 — Write CDK unit tests FIRST (RED): Lambda function exists with correct runtime (Node.js 20.x), memory (256 MB), timeout (60s); IAM policy for SES SendRawEmail; S3 read permission
  - [ ] T5.2 — Test: S3 event notification for `forwarding/` prefix triggers Lambda (not SQS)
  - [ ] T5.3 — Test: CloudWatch alarm for `EmailsRejected` metric
  - [ ] T5.4 — Implement: Lambda construct, S3 notification, CloudWatch alarm
  - [ ] T5.5 — Wire Lambda with VPC access (needs to call internal service APIs via Service Connect DNS)
  - [ ] T5.6 — Pass environment variables: `API_GATEWAY_URL` (internal Service Connect URL), `SES_SENDER_ADDRESS` (`noreply@batbern.ch`)
  - [ ] T5.7 — Tests GREEN
  - [ ] T5.8 — `cdk synth` passes clean

### Phase 4: Lambda Forwarder Implementation (TDD)

- [ ] **T6 — Lambda handler: S3 event parsing** (AC: #3, #13)
  - [ ] T6.1 — Create `infrastructure/lambda/email-forwarder/` directory with `index.ts`, `package.json`
  - [ ] T6.2 — Write Jest unit test FIRST (RED): handler parses S3 event notification, extracts bucket/key
  - [ ] T6.3 — Test: fetches raw MIME email from S3
  - [ ] T6.4 — Test: parses From, To, Subject, Message-ID headers from raw email
  - [ ] T6.5 — Implement: S3 fetch + MIME header parsing (use `mailparser` npm package or raw header regex)
  - [ ] T6.6 — Tests GREEN

- [ ] **T7 — Lambda handler: address resolution** (AC: #4, #5, #13)
  - [ ] T7.1 — Write Jest tests FIRST (RED): `ok@batbern.ch` calls `GET /api/v1/users?role=ORGANIZER` and returns email list
  - [ ] T7.2 — Test: `info@batbern.ch` resolves same as `ok@`
  - [ ] T7.3 — Test: `events@batbern.ch` resolves same as `ok@`
  - [ ] T7.4 — Test: `partner@batbern.ch` calls `GET /api/v1/users?role=PARTNER`
  - [ ] T7.5 — Test: `support@batbern.ch` calls `GET /api/v1/admin/settings/email-forwarding.support-contacts` and parses comma-separated emails; fallback to organizer list when empty
  - [ ] T7.6 — Test: `batbern58@batbern.ch` extracts `58`, calls `GET /api/v1/events/BATbern58/registrations?status=registered&status=confirmed&limit=500`, returns `attendeeEmail` list
  - [ ] T7.7 — Test: pagination — when first page indicates more results, fetches subsequent pages
  - [ ] T7.8 — Test: `batbern999@batbern.ch` with non-existent event (404) → empty recipients → log WARN
  - [ ] T7.9 — Test: unknown address (e.g., `random@batbern.ch`) → empty recipients → log WARN
  - [ ] T7.10 — Implement: address resolution module calling existing APIs via API Gateway
  - [ ] T7.11 — Tests GREEN

- [ ] **T8 — Lambda handler: sender authorization** (AC: #6, #7, #13)
  - [ ] T8.1 — Write Jest tests FIRST (RED): organizer sender to `ok@` → allowed
  - [ ] T8.2 — Test: non-organizer sender to `ok@` → rejected, logged
  - [ ] T8.3 — Test: non-organizer sender to `partner@` → rejected
  - [ ] T8.4 — Test: non-organizer sender to `batbern58@` → rejected
  - [ ] T8.5 — Test: any sender to `info@` → allowed (public contact)
  - [ ] T8.6 — Test: any sender to `events@` → allowed (public contact)
  - [ ] T8.7 — Test: any sender to `support@` → allowed
  - [ ] T8.8 — Test: organizer list is cached for 5 minutes (second call within TTL does not hit API)
  - [ ] T8.9 — Implement: sender authorization with TTL cache
  - [ ] T8.10 — Tests GREEN

- [ ] **T9 — Lambda handler: email rewriting and forwarding** (AC: #3, #11, #13)
  - [ ] T9.1 — Write Jest tests FIRST (RED): From rewritten to `{name} via BATbern <noreply@batbern.ch>`
  - [ ] T9.2 — Test: Reply-To set to original sender address
  - [ ] T9.3 — Test: Subject preserved as-is
  - [ ] T9.4 — Test: SES `SendRawEmail` called with modified headers + original body/attachments
  - [ ] T9.5 — Test: sequential sending with 70ms delay for large recipient lists
  - [ ] T9.6 — Test: CloudWatch metric `EmailsForwarded` published on success
  - [ ] T9.7 — Test: CloudWatch metric `EmailsRejected` published on authorization failure
  - [ ] T9.8 — Test: CloudWatch metric `EmailsUnresolved` published when no recipients resolved
  - [ ] T9.9 — Implement: MIME rewriting + rate-limited SES send + CloudWatch metrics
  - [ ] T9.10 — Tests GREEN

### Phase 5: Integration & Wiring

- [ ] **T10 — Wire forwarder Lambda into bin/batbern-infrastructure.ts** (AC: #1, #2)
  - [ ] T10.1 — Ensure Lambda has VPC access to call services via Service Connect DNS
  - [ ] T10.2 — Grant Lambda access to API Gateway internal URL (or direct service URLs)
  - [ ] T10.3 — Lambda authenticates to APIs using IAM-based or service-account JWT (determine best approach during implementation)
  - [ ] T10.4 — `cdk synth` passes clean

- [ ] **T11 — End-to-end smoke test documentation** (AC: #11)
  - [ ] T11.1 — Document manual E2E test procedure: send email to `ok@batbern.ch` from organizer → verify delivery to all organizers
  - [ ] T11.2 — Document: send email to `batbernXX@batbern.ch` → verify delivery to event registrants
  - [ ] T11.3 — Document: send email from non-organizer to `ok@` → verify rejection (no delivery)
  - [ ] T11.4 — Document: send email from non-organizer to `partner@` → verify rejection
  - [ ] T11.5 — Document: send email from non-organizer to `info@` or `events@` → verify delivery (public)

### Phase 6: MX Record Configuration

- [ ] **T12 — Route 53 MX record for batbern.ch** (AC: #1)
  - [ ] T12.1 — Add MX record for `batbern.ch` pointing to `inbound-smtp.eu-central-1.amazonaws.com` (priority 10)
  - [ ] T12.2 — This can be done in CDK (DnsStack or InboundEmailStack) or manually via Route 53 console
  - [ ] T12.3 — Verify: `dig MX batbern.ch` returns the SES inbound SMTP endpoint
  - [ ] T12.4 — Note: existing `replies@batbern.ch` receipt rule continues to work — the new forwarding rules are additive

---

## Technical Notes

### SES Receipt Rule Ordering
SES evaluates receipt rules in order within a rule set. The `replies@batbern.ch` rule (Story 10.17) should remain first. The named forwarding addresses (`ok@`, `info@`, `partner@`, `support@`) are added as a second rule. The catch-all domain rule for `batbern.ch` (catches `batbern{N}@` addresses) must be last to avoid intercepting known addresses.

### Reusing Existing APIs (No New Endpoints)
The Lambda calls existing authenticated APIs through the API Gateway:
- **Users by role**: `GET /api/v1/users?role=ORGANIZER` (paginated, existing `UserController.listUsers()`)
- **Event registrations**: `GET /api/v1/events/{eventCode}/registrations?status=registered&status=confirmed&limit=500` (paginated, existing `EventController.listRegistrations()`)
- **Admin settings**: `GET /api/v1/admin/settings/{key}` (new, but simple CRUD — the only new API in this story)

### Lambda Authentication to APIs
The Lambda needs to authenticate to the API Gateway to call existing endpoints. Options:
1. **VPC Lambda + Service Connect DNS + service-account JWT**: Lambda in VPC calls services directly via Service Connect names. Uses a pre-generated service-account JWT stored in Secrets Manager.
2. **API Gateway with IAM auth**: Lambda calls the public API Gateway with IAM-based authentication (SigV4). API Gateway validates IAM role.

Recommend option 1 (VPC Lambda + Service Connect) since it matches the existing architecture.

### Rate-Limited Sending for Event Distribution
For `batbern{N}@` addresses targeting ~300 event participants:
- Sequential sending with 70ms delay between SES `SendRawEmail` calls
- 300 × 70ms = ~21 seconds — well within the 60s Lambda timeout
- Matches the existing newsletter sending pattern (`NewsletterEmailService`, `SEND_PAGE_SIZE=50`, `rate-delay-ms=70`)
- No async processing, no SQS, no database tracking needed at this volume

### Email Volume Estimate
- `ok@batbern.ch`: ~5-10 emails/month
- `batbern{N}@batbern.ch`: ~2-5 emails/event (organizer announcements to ~300 attendees each)
- `partner@batbern.ch`: ~5-10 emails/month
- `support@batbern.ch`: ~10-20 emails/month
- Total inbound: well under SES free tier (1,000 received/month free)
- Total outbound (forwarded copies): ~1,500-2,000/month → $0.15-0.20

### Cost Estimate
- SES receiving: free (< 1,000/month)
- Lambda: free tier (< 1M invocations, < 400K GB-seconds)
- SES sending (forwarded copies): ~$0.20/month
- S3 storage: pennies (7-day lifecycle)
- **Total: ~$0.20/month**

### Limitations
- **From rewriting**: SES requires verified sender. Forwarded emails will show `noreply@batbern.ch` as From with original sender in Reply-To. Recipients see "via BATbern" — standard for forwarding services.
- **SPF/DKIM**: Forwarded emails are re-sent from SES (not relayed), so BATbern's own SPF/DKIM apply. No alignment issues with the original sender's domain.
- **Bounce handling**: SES handles bounces. If a recipient address bounces, SES suppression list will suppress future sends. Monitor via SES reputation dashboard.
- **Large events**: If events ever exceed ~700 attendees, the 60s Lambda timeout may be insufficient. At that point, consider chunking into multiple Lambda invocations or switching to the async SQS-based pattern. Not needed for current ~300 attendee volume.

---

## Dev Agent Record

_To be filled during implementation._

## File List

_To be filled during implementation._
