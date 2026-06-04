# Story 12.11: Federated Onboarding Completion — Consent, Company & Newsletter for Google Sign-ups

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **user who registered via "Continue with Google"**,
I want **to be taken to a profile-completion step where I accept the Terms of Service / Privacy Policy, optionally set my company, and choose my newsletter preference**,
so that **the platform has my legally required consent on record and the same onboarding data it collects from native registrations — and I cannot use the platform until I've consented.**

This closes the GDPR-relevant gap opened by Epic 12: federated JIT provisioning (PostConfirmation Lambda federated branch + CUMS `JITUserProvisioningInterceptor`) creates a `user_profiles` row with **no consent recorded, no company, no newsletter choice** — everything the native 2-step registration collects in `RegistrationStep2.tsx` (required ToS+Privacy checkbox at lines 117-146, newsletter opt-in at lines 148-160). Worse: even for NATIVE registrations, `agreedToTerms` is collected but **persisted nowhere** (it is not packed into `custom:preferences` by `authService.signUp` — see authService.ts:313-341 — and `user_profiles` has no consent column), and `newsletterOptIn` is packed into `custom:preferences` but **never consumed** (post-confirmation.ts declares it in its `UserPreferences` interface and never reads it — no `newsletter_subscribers` row is created). This story fixes the consent data model for both paths, wires the dropped registration newsletter opt-in into the EXISTING newsletter machinery, and adds the blocking onboarding gate for federated users.

**Design direction (Nissim, 2026-06-04):** reuse the existing speaker-portal profile page (`web-frontend/src/pages/speaker-portal/ProfileUpdatePage.tsx`, route `/speaker-portal/profile`), generalize it to a **role-neutral `/profile` route**, add a **second tab** containing consent + newsletter. After a first federated registration the user is **redirected there and blocked from doing anything else** until they consent and save.

**SCOPE REVISION (Nissim, 2026-06-04, pre-implementation audit):**
1. **NO `newsletter_opt_in` column.** Newsletter consent already has an authoritative store: EMS `newsletter_subscribers` (Story 10.7 V67, with unsubscribe tokens, Story 10.29 bounce suppression, Story 10.28 organizer management). Authenticated self-service already exists (`GET/PATCH /api/v1/newsletter/my-subscription`; `patchMySubscription` creates-or-reactivates the row, source=`account`) and is already used by the `NewsletterSection` toggle in `UserSettingsTab.tsx`. A `user_profiles.newsletter_opt_in` column would be a second source of truth the newsletter sender never reads. The `/profile` Consent & Newsletter tab reuses `useMySubscription`/`usePatchMySubscription` instead.
2. **Consent stays a top-level column** (`terms_accepted_at` on `user_profiles`, top-level `termsAcceptedAt` in `UserResponse`) — NOT inside the `preferences` embeddable: preferences are freely overwritten both ways by `PUT /users/me/preferences` (write-once would need special-casing there) and only appear with `?include=preferences` (the gate flag must be unconditionally present).
3. **Backfill discriminator corrected (verified against live prod DB via tunnel, 2026-06-04):** `cognito_user_id` stores the Cognito **sub UUID for ALL users including federated** (`Google_<sub>` is the Cognito *username*, a different attribute that is never persisted). `LIKE 'google_%'` matches ZERO rows. Verified: exactly 2 federated rows exist (test identities), first created `2026-06-04 16:04:51 UTC`; 273 UUID-shaped rows; 2108 anonymous rows. Discriminator: `created_at < TIMESTAMP '2026-06-04 16:00:00+00'` (SSO go-live; no federated row can predate it). Native registrants after the cutoff but before this deploy get gated once and re-accept — acceptable (hours-sized window).
4. **Native newsletter gap fixed frontend-side:** `RegistrationWizard` calls the existing public `POST /api/v1/newsletter/subscribe` when the opt-in box is checked (same approach as `NewsletterSubscribeWidget`). The Lambda does NOT write newsletter data (its DB connection has no business in EMS tables).
5. **Lambda consent covers BOTH branches:** the link-historical-participant UPDATE branch (anon row + registration) must also set `terms_accepted_at`, and both branches must skip it for federated trigger events (detected via the `identities` user attribute, with `Google_`-username-prefix fallback).
6. **Future organizer-provisioned speakers** (Pattern N, post-deploy) will have `terms_accepted_at NULL` → they see the consent gate on first login and give real consent. This supersedes the "accepted tradeoff" note below for post-deploy rows; pre-deploy rows are still backfilled as consented.

