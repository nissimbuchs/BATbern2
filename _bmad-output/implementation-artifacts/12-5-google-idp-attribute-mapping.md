# Story 12.5: Define the Google IdP + Add to Client (SSO Phase 1)

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **platform engineer wiring federated identity into the single real Cognito pool**,
I want **a `UserPoolIdentityProviderGoogle` defined in `cognito-stack.ts` (reading the Google client id/secret from the Secrets Manager secret created in Story 12.4, never inlined), with Google's `email`/`given_name`/`family_name` claims mapped onto the pool's attributes, and `GOOGLE` added to the app client's `supportedIdentityProviders` alongside the existing `COGNITO`**,
so that **the Cognito hosted-UI can broker a Google OIDC sign-in (testable directly via `/oauth2/authorize?...&identity_provider=Google`) while every existing email/password login keeps working unchanged — a purely additive, invisible-to-users change with a one-line rollback.**

This is **Phase 1** of Epic 12 (SSO / OIDC Federation), per `docs/plans/sso-oidc-federation.md` §5 "Phase 1 — Define the Google IdP + add to client" and ADR-010 §D1/D9. It is **additive and invisible**: there is **no frontend change and no Google button** (that is Stories 12-7/12-9). It **consumes** the Secrets Manager secret stood up by the DONE Story 12.4 runbook (`batbern/staging/sso/google-oauth`). It does **not** build the account-linking trigger — a brand-new Google user signing in at this phase hits the §3 gotchas with no linking trigger yet, so **real federated testing is gated on Phase 2 (Story 12-6)**; this phase tests only the provider/client wiring (via the hosted-UI authorize URL) and, if at all, with throwaway emails.

## Acceptance Criteria

1. **(Google IdP defined, secret read — not inlined.)** A `cognito.UserPoolIdentityProviderGoogle` is added to `infrastructure/lib/stacks/cognito-stack.ts` (after the `UserPool` is created at `:157-218` and before/around the client at `:234`). Its `clientId` and `clientSecretValue` are sourced from the Secrets Manager secret **`batbern/staging/sso/google-oauth`** (created by Story 12.4; ARN `arn:aws:secretsmanager:eu-central-1:188701360969:secret:batbern/staging/sso/google-oauth-TpSKCG`, JSON `{"clientId":"…","clientSecret":"…"}`), resolved via `secretsmanager.Secret.fromSecretNameV2(this, 'GoogleOAuthSecret', 'batbern/staging/sso/google-oauth')` (the established pattern at `event-management-stack.ts:71`) + `secret.secretValueFromJson('clientId').unsafeUnwrap()` for the id and `secret.secretValueFromJson('clientSecret')` (a `SecretValue`) for `clientSecretValue`. **The secret value is NEVER inlined, logged, or committed** (CLAUDE.md security). Scopes requested from Google: `['openid', 'email', 'profile']` (matches Story 12.4 consent screen; non-sensitive).

2. **(Attribute mapping — `email` is required, name folds via standard attrs.)** The provider's `attributeMapping` maps:
   - Google `email` → pool `email` (`{ email: cognito.ProviderAttribute.GOOGLE_EMAIL }`) — **required** so the email-keyed account-linking (Phase 2) and the email sign-in alias resolve.
   - Google `given_name` → pool `givenName` (`cognito.ProviderAttribute.GOOGLE_GIVEN_NAME`) and Google `family_name` → pool `familyName` (`cognito.ProviderAttribute.GOOGLE_FAMILY_NAME`), **only if** the pool declares standard `given_name`/`family_name` attributes (see AC3). These feed the firstName/lastName that downstream provisioning needs.
   - **The name MUST NOT be mapped into the `custom:preferences` JSON blob** — Cognito attribute mapping is strictly **one provider claim → one pool attribute** and cannot write into a JSON *sub-field* (`firstName`/`lastName`) of `custom:preferences`. See AC4 + the Dev Note "custom:preferences JSON-fold is NOT expressible via Cognito attribute mapping" for the resolution (canonical JIT, Story 12-3, reads names from the mapped standard attributes — NOT from `custom:preferences` — for federated users).

