# Story 12.1: Cognito Attribute Hygiene (SSO PR 0)

Status: review

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **platform engineer preparing the auth surface for Google SSO federation**,
I want **the token/attribute surface realigned with the documented ADR-001 target — frontend reads company/preferences from the DB (`/users/me`) not the JWT, `custom:companyId` is no longer emitted or written, and `custom:role` stops leaking from its stored attribute into tokens**,
so that **Phase 1's Google attribute-mapping maps into a clean target (no `companyId` to map, `preferences` as a seed only), and the token carries only identity (`sub`, `email`) + authorization (`custom:role`, `custom:username`) per `06b`'s "Minimal target footprint".**

This is **PR 0** of Epic 12 (SSO / OIDC Federation). It is **standalone** — no dependency on any SSO phase — and lands **first** so the later Google attribute-mapping (Story 12.5) targets a clean surface. Source: `docs/plans/sso-oidc-federation.md` §5 "PR 0"; target spec: `docs/architecture/06b-user-lifecycle-sync.md` §"Cognito Custom-Attribute Inventory & Deprecation Status".

## Acceptance Criteria

1. **(Step 1 — FE reads company/preferences from the DB, not the token.)** `extractUserContextFromToken` (`web-frontend/src/services/auth/authService.ts:424-455`) no longer sources `companyId` from `tokenPayload['custom:companyId']` (currently line 448) nor `preferences` from `tokenPayload['custom:preferences']` (currently line 425). Instead the authenticated `UserContext` is populated with `companyId` + `preferences` from `GET /users/me` via the existing hydration path in `web-frontend/src/contexts/AuthContext.tsx` (`hydrateRolesIfMissing`, currently `getUserProfile(['roles'])` → extend to include `company` + `preferences`). **Regression guard:** `preferences.language` MUST be populated on the `UserContext` *before* the language-sync effect at `web-frontend/src/App.tsx:241` runs, so locale selection does not regress for users whose token no longer feeds preferences.

2. **(Step 2a — gateway stops extracting `custom:companyId`.)** `api-gateway/.../auth/UserContextExtractor.java:79-81` no longer reads the `custom:companyId` claim into `UserContext.companyId`. The now-dead `X-Company-Id` header propagation in `api-gateway/.../routing/RequestTransformer.java:37-38` is removed (verified: **no service reads `X-Company-Id`** anywhere in `services/` or `shared-kernel/`). `UserContextExtractorTest` and any `RequestTransformer` test are updated to assert the claim/header are no longer produced.

3. **(Step 2b — CUMS removes dead `getCompanyId()`.)** `services/company-user-management-service/.../security/SecurityContextHelper.java:155-164` `getCompanyId()` is removed entirely — verified to have **zero callers** (the `getCompanyId()` hits elsewhere in CUMS are on domain/DTO objects `user.getCompanyId()` / `request.getCompanyId()`, unrelated). `SecurityContextHelperTest.java` is updated to drop the corresponding test.

4. **(Step 2c — FE signup stops writing `custom:companyId`.)** The `...(signUpData.companyId && { 'custom:companyId': signUpData.companyId })` attribute at `authService.ts:318` is removed. **Confirmed loses nothing (OQ-2, traced 2026-05-31):** self-registration already sends `companyId: ''` (`useRegistration.ts:44`, "Not needed for self-registration") so the spread never fires for self-signups; and `post-confirmation.ts` creates `user_profiles` reading **only** `custom:preferences` + `custom:role` — it never reads `custom:companyId` and its INSERT (`post-confirmation.ts:294`) does not set `company_id`. Company is owned by `user_profiles.company_id` via the user-management path (ADR-003/004), never seeded from the token attribute. Smoke `registration-flow.spec.ts` still completes end-to-end.

