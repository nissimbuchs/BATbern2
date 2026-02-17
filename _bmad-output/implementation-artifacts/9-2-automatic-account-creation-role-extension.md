# Story 9.2: Automatic Account Creation & Role Extension on Invitation Acceptance

Status: ready-for-dev

## Story

As a **system**,
I want to automatically create or update Cognito user accounts when speakers accept invitations,
so that speakers have unified access without duplicate accounts and can use both magic link and email/password login.

## Acceptance Criteria

1. **AC1 — New User Creation:** When speaker email does NOT exist in Cognito → auto-create new Cognito user with SPEAKER role assigned via `custom:roles` attribute; system generates a high-entropy temporary password (20+ chars, Cognito-compliant) and sends it alongside the existing magic link in the invitation email
2. **AC2 — Existing User Role Extension:** When speaker email EXISTS in Cognito (e.g., attendee account) → add SPEAKER role to `custom:roles` attribute without creating duplicate; existing attendee sessions remain valid after SPEAKER role is added
3. **AC3 — Zero Duplicate Accounts:** Email uniqueness enforced at Cognito level; calling the account creation process twice (idempotency) must not create a second Cognito user or double-add a role
4. **AC4 — Audit Trail:** All account creations/role extensions logged in a `speaker_account_creation_audit` table with fields: `speaker_pool_id`, `email` (masked in logs), `cognito_user_id`, `action` (NEW | EXTENDED), `created_at`; domain event `SpeakerAccountCreatedEvent` published to Spring ApplicationEventPublisher
5. **AC5 — Invitation Email Contains Credentials:** Existing invitation email templates (DE + EN) updated to include temporary password block; magic link remains the primary CTA; password section only shown for NEW accounts (not for EXTENDED role accounts since they already have a password)
6. **AC6 — Existing Attendee Sessions Remain Valid:** Adding SPEAKER role to an existing Cognito user must NOT invalidate their current JWT; updated roles reflected on next login

## Tasks / Subtasks

- [ ] Task 1: Add `addRoleToUser` method to `UserService` (company-user-management-service) (AC: 2, 6)
  - [ ] 1.1 Add `addRoleToUser(String username, Role newRole)` to `UserService.java` — no-op if role already present
  - [ ] 1.2 Add `PUT /api/v1/users/{username}/roles` endpoint to `UserController.java` (internal calls only, requires ORGANIZER role or service-to-service auth)
  - [ ] 1.3 Write unit tests for `addRoleToUser()` — new role, duplicate role (no-op), unknown user
  - [ ] 1.4 Publish `UserRoleAddedEvent` domain event when role successfully added

- [ ] Task 2: Extend `CognitoIntegrationService` with role management methods (AC: 1, 2, 3) (company-user-management-service)
  - [ ] 2.1 Add `findUserByEmail(String email): Optional<AdminGetUserResponse>` — lookup by email attribute
  - [ ] 2.2 Add `createCognitoSpeaker(String email, String name, String temporaryPassword)` — calls `AdminCreateUser` with `custom:roles=SPEAKER`
  - [ ] 2.3 Add `addRoleToCognitoUser(String email, Role newRole)` — reads existing `custom:roles`, appends new role, calls `AdminUpdateUserAttributes`
  - [ ] 2.4 Write unit tests with `@MockBean` for all three methods — test success, duplicate email (idempotency), Cognito API timeout
  - [ ] 2.5 Implement exponential backoff retry (3 attempts: 1s / 2s / 4s) for all Cognito API calls

- [ ] Task 3: Add `UserApiClient` method for role management (AC: 2) (event-management-service)
  - [ ] 3.1 Add `addRoleToUser(String username, String role)` to `UserApiClient.java` — calls `PUT /api/v1/users/{username}/roles` on company-user-management-service
  - [ ] 3.2 Follow existing Feign client pattern (see `UserApiClient` usage in `SpeakerInvitationService` lines 56-57)

