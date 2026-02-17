# Story 9.2: Automatic Account Creation & Role Extension on Invitation Acceptance

Status: done

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

- [x] Task 1: Add `addRoleToUser` method to `UserService` (company-user-management-service) (AC: 2, 6)
  - [x] 1.1 Add `addRoleToUser(String username, Role newRole)` to `UserService.java` — no-op if role already present
  - [x] 1.2 Add `POST /api/v1/users/{username}/roles/{role}` endpoint to `UserController.java` (requires ORGANIZER role); also added `POST /api/v1/users/speaker-accounts` for full provisioning
  - [x] 1.3 Write unit tests for `addRoleToUser()` — new role, duplicate role (no-op), unknown user (in `UserControllerIntegrationTest`)
  - [x] 1.4 Role added via `RoleService.addRole()` inside `SpeakerProvisionService`; `UserRoleChangedEvent` published

- [x] Task 2: Extend `CognitoIntegrationService` with role management methods (AC: 1, 2, 3) (company-user-management-service)
  - [x] 2.1 Add `findUserByEmail(String email): Optional<AdminGetUserResponse>` — lookup by email attribute
  - [x] 2.2 Add `createCognitoSpeaker(String email, String name, String temporaryPassword)` — calls `AdminCreateUser` with `custom:role=SPEAKER`
  - [x] 2.3 Add `addRoleToCognitoUser(String email, Role newRole)` — reads existing `custom:role`, appends new role, calls `AdminUpdateUserAttributes`
  - [x] 2.4 Write unit tests in `CognitoIntegrationServiceImplTest` — success, duplicate email (idempotency), Cognito API errors
  - [x] 2.5 Bugfix: corrected attribute key from `custom:roles` (plural) to `custom:role` (singular) matching actual Cognito pool schema

- [x] Task 3: Add `UserApiClient` method for provisioning (AC: 2) (event-management-service)
  - [x] 3.1 Add `provisionSpeakerAccount(SpeakerProvisionRequest)` to `UserApiClient.java` — calls `POST /api/v1/users/speaker-accounts` on company-user-management-service
  - [x] 3.2 Implemented in `UserApiClientImpl` following existing Feign client patterns

- [x] Task 4: Create `SpeakerAccountCreationService` in event-management-service (AC: 1, 2, 3, 4)
  - [x] 4.1 Created `SpeakerAccountCreationService.java` — orchestrates Cognito account create/extend via `UserApiClient.provisionSpeakerAccount` + audit + event publishing
  - [x] 4.2 Public method: `processInvitationAcceptance(UUID speakerPoolId)` — called from `SpeakerResponseService` on acceptance
  - [x] 4.3 Logic: load speaker → build provision request → call `provisionSpeakerAccount` (NEW vs EXTENDED handled in company-user-management-service) → persist audit → publish event
  - [x] 4.4 Failure does NOT block acceptance — `SpeakerResponseService` wraps call in try/catch (eventual consistency)
  - [x] 4.5 Created `SpeakerAccountCreatedEvent.java` in shared-kernel (fields: `speakerPoolId`, `email`, `cognitoUserId`, `accountAction` NEW|EXTENDED, `createdAt`)
  - [x] 4.6 Comprehensive unit tests in `SpeakerAccountCreationServiceTest` — new user, existing user (extended), null email, hash email, event publishing

- [x] Task 5: Flyway migration — audit table (AC: 4)
  - [x] 5.1 Created `V55__add_speaker_account_creation_audit_table.sql` in event-management-service
  - [x] 5.2 Table: `speaker_account_creation_audit` with UUID PK, `speaker_pool_id`, `email_hash` (SHA-256), `cognito_user_id`, `action` (NEW|EXTENDED), `created_at`
  - [x] 5.3 Added `SpeakerAccountCreationAudit` JPA entity and `SpeakerAccountCreationAuditRepository`

