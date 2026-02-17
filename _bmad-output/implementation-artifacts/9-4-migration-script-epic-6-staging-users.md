# Story 9.4: Migration Script for Epic 6 Staging Users

Status: done

## Story

As a **system administrator**,
I want to migrate existing Epic 6 token-based speakers to the new Cognito-backed authentication,
so that all staging speakers have JWT-capable accounts before Epic 9 is deployed to production.

## Acceptance Criteria

1. **AC1 — Target Identification:** Migration service identifies all `SpeakerPool` records with `status = ACCEPTED` across all events in the database.
2. **AC2 — Cognito Provisioning:** For each ACCEPTED speaker, calls `userApiClient.provisionSpeakerAccount()` (idempotent — Story 9.2): creates new Cognito account (NEW) or extends existing account with SPEAKER role (EXTENDED).
3. **AC3 — Fresh Invitation Email:** After successful provisioning, generates new VIEW + RESPOND tokens via `MagicLinkService.generateToken()` and sends invitation email via `SpeakerInvitationEmailService.sendInvitationEmail()` with new JWT magic link + credentials (temporaryPassword from provision response, only for NEW accounts).
4. **AC4 — Grace Period:** Old tokens in `speaker_invitation_tokens` are NOT explicitly invalidated. They expire naturally at their existing `expires_at` timestamp (up to 30 days), providing a grace period where both old and new tokens work.
5. **AC5 — Dry-Run Mode:** Migration endpoint/service supports `dryRun=true` mode that validates all speakers and logs what would be done WITHOUT creating Cognito accounts or sending emails. Returns the same report shape.
6. **AC6 — Migration Report:** Every run returns a structured `MigrationReport` containing per-speaker outcome: `PROVISIONED_NEW`, `EXTENDED`, `EMAIL_SENT`, `EMAIL_FAILED`, `SKIPPED` (no event found), `ERROR`. Aggregate counts: total processed, new accounts, extended accounts, emails sent, errors.
7. **AC7 — Idempotency:** Running migration multiple times is safe. `provisionSpeakerAccount()` is already idempotent (Story 9.2). The service does NOT resend emails if Cognito account already exists (use provision response `action` field: only send email when `action == NEW`).
8. **AC8 — Admin Endpoint:** `POST /api/v1/admin/migrations/epic9?dryRun=false` — Organizer-authenticated endpoint to trigger migration. Returns `MigrationReport` as JSON.
9. **AC9 — Shell Script:** `scripts/migration/epic9-migration.sh` wraps the API call with environment config (staging URL, auth token). Supports `--dry-run` flag.
10. **AC10 — Integration Tests:** Migration service tested with Testcontainers PostgreSQL, mocked `UserApiClient` and `SpeakerInvitationEmailService`.

## Tasks / Subtasks

- [x] Task 1: Create `Epic9MigrationService.java` in event-management-service (AC: 1, 2, 3, 4, 5, 6, 7)
  - [x] 1.1 Create `MigrationReport.java` record: `{ int total, int provisionedNew, int extended, int emailsSent, int errors, List<SpeakerMigrationResult> results }` — package `ch.batbern.events.dto`
  - [x] 1.2 Create `SpeakerMigrationResult.java` record: `{ UUID speakerPoolId, String email, String speakerName, MigrationOutcome outcome, String detail }` where `MigrationOutcome` is an enum: `PROVISIONED_NEW`, `EXTENDED`, `EMAIL_SENT`, `EMAIL_FAILED`, `SKIPPED`, `ERROR` — package `ch.batbern.events.dto`
  - [x] 1.3 Create `Epic9MigrationService.java` — package `ch.batbern.events.service`:
    - Query `speakerPoolRepository.findByStatus(SpeakerWorkflowState.ACCEPTED)` for all ACCEPTED speakers
    - For each speaker (wrapped in try-catch for error isolation):
      - Look up event via `eventRepository.findById(speakerPool.getEventId())` → if absent, record `SKIPPED`
      - If `dryRun=true`: log intent, record outcome without calling external services
      - Call `userApiClient.provisionSpeakerAccount(request)` → get `SpeakerProvisionResponse`
      - If `action == NEW`: generate tokens + send email → record `PROVISIONED_NEW` then `EMAIL_SENT`/`EMAIL_FAILED`
      - If `action == EXTENDED`: record `EXTENDED` (no email resent — AC7 idempotency)
    - Return `MigrationReport` aggregate
  - [x] 1.4 **CRITICAL — name splitting**: `SpeakerPool.speakerName` is a single String field. Split it for `SpeakerProvisionRequest`: `firstName = name.contains(" ") ? name.substring(0, name.indexOf(" ")) : name`, `lastName = name.contains(" ") ? name.substring(name.indexOf(" ")+1) : ""`. Handle null/blank defensively.
  - [x] 1.5 Token generation: `String respondToken = magicLinkService.generateToken(speakerPool.getId(), TokenAction.RESPOND)` and `String dashboardToken = magicLinkService.generateToken(speakerPool.getId(), TokenAction.VIEW)`
  - [x] 1.6 Email sending: `speakerInvitationEmailService.sendInvitationEmail(speakerPool, event, respondToken, dashboardToken, Locale.GERMAN)` — wrap in try-catch to distinguish EMAIL_FAILED vs ERROR

