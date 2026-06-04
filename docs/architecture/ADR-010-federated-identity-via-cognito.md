# ADR-010: Federated Identity via Cognito (Google SSO over OIDC)

**Status**: Accepted & Implemented — Google SSO live in production since 2026-06-04
(Stories 12.1–12.9, 12.11, 12.12; runtime kill-switch `FEATURES_SSO_ENABLED`). Deferred
tail: Apple/generic OIDC + Cognito trigger-retirement cleanup (D7 end-state) tracked as
backlog Story 12.10 — PostConfirmation/PreAuthentication are still deployed until then.
**Date**: 2026-05-31 (decided) · 2026-06-04 (delivered)
**Decision Makers**: Nissim Buchs (owner), Architecture
**Related ADRs**: ADR-001 (Invitation-Based Registration / Cognito-for-auth-only), ADR-003 (Meaningful Identifiers in Public APIs), ADR-004 (Factor User Fields from Domain Entities), ADR-007 (Unified User Profile), ADR-009 (Unified Speaker Workflow — Cognito `FORCE_CHANGE_PASSWORD`)
**Related Plan**: `docs/plans/sso-oidc-federation.md` (phased, prod-safe delivery)
**Affects documentation**: `docs/architecture/06b-user-lifecycle-sync.md` (§"Cognito Custom-Attribute Inventory & Deprecation Status", and the now-corrected PreAuthentication claim)

## Context

BATbern authenticates with AWS Cognito but keeps the **database as the single source of
truth** for users and roles (ADR-001). Login today is email/password only. To lower friction
for the **public attendee self-registration funnel** (FR1/FR6/FR14), we want **"Continue with
Google"** via OpenID Connect federation.

Two findings shaped this ADR:

