---
title: 'Additional-email verification flow (v2 of Story 10.32)'
type: 'feature'
created: '2026-06-06'
status: 'done'
baseline_commit: 'd896fe13'
context:
  - '{project-root}/_bmad-output/project-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** `user_additional_emails.verified_at` (V16) is always NULL — any logged-in user can claim any address with no ownership proof. This blocks the planned SSO-linking-via-additional-email feature (account-takeover vector) and means forwarder/CC fan-out trusts unverified addresses.

**Approach:** On add (and on demand via resend), CUMS emails a signed verification link (HMAC JWT, 48h TTL, stateless — no new table) to the additional address. A public frontend page confirms via POST, setting `verified_at`. Wire the existing "Unverified" pill to real state and add Verified/Resend UI.

## Boundaries & Constraints

**Always:** Contract-first (`docs/api/users-api.openapi.yml` updated before code, regen backend DTOs + frontend types, both committed). TDD with `AbstractIntegrationTest` (PostgreSQL Testcontainers). Email templates DE + EN only; UI i18n keys in all 10 locales. Verification confirm is POST-only (mail scanners prefetch GETs — GET must never mutate). Send failure must never fail/roll back the add. New Flyway migrations only if truly needed (target: none). Mock `EmailService` in all tests — no real outbound email.