- [ ] Task 4: Create `SpeakerAccountCreationService` in event-management-service (AC: 1, 2, 3, 4)
  - [ ] 4.1 Create `SpeakerAccountCreationService.java` — orchestrates Cognito account create/extend + local DB role update + audit event publishing
  - [ ] 4.2 Public method: `processInvitationAcceptance(UUID speakerPoolId)` — called from `SpeakerInvitationService` on acceptance
  - [ ] 4.3 Logic: check if email exists in Cognito → branch NEW vs EXTENDED → call CognitoIntegrationService → call UserApiClient.addRoleToUser → persist audit → publish event
  - [ ] 4.4 Compensating transaction: if Cognito creation succeeds but local DB update fails → call Cognito `AdminDeleteUser` to rollback; log full error
  - [ ] 4.5 Create `SpeakerAccountCreatedEvent.java` domain event (fields: `speakerPoolId`, `email`, `cognitoUserId`, `accountAction` enum NEW|EXTENDED, `createdAt`)
  - [ ] 4.6 Write comprehensive unit tests for all branches (new user, existing user, compensating rollback, idempotency)

- [ ] Task 5: Flyway migration — audit table (AC: 4)
  - [ ] 5.1 Create `V{N}__add_speaker_account_creation_audit_table.sql` in event-management-service
  - [ ] 5.2 Table: `speaker_account_creation_audit` (`id` UUID PK, `speaker_pool_id` UUID NOT NULL, `email_hash` VARCHAR(64) NOT NULL (SHA-256), `cognito_user_id` VARCHAR(255), `action` VARCHAR(10) NOT NULL CHECK(action IN ('NEW', 'EXTENDED')), `created_at` TIMESTAMPTZ NOT NULL DEFAULT NOW())
  - [ ] 5.3 Add `SpeakerAccountCreationAudit` JPA entity and repository

- [ ] Task 6: Hook account creation into `SpeakerInvitationService.acceptInvitation()` (AC: 1, 2)
  - [ ] 6.1 Find the invitation acceptance endpoint in `SpeakerInvitationService` (look for `acceptInvitation()` or `processResponse()`)
  - [ ] 6.2 After invitation acceptance persisted (state change to ACCEPTED) → call `speakerAccountCreationService.processInvitationAcceptance(speakerPoolId)`
  - [ ] 6.3 Failure must NOT block the acceptance itself — wrap in try/catch, log error, allow acceptance to complete (eventual consistency for account creation)
  - [ ] 6.4 Write integration test for the full acceptance → account creation flow

- [ ] Task 7: Update invitation email templates to include temporary password (AC: 5)
  - [ ] 7.1 Extend `SpeakerInvitationEmailService` to accept `temporaryPassword` and `accountAction` (NEW|EXTENDED) in email context map
  - [ ] 7.2 Update `speaker-invitation-en.html` — add password section (conditional: only shown for `accountAction=NEW`)
  - [ ] 7.3 Update `speaker-invitation-de.html` — same as EN but German text
  - [ ] 7.4 Template text (EN): "Your BATbern account has been created. You can also login with: Email: {{email}} / Temporary Password: {{temporaryPassword}}"
  - [ ] 7.5 Write test asserting email contains password for new accounts, does NOT contain password for extended accounts

- [ ] Task 8: Tests — integration and E2E (AC: 1-6)
  - [ ] 8.1 Extend existing `SpeakerInvitationControllerTest` integration tests: invitation acceptance → account creation triggered → audit record persisted
  - [ ] 8.2 Add Bruno API test: `POST /api/v1/speaker-invitations/{eventCode}/respond` with acceptance → verify audit table populated
  - [ ] 8.3 Test scenario: existing attendee speaker (email match) → role extended, no duplicate in Cognito

## Dev Notes

### CRITICAL: Account Creation Timing

Account creation is triggered on **invitation acceptance** (when speaker clicks "Accept" in the response portal). This is intentional:
- Aligns with speaker's explicit consent to participate
- Avoids creating accounts for speakers who never respond
- Matches Epic 9 PRD specification: `SpeakerInvitationService.processAcceptance()`
- Failure to create Cognito account must NOT block the acceptance (eventual consistency)

### CRITICAL: Session Bridge Pattern (Story 9.1 Context)

Story 9.1 established a "session bridge" — JWT → opaque VIEW token. Story 9.2 does **NOT** change this:
- The `?token=xxx` query param pattern in all speaker portal API calls remains **UNTOUCHED**
- `SpeakerDashboardPage.tsx` is **NOT modified** in this story
- Full JWT session management is Story 9.4 responsibility
- Story 9.2 only adds Cognito account persistence; the login UX from Story 9.1 is unchanged

### CRITICAL: Cognito Attribute Key

Existing Cognito integration uses `custom:roles` (plural). Do NOT use `custom:role` (singular) — check the actual Cognito User Pool attribute configuration in `CognitoIntegrationServiceImpl` before writing any code. Using the wrong key will silently fail.