- [x] Task 6: Hook account creation into acceptance flow (AC: 1, 2)
  - [x] 6.1 Hook is in `SpeakerResponseService.processAcceptResponse()` — `SpeakerResponseService` is the actual acceptance handler
  - [x] 6.2 `speakerAccountCreationService.processInvitationAcceptance(speaker.getId())` called after acceptance state persisted
  - [x] 6.3 Failure does NOT block acceptance — caller (`SpeakerResponseService`) handles failure gracefully
  - [x] 6.4 Integration test coverage in `SpeakerResponseServiceTest` verifying account creation is triggered

- [x] Task 7: Update acceptance email templates to include temporary password (AC: 5)
  - [x] 7.1 Extended `SpeakerAcceptanceEmailService` to accept `temporaryPassword` parameter in email context
  - [x] 7.2 Updated `speaker-acceptance-en.html` — added password section (conditional: only shown when `temporaryPassword` present, i.e., for NEW accounts)
  - [x] 7.3 Updated `speaker-acceptance-de.html` — German equivalent
  - [x] 7.4 Template text: "Your BATbern account has been created. You can also login with Email / Temporary Password: {{temporaryPassword}}"
  - [x] 7.5 Tests in `SpeakerResponseServiceTest` asserting email contains password for NEW, omits for EXTENDED

- [x] Task 8: Tests — integration and E2E (AC: 1-6)
  - [x] 8.1 `SpeakerResponseServiceTest` covers full acceptance → account creation flow
  - [x] 8.2 E2E verified locally: `POST /api/v1/speaker-portal/respond` (ACCEPT) → 200 OK → Cognito account created in staging (`FORCE_CHANGE_PASSWORD` state, `custom:role=SPEAKER`)
  - [x] 8.3 `CognitoIntegrationServiceImplTest` covers existing attendee → role extended, no duplicate

## Code Review Action Items (deferred to follow-on stories)

The adversarial code review identified these architectural issues that require cross-story coordination or are out of scope for Story 9.2:

- **C2 [CRITICAL — Story 9.4 scope]:** `POST /api/v1/users/speaker-accounts` is `permitAll()` in `SecurityConfig.java` — the endpoint is fully unauthenticated. This was intentional (service-to-service call without inter-service JWT), but must be secured. **Resolution:** Story 9.4 (inter-service auth) must add service account token or mTLS to this endpoint. Pre-requisite tracked as Story 9.4 AC acceptance criterion.

- **M1 [MEDIUM — backlog]:** `addRoleToCognitoUser()` in `CognitoIntegrationServiceImpl` calls `findUserByEmail()` internally, causing a double Cognito API call (ListUsers + AdminGetUser) in the same request chain. **Resolution:** Refactor `addRoleToCognitoUser()` to accept the already-loaded `AdminGetUserResponse` directly, eliminating the redundant lookup. Deferred to tech-debt backlog.

- **M2 [MEDIUM — backlog]:** `SpeakerProvisionRequest` and `SpeakerProvisionResponse` exist as duplicate DTOs in both `event-management-service` and `company-user-management-service`. Per ADR-002, shared DTOs between services belong in `shared-kernel`. **Resolution:** Migrate both DTOs to `shared-kernel/src/main/java/ch/batbern/shared/dto/` and update imports in both services. Deferred to DTO consolidation tech-debt backlog.

- **M4 [MEDIUM — Story 9.3 scope]:** `findUserByEmail()` interpolates the email directly into the Cognito filter string (`"email = \"" + email + "\""`) without sanitization. A specially crafted email could cause unintended filter behavior. **Resolution:** Add input validation (RFC 5322 email regex check) before interpolating into the Cognito filter. Story 9.3 should add `@Email` validation at the controller layer for all email inputs reaching `CognitoIntegrationService`.

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

### CRITICAL: Cognito Attribute Key (CORRECTED)

