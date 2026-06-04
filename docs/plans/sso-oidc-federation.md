# SSO / OIDC Federation Plan — "Sign in with Google" (Apple deferred)

**Author:** Winston (System Architect) · **Date:** 2026-05-31 · **Status:** Draft for review

## 1. Goal & scope

Add federated **Sign-in with Google** to BATbern via AWS Cognito's hosted-UI identity
federation (OpenID Connect). Existing email/password Cognito users must be able to use
Google with the **same account** (transparent account-linking — no migration, no lost roles).
Apple and generic-OIDC corporate providers are **explicitly deferred** to a later phase
(Apple's private-relay email + name-on-first-auth-only + 6-month key rotation make it a
materially harder, separate decision).

### What "OIDC federation" means here

Cognito acts as a **broker**: an OIDC *relying party* toward Google, and an OIDC/OAuth
*provider* toward our React app. The app keeps talking only to Cognito and keeps receiving
the **same JWT shape** it already parses. Therefore the **entire backend is provider-agnostic
and unchanged** — `shared-kernel/.../security/JwtRolesConverter`, every `@PreAuthorize`, the
Pattern 3b DB-fallback, `PreTokenGeneration` role enrichment: all untouched.

```
Browser → Cognito Hosted UI → Google (OIDC) → back to Cognito → app /auth/callback
                       (federation / broker)
```

## 2. Why this is low-risk: ~70% is already wired

| Piece | State | Location |
|---|---|---|
| Cognito **Hosted-UI domain** (required for federation) | ✅ exists | `infrastructure/lib/stacks/cognito-stack.ts:271` |
| App client **authorization-code grant** + scopes `openid/email/profile` | ✅ | `cognito-stack.ts:247-255` |
| Callback/logout URLs `/auth/callback`, `/logout` (all envs) | ✅ | `cognito-stack.ts:220-231` |
| SPA client, **no secret** | ✅ | `cognito-stack.ts:243` |
| Frontend Amplify **`loginWith.oauth`** block (domain, scopes, code flow) | ✅ already there, dormant | `web-frontend/src/config/amplify.ts:54-61` |
| JWT claim extraction (`custom:role`, `custom:username`, …) | ✅ provider-agnostic | `web-frontend/src/services/auth/authService.ts:424` |
| **Google/Apple/OIDC identity provider** | ❌ none (`COGNITO` only) | `cognito-stack.ts:259-261` |
| `signInWithRedirect(...)` call | ❌ missing | `authService.ts` (password-only) |
| `/auth/callback` route + handler | ❌ missing | `web-frontend/src/App.tsx` |
| "Continue with Google" button | ❌ missing | `web-frontend/src/components/auth/LoginForm/LoginForm.tsx` |

## 3. The two Cognito gotchas every phase is designed around

These are **correctness**, not polish. For external-IdP (federated) sign-ins, Cognito invokes
**only** Pre-Sign-up, Pre-Token-Generation, and Post-Authentication triggers. It does **NOT**
fire **PostConfirmation** or **PreAuthentication**. Consequences:

1. **DB-row creation is bypassed.** `post-confirmation.ts` (the canonical `user_profiles`
   creation path for self-registered attendees) never runs for a brand-new Google user. We
   must provision via another path (the existing **JIT interceptor** + `PostAuthentication`
   email-link, hardened in Phase 3). *Moot* for existing users being linked — their row
   already exists.
2. **The `is_active` deactivation gate is bypassed.** `pre-authentication.ts` (which blocks
   `is_active = false`) does not run for federated logins. `06b-user-lifecycle-sync.md` already
   asserts the app layer also checks `is_active`; Phase 3 verifies that is true on the hot path.

### Account-linking is the heart of "can existing users switch"

By default a Google login for `john@x.com` creates a **separate** Cognito user (`Google_…`,
new `sub`). Because `email` is an auto-verified **sign-in alias** (`cognito-stack.ts:160-166`),
that collides → `AliasExistsException` / orphaned roles (our DB is keyed on `cognito_user_id`).

Fix: call **`AdminLinkProviderForUser`** inside the **`PreSignUp_ExternalProvider`** trigger
to merge the Google identity into the existing native user (matched by email). Result: **one**
Cognito user, **same `sub`** → `cognito_user_id` unchanged → roles/profile/company/history all
preserved; the user can then sign in with **either** password or Google. This is a transparent
link-on-first-federated-login, not a migration.

