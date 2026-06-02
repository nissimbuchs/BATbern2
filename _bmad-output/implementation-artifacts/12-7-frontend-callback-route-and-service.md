# Story 12.7: Frontend Callback Route + Service Method + `ACCOUNT_DEACTIVATED` Handling (SSO Phase 4)

Status: ready-for-dev

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

> **Renumbered 2026-06-02:** this story (frontend callback plumbing) was **Story 12.8** and is now **12.7**, swapped with the verify-only story (now 12.8), so the numeric sequence matches execution order — the callback route lands **before** the Phase-3 verification, letting that verification acquire a real federated token via this `/auth/callback` route instead of hand-driving the OAuth code exchange. This story also now **homes gap G1** from the readiness review: the frontend handling of the gateway's `403 ACCOUNT_DEACTIVATED` (Story 12.2 / its OQ-3), folded in as **AC10** because it lives on the same auth/session path this story already touches.

## Story

As a **platform engineer wiring the frontend half of Google SSO federation**,
I want **a `signInWithFederated('Google')` service method that triggers Amplify's hosted-UI redirect, an `/auth/callback` route that completes the returned OAuth session (fetch tokens → extract user context → hydrate from `/users/me` → route to `/dashboard`), a `/logout` route, and a global handler that turns the gateway's `403 ACCOUNT_DEACTIVATED` into a forced logout + clear message**,
so that **a federated round-trip works end-to-end through the same JWT/hydration path as password login — verifiable by hand-navigating the hosted-UI OAuth URL — and a deactivated user (native or federated) is cleanly logged out with an explanation rather than seeing a raw 403; leaving only the visible "Continue with Google" button (Story 12.9) to surface it.**

This is **Phase 4** of Epic 12 (SSO / OIDC Federation). It is **invisible to active users** — **no button is added** (that is Story 12.9). The Amplify `loginWith.oauth` config and the Cognito callback URLs already exist; this story adds only the `signInWithRedirect` call, the React Router routes that consume the redirect, and the `ACCOUNT_DEACTIVATED` response handler. Source: `docs/plans/sso-oidc-federation.md` §5 "Phase 4 — Frontend plumbing: callback route + service method" (+ §5 PR1-A: "Frontend maps the code to a forced logout + 'account deactivated' message"); ADR: `docs/architecture/ADR-010-federated-identity-via-cognito.md` (D1 unchanged-JWT contract, D5 `is_active` gate, D8 button deferred to flag/Phase 5).

## Acceptance Criteria

1. **(Service: `signInWithFederated`.)** `web-frontend/src/services/auth/authService.ts` gains a public method `async signInWithFederated(provider: 'Google'): Promise<void>` that calls Amplify v6 `signInWithRedirect({ provider })`. The `signInWithRedirect` symbol is added to the existing `aws-amplify/auth` modular import block (`authService.ts:10-17`, alongside `signIn`, `signUp`, `fetchAuthSession`, …) — **not** imported elsewhere. The method does not catch-and-swallow: a redirect-initiation failure propagates so the (future) caller can surface it; `signInWithRedirect` itself navigates the browser away on success, so the promise normally does not resolve in-page. The `provider` parameter is typed `'Google'` (the only supported provider per ADR-010 D2 — Apple/OIDC deferred), keeping the call site honest.

2. **(Route: `/auth/callback` handler component.)** `web-frontend/src/App.tsx` gains a `/auth/callback` route rendering a new minimal handler component (e.g. `AuthCallbackPage`, defined in App.tsx near the other inline page components `LoginPage`/`RegistrationPage` `:160-218`, or in a small dedicated file under `src/components/auth/`). On mount the handler **does not call `fetch`/`axios` directly** (project rule); it drives the completion through the service/context layer: it awaits the OAuth session via `authService` (Amplify `fetchAuthSession` already imported at `authService.ts:16`), populates auth state through the **existing** `extractUserContextFromToken` + `hydrateUserFromDb` path, then `navigate('/dashboard', { replace: true })`. **Reuse, do not re-implement:** the token-extract + DB-hydrate + partner-companyName-resolve sequence already lives in `AuthContext` (`hydrateUserFromDb` at `web-frontend/src/contexts/AuthContext.tsx:91`, invoked in `initializeAuth` `:222` and `signIn` `:296`). The handler must funnel through that same logic so a federated session lands an identical `UserContext` to a password session (no duplicate hydration code).