3. **(Pool exposes standard name attributes for the mapping target.)** Because the pool currently declares only `email` under `standardAttributes` (`cognito-stack.ts:169-174`) and the `custom:preferences` JSON cannot be a mapping target (AC2), the pool's `standardAttributes` is extended with `givenName: { required: false, mutable: true }` and `familyName: { required: false, mutable: true }` so Google's `given_name`/`family_name` have a destination. **If** the dev determines (during implementation) that adding standard attributes to the existing pool risks a non-additive CFN diff on the single real pool, the fallback is to map the name into `custom:` attributes that already exist — but there is **no** existing custom name attribute (`username`/`role`/`companyId`/`preferences` only, `:175-197`), so the standard-attribute path is preferred. The chosen approach + its CFN-diff classification (additive vs. replacement) is recorded in the Dev Agent Record. **No `custom:preferences` schema change.**

4. **(`custom:preferences` JSON-fold flagged as an Open Question / handled by JIT, not by mapping.)** The plan's wording "fold into `custom:preferences` JSON … and/or standard `given_name`/`family_name`" is resolved in this story as: **standard attributes only** for the Cognito mapping (`given_name`/`family_name`), because the JSON-fold is **not expressible** in Cognito attribute mapping (1:1 claim→attr; no sub-field writes). The canonical-JIT provisioning path (Story 12-3, PR 1B) is responsible for reading names for **federated** users from the mapped standard `given_name`/`family_name` attributes (the federated equivalent of how `post-confirmation.ts:218-225` reads `firstName`/`lastName`/`language` from the `custom:preferences` JSON for native sign-ups). This story does **not** change `post-confirmation.ts` or the JIT interceptor — it only guarantees the names are *present on the Cognito identity* via standard-attribute mapping. (See Open Question OQ-1.)

5. **(`GOOGLE` added to client supportedIdentityProviders; `COGNITO` stays.)** `cognito-stack.ts:259-261` `supportedIdentityProviders` becomes `[cognito.UserPoolClientIdentityProvider.COGNITO, cognito.UserPoolClientIdentityProvider.GOOGLE]`. **`COGNITO` MUST remain** so password auth keeps working. The client construct gains an explicit dependency on the Google IdP (`userPoolClient.node.addDependency(googleIdp)`) so CloudFormation creates the provider before the client references it (the standard CDK gotcha for IdP-before-client ordering).

6. **(No frontend change; test via hosted-UI authorize URL.)** This story touches **only** `infrastructure/`. No `web-frontend/` file changes; no "Continue with Google" button (Stories 12-7/12-9). Verification of the wiring is by hitting the hosted-UI directly: `https://batbern-staging-auth.auth.eu-central-1.amazoncognito.com/oauth2/authorize?identity_provider=Google&client_id=<webClientId>&response_type=code&scope=openid+email+profile&redirect_uri=https://www.batbern.ch/auth/callback` should redirect to Google's consent (not return `Unknown identity provider`). **Real end-to-end federated login is GATED on Phase 2 (Story 12-6)** — without the linking trigger a brand-new Google user would orphan/collide per §3; test only with throwaway emails if testing at all before 12-6.

7. **(CDK `Template.fromStack` tests — mandatory; no Lambda handler test this phase.)** `infrastructure/test/unit/cognito-stack.test.ts` gains assertions that:
   - an `AWS::Cognito::UserPoolIdentityProvider` resource exists with `ProviderName: 'Google'`, `ProviderType: 'Google'`, and an `AttributeMapping` containing `email` (and `given_name`/`family_name`);
   - the `AWS::Cognito::UserPoolClient` `SupportedIdentityProviders` now equals/contains `['COGNITO', 'Google']` (update the existing assertion at `:123` which asserts `['COGNITO']`).
   There is **no Lambda change in this phase** (the linking trigger is Story 12-6), so **no handler test is required here** — but the `Template.fromStack` test IS required (CLAUDE.md infra-test rule). Tests run with `npm test -- cognito-stack.test.ts` (jest) + `tsc --noEmit` clean.

