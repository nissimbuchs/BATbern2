# Story 12.12: Import the Google Profile Picture into S3 on Federated Sign-up

Status: ready-for-dev

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

- [ ] **Task 1 — CDK: map + permit the `picture` attribute (AC: 1)** — *infrastructure, TDD*
  - [ ] RED: extend cognito-stack unit tests — Google IdP AttributeMapping contains `picture`; client ReadAttributes + WriteAttributes contain `picture`.
  - [ ] GREEN: `profilePicture: cognito.ProviderAttribute.GOOGLE_PICTURE` in attributeMapping; add to read/writeAttributes. Reference the F1b comment style (cognito-stack.ts:297-307) explaining WHY writeAttributes is required.
- [ ] **Task 2 — Migration `V{next}` + entity field (AC: 4)** — *CUMS*
- [ ] **Task 3 — Import service + interceptor hook (AC: 2, 3, 5)** — *CUMS, TDD*
  - [ ] RED: the five ITs from AC7.
  - [ ] GREEN: `FederatedAvatarImportService` (thin orchestrator: guard conditions → mark attempt → async fetch via `ProfilePictureService.uploadProfilePictureDirectly` internals → set URL/key). Interceptor reads `picture` claim from the JWT (`Jwt#getClaimAsString("picture")`). Non-blocking + async per AC3.
  - [ ] Guard against SSRF drift: only fetch URLs whose host ends in `googleusercontent.com` (the claim is attacker-influencable in principle; tighten to the known host).
- [ ] **Task 4 — cdn-URL assertion + display verification (AC: 6)**
- [ ] **Task 5 — deploy order note + manual smoke** — infra (cognito-stack) deploys first; then CUMS. Manual smoke: fresh Google sign-in (or existing federated user with no picture and no prior attempt) → picture appears on `/profile` within one request cycle; verify object exists under `profile-pictures/` in `batbern-content-staging`.
- [ ] **Task 6 — docs (AC: 8)**

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

### Debug Log References

### Completion Notes List

### File List