3. **(Hydration naming — depends on Story 12.1.)** The hydration helper is named **`hydrateUserFromDb`** (renamed from `hydrateRolesIfMissing` by Story 12.1, which is `status: review`; see `AuthContext.tsx:91` and the rename note in 12.1 Completion Notes). This story references `hydrateUserFromDb`. **If 12.1 has not yet merged** when this story is implemented, reconcile against whichever name is on the branch (`hydrateRolesIfMissing` vs `hydrateUserFromDb`) — do not introduce a third name. The plan's Phase-4 wording ("`extractUserContextFromToken` + `hydrateRolesIfMissing`") predates the rename; treat `hydrateUserFromDb` as the current truth.

4. **(Callback completion goes through the context, not a parallel path.)** Because `AuthProvider.initializeAuth` (`AuthContext.tsx:209-264`) already runs on app mount and calls `authService.getCurrentUser()` → `hydrateUserFromDb`, the cleanest wiring is: the callback handler waits for the Amplify redirect to settle, then **relies on / triggers the existing context initialization** so `isAuthenticated` flips and the hydrated user (with `preferences.language` for `LanguageSync`) is in state **before** navigating. Acceptable implementations: (a) expose a `useAuth()`-consumable trigger (e.g. a `completeFederatedSignIn()` added to `AuthContext` mirroring the `signIn` success branch `:291-316`: `hydrateUserFromDb` → partner-companyName resolve → `setState({ isAuthenticated: true, … })`) that the handler awaits; or (b) await `fetchAuthSession()` then re-run init. **Whichever path: `hydrateUserFromDb` is awaited before `navigate('/dashboard')`** so the regression guard from Story 12.1 (preferences/language present before any auth-gated effect like `App.tsx:242` `<LanguageSync />`) holds for federated logins too. No `fetch`/`axios` in the component.

5. **(Route: `/logout`.)** A `/logout` route is added to `App.tsx` (currently **absent** — verified: no `/logout` or `/auth/callback` route exists in `App.tsx:244-679`; only `/login`, `/auth/login`, `/auth/forgot-password`, `/auth/reset-password`, `/auth/register`, `/auth/verify-email`). Its handler calls the existing `useAuth().signOut()` (`AuthContext.tsx:404`, which calls `authService.signOut()` → Amplify `signOut`) and then `navigate('/', { replace: true })` (matching the Amplify `redirectSignOut` target `https://www.batbern.ch/` configured at `amplify.ts:39-45`). This route exists so the Cognito hosted-UI logout `redirectSignOut` URL (registered in `cognito-stack.ts:220-231`) lands on a real route rather than the catch-all `*` → `/` (`App.tsx:679`).

6. **(No button / no entry point — invisible.)** **No "Continue with Google" button is added** in this story (deferred to Story 12.9, behind the `features.sso` flag). `LoginForm.tsx` is **not** modified. The feature is reachable only by hand-navigating the hosted-UI authorize URL (`https://batbern-staging.auth.eu-central-1.amazoncognito.com/oauth2/authorize?...&identity_provider=Google&redirect_uri=https://www.batbern.ch/auth/callback&response_type=code`) — which also requires the Google IdP from Story 12.5 + the linking trigger from Story 12.6 to be live to actually return a usable session (see Prereq).

7. **(Config already present — do NOT touch.)** The Amplify `loginWith.oauth` block (`web-frontend/src/config/amplify.ts:54-61`: `domain`, `scopes: ['email','openid','profile']`, `redirectSignIn`/`redirectSignOut`, `responseType: 'code'`) is **already wired and dormant** and is **not modified** by this story — it is the config `signInWithRedirect` consumes. The `redirectSignIn` already points at `/auth/callback` (`amplify.ts:34,39,44`) and `redirectSignOut` at `/` (`:35,40,45`), matching the routes added here and the Cognito-registered URLs (`cognito-stack.ts:220-231`). Confirm (do not change) that these targets align.