5. **(Step 3 — `custom:role` stored-attribute hygiene, Layer-3.)** The client `readAttributes` in `infrastructure/lib/stacks/cognito-stack.ts:262-264` drops `'role'` (`.withCustomAttributes('companyId', 'preferences', 'role')` → `('companyId', 'preferences')`) so the **stored** `custom:role` attribute stops flowing into issued tokens. **The DB-projected `custom:role` authorization claim (injected fresh by the PreTokenGeneration Lambda via `claimsToAddOrOverride`) is UNCHANGED** — Spring Security still builds authorities from it. `infrastructure/test/unit/cognito-stack.test.ts:80` `ReadAttributes` assertion is updated. Password login and role-based authorization verified unaffected.

6. **(Step 3 — `custom:role` "UNUSED" sentinel — CONFIRMED, OQ-1 resolved 2026-05-31: do it.)** A one-time paginated `AdminUpdateUserAttributes` script (model: `scripts/staging/sync-bootstrap-user.ts`, AWS SDK v3 + ts-node, `batbern-staging` profile) sets `custom:role='UNUSED'` on **all existing pool users**, AND `'UNUSED'` is written on every **new** user going forward, as documentation-in-the-data for console inspectors. New-user write points (both creation chokepoints): (a) **self-registered** → best-effort, non-blocking `AdminUpdateUserAttributes` in `post-confirmation.ts` after the `user_profiles` INSERT (mirror the existing non-blocking error handling — a failure must NOT block confirmation); (b) **admin-provisioned** → include `custom:role: 'UNUSED'` in the initial attributes of the CUMS `adminCreateUser` path. **Backfill execution is a prod data mutation (staging IS prod):** script defaults to `--dry-run`, prints affected count, and runs live only with an explicit flag + operator confirmation. This is the sentinel value `06b:643` calls out (fits `maxLen:20`); it does not touch the DB-projected `custom:role` authorization claim.

7. **(Deploy ordering & blast radius.)** Changes ship in the order **FE (step 1) → service/gateway (step 2) → Layer-3 (step 3)** so token *reads* move to the DB **before** token *writes/leaks* stop. Each step is independently revertible (revert per step). **None** of this touches authentication, token issuance (PreTokenGeneration), the Pattern 3b DB-fallback, or the partner authorization path (`PartnerSecurityService` already resolves company server-side via the user-api — it never read the `custom:companyId` claim).

8. **(Doc-drift, same commit.)** `docs/architecture/06b-user-lifecycle-sync.md` §"Inventory" is updated so the `custom:companyId` and `custom:preferences` rows read as **present-tense done** (companyId no longer extracted/written anywhere; preferences read from `/users/me`), and the `custom:role` permanence note reflects the dropped `readAttributes` entry. Satisfies the doc-drift mapping (CUMS `source_pattern` → `06b-user-lifecycle-sync.md`, `.github/doc-drift-mappings.yml:32`). Add `[no-doc]` is NOT applicable — this is a business-logic/contract change.

## Tasks / Subtasks

- [x] **Task 1 — FE: source company/preferences from `/users/me` (AC: 1)** — *deploy step 1, frontend first*
  - [x] RED: extend `AuthContext.test.tsx` — assert the hydrated `UserContext` carries `companyId` + `preferences` from a mocked `/users/me` (`getUserProfile`) response, and that `preferences.language` is set; update `authService.test.ts` to assert `extractUserContextFromToken` no longer reads `custom:companyId`/`custom:preferences` from the token payload.
  - [x] GREEN: in `AuthContext.tsx` `hydrateRolesIfMissing` (line 72), call `getUserProfile(['roles', 'company', 'preferences'])` and merge `companyId` + `preferences` (from `UserResponse.companyId` line 939 / `UserResponse.preferences` line 964, `user-api.types.ts`) into the returned `UserContext`. Rename the helper if it now hydrates more than roles (e.g. `hydrateUserFromDb`), keeping all 3 call sites (AuthContext.tsx:169, 243, 302).
  - [x] GREEN: in `authService.ts:424-455`, stop reading `tokenPayload['custom:companyId']` (line 448) and `tokenPayload['custom:preferences']` (line 425); default `companyId: undefined` and `preferences: {}` so hydration fills them. Keep `custom:username` + `custom:role` reads intact.
  - [x] Verify the `App.tsx:241` language-sync effect still receives `preferences.language` (hydration completes before the effect — assert ordering in test). **Regression-critical.**
  - [x] REFACTOR + `npm run type-check` + `npm run lint` + targeted vitest green.

