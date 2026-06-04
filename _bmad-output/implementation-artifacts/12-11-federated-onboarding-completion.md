# Story 12.11: Federated Onboarding Completion — Consent, Company & Newsletter for Google Sign-ups

Status: ready-for-dev

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **user who registered via "Continue with Google"**,
I want **to be taken to a profile-completion step where I accept the Terms of Service / Privacy Policy, optionally set my company, and choose my newsletter preference**,
so that **the platform has my legally required consent on record and the same onboarding data it collects from native registrations — and I cannot use the platform until I've consented.**

This closes the GDPR-relevant gap opened by Epic 12: federated JIT provisioning (PostConfirmation Lambda federated branch + CUMS `JITUserProvisioningInterceptor`) creates a `user_profiles` row with **no consent recorded, no company, no newsletter choice** — everything the native 2-step registration collects in `RegistrationStep2.tsx` (required ToS+Privacy checkbox at lines 117-146, newsletter opt-in at lines 148-160). Worse: even for NATIVE registrations, `agreedToTerms` is collected but **persisted nowhere** (it is not packed into `custom:preferences` by `authService.signUp` — see authService.ts:313-341 — and `user_profiles` has no consent column), and `newsletterOptIn` is folded lossily into `pref_email_notifications`. This story fixes the data model for both paths and adds the blocking onboarding gate for federated users.

**Design direction (Nissim, 2026-06-04):** reuse the existing speaker-portal profile page (`web-frontend/src/pages/speaker-portal/ProfileUpdatePage.tsx`, route `/speaker-portal/profile`), generalize it to a **role-neutral `/profile` route**, add a **second tab** containing consent + newsletter. After a first federated registration the user is **redirected there and blocked from doing anything else** until they consent and save.

**Prerequisites:** Epic 12 Phases 0-5 live (they are — SSO shipped enabled). No dependency on 12.10 or 12.12.

## Acceptance Criteria

1. **(DB — consent + newsletter columns, CUMS migration `V17`.)** A new Flyway migration `services/company-user-management-service/src/main/resources/db/migration/V17__add_consent_and_newsletter_to_user_profiles.sql` adds to `user_profiles`:
   - `terms_accepted_at TIMESTAMP NULL` — the moment the user accepted ToS+Privacy. `NULL` = consent not on record → onboarding gate applies.
   - `newsletter_opt_in BOOLEAN NOT NULL DEFAULT FALSE` — explicit newsletter consent, decoupled from `pref_email_notifications` (which governs transactional notification emails, not marketing).
   - **Backfill:** `UPDATE user_profiles SET terms_accepted_at = created_at WHERE cognito_user_id IS NOT NULL AND cognito_user_id NOT LIKE 'google_%'` — native registrants could not have completed `RegistrationStep2` without the required checkbox, so `created_at` is a defensible consent timestamp. Federated rows (Cognito federated usernames are `google_<sub>`) stay `NULL` → they get the gate **retroactively**, which is exactly the desired remediation for the users JIT-provisioned since SSO went live. Anonymous rows (`cognito_user_id IS NULL`, ADR-005) stay `NULL` — they cannot authenticate, so the gate never fires for them; when they later register natively, AC2 records consent.
   - Migration is forward-only; per CLAUDE.md NEVER touch applied migrations.

2. **(Native path writes consent going forward.)** `infrastructure/lib/lambda/triggers/post-confirmation.ts` `createUser()` (INSERT/UPDATE around lines 299-333): when the user is a **native self-registration** (non-federated — `custom:preferences` present / not the 12.8-F1a federated branch), set `terms_accepted_at = NOW()` and `newsletter_opt_in` from `custom:preferences.newsletterOptIn ?? false` (the JSON already carries `newsletterOptIn` — authService.ts:320). The **federated branch leaves `terms_accepted_at` NULL** (consent must be explicit, not implied by Google sign-in). `JITUserProvisioningInterceptor.java` (CUMS, lines 161-172) likewise leaves it NULL and defaults `newsletter_opt_in` false. The Lambda handler-level unit test (per CLAUDE.md Lambda testing rule) is extended for both branches.

