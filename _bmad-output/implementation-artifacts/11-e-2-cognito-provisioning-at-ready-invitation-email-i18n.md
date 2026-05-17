# Story 11.E.2: Cognito provisioning at READY + invitation-email rewrite + 10-locale i18n

Status: ready-for-dev

<!-- Validation is optional — run validate-create-story for quality check before dev-story. -->

## Story

**As a** speaker who has just been promoted from "lead" to "real invitee",
**I want** to receive a clear invitation email with my login link and a temporary password I can change on first login,
**So that** I can access the speaker portal with a standard Cognito experience — no magic links, no parallel auth, no tokens to juggle.

## Phase / Dependencies / Requirements Covered

- **Phase:** E — Cognito with forced password change (second of three E stories). This is the substantive backend + email-template story; Story 11.E.1 (CDK + IAM prereq, status: `ready-for-dev` at story-creation time) lands the infrastructure underneath, and Story 11.E.3 wires the frontend session.
- **Depends on:**
  1. **Story 11.E.1** (hard infrastructure prereq). 11.E.1 grants `AdminCreateUser`, `AdminSetUserPassword`, `AdminInitiateAuth`, `AdminGetUser` to the CUMS task role and enables `ALLOW_ADMIN_USER_PASSWORD_AUTH` on the App Client. Without 11.E.1, the Cognito SDK calls in this story will return `AccessDeniedException` at runtime. **11.E.1 must be merged AND deployed to staging before this story's manual verification step (AC10) can run.**
  2. **Story 11.B.2** (status: `done`). Provides the `SpeakerWorkflowService.transition()` seam — `runReadyHook` (provisioning) and `runInvitedHook` (invitation email) — that this story extends. Verified in `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerWorkflowService.java` lines 250-305.
  3. **Story 11.C.2** (status: `done`). Provides the `UserApiClient.provisionUserWithRole(ProvisionUserRequest) → ProvisionUserResponse` contract and the matching `POST /api/v1/users/provision` endpoint in CUMS. The `ProvisionUserResponse.temporaryPassword` field exists today and is always `null`; this story populates it. Verified in `services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/UserService.java` lines 620-721 — the four explicit `temporaryPassword(null);  // Story 11.E.2 wires Cognito` comments mark the exact lines to edit.
