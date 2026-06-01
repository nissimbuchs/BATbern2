# Story 12.6: Account-Linking PreSignUp Trigger (SSO Phase 2)

Status: ready-for-dev

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **platform engineer enabling transparent Google account-linking for existing BATbern users**,
I want **the inline `preSignUp` Cognito Lambda replaced by a real VPC + DB-secret `NodejsFunction` that, on a federated (`PreSignUp_ExternalProvider`) sign-in, merges the Google identity into the existing native user via `AdminLinkProviderForUser` (matched by email, preserving the Cognito `sub`), while preserving the current native-signup company-UUID validation verbatim**,
so that **a native email/password user signing in with Google for the first time keeps the same `cognito_user_id` → their roles, profile, company, and history stay intact (no migration, no orphaned roles, no `AliasExistsException`), and they can subsequently sign in with either password or Google.**

This is **Phase 2** of Epic 12 (SSO / OIDC Federation) — *"the correctness core; highest-care phase"* (`docs/plans/sso-oidc-federation.md` §5 Phase 2). It is the heart of *"can existing users switch"* (plan §3, ADR-010 D3). Source: `docs/plans/sso-oidc-federation.md` §5 "Phase 2", §3 ("Account-linking…"); decision record: `docs/architecture/ADR-010-federated-identity-via-cognito.md` (D3 transparent link-on-first-federated-login; D7 PreSignUp keeps validation + adds external-provider linking).

**Prerequisite:** Story 12.5 (Phase 1 — Google IdP defined + added to client). Without the Google provider wired, no `PreSignUp_ExternalProvider` event can fire. Phase 1 explicitly gates real federated testing on this story (plan §5 Phase 1, ⚠️ note).

## Why this matters (the two Cognito gotchas, from plan §3)

By default a Google login for `john@x.com` creates a **separate** Cognito user (`Google_…`, new `sub`). Because `email` is an auto-verified **sign-in alias** (`infrastructure/lib/stacks/cognito-stack.ts:160-166` — `signInAliases.email: true` + `autoVerify.email: true`), that collides with the existing native user → `AliasExistsException`, OR (worse) the federated user lands under a brand-new `sub` while our DB is keyed on `cognito_user_id` → the user's roles/profile/company are silently orphaned.

The fix is to call **`AdminLinkProviderForUser`** inside the **`PreSignUp_ExternalProvider`** trigger to merge the Google identity into the existing native user (matched by email). Result: **one** Cognito user, **same `sub`** → `user_profiles.cognito_user_id` unchanged → roles/profile/company/history all preserved. The user can then sign in with **either** password or Google. This is a transparent link-on-first-federated-login, **not** a migration (ADR-010 D3).

## Acceptance Criteria

1. **(Inline → real `NodejsFunction`.)** The inline `preSignUp` Lambda at `infrastructure/lib/stacks/cognito-stack.ts:42-79` (the `lambda.Function` built from `lambda.Code.fromInline(...)`, its `PreSignupLogGroup` at `:43-47`, and the `lambdaTriggers.preSignUp: preSignupLambda` wiring at `:213`) is replaced by a real `NodejsFunction` whose source lives in its own file `infrastructure/lib/lambda/triggers/pre-signup.ts`. The function is **VPC-attached with DB-secret env** modelled on `infrastructure/lib/constructs/cognito-user-sync-triggers.ts` (`commonLambdaProps`: `NODEJS_20_X`, `memorySize: 512`, `vpc`, `vpcSubnets: PRIVATE_WITH_EGRESS`, `securityGroups: [lambdaSecurityGroup]`, `commonEnv: { DB_HOST, DB_NAME, DB_SECRET_ARN, LOG_LEVEL }`, `bundling: { externalModules: ['@aws-sdk/*'], minify: true, sourceMap: false, forceDockerBundling: false }`). It connects to the DB via `getDbClient()` from `./common/database` exactly as `pre-authentication.ts` does (the secret ARN is read at runtime, never inlined). The wiring belongs in the `CognitoUserSyncTriggers` construct (which already owns the VPC/secret-wired triggers) so the new trigger reuses `commonLambdaProps` and the construct exposes a `preSignUpTrigger` field; `cognito-stack.ts` no longer constructs the inline Lambda, and the `lambdaTriggers.preSignUp` wiring moves into the construct (`props.userPool.addTrigger(cognito.UserPoolOperation.PRE_SIGN_UP, this.preSignUpTrigger)`). **Constraint:** the construct is only created when `vpc && lambdaTriggersSecurityGroup && databaseSecret && databaseEndpoint` are present (`cognito-stack.ts:292`). In **LOCAL dev** the construct is not created — but local dev points at the staging pool, whose triggers are deployed (plan §4: one real pool); there is no inline fallback needed because the inline Lambda's only job (company-UUID validation, see AC2) is now carried by the real function in the one real pool.

