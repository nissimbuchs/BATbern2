# Doc Audit Findings — User Lifecycle Sync
**Audited:** 2026-03-09
**Doc:** `docs/architecture/06b-user-lifecycle-sync.md`
**Tests searched:** `services/company-user-management-service/src/test/java`

## Summary
- VALIDATED: 9
- MISMATCH: 6
- UNTESTED: 7
- UNDOCUMENTED: 9

---

## MISMATCH

### M1 — JIT Provisioning is explicitly declared absent, but exists as a core component

**Doc claims:** "❌ No JIT (Just-In-Time) Provisioning — Reason: PostConfirmation Lambda creates database users automatically. No users exist in Cognito without database records. Alternative: PostConfirmation handles all user creation."

**Test asserts:** `JITUserProvisioningInterceptorTest` — 14 tests covering `JITUserProvisioningInterceptor.preHandle()` which provisions users on first authenticated API request (creates DB record from JWT claims if `findByCognitoUserId()` returns empty). Tests include role assignment from JWT authorities, username collision handling, non-blocking error behaviour, and event publishing with `source = "JIT_PROVISIONING"`.

**Action:** Replace the "❌ No JIT Provisioning" section with a description of Pattern 1b: JIT Provisioning Interceptor, which runs on every authenticated API request and creates a DB user if none exists. PostConfirmation Lambda is the primary path; JIT is the safety net.

---

### M2 — Reconciliation Jobs are declared absent, but a full reconciliation service exists

**Doc claims:** "❌ No Reconciliation Jobs — Reason: Unidirectional sync eliminates drift. Database never needs to sync back to Cognito. Alternative: None needed."

**Test asserts:** `UserReconciliationServiceTest` — 15 tests for `UserReconciliationService.reconcileUsers()` and `checkSyncStatus()`. The service iterates all active DB users, checks each against Cognito via `adminGetUser`, deactivates orphans (sets `isActive = false`, `deactivationReason = "Cognito user deleted"`), and creates missing users found in Cognito but absent from DB. Reports `orphanedUsers`, `missingUsers`, duration, and errors. Publishes metrics via `UserSyncMetricsService`.

**Action:** Remove "❌ No Reconciliation Jobs". Add a section describing the reconciliation job, its bidirectional checks, and the deactivation/creation logic.

---

### M3 — PreTokenGeneration Lambda documented as only adding `custom:role`; tests show `custom:username` is also expected

**Doc claims:** PreTokenGeneration code only adds `'custom:role': roles.join(',')` to the JWT. The JWT example contains: `sub`, `cognito:username`, `email`, `custom:role`, `custom:language`, `iss`, `exp`, `iat`.

**Test asserts:** `SecurityContextHelperTest#should_extractUsername_when_customUsernameClaimPresent` — reads `custom:username` claim from JWT and returns it as the current user's username. Comment: `"ADR-001: PreTokenGeneration Lambda sets custom:username claim from database"`. Also `SecurityContextHelperTest#should_extractCompanyId_when_presentInToken` reads `custom:companyId`.

**Action:** Update PreTokenGeneration Lambda code snippet and JWT example to include `custom:username` (DB username) and `custom:companyId` claims. Update the "JWT Token Example" block accordingly.

---

### M4 — Schema says `cognito_user_id NOT NULL`; tests prove null is valid for pre-invited users

**Doc claims:** "cognito_user_id is NOT NULL for self-registered users (always populated by PostConfirmation)" and the schema shows `cognito_user_id VARCHAR(255) NOT NULL UNIQUE`.

**Test asserts:**
- `JITUserProvisioningInterceptorTest#should_linkCognitoIdToExistingUser_when_emailAlreadyExistsInDatabase` — creates `preExistingUser = createUser(null, "partner.user", email, ...)` with `cognitoUserId = null` stored in DB. The interceptor then links the Cognito ID to this existing record on first login.
- `UserReconciliationServiceTest#should_skipUsers_when_noCognitoUserId` — creates a user with `cognitoUserId(null)` in the DB and asserts it is skipped during the orphan check (`verify(cognitoClient, never()).adminGetUser(...)`).

**Action:** Change the schema comment to reflect that `cognito_user_id` is nullable (pre-invited users), and add a note that users without a `cognito_user_id` are skipped in reconciliation and linked on first login via the JIT interceptor.

---

### M5 — SecurityConfig declares `anyRequest().authenticated()`; company search is publicly accessible without auth

**Doc claims:** SecurityConfig code snippet (Pattern 3) shows:
```java
.anyRequest().authenticated()
```
implying all unlisted routes require authentication.

**Test asserts:** `AuthenticationIntegrationTest#should_allowSearch_when_notAuthenticated` (Test 10.9) — performs `GET /api/v1/companies/search` without any authentication and asserts `status().isOk()`. Comment: "Story 4.1.5: Company search is now public for registration autocomplete".

