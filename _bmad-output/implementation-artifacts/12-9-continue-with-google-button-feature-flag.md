# Story 12.9: "Continue with Google" Button Behind a Feature Flag (SSO Phase 5)

Status: ready-for-dev

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **public attendee (or any role) signing in to BATbern**,
I want **a "Continue with Google" button on the login form that, when enabled, federates me through Cognito's Google identity provider**,
so that **I can sign in without a password — linking transparently to my existing account if I have one, or auto-provisioning as ATTENDEE if I'm new — while the platform team keeps an instant kill-switch via a `features.sso` flag.**

This is **Phase 5** of Epic 12 (SSO / OIDC Federation) and the **ONLY user-visible flip** in the whole epic. Everything behind it — the Google IdP, account-linking trigger, provisioning/gating, the `signInWithFederated` service method, and the `/auth/callback` route — was delivered by the prior phases. This story (a) adds a `features.sso` flag to the runtime-config contract so the button can be dark-launched and toggled off instantly without a redeploy, and (b) renders the gated button in `LoginForm.tsx`, wired to `authService.signInWithFederated('Google')`. Source: `docs/plans/sso-oidc-federation.md` §5 "Phase 5"; `docs/architecture/ADR-010-federated-identity-via-cognito.md` §D8 ("Continue with Google" on all login surfaces, behind `features.sso`).

**Prerequisites (hard):** Story 12.7 (frontend callback route + `authService.signInWithFederated`) and all prior SSO phases (Phases 0–4 + PR 0/PR 1) must be merged and verified in prod — the button is the last wire that connects a fully-built path. Per ADR-010 §Implementation: "the only user-visible flip (the button) is behind `features.sso`."

## Acceptance Criteria

1. **(`features.sso` flag — backend half of the runtime-config contract.)** A new boolean `sso` field is added to the gateway feature-flags DTO `api-gateway/src/main/java/ch/batbern/gateway/config/dto/FeatureFlagsDTO.java` (alongside `notifications`/`analytics`/`pwa`/`turnstile`, currently lines 20-36), and `ConfigController.getConfig()` (`api-gateway/src/main/java/ch/batbern/gateway/config/ConfigController.java:74-79`) populates it from a configurable property `features.sso.enabled` (default **`false`**, read via `@Value("${features.sso.enabled:false}")`) so the flag ships **off** and can be flipped **without a frontend rebuild** (and, per the plan, without a frontend redeploy — toggling the gateway property + restart is enough since the FE reads it at runtime from `GET /api/v1/config`). The `GET /api/v1/config` JSON response now carries `features.sso`.

2. **(`features.sso` flag — frontend half of the runtime-config contract.)** The `AppConfig['features']` interface in `web-frontend/src/config/runtime-config.ts:23-28` gains `sso: boolean`. This makes `useFeature('sso')` (`web-frontend/src/contexts/useFeature.ts:21`) type-check and resolve the flag, exactly as `useFeature('turnstile')` already does for the Turnstile feature (`web-frontend/src/hooks/useTurnstile/useTurnstile.ts:67`). The dev-fallback `getDefaultDevelopmentConfig()` (`runtime-config.ts:163-179`) sets `sso: false`. **No `process.env`** access — the flag flows through the runtime-config object per CLAUDE.md.

3. **("Continue with Google" button rendered ONLY when `features.sso` is on.)** `web-frontend/src/components/auth/LoginForm/LoginForm.tsx` renders a "Continue with Google" `Button` **above the email/password form** (i.e. before the `<Box component="form" onSubmit={handleSubmit(onSubmit)}>` at line 276), followed by a labelled divider ("or" / "oder"), gated by `const ssoEnabled = useFeature('sso')`. The button is shown **only on the password-login panel** (the `pendingNewPassword === false` branch) — it must NOT appear on the "set new password" panel (lines 224-274). When `features.sso` is `false`, neither the button nor the divider renders. The button uses MUI 7.x components: `Button` (`variant="outlined"`, `fullWidth`, `startIcon={<Google />}` from `@mui/icons-material`, already present in `node_modules/@mui/icons-material/Google.js`) and a `Divider` for the "or" separator.

4. **(Wired to the service layer, not Amplify directly.)** The button's `onClick` calls `authService.signInWithFederated('Google')` — the method delivered by Story 12.7 (it wraps Amplify `signInWithRedirect({ provider: 'Google' })`). The component **must not** import or call Amplify directly (CLAUDE.md service-layer rule). On click the browser is redirected to the Cognito hosted UI; no local form validation runs for this path. The button is `disabled` while `isLoading` is true (consistent with the existing sign-in button at lines 352-361).