8. **(Deploy tier, risk, rollback.)** This is a Cognito change ⇒ **Layer-3** deploy (`npm run deploy:staging:layer3-application`, or full `npm run deploy:staging`). **Risk: low** — purely additive provider; `COGNITO` stays so password auth is untouched; no token-issuance, trigger, or backend change. **Rollback: remove `GOOGLE` from `supportedIdentityProviders` (one-line revert)**; the IdP resource itself can also be removed (it has no users yet, so no orphaning). The single real pool is production (188701360969); the change is verified to be additive (new provider + new optional standard attributes + extended client provider list) before deploy.

9. **(Doc-drift / ADR.)** ADR-010 already records the federated-identity decision (`docs/architecture/ADR-010-federated-identity-via-cognito.md` §D1/D9) and needs no edit. The dedicated "Pattern F: Federated sign-in + account linking" addition to `docs/architecture/06b-user-lifecycle-sync.md` is **Story 12-6's** job (it lands with the linking trigger) — scope it there, **not** here. No doc-drift mapping in `.github/doc-drift-mappings.yml` covers `infrastructure/lib/stacks/` (verified — the mappings are service-source-pattern-keyed), so this infra-only change has no mapped doc; add `[no-doc]` to the commit (pure infra wiring, no business-logic/contract change visible to a mapped doc).

## Tasks / Subtasks

- [x] **Task 1 — Read the Google OAuth secret in `cognito-stack.ts` (AC: 1)**
  - [x] Import already present (`secretsmanager` at `cognito-stack.ts:6`). Added `const googleOAuthSecret = secretsmanager.Secret.fromSecretNameV2(this, 'GoogleOAuthSecret', 'batbern/staging/sso/google-oauth');` after the `UserPool` (model: `event-management-stack.ts:71`). No ARN/value hardcoded.
  - [x] Confirmed in the staging synth: both `client_id` AND `client_secret` render as `{{resolve:secretsmanager:arn:aws:secretsmanager:eu-central-1:188701360969:secret:batbern/staging/sso/google-oauth:SecretString:…}}` dynamic references — **zero plaintext** in the template (`/tmp/cognito-synth.log:592-593`).

- [x] **Task 2 — Define `UserPoolIdentityProviderGoogle` with attribute mapping (AC: 1, 2)**
  - [x] RED: added `should_defineGoogleIdentityProvider_when_stackDeployed` asserting `ProviderName/ProviderType: 'Google'` + `AttributeMapping` with `email`/`given_name`/`family_name`; plus `should_sourceGoogleClientSecretFromSecretsManager_when_idpDefined` for the dynamic-reference shape.
  - [x] GREEN: constructed `googleIdp` with `clientId` (unwrapped), `clientSecretValue` (kept as `SecretValue`), `scopes: ['openid','email','profile']`, and the 3-claim `attributeMapping`.
  - [x] Documented the `clientId` unwrap / `clientSecretValue` no-unwrap asymmetry in a code comment.

- [x] **Task 3 — Add standard name attributes to the pool as the mapping target (AC: 3)**
  - [x] RED: added `should_configureStandardNameAttributes_when_userPoolCreated` asserting `Schema` includes `given_name` + `family_name` (`Required: false`, `Mutable: true`).
  - [x] GREEN: extended `standardAttributes` with `givenName`/`familyName` (`required:false, mutable:true`) alongside `email`.
  - [x] **CFN-diff classification = ADDITIVE (no pool replacement).** Staging synth succeeded (exit 0). Decisive cross-check via `describe-user-pool` on the LIVE pool (`eu-central-1_FtgfxgQRF`): `given_name`/`family_name` **already exist** with exactly `Required:false, Mutable:true` (Cognito always provisions standard OIDC attrs; CDK `standardAttributes` only renders their Required/Mutable), so the `Schema` change is a no-op for those. `list-identity-providers` returned `[]`, so the Google IdP is a pure new-resource add and the client provider-list gains `Google` in-place. No replacement risk → OQ-2 resolved.
  - [x] `customAttributes` untouched — no `custom:preferences` schema change (AC4).