3. **(API contract — spec first, then regenerate.)** `docs/api/users-api.openapi.yml`:
   - `UserResponse` gains `termsAcceptedAt` (`string, format: date-time, nullable`) and `newsletterOptIn` (`boolean`). Both returned by `GET /api/v1/users/me` unconditionally (no `?include=` needed — the gate check must be cheap and always present).
   - `UpdateUserRequest` (line 2140) gains `termsAccepted` (`boolean`, write-once semantics documented) and `newsletterOptIn` (`boolean`).
   - DTOs are **generated** (`ch.batbern.companyuser.dto.generated.*` — backend regenerates on build, NOT committed) and frontend types regenerated via `npm run generate:api-types` (committed). Update spec BEFORE implementation (contract-first, ADR-006).

4. **(PUT /users/me — write-once consent semantics.)** `UserController.updateCurrentUser` (UserController.java:104-115) / `UserService`: when `termsAccepted == true` and `terms_accepted_at IS NULL`, set `terms_accepted_at = now()` **server-side** (client never supplies the timestamp). When `termsAccepted` is `false` or absent → no change; consent can NEVER be revoked/unset via this endpoint (write-once). `newsletterOptIn` is freely updatable both ways at any time. Covered by integration tests (Testcontainers, extends `AbstractIntegrationTest`, `@Transactional`): accept-sets-timestamp-once, second-accept-does-not-move-timestamp, false-never-clears, newsletter-toggles-both-ways.

5. **(Role-neutral `/profile` route with tabs — generalized from the speaker page.)** `ProfileUpdatePage` moves/generalizes from `/speaker-portal/profile` (App.tsx:149, `SpeakerRoute`-gated) to **`/profile`** behind plain `ProtectedRoute` (any authenticated role). Two MUI tabs:
   - **Tab 1 "Profile"**: the existing fields (firstName, lastName, bio, profile photo via `ProfilePhotoUpload`, read-only email) **plus company** (uses `companyId` on `UpdateUserRequest` — the field already exists in the PUT contract; render the same company-autocomplete pattern used elsewhere, or a plain text field matching `^[a-zA-Z0-9]{1,12}$` if no shared autocomplete exists — check `RegistrationStep1`/organizer components for a reusable picker first; do not reinvent).
   - **Tab 2 "Consent & Newsletter"**: (a) if `termsAcceptedAt` is null → required checkbox with ToS + Privacy links (reuse copy/links from `RegistrationStep2.tsx:117-146`) + save; (b) if already accepted → read-only "Accepted on {date}" line (no checkbox); (c) newsletter switch bound to `newsletterOptIn`, always editable.
   - `/speaker-portal/profile` remains as a `<Navigate to="/profile" replace />` redirect (bookmarks, existing speaker-portal nav links). Speaker-portal navigation updated to point at `/profile`.

6. **(Blocking onboarding gate.)** `AuthContext`'s user object carries `termsAcceptedAt` (hydrated via the existing `hydrateUserFromDb` → `GET /users/me` path — AuthContext already awaits this before `setState({isAuthenticated: true})`, so the flag is present the moment any guard runs). `ProtectedRoute` (ProtectedRoute.tsx:20-71): when `isAuthenticated && user.termsAcceptedAt == null` and the target path is NOT `/profile` (and not `/logout`), redirect to `/profile?onboarding=1`. On `/profile` with `onboarding=1`: show a non-dismissible info Alert ("Please complete your profile to continue"), and the Consent tab is pre-selected. After a save that records consent, the user context is refreshed (invalidate/refetch `users/me`) → gate lifts → navigate to `/dashboard`. The gate applies to ALL protected routes for ALL roles; public (anonymous) routes are untouched.

7. **(Federated callback lands in the gate naturally.)** `AuthCallbackPage` keeps navigating to `/dashboard`; no special-casing — the `ProtectedRoute` gate performs the redirect to `/profile?onboarding=1` for consent-less users. (Keeps gate logic in exactly one place.) Add an RTL test asserting a fresh federated user (user object with `termsAcceptedAt: null`) ends up gated.

8. **(i18n — all 10 locales.)** New UI keys (tab labels, consent copy if not reusable from `register.*`, "accepted on" line, onboarding notice, newsletter label, save/success) added in all 10 locales (`de, en, fr, it, rm, es, fi, nl, ja, gsw-BE`), EN+DE first-class. Tests assert EN strings or namespace-stripped keys only.