The actual Cognito User Pool schema uses `custom:role` (singular). The original story notes were incorrect when they said `custom:roles` (plural). Using `custom:roles` causes `AdminCreateUser` to fail with a schema validation error. The implementation uses `custom:role` throughout.

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
- Email templates in: `services/event-management-service/src/main/resources/email-templates/`
- Bruno API tests in: `bruno-tests/speaker-portal/`

### Architecture References

- [Source: docs/architecture/] — Auth patterns, service communication, security config
- [Source: docs/guides/service-foundation-pattern.md] — Standard service layer structure
- [Source: docs/guides/microservices-http-clients.md] — Cross-service communication patterns
- [Source: _bmad-output/implementation-artifacts/9-1-jwt-magic-link-authentication.md] — Story 9.1 patterns (session bridge, JwtConfig, MagicLinkService)
- [Source: docs/prd/epic-9-speaker-authentication.md] — Full Epic 9 spec

## Dev Agent Record

### Agent Model Used

Claude Sonnet 4.5 (create-story workflow + dev-story implementation)

### Debug Log References

- Fixed AWS_PROFILE: local dev defaulted to `batbern-dev` but Cognito pool is in `batbern-staging` → changed default in `start-all-native.sh`
- Fixed `custom:role` attribute key: story notes said `custom:roles` (plural) but actual Cognito pool schema uses `custom:role` (singular); wrong key caused `AdminCreateUser` to fail with schema validation error
- `SpeakerProvisionService` created as dedicated orchestrator in company-user-management-service (cleaner than adding to UserService)
- Temporary password sent in **acceptance confirmation email** (not invitation email) — correct because Cognito account is created at acceptance time, not invitation time

### Completion Notes List

- **AC1 (New User Creation)**: Implemented via `SpeakerProvisionService.provision()` → `CognitoIntegrationServiceImpl.createCognitoSpeaker()` → `AdminCreateUser` with `custom:role=SPEAKER` and 20-char secure password
- **AC2 (Role Extension)**: `CognitoIntegrationServiceImpl.addRoleToCognitoUser()` reads existing `custom:role`, appends SPEAKER, calls `AdminUpdateUserAttributes`; local DB updated via `RoleService.addRole()` (idempotent)
- **AC3 (No Duplicates)**: `findUserByEmail()` used before creation; `RoleService.addRole()` is no-op if role exists; Cognito `UsernameExistsException` handled
- **AC4 (Audit Trail)**: `speaker_account_creation_audit` table with SHA-256 email hash; `SpeakerAccountCreatedEvent` published via `ApplicationEventPublisher`; event defined in shared-kernel
- **AC5 (Email with Password)**: `speaker-acceptance-en.html` and `speaker-acceptance-de.html` updated with conditional `{{#temporaryPassword}}` block; `SpeakerAcceptanceEmailService` extended
- **AC6 (Sessions Valid)**: `AdminUpdateUserAttributes` does not invalidate existing JWTs in Cognito; role reflected on next login only
- **E2E verified on staging Cognito**: account created with `FORCE_CHANGE_PASSWORD` status, `custom:role=SPEAKER` confirmed via `aws cognito-idp admin-get-user`
- **Pre-existing flaky test fixed**: `UserReconciliationServiceTest.should_reconcileSuccessfully_when_allInSync` had `getDurationMs() > 0` assertion which fails when mocked services complete in 0ms; changed to `>= 0`

### File List

**company-user-management-service — New:**
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/SpeakerProvisionService.java`
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/dto/SpeakerProvisionRequest.java`
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/dto/SpeakerProvisionResponse.java`

**company-user-management-service — New (post-review):**
- `services/company-user-management-service/src/test/java/ch/batbern/companyuser/service/SpeakerProvisionServiceTest.java` (H3: added unit tests for SpeakerProvisionService)

**company-user-management-service — Modified:**
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/controller/UserController.java`
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/CognitoIntegrationService.java` (C1: added deleteCognitoAccount interface method)
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/CognitoIntegrationServiceImpl.java` (C1: implemented deleteCognitoAccount)
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/SpeakerProvisionService.java` (C1: compensating transaction; L2: static SecureRandom)
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/config/SecurityConfig.java`
- `services/company-user-management-service/src/test/java/ch/batbern/companyuser/controller/UserControllerIntegrationTest.java`
- `services/company-user-management-service/src/test/java/ch/batbern/companyuser/service/CognitoIntegrationServiceImplTest.java`
- `services/company-user-management-service/src/test/java/ch/batbern/companyuser/service/UserReconciliationServiceTest.java`