- [x] **Task 2 — Gateway: stop extracting `custom:companyId` + remove dead header (AC: 2)** — *deploy step 2*
  - [x] RED: update `api-gateway/src/test/java/ch/batbern/gateway/auth/UserContextExtractorTest.java` — a JWT carrying `custom:companyId` produces a `UserContext` with `companyId == null`.
  - [x] GREEN: delete `UserContextExtractor.java:79-81` (the `custom:companyId` block). Delete `RequestTransformer.java:37-38` (`X-Company-Id` header) — confirmed no downstream reader. Leave `UserContext.companyId` field if other code references it; otherwise remove.
  - [x] Update/add `RequestTransformer` test to assert no `X-Company-Id` header is emitted.

- [x] **Task 3 — CUMS: delete dead `getCompanyId()` (AC: 3)** — *deploy step 2*
  - [x] RED/adjust: remove the `getCompanyId()` test from `SecurityContextHelperTest.java`.
  - [x] GREEN: delete `SecurityContextHelper.java:155-164` `getCompanyId()`. Re-grep to reconfirm zero callers before deleting.

- [x] **Task 4 — FE: stop writing `custom:companyId` at signup (AC: 4)** — *deploy step 2 (frontend)*
  - [x] RED: update `authService.test.ts` signup test — `amplifySignUp` is called WITHOUT `custom:companyId` in `userAttributes`.
  - [x] GREEN: remove the `...(signUpData.companyId && {...})` spread at `authService.ts:318`.
  - [x] Verify registration wizard completes E2E (manual/Playwright `registration-flow.spec.ts`); confirm company still lands in `user_profiles.company_id` via the user-creation path.

- [x] **Task 5 — Infra: drop `'role'` from client `readAttributes` (AC: 5)** — *deploy step 3, Layer-3*
  - [x] RED: update `infrastructure/test/unit/cognito-stack.test.ts:80` `ReadAttributes` assertion to expect `companyId` + `preferences` only (no `role`).
  - [x] GREEN: `cognito-stack.ts:264` → `.withCustomAttributes('companyId', 'preferences')`. Leave the **schema** `customAttributes.role` (cognito-stack.ts:185) intact — Cognito custom attributes are permanent and cannot be deleted; this only stops the client reading it into the token.
  - [x] `npm test -- cognito-stack.test.ts` green; verify the PreTokenGeneration projected `custom:role` claim is untouched (no change to `lambda/pre-token-generation`).

- [x] **Task 6 — `custom:role` "UNUSED" sentinel (AC: 6)** — *OQ-1 resolved: GO*
  - [x] Write `scripts/staging/backfill-cognito-role-unused.ts` (model `sync-bootstrap-user.ts`): paginated `ListUsersCommand` + `AdminUpdateUserAttributesCommand` setting `custom:role='UNUSED'`. `--dry-run` default-on (prints affected count + sample); live run requires explicit `--execute` + a typed confirmation. AWS SDK v3, `batbern-staging` profile, eu-central-1.
  - [x] New-user write (a) self-registered: in `infrastructure/lib/lambda/triggers/post-confirmation.ts`, after the `user_profiles` INSERT, best-effort `AdminUpdateUserAttributes` `custom:role='UNUSED'` — non-blocking (swallow + log on failure, same style as existing handler error handling); add/extend a `post-confirmation.test.ts` case asserting the write is attempted and a failure does not throw.
  - [x] New-user write (b) admin-provisioned: include `custom:role: 'UNUSED'` in the initial attributes of the CUMS `adminCreateUser` path (`CognitoIntegrationServiceImpl` or equivalent); add a unit assertion.
  - [x] Run the backfill `--dry-run` first; capture the affected-user count; execute live only after sign-off. Record the run in the PR.

- [x] **Task 7 — Docs same commit (AC: 8)**
  - [x] Update `06b-user-lifecycle-sync.md` Inventory table rows for `custom:companyId` (now removed from extraction/write) + `custom:preferences` (now read from `/users/me`) + the `custom:role` permanence note (readAttributes dropped). Past/present-tense flip.