**Ask First:** Any new Flyway migration. Any change to Cognito/auth flow (that's Story B). Any new Secrets Manager secret (reuse `WATCH_JWT_SECRET`→`JWT_SECRET` pattern).

**Never:** Don't gate existing fan-out/CC behaviour on `verified_at` in this story (forwarder/CC keep using all emails — behaviour change is out of scope). Don't touch `pre-signup.ts`/JIT (Story B). Don't store tokens in the DB. Don't edit applied migrations V1–V19.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Add email | POST /users/me/additional-emails | 201 + verification email sent async (locale de/en per user pref, else en) | send failure → WARN log, add still succeeds |
| Verify (check) | GET /users/additional-emails/verify?token= | 200 `{email(masked), status}` | invalid/expired → 400 `TOKEN_INVALID`/`TOKEN_EXPIRED` |
| Confirm | POST /users/additional-emails/verify `{token}` | 200, `verified_at` set | row deleted since issuance → 404; expired → 400 |
| Confirm again | already-verified row | 200 idempotent (`alreadyVerified: true`) | n/a |
| Re-added email | row deleted + re-added (new row id) | old token → 404 (token carries row UUID) | n/a |
| Resend | POST /users/me/additional-emails/{email}/resend-verification (auth) | 204 + new email | 404 if not caller's; 409 `ALREADY_VERIFIED` if verified |
| Tampered token | bad signature | 400 `TOKEN_INVALID` | never 500 |

</frozen-after-approval>

## Code Map

- `services/company-user-management-service/.../service/UserService.java:~1352` — `addAdditionalEmail` (hook send here); `UserAdditionalEmailRepository` has `findByUserAndEmailIgnoreCase`
- `services/event-management-service/.../service/ConfirmationTokenService.java` — JJWT HS256 pattern to copy (claims: type + ids, `${jwt.secret}`, TTL property)
- `shared-kernel/.../service/EmailService.java` — `@Service`, already component-scanned by CUMS (`scanBasePackages` includes `ch.batbern.shared`); `{{var}}` template replacement
- `services/event-management-service/src/main/resources/email-templates/` — `{key}-{locale}.html` naming convention
- `api-gateway/.../config/SecurityConfig.java:199-309` + `services/company-user-management-service/.../config/SecurityConfig.java` (both profiles) — permitAll lists; new verify endpoints go in BOTH (dual-SecurityConfig rule)
- `web-frontend/src/pages/public/UnsubscribePage.tsx` — verify→confirm landing-page pattern to mirror
- `web-frontend/src/components/user/UserSettingsTab/UserSettingsTab.tsx:90-336` — AdditionalEmailsSection; unverified pill at 235-241
- `web-frontend/src/hooks/useUserAccount/useUserAccount.ts:159-181` — add/delete mutation hooks; invalidate `['user-profile']`
- `infrastructure/lib/stacks/company-management-stack.ts` — add SES grant (copy partner-coordination-stack.ts:92-103) + `JWT_SECRET` secret mapping (copy event-management-stack.ts:139-143) + `APP_BASE_URL` env (event-management-stack.ts:108)
- `docs/api/users-api.openapi.yml` — contract source of truth

## Tasks & Acceptance

**Execution:**
- [x] `docs/api/users-api.openapi.yml` — add `GET/POST /users/additional-emails/verify` (public, token-credentialed) + `POST /users/me/additional-emails/{email}/resend-verification`; regen: `./gradlew :services:company-user-management-service:openApiGenerateUsers` + `cd web-frontend && npm run generate:api-types:users` — contract-first
- [x] `services/company-user-management-service/.../service/AdditionalEmailVerificationTokenService.java` — NEW: JJWT HS256, claims `{type: additional-email-verification, additionalEmailId, email}`, `${jwt.secret}`, `${batbern.user.additional-emails.verification.token-validity-hours:48}`; CUMS `application.yml` gains `jwt.secret: ${JWT_SECRET:changeme}` — stateless token, row-UUID binding invalidates on delete/re-add
- [x] `services/company-user-management-service/src/main/resources/email-templates/additional-email-verification-{de,en}.html` — NEW: link `{{baseUrl}}/verify-email?token={{token}}`; rendering via EmailService `{{var}}` replacement; user-locale de/en, fallback en
- [x] `services/company-user-management-service/.../service/UserService.java` — send on add (async-safe, failure tolerated); `resendVerification` (404/409 rules); `verifyAdditionalEmail(token)` (load row by UUID, match email ignore-case, set `verified_at` if null, idempotent)
- [x] `services/company-user-management-service/.../controller/UserController.java` + both SecurityConfigs (CUMS local+prod profiles, api-gateway) — wire 3 endpoints; verify endpoints permitAll in BOTH configs
- [x] `services/company-user-management-service/src/test/...` — integration tests FIRST (RED): full I/O matrix above + token round-trip; `@MockBean EmailService`, assert template key + recipient + token link var
- [x] `infrastructure/lib/stacks/company-management-stack.ts` — SES grant + JWT_SECRET + APP_BASE_URL (see Code Map); `cd infrastructure && npm test` green
- [x] `web-frontend/src/pages/public/VerifyAdditionalEmailPage.tsx` + route `/verify-email` — NEW, mirror UnsubscribePage (verify→confirm→success/error states) + Vitest tests
- [x] `web-frontend/src/components/user/UserSettingsTab/UserSettingsTab.tsx` + `useUserAccount` — pill: Verified (success) / Unverified + Resend button (`useResendVerification` mutation); Vitest tests; i18n keys ×10 locales (`userManagement.json`)

**Acceptance Criteria:**
- Given a fresh additional email, when the user clicks the emailed link and confirms on `/verify-email`, then `verified_at` is set and the settings pill shows Verified
- Given an expired (>48h) or tampered token, when confirming, then 400 with errorCode and the page shows a localized error + no DB change
- Given the email row was deleted after issuance, when confirming, then 404 and `verified_at` of any other row unchanged
- Given an unverified email, when the owner clicks Resend, then a new verification email is dispatched and 204 returned; a verified one returns 409
- Given EmailService throws on add, when POSTing a new additional email, then 201 is still returned and a WARN is logged

## Spec Change Log

## Design Notes

Token is the credential → endpoints public (`permitAll` both layers), no JWT needed on verify path. Row-UUID claim (not email alone) makes delete/re-add a hard invalidation and prevents cross-user replay (global email uniqueness + fresh UUID per row). Two-step GET-verify/POST-confirm copied from newsletter unsubscribe to survive mail-scanner prefetch. `verifiedAt` already exists in OpenAPI `AdditionalEmail` schema and frontend types — response shape unchanged except new endpoints.

## Verification

**Commands:**
- `set -o pipefail; ./gradlew :services:company-user-management-service:test 2>&1 | tee /tmp/cums-test.log` — expected: BUILD SUCCESSFUL, new integration tests green
- `cd infrastructure && npx jest test/unit --silent 2>&1 | tee /tmp/infra-test.log` — expected: green (stack snapshot updated)
- `cd web-frontend && npx vitest run src/components/user/UserSettingsTab src/pages/public/VerifyAdditionalEmailPage 2>&1 | tee /tmp/fe-test.log` — expected: green
- `cd web-frontend && npm run type-check` — expected: clean after type regen

## Suggested Review Order

**Token design — the security core**

- Stateless HS256 JWT bound to the row UUID; delete/re-add hard-invalidates outstanding links
  [`AdditionalEmailVerificationTokenService.java:73`](../../services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/AdditionalEmailVerificationTokenService.java#L73)
- Claim extraction inside validation — malformed-but-signed tokens → 400, never 500 (review patch)
  [`AdditionalEmailVerificationTokenService.java:98`](../../services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/AdditionalEmailVerificationTokenService.java#L98)

**Verify endpoints — public, token-credentialed**

- GET check is read-only by design (mail scanners prefetch links); only POST mutates
  [`UserService.java:1495`](../../services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/UserService.java#L1495)
- Confirm: row-UUID + email match, idempotent `alreadyVerified`, `@Transactional` (review patch)
  [`UserService.java:1524`](../../services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/UserService.java#L1524)
- Three endpoints; resend stays auth-gated, verify pair is public
  [`UserController.java:594`](../../services/company-user-management-service/src/main/java/ch/batbern/companyuser/controller/UserController.java#L594)
- Dual-SecurityConfig rule: permitAll in CUMS (all 3 profiles)…
  [`SecurityConfig.java:143`](../../services/company-user-management-service/src/main/java/ch/batbern/companyuser/config/SecurityConfig.java#L143)
- …and in the gateway
  [`SecurityConfig.java:291`](../../api-gateway/src/main/java/ch/batbern/gateway/config/SecurityConfig.java#L291)

**Send path — failure-tolerant**

- Send-on-add hook: `flush()` to materialise the row UUID before token minting
  [`UserService.java:1369`](../../services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/UserService.java#L1369)
- Swallow-and-WARN wrapper — SES outage never rolls back the 201
  [`UserService.java:1430`](../../services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/UserService.java#L1430)
- DE/EN template selection (`de*` prefix match), EN fallback
  [`AdditionalEmailVerificationEmailService.java:47`](../../services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/AdditionalEmailVerificationEmailService.java#L47)

**Infra — first email-sending capability for CUMS**

- SES grant (PCS pattern), JWT_SECRET reuse of WATCH_JWT_SECRET, APP_BASE_URL
  [`company-management-stack.ts:180`](../../infrastructure/lib/stacks/company-management-stack.ts#L180)

**Frontend**

- Public landing page: useEffect-driven state machine, distinct 404 (`notFound`) vs 400 (`invalid`) states
  [`VerifyAdditionalEmailPage.tsx:46`](../../web-frontend/src/pages/public/VerifyAdditionalEmailPage.tsx#L46)
- Settings pill: Verified/Unverified + double-click-guarded Resend
  [`UserSettingsTab.tsx:284`](../../web-frontend/src/components/user/UserSettingsTab/UserSettingsTab.tsx#L284)
- Resend mutation hook
  [`useUserAccount.ts:188`](../../web-frontend/src/hooks/useUserAccount/useUserAccount.ts#L188)

**Peripherals — contract, tests, i18n**

- Contract-first: 3 new operations + schemas
  [`users-api.openapi.yml:1000`](../../docs/api/users-api.openapi.yml#L1000)
- Full I/O-matrix integration suite (16 tests, mocked EmailService, Testcontainers)
  [`AdditionalEmailVerificationIntegrationTest.java:57`](../../services/company-user-management-service/src/test/java/ch/batbern/companyuser/controller/AdditionalEmailVerificationIntegrationTest.java#L57)
- i18n ×10 locales (`verifyEmail.*` + pill/resend keys)
  [`en/userManagement.json`](../../web-frontend/public/locales/en/userManagement.json)