### CRITICAL: Cross-Service Transaction Handling

event-management-service → company-user-management-service is a **distributed call**. There is no 2PC. Use compensating transactions:

```
1. Create/find Cognito user (CognitoIntegrationServiceImpl)
2. Add SPEAKER role to Cognito user (CognitoIntegrationServiceImpl)
3. Add SPEAKER role to local User entity (via UserApiClient → UserService)
   → If step 3 fails: call Cognito AdminDeleteUser/AdminUpdateUserAttributes to undo step 2
4. Persist audit record (local DB, same @Transactional as step 3)
5. Publish SpeakerAccountCreatedEvent
```

### Do NOT Change in This Story

- `speaker_invitation_tokens` table and `?token=xxx` pattern — Story 9.4 handles migration
- `SpeakerDashboardPage.tsx` — Story 9.5 handles frontend navigation
- `SecurityConfig.java` for `/api/v1/speaker-portal/**` permitAll() — Story 9.4 handles JWT enforcement
- JWT generation/validation in `MagicLinkService` — Story 9.1's implementation, don't modify

### Temporary Password Generation

Use `java.security.SecureRandom` to generate a 20-char password meeting Cognito policy:
- Minimum requirements: uppercase, lowercase, number, special char
- Recommended: `SecureRandom` + character class pools (don't use Apache Commons RandomStringUtils as it's not cryptographically secure for passwords)
- Password is sent via email and must be changed by user on first traditional login
- Store: Never store the plain text password — send via email only

### Local Dev vs Staging Cognito

- **Local:** CognitoIntegrationService mock/stub acceptable; test with `cognitoSync=false` flag
- **Staging:** Real Cognito pool at `AWS_PROFILE=batbern-staging`; User Pool ID via `AWS_COGNITO_USER_POOL_ID` env var
- Check existing `application-local.yml` for local Cognito mock configuration before implementing

### Existing Pattern: UserApiClient (event-management-service)

```java
// SpeakerInvitationService.java lines 56-57
private final UserApiClient userApiClient;

GetOrCreateUserResponse userResponse = userApiClient.getOrCreateUser(userRequest);
```

Follow this exact pattern for `addRoleToUser()` — same client, same error handling, same Feign configuration.

### Existing Pattern: Domain Event Publishing

```java
// SpeakerInvitationService.java lines 250-258
SpeakerInvitationSentEvent sentEvent = new SpeakerInvitationSentEvent(...);
eventPublisher.publishEvent(sentEvent);
log.debug("Published SpeakerInvitationSentEvent for speakerPoolId: {}", speakerPoolId);
```

Use same `ApplicationEventPublisher` for `SpeakerAccountCreatedEvent`.

### Existing Pattern: Integration Test Setup

Tests extend `AbstractIntegrationTest` which provides Testcontainers PostgreSQL. Follow cleanup pattern:

```java
@BeforeEach
void setUp() {
    tokenRepository.deleteAll();
    speakerPoolRepository.deleteAll();
    eventRepository.deleteAll();
    speakerAccountCreationAuditRepository.deleteAll(); // ADD THIS
}
```

### Project Structure Notes

- All new Java classes in event-management-service follow package: `ch.batbern.events.service` (services), `ch.batbern.events.events` (domain events), `ch.batbern.events.entity` (JPA entities)
- All new Java classes in company-user-management-service follow package: `ch.batbern.companyuser.service` (services), `ch.batbern.companyuser.events` (domain events)
- Flyway migration files in: `services/event-management-service/src/main/resources/db/migration/`
- Email templates in: `services/event-management-service/src/main/resources/templates/` (or similar — check existing `SpeakerInvitationEmailService` for exact path)
- Bruno API tests in: `bruno-tests/speaker-portal/`

### Architecture References

- [Source: docs/architecture/] — Auth patterns, service communication, security config
- [Source: docs/guides/service-foundation-pattern.md] — Standard service layer structure
- [Source: docs/guides/microservices-http-clients.md] — Cross-service communication patterns
- [Source: _bmad-output/implementation-artifacts/9-1-jwt-magic-link-authentication.md] — Story 9.1 patterns (session bridge, JwtConfig, MagicLinkService)
- [Source: docs/prd/epic-9-speaker-authentication.md] — Full Epic 9 spec

## Dev Agent Record

### Agent Model Used

Claude Sonnet 4.5 (create-story workflow)

### Debug Log References

### Completion Notes List

### File List