- **Unblocks:** Story 11.E.3 (speaker-portal Cognito auth — frontend half of Phase E; consumes the standard Cognito users provisioned here) and ultimately Story 11.F.1 (magic-link teardown — Phase F; depends on Phase E being stable in production for ≥ 1 week per sprint-status.yaml line 195).
- **Requirements covered (PRD lines 1176-1249):**
  - **FR3** (Cognito part) — CONTACTED → READY triggers Cognito provisioning with FORCE_CHANGE_PASSWORD.
  - **FR9** — Invitation email carries a portal login link + a generated temporary password (no Custom Auth Lambdas, no OTP).
  - **AR15** — Cognito provisioning logic added to CUMS — `AdminCreateUser` with FORCE_CHANGE_PASSWORD.
  - **UX-DR21** — Invitation/confirmation/reminder/escalation email templates rewritten; tentative-response language removed.
  - **UX-DR22** — i18n — 10 locales (de, en, fr, it, rm, es, fi, nl, ja, gsw-BE).
  - **NFR2** — No new Cognito Lambda triggers required. SPEAKER role granted via PostgreSQL `user_roles` row insert per ADR-001, NOT via `AdminAddUserToGroup`.
  - **NFR3** — Idempotency. Re-running provisioning for an already-provisioned speaker is a no-op (returns `temporaryPassword = null`).
  - **NFR5** — Least-privilege. The only Cognito SDK calls made are `AdminCreateUser` and (depending on Open Q#1 resolution) `AdminSetUserPassword` — both already granted by 11.E.1.
  - **NFR9** — Cognito User Pool password policy accepts the backend-generated temporary password — the generator (≥ 16 chars, all four character classes) satisfies the policy (≥ 8 chars, lowercase + uppercase + digit + symbol) with margin.
  - **NFR10** — 10-locale parity. No locale lags behind.
- **Plan / ADR anchors:**
  - Epic 11 PRD §"Story 11.E.2" lines 1176-1249 — the AC source-of-truth.
  - `docs/plans/speaker-workflow-refactor.md` §0.5 ("Authentication"), §2.3 ("State-machine consolidation" — provisioning side-effect hook), §3.2 ("`company-user-management-service`" — Cognito provisioning logic).
  - `docs/architecture/ADR-009-unified-speaker-workflow.md` §"Decision 3" (lines 254-298) — Cognito provisioning skeleton with `cognitoClient.adminCreateUser` shape; §"Implementation Guidelines" lines 488-515 (`SpeakerProvisioningService.provisionForSpeaker` reference design).
  - Revision-history row in ADR-009 dated 2026-05-17 (1.3) — confirms `AdminAddUserToGroup` is dropped; the SPEAKER role grant is a PostgreSQL `user_roles` insert. This story's IAM call list aligns with that decision (four actions, not five).

---

## Branch state at story start

- **Current branch:** `feature/speaker-workflow-refactor`.
- **Verified at story-creation time (2026-05-17, after reading the source files):**
  - `services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/UserService.java`:
    - Lines 620-721: `provisionUserWithRole(ProvisionUserRequest)` — three explicit `.temporaryPassword(null);` markers at lines 658, 709, 720, each with a comment ending `// Story 11.E.2 wires Cognito` or `// Story 11.E.2 wires Cognito (AdminCreateUser + AdminSetUserPassword)`. The three markers correspond to: (a) existing-user idempotent path (line 658), (b) DataIntegrityViolation race-loser path (line 709), (c) new-user create path (line 720). Only (c) needs a real temp password in 11.E.2; (a) and (b) stay null per AC2.
  - `services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/CognitoIntegrationServiceImpl.java`:
    - Both methods (`syncUserAttributes`, `createCognitoUser`) are documented NO-OPs (lines 38-51). The class injects `CognitoIdentityProviderClient cognitoClient` and `String userPoolId` (constructor lines 31-36) — both fields are flagged `@SuppressWarnings({"FieldCanBeLocal", "unused"})` because the class doesn't currently call Cognito. This story removes that `unused` suppression by adding real call sites.
    - There is a private `buildUserAttributes(...)` helper (lines 62-106) that builds `email`, `given_name`, `family_name`, `custom:companyId`, `custom:role` attributes — reusable for `AdminCreateUser` (mostly; see Task 1 for what gets reused vs. replaced).
  - `services/company-user-management-service/src/main/java/ch/batbern/companyuser/config/CognitoConfig.java`:
    - Single `@Bean` `cognitoIdentityProviderClient()` (lines 26-31), `@Profile("!test")` — uses default credentials provider chain (IAM roles in ECS). Test profile uses the mock from `TestAwsConfig`. **No change needed in this story.**
  - `services/company-user-management-service/src/main/resources/application.yml`:
    - Line 69: `user-pool-id: ${COGNITO_USER_POOL_ID:}` under `aws.cognito.` — already wired. This story consumes it.
  - `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerWorkflowService.java`:
    - Lines 250-288: `runReadyHook` calls `userApiClient.provisionUserWithRole(provisionRequest)` and captures `ProvisionUserResponse userResponse`. The response is used to set `speaker.setUsername(userResponse.getUsername())` (line 286) — the `temporaryPassword` field on the response is NOT read today. This story changes `runReadyHook` to capture `temporaryPassword` and thread it forward (mechanism per Open Q#1).
    - Lines 290-305: `runInvitedHook` calls `magicLinkService.generateToken(...)` twice + `invitationEmailService.sendInvitationEmail(speaker, event, respondToken, dashboardToken, locale)`. The signature of `SpeakerInvitationEmailService.sendInvitationEmail` is `(SpeakerPool, Event, String respondToken, String dashboardToken, Locale)` — changed in this story to accept the temporary password (and optionally drop or ignore the magic-link tokens; the magic-link teardown itself is Phase F).
  - `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerInvitationEmailService.java`:
    - 100+-line file, currently builds emails from classpath resources `email-templates/speaker-invitation-{en|de}.html` and `.txt`. The `loadEmailTemplate` helper (around line 92) populates Mustache-style `{{var}}` placeholders including `{{acceptLink}}`, `{{declineLink}}`, `{{jwtMagicLink}}`, `{{dashboardLink}}` — these placeholders are the magic-link payload that this story rewrites to a Cognito login URL + temporary password.
    - The `@Value("${app.base-url:https://batbern.ch}")` field at line 47 provides the base URL for the login link.
  - `services/event-management-service/src/main/resources/email-templates/`:
    - **Today: 2 locales (de + en)** for these speaker templates: `speaker-invitation-{de|en}.{html|txt}` (4 files), `speaker-acceptance-{de|en}.html` (2 files — no .txt today), `speaker-reminder-response-tier{1|2|3}-{de|en}.html` (6 files), `speaker-reminder-content-tier{1|2|3}-{de|en}.html` (6 files).
    - **After this story: 10 locales** for invitation + acceptance + 6 reminder tiers = 10 × 8 = **80 template files** (matching the existing two-format invitation pattern means .html and .txt for invitations only — see Open Q#3 for whether other templates get .txt parity).
  - `web-frontend/public/locales/`:
    - 10 locale directories exist today: `de, en, es, fi, fr, gsw-BE, it, ja, nl, rm` — verified by `ls` at story creation. These are the same 10 locales the email templates must cover.
  - `docs/api/users-api.openapi.yml`:
    - Lines 1972-2050 (`ProvisionUserRequest` + `ProvisionUserResponse` schemas). The `temporaryPassword` field on the response exists today with a description that explicitly cites Story 11.E.2 (line 2015-2017). This story updates the description to remove the "always null" caveat and add the new behaviour wording. The schema field shape itself does not change.

---

## Acceptance Criteria

The AC are pinned to PRD lines 1176-1249. Each AC names exact files. Where the literal PRD wording leaves an architectural ambiguity (notably the temp-password lifecycle between `READY` and `INVITED`), the AC reflects the recommended resolution from Open Q#1 below — the dev applies whichever variant PM picks before development starts.

### AC1 — `UserService.provisionUserWithRole` creates a real Cognito user on the new-user path (FR3, AR15, NFR3)

**Given** `services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/UserService.java`,
**When** `provisionUserWithRole(ProvisionUserRequest request)` runs for a non-existent user (the new-user branch around line 714-720),
**Then** before the existing `return new ProvisionUserResponse() ... .temporaryPassword(null)`:

1. The service generates a strong random temporary password via a new injected `PasswordGenerator` collaborator (see AC4 — ≥ 16 chars, contains at least one lowercase letter, one uppercase letter, one digit, and one symbol from the set Cognito accepts).
2. The service calls `cognitoIntegrationService.adminCreateUserWithTemporaryPassword(email, generatedPassword, username)` (new method on `CognitoIntegrationService` — see AC2/AC3) where `username` is `created.getUsername()` from line 716. The call is wrapped in a try/catch (see AC5 for the failure-handling contract).
3. The `temporaryPassword(...)` field on the returned `ProvisionUserResponse` is populated with the generated password (replacing the current `null`).
4. **Audit logging:** the existing `log.info("Provisioning: created user {} with role {} (created=true)", ...)` line stays. **A new log line is NOT added** that prints the temp password — the password is never logged (NFR2). A separate `log.info("Cognito user provisioned for {} (status=FORCE_CHANGE_PASSWORD)", maskedEmail)` IS added before the return — verifies the AdminCreateUser succeeded without disclosing the password.

**And** for the existing-user idempotent paths (lines 655-659 and 706-710), `temporaryPassword(null)` stays — these paths do NOT call AdminCreateUser, do NOT generate a new password, and do NOT modify the Cognito user. The two lines' comments are updated from `// Story 11.E.2 wires Cognito` to `// Idempotent re-provisioning: Cognito user already exists; no new temp password generated (NFR3)`.

**And** the existing P0 role-grant whitelist (lines 633-638) stays untouched — non-ADMIN callers can only request the `SPEAKER` role, which is the only role that triggers Cognito provisioning in this epic.

**And** the existing `@Counted` Micrometer metric stays unchanged. A new `@Timed` metric is **NOT** required — the Cognito call latency rolls into the existing one.

---

### AC2 — `CognitoIntegrationService` gains `adminCreateUserWithTemporaryPassword(...)` (AR15, NFR5)

**Given** `services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/CognitoIntegrationService.java`,
**Then** a new method is added to the interface:

```java
/**
 * Create a Cognito user with a temporary password and FORCE_CHANGE_PASSWORD status.
 * Story 11.E.2 (AR15, FR3). Called by UserService.provisionUserWithRole on the new-user path.
 *
 * <p>Suppresses Cognito's default invitation email — BATbern sends its own templated
 * invitation containing the temp password (per UX-DR21).
 *
 * <p>Idempotency: if the email already exists in the user pool (UsernameExistsException),
 * the method returns silently (the caller is responsible for the no-new-temp-password
 * branch per NFR3). This is the only Cognito error path the method swallows; any other
 * exception propagates.
 *
 * @param email speaker's email (becomes the Cognito Username AND the email attribute)
 * @param temporaryPassword generated by {@link PasswordGenerator}; must satisfy the pool policy
 * @param appUsername BATbern's username (used as the {@code preferred_username} Cognito attribute
 *                    so JWTs can carry it without an extra lookup)
 * @throws CognitoOperationException on any Cognito error other than UsernameExistsException
 */
void adminCreateUserWithTemporaryPassword(String email, String temporaryPassword, String appUsername);
```

**And** the existing `createCognitoUser(GetOrCreateUserRequest)` method on the interface **stays** — it remains a NO-OP for the invitation-based registration path (Story 1.14-2 contract). The new `adminCreateUserWithTemporaryPassword` is a distinct method on a distinct flow (organizer-led promotion at `CONTACTED → READY`).

**And** `CognitoOperationException` is a new runtime exception at `services/company-user-management-service/src/main/java/ch/batbern/companyuser/exception/CognitoOperationException.java`, extends `RuntimeException`, carries the failed action name and the user-pool email as accessor fields. Mapped to HTTP 502 Bad Gateway by `GlobalExceptionHandler` (Cognito is an upstream dependency; 5xx is correct).

---

### AC3 — `CognitoIntegrationServiceImpl.adminCreateUserWithTemporaryPassword` calls AdminCreateUser with the right shape (FR3, NFR2)

**Given** `services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/CognitoIntegrationServiceImpl.java`,
**When** the new method runs,
**Then** it calls `cognitoClient.adminCreateUser(...)` with these exact request fields (using the AWS SDK v2 builder):

```java
AdminCreateUserRequest req = AdminCreateUserRequest.builder()
    .userPoolId(userPoolId)
    .username(email)                                       // Username = email (matches existing pool config)
    .temporaryPassword(temporaryPassword)
    .messageAction(MessageActionType.SUPPRESS)             // BATbern sends its own templated invitation
    .userAttributes(
        AttributeType.builder().name("email").value(email).build(),
        AttributeType.builder().name("email_verified").value("true").build(),
        AttributeType.builder().name("preferred_username").value(appUsername).build()
        // No given_name / family_name here — they live in PostgreSQL user_profiles
        // per ADR-004; Cognito only carries identity attributes.
        // No custom:role attribute — roles live in user_roles per ADR-001.
        // No custom:companyId — companies live in PostgreSQL companies table; the
        // pre-token-generation Lambda enriches the JWT at login time.
    )
    .build();

try {
    cognitoClient.adminCreateUser(req);
    log.info("Cognito user created for {} (status=FORCE_CHANGE_PASSWORD)", LoggingUtils.maskEmail(email));
} catch (UsernameExistsException e) {
    // NFR3: idempotent. Caller's existing-user branch handles "already provisioned" — return silently.
    log.info("Cognito user already exists for {} — idempotent no-op", LoggingUtils.maskEmail(email));
} catch (CognitoIdentityProviderException e) {
    log.error("Cognito AdminCreateUser failed for {}: {}", LoggingUtils.maskEmail(email), e.getMessage());
    throw new CognitoOperationException("adminCreateUser", email, e);
}
```

**And** the `@SuppressWarnings({"FieldCanBeLocal", "unused"})` annotation at line 24 of `CognitoIntegrationServiceImpl.java` is **removed** — both `cognitoClient` and `userPoolId` are now actually used.

**And** `LoggingUtils.maskEmail(...)` is reused from the existing pattern (`SpeakerInvitationEmailService.java` line 80 uses it). Both forms — full email and masked — must NEVER appear next to a plaintext temp password in any log statement.

**And** the dev does **NOT** call `AdminAddUserToGroup` anywhere — confirmed by grep:

```bash
grep -rn "AdminAddUserToGroup\|adminAddUserToGroup" \
    services/company-user-management-service/src/main/
```

→ expects **zero** matches in `src/main/`. This is the post-condition of Story 11.E.1's Resolved Q#1 enforced at the application-code layer. Roles live in PostgreSQL `user_roles` per ADR-001; the SPEAKER role grant is already handled by `RoleService.addRole(...)` at line 714 of `UserService.provisionUserWithRole`.

---

### AC4 — `PasswordGenerator` collaborator (NFR9)

**Given** the temporary password must satisfy the Cognito policy (`minLength: 8`, `requireLowercase`, `requireUppercase`, `requireDigits`, `requireSymbols` per `cognito-stack.ts` lines 197-205, with `tempPasswordValidity: 14 days` after 11.E.1's Resolved Q#4 bump),
**Then** a new class is created at `services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/PasswordGenerator.java`:

```java
@Component
public class PasswordGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] LOWERCASE = "abcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final char[] UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
    private static final char[] DIGITS = "0123456789".toCharArray();
    // Cognito accepts these symbols per AWS docs (not ambiguous in console-paste flows):
    // ! @ # $ % & ? + - = _ * .
    // Exclude " ' \ ` ~ ; , ( ) [ ] { } < > / | : space (Cognito accepts some but they confuse copy-paste).
    private static final char[] SYMBOLS = "!@#$%&?+-=_*.".toCharArray();

    /**
     * Generate a 16-char temporary password containing at least one character
     * from each of the four classes (lowercase, uppercase, digit, symbol).
     * Cryptographically secure via {@link SecureRandom}.
     */
    public String generate() {
        return generate(16);
    }

    public String generate(int length) {
        if (length < 8) {
            throw new IllegalArgumentException("Length must be ≥ 8 to satisfy Cognito password policy");
        }
        // Guarantee one of each class, fill the rest randomly, shuffle.
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

**And** unit test at `services/company-user-management-service/src/test/java/ch/batbern/companyuser/service/PasswordGeneratorTest.java` covers (≥ 6 cases):

1. `should_generate16CharsByDefault_when_generateCalled` — `assertEquals(16, generator.generate().length())`.
2. `should_containAllFourClasses_when_passwordGenerated` — across 100 generated passwords, each contains ≥ 1 lowercase, ≥ 1 uppercase, ≥ 1 digit, ≥ 1 symbol from the allowed set.
3. `should_generateDifferentPasswords_when_calledRepeatedly` — `Set<String>` of 1000 passwords has size 1000 (no collisions — sanity check on `SecureRandom`).
4. `should_acceptCustomLength_when_generateCalledWithLength` — 24, 32 lengths work.
5. `should_throwIllegalArgument_when_lengthBelow8` — generator rejects sub-policy lengths.
6. `should_notContainAmbiguousSymbols_when_generated` — across 100 passwords, none contain `"`, `'`, `\`, `` ` ``, `~`, `;`, `,`, `(`, `)`, `[`, `]`, `{`, `}`, `<`, `>`, `/`, `|`, `:`, or space (the operator must be able to paste the password from the invitation email without escape pitfalls).

---

### AC5 — Failure-handling contract: Cognito failure rolls back User row + role grant (FR3 "atomic, all-or-nothing")

**Given** `UserService.provisionUserWithRole` runs inside a Spring `@Transactional` boundary (the method is currently annotated `@Counted` but **NOT** `@Transactional` today — that's a gap this story closes),
**When** `cognitoIntegrationService.adminCreateUserWithTemporaryPassword(...)` throws `CognitoOperationException`,
**Then**:

1. `provisionUserWithRole` is annotated `@Transactional` (write transaction). The annotation lands on the method declaration at line 621 of `UserService.java`. Class-level `@Transactional` is not added — only the one method needs it.
2. The thrown `CognitoOperationException` propagates up; the surrounding `@Transactional` aborts the PostgreSQL transaction; the User row created by `createNewUser` at line 694 is rolled back; the role-assignment row from `roleService.addRole(...)` at line 714 is rolled back.
3. The HTTP response to EMS is 502 Bad Gateway (mapped via `GlobalExceptionHandler`). EMS receives the error from `userApiClient.provisionUserWithRole(...)` and the workflow `transition()` aborts — the speaker stays in `CONTACTED`, the `speaker_pool` row is unchanged, no `SpeakerPromotedToReadyEvent` is published.
4. **No partial state.** The post-condition of the failed call is identical to the pre-condition. The organizer can retry the promote action; the second call hits Cognito again. If Cognito is permanently misconfigured, the dev troubleshoots via CloudWatch (`/aws/ecs/BATbern-staging/company-user-management`) — Cognito failures are operationally distinct from "no slot capacity" or "missing email", and the 502 status code surfaces that.
5. The race-loser idempotent path at line 695-710 (`DataIntegrityViolationException` on `createNewUser`) is **NOT** modified — that path catches the unique-constraint race and returns the existing user. The Cognito call is **not made** on this path (the user already exists, so they already have a Cognito user from a parallel call). `.temporaryPassword(null)` stays at line 709.

**And** the integration test at AC9 item 4 covers the rollback scenario with a Mockito stub that throws `CognitoOperationException` on the second call.

---

### AC6 — `SpeakerWorkflowService.runReadyHook` captures the temp password and dispatches `SpeakerPromotedToReadyEvent` (FR3)

**Given** `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerWorkflowService.java` lines 250-288 (the existing `runReadyHook`),
**When** `runReadyHook` runs,
**Then** the existing call `ProvisionUserResponse userResponse = userApiClient.provisionUserWithRole(provisionRequest);` (line 265) is unchanged in shape, BUT the response's `temporaryPassword` field is now consumed:

```java
String tempPassword = userResponse.getTemporaryPassword();   // may be null for the idempotent path
// ... existing identity-rebind guard (lines 271-284) stays unchanged ...
speaker.setUsername(userResponse.getUsername());
speaker.setEmail(payload.email());

// Stash the temp password for the INVITED hook — see Open Q#1 for the chosen mechanism.
// The reference implementation below uses the agreed mechanism; the dev applies whichever
// variant PM picks. See "Tasks → Task 4" for the three concrete variants.
```

**And** the existing `SpeakerPromotedToReadyEvent` published by the workflow service after step 6 in `transition()` already carries enough payload (per shared-kernel from 11.B.1) to mark the moment. **The temp password is NOT added to the event payload** — domain events go on EventBridge and are observable across services; embedding a credential in an event payload would be a leak. The temp password lives on a separate, internal pathway (Open Q#1 resolution determines which).

**And** the dev does **NOT** persist the temp password to any column of `speaker_pool` or any table reachable from a SQL `SELECT`. The grep:

```bash
grep -rn "temporaryPassword\|temp_password\|tempPassword" \
    services/event-management-service/src/main/resources/db/migration/
```

→ expects zero new matches after this story (only any pre-existing matches survive).

---

### AC7 — `SpeakerWorkflowService.runInvitedHook` invokes the rewritten email service with login URL + temp password (FR9, UX-DR21)

**Given** `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerWorkflowService.java` lines 290-305 (the existing `runInvitedHook`),
**When** `runInvitedHook` runs,
**Then**:

1. The existing two `magicLinkService.generateToken(...)` calls (lines 298-299) are **deleted** — Phase E removes the magic-link payload from the email; Phase F (11.F.1) deletes the entire `MagicLinkService` after the magic-link branch deletion. (Deleting the two calls here in 11.E.2 is a no-op for the magic-link DB rows — they no longer fire on the invitation path — but the rest of `MagicLinkService` stays for the speaker portal until 11.F.1.)
2. The rewritten call to `SpeakerInvitationEmailService.sendInvitationEmail(...)` uses the new signature from AC8:

   ```java
   String tempPassword = readTemporaryPassword(speaker.getId());  // per Open Q#1 resolution
   String loginUrl = baseUrl + "/login";   // injected via @Value("${app.base-url}")
   Locale locale = resolveLocale(payload);

   invitationEmailService.sendInvitationEmail(
       speaker,
       event,
       loginUrl,
       tempPassword,        // null if the speaker is already a Cognito user (NFR3 re-invite case)
       locale
   );
   ```

3. The temp password is **discarded from memory** after the email send returns — no field on `SpeakerWorkflowService` retains it; the local variable goes out of scope. The cache/stash from Open Q#1 is also evicted at this point (best-effort — TTL-based eviction is the safety net).
4. The existing `speaker.setInvitedAt(Instant.now())` (line 304) stays unchanged.
5. The existing `AFTER_COMMIT` concern documented in the code comments (lines 295-297) is **NOT addressed** in this story — it remains in deferred-work. The email-send-inside-transaction risk persists; mitigating it is orthogonal to the Phase E rewire.

**And** the existing `MagicLinkService` field on `SpeakerWorkflowService` (line 96) stays — it's still used by `runAcceptedHook` (line 321: `magicLinkService.generateToken(speaker.getId(), TokenAction.VIEW, 30)` for the speaker dashboard). Deletion of the field is scoped to Phase F.

---

### AC8 — `SpeakerInvitationEmailService` signature change + Cognito-flow email rendering (FR9, UX-DR21)

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
                                   String loginUrl, String temporaryPassword, Locale locale)
   ```

   The `@Async` annotation stays. The two old parameters (`respondToken`, `dashboardToken`) are dropped. The new parameters carry the Cognito-flow payload.

2. The internal `loadEmailTemplate(...)` helper is updated to populate these new Mustache template variables (replacing the old magic-link-flow variables):

   | Old (magic-link)             | New (Cognito flow)                                                       |
   |------------------------------|--------------------------------------------------------------------------|
   | `{{acceptLink}}`             | **DELETED** — speaker accepts via the portal after logging in            |
   | `{{declineLink}}`            | **DELETED** — same                                                       |
   | `{{jwtMagicLink}}`           | **DELETED**                                                              |
   | `{{dashboardLink}}`          | `{{loginUrl}}` — single canonical entry point                            |
   | _none_                       | `{{temporaryPassword}}` — conditional via `{{#temporaryPassword}} ... {{/temporaryPassword}}` Mustache section |
   | _none_                       | `{{usernameForLogin}}` — speaker's email (the Cognito username)          |

   The conditional section means: if `temporaryPassword` is null (re-invite of an already-provisioned Cognito user), the password block does not render. The re-invite branch instead renders a fixed message (template-localized): "Use your existing password to log in, or use 'Forgot password' on the login page to reset it." See AC9 for the localized strings.

3. The two existing helper methods `generateJwtToken(...)` call (line 89) and the `respondToken` / `dashboardToken` passes are deleted. The `MagicLinkService` dependency on this service can be removed — that's a one-line constructor cleanup. The `MagicLinkService` field itself stays on the codebase for non-invitation callers.

4. A new unit test `SpeakerInvitationEmailServiceTest` covers (≥ 5 cases):
   - `should_renderTemporaryPasswordBlock_when_passwordProvided` (English template).
   - `should_renderReinviteMessage_when_passwordNull` (English template).
   - `should_renderInGermanLocale_when_localeIsDeutsch` (German template).
   - `should_renderInBerneseSwissGerman_when_localeIsGswBE` (gsw-BE template — locale parity).
   - `should_includeLoginUrl_when_emailRendered` (any locale; asserts the `{{loginUrl}}` placeholder resolves to the configured base URL + `/login`).

---

### AC9 — Email templates rewritten in all 10 locales (UX-DR21, UX-DR22, NFR10)

**Given** the existing 2-locale (de + en) speaker email templates in `services/event-management-service/src/main/resources/email-templates/`,
**When** this story merges,
**Then** the following template files exist and have been **rewritten or freshly authored** to the new Cognito-flow content:

#### Invitation templates (the substantive rewrite — both .html and .txt for parity with the existing pattern)

```
speaker-invitation-de.html         (rewritten)
speaker-invitation-de.txt          (rewritten)
speaker-invitation-en.html         (rewritten)
speaker-invitation-en.txt          (rewritten)
speaker-invitation-fr.html         (new)
speaker-invitation-fr.txt          (new)
speaker-invitation-it.html         (new)
speaker-invitation-it.txt          (new)
speaker-invitation-rm.html         (new)
speaker-invitation-rm.txt          (new)
speaker-invitation-es.html         (new)
speaker-invitation-es.txt          (new)
speaker-invitation-fi.html         (new)
speaker-invitation-fi.txt          (new)
speaker-invitation-nl.html         (new)
speaker-invitation-nl.txt          (new)
speaker-invitation-ja.html         (new)
speaker-invitation-ja.txt          (new)
speaker-invitation-gsw-BE.html     (new)   ← Bernese Swiss German; matches the frontend locale dir
speaker-invitation-gsw-BE.txt      (new)
```

20 files total: 4 rewritten, 16 net-new. Naming pattern matches the existing one (`speaker-invitation-{locale}.{format}`); the `{locale}` token is the BCP-47 tag from the `web-frontend/public/locales/` directory listing — `gsw-BE` has a hyphen and a region subtag; the file loader (`ClassPathResource("email-templates/speaker-invitation-" + localeTag + ".html")`) just needs to resolve `locale.toLanguageTag()` correctly.

**Content of the rewritten invitation template (English reference; other locales are translations of this exact content):**

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

The HTML version mirrors the same content with BATbern branding (existing `layout-batbern-default-{de|en}.html` layout, where applicable — keep the existing HTML structure of the current `speaker-invitation-{de|en}.html` file, just swap the magic-link block for the new Cognito-flow block).

**And** the dev runs a grep to confirm no magic-link vocabulary leaks through:

```bash
grep -l "magic\|acceptLink\|declineLink\|jwtMagicLink\|dashboardLink" \
    services/event-management-service/src/main/resources/email-templates/speaker-invitation-*
```

→ expects **zero** matches. The replacement is total.

#### Acceptance + reminder templates (simplification: drop tentative-response language)

The acceptance and reminder template families today reference tentative-response options ("Or mark as tentative" buttons, "We need a definitive answer" reminder copy). UX-DR21 says all four template families have the tentative-response copy stripped.

The dev:

1. **Simplifies** `speaker-acceptance-{de|en}.html` (2 files): removes any tentative-response copy; keeps the confirmation message + portal link.
2. **Simplifies** `speaker-reminder-response-tier{1|2|3}-{de|en}.html` (6 files): same.
3. **Simplifies** `speaker-reminder-content-tier{1|2|3}-{de|en}.html` (6 files): same — these don't reference tentative response, but the dev verifies. If they don't reference it, no edit needed.
4. **Adds** 8 new locales (fr, it, rm, es, fi, nl, ja, gsw-BE) for each of the simplified templates. Format parity matches the existing (.html only for acceptance/reminder; no .txt requirement — see Open Q#3 if the dev wants .txt parity).

Total files for the acceptance/reminder family: 14 existing simplified + 8 × 7 = **56 new files**.

**Grand total** for AC9: 20 invitation files (4 rewritten + 16 new) + 14 acceptance/reminder simplified + 56 acceptance/reminder new = **90 template files** touched in this story.

**See Open Q#2 for the translation source** — this is a large body of localized content; PM must confirm whether translations are hand-written, machine-translated, or AI-assisted with human review.

---

### AC10 — Manual smoke test on staging (FR9 verification)

**Given** Story 11.E.1 has been deployed to staging (AC verified via `aws cognito-idp describe-user-pool-client ... --query 'UserPoolClient.ExplicitAuthFlows'` showing `ALLOW_ADMIN_USER_PASSWORD_AUTH`),
**When** this story is deployed to staging,
**Then** the dev runs an end-to-end manual smoke test before requesting code review:

1. Log in to the organizer kanban at `https://www.batbern.ch` (or the staging hostname per current deploy).
2. Pick an upcoming event with at least one open speaker slot.
3. Add a test speaker to the brainstorm panel (state: `IDENTIFIED`) using a personal test email address the dev controls. **DO NOT** use a real speaker's email.
4. Move the speaker through `CONTACTED` (log an outreach action), then **`READY`** (the promote button — Story 11.D.1). Verify in CloudWatch (`/aws/ecs/BATbern-staging/company-user-management`) that the log line `Cognito user provisioned for {masked-email} (status=FORCE_CHANGE_PASSWORD)` appears.
5. Move the speaker to **`INVITED`** (the "Send invitation" button — Story 11.D.2). Wait for the invitation email to arrive.
6. Verify the email contains: a login URL (`https://www.batbern.ch/login` or the staging URL), the email address as the username, a 16-character temporary password, and the localized "you will be asked to set your own password" note.
7. Open the login URL in an incognito browser. Enter the email + temporary password. Confirm Cognito challenges with `NEW_PASSWORD_REQUIRED` (the standard Cognito "you must change your password" screen).
8. Set a new password. Confirm the speaker lands on `/speaker-portal/dashboard` (the existing speaker dashboard page — full Cognito-session refactor is Story 11.E.3; the dashboard page being reachable here is enough to prove the auth flow works end-to-end).
9. Verify in CloudWatch + the AWS console that the Cognito user status is now `CONFIRMED` (no longer `FORCE_CHANGE_PASSWORD`).
10. **Clean up:** delete the test Cognito user via `AWS_PROFILE=batbern-staging aws cognito-idp admin-delete-user --user-pool-id $POOL --username <email>` and delete the speaker-pool row via the kanban "Decline with reason: test cleanup" action.

**And** the dev pastes the masked email + the four CloudWatch log lines (provisioning log + AdminCreateUser-succeeded log + invitation-email-sent log + password-changed Cognito event) into the PR description as the manual-verification evidence. The temporary password value is **NEVER** pasted into the PR description, the commit message, or any chat tool — only the dev's local browser sees it.

**And** the dev documents one negative case in the PR description: a known-existing Cognito user (the dev's own organizer email) is added to the kanban as a "speaker" and promoted through `CONTACTED → READY`. The dev verifies the invitation email omits the temporary-password line and includes the "use your existing password" wording — confirming the idempotent re-invite branch (NFR3, AC1 existing-user path, AC8 conditional rendering).

---

### AC11 — Integration tests (NFR6, NFR3)

**Given** `./gradlew :services:company-user-management-service:test :services:event-management-service:test`,
**Then** new and updated integration tests cover (≥ 8 cases across both services):

#### CUMS — `UserServiceIntegrationTest` (extend the existing file)

1. `should_callAdminCreateUser_when_provisioningNewSpeaker` — seeds no existing user, calls `provisionUserWithRole(email=..., role=SPEAKER)`. Asserts: User row exists, role-assignment row exists, **Mockito spy on `CognitoIntegrationService` records exactly one `adminCreateUserWithTemporaryPassword` call** with the right email + a non-null 16-char temp password. The response's `temporaryPassword` field is non-null and matches the value passed to Cognito.
2. `should_returnNullTempPassword_when_provisioningExistingUser` — seeds an existing User (no SPEAKER role yet), calls `provisionUserWithRole`. Asserts: role-assignment row added (idempotent grant succeeds); Cognito spy was **NOT** called; response's `temporaryPassword` is null.
3. `should_rollbackUserRow_when_cognitoCallFails` — Mockito stub on `CognitoIntegrationService` throws `CognitoOperationException`. Asserts: no User row in `users` table after the call; no role-assignment row; `CognitoOperationException` propagated to the caller (502 at the HTTP boundary). Verifies the `@Transactional` rollback path from AC5.
4. `should_handleUsernameExists_when_cognitoRaceCondition` — Mockito stub on `cognitoClient.adminCreateUser(...)` throws `UsernameExistsException`. Asserts: the method returns normally; the User + role rows DO persist (since the Cognito user already exists, the partial state is fine — this is the only Cognito error that doesn't roll back). Returned `temporaryPassword` is null because we don't know the existing user's password.

#### EMS — `SpeakerWorkflowServiceIntegrationTest` (extend the existing file)

5. `should_captureTempPasswordOnReady_when_provisioningNewSpeaker` — seeds CONTACTED speaker, runs `transition(speaker.id, READY, organizer, payload(email))`. The WireMock stub on CUMS's `/users/provision` returns a fixed 16-char temp password. Asserts: speaker status → READY; `speaker_pool.username` populated; `SpeakerPromotedToReadyEvent` published; **the temp password value is captured by the workflow service into the stash mechanism agreed in Open Q#1**, verifiable by inspecting the stash directly OR by triggering the immediate INVITED transition (case 6).
6. `should_embedTempPasswordInInvitationEmail_when_runningInvitedHook` — runs `transition(READY → INVITED)` immediately after case 5. WireMock stub on `EmailService` (or the shared-kernel email-send seam) captures the email. Asserts: the email body contains the temp password from case 5 + the login URL + the speaker's email as the username; the magic-link payload (`acceptLink`, `declineLink`, `jwtMagicLink`) is **NOT** in the body.
7. `should_omitTempPasswordBlock_when_invitingAlreadyProvisionedSpeaker` — seeds CONTACTED speaker where the CUMS `/users/provision` stub returns `temporaryPassword=null` (existing-user branch). Runs READY then INVITED. Asserts: the rendered email contains the "use your existing password" wording (in the test's locale) and **does NOT** contain a 16-char alphanumeric password pattern (regex assertion).
8. `should_rollbackTransition_when_cognitoProvisioningFails` — WireMock stub on CUMS `/users/provision` returns 502. Asserts: `SpeakerWorkflowService.transition(...)` throws `ApiClientException` (or whatever the existing `UserApiClient` failure type is); the speaker stays at CONTACTED; no `SpeakerPromotedToReadyEvent` published; no `speaker_status_history` row written.

**And** the email-rendering test (AC8 item 4) is a separate `SpeakerInvitationEmailServiceTest` unit test (not integration) — it doesn't need Testcontainers, just a stubbed `EmailService`.

**And** all four CUMS integration tests + all four EMS integration tests extend `AbstractIntegrationTest` (per `_bmad-output/project-context.md` "Backend Integration Tests — Critical Requirements"). The CUMS class uses `TestAwsConfig.cognitoIdentityProviderClient()` (the Mockito-mocked `CognitoIdentityProviderClient` bean — verified at `services/company-user-management-service/src/test/java/ch/batbern/companyuser/config/TestAwsConfig.java` line 90). The EMS class uses WireMock for the cross-service CUMS HTTP boundary.

---

### AC12 — OpenAPI spec description updated; no schema break (ADR-006)

**Given** `docs/api/users-api.openapi.yml` line 2015-2050 (the `ProvisionUserResponse` schema),
**When** the file is edited,
**Then** the description string for the `temporaryPassword` field is updated from:

> Reserved for Story 11.E.2 — Cognito AdminCreateUser/AdminSetUserPassword wiring. Always `null` in 11.C.2.

to:

> Temporary password used by the speaker for first login. Non-null only on the new-user
> branch (when a brand-new Cognito user is created); null for the idempotent re-invite
> branch (when the Cognito user already exists). Per NFR2 + ADR-001, the SPEAKER role is
> granted via PostgreSQL `user_roles` row insert — NOT via `cognito-idp:AdminAddUserToGroup`.
> Never persisted at rest; the caller embeds it once in the invitation email and discards.

**And** the corresponding line on line 1390-1393 (the path-level description of `POST /users/provision`) is updated to drop the "Cognito wiring is **deliberately stubbed**" wording — replace with "Cognito wiring (AdminCreateUser) is wired per Story 11.E.2."

**And** the dev runs:

```bash
cd web-frontend && npm run generate:api-types:users
./gradlew :services:company-user-management-service:openApiGenerateUsers
./gradlew :services:event-management-service:openApiGenerateUsers
```

— and commits any regenerated TypeScript types (`web-frontend/src/types/generated/users-api.types.ts`) and any regenerated Java DTOs. **The field shape itself does not change** (it's still `string` / `nullable`), so the regen diff should be limited to docstring updates.

---

### AC13 — Bruno contract test extended; staging smoke

**Given** `bruno-tests/users-api/`,
**When** the dev surveys the existing `provision-user.bru` (added by Story 11.C.2 per its AC10),
**Then** the dev:

1. Extends the existing `provision-user.bru` test for the new-user case to assert the response includes a non-null, 16-character `temporaryPassword` value (regex: `^[a-zA-Z0-9!@#$%&?+\-=_*.]{16}$`).
2. Adds an idempotency follow-up assertion: a second invocation with the same payload returns `created: false` and `temporaryPassword: null`.
3. Does **NOT** add a new `bruno-tests/auth/AdminInitiateAuth.bru` test — the IAM permission and the App Client flow are exercised by AC10's manual smoke test against staging (the same approach Story 11.E.1 took with its AR42 deferral).
4. Runs `./scripts/ci/run-bruno-tests.sh` — green across all collections.

---

## Tasks / Subtasks

Tasks ordered so the build stays green at each step. Each task names the AC it satisfies, the test command to run after, and the grep that confirms completion.

### Task 1 — `PasswordGenerator` (AC4)

1.1. Create `services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/PasswordGenerator.java` per AC4.
1.2. Create the unit test `PasswordGeneratorTest` with the six cases.
1.3. **Verify:** `./gradlew :services:company-user-management-service:test --tests PasswordGeneratorTest 2>&1 | tee /tmp/pwgen-test.log` — green.

### Task 2 — `CognitoIntegrationService` + `Impl` real call site (AC2, AC3)

2.1. Add the `adminCreateUserWithTemporaryPassword(...)` method to the `CognitoIntegrationService` interface per AC2.
2.2. Create `CognitoOperationException` per AC2's "new runtime exception" requirement.
2.3. Implement the method in `CognitoIntegrationServiceImpl` per AC3. Remove the `unused` from the class-level `@SuppressWarnings`.
2.4. Wire `CognitoOperationException → HTTP 502` in `GlobalExceptionHandler` (or wherever the CUMS handler lives).
2.5. **Verify:** `./gradlew :services:company-user-management-service:compileJava 2>&1 | tee /tmp/cums-compile.log` — green.

### Task 3 — `UserService.provisionUserWithRole` wires real Cognito (AC1, AC5)

3.1. Add the `@Transactional` annotation on line 621 of `UserService.java`.
3.2. Inject `PasswordGenerator` + `CognitoIntegrationService` into `UserService` (if not already injected). Verify constructor wiring + Mockito setup in existing tests doesn't break.
3.3. In the new-user branch (around line 714-720), generate the temp password, call `adminCreateUserWithTemporaryPassword(...)`, populate `temporaryPassword(...)` on the returned `ProvisionUserResponse`.
3.4. Update the two existing-user paths' inline comments per AC1.
3.5. Verify the grep:
   ```
   grep -rn "AdminAddUserToGroup" services/company-user-management-service/src/main/
   ```
   → zero matches.
3.6. **Verify:** `./gradlew :services:company-user-management-service:test 2>&1 | tee /tmp/cums-test.log` — green (existing tests may need Mockito updates to stub the new collaborators; do this in 3.7).
3.7. Extend `UserServiceIntegrationTest` with the four cases from AC11 items 1-4.
3.8. **Verify:** `./gradlew :services:company-user-management-service:test --tests UserServiceIntegrationTest 2>&1 | tee /tmp/cums-int-test.log` — green.

### Task 4 — Temp-password stash mechanism (per Open Q#1 resolution)

4.1. **PM must resolve Open Q#1 BEFORE this task starts.** The dev applies whichever variant PM picks:
   - **Variant A — Caffeine cache:** Add `Caffeine<UUID, String>` cache (`pendingTempPasswords`, TTL = 14 days matching `tempPasswordValidity`, maxSize = 1000) as a `@Bean` in EMS config. `runReadyHook` puts; `runInvitedHook` gets-and-evicts. **Pros:** Zero Cognito side-effects; minimal new code. **Cons:** Lost on EMS restart between READY and INVITED. **Mitigation:** if cache miss at INVITED time, the org gets an error and can re-trigger via "Decline then re-promote" or via the AWS console; alternatively the dev adds an `AdminSetUserPassword` fallback to regenerate.
   - **Variant B — AdminSetUserPassword at INVITED:** Drop the `temporaryPassword` field from the `provisionUserWithRole` response wiring (back to always-null); add a new `CognitoIntegrationService.adminSetTemporaryPassword(email)` method that calls `AdminSetUserPassword(Permanent=false)` and returns a fresh temp password; call it from `runInvitedHook`. **Pros:** No EMS state across transitions; clean separation. **Cons:** Two Cognito SDK calls per speaker invitation; the AC1 wording "returns `{ username, temporaryPassword }`" is contradicted (the dev updates the AC + PRD wording in the same commit per doc-drift policy).
   - **Variant C — Combine READY + INVITED into a single organizer action:** Modify the kanban so the "Promote to speaker" button does CONTACTED → READY → INVITED in one click. Skip the gap problem entirely. **Pros:** Cleanest; matches AC literal wording. **Cons:** Removes the kanban-state granularity that 11-d-2 + 11-d-3 introduced; a substantial UX change that touches stories already in `review`/`done` — out of this story's scope to revisit.

4.2. **Recommended (Variant B)** — the dev implements `adminSetTemporaryPassword(email)` per the AdminSetUserPassword Cognito API (with `Permanent=false`). The temp password lives only on the local variable in `runInvitedHook` between the Cognito call and the email-send call. This variant requires a one-line PRD edit (line 1190-1205, AC2 phrasing) in the same commit.

4.3. Implement the chosen variant; document the choice + the PRD edit (if any) in the PR description.

4.4. **Verify:** `./gradlew :services:event-management-service:compileJava 2>&1 | tee /tmp/ems-compile.log` — green.

### Task 5 — `SpeakerWorkflowService` runReadyHook + runInvitedHook updates (AC6, AC7)

5.1. Edit `runReadyHook` (lines 250-288): consume `userResponse.getTemporaryPassword()` per AC6. If Variant A from Task 4 is chosen, write to the cache. If Variant B, skip — Task 4's `adminSetTemporaryPassword` runs at INVITED time.

5.2. Edit `runInvitedHook` (lines 290-305): delete the two `magicLinkService.generateToken(...)` calls; call `SpeakerInvitationEmailService.sendInvitationEmail(speaker, event, loginUrl, tempPassword, locale)` with the new signature; read `tempPassword` from the chosen variant's stash.

5.3. Extend `SpeakerWorkflowServiceIntegrationTest` with the four cases from AC11 items 5-8. The tests stub `UserApiClient` (or the underlying WireMock for `POST /api/v1/users/provision`) to return a controlled `temporaryPassword`.

5.4. **Verify:** `./gradlew :services:event-management-service:test --tests SpeakerWorkflowServiceIntegrationTest 2>&1 | tee /tmp/ems-wf-test.log` — green.

### Task 6 — `SpeakerInvitationEmailService` signature change + template-variable rewrite (AC8)

6.1. Change the method signature per AC8 item 1.

6.2. Update `loadEmailTemplate(...)` to populate `{{loginUrl}}`, `{{temporaryPassword}}`, `{{usernameForLogin}}` and drop the four magic-link variables per AC8 item 2.

6.3. Drop the `magicLinkService.generateJwtToken(...)` call from this service. The `MagicLinkService` field can be removed from `SpeakerInvitationEmailService`'s constructor; verify the existing tests are updated.

6.4. Create the new unit test `SpeakerInvitationEmailServiceTest` with the five cases from AC8 item 4.

6.5. **Verify:** `./gradlew :services:event-management-service:test --tests SpeakerInvitationEmailServiceTest 2>&1 | tee /tmp/ems-email-test.log` — green.

### Task 7 — Rewrite English + German invitation templates (AC9 / Phase 1 of 3 — locale fan-out comes next)

7.1. Edit `services/event-management-service/src/main/resources/email-templates/speaker-invitation-en.html` per the AC9 reference template content (HTML version: keep existing branding + structure; swap magic-link block for Cognito-flow block).

7.2. Edit `services/event-management-service/src/main/resources/email-templates/speaker-invitation-en.txt` per the AC9 reference template (the plain-text content shown in AC9 is the source-of-truth for the `.txt` version).

7.3. Translate the English content to German and edit `speaker-invitation-de.html` + `speaker-invitation-de.txt`. The dev uses the existing German wording from the current invitation template for tone/style consistency (event-details block, deadline phrasing, signature).

7.4. **Verify** with the grep from AC9:
   ```
   grep -l "magic\|acceptLink\|declineLink\|jwtMagicLink\|dashboardLink" \
       services/event-management-service/src/main/resources/email-templates/speaker-invitation-{en,de}.*
   ```
   → zero matches.

7.5. Re-run the `SpeakerInvitationEmailServiceTest` (Task 6.4). Both `en` and `de` test cases now use the rewritten templates.

### Task 8 — Rewrite acceptance + reminder templates for de + en (AC9 simplification)

8.1. Open each of the 14 existing templates listed under AC9 acceptance/reminder family. Strip any tentative-response copy ("Or mark as tentative", "we'd appreciate a definitive answer", etc.). Keep accept/decline language otherwise unchanged.

8.2. **Verify** the grep:
   ```
   grep -l "tentative\|Tentativ\|Tentative" \
       services/event-management-service/src/main/resources/email-templates/speaker-{acceptance,reminder}-*
   ```
   → zero matches in the de + en files after the edits.

8.3. The dev runs the existing acceptance / reminder unit tests (if any) and confirms green. If no such tests exist, the dev adds a minimal "the template renders without throwing" smoke test for each.

### Task 9 — Add 8 new locales for all four template families (AC9 / NFR10) — **see Open Q#2 for translation source**

9.1. **PM must resolve Open Q#2 BEFORE this task starts** — the dev applies whichever translation strategy PM picks.

9.2. For each of the 8 new locales (fr, it, rm, es, fi, nl, ja, gsw-BE), create:

   - `speaker-invitation-{locale}.html` (10 new files)
   - `speaker-invitation-{locale}.txt` (10 new files — see Open Q#3 if .txt parity isn't required, then drop these)
   - `speaker-acceptance-{locale}.html` (8 new files)
   - `speaker-reminder-response-tier{1,2,3}-{locale}.html` (24 new files)
   - `speaker-reminder-content-tier{1,2,3}-{locale}.html` (24 new files)

   Per locale: **9 files** (or 10 with .txt parity per Open Q#3) × 8 locales = **72 to 80 new files**.

9.3. The dev sources the translations per Open Q#2's resolution. If hand-translated, a single native-speaker reviewer per locale signs off (the dev documents who reviewed in the PR description). If machine-translated, the dev uses DeepL or GPT-4 + a one-pass human review for grammar and idiom.

9.4. **Verify** at least one rendering per locale:
   - The `should_renderInBerneseSwissGerman_when_localeIsGswBE` test from AC8 already covers `gsw-BE`.
   - The dev adds a parametrized test `should_renderForEachLocale_when_localeProvided` that iterates over the 10 locales and asserts: (a) the template loads without throwing; (b) the rendered output contains the speaker name and the event title (a minimal "template variables resolved" check).

9.5. **Verify:** `./gradlew :services:event-management-service:test 2>&1 | tee /tmp/ems-test-full.log` — green across all four template-family test classes.

### Task 10 — OpenAPI spec + regenerate (AC12)

10.1. Edit `docs/api/users-api.openapi.yml` per AC12: update the `temporaryPassword` field's description and the path-level operation description.

10.2. **Regenerate:**
   ```
   cd web-frontend && npm run generate:api-types:users
   ./gradlew :services:company-user-management-service:openApiGenerateUsers
   ./gradlew :services:event-management-service:openApiGenerateUsers
   ```

10.3. Commit any regenerated `*.java` (CUMS + EMS generated DTOs are NOT committed — they're in `build/generated/`) and the regenerated `web-frontend/src/types/generated/users-api.types.ts`.

10.4. **Verify:** `./gradlew :services:company-user-management-service:compileJava :services:event-management-service:compileJava 2>&1 | tee /tmp/openapi-compile.log` — green.

### Task 11 — Bruno contract test extension (AC13)

11.1. Edit `bruno-tests/users-api/provision-user.bru` (or whichever existing `.bru` file Story 11.C.2 added) to assert the new-user case returns a 16-char `temporaryPassword` and the idempotent case returns null.

11.2. **Verify:** `./scripts/ci/run-bruno-tests.sh 2>&1 | tee /tmp/bruno.log` — green.

### Task 12 — Manual smoke test on staging (AC10)

12.1. Confirm Story 11.E.1 is deployed to staging — run `AWS_PROFILE=batbern-staging aws cognito-idp describe-user-pool-client --user-pool-id $POOL --client-id $CLIENT --query 'UserPoolClient.ExplicitAuthFlows'` and confirm `ALLOW_ADMIN_USER_PASSWORD_AUTH` is present. **If not, halt — wait for 11.E.1 deploy to complete before continuing.**

12.2. Push this story's branch to remote; merge `feature/speaker-workflow-refactor` → `develop` triggers staging deploy.

12.3. Wait for the staging deploy to complete (~20-30 min, watch the GitHub Actions Deploy workflow + the ECS service-event stream per CLAUDE.md "ECS Deploy Patience" guidance — do NOT cancel mid-deploy).

12.4. Execute the 10-step smoke-test procedure from AC10 against staging.

12.5. Paste the four CloudWatch log lines + the negative-case (re-invite) observation into the PR description as the manual-verification evidence.

12.6. Clean up the test Cognito user + the test speaker-pool row (AC10 step 10).

### Task 13 — Doc drift sweep (CLAUDE.md doc-drift policy)

13.1. Per `.github/doc-drift-mappings.yml`, this story changes:
   - `services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/UserService.java` → docs in `docs/architecture/06-backend-architecture.md` §"Speaker authentication (ADR-009)" + `docs/architecture/06b-user-lifecycle-sync.md`.
   - `services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/CognitoIntegrationService*.java` → ADR-009 §"Implementation Guidelines" lines 488-515 (the `SpeakerProvisioningService.provisionForSpeaker` skeleton).
   - `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerWorkflowService.java` + `SpeakerInvitationEmailService.java` → docs in `docs/architecture/06a-workflow-state-machines.md` §"Callers of transition()" + ADR-009 §Decision 3.
   - `services/event-management-service/src/main/resources/email-templates/` (new locale fan-out) → `docs/architecture/04-api-design.md` (if it mentions invitation-email shape) and the PRD §UX-DR21.

13.2. Update each doc to reflect the post-11.E.2 reality (Cognito provisioning lands at READY, AdminCreateUser only, temp password lifecycle per Open Q#1). The edits should be on the order of 5-15 lines per doc — the architectural decisions don't change, only the "implementation status: stubbed / wired" labels.

13.3. Add a revision-history row dated `2026-MM-DD` (current date) to ADR-009 noting "Story 11.E.2 PM-resolved Q#1-Q#4: temp-password lifecycle is `<chosen variant>`; translation source for 10 locales is `<chosen source>`; .txt template parity is `<yes/no>`; re-invite UX is `<chosen path>`. CUMS now wires `AdminCreateUser` with `MessageAction=SUPPRESS` in `CognitoIntegrationServiceImpl`; EMS captures + threads the temp password through `SpeakerInvitationEmailService.sendInvitationEmail`."

13.4. **Verify:** `git diff --name-only` includes both code AND doc files in the same commit (NOT a separate doc-only commit — per CLAUDE.md doc-drift policy, "Update those docs in the **same commit** as the code change").

### Task 14 — Commit + PR

14.1. Commit using a conventional-commit message:

```
feat(speaker-workflow): wire Cognito provisioning at READY + invitation-email rewrite + 10-locale i18n [Story 11.E.2]

CUMS:
- UserService.provisionUserWithRole now calls CognitoIntegrationService.adminCreateUserWithTemporaryPassword
  on the new-user path (AR15, FR3); existing-user paths return temporaryPassword=null (NFR3).
- New PasswordGenerator (SecureRandom, 16 chars, all four character classes) satisfies the pool
  policy from Story 11.E.1 (NFR9).
- CognitoIntegrationServiceImpl.adminCreateUserWithTemporaryPassword calls AdminCreateUser with
  MessageAction=SUPPRESS (BATbern sends its own templated invite per UX-DR21).
- Cognito UsernameExistsException is swallowed as idempotent no-op (NFR3); other failures
  propagate as 502 via new CognitoOperationException.
- @Transactional on provisionUserWithRole rolls back User + role on Cognito failure (FR3
  "atomic, all-or-nothing").

EMS:
- SpeakerWorkflowService.runReadyHook captures ProvisionUserResponse.temporaryPassword.
- SpeakerWorkflowService.runInvitedHook drops magic-link token generation; calls
  SpeakerInvitationEmailService with new signature (loginUrl + temporaryPassword + locale).
- SpeakerInvitationEmailService rewires email templates to Cognito-flow variables:
  {{loginUrl}}, {{usernameForLogin}}, {{temporaryPassword}} (Mustache conditional for
  re-invite branch where temp password is null).

Email templates (90 files touched):
- Rewrite speaker-invitation-{de,en}.{html,txt} for Cognito flow (drop magic-link payload).
- Add speaker-invitation-{fr,it,rm,es,fi,nl,ja,gsw-BE}.{html,txt} (16 new files).
- Simplify speaker-acceptance + speaker-reminder-{response,content}-tier{1,2,3} de+en
  (drop tentative-response copy per UX-DR21).
- Add the same simplified templates for 8 new locales (NFR10).

PM-resolved Open Questions:
- Q#1: temp-password lifecycle = <chosen variant> [Variant A/B/C].
- Q#2: translation source = <chosen source>.
- Q#3: .txt parity for acceptance/reminder = <yes/no>.
- Q#4: re-invite UX = <chosen path>.

Manual verification on staging: <paste 4 CloudWatch log lines from AC10 step 5-9>.

Doc alignment (per CLAUDE.md doc-drift policy, same commit):
- docs/architecture/ADR-009-unified-speaker-workflow.md — Implementation Guidelines + new
  revision-history row.
- docs/architecture/06-backend-architecture.md — Speaker authentication section.
- docs/architecture/06a-workflow-state-machines.md — Callers of transition().

Depends on: Story 11.E.1 (CDK + IAM), Story 11.B.2 (sole-writer seam), Story 11.C.2
(UserApiClient.provisionUserWithRole contract).
```

14.2. Push the branch and open a PR against `feature/speaker-workflow-refactor`. The PR description must include:
   - The four PM-resolved Open Questions (Q#1 through Q#4) with the resolution one-liners.
   - The manual-verification evidence from AC10 (CloudWatch log lines + re-invite negative-case observation).
   - The Variant-letter chosen for Open Q#1 and a one-paragraph justification.
   - A "Translation source" section per Open Q#2 — who reviewed each locale, and what the source was (DeepL, GPT-4, native speaker).
   - A "Doc alignment" section listing the architecture-doc edits from Task 13.

### Task 15 — Mark `sprint-status.yaml` `review` once the PR is open

15.1. Update `_bmad-output/implementation-artifacts/sprint-status.yaml`: set `11-e-2-cognito-provisioning-at-ready-invitation-email-i18n` from `ready-for-dev` (set by this story-creation) → `in-progress` (when the dev starts coding) → `review` (when the PR opens). Bump `last_updated`.

---

## Dev Notes

### Why this story is large

This story is **substantially larger** than 11.E.1 (the IAM prereq, ~15-25 LOC) — it spans CUMS Cognito wiring (Tasks 1-3), EMS workflow + email service rewires (Tasks 4-6), 90 email-template files across 10 locales (Tasks 7-9), OpenAPI + Bruno + manual verification (Tasks 10-13), and doc alignment (Task 14). The dev should expect 4-7 days of focused work, with translation review (Task 9) being the rate-limiting step.

If PM wants to ship the Cognito wiring sooner, the story could be split:
- **11.E.2a**: Cognito wiring + temp-password flow + en/de template rewrite (Tasks 1-7 + 10-13). Ships the technical Cognito flow on 2 locales.
- **11.E.2b**: 8-locale i18n expansion for all template families (Tasks 8-9). Pure content; can land later.

This split is **NOT** part of the AC — it's a deferred-work suggestion if PM wants to unblock 11.E.3 sooner. The dev should default to shipping 11.E.2 as one story unless PM explicitly opts in to the split before development starts.

### The four Cognito SDK calls in scope vs. NOT in scope

This story uses **at most two** of the four Cognito permissions granted by 11.E.1:

| Cognito API | Story 11.E.2 use | Granted by 11.E.1? |
|---|---|---|
| `AdminCreateUser` | ✅ Always — at `CONTACTED → READY` provisioning, new-user branch | ✅ Yes |
| `AdminSetUserPassword` | ⏳ Used only if Open Q#1 resolves to Variant B (reset temp password at INVITED) | ✅ Yes |
| `AdminInitiateAuth` | ❌ Not used here — the speaker authenticates from the frontend (Story 11.E.3) | ✅ Yes (for 11.E.3) |
| `AdminGetUser` | ❌ Not used here — informational only; could be used to detect existing-Cognito-user state if Open Q#4 resolves toward that path | ✅ Yes |

The IAM permissions in 11.E.1 are deliberately scoped to support all three Phase-E stories; this story exercises only the subset needed for provisioning.

### Why no `AdminAddUserToGroup`

Per ADR-009 v1.3 (revision-history row 2026-05-17, lines 567-569) and 11.E.1's Resolved Q#1, BATbern's Cognito User Pool has NO groups (see `infrastructure/lib/stacks/cognito-stack.ts` lines 277-279, "REMOVED: Cognito Groups (Story 1.2.6: ADR-001 Database-centric architecture)"). The SPEAKER role lives in PostgreSQL `user_roles`, granted by `RoleService.addRole(...)` at line 714 of `UserService.provisionUserWithRole`. The pre-token-generation Lambda picks up `user_roles` at login time and adds it to the JWT — speaker `@PreAuthorize("hasRole('SPEAKER')")` checks at the controller layer (added in Story 11.E.3) will work without any Cognito-side group operation.

### Why the email is sent inside the @Transactional boundary (and why that's accepted)

`SpeakerWorkflowService.runInvitedHook` currently sends the invitation email synchronously inside the transaction (see the explicit code comment at lines 295-297 of `SpeakerWorkflowService.java`). If the transition rolls back AFTER the email send, the email has already gone out — a "leak" of the invitation. The 11.B.2 code review flagged this for migration to `@TransactionalEventListener(phase = AFTER_COMMIT)` in deferred-work (per the project's `deferred-work.md`).

**This story does NOT fix that.** Mitigating the email-after-rollback risk is orthogonal to the Phase-E rewire and stays in deferred-work. The dev keeps the existing behaviour — the same risk applies to today's magic-link email; swapping the payload to Cognito doesn't materially change the failure mode.

### Why CUMS does the Cognito call (and not EMS)

ADR-009 §Decision 3 + the refactor plan §3.2 + Story 11.C.2 all place the Cognito SDK call on the **CUMS task role** (per Story 11.E.1's IAM grant — note that the IAM perms went to `company-management-stack.ts`, not `event-management-stack.ts`). The architectural reason: Cognito User Pool is the User domain's responsibility (CUMS owns Users); EMS owns Speakers + Workflow but does not own User identity. Cross-service HTTP from EMS → CUMS for provisioning is the right boundary.

Practical implication: the dev does NOT add `cognitoClient` or `CognitoIntegrationService` to EMS. The only Cognito-related code in EMS is the `temporaryPassword` field on `ProvisionUserResponse` (returned by `UserApiClient.provisionUserWithRole`) — which is just a DTO field, not a Cognito SDK call.

### Project Structure Notes

Primary files under change in this story:

**CUMS:**
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/UserService.java` (MODIFIED — wire Cognito on new-user path; add `@Transactional`)
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/CognitoIntegrationService.java` (MODIFIED — add `adminCreateUserWithTemporaryPassword` to interface)
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/CognitoIntegrationServiceImpl.java` (MODIFIED — implement real Cognito call; drop `unused` suppression)
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/PasswordGenerator.java` (NEW)
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/exception/CognitoOperationException.java` (NEW)
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/exception/GlobalExceptionHandler.java` (MODIFIED — new exception → 502)
- `services/company-user-management-service/src/test/java/ch/batbern/companyuser/service/PasswordGeneratorTest.java` (NEW)
- `services/company-user-management-service/src/test/java/ch/batbern/companyuser/service/UserServiceIntegrationTest.java` (MODIFIED — extend with AC11 items 1-4)

**EMS:**
- `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerWorkflowService.java` (MODIFIED — capture tempPassword in `runReadyHook`; rewire `runInvitedHook`; Variant-A-or-B stash mechanism per Open Q#1)
- `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerInvitationEmailService.java` (MODIFIED — new signature; drop magic-link variables; populate Cognito-flow variables)
- `services/event-management-service/src/test/java/ch/batbern/events/service/SpeakerWorkflowServiceIntegrationTest.java` (MODIFIED — extend with AC11 items 5-8)
- `services/event-management-service/src/test/java/ch/batbern/events/service/SpeakerInvitationEmailServiceTest.java` (NEW or MODIFIED — add AC8 item 4 cases)

**Email templates** (under `services/event-management-service/src/main/resources/email-templates/`):
- `speaker-invitation-{de,en}.{html,txt}` (4 files, MODIFIED — Cognito-flow rewrite)
- `speaker-invitation-{fr,it,rm,es,fi,nl,ja,gsw-BE}.{html,txt}` (16 files, NEW)
- `speaker-acceptance-{de,en}.html` (2 files, MODIFIED — strip tentative copy)
- `speaker-acceptance-{fr,it,rm,es,fi,nl,ja,gsw-BE}.html` (8 files, NEW)
- `speaker-reminder-response-tier{1,2,3}-{de,en}.html` (6 files, MODIFIED)
- `speaker-reminder-response-tier{1,2,3}-{fr,it,rm,es,fi,nl,ja,gsw-BE}.html` (24 files, NEW)
- `speaker-reminder-content-tier{1,2,3}-{de,en}.html` (6 files, MODIFIED — verify no tentative copy; otherwise no-op edits)
- `speaker-reminder-content-tier{1,2,3}-{fr,it,rm,es,fi,nl,ja,gsw-BE}.html` (24 files, NEW)

**Specs + docs:**
- `docs/api/users-api.openapi.yml` (MODIFIED — `temporaryPassword` description; path-level description)
- `web-frontend/src/types/generated/users-api.types.ts` (REGENERATED via `npm run generate:api-types:users`)
- `docs/architecture/ADR-009-unified-speaker-workflow.md` (MODIFIED — Implementation Guidelines + new revision-history row)
- `docs/architecture/06-backend-architecture.md` (MODIFIED — Speaker authentication section)
- `docs/architecture/06a-workflow-state-machines.md` (MODIFIED — Callers-of-transition section)

**Bruno:**
- `bruno-tests/users-api/provision-user.bru` (MODIFIED — assert tempPassword shape; second-call idempotency)

**No files under change in:** `web-frontend/src/` (any frontend changes are Story 11.E.3), `infrastructure/` (any CDK changes are Story 11.E.1), `services/speaker-coordination-service/` (deleted in Story 11.C.1), shared-kernel (the existing `SpeakerPromotedToReadyEvent` is sufficient).

### Testing Standards

- **Backend integration tests** extend `AbstractIntegrationTest` (Testcontainers PostgreSQL, mandatory per project-context.md). Cognito calls are stubbed via Mockito on `CognitoIntegrationService` (CUMS) or WireMock on the `/api/v1/users/provision` HTTP boundary (EMS).
- **Unit tests** use Vitest equivalent for Java (JUnit 5) + Mockito. `PasswordGeneratorTest` is a pure unit test (no Spring context).
- **Bruno contract tests** assert the OpenAPI shape only — no business-logic assertions. The `tempPassword` regex assertion in AC13 is the strongest contract check.
- **Manual verification** at AC10 is the only test that exercises the real Cognito user pool. The dev runs it AFTER the PR is approved and the staging deploy is complete, then pastes the evidence into the PR description before merging to develop / main.

### Cherry-pick mechanics: none required

Unlike Story 11.E.1, this story does NOT cherry-pick from any existing branch. The Cognito-flow design is **net-new** to BATbern (the magic-link flow on `feature/epic-6` was the previous implementation; this story replaces it). The dev should not look for an existing `provisionUserWithRole-with-Cognito` implementation — it doesn't exist; this is the first one.

### References

- [Source: docs/prd/epic-11-speaker-workflow-refactor.md lines 1176-1249] — Story 11.E.2 AC list (this story's primary spec).
- [Source: docs/prd/epic-11-speaker-workflow-refactor.md lines 27-129] — FR3, FR9, NFR2, NFR3, NFR5, NFR9, NFR10 definitions.
- [Source: docs/prd/epic-11-speaker-workflow-refactor.md lines 160-167] — AR12-AR15 (CUMS architecture requirements).
- [Source: docs/prd/epic-11-speaker-workflow-refactor.md lines 304-322] — UX-DR21, UX-DR22 (email + i18n requirements).
- [Source: docs/architecture/ADR-009-unified-speaker-workflow.md §Decision 3, lines 254-298] — Cognito provisioning architectural decision.
- [Source: docs/architecture/ADR-009-unified-speaker-workflow.md §Implementation Guidelines, lines 488-515] — `SpeakerProvisioningService.provisionForSpeaker` reference design.
- [Source: docs/architecture/ADR-009-unified-speaker-workflow.md Revision History v1.3, line 567] — Resolved Q#1 from 11.E.1 (drop AdminAddUserToGroup).
- [Source: docs/plans/speaker-workflow-refactor.md §0.5 "Authentication"] — Cognito with forced password change architectural decision.
- [Source: docs/plans/speaker-workflow-refactor.md §2.3 "State-machine consolidation"] — side-effect hooks at READY + INVITED.
- [Source: docs/plans/speaker-workflow-refactor.md §3.2 "company-user-management-service"] — AR15 (Cognito provisioning logic added).
- [Source: services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/UserService.java lines 620-721] — current `provisionUserWithRole` stub with three explicit `Story 11.E.2 wires Cognito` markers.
- [Source: services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/CognitoIntegrationServiceImpl.java] — current NO-OP impl; this story converts to real call sites.
- [Source: services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerWorkflowService.java lines 250-305] — `runReadyHook` + `runInvitedHook` current state; this story extends both.
- [Source: services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerInvitationEmailService.java] — current signature uses magic-link tokens; this story replaces.
- [Source: services/event-management-service/src/main/resources/email-templates/speaker-invitation-en.html] — current invitation template content; this story's reference for what the rewrite replaces.
- [Source: web-frontend/public/locales/] — directory listing confirms the 10 supported locales (de, en, es, fi, fr, gsw-BE, it, ja, nl, rm).
- [Source: _bmad-output/implementation-artifacts/11-e-1-cdk-iam-prereq-cognito-admin-flow.md] — the IAM prereq story that this story consumes. Resolved Q#1 (no AdminAddUserToGroup) + Q#4 (14-day tempPasswordValidity) are inherited as binding decisions.
- [Source: _bmad-output/implementation-artifacts/11-b-2-speakerworkflowservice-sole-status-writer.md] — the workflow-service seam this story extends.
- [Source: _bmad-output/implementation-artifacts/11-c-2-userapiclient-provisioning-contentsubmissionservice-shared.md] — the `provisionUserWithRole` contract this story populates with real Cognito wiring.
- [Source: _bmad-output/project-context.md §"Authentication & Roles"] — "Roles stored exclusively in PostgreSQL `role_assignments` — NOT in Cognito groups" + "JWT claim for roles is `custom:role`" — both binding for AC3's no-`AdminAddUserToGroup` invariant.
- [Source: _bmad-output/project-context.md §"Backend Integration Tests — Critical Requirements"] — `AbstractIntegrationTest` mandate for integration tests.
- [Source: CLAUDE.md §"Doc Drift Prevention"] — Update related docs in the same commit as the code change.
- [Source: CLAUDE.md §"ECS Deploy Patience" (via memory)] — Don't cancel CFN deploys; rolling deploys can take 20-30 min.

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

## Open Questions

The four questions below need PM (Nissim) resolution **before** development starts. The story's AC reflect the recommended resolution where applicable; alternative resolutions are listed inside each question. Plain language, in the 11-A-1 style — each item is a discussion prompt for the PM, not a multi-page rationale.

### Q#1 — How does the temporary password survive the READY → INVITED gap?

The provisioning happens at READY (per FR3). The invitation email is sent at INVITED (per FR9 + the existing `runInvitedHook` seam). These are two separate organizer actions on the kanban — minutes, hours, or days apart. The temporary password is generated at READY but consumed at INVITED. PM needs to pick the storage mechanism:

- **Variant A — Caffeine cache (recommended for simplicity).** EMS holds an in-memory cache keyed by `speakerPoolId`, TTL = 14 days matching `tempPasswordValidity`. `runReadyHook` puts; `runInvitedHook` gets-and-evicts. Cost: lost on EMS restart between READY and INVITED — the dev would have to manually trigger an AdminSetUserPassword reset in that case (or simply re-promote the speaker, which the kanban allows).
- **Variant B — AdminSetUserPassword at INVITED (recommended for clean separation).** Drop the temp password from the READY-time provisioning response (it stays null in `ProvisionUserResponse`); add a new CUMS endpoint that EMS calls at INVITED time, which runs `AdminSetUserPassword(Permanent=false)` and returns a fresh temp password. No state across transitions; one extra Cognito call per invitation; the AC1 wording in the PRD needs a one-line edit in the same commit.
- **Variant C — Combine READY + INVITED into one organizer action.** Make the kanban "Promote to speaker" button do CONTACTED → READY → INVITED in one click. Removes the gap entirely. But it also removes the kanban-state granularity that Stories 11-D-2 and 11-D-3 introduced — and those stories are `done` / `review` today. PM would be reverting a UX decision.

**Recommended:** Variant B. It cleanly avoids any local state, matches the existing CUMS-owns-Cognito boundary, uses the AdminSetUserPassword permission that 11.E.1 already granted, and the one-line PRD edit is well within doc-drift policy.

### Q#2 — Where do the translations for the 8 new locales come from?

Story scope adds 8 new locales (fr, it, rm, es, fi, nl, ja, gsw-BE) for four template families (invitation, acceptance, response-reminder × 3 tiers, content-reminder × 3 tiers). That's 72-80 new template files. The English wording is short (≈200 words per template), but it's user-facing copy that lands in invitation emails to real speakers.

Three plausible translation sources:

- **Hand-translated by native speakers.** Highest quality; takes the longest. BATbern may already have community contributors for some of the 8 locales (the frontend i18n was done somehow — Story 10-9's i18n cleanup memory note suggests the team can source translations).
- **Machine translation (DeepL or GPT-4) with one-pass human review.** Fast; reasonable quality for short, formal email copy; the dev does the review pass.
- **English fallback for non-DE/EN locales.** Ship 10 locales of code but only ship DE + EN content for now; the other 8 fall back to English at render time. Lowest effort; punts the i18n parity NFR (NFR10) to a follow-up story.

**Recommended:** Machine translation + human review (option B). Email copy is formal and short — a competent MT engine handles it well, and one-pass human review catches the egregious errors. PM should also decide whether the dev acts as the reviewer or delegates to community contributors per locale.

### Q#3 — Do the acceptance + reminder templates need `.txt` parity, or HTML-only is fine?

Today's templates have asymmetric formats:
- `speaker-invitation-{de,en}.{html,txt}` — both formats.
- `speaker-acceptance-{de,en}.html` — HTML only, no `.txt`.
- `speaker-reminder-response-tier{1,2,3}-{de,en}.html` — HTML only.
- `speaker-reminder-content-tier{1,2,3}-{de,en}.html` — HTML only.

The asymmetry suggests the invitation is special (some email clients fall back to plain text and the temp password should be readable there), while reminders are HTML-only because they're triaged by SES/the email client anyway. PM should confirm:

- **Keep the asymmetry (HTML-only for acceptance + reminders, both formats for invitation).** Recommended — matches current pattern; reduces total template count from ~90 to ~80.
- **Add `.txt` parity for all four families.** Pedantic correctness; adds ~50 more files. Operational value is marginal — modern email clients all render HTML.

**Recommended:** Keep the asymmetry. The story's AC9 file count assumes this resolution.

### Q#4 — Re-invite UX: how does EMS detect an already-provisioned speaker?

AC8 specifies that the invitation email omits the temporary-password block when the speaker is already a Cognito user (the conditional `{{#temporaryPassword}}` Mustache section). But how does EMS know? Two paths:

- **`UserApiClient.provisionUserWithRole` returns `temporaryPassword=null` for existing users.** Per AC1's "existing user idempotent path" — the CUMS `UserService.provisionUserWithRole` checks `userRepository.findByEmailIgnoreCase(email).isPresent()` and returns null for the temp password if so. EMS passes the null through to `SpeakerInvitationEmailService.sendInvitationEmail`; the conditional renders the "use your existing password" branch.
  - **Gap:** if a speaker's Cognito user exists but they haven't logged in yet (status still `FORCE_CHANGE_PASSWORD`), the null branch is the WRONG behaviour — they need a fresh temp password, not a "use your existing password" note. The User row exists in PostgreSQL but the speaker never set a password.
- **EMS detects via Cognito's `AdminGetUser` status.** Before sending the email, EMS asks CUMS "is this user in FORCE_CHANGE_PASSWORD state?" — if yes, generate a fresh temp password (via AdminSetUserPassword from Variant B above); if no (status = CONFIRMED), use the "use your existing password" branch.
  - **Cost:** one extra Cognito call per invitation; couples EMS to a Cognito-specific status; the additional `AdminGetUser` IAM permission was granted by 11.E.1 specifically for this kind of check.

**Recommended:** The second approach (AdminGetUser + status-driven branching) — more robust and uses the IAM permission 11.E.1 granted. If Variant B from Q#1 is chosen, this fits naturally: `runInvitedHook` calls a single CUMS endpoint that internally does AdminGetUser + AdminSetUserPassword (conditional) + returns either a fresh temp password OR a "use existing password" signal.

If PM picks Variant A from Q#1, then this question collapses to the first sub-bullet (status null = no temp password) with the noted gap left as deferred-work.

---

_Story created via `bmad-create-story` skill on 2026-05-17. Four Open Questions surfaced for PM resolution. **The story is `ready-for-dev` pending Open Question resolution** — the dev should re-read the affected AC and Tasks after PM picks variants for Q#1 through Q#4, then proceed via `bmad-dev-story`. The story's primary scope (Cognito provisioning + invitation-email rewrite + 10-locale fan-out) is unchanged regardless of variant; only the implementation details inside Tasks 4, 5, 7-9 shift._