- [x] Task 2: Add `findByStatus()` to `SpeakerPoolRepository` (AC: 1)
  - [x] 2.1 Add `List<SpeakerPool> findByStatus(SpeakerWorkflowState status)` to `SpeakerPoolRepository.java`

- [x] Task 3: Create `Epic9MigrationController.java` (AC: 8)
  - [x] 3.1 Create controller at `POST /api/v1/admin/migrations/epic9` — package `ch.batbern.events.controller`
  - [x] 3.2 Request param: `@RequestParam(defaultValue = "false") boolean dryRun`
  - [x] 3.3 Delegates to `Epic9MigrationService.migrate(dryRun)`, returns `ResponseEntity<MigrationReport>`
  - [x] 3.4 Secured: endpoint must be behind organizer JWT auth (NOT `permitAll`) — do NOT add to SecurityConfig's public paths. Check existing SecurityConfig for organizer-protected path patterns.
  - [x] 3.5 Log migration start/end with dry-run flag and aggregate counts at INFO level

- [x] Task 4: Create `scripts/migration/epic9-migration.sh` (AC: 9)
  - [x] 4.1 Bash script accepting `--dry-run` flag
  - [x] 4.2 Reads `STAGING_API_URL` and `STAGING_AUTH_TOKEN` from env (or prompts if absent)
  - [x] 4.3 Calls `POST $STAGING_API_URL/api/v1/admin/migrations/epic9?dryRun=$DRY_RUN` with `Authorization: Bearer $STAGING_AUTH_TOKEN` header
  - [x] 4.4 Pretty-prints the JSON report using `jq` (already available in scripts per `CLAUDE.md`)
  - [x] 4.5 Exits with code 0 on success, 1 on any error
  - [x] 4.6 Includes usage help: `Usage: ./epic9-migration.sh [--dry-run]`

- [x] Task 5: Integration tests for `Epic9MigrationService` (AC: 10)
  - [x] 5.1 Create `Epic9MigrationServiceIntegrationTest.java` extending `AbstractIntegrationTest`, `@Import(TestUserApiClientConfig.class)`
  - [x] 5.2 Add `@MockitoBean SpeakerInvitationEmailService` (same pattern as `SpeakerMagicLinkRequestControllerIntegrationTest`)
  - [x] 5.3 Test: `migrate(false)` with no ACCEPTED speakers → report has total=0, all zeros
  - [x] 5.4 Test: `migrate(false)` with one NEW speaker (mock `provisionSpeakerAccount` returns `action=NEW`) → EMAIL_SENT outcome (provisionedNew=1, emailsSent=1)
  - [x] 5.5 Test: `migrate(false)` with one EXTENDED speaker (mock returns `action=EXTENDED`) → `EXTENDED`, no email sent
  - [x] 5.6 Test: `migrate(true)` (dry-run) → returns report but `provisionSpeakerAccount` NOT called (verify with `Mockito.verify(userApiClient, never())`)
  - [x] 5.7 Test: email failure → `EMAIL_FAILED` in report but migration continues (error isolation)
  - [x] 5.8 Test: idempotency — two speakers, second fails provisioning → first success recorded, second `ERROR`, migration completes
  - [x] 5.9 Create `Epic9MigrationControllerIntegrationTest.java` — tests `POST /api/v1/admin/migrations/epic9`: requires organizer JWT header → test with and without auth token