**Prerequisites:** Epic 12 Phases 0-5 live (they are — SSO shipped enabled). No dependency on 12.10 or 12.12.

## Acceptance Criteria

1. **(DB — consent column, CUMS migration `V17`.)** A new Flyway migration `services/company-user-management-service/src/main/resources/db/migration/V17__add_terms_accepted_at_to_user_profiles.sql` adds to `user_profiles`:
   - `terms_accepted_at TIMESTAMP WITH TIME ZONE NULL` — the moment the user accepted ToS+Privacy. `NULL` = consent not on record → onboarding gate applies.
   - **Backfill:** `UPDATE user_profiles SET terms_accepted_at = created_at WHERE cognito_user_id IS NOT NULL AND created_at < TIMESTAMP WITH TIME ZONE '2026-06-04 16:00:00+00'` — native registrants could not have completed `RegistrationStep2` without the required checkbox, so `created_at` is a defensible consent timestamp. The cutoff is the SSO go-live moment (first federated row: `2026-06-04 16:04:51 UTC`, verified live — see Scope Revision #3; `cognito_user_id` is the sub UUID for everyone, so NO username-shape discriminator exists). Federated rows stay `NULL` → they get the gate **retroactively**. Anonymous rows (`cognito_user_id IS NULL`, ADR-005) stay `NULL` — they cannot authenticate, so the gate never fires for them; when they later register natively, AC2 records consent.
   - Migration is forward-only; per CLAUDE.md NEVER touch applied migrations. NO newsletter column (Scope Revision #1).

2. **(Native path writes consent going forward; registration newsletter opt-in wired.)** `infrastructure/lib/lambda/triggers/post-confirmation.ts` `createUser()`: when the confirmation is a **native self-registration** (NOT federated — federated = `identities` user attribute present, `Google_` username-prefix fallback), set `terms_accepted_at = CURRENT_TIMESTAMP` in **BOTH** the INSERT branch and the link-historical-participant UPDATE branch. The **federated path leaves `terms_accepted_at` NULL** (consent must be explicit, not implied by Google sign-in). `JITUserProvisioningInterceptor.java` (CUMS, lines 161-172) likewise leaves it NULL (entity default). The Lambda handler-level unit test (per CLAUDE.md Lambda testing rule) is extended for both branches. **Newsletter:** the Lambda does NOT touch newsletter data; instead `RegistrationWizard`/`useRegistration` calls the existing public `POST /api/v1/newsletter/subscribe` (newsletterService.subscribe) when `newsletterOptIn` is checked — fixing the silently-dropped checkbox (Scope Revision #4).

3. **(API contract — spec first, then regenerate.)** `docs/api/users-api.openapi.yml`:
   - `UserResponse` gains `termsAcceptedAt` (`string, format: date-time, nullable`), returned by `GET /api/v1/users/me` unconditionally (no `?include=` needed — the gate check must be cheap and always present).
   - `UpdateUserRequest` (line 2140) gains `termsAccepted` (`boolean`, write-once semantics documented).
   - NO `newsletterOptIn` anywhere in users-api (Scope Revision #1).
   - DTOs are **generated** (`ch.batbern.companyuser.dto.generated.*` — backend regenerates on build, NOT committed) and frontend types regenerated via `npm run generate:api-types` (committed). Update spec BEFORE implementation (contract-first, ADR-006).

4. **(PUT /users/me — write-once consent semantics.)** `UserController.updateCurrentUser` (UserController.java:104-115) / `UserService`: when `termsAccepted == true` and `terms_accepted_at IS NULL`, set `terms_accepted_at = now()` **server-side** (client never supplies the timestamp). When `termsAccepted` is `false` or absent → no change; consent can NEVER be revoked/unset via this endpoint (write-once). Covered by integration tests (Testcontainers, extends `AbstractIntegrationTest`, `@Transactional`): accept-sets-timestamp-once, second-accept-does-not-move-timestamp, false-never-clears.

5. **(Role-neutral `/profile` route with tabs — generalized from the speaker page.)** `ProfileUpdatePage` moves/generalizes from `/speaker-portal/profile` (App.tsx:149, `SpeakerRoute`-gated) to **`/profile`** behind plain `ProtectedRoute` (any authenticated role). Two MUI tabs:
   - **Tab 1 "Profile"**: the existing fields (firstName, lastName, bio, profile photo via `ProfilePhotoUpload`, read-only email) **plus company** (uses `companyId` on `UpdateUserRequest` — the field already exists in the PUT contract; render the same company-autocomplete pattern used elsewhere, or a plain text field matching `^[a-zA-Z0-9]{1,12}$` if no shared autocomplete exists — check `RegistrationStep1`/organizer components for a reusable picker first; do not reinvent).
   - **Tab 2 "Consent & Newsletter"**: (a) if `termsAcceptedAt` is null → required checkbox with ToS + Privacy links (reuse copy/links from `RegistrationStep2.tsx:117-146`) + save; (b) if already accepted → read-only "Accepted on {date}" line (no checkbox); (c) newsletter switch via the EXISTING `useMySubscription`/`usePatchMySubscription` hooks (same pattern as `NewsletterSection` in `UserSettingsTab.tsx:333-372` — `PATCH /newsletter/my-subscription` creates-or-reactivates, so it works for fresh federated users with no subscriber row), always editable, saves independently of the consent form (Scope Revision #1).
   - `/speaker-portal/profile` remains as a `<Navigate to="/profile" replace />` redirect (bookmarks, existing speaker-portal nav links). Speaker-portal navigation updated to point at `/profile`.

6. **(Blocking onboarding gate.)** `AuthContext`'s user object carries `termsAcceptedAt` (hydrated via the existing `hydrateUserFromDb` → `GET /users/me` path — AuthContext already awaits this before `setState({isAuthenticated: true})`, so the flag is present the moment any guard runs). `ProtectedRoute` (ProtectedRoute.tsx:20-71): when `isAuthenticated && user.termsAcceptedAt == null` and the target path is NOT `/profile` (and not `/logout`), redirect to `/profile?onboarding=1`. On `/profile` with `onboarding=1`: show a non-dismissible info Alert ("Please complete your profile to continue"), and the Consent tab is pre-selected. After a save that records consent, the user context is refreshed (invalidate/refetch `users/me`) → gate lifts → navigate to `/dashboard`. The gate applies to ALL protected routes for ALL roles; public (anonymous) routes are untouched.

7. **(Federated callback lands in the gate naturally.)** `AuthCallbackPage` keeps navigating to `/dashboard`; no special-casing — the `ProtectedRoute` gate performs the redirect to `/profile?onboarding=1` for consent-less users. (Keeps gate logic in exactly one place.) Add an RTL test asserting a fresh federated user (user object with `termsAcceptedAt: null`) ends up gated.

8. **(i18n — all 10 locales.)** New UI keys (tab labels, consent copy if not reusable from `register.*`, "accepted on" line, onboarding notice, newsletter label, save/success) added in all 10 locales (`de, en, fr, it, rm, es, fi, nl, ja, gsw-BE`), EN+DE first-class. Tests assert EN strings or namespace-stripped keys only.

9. **(Tests across layers.)**
   - CUMS: migration applies on Testcontainers PostgreSQL (suite boots with `spring.flyway.enabled=true`); UserController IT per AC4; JIT interceptor IT asserting federated provisioning leaves `terms_accepted_at` NULL.
   - Lambda: post-confirmation handler unit tests for AC2 (native INSERT sets consent; native link-UPDATE sets consent; federated leaves NULL in both branches).
   - Frontend (Vitest+RTL): tabs render; consent write-once UI states (checkbox vs "accepted on"); gate redirect logic in `ProtectedRoute`; newsletter toggle wired to `usePatchMySubscription`; registration newsletter opt-in calls `newsletterService.subscribe`; `userAccountApi.ts:103-134` field-filter extended for `termsAccepted` (it filters to allowed fields — without extending the allowlist the new field is silently dropped!).
   - Playwright (chromium project): smoke — authenticated organizer (already-consented after backfill) can open `/profile`, sees both tabs, is NOT gated.

10. **(Doc-drift, same commit.)** Update `docs/plans/sso-oidc-federation.md` (new follow-up section), `docs/architecture/06b-user-lifecycle-sync.md` (consent fields in the lifecycle), and ADR-010 if it documents the JIT footprint. OpenAPI spec change is itself part of AC3.

## Tasks / Subtasks

- [x] **Task 1 — Migration V17 + entity (AC: 1)** — *CUMS, TDD*
  - [x] RED: integration test asserting the column exists + backfill semantics (insert a fake pre-cutoff native row, a post-cutoff row, an anonymous row → run migrations → assert backfill hit only the pre-cutoff native one). Extends `AbstractIntegrationTest`. (Note: migrations run before test data can be inserted — assert backfill semantics by replaying the UPDATE predicate against test rows, or assert column presence + entity round-trip; backfill correctness asserted on the SQL predicate.)
  - [x] GREEN: write `V17__add_terms_accepted_at_to_user_profiles.sql` per AC1. Map onto the `User` entity (`termsAcceptedAt` Instant nullable) — follow the existing column-mapping style in the entity.
- [x] **Task 2 — OpenAPI spec + regeneration (AC: 3)** — *contract-first; do this BEFORE Tasks 3-4 code*
  - [x] Update `docs/api/users-api.openapi.yml` (`UserResponse.termsAcceptedAt`, `UpdateUserRequest.termsAccepted`).
  - [x] Backend build regenerates DTOs; `cd web-frontend && npm run generate:api-types` and COMMIT the generated frontend types.
- [x] **Task 3 — PUT /users/me write-once consent (AC: 4)** — *CUMS, TDD*
  - [x] RED: the three ITs from AC4.
  - [x] GREEN: `UserService.updateCurrentUser` handles `termsAccepted` (write-once, server clock). `GET /users/me` returns `termsAcceptedAt` unconditionally (note: serialized only when non-null due to service-wide `default-property-inclusion: non_null`; frontend treats success+absent as null).
- [x] **Task 4 — post-confirmation Lambda + JIT + registration newsletter (AC: 2)** — *infrastructure + CUMS + frontend*
  - [x] RED: extend the handler unit tests (native INSERT sets `terms_accepted_at`; native link-UPDATE sets it; federated leaves NULL in both branches) — 4 new tests, 40/40 green.
  - [x] GREEN: add `isFederatedSignIn()` (identities attribute + `Google_` username prefix) and extend the INSERT/UPDATE column lists in `post-confirmation.ts`; `JITUserProvisioningInterceptor` needs no change beyond entity defaults (asserted via JITProvisioningIntegrationTest).
  - [x] Frontend: `useRegistration` calls `newsletterService.subscribe` when `newsletterOptIn` checked (best-effort, non-blocking on failure); 3 new unit tests, 11/11 green.
- [x] **Task 5 — `/profile` route + tabs (AC: 5)** — *frontend, TDD*
  - [x] RED: RTL tests — both tabs render for a non-speaker role; consent tab states (checkbox when null / "accepted on" when set); company field present; consent save calls `updateUserProfile` with `termsAccepted`; newsletter switch calls `usePatchMySubscription` — 10/10 green.
  - [x] GREEN: `git mv` ProfileUpdatePage → `src/pages/profile/ProfilePage.tsx` (history kept), MUI Tabs, CompanyAutocomplete (public Registration variant) + companyId pattern validation, mounted at `/profile` under plain `ProtectedRoute`; `/speaker-portal/profile` (+`/:eventCode`) → `<Navigate to="/profile" replace />`; 5 nav links updated (PublicNavigation ×2, SpeakerDashboardPage, InvitationResponsePage, ContentSubmissionPage); `userAccountApi.ts` allowlist + types extended with `termsAccepted`/`termsAcceptedAt`; AuthContext `canAccess` grants `/profile` to organizer + attendee (was speaker/partner-only — would have bounced).
- [x] **Task 6 — onboarding gate (AC: 6, 7)** — *frontend, TDD*
  - [x] RED: ProtectedRoute tests — consent-less user redirected to `/profile?onboarding=1` from any protected path; consented user passes; fail-open on unknown consent state; `/profile` itself never loops; fresh-federated-user flow test (AC7 — AuthCallbackPage unchanged, gate is the single redirect point) — 25/25 green.
  - [x] GREEN: `AuthContext` user mapping carries `termsAcceptedAt` (null on confirmed-absent under non_null serialization, undefined on hydration failure → fail-open); new `refreshUser()` on the context; gate branch in `ProtectedRoute`; onboarding banner + consent-tab preselect + post-save refresh/navigate in ProfilePage.
- [x] **Task 7 — i18n 10 locales (AC: 8)** — 17 `profile.*` keys added to `common.json` in all 10 locales (EN+DE first-class); tests assert EN strings only.
- [x] **Task 8 — Playwright smoke + full verify (AC: 9)** — CUMS full suite BUILD SUCCESSFUL (re-run clean after a concurrent-build Flyway-checksum artifact from the parallel 12-12 session's Gradle run — not a code defect); frontend vitest 5019 passed/0 failed (360 files); type-check + lint clean; infrastructure jest 374/374; Playwright smoke `e2e/organizer/profile-page.spec.ts` authored (@gate) — executes against the deployed env post-deploy (new testids/V17 don't exist on the currently-deployed build). All outputs tee'd to /tmp logs.
- [x] **Task 9 — docs (AC: 10)** — 06b Pattern C + schema row; SSO plan §9 12.11 entry; ADR-010 D5 consent addendum. Same commit as code.

### Review Findings

_Code review 2026-06-04 (bmad-code-review, 3 adversarial layers: Blind Hunter, Edge Case Hunter, Acceptance Auditor). Auditor verdict: all 10 ACs IMPLEMENTED, all 6 Scope Revisions honored._

- [x] [Review][Patch] **(Medium)** Consent gate can re-fire after a SUCCESSFUL save when the post-save refresh transiently fails — `consentMutation.onSuccess` relies solely on `refreshUser()` → `hydrateUserFromDb`, whose catch returns the OLD user (`termsAcceptedAt: null`); `navigate('/dashboard')` then bounces straight back to `/profile?onboarding=1` and nothing re-triggers a refresh until full reload. Fix: lift the gate from the server-authoritative PUT response (`updatedUser.termsAcceptedAt`) in addition to the refresh. [web-frontend/src/pages/profile/ProfilePage.tsx:111-121, web-frontend/src/contexts/AuthContext.tsx:189-195]
- [x] [Review][Patch] **(Medium)** Lambda consent INSERT tests are non-discriminating — `expect(insertCall[1]).toContain(true)` is satisfied by the `pref_email_notifications` param (defaults `true`) regardless of the consent flag; an inverted `isFederatedSignIn()` would not fail the native test. Fix: pin the positional index (`$8` → `insertCall[1][7]`), same for the federated `toContain(false)` and the link-UPDATE branch tests. [infrastructure/test/unit/lambda/post-confirmation.test.ts:1005,1023]
- [x] [Review][Patch] **(Low)** Stale javadoc on `replayV17Backfill()` — claims the `terms_accepted_at IS NULL` guard is test-added ("which the real migration satisfies trivially"); the guard IS in V17 itself and the replay is byte-identical as-is. One-line comment fix. [services/company-user-management-service/src/test/java/ch/batbern/companyuser/migration/TermsAcceptedAtMigrationTest.java:118-120]
- [x] [Review][Defer] **(Medium)** Company association can never be cleared via /profile — `companyId: companyId || undefined` drops the empty string and the backend PUT only writes `companyId` when non-null, so a set company is permanent through this form. [web-frontend/src/pages/profile/ProfilePage.tsx:194, UserService.java] — deferred, pre-existing backend PUT semantics (no clear-field contract); clearing was not in story scope ("optionally set my company")
- [x] [Review][Defer] **(Low)** Latent `/profile↔/dashboard` redirect loop for an authenticated user with an empty roles array — consent gate sends to `/profile`, role check bounces to `/dashboard`, gate fires again. Unreachable today (JIT interceptor + post-confirmation Lambda both always assign ATTENDEE). [web-frontend/src/components/auth/ProtectedRoute/ProtectedRoute.tsx:49-62] — deferred, pre-existing role-bounce pattern; roleless state not producible by current provisioning

_All 3 patches applied + verified 2026-06-04: P1 `refreshUser(overrides?)` + server-authoritative `termsAcceptedAt` from the PUT response (FE vitest 55/55 affected incl. 2 new AuthContext tests, tsc clean); P2 positional consent-flag assertions `insertCall[1][7]` / `updateCall[1][5]` (Lambda 40/40); P3 javadoc (TermsAcceptedAtMigrationTest 3/3; the lone checkstyle error during verification is 12-12's in-flight `FederatedAvatarImportServiceTest`, parallel session, not this story)._

_Dismissed (9): GET-path drops `termsAcceptedAt` (false positive — `getUserProfile` spreads `...userData`); company name-vs-ID conflation (false positive — `company.name` IS the ADR-003 meaningful ID, ≤12 alphanumeric; dropdown picks are valid, free-text mirrors the server-side pattern validation); `identities` `'[]'` string-length misclassification (shape not producible by Cognito; detection is per-spec "attribute present"); newsletter-toggle silent failure (byte-for-byte matches the UserSettingsTab precedent); consented user at `/profile?onboarding=1` (cosmetic, notice correctly hidden); cutoff-window re-consent (documented spec tradeoff); `refreshUser` closure capture (no bug, network is the freshness source); Pattern-N pre-cutoff backfill + FE/CUMS deploy-ordering (both explicitly documented tradeoffs in story + commit + 06b)._

## Dev Notes

### Why the data model looks like this
- `terms_accepted_at` as nullable timestamp (not boolean) doubles as audit record + gate flag. Write-once server-side prevents consent forgery/revocation via API.
- Newsletter consent lives EXCLUSIVELY in EMS `newsletter_subscribers` (Story 10.7) — see Scope Revision #1. `pref_email_notifications` governs transactional notification emails and is untouched.
- ~~Federated detection for backfill via `LIKE 'google_%'`~~ **VERIFIED WRONG 2026-06-04 (live DB):** `cognito_user_id` stores the sub UUID for all users (post-confirmation stores `attributes.sub`; JIT stores `jwt.getSubject()`). Discriminator is the SSO go-live `created_at` cutoff `2026-06-04 16:00:00+00` (first federated row 16:04:51Z; only 2 federated rows exist, both test identities).
- **Accepted tradeoff (confirmed):** organizer-provisioned speakers (Pattern N `adminCreateUserSilently`) created BEFORE the cutoff never saw the ToS checkbox but get backfilled as consented. Gating them retroactively would lock established speakers out of active workflows. Organizer-provisioned users created AFTER this ships have `terms_accepted_at NULL` → they see the gate on first login (real consent — desirable).

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

Claude Opus 4.8 (1M context) — `claude-opus-4-8[1m]`, via bmad-dev-story, 2026-06-04.

### Implementation Plan / Key Decisions

- **Scope revision applied before any code** (see "SCOPE REVISION" block in the story intro;
  all confirmed by Nissim 2026-06-04): no `newsletter_opt_in` column (reuse EMS
  `newsletter_subscribers` self-service), consent as a top-level column (not inside the
  `preferences` embeddable), backfill discriminator = SSO go-live `created_at` cutoff
  (verified live: `cognito_user_id` is the sub UUID for ALL users; `LIKE 'google_%'`
  matches ZERO rows), native registration newsletter checkbox wired to the public
  `POST /newsletter/subscribe` (was silently dropped), Lambda consent covers the
  link-historical-participant UPDATE branch too.
- **Federated detection in the Lambda**: `identities` user attribute (primary, set by
  Cognito exclusively for external-provider users) + `Google_` username-prefix fallback
  (case-insensitive — live pool shows `Google_<sub>`, capital G).
- **non_null serialization nuance**: CUMS serializes with
  `default-property-inclusion: non_null`, so a null `termsAcceptedAt` is OMITTED from
  `/users/me`. The frontend therefore maps success+absent → `null` (gate fires) and keeps
  `undefined` for hydration FAILURE (gate fails open — never lock users out on a transient
  /users/me error).
- **`canAccess` fix**: `/profile` was only in the speaker/partner path lists; added to
  organizer + attendee or the gate target itself would have bounced those roles.
- **Deploy-window caveat (documented in 06b Pattern C)**: a new frontend against an OLD
  CUMS reads absent→null and would gate everyone until the backend rolls (minutes, same
  pipeline); consent clicks in that window are absorbed by the old DTO and lost (user
  re-accepts once). Do not ship the frontend bundle alone ahead of CUMS.
- **Future organizer-provisioned users** (post-deploy, Pattern N): consent NULL → they see
  the gate on first login and give real consent (improvement over the backfill tradeoff).

### Debug Log References

- /tmp/cums-t1-red.log, /tmp/cums-t1-green.log — V17 migration test RED→GREEN
- /tmp/cums-t3-red.log, /tmp/cums-t3-green.log — write-once consent ITs RED→GREEN
- /tmp/lambda-red.log, /tmp/lambda-green.log — post-confirmation 4 new tests RED→GREEN (40/40)
- /tmp/usereg-test.log — useRegistration 11/11 (3 new newsletter tests)
- /tmp/profilepage-test2.log — ProfilePage 10/10
- /tmp/protectedroute-test.log — ProtectedRoute 25/25 (5 new gate tests)
- /tmp/auth-regression.log — auth-related suites 228 passed / 2 skipped
- /tmp/fe-typecheck.log, /tmp/fe-lint.log — clean
- /tmp/fe-vitest-full.log, /tmp/cums-full.log — full suites (see Completion Notes)

### Completion Notes List

- AC1 ✅ V17 `terms_accepted_at TIMESTAMPTZ NULL` + cutoff backfill (`created_at <
  '2026-06-04 16:00:00+00' AND cognito_user_id IS NOT NULL AND terms_accepted_at IS NULL`);
  `TermsAcceptedAtMigrationTest` (3 ITs) verifies column + replayed-predicate semantics
  (pre-cutoff native backfilled to created_at; post-cutoff + anonymous stay NULL; existing
  consent never moved).
- AC2 ✅ `post-confirmation.ts`: `isFederatedSignIn()` + consent stamping in INSERT
  (`CASE WHEN $8 THEN CURRENT_TIMESTAMP END`) and link-UPDATE
  (`COALESCE(terms_accepted_at, CASE WHEN $6 ...)` — write-once) branches; federated leaves
  NULL in both. JIT needs no change (entity default NULL, asserted in
  `JITProvisioningIntegrationTest`). Native newsletter checkbox now subscribes via
  `newsletterService.subscribe` in `useRegistration` (best-effort, non-blocking).
- AC3 ✅ `users-api.openapi.yml`: `UserResponse.termsAcceptedAt` (nullable date-time) +
  `UpdateUserRequest.termsAccepted` (write-once documented); frontend types regenerated +
  committed; backend DTOs regenerate on build. NO newsletterOptIn anywhere.
- AC4 ✅ `UserService.updateCurrentUser`: write-once server-clock consent;
  `ConsentWriteOnceIntegrationTest` (3 ITs): first-accept-sets-once,
  second-accept-no-move, false/absent-never-clears (incl. false-on-consent-less no-op).
  Deliberately NOT added to the admin `updateUserByUsername` path (no consent by proxy).
- AC5 ✅ `ProfilePage` (git-mv'd from speaker-portal `ProfileUpdatePage`, history kept) at
  `/profile` behind plain `ProtectedRoute`; MUI Tabs (Profile | Consent & Newsletter);
  company via the public-Registration `CompanyAutocomplete` + `^[a-zA-Z0-9]{1,12}$`
  client validation; consent tab states (checkbox+links when NULL / read-only accepted-on
  when set); newsletter switch via `useMySubscription`/`usePatchMySubscription` (EMS
  creates-or-reactivates, works for fresh federated users); old routes redirect; 5 nav
  links updated; `userAccountApi` allowlist + `User.termsAcceptedAt` type extended.
- AC6 ✅ `AuthContext`: `termsAcceptedAt` hydrated (null=confirmed-absent /
  undefined=unknown→fail-open) + new `refreshUser()`; `ProtectedRoute` gate redirects
  consent-less users from ANY protected path (except /profile, /logout) to
  `/profile?onboarding=1`; onboarding notice + consent-tab preselect + post-save
  refresh→navigate(/dashboard).
- AC7 ✅ `AuthCallbackPage` unchanged — gate is the single redirect point; RTL test
  `should_redirectToOnboardingProfile_when_freshFederatedUserHasNoConsent`.
- AC8 ✅ 17 `profile.*` keys in `common.json` across all 10 locales (EN+DE first-class);
  tests assert EN strings only.
- AC9 ✅ Tests across layers (see Debug Log); Playwright smoke
  `e2e/organizer/profile-page.spec.ts` (@gate, read-only, testid-only) — runs against the
  deployed environment AFTER this story deploys (the new testids/V17 don't exist on the
  currently-deployed build; pre-deploy local run not possible without booting the full
  native stack).
- AC10 ✅ Docs same-commit: 06b Pattern C (+ user_profiles schema row), SSO plan §9
  follow-up entry, ADR-010 D5 consent addendum.

### File List

**CUMS (backend)**
- services/company-user-management-service/src/main/resources/db/migration/V17__add_terms_accepted_at_to_user_profiles.sql (new)
- services/company-user-management-service/src/main/java/ch/batbern/companyuser/domain/User.java
- services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/UserService.java
- services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/UserResponseMapper.java
- services/company-user-management-service/src/test/java/ch/batbern/companyuser/migration/TermsAcceptedAtMigrationTest.java (new)
- services/company-user-management-service/src/test/java/ch/batbern/companyuser/controller/ConsentWriteOnceIntegrationTest.java (new)
- services/company-user-management-service/src/test/java/ch/batbern/companyuser/integration/JITProvisioningIntegrationTest.java

**Infrastructure (Lambda)**
- infrastructure/lib/lambda/triggers/post-confirmation.ts
- infrastructure/test/unit/lambda/post-confirmation.test.ts

**API contract**
- docs/api/users-api.openapi.yml
- web-frontend/src/types/generated/user-api.types.ts (regenerated)

**Frontend**
- web-frontend/src/pages/profile/ProfilePage.tsx (git-mv from src/pages/speaker-portal/ProfileUpdatePage.tsx, rewritten)
- web-frontend/src/pages/profile/__tests__/ProfilePage.test.tsx (git-mv from src/pages/speaker-portal/__tests__/ProfileUpdatePage.test.tsx, rewritten)
- web-frontend/src/App.tsx
- web-frontend/src/components/auth/ProtectedRoute/ProtectedRoute.tsx
- web-frontend/src/components/auth/ProtectedRoute/ProtectedRoute.test.tsx
- web-frontend/src/contexts/AuthContext.tsx
- web-frontend/src/types/auth.ts
- web-frontend/src/types/userAccount.types.ts
- web-frontend/src/services/api/userAccountApi.ts
- web-frontend/src/hooks/useRegistration/useRegistration.ts
- web-frontend/src/hooks/useRegistration/useRegistration.test.ts
- web-frontend/src/components/public/Navigation/PublicNavigation.tsx
- web-frontend/src/pages/speaker-portal/SpeakerDashboardPage.tsx
- web-frontend/src/pages/speaker-portal/InvitationResponsePage.tsx
- web-frontend/src/pages/speaker-portal/ContentSubmissionPage.tsx
- web-frontend/public/locales/{de,en,fr,it,rm,es,fi,nl,ja,gsw-BE}/common.json
- web-frontend/e2e/organizer/profile-page.spec.ts (new)

**Docs**
- docs/architecture/06b-user-lifecycle-sync.md
- docs/architecture/ADR-010-federated-identity-via-cognito.md
- docs/plans/sso-oidc-federation.md

### Change Log

- 2026-06-04 — Story 12.11 implemented (Claude Opus 4.8 via bmad-dev-story). SCOPE REVISED
  pre-implementation with Nissim: newsletter stays in EMS `newsletter_subscribers` (no
  user_profiles column); consent = top-level `terms_accepted_at`; backfill discriminator
  corrected to SSO go-live created_at cutoff after live-DB verification proved
  `cognito_user_id` is the sub UUID for all users (story's original `LIKE 'google_%'`
  would have matched nothing → federated users wrongly backfilled as consented); native
  registration newsletter checkbox (silently dropped since ever) wired to public
  subscribe endpoint. All 10 ACs implemented TDD red-green; story file updated in place
  (per Nissim "update the story too").