5. **(i18n — new keys in ALL 10 locales, EN + DE first-class.)** New keys are added under the `login` namespace of `web-frontend/public/locales/{locale}/auth.json` for all 10 locales (`de, en, fr, it, rm, es, fi, nl, ja, gsw-BE`): `login.continueWithGoogle` (EN: "Continue with Google", DE: "Mit Google fortfahren") and `login.orDivider` (EN: "or", DE: "oder"). EN + DE are first-class quality; the other 8 may be straight/tool translations per CLAUDE.md §Localization (this is **UI** i18n, not email — all 10 locales required). All strings in the component go through `t(...)` via the existing `useTranslation(['auth', 'validation'])` (LoginForm.tsx:71) — no hardcoded copy.

6. **(Unit tests — vitest + RTL, gated rendering + service call.)** `LoginForm.test.tsx` covers: (a) the Google button **renders when `features.sso === true`**; (b) it does **NOT render when `features.sso === false`**; (c) **clicking it calls `authService.signInWithFederated('Google')`** exactly once. The test must pass `sso` in the `mockConfig.features` object used by `ConfigProvider` (currently `LoginForm.test.tsx:43-47` — add `sso` and `turnstile`). Assertions use the EN string or the namespace-stripped key per project testing rules, never a non-EN translation. `signInWithFederated` is mocked at the `authService` boundary.

7. **(Playwright smoke — `google-sso.spec.ts`, with the CI-automation caveat captured.)** A new `web-frontend/e2e/auth/google-sso.spec.ts` smoke is added under the default `chromium` (organizer) project. Because a real Google OAuth round-trip cannot be automated in CI without a test Google identity (§6 of the plan), the spec covers what is automatable: with `features.sso` on, the login page renders the "Continue with Google" button and clicking it initiates the Cognito hosted-UI redirect (assert navigation toward the Cognito `/oauth2/authorize` host, or intercept the redirect) — and **does not** trigger any backend mutation. The link-existing-user and brand-new-user paths are covered by the **manual prod smoke** in AC8 (documented as an Open Question / manual gate in the spec header, mirroring the `HAS_EMAIL_INTEGRATION` skip pattern in `web-frontend/e2e/auth/forgot-password.spec.ts:28-30`). **No real outbound comms / no real invites** may be triggered (staging IS prod — MEMORY rule).

8. **(Deploy off → manual prod smoke → flip on. Rollback = flag.)** Deploy with `features.sso.enabled=false`. Then run the **manual prod smoke** (plan §6, Phase 5): (i) one **real existing** user signs in with Google, lands authenticated, and **retains their roles** (account-linking from Phase 2 preserved their `sub`); (ii) one **brand-new** Google account signs in and lands as **ATTENDEE** (JIT provisioning from PR 1). Only after both pass is `features.sso.enabled` flipped to `true`. **Rollback:** set `features.sso.enabled=false` — no redeploy needed (the FE re-reads the flag from `GET /api/v1/config`); this is the kill-switch ADR-010 §D8 mandates. The manual smoke uses real Google accounts but must not trigger spurious comms.

9. **(Doc-drift, same commit.)** `docs/plans/sso-oidc-federation.md` Phase 5 row is annotated as delivered (button + flag), and ADR-010 §D8 / §Implementation note that `features.sso` now exists in the runtime-config contract. If no further business-logic doc applies, the commit still carries the doc update (do not use `[no-doc]` — this adds a user-visible feature + a config-contract field).

## Tasks / Subtasks

- [ ] **Task 1 — Backend: add `sso` to the feature-flags contract (AC: 1)** — *gateway*
  - [ ] RED: add/extend a gateway test asserting `GET /api/v1/config` returns `features.sso` and that it defaults to `false` when `features.sso.enabled` is unset. (If no `ConfigControllerTest` exists yet — confirmed none under `api-gateway/src/test/java/ch/batbern/gateway/config/` — add a focused `@WebMvcTest`/slice test or extend the nearest config test; keep it minimal and aligned with existing patterns in that package.)
  - [ ] GREEN: add `private boolean sso;` to `FeatureFlagsDTO.java` (Lombok `@Builder`/`@Data` — no manual getter). In `ConfigController.java`, add `@Value("${features.sso.enabled:false}") private boolean ssoEnabled;` and `.sso(ssoEnabled)` in the `FeatureFlagsDTO.builder()` chain (after `.turnstile(...)`, line 78).
  - [ ] Confirm the property is documented (where other `features.*`/`security.*` toggles live) so an operator knows the flip lever; default stays `false`.