1. **~70% of the OAuth/hosted-UI plumbing is already wired but dormant**: a Cognito hosted-UI
   domain (`cognito-stack.ts:271`), the app client's authorization-code grant + `openid/email/
   profile` scopes (`:247`), `/auth/callback` callback URLs (`:220`), and the frontend Amplify
   `loginWith.oauth` block (`web-frontend/src/config/amplify.ts:54`). Only the identity provider,
   the `signInWithRedirect` call, the callback route, and the button are missing.
2. **Federated logins invoke a *smaller* Cognito trigger set.** For external-IdP sign-ins Cognito
   fires only **PreSignUp**, **PreTokenGeneration**, and **PostAuthentication** — it does **NOT**
   fire **PostConfirmation** or **PreAuthentication**. Those two carry "create the DB user" and
   "block inactive users" respectively, so federation forces those guarantees onto
   provider-agnostic paths.

**Hard constraint:** there is exactly **one real user pool** — local dev points at the
staging-account pool, and the staging account *is* production. Every pool change is a production
change, so delivery is phased and the only user-visible step is behind a feature flag.

## Decision

### D1 — Cognito as OIDC broker; the backend stays provider-agnostic
Cognito acts as an **OIDC relying party** toward Google and an **OIDC/OAuth provider** toward the
app. The React app talks only to Cognito and receives the **same JWT shape** it already parses.
Consequently the backend is unchanged: `shared-kernel/.../security/JwtRolesConverter`, all
`@PreAuthorize` checks, the Pattern 3b DB-fallback, and the DB-projected `custom:role` /
`custom:username` claims require **no modification**.

### D2 — Google first; Apple and generic OIDC deferred
Ship Google only. Apple ("Sign in with Apple") and generic corporate OIDC are deferred — Apple's
private-relay emails (break email-keyed linking), name-returned-only-once, paid developer account,
and 6-month signing-key rotation make it a separate, heavier decision.

### D3 — Existing accounts link transparently (no migration)
A native user signing in with Google for the first time is **merged into their existing Cognito
user** via `AdminLinkProviderForUser`, called in the **`PreSignUp_ExternalProvider`** trigger and
matched by email. The Cognito `sub` is preserved, so `user_profiles.cognito_user_id`, roles,
company, and history all remain intact. The user can subsequently sign in with **either** password
**or** Google. Without this, Cognito's email sign-in alias would raise `AliasExistsException` or
orphan the user's roles under a new `sub`.

### D4 — Brand-new Google users auto-provision as ATTENDEE
A Google identity with no matching account is created with the default **ATTENDEE** role
(consistent with existing self-registration). Organizers promote via the existing role workflows.

### D5 — Provisioning and inactive-gating move to provider-agnostic paths
Because federation skips PostConfirmation and PreAuthentication (see Context #2):
- **JIT becomes the sole canonical create path.** `JITUserProvisioningInterceptor` (CUMS,
  request-time) creates the `user_profiles` row + links-by-email + defaults to ATTENDEE for *any*
  authenticated identity — native or federated. **PostConfirmation is removed** (full removal),
  after the interceptor is made to capture names/language from `custom:preferences` exactly as
  PostConfirmation did (closing the 2026-05-18 divergence incident) and the bootstrap organizer
  is switched to a direct role-insert.
- **A new API-Gateway `is_active` gate replaces PreAuthentication.** A request-time filter at the
  single front door checks `is_active` (Caffeine-cached, ~60s TTL; `403 ACCOUNT_DEACTIVATED`;
  fail-open on CUMS error; kill-switch flag). This **fixes two pre-existing gaps**: the federated
  bypass (PreAuthentication never fires for federation) and the ≤24h post-issuance window (today
  deactivation only bites at next login, while tokens live 24h). PreAuthentication is then retired.

> **Consent addendum (Story 12.11, 2026-06-04):** the JIT/federated provisioning footprint
> records **no ToS/Privacy consent** — `user_profiles.terms_accepted_at` (V17) stays NULL for
> federated identities, and a frontend `ProtectedRoute` gate blocks consent-less users on
> `/profile?onboarding=1` until they explicitly accept (write-once via `PUT /users/me`,
> server clock). Native sign-ups get consent stamped by PostConfirmation while it still
> exists; once PostConfirmation is retired (D7), the gate is the universal consent collector
> for ALL new identities. See `06b-user-lifecycle-sync.md` Pattern C.

### D6 — The token carries identity + authorization only
`companyId` is **removed from the JWT** — it is pure business data (a user→company FK, ADR-003/004),
and the only company-scoped authorization (`PartnerSecurityService`) already resolves it via the
user-api keyed on `username`. The dead `custom:role` *stored attribute* gets a `"UNUSED"` sentinel
(Cognito attributes are permanent and cannot be deleted). Target token claims: `sub`, `email`,
`custom:role`, `custom:username` — all identity or authorization, nothing else. (Note: `custom:role`
is a claim because Spring Security builds authorities from it; `companyId` participates in no
authorization-from-token decision, so it is **not** projected as a claim.)

### D7 — Target Cognito trigger set
After cleanup, three triggers remain: **PreSignUp** (validation + external-provider linking),
**PreTokenGeneration** (DB→JWT claim projection), **CustomEmailSender** (branded de/en emails for
native sign-up/forgot-password). **PostConfirmation, PostAuthentication, and PreAuthentication are
retired**, their guarantees absorbed by JIT + the API-Gateway gate + reconciliation.

### D8 — "Continue with Google" on all login surfaces
Rendered on every login entry point (behind a `features.sso` flag for dark-launch/kill-switch);
account-linking benefits all roles, not just the attendee funnel.

> **Implemented (Story 12.9, 2026-06-03):** `features.sso` now exists in the runtime-config
> contract — `FeatureFlagsDTO.sso` (gateway) → `GET /api/v1/config` → `AppConfig.features.sso`
> → `useFeature('sso')` — gating the "Continue with Google" button in `LoginForm.tsx`. The
> gateway property `features.sso.enabled` (env `FEATURES_SSO_ENABLED`) is the instant
> kill-switch (flip false + restart; the FE re-reads at runtime, no redeploy).
> **Federated-naming correctness (found via Story 12.8 verification):** the Cognito web-client
> `writeAttributes` MUST include `given_name`/`family_name` — Cognito only persists IdP-mapped
> attributes the federating client can write, else the mapped Google names are silently dropped
> and the user provisions as "User User". `post-confirmation.ts` reads `given_name`/`family_name`
> (federated names are NOT in `custom:preferences`). Note: contrary to the original premise that
> PostConfirmation does not fire for federated, it DOES fire (the PreSignUp `autoConfirmUser`
> triggers it) and is the create path for federated users in this configuration.

### D9 — A new Google Cloud project, non-sensitive scopes
No Google Cloud project exists (the watch app used Apple Developer). A new project is created with
scopes `openid email profile` (**non-sensitive → no Google security assessment**), the existing
`https://batbern.ch/privacy` + `/support` + `www.batbern.ch` satisfying consent-screen content,
plus `batbern.ch` domain verification and consent-screen publication to Production.