8. **(i18n — only if the callback page renders text.)** If the `AuthCallbackPage` shows any user-visible copy (e.g. a "Signing you in…" spinner caption, or an error message if the session fails to settle), those strings go through `useTranslation()` (project i18n rule; pattern: `LoginForm.tsx:71` `useTranslation(['auth', …])`) with new keys added to the **`auth` namespace in ALL 10 locales** (`de, en, es, fi, fr, gsw-BE, it, ja, nl, rm` — verified present under `web-frontend/public/locales/*/auth.json`); EN + DE first-class per CLAUDE.md. **Prefer reusing the existing `BATbernLoader` spinner with no caption** (as `App.tsx`'s `PageLoader` `:141-152` and `ProtectedRoute`'s loading state do) to keep the page text-free and skip the i18n fan-out — in which case this AC is satisfied with "no new strings". If any text is shown, the 10-locale keys are mandatory before review.

9. **(Provider-agnostic contract — backend untouched; existing tests stay green.)** No backend, gateway, Cognito-stack, or token-issuance change. The federated session yields the **same JWT shape** password login produces (ADR-010 D1), so `extractUserContextFromToken` and `hydrateUserFromDb` need no SSO-specific branching. All existing `authService.test.ts` + `AuthContext.test.tsx` cases (password sign-in, FORCE_CHANGE_PASSWORD, role hydration, partner companyName) remain green; `npm run type-check` + `npm run lint` clean.

10. **(`ACCOUNT_DEACTIVATED` handling — G1, folded from Story 12.2/OQ-3.)** When **any** API call returns **HTTP `403` with error code `ACCOUNT_DEACTIVATED`** (emitted by the Story 12.2 gateway `is_active` filter), the frontend **forces a logout** and shows a clear "your account has been deactivated" message — for **both** password and federated sessions (the gate is provider-agnostic). Specifics:
    - Detect the code at the **central HTTP layer** (the shared axios/fetch client/interceptor the service layer already routes through — locate the existing response-error interceptor that handles 401; place this **beside** it, NOT inside individual services). **Critical (project-context auth-retry rule):** `ACCOUNT_DEACTIVATED` is a **403** and must be handled **distinctly from 401** — do **NOT** route it through the token-refresh path (a refresh loop would result). On match: call `authService.signOut()` (→ Amplify `signOut`, clears the session) and redirect to the login surface with a deactivated-state indicator (e.g. `navigate('/login?reason=account_deactivated')` or a one-shot flag the login page reads), so the user lands on a page that renders the message rather than a blank/raw error.
    - The login/landing surface renders a dismissible "account deactivated" notice when that indicator is present. Copy goes through `useTranslation()` with new keys in the **`auth` namespace across ALL 10 locales** (`de, en, es, fi, fr, gsw-BE, it, ja, nl, rm`), EN+DE first-class (this AC **does** add user-visible strings → the 10-locale fan-out is mandatory, unlike AC8).
    - **Tests:** a unit test on the interceptor/handler — a mocked `403 { errorCode: 'ACCOUNT_DEACTIVATED' }` response triggers `signOut` + the redirect, and a generic `403` (e.g. authorization failure) does **NOT** (no false logout); a `401` still follows the existing refresh path unchanged (regression guard). Assert against EN or the namespace-stripped key.

## Tasks / Subtasks