**Action:** Update the SecurityConfig code snippet in the doc to add an explicit `permitAll()` rule for the company search endpoint (e.g., `"/api/v1/companies/search"`), before the `anyRequest().authenticated()` catch-all.

---

### M6 — Doc omits that DB-Cognito sync is not strictly unidirectional; reconciliation deactivates DB users based on Cognito state

**Doc claims:** "Unidirectional Sync: Cognito → Database only (via Lambda triggers)" and "No Bidirectional Sync (Database → Cognito)".

**Test asserts:** `UserReconciliationServiceTest#should_deactivateOrphanedUsers_when_usersInDbNotInCognito` — the reconciliation service reads Cognito user state and writes it back to the DB (setting `isActive = false`). This is still a Cognito→DB direction, but it involves Cognito acting as the authoritative source to drive DB mutations (deactivations). The doc's framing of "no reconciliation jobs" is incorrect (see M2), and the "unidirectional" claim needs qualification: there is a reconciliation job that reads Cognito and mutates the DB.

**Action:** Qualify "Unidirectional Sync" to clarify it means no reverse sync (DB→Cognito), but a reconciliation job does read Cognito state to deactivate orphaned DB users.

---

## UNTESTED

### U1 — PostConfirmation Lambda performance target: < 1s p95

**Doc claims:** "Performance: Completes within 1 second (p95 latency requirement)"

**Risk:** Low — this is an operational SLA, not a business rule. Unlikely to be wrong, but no test validates it.

---

### U2 — PreTokenGeneration Lambda performance target: < 500ms p95

**Doc claims:** "Performance: Completes within 500ms (p95 latency requirement)"

**Risk:** Low — same as U1.

---

### U3 — Lambda timeout values (10s / 5s)

**Doc claims:** "Lambda timeout: 10s for PostConfirmation, 5s for PreTokenGeneration"

**Risk:** Low — infrastructure config, not validated by unit/integration tests.

---

### U4 — CloudWatch alarm thresholds

**Doc claims:** "`HighUserCreationFailureRate` — Triggers when failures exceed 5 per 5-minute window" and "`HighLambdaLatency` — Triggers when latency exceeds 2 seconds (average over 5 minutes)"

**Risk:** Low — CDK config, no unit test coverage. Alarm thresholds may have drifted from doc.

---

### U5 — Database connection pool: max 2, 30s idle timeout, 5s connection timeout

**Doc claims:** Lambda DB pool `max: 2`, `idleTimeoutMillis: 30000`, `connectionTimeoutMillis: 5000`.

**Risk:** Low — Lambda-level config, not covered by Java tests.

---

### U6 — `is_active` flag checked by application logic to block inactive users

**Doc claims:** "No PreAuthentication Trigger — Alternative: Application logic checks `is_active` flag in database."

**Risk:** Medium — the reconciliation service deactivates users, but no test validates that deactivated users are actually blocked from API access. The enforcement path is untested.

---

### U7 — Provisioned concurrency for PostConfirmation and PreTokenGeneration Lambdas

**Doc claims:** "Provisioned concurrency for PostConfirmation and PreTokenGeneration Lambdas"

**Risk:** Low — infrastructure config, no unit test coverage.

---

## UNDOCUMENTED

### N1 — `custom:username` JWT claim set by PreTokenGeneration Lambda

**Test:** `SecurityContextHelperTest#should_extractUsername_when_customUsernameClaimPresent` — asserts that `SecurityContextHelper.getCurrentUsername()` reads the `custom:username` claim (e.g., `"john.doe"`) from the JWT. Comment attributes this to ADR-001: "PreTokenGeneration Lambda sets custom:username claim from database".

**Action:** Add `custom:username` to the JWT Token Example block and to the PreTokenGeneration Lambda implementation snippet in the doc. Describe fallback to `sub` when the claim is absent (validated by `should_fallbackToSubject_when_customUsernameClaimMissing`).

---

### N2 — `custom:companyId` JWT claim

**Test:** `SecurityContextHelperTest#should_extractCompanyId_when_presentInToken` — reads `custom:companyId` from JWT. `should_returnNull_when_companyIdNotInToken` confirms it's optional.

**Action:** Add `custom:companyId` to the JWT Token Example and PreTokenGeneration description.

---

### N3 — Minimum 2 organizers business rule enforced at role removal

**Test:** `RoleServiceTest#should_throwMinimumOrganizersException_when_removingOrganizerAndOnlyOneRemains` — throws `MinimumOrganizersException` with message "minimum of 2 organizers" when removing the last organizer. Also enforced by `setRoles` (`should_throwMinimumOrganizersException_when_setRolesWouldRemoveLastOrganizer`).