- [ ] **Task 2 — Frontend: add `sso` to `AppConfig['features']` + dev fallback (AC: 2)** — *frontend*
  - [ ] RED: extend `web-frontend/src/config/runtime-config.test.ts` (features block, line 16) and `web-frontend/src/contexts/__tests__/useFeature.test.tsx` to assert `sso` is read.
  - [ ] GREEN: add `sso: boolean;` to the `features` object in the `AppConfig` interface (`runtime-config.ts:23-28`); set `sso: false` in `getDefaultDevelopmentConfig()` (lines 172-177). `validateConfig` already only requires `features` to be an object — no change needed there.
  - [ ] `npm run type-check` to surface any `AppConfig` consumers (e.g. test mock fixtures) that now need `sso`.

- [ ] **Task 3 — FE: render the gated "Continue with Google" button (AC: 3, 4)** — *frontend, TDD*
  - [ ] RED: in `LoginForm.test.tsx`, add the three cases from AC6 (renders when on / hidden when off / click → `signInWithFederated('Google')`). Add `sso` (and `turnstile`) to `mockConfig.features` (lines 43-47). Mock `authService.signInWithFederated`.
  - [ ] GREEN: import `useFeature` (`@/contexts/useFeature`), `Divider` (`@mui/material`), `Google` (`@mui/icons-material`), and `authService` (`@/services/auth/authService` — or the established import path used by Story 12.7). Compute `const ssoEnabled = useFeature('sso')`. In the password-login branch (the `:` arm at line 275, before the `<Box component="form" onSubmit={handleSubmit(onSubmit)}>`), conditionally render: `{ssoEnabled && (<><Button variant="outlined" fullWidth startIcon={<Google />} disabled={isLoading} onClick={() => authService.signInWithFederated('Google')}>{t('auth:login.continueWithGoogle')}</Button><Divider sx={{ my: 2 }}>{t('auth:login.orDivider')}</Divider></>)}`. Do NOT render it in the `pendingNewPassword` branch.
  - [ ] Verify the button calls the **service layer** (no direct Amplify import in the component); `disabled` mirrors `isLoading`.
  - [ ] REFACTOR + `npm run type-check` + `npm run lint` + targeted vitest green.

- [ ] **Task 4 — i18n: add keys in all 10 locales (AC: 5)** — *frontend*
  - [ ] Add `login.continueWithGoogle` + `login.orDivider` to `web-frontend/public/locales/{de,en,fr,it,rm,es,fi,nl,ja,gsw-BE}/auth.json` under the existing `login` object. EN + DE first-class (EN: "Continue with Google"/"or"; DE: "Mit Google fortfahren"/"oder"); other 8 straight translations.
  - [ ] Confirm valid JSON for each file (10 files) and that no existing `login.*` key is disturbed.

- [ ] **Task 5 — Playwright smoke `google-sso.spec.ts` (AC: 7)** — *e2e, chromium project*
  - [ ] Add `web-frontend/e2e/auth/google-sso.spec.ts`: with the flag on, the login page shows "Continue with Google"; clicking it initiates the Cognito hosted-UI redirect (assert the navigation target host / intercept the redirect) without backend mutation. Header documents the manual-gate caveat (real Google round-trip not CI-automatable) and the MEMORY no-real-comms rule.
  - [ ] Keep it in the default `chromium` (organizer) project (per `web-frontend/playwright.config.ts:109-117`); skip gracefully if the flag is off in the target env (mirror the env-guard pattern in `forgot-password.spec.ts`).

- [ ] **Task 6 — Docs same commit (AC: 9)**
  - [ ] Annotate `docs/plans/sso-oidc-federation.md` Phase 5 (button + flag delivered) and ADR-010 §D8/§Implementation (`features.sso` now in the runtime-config contract). No `[no-doc]`.