## 4. Hard constraint: staging IS production, single real pool

`infrastructure/` runs dev in **LOCAL mode** with **no AWS infra** — local dev points at the
**staging-account** Cognito pool, and per CLAUDE.md the staging account (188701360969) **is**
production. So there is exactly **one real user pool**, and every CDK change to it is a
production change. Each phase below is therefore **independently deployable to that one pool
without breaking existing password logins**, verified before the next phase, and the only
user-visible flip (Phase 5) is behind an instantly-revertible feature flag.

---

## 5. Phased delivery (each phase independently prod-deployable)

> **Ordering:** **PR 0 (Cognito attribute hygiene)** lands first as a standalone PR, ahead of
> the numbered SSO phases below. It has no dependency on SSO, but doing it first means the SSO
> attribute-mapping step (Phase 1) maps Google's claims into a *clean* target — no `companyId`
> to map, `preferences` as a seed only.

### PR 0 — Cognito attribute hygiene *(standalone; lands first, before all SSO phases)*
Realigns the token/attribute surface with the documented target in
`06b-user-lifecycle-sync.md` §"Cognito Custom-Attribute Inventory & Deprecation Status".
Three independently-shippable steps; **none touches authentication, token issuance, or the
partner authorization path** (that path already resolves company via the user-api in
`PartnerSecurityService`, so it is unaffected):

1. **Frontend reads company/preferences from the DB, not the token.** Switch
   `UserContext.companyId` / `.preferences` consumers to `/users/me` (the frontend already calls
   it for role hydration) instead of `extractUserContextFromToken` (`authService.ts:425,448`).
2. **Stop emitting/writing `custom:companyId`.** Remove the claim extraction in
   `api-gateway/UserContextExtractor.java:79-80` and `SecurityContextHelper.java:163`; stop
   writing it at signup (`authService.ts:318`). Company is seeded into `user_profiles.company_id`
   via the existing user-creation path and thereafter owned by the DB (ADR-003/004).
3. **`custom:role` hygiene.** Drop `'role'` from the client `readAttributes`
   (`cognito-stack.ts:264`) so it stops appearing in tokens, **and** run the `"UNUSED"` sentinel
   backfill (§8 Q6): a one-time paginated `AdminUpdateUserAttributes` script setting
   `custom:role='UNUSED'` on all existing users, plus write `"UNUSED"` on every new user going
   forward (documentation-in-the-data for anyone inspecting the Cognito console).
- **Deploy:** frontend deploy (step 1) → service/gateway deploy (step 2) → Layer-3 (step 3),
  in that order so the token reads move *before* the writes stop. **Risk:** low. **Rollback:**
  revert per step.
- Note: Cognito custom attributes are **permanent** — this PR *stops using* them; it does not
  (cannot) delete the schema. `preferences` remains as the signup seed.

### PR 1 — SSO-enabling backend: app-side `is_active` gate + canonical JIT *(backend-only, invisible; lands before the SSO phases)*
The highest-leverage prerequisite. It moves the two guarantees that federated logins can't get
from triggers (account-create, inactive-block) onto provider-agnostic, request-time paths — which
**makes SSO Phase 3 evaporate** (see below) and is *strictly better* than the trigger it replaces.

**Background (traced 2026-05-31):** the **PreAuthentication Lambda is the only place `is_active`
is enforced today** — there is **no** request-time app gate (the `06b` "app logic checks
is_active" claim is wrong; the `JITUserProvisioningInterceptor` only *sets* `isActive(true)` on
create, it never checks an existing user). Two consequences: (a) federated logins skip
PreAuthentication entirely → a deactivated user could sign in via Google unchecked; (b) even for
native logins, deactivation only bites at next login, so a 24h-valid token keeps working after
an admin deactivates the account.

**Part A — `is_active` gate at the API Gateway (the single front door):**
- Add a `OncePerRequestFilter` in `api-gateway` that runs **after** JWT authentication, only for
  authenticated requests (public/permitAll routes and anonymous calls skip it).