- [x] **Task 4 — Add `GOOGLE` to client `supportedIdentityProviders` + ordering dependency (AC: 5)**
  - [x] RED: updated `should_configureOAuthFlows_when_appClientCreated` so `SupportedIdentityProviders` is `['COGNITO', 'Google']`.
  - [x] GREEN: `supportedIdentityProviders: [COGNITO, GOOGLE]` — `COGNITO` kept.
  - [x] Added `this.userPoolClient.node.addDependency(googleIdp);` — synth confirms client `DependsOn: [GoogleIdp206FECA7]` (`/tmp/cognito-synth.log:645`).

- [x] **Task 5 — Infra tests + type-check green (AC: 7)**
  - [x] `npm test -- cognito-stack.test.ts` → **10 passed, 0 failed** (`/tmp/cognito-green2.log`).
  - [x] `npx tsc --noEmit` clean (exit 0). No Lambda handler test this phase (no Lambda change).

- [x] **Task 6 — Verification + deploy/rollback note (AC: 6, 8, 9)**
  - [x] Confirmed IdP `client_secret` (and `client_id`) are `{{resolve:secretsmanager:...}}` dynamic references, not plaintext (synth grep).
  - [x] Deploy tier = **Layer-3** (`npm run deploy:staging:layer3-application`); rollback = remove `GOOGLE` from `supportedIdentityProviders` (one-line). Real federated login gated on Story 12-6 (no linking trigger yet).
  - [x] Hosted-UI authorize-URL smoke (AC6) **deferred to Story 12-6 verification** per the story's own gate — a brand-new Google sign-in pre-12-6 would orphan/collide (§3 gotchas, no linking trigger). Not run with throwaway email to avoid touching the production pool.
  - [x] Commit will carry `[no-doc]` (infra-only wiring; no mapped doc; ADR-010 already covers the decision; 06b "Pattern F" is Story 12-6's).

## Dev Notes

### The crux: `custom:preferences` JSON-fold is NOT expressible via Cognito attribute mapping
The plan (`sso-oidc-federation.md:184-188`) says map Google `name`/`given_name`/`family_name`
"**fold into `custom:preferences` JSON** … **and/or** standard `given_name`/`family_name`". Verified
against the CDK API (`aws-cognito/lib/user-pool-idps/base.d.ts`): Cognito's `AttributeMapping` is a
**1:1 map from one provider claim to one pool attribute**. `attributeMapping.custom` maps a provider
claim to a **named custom attribute** (e.g. `custom:something`), but it **cannot write into a JSON
sub-field** of an existing blob like `custom:preferences` (which native sign-ups populate as a single
JSON string: `{"firstName":…,"lastName":…,"language":…}` per `post-confirmation.ts:53-68,99-111`).
There is no Cognito mechanism to merge `given_name` into `custom:preferences.firstName`.

**Resolution adopted in this story:** map names to **standard `given_name`/`family_name`** pool
attributes (AC2/AC3) — the only mapping Cognito supports. The *consumption* of those names for
**federated** users is the job of the **canonical JIT path (Story 12-3 / PR 1B)**, which must read
firstName/lastName for a federated identity from the mapped standard attributes — the federated
counterpart of how `post-confirmation.ts:218-225` reads them from the `custom:preferences` JSON for
native sign-ups. This story only ensures the names are **present on the Cognito identity**; it does
**not** modify any trigger or interceptor. This is flagged as **OQ-1** below and is the contract
hand-off to Story 12-3/12-6.

### Architecture context — why this is low-risk and additive
- **Broker model (ADR-010 §D1):** Cognito is the OIDC relying-party toward Google and the OAuth
  provider toward the app. The React app keeps receiving the **same JWT shape**; the backend
  (`JwtRolesConverter`, `@PreAuthorize`, Pattern 3b, PreTokenGeneration) is **untouched**.
- **~70% already wired (plan §2):** hosted-UI domain (`cognito-stack.ts:276-281`, output URL
  `:341-345`), authorization-code grant + `openid/email/profile` scopes (`:247-258`), callback/logout
  URLs `/auth/callback` + `/logout` (`:220-231`), SPA client `generateSecret: false` (`:243`). The
  **only missing pool-side piece** this story adds is the IdP itself + `GOOGLE` in
  `supportedIdentityProviders` (currently `[COGNITO]` only at `:259-261`).
- **Story 12.1 already landed on this file:** `readAttributes` already drops `'role'`
  (`:267-269`), the `custom:role` permanence comment is at `:262-266`. Don't disturb those.
- **Secret comes from Story 12.4 (DONE):** secret name `batbern/staging/sso/google-oauth`, JSON
  `{clientId, clientSecret}`, rotated and closed (12-4 Completion Notes). The redirect URI Google was
  configured with is `https://batbern-staging-auth.auth.eu-central-1.amazoncognito.com/oauth2/idpresponse`
  — that is Cognito's fixed federation callback (distinct from our app `/auth/callback`), so no
  redirect change is needed here.

### Files to touch — current state & what to preserve
| File | Current state | Change | Preserve |
|---|---|---|---|
| `infrastructure/lib/stacks/cognito-stack.ts` | `:259-261` `supportedIdentityProviders: [COGNITO]`; `:169-174` standardAttributes = `email` only; `:175-197` customAttributes (username/role/companyId/preferences); `secretsmanager` imported `:6` | add `GoogleOAuthSecret` lookup; add `UserPoolIdentityProviderGoogle` with attributeMapping; add `givenName`/`familyName` standard attrs; add `GOOGLE` to `supportedIdentityProviders`; `client.node.addDependency(googleIdp)` | `COGNITO` in the list; all `customAttributes` (esp. `preferences` JSON — no schema change); Story 12.1 `readAttributes`/`writeAttributes` (`:267-272`); the inline preSignUp lambda (`:50-79` — Story 12-6 replaces it, NOT here); KMS/CustomEmailSender wiring |
| `infrastructure/test/unit/cognito-stack.test.ts` | `:123` asserts `SupportedIdentityProviders: ['COGNITO']`; `:46-66` asserts custom-attr schema; `:116-125` OAuth flows | update `:123` to include `'Google'`; add IdP-resource assertion; add `given_name`/`family_name` schema assertion | the Story 12.1 `readAttrs` capture assertions (`:70-98`); all other tests |

### Secret-handling specifics (CLAUDE.md security)
- `secretsmanager.Secret.fromSecretNameV2(...)` does **not** read the value at synth — it produces a
  reference. `.secretValueFromJson('clientId')` / `('clientSecret')` yield `SecretValue`s that synth
  to `{{resolve:secretsmanager:batbern/staging/sso/google-oauth:SecretString:clientId::}}` dynamic
  references → the plaintext never enters the CloudFormation template or git. The `clientId` prop is a
  plain `string`, so it needs `.unsafeUnwrap()`; the client *id* is not sensitive (it ends in
  `.apps.googleusercontent.com` and is public), but keep the **secret** as a `SecretValue` via
  `clientSecretValue` so it stays a dynamic reference. **Never** paste the secret value into chat,
  logs, the PR, or a test fixture.

### Testing standards (per CLAUDE.md infra rule + TDD)
- Infra unit tests use `Template.fromStack` (`cognito-stack.test.ts`), `npm test -- cognito-stack.test.ts`.
- **No Lambda handler test this phase** — there is no Lambda change (the account-linking
  `NodejsFunction` is Story 12-6). The `Template.fromStack` assertions (AC7) are the required coverage.
- Run via tee-to-temp-file then grep (CLAUDE.md) — don't re-run the suite repeatedly.
- `tsc --noEmit` clean (infra TypeScript).

### Deploy tier & ordering (infra/CLAUDE.md)
- Cognito = **Layer-3** (`npm run deploy:staging:layer3-application`; or `npm run deploy:staging`).
  Single real pool = production (188701360969). Verify the `cdk diff`/synth shows an **additive**
  pool update (new IdP resource + optional standard attrs + extended client provider list), not a pool
  replacement, before deploy. Rollback is a one-line revert (drop `GOOGLE`).

### References
- [Source: docs/plans/sso-oidc-federation.md#Phase 1 — Define the Google IdP + add to client] (`:182-196`) — IdP, attribute-mapping, `supportedIdentityProviders`, no-frontend, test-via-authorize-URL, Layer-3, one-line rollback, Phase-2 testing gate
- [Source: docs/plans/sso-oidc-federation.md#2 ~70% already wired] (`:29-40`) — hosted-UI `:271`(now `:276`), grant+scopes `:247`, callbacks `:220`, SPA no-secret `:243`, provider gap `:259-261`
- [Source: docs/plans/sso-oidc-federation.md#3 The two Cognito gotchas] (`:42-67`) — federated skips PostConfirmation/PreAuthentication; `AliasExistsException` risk without linking → Phase-2 gate
- [Source: docs/architecture/ADR-010-federated-identity-via-cognito.md §D1, §D9] — broker model; new Google project, non-sensitive scopes
- [Source: _bmad-output/implementation-artifacts/12-4-google-cloud-oauth-consent-setup.md] — secret `batbern/staging/sso/google-oauth` (ARN `…google-oauth-TpSKCG`, JSON `{clientId, clientSecret}`); redirect URI `…/oauth2/idpresponse`; DONE + rotated
- [Source: infrastructure/lib/stacks/cognito-stack.ts:6,157-218,169-174,175-197,234-273,259-261,267-272,276-281] — pool, standard/custom attrs, client, provider list, Story 12.1 readAttributes, hosted-UI domain
- [Source: infrastructure/lib/stacks/event-management-stack.ts:71] — `secretsmanager.Secret.fromSecretNameV2` pattern
- [Source: infrastructure/lib/lambda/triggers/post-confirmation.ts:53-68,99-111,218-225] — `custom:preferences` JSON shape (firstName/lastName/language) that native sign-ups use; the federated counterpart is standard `given_name`/`family_name` (this story) read by JIT (Story 12-3)
- [Source: infrastructure/test/unit/cognito-stack.test.ts:46-66,70-98,116-125] — schema, readAttrs capture (Story 12.1), OAuth-flows `SupportedIdentityProviders: ['COGNITO']` assertion to update
- [Source: aws-cdk-lib/aws-cognito/lib/user-pool-idps/base.d.ts] — `AttributeMapping` is 1:1 provider-claim→pool-attr; `ProviderAttribute.GOOGLE_EMAIL/_GIVEN_NAME/_FAMILY_NAME`; `custom` maps to a named attr, NOT a JSON sub-field
- ADR-001 (Cognito-for-auth-only), ADR-003/004 (company/user fields owned by DB)

### Open Questions
- **OQ-1 (AC2/AC4) — `custom:preferences` JSON-fold is not expressible in Cognito attribute mapping; resolved by mapping to standard `given_name`/`family_name` and having canonical JIT (Story 12-3/PR 1B) read names for federated users from those standard attributes.** This story maps names to standard attributes only and does NOT modify any trigger/interceptor. **Confirm with the architect** that Story 12-3's JIT will read federated names from standard `given_name`/`family_name` (it currently reads native names from the `custom:preferences` JSON via `post-confirmation.ts`). If the architect instead wants names folded into `custom:preferences` for federated users, that requires a **trigger** (e.g. the PreSignUp_ExternalProvider lambda in Story 12-6 writing the JSON) — NOT attribute mapping — and that work belongs in Story 12-6, not here. Flag at PR time.
- **OQ-2 (AC3) — adding standard `given_name`/`family_name` to the existing single real pool.** Must be confirmed **additive** (no pool replacement) via `cdk diff`/synth before the Layer-3 deploy. If CFN signals a replacement, escalate (a pool replacement is unacceptable on the production pool) and fall back to coordinating with Story 12-6 to capture names in the linking trigger instead of via standard-attribute mapping.

## Review Findings (code review 2026-06-03, bmad-code-review, Claude Opus 4.8 1M; 3 adversarial layers)

**✅ Clean — no findings.** Acceptance Auditor verified all 9 ACs satisfied against source: Google IdP defined with `clientId` (`unsafeUnwrap`) + `clientSecretValue` (kept as `SecretValue` → `{{resolve:secretsmanager:…}}` dynamic reference, never inlined); scopes `['openid','email','profile']`; `attributeMapping` email/givenName/familyName; `standardAttributes` extended with `givenName`/`familyName` (`required:false, mutable:true`) with no `custom:preferences` schema change; `supportedIdentityProviders: [COGNITO, GOOGLE]` (COGNITO retained) + `userPoolClient.node.addDependency(googleIdp)`. Blind/Edge layers raised no defects in the 12.5 surface. (All actionable findings from this pass are in Story 12.6.)

## Dev Agent Record

### Agent Model Used
Claude Opus 4.8 (1M context) — bmad-dev-story (Amelia), 2026-06-03.

### Debug Log References
- `/tmp/cognito-red.log` — RED: 4 new assertions failing pre-implementation.
- `/tmp/cognito-green2.log` — GREEN: 10 passed / 0 failed.
- `/tmp/cognito-tsc.log` — `tsc --noEmit` clean (exit 0).
- `/tmp/cognito-synth.log` — staging synth (exit 0): IdP resource, dynamic-reference secret, Schema name attrs, client `DependsOn`.
- Live-pool cross-check (read-only): `describe-user-pool eu-central-1_FtgfxgQRF` → `given_name`/`family_name` already `Required:false,Mutable:true`; `list-identity-providers` → `[]`.

### Completion Notes List
- **All 9 ACs satisfied.** Google IdP wired in `cognito-stack.ts`, secret read from Secrets Manager (never inlined — synth proves `{{resolve:secretsmanager:...}}` for both id and secret), names mapped to standard `given_name`/`family_name`, `GOOGLE` added to client `supportedIdentityProviders` (`COGNITO` retained), client→IdP ordering dependency added.
- **OQ-2 RESOLVED (additive, not replacement).** The live production pool already exposes `given_name`/`family_name` with the exact `Required:false,Mutable:true` I declare, and has zero existing IdPs — so the change is a pure addition (new IdP resource + in-place client provider-list extension; Schema name-attr diff is a no-op). No pool replacement.
- **OQ-1 (hand-off to architect / Story 12-3) — still open by design.** This story only guarantees federated names land on standard `given_name`/`family_name`. Consumption of those names for federated users is the canonical-JIT path (Story 12-3). Confirm at PR time that 12-3's JIT reads federated names from standard attrs (it currently reads native names from the `custom:preferences` JSON). If names must instead be folded into `custom:preferences` for federated users, that needs the PreSignUp_ExternalProvider Lambda in **Story 12-6**, not attribute mapping.
- **Hosted-UI smoke deferred to 12-6** per the story's own Phase-2 gate (no linking trigger yet → a brand-new Google sign-in would orphan/collide). Not exercised against the production pool.
- **No Lambda change** this phase → no handler test required (the linking `NodejsFunction` is Story 12-6). `Template.fromStack` assertions are the mandated coverage.
- Deploy tier **Layer-3**; rollback = drop `GOOGLE` from `supportedIdentityProviders` (one-line). PR stacked on the 12-3 branch (bundled with Story 12-6 per Nissim).

### File List
- `infrastructure/lib/stacks/cognito-stack.ts` (modified) — Google OAuth secret lookup; `UserPoolIdentityProviderGoogle` with attribute mapping; `givenName`/`familyName` standard attributes; `GOOGLE` in `supportedIdentityProviders`; client→IdP `addDependency`.
- `infrastructure/test/unit/cognito-stack.test.ts` (modified) — updated OAuth-flows assertion to `['COGNITO','Google']`; added IdP-resource, secret-dynamic-reference, and standard-name-attribute tests.

### Change Log

| Date | Change |
|---|---|
| 2026-06-01 | Story 12.5 drafted (SSO Phase 1 — Google IdP + attribute mapping + supportedIdentityProviders). Status ready-for-dev. |
| 2026-06-03 | Implemented (TDD). Google IdP + secret-sourced creds + standard name-attr mapping + `GOOGLE` client provider + ordering dep. 10/10 infra tests green, tsc clean, staging synth clean. OQ-2 resolved ADDITIVE via live-pool inspection. Status → review. |