## Consequences

**Positive**
- Lower-friction sign-in for the public funnel; no password for Google users.
- Backend untouched (provider-agnostic JWT contract).
- The API-Gateway `is_active` gate is a **net security improvement** independent of SSO: deactivation
  becomes effective platform-wide within ~60s (was ≤24h) and applies to every auth method.
- Auth surface shrinks from 6 triggers (+ inline preSignUp) to 3, with one shared reconcile path (JIT).

**Negative / risk**
- Provisioning becomes **lazy** (DB row on first authenticated request, not at confirm). Mitigated:
  the frontend's first call (`/users/me` hydration) fires JIT within milliseconds; the divergence
  that caused the 2026-05-18 duplicate-without-names bug must be closed *before* PostConfirmation is
  removed.
- The `PreSignUp_ExternalProvider` linking trigger is correctness-critical and runs in the auth path
  (handler test mandatory; a module-load failure 503s all auth).
- Apple's deferral leaves a known gap if Apple is demanded later (private-relay handling).
- Operating a new external IdP adds Google client-secret management.

**Neutral**
- Cognito custom attributes are permanent; cleanup means "stop using"/sentinel, not delete.

## Implementation

Delivery is phased and prod-safe; see `docs/plans/sso-oidc-federation.md`. Order: **PR 0** (attribute
hygiene) → **PR 1** (API-Gateway `is_active` gate + canonical JIT) → **SSO Phases 0–5** (Phase 3 is
verify-only, since PR 1 already builds provisioning + gating) → **cleanup track** (retire
PostAuthentication, PreAuthentication, PostConfirmation). Each phase is independently deployable to
the single pool; the only user-visible flip (the button) is behind `features.sso`.

## Alternatives considered

- **Per-app integration of Google (no broker)** — rejected; loses the unchanged-JWT property and
  couples every client to provider specifics. The broker model keeps the app Cognito-only.
- **All-Java (or all-TypeScript) trigger rewrite for single-language reuse** — rejected; "only TS"
  is impossible (the JIT interceptor must run in the Spring request pipeline), and "only Java" trades
  the cross-language duplication for Java-Lambda cold-start risk on the auth hot path. Instead, JIT
  (Java) becomes the single home for reconcile logic and triggers shrink to three. (See plan §"triggers".)
- **Keep `companyId` in the token** — rejected; it is business data with no authorization-from-token
  use, already resolved server-side via the user-api (ADR-003/004).
- **Apple + Google now** — deferred; Apple's private-relay/name-once/key-rotation cost is disproportionate
  to the marginal funnel gain over Google alone.
- **Keep all six triggers** — rejected; PostConfirmation/PreAuthentication can't serve federation, and
  PostAuthentication's email-link is fully covered by JIT.