## Dev Notes

### CRITICAL — Implementation Architecture

**This story is LAST in the implementation sequence** (after Stories 9.1, 9.2, 9.3, 9.5). The migration is a one-time operation run on staging before production deployment. Design for correctness and observability over performance.

### Key Service Dependencies

All required services/repositories already exist:

| Dependency | Location | Story Origin |
|---|---|---|
| `SpeakerPoolRepository` | `repository/SpeakerPoolRepository.java` | Epic 6 |
| `EventRepository` | `repository/EventRepository.java` | Epic 2 |
| `MagicLinkService` | `service/MagicLinkService.java` | Story 6.1a |
| `SpeakerInvitationEmailService` | `service/SpeakerInvitationEmailService.java` | Story 6.1b |
| `UserApiClient.provisionSpeakerAccount()` | `client/UserApiClient.java` | Story 9.2 |
| `SpeakerInvitationTokenRepository` | `repository/SpeakerInvitationTokenRepository.java` | Story 6.1a |

### SpeakerProvisionRequest Construction

```java
// SpeakerPool.speakerName is a single String — must split for DTO
String name = speakerPool.getSpeakerName();
String firstName;
String lastName;
if (name != null && name.contains(" ")) {
    int idx = name.indexOf(' ');
    firstName = name.substring(0, idx);
    lastName = name.substring(idx + 1);
} else {
    firstName = name != null ? name : "";
    lastName = "";
}
SpeakerProvisionRequest request = new SpeakerProvisionRequest(
    speakerPool.getEmail(), firstName, lastName
);
```

### sendInvitationEmail Signature

```java
// From SpeakerInvitationEmailService.sendInvitationEmail():
speakerInvitationEmailService.sendInvitationEmail(
    speakerPool,          // SpeakerPool entity
    event,                // Event entity
    respondToken,         // String — RESPOND token (single-use)
    dashboardToken,       // String — VIEW token (reusable)
    Locale.GERMAN         // Always GERMAN per project standard
);
```

### Idempotency Logic

```java
SpeakerProvisionResponse provision = userApiClient.provisionSpeakerAccount(request);
if (provision.getAction() == SpeakerProvisionResponse.Action.NEW) {
    // New Cognito account — send fresh invite with credentials
    String respondToken = magicLinkService.generateToken(speakerPool.getId(), TokenAction.RESPOND);
    String dashboardToken = magicLinkService.generateToken(speakerPool.getId(), TokenAction.VIEW);
    try {
        speakerInvitationEmailService.sendInvitationEmail(speakerPool, event, respondToken, dashboardToken, Locale.GERMAN);
        results.add(new SpeakerMigrationResult(speakerPool.getId(), speakerPool.getEmail(), speakerPool.getSpeakerName(), MigrationOutcome.EMAIL_SENT, "Cognito account created, invitation sent"));
    } catch (Exception e) {
        LOG.error("Failed to send migration email for speakerPool {}: {}", speakerPool.getId(), e.getMessage());
        results.add(new SpeakerMigrationResult(speakerPool.getId(), speakerPool.getEmail(), speakerPool.getSpeakerName(), MigrationOutcome.EMAIL_FAILED, e.getMessage()));
    }
} else {
    // EXTENDED — Cognito account already exists, don't spam with email
    results.add(new SpeakerMigrationResult(..., MigrationOutcome.EXTENDED, "Cognito account extended, no email sent"));
}
```

### Grace Period — No Explicit Token Invalidation

**Do NOT invalidate old `speaker_invitation_tokens` entries.** They expire naturally via their `expires_at` field. The migration just issues new tokens; old tokens remain valid until expiry (up to 30 days), providing the grace period automatically.

### SecurityConfig — Admin Endpoint

The migration endpoint is at `/api/v1/admin/migrations/epic9`. Check `services/event-management-service/src/main/java/ch/batbern/events/config/SecurityConfig.java` for how existing admin/organizer endpoints are secured. Do NOT add to `permitAll()` list — this endpoint requires a valid Cognito JWT with organizer role.

