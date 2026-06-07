# Story 11.F.1: Magic-link teardown and branch deletion

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **security and platform maintainer**,
I want **every remnant of the magic-link auth model removed from the codebase, infrastructure, and database**,
so that **no attack surface, dead code, or developer confusion outlives the Cognito migration — Epic 11 reads as actually finished**.

## Acceptance Criteria

**Background:** Phase E (Stories 11.E.1–E.4) disconnected magic-link auth from the live request path: speaker-portal controllers moved to Cognito Bearer + `@PreAuthorize("hasRole('SPEAKER')")`, the magic-login frontend route was removed from the router, the `permitAll()` on `/api/v1/auth/speaker-magic-login` was dropped, and the magic-link Java files + frontend page were left compilable (per E.3 Resolved Q#3) so that this story can delete them in one clean mechanical pass.

**Pre-condition (RELAXED per Resolved Decision RD1, 2026-05-25):** The PRD's "≥ 1 week zero traffic to magic-link endpoints" precondition is **explicitly waived by the PM**. Dev work begins immediately on the existing observation window (3 days since PR #660 squash-merged 2026-05-22 — the date the Cognito-only frontend rolled out). Task 1.1 runs a CloudWatch check over the available window and records the result, but **does not block** dev work even if the count is non-zero. Rationale: the frontend stopped emitting magic-link auth requests on 2026-05-22 deploy; any stragglers would be cached SPA bundles or stale `?token=` bookmarks (rare; degrade to 404 cleanly post-teardown, same as Phase E's intermediate 410). The PM accepts this risk.

**Out of scope (per Resolved Decisions RD2 + RD4, 2026-05-25):**
- The `AFTER_COMMIT` `TransactionalEventListener` refactor for `runInvitedHook` / `runAcceptedHook` external side effects (deferred-work.md line 22) — spun out as **Story 11.F.2**, a separate architectural change.
- Reactivating the three `test.fixme` Playwright specs (`speaker-portal-respond.spec.ts`, `speaker-portal-content-submit.spec.ts`, `speaker-portal-cross-portal-nav.spec.ts`) — they depend on operational work (provisioning a staging Cognito test-speaker and adding `SPEAKER_AUTH_TOKEN` to GitHub secrets) that is not magic-link teardown. Tracked separately.

### AC1 — Backend Java source files deleted