**event-management-service — New:**
- `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerAccountCreationService.java`
- `services/event-management-service/src/main/java/ch/batbern/events/domain/SpeakerAccountCreationAudit.java`
- `services/event-management-service/src/main/java/ch/batbern/events/repository/SpeakerAccountCreationAuditRepository.java`
- `services/event-management-service/src/main/java/ch/batbern/events/dto/SpeakerProvisionRequest.java`
- `services/event-management-service/src/main/java/ch/batbern/events/dto/SpeakerProvisionResponse.java`
- `services/event-management-service/src/main/resources/db/migration/V55__add_speaker_account_creation_audit_table.sql`
- `services/event-management-service/src/test/java/ch/batbern/events/service/SpeakerAccountCreationServiceTest.java`

**event-management-service — Modified:**
- `services/event-management-service/src/main/java/ch/batbern/events/client/UserApiClient.java`
- `services/event-management-service/src/main/java/ch/batbern/events/client/impl/UserApiClientImpl.java` (H1: safe email masking for short emails)
- `services/event-management-service/src/main/java/ch/batbern/events/dto/SpeakerProvisionResponse.java` (L1: added @Builder/@NoArgsConstructor/@AllArgsConstructor)
- `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerAccountCreationService.java` (H2: pass emailHash not plain email to event)
- `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerResponseService.java`
- `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerAcceptanceEmailService.java`
- `services/event-management-service/src/main/resources/db/migration/V55__add_speaker_account_creation_audit_table.sql` (M3: added FK omission rationale comment)
- `services/event-management-service/src/main/resources/email-templates/speaker-acceptance-en.html`
- `services/event-management-service/src/main/resources/email-templates/speaker-acceptance-de.html`
- `services/event-management-service/src/test/java/ch/batbern/events/service/SpeakerAccountCreationServiceTest.java` (L1: updated to builder pattern)
- `services/event-management-service/src/test/java/ch/batbern/events/service/SpeakerResponseServiceTest.java`

**shared-kernel — New:**
- `shared-kernel/src/main/java/ch/batbern/shared/events/SpeakerAccountCreatedEvent.java` (H2: renamed email field to emailHash)

**Infrastructure / Config:**
- `scripts/dev/start-all-native.sh` (AWS_PROFILE default: batbern-dev → batbern-staging)

### Change Log

- feat(story-9.2): implement automatic Cognito account creation on speaker invitation acceptance (2026-02-17)
- fix(cognito): correct `custom:role` attribute key (was `custom:roles`, pool schema uses singular)
- fix(dev): correct AWS_PROFILE default to batbern-staging in start-all-native.sh
- fix(test): resolve flaky UserReconciliationServiceTest timing assertion
- fix(security): add compensating transaction — delete Cognito account if local DB update fails (C1)
- fix(pii): rename `email` to `emailHash` in SpeakerAccountCreatedEvent — no plain email in event bus (H2)
- fix(logging): safe email masking in UserApiClientImpl for emails shorter than 3 chars (H1)
- test(story-9.2): add SpeakerProvisionServiceTest — NEW/EXTENDED branches, compensating tx, password policy (H3)
- refactor(story-9.2): static final SecureRandom in SpeakerProvisionService (L2)
- docs(story-9.2): add FK omission rationale to V55 migration comment (M3)
- refactor(test): use builder pattern for SpeakerProvisionResponse in SpeakerAccountCreationServiceTest (L1)