### SpeakerProvisionResponse.Action Enum

From Story 9.2 — `SpeakerProvisionResponse.java`:
```java
public enum Action { NEW, EXTENDED }
```
Access via `response.getAction()` (it's a `@Data` class).

### SpeakerPool Entity Fields Available

```java
speakerPool.getId()           // UUID
speakerPool.getEventId()      // UUID → use to load Event
speakerPool.getSpeakerName()  // String (single name field — must split)
speakerPool.getEmail()        // String (added in V44 migration)
speakerPool.getStatus()       // SpeakerWorkflowState
speakerPool.getCompany()      // String
```

### MagicLinkService Token Generation

```java
// Correct method — from MagicLinkService.java (Story 6.1a):
String respondToken = magicLinkService.generateToken(speakerPool.getId(), TokenAction.RESPOND);
String dashboardToken = magicLinkService.generateToken(speakerPool.getId(), TokenAction.VIEW);
// Returns base64url-encoded 32-byte random token (plaintext)
// Token hash stored in speaker_invitation_tokens table by the service
```

### Integration Test Pattern

Follow the exact pattern from `SpeakerPasswordResetControllerIntegrationTest.java` (Story 9.3):

```java
@Import(TestUserApiClientConfig.class)
class Epic9MigrationServiceIntegrationTest extends AbstractIntegrationTest {
    @Autowired Epic9MigrationService migrationService;
    @Autowired UserApiClient userApiClient;
    @MockitoBean SpeakerInvitationEmailService speakerInvitationEmailService;

    @BeforeEach
    void setUp() {
        reset(userApiClient);
        // set up default mock behaviors
    }
}
```

For `provisionSpeakerAccount()` which returns a value (not void), use standard `when().thenReturn()` pattern.

### Shell Script Dependencies

`jq` is available on the target machines (mentioned in `CLAUDE.md` prerequisites). Use `./scripts/auth/get-token.sh` as a reference for how staging auth tokens are obtained.

### Project Structure Notes

**New files — event-management-service:**
- `src/main/java/ch/batbern/events/dto/MigrationReport.java`
- `src/main/java/ch/batbern/events/dto/SpeakerMigrationResult.java`
- `src/main/java/ch/batbern/events/service/Epic9MigrationService.java`
- `src/main/java/ch/batbern/events/controller/Epic9MigrationController.java`
- `src/test/java/ch/batbern/events/service/Epic9MigrationServiceIntegrationTest.java`
- `src/test/java/ch/batbern/events/controller/Epic9MigrationControllerIntegrationTest.java`

**Modified files — event-management-service:**
- `src/main/java/ch/batbern/events/repository/SpeakerPoolRepository.java` (add `findByStatus()`)

**New files — scripts:**
- `scripts/migration/epic9-migration.sh`

**No Flyway migrations needed** — no new DB tables. Old `speaker_invitation_tokens` entries stay as-is (grace period via natural expiry).

**No SecurityConfig changes needed** — admin endpoint uses existing JWT validation, not a public endpoint.

### References

- [Source: docs/prd/epic-9-speaker-authentication.md#Story-9.4] — Acceptance criteria source
- [Source: _bmad-output/implementation-artifacts/9-3-dual-authentication-support.md] — SpeakerInvitationEmailService.sendInvitationEmail() signature, integration test patterns, TestUserApiClientConfig
- [Source: _bmad-output/implementation-artifacts/9-2-automatic-account-creation-role-extension.md] — provisionSpeakerAccount() flow, SpeakerProvisionResponse.Action enum
- [Source: services/event-management-service/src/main/java/ch/batbern/events/service/MagicLinkService.java] — generateToken() method signature
- [Source: services/event-management-service/src/main/java/ch/batbern/events/domain/SpeakerInvitationToken.java] — Token model (SHA-256 hash, expires_at, actions)
- [Source: services/event-management-service/src/main/java/ch/batbern/events/client/UserApiClient.java] — provisionSpeakerAccount() interface
- [Source: services/event-management-service/src/test/java/ch/batbern/events/controller/SpeakerMagicLinkRequestControllerIntegrationTest.java] — @MockitoBean SpeakerInvitationEmailService pattern
- [Source: CLAUDE.md] — TDD mandatory, AbstractIntegrationTest (never H2), test naming convention

## Dev Agent Record

### Agent Model Used

Claude Sonnet 4.5 (create-story + dev-story workflows)

### Debug Log References

- `/tmp/epic9-migration-tests.out` — first test run (9/10 pass; 1 fail: 401 vs 403 for unauthenticated call)
- `/tmp/epic9-tests-2.out` — second test run after fix (10/10 pass, BUILD SUCCESSFUL)

### Completion Notes List

1. **All 12 tests pass (BUILD SUCCESSFUL)** — Epic9MigrationServiceIntegrationTest (9 tests) + Epic9MigrationControllerIntegrationTest (4 tests), after code review fixes (2 new name-split tests added)
2. **SpeakerProvisionResponse.AccountAction** — the actual enum name is `AccountAction` (not `Action` as the story notes say). The field is accessed via `response.getAction()`.
3. **TestSecurityConfig behaviour** — unauthenticated requests return 403 (not 401) because TestSecurityConfig uses `permitAll()` at the HTTP level; `@PreAuthorize` is enforced at the method level. Controller test was updated accordingly.
4. **EMAIL_SENT counting** — when action=NEW, the outcome is EMAIL_SENT (not a separate PROVISIONED_NEW + EMAIL_SENT pair). The aggregate counter increments both `provisionedNew` and `emailsSent` for EMAIL_SENT outcomes.
5. **Grace period implemented by omission** — no code explicitly invalidates old tokens; they expire naturally via their `expires_at` timestamp per AC4.
6. **No SecurityConfig changes** — the admin endpoint `/api/v1/admin/migrations/epic9` is NOT added to permitAll(); it falls through to `anyRequest().authenticated()` and is additionally protected by `@PreAuthorize("hasRole('ORGANIZER')")`.

### Code Review Fixes (post-review, 2026-02-17)

Adversarial code review found 1 HIGH + 3 MEDIUM issues — all fixed:

- **H1 (fixed)**: `EMAIL_FAILED` was unreachable in production because `sendInvitationEmail()` is `@Async` (dispatches fire-and-forget) AND internally swallows all exceptions. Fix: added `sendInvitationEmailSync()` to `SpeakerInvitationEmailService` — synchronous, no internal try-catch, propagates exceptions. `Epic9MigrationService` now calls `sendInvitationEmailSync()` so the `EMAIL_FAILED` outcome is reachable.
- **M1 (fixed)**: AC1.4 name-splitting logic was untested. Added 2 `ArgumentCaptor<SpeakerProvisionRequest>` tests: (a) "Hans Muster" → firstName="Hans", lastName="Muster"; (b) single-word "Cher" → firstName="Cher", lastName="".
- **M2 (fixed)**: `eventNumber` in test `@BeforeEach` used `Math.random() * 99` — only 99 distinct values, collision risk. Replaced with `Math.abs(UUID.randomUUID().hashCode())` for effectively unique values.
- **M3 (fixed)**: `@Transactional` on `Epic9MigrationControllerIntegrationTest` was misleading — MockMvc HTTP dispatch runs in a separate transaction. Removed the annotation.

### File List

**New files — event-management-service:**
- `src/main/java/ch/batbern/events/dto/MigrationReport.java`
- `src/main/java/ch/batbern/events/dto/SpeakerMigrationResult.java` (includes `MigrationOutcome` enum)
- `src/main/java/ch/batbern/events/service/Epic9MigrationService.java`
- `src/main/java/ch/batbern/events/controller/Epic9MigrationController.java`
- `src/test/java/ch/batbern/events/service/Epic9MigrationServiceIntegrationTest.java`
- `src/test/java/ch/batbern/events/controller/Epic9MigrationControllerIntegrationTest.java`

**Modified files — event-management-service:**
- `src/main/java/ch/batbern/events/repository/SpeakerPoolRepository.java` (added `findByStatus()` method)
- `src/main/java/ch/batbern/events/service/SpeakerInvitationEmailService.java` (added `sendInvitationEmailSync()` — sync throwable overload, H1 fix)

**New files — scripts:**
- `scripts/migration/epic9-migration.sh` (executable)