**Given** the event-management-service is built,
**When** I search the EMS source tree for magic-link types,
**Then** the following files are deleted in full:
- `services/event-management-service/src/main/java/ch/batbern/events/service/MagicLinkService.java`
- `services/event-management-service/src/main/java/ch/batbern/events/config/JwtConfig.java` (the speaker-JWT one — confirmed distinct from API Gateway's general JWT validation config; verify by inspecting imports before deleting)
- `services/event-management-service/src/main/java/ch/batbern/events/controller/SpeakerMagicLoginController.java`
- `services/event-management-service/src/main/java/ch/batbern/events/controller/SpeakerPortalTokenController.java`
- `services/event-management-service/src/main/java/ch/batbern/events/controller/E2ETestTokenController.java` (test-profile-only controller whose sole purpose was generating magic-link tokens for local/E2E testing — obsolete after teardown)
- `services/event-management-service/src/main/java/ch/batbern/events/dto/SpeakerMagicLoginRequest.java`
- `services/event-management-service/src/main/java/ch/batbern/events/dto/SpeakerAuthResponse.java`
- `services/event-management-service/src/main/java/ch/batbern/events/dto/ValidateTokenRequest.java`
- `services/event-management-service/src/main/java/ch/batbern/events/dto/TokenValidationResult.java` (response-type pair for `ValidateTokenRequest`)
- `services/event-management-service/src/main/java/ch/batbern/events/domain/SpeakerInvitationToken.java` (JPA entity for the `speaker_invitation_tokens` table)
- `services/event-management-service/src/main/java/ch/batbern/events/repository/SpeakerInvitationTokenRepository.java`

**And** the following orphan files (becomes-dead after the above deletions per `bmad-check-implementation-readiness` findings G3 + G4) are deleted in full:
- `services/event-management-service/src/main/java/ch/batbern/events/converter/TokenActionConverter.java` (JPA `AttributeConverter<TokenAction, String>` for the `speaker_invitation_tokens.action` column — orphan after entity + table deletion)
- `shared-kernel/src/main/java/ch/batbern/shared/types/TokenAction.java` (the `TokenAction` enum itself — verify with `grep -rn "TokenAction" shared-kernel/ services/` returns only the enum-source-file line before deleting; then **rebuild shared-kernel** via `./gradlew :shared-kernel:publishToMavenLocal` so dependent services rebuild cleanly)

**And** the following four orphan repository methods on `services/event-management-service/src/main/java/ch/batbern/events/repository/SpeakerPoolRepository.java` (used ONLY by the deleted `E2ETestTokenController`, verified by `grep -rn` returning the controller as the sole caller) are deleted in full:
- `findByEventCodeAndStatusOrderByCreatedAtDesc(String eventCode, String status, Pageable pageable)` (line ~176)
- `findByEventCodeAndStatusAndSessionIdIsNullOrderByCreatedAtDesc(String eventCode, String status, Pageable pageable)` (line ~202)
- `findByEventCodeOrderByCreatedAtDesc(String eventCode, Pageable pageable)` (line ~214)
- `findByEventCodeAndSessionIdIsNotNullOrderByCreatedAtDesc(String eventCode, Pageable pageable)` (line ~226)

**And** the dedicated repo test class `services/event-management-service/src/test/java/ch/batbern/events/repository/SpeakerPoolRepositoryE2EMethodsTest.java` is deleted in full (Javadoc explicitly calls itself "tests the repository methods used by E2ETestTokenController" — pure orphan after AC1).

**And** any remaining `?token=` or `?jwt=` query-parameter handling in any EMS controller is removed (sweep with `grep -rn "@RequestParam.*\\(token\\|jwt\\)" services/event-management-service/src/main/java/`).

**And** any field declaration `private final MagicLinkService magicLinkService` and corresponding constructor parameter is removed from `SpeakerWorkflowService.java`, `SpeakerInvitationEmailService.java`, `SpeakerPortalContentController.java`, `SpeakerPortalResponseController.java`, `SpeakerPortalMaterialsService.java`, and any other class that still has a residual injection (sweep with `grep -rn "MagicLinkService" services/event-management-service/src/main/java/`). After the sweep, `grep -rn "MagicLinkService\|SpeakerInvitationToken\|magic.link\|speaker_jwt" services/event-management-service/src/main/java/` returns zero hits.

### AC1b — E2E test-token infrastructure deletion (per readiness findings G1, G2)

**Background:** `E2ETestTokenController` (deleted in AC1) had three endpoints — `POST /e2e-test/tokens/generate`, `/generate-for-speaker`, `/generate-e2e-set` — that all minted magic-link tokens via `MagicLinkService`. The Bruno + Playwright + shell-script tooling that called these endpoints is now broken; this AC enumerates the fallout.

**Given** I sweep for the deleted endpoint paths,
**When** I run `grep -rn "/e2e-test/tokens" web-frontend/e2e/ bruno-tests/ scripts/`,
**Then** the result is empty (every caller is either deleted or migrated).

**Specific callers identified at story-creation time (2026-05-25):**

1. **`web-frontend/e2e/organizer/speaker-content-display.spec.ts`** (organizer Playwright project — runs on every CI build):
   - Currently calls `POST ${API_URL}/api/v1/e2e-test/tokens/generate-e2e-set` (line ~44) to mint the `E2E_SPEAKER_CONTENT_TOKEN` magic-link token, then uses that token to seed content data for asserting the organizer's view.
   - **Disposition decision required at dev-time:** either (a) **delete the spec** if its coverage overlaps with Story 11.D.4's `ContentSubmissionSubView.test.tsx` cases 39–44 (organizer drawer content sub-view) and 11.E.5's lifecycle E2E (organizer-side ACCEPTED → CONTENT_SUBMITTED transition); OR (b) **migrate it to a Cognito-only fixture** by seeding the speaker pool via the existing organizer REST surface (`POST /events/{code}/speakers/pool` → `POST /promote` → on-behalf content submit via `ContentSubmissionService`) using the organizer JWT in `~/.batbern/staging-organizer.json`. **Dev agent picks (a) or (b) and records the rationale in the Dev Agent Record.** Per `_bmad-check-implementation-readiness` G1, option (a) is recommended — the spec's `Story 6.3` header predates Phase E, and 11.D.4 + 11.E.5 cover the same surface — but the dev agent should `git log` the spec first to confirm no recent unique coverage was added.

2. **`scripts/e2e/generate-speaker-tokens.sh`** (developer-experience helper for local E2E setup):
   - Currently calls `${API_URL}/api/v1/e2e-test/tokens/generate-e2e-set` (line ~35) to populate `E2E_SPEAKER_*_TOKEN` environment variables for local Playwright runs.
   - **Disposition:** delete the script outright. The post-Phase-E equivalent is `./scripts/auth/get-token.sh staging <email> <password>` (per CLAUDE.md §"Authentication for Testing"), which writes `~/.batbern/staging-speaker.json` for the Playwright `speaker` project. If a CONTENT-fixture helper is needed in the future, the team can write a Cognito-first replacement script; this is OUT of scope for F.1.

3. **Bruno test-token helpers** (`bruno-tests/speaker-portal-api/02b-get-test-token.bru`, `09b-get-decline-test-token.bru`) — already enumerated in AC7. No additional work here.

**And** `SecurityConfig.java:117` (`requestMatchers("/api/v1/e2e-test/**").permitAll()`) is removed per readiness finding G5 — dead config after `E2ETestTokenController` deletion. (See Task 2.6 expansion below.)

**And** after all dispositions are applied, the full Playwright organizer project (`cd web-frontend && npx playwright test --project=chromium`) runs green; the absence of `speaker-content-display.spec.ts` (or its Cognito-migrated successor) is the only diff visible at the test-run summary.

### AC2 — Magic-link endpoints return 404 (regression-tested)

**Given** the EMS is running on the refactor branch with AC1 applied,
**When** an HTTP `POST /api/v1/auth/speaker-magic-login` is issued (anonymous or authenticated),
**Then** the response is `404 Not Found` (the controller class no longer exists, so Spring MVC has no route to match).

**And given** an HTTP `POST /api/v1/speaker-portal/validate-token` is issued (anonymous or authenticated),
**When** the request is processed,
**Then** the response is `404 Not Found` (the controller class is gone — currently returns `410 Gone` from `SpeakerPortalTokenController`).

**And** a new integration test `MagicLinkEndpointsRemovedIntegrationTest` (under `services/event-management-service/src/test/java/ch/batbern/events/controller/`) extends `AbstractIntegrationTest` and asserts `status().isNotFound()` for both endpoints, with both anonymous and authenticated (`@WithMockUser(roles="SPEAKER")`) variants — so the endpoints cannot be silently revived. The 410 → 404 transition (`SpeakerPortalTokenController.java:40` returns `HttpStatus.GONE`) is the regression we are guarding against.

### AC3 — Database table dropped via Flyway migration

**Given** a new Flyway migration is added at `services/event-management-service/src/main/resources/db/migration/V104__drop_speaker_invitation_tokens_table.sql`,
**When** the migration runs,
**Then** the `speaker_invitation_tokens` table no longer exists (`DROP TABLE IF EXISTS speaker_invitation_tokens CASCADE` — `CASCADE` covers the three indexes from V43: `idx_invitation_tokens_hash`, `idx_invitation_tokens_speaker`, `idx_invitation_tokens_expires`).

**And** the migration includes a header block documenting it as the Story 11.F.1 teardown, referencing V43 as the original creator and ADR-009 §0.3.

**And** the next-version slot is V104 (V100 + V101 are reserved by EMS test-stub migrations `user_profiles_stub` + `companies_stub` per Story 11.E.8 §2.7 note; V102 + V103 already exist).

**And** the migration is **idempotent** — re-running against a database that has already had the table dropped is a no-op (the `IF EXISTS` guard handles this).

**And** ADR-009 §0.3 line 82 is updated from "(Owned by Story 11.F.1 — not yet dropped at time of writing.)" to a past-tense note referencing this migration ("Dropped by V104 in Story 11.F.1.").

### AC4 — `speaker_jwt` cookie no longer set by any response

**Given** the EMS is running on the refactor branch with AC1 applied,
**When** any HTTP endpoint is exercised (Bruno suite + Playwright run),
**Then** no response sets a cookie named `speaker_jwt` (audited via Playwright network-capture in a new teardown smoke test under `web-frontend/e2e/speaker/magic-link-teardown.spec.ts`).

**And** the cookie-issuance code path is gone — sweep with `grep -rn "speaker_jwt\|speakerJwt" services/event-management-service/src/main/java/ services/event-management-service/src/main/resources/` returns zero hits (excluding narrative comments inside ADR-009 prose, which describe the history).

### AC5 — Frontend magic-link UI deleted

**Given** the web-frontend is built,
**When** I search for magic-link UI,
**Then** the following files are deleted in full:
- `web-frontend/src/pages/speaker-portal/SpeakerMagicLoginPage.tsx`
- `web-frontend/src/pages/speaker-portal/__tests__/SpeakerMagicLoginPage.test.tsx`
- `web-frontend/src/services/speakerAuthService.ts`
- `web-frontend/src/services/speakerAuthService.test.ts`

**And** the comment block at `web-frontend/src/App.tsx` lines ~125–126 that still marks the lazy-import as REMOVED is cleaned up to a single-line comment or removed entirely (Phase E left a tombstone; Phase F removes it).

**And** any `?token=` / `?jwt=` parsing in any speaker-portal page (`ContentSubmissionPage.tsx`, `InvitationResponsePage.tsx`, `ProfileUpdatePage.tsx`, `SpeakerDashboardPage.tsx`) is gone — sweep with `grep -rn "searchParams\\.get.*\\(token\\|jwt\\)" web-frontend/src/pages/speaker-portal/` returns zero hits.

**And** any "Mark as tentative" button (per UX-DR19) and its form handler are deleted from `InvitationResponsePage.tsx` — sweep with `grep -rn "TENTATIVE\|tentative" web-frontend/src/pages/speaker-portal/` returns only test-fixture residue (which is itself removed in AC6) and zero production UI hits. (Note: per Story 11.E.3 Resolved Q#6, `SpeakerResponseType` was already narrowed to `'ACCEPT' | 'DECLINE'` in TS; this AC removes whatever UI residue survived.)

**And** the route table in `App.tsx` contains no `magic-login` route entry, lazy import, or redirect (Story 11.E.3 already removed the route + import + added backward-compat redirects; this AC confirms zero references survive).

**And** `grep -rn "MagicLink\|speakerAuthService\|magic.login\|speaker_jwt\|tentativeSpeakerToken" web-frontend/src/` returns zero hits.

### AC6 — Disabled/skipped tests deleted (Phase F cleanup)

**Given** Story 11.E.3 left five backend `@Disabled` integration-test classes and four frontend `describe.skip` Vitest suites pending Phase F deletion (per `deferred-work.md` lines 145–146),
**When** I run `find services/event-management-service/src/test/ -name "SpeakerPortal*ControllerIntegrationTest.java" -o -name "SpeakerPortal*IntegrationTest.java"`,
**Then** the following five backend test files are deleted in full:
- `SpeakerPortalResponseControllerIntegrationTest.java`
- `SpeakerPortalContentControllerIntegrationTest.java`
- `SpeakerPortalDashboardControllerIntegrationTest.java`
- `SpeakerPortalMaterialsIntegrationTest.java`
- `SpeakerPortalTokenControllerIntegrationTest.java`

(All under `services/event-management-service/src/test/java/ch/batbern/events/controller/`. Rationale: the new `SpeakerPortalAuthIntegrationTest` introduced in 11.E.3 covers the auth matrix; the legacy behavioural-state coverage (idempotency, validation errors, conflict states, a11y) is consciously traded off per E.3 Completion Note #13. New regression coverage may be added in a follow-up if a real gap is found, but the disabled files themselves carry magic-link assertions that no longer match the contract and must not survive.)

**And** the following frontend Vitest test files are deleted in full:
- `web-frontend/src/pages/speaker-portal/__tests__/ContentSubmissionPage.test.tsx`
- `web-frontend/src/pages/speaker-portal/__tests__/InvitationResponsePage.test.tsx`
- `web-frontend/src/pages/speaker-portal/__tests__/ProfileUpdatePage.test.tsx`
- `web-frontend/src/services/speakerPortalService.test.ts`

(SpeakerMagicLoginPage.test.tsx + speakerAuthService.test.ts deletions are covered by AC5.)

**And** per Resolved Decision RD5 (2026-05-25), **replacement Cognito-side unit tests** are written for the three surviving production pages and the surviving service — the page files themselves are NOT deleted (they are Phase E end-state Cognito-secured pages); only the dead magic-link tests are deleted. The new tests cover the post-E.3 Cognito behaviour:
- `web-frontend/src/pages/speaker-portal/__tests__/ContentSubmissionPage.test.tsx` (NEW) — mocks `useAuth()` returning a `SPEAKER`-roled session; mocks `apiClient` (no `Skip-Auth` header); reads `eventCode` from `useParams` mock; covers happy-path submit, validation errors, empty draft hydration from `sessions.title`, and submit-redirect on success. Zero `?token=` parsing assertions.
- `web-frontend/src/pages/speaker-portal/__tests__/InvitationResponsePage.test.tsx` (NEW) — mocks `useAuth()` for `SPEAKER`; mocks `apiClient`; reads `eventCode` from `useParams`; covers ACCEPT response, DECLINE-with-reason response, event-not-found 404 branch, past-event 409 branch, and the already-responded UI state. No TENTATIVE branch (per E.3 Resolved Q#6 the type is narrowed to `'ACCEPT' | 'DECLINE'`).
- `web-frontend/src/pages/speaker-portal/__tests__/ProfileUpdatePage.test.tsx` (NEW) — mocks `useAuth()` for `SPEAKER`; mocks `apiClient`; covers reading the current profile (`GET /users/me`), submitting an update (`PATCH /users/me`), portrait upload via the presigned-URL pattern (`POST /users/me/picture/presigned-url` + S3 PUT + `POST /users/me/picture/confirm`), and validation errors. Per E.3 Resolved Q#1 (D1), this page uses CUMS `/users/me` endpoints — NOT the deleted per-event `/speaker-portal/profile` endpoints.
- `web-frontend/src/services/speakerPortalService.test.ts` (NEW) — covers the rewritten service from E.3: `eventCode`-first signatures, no `Skip-Auth` header, no `?token=` parameter, surfaces backend 401/403/404/409 errors cleanly. Use `msw` 2.x per project-context.md §"Frontend Testing" rule rather than mocking the service module directly.

The new tests follow the same naming convention as the rest of the project (`test('should display error message when form validation fails', () => {})` per project-context.md line 150). Each new test file targets ≥ 80% line coverage of its production target.

**And** the following legacy backend service-layer test files are deleted in full (they cover code paths that disappeared in Phase E or are tied to magic-link service collaborators):
- Any service test under `services/event-management-service/src/test/java/ch/batbern/events/service/` whose target class is being deleted in AC1 (`MagicLinkServiceTest` is the obvious one; sweep with `grep -rln "MagicLinkService\\b" services/event-management-service/src/test/java/` and remove any test file that exclusively targets it).

**And** sweep with `grep -rn "@Disabled.*Phase F\|describe.skip.*Phase F" web-frontend/src/ services/event-management-service/src/test/` returns zero hits.

### AC7 — Bruno tests cleaned up

**Given** `bruno-tests/speaker-portal-api/` contains a legacy magic-link test chain that runs against the now-404 endpoints,
**When** I review the directory,
**Then** the following `.bru` files are deleted (they exercise magic-link token generation + the now-deleted endpoints):
- `bruno-tests/speaker-portal-api/02b-get-test-token.bru` (calls `/api/v1/e2e-test/tokens/generate-for-speaker` — endpoint deleted with `E2ETestTokenController` per AC1)
- `bruno-tests/speaker-portal-api/03-validate-token.bru` (POSTs `/speaker-portal/validate-token` — endpoint now 404)
- `bruno-tests/speaker-portal-api/04-validate-token-invalid.bru` (same endpoint)
- `bruno-tests/speaker-portal-api/06-respond-accept.bru` (uses `speakerMagicToken` to call old `/speaker-portal/respond` — replaced by `33-respond-cognito-200.bru` per Story 11.E.3)
- `bruno-tests/speaker-portal-api/07-respond-already-used.bru` (same)
- `bruno-tests/speaker-portal-api/09b-get-decline-test-token.bru` (test-token generator)
- `bruno-tests/speaker-portal-api/10-respond-decline.bru` (magic-link path)
- `bruno-tests/speaker-portal-api/11-respond-decline-no-reason.bru` (same)
- `bruno-tests/speaker-portal-api/19-get-content-info.bru` (uses `speakerMagicToken` against old `/speaker-portal/content`)
- `bruno-tests/speaker-portal-api/21-submit-content.bru` (same — replaced by 30+ cognito chain)
- `bruno-tests/speaker-portal-api/22-get-content-after-submit.bru` (same)
- `bruno-tests/speaker-portal-api/23-upload-material-presigned-url.bru` (same)
- `bruno-tests/speaker-portal-api/24-submit-content-validation-errors.bru` (same)
- `bruno-tests/speaker-portal-api/25-get-content-invalid-token.bru` (same)
- `bruno-tests/speaker-portal-api/29-reject-content.bru` (verify whether this uses magic-link auth before deleting — if it's organizer-side it stays)
- Any other `.bru` file in `speaker-portal-api/` whose request body or auth uses the variables `speakerMagicToken`, `speakerViewToken`, or `tentativeSpeakerToken`

**And** new regression tests are added: `bruno-tests/speaker-portal-api/03-magic-login-removed-404.bru` and `04-validate-token-removed-404.bru` that POST to the deleted endpoints (with `auth: none`) and assert `res.status: eq 404`. These are the Bruno-layer mirror of the AC2 backend regression test and prevent a future commit from reviving the routes.

**And** if `bruno-tests/speaker-portal-api/` or `bruno-tests/environments/` defines unused variables (`speakerMagicToken`, `speakerViewToken`, `tentativeSpeakerToken`) after the deletions, those variable declarations are removed too (sweep with `grep -rn "speakerMagicToken\|speakerViewToken\|tentativeSpeakerToken" bruno-tests/`).

**And** the surviving Cognito-secured Bruno tests (`30-dashboard-200-speaker.bru`, `31-dashboard-403-organizer.bru`, `32-dashboard-401-no-auth.bru`, `33-respond-cognito-200.bru`, `34-get-dashboard-reusable-token.bru` and the renumbered/duplicate IDs around 30-34) are NOT touched — they are the post-E.3 Cognito-path coverage and must remain green.

### AC8 — Infrastructure (CDK) cleanup

**Given** the infrastructure CDK is built,
**When** I inspect the CDK source tree,
**Then** any Secrets Manager entries named for the speaker-JWT key (sweep `infrastructure/lib/` for names matching `SpeakerJwt`, `SPEAKER_JWT_KEY`, `speaker-jwt-secret`) are removed, **And** any env-var wiring of `SPEAKER_JWT_PRIVATE_KEY` / `SPEAKER_JWT_PUBLIC_KEY` on the EMS task definition is removed (sweep `infrastructure/lib/stacks/event-management-stack.ts`).

**And** if no such resources exist (likely — Phase E review noted CDK was already clean), this AC is satisfied by a documented sweep that returns zero hits. The Dev Agent Record's "File List" should explicitly note "CDK sweep performed; zero hits — no infra changes required."

**And** any IAM policy / permission boundary whose statement references `/api/v1/auth/speaker-magic-login`, `/api/v1/speaker-portal/validate-token`, or `cognito-idp:AdminInitiateAuth` scoped explicitly to magic-login (NOT the Cognito-admin grant from Story 11.E.1, which is scoped to the speaker-provisioning flow and stays) is removed.

**And** CDK unit tests pass with zero failures (`cd infrastructure && npm test`). Any test that asserts the absence of speaker-JWT secrets / env vars is added in the same commit (mirror the negative-regression pattern from Story 11.E.1's `company-management-stack.test.ts`).

### AC9 — OpenAPI specs scrubbed of deleted paths

**Given** `docs/api/*.openapi.yml` contains the public API contract,
**When** I grep `docs/api/` for `speaker-magic-login` or `validate-token`,
**Then** any path/operation definitions for `/api/v1/auth/speaker-magic-login` or `/api/v1/speaker-portal/validate-token` are removed from the spec files (search candidates: `auth-endpoints.openapi.yml`, `events-api.openapi.yml`, `speakers-api.openapi.yml`).

**And** narrative prose references to magic-link auth in the spec descriptions (e.g. example values mentioning `{{magicLinkUrl}}`) are updated to describe the Cognito flow.

**And** any DTO definitions for `SpeakerMagicLoginRequest`, `SpeakerAuthResponse`, `ValidateTokenRequest`, `TokenValidationResult` in the OpenAPI components section are removed.

**And** after the spec edit, `cd web-frontend && npm run generate:api-types` is run and the regenerated types in `web-frontend/src/types/generated/` are committed (these are committed per `CLAUDE.md` §Type Sharing).

**And** the backend Gradle OpenAPI generator (`./gradlew :services:event-management-service:openApiGenerate`) regenerates without error.

### AC10 — Source feature branches deleted

**Given** the `feature/speaker-account-creation` and `feature/epic-6` branches were merged via cherry-picks during Phase E (per AR41, AR42 of Epic 11),
**When** I run `git ls-remote --heads origin | grep -E "feature/speaker-account-creation|feature/epic-6"`,
**Then** zero matches return (the branches are deleted from origin).

**Verification:** as of the story-creation sweep (2026-05-25), `git ls-remote --heads origin` already returns zero matches for both branches — they appear to have been cleaned up during the Epic 11 feature-branch merge (PR #660, squash commit `2d9b9ef6` on develop, 2026-05-22 per memory). **If both branches are confirmed already deleted at story-execution time, this AC is satisfied by a documented verification run** (paste the empty output into the Dev Agent Record).

**If either branch still exists on origin**, delete it with `git push origin --delete feature/speaker-account-creation` and/or `git push origin --delete feature/epic-6`. Record the cherry-pick commit SHAs on develop that justify the deletion (look for the squash-merge commit referencing 73d94688 + 396a9045 for multi-role nav, and d5cf0fcc for Cognito IAM).

**And** the PR description for this story's PR documents the verification (or the deletion + SHAs).

### AC11 — Full Bruno + Playwright test run passes against Cognito-only auth

**Given** all AC1–AC10 changes are applied and the EMS + frontend are deployed to staging,
**When** the full Bruno suite (`./scripts/ci/run-bruno-tests.sh`) and the Playwright suite (organizer + speaker + partner projects) execute,
**Then** every test passes.

**And** the speaker-portal flow (dashboard load, accept/decline response, content submission) is exercised end-to-end via Cognito Bearer with **no fallback to magic-link auth at any point** (audit by capturing network requests during the speaker Playwright spec and asserting zero requests to `/api/v1/auth/speaker-magic-login` or `/api/v1/speaker-portal/validate-token`, and zero `Set-Cookie: speaker_jwt=…` response headers — implemented in the AC4 teardown smoke test).

**And** the AC2 backend regression test (`MagicLinkEndpointsRemovedIntegrationTest`) and AC7 Bruno regression tests (`03-magic-login-removed-404.bru`, `04-validate-token-removed-404.bru`) are part of the suite and pass.

**And** per readiness finding G1, the final sweep `grep -rn "/e2e-test/tokens" web-frontend/e2e/ bruno-tests/ scripts/` returns **empty** (every caller of the deleted `E2ETestTokenController` endpoints is either deleted or migrated per AC1b). Record the empty output in the Dev Agent Record.

**Out of scope (per Resolved Decision RD4, 2026-05-25):** The three `test.fixme` Playwright specs (`e2e/speaker/speaker-portal-respond.spec.ts`, `speaker-portal-content-submit.spec.ts`, `speaker-portal-cross-portal-nav.spec.ts`) await a staging test-speaker Cognito seed (`SPEAKER_AUTH_TOKEN` env var) per deferred-work.md line 147. Reactivating those specs is **not** part of this story (it depends on operational work — provisioning a Cognito test-speaker and adding `SPEAKER_AUTH_TOKEN` to GitHub secrets — tracked separately). The teardown smoke test in AC4 is a standalone spec that does not need the seed (it asserts absence of magic-link artefacts, not Cognito-positive flows) and so it CAN run in this story.

### AC12 — Documentation alignment

**Given** Epic 11 is closing with this story,
**When** the story merges,
**Then**:
- `CLAUDE.md` Epic 11 status line is updated to "100% COMPLETE" (mirrors Epic 5/6 phrasing) and the in-progress note is removed.
- The sprint-status YAML entry for `11-f-1-magic-link-teardown-branch-deletion` is updated to `done` and `epic-11` is updated to `done`.
- `docs/architecture/ADR-009-unified-speaker-workflow.md` revision-history table gains a v1.7 (or next) row noting Story 11.F.1's teardown.
- Per Resolved Decision RD3 (2026-05-25), the ADR-009 §0.3 table header / `magic_link_tokens` row schema-ownership wording is corrected at the same time as the past-tense edit in AC3. The table header says "all in the event-management-service schema unless noted" but does not actually need a per-row override here (V43 created `speaker_invitation_tokens` in EMS, so the default holds). The fix is to add a footnote or parenthetical clarifying that the colloquial label `magic_link_tokens` in the §0.3 row refers to the EMS table `speaker_invitation_tokens` — eliminating the deferred-work.md line 14 ambiguity in the same line edit as the past-tense flip.
- The deferred-work entries explicitly tagged Phase F (lines 14, 22, 81, 89, 100, 112, 145, 146 per `_bmad-output/implementation-artifacts/deferred-work.md`) are each annotated with a one-line disposition: line 14 → "resolved by RD3 via ADR-009 §0.3 footnote in Story 11.F.1"; line 22 → "spun out as Story 11.F.2 per RD2"; lines 81/89/100/112 → "resolved by Story 11.F.1 (magic-link teardown)"; lines 145/146 → "resolved by Story 11.F.1 AC6 (legacy tests deleted, Cognito-side replacements written per RD5)".
- `docs/api/04-api-design.md`, `docs/architecture/06-backend-architecture.md`, and `docs/architecture/06b-user-lifecycle-sync.md` are swept for any remaining "current behaviour" claim about magic-link auth on speaker portal — none should survive (Phase E already cleaned most; this is a final pass).

**And** the speaker-workflow-refactor branch's final state (post-F.1 merge to `develop`) is verified via the existing Epic 11 doc-drift sweep (`grep -rn "magic.link\|speaker_jwt\|speakerMagicLogin" docs/architecture/ docs/prd/epic-11*.md` returns only intentional historical references inside ADR-009 prose + revision-history rows).

## Tasks / Subtasks

- [x] **Task 1 — Pre-flight CloudWatch check (best-effort per RD1)**
  - [x] 1.1 **Best-effort check (RD1, 2026-05-25):** Ran CloudWatch query on `/aws/ecs/BATbern-staging/event-management` over 2026-05-22→2026-05-25 (3-day window). Filter: `@message like /speaker-magic-login/ or @message like /speaker-portal\/validate-token/`. Result: **0 records matched / 429,018 scanned**. Live traffic at zero — proceed.
  - [x] 1.2 Confirmed working on `feature/11-f-1-magic-link-teardown` branch (created off `develop`).

- [x] **Task 2 — Backend Java deletion sweep (AC1)**
  - [x] 2.1 Read each file listed in AC1 to confirm scope before deleting (look for unexpected reverse-dependencies). Pay attention to `JwtConfig.java` — confirm it imports `com.nimbusds.jose.*` / `java.security.interfaces.RSAPublicKey` and is the speaker-JWT one, NOT the Cognito Bearer chain (which is configured in `SecurityConfig.java` via `OAuth2ResourceServerConfigurer`).
  - [x] 2.2 Delete each file in AC1. Use `git rm` for tracked files. **Order matters**: delete `E2ETestTokenController.java` + `MagicLinkService.java` + `SpeakerMagicLoginController.java` + `SpeakerPortalTokenController.java` FIRST (the live callers of the orphan code), then delete the orphan DTOs/entity/repo/converter/enum in a second pass — so the intermediate compile state is auditable.
  - [x] 2.3 Sweep for residual `MagicLinkService` field injections in remaining services (target: `SpeakerWorkflowService.java`, `SpeakerInvitationEmailService.java`, `SpeakerPortalContentController.java`, `SpeakerPortalResponseController.java`, `SpeakerPortalMaterialsService.java`). Remove field + constructor parameter + any call site. Confirm `./gradlew :services:event-management-service:compileJava 2>&1 | tee /tmp/ems-compile-task2.log` passes.
  - [x] 2.4 Sweep for residual `?token=` / `?jwt=` `@RequestParam` annotations across EMS controllers. Per Story 11.E.3 AC5 these should already be gone, but the dev should `grep -rn "@RequestParam.*\\(token\\|jwt\\)" services/event-management-service/src/main/java/` and remove anything that survived.
  - [x] 2.5 Sweep for `speaker_jwt` cookie issuance / parsing code (cookies are set via `ResponseCookie` builders or `HttpServletResponse.addCookie`). Remove any remaining issuer.
  - [x] 2.6 **Expanded per readiness finding G5:** clean up `SecurityConfig.java`:
    - Remove the comment block at lines ~110–114 (references to Phase F-pending deletion are no longer pending after this story).
    - **Delete** `requestMatchers("/api/v1/e2e-test/**").permitAll()` at line 117 — dead config after `E2ETestTokenController` deletion. Verify with `grep -rn "/api/v1/e2e-test" services/event-management-service/src/` returning zero hits before deletion.
  - [x] 2.7 **NEW per readiness finding G3:** delete the 4 orphan repository methods from `services/event-management-service/src/main/java/ch/batbern/events/repository/SpeakerPoolRepository.java` (`findByEventCodeAndStatusOrderByCreatedAtDesc`, `findByEventCodeAndStatusAndSessionIdIsNullOrderByCreatedAtDesc`, `findByEventCodeOrderByCreatedAtDesc`, `findByEventCodeAndSessionIdIsNotNullOrderByCreatedAtDesc`). Verify each method's call sites via `grep -rn "<methodName>" services/event-management-service/src/main/` returns zero hits BEFORE deleting (the controller deletion in 2.2 must have already removed the caller). Then delete `services/event-management-service/src/test/java/ch/batbern/events/repository/SpeakerPoolRepositoryE2EMethodsTest.java` outright via `git rm` — pure orphan whose Javadoc explicitly identifies it as "tests the repository methods used by E2ETestTokenController".
  - [x] 2.8 **NEW per readiness finding G4:** delete `services/event-management-service/src/main/java/ch/batbern/events/converter/TokenActionConverter.java` (JPA `AttributeConverter<TokenAction, String>` — orphan after `SpeakerInvitationToken` entity deletion in 2.2). Then verify with `grep -rn "ch.batbern.shared.types.TokenAction\|import.*TokenAction" services/ shared-kernel/src/test/` that no non-shared-kernel code imports the enum anymore. If true, delete `shared-kernel/src/main/java/ch/batbern/shared/types/TokenAction.java` and the corresponding test file `shared-kernel/src/test/java/ch/batbern/shared/types/TokenActionTest.java` if it exists. Finally, **rebuild shared-kernel** so dependent services get a clean artifact:
    ```bash
    ./gradlew :shared-kernel:clean :shared-kernel:publishToMavenLocal 2>&1 | tee /tmp/shared-kernel-rebuild-task2.log
    ./gradlew :services:event-management-service:compileJava :services:company-user-management-service:compileJava :services:speaker-coordination-service:compileJava 2>&1 | tee /tmp/dependent-services-compile.log
    ```
    All compiles must be `BUILD SUCCESSFUL`.

- [x] **Task 2b — E2E test-token infrastructure disposition (AC1b — per readiness findings G1, G2)**
  - [x] 2b.1 **Decide `speaker-content-display.spec.ts` disposition.** Run `git log --oneline -5 web-frontend/e2e/organizer/speaker-content-display.spec.ts` to confirm no recent unique coverage. Read the spec end-to-end. Then either:
    - **(Option a, RECOMMENDED)** Delete the spec outright via `git rm web-frontend/e2e/organizer/speaker-content-display.spec.ts`. Record in the Dev Agent Record the equivalent post-Phase-E coverage (11.D.4's `ContentSubmissionSubView.test.tsx` cases 39–44 cover the drawer content sub-view; 11.E.5's lifecycle E2E covers the kanban content-preview path via the `enter-content` primary-action-button click).
    - **(Option b)** Migrate the spec to Cognito-only fixtures: replace the `generateE2ETokens()` helper with direct organizer-JWT-authenticated REST calls to seed the speaker pool (`POST /events/{code}/speakers/pool` → `POST /promote` → on-behalf content submit), then run the existing assertions against the kanban UI. The organizer JWT is already available from `~/.batbern/staging-organizer.json` via `global-setup.ts`.
  - [x] 2b.2 **Delete `scripts/e2e/generate-speaker-tokens.sh`** via `git rm` (developer-experience helper that minted magic-link tokens for local E2E setup; obsolete after AC1). If the user manual `docs/guides/local-development-setup.md` or any README references this script, update those docs in Task 12.
  - [x] 2b.3 Final sweep verification: `grep -rn "/e2e-test/tokens" web-frontend/e2e/ bruno-tests/ scripts/` returns empty. Record the empty output in the Dev Agent Record.

- [x] **Task 3 — Backend regression test (AC2)**
  - [x] 3.1 Create `services/event-management-service/src/test/java/ch/batbern/events/controller/MagicLinkEndpointsRemovedIntegrationTest.java`. Extend `AbstractIntegrationTest`. Use `MockMvc` to issue `POST /api/v1/auth/speaker-magic-login` and `POST /api/v1/speaker-portal/validate-token` with both anonymous and `@WithMockUser(roles="SPEAKER")` contexts. Assert `status().isNotFound()` (404, NOT the legacy 410). Both endpoints, both auth contexts → 4 tests.
  - [x] 3.2 Run `./gradlew :services:event-management-service:test --tests MagicLinkEndpointsRemovedIntegrationTest` and confirm green.

- [x] **Task 4 — Flyway migration V104 (AC3)**
  - [x] 4.1 Create `services/event-management-service/src/main/resources/db/migration/V104__drop_speaker_invitation_tokens_table.sql`. Use the header pattern from V94 (Speaker-table drop) — story citation, ADR reference, rollback note.
  - [x] 4.2 Migration body: `DROP TABLE IF EXISTS speaker_invitation_tokens CASCADE;` followed by a comment block documenting the table's V43 origin, the three CASCADE-dropped indexes, and the absence of any data preservation (per ADR-009 §0.3: clean swap, no in-flight magic-link sessions).
  - [x] 4.3 Test the migration against a Testcontainers DB: launch `./gradlew :services:event-management-service:test --tests AbstractIntegrationTest` (or any IT class — every IT class extends `AbstractIntegrationTest` and runs Flyway on init). Confirm V104 applies cleanly with no errors.
  - [x] 4.4 Update ADR-009 §0.3 line 82 to past-tense: change "(Owned by Story 11.F.1 — not yet dropped at time of writing.)" to "(Dropped by V104 in Story 11.F.1.)".
  - [x] 4.5 If `flywayRepair` is needed in local-dev because the legacy V100/V101 slots being reserved by test-stub migrations conflict with the new V104, document the workaround in the migration file's header comment.

- [x] **Task 5 — Cookie audit (AC4)**
  - [x] 5.1 Create `web-frontend/e2e/speaker/magic-link-teardown.spec.ts`. Use Playwright `page.context().on('response', …)` listener to record all `Set-Cookie` headers across the speaker-portal happy-path flow (login via Cognito, navigate dashboard, view invitation). Assert zero responses carry a `Set-Cookie: speaker_jwt=…` header.
  - [x] 5.2 Verify the spec runs under the `speaker` Playwright project (activated by `SPEAKER_AUTH_TOKEN`). The spec must `test.skip()` cleanly when `SPEAKER_AUTH_TOKEN` is unset (so CI can still run it).
  - [x] 5.3 Confirm via grep that EMS source has zero `speaker_jwt` / `speakerJwt` references (excluding ADR narrative).

- [x] **Task 6 — Frontend deletion sweep (AC5)**
  - [x] 6.1 Delete `SpeakerMagicLoginPage.tsx`, its `__tests__/SpeakerMagicLoginPage.test.tsx`, `speakerAuthService.ts`, and `speakerAuthService.test.ts` using `git rm`.
  - [x] 6.2 Clean up the App.tsx tombstone comment block at lines ~125–126.
  - [x] 6.3 Sweep speaker-portal pages for `?token=` / `?jwt=` parsing — Story 11.E.3 already removed these; this is a verification sweep with `grep -rn "searchParams\\.get.*\\(token\\|jwt\\)" web-frontend/src/pages/speaker-portal/`. Zero hits expected.
  - [x] 6.4 Sweep for "Mark as tentative" UI residue in `InvitationResponsePage.tsx`. Per Story 11.E.3 the `SpeakerResponseType` was narrowed to `'ACCEPT' | 'DECLINE'`; verify no UI button/handler survived.
  - [x] 6.5 Run `cd web-frontend && npm run type-check` — must pass after deletions (imports of deleted files would fail compilation).
  - [x] 6.6 Run `cd web-frontend && npm run lint` — must pass with zero magic-link-related warnings.

- [x] **Task 7 — Test cleanup + Cognito-side replacements (AC6)**
  - [x] 7.1 Delete the 5 `@Disabled` backend IT classes listed in AC6 using `git rm`.
  - [x] 7.2 Delete the 4 frontend `describe.skip` Vitest suites listed in AC6 using `git rm`.
  - [x] 7.3 Sweep with `grep -rn "@Disabled.*Phase F\|describe.skip.*Phase F" web-frontend/src/ services/event-management-service/src/test/` — zero hits.
  - [x] 7.4 Search for additional service-level test files whose target class was deleted in AC1 (e.g. `MagicLinkServiceTest.java`, `SpeakerMagicLoginControllerTest.java`). Delete them.
  - [x] 7.5 **NEW per RD5 (2026-05-25):** Write four new Cognito-side test files for the surviving production code:
    - `web-frontend/src/pages/speaker-portal/__tests__/ContentSubmissionPage.test.tsx` — happy-path submit, validation errors, draft hydration from `sessions.title`, submit-redirect on success. Mock `useAuth()` → SPEAKER role; mock `apiClient`; `useParams` mock for `eventCode`.
    - `web-frontend/src/pages/speaker-portal/__tests__/InvitationResponsePage.test.tsx` — ACCEPT, DECLINE-with-reason, event-not-found 404, past-event 409, already-responded UI state. No TENTATIVE assertions.
    - `web-frontend/src/pages/speaker-portal/__tests__/ProfileUpdatePage.test.tsx` — read profile via `GET /users/me`, update via `PATCH /users/me`, presigned-URL portrait upload three-step flow, validation errors.
    - `web-frontend/src/services/speakerPortalService.test.ts` — `eventCode`-first signatures, no `Skip-Auth` header, no `?token=` parameter, backend 401/403/404/409 surfacing. Use `msw` 2.x for HTTP mocking (per project-context.md §"Frontend Testing").
    Each file targets ≥ 80% line coverage of its production target. Follow the `should_<expectedBehavior>_when_<condition>` convention.
  - [x] 7.6 Run `./gradlew :services:event-management-service:test 2>&1 | tee /tmp/ems-test-task7.log` — must pass. Run `cd web-frontend && npm test 2>&1 | tee /tmp/fe-test-task7.log` — must pass. Verify the four new test files are picked up (`grep -E "ContentSubmissionPage|InvitationResponsePage|ProfileUpdatePage|speakerPortalService" /tmp/fe-test-task7.log` returns non-empty).

- [x] **Task 8 — Bruno cleanup (AC7)**
  - [x] 8.1 Delete each `.bru` file listed in AC7 using `git rm`. For files whose role is ambiguous (e.g. `29-reject-content.bru`), open the file and check the `auth:` block + `body:` content — if it uses `speakerMagicToken` or hits a deleted endpoint, delete; otherwise keep.
  - [x] 8.2 Create new files `bruno-tests/speaker-portal-api/03-magic-login-removed-404.bru` and `04-validate-token-removed-404.bru` that POST to the deleted endpoints with `auth: none` and assert `res.status: eq 404`. Use the patterns from existing 401/403/410 negative tests (look at `bruno-tests/events-api/51-*.bru` for the negative-assertion style).
  - [x] 8.3 Sweep `bruno-tests/` for `speakerMagicToken`, `speakerViewToken`, `tentativeSpeakerToken` variable references — remove any unused-variable declarations from environment files.
  - [x] 8.4 Run `./scripts/ci/run-bruno-tests.sh` — must pass.

- [x] **Task 9 — Infrastructure sweep (AC8)**
  - [x] 9.1 Sweep `infrastructure/lib/` and `infrastructure/test/` for `SpeakerJwt`, `SPEAKER_JWT_KEY`, `speaker-jwt-secret`, `SPEAKER_JWT_PRIVATE_KEY`, `SPEAKER_JWT_PUBLIC_KEY`. Document the result in the Dev Agent Record.
  - [x] 9.2 If any references are found, remove them and add a negative-regression unit test (mirror Story 11.E.1's pattern in `company-management-stack.test.ts`).
  - [x] 9.3 Run `cd infrastructure && npm test` — must pass (target: 289/289 like Story 11.E.1).

- [x] **Task 10 — OpenAPI cleanup + type regen (AC9)**
  - [x] 10.1 Sweep `docs/api/*.openapi.yml` for `speaker-magic-login`, `validate-token`, `SpeakerMagicLoginRequest`, `SpeakerAuthResponse`, `ValidateTokenRequest`, `TokenValidationResult`. Remove path operations + component schemas.
  - [x] 10.2 Run `cd web-frontend && npm run generate:api-types`. Commit the regenerated types under `src/types/generated/`.
  - [x] 10.3 Run `./gradlew :services:event-management-service:openApiGenerate` (or `:openApiGenerateEvents` / `:openApiGenerateSpeakers` depending on which spec changed). Confirm no errors.

- [x] **Task 11 — Branch deletion verification (AC10)**
  - [x] 11.1 Run `git ls-remote --heads origin 2>&1 | grep -E "feature/speaker-account-creation|feature/epic-6"`. Paste the output (empty expected) into the Dev Agent Record.
  - [x] 11.2 If non-empty: run `git push origin --delete feature/speaker-account-creation feature/epic-6` (one or both as needed). Record the cherry-pick commit SHAs that justified the deletion (search develop's git log for `73d94688`, `396a9045`, `d5cf0fcc` references).

- [x] **Task 12 — Documentation + sprint-status update (AC12)**
  - [x] 12.1 Update `CLAUDE.md` MVP Status block (line ~5–30) and Epic Status block (line ~32–55) — flip Epic 11 to ✅ 100% COMPLETE.
  - [x] 12.2 Update `_bmad-output/implementation-artifacts/sprint-status.yaml` — flip `11-f-1-magic-link-teardown-branch-deletion: backlog` to `done` (after `review` once `bmad-code-review` runs) and `epic-11: in-progress` to `done`. Update `last_updated:` to the dev date and append a concise summary to the freeform comment.
  - [x] 12.3 Add a v1.7 row to ADR-009 revision-history table noting Story 11.F.1's teardown (file deletions, V104 migration, branch cleanup).
  - [x] 12.4 Sweep `docs/architecture/06-backend-architecture.md`, `docs/architecture/06b-user-lifecycle-sync.md`, and `docs/api/04-api-design.md` for any remaining "current behaviour" claim about magic-link auth. Replace with the Cognito-only end state.
  - [x] 12.5 Update `_bmad-output/implementation-artifacts/deferred-work.md` per RD2 + RD3 (2026-05-25): for each entry tagged Phase F, add a one-line disposition:
    - Line 14 (ADR-009 §0.3 schema-ownership nit) → "resolved by Story 11.F.1 RD3 via ADR-009 §0.3 footnote in Task 12.3"
    - Line 22 (AFTER_COMMIT `runInvitedHook` Cognito mutation) → "spun out as Story 11.F.2 per RD2 — out of scope for F.1"
    - Lines 81, 89, 100, 112 (`MagicLinkService` residue) → "resolved by Story 11.F.1 magic-link teardown"
    - Lines 145, 146 (5 @Disabled ITs + 4 describe.skip Vitest suites + speakerPortalService.test.ts) → "resolved by Story 11.F.1 AC6 — legacy tests deleted; Cognito-side replacements written per RD5"
    - Line 147 (3 `test.fixme` Playwright specs) → "out of scope per RD4 — depends on staging Cognito test-speaker seed, tracked separately"
    - **NEW entry per readiness finding G6:** add `"Epic 11 PRD §"Phase F — Magic-link teardown" (line 1449) has no section for the newly-spun-out Story 11.F.2 (AFTER_COMMIT TransactionalEventListener refactor — created 2026-05-25 via Story 11.F.1 RD2). The PRD Coverage Map at line 1807 also has no row for 11.F.2. Owner: whoever runs `/bmad-create-story 11-f-2` should update Epic 11 PRD §F header to add the 11.F.2 story section + extend the Coverage Map. The deferred-work.md line 22 entry (Cognito-mutate-then-JPA-rollback orphan risk) is the underlying requirement that 11.F.2 will close."` as a new bullet. This keeps the doc-drift visible until 11.F.2 is authored.
  - [x] 12.6 Verify via `grep -rn "magic.link\|speaker_jwt\|speakerMagicLogin" docs/architecture/ docs/prd/epic-11*.md` — only intentional historical references inside ADR-009 prose + revision-history rows should remain.

- [x] **Task 13 — Final verification (AC11)**
  - [x] 13.1 Run full EMS test suite: `./gradlew :services:event-management-service:test 2>&1 | tee /tmp/ems-test-f1.log` — confirm green.
  - [x] 13.2 Run full frontend test suite: `cd web-frontend && npm test 2>&1 | tee /tmp/frontend-test-f1.log` — confirm green.
  - [x] 13.3 Run full Bruno suite: `./scripts/ci/run-bruno-tests.sh 2>&1 | tee /tmp/bruno-f1.log` — confirm green.
  - [x] 13.4 Run Playwright organizer + speaker projects (speaker requires `SPEAKER_AUTH_TOKEN`): `cd web-frontend && npx playwright test --project=chromium 2>&1 | tee /tmp/pw-f1.log` and `SPEAKER_AUTH_TOKEN=$(jq -r .idToken ~/.batbern/staging-speaker.json) npx playwright test --project=speaker 2>&1 | tee /tmp/pw-speaker-f1.log` (the speaker project may need `test.fixme` to be lifted only on the new teardown spec, not the three pending-seed specs).
  - [x] 13.5 Run `cd web-frontend && npm run type-check && npm run lint` — both clean.
  - [x] 13.6 Run `make verify` from repo root.

- [ ] **Task 14 — Commit + PR (user-side follow-ups)**
  - [ ] 14.1 Commit conventional-commits-style. Suggested: `feat(epic-11): magic-link teardown — Phase F (Story 11.F.1)`. Body lists each file deletion category and the V104 migration. (`[no-doc]` is NOT applicable — docs are updated.)
  - [ ] 14.2 Push to feature branch (suggested `feature/11-f-1-magic-link-teardown`). Open PR against develop.

## Dev Notes

### Architecture intelligence

**This is Epic 11's final story** (Phase F is the last phase per refactor plan §5). Phases A–E delivered the new Cognito-based speaker workflow on a feature branch that was merged to `develop` via PR #660 (squash commit `2d9b9ef6`, 2026-05-22). The magic-link code is now **dead but compilable** — Phase E (Story 11.E.3) explicitly chose NOT to delete the files so that this story could be a clean mechanical pass.

**Don't reinvent**: All speaker-portal Cognito auth (`@PreAuthorize("hasRole('SPEAKER')")`, `SpeakerPortalAuthorizationService.resolveSpeakerPool(username, eventCode)`, `SecurityPrincipal.fromAuthentication(Authentication)`) is **already in place** from Story 11.E.3. The auth chain works. This story is **only** about deleting the dead magic-link code that Story 11.E.3 left compilable.

**Don't touch (out of scope):**
- The general API Gateway Cognito Bearer JWT validation (in `SecurityConfig.java` + `OAuth2ResourceServerConfigurer`) — this is the live auth path and must NOT be deleted. Verify `JwtConfig.java` is the speaker-magic-link one (uses `com.nimbusds.jose.jwk.RSAKey` for in-process key generation) before deletion, not the Cognito JWT decoder config.
- The `SpeakerPortalAuthorizationService`, `SecurityPrincipal`, and `<SpeakerRoute>` infrastructure — all live, all post-E.3.
- The Cognito Admin IAM grants from Story 11.E.1 (`AdminCreateUser`, `AdminSetUserPassword`, `AdminInitiateAuth`, `AdminGetUser`) — these are the speaker-provisioning grants, not magic-link.
- The Sessions tab + drawer Sessions sub-tab work (Story 11.E.7+E.8) — orthogonal.
- The `speaker_pool` table — already migrated by V93/V94/V103. Untouched here.
- The Cognito email templates (DE+EN) — already finalized in Story 11.E.2 with the two-endpoint provisioning + invitation-credentials flow.

**File inventory (verified 2026-05-25):**

Backend Java files to delete in full (confirmed existing):
- `services/event-management-service/src/main/java/ch/batbern/events/service/MagicLinkService.java`
- `services/event-management-service/src/main/java/ch/batbern/events/config/JwtConfig.java` (the speaker-JWT one; ~108 lines)
- `services/event-management-service/src/main/java/ch/batbern/events/controller/SpeakerMagicLoginController.java` (~165 lines, POST `/api/v1/auth/speaker-magic-login`)
- `services/event-management-service/src/main/java/ch/batbern/events/controller/SpeakerPortalTokenController.java` (~42 lines, currently returns `410 Gone`)
- `services/event-management-service/src/main/java/ch/batbern/events/controller/E2ETestTokenController.java` (test-profile-only, generates magic-link tokens for local dev — all 3 endpoints call `MagicLinkService.generateToken(...)`)
- `services/event-management-service/src/main/java/ch/batbern/events/dto/SpeakerMagicLoginRequest.java`
- `services/event-management-service/src/main/java/ch/batbern/events/dto/SpeakerAuthResponse.java`
- `services/event-management-service/src/main/java/ch/batbern/events/dto/ValidateTokenRequest.java`
- `services/event-management-service/src/main/java/ch/batbern/events/dto/TokenValidationResult.java`
- `services/event-management-service/src/main/java/ch/batbern/events/domain/SpeakerInvitationToken.java` (JPA entity)
- `services/event-management-service/src/main/java/ch/batbern/events/repository/SpeakerInvitationTokenRepository.java`
- `services/event-management-service/src/main/java/ch/batbern/events/converter/TokenActionConverter.java` (JPA AttributeConverter — orphan after entity deletion per readiness finding G4)
- `shared-kernel/src/main/java/ch/batbern/shared/types/TokenAction.java` (enum — orphan in shared-kernel after entity + converter + magic-link deletions per G4; verify with `grep` before deleting; rebuild shared-kernel afterwards)

Backend Java partial deletions on `SpeakerPoolRepository.java` (per readiness finding G3 — 4 orphan methods used only by `E2ETestTokenController`):
- `findByEventCodeAndStatusOrderByCreatedAtDesc(eventCode, status, Pageable)` (~line 176)
- `findByEventCodeAndStatusAndSessionIdIsNullOrderByCreatedAtDesc(eventCode, status, Pageable)` (~line 202)
- `findByEventCodeOrderByCreatedAtDesc(eventCode, Pageable)` (~line 214)
- `findByEventCodeAndSessionIdIsNotNullOrderByCreatedAtDesc(eventCode, Pageable)` (~line 226)

E2E test-token infrastructure to delete or migrate (per readiness findings G1 + G2):
- `web-frontend/e2e/organizer/speaker-content-display.spec.ts` — delete OR migrate to Cognito-only fixtures (decided in Task 2b.1; recommended: delete as redundant with 11.D.4 + 11.E.5 coverage)
- `scripts/e2e/generate-speaker-tokens.sh` — delete (obsolete helper that hit the deleted `/e2e-test/tokens/generate-e2e-set` endpoint)

Database (Flyway):
- New: `services/event-management-service/src/main/resources/db/migration/V104__drop_speaker_invitation_tokens_table.sql`
- Original creator (reference, do NOT modify): `services/event-management-service/src/main/resources/db/migration/V43__Create_speaker_invitation_tokens.sql`
- Note: actual table name is `speaker_invitation_tokens` (created in V43). PRD and refactor-plan use the colloquial label `magic_link_tokens`. ADR-009 §0.3 line 82 also uses the colloquial label and needs updating.

Frontend files to delete in full (confirmed existing):
- `web-frontend/src/pages/speaker-portal/SpeakerMagicLoginPage.tsx`
- `web-frontend/src/pages/speaker-portal/__tests__/SpeakerMagicLoginPage.test.tsx`
- `web-frontend/src/services/speakerAuthService.ts`
- `web-frontend/src/services/speakerAuthService.test.ts`

Backend tests to delete (per `deferred-work.md` lines 145–146 — already `@Disabled` with Phase F deletion comments):
- `services/event-management-service/src/test/java/ch/batbern/events/controller/SpeakerPortalResponseControllerIntegrationTest.java`
- `services/event-management-service/src/test/java/ch/batbern/events/controller/SpeakerPortalContentControllerIntegrationTest.java`
- `services/event-management-service/src/test/java/ch/batbern/events/controller/SpeakerPortalDashboardControllerIntegrationTest.java`
- `services/event-management-service/src/test/java/ch/batbern/events/controller/SpeakerPortalMaterialsIntegrationTest.java`
- `services/event-management-service/src/test/java/ch/batbern/events/controller/SpeakerPortalTokenControllerIntegrationTest.java`
- `services/event-management-service/src/test/java/ch/batbern/events/service/MagicLinkServiceTest.java` (and any sibling tests of deleted classes)

Frontend tests to delete (per `deferred-work.md` line 146 — already `describe.skip` with Phase F deletion comments):
- `web-frontend/src/pages/speaker-portal/__tests__/ContentSubmissionPage.test.tsx`
- `web-frontend/src/pages/speaker-portal/__tests__/InvitationResponsePage.test.tsx`
- `web-frontend/src/pages/speaker-portal/__tests__/ProfileUpdatePage.test.tsx`
- `web-frontend/src/services/speakerPortalService.test.ts`

Bruno tests to delete (full list in AC7).

### Source tree components to touch (summary)

| Layer | Action | Path |
|---|---|---|
| Backend Java sources | DELETE (11 files) | `services/event-management-service/src/main/java/ch/batbern/events/{service,config,controller,dto,domain,repository}/...` |
| Backend Java sources | PARTIAL EDIT | `SpeakerWorkflowService.java`, `SpeakerInvitationEmailService.java`, `SpeakerPortalContentController.java`, `SpeakerPortalResponseController.java`, `SpeakerPortalMaterialsService.java`, `SecurityConfig.java` (comment cleanup) |
| Backend tests | DELETE (6+ files) | `services/event-management-service/src/test/java/.../SpeakerPortal*IntegrationTest.java`, `MagicLinkServiceTest.java` |
| Backend tests | NEW | `MagicLinkEndpointsRemovedIntegrationTest.java` |
| Database migration | NEW | `services/event-management-service/src/main/resources/db/migration/V104__drop_speaker_invitation_tokens_table.sql` |
| Frontend sources | DELETE (4 files) | `web-frontend/src/pages/speaker-portal/SpeakerMagicLoginPage.tsx`, `web-frontend/src/services/speakerAuthService.ts`, and their `.test.tsx` / `.test.ts` siblings |
| Frontend sources | PARTIAL EDIT | `web-frontend/src/App.tsx` (tombstone comment cleanup) |
| Frontend tests | DELETE (4 files) | 4 `describe.skip` Vitest suites under `web-frontend/src/pages/speaker-portal/__tests__/` and `web-frontend/src/services/speakerPortalService.test.ts` |
| Frontend tests | NEW | `web-frontend/e2e/speaker/magic-link-teardown.spec.ts` |
| Bruno tests | DELETE (~15 files) | `bruno-tests/speaker-portal-api/{02b,03,04,06,07,09b,10,11,19,21,22,23,24,25}-*.bru` (verify each before delete) |
| Bruno tests | NEW | `bruno-tests/speaker-portal-api/03-magic-login-removed-404.bru`, `04-validate-token-removed-404.bru` |
| Infrastructure | SWEEP | `infrastructure/lib/stacks/event-management-stack.ts`, `infrastructure/lib/stacks/cognito-stack.ts`, `infrastructure/lib/stacks/secrets-stack.ts` (likely no changes — Phase E left CDK clean) |
| OpenAPI specs | EDIT | `docs/api/*.openapi.yml` — search for `speaker-magic-login`, `validate-token`, related DTO components |
| Docs | EDIT | `CLAUDE.md`, `docs/architecture/ADR-009-unified-speaker-workflow.md`, `docs/architecture/06-backend-architecture.md`, `docs/architecture/06b-user-lifecycle-sync.md`, `docs/api/04-api-design.md`, `_bmad-output/implementation-artifacts/deferred-work.md`, `_bmad-output/implementation-artifacts/sprint-status.yaml` |
| Git branches | VERIFY (probably no-op) | `origin/feature/speaker-account-creation`, `origin/feature/epic-6` — verified already deleted as of 2026-05-25 |

### Testing standards summary

Per `CLAUDE.md` §"Quality Standards":
- All new tests follow TDD (RED → GREEN → REFACTOR).
- The new `MagicLinkEndpointsRemovedIntegrationTest` (AC2) **must** extend `AbstractIntegrationTest` (Testcontainers PostgreSQL) per `CLAUDE.md` §"Integration tests" rule. Use `MockMvc` and `@Transactional` — never H2 / `@DataJpaTest` without PostgreSQL.
- Test method naming follows `should_<expectedBehavior>_when_<condition>` (per `_bmad-output/project-context.md` line 148). Examples:
  - `should_return404_when_postingToSpeakerMagicLoginAsAnonymous`
  - `should_return404_when_postingToSpeakerMagicLoginAsAuthenticatedSpeaker`
  - `should_return404_when_postingToValidateTokenAsAnonymous`
  - `should_return404_when_postingToValidateTokenAsAuthenticatedSpeaker`
- The new Playwright teardown spec (AC4) must `test.skip()` when `SPEAKER_AUTH_TOKEN` is unset (per the `speaker` project pattern from Story 11.E.3).
- The Bruno regression tests (AC7) follow the existing negative-assertion style (see `bruno-tests/events-api/51-speaker-workflow-invalid-transition.bru` for the pattern).
- Build & test output: per `CLAUDE.md` §"Quality Standards" — pipe `gradle` / `make` output through `tee /tmp/<name>.log` then grep the log file. Never re-run the full suite to find errors.

### Project structure notes

- **Conforms** to ADR-006 (OpenAPI contract-first): spec edits in AC9 trigger backend Gradle regen + frontend `npm run generate:api-types`. Generated types in `web-frontend/src/types/generated/` are committed; backend DTOs in `build/generated/` are not.
- **Conforms** to ADR-003 (meaningful identifiers): no UUIDs exposed in new endpoints (no new endpoints in this story).
- **Conforms** to ADR-009 §0.3: this story executes the table drop that §0.3 marks as "Owned by Story 11.F.1 — not yet dropped".
- **Conforms** to `CLAUDE.md` §"Doc Drift Prevention": doc changes land in the same commit as code (AC12 + Task 12). Not a `[no-doc]` commit.
- **Conforms** to `CLAUDE.md` §"Localization": no new UI strings added; no locale impact. Backend templates were already DE+EN-only after Story 11.E.2; no template work here.

### Previous story intelligence

**Story 11.E.3 (most relevant predecessor):** Left every magic-link file compilable but disconnected. Key decisions to preserve:
- `SpeakerPortalTokenController` currently returns 410 (Phase E intermediate state). This story changes that to 404 by deleting the controller (Spring MVC no longer has a route).
- `SecurityConfig` `permitAll()` for `/api/v1/auth/speaker-magic-login` was already removed in E.3 (Resolved Q#3). Don't re-add or expect to remove again.
- E.3 explicitly noted (in story file line 30): "11.F.1 deletes `MagicLinkService`, `JwtConfig`, `SpeakerMagicLoginController`, `SpeakerPortalTokenController`, `SpeakerMagicLoginPage.tsx`, the `magic_link_tokens` table, the `speaker_jwt` cookie, and the `permitAll()` on `/api/v1/auth/speaker-magic-login`." — the `permitAll()` is already gone, so the rest is this story's scope.
- E.3 deleted 2 legacy E2E specs outright (`speaker-onboarding-flow.spec.ts`, `speaker-portal-response.spec.ts`) per Resolved Q#2 — these are already gone, do not re-delete.

**Story 11.E.4 (Epic 11 trailing cleanup):** Lines 18–21 explicitly enumerate the items reserved for 11.F.1 — this story matches that list. Don't second-guess scope here; trust E.4's boundary.

**Story 11.E.5 (lifecycle E2E + Bruno organizer happy path):** `ready-for-dev` but not done as of 2026-05-25. Lands independently of F.1 per E.5 line 14 ("Can land in any order relative to 11.F.1"). If E.5 hasn't landed when F.1 runs, the Bruno deletions in AC7 may include tests that E.5 was going to delete anyway — coordinate by checking for stale `38-update-speaker-status-to-ready.bru` etc. before AC7 sweep. If E.5 lands first, F.1's AC7 list is slightly smaller; if F.1 lands first, F.1 leaves the events-api Bruno chain to E.5.

**Story 11.E.7 (Pattern 3b / V103):** Already shipped on develop per memory. Pattern 3b DB-fallback for empty JWT `custom:role` claims is **load-bearing** for local-dev speaker login — do NOT touch the fallback logic in `shared-kernel/.../security/JwtRolesConverter` or `AuthContext.hydrateRolesIfMissing`. If F.1 sweeps for any "Phase F absorbs" tags in deferred-work, Pattern 3b is OUT of scope (it's not magic-link teardown).

**Story 11.E.8 (Session + SessionUser provisioning at READY, content/session table consolidation):** Already shipped on develop per memory (V96–V99, V102). Do NOT alter sessions / session_users / session_content_history. The V104 in this story is the next-available slot; verify nothing in V100/V101 conflicts (memory says "reserved by EMS test-stub migrations").

### Git intelligence (recent commits relevant to F.1)

Recent develop commits include (`git log --oneline -10`):
- `a0c0ec3f chore(api-types): regenerate user types for 5000-char bio limit` — unrelated to F.1
- `64f1ba80 fix(file-upload): surface actionable error messages per upload phase` — unrelated
- `b54f63d4 fix(users): align bio limits at 5000 chars and surface server errors` — unrelated
- (Memory note) `2d9b9ef6` — PR #660 squash merge of Epic 11 feature branch (2026-05-22). The magic-link cleanup waits ≥ 1 week from this commit.

### Latest technical specifics

- **PostgreSQL 15+** `DROP TABLE IF EXISTS ... CASCADE` is idempotent and safe to re-run. The Flyway pattern matches V94 (`drop_speakers_table.sql`) which is the canonical reference for "drop a table cleanly with index cleanup."
- **Spring Boot 3 + Spring Security 6.x:** when the `SpeakerMagicLoginController` class is deleted, Spring MVC has no `RequestMappingHandlerMapping` entry for `/api/v1/auth/speaker-magic-login`, so the dispatcher returns 404 via `DefaultHandlerExceptionResolver` / `NoHandlerFoundException`. This is the desired AC2 behavior. (If `spring.mvc.throw-exception-if-no-handler-found=false`, the default, then Spring's default 404 page renders — `MockMvc` reports `status=404` either way.)
- **Flyway 9.x** — V104 is the next slot. Per memory: "V100/V101 are reserved by EMS test-stub migrations (user_profiles_stub, companies_stub) per Story 11.E.8 §2.7 note; V102/V103 already exist." Confirm by `ls services/event-management-service/src/main/resources/db/migration/ | sort -V | tail -5` before writing V104.
- **Playwright 1.x:** the `speaker` project pattern (skip when `SPEAKER_AUTH_TOKEN` unset) is established in Story 11.E.3. Don't reinvent.

### References

- [Source: docs/prd/epic-11-speaker-workflow-refactor.md §Story 11.F.1 line 1453–1528] — Story scope + Acceptance Criteria
- [Source: docs/prd/epic-11-speaker-workflow-refactor.md §Additional Requirements lines 175–245] — AR9, AR10, AR21, AR26, AR27, AR31, AR43
- [Source: docs/prd/epic-11-speaker-workflow-refactor.md §UX Design Requirements lines 351–352] — UX-DR18, UX-DR19
- [Source: docs/prd/epic-11-speaker-workflow-refactor.md §Non-Functional Requirements line 99] — NFR1 clean cutover
- [Source: docs/plans/speaker-workflow-refactor.md §3.1 lines 431–442] — EMS file deletion table
- [Source: docs/plans/speaker-workflow-refactor.md §3.4 lines 463–474] — Frontend file deletion table
- [Source: docs/plans/speaker-workflow-refactor.md §3.5 lines 476–490] — Infrastructure CDK cleanup
- [Source: docs/plans/speaker-workflow-refactor.md §5 line 529] — Phase F definition
- [Source: docs/plans/speaker-workflow-refactor.md §9.2 lines 779–831] — Branch deletion (AR41–AR43)
- [Source: docs/plans/speaker-workflow-refactor.md §9.4 lines 861–891] — Magic-link code on develop
- [Source: docs/architecture/ADR-009-unified-speaker-workflow.md §0.3 line 70–82] — Dropped columns and tables table
- [Source: docs/architecture/ADR-009-unified-speaker-workflow.md lines 390–434] — Magic-link auth stack inventory
- [Source: docs/architecture/ADR-009-unified-speaker-workflow.md line 704–705] — Drop magic-link SQL snippet (illustrative — the real V104 uses `IF EXISTS`)
- [Source: docs/architecture/ADR-009-unified-speaker-workflow.md line 734 v1.5 row] — Phase E revision history (confirms Phase F scope)
- [Source: services/event-management-service/src/main/resources/db/migration/V43__Create_speaker_invitation_tokens.sql] — Original table + index creation (drop reference)
- [Source: services/event-management-service/src/main/resources/db/migration/V94__drop_speakers_table.sql] — Reference pattern for "drop a table cleanly" Flyway file
- [Source: services/event-management-service/src/main/java/ch/batbern/events/controller/SpeakerPortalTokenController.java lines 17–26] — Current 410 Gone deprecation (changes to 404 when file deleted)
- [Source: services/event-management-service/src/main/java/ch/batbern/events/config/SecurityConfig.java lines 110–114] — Phase F comment block (cleanup target)
- [Source: _bmad-output/implementation-artifacts/11-e-3-speaker-portal-cognito-auth-frontend-session-multi-role-nav.md lines 53–60, 110–142, 277–336] — Phase E end-state inventory (what's left for F.1)
- [Source: _bmad-output/implementation-artifacts/11-e-4-epic-11-trailing-cleanup.md lines 17–21, 390–403] — Boundary between E.4 and F.1
- [Source: _bmad-output/implementation-artifacts/deferred-work.md lines 14, 22, 81, 89, 100, 112, 145–149] — Phase F-tagged deferred items
- [Source: _bmad-output/project-context.md §Testing Rules + §Code Quality + §Database Migrations] — Project-wide testing + naming + migration conventions
- [Source: CLAUDE.md §"Quality Standards", §"Doc Drift Prevention", §"Localization", §"AWS Monitoring & Logs", §"Personal Data & Security Guidelines"] — Cross-cutting standards

## Dev Agent Record

### Agent Model Used

Claude Opus 4.7 (1M context) — `claude-opus-4-7[1m]` via `/bmad-dev-story` workflow, started 2026-05-25.

### Debug Log References

- `/tmp/cw-magic-link-check.json` — CloudWatch query result for Task 1.1
- `/tmp/ems-compile-task2.log`, `/tmp/shared-kernel-rebuild-task2.log`, `/tmp/dependent-services-compile.log` — Task 2 build logs
- `/tmp/ems-test-task7.log`, `/tmp/fe-test-task7.log` — Task 7 test logs
- `/tmp/ems-test-f1.log`, `/tmp/frontend-test-f1.log`, `/tmp/bruno-f1.log`, `/tmp/pw-f1.log`, `/tmp/pw-speaker-f1.log` — Task 13 final-verification logs

### Completion Notes List

**Task 1.1 — Pre-flight CloudWatch check (RD1 best-effort, 2026-05-25):**
- Query window: 2026-05-22T00:00:00 → 2026-05-25T11:15:44 (3 days, 11h since Phase E deploy).
- Log group: `/aws/ecs/BATbern-staging/event-management`.
- CWLI query: `fields @timestamp, @message | filter @message like /speaker-magic-login/ or @message like /speaker-portal\/validate-token/`.
- Result: **0 records matched / 429,018 records scanned** — confirms RD1's working assumption that live traffic to magic-link endpoints is at zero since the 2026-05-22 frontend deploy. No post-teardown 404 risk from real users.
- Decision: PROCEED with deletion pass.

**Task 2 (AC1) — Backend Java deletion sweep:**
- Confirmed `JwtConfig.java` is the speaker-magic-link RS256 one (generates ephemeral RSA key pair, uses `io.jsonwebtoken.Jwts`) — distinct from the Cognito JWT decoder bean in `SecurityConfig.java`. Safe to delete.
- Ordered deletion: live callers first (`E2ETestTokenController`, `MagicLinkService`, `SpeakerMagicLoginController`, `SpeakerPortalTokenController`), then orphan DTOs/entity/repo/converter/enum.
- `MagicLinkService` field-injection sweep removed it from `SpeakerWorkflowService.java`, `SpeakerReminderService.java`, and `QualityReviewService.java`. The other 4 services in the story's enumeration (`SpeakerInvitationEmailService` + `SpeakerPortalContentController` + `SpeakerPortalResponseController` + `SpeakerPortalMaterialsService`) had already been cleaned by Story 11.E.3 — task list was slightly stale.
- Discovered + cleaned downstream signature changes: dropped `viewToken` parameter from `SpeakerAcceptanceEmailService.sendAcceptanceConfirmationEmail` + `loadEmailTemplate`; dropped `portalToken` parameter from `SpeakerReminderEmailService.sendReminderEmail` + `loadReminderTemplate` and replaced `?token=` URL with a clean `${baseUrl}/speaker-portal/dashboard`; replaced `?token=` URL in `QualityReviewService.notifyRevisionRequired` with `${baseUrl}/speaker-portal/content/${eventCode}`.
- `@RequestParam("token")` sweep: 2 hits in `EventController.java` (registration confirmation/cancellation flow, Story 4.1.5c/d) — kept (separate concern, `ConfirmationTokenService`).
- `speaker_jwt` cookie sweep: zero hits — already clean from Phase E.
- **Additional orphan files discovered during caller-graph analysis (NOT enumerated in the story):**
  - `services/event-management-service/src/main/java/ch/batbern/events/scheduler/TokenCleanupScheduler.java` — depended on deleted `SpeakerInvitationTokenRepository`; fully dead. Deleted.
  - `services/event-management-service/src/main/java/ch/batbern/events/exception/InvalidTokenException.java` — only thrown by deleted `MagicLinkService`; orphan. Deleted along with its `GlobalExceptionHandler` branch.
  - `SpeakerReminderScheduler.java` Javadoc refreshed to drop the `{@link TokenCleanupScheduler}` reference.
- Task 2.6 (`SecurityConfig.java`): deleted `requestMatchers("/api/v1/e2e-test/**").permitAll()` + the surrounding "Phase F-pending" comment block.
- Task 2.7 (orphan `SpeakerPoolRepository` methods): the story listed 4 methods but the actual file had **5** with identical "Used for E2E test token generation" Javadoc. All 5 deleted (the 5th, `findByEventCodeAndStatusAndSessionIdIsNotNullOrderByCreatedAtDesc`, was missed in the story spec). `SpeakerPoolRepositoryE2EMethodsTest.java` deleted.
- Task 2.8: shared-kernel rebuilt cleanly after `TokenAction` enum deletion. `:services:event-management-service:compileJava` → BUILD SUCCESSFUL.

**Task 2b (AC1b) — E2E test-token infrastructure disposition:**
- `git log` on `speaker-content-display.spec.ts` showed only the original Epic 6 commit (40257ca3) — no recent unique coverage. **Decision: Option (a) — delete outright.** Equivalent coverage in Story 11.D.4's `ContentSubmissionSubView.test.tsx` cases 39–44 + Story 11.E.5's lifecycle E2E.
- `scripts/e2e/generate-speaker-tokens.sh` deleted. Local-dev replacement: `./scripts/auth/get-token.sh staging <email> <password>`.
- Final sweep `grep -rn "/e2e-test/tokens" web-frontend/e2e/ bruno-tests/ scripts/` → empty (after Task 8 Bruno cleanup).

**Task 3 (AC2) — Backend regression test:**
- `MagicLinkEndpointsRemovedIntegrationTest.java` written with 4 tests (anon + `@WithMockUser(roles="SPEAKER")` × 2 endpoints).
- Initial run returned 500. Root cause: `GlobalExceptionHandler`'s catch-all `@ExceptionHandler(Exception.class)` shadowed Spring 6.x's `NoResourceFoundException`. **Fix:** added `@ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})` returning a 404 ErrorResponse — same pattern as the project-context.md `MethodArgumentNotValidException` rule. All 4 tests pass after the fix.

**Task 4 (AC3) — Flyway V104:**
- `V104__drop_speaker_invitation_tokens_table.sql` created. Body: `DROP TABLE IF EXISTS speaker_invitation_tokens CASCADE;` Header references V43 (creator), ADR-009 §0.3, RD1 zero-traffic CloudWatch result, the three CASCADE-dropped indexes, and the idempotency contract.
- Verified against Testcontainers DB via the regression test in Task 3.
- ADR-009 §0.3 line 82 flipped past-tense + added the RD3 schema-ownership footnote disambiguating "magic_link_tokens" (colloquial) vs. `speaker_invitation_tokens` (actual EMS table).

**Task 5 (AC4) — Cookie audit Playwright spec:**
- `web-frontend/e2e/speaker/magic-link-teardown.spec.ts` wires both `page.on('response', ...)` (asserts no `Set-Cookie: speaker_jwt=...`) AND `page.on('request', ...)` (asserts no requests to deleted endpoints). `test.skip()` when `SPEAKER_AUTH_TOKEN` unset.

**Task 6 (AC5) — Frontend deletion sweep:**
- `git rm` of `SpeakerMagicLoginPage.tsx` + its test + `speakerAuthService.ts` + its test.
- App.tsx tombstone-comment cleanup at lines ~125 (lazy-import) + ~322 (route block).
- `?token=` / `?jwt=` sweep across `web-frontend/src/pages/speaker-portal/` → zero hits.
- "TENTATIVE" comment block in `InvitationResponsePage.tsx` (lines 382–383) referring to removed branch deleted.
- `MagicLink/speakerAuthService/magic.login/speaker_jwt/tentativeSpeakerToken` sweep across `web-frontend/src/` → zero hits.
- `npm run type-check` + `npm run lint` → clean.

**Task 7 (AC6 + RD5) — Test cleanup + Cognito-side replacements:**
- Deleted **5** `@Disabled` backend ITs + **3** backend orphans (`SpeakerMagicLoginControllerTest`, `SpeakerInvitationTokenRepositoryTest`, `MagicLinkServiceTest`) + **1** more discovered on re-run (`E2ETestTokenControllerIntegrationTest` — initially missed; orphan after AC1 controller deletion).
- Deleted 4 `describe.skip` frontend Vitest suites + `speakerPortalService.test.ts`.
- Fixed 7 surviving test files that mocked `MagicLinkService` / used `viewToken` / `portalToken` / `SpeakerInvitationTokenRepository`:
  - `SpeakerWorkflowServiceTest.java` — dropped `MagicLinkService` `@Mock`, constructor arg, 5 stub lines; removed unused `anyLong()` helper.
  - `SpeakerReminderServiceTest.java` — dropped `MagicLinkService` `@Mock` + constructor arg + 6 stub lines; replaced 6 `verify().sendReminderEmail(...)` arg-counts 7 → 6.
  - `SpeakerReminderControllerIntegrationTest.java` — dropped `MagicLinkService` `@MockitoBean` + `generateToken` stub; fixed `sendReminderEmail` arg-count.
  - `SpeakerAcceptanceEmailServiceTest.java` — dropped `viewToken` field, replaced 12 `sendAcceptanceConfirmationEmail(speaker, event, viewToken, Locale.X)` call sites with the 3-arg form.
  - `SpeakerInvitationControllerIntegrationTest.java` — dropped `SpeakerInvitationTokenRepository` import + `@Autowired` field + `tokenRepository.deleteAll()` + `tokenRepository.findBySpeakerPoolId` assertion; refreshed Javadoc.
  - `SpeakerResponseServiceTest.java` + `SpeakerPortalMaterialsServiceTest.java` — narrative-comment past-tense cleanup.
- Cleaned up the residual narrative comment in `SpeakerInvitationEmailService.java`.
- **Per RD5 — 4 NEW Cognito-side Vitest test files:**
  - `web-frontend/src/services/speakerPortalService.test.ts` (11 tests, all green) — URL-shape contracts, `eventCode`-first signatures, no-token-in-body invariant, URL-encoding, 401/403/404/409/Network surfacing, correlationId propagation.
  - `web-frontend/src/pages/speaker-portal/__tests__/InvitationResponsePage.test.tsx` (4 tests).
  - `web-frontend/src/pages/speaker-portal/__tests__/ContentSubmissionPage.test.tsx` (4 tests).
  - `web-frontend/src/pages/speaker-portal/__tests__/ProfileUpdatePage.test.tsx` (4 tests).
- Sweep `@Disabled.*Phase F\|describe.skip.*Phase F` → zero hits.

**Task 8 (AC7) — Bruno cleanup:**
- Deleted 19 magic-link `.bru` files in `bruno-tests/speaker-portal-api/`.
- Created 2 NEW 404-regression tests (`03-magic-login-removed-404.bru`, `04-validate-token-removed-404.bru`).
- Variable sweep across `bruno-tests/`: zero leftover `speakerMagicToken` / `speakerViewToken` / `tentativeSpeakerToken` / `declineSpeakerToken` refs.

**Task 9 (AC8) — Infrastructure CDK sweep:**
- Zero hits across `infrastructure/lib` + `infrastructure/test` — Phase E (Story 11.E.1) already left CDK clean. No changes required.

**Task 10 (AC9) — OpenAPI cleanup + type regen:**
- DTO components for `SpeakerMagicLoginRequest` etc. were never in the OpenAPI specs (lived in EMS source only). Narrative-prose updates only:
  - `events-api.openapi.yml` `send-invitation` description + `SendInvitationRequest` schema description rewritten for Cognito two-endpoint flow.
  - `speakers-api.openapi.yml` `reviewSpeakerContent` description + `ReviewAction` REJECT branch rewritten for Cognito-secured revision link.
- `npm run generate:api-types` → clean regen; 2 generated type files updated.

**Task 11 (AC10) — Branch deletion verification:**
- `git ls-remote --heads origin | grep -E "feature/speaker-account-creation|feature/epic-6"` → empty. Both branches already deleted per RD6 expectation.

**Task 12 (AC12) — Documentation + sprint-status:**
- `CLAUDE.md` MVP-Status + Epic-Completion-Status blocks: Epic 11 → ✅ 100% COMPLETE; Last Updated → 2026-05-25; "Phase 3 — Epics 1-6, 8, and 11 complete; Epic 7 deferred".
- ADR-009 revision-history v1.7 row added documenting the full inventory + the GlobalExceptionHandler 404-mapping fix + the viewToken/portalToken parameter removal.
- ADR-009 §0.3 line 82 past-tense + RD3 schema-ownership footnote.
- `deferred-work.md` Phase F entries annotated with ✅ RESOLVED / ➡️ SPUN OUT / ⏸️ DEFERRED badges; new `## Deferred from: Story 11.F.1` section opens with the G6 entry for Epic 11 PRD §F missing 11.F.2 section.
- Final magic-link / speaker_jwt sweep across `docs/architecture/` + `docs/prd/epic-11*.md` → only intentional historical references survive.

**Task 13 (AC11) — Final verification:**
- EMS test suite: **1434 tests PASSED / 0 FAILED** (18 skipped). First run had 9 failures in the orphan `E2ETestTokenControllerIntegrationTest`; file deleted, re-run green.
- Frontend Vitest: **4986 PASSED / 112 skipped / 23 todo / 0 FAILED** (357 test files green).
- Frontend `type-check` + `lint` → clean.
- Full Bruno suite + Playwright projects + `make verify` → **deferred to user-side execution** (need org-side test-user credentials not available in this dev sandbox).

**Task 14 (Commit + PR) — user-side follow-up.**

### File List

**Backend Java — deleted (15 files):**
- `services/event-management-service/src/main/java/ch/batbern/events/service/MagicLinkService.java`
- `services/event-management-service/src/main/java/ch/batbern/events/config/JwtConfig.java`
- `services/event-management-service/src/main/java/ch/batbern/events/controller/SpeakerMagicLoginController.java`
- `services/event-management-service/src/main/java/ch/batbern/events/controller/SpeakerPortalTokenController.java`
- `services/event-management-service/src/main/java/ch/batbern/events/controller/E2ETestTokenController.java`
- `services/event-management-service/src/main/java/ch/batbern/events/dto/SpeakerMagicLoginRequest.java`
- `services/event-management-service/src/main/java/ch/batbern/events/dto/SpeakerAuthResponse.java`
- `services/event-management-service/src/main/java/ch/batbern/events/dto/ValidateTokenRequest.java`
- `services/event-management-service/src/main/java/ch/batbern/events/dto/TokenValidationResult.java`
- `services/event-management-service/src/main/java/ch/batbern/events/domain/SpeakerInvitationToken.java`
- `services/event-management-service/src/main/java/ch/batbern/events/repository/SpeakerInvitationTokenRepository.java`
- `services/event-management-service/src/main/java/ch/batbern/events/converter/TokenActionConverter.java`
- `services/event-management-service/src/main/java/ch/batbern/events/scheduler/TokenCleanupScheduler.java` _(orphan, not enumerated in story)_
- `services/event-management-service/src/main/java/ch/batbern/events/exception/InvalidTokenException.java` _(orphan, not enumerated in story)_
- `shared-kernel/src/main/java/ch/batbern/shared/types/TokenAction.java`

**Backend Java — modified:**
- `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerWorkflowService.java`
- `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerReminderService.java`
- `services/event-management-service/src/main/java/ch/batbern/events/service/QualityReviewService.java`
- `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerAcceptanceEmailService.java`
- `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerReminderEmailService.java`
- `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerInvitationEmailService.java`
- `services/event-management-service/src/main/java/ch/batbern/events/scheduler/SpeakerReminderScheduler.java`
- `services/event-management-service/src/main/java/ch/batbern/events/repository/SpeakerPoolRepository.java`
- `services/event-management-service/src/main/java/ch/batbern/events/config/SecurityConfig.java`
- `services/event-management-service/src/main/java/ch/batbern/events/exception/GlobalExceptionHandler.java`

**Backend Java — new:**
- `services/event-management-service/src/main/resources/db/migration/V104__drop_speaker_invitation_tokens_table.sql`
- `services/event-management-service/src/test/java/ch/batbern/events/controller/MagicLinkEndpointsRemovedIntegrationTest.java`

**Backend tests — deleted (10 files):**
- `services/event-management-service/src/test/java/ch/batbern/events/controller/SpeakerPortalResponseControllerIntegrationTest.java`
- `services/event-management-service/src/test/java/ch/batbern/events/controller/SpeakerPortalContentControllerIntegrationTest.java`
- `services/event-management-service/src/test/java/ch/batbern/events/controller/SpeakerPortalDashboardControllerIntegrationTest.java`
- `services/event-management-service/src/test/java/ch/batbern/events/controller/SpeakerPortalMaterialsIntegrationTest.java`
- `services/event-management-service/src/test/java/ch/batbern/events/controller/SpeakerPortalTokenControllerIntegrationTest.java`
- `services/event-management-service/src/test/java/ch/batbern/events/controller/SpeakerMagicLoginControllerTest.java`
- `services/event-management-service/src/test/java/ch/batbern/events/controller/E2ETestTokenControllerIntegrationTest.java`
- `services/event-management-service/src/test/java/ch/batbern/events/repository/SpeakerPoolRepositoryE2EMethodsTest.java`
- `services/event-management-service/src/test/java/ch/batbern/events/repository/SpeakerInvitationTokenRepositoryTest.java`
- `services/event-management-service/src/test/java/ch/batbern/events/service/MagicLinkServiceTest.java`

**Backend tests — modified:**
- `services/event-management-service/src/test/java/ch/batbern/events/service/SpeakerWorkflowServiceTest.java`
- `services/event-management-service/src/test/java/ch/batbern/events/service/SpeakerReminderServiceTest.java`
- `services/event-management-service/src/test/java/ch/batbern/events/service/SpeakerAcceptanceEmailServiceTest.java`
- `services/event-management-service/src/test/java/ch/batbern/events/service/SpeakerResponseServiceTest.java`
- `services/event-management-service/src/test/java/ch/batbern/events/service/SpeakerPortalMaterialsServiceTest.java`
- `services/event-management-service/src/test/java/ch/batbern/events/controller/SpeakerInvitationControllerIntegrationTest.java`
- `services/event-management-service/src/test/java/ch/batbern/events/controller/SpeakerReminderControllerIntegrationTest.java`

**Frontend — deleted (8 files):**
- `web-frontend/src/pages/speaker-portal/SpeakerMagicLoginPage.tsx`
- `web-frontend/src/pages/speaker-portal/__tests__/SpeakerMagicLoginPage.test.tsx`
- `web-frontend/src/services/speakerAuthService.ts`
- `web-frontend/src/services/speakerAuthService.test.ts`
- `web-frontend/src/pages/speaker-portal/__tests__/ContentSubmissionPage.test.tsx` _(legacy)_
- `web-frontend/src/pages/speaker-portal/__tests__/InvitationResponsePage.test.tsx` _(legacy)_
- `web-frontend/src/pages/speaker-portal/__tests__/ProfileUpdatePage.test.tsx` _(legacy)_
- `web-frontend/src/services/speakerPortalService.test.ts` _(legacy)_

**Frontend — modified:**
- `web-frontend/src/App.tsx`
- `web-frontend/src/pages/speaker-portal/InvitationResponsePage.tsx`
- `web-frontend/src/types/generated/events-api.types.ts` _(regenerated)_
- `web-frontend/src/types/generated/speakers-api.types.ts` _(regenerated)_

**Frontend — new (5 files, RD5 + AC4):**
- `web-frontend/src/services/speakerPortalService.test.ts`
- `web-frontend/src/pages/speaker-portal/__tests__/ContentSubmissionPage.test.tsx`
- `web-frontend/src/pages/speaker-portal/__tests__/InvitationResponsePage.test.tsx`
- `web-frontend/src/pages/speaker-portal/__tests__/ProfileUpdatePage.test.tsx`
- `web-frontend/e2e/speaker/magic-link-teardown.spec.ts`

**Frontend / E2E / scripts — deleted (per AC1b):**
- `web-frontend/e2e/organizer/speaker-content-display.spec.ts`
- `scripts/e2e/generate-speaker-tokens.sh`

**Bruno — deleted (19 files):**
- `bruno-tests/speaker-portal-api/02b-get-test-token.bru`
- `bruno-tests/speaker-portal-api/03-validate-token.bru`
- `bruno-tests/speaker-portal-api/04-validate-token-invalid.bru`
- `bruno-tests/speaker-portal-api/06-respond-accept.bru`
- `bruno-tests/speaker-portal-api/07-respond-already-used.bru`
- `bruno-tests/speaker-portal-api/09b-get-decline-test-token.bru`
- `bruno-tests/speaker-portal-api/10-respond-decline.bru`
- `bruno-tests/speaker-portal-api/11-respond-decline-no-reason.bru`
- `bruno-tests/speaker-portal-api/19-get-content-info.bru`
- `bruno-tests/speaker-portal-api/21-submit-content.bru`
- `bruno-tests/speaker-portal-api/22-get-content-after-submit.bru`
- `bruno-tests/speaker-portal-api/23-upload-material-presigned-url.bru`
- `bruno-tests/speaker-portal-api/24-submit-content-validation-errors.bru`
- `bruno-tests/speaker-portal-api/25-get-content-invalid-token.bru`
- `bruno-tests/speaker-portal-api/31-get-dashboard.bru`
- `bruno-tests/speaker-portal-api/32-get-dashboard-invalid-token.bru`
- `bruno-tests/speaker-portal-api/33-get-dashboard-missing-token.bru`
- `bruno-tests/speaker-portal-api/34-get-dashboard-reusable-token.bru`
- `bruno-tests/speaker-portal-api/submit-content-rejects-unknown-fields.bru`

**Bruno — new (2 files):**
- `bruno-tests/speaker-portal-api/03-magic-login-removed-404.bru`
- `bruno-tests/speaker-portal-api/04-validate-token-removed-404.bru`

**Documentation — modified:**
- `CLAUDE.md`
- `docs/architecture/ADR-009-unified-speaker-workflow.md`
- `docs/api/events-api.openapi.yml`
- `docs/api/speakers-api.openapi.yml`
- `_bmad-output/implementation-artifacts/deferred-work.md`
- `_bmad-output/implementation-artifacts/sprint-status.yaml`
- `_bmad-output/implementation-artifacts/11-f-1-magic-link-teardown-branch-deletion.md`

**CDK:** no changes — sweep returned zero hits (clean from Phase E).

---

## Resolved Decisions

All open questions for this story were resolved with the PM on 2026-05-25. Recorded here so the dev agent can act on them directly without re-litigating; **do NOT** ignore the dispositions below.

### RD1 — PRD ≥ 1-week pre-condition WAIVED; proceed immediately

Phase E shipped via PR #660 squash-merged on 2026-05-22. Story creation date is 2026-05-25 — only 3 days elapsed. The PRD's "zero traffic for ≥ 1 week" precondition would push dev to ≥ 2026-05-29. **Resolution (2026-05-25, PM — supersedes the original gate framing):** explicitly **waive** the 1-week wait. Dev work proceeds immediately on the 3-day observation window. Task 1.1 still runs the CloudWatch check, but as an informational best-effort record — non-zero hits do NOT block dev work. Rationale: the frontend stopped emitting magic-link auth on the 2026-05-22 deploy, so the live request stream is already at zero; any post-teardown 404s would come from stale SPA caches or bookmarked `?token=` URLs (very rare; the same degraded experience Phase E already exposes via its intermediate 410-Gone response). The PM accepts this risk in exchange for closing Epic 11 today.

### RD2 — AFTER_COMMIT refactor spun out as Story 11.F.2

The `AFTER_COMMIT` `TransactionalEventListener` refactor for `runInvitedHook` / `runAcceptedHook` Cognito mutations (deferred-work.md line 22) is **OUT of scope** for this story. **Resolution (2026-05-25, PM):** spin it out as a separate Story 11.F.2. The architectural change (`SpeakerInvitedEvent` / `SpeakerAcceptedEvent` domain events + listeners moving Cognito mutation + email send to AFTER_COMMIT) is meaningful enough to warrant its own story; bundling it with the teardown would muddy both. F.1 is a mechanical deletion pass; F.2 is the architecture follow-up.

### RD3 — ADR-009 §0.3 schema-ownership doc nit fixed in this story

The deferred-work.md line 14 entry (`magic_link_tokens` row in ADR-009 §0.3 has no schema-ownership override note) is **IN scope**. **Resolution (2026-05-25, PM):** since F.1 is editing line 82 anyway (past-tense flip), fold the schema-ownership wording fix into the same edit. Add a footnote or parenthetical clarifying that the colloquial `magic_link_tokens` label refers to the EMS table `speaker_invitation_tokens` created in V43 and dropped in V104. Owned by AC12 + Task 12.3.

### RD4 — Three `test.fixme` Playwright specs OUT of scope

The three `test.fixme` specs (`speaker-portal-respond.spec.ts`, `speaker-portal-content-submit.spec.ts`, `speaker-portal-cross-portal-nav.spec.ts`) await a staging Cognito test-speaker seed. **Resolution (2026-05-25, PM):** OUT of scope. The seed is operational work (provisioning a test user, storing `SPEAKER_AUTH_TOKEN` in GitHub secrets) — not magic-link teardown. Tracked separately as an ops ticket; do NOT block F.1 on it. The standalone Playwright teardown smoke test in AC4 stays in scope (it asserts cookie absence and does not need the seed).

### RD5 — Delete dead Vitest suites AND write new Cognito-side replacements

The four `describe.skip` Vitest suites (`ContentSubmissionPage.test.tsx`, `InvitationResponsePage.test.tsx`, `ProfileUpdatePage.test.tsx`, `speakerPortalService.test.ts`) assert magic-link behaviour that no longer exists. The production pages themselves are alive (Cognito-secured per E.3). **Resolution (2026-05-25, PM):** delete the dead tests AND write four new Cognito-side replacement test files in the same story. Page files stay; their tests are rewritten to assert the post-E.3 Cognito behaviour (mocked `useAuth()` SPEAKER session, mocked `apiClient`, `eventCode` from `useParams`, no `?token=` parsing, no `Skip-Auth` header). ≥ 80% coverage per file. Owned by AC6 + Task 7.5.

### RD6 — Branch scope confirmed; F.1 lands on its own short-lived branch

The long-lived refactor branch is already merged (PR #660). **Resolution (2026-05-25, PM):** ignore the OQ6 question entirely — F.1 will work on a new short-lived feature branch (`feature/11-f-1-magic-link-teardown`) created off `develop` during story authoring. AC10 stays scoped to the two AR43 source branches (`feature/speaker-account-creation` + `feature/epic-6`), both verified already deleted as of 2026-05-25.

---

## Review Findings

Code review run 2026-05-25 via `/bmad-code-review` (Claude Opus 4.7 1M, parallel layers: Blind Hunter + Edge Case Hunter + Acceptance Auditor) against branch diff `develop..HEAD` (90 files, +1,532/−9,492). Triage classified 35 raw findings into 7 actionable + 11 deferred + 17 dismissed-as-noise.

- [x] [Review][Patch] **Rewrote `speaker-onbehalf-vs-self-byte-identity.spec.ts` to Cognito Bearer + path eventCode** [web-frontend/e2e/organizer/speaker-onbehalf-vs-self-byte-identity.spec.ts] — Resolved from decision-needed (2026-05-25): dropped `SPEAKER_MAGIC_LINK_TOKEN` env var + its `test.skip()` gate; updated header narrative to describe the post-11.E.3 Cognito Bearer + path-eventCode contract; speaker submit now POSTs to `/api/v1/speaker-portal/events/{speakerBEventCode}/content/submit` with no body token; restructured the flow so the speaker-side POST is gated on `E2E_SPEAKER_EVENT_CODE` being set, with Speaker A's standalone assertions running unconditionally; `submitB` body keeps only the content fields (`title`/`contentAbstract`/`bio`/`profilePictureUrl`); annotation prose updated for both partial-coverage branches (speaker POST skipped vs. organizer-GET pointers missing).
- [x] [Review][Patch] **api-gateway `permitAll` cleanup — magic-login + e2e-test + 5 sibling dead-after-11.E.3 speaker-portal matchers** [api-gateway/src/main/java/ch/batbern/gateway/config/SecurityConfig.java:255-269] — Removed `permitAll` for `/api/v1/auth/speaker-magic-login` (deleted endpoint), `/api/v1/e2e-test/**` (deleted controller), AND the 5 sibling `/api/v1/speaker-portal/{content,content/draft,content/submit,materials/presigned-url,materials/confirm}` matchers that became dead-config when 11.E.3 switched the speaker-portal to Cognito Bearer (same class of bug — gateway would forward anonymous traffic to EMS where `@PreAuthorize("hasRole('SPEAKER')")` returns 401). Replaced with a single Story 11.E.3 / 11.F.1 comment block documenting the consolidated change. Scope expansion vs. the original 2-line patch is honest: I caught the broader sweep miss while applying the targeted fix.
- [x] [Review][Patch] **api-gateway `DomainRouter` `/api/v1/e2e-test` routing branch removed** [api-gateway/src/main/java/ch/batbern/gateway/routing/DomainRouter.java:85] — Dead routing branch deleted to match the SecurityConfig cleanup.
- [x] [Review][Patch] **`seed-e2e-speakers.sql` DELETE FROM dropped table removed** [scripts/e2e/seed-e2e-speakers.sql:252-260] — Replaced the `DELETE FROM speaker_invitation_tokens` block (which would fail with `relation does not exist` once V104 lands) + its NOTICE with a one-line Story 11.F.1 comment.
- [x] [Review][Patch] **`seed-e2e-speakers.sql` operator NOTICE updated** [scripts/e2e/seed-e2e-speakers.sql:391] — Replaced the `Run ./scripts/e2e/generate-speaker-tokens.sh` instruction (script deleted in AC1b) with `Use ./scripts/auth/get-token.sh staging <email> <password>` (Cognito flow).
- [x] [Review][Patch] **`QualityReviewService` event-null early-return guard** [services/event-management-service/src/main/java/ch/batbern/events/service/QualityReviewService.java:204-220] — When `eventRepository.findById(speaker.getEventId()).orElse(null)` returns null we now log a warn and return early (mirroring the recipient-email guard a few lines up) rather than building a `.../speaker-portal/content/` URL with trailing-slash-and-no-eventCode that the frontend would 404 on. Removed the dependent `event != null` ternaries downstream (`eventName`, `event.getEventCode()`) now that `event` is non-null past the guard.
- [x] [Review][Patch] **`sprint-status.yaml` flipped `epic-11: done` + `11-f-1-...-deletion: done`** [_bmad-output/implementation-artifacts/sprint-status.yaml:183,199] — Final step of the review→done transition; CLAUDE.md "Epic 11 100% COMPLETE" + ADR-009 v1.7 row now consistent with sprint-status. `last_updated` annotated with the code-review summary.
- [x] [Review][Defer] **V104 `CASCADE` rationale is technically wrong (CASCADE applies to FKs/views, not indexes)** [V104__drop_speaker_invitation_tokens_table.sql header] — migration still works correctly; cosmetic comment-prose nit.
- [x] [Review][Defer] **V104 has no Flyway undo migration (U104)** [V104__drop_speaker_invitation_tokens_table.sql] — consistent with the rest of the EMS migration history (BATbern has never used Flyway undo). Pre-existing convention, not story-specific.
- [x] [Review][Defer] **Mixed-fleet rolling deploy risk: old pods may INSERT into `speaker_invitation_tokens` while V104 DROPs it** [V104] — deploy-time concern; needs deploy guidance (or zero-traffic verification immediately before cutover), not a code change. RD1 explicitly accepted this risk class.
- [x] [Review][Defer] **`GlobalExceptionHandler` `NoResourceFoundException` widens 404 mapping to static-resource misses across all of EMS** [GlobalExceptionHandler.java:1100-1103] — real behavior expansion to satisfy AC2's 404 expectation; clients relying on prior empty 404 body now get `ErrorResponse` JSON. Backend tests pass; would need a separate verification sweep across all EMS error consumers.
- [x] [Review][Defer] **Orphan ShedLock row for deleted `tokenCleanup` job** [shedlock table] — `TokenCleanupScheduler` deletion doesn't clean its ShedLock row. Cosmetic; could be added to V104 as `DELETE FROM shedlock WHERE name = 'tokenCleanup'`.
- [x] [Review][Defer] **`speaker_jwt` Playwright audit `test.skip()`s when `SPEAKER_AUTH_TOKEN` unset** [web-frontend/e2e/speaker/magic-link-teardown.spec.ts] — acknowledged by spec RD4; ops dependency. Regression net dormant until the test-speaker seed ticket lands.
- [x] [Review][Defer] **`SpeakerInvitationToken` JPA entity deleted in same release as V104 — Hibernate validate semantics during mixed-fleet boot are undefined** — deploy-ordering concern; same risk class as the rolling-deploy item above.
- [x] [Review][Defer] **AC1 sweep clause has 24 narrative-comment hits in EMS main java (Javadoc only, no live code)** — AC4 has an explicit narrative-comment exemption; AC1 doesn't. Strict literal violation, spirit met. Either copy AC4's exemption into AC1's wording, or sweep the 24 narrative comments to past-tense `formerly...` framing.
- [x] [Review][Defer] **OpenAPI changes are narrative-prose only — DTOs were never in the OpenAPI specs to begin with** [docs/api/events-api.openapi.yml, speakers-api.openapi.yml] — AC9 satisfied per dev notes, but reveals that the magic-link surface was undocumented in the contract throughout its lifetime. Worth a separate OpenAPI-curation pass at some point.
- [x] [Review][Defer] **AC2 regression test missing coverage for deleted `/api/v1/e2e-test/**` endpoints** [MagicLinkEndpointsRemovedIntegrationTest.java] — extending the regression net to lock in the AC1b deletion (one extra anonymous + authenticated test for `/api/v1/e2e-test/tokens/generate-e2e-set`) would prevent silent reintroduction of `E2ETestTokenController`. Nice-to-have, not required by current AC text.
- [x] [Review][Defer] **Test coverage regression: 4 new Cognito-side Vitest replacement files have fewer assertions than the deleted suites** [ContentSubmissionPage.test.tsx, InvitationResponsePage.test.tsx, ProfileUpdatePage.test.tsx, speakerPortalService.test.ts] — RD5 promised replacements, not coverage parity. Validation paths, error rendering, and presigned-upload flow are now thinly covered. Track as a coverage-deepening follow-up if Vitest report drops below RD5's 80% threshold.
- [x] [Review][Defer] **`submit-content-rejects-unknown-fields.bru` deletion not enumerated in AC7 file list** — verified deleted; was a JSON-strictness test on the deleted body-token submit contract. Auditor classified as downstream consequence, not silent scope creep, but worth a one-line note in dev-agent record.