- [ ] **Task 7 — Full verification + deploy/smoke note**
  - [ ] `make verify` (or targeted: `:api-gateway:test`, frontend type-check/lint/vitest) — dump to a temp file, grep, all green (CLAUDE.md: tee → grep, don't re-run repeatedly).
  - [ ] Record the deploy/smoke procedure in the PR: ship flag **off** → manual prod smoke (existing-user-links + new-user-ATTENDEE) → flip `features.sso.enabled=true`. Rollback = flip back to `false` (no redeploy).

## Dev Notes

### Architecture context — why the flag is the whole safety story
- **This is the ONLY user-visible flip in Epic 12.** Phases 0–4 + PR 0/PR 1 are all invisible (provider, linking trigger, provisioning, gating, callback route, service method). The button is the single moment a user can choose Google — so it sits behind `features.sso` for an instant, redeploy-free kill-switch. Per `docs/plans/sso-oidc-federation.md:238-247` and ADR-010 §D8.
- **The flag is plumbed end-to-end through the existing runtime-config contract**, not a frontend-only env toggle. The chain is: `FeatureFlagsDTO` (gateway DTO) → `ConfigController.getConfig()` serves it at `GET /api/v1/config` → `runtime-config.ts` `loadRuntimeConfig()` fetches + caches it → `ConfigContext`/`useConfig` → `useFeature('sso')`. This is the **exact same wiring** `turnstile` already uses (added Story 10.31): `FeatureFlagsDTO.turnstile` → `useFeature('turnstile')` (`useTurnstile.ts:67`). Because the FE reads the flag at runtime, flipping the gateway property toggles the button **without rebuilding or redeploying the frontend** (a gateway property change + restart suffices) — that is what makes "Rollback: `features.sso=false`, no redeploy" (plan §Phase 5) literally true.
- **`signInWithFederated` is a Story 12.7 deliverable** — grep confirms it does **not** exist in `web-frontend/src/` today (`signInWithRedirect`/`signInWithFederated` have zero hits in `src/`). This story consumes it; if 12.7 is not yet merged, this story is blocked (the button has nothing to call). The Amplify `loginWith.oauth` block it ultimately drives is already configured in `web-frontend/src/config/amplify.ts:54-61`.

### Files to touch — current state & what to preserve
| File | Current state | Change | Preserve |
|---|---|---|---|
| `api-gateway/.../config/dto/FeatureFlagsDTO.java` | `:20-36` notifications/analytics/pwa/turnstile | add `private boolean sso;` | existing flags; Lombok annotations |
| `api-gateway/.../config/ConfigController.java` | `:74-79` builds `FeatureFlagsDTO` (no sso); `@Value` props `:33-43` | add `@Value("${features.sso.enabled:false}")` + `.sso(ssoEnabled)` | all other flag values; `apiBaseUrl`/`cognito`/`turnstile` logic |
| `web-frontend/src/config/runtime-config.ts` | `:23-28` `features` interface (no sso); `:172-177` dev fallback | add `sso: boolean` to interface + `sso: false` to fallback | `validateConfig`; turnstile optional block |
| `web-frontend/src/components/auth/LoginForm/LoginForm.tsx` | password-login panel `:275-391`; new-password panel `:224-274`; `useTranslation(['auth','validation'])` `:71` | add `useFeature('sso')`-gated Google `Button` + `Divider` above the form (password panel only), `onClick` → `authService.signInWithFederated('Google')` | existing email/password form, new-password panel, `isLoading` disabling, all current keys |
| `web-frontend/src/components/auth/LoginForm/LoginForm.test.tsx` | `mockConfig.features` `:43-47` lacks `sso`/`turnstile`; `ConfigProvider` wrapper `:52-58` | add `sso`+`turnstile` to mock; add 3 button cases; mock `signInWithFederated` | existing render helper + sign-in tests |
| `web-frontend/public/locales/{10}/auth.json` | `login` object (EN keys at en/auth.json) | add `continueWithGoogle` + `orDivider` to all 10 | every existing `login.*` key |
| `web-frontend/e2e/auth/google-sso.spec.ts` | (new) | render-button + redirect-initiation smoke (chromium) | — |

### Testing standards (per CLAUDE.md 4-layer + TDD red-green-refactor)
- **Frontend unit:** vitest + RTL — `screen`, `userEvent`, `waitFor`; test names `should ... when ...`. Render via the existing `renderWithTheme` helper (wraps `ConfigProvider` + `I18nextProvider` + `ThemeProvider`); the `ConfigProvider` `config` prop is where `features.sso` is toggled per test. Assert on EN string or namespace-stripped key, never a non-EN translation.
- **Gateway:** the `sso` flag is a DTO/serialization concern; a slice/MVC test asserting the `GET /api/v1/config` JSON carries `features.sso` (default false) suffices — no Testcontainers needed (no DB on this path).
- **Playwright:** default `chromium` (organizer) project; real Google OAuth is NOT CI-automatable — the spec asserts button presence + redirect initiation and documents the manual gate (model the env-skip on `forgot-password.spec.ts:28-30`). No real outbound comms (staging IS prod).
- Run via tee → temp file → grep (CLAUDE.md), don't re-run suites repeatedly.

### Project Structure Notes
- MUI 7.x: `Button`/`Divider` from `@mui/material`; `Google` icon from `@mui/icons-material` (confirmed present: `node_modules/@mui/icons-material/Google.js`). The component already imports `Button`, `Box`, `Typography` etc. from `@mui/material` (LoginForm.tsx:7-21) — add `Divider`.
- `useFeature` is the canonical flag accessor (`web-frontend/src/contexts/useFeature.ts`); do not read `config.features.sso` ad-hoc.
- i18n namespace: LoginForm uses `useTranslation(['auth', 'validation'])` → new keys live in the **`auth`** namespace (`auth.json`), under the existing `login` object.
- Deploy tier: frontend deploy for the FE changes + gateway deploy for the DTO/controller change. The flag flips at the gateway (runtime config), not in the bundle.

### Open Questions
- **OQ-1 (AC7) — Google OAuth in CI.** A full Google round-trip (consent → `idpresponse` → Cognito → `/auth/callback`) needs a managed test Google identity, which is out of reach for this repo's CI today. **Resolution (plan §6):** the Playwright spec automates only button-presence + redirect-initiation; the link-existing + new-user assertions are the **manual prod smoke** (AC8), gated before the flip. Capture the manual run in the PR. Revisit a CI test-IdP only if Google automation becomes available.

### References
- [Source: docs/plans/sso-oidc-federation.md#Phase 5 — Surface the button behind a feature flag] (lines 238-247: flag, button placement, deploy-off→smoke→flip, rollback) · [§6 Testing strategy, lines 273-284: Playwright google-sso.spec.ts + manual prod smoke] · [§2 line 40: button location = LoginForm.tsx]
- [Source: docs/architecture/ADR-010-federated-identity-via-cognito.md#D8] (button on all login surfaces, behind `features.sso`) · [§Implementation lines 121-127: only user-visible flip behind the flag]
- [Source: api-gateway/.../config/dto/FeatureFlagsDTO.java:16-37] · [Source: api-gateway/.../config/ConfigController.java:60-88 (FeatureFlagsDTO.builder, lines 74-79)]
- [Source: web-frontend/src/config/runtime-config.ts:15-32 (AppConfig.features), :163-179 (dev fallback)] · [Source: web-frontend/src/contexts/useFeature.ts:21]
- [Source: web-frontend/src/hooks/useTurnstile/useTurnstile.ts:67 — `useFeature('turnstile')` precedent]
- [Source: web-frontend/src/components/auth/LoginForm/LoginForm.tsx:71 (useTranslation), :224-274 (new-password panel), :275-391 (password-login panel), :352-361 (sign-in button)]
- [Source: web-frontend/src/components/auth/LoginForm/LoginForm.test.tsx:35-58 (mockConfig + renderWithTheme)]
- [Source: web-frontend/src/config/amplify.ts:54-61 — Amplify loginWith.oauth (consumed by 12.7's signInWithFederated)]
- [Source: web-frontend/public/locales/en/auth.json (login object) — new keys land in all 10 locales]
- [Source: web-frontend/e2e/auth/forgot-password.spec.ts:28-30 — env-guard/skip pattern to model the manual gate]
- [Source: web-frontend/playwright.config.ts:109-117 — chromium (organizer) default project]
- Prereq: Story 12.7 (frontend callback route + `authService.signInWithFederated`) · CLAUDE.md §Localization (UI i18n = all 10 locales), §API Development (service layer not direct HTTP/Amplify), §Environment Variables (config objects not process.env)

## Dev Agent Record

### Agent Model Used

_(to be filled by dev agent)_

### Debug Log References

_(to be filled by dev agent)_

### Completion Notes List

_(to be filled by dev agent)_

### File List

_(to be filled by dev agent)_

### Change Log

| Date | Change |
|---|---|
| 2026-06-01 | Story 12.9 drafted (SSO Phase 5 — "Continue with Google" button behind `features.sso`). Status: ready-for-dev. |