- [x] **Task 8 — Full verification + deploy-order note**
  - [x] `make verify` (or targeted: gateway + CUMS Java suites, frontend type-check/lint/vitest, infra `npm test`) — dump to temp file, grep, all green.
  - [x] Confirm deploy sequence in the PR description: FE → svc/gw → L3.

## Dev Notes

### Architecture context — why each surface is safe to change
- **`custom:role` is a PROJECTED claim, not the stored attribute.** Per `06b:606-611`, the PreTokenGeneration Lambda computes `custom:role`/`custom:username` fresh from the DB on every token via `claimsToAddOrOverride` — it does **not** read the stored Cognito attributes. So dropping `'role'` from the client `readAttributes` (Task 5) removes only the **dead fossil stored attribute** from the token; the authorization claim Spring Security consumes (`JwtRolesConverter`, `UserContextExtractor.java:55-77`) is untouched. **Do not touch `lambda/pre-token-generation`.**
- **`custom:companyId` is redundant token bloat.** `06b:622-636`: company membership is business data, not identity/authorization. The only company-scoped authorization path (`PartnerAnalyticsController` → `@partnerSecurityService.isCurrentUserCompany`) already resolves company server-side via `userServiceClient.getUserByUsername(username)` and compares `companyName` — it never reads the `custom:companyId` claim. Confirmed at trace time: **no service reads the `X-Company-Id` header** the gateway sets, and **CUMS `SecurityContextHelper.getCompanyId()` has zero callers** — both are dead.
- **`/users/me` already carries what the FE needs.** `UserResponse` (`user-api.types.ts:913-964`) exposes `companyId` (line 939, the 12-char company *name* per ADR-003) and `preferences` (line 964 → `UserPreferences`: theme, language, notifications) via `?include=company` / `?include=preferences`. The hydration plumbing already exists — `AuthContext.hydrateRolesIfMissing` (added Epic 11.E.7) calls `getUserProfile(['roles'])`; this story extends the include list and the merge.

### Files to touch — current state & what to preserve
| File | Current state | Change | Preserve |
|---|---|---|---|
| `web-frontend/src/services/auth/authService.ts` | `:425` reads `custom:preferences`; `:448` reads `custom:companyId`; `:318` writes `custom:companyId` at signup | Stop reading (425/448) → default empty; stop writing (318) | `custom:username` (439) + `custom:role` (429-432) reads; signup `custom:preferences` seed (302-317) |
| `web-frontend/src/contexts/AuthContext.tsx` | `hydrateRolesIfMissing` (72) calls `getUserProfile(['roles'])`, merges roles only; 3 call sites (169/243/302) | Add `company`+`preferences` to include; merge `companyId`+`preferences` | All 3 call sites; roles merge; graceful fail-open on `/users/me` error (catch at 113) |
| `web-frontend/src/App.tsx` | `:241` language-sync effect consumes `preferences.language` | none (consumer) | **Must still receive `preferences.language`** after hydrate (AC1 regression guard) |
| `api-gateway/.../auth/UserContextExtractor.java` | `:79-81` extracts `custom:companyId` | delete block | username/role/email/preferences extraction (28-85) |
| `api-gateway/.../routing/RequestTransformer.java` | `:37-38` sets `X-Company-Id` from `userContext.getCompanyId()` | delete block (dead) | all other header propagation |
| `services/company-user-management-service/.../security/SecurityContextHelper.java` | `:155-164` `getCompanyId()` reads `custom:companyId` | delete method (0 callers) | `getUsername()`/other helpers |
| `infrastructure/lib/stacks/cognito-stack.ts` | `:264` `readAttributes` includes `'role'`; `:267` `writeAttributes` includes `companyId`; `:185` schema `role` attr | drop `'role'` from `readAttributes` only | schema attrs (permanent); writeAttributes (leave — FE just stops sending); `supportedIdentityProviders` |