**Action:** Add a "Business Rules" section to the doc (or expand the role_assignments section) describing the minimum 2 organizers constraint.

---

### N4 — `deactivationReason` field on user entity

**Test:** `UserReconciliationServiceTest#should_deactivateUser_when_orphanDetected` — asserts `user.getDeactivationReason() == "Cognito user deleted"`.

**Action:** Add `deactivation_reason` column to the `user_profiles` schema block.

---

### N5 — Users with null `cognito_user_id` are skipped in reconciliation

**Test:** `UserReconciliationServiceTest#should_skipUsers_when_noCognitoUserId` — user with `cognitoUserId = null` is skipped; `adminGetUser` is never called for it.

**Action:** Add a note to the Reconciliation section: users without a Cognito ID (pre-invited, not yet linked) are excluded from the orphan check.

---

### N6 — `activity_history` table exists in the schema

**Test:** `UserManagementMigrationsTest#should_createActivityHistoryTable_when_migrationsRun` — asserts the `activity_history` table exists after migrations.

**Action:** Add `activity_history` table to the Database Schema section.

---

### N7 — Additional notification preference columns not in doc schema

**Test:** `UserManagementMigrationsTest#should_haveEmbeddedPreferencesColumns_when_userProfilesTableExists` — asserts columns `pref_in_app_notifications`, `pref_push_notifications`, `pref_notification_frequency` exist, in addition to `pref_theme`, `pref_language`, `pref_email_notifications`.

**Action:** Add the three missing `pref_*` columns to the `user_profiles` schema block in the doc.

---

### N8 — Additional settings columns not in doc schema

**Test:** `UserManagementMigrationsTest#should_haveEmbeddedSettingsColumns_when_userProfilesTableExists` — asserts columns `settings_show_email` and `settings_show_company` exist, which are not in the doc's `user_profiles` CREATE TABLE block.

**Action:** Add `settings_show_email` and `settings_show_company` columns to the `user_profiles` schema block.

---

### N9 — JIT provisioning assigns roles from JWT authorities, not always ATTENDEE

**Test:** `JITUserProvisioningInterceptorTest#should_assignRoleFromJWT_when_jitProvisioningUser` — when a new user is JIT-provisioned and the JWT carries `ROLE_ORGANIZER`, the created user gets `Role.ORGANIZER`. `should_assignMultipleRoles_when_userHasMultipleAuthorities` confirms multi-role assignment. `should_defaultToAttendeeRole_when_noRolesInJWT` confirms ATTENDEE fallback when JWT has no roles.

**Action:** Once M1 (JIT section) is added to the doc, document that JIT-provisioned users receive roles extracted from the JWT's `GrantedAuthority` list, defaulting to ATTENDEE when no roles are present.

---

## VALIDATED
- "Roles stored in `role_assignments` table" → `RoleServiceTest` operates exclusively on the `roles` set via `UserRepository`; no Cognito group calls made
- "JWT claim `custom:role` with comma-separated values" → `SecurityContextHelperTest#should_extractUserRoles_when_authenticated` parses `"ORGANIZER,SPEAKER"` into 2 entries; whitespace trimmed (`should_trimRoles_when_roleClaimHasWhitespace`)
- "Empty roles returned on DB error in PreTokenGeneration" → `SecurityContextHelperTest#should_returnEmptyList_when_roleClaimMissing` and `should_returnEmptyList_when_roleClaimEmpty`
- "Role enum: ORGANIZER, SPEAKER, PARTNER, ATTENDEE (exactly 4)" → `RoleTest#should_haveAllRoles_when_enumDefined`
- "Default ATTENDEE role assigned on JIT user creation (no JWT roles)" → `JITUserProvisioningInterceptorTest#should_defaultToAttendeeRole_when_noRolesInJWT`
- "Username format `firstname.lastname`, numeric suffix for duplicates (`john.doe.2`)" → `JITUserProvisioningInterceptorTest#should_addNumericSuffix_when_usernameAlreadyExists`; `UserManagementMigrationsTest#should_enforceUsernameFormat_when_invalidUsernameInserted` (rejects `john.doe.abc`)
- "Non-blocking JIT provisioning error handling" → `JITUserProvisioningInterceptorTest#should_continueRequest_when_jitProvisioningFails` and `should_continueRequest_when_userSaveFails`
- "`role_assignments` cascade deletes when user deleted" → `UserManagementMigrationsTest#should_cascadeDeleteRoles_when_userDeleted`
- "ORGANIZER-only endpoints for admin operations; ATTENDEE can read" → `AuthenticationIntegrationTest` (Tests 10.1–10.8): ORGANIZER creates/updates, SPEAKER forbidden to update, ATTENDEE allowed to list/search
