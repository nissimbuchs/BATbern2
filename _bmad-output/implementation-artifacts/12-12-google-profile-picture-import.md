# Story 12.12: Import the Google Profile Picture into S3 on Federated Sign-up

Status: review

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **user signing in with Google**,
I want **my Google account photo to automatically become my BATbern profile picture (stored on the platform, served from cdn.batbern.ch)**,
so that **my profile looks complete from the first login without a manual upload — just like my first/last name already arrive from Google.**

Google's OIDC `picture` claim provides a photo URL on every federated sign-in, but today it is dropped: the Cognito Google IdP `attributeMapping` (cognito-stack.ts:231-241) maps only `email`/`givenName`/`familyName`. Even if mapped, hotlinking `googleusercontent.com` URLs is wrong — they rotate/expire and leak the dependency. The right shape: map the claim, then **fetch once server-side and store in our own S3** via the import machinery CUMS already has (`ProfilePictureService.uploadProfilePictureDirectly`, used by the existing admin `upload-from-url` endpoint at UserController.java:790-870 — content-type + 5MB validation included).

**Prerequisite/sequencing:** independent of 12.11 (can ship in either order). Infra change (cognito-stack) deploys via the normal pipeline.

## Acceptance Criteria

1. **(Cognito mapping — picture claim flows into the pool.)** `infrastructure/lib/stacks/cognito-stack.ts`: add `profilePicture: cognito.ProviderAttribute.GOOGLE_PICTURE` to the Google IdP `attributeMapping` (after `familyName`, line 240). **Heed the 12.8-F1b lesson:** an IdP-mapped attribute is *silently dropped* unless the user-pool client can write it — add `profilePicture` to BOTH the client `readAttributes` AND `writeAttributes` lists (cognito-stack.ts:294-296 / 305-307; `picture` is a built-in OIDC standard attribute like `given_name` — no `standardAttributes` declaration needed on the pool, same pattern as the line-153-160 comment documents). CDK unit tests assert the mapping + both attribute lists (extend the existing cognito-stack tests that already cover the F1b fix).

2. **(One-time server-side import in CUMS, keyed off the JWT claim.)** When an authenticated request carries an ID-token `picture` claim AND the user's `profile_picture_url IS NULL`, CUMS imports the picture **once**: fetch the URL server-side, validate (image/* content type, ≤5MB — reuse the existing validation in `uploadProfilePictureDirectly`, ProfilePictureService.java:225-275), store under the existing key convention `profile-pictures/{year}/{username}/profile-{uuid}.{ext}` (ProfilePictureService.java:166-172), set `profile_picture_url` (CloudFront URL via `CloudFrontUrlBuilder`) + `profile_picture_s3_key`. Implementation hook: the natural place is alongside `JITUserProvisioningInterceptor` (it already inspects the JWT per request and has the non-blocking pattern) — either extend it or add a sibling `FederatedAvatarImportInterceptor`; pick whichever keeps JIT single-purpose, but REUSE `ProfilePictureService` (no new S3/fetch code).

3. **(Never clobber, never loop.)**
   - Import runs ONLY when `profile_picture_url IS NULL` — a user-uploaded or previously imported picture is never overwritten.
   - If the user **deletes** their picture later (`DELETE /api/v1/users/me/picture`, UserController.java:657), the next federated request would re-import. Prevent that: record import-or-deletion state (e.g. a `picture_import_attempted_at TIMESTAMP NULL` column in the same migration, set on first attempt — success OR failure — and never retried). Deliberate simple semantics: one attempt per user, ever.
   - Failures (Google URL 403/timeout/oversize) are logged + swallowed (same non-blocking contract as JIT, JITUserProvisioningInterceptor.java:186-192) — a broken avatar fetch must NEVER fail the user's API request. Per-request latency budget: the fetch runs async (separate executor / `@Async`) so the triggering request is not delayed.