- It resolves the caller's account status via the existing `UserServiceClient`
  (`getUserByUsername` → `UserResponse.active`, already populated by `UserResponseMapper:52`),
  keyed on the JWT `custom:username` (fall back to `sub`).
- **Caffeine cache** (project standard — no Redis), key = `username`/`sub`, **TTL ≈ 60s**
  (configurable via `security.active-gate.ttl-seconds`). This cuts the deactivation-effect lag
  from **≤24h → ≤60s** while keeping CUMS off the hot path (one lookup per user per minute).
- **Deactivated → `403` + error code `ACCOUNT_DEACTIVATED`** (NOT `401`: a 401 risks the
  frontend's token-refresh loop — see project-context auth-retry warning). Frontend maps the
  code to a forced logout + "account deactivated" message.
- **Fail-open on CUMS error** (allow + WARN log + metric) — consistent with the existing
  graceful-degradation pattern (PreTokenGen→empty roles, PreAuth→allow on DB error) and avoids
  locking out every user if CUMS hiccups. The gate only ever *tightens* over today's no-gate state.
- **Kill-switch:** `security.active-gate.enabled` flag (deploy `false`, verify with a test
  deactivation, then flip `true`) so a bug can't brick auth.
- Cache invalidation: TTL-based only (≤60s lag accepted for a rare event). Active eviction via an
  EventBridge `UserDeactivated` event is noted as a *future* option if instant revocation is ever
  required — out of scope here.
- Tests: filter unit tests (active→pass, inactive→403, cache hit path, CUMS-error→fail-open) +
  a gateway integration test.

**Part B — make JIT the canonical reconcile path:**
- Ensure `JITUserProvisioningInterceptor` reads names/language from `custom:preferences` **exactly
  as `post-confirmation.ts` does**, closing the attribute divergence behind the 2026-05-18
  duplicate-without-names incident. After this, *any* authenticated identity — native **or
  federated** — provisions a correct `user_profiles` row (create + email-link + default ATTENDEE)
  on its first request, with no SSO-specific create code.
- Integration tests (Testcontainers): fresh-identity → row + ATTENDEE + names captured;
  existing-anonymous-by-email → linked.

- **Deploy:** gateway + CUMS service deploy (fast-path/hotswap). **Risk:** low — invisible to
  active users; fail-open + kill-switch contain the downside. **Rollback:** flip
  `security.active-gate.enabled=false` (instant) or revert.
- Doc: rewrite the false `06b` "❌ No PreAuthentication Trigger / app checks is_active" line to
  describe the gateway gate as the canonical enforcement, and PreAuthentication as redundant
  (slated for retirement in the cleanup track).

**Why this makes SSO simpler:** with Part A+B in place, federated users provision (JIT) and get
inactive-blocked (gateway gate) through the *same* paths as everyone else. The SSO work drops to
"add a provider + a link branch + a button" — **no parallel provisioning or gating path**, and
**Phase 3 below becomes verify-only.**

### Phase 0 — Google provider prerequisites *(no code, no deploy — joint setup with Nissim)*
**A new Google Cloud project must be created — none exists** (per §8 Q4). Done together:
- Create the Google Cloud project + **OAuth consent screen** (External, app name, support email
  `→ /support`, homepage `www.batbern.ch`, privacy policy `https://batbern.ch/privacy`). ToS
  optional. Scopes: `openid email profile` (non-sensitive → no security assessment).
- **Verify the `batbern.ch` authorized domain** (Search Console / Cloud Console) and **publish
  the consent screen to Production** so public attendees don't hit the testing-mode 100-user cap
  or an "unverified app" warning.
- Create an **OAuth 2.0 Web client**; authorized redirect URI =
  `https://batbern-staging-auth.auth.eu-central-1.amazoncognito.com/oauth2/idpresponse`.
- Store the Google **client ID + secret** in **Secrets Manager** (do not inline in CDK).
- **Risk:** none (nothing wired yet). **Rollback:** delete the Google client/project.

### Phase 1 — Define the Google IdP + add to client *(additive, invisible to users)*
- CDK: add `cognito.UserPoolIdentityProviderGoogle` to `cognito-stack.ts`, reading the
  secret. **Attribute mapping** (the part that feeds our triggers):
  - Google `email` → Cognito `email` (required)
  - Google `name` / `given_name` / `family_name` → fold into `custom:preferences` JSON
    (`post-confirmation.ts` reads firstName/lastName/language from `custom:preferences`) and/or
    standard `given_name`/`family_name`.
- Add `UserPoolClientIdentityProvider.GOOGLE` to the client's `supportedIdentityProviders`
  array (`cognito-stack.ts:259`). **Password auth keeps working** — `COGNITO` stays in the list.
- **No frontend change** → no user sees a Google button. Test by hitting the hosted-UI
  `/oauth2/authorize?...&identity_provider=Google` URL directly.
- **Deploy:** Layer-3 (Cognito) infra deploy. **Risk:** low — purely additive provider.
  **Rollback:** remove `GOOGLE` from `supportedIdentityProviders` (one-line revert).
- ⚠️ A brand-new Google user signing in at this phase would hit the gotchas in §3 with no
  linking trigger yet — so **gate real testing on Phase 2**, or test only with throwaway emails.

### Phase 2 — Account-linking trigger *(the correctness core; highest-care phase)*
- Replace the **inline** `preSignUp` lambda (`cognito-stack.ts:50-79`) with a real
  `NodejsFunction` (own source file under `infrastructure/lib/lambda/triggers/`, VPC +
  DB-secret wiring like the other sync triggers in
  `infrastructure/lib/constructs/cognito-user-sync-triggers.ts`), granting it
  `cognito-idp:AdminLinkProviderForUser` + `cognito-idp:ListUsers` on the pool ARN.
- Behaviour, branched on `event.triggerSource`:
  - **Native** (`PreSignUp_SignUp` / `_AdminCreateUser`): **preserve the existing company-UUID
    validation** verbatim (regression-critical — it's load-bearing today).
  - **Federated** (`PreSignUp_ExternalProvider`): look up an existing user by email
    (Cognito `ListUsers` and/or DB); if found → `AdminLinkProviderForUser` (merge into native
    user, preserve `sub`); set `autoConfirmUser` + `autoVerifyEmail` so the linked/new user is
    immediately usable.
- **Handler unit test is mandatory** per CLAUDE.md (a Lambda that fails module-load 503s all
  auth). Cover: native validation unchanged, federated-link path, federated-new-user path,
  missing-email guard.
- **Deploy:** Layer-3. **Risk:** medium — we're swapping a wired trigger. **Rollback:** the
  inline lambda is preserved in git; revert the construct wiring to restore prior behaviour.
- Doc-drift: update `docs/architecture/06b-user-lifecycle-sync.md` (new "Pattern F: Federated
  sign-in + account linking") in the **same commit**.

### Phase 3 — Federated provisioning + inactive-gating *(VERIFY-ONLY — built in PR 1)*
PR 1 already moved both guarantees onto provider-agnostic paths, so this phase builds nothing —
it **verifies** them against a real federated identity:
- Confirm a brand-new Google user provisions a correct `user_profiles` row (canonical JIT, PR 1B)
  on first API call: row created, default ATTENDEE, names captured from the mapped attributes.
- Confirm a **deactivated** account is blocked from federated access by the **API-Gateway
  `is_active` gate** (PR 1A) — not by PreAuthentication (which never fires for federated).
- If either check fails, the fix lands back in PR 1's components, not here.
- **Deploy:** none (verification). **Risk:** none.

### Phase 4 — Frontend plumbing: callback route + service method *(invisible — no button)*
- `authService.ts`: add `signInWithFederated(provider: 'Google')` → Amplify
  `signInWithRedirect({ provider: 'Google' })`. (Amplify OAuth config already present.)
- `App.tsx`: add the **`/auth/callback`** route → a minimal handler that completes the Amplify
  redirect (`fetchAuthSession`), runs the existing `extractUserContextFromToken` +
  `hydrateRolesIfMissing`, then routes to `/dashboard`. Add `/logout` handler if absent.
- **No button yet** → invisible to users; test by manually navigating the OAuth URL.
- **Deploy:** frontend deploy. **Risk:** low (new route, no entry point). **Rollback:** revert.

### Phase 5 — Surface the button behind a feature flag *(the only user-visible flip)*
> **DELIVERED 2026-06-03 (Story 12.9).** `features.sso` is now plumbed end-to-end:
> `FeatureFlagsDTO.sso` → `ConfigController` (`@Value("${features.sso.enabled:false}")`,
> env `FEATURES_SSO_ENABLED`) → `GET /api/v1/config` → `AppConfig.features.sso` →
> `useFeature('sso')`. `LoginForm.tsx` renders the gated "Continue with Google" button +
> divider above the password form (password panel only), wired to
> `authService.signInWithFederated('Google')`. i18n keys (`login.continueWithGoogle`,
> `login.orDivider`) added to all 10 locales. Flag enabled in prod via
> `FEATURES_SSO_ENABLED=true` on the api-gateway service. Kill-switch = set it false + restart
> (no redeploy). Also delivered alongside: F1b (Cognito web-client `writeAttributes` must
> include `given_name`/`family_name` or the IdP-mapped Google names are silently dropped) and
> F1a (`post-confirmation.ts` reads `given_name`/`family_name` for federated users) — found via
> the Story 12.8 verification, and F3 (frontend Amplify OAuth domain corrected to
> `batbern-staging-auth`).
- Add a `features.sso` flag to `web-frontend/src/config/runtime-config.ts` (served by
  `GET /api/v1/config`) so the button can be **dark-launched and toggled off instantly**.
- `LoginForm.tsx`: add a "Continue with Google" button (above the email/password form, with a
  divider), rendered only when `features.sso` is on, wired to `signInWithFederated`.
- i18n: add the new keys to **all 10 locales** (per CLAUDE.md frontend-i18n rule; EN+DE
  first-class).
- **Deploy:** frontend deploy with flag **off**; flip on after a smoke test with a real Google
  account that links an existing user and a brand-new user. **Risk:** low — flag is the
  kill-switch. **Rollback:** set `features.sso=false` (no redeploy needed if served by config).

### Phase 6 — *(Deferred)* Apple + generic OIDC
- Documented, not scheduled. Must handle: Apple **private-relay** emails (break email-keyed
  linking — fall back to provider-`sub` linking), **name returned only on first authorization**
  (capture-or-lose), paid developer account, **signing key rotates every 6 months** (Secrets
  Manager rotation). Generic OIDC (corporate IdP) is a smaller variant of the same shape.

### Cleanup track — trigger retirement *(enabled by PR 1; not blocking SSO; each PR independent)*
Once PR 1 makes JIT canonical and the gateway gate is live, three Cognito triggers become
redundant. These are *optional* cleanup PRs — they reduce the auth surface to three triggers
(`PreSignUp`, `PreTokenGeneration`, `CustomEmailSender`) but are not prerequisites for SSO. Run
each independently, after PR 1 is verified in prod:
- **Retire `PostAuthentication`** — its email-link is fully covered by canonical JIT. Lowest risk.
- **Retire `PreAuthentication`** — replaced by the gateway `is_active` gate (which also covers
  federated + the post-issuance window). Remove only after the gate is confirmed live.
- **Remove `PostConfirmation`** (§8 Q5 → full removal; JIT is the sole create path). Hard
  prerequisites, in order: (a) PR 1B has closed the `custom:preferences` attribute divergence so
  JIT captures names; (b) the bootstrap organizer (`BootstrapOrganizer`, which today manually
  invokes the PostConfirmation ARN to grant ORGANIZER) is switched to a **direct role-insert** in
  the construct. Only then delete the trigger. Accepts lazy (first-request) provisioning.
- Each: update `06b` inventory + the `cognito-user-sync-triggers.ts` construct; handler/integration
  tests adjusted. **Rollback:** re-add the trigger wiring (handlers preserved in git history).

---

## 6. Testing strategy (per layer, per project's 4-layer framework)
- **Gateway filter + interceptor tests** (PR 1): `is_active` filter (active→pass, inactive→403
  `ACCOUNT_DEACTIVATED`, cache-hit, CUMS-error→fail-open) + canonical-JIT provisioning
  (Testcontainers): fresh identity → row + ATTENDEE + names; anonymous-by-email → linked.
- **Lambda handler tests** (Phase 2): module-load + each `triggerSource` branch.
- **Verification** (Phase 3): real federated identity provisions correctly + deactivated account
  is blocked by the gateway gate (no new code — confirms PR 1).
- **Playwright** (Phase 5): a `google-sso.spec.ts` smoke covering link-existing + new-user
  (a stub/test Google identity, or a documented manual gate if Google test automation is out
  of reach in CI).
- **Manual prod smoke** (Phase 5): one real existing user links Google and retains roles; one
  brand-new Google user lands as ATTENDEE.

## 7. Doc-drift / artifacts to update alongside code
- `docs/architecture/06b-user-lifecycle-sync.md` — add federated sign-in + linking pattern, and
  the explicit "PostConfirmation/PreAuthentication do NOT fire for federated" note.
- Consider a new **ADR-010: Federated Identity via Cognito** (run the `CA` workflow) to record
  the broker model, Google-first decision, and Apple deferral as a first-class architecture
  decision.

## 8. Resolved decisions (2026-05-31 · owner: Nissim)
1. **Provider scope** — ✅ **Google only.** Apple + generic OIDC deferred (Phase 6).
2. **Button placement** — ✅ **All login surfaces.** Any role can link Google; no per-surface gating.
3. **Brand-new Google user** — ✅ **Auto-create as ATTENDEE** (consistent with current
   self-registration); organizers promote via existing role workflows.
4. **Google Cloud project** — ✅ **Nissim owns it; created jointly — none exists today** (the
   watch-app setup was an Apple Developer account, unrelated). Content requirements are met by the
   existing live routes `https://batbern.ch/privacy` + `/support` + the `www.batbern.ch` homepage.
   Scopes are **non-sensitive** (`openid/email/profile`) ⇒ **no Google security assessment**.
   Phase 0 one-time steps: create the project + OAuth client, **verify the `batbern.ch` domain**,
   and **publish the consent screen to Production** (light brand verification, no review).
5. **PostConfirmation fate** — ✅ **Full removal; JIT becomes the sole create path.** Hard
   prerequisites: PR 1B closes the `custom:preferences` attribute divergence *first*, and the
   bootstrap organizer switches to a direct role-insert *before* PostConfirmation is removed.
6. **`custom:role` sentinel** — ✅ **Do the `"UNUSED"` backfill** (one-time
   `AdminUpdateUserAttributes` over existing users + write `"UNUSED"` on new users) in addition to
   dropping `'role'` from the client `readAttributes`.

## 9. Post-GA follow-up stories

- **Story 12.12 — Google avatar import** ✅ *(2026-06-04)*: the Google `picture` claim is now
  mapped (IdP `attributeMapping` + client read/write attributes — the 12.8-F1b lesson applied
  pre-emptively) and CUMS imports the photo **once** per user, server-side, into our own S3
  via the existing `ProfilePictureService` (`profile-pictures/{year}/{username}/`, served from
  `cdn.batbern.ch`). One-attempt-ever semantics via `user_profiles.picture_import_attempted_at`
  (V18); SSRF-guarded to `googleusercontent.com`; async + non-blocking (same contract as JIT).
  See `06b-user-lifecycle-sync.md` Pattern 1c.
- **Story 12.11 — federated onboarding completion** ✅ *(2026-06-04)*: closes the GDPR gap —
  federated JIT provisioning recorded no ToS/Privacy consent (and even native registrations
  never persisted `agreedToTerms`). Delivered: `user_profiles.terms_accepted_at` (CUMS V17,
  write-once via `PUT /users/me`, server clock) with a backfill keyed on the SSO go-live
  cutoff `2026-06-04 16:00 UTC` (NOT a `google_%` LIKE — `cognito_user_id` stores the sub
  UUID for everyone; verified live); PostConfirmation stamps consent for native sign-ups
  (INSERT + link-UPDATE branches) and skips federated ones (`identities`-attribute
  detection); role-neutral `/profile` page (generalized speaker ProfileUpdatePage) with a
  Consent & Newsletter tab; a blocking `ProtectedRoute` gate redirecting consent-less users
  to `/profile?onboarding=1` (fail-open on hydration failure). Newsletter consent was
  deliberately NOT added to `user_profiles` — it stays in EMS `newsletter_subscribers`
  (Story 10.7) via `PATCH /newsletter/my-subscription`; additionally the native
  registration checkbox (previously silently dropped) now subscribes via the public
  `POST /newsletter/subscribe`. See `06b-user-lifecycle-sync.md` Pattern C.
