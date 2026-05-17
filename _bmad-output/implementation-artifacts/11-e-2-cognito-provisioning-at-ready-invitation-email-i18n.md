# Story 11.E.2: Cognito provisioning at READY + invitation-email rewrite (de + en only, HTML-only)

Status: ready-for-dev

<!-- All 4 Open Questions PM-resolved 2026-05-17. AC + Tasks + Dev Notes below reflect the
resolutions. Resolution narrative preserved at the bottom in "Open Questions (resolved)". -->

## Story

**As a** speaker who has just been promoted from "lead" to "real invitee",
**I want** to receive a clear invitation email with my login link and a temporary password I can change on first login,
**So that** I can access the speaker portal with a standard Cognito experience — no magic links, no parallel auth, no tokens to juggle.

## Phase / Dependencies / Requirements Covered

- **Phase:** E — Cognito with forced password change (second of three E stories). Substantive backend story + de + en email-template rewrite; Story 11.E.1 (CDK + IAM prereq) lands the infrastructure underneath; Story 11.E.3 wires the frontend session.
- **Depends on:**
  1. **Story 11.E.1** (hard infrastructure prereq, status `ready-for-dev` at story-creation time). 11.E.1 grants `AdminCreateUser`, `AdminSetUserPassword`, `AdminInitiateAuth`, `AdminGetUser` on the CUMS task role and enables `ALLOW_ADMIN_USER_PASSWORD_AUTH` on the App Client. All four IAM perms are used by this story (per the two-endpoint design from Resolved Q#1 + Q#4 — see below). **11.E.1 must be merged AND deployed to staging before this story's manual verification step (AC10) can run.**
  2. **Story 11.B.2** (status: `done`). Provides the `SpeakerWorkflowService.transition()` seam — `runReadyHook` (provisioning) and `runInvitedHook` (invitation email) — that this story extends.
  3. **Story 11.C.2** (status: `done`). Provides the `UserApiClient.provisionUserWithRole(ProvisionUserRequest) → ProvisionUserResponse` contract and the `POST /api/v1/users/provision` endpoint in CUMS. **This story removes the `temporaryPassword` field from `ProvisionUserResponse`** — the field was added by 11.C.2 speculatively for the original 11.E.2 design; the resolved Variant-B design (Q#1) puts credential issuance on a separate endpoint at INVITED time, so the field is now obsolete and removed cleanly (still feature-branch state).
- **Unblocks:** Story 11.E.3 (speaker-portal Cognito auth — frontend half of Phase E) and ultimately Story 11.F.1 (magic-link teardown — Phase F; depends on Phase E being stable in production for ≥ 1 week per sprint-status.yaml).
- **Requirements covered (PRD lines 1176-1249, with PM-resolved scope reductions):**
  - **FR3** (Cognito part) — CONTACTED → READY triggers Cognito provisioning with FORCE_CHANGE_PASSWORD.
  - **FR9** — Invitation email carries a portal login link + a generated temporary password.
  - **AR15** — Cognito provisioning logic added to CUMS — `AdminCreateUser` at READY; `AdminGetUser` + conditional `AdminSetUserPassword` at INVITED.
  - **UX-DR21** — Invitation/confirmation/reminder/escalation email templates simplified: tentative-response language removed.
  - **UX-DR22** — i18n. **Scope reduced per PM Resolved Q#2:** only the two official languages (`de` + `en`) ship in this story. The 8 optional locales (`fr`, `it`, `rm`, `es`, `fi`, `nl`, `ja`, `gsw-BE`) are deferred per `CLAUDE.md` §"Localization — Official vs Optional Languages".
  - **NFR2** — No new Cognito Lambda triggers. SPEAKER role granted via PostgreSQL `user_roles` row insert per ADR-001.
  - **NFR3** — Idempotency. `provisionUserWithRole` at READY is retry-safe. The new `issueInvitationCredentials` at INVITED is also retry-safe (AdminGetUser → status-driven branch handles repeated invitations naturally).
  - **NFR5** — Least-privilege. The four Cognito SDK calls used (`AdminCreateUser`, `AdminGetUser`, `AdminSetUserPassword`, plus the existing pool config) all map 1:1 to the four IAM perms 11.E.1 granted.
  - **NFR9** — Cognito User Pool password policy accepts the backend-generated temporary password (≥ 16 chars, all four character classes).
  - **NFR10** — Locale parity — **scope reduced to `de` + `en` only per PM Resolved Q#2.**
- **Plan / ADR anchors:**
  - Epic 11 PRD §"Story 11.E.2" lines 1176-1249.
  - `docs/plans/speaker-workflow-refactor.md` §0.5, §2.3, §3.2.
  - `docs/architecture/ADR-009-unified-speaker-workflow.md` §"Decision 3" + §"Implementation Guidelines" lines 488-515.
  - ADR-009 Revision History v1.3 (2026-05-17) — 11.E.1's Resolved Q#1 (drop `AdminAddUserToGroup`); this story's resolutions extend that record.
  - `CLAUDE.md` §"Localization — Official vs Optional Languages" (added in same commit as this story per PM Resolved Q#2).

---

## Branch state at story start

- **Current branch:** `feature/speaker-workflow-refactor`.
- **Verified at story-creation time (2026-05-17, after reading the source files):**
  - `services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/UserService.java` — lines 620-721 contain `provisionUserWithRole` with three explicit `.temporaryPassword(null);  // Story 11.E.2 wires Cognito` markers (lines 658, 709, 720). This story removes those `.temporaryPassword(...)` lines entirely (the field is removed from the response in this story), and adds a `cognitoIntegrationService.adminCreateUserSilently(...)` call on the new-user path.
  - `services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/CognitoIntegrationServiceImpl.java` — both methods (`syncUserAttributes`, `createCognitoUser`) are documented NO-OPs. The class injects `CognitoIdentityProviderClient cognitoClient` + `String userPoolId` (constructor lines 31-36) — flagged `@SuppressWarnings("unused")` because the class doesn't currently call Cognito. This story replaces the suppression with real call sites.
  - `services/company-user-management-service/src/main/java/ch/batbern/companyuser/controller/UserController.java` — `POST /api/v1/users/provision` exists at line 293-313 (added by 11.C.2). This story adds a sibling endpoint `POST /api/v1/users/{username}/issue-invitation-credentials` (used by EMS at INVITED time).
  - `services/company-user-management-service/src/main/resources/application.yml` line 69 — `user-pool-id: ${COGNITO_USER_POOL_ID:}` is already wired.
  - `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerWorkflowService.java` lines 250-305 — `runReadyHook` (provisioning) + `runInvitedHook` (invitation email). Both hooks land their changes in this story.
  - `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerInvitationEmailService.java` — current 100+-line file loads `email-templates/speaker-invitation-{en|de}.html` and `.txt`. The Mustache-style variables `{{acceptLink}}`, `{{declineLink}}`, `{{jwtMagicLink}}`, `{{dashboardLink}}` are the magic-link payload that this story rewrites.
  - `services/event-management-service/src/main/resources/email-templates/`:
    - Existing today: `speaker-invitation-{de,en}.{html,txt}` (4 files); `speaker-acceptance-{de,en}.html` (2 files — no .txt); `speaker-reminder-response-tier{1,2,3}-{de,en}.html` (6 files); `speaker-reminder-content-tier{1,2,3}-{de,en}.html` (6 files).
    - **This story DELETES** `speaker-invitation-{de,en}.txt` per PM Resolved Q#3 (HTML-only across the board).
    - **This story MODIFIES** the 14 remaining files (`.html` versions) — rewrite invitation, simplify acceptance + reminders.
    - **This story does NOT add 8-locale fan-out** per PM Resolved Q#2. Total file operations: 14 modifications + 2 deletions = 16 (vs. 90 in the pre-resolution draft).
  - `docs/api/users-api.openapi.yml` — lines 1972-2050 carry the `ProvisionUserRequest` + `ProvisionUserResponse` schemas. The `temporaryPassword` field (lines 2033-2040) is **removed** in this story (PM Resolved Q#1 Variant B). A new schema `InvitationCredentialsResponse` is added for the new endpoint.

---

## Acceptance Criteria

The AC are pinned to PRD lines 1176-1249, with PM resolutions to the four Open Questions (2026-05-17) baked in. Where the resolved design diverges from the literal PRD wording (notably AC1 — temp password is no longer returned by `provisionUserWithRole`), the AC reflects the resolved design AND the PRD edit lands in the same commit per CLAUDE.md doc-drift policy.

### AC1 — `UserService.provisionUserWithRole` creates a Cognito user shell on the new-user path (FR3, AR15)

**Given** `services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/UserService.java`,
**When** `provisionUserWithRole(ProvisionUserRequest request)` runs for a non-existent user (the new-user branch around line 714-720),
**Then** before the existing `return new ProvisionUserResponse() ...`:

1. The service generates a strong random "shell" temporary password via the new injected `PasswordGenerator` (see AC4). This password is a throwaway — it satisfies Cognito's pool policy so `AdminCreateUser` accepts the call, but the speaker never sees it. The fresh temp password used at invitation time is generated separately at INVITED (see AC2).
2. The service calls `cognitoIntegrationService.adminCreateUserSilently(email, throwawayPassword, username)` (new method on `CognitoIntegrationService` — see AC2 / AC3). The call is wrapped per the failure-handling contract in AC5.
3. The throwaway password is **immediately discarded** — no field on `UserService`, no log statement, no DB persist. The local variable goes out of scope.
4. **Audit logging:** the existing `log.info("Provisioning: created user {} with role {} (created=true)", ...)` line stays. A new `log.info("Cognito user provisioned for {} (status=FORCE_CHANGE_PASSWORD)", LoggingUtils.maskEmail(email))` IS added before the return. The throwaway password value never appears in any log statement.

**And** for both existing-user idempotent paths (lines 655-659 and 706-710), `adminCreateUserSilently(...)` is **NOT** called — the Cognito user already exists from a prior provisioning. The grep `grep -rn "adminCreateUserSilently" services/company-user-management-service/src/main/` returns exactly two hits: the interface declaration and the one call site inside the new-user branch.

**And** the `ProvisionUserResponse` returned by all three branches drops the `.temporaryPassword(...)` call entirely. The field is **removed from the response schema** (see AC12). Builder calls revert to `new ProvisionUserResponse().username(...).created(...)` only.

**And** the existing P0 role-grant whitelist (lines 633-638) stays untouched — non-ADMIN callers can only request the `SPEAKER` role.

---

### AC2 — `UserService.issueInvitationCredentials(...)` issues a fresh temp password at INVITED time (FR9, AR15, NFR3, Resolved Q#1 Variant B + Q#4)

**Given** the existing `provisionUserWithRole` flow no longer returns a temp password (per AC1),
**When** EMS's `SpeakerWorkflowService.runInvitedHook` needs to send the invitation email,
**Then** EMS calls a new CUMS endpoint to issue (or skip) the temp password based on the Cognito user's current state:

1. New endpoint **`POST /api/v1/users/{username}/issue-invitation-credentials`** added to `UserController`, annotated `@PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")` (service-to-service principal — EMS propagates the ORGANIZER JWT per the existing microservices pattern).
2. Endpoint delegates to `UserService.issueInvitationCredentials(username) → InvitationCredentialsResponse`. The service:
   1. Loads the User by username → 404 if missing.
   2. Calls `cognitoIntegrationService.getUserStatus(email)` (new method — see AC3). Returns the `UserStatusType` enum value reported by `AdminGetUser`.
   3. If the status is `FORCE_CHANGE_PASSWORD` or `RESET_REQUIRED` → generate a fresh temp password via `PasswordGenerator`; call `cognitoIntegrationService.adminSetTemporaryPassword(email, freshTempPassword)` (new method); return `new InvitationCredentialsResponse().temporaryPassword(freshTempPassword).action("FRESH_TEMP_PASSWORD")`.
   4. If the status is `CONFIRMED` → no Cognito mutation; return `new InvitationCredentialsResponse().temporaryPassword(null).action("USE_EXISTING_PASSWORD")`.
   5. If the status is `UNCONFIRMED` (signed up but never confirmed) → log a warning and treat as `FORCE_CHANGE_PASSWORD` (issue fresh temp password). UNCONFIRMED is a self-signup state that does not apply to BATbern's organizer-led provisioning, but the branch is defensive.
   6. Any other status (`ARCHIVED`, `COMPROMISED`, `UNKNOWN`) → throw `IllegalStateException` with a clear message; map to HTTP 422 Unprocessable Entity. These states require operator intervention; EMS should NOT silently mask them.

**And** the returned `InvitationCredentialsResponse` is a new OpenAPI schema with exactly two fields:

```yaml
InvitationCredentialsResponse:
  type: object
  required: [action]
  properties:
    temporaryPassword:
      type: string
      nullable: true
      description: |
        Fresh temporary password for the speaker's first login. Non-null when
        `action = "FRESH_TEMP_PASSWORD"`; null when `action = "USE_EXISTING_PASSWORD"`.
        Never persisted at rest in CUMS or EMS; the caller embeds it once in the
        invitation email and discards from memory.
    action:
      type: string
      enum: ["FRESH_TEMP_PASSWORD", "USE_EXISTING_PASSWORD"]
      description: |
        Discriminator for the calling email service. Determines whether the
        invitation-email template renders the temp-password block or the
        "use your existing password" block (see AC8).
```

**And** the endpoint is idempotent: repeated calls are safe. Each call to the `FRESH_TEMP_PASSWORD` branch generates a *new* temp password (AdminSetUserPassword replaces the prior one — the speaker can only use the most recently issued one). Each call to the `USE_EXISTING_PASSWORD` branch is a pure read (AdminGetUser only).

---

### AC3 — `CognitoIntegrationService` gains three new methods (FR3, FR9, AR15, NFR2)

**Given** `services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/CognitoIntegrationService.java`,
**Then** three new methods are added to the interface:

```java
/**
 * Create a Cognito user shell with a throwaway temporary password and FORCE_CHANGE_PASSWORD status.
 * Story 11.E.2 (AR15, FR3). Called by UserService.provisionUserWithRole at CONTACTED → READY.
 *
 * <p>Suppresses Cognito's default invitation email (MessageAction=SUPPRESS) — BATbern sends its
 * own templated invitation at INVITED time. The temp password passed here is internal-only
 * and is NEVER returned to the speaker; {@link #adminSetTemporaryPassword} generates the real
 * one at INVITED time.
 *
 * <p>Idempotency: if the email already exists in the user pool (UsernameExistsException),
 * the method returns silently (the caller's existing-user branch handles "already provisioned").
 *
 * @param email speaker's email (becomes the Cognito Username AND the email attribute)
 * @param throwawayTempPassword satisfies the pool policy; never surfaced
 * @param appUsername BATbern's username (set as the preferred_username Cognito attribute)
 * @throws CognitoOperationException on any Cognito error other than UsernameExistsException
 */
void adminCreateUserSilently(String email, String throwawayTempPassword, String appUsername);

/**
 * Read the current Cognito user-status for the given email.
 * Story 11.E.2 (AR15, FR9). Called by UserService.issueInvitationCredentials at READY → INVITED.
 *
 * @param email speaker's email
 * @return the AWS SDK {@code UserStatusType} value reported by AdminGetUser
 * @throws UserNotFoundException if AdminGetUser returns UserNotFoundException (the User
 *         row exists in PostgreSQL but no Cognito user — operator must intervene)
 * @throws CognitoOperationException on any other Cognito error
 */
UserStatusType getUserStatus(String email);

/**
 * Set a new temporary password for an existing Cognito user, keeping them in
 * FORCE_CHANGE_PASSWORD state. Story 11.E.2 (AR15, FR9). Called by
 * UserService.issueInvitationCredentials when the user's status is
 * FORCE_CHANGE_PASSWORD or RESET_REQUIRED.
 *
 * <p>Uses {@code AdminSetUserPassword} with {@code Permanent=false}.
 *
 * @param email speaker's email
 * @param freshTempPassword the password to set (must satisfy the pool policy);
 *        the speaker will be challenged to change it on first login
 * @throws CognitoOperationException on any Cognito error
 */
void adminSetTemporaryPassword(String email, String freshTempPassword);
```

**And** the existing `createCognitoUser(GetOrCreateUserRequest)` method on the interface **stays** — it remains a NO-OP for the invitation-based registration path (Story 1.14-2 contract).

**And** `CognitoOperationException` is a new runtime exception at `services/company-user-management-service/src/main/java/ch/batbern/companyuser/exception/CognitoOperationException.java`, extends `RuntimeException`, carries the failed action name and the masked email as accessor fields. Mapped to HTTP 502 Bad Gateway by `GlobalExceptionHandler`.

**And** the impl in `CognitoIntegrationServiceImpl.java`:
- `adminCreateUserSilently(...)` calls `cognitoClient.adminCreateUser(...)` with `MessageAction=SUPPRESS`, the throwaway password, and the user attributes per the Mustache-pattern shown below. `UsernameExistsException` is logged + swallowed; any other error is wrapped in `CognitoOperationException`.
- `getUserStatus(...)` calls `cognitoClient.adminGetUser(...)` and returns `response.userStatus()` directly.
- `adminSetTemporaryPassword(...)` calls `cognitoClient.adminSetUserPassword(...)` with `Permanent=false`.
- The `@SuppressWarnings("unused")` at line 24 is **removed** — both `cognitoClient` and `userPoolId` are now actually used.

**Reference shape for `adminCreateUserSilently`'s AWS SDK call** (matches ADR-009 §"Implementation Guidelines"):

```java
AdminCreateUserRequest req = AdminCreateUserRequest.builder()
    .userPoolId(userPoolId)
    .username(email)
    .temporaryPassword(throwawayTempPassword)
    .messageAction(MessageActionType.SUPPRESS)
    .userAttributes(
        AttributeType.builder().name("email").value(email).build(),
        AttributeType.builder().name("email_verified").value("true").build(),
        AttributeType.builder().name("preferred_username").value(appUsername).build()
        // No given_name / family_name / custom:role — those live in PostgreSQL
        // per ADR-004 (user_profiles) + ADR-001 (user_roles).
    )
    .build();

try {
    cognitoClient.adminCreateUser(req);
    log.info("Cognito user created for {} (FORCE_CHANGE_PASSWORD)", LoggingUtils.maskEmail(email));
} catch (UsernameExistsException e) {
    log.info("Cognito user already exists for {} — idempotent no-op", LoggingUtils.maskEmail(email));
} catch (CognitoIdentityProviderException e) {
    log.error("AdminCreateUser failed for {}: {}", LoggingUtils.maskEmail(email), e.getMessage());
    throw new CognitoOperationException("adminCreateUser", email, e);
}
```

**And** the dev does **NOT** call `AdminAddUserToGroup` anywhere — confirmed by grep returning zero matches in `src/main/`. This is the post-condition of Story 11.E.1's Resolved Q#1 enforced at the application-code layer.

---

### AC4 — `PasswordGenerator` collaborator (NFR9)

**Given** the Cognito pool policy from 11.E.1 (`minLength: 8`, all four character classes, `tempPasswordValidity: 14 days`),
**Then** a new class is created at `services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/PasswordGenerator.java`:

```java
@Component
public class PasswordGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] LOWERCASE = "abcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final char[] UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
    private static final char[] DIGITS = "0123456789".toCharArray();
    // Cognito accepts these symbols cleanly (no copy-paste pitfalls):
    private static final char[] SYMBOLS = "!@#$%&?+-=_*.".toCharArray();

    public String generate() { return generate(16); }

    public String generate(int length) {
        if (length < 8) {
            throw new IllegalArgumentException("Length must be ≥ 8 to satisfy Cognito password policy");
        }
        char[] result = new char[length];
        result[0] = LOWERCASE[RANDOM.nextInt(LOWERCASE.length)];
        result[1] = UPPERCASE[RANDOM.nextInt(UPPERCASE.length)];
        result[2] = DIGITS[RANDOM.nextInt(DIGITS.length)];
        result[3] = SYMBOLS[RANDOM.nextInt(SYMBOLS.length)];
        char[] all = (new String(LOWERCASE) + new String(UPPERCASE)
                + new String(DIGITS) + new String(SYMBOLS)).toCharArray();
        for (int i = 4; i < length; i++) {
            result[i] = all[RANDOM.nextInt(all.length)];
        }
        // Fisher-Yates shuffle so the first four positions aren't predictable.
        for (int i = result.length - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            char tmp = result[i]; result[i] = result[j]; result[j] = tmp;
        }
        return new String(result);
    }
}
```

**And** `PasswordGenerator` is used at **two** sites in this story:
1. `UserService.provisionUserWithRole` → AC1's throwaway temp password.
2. `UserService.issueInvitationCredentials` → AC2's fresh temp password embedded in the invitation email.

**And** unit test `PasswordGeneratorTest` covers the six cases from the pre-resolution draft (length defaults to 16, all four character classes always present across 100 generations, 1000-generation uniqueness sanity, custom-length support, sub-8 length rejection, no ambiguous symbols).

---

### AC5 — Failure-handling contract (FR3 atomic, Resolved Q#1 Variant B)

**Given** `UserService.provisionUserWithRole` and the new `UserService.issueInvitationCredentials`,
**When** the underlying Cognito call fails,
**Then**:

1. `provisionUserWithRole` is annotated `@Transactional` (write transaction). If `adminCreateUserSilently` throws `CognitoOperationException`, the surrounding transaction aborts. The User row from `createNewUser` rolls back; the role-assignment row from `roleService.addRole` rolls back. EMS receives HTTP 502; the workflow transition aborts; the speaker stays at `CONTACTED`. No partial state.
2. `issueInvitationCredentials` is **NOT** annotated `@Transactional` — there is no PostgreSQL write to roll back. If `AdminGetUser` or `AdminSetUserPassword` fails, EMS receives HTTP 502 and the workflow transition to `INVITED` aborts. The speaker stays at `READY`. The organizer can retry the invitation; the retry is safe (AdminSetUserPassword is idempotent from the speaker's perspective — the most recently issued temp password is the only valid one).
3. The race-loser idempotent path in `provisionUserWithRole` (DataIntegrityViolationException at lines 695-710) is **NOT** modified — that path catches the unique-constraint race and returns the existing user. The Cognito call is **not made** on that path (the user already exists, so they already have a Cognito user from a parallel call).

**And** the integration tests at AC11 items 4 + 9 cover the rollback scenarios with Mockito stubs.

---

### AC6 — `SpeakerWorkflowService.runReadyHook` is unchanged in temp-password handling (FR3, Resolved Q#1 Variant B)

**Given** `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerWorkflowService.java` lines 250-288,
**When** `runReadyHook` runs,
**Then** the existing call `ProvisionUserResponse userResponse = userApiClient.provisionUserWithRole(provisionRequest);` (line 265) stays unchanged. The response shape is now narrower (per AC12 — `temporaryPassword` field removed), but the existing code already only reads `userResponse.getUsername()`. **No code change required in this hook beyond keeping the identity-rebind guard intact.**

**And** the existing `SpeakerPromotedToReadyEvent` publish (in `transition()`'s state-specific event-publish step, lines 415-420 of `SpeakerWorkflowService.java`) is **unchanged** — domain-event observers downstream rely on it.

**And** no Caffeine cache or in-memory temp-password stash is added to EMS. The temp password is generated at INVITED time, not READY time — there is no value to retain.

---

### AC7 — `SpeakerWorkflowService.runInvitedHook` calls the new CUMS endpoint via `UserApiClient` (FR9, AR15)

**Given** `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerWorkflowService.java` lines 290-305 (existing `runInvitedHook`),
**When** `runInvitedHook` runs,
**Then**:

1. The two `magicLinkService.generateToken(...)` calls (lines 298-299) are **deleted** — Phase E removes the magic-link payload from the invitation email; Phase F (11.F.1) deletes the rest of `MagicLinkService`. (`MagicLinkService` itself stays — `runAcceptedHook` still uses it for the post-acceptance dashboard token; that's Phase F's cleanup.)
2. The hook calls `userApiClient.issueInvitationCredentials(speaker.getUsername())` (new method — see AC8) and captures the returned `InvitationCredentialsResponse`. The username was persisted on `speaker_pool` at READY (verified at line 286 of the current code).
3. The hook calls `SpeakerInvitationEmailService.sendInvitationEmail(speaker, event, loginUrl, credentials, locale)` with the new signature (per AC8). `credentials` is the captured response (carrying either a fresh temp password or null + action discriminator).
4. The temp password (if non-null) is **discarded from memory** immediately after the email-send returns. The local variable goes out of scope. No field on the workflow service retains it.
5. The existing `speaker.setInvitedAt(Instant.now())` stays unchanged.
6. The existing `AFTER_COMMIT` concern documented in lines 295-297 of the code comments stays in deferred-work; mitigating it is orthogonal to this story.

---

### AC8 — `SpeakerInvitationEmailService` signature change + Cognito-flow email rendering (FR9, UX-DR21, Resolved Q#3)

**Given** `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerInvitationEmailService.java`,
**When** the file is edited,
**Then**:

1. The `sendInvitationEmail` public signature changes from:

   ```java
   public void sendInvitationEmail(SpeakerPool speaker, Event event,
                                   String respondToken, String dashboardToken, Locale locale)
   ```

   to:

   ```java
   public void sendInvitationEmail(SpeakerPool speaker, Event event,
                                   String loginUrl, InvitationCredentialsResponse credentials,
                                   Locale locale)
   ```

   The `@Async` annotation stays. The two old token parameters are dropped. The new parameters carry the Cognito-flow payload + action discriminator.

2. **`.txt` template loading is removed entirely (per PM Resolved Q#3 — HTML-only).** The current `loadEmailTemplate(...)` helper loads both `.html` and `.txt` via `ClassPathResource`; this is reduced to `.html` only. The `EmailService.send(...)` call drops the `textBody` parameter (or passes empty string if the API requires it). Reference: review the call site to confirm whether `EmailService` accepts an HTML-only path; if not, pass an empty `textBody` and document that all email clients in BATbern's audience render HTML.

3. The internal template-variable mapping is updated (replacing the magic-link variables with Cognito-flow variables, plus a new discriminator for the conditional block):

   | Old (magic-link)             | New (Cognito flow)                                                          |
   |------------------------------|-----------------------------------------------------------------------------|
   | `{{acceptLink}}`             | **DELETED**                                                                 |
   | `{{declineLink}}`            | **DELETED**                                                                 |
   | `{{jwtMagicLink}}`           | **DELETED**                                                                 |
   | `{{dashboardLink}}`          | `{{loginUrl}}` — single canonical entry point                               |
   | _none_                       | `{{usernameForLogin}}` — speaker's email (the Cognito username)             |
   | _none_                       | `{{#temporaryPassword}} … {{/temporaryPassword}}` — conditional block; renders only when `credentials.action() == FRESH_TEMP_PASSWORD` |
   | _none_                       | `{{^temporaryPassword}} … {{/temporaryPassword}}` — inverted conditional; renders the "use your existing password" branch when `credentials.action() == USE_EXISTING_PASSWORD` |

4. The `magicLinkService.generateJwtToken(...)` call (around line 89) is **deleted**. The `MagicLinkService` field on `SpeakerInvitationEmailService` is **removed** from the constructor (one-line cleanup). The `MagicLinkService` class itself stays on the codebase for non-invitation callers (Phase F territory).

5. A new unit test `SpeakerInvitationEmailServiceTest` covers (≥ 4 cases — reduced from 5 per PM Q#2 removing the gsw-BE case):
   - `should_renderTemporaryPasswordBlock_when_actionIsFreshTempPassword` (English template).
   - `should_renderUseExistingPasswordBlock_when_actionIsUseExistingPassword` (English template).
   - `should_renderInGermanLocale_when_localeIsDeutsch` (German template).
   - `should_includeLoginUrl_when_emailRendered` (asserts the `{{loginUrl}}` placeholder resolves to the configured base URL + `/login`).

---

### AC9 — Email templates rewritten in `de` + `en` (UX-DR21, UX-DR22 — scope reduced per PM Resolved Q#2 + Q#3)

**Given** the existing 2-locale (`de` + `en`) speaker email templates in `services/event-management-service/src/main/resources/email-templates/`,
**When** this story merges,
**Then** the following file operations are executed:

#### Invitation templates (rewritten; `.txt` deleted per Q#3)

| File | Action |
|---|---|
| `speaker-invitation-en.html` | **MODIFIED** — Cognito-flow rewrite (see reference template below) |
| `speaker-invitation-de.html` | **MODIFIED** — Cognito-flow rewrite (German translation of the English reference) |
| `speaker-invitation-en.txt` | **DELETED** — HTML-only per Q#3 |
| `speaker-invitation-de.txt` | **DELETED** — HTML-only per Q#3 |

**Content of the rewritten invitation template (English reference; German is a translation of this exact content; layout is the existing HTML structure with BATbern branding):**

```
Subject: Speaker Invitation — {{eventTitle}}

Dear {{speakerName}},

We are pleased to invite you as a speaker at {{eventTitle}} on {{eventDate}} at
{{venueName}}, {{venueAddress}}.

{{#sessionTitle}}
Proposed topic: {{sessionTitle}}
{{#sessionDescription}}
{{sessionDescription}}
{{/sessionDescription}}
{{/sessionTitle}}

Your speaker portal account
---------------------------

We have created a portal account for you. To respond to this invitation and
submit your session materials, please log in at:

   {{loginUrl}}

Username: {{usernameForLogin}}

{{#temporaryPassword}}
Temporary password: {{temporaryPassword}}

You will be asked to set your own password on first login. The temporary
password is valid for 14 days.
{{/temporaryPassword}}

{{^temporaryPassword}}
You already have a BATbern account. Please log in with your existing password,
or use the "Forgot password" link on the login page if you need to reset it.
{{/temporaryPassword}}

{{#responseDeadline}}
Please respond by: {{responseDeadline}}
{{#contentDeadline}}
If you accept, please submit your presentation materials by: {{contentDeadline}}
{{/contentDeadline}}
{{/responseDeadline}}

Questions? Reply to this email — {{organizerName}} ({{organizerEmail}}).

Thank you,
The BATbern Team
```

**And** the dev runs:

```bash
grep -l "magic\|acceptLink\|declineLink\|jwtMagicLink\|dashboardLink" \
    services/event-management-service/src/main/resources/email-templates/speaker-invitation-*.html
```

→ expects **zero** matches. The replacement is total.

#### Acceptance + reminder templates (simplified to drop tentative-response language)

| File | Action |
|---|---|
| `speaker-acceptance-en.html` | **MODIFIED** — remove tentative-response copy |
| `speaker-acceptance-de.html` | **MODIFIED** — remove tentative-response copy |
| `speaker-reminder-response-tier{1,2,3}-en.html` (3 files) | **MODIFIED** — remove tentative-response copy |
| `speaker-reminder-response-tier{1,2,3}-de.html` (3 files) | **MODIFIED** — remove tentative-response copy |
| `speaker-reminder-content-tier{1,2,3}-en.html` (3 files) | **REVIEWED**; modified only if tentative-response copy is present |
| `speaker-reminder-content-tier{1,2,3}-de.html` (3 files) | **REVIEWED**; modified only if tentative-response copy is present |

**Total file operations for AC9: 14 modifications + 2 deletions = 16 (vs. 90 in the pre-resolution draft).**

**Optional locales (`fr`, `it`, `rm`, `es`, `fi`, `nl`, `ja`, `gsw-BE`) are NOT part of this story** — per PM Resolved Q#2 and `CLAUDE.md` §"Localization — Official vs Optional Languages". If a render is requested at runtime for one of those locales and no template exists, the email service falls back to English (the existing `Locale.getDefault()` / `Locale.GERMAN` fallback in `SpeakerInvitationEmailService` line 83 already handles this — verify, do not break it).

---

### AC10 — Manual smoke test on staging (FR9 verification)

**Given** Story 11.E.1 has been deployed to staging,
**When** this story is deployed to staging,
**Then** the dev runs an end-to-end manual smoke test before requesting code review:

1. Log in to the organizer kanban at the staging hostname.
2. Pick an upcoming event with at least one open speaker slot.
3. Add a test speaker (the dev's own personal test email) to the brainstorm panel (state: `IDENTIFIED`).
4. Move through `CONTACTED` → `READY`. Verify CloudWatch (`/aws/ecs/BATbern-staging/company-user-management`) shows `Cognito user created for {masked-email} (FORCE_CHANGE_PASSWORD)`.
5. Move to `INVITED` ("Send invitation"). Verify CloudWatch shows the `issueInvitationCredentials` call with action=FRESH_TEMP_PASSWORD. Wait for the invitation email.
6. Verify the email is HTML-only (no plain-text alternative — per Q#3), contains the login URL, the speaker's email as the username, a 16-char temp password, and the "you will be asked to set your own password" note.
7. Open the login URL incognito. Enter email + temp password. Confirm `NEW_PASSWORD_REQUIRED` Cognito challenge appears. Set a new password. Confirm the speaker lands on `/speaker-portal/dashboard` (full Cognito-session refactor is Story 11.E.3).
8. Verify in the AWS console that the Cognito user status is now `CONFIRMED`.
9. **Re-invite negative case:** in the kanban, manually transition the speaker back to `READY`, then `INVITED` again. Verify CloudWatch shows the `issueInvitationCredentials` call with action=USE_EXISTING_PASSWORD (because the speaker is now CONFIRMED). Verify the second invitation email contains the "use your existing password" wording and NO temp password.
10. Clean up: delete the test Cognito user (`aws cognito-idp admin-delete-user`); decline the test speaker in the kanban with reason "test cleanup".

**And** the dev pastes (in the PR description, NOT in the commit message): masked email + the four CloudWatch log lines (AdminCreateUser + issueInvitationCredentials × 2 + Cognito password-change event). The temp password value is NEVER pasted into the PR description, the commit message, or any chat tool.

---

### AC11 — Integration tests (NFR6, NFR3)

**Given** `./gradlew :services:company-user-management-service:test :services:event-management-service:test`,
**Then** new and updated integration tests cover (≥ 9 cases across both services):

#### CUMS — extend `UserServiceIntegrationTest`

1. `should_callAdminCreateUserSilently_when_provisioningNewSpeaker` — Mockito spy on `CognitoIntegrationService` records exactly one `adminCreateUserSilently` call with the right email + a non-null 16-char throwaway password. The response from `provisionUserWithRole` has **no temporaryPassword field** (it's removed from the schema — see AC12).
2. `should_notCallCognito_when_provisioningExistingUser` — seeds an existing User (no SPEAKER role yet), calls `provisionUserWithRole`. Asserts: role-assignment row added (idempotent grant succeeds); Cognito spy was **NOT** called.
3. `should_rollbackUserRow_when_cognitoCreateFails` — Mockito stub throws `CognitoOperationException` on `adminCreateUserSilently`. Asserts: no User row in `users` table; no role-assignment row; exception propagated to the caller. Verifies the `@Transactional` rollback from AC5.
4. `should_handleUsernameExists_when_cognitoRaceCondition` — Mockito stub throws `UsernameExistsException`. Asserts: method returns normally; User + role rows DO persist (partial state is fine — Cognito user exists from a parallel call).

#### CUMS — new `UserServiceIssueCredentialsIntegrationTest`

5. `should_returnFreshTempPassword_when_userInForceChangePassword` — Mockito stub: `getUserStatus` returns `FORCE_CHANGE_PASSWORD`. Asserts: `adminSetTemporaryPassword` invoked once with a 16-char password; response action = FRESH_TEMP_PASSWORD; response temporaryPassword = the value passed to AdminSetUserPassword.
6. `should_returnNullPassword_when_userInConfirmed` — Mockito stub: `getUserStatus` returns `CONFIRMED`. Asserts: `adminSetTemporaryPassword` NOT invoked; response action = USE_EXISTING_PASSWORD; response temporaryPassword = null.
7. `should_treatUnconfirmedAsForceChangePassword` — Mockito stub: `getUserStatus` returns `UNCONFIRMED`. Asserts: same behaviour as FORCE_CHANGE_PASSWORD; warning logged.
8. `should_throw422_when_userInArchivedOrCompromised` — Mockito stub: `getUserStatus` returns `ARCHIVED`. Asserts: `IllegalStateException` thrown (mapped to 422 at the HTTP layer); no Cognito mutation.

#### EMS — extend `SpeakerWorkflowServiceIntegrationTest`

9. `should_callIssueInvitationCredentials_when_runningInvitedHook` — seeds READY speaker. WireMock stub on CUMS `POST /api/v1/users/{username}/issue-invitation-credentials` returns `{action:"FRESH_TEMP_PASSWORD", temporaryPassword:"Test1234!@#$abcde"}`. Runs `transition(speaker.id, INVITED, organizer, payload)`. Asserts: the WireMock call was made exactly once with the right username; `SpeakerInvitationEmailService.sendInvitationEmail` was invoked once with the credentials response; speaker → INVITED; `invitedAt` set.

**And** all integration tests extend `AbstractIntegrationTest` (per project-context.md). CUMS uses `TestAwsConfig.cognitoIdentityProviderClient()` (Mockito-mocked). EMS uses WireMock for the cross-service CUMS HTTP boundary.

**And** `SpeakerInvitationEmailServiceTest` (per AC8 item 5) is a unit test, not integration.

---

### AC12 — OpenAPI spec updates (ADR-006)

**Given** `docs/api/users-api.openapi.yml`,
**When** the file is edited,
**Then**:

1. The `temporaryPassword` field is **removed** from the `ProvisionUserResponse` schema (lines 2033-2040 of the current file). The field was added by 11.C.2 anticipating the original 11.E.2 design; the resolved Variant-B design moves credential issuance to a separate endpoint, so the field is obsolete. This is a clean OpenAPI break (still feature-branch state — no consumer outside `feature/speaker-workflow-refactor` reads the field).
2. The `POST /users/provision` path-level description (line 1390-1402) is updated to drop "Cognito wiring is **deliberately stubbed**" and replace with "Cognito wiring (AdminCreateUser at READY; AdminGetUser + AdminSetUserPassword at INVITED via the sibling `issue-invitation-credentials` endpoint) is wired per Story 11.E.2."
3. A new path is added: `POST /users/{username}/issue-invitation-credentials` with the `InvitationCredentialsResponse` schema per AC2. Path-level description names Story 11.E.2 and references the AdminGetUser + AdminSetUserPassword Cognito API.
4. The dev runs:

   ```bash
   cd web-frontend && npm run generate:api-types:users
   ./gradlew :services:company-user-management-service:openApiGenerateUsers
   ./gradlew :services:event-management-service:openApiGenerateUsers
   ```

   — and commits any regenerated `web-frontend/src/types/generated/users-api.types.ts`. The Java DTOs in `build/generated/` are NOT committed (per ADR-006).

---

### AC13 — Bruno contract test extension

**Given** `bruno-tests/users-api/`,
**Then**:

1. The existing `provision-user.bru` test asserting `temporaryPassword: null` (added by 11.C.2) is **updated** to drop the `temporaryPassword` assertion — the field no longer exists in the response.
2. A new `issue-invitation-credentials.bru` is added covering: (a) call against a newly-provisioned speaker (status FORCE_CHANGE_PASSWORD → action FRESH_TEMP_PASSWORD with a 16-char `temporaryPassword` matching the regex); (b) call against a CONFIRMED speaker (action USE_EXISTING_PASSWORD with `temporaryPassword: null`).
3. The dev does **NOT** create `bruno-tests/auth/` (per Story 11.E.1's AC5 deferral — the auth-flow Bruno tests can be added by a follow-up story if needed). Manual smoke test in AC10 covers the integration path.
4. `./scripts/ci/run-bruno-tests.sh` runs green.

---

## Tasks / Subtasks

Tasks ordered so the build stays green at each step.

### Task 1 — `PasswordGenerator` (AC4)

1.1. Create `PasswordGenerator.java` + `PasswordGeneratorTest.java` per AC4.
1.2. **Verify:** `./gradlew :services:company-user-management-service:test --tests PasswordGeneratorTest 2>&1 | tee /tmp/pwgen-test.log` — green.

### Task 2 — `CognitoIntegrationService` interface + impl (AC2, AC3)

2.1. Add the three new methods to the `CognitoIntegrationService` interface per AC3.
2.2. Create `CognitoOperationException` per AC3.
2.3. Implement the three methods in `CognitoIntegrationServiceImpl` per AC3. Remove the `unused` from the class-level `@SuppressWarnings`.
2.4. Wire `CognitoOperationException → HTTP 502` in CUMS `GlobalExceptionHandler`.
2.5. Add unit tests for `CognitoIntegrationServiceImpl` with Mockito-stubbed `CognitoIdentityProviderClient` (extends the existing `CognitoIntegrationServiceImplTest`).
2.6. **Verify:** `./gradlew :services:company-user-management-service:test --tests CognitoIntegrationServiceImplTest 2>&1 | tee /tmp/cognito-test.log` — green.

### Task 3 — `UserService.provisionUserWithRole` wires AdminCreateUser silent (AC1, AC5)

3.1. Add `@Transactional` to `provisionUserWithRole` at line 621.
3.2. Inject `PasswordGenerator` + `CognitoIntegrationService` into `UserService`. Update existing test stubs.
3.3. On the new-user branch (around line 714-720), generate the throwaway temp password and call `adminCreateUserSilently(...)`.
3.4. Remove the `.temporaryPassword(null)` call from all three branches (lines 658, 709, 720) — the field is removed from the response in this story.
3.5. Update inline comments per AC1.
3.6. Extend `UserServiceIntegrationTest` with the four cases from AC11 items 1-4.
3.7. **Verify:** `./gradlew :services:company-user-management-service:test --tests UserServiceIntegrationTest 2>&1 | tee /tmp/cums-int-test.log` — green.

### Task 4 — `UserService.issueInvitationCredentials` + new endpoint (AC2)

4.1. Implement `UserService.issueInvitationCredentials(username) → InvitationCredentialsResponse` per AC2.
4.2. Add the `POST /api/v1/users/{username}/issue-invitation-credentials` endpoint to `UserController`, annotated `@PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")`.
4.3. Create the new integration test class `UserServiceIssueCredentialsIntegrationTest` with the four cases from AC11 items 5-8.
4.4. **Verify:** `./gradlew :services:company-user-management-service:test --tests UserServiceIssueCredentialsIntegrationTest 2>&1 | tee /tmp/cums-creds-test.log` — green.

### Task 5 — `UserApiClient.issueInvitationCredentials` + EMS hook wiring (AC6, AC7)

5.1. Add `UserApiClient.issueInvitationCredentials(String username) → InvitationCredentialsResponse` method to the EMS client (`services/event-management-service/src/main/java/ch/batbern/events/client/UserApiClient.java`) + its `UserApiClientImpl`.
5.2. Update `SpeakerWorkflowService.runInvitedHook` per AC7: delete the two magic-link `generateToken` calls; call the new `userApiClient.issueInvitationCredentials(...)`; call `invitationEmailService.sendInvitationEmail(...)` with the new signature.
5.3. Verify `runReadyHook` (AC6) compiles unchanged — the existing code only reads `userResponse.getUsername()`.
5.4. Extend `SpeakerWorkflowServiceIntegrationTest` with AC11 item 9.
5.5. **Verify:** `./gradlew :services:event-management-service:test --tests SpeakerWorkflowServiceIntegrationTest 2>&1 | tee /tmp/ems-wf-test.log` — green.

### Task 6 — `SpeakerInvitationEmailService` signature change + HTML-only + template-variable rewrite (AC8)

6.1. Change `sendInvitationEmail` signature per AC8 item 1.
6.2. Drop the `.txt` template-loading code path per AC8 item 2.
6.3. Update `loadEmailTemplate(...)` to populate `{{loginUrl}}`, `{{usernameForLogin}}`, and the `{{#temporaryPassword}}` / `{{^temporaryPassword}}` conditional sections per AC8 item 3.
6.4. Drop `magicLinkService.generateJwtToken(...)` + remove `MagicLinkService` from the constructor.
6.5. Create / update `SpeakerInvitationEmailServiceTest` with the four AC8 item 5 cases.
6.6. **Verify:** `./gradlew :services:event-management-service:test --tests SpeakerInvitationEmailServiceTest 2>&1 | tee /tmp/ems-email-test.log` — green.

### Task 7 — Rewrite `de` + `en` invitation HTML templates + delete `.txt` (AC9)

7.1. Edit `speaker-invitation-en.html` per the AC9 reference (preserve existing branding + structure; swap magic-link block for Cognito-flow block).
7.2. Translate to German + edit `speaker-invitation-de.html`.
7.3. `git rm services/event-management-service/src/main/resources/email-templates/speaker-invitation-{de,en}.txt`.
7.4. **Verify** the grep from AC9 returns zero matches.
7.5. Re-run `SpeakerInvitationEmailServiceTest` (Task 6.5) against the rewritten templates.

### Task 8 — Simplify `de` + `en` acceptance + reminder templates (AC9)

8.1. Edit `speaker-acceptance-{de,en}.html` (2 files) — strip tentative-response copy; keep confirmation message + portal link.
8.2. Edit `speaker-reminder-response-tier{1,2,3}-{de,en}.html` (6 files) — strip tentative-response copy.
8.3. Review `speaker-reminder-content-tier{1,2,3}-{de,en}.html` (6 files) — modify only if tentative-response copy is present.
8.4. **Verify** grep:
   ```
   grep -l "tentative\|Tentativ" services/event-management-service/src/main/resources/email-templates/speaker-{acceptance,reminder}-*.html
   ```
   → zero matches in `de` + `en` files.

### Task 9 — OpenAPI spec + regenerate types (AC12)

9.1. Edit `docs/api/users-api.openapi.yml` per AC12: remove `temporaryPassword` from `ProvisionUserResponse`; update path-level description; add `POST /users/{username}/issue-invitation-credentials` + `InvitationCredentialsResponse` schema.
9.2. Regenerate types:
   ```
   cd web-frontend && npm run generate:api-types:users
   ./gradlew :services:company-user-management-service:openApiGenerateUsers
   ./gradlew :services:event-management-service:openApiGenerateUsers
   ```
9.3. Commit any regenerated `web-frontend/src/types/generated/users-api.types.ts`.
9.4. **Verify:** `./gradlew :services:company-user-management-service:compileJava :services:event-management-service:compileJava 2>&1 | tee /tmp/openapi-compile.log` — green.

### Task 10 — Bruno tests (AC13)

10.1. Update `bruno-tests/users-api/provision-user.bru` — drop the `temporaryPassword` assertion.
10.2. Add `bruno-tests/users-api/issue-invitation-credentials.bru` covering both action branches.
10.3. **Verify:** `./scripts/ci/run-bruno-tests.sh 2>&1 | tee /tmp/bruno.log` — green.

### Task 11 — Manual smoke test on staging (AC10)

11.1. Confirm Story 11.E.1 is deployed to staging — `aws cognito-idp describe-user-pool-client --query 'UserPoolClient.ExplicitAuthFlows'` contains `ALLOW_ADMIN_USER_PASSWORD_AUTH`.
11.2. Merge this story's branch via PR; wait for staging deploy (~20-30 min — do NOT cancel mid-deploy per CLAUDE.md "ECS Deploy Patience").
11.3. Execute the 10-step smoke-test procedure from AC10.
11.4. Paste the four CloudWatch log lines + the negative-case observation into the PR description.
11.5. Clean up test Cognito user + test speaker-pool row.

### Task 12 — Doc-drift sweep (CLAUDE.md doc-drift policy)

12.1. **PRD edit** — `docs/prd/epic-11-speaker-workflow-refactor.md`:
   - Lines 1190-1205 (AC1): rewrite to reflect Variant B — temp password is NOT returned by `provisionUserWithRole`; it's issued at INVITED by `issueInvitationCredentials`.
   - Lines 1232-1235 (NFR10 locale parity): replace "all 10 locales" with "de + en officially per `CLAUDE.md` §Localization; 8 optional locales deferred to follow-up story".
   - Line 127-128 (NFR10): same edit.
2.2. **ADR-009 edit** — `docs/architecture/ADR-009-unified-speaker-workflow.md`:
   - Add revision-history row dated 2026-MM-DD: "Story 11.E.2 PM-resolved Q#1-Q#4: temp-password issuance moved to a separate endpoint at INVITED (Variant B); locale scope reduced to `de` + `en` per CLAUDE.md §Localization; .txt template parity dropped (HTML-only); re-invite UX uses AdminGetUser status detection. `ProvisionUserResponse.temporaryPassword` field removed from OpenAPI."
   - Update §"Implementation Guidelines" lines 488-515: the `SpeakerProvisioningService.provisionForSpeaker` skeleton currently returns `tempPassword`; rewrite to show the two-endpoint pattern (AdminCreateUser silently at READY; AdminGetUser + conditional AdminSetUserPassword at INVITED).
12.3. **Backend architecture doc** — `docs/architecture/06-backend-architecture.md` §"Speaker authentication (ADR-009)": update to describe the two-endpoint Cognito-call topology.
12.4. **State-machine doc** — `docs/architecture/06a-workflow-state-machines.md`: update the READY + INVITED hook sections to reflect the resolved design.
12.5. **CLAUDE.md** — already updated (in the same commit as this story) to add §"Localization — Official vs Optional Languages". Verify the section is present.

### Task 13 — Commit + PR

13.1. Single commit per the doc-drift policy. Conventional-commit subject:

```
feat(speaker-workflow): wire Cognito provisioning + invitation-email rewrite (de+en, HTML-only) [Story 11.E.2]
```

Body summarises: AdminCreateUser at READY + new issueInvitationCredentials endpoint at INVITED (AdminGetUser + conditional AdminSetUserPassword per resolved Q#1 Variant B + Q#4); `de` + `en` template rewrite per Q#2; .txt files deleted per Q#3; `temporaryPassword` field removed from `ProvisionUserResponse`; CLAUDE.md §Localization added; PRD + ADR-009 + backend + state-machine docs aligned in same commit.

13.2. Open PR against `feature/speaker-workflow-refactor`. PR description includes:
   - Resolved Open Questions (Q#1 Variant B, Q#2 de+en, Q#3 HTML-only, Q#4 AdminGetUser detection) with one-line rationale each.
   - Manual-verification evidence from AC10 (CloudWatch log lines + re-invite negative-case observation).
   - "Doc alignment" section listing the edits from Task 12.

### Task 14 — Sprint status

14.1. Update `_bmad-output/implementation-artifacts/sprint-status.yaml`: set `11-e-2-cognito-provisioning-at-ready-invitation-email-i18n` from `ready-for-dev` → `in-progress` (when the dev starts coding) → `review` (when the PR opens). Bump `last_updated`.

---

## Dev Notes

### Why the two-endpoint design is the right resolution

PM picked Variant B for Q#1 because:
- It avoids any local state in EMS between READY and INVITED. The temp password is generated at the moment it's needed and consumed before the call returns.
- It uses the `AdminSetUserPassword` IAM permission that Story 11.E.1 already granted (which would have been an unused, dead-letter permission under Variant A).
- It aligns with the existing CUMS-owns-Cognito boundary — the User domain handles all Cognito SDK calls; EMS just makes HTTP calls.

Pairing with Q#4 (AdminGetUser status detection) is natural: both Cognito calls live behind the same CUMS endpoint, which can check status and act accordingly without leaking Cognito concerns into EMS.

### The four Cognito SDK calls in scope

| Cognito API | When | IAM perm granted by 11.E.1? |
|---|---|---|
| `AdminCreateUser` (with `MessageAction=SUPPRESS`) | READY, via `adminCreateUserSilently` | ✅ Yes |
| `AdminGetUser` | INVITED, via `getUserStatus` (status discrimination) | ✅ Yes |
| `AdminSetUserPassword` (with `Permanent=false`) | INVITED, via `adminSetTemporaryPassword`, when status is FORCE_CHANGE_PASSWORD or RESET_REQUIRED | ✅ Yes |
| `AdminInitiateAuth` | NOT used in this story — Story 11.E.3 uses it on the frontend session-refresh path | ✅ Yes (for 11.E.3) |

The four IAM permissions in 11.E.1 are scoped to support all three Phase-E stories; this story exercises three of them.

### Why no `AdminAddUserToGroup`

Per ADR-009 v1.3 + 11.E.1's Resolved Q#1: BATbern's Cognito User Pool has no groups (see `cognito-stack.ts` lines 277-279, "REMOVED: Cognito Groups"). The SPEAKER role lives in PostgreSQL `user_roles`, granted by `RoleService.addRole(...)` at line 714 of `UserService.provisionUserWithRole`. The pre-token-generation Lambda picks up `user_roles` at login time and adds it to the JWT — Spring `@PreAuthorize("hasRole('SPEAKER'))` (added by 11.E.3) just works.

### Why we discard the throwaway temp password at READY (Q#1 + Q#4 interaction)

`AdminCreateUser` requires `TemporaryPassword` when `MessageAction=SUPPRESS` (Cognito would otherwise auto-generate one and try to email it, which SUPPRESS blocks — so an explicit value is required). The value we pass is real but useless to the speaker:
- The speaker never sees it.
- At INVITED time, `AdminSetUserPassword(Permanent=false)` overwrites it with a fresh value.
- Between READY and INVITED, an operator with AWS-console access could theoretically use it — but the dev's audit log makes that visible, and BATbern doesn't grant production Cognito console access casually.

If PM later wants stricter behaviour (e.g., the throwaway password is deleted from Cognito between READY and INVITED), that's a follow-up story — the AdminSetUserPassword at INVITED already mitigates the risk by overwriting.

### Why CUMS does the Cognito calls (and not EMS)

ADR-009 §Decision 3 + the refactor plan §3.2 + Story 11.E.1's IAM grant all place the Cognito SDK call on the **CUMS task role**. Cognito User Pool is the User domain's responsibility (CUMS owns Users); EMS owns Speakers + Workflow but does not own User identity. The two-endpoint design preserves this boundary cleanly.

### Why HTML-only emails (Q#3)

The team accepts the trade-off: modern email clients all render HTML; maintaining `.txt` parity adds files without operational value. The 14-day temp password is readable in the HTML version on every client BATbern's audience uses. If a speaker reports inability to read the email, the fallback is to use Cognito's "Forgot password" flow — which doesn't depend on the invitation email at all.

### Why we don't ship 8-locale fan-out (Q#2)

`CLAUDE.md` §"Localization — Official vs Optional Languages" makes this a project-wide rule going forward: `de` + `en` are official; the other 8 locales are optional. The 8-locale fan-out for 4 template families would add ~70 files to this story without proportionate value — the speaker email audience is overwhelmingly DE/EN. A community contribution or a follow-up story can add the other locales later if real demand emerges.

### Project Structure Notes

**CUMS:**
- `UserService.java` (MODIFIED — wire AdminCreateUser silent; add `issueInvitationCredentials`; add `@Transactional`)
- `CognitoIntegrationService.java` (MODIFIED — three new methods)
- `CognitoIntegrationServiceImpl.java` (MODIFIED — implement three new methods; drop `unused` suppression)
- `UserController.java` (MODIFIED — new `POST /users/{username}/issue-invitation-credentials` endpoint)
- `PasswordGenerator.java` (NEW)
- `CognitoOperationException.java` (NEW)
- `GlobalExceptionHandler` (MODIFIED — map new exception to 502)
- `PasswordGeneratorTest.java` (NEW)
- `UserServiceIntegrationTest.java` (MODIFIED — AC11 items 1-4)
- `UserServiceIssueCredentialsIntegrationTest.java` (NEW — AC11 items 5-8)
- `CognitoIntegrationServiceImplTest.java` (MODIFIED — unit tests for three new methods)

**EMS:**
- `UserApiClient.java` (MODIFIED — new `issueInvitationCredentials` method)
- `UserApiClientImpl.java` (MODIFIED — implement)
- `SpeakerWorkflowService.java` (MODIFIED — runInvitedHook rewires; runReadyHook unchanged)
- `SpeakerInvitationEmailService.java` (MODIFIED — new signature; drop .txt loading; new template variables)
- `SpeakerWorkflowServiceIntegrationTest.java` (MODIFIED — AC11 item 9)
- `SpeakerInvitationEmailServiceTest.java` (NEW or MODIFIED — AC8 item 5)

**Email templates** (under `services/event-management-service/src/main/resources/email-templates/`):
- `speaker-invitation-{de,en}.html` (2 files, MODIFIED)
- `speaker-invitation-{de,en}.txt` (2 files, DELETED)
- `speaker-acceptance-{de,en}.html` (2 files, MODIFIED)
- `speaker-reminder-response-tier{1,2,3}-{de,en}.html` (6 files, MODIFIED)
- `speaker-reminder-content-tier{1,2,3}-{de,en}.html` (6 files, REVIEWED — modified only if tentative copy present)

Total: 14 modifications + 2 deletions.

**Specs + docs:**
- `docs/api/users-api.openapi.yml` (MODIFIED — remove field; add endpoint + schema)
- `web-frontend/src/types/generated/users-api.types.ts` (REGENERATED)
- `docs/architecture/ADR-009-unified-speaker-workflow.md` (MODIFIED — Implementation Guidelines + new revision-history row)
- `docs/architecture/06-backend-architecture.md` (MODIFIED — Speaker authentication section)
- `docs/architecture/06a-workflow-state-machines.md` (MODIFIED — READY/INVITED hook narratives)
- `docs/prd/epic-11-speaker-workflow-refactor.md` (MODIFIED — AC1 + NFR10 wording per resolved Q#1 + Q#2)
- `CLAUDE.md` (ALREADY MODIFIED in this commit — §"Localization — Official vs Optional Languages")

**Bruno:**
- `bruno-tests/users-api/provision-user.bru` (MODIFIED — drop tempPassword assertion)
- `bruno-tests/users-api/issue-invitation-credentials.bru` (NEW)

### Testing Standards

- Backend integration tests extend `AbstractIntegrationTest` (Testcontainers PostgreSQL). Cognito calls mocked via Mockito on `CognitoIntegrationService` (CUMS) or WireMock on the `POST /api/v1/users/{username}/issue-invitation-credentials` boundary (EMS).
- `PasswordGeneratorTest` is a pure unit test.
- `SpeakerInvitationEmailServiceTest` is a unit test with a stubbed `EmailService` — verifies template rendering only.
- Manual verification (AC10) is the only test that hits the real Cognito user pool.

### References

- [Source: docs/prd/epic-11-speaker-workflow-refactor.md lines 1176-1249] — Story 11.E.2 AC list (will be edited in same commit per resolutions).
- [Source: docs/prd/epic-11-speaker-workflow-refactor.md lines 27-129] — FR3, FR9, NFR2-NFR10 definitions.
- [Source: docs/architecture/ADR-009-unified-speaker-workflow.md §Decision 3, lines 254-298].
- [Source: docs/architecture/ADR-009-unified-speaker-workflow.md §Implementation Guidelines, lines 488-515] — pre-resolution skeleton; will be updated in same commit.
- [Source: docs/architecture/ADR-009-unified-speaker-workflow.md Revision History v1.3, line 567].
- [Source: docs/plans/speaker-workflow-refactor.md §0.5, §2.3, §3.2].
- [Source: services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/UserService.java lines 620-721] — current stub with three `.temporaryPassword(null)` markers.
- [Source: services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/CognitoIntegrationServiceImpl.java] — current NO-OP impl.
- [Source: services/company-user-management-service/src/main/java/ch/batbern/companyuser/controller/UserController.java lines 277-313] — existing `POST /api/v1/users/provision` endpoint.
- [Source: services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerWorkflowService.java lines 250-305] — `runReadyHook` + `runInvitedHook`.
- [Source: services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerInvitationEmailService.java] — current signature uses magic-link tokens.
- [Source: services/event-management-service/src/main/resources/email-templates/speaker-invitation-en.html] — current template content.
- [Source: CLAUDE.md §"Localization — Official vs Optional Languages"] — new section added in same commit; governs scope of Q#2.
- [Source: CLAUDE.md §"Doc Drift Prevention"] — same-commit doc updates.
- [Source: _bmad-output/implementation-artifacts/11-e-1-cdk-iam-prereq-cognito-admin-flow.md] — IAM prereq inherited as binding (no `AdminAddUserToGroup`; 14-day tempPasswordValidity).
- [Source: _bmad-output/implementation-artifacts/11-b-2-speakerworkflowservice-sole-status-writer.md] — workflow-service seam.
- [Source: _bmad-output/implementation-artifacts/11-c-2-userapiclient-provisioning-contentsubmissionservice-shared.md] — `provisionUserWithRole` contract.
- [Source: _bmad-output/project-context.md §"Authentication & Roles"] — roles in PostgreSQL, not Cognito groups.
- [Source: _bmad-output/project-context.md §"Backend Integration Tests"] — `AbstractIntegrationTest` mandate.

---

## Dev Agent Record

### Agent Model Used

claude-opus-4-7[1m]

### Debug Log References

_To be filled by the dev agent during implementation._

### Completion Notes List

_To be filled by the dev agent during implementation._

### File List

_To be filled by the dev agent during implementation._

---

## Open Questions (resolved 2026-05-17)

All four questions were resolved with PM (Nissim) before development. The AC, Tasks, and Dev Notes above reflect the resolutions; this section preserves the resolution narrative for traceability.

1. ✅ **Variant B — AdminSetUserPassword at INVITED time.** Temp-password issuance moves from `provisionUserWithRole` (where the original PRD wording put it) to a new sibling endpoint `POST /api/v1/users/{username}/issue-invitation-credentials` called at READY → INVITED. The new endpoint runs `AdminGetUser` to check status (see Q#4 below), then conditionally `AdminSetUserPassword(Permanent=false)` to generate the fresh temp password. The `temporaryPassword` field is **removed** from `ProvisionUserResponse` entirely — it was added by 11.C.2 anticipating the original design and is now obsolete. AC1, AC2, AC6, AC7, AC12 reflect the new design. PRD lines 1190-1205 (AC1) are edited in the same commit per CLAUDE.md doc-drift policy.

2. ✅ **`de` + `en` only — 8 optional locales deferred per `CLAUDE.md` §Localization.** Only the two official communication languages ship in this story. The 8 optional locales (`fr`, `it`, `rm`, `es`, `fi`, `nl`, `ja`, `gsw-BE`) are a follow-up if community demand emerges. This rule is now project-wide policy (added to `CLAUDE.md` in the same commit) and applies to future stories with i18n scope. Template count drops from 90 → 14 modifications + 2 deletions. PRD NFR10 wording is edited in the same commit.

3. ✅ **HTML-only — drop `.txt` templates.** The existing `speaker-invitation-{de,en}.txt` files are deleted; future email templates ship HTML-only. Modern email clients all render HTML; the operational value of `.txt` parity is marginal. AC8 + AC9 + Task 7.3 reflect the deletion.

4. ✅ **AdminGetUser status detection at INVITED time.** The `issueInvitationCredentials` endpoint calls `AdminGetUser` first, branches on `UserStatusType`: FORCE_CHANGE_PASSWORD or RESET_REQUIRED → generate fresh temp password + AdminSetUserPassword; CONFIRMED → return null (use-existing-password); UNCONFIRMED → treat as FORCE_CHANGE_PASSWORD with a warning log; other statuses (ARCHIVED, COMPROMISED) → 422. Uses the AdminGetUser IAM permission that 11.E.1 explicitly granted for this purpose. AC2 + AC3 + AC11 items 5-8 reflect this branching logic.

---

_Story created via `bmad-create-story` skill on 2026-05-17. All 4 Open Questions PM-resolved the same day. Story re-authored in place to reflect: (Q#1) two-endpoint Variant B Cognito design with `temporaryPassword` field removed from `ProvisionUserResponse`; (Q#2) `de` + `en` scope only per new CLAUDE.md §Localization rule; (Q#3) HTML-only emails — `.txt` templates deleted; (Q#4) AdminGetUser status-driven branching at invitation time. Phase E continuation. Ready for `bmad-dev-story` execution._