9. **(Tests across layers.)**
   - CUMS: migration applies on Testcontainers PostgreSQL (suite boots with `spring.flyway.enabled=true`); UserController IT per AC4; JIT interceptor IT asserting federated provisioning leaves `terms_accepted_at` NULL.
   - Lambda: post-confirmation handler unit tests for AC2 (native sets consent+newsletter; federated leaves NULL).
   - Frontend (Vitest+RTL): tabs render; consent write-once UI states (checkbox vs "accepted on"); gate redirect logic in `ProtectedRoute`; newsletter toggle round-trip; `userAccountApi.ts:103-134` field-filter extended for the two new fields (it filters to allowed fields — without extending the allowlist the new fields are silently dropped!).
   - Playwright (chromium project): smoke — authenticated organizer (already-consented after backfill) can open `/profile`, sees both tabs, is NOT gated.

10. **(Doc-drift, same commit.)** Update `docs/plans/sso-oidc-federation.md` (new follow-up section), `docs/architecture/06b-user-lifecycle-sync.md` (consent fields in the lifecycle), and ADR-010 if it documents the JIT footprint. OpenAPI spec change is itself part of AC3.

## Tasks / Subtasks

- [ ] **Task 1 — Migration V17 + entity (AC: 1)** — *CUMS, TDD*
  - [ ] RED: integration test asserting the two columns exist + backfill semantics (insert a fake native row, a `google_`-prefixed row, an anonymous row → run migrations → assert backfill hit only the native one). Extends `AbstractIntegrationTest`.
  - [ ] GREEN: write `V17__add_consent_and_newsletter_to_user_profiles.sql` per AC1. Map onto the `User` entity (`termsAcceptedAt` Instant nullable, `newsletterOptIn` boolean) — follow the existing column-mapping style in the entity.
- [ ] **Task 2 — OpenAPI spec + regeneration (AC: 3)** — *contract-first; do this BEFORE Tasks 3-4 code*
  - [ ] Update `docs/api/users-api.openapi.yml` (`UserResponse`, `UpdateUserRequest`).
  - [ ] Backend build regenerates DTOs; `cd web-frontend && npm run generate:api-types` and COMMIT the generated frontend types.
- [ ] **Task 3 — PUT /users/me write-once consent (AC: 4)** — *CUMS, TDD*
  - [ ] RED: the four ITs from AC4.
  - [ ] GREEN: `UserService.updateCurrentUser` handles `termsAccepted` (write-once, server clock) + `newsletterOptIn`. `GET /users/me` returns both fields unconditionally.
- [ ] **Task 4 — post-confirmation Lambda + JIT (AC: 2)** — *infrastructure + CUMS*
  - [ ] RED: extend the handler unit tests (native sets `terms_accepted_at`/`newsletter_opt_in`; federated branch leaves consent NULL).
  - [ ] GREEN: extend the INSERT/UPDATE column lists in `post-confirmation.ts`; verify `JITUserProvisioningInterceptor` needs no change beyond entity defaults (assert via IT).
- [ ] **Task 5 — `/profile` route + tabs (AC: 5)** — *frontend, TDD*
  - [ ] RED: RTL tests — both tabs render for a non-speaker role; consent tab states (checkbox when null / "accepted on" when set); company field present; save calls `updateUserProfile` with new fields.
  - [ ] GREEN: generalize `ProfileUpdatePage` (move to `src/pages/profile/` or keep file and re-route — prefer minimal move that keeps git history), add Tabs, mount at `/profile` under plain `ProtectedRoute`; `/speaker-portal/profile` → `<Navigate to="/profile" replace />`; update speaker-portal nav link; extend `userAccountApi.ts` allowlist.
- [ ] **Task 6 — onboarding gate (AC: 6, 7)** — *frontend, TDD*
  - [ ] RED: ProtectedRoute tests — consent-less user redirected to `/profile?onboarding=1` from any protected path; consented user passes; `/profile` itself never loops; fresh-federated-user flow test.
  - [ ] GREEN: extend `AuthContext` user mapping with `termsAcceptedAt` (from `/users/me`), add gate branch in `ProtectedRoute`, onboarding banner + tab preselect + post-save refresh/navigate in the profile page.
- [ ] **Task 7 — i18n 10 locales (AC: 8)**
- [ ] **Task 8 — Playwright smoke + full verify (AC: 9)** — run CUMS suite, frontend vitest+type-check+lint, Playwright chromium smoke; pipe outputs through `tee /tmp/<name>.log`.
- [ ] **Task 9 — docs (AC: 10)**

## Dev Notes