### Testing standards (per CLAUDE.md 4-layer + TDD red-green-refactor)
- Java integration tests use PostgreSQL via Testcontainers (`AbstractIntegrationTest`) — but the gateway/CUMS changes here are unit-level (claim extraction / helper), so `UserContextExtractorTest` + `SecurityContextHelperTest` unit tests suffice.
- Frontend: vitest for `authService.test.ts` + `AuthContext.test.tsx`. Assert against EN values or namespace-stripped keys, never a non-EN translation.
- Infra: `cognito-stack.test.ts` `Template.fromStack` `ReadAttributes` assertion. (No Lambda handler change → no handler test needed here.)
- Run via tee-to-temp-file then grep (CLAUDE.md), don't re-run suites repeatedly.

### Project Structure Notes
- One-time admin scripts live in `scripts/staging/*.ts` (ts-node + AWS SDK v3 + `pg`), model `sync-bootstrap-user.ts`. The OQ-1 backfill belongs there if approved.
- Deploy tiers (infra/CLAUDE.md): step 1+2+4 are code-only/frontend (fast-path/frontend deploy); step 5 is a Cognito change → **Layer-3** (`deploy:staging:layer3-application`). This is why the story is sequenced FE → svc/gw → L3.

### References
- [Source: docs/plans/sso-oidc-federation.md#PR 0 — Cognito attribute hygiene] (3 sub-steps, deploy order, risk/rollback)
- [Source: docs/architecture/06b-user-lifecycle-sync.md#Cognito Custom-Attribute Inventory & Deprecation Status] (lines 599-664 — inventory, companyId-not-in-token decision, permanence constraint, minimal target footprint)
- [Source: web-frontend/src/services/auth/authService.ts:318,425,448] · [Source: web-frontend/src/contexts/AuthContext.tsx:72-118] · [Source: web-frontend/src/App.tsx:241]
- [Source: api-gateway/.../auth/UserContextExtractor.java:79-81] · [Source: api-gateway/.../routing/RequestTransformer.java:37-38]
- [Source: services/company-user-management-service/.../security/SecurityContextHelper.java:155-164]
- [Source: infrastructure/lib/stacks/cognito-stack.ts:185,262-267] · [Source: infrastructure/test/unit/cognito-stack.test.ts:50,55,80]
- [Source: web-frontend/src/types/generated/user-api.types.ts:913-964 (UserResponse: companyId, preferences)]
- [Source: .github/doc-drift-mappings.yml:31-37 (CUMS → 06b)]
- ADR-001 (Cognito-for-auth-only), ADR-003 (meaningful IDs), ADR-004 (factor user fields, enrich via user-api)

### Open Questions — ALL RESOLVED 2026-05-31 (owner: Nissim)
- **OQ-1 (AC6/Task 6) — ✅ RESOLVED: do the backfill.** Existing pool users get `custom:role='UNUSED'` via a one-time paginated `AdminUpdateUserAttributes` script (dry-run default; live only with `--execute` + confirmation, since staging IS prod), AND new users get `'UNUSED'` written going forward at both creation chokepoints (self-registered via `post-confirmation.ts` best-effort; admin-provisioned via CUMS `adminCreateUser`). Task 6 is unblocked.
- **OQ-2 (AC4) — ✅ RESOLVED: dropping line 318 loses nothing (verified in code).** Self-registration sends `companyId: ''` (`useRegistration.ts:44`) so the signup spread never fires; `post-confirmation.ts` reads only `custom:preferences` + `custom:role` and never reads `custom:companyId` (INSERT at `:294` sets no `company_id`). Company is owned by `user_profiles.company_id` via the user-management path, not the token.
- **OQ-3 (AC2) — ✅ RESOLVED: nothing changed in the meantime.** No `X-Company-Id` reader exists in `services/`/`shared-kernel/`; the `RequestTransformer.java:37-38` block is dead and is removed.

## Dev Agent Record

### Agent Model Used

Claude Opus 4.8 (1M context) — bmad-dev-story, 2026-05-31.

### Debug Log References

- FE auth tests: `/tmp/t1-green.log`, `/tmp/t14.log` (39 passed / 2 skipped).
- Full frontend vitest: `/tmp/fe-full.log` (4959 passed, 0 failed).
- Gateway: `/tmp/t8-gw.log` (full `:api-gateway:test` BUILD SUCCESSFUL).
- CUMS: `/tmp/t8-cums.log` (full `:services:company-user-management-service:test` BUILD SUCCESSFUL).
- Infra: `/tmp/t8-infra2.log` (356 passed, 0 failed); infra `tsc --noEmit` clean.

### Completion Notes List

- **AC1 (FE hydration):** `extractUserContextFromToken` (`authService.ts`) no longer reads
  `custom:companyId`/`custom:preferences` — `companyId: undefined`, `preferences: {}` at
  extraction. `AuthContext.hydrateRolesIfMissing` renamed → `hydrateUserFromDb`, now always
  fetches `getUserProfile(['roles','company','preferences'])` and merges `companyId` +
  `preferences` (mapping the canonical backend `UserPreferences` onto the frontend shape);
  roles fall back to DB only when the JWT carried none (Pattern 3b preserved). All 3 call
  sites renamed. **Regression guard satisfied structurally:** hydration is awaited BEFORE
  `setState({ isAuthenticated: true })`, so `preferences.language` is on the user before any
  auth-gated effect (LanguageSync / App.tsx) fires — asserted in `AuthContext.test.tsx`.
- **AC2 (gateway):** deleted the `custom:companyId` extraction block in `UserContextExtractor`
  and the dead `X-Company-Id` header in `RequestTransformer`. `UserContext.companyId` field kept
  (still referenced by test builders; harmless POJO field, now always unset). Updated
  `UserContextExtractorTest`, `RequestTransformerTest`, and `CognitoJWTValidatorTest` (3 companyId
  assertions → `isNull`).
- **AC3 (CUMS):** deleted `SecurityContextHelper.getCompanyId()` (re-confirmed zero production
  callers; the only callers were 4 tests in `SecurityContextHelperTest`, removed).
- **AC4 (FE signup):** removed the `custom:companyId` spread at `authService.ts`; test asserts it
  is never sent even when a `companyId` is supplied.
- **AC5 (infra):** dropped `'role'` from the client `readAttributes` in `cognito-stack.ts`
  (`.withCustomAttributes('companyId','preferences')`); schema attribute + PreTokenGeneration
  projected claim untouched. `cognito-stack.test.ts` rewritten to capture `ReadAttributes` and
  assert `custom:role` absent / `custom:companyId`+`custom:preferences` present.
- **AC6 (UNUSED sentinel):** (a) NEW `scripts/staging/backfill-cognito-role-unused.ts` —
  paginated `ListUsers` + `AdminUpdateUserAttributes`, `--dry-run` default, live run needs
  `--execute` + typed confirmation (`backfill unused`), `batbern-staging`/eu-central-1, modelled
  on `sync-bootstrap-user.ts`. (b) self-registered: best-effort non-blocking
  `AdminUpdateUserAttributesCommand` in `post-confirmation.ts` after the user sync (failure
  swallowed, never blocks confirmation; new tests cover the write + the failure-non-blocking
  path). (c) admin-provisioned: `custom:role='UNUSED'` added to `adminCreateUserSilently`
  attributes (CUMS), test updated. **Backfill `--dry-run`/live execution is a deploy-time
  operator step** (production AWS creds + sign-off; staging IS prod) — NOT run from here.
- **AC7 (deploy order):** FE (step 1) → service/gateway (step 2) → Layer-3 (step 3). Each step
  independently revertible. Authentication / token issuance / Pattern 3b / partner-auth untouched.
- **AC8 (docs):** `06b-user-lifecycle-sync.md` Inventory + permanence sections flipped to
  present-tense done for `custom:companyId` (removed) + `custom:preferences` (read from
  `/users/me`) + `custom:role` (readAttributes dropped, sentinel). Same-commit doc-drift mapping
  satisfied (CUMS → 06b).
- **Pre-existing test fixes done while in these files (user-requested "fix it"):** (1) 13
  `post-confirmation.test.ts` new-user-path tests were red on `develop` due to a missing
  `resolveUniqueUsername` SELECT mock (drift since the 2026-05-18 username-collision fix) — added
  the missing mock to each; suite now 35/35. (2) `company-management-stack.test.ts`
  least-privilege guard asserted 4 Cognito actions but the source correctly grants 6 (`ListUsers`
  + `ResendConfirmationCode` added later) — aligned the expected set to 6 (exact-array guard
  preserved). Neither was caused by Story 12.1; both confirmed pre-existing via `git stash`.
- **Verification:** frontend 4959 passed / 0 failed (type-check + lint clean); `:api-gateway:test`
  + `:services:company-user-management-service:test` BUILD SUCCESSFUL; infra jest 356 passed / 0
  failed + `tsc` clean.

### File List

**Frontend**
- `web-frontend/src/services/auth/authService.ts` (M — drop token reads of companyId/preferences; drop signup custom:companyId write)
- `web-frontend/src/services/auth/authService.test.ts` (M — tests)
- `web-frontend/src/contexts/AuthContext.tsx` (M — `hydrateUserFromDb`: hydrate company+preferences+roles)
- `web-frontend/src/contexts/AuthContext.test.tsx` (M — tests + userApi mock)

**API Gateway**
- `api-gateway/src/main/java/ch/batbern/gateway/auth/UserContextExtractor.java` (M — remove custom:companyId extraction)
- `api-gateway/src/main/java/ch/batbern/gateway/routing/RequestTransformer.java` (M — remove X-Company-Id header)
- `api-gateway/src/test/java/ch/batbern/gateway/auth/UserContextExtractorTest.java` (M)
- `api-gateway/src/test/java/ch/batbern/gateway/routing/RequestTransformerTest.java` (M)
- `api-gateway/src/test/java/ch/batbern/gateway/auth/CognitoJWTValidatorTest.java` (M)

**Company-User Management Service (CUMS)**
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/security/SecurityContextHelper.java` (M — delete getCompanyId())
- `services/company-user-management-service/src/test/java/ch/batbern/companyuser/security/SecurityContextHelperTest.java` (M — drop getCompanyId tests)
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/CognitoIntegrationServiceImpl.java` (M — custom:role='UNUSED' on adminCreateUser)
- `services/company-user-management-service/src/test/java/ch/batbern/companyuser/service/CognitoIntegrationServiceImplTest.java` (M)

**Infrastructure**
- `infrastructure/lib/stacks/cognito-stack.ts` (M — drop 'role' from client readAttributes)
- `infrastructure/test/unit/cognito-stack.test.ts` (M)
- `infrastructure/lib/lambda/triggers/post-confirmation.ts` (M — best-effort custom:role='UNUSED' sentinel)
- `infrastructure/test/unit/lambda/post-confirmation.test.ts` (M — sentinel tests + pre-existing resolveUniqueUsername mock fixes)
- `infrastructure/test/unit/company-management-stack.test.ts` (M — pre-existing least-privilege assertion aligned to 6 actions)

**Scripts**
- `scripts/staging/backfill-cognito-role-unused.ts` (NEW — one-time UNUSED sentinel backfill, dry-run default)

**Docs**
- `docs/architecture/06b-user-lifecycle-sync.md` (M — inventory + permanence present-tense done)

**Sprint tracking**
- `_bmad-output/implementation-artifacts/sprint-status.yaml` (M — 12-1 ready-for-dev → in-progress → review)

### Change Log

| Date | Change |
|---|---|
| 2026-05-31 | Story 12.1 implemented (Cognito attribute hygiene, SSO PR 0). FE sources company/preferences from `/users/me`; gateway/CUMS/FE stop emitting/reading `custom:companyId`; `custom:role` dropped from client readAttributes + `'UNUSED'` sentinel (backfill script + post-confirmation + CUMS adminCreateUser). Docs updated. Also fixed 14 pre-existing infra test failures (post-confirmation mock drift ×13 + company-management least-privilege assertion ×1) flagged during verification. All layers green. Status → review. |