- [ ] **Task 1 — Service: `signInWithFederated('Google')` (AC: 1, 9)**
  - [ ] RED: in `web-frontend/src/services/auth/authService.test.ts`, add a test `should call Amplify signInWithRedirect with { provider: 'Google' } when signInWithFederated is invoked` — mock `signInWithRedirect` from `aws-amplify/auth` (extend the existing module mock used for `signIn`/`signUp`) and assert it is called once with `{ provider: 'Google' }`.
  - [ ] GREEN: add `signInWithRedirect` to the `aws-amplify/auth` import block (`authService.ts:10-17`); add `async signInWithFederated(provider: 'Google'): Promise<void> { await signInWithRedirect({ provider }); }` as a public method on `AuthService`. No try/catch swallow (let initiation errors propagate).
  - [ ] Confirm the existing `authService.test.ts` mock surface still resolves (don't break `signIn`/`signUp`/`fetchAuthSession` mocks).

- [ ] **Task 2 — Context: federated-completion entry point (AC: 2, 3, 4, 9)**
  - [ ] Decide the wiring: prefer adding `completeFederatedSignIn(): Promise<SignInOutcome>` to `AuthContext` (mirror the `signIn` success branch `AuthContext.tsx:291-316`: `fetchAuthSession`/`getCurrentUser` → `hydrateUserFromDb` → partner-companyName resolve → `setState({ isAuthenticated: true, … })`). Reuse `hydrateUserFromDb` (`:91`) — do NOT duplicate the extract/hydrate logic.
  - [ ] RED: in `AuthContext.test.tsx`, assert that after the federated-completion path the hydrated `UserContext` carries `preferences.language` (regression guard from 12.1) and `isAuthenticated === true` BEFORE the caller would navigate — mock `authService.getCurrentUser`/`fetchAuthSession` + `getUserProfile` (the `/users/me` mock pattern 12.1 added).
  - [ ] GREEN: implement the method; add it to `UseAuthReturn` (`AuthContext.tsx:32-49`) and the memoized `contextValue` (`:637-664`). **If Story 12.1's `hydrateUserFromDb` rename is not on the branch, reconcile to the name present (`hydrateRolesIfMissing`) — see AC3.**

- [ ] **Task 3 — Route: `/auth/callback` handler component (AC: 2, 4, 8)**
  - [ ] Add an `AuthCallbackPage` component (inline in `App.tsx` near `LoginPage` `:160-191`, or `src/components/auth/AuthCallbackPage/`). On mount: `useEffect` → await `completeFederatedSignIn()` (Task 2) → `navigate('/dashboard', { replace: true })`; on failure → `navigate('/login', { replace: true })` (optionally with an error toast/state). Render `BATbernLoader` (text-free) while completing — see AC8 i18n note.
  - [ ] Register the route in `App.tsx` (within `<Routes>` `:244`, e.g. near the auth routes `:336-384`): `<Route path="/auth/callback" element={<AuthCallbackPage />} />`. **No `<AuthPageLayout>` wrapper needed** (it's a transient redirect target); keep it inside `<Suspense>` consistently with siblings.
  - [ ] RED/GREEN: component test (`AuthCallbackPage.test.tsx`) — mock the context's `completeFederatedSignIn` (resolve success) and assert `navigate` is called with `/dashboard`; a second case where it rejects asserts navigation to `/login`. Use RTL `render` within a `MemoryRouter`, `screen` queries, `waitFor`. Mock the service/context layer (no `msw` HTTP needed since the component touches no HTTP directly).

- [ ] **Task 4 — Route: `/logout` handler (AC: 5)**
  - [ ] Add a `LogoutPage` component (inline near `LoginPage`) whose `useEffect` calls `useAuth().signOut()` then `navigate('/', { replace: true })`.
  - [ ] Register `<Route path="/logout" element={<LogoutPage />} />` in `App.tsx`.
  - [ ] RED/GREEN: test asserts `signOut` is invoked and navigation lands on `/`.

- [ ] **Task 5 — Verify config alignment (AC: 7) — read-only**
  - [ ] Confirm (do NOT edit) `amplify.ts:54-61` oauth block + `redirectSignIn`→`/auth/callback` (`:34,39,44`) and `redirectSignOut`→`/` (`:35,40,45`). Cross-check the Cognito-registered callback/logout URLs in `infrastructure/lib/stacks/cognito-stack.ts:220-231` match the new routes. Record the confirmation in the PR (no code change here).

- [ ] **Task 6 — i18n (AC: 8) — only if the callback page shows text**
  - [ ] If `AuthCallbackPage` renders any copy: add keys to the `auth` namespace in ALL 10 locales (`web-frontend/public/locales/{de,en,es,fi,fr,gsw-BE,it,ja,nl,rm}/auth.json`), EN+DE first-class, others straight translations. If the page is text-free (`BATbernLoader` only), explicitly note "no new i18n keys" in the PR and skip.

- [ ] **Task 7 — Full verification (AC: 9)**
  - [ ] Targeted vitest: `authService.test.ts`, `AuthContext.test.tsx`, `AuthCallbackPage.test.tsx`, `LogoutPage` test — dump to a temp file, grep, all green (don't re-run repeatedly).
  - [ ] `npm run type-check` + `npm run lint` clean. Confirm the full frontend vitest suite still passes (no regression in password-login / FORCE_CHANGE_PASSWORD / partner-companyName paths).
  - [ ] Manual (or documented) verification note: hand-navigate the hosted-UI authorize URL with `identity_provider=Google` lands back on `/auth/callback` → `/dashboard` (only fully exercisable once 12.5 + 12.6 are deployed — see Prereq). Record as a deploy-time smoke step.

- [ ] **Task 8 — `ACCOUNT_DEACTIVATED` forced-logout handler (AC: 10) — G1, folded from Story 12.2/OQ-3**
  - [ ] Locate the shared HTTP response-error interceptor that already handles `401`/token-refresh (the client the service layer routes through, e.g. under `web-frontend/src/services/` or `src/config/`). RED: add a unit test asserting a mocked `403 { errorCode: 'ACCOUNT_DEACTIVATED' }` triggers `authService.signOut()` + redirect; a generic `403` does NOT; a `401` still hits the existing refresh path (regression guard).
  - [ ] GREEN: add an `ACCOUNT_DEACTIVATED` branch **beside** (not inside) the 401 handler — on match call `authService.signOut()` then route to the login surface with a deactivated indicator (e.g. `?reason=account_deactivated`). **Do NOT** funnel it through token-refresh (403 ≠ 401 — avoids the refresh loop per project-context).
  - [ ] Render a dismissible "account deactivated" notice on the login surface when the indicator is present; add `auth`-namespace keys in ALL 10 locales (EN+DE first-class).
  - [ ] Targeted vitest + type-check + lint green.

## Dev Notes

### Architecture context — why this is low-risk and provider-agnostic
- **ADR-010 D1: the backend is unchanged.** Cognito brokers Google→app, so the React app keeps receiving the **same JWT shape** it parses today. `extractUserContextFromToken` (`authService.ts:426`) and `hydrateUserFromDb` (`AuthContext.tsx:91`) require **no SSO-specific branching** — a federated session is indistinguishable from a password session at the token layer. This story is pure frontend plumbing: one service method + two routes.
- **~70% already wired.** Per the plan §2 table, the Amplify `loginWith.oauth` block (`amplify.ts:54-61`), the Cognito hosted-UI domain, app-client auth-code grant + `openid/email/profile` scopes, and the `/auth/callback` + `/logout` callback URLs (`cognito-stack.ts:220-231`) all already exist and are dormant. Only `signInWithRedirect`, the callback route, and (later) the button are missing. This story adds the first two.
- **Reuse the hydration path — don't fork it.** `AuthContext.signIn` (`:269-341`) already does: `authService.signIn` → `hydrateUserFromDb` → partner-companyName resolve → `setState`. The federated completion is the **same success branch minus the password step**. Funnel through `hydrateUserFromDb` so federated and password logins converge on one `UserContext` shape (and the 12.1 preferences/language regression guard is preserved for federated too).

### Story 12.1 dependency — the rename
Story 12.1 (`status: review`) renamed `hydrateRolesIfMissing` → **`hydrateUserFromDb`** and extended it to fetch `getUserProfile(['roles','company','preferences'])`, merging `companyId` + `preferences` from `/users/me` (not the token). This story references `hydrateUserFromDb` (current truth at `AuthContext.tsx:91`). The plan's Phase-4 prose still says "`hydrateRolesIfMissing`" (pre-rename). **If 12.1 lands first (expected), use `hydrateUserFromDb`. If not, reconcile to the name on the branch — never add a third name.** The regression-critical property to preserve either way: hydration is `await`-ed **before** `setState({ isAuthenticated: true })`, so `preferences.language` is on the user before `LanguageSync` / any auth-gated effect fires.

### aws-amplify v6 modular imports (project is on 6.16.2)
- `signInWithRedirect` and `fetchAuthSession` both come from `'aws-amplify/auth'` (verified exported: `@aws-amplify/auth/dist/esm/index.mjs` re-exports `signInWithRedirect` from `providers/cognito/apis/signInWithRedirect`). `fetchAuthSession` is **already imported** at `authService.ts:16`; add `signInWithRedirect` to the same block.
- `signInWithRedirect({ provider: 'Google' })` navigates the browser to the Cognito hosted UI; on return, Amplify processes the `?code=` on the `/auth/callback` URL and the next `fetchAuthSession()`/`getCurrentUser()` resolves the tokens. The handler component's job is to wait for that and route on.

### Files to touch — current state & what to preserve
| File | Current state | Change | Preserve |
|---|---|---|---|
| `web-frontend/src/services/auth/authService.ts` | imports `signIn/signUp/signOut/getCurrentUser/confirmSignIn/fetchAuthSession` from `aws-amplify/auth` (`:10-17`); password-only | add `signInWithRedirect` import + `signInWithFederated('Google')` method | all existing methods; `extractUserContextFromToken` (`:426`) untouched |
| `web-frontend/src/contexts/AuthContext.tsx` | `hydrateUserFromDb` (`:91`); `signIn` success branch (`:291-316`); `signOut` (`:404`); `UseAuthReturn` (`:32-49`); `contextValue` (`:637-664`) | add `completeFederatedSignIn()` mirroring the `signIn` success branch; expose it in the interface + memo | `hydrateUserFromDb`; all existing methods; regression-guard ordering (hydrate before `setState`) |
| `web-frontend/src/App.tsx` | inline page components `:160-218`; `<Routes>` `:244-680`; auth routes `:336-384`; catch-all `:679`; `<LanguageSync />` `:242` | add `AuthCallbackPage` + `LogoutPage` components + `/auth/callback` + `/logout` routes | all existing routes; `<Suspense>`/`<LanguageSync>` structure; catch-all last |
| `web-frontend/src/config/amplify.ts` | oauth block `:54-61`; redirect targets `:34-45` | **none** (read-only confirm) | everything — this is the config `signInWithRedirect` consumes |
| `web-frontend/src/components/auth/LoginForm/LoginForm.tsx` | password form | **none** (button is Story 12.9) | everything |

### Testing standards (per CLAUDE.md 4-layer + TDD red-green-refactor)
- Frontend: **vitest 4.x + RTL 16.x**. Test names `should … when …`. Mock the **service/context layer**, not raw HTTP — the callback component touches no `fetch`/`axios` directly (project rule), so `msw` is unnecessary here; mock `useAuth()`'s `completeFederatedSignIn`/`signOut` for the component tests, and mock `aws-amplify/auth` for the `authService` unit test (extend the existing module mock).
- Use `screen` queries, `userEvent` (n/a here — no user interaction on the callback page), `waitFor` for the navigate assertions (render in `MemoryRouter`, assert via a mocked `useNavigate`).
- Assert against EN values OR namespace-stripped i18n keys, never a non-EN translation (per project-context). If the page is text-free, no i18n assertions needed.
- Run via tee-to-temp-file then grep (CLAUDE.md); don't re-run suites repeatedly.

### Project Structure Notes
- This is **frontend-only** — deploy is a **frontend deploy** (plan §5 Phase 4). **Risk: low** (new route, no entry point reachable without the deferred button). **Rollback: revert.**
- `@/` / `@components` / `@hooks` / `@pages` path aliases are in use (see `App.tsx` imports `:6-28`). Use them.
- Inline page components (`LoginPage`, `RegistrationPage`, …) live at the top of `App.tsx` (`:160-218`); `AuthCallbackPage`/`LogoutPage` can follow that convention, or live under `src/components/auth/` if a dedicated test file is cleaner.

### Doc-drift / scope
- ADR-010 already documents the federated model (D1 unchanged-JWT, D8 button-behind-flag). This story implements a slice already described there — **no ADR change needed**. No business-logic/state-machine/API-contract change in the backend, so `06b-user-lifecycle-sync.md` is untouched. If a `feat` commit message wants a marker and no doc is needed, `[no-doc]` is appropriate for any pure-plumbing sub-commit (the routes/method are described by ADR-010 already).

### References
- [Source: docs/plans/sso-oidc-federation.md#Phase 4 — Frontend plumbing: callback route + service method] (lines 229-236: `signInWithFederated`→`signInWithRedirect`; `/auth/callback` handler = `fetchAuthSession` + `extractUserContextFromToken` + `hydrateRolesIfMissing`→`/dashboard`; `/logout`; no button; FE deploy; risk low)
- [Source: docs/plans/sso-oidc-federation.md#2 Why this is low-risk (~70% already wired)] (amplify.ts:54-61 oauth block; authService.ts:424 claim extraction; cognito-stack.ts:220-231 callback/logout URLs)
- [Source: docs/architecture/ADR-010-federated-identity-via-cognito.md] (D1 backend provider-agnostic / same JWT; D2 Google only; D8 button behind `features.sso` flag — deferred to Phase 5/Story 12.9)
- [Source: web-frontend/src/config/amplify.ts:34-45,54-61] (oauth block + redirectSignIn `/auth/callback` + redirectSignOut `/` — already present, dormant)
- [Source: web-frontend/src/services/auth/authService.ts:10-17 (aws-amplify/auth imports incl. fetchAuthSession), :426 (extractUserContextFromToken)]
- [Source: web-frontend/src/contexts/AuthContext.tsx:32-49 (UseAuthReturn), :91 (hydrateUserFromDb — renamed from hydrateRolesIfMissing by Story 12.1), :222/:296 (hydrate call sites), :291-316 (signIn success branch to mirror), :404 (signOut), :637-664 (contextValue)]
- [Source: web-frontend/src/App.tsx:160-218 (inline page components), :242 (<LanguageSync/>), :244-680 (Routes), :336-384 (auth routes), :679 (catch-all) — no /auth/callback or /logout route exists today]
- [Source: web-frontend/src/components/auth/ProtectedRoute/ProtectedRoute.tsx (loading-state BATbernLoader pattern) · web-frontend/src/components/auth/LoginForm/LoginForm.tsx:71 (useTranslation(['auth',…]) pattern)]
- [Source: _bmad-output/implementation-artifacts/12-1-cognito-attribute-hygiene.md (hydrateRolesIfMissing → hydrateUserFromDb rename; /users/me hydration of company+preferences; regression guard)]
- [Source: web-frontend/public/locales/{de,en,es,fi,fr,gsw-BE,it,ja,nl,rm}/auth.json (10 locales, auth namespace)]
- aws-amplify 6.16.2 — v6 modular imports (`signInWithRedirect`, `fetchAuthSession` from `aws-amplify/auth`)

### Prerequisite & ordering
- **Prereq: Story 12.6** (account-linking `PreSignUp_ExternalProvider` trigger) — and transitively 12.5 (Google IdP) — must be deployed for a federated round-trip to actually return a usable session (otherwise the hosted-UI authorize URL has no Google provider / no linking). This story's **code** has no hard build dependency on the backend (it only adds a method + routes), so it can be **implemented and merged independently**; but its **manual verification** (Task 7 hosted-UI smoke) is only meaningful once 12.5 + 12.6 are live. Story 12.9 (the visible button) depends on this story.
- Story 12.1 (`hydrateUserFromDb` rename) should land first; if not, see AC3 reconciliation note.

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
| 2026-06-01 | Story drafted (SSO Phase 4 — frontend callback route + service method, invisible/no button) as Story 12.8. Status → ready-for-dev. |
| 2026-06-02 | **Renumbered 12.8 → 12.7** (swapped with the verify-only story, now 12.8) so callback plumbing precedes Phase-3 verification. **Folded in G1** (frontend `ACCOUNT_DEACTIVATED` forced-logout handler from Story 12.2/OQ-3) as AC10 + Task 8 (+10-locale i18n). Cross-references updated across Epic 12. |