4. **(DB migration.)** CUMS Flyway `V{next}` (V18 if 12.11's V17 lands first — take the next free number at implementation time): add `picture_import_attempted_at TIMESTAMP NULL` to `user_profiles`. Forward-only.

5. **(Native users unaffected.)** Native sign-ins have no `picture` claim → zero behavior change. Asserted by an integration test (JWT without claim → no import attempt recorded).

6. **(Display path requires no change — verify, don't build.)** `profile_picture_url` is already rendered (e.g. `useUserPortrait.ts:46-83`, profile page `ProfilePhotoUpload`); imported pictures get image-resize/WebP treatment via the existing cdn.batbern.ch Lambda@Edge automatically (storage-stack.ts:152-256). Add one assertion-level check (IT or unit) that the stored URL is a `cdn.batbern.ch`-domain CloudFront URL, not a googleusercontent URL.

7. **(Tests.)**
   - CDK: mapping + read/writeAttributes assertions (AC1).
   - CUMS ITs (Testcontainers, `AbstractIntegrationTest`): claim + NULL picture → S3 put invoked (mock S3Client) + both columns set; claim + existing picture → no-op; claim + prior `picture_import_attempted_at` → no-op; fetch failure → attempt recorded, request still 200, no picture set; no claim → nothing.
   - Async executor behavior unit-tested (import does not block request thread).

8. **(Docs, same commit.)** Annotate `docs/plans/sso-oidc-federation.md` follow-ups and `docs/architecture/06b-user-lifecycle-sync.md` (avatar import in the federated lifecycle). OpenAPI: no API surface change expected (import is implicit); if any response field is added, spec-first + regenerate types.

## Tasks / Subtasks

- [x] **Task 1 — CDK: map + permit the `picture` attribute (AC: 1)** — *infrastructure, TDD*
  - [x] RED: extend cognito-stack unit tests — Google IdP AttributeMapping contains `picture`; client ReadAttributes + WriteAttributes contain `picture`.
  - [x] GREEN: `profilePicture: cognito.ProviderAttribute.GOOGLE_PICTURE` in attributeMapping; add to read/writeAttributes. Reference the F1b comment style (cognito-stack.ts:297-307) explaining WHY writeAttributes is required.
- [x] **Task 2 — Migration `V{next}` + entity field (AC: 4)** — *CUMS* — took **V18** (12-11 claims V17 in parallel)
- [x] **Task 3 — Import service + interceptor hook (AC: 2, 3, 5)** — *CUMS, TDD*
  - [x] RED: the five ITs from AC7 (plus a 6th: SSRF-guard IT, and a 7th: fresh-federated same-request import proving interceptor ordering). RED run: 10/18 failed against skeleton.
  - [x] GREEN: `FederatedAvatarImportService` (thin orchestrator: guard conditions → mark attempt → async fetch → `ProfilePictureService.uploadProfilePictureDirectly`). Sibling `FederatedAvatarImportInterceptor` (JIT stays single-purpose) registered AFTER JIT in WebMvcConfig. Plain injected `Executor` (`avatarImportExecutor`, `@ConditionalOnMissingBean` override pattern) instead of `@Async` self-proxy — directly unit-testable non-blocking behavior. All 18 green.
  - [x] Guard against SSRF drift: https-only + host == `googleusercontent.com` or subdomain (endsWith dot-check); `=s96-c` → `=s512-c` variant upgrade. 8 unit tests.
- [x] **Task 4 — cdn-URL assertion + display verification (AC: 6)** — cdn-URL assertion folded into IT #1 (`startsWith("https://cdn.batbern.ch/profile-pictures/")` + `doesNotContain("googleusercontent")`). Display path verified unchanged: `useUserPortrait.ts:72-83` re-projects `profilePictureUrl`; `ProfilePhotoUpload.tsx:246` renders it as `<img src>`; image-resize Lambda@Edge applies automatically (cdn domain).
- [x] **Task 5 — deploy order note + manual smoke** — infra (cognito-stack) deploys first; then CUMS. **Note:** a single `develop` push satisfies the ordering automatically — the pipeline detects infra changes and uses layer-based deployment, where Layer 3 (Cognito) deploys before Layer 4 (services). `UpdateUserPoolClient` read/write-attribute changes are in-place (no replacement); `picture` is a built-in standard attribute so there is NO pool Schema change (the PR #735 "Invalid AttributeDataType" trap is avoided by design + regression-tested). **Manual smoke (post-deploy, user-side):** fresh Google sign-in (or existing federated user with no picture and no prior attempt) → picture appears on `/profile` within one request cycle; verify object exists under `profile-pictures/` in `batbern-content-staging`.
- [x] **Task 6 — docs (AC: 8)** — `docs/plans/sso-oidc-federation.md` §9 (post-GA follow-ups, 12.12 delivered) + `docs/architecture/06b-user-lifecycle-sync.md` Pattern 1c (federated avatar import in the lifecycle). OpenAPI: no surface change — the import is implicit; no response field added; no type regen needed. 06-backend-architecture / 08-operations-security checked: no profile-picture/interceptor content to drift.

## Dev Notes

### Why this shape
- **Fetch-once-and-own** beats hotlinking: googleusercontent URLs rotate, are sized-variant-encoded (`=s96-c` suffix — strip or request a larger variant, e.g. replace with `=s512-c`, before fetching; test with a real claim value), and create a third-party dependency on every page render.
- **One-attempt-ever semantics** (`picture_import_attempted_at`) is deliberately dumb: it avoids re-import-after-delete loops AND repeated fetch attempts for users whose Google photo is broken. A richer "sync from Google" feature can supersede it later.
- **Interceptor placement**: PostConfirmation Lambda was considered (it creates the federated row) but rejected — it would need outbound fetch + S3 + size-validation logic duplicated in TypeScript inside a VPC Lambda, while CUMS already has ALL of it in `ProfilePictureService`. The JWT carries the claim on every request once AC1 is deployed, so the CUMS-side hook is strictly simpler. (Note: `picture` lands in the ID token; CUMS validates the access token in some flows — VERIFY which token the resource servers see. If the access token lacks the claim, read it via the PreTokenGeneration Lambda passthrough or from `GET /oauth2/userInfo` — check `JwtRolesConverter`/gateway to see which token reaches services before committing to the claim-read approach. This is the story's main open verification.)

### Existing-code ground truth
- `infrastructure/lib/stacks/cognito-stack.ts:208-242` (IdP + mapping), `:294-307` (read/writeAttributes + F1b comment), `:148-161` (standardAttributes pattern note).
- `services/.../service/ProfilePictureService.java` — `uploadProfilePictureDirectly` (225-275), key convention (166-172), CloudFront URL build (149, 267), validation (PNG/JPG/JPEG/SVG, 5MB).
- `services/.../controller/UserController.java:790-870` — existing server-side fetch endpoint (HttpClient fetch, content-type + size checks) = the pattern to extract/reuse.
- `services/.../interceptor/JITUserProvisioningInterceptor.java` — JWT-claim reading + non-blocking catch-all pattern (186-192).
- `infrastructure/lib/stacks/storage-stack.ts:72-123` (content bucket + CORS), `:152-256` (image-resize Lambda@Edge, WebP, cache).
- `web-frontend/src/hooks/useUserPortrait.ts:46-83` — display path (no FE change expected).
- 12.8 story file — F1b root cause write-up (writeAttributes silently gates IdP mapping): `_bmad-output/implementation-artifacts/12-8-federated-provisioning-inactive-gating-verify.md`.

### Project Structure Notes
- New service class in `ch.batbern.companyuser.service`; interceptor in `ch.batbern.companyuser.interceptor`; follow CUMS layering (no repository access from mapper, `@Transactional` boundaries in service).
- Async executor: check whether CUMS already configures one (`@EnableAsync`/TaskExecutor bean) before adding config.

### References
- [Source: docs/plans/sso-oidc-federation.md], [Source: docs/architecture/ADR-010-federated-identity-via-cognito.md]
- [Source: _bmad-output/implementation-artifacts/12-5-google-idp-attribute-mapping.md] (mapping constraints: 1 claim → 1 attr)
- [Source: docs/api/users-api.openapi.yml] (PublicUserResponse.profilePictureUrl)

## Dev Agent Record

### Agent Model Used

Claude Opus 4.8 (1M context) via bmad-dev-story, 2026-06-04

### Implementation Plan / Key Decisions

1. **Story's main open verification RESOLVED first**: the **ID token** reaches CUMS — `web-frontend/src/services/api/apiClient.ts:38` puts `session.tokens.idToken` in the `Authorization` header, and the JIT interceptor already reads ID-token-only claims (`email`/`given_name`/`family_name`/`custom:preferences`) in production. `Jwt#getClaimAsString("picture")` therefore works directly; no PreTokenGen passthrough / userInfo call needed.
2. **Sibling interceptor** (`FederatedAvatarImportInterceptor`), not a JIT extension — keeps JIT single-purpose per the story's guidance. Registered AFTER JIT in `WebMvcConfig` (interceptor order = registration order) so the first-ever federated request both provisions the row AND imports the avatar (covered by a dedicated IT).
3. **Plain injected `Executor` instead of `@Async`**: no self-proxy magic, directly unit-testable non-blocking behavior (a capturing executor proves zero network I/O on the request thread). `avatarImportExecutor` (1-2 threads, queue 50) + `avatarFetchHttpClient` beans use `@ConditionalOnMissingBean(name=...)` so `TestAwsConfig` overrides them with `SyncTaskExecutor` + Mockito mock — same override pattern as the existing AWS client mocks. Sync executor in ITs is REQUIRED (a real pool thread could not see uncommitted `@Transactional` test data).
4. **Mark-attempt-BEFORE-dispatch**: `picture_import_attempted_at` is saved synchronously before URL validation + fetch dispatch — failure paths (bad host, 403, oversize, timeout) are naturally recorded with no extra writes, and async-thread snapshots can never resurrect a NULL timestamp (no surrounding TX in the interceptor path → save commits immediately). Worker re-checks `profilePictureUrl IS NULL` right before the S3 write (never clobber a picture uploaded between mark and async execution).
5. **Migration is V18, not V17**: Story 12.11 (developed in parallel, same branch) claims V17. Flyway gaps are harmless if 12.11 were abandoned; both ship on the same branch so out-of-order application cannot occur.
6. **DELETE /users/me/picture untouched**: marking on import-attempt alone satisfies "never re-import after delete" for imported pictures (attempt already recorded). A user-uploaded-then-deleted picture may trigger ONE import afterwards — consistent with one-attempt-ever semantics, smaller blast radius.

### Debug Log References

- `/tmp/12-12-cdk-red.log` / `/tmp/12-12-cdk-green.log` — CDK TDD cycle (RED: mapping missing; GREEN: 12/12)
- `/tmp/12-12-red.log` — CUMS RED run: 10/18 failed against skeleton (negatives pass by design)
- `/tmp/12-12-green.log` — CUMS GREEN run: 18/18
- `/tmp/12-12-cums-full.log` — full-suite run #1: 39 failures in `LogoControllerTest`/`CompanyControllerTest` (`@WebMvcTest` slices missing the new `WebMvcConfig` dependency) → fixed with `@MockitoBean FederatedAvatarImportService` (same pattern as the existing JIT `UserRepository` mock)
- `/tmp/12-12-cums-full2.log` — full CUMS suite + checkstyle after fix
- `/tmp/12-12-infra-full.log` — full infrastructure jest suite

### Completion Notes List

- AC1 ✅ Cognito: `profilePicture: GOOGLE_PICTURE` mapped + `picture` in client read/write attributes (F1b lesson applied); 2 new CDK tests incl. a Schema-regression guard (PR #735 trap).
- AC2 ✅ One-time server-side import keyed off the ID-token `picture` claim; reuses `ProfilePictureService.uploadProfilePictureDirectly` (validation + key convention + CloudFront URL) — no new S3 code.
- AC3 ✅ Never clobber (guard + pre-write re-check), never loop (`picture_import_attempted_at` one-attempt-ever), non-blocking (interceptor catch-all + dedicated executor; fetch never on request thread).
- AC4 ✅ V18 forward-only migration (TIMESTAMPTZ, matching user_profiles conventions).
- AC5 ✅ Native JWTs (no claim) exit before any DB access; IT asserts no attempt recorded.
- AC6 ✅ IT asserts stored URL `startsWith https://cdn.batbern.ch/profile-pictures/` + `doesNotContain googleusercontent`; display path verified unchanged (`useUserPortrait.ts`, `ProfilePhotoUpload.tsx:246`).
- AC7 ✅ 2 CDK tests, 7 ITs (5 story scenarios + SSRF-guard + fresh-federated same-request), 11 unit tests (8 URL-guard + 3 async hand-off).
- AC8 ✅ Plan §9 + 06b Pattern 1c; OpenAPI unchanged (implicit import, no new fields).
- SSRF guard: https-only, host == `googleusercontent.com` or subdomain; `=s96-c` → `=s512-c` upgrade before fetch.
- **Post-deploy manual smoke pending (user-side)**: fresh Google sign-in → picture on `/profile`; S3 object under `profile-pictures/` in `batbern-content-staging`. Note: existing federated users (e.g. Nissim's earlier sign-ins) qualify if they have no picture and no prior attempt — their next API request after both deploys triggers the import.

### File List

- `infrastructure/lib/stacks/cognito-stack.ts` — modified (IdP attributeMapping + client read/writeAttributes)
- `infrastructure/test/unit/cognito-stack.test.ts` — modified (2 new tests)
- `services/company-user-management-service/src/main/resources/db/migration/V18__add_picture_import_attempted_at.sql` — new
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/domain/User.java` — modified (pictureImportAttemptedAt field)
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/FederatedAvatarImportService.java` — new
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/interceptor/FederatedAvatarImportInterceptor.java` — new
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/config/AvatarImportConfig.java` — new
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/config/WebMvcConfig.java` — modified (register avatar interceptor after JIT)
- `services/company-user-management-service/src/test/java/ch/batbern/companyuser/config/TestAwsConfig.java` — modified (sync executor + mock HttpClient beans)
- `services/company-user-management-service/src/test/java/ch/batbern/companyuser/integration/FederatedAvatarImportIntegrationTest.java` — new (7 ITs)
- `services/company-user-management-service/src/test/java/ch/batbern/companyuser/service/FederatedAvatarImportServiceTest.java` — new (11 unit tests)
- `services/company-user-management-service/src/test/java/ch/batbern/companyuser/controller/LogoControllerTest.java` — modified (@MockitoBean for new WebMvcConfig dep)
- `services/company-user-management-service/src/test/java/ch/batbern/companyuser/controller/CompanyControllerTest.java` — modified (@MockitoBean for new WebMvcConfig dep)
- `docs/plans/sso-oidc-federation.md` — modified (§9 post-GA follow-ups)
- `docs/architecture/06b-user-lifecycle-sync.md` — modified (Pattern 1c)
- `_bmad-output/implementation-artifacts/12-12-google-profile-picture-import.md` — story file (tasks/record)
- `_bmad-output/implementation-artifacts/sprint-status.yaml` — status updates

## Change Log

- 2026-06-04: Story 12.12 implemented end-to-end (CDK picture mapping, V18 migration, one-time async avatar import service + interceptor, 20 new backend tests + 2 CDK tests, docs). Token-type open question resolved: ID token reaches CUMS. Migration numbered V18 (12.11 holds V17 in parallel).
