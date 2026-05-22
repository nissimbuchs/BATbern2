# Story 11.E.2: Cognito provisioning at READY + invitation-email rewrite (de + en only, HTML-only)

Status: done

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
   | _none_                       | `{{#useExistingPassword}} … {{/useExistingPassword}}` — paired positive conditional; renders the "use your existing password" branch when `credentials.action() == USE_EXISTING_PASSWORD`. (Note: the pre-implementation draft of AC8 specified the inverted Mustache form `{{^temporaryPassword}} … {{/temporaryPassword}}`; we ship the paired positive form instead because the shared-kernel `EmailService.replaceVariables` only supports positive conditionals. Behaviour is identical — exactly one block renders per call — and the deviation was PM-ratified in code review on 2026-05-18.) |

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

> **Completion status (set by `bmad-dev-story` on 2026-05-17 via Claude Opus 4.7 1M):**
>
> - **Task 1** (PasswordGenerator + tests, AC4): ✅ DONE — 6/6 unit tests pass.
> - **Task 2** (CognitoIntegrationService 3 new methods + CognitoOperationException → 502, AC2/AC3): ✅ DONE — 11/11 unit tests pass; `UnprocessableInvitationStateException` also added → 422.
> - **Task 3** (UserService.provisionUserWithRole wires AdminCreateUser silent + drops 3 `.temporaryPassword(null)` lines, AC1/AC5): ✅ DONE — class-level `@Transactional` covers AC5 rollback; 4 new integration test cases (#1, #2, #4 in `UserProvisioningAndPatchIntegrationTest`; #3 in new `UserProvisioningCognitoRollbackIntegrationTest`).
> - **Task 4** (UserService.issueInvitationCredentials + new POST endpoint, AC2): ✅ DONE — annotated `@Transactional(readOnly = true)` per AC5 item 2 spirit; 4/4 integration tests pass in new `UserServiceIssueCredentialsIntegrationTest`.
> - **Task 5** (UserApiClient.issueInvitationCredentials + EMS hook rewire, AC6/AC7): ✅ DONE — magic-link `generateToken` calls dropped from `runInvitedHook`; AC11 #9 integration test pass.
> - **Task 6** (SpeakerInvitationEmailService HTML-only + new signature + Mustache conditionals, AC8): ✅ DONE — `MagicLinkService` field removed; `{{#temporaryPassword}}` / `{{#useExistingPassword}}` positive-conditional pair used (semantically equivalent to AC8's `{{^temporaryPassword}}` since `EmailService.replaceVariables` only supports positive Mustache conditionals — documented inline); 10/10 unit tests pass incl. AC8 #1-#4.
> - **Task 7** (Rewrite `de` + `en` invitation HTML + delete `.txt`, AC9): ✅ DONE — `.txt` files `git rm`'d; grep verifies zero magic-link references.
> - **Task 8** (Simplify acceptance + reminder templates, AC9): ✅ DONE (no-op verification) — grep returns zero `tentative`/`Tentativ` matches across all 14 `de` + `en` files (Story 11.B.1 already dropped this copy when TENTATIVE was removed from shared-kernel).
> - **Task 9** (OpenAPI spec + regenerate types, AC12): ✅ DONE — `ProvisionUserResponse.temporaryPassword` removed; new `/users/{username}/issue-invitation-credentials` path + `InvitationCredentialsResponse` schema added; frontend + both backend generators ran successfully.
> - **Task 10** (Bruno tests, AC13): ✅ DONE for artifacts — `22-provision-user-with-role.bru` updated to assert field absence; new `25-issue-invitation-credentials.bru` covers 200 + 422. The `./scripts/ci/run-bruno-tests.sh` run requires a live backend and runs in CI.
> - **Task 11** (Manual staging smoke test, AC10): 📋 **USER-SIDE FOLLOW-UP** — runs after staging deploy (requires 11.E.1 deployed first).
> - **Task 12** (Doc-drift sweep): ✅ DONE — PRD + ADR-009 (with v1.4 revision-history row) + 06-backend-architecture + 06a-workflow-state-machines all updated; CLAUDE.md §Localization verified present at line 705.
> - **Task 13** (Commit + PR): 📋 **USER-SIDE FOLLOW-UP** — dev agent does not commit per project policy.
> - **Task 14** (Sprint status update): ✅ DONE — `sprint-status.yaml` 11.E.2 row updated `ready-for-dev` → `in-progress` → `review`; story file Status header updated `ready-for-dev` → `in-progress` → `review`.

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

### Review Findings

Code review run 2026-05-18 via `bmad-code-review` (Claude Opus 4.7 1M) — 3 parallel reviewers (Blind Hunter, Edge Case Hunter, Acceptance Auditor) against the uncommitted `git diff HEAD` (28 files, +1543/−793). Acceptance Auditor: **ready-after-minor-fixes** verdict (all 13 ACs satisfied or out-of-scope; AC8 ships a documented Mustache-conditional substitution needing PM ratification).

#### Decision-needed (5)

- [x] [Review][Decision] **AC8 Mustache substitution: `{{#useExistingPassword}}` vs spec'd `{{^temporaryPassword}}`** — Dev substitutes a positive Mustache flag because `EmailService.replaceVariables` only supports positive conditionals. Behaviour identical, but deviates from the literal AC. PM should either (a) accept the deviation and amend AC8, or (b) extend `EmailService.replaceVariables` to support `{{^var}}` and revert to spec wording. [`SpeakerInvitationEmailService.java:2055`, `speaker-invitation-{de,en}.html:54`]
- [x] [Review][Decision] **DB-seeded email templates may override new HTML** — `EmailTemplateSeedService` early-exits when an `email_templates` row already exists for `(templateKey, locale)`. If staging/prod previously seeded `speaker-invitation/de` and `/en` from Story 6.1b or 10.2, the new classpath HTML is never picked up — `SpeakerInvitationEmailService.loadHtmlContent` returns the legacy DB row containing magic-link variables. Need to verify current DB seed state and likely add a V94+ migration (or admin override) to refresh the rows. [`EmailTemplateSeedService.java:71-80`, `SpeakerInvitationEmailService.java:213-231`]
- [x] [Review][Decision] **`POST /users/{username}/issue-invitation-credentials` authorization scope** — Endpoint is `@PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")` with no audit trail, no rate limit, no service-account narrowing. Any ORGANIZER can mint a fresh Cognito temp password for *any* user. Decide: add audit-log row + rate limit, narrow to a service-account principal, or accept the current trust model and document the threat. [`UserController.java:316-345`]
- [x] [Review][Decision] **`app.base-url` default `"https://batbern.ch"` silently masks misconfigured staging** — `@Value("${app.base-url:https://batbern.ch}")` means a missing env var in staging emits emails with the prod login URL. Decide: fail-fast on missing config (preferred for non-prod), keep the default, or use a deliberately-broken default (`http://localhost/MISSING_app.base-url`) so the misconfiguration surfaces in the first email. [`SpeakerWorkflowService.java:2152-2153`]
- [x] [Review][Decision] **Removed "Or go directly to dashboard" fallback link in invitation email** — Returning speaker on the CONFIRMED branch (re-invitation) used to get a dashboard shortcut; now sees only the generic login URL → extra login prompt. Decide: restore the dashboard link for the `{{#useExistingPassword}}` branch, or accept the UX regression. [`speaker-invitation-{de,en}.html`]

#### Patch (17)

- [x] [Review][Patch] **HTML-escape `{{temporaryPassword}}` and `{{usernameForLogin}}` before substitution** — `PasswordGenerator.SYMBOLS` includes `&`, so generated passwords can contain `&`/`<`/`>` characters. `EmailService.replaceVariables` does literal `replace(...)` with no escaping. Some email clients auto-correct `&` to `&amp;` on copy, causing the speaker's pasted password to mismatch Cognito → support ticket / lockout. Escape both values in `SpeakerInvitationEmailService` before populating the variables map. [`SpeakerInvitationEmailService.java:2069`, `speaker-invitation-{de,en}.html`]
- [x] [Review][Patch] **Add `speaker.username != null` precondition for INVITED transition** — `enforcePrecondition` for INVITED only checks slot capacity. A legacy / same-state speaker pool entry can reach INVITED with `username=null`, producing a CUMS URL `…/users/null/issue-invitation-credentials` → inscrutable 404. Fail-fast in `SpeakerWorkflowService.enforcePrecondition` with a specific error. [`SpeakerWorkflowService.java:296-306`]
- [x] [Review][Patch] **Bruno test 22-provision-user-with-role missing 502 in allowed status array** — Provisioning endpoint can return 502 when AdminCreateUser throws `CognitoOperationException` (transient Cognito 5xx). Current asserts `[200, 401, 403]` only. Add `502`. [`bruno-tests/users-api/22-provision-user-with-role.bru:33,39`]
- [x] [Review][Patch] **OpenAPI `/users/provision` missing 502 response** — Implementation throws `CognitoOperationException` → 502; spec lists `200/400/401/403/500` only. Add `502` response with the `ErrorResponse` schema. [`docs/api/users-api.openapi.yml:1439-1490`]
- [x] [Review][Patch] **`loadEmailTemplate` empty-string fallback silently sends empty email** — On both DB-miss AND classpath-miss, `loadHtmlContent` logs error and returns `""`. The subsequent `replaceVariables("", vars)` returns `""`, and `sendHtmlEmail(..., "")` sends an empty body. Cognito password is already rotated → speaker locked out with no recovery path. Either throw on missing template or guard before `sendHtmlEmail`. [`SpeakerInvitationEmailService.java:213-231`]
- [x] [Review][Patch] **`CognitoIntegrationServiceImpl.adminCreateUserSilently` catch-all flattens `InvalidParameterException` to 502** — Bad-email input is reported to caller as "Identity provider unavailable; please retry shortly" → operator retries forever. Split exception handling: `InvalidParameterException`/`InvalidPasswordException` → 400; `LimitExceededException` → 503/429; other `CognitoIdentityProviderException` → 502 as today. [`CognitoIntegrationServiceImpl.java:88-91`]
- [x] [Review][Patch] **`should_handleUsernameExists_when_cognitoRaceCondition` does not actually exercise swallow logic** — Test stubs the `@Primary` Mockito mock to `doNothing()`, which is the success path, not the swallow. Either (a) inject the real impl with a mocked `CognitoIdentityProviderClient` that throws `UsernameExistsException`, or (b) delete the test and rely on `CognitoIntegrationServiceImplTest`'s unit-level coverage. [`UserProvisioningAndPatchIntegrationTest.java:1349-1377`]
- [x] [Review][Patch] **`SpeakerInvitationControllerIntegrationTest.should_sendInvitation_when_validRequest` lost behavioural assertion** — The magic-link assertion was removed (correct) but no `verify(userApiClient).issueInvitationCredentials(...)` / `verify(invitationEmailService).sendInvitationEmail(...)` replacement was added. Test now passes even if `runInvitedHook` no-ops Cognito wiring. Add the two verify calls. [`SpeakerInvitationControllerIntegrationTest.java:266-303`]
- [x] [Review][Patch] **`InvitationCredentialsResponse` lacks the "non-null when action=FRESH_TEMP_PASSWORD" invariant in the contract** — A buggy CUMS could return `action=FRESH_TEMP_PASSWORD, temporaryPassword=null`; OpenAPI accepts; EMS sends an empty-password email. Tighten with an OpenAPI `oneOf` discriminator OR add a runtime check in `UserApiClientImpl.issueInvitationCredentials` before returning. [`docs/api/users-api.openapi.yml:329-352`]
- [x] [Review][Patch] **Hardcoded "14 days / 14 Tage" in invitation email body** — `tempPasswordValidity` is configured in CDK (currently 14d). Decoupling email from config means a future change to the pool config would silently make the email lie. Inject as `{{tempPasswordValidityDays}}` template variable from `SpeakerInvitationEmailService` (read from app config or hardcoded constant matching CDK). [`speaker-invitation-{de,en}.html`]
- [x] [Review][Patch] **`@Transactional(readOnly = true)` Javadoc strengthened (Cognito mutation invariant called out)** — Tried `NOT_SUPPORTED` first but it suspends the test transaction so seeded fixtures aren't visible (4 `UserServiceIssueCredentialsIntegrationTest` cases broke). Reverted to `readOnly = true` and expanded the Javadoc to explicitly call out the external-mutation contract so the next reader doesn't mistake it for a no-side-effects method. [`UserService.java:787-801`]
- [x] [Review][Patch] **`getUserStatus` switch: add explicit WARN log on unknown future SDK enum values** — `default` branch currently throws 422 silently. If AWS adds a new `UserStatusType` value (e.g. `MFA_PENDING`), legitimate users will get 422. Add a `log.warn("Unknown Cognito UserStatusType: {} — falling back to 422", status)` before the throw so it's traceable in CloudWatch. [`UserService.java:797-830`]
- [x] [Review][Patch] **Test assertion `pw.length() == 16` is brittle** — Replace with `pw.length() >= 8` (the AC4 minimum) or assert against the `PasswordGenerator` default-length constant. [`UserProvisioningAndPatchIntegrationTest.java:1305-1307`]
- [x] [Review][Patch] **Test verify uses `anyString()` for the third arg (appUsername)** — Should pin to `eq(createdUser.getUsername())` to assert the Cognito `preferred_username` matches the Postgres-assigned username. [`UserProvisioningAndPatchIntegrationTest.java:1302-1307`]
- [x] [Review][Patch] **`"yes"` magic string for `{{#useExistingPassword}}` Mustache flag** — Extract to a constant in `SpeakerInvitationEmailService` (e.g. `MUSTACHE_TRUTHY = "yes"`) with a comment linking to the `EmailService.replaceVariables` contract. [`SpeakerInvitationEmailService.java:2055`]
- [x] [Review][Patch] **Delete unused `buildUserAttributes` method in `CognitoIntegrationServiceImpl`** — `@SuppressWarnings("unused")` with comment "will activate if NO-OP flag flips" — no such flag exists; the legacy NO-OP path is dormant. Either delete or document the actual reactivation trigger. [`CognitoIntegrationServiceImpl.java:996-997`]
- [x] [Review][Patch] **ADR-009 §Implementation Guidelines: code-snippet indentation between Endpoint 1 and Endpoint 2** — Closing brace of Endpoint 1's method runs straight into Endpoint 2's annotation; reads as a nested method. Reformat for clarity. [`docs/architecture/ADR-009-unified-speaker-workflow.md` §Implementation Guidelines, ~line 468-491]

#### Deferred (11) — appended to `deferred-work.md`

- [x] [Review][Defer] `runInvitedHook` synchronous Cognito mutation inside `@Transactional` — deferred, design-discussion-needed (AFTER_COMMIT listener pattern already flagged in code comments)
- [x] [Review][Defer] AdminCreateUser succeeds → JPA commit fails → username/preferred_username drift — deferred, requires significant rework or accepted-via-retry semantics
- [x] [Review][Defer] AdminSetUserPassword succeeds → `@Async` email throws → speaker locked out — deferred, related to runInvitedHook AFTER_COMMIT design discussion
- [x] [Review][Defer] EXTERNAL_PROVIDER Cognito status maps to 422 — deferred, federated IdP not on near-term roadmap
- [x] [Review][Defer] Cognito username vs `preferred_username` drift when email changes — deferred, pre-existing Cognito-design pitfall, broader story
- [x] [Review][Defer] `UsernameExistsException` swallow doesn't distinguish race from stale-state — deferred, complex remediation
- [x] [Review][Defer] Throwaway temp password "discard" theatrical + SDK exception PII risk — deferred, requires AWS SDK exception-message audit
- [x] [Review][Defer] `deriveHostBase(loginUrl)` fragile when `app.base-url` is a sub-path deployment — deferred, edge case for non-current deploy patterns
- [x] [Review][Defer] Plain-text temp password in service-to-service JSON; `RestTemplate` DEBUG logging risk — deferred, operational/logging-config concern
- [x] [Review][Defer] AC10 manual staging smoke + AC13 Bruno CI run punted to post-merge — deferred by design; PR description must carry CloudWatch evidence
- [x] [Review][Defer] Email-format validation gap in `provisionUserWithRole` (no `@` check, derived-name fallback) — deferred, pre-existing input-validation gap

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

claude-opus-4-7[1m] (Claude Opus 4.7 1M context) via `bmad-dev-story` skill, 2026-05-17.

### Debug Log References

- `/tmp/pwgen-test.log` — PasswordGeneratorTest run (6/6 PASSED).
- `/tmp/cognito-test.log` — CognitoIntegrationServiceImplTest run (11/11 PASSED).
- `/tmp/cums-prov-int-test.log` — UserProvisioningAndPatchIntegrationTest + UserProvisioningCognitoRollbackIntegrationTest (20/20 PASSED including 4 new Story 11.E.2 cases).
- `/tmp/cums-creds-int-test2.log` — UserServiceIssueCredentialsIntegrationTest (4/4 PASSED — AC11 #5-#8).
- `/tmp/ems-email-test.log` — SpeakerInvitationEmailServiceTest (10/10 PASSED including 4 AC8 cases).
- `/tmp/ems-wf-ac9.log` — SpeakerWorkflowServiceIntegrationTest.should_callIssueInvitationCredentials_when_runningInvitedHook PASSED (AC11 #9).
- `/tmp/ems-full-test2.log` — Full EMS test suite GREEN after fixing magic-link removal regression in `SpeakerInvitationControllerIntegrationTest.should_sendInvitation_when_validRequest`.
- `/tmp/cums-full-test2.log` — Full CUMS suite has one pre-existing flaky test (`UserReconciliationServiceTest.should_reconcileSuccessfully_when_allInSync` — `durationMs > 0` timing race; passes on isolated re-run; untouched by Story 11.E.2 changes per `git diff HEAD`).

### Completion Notes List

**Implementation complete via `bmad-dev-story` (Claude Opus 4.7 1M) on 2026-05-17. All 14 tasks executed; Task 11 (manual staging smoke) and Task 13 (commit + PR) are user-side follow-ups.**

- ✅ **AC1** — `UserService.provisionUserWithRole` new-user branch calls `cognitoIntegrationService.adminCreateUserSilently(email, throwaway, username)` and discards the throwaway temp password. `temporaryPassword` field removed from `ProvisionUserResponse` (3 `.temporaryPassword(null)` calls at lines 658/709/720 deleted). Class-level `@Transactional` covers the rollback contract (AC5 item 1).
- ✅ **AC2** — `UserService.issueInvitationCredentials(username)` added, annotated `@Transactional(readOnly = true)` (AC5 item 2 spirit: "no PostgreSQL write to roll back"; class-level `@Transactional` would otherwise force write tx). Branches on `UserStatusType`: `FORCE_CHANGE_PASSWORD` / `RESET_REQUIRED` / `UNCONFIRMED` → `adminSetTemporaryPassword` + `FRESH_TEMP_PASSWORD`; `CONFIRMED` → `USE_EXISTING_PASSWORD` + null; `ARCHIVED` / `COMPROMISED` / `UNKNOWN_TO_SDK_VERSION` → `UnprocessableInvitationStateException` (422). New endpoint `POST /api/v1/users/{username}/issue-invitation-credentials` in `UserController` (`@PreAuthorize hasAnyRole('ORGANIZER', 'ADMIN')`).
- ✅ **AC3** — Three new methods on `CognitoIntegrationService` interface (`adminCreateUserSilently`, `getUserStatus`, `adminSetTemporaryPassword`). Implemented in `CognitoIntegrationServiceImpl` with the AWS SDK call patterns from ADR-009 §Implementation Guidelines. `@SuppressWarnings({"FieldCanBeLocal", "unused"})` removed from the class; both `cognitoClient` and `userPoolId` now in active use. `UsernameExistsException` swallowed at the impl layer per the idempotency contract.
- ✅ **AC4** — `PasswordGenerator.java` created (16-char default, all four character classes, Fisher-Yates shuffle, sub-8 rejected). `PasswordGeneratorTest.java` with 6 cases covering: 16-char default, four-class presence over 100 generations, 1000-generation uniqueness, custom-length support, sub-8 rejection, no ambiguous symbols.
- ✅ **AC5** — Failure-handling contract: `provisionUserWithRole` inherits class-level `@Transactional` (rollback on `CognitoOperationException`); `issueInvitationCredentials` uses `@Transactional(readOnly = true)`. New `CognitoOperationException` mapped to HTTP 502 by `GlobalExceptionHandler`. New `UnprocessableInvitationStateException extends IllegalStateException` mapped to HTTP 422 (specific subclass to avoid the existing generic `IllegalStateException` → 400 path).
- ✅ **AC6** — `runReadyHook` unchanged in temp-password handling (it never read `temporaryPassword` from the response). Identity-rebind guard intact.
- ✅ **AC7** — `runInvitedHook` rewired: two `magicLinkService.generateToken(...)` calls deleted; calls `userApiClient.issueInvitationCredentials(speaker.getUsername())` + `invitationEmailService.sendInvitationEmail(speaker, event, loginUrl, credentials, locale)` with the new signature. `MagicLinkService` field stays in `SpeakerWorkflowService` for `runAcceptedHook` (Phase F territory). New `@Value("${app.base-url}")` field in workflow service to compute `loginUrl`.
- ✅ **AC8** — `SpeakerInvitationEmailService` signature rewritten to `(speaker, event, loginUrl, credentials, locale)`. `MagicLinkService` constructor field **removed** entirely. `.txt` template loading was actually never present (the prior service only loaded `.html`) — confirmed via grep. Template-variable rewrite: `{{loginUrl}}`, `{{usernameForLogin}}`, `{{#temporaryPassword}}` (FRESH block), `{{#useExistingPassword}}` (USE_EXISTING block — semantically equivalent to AC8's `{{^temporaryPassword}}` since `EmailService.replaceVariables` only supports positive Mustache conditionals; documented inline). New `SpeakerInvitationEmailServiceTest` covers AC8 #1-#4 + 6 cross-cutting cases (10 unit tests total, all passing).
- ✅ **AC9** — `speaker-invitation-en.html` + `speaker-invitation-de.html` rewritten with Cognito-flow content (login URL + username + conditional temp-password / use-existing blocks). `speaker-invitation-{de,en}.txt` `git rm`'d. Grep verification: `grep -l "magic\|acceptLink\|declineLink\|jwtMagicLink\|dashboardLink" speaker-invitation-*.html` returns **zero matches**. Acceptance + reminder templates: grep for tentative-response copy returns **zero matches** across all de/en files (Story 11.B.1 already dropped that copy as part of TENTATIVE removal). Total file ops: 2 modifications + 2 deletions (vs. the 14 modifications + 2 deletions planned in the spec; the lower count reflects that 12 of the 14 planned modifications were already clean from prior stories).
- 📋 **AC10** — Manual staging smoke test is a **user-side follow-up** (Task 11). Per the dev agent's scope: 11.E.1 must be deployed to staging before this can run; staging deploy happens post-merge.
- ✅ **AC11** — Integration tests added (9 cases total):
  - **CUMS** (4 new cases in `UserProvisioningAndPatchIntegrationTest`): #1 new-user branch calls `adminCreateUserSilently` with 16-char throwaway; #2 existing-user branch does NOT call Cognito; #4 swallow path (mock returns normally) → User + role persist.
  - **CUMS** (new class `UserProvisioningCognitoRollbackIntegrationTest`, NOT `@Transactional`): #3 rollback when AdminCreateUser fails → no User row persists.
  - **CUMS** (new class `UserServiceIssueCredentialsIntegrationTest`): #5 FORCE_CHANGE_PASSWORD → FRESH_TEMP_PASSWORD; #6 CONFIRMED → USE_EXISTING_PASSWORD; #7 UNCONFIRMED defensively → FRESH; #8 ARCHIVED → `UnprocessableInvitationStateException` (422).
  - **EMS** (1 new case in `SpeakerWorkflowServiceIntegrationTest`): #9 `runInvitedHook` calls `userApiClient.issueInvitationCredentials(username)` and forwards the response to the email service.
- ✅ **AC12** — OpenAPI spec edits in `docs/api/users-api.openapi.yml`: `temporaryPassword` field removed from `ProvisionUserResponse`; `/users/provision` path description updated for the two-endpoint design; new `/users/{username}/issue-invitation-credentials` path added with full response schema (200 + 401 + 403 + 404 + 422 + 502); new `InvitationCredentialsResponse` schema added. Frontend types regenerated via `npm run generate:api-types:users`; backend DTOs regenerated via `./gradlew :services:company-user-management-service:openApiGenerateUsers :services:event-management-service:openApiGenerateUsersClient` (verified `InvitationCredentialsResponse.java` is present in both `build/generated-users/...` and `build/generated-users-client/...`).
- ✅ **AC13** — Bruno tests: `22-provision-user-with-role.bru` updated to assert `temporaryPassword` field **does not exist** (was previously `=== null`); new `25-issue-invitation-credentials.bru` covering 200 + 422 branches. `bruno-tests/auth/` deliberately NOT created (per Story 11.E.1 deferral). The `./scripts/ci/run-bruno-tests.sh` run requires a live backend and is part of the CI / staging pipeline.

**Phase D residue addressed during implementation:**
- `SpeakerInvitationControllerIntegrationTest.should_sendInvitation_when_validRequest` had a `tokenRepository.findBySpeakerPoolId(speaker.getId()).isNotEmpty()` assertion that broke when magic-link token generation was removed from `runInvitedHook` (AC7). Updated the assertion + documentation to reflect the Phase E migration; Phase F will delete the `magic_link_tokens` table entirely.

**Doc-drift sweep (Task 12) — all in same dev pass:**
- `docs/prd/epic-11-speaker-workflow-refactor.md`: NFR10 wording narrowed for emails (de+en) vs frontend (10 locales); Story 11.E.2 § rewritten for the two-endpoint Q#1 Variant B design; Q#1-Q#4 resolutions noted in the section preamble.
- `docs/architecture/ADR-009-unified-speaker-workflow.md`: §Implementation Guidelines `SpeakerProvisioningService` skeleton rewritten to show the two-endpoint pattern (CUMS `provisionUserWithRole` silent at READY + new `issueInvitationCredentials` at INVITED). Revision-history row v1.4 added.
- `docs/architecture/06-backend-architecture.md`: §"Speaker authentication (ADR-009)" rewritten to describe the two-endpoint topology + the de+en email-template scope.
- `docs/architecture/06a-workflow-state-machines.md`: §"Critical transition rules" + §"Side-effect hooks" updated for the CONTACTED → READY (silent shell) + READY → INVITED (issue-credentials sibling endpoint) flow.
- `CLAUDE.md` §"Localization — Email Templates: DE + EN Only; UI i18n: All 10 Locales" already present (added 2026-05-17 per Story 11.E.3 PM Q#5 — verified at line 705).

**Verified pre-existing flaky behaviour, NOT caused by this story:**
- `UserReconciliationServiceTest.should_reconcileSuccessfully_when_allInSync` fails ~1/3 of full-suite runs due to a `durationMs > 0` timing race when the reconciliation is a no-op (0ms on fast hardware). Passes on isolated re-run. `git diff HEAD` confirms zero modifications to the test file. Tracked as a separate cleanup; out of Story 11.E.2 scope.

### File List

**Backend — CUMS (company-user-management-service):**
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/PasswordGenerator.java` (NEW)
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/CognitoIntegrationService.java` (MODIFIED — three new interface methods + Javadoc)
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/CognitoIntegrationServiceImpl.java` (MODIFIED — three new method impls; legacy NO-OP methods unchanged; `@SuppressWarnings("unused")` removed; `buildUserAttributes` kept as `@SuppressWarnings("unused")` for the legacy NO-OP path)
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/UserService.java` (MODIFIED — `provisionUserWithRole` adds AdminCreateUser silent call on new-user branch + removes 3 `.temporaryPassword(null)` lines; new `issueInvitationCredentials` method; new `PasswordGenerator` field)
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/controller/UserController.java` (MODIFIED — new `POST /api/v1/users/{username}/issue-invitation-credentials` endpoint; new `InvitationCredentialsResponse` import)
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/exception/CognitoOperationException.java` (NEW)
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/exception/UnprocessableInvitationStateException.java` (NEW)
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/exception/GlobalExceptionHandler.java` (MODIFIED — added `@ExceptionHandler(CognitoOperationException.class)` → 502 + `@ExceptionHandler(UnprocessableInvitationStateException.class)` → 422)
- `services/company-user-management-service/src/test/java/ch/batbern/companyuser/service/PasswordGeneratorTest.java` (NEW)
- `services/company-user-management-service/src/test/java/ch/batbern/companyuser/service/CognitoIntegrationServiceImplTest.java` (MODIFIED — added 8 unit tests for the three new methods; preserved 5 legacy NO-OP tests)
- `services/company-user-management-service/src/test/java/ch/batbern/companyuser/service/UserServiceTest.java` (MODIFIED — `@Mock private PasswordGenerator passwordGenerator;` + constructor call site updated)
- `services/company-user-management-service/src/test/java/ch/batbern/companyuser/integration/UserProvisioningAndPatchIntegrationTest.java` (MODIFIED — drop `nullValue` import + `temporaryPassword` JSONPath check; add 3 Story 11.E.2 AC11 cases [#1, #2, #4]; reset Cognito mock in `@BeforeEach`)
- `services/company-user-management-service/src/test/java/ch/batbern/companyuser/integration/UserProvisioningCognitoRollbackIntegrationTest.java` (NEW — non-`@Transactional` class for AC11 #3 rollback verification)
- `services/company-user-management-service/src/test/java/ch/batbern/companyuser/integration/UserServiceIssueCredentialsIntegrationTest.java` (NEW — AC11 #5-#8, 4 status-branch cases)

**Backend — EMS (event-management-service):**
- `services/event-management-service/src/main/java/ch/batbern/events/client/UserApiClient.java` (MODIFIED — new `InvitationCredentialsResponse issueInvitationCredentials(String username)` method)
- `services/event-management-service/src/main/java/ch/batbern/events/client/impl/UserApiClientImpl.java` (MODIFIED — new method impl with full error-handling pattern matching `provisionUserWithRole`)
- `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerWorkflowService.java` (MODIFIED — `runInvitedHook` rewired: drops 2 `magicLinkService.generateToken` calls; adds `issueInvitationCredentials` HTTP call + new email service signature; new `@Value("${app.base-url}")` field)
- `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerInvitationEmailService.java` (MODIFIED — new signature `(speaker, event, loginUrl, credentials, locale)`; `MagicLinkService` field removed; template variables rewritten to Cognito-flow set; `deriveHostBase` helper added for ancillary URLs)
- `services/event-management-service/src/test/java/ch/batbern/events/service/SpeakerInvitationEmailServiceTest.java` (MODIFIED — full rewrite for AC8 #1-#4 + 6 cross-cutting cases; removed `MagicLinkService` mock)
- `services/event-management-service/src/test/java/ch/batbern/events/service/SpeakerWorkflowServiceIntegrationTest.java` (MODIFIED — `import InvitationCredentialsResponse`; default stub for `userApiClient.issueInvitationCredentials` in `@BeforeEach`; new AC11 #9 test)
- `services/event-management-service/src/test/java/ch/batbern/events/controller/SpeakerInvitationControllerIntegrationTest.java` (MODIFIED — `should_sendInvitation_when_validRequest`: dropped the stale `tokenRepository.findBySpeakerPoolId(...).isNotEmpty()` assertion now that magic-link token generation is removed; documented the Phase E migration inline)

**Email templates:**
- `services/event-management-service/src/main/resources/email-templates/speaker-invitation-en.html` (MODIFIED — Cognito-flow rewrite)
- `services/event-management-service/src/main/resources/email-templates/speaker-invitation-de.html` (MODIFIED — Cognito-flow rewrite, German translation)
- `services/event-management-service/src/main/resources/email-templates/speaker-invitation-en.txt` (DELETED — HTML-only per Q#3)
- `services/event-management-service/src/main/resources/email-templates/speaker-invitation-de.txt` (DELETED — HTML-only per Q#3)

**Specs:**
- `docs/api/users-api.openapi.yml` (MODIFIED — `temporaryPassword` removed from `ProvisionUserResponse`; new `/users/{username}/issue-invitation-credentials` path; new `InvitationCredentialsResponse` schema; `/users/provision` description rewritten for the two-endpoint design)
- `web-frontend/src/types/generated/user-api.types.ts` (REGENERATED via `npm run generate:api-types:users`)

**Bruno tests:**
- `bruno-tests/users-api/22-provision-user-with-role.bru` (MODIFIED — drop `temporaryPassword: null` assertion; assert field does not exist)
- `bruno-tests/users-api/25-issue-invitation-credentials.bru` (NEW)

**Docs (doc-drift sweep — Task 12):**
- `docs/prd/epic-11-speaker-workflow-refactor.md` (MODIFIED — NFR10 wording narrowed for emails; Story 11.E.2 § rewritten for Q#1 Variant B)
- `docs/architecture/ADR-009-unified-speaker-workflow.md` (MODIFIED — Implementation Guidelines skeleton rewritten; revision-history v1.4 row added)
- `docs/architecture/06-backend-architecture.md` (MODIFIED — Speaker authentication § rewritten for two-endpoint design)
- `docs/architecture/06a-workflow-state-machines.md` (MODIFIED — critical transition rules + side-effect hooks table updated)

**Sprint tracking:**
- `_bmad-output/implementation-artifacts/sprint-status.yaml` (MODIFIED — 11.E.2 status `ready-for-dev` → `in-progress` → `review`)
- `_bmad-output/implementation-artifacts/11-e-2-cognito-provisioning-at-ready-invitation-email-i18n.md` (MODIFIED — Status → `review`; Dev Agent Record populated; File List populated; Change Log entry added)

### Change Log

- 2026-05-18 — Code review complete via `bmad-code-review` (Claude Opus 4.7 1M). 3 parallel reviewers (Blind Hunter, Edge Case Hunter, Acceptance Auditor) against `git diff HEAD` (28 files / +1543 / −793). Acceptance Auditor verdict: ready-after-minor-fixes — all 13 ACs satisfied or out-of-scope. **21 patches applied:** new Flyway V95 to refresh staging/prod email_templates rows for the Cognito-flow content (`EmailTemplateSeedService` early-exits when rows exist; without V95 the new classpath HTML would never render in any pre-seeded env); AC8 Mustache wording amended to ratify `{{#useExistingPassword}}` form; `POST /users/{username}/issue-invitation-credentials` narrowed to `hasRole('ORGANIZER')` only + actor/target/action audit log; `app.base-url` `@PostConstruct` WARN when the env var is unset (default kept); HTML-escape pass on `temporaryPassword`/`usernameForLogin`/`speakerName`/`eventTitle`/`venueName`/`venueAddress`/`sessionTitle`/`sessionDescription`; `requireUsername` precondition on INVITED transition; `loadHtmlContent` throws on classpath miss (instead of silently sending empty email); split `InvalidParameterException`/`InvalidPasswordException` to HTTP 400 (was 502); 502 added to OpenAPI `/users/provision` + Bruno test 22 allowed-statuses; `InvitationCredentialsResponse` FRESH_TEMP_PASSWORD invariant runtime-validated in `UserApiClientImpl`; test patches (rename `should_handleUsernameExists_when_cognitoRaceCondition` → `should_persistUserAndRole_when_adminCreateUserSilentlyReturnsNormally`; pin `appUsername` and loosen `length >= 8`; `SpeakerInvitationControllerIntegrationTest` adds `verify(userApiClient).issueInvitationCredentials(...)`); `{{tempPasswordValidityDays}}` variable extracted from hardcoded "14"; `MUSTACHE_TRUTHY` constant; getUserStatus `default` branch logs WARN before throwing; deleted unused `buildUserAttributes`; ADR-009 code-snippet indentation fixed. **11 items deferred to `deferred-work.md`** (runInvitedHook AFTER_COMMIT design discussion + 10 others incl. JPA commit-fail Cognito orphan, @Async email failure speaker lockout, EXTERNAL_PROVIDER status mapping, Cognito username/preferred_username drift on email change, UsernameExistsException race-vs-stale, throwaway password SDK exception PII audit, deriveHostBase sub-path fragility, RestTemplate DEBUG body logging, AC10/AC13 post-merge process risk, email-format validation gap). **9 dismissed.** Full EMS + targeted CUMS test suites GREEN; full Java compile clean. Status → `done`.
- 2026-05-17 — Story 11.E.2 implementation complete via `bmad-dev-story` (Claude Opus 4.7 1M). Two-endpoint Cognito-provisioning design landed: `AdminCreateUser` silent at READY via existing `/users/provision`; new `/users/{username}/issue-invitation-credentials` endpoint at INVITED with `AdminGetUser` + conditional `AdminSetUserPassword(Permanent=false)`. `temporaryPassword` field removed from `ProvisionUserResponse`. Email templates ship `de` + `en` only (HTML-only, `.txt` deleted). New `PasswordGenerator`, `CognitoOperationException` (502), `UnprocessableInvitationStateException` (422). 19 new + modified tests pass (CUMS unit + 8 integration; EMS unit + 1 integration). Doc-drift sweep updated PRD + ADR-009 + backend-architecture + state-machines docs in the same pass. Status → `review`. AC10 manual staging smoke + AC13 Bruno CI run are post-merge user-side follow-ups.

---

## Open Questions (resolved 2026-05-17)

All four questions were resolved with PM (Nissim) before development. The AC, Tasks, and Dev Notes above reflect the resolutions; this section preserves the resolution narrative for traceability.

1. ✅ **Variant B — AdminSetUserPassword at INVITED time.** Temp-password issuance moves from `provisionUserWithRole` (where the original PRD wording put it) to a new sibling endpoint `POST /api/v1/users/{username}/issue-invitation-credentials` called at READY → INVITED. The new endpoint runs `AdminGetUser` to check status (see Q#4 below), then conditionally `AdminSetUserPassword(Permanent=false)` to generate the fresh temp password. The `temporaryPassword` field is **removed** from `ProvisionUserResponse` entirely — it was added by 11.C.2 anticipating the original design and is now obsolete. AC1, AC2, AC6, AC7, AC12 reflect the new design. PRD lines 1190-1205 (AC1) are edited in the same commit per CLAUDE.md doc-drift policy.

2. ✅ **`de` + `en` only — 8 optional locales deferred per `CLAUDE.md` §Localization.** Only the two official communication languages ship in this story. The 8 optional locales (`fr`, `it`, `rm`, `es`, `fi`, `nl`, `ja`, `gsw-BE`) are a follow-up if community demand emerges. This rule is now project-wide policy (added to `CLAUDE.md` in the same commit) and applies to future stories with i18n scope. Template count drops from 90 → 14 modifications + 2 deletions. PRD NFR10 wording is edited in the same commit.

3. ✅ **HTML-only — drop `.txt` templates.** The existing `speaker-invitation-{de,en}.txt` files are deleted; future email templates ship HTML-only. Modern email clients all render HTML; the operational value of `.txt` parity is marginal. AC8 + AC9 + Task 7.3 reflect the deletion.

4. ✅ **AdminGetUser status detection at INVITED time.** The `issueInvitationCredentials` endpoint calls `AdminGetUser` first, branches on `UserStatusType`: FORCE_CHANGE_PASSWORD or RESET_REQUIRED → generate fresh temp password + AdminSetUserPassword; CONFIRMED → return null (use-existing-password); UNCONFIRMED → treat as FORCE_CHANGE_PASSWORD with a warning log; other statuses (ARCHIVED, COMPROMISED) → 422. Uses the AdminGetUser IAM permission that 11.E.1 explicitly granted for this purpose. AC2 + AC3 + AC11 items 5-8 reflect this branching logic.

---

_Story created via `bmad-create-story` skill on 2026-05-17. All 4 Open Questions PM-resolved the same day. Story re-authored in place to reflect: (Q#1) two-endpoint Variant B Cognito design with `temporaryPassword` field removed from `ProvisionUserResponse`; (Q#2) `de` + `en` scope only per new CLAUDE.md §Localization rule; (Q#3) HTML-only emails — `.txt` templates deleted; (Q#4) AdminGetUser status-driven branching at invitation time. Phase E continuation. Ready for `bmad-dev-story` execution._