2. **(Native signup validation — PRESERVED VERBATIM, regression-critical.)** For `event.triggerSource ∈ { 'PreSignUp_SignUp', 'PreSignUp_AdminCreateUser' }` the handler reproduces the **current inline behaviour exactly** (`cognito-stack.ts:59-72`):
   - Read `const companyId = event.request.userAttributes['custom:companyId'];`
   - If `companyId` is truthy AND does not match `/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i` → `throw new Error('Invalid company ID format. Must be a valid UUID.')` (same message, same regex, same case-insensitive flag).
   - Otherwise return the event unchanged (no `autoConfirmUser`/`autoVerifyEmail` set — native confirmation flow via CustomEmailSender is unchanged; the comment block at `:65-71` about role validation removed / auto-verification disabled is preserved as documentation).
   - **No DB call on the native path** — native signup validation must not depend on the DB (it never did; keep it as a pure synchronous check so a DB hiccup can never break native registration). The handler opens a DB client **only** on the federated path (AC3).

3. **(Federated linking — `PreSignUp_ExternalProvider`.)** For `event.triggerSource === 'PreSignUp_ExternalProvider'`:
   - **Missing-email guard:** if `event.request.userAttributes.email` is absent/empty → do **not** attempt linking; log a warning and return the event with `autoConfirmUser`/`autoVerifyEmail` left unset (email-keyed linking is impossible without an email; Apple private-relay is explicitly out of scope per plan §5 Phase 6 / ADR-010 D2). (This is the deliberate fall-through — a federated identity with no email cannot be merged by email.)
   - **Look up an existing user by email** — query `user_profiles` for a row with this email that already has a non-null `cognito_user_id` (the native user's `sub`). Use the DB as the source of truth (`getDbClient()` + `SELECT cognito_user_id ... FROM user_profiles WHERE email = $1`); a Cognito `ListUsers` `filter=email="..."` lookup MAY be used to resolve the destination user where the DB row's `cognito_user_id` is the native pool username. Capture the destination native username (the existing `sub`/username).
   - **If a matching native user is found → call `AdminLinkProviderForUser`** to merge the incoming Google identity into that existing native user:
     - `DestinationUser` = the existing native user (`ProviderName: 'Cognito'`, `ProviderAttributeValue: <existing username/sub>`).
     - `SourceUser` = the incoming Google identity (`ProviderName: 'Google'`, `ProviderAttributeName: 'Cognito_Subject'`, `ProviderAttributeValue: <event.userName provider sub>`), per AWS `AdminLinkProviderForUser` semantics for an external OIDC provider.
     - The destination `sub` is **preserved** → `user_profiles.cognito_user_id` is unchanged → roles/profile/company/history intact (ADR-010 D3). Set `event.response.autoConfirmUser = true` and `event.response.autoVerifyEmail = true` so the linked sign-in completes without a confirmation round-trip.
   - **If no matching native user is found → brand-new federated user path:** do **not** call `AdminLinkProviderForUser`; set `event.response.autoConfirmUser = true` + `event.response.autoVerifyEmail = true` so the new Google user is immediately usable (provisioning of the `user_profiles` row happens later, lazily, via canonical JIT — Story 12.3 / plan PR 1B; this trigger creates no DB row, consistent with ADR-010 D4/D5).
   - Always `return event` (the modified event); never throw on the federated path for a found/not-found outcome — a thrown error here would 503 the federated sign-in.

4. **(IAM least-privilege.)** The new function's execution role is granted exactly: `cognito-idp:AdminLinkProviderForUser` and `cognito-idp:ListUsers`, plus `props.databaseSecret.grantRead(...)` and the existing `cloudwatch:PutMetricData` `commonLambdaProps` grant. Scope the Cognito actions to the pool ARN where possible; if scoping to `props.userPool.userPoolArn` creates a CloudFormation circular dependency (the pool already depends on this Lambda via `addTrigger`), fall back to the wildcard pool resource `arn:aws:cognito-idp:${region}:${account}:userpool/*` **with an explicit code comment explaining the circular-dependency reason** — exactly the documented pattern used for the existing `AdminUpdateUserAttributes` grant on `post-confirmation` (`cognito-user-sync-triggers.ts:154-170`). No other Cognito actions are granted.

5. **(Handler unit test — MANDATORY, per CLAUDE.md "Lambda Handler Tests".)** `infrastructure/test/unit/lambda/pre-signup.test.ts` imports and runs the handler module (a Lambda that fails module-load `Runtime.ImportModuleError`s and 503s **all** auth). It covers, with `getDbClient` + the Cognito SDK client mocked (model `post-authentication.test.ts` / `pre-authentication.test.ts` mock style):
   1. **module loads without crashing** (`expect(typeof handler).toBe('function')`);
   2. **native validation unchanged** — `PreSignUp_SignUp` with a valid UUID `custom:companyId` returns the event; with a malformed `custom:companyId` throws `'Invalid company ID format. Must be a valid UUID.'`; with no `custom:companyId` returns the event; **and the native path performs no DB call** (`getDbClient` not called);
   3. **federated-link path** — `PreSignUp_ExternalProvider` with an email matching an existing native user (DB returns a row with `cognito_user_id`) → `AdminLinkProviderForUserCommand` is sent with `DestinationUser` = the existing native user and `SourceUser` = the Google identity; the event returns with `autoConfirmUser === true` and `autoVerifyEmail === true`;
   4. **federated-new-user path** — `PreSignUp_ExternalProvider` with an email that matches **no** native user → `AdminLinkProviderForUser` is **NOT** called, and the event returns with `autoConfirmUser === true` + `autoVerifyEmail === true`;
   5. **missing-email guard** — `PreSignUp_ExternalProvider` with no/empty `email` → no link attempted, no throw, event returned.

6. **(CDK `Template.fromStack` test.)** `infrastructure/test/unit/cognito-stack.test.ts` (or a focused `cognito-user-sync-triggers` template test) asserts: (a) a `AWS::Lambda::Function` for the PreSignUp trigger now exists as a bundled `NodejsFunction` (no more `Code: { ZipFile: ... }` inline code for presignup — the inline `lambda.Code.fromInline` assertion, if any, is removed/replaced); (b) the function is wired as the pool's `PreSignUp` `LambdaConfig`; (c) an `AWS::IAM::Policy` grants `cognito-idp:AdminLinkProviderForUser` + `cognito-idp:ListUsers` (Match.arrayWith on the policy statement actions). Existing assertions (`Schema`, `ReadAttributes`, etc.) stay green.

7. **(Deploy, blast radius & rollback.)** **Layer-3** (Cognito) infra deploy (`npm run deploy:staging:layer3-application`). **Risk: MEDIUM** — we are swapping a *wired* trigger in the one real (production) pool (plan §4). Because the native path is preserved verbatim and short-circuits before any DB/SDK call, existing **password signups are unaffected**; the new code path only executes for `PreSignUp_ExternalProvider`, which cannot fire until a federated identity attempts sign-in (Phase 1 prerequisite). **Rollback:** the inline Lambda is preserved in git history — revert the construct + `cognito-stack.ts` wiring to restore the prior inline trigger. No data migration, so rollback is a pure code revert.

8. **(Doc-drift, SAME COMMIT — per CLAUDE.md "Doc Drift Prevention".)** `docs/architecture/06b-user-lifecycle-sync.md` gains a new **"Pattern F: Federated sign-in + account linking"** section describing: the federated trigger set (PreSignUp + PreTokenGeneration + PostAuthentication fire; **PostConfirmation + PreAuthentication do NOT** fire for federated — plan §3); the `PreSignUp_ExternalProvider` → `AdminLinkProviderForUser` email-keyed merge preserving `sub`; the auto-confirm/auto-verify for linked + new federated users; and the native-vs-federated `triggerSource` branch. It cross-references `ADR-010-federated-identity-via-cognito.md` (D3, D7) and corrects/links the existing preSignUp UUID-validation note at `06b:629`. This satisfies the `infrastructure/` → `06b`-adjacent doc-drift expectation (the `infrastructure/` mapping at `.github/doc-drift-mappings.yml:62-65` points at `06-backend-architecture.md` + `08-operations-security.md`; `06b` is the canonical user-lifecycle doc and is the documented target for the SSO trigger work per ADR-010's "Affects documentation"). `[no-doc]` is **NOT** applicable — this changes auth-path behaviour.

## Tasks / Subtasks

- [ ] **Task 1 — Author the `pre-signup.ts` handler source (AC: 2, 3)** *(TDD: write Task 5 tests first / alongside)*
  - [ ] Create `infrastructure/lib/lambda/triggers/pre-signup.ts` with `export const handler: PreSignUpTriggerHandler` (typed from `aws-lambda`). Set `context.callbackWaitsForEmptyEventLoop = false` and structured `console.log` of `{ triggerSource, userPoolId }` like the other triggers.
  - [ ] **Native branch** (`PreSignUp_SignUp` / `PreSignUp_AdminCreateUser`): copy the company-UUID validation **verbatim** from `cognito-stack.ts:59-72` (same regex `/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i`, same error message `'Invalid company ID format. Must be a valid UUID.'`, preserve the explanatory comment block at `:65-71`). Return the event. **No DB/SDK call on this path.**
  - [ ] **Federated branch** (`PreSignUp_ExternalProvider`): missing-email guard → log + return (no link). Else `getDbClient()` + `SELECT cognito_user_id, username FROM user_profiles WHERE email = $1` (lower/normalise email consistent with how `post-confirmation.ts:233-236` matches by email). If a native row (`cognito_user_id` non-null) is found → `AdminLinkProviderForUserCommand` (Destination = native user, Source = Google identity with `ProviderAttributeName: 'Cognito_Subject'`); set `autoConfirmUser`/`autoVerifyEmail` true. If none found → set `autoConfirmUser`/`autoVerifyEmail` true, no link. `release()` the client in a `finally`. Wrap link/lookup in try/catch — log + still return the event (never throw on the federated path).
  - [ ] Reuse `common/database`'s `getDbClient` (do not open a raw `pg` client). Initialise the `CognitoIdentityProviderClient` at module scope (like `post-confirmation.ts:28-30`). Add a `publishMetric` helper (CloudWatch `BATbern/UserSync`) mirroring `pre-authentication.ts:126-147` (fire-and-forget, swallow errors) — emit at least `FederatedUserLinked` / `FederatedNewUser` / `PreSignUpFailure` counters.

- [ ] **Task 2 — Wire the real `NodejsFunction` in the sync-triggers construct (AC: 1, 4)** *(deploy step: Layer-3)*
  - [ ] In `infrastructure/lib/constructs/cognito-user-sync-triggers.ts`: add `public readonly preSignUpTrigger: lambda.Function;`. Create it with `...commonLambdaProps`, `functionName: \`batbern-${props.envName}-presignup-trigger\``, `entry: path.join(__dirname, '../lambda/triggers/pre-signup.ts')`, `handler: 'handler'`, `timeout: cdk.Duration.seconds(15)` (VPC cold start + DB query, matching pre-auth/pre-token), and a dedicated `PreSignUpLogGroup` (`/aws/lambda/BATbern-${envName}/presignup-trigger`, matching the existing log-group naming/retention pattern).
  - [ ] `props.databaseSecret.grantRead(this.preSignUpTrigger)` + `this.preSignUpTrigger.addToRolePolicy(cloudWatchPolicy)`.
  - [ ] Add the Cognito IAM grant: `addToRolePolicy` with actions `['cognito-idp:AdminLinkProviderForUser', 'cognito-idp:ListUsers']`. Attempt `resources: [props.userPool.userPoolArn]`; if `cdk synth`/test surfaces a circular dependency, fall back to `arn:aws:cognito-idp:${region}:${account}:userpool/*` **with the documented comment** (mirror `cognito-user-sync-triggers.ts:154-170`).
  - [ ] `props.userPool.addTrigger(cognito.UserPoolOperation.PRE_SIGN_UP, this.preSignUpTrigger);`

- [ ] **Task 3 — Remove the inline trigger from `cognito-stack.ts` (AC: 1)** *(deploy step: Layer-3)*
  - [ ] Delete the inline `preSignupLambda` (`cognito-stack.ts:50-79`) and its `PreSignupLogGroup` (`:43-47`). Remove `preSignUp: preSignupLambda` from `userPool.lambdaTriggers` (`:212-215`) — leave `customEmailSender` wired (it is created in the stack, not the construct).
  - [ ] Confirm the `CognitoUserSyncTriggers` construct is created **before** anything that needs the PreSignUp trigger (it is already constructed at `cognito-stack.ts:293` inside the `if (props.vpc && ...)` guard). The PreSignUp trigger now lives inside that construct.
  - [ ] Guard note in the story: when `props.vpc` etc. are absent (pure unit-test / no-infra synth), there is no PreSignUp trigger created — the existing `cognito-stack.test.ts` (constructed without VPC) must still pass; the new `PreSignUp`/IAM assertions (AC6) belong in a test that constructs the stack/construct **with** the VPC+secret props (see Task 6).

- [ ] **Task 4 — `PreSignUpTriggerEvent` typing & `triggerSource` handling (AC: 2, 3)**
  - [ ] Use the `aws-lambda` `PreSignUpTriggerEvent` / `PreSignUpTriggerHandler` types. Branch on `event.triggerSource`; treat the three values explicitly: `PreSignUp_SignUp`, `PreSignUp_AdminCreateUser` (native), `PreSignUp_ExternalProvider` (federated). Any unrecognised source → default to the native (validate-and-return) path (safe default; never set autoConfirm for unknown sources).

- [ ] **Task 5 — Handler unit test (AC: 5)** *(write RED first)*
  - [ ] Create `infrastructure/test/unit/lambda/pre-signup.test.ts`. Mock `../../../lib/lambda/triggers/common/database` (`getDbClient`) and `@aws-sdk/client-cognito-identity-provider` (`CognitoIdentityProviderClient` → `{ send }`, `AdminLinkProviderForUserCommand` / `ListUsersCommand` pass-through) and `@aws-sdk/client-cloudwatch` — mirror `post-authentication.test.ts:16-31`.
  - [ ] Cover the 5 cases in AC5: (1) module-load; (2) native valid/invalid/absent UUID + assert `getDbClient` NOT called on the native path; (3) federated-link → `AdminLinkProviderForUserCommand` sent with correct Destination/Source + `autoConfirmUser`/`autoVerifyEmail` true; (4) federated-new-user → no link + auto-confirm/verify true; (5) missing-email → no link, no throw.
  - [ ] Add a `makeEvent(triggerSource, attributes)` helper (model `post-authentication.test.ts:37-57`).

- [ ] **Task 6 — CDK template test (AC: 6)**
  - [ ] Add a test (in `cognito-stack.test.ts` constructing the stack **with** `vpc`/`lambdaTriggersSecurityGroup`/`databaseSecret`/`databaseEndpoint`, or a dedicated `cognito-user-sync-triggers.test.ts`) asserting: a bundled PreSignUp `AWS::Lambda::Function` exists (not inline `ZipFile`); the pool's `LambdaConfig.PreSignUp` references it; an `AWS::IAM::Policy` `Statement` includes `Match.arrayWith(['cognito-idp:AdminLinkProviderForUser', 'cognito-idp:ListUsers'])`.
  - [ ] Verify the no-VPC `cognito-stack.test.ts` suite still passes (no PreSignUp trigger created in that configuration).

- [ ] **Task 7 — Doc-drift, same commit (AC: 8)**
  - [ ] Add "Pattern F: Federated sign-in + account linking" to `docs/architecture/06b-user-lifecycle-sync.md` (after Pattern N or near the federated/PreAuthentication notes at `06b:743-760`). Cover the federated trigger set, the `AdminLinkProviderForUser` email-keyed merge (sub preserved), auto-confirm/verify, the native-vs-federated branch, and the missing-email/Apple-deferred note. Cross-link ADR-010 (D3, D7) and the preSignUp UUID note at `06b:629`.

- [ ] **Task 8 — Full verification**
  - [ ] `cd infrastructure && npm test -- pre-signup.test.ts` and `npm test -- cognito-stack.test.ts` (and the new construct test) — dump to a temp file, grep, all green (per CLAUDE.md tee-to-temp-file rule).
  - [ ] `npx tsc --noEmit` clean in `infrastructure/`.
  - [ ] `npm test` full infra suite green (no regressions in `post-confirmation`, `pre-authentication`, `post-authentication` handler tests).
  - [ ] Confirm in the PR description: Layer-3 deploy; native path preserved verbatim; rollback = revert to inline (git history). **Do NOT run a real federated sign-in from here** (staging IS production — no real outbound auth flows in dev per the project no-real-comms rule); real link/new-user smoke is the Phase 3 (Story 12.7) verify-only step + the Phase 5 manual prod smoke.

## Dev Notes

### The exact inline validation that MUST be preserved verbatim (regression-critical)
Current inline `preSignUp` (`cognito-stack.ts:55-73`), the **only** load-bearing logic today:
```js
exports.handler = async (event) => {
  console.log('Pre-signup trigger:', JSON.stringify(event));

  // Validate company ID if provided
  const companyId = event.request.userAttributes['custom:companyId'];
  if (companyId && !companyId.match(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i)) {
    throw new Error('Invalid company ID format. Must be a valid UUID.');
  }

  // Role validation removed - Story 1.2.6: ADR-001 database-centric architecture
  // Roles are managed in PostgreSQL and synced to JWT via PreTokenGeneration Lambda
  // Self-registered users receive ATTENDEE role (assigned by PostConfirmation trigger)

  // Auto-verification disabled to test email verification flow
  // Users must verify their email via CustomEmailSender Lambda

  return event;
};
```
That is the **entire** current behaviour: a single optional company-UUID format check (only fires when `custom:companyId` is present and malformed), then return. **No auto-verify, no DB, no roles.** The TypeScript port must reproduce the regex, the truthiness gate, and the exact error string byte-for-byte on the native path. Note the *mild irony* the plan calls out (`06b:629`): `custom:companyId` is now `''`/unused for self-registration (Story 12.1 stopped writing it), so this check is effectively dormant for the funnel — but it is preserved verbatim anyway because (a) admin-created users could still pass a `custom:companyId`, and (b) silently dropping a wired validation in the one real pool is exactly the kind of regression this phase is "highest-care" about avoiding.

### Why this is provider-agnostic for the backend
Per ADR-010 D1 and plan §1, Cognito is the broker — the React app and every backend service keep receiving the **same JWT shape**. This story touches **only** the Cognito trigger; `shared-kernel/.../security/JwtRolesConverter`, every `@PreAuthorize`, the Pattern 3b DB-fallback, and `pre-token-generation.ts` are **untouched**. Linking preserves the `sub`, so PreTokenGeneration (Pattern 2) keeps projecting the same DB-sourced `custom:role`/`custom:username` for the linked user.

### Federated trigger set (the gotcha that shapes this phase)
For external-IdP sign-ins Cognito fires **only** PreSignUp, PreTokenGeneration, and PostAuthentication — it does **NOT** fire PostConfirmation or PreAuthentication (plan §3, ADR-010 Context #2, `06b:743-760`). Consequences this story relies on:
- DB-row creation (PostConfirmation) is bypassed → for a brand-new Google user we set auto-confirm/verify and let **canonical JIT** (Story 12.3 / PR 1B) create the row lazily on first API call. **This trigger creates no DB row.**
- The `is_active` deactivation gate (PreAuthentication) is bypassed → handled by the API-Gateway `is_active` gate (Story 12.2 / PR 1A), **not** here. This story does not gate inactive users.
- For an **existing** user being *linked*, the row already exists — provisioning is moot; we only merge the identity.

### Construct vs. stack placement
The VPC + DB-secret wiring already lives in `CognitoUserSyncTriggers` (`commonLambdaProps` at `:57-70`, `commonEnv` at `:49-54`, secret grants at `:137-140`, CloudWatch grant at `:143-152`, the existing pool-scoped Cognito grant pattern at `:154-170`, and `addTrigger` calls at `:176-191`). Putting the PreSignUp trigger there means it reuses all of that with no duplication. The inline trigger in `cognito-stack.ts` exists *outside* the VPC (no DB access) precisely because its old job needed none — moving it into the construct is what gives it DB access for the email lookup. The construct is only instantiated when VPC+secret+endpoint are supplied (`cognito-stack.ts:292`), which is true for the one real (staging=prod) pool.

### `AdminLinkProviderForUser` shape (AWS API)
Merging an external OIDC (Google) identity into an existing native Cognito user:
- `DestinationUser`: `{ ProviderName: 'Cognito', ProviderAttributeValue: '<existing native username/sub>' }`
- `SourceUser`: `{ ProviderName: 'Google', ProviderAttributeName: 'Cognito_Subject', ProviderAttributeValue: '<Google subject from event.userName>' }`
- `UserPoolId`: `event.userPoolId`
The destination user's `sub` is preserved; the Google identity becomes an additional linked provider on that same user. (For the brand-new path there is no destination user, so we skip the call entirely and let auto-confirm + JIT take over.)

### Files to touch — current state & what to preserve
| File | Current state | Change | Preserve |
|---|---|---|---|
| `infrastructure/lib/stacks/cognito-stack.ts` | `:43-47` `PreSignupLogGroup`; `:50-79` inline `preSignupLambda`; `:213` `lambdaTriggers.preSignUp` | Delete inline Lambda + its log group; remove `preSignUp` from `lambdaTriggers` (PreSignUp now wired in the construct) | `customEmailSender` trigger; pool config; client config; `:292-304` construct instantiation |
| `infrastructure/lib/constructs/cognito-user-sync-triggers.ts` | 4 triggers (post-confirmation, pre-token, pre-auth, post-auth); `commonLambdaProps`; pool-scoped Cognito grant comment pattern at `:154-170` | Add `preSignUpTrigger` NodejsFunction + log group + secret/CW grants + `AdminLinkProviderForUser`/`ListUsers` IAM grant + `addTrigger(PRE_SIGN_UP, ...)` | all 4 existing triggers + their grants/wiring; `commonLambdaProps`/`commonEnv` |
| `infrastructure/lib/lambda/triggers/pre-signup.ts` | **does not exist** | NEW handler: native verbatim validation + federated `AdminLinkProviderForUser` merge | n/a |
| `infrastructure/test/unit/lambda/pre-signup.test.ts` | **does not exist** | NEW handler unit test (5 cases) | n/a |
| `infrastructure/test/unit/cognito-stack.test.ts` (or new construct test) | inline presignup assertions, if any | add bundled-fn + PreSignUp wiring + IAM-policy assertions (with VPC props) | `Schema`, `ReadAttributes`, password-policy, existing tests |
| `docs/architecture/06b-user-lifecycle-sync.md` | preSignUp UUID note `:629`; federated/PreAuth notes `:743-760` | add "Pattern F: Federated sign-in + account linking" | existing Patterns 1/1b/2/3/3b/N |

### Testing standards (per CLAUDE.md + infrastructure/CLAUDE.md)
- **Handler unit test is MANDATORY** — a Lambda that fails module-load `Runtime.ImportModuleError`s and 503s all auth. Test 1 (`expect(typeof handler).toBe('function')`) catches that. Mock `pg`-backed `getDbClient` + the Cognito/CloudWatch SDK clients (don't hit a real DB/AWS).
- **Native deps + bundling:** the trigger imports `pg` (transitively, via `common/database`). The construct's `commonLambdaProps.bundling.forceDockerBundling: false` + `externalModules: ['@aws-sdk/*']` is the established pattern — `pg` is a pure-JS dependency (no native binary like `pg-native`/`sharp`), so local esbuild bundling is safe (the other DB-touching triggers — post-confirmation, pre-auth, pre-token — already bundle this way). The CLAUDE.md `tryBundle`-must-return-false rule targets **native** deps (`sharp`, `pg-native`); plain `pg` does not trigger it. If `pg-native` is ever introduced, that rule applies — out of scope here.
- Infra tests: jest `npm test`; `npx tsc --noEmit` must be clean. Run via tee-to-temp-file then grep (CLAUDE.md), don't re-run suites repeatedly.
- **No real outbound auth flows from dev** (staging IS production): real link/new-user verification is Phase 3 (Story 12.7, verify-only) + Phase 5 manual prod smoke — not this story.

### Project Structure Notes
- Trigger sources live in `infrastructure/lib/lambda/triggers/*.ts`; their handler tests in `infrastructure/test/unit/lambda/*.test.ts`; the shared DB client in `infrastructure/lib/lambda/triggers/common/database.ts`.
- Deploy tier: a Cognito/trigger change is **Layer-3** (`infrastructure/CLAUDE.md` deployment layers; `npm run deploy:staging:layer3-application`).

### References
- [Source: docs/plans/sso-oidc-federation.md §5 "Phase 2 — Account-linking trigger"] (inline→NodejsFunction, triggerSource branch, AdminLinkProviderForUser, mandatory handler test, Layer-3, MEDIUM risk, rollback, Pattern F doc-drift)
- [Source: docs/plans/sso-oidc-federation.md §3 "Account-linking is the heart…"] (AliasExistsException, sign-in-alias collision, sub-preserving merge) · §4 (one real pool, staging IS prod) · §2 (~70% wired)
- [Source: docs/architecture/ADR-010-federated-identity-via-cognito.md] D1 (broker, backend unchanged), D3 (transparent link via AdminLinkProviderForUser in PreSignUp_ExternalProvider, sub preserved), D4 (new Google user → ATTENDEE via JIT), D5 (provisioning + gating on provider-agnostic paths), D7 (target trigger set: PreSignUp keeps validation + adds linking)
- [Source: infrastructure/lib/stacks/cognito-stack.ts:42-79 (inline preSignUp + log group), :59-72 (verbatim company-UUID validation), :160-166 (email sign-in alias + autoVerify → the collision), :213 (lambdaTriggers wiring), :292-304 (construct instantiation guard)]
- [Source: infrastructure/lib/constructs/cognito-user-sync-triggers.ts:49-70 (commonEnv/commonLambdaProps), :137-152 (secret + CloudWatch grants), :154-170 (pool-scoped Cognito grant + circular-dep comment pattern), :176-191 (addTrigger pattern)]
- [Source: infrastructure/lib/lambda/triggers/pre-authentication.ts:1-5,25-121,126-147 (getDbClient usage, callbackWaitsForEmptyEventLoop, fire-and-forget metrics)]
- [Source: infrastructure/lib/lambda/triggers/post-confirmation.ts:16-30 (CognitoIdentityProviderClient + AdminUpdateUserAttributes import pattern), :231-287 (email-keyed lookup against user_profiles)]
- [Source: infrastructure/test/unit/lambda/post-authentication.test.ts:16-57 (mock style, makeEvent helper, module-load test, fail-open tests)]
- [Source: infrastructure/test/unit/cognito-stack.test.ts:46-98 (Schema + ReadAttributes Template.fromStack assertions, Capture usage)]
- [Source: docs/architecture/06b-user-lifecycle-sync.md:599-667 (custom-attribute inventory, preSignUp UUID note :629), :743-760 (PreAuthentication corrected / federated bypass / ADR-010 target)]
- [Source: .github/doc-drift-mappings.yml:62-65 (infrastructure → 06/08); 06b is the canonical user-lifecycle doc per ADR-010 "Affects documentation"]
- ADR-001 (Cognito-for-auth-only), ADR-003 (meaningful IDs), ADR-009 (Cognito FORCE_CHANGE_PASSWORD)

### Prerequisites & sequencing
- **Hard prereq: Story 12.5** (Phase 1 — Google IdP defined + added to client `supportedIdentityProviders`). No `PreSignUp_ExternalProvider` event can fire without it.
- **Related (not blocking this story's code):** Story 12.2 (PR 1A — API-Gateway `is_active` gate, which is what blocks deactivated federated users) and Story 12.3 (PR 1B — canonical JIT, which is what creates the brand-new federated user's DB row). This trigger deliberately does neither — it only validates (native) or links (federated).
- **Verified by:** Story 12.7 (Phase 3, verify-only) — confirms a real Google identity links an existing user (roles preserved) and a brand-new Google user provisions as ATTENDEE.

## Dev Agent Record

### Agent Model Used

_(empty — to be filled by dev-story)_

### Debug Log References

_(empty)_

### Completion Notes List

_(empty)_

### File List

_(empty)_

### Change Log

| Date | Change |
|---|---|
| 2026-06-01 | Story 12.6 drafted (Account-linking PreSignUp trigger, SSO Phase 2). Status: ready-for-dev. |
