---
title: 'Google SSO login via verified additional email (Epic 12 follow-up)'
type: 'feature'
created: '2026-06-06'
status: 'draft'
context:
  - '{project-root}/_bmad-output/project-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** A user whose private Gmail is registered (and now verifiable, per Story A) as an *additional* email cannot "Continue with Google" — the PreSignUp linker only matches `user_profiles.email`, so Google sign-in JIT-creates a duplicate ATTENDEE user instead of logging into the existing account.

**Approach:** Extend Epic 12 account linking with a fallback lookup on `user_additional_emails`, gated on `verified_at IS NOT NULL` AND the IdP's `email_verified=true` attribute. On match, `AdminLinkProviderForUser` to the owning user — identical to the primary-email path. Defensive twin in the JIT interceptor prevents duplicate creation if linking didn't happen.

## Boundaries & Constraints

**Always:** `pre-signup.ts` must never throw on the federated path (a throw 503s ALL sign-ins) — preserve the existing catch-all + metric pattern. The fallback runs ONLY when the primary `user_profiles.email` match found nothing. Both gates required in the Lambda: DB `verified_at IS NOT NULL` AND `event.request.userAttributes.email_verified === 'true'` (case-insensitive). Lambda handler unit tests that import and run the handler (CDK Template tests don't count). CUMS integration tests via `AbstractIntegrationTest`.

**Ask First:** Any change to the linking destination logic of the existing primary-email path. Any new Cognito API call other than the existing `AdminLinkProviderForUser`. Any schema/migration change.

**Never:** Don't link on UNverified additional emails. In JIT, never overwrite an existing `cognito_user_id` (would break the owner's native login). Don't modify Story A's verification endpoints. No frontend changes. No OpenAPI changes.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Happy link | Google sign-in; gmail = verified additional email of user X | AdminLinkProviderForUser → X; metric `FederatedUserLinkedViaAdditionalEmail` | n/a |
| Unverified | gmail = additional email, `verified_at IS NULL` | NO link; falls through to existing new-user path (JIT provisions separate user) | n/a |
| IdP unverified | `email_verified` attr ≠ 'true' (or absent) | NO additional-email fallback (primary path unchanged) | n/a |
| Primary wins | gmail = some user's PRIMARY email | existing path links as today; fallback never queried | n/a |
| Owner has no Cognito | additional-email owner row has `cognito_user_id NULL` | NO link (no destination); log + existing pending-JIT metric pattern | n/a |
| DB error in fallback | query throws | caught; sign-in proceeds (never throw); `PreSignUpFailure` metric | log error |
| JIT duplicate guard | first API call, JWT email = verified additional email of X (linking didn't happen) | NO new user created; WARN log; request proceeds unresolved | n/a |
| JIT unverified | JWT email = unverified additional email | existing behaviour: JIT creates new user (unchanged) | n/a |

</frozen-after-approval>

## Code Map

- `infrastructure/lib/lambda/triggers/pre-signup.ts:91-205` — `handleFederated()`: primary match at 120-125, three-outcome branch at 128-189, never-throw at 191-204; insert fallback between outcome 1 and 2
- `infrastructure/test/unit/pre-signup.test.ts` — existing handler-level tests to extend (imports + runs handler)
- `services/company-user-management-service/.../interceptor/JITUserProvisioningInterceptor.java:112-160` — `findByCognitoUserId` → `findByEmail` linking → JIT-create; insert guard before create
- `services/company-user-management-service/.../repository/UserAdditionalEmailRepository.java` — add `@Query` finder (JOIN FETCH user) by email ignore-case + verified
- `docs/architecture/ADR-010-federated-identity-via-cognito.md` D3 — linking decision doc; needs an amendment note (doc-drift rule)

## Tasks & Acceptance

**Execution:**
- [ ] `infrastructure/test/unit/pre-signup.test.ts` — RED first: mock pg client; cases = happy link (verified+email_verified → AdminLinkProviderForUser with owner's username), unverified row, email_verified absent/'false', owner without cognito_user_id, fallback query throws → still auto-confirms
- [ ] `infrastructure/lib/lambda/triggers/pre-signup.ts` — after empty primary-match result: if `email_verified === 'true'` (case-insensitive), query `SELECT u.cognito_user_id, u.username FROM user_additional_emails ae JOIN user_profiles u ON u.id = ae.user_id WHERE LOWER(ae.email) = LOWER($1) AND ae.verified_at IS NOT NULL`; on hit with `cognito_user_id` → same AdminLink block as primary path + new metric; on hit without → log, fall through; reuse the single client/finally-release
- [ ] `services/company-user-management-service/.../repository/UserAdditionalEmailRepository.java` — `Optional<UserAdditionalEmail> findVerifiedByEmailIgnoreCase(String email)` via `@Query` with `JOIN FETCH ae.user`
- [ ] `services/company-user-management-service/.../interceptor/JITUserProvisioningInterceptor.java` — before JIT-create: if JWT email matches a verified additional email → skip creation, WARN (masked email) referencing pre-signup linking; never touch owner's `cognito_user_id`
- [ ] `services/company-user-management-service/src/test/...JITUserProvisioning*IntegrationTest` — RED first: duplicate-guard case (no user row created), unverified case (existing create behaviour unchanged)
- [ ] `docs/architecture/ADR-010-federated-identity-via-cognito.md` — amend D3 with the verified-additional-email fallback (same commit, doc-drift rule)

**Acceptance Criteria:**
- Given user X with verified additional email g@gmail.com and a Cognito-linked profile, when a Google sign-in for g@gmail.com fires PreSignUp_ExternalProvider, then AdminLinkProviderForUser is called with X as destination and no new user is provisioned
- Given the same row but `verified_at IS NULL`, when the same sign-in fires, then no link occurs and behaviour equals today's new-user path
- Given the fallback DB query throws, when a federated sign-in fires, then the handler still returns auto-confirmed (never throws)
- Given a JWT whose email is X's verified additional email and no `cognito_user_id` match, when the first API call hits CUMS, then no duplicate user row is created

## Spec Change Log

## Design Notes

JIT guard deliberately does NOT resolve the request to the owning user: granting the unlinked federated session X's identity at service level only (Cognito still split, `JwtRolesConverter` lookups by sub would disagree) creates a half-linked state worse than a degraded session. The guard's only job is preventing the duplicate row; the real link belongs to PreSignUp. Lambda gate uses Cognito's string attribute semantics (`email_verified` arrives as `'true'`/`'false'` strings).

## Verification

**Commands:**
- `cd infrastructure && set -o pipefail; npx jest test/unit/pre-signup.test.ts 2>&1 | tee /tmp/presignup-test.log` — expected: green, new cases included
- `set -o pipefail; ./gradlew :services:company-user-management-service:test 2>&1 | tee /tmp/cums-test-b.log` — expected: BUILD SUCCESSFUL
