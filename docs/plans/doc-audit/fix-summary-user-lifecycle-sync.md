# Fix Summary — User Lifecycle Sync
**Fixed:** 2026-03-09
**Doc:** `docs/architecture/06b-user-lifecycle-sync.md`

## Changes made

### Mismatches

- **M1**: Replaced "❌ No JIT Provisioning" stub with a full description of `JITUserProvisioningInterceptor` (Pattern 1b). Documents: safety-net role, role extraction from JWT authorities (with ATTENDEE default), pre-invited-user linking on first login, non-blocking error handling, and event publishing with `source = "JIT_PROVISIONING"`.

- **M2**: Replaced "❌ No Reconciliation Jobs" stub with a description of `UserReconciliationService`. Documents: `reconcileUsers()` and `checkSyncStatus()`, orphan deactivation (`is_active = false`, `deactivation_reason = "Cognito user deleted"`), missing-user creation, null-`cognito_user_id` skip rule, and `UserSyncMetricsService` publishing.

- **M3**: Updated PreTokenGeneration Lambda code snippet to also add `custom:username` and `custom:companyId` claims to the JWT (in addition to `custom:role`). Updated the JWT Token Example to include those two fields. Added a "JWT Claim Notes" block explaining semantics and fallback behaviour.

- **M4**: Changed `cognito_user_id VARCHAR(255) NOT NULL UNIQUE` to nullable in the `user_profiles` schema block. Updated Key Points to reflect that pre-invited users have `NULL` until first login (JIT linking) and that those users are skipped during reconciliation. Removed the stale "Migration needed" note from Future Enhancements.

- **M5**: Added `.requestMatchers("/api/v1/companies/search").permitAll()` to the SecurityConfig snippet (before the `anyRequest().authenticated()` catch-all), with a comment referencing Story 4.1.5.

- **M6**: Updated "Unidirectional Sync" bullet in Architecture Decision and Design Principles to clarify that "unidirectional" means no DB→Cognito writes; a reconciliation job does read Cognito to mutate the DB. Renamed "❌ No Bidirectional Sync" to "❌ No Reverse Sync (Database → Cognito)" and added a clarification sentence.

### Undocumented behaviours added

- **N1**: Added `custom:username` to the JWT Token Example and PreTokenGeneration code snippet. Added fallback-to-`sub` note in JWT Claim Notes block.

- **N2**: Added `custom:companyId` to the JWT Token Example and PreTokenGeneration code snippet.

- **N3**: Added "Business Rules" section with the minimum-2-organizers constraint enforced by `RoleService` (`MinimumOrganizersException`).

- **N4**: Added `deactivation_reason VARCHAR(255)` column to the `user_profiles` schema block, with a comment explaining it is set by the reconciliation job.

- **N5**: Documented the null-`cognito_user_id` skip rule in both the Reconciliation section and the Key Points block.

- **N6**: Added `activity_history` table definition to the Database Schema section.

- **N7**: Added three missing preference columns to `user_profiles`: `pref_in_app_notifications`, `pref_push_notifications`, `pref_notification_frequency`.

- **N8**: Added two missing settings columns to `user_profiles`: `settings_show_email`, `settings_show_company`.

- **N9**: Documented JIT role assignment from JWT authorities (with ATTENDEE default) in the new Pattern 1b (JIT Interceptor) section under "What We DON'T Do".

## Skipped — needs manual decision

- **U1**: "Performance: Completes within 1 second (p95 latency requirement)" — no test exists. Operational SLA; unlikely wrong, but untested.
- **U2**: "Performance: Completes within 500ms (p95 latency requirement)" — no test exists. Same as U1.
- **U3**: "Lambda timeout: 10s for PostConfirmation, 5s for PreTokenGeneration" — infrastructure config, no unit test coverage.
- **U4**: CloudWatch alarm thresholds (`HighUserCreationFailureRate` ≥5/5min, `HighLambdaLatency` >2s/5min) — CDK config, no unit test coverage.
- **U5**: DB connection pool (`max: 2`, 30s idle, 5s connect) — Lambda-level config, not covered by Java tests.
- **U6**: "`is_active` flag checked by application logic to block inactive users" — reconciliation deactivates users, but no test validates that deactivated users are blocked from API access. Enforcement path is untested; consider adding an integration test.
- **U7**: "Provisioned concurrency for PostConfirmation and PreTokenGeneration Lambdas" — infrastructure config, no unit test coverage.