### Why the data model looks like this
- `terms_accepted_at` as nullable timestamp (not boolean) doubles as audit record + gate flag. Write-once server-side prevents consent forgery/revocation via API.
- `newsletter_opt_in` decoupled from `pref_email_notifications`: the latter governs transactional notification emails (`preferences.notifications.email` → post-confirmation.ts:331) and is semantically NOT marketing consent.
- Federated detection for backfill: Cognito federated usernames/`sub` mapping — `cognito_user_id` is stored as the Cognito username which for Google-federated users is `google_<numeric-sub>`. Verify against a live federated row before finalizing the LIKE pattern (e.g. `SELECT cognito_user_id FROM user_profiles WHERE ...` via the DB tunnel — `scripts/staging/start-db-tunnel.sh`).
- **Accepted tradeoff (flag to Nissim if uncomfortable):** organizer-provisioned speakers (Pattern N `adminCreateUserSilently`) never saw the ToS checkbox but get backfilled as consented (their `cognito_user_id` is non-`google_`). Gating them retroactively would lock established speakers out of active workflows. If stricter handling is wanted, a follow-up can gate rows where no registration evidence exists.

### Existing-code ground truth (READ THESE before implementing)
- `web-frontend/src/components/auth/RegistrationStep2/RegistrationStep2.tsx:117-160` — consent + newsletter UI to reuse copy/links from (`/terms`, `/privacy`).
- `web-frontend/src/services/auth/authService.ts:313-341` — `signUp` packs `custom:preferences` (`newsletterOptIn` IS there; `agreedToTerms` is NOT — AC2 derives native consent from the flow itself, not a claim).
- `infrastructure/lib/lambda/triggers/post-confirmation.ts` — `parseUserPreferences` (106-118), `createUser` (221-361, INSERT/UPDATE 299-333), federated name fallback (455-465 — that branch = federated detection).
- `services/.../interceptor/JITUserProvisioningInterceptor.java:80-192` — federated/JIT row creation; non-blocking catch-all (186-192).
- `services/.../controller/UserController.java:82-95` (GET /users/me), `:104-115` (PUT /users/me) — DTOs are OpenAPI-GENERATED (`dto.generated.*`): change the spec, not handwritten DTOs.
- `web-frontend/src/services/userAccountApi.ts:103-134` — **field allowlist filter; MUST be extended or the new fields silently vanish.**
- `web-frontend/src/pages/speaker-portal/ProfileUpdatePage.tsx` — page to generalize; uses `getUserProfile(['company'])` + `updateUserProfile`; `ProfilePhotoUpload` component for the photo.
- `web-frontend/src/components/auth/ProtectedRoute/ProtectedRoute.tsx:20-71` — gate hook point; role wrappers at 80-97.
- `web-frontend/src/contexts/AuthContext.tsx` — `hydrateUserFromDb` runs BEFORE `setState({isAuthenticated: true})` (both native + federated paths), so `termsAcceptedAt` is reliably on the user before any route guard evaluates. Mirror the field through `extractUserContextFromToken`'s User shape (`src/types/auth`).
- Enum/flow rules, ADR-003 (no UUIDs in APIs), ADR-006 (contract-first), TDD red-green-refactor: `_bmad-output/project-context.md`.

### Project Structure Notes
- New page location: prefer `web-frontend/src/pages/profile/ProfilePage.tsx` (role-neutral pages live outside `speaker-portal/`); keep the old path as redirect.
- CUMS layering: Controller (generated API) → Service (business logic incl. write-once) → Repository; no logic in mapper.

### References
- [Source: docs/plans/sso-oidc-federation.md] (Epic 12 plan), [Source: docs/architecture/ADR-010-federated-identity-via-cognito.md]
- [Source: _bmad-output/implementation-artifacts/12-3-canonical-jit-provisioning.md] (JIT premise + language-normalization lesson)
- [Source: _bmad-output/implementation-artifacts/12-8-federated-provisioning-inactive-gating-verify.md] (federated provisioning reality: PostConfirmation creates the row, names from given_name/family_name)
- [Source: docs/api/users-api.openapi.yml:2140] (UpdateUserRequest)
- Sprint context: SSO live since 2026-06-04 (PR #737-#740); every federated sign-up until this ships accumulates consent-less rows — the AC1 backfill + retro gate covers them.

## Dev Agent Record

### Agent Model Used

### Debug Log References

### Completion Notes List

### File List
