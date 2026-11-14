---
title: 'Upload purpose profile-picture'
type: 'refactor'
ticket: '11a'
created: '2026-11-13'
status: 'built'
baseline_revision: '130d70523'
route: 'full'
route_source: 'auto'
risk: 'medium'
review: 'quick'
review_source: ''
lenses_ran: []
review_loop_iteration: 0
context:
  - '{project-root}/docs/architecture/ADR-002-generic-file-upload-service.md'
  - '{project-root}/_bmad-output/initiative-architecture-hardening/tickets/epic-api-conventions-and-uploads/tickets/story-one-upload-contract-first-purpose-company-logo-plan.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Profile pictures still use their own presign, confirm and upload-from-url operations in CUMS (`ProfilePictureService`). The presign writes directly to the final key. Confirm rebuilds that key from the current year and never checks that the file is in S3, so a picture uploaded on 31 Dec and confirmed on 1 Jan points at a missing object. There is also no pending state and no expiry.

**Approach:** Breaking, no aliases. Purpose `profile-picture` (CUMS) goes on the 17.11 contract, with target `username`. The organizer or the user themself may attach (ownership rule `UserOwnership`). Import is for organizers only (speaker batch import: `import`, then `confirm` with the target). The three old operations are deleted along with their frontend callers, Bruno and Playwright. `DELETE /users/{username}/picture` stays. Owner go 2026-11-13: 11a to 11d ship in one PR.

## Boundaries & Constraints

**Always:** Spec first (`uploads-api` purpose enum + `UploadTarget.username`, `users-api` without the 3 operations), regenerate types. New final keys carry no year (owner 2026-11-13: the upload year means nothing and caused the rollover bug): `profile-pictures/{username}/profile-{uploadId}.{ext}`, and the 17.11 purposes too: `logos/companies/{name}/logo-{uploadId}.{ext}`, `logos/events/{eventCode}/theme-{uploadId}.{ext}`. Existing objects, keys and URLs are untouched (rows store the full key and URL). Test fidelity rules: S3, the clock, Cognito and SES are the only fakes, and the screens and hooks touched run on MSW. Every removed path is pinned by an IT to the status it now returns. One heavy job at a time on rack; logs go to `~/.cache/batbern-test-logs/` via `tee` with `set -o pipefail`.

**Never:** No change to the Google avatar import beyond its key and URL helper (`FederatedAvatarImportService` stays server-side, no upload row). No copy of old picture data. No push or PR before 11b to 11d are built and the owner has said go.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected | Error |
|---|---|---|---|
| Own picture | speaker: `POST /uploads/profile-picture {target:{username:self}}`, PUT, confirm with target | ASSOCIATED, `profilePictureUrl` set, shown after reload | png/jpeg/svg only, ≤ 5 MB: else 400 |
| Other user's picture | non-organizer, target another username | — | 403 on create and confirm |
| Organizer | target any existing user | ASSOCIATED | unknown username: 404 |
| No target | create without target | — | 400 (purpose requires a target) |
| Batch import | organizer `import {url}`, then confirm `{target:{username}}` | ASSOCIATED, picture set | non-organizer: 403; non-HTTPS: 400; unreachable: 502 |
| Confirm before PUT | file not in S3 | — | 409 |
| Old paths | `POST /users/{u}/picture/presigned-url`, `…/picture/confirm`, `…/profile-picture/upload-from-url` | pinned status (404 or 405) | — |

</frozen-after-approval>

## Code Map

- `docs/api/users-api.openapi.yml` -- ops at L647 (`uploadProfilePictureFromUrl`), L2665 (`requestProfilePictureUploadUrlForUser`), L2732 (`confirmProfilePictureUploadForUser`) and their schemas (`AdminUploadProfilePictureFromUrl*` L2907/L2919, `ProfilePictureUpload*`) are deleted. DELETE L2786 stays.
- `docs/api/uploads-api.openapi.yml` -- purpose table L24-27, enums L206/L213, `UploadTarget` (add `username`, maxLength 100).
- `shared-kernel/.../upload/UploadPurposeHandler.java` -- add `default boolean requiresTarget() { return false; }`. `UploadService.create`/`confirm` answer 400 when it is required and missing. Unit test in `UploadServiceTest`.
- CUMS `controller/UserController.java` -- remove L414 presign, L437 confirm, L504 upload-from-url. Keep L462 delete (`ProfilePictureApi` regenerates).
- CUMS `service/ProfilePictureService.java` -- remove presign/confirm/`findS3KeyByFileId`. Keep `uploadProfilePictureDirectly` (avatar import) and the key builder, which is shared with the new handler.
- No year in keys: CUMS `CompanyLogoUploadHandler.finalKey` L71-73, EMS `EventThemeImageUploadHandler.finalKey` L74-77, the profile-picture key builder (also used by `FederatedAvatarImportService`, Javadoc L28). Their tests that pin the year are updated.
- CUMS `controller/UploadsController.java` `target()` -- add `username`. EMS `UploadsController.target()` also maps it (the generated DTO is shared by the spec).
- New CUMS `service/ProfilePictureUploadHandler.java` -- pattern `CompanyLogoUploadHandler`. `targetKey "username"`, `targetType "USER"`, `requiresTarget true`. `checkTarget`: ORGANIZER or `userOwnership.isCurrentUser` (403), user exists (404). Temp key `profile-pictures/temp/{id}/profile-{id}.{ext}`. `attach` sets `profilePictureUrl` and `profilePictureS3Key`. Import for ORGANIZER.
- `api-gateway/.../routing/DomainRouter.java` L131 `uploadService` -- an explicit purpose→service map replaces the prefix rule (see Design Notes). Unknown purpose: 404. `DomainRouterTest` L76-101.
- Frontend `services/api/userAccountApi.ts` L211-310 -- `uploadProfilePicture` runs on `uploadTarget('profile-picture', {username})` (presign, XHR PUT kept for progress, confirm). Drop `requestPresignedUrl`/`confirmUpload`. Callers `useUserAccount.ts` L125, `ProfilePhotoUpload.tsx` L131, `UserCreateEditModal.tsx` L215.
- `services/api/userApi.ts` L81-87 `uploadProfilePictureFromUrl` -- replaced by `importUpload('profile-picture', url)` + confirm with target. Caller `useSpeakerBatchImport.ts` L57.
- Tests: CUMS `UserControllerIntegrationTest` L630-739/L1453-1568, `UserProfileOwnershipIntegrationTest` L273-361 (move to the new contract), `ProfilePictureServiceTest`. FE `ProfilePhotoUpload.test`, `useUserAccount.test`, `userAccountApi.test`, `userApi.test` (MSW). `useSpeakerBatchImport.test` and `UserCreateEditModal.test` go from axios-mock-adapter to MSW (touch rule). Bruno `users-api/10–13` (two disabled, two hit presigned-url) are rewritten on `/uploads/profile-picture`. Playwright `user-account/photo-upload.spec.ts`.
- Baselines: Spectral 91 lines; N1 = lines 33, 34.

## Tasks & Acceptance

**Execution:**
- [x] Specs + `npm run generate:api-types` -- contract first.
- [x] Shared-kernel `requiresTarget` + `UploadServiceTest` (RED first).
- [x] CUMS: RED ITs `ProfilePictureUploadIntegrationTest` (matrix rows, old paths pinned), then the handler, `UploadsController` target, removal from `UserController`/`ProfilePictureService`. Move the picture tests in the two ITs onto the contract.
- [x] Gateway: map + `DomainRouterTest` (`profile-picture` → CUMS).
- [x] Frontend: services, hooks, components; tests on MSW; `npm run check:api-paths`.
- [x] Bruno `users-api` picture files rewritten (presign, invalid type, too large, other user 403 not testable with one identity: IT covers it). Prose in `docs {}`. Playwright `photo-upload.spec.ts` updated.
- [x] Baselines: `npm run lint:api -- --update` (91 → 89), remove N1 from `api-lint-owners.txt`, `make check-test-ratchet`.
- [x] Docs: ADR-002 Implementation Status (purpose table), `uploads-api` description, doc-drift mapped docs.

**Acceptance Criteria:**
- Given local dev, when a speaker changes their own picture, an organizer changes another user's, and an organizer runs a speaker batch import with a picture URL, then each picture shows after a reload, and the network log shows only `/uploads/profile-picture*` calls for it.
- Given `npm run lint:api`, when it runs, then the N1 lines are gone and the baseline is 2 lines smaller.

## Implementation Notes

- Built 2026-11-13 on `feature/17-4-11-upload-purposes`, not committed. TDD order: the shared-kernel `requiresTarget` test and the `DomainRouterTest` lines were written first and seen RED. `ProfilePictureUploadIntegrationTest` was NOT written first: it was written after `ProfilePictureUploadHandler`, `UploadsController` and the `UserController` removal, and then run (one failure, a 403 on `GET /users/{username}` by a speaker in the test itself, fixed in the test). The Execution item "RED ITs, then the handler" was therefore not followed as written.
- Open gap, not closed by 11a: create and confirm check their target independently, and nothing binds the confirm to the target (or the caller) the upload was reserved for. An upload reserved for user X can be confirmed by another user with target = themself (their own target check passes), which puts the file meant for X on their own profile; an organizer can equally confirm a reserve for X onto user Y. Each target check still holds per call (no one attaches where they may not), but the reserve→confirm binding is missing. It is closed by 11b's shared target binding in `UploadService` (store the target on create, require the same one on confirm), not by 11a.
- Review fixes (coordinator, 2026-11-13): `03-data-architecture.md` key bullets and the stale "not yet on the contract" bullet, `02-infrastructure-deployment.md` key shapes (new objects without year, old keys keep it), the stale upload-from-url Javadoc in `AvatarImportConfig` and `ImageUrlFetcher`, and Bruno `users-api/14b`–`14e` (import, confirm with the own username, read `/users/me`, DELETE). The chain runs only on `development` and only when 01 saw no picture; outside local dev it is skipped because in the deploy run the collection hits production, and a DELETE clears only the user row, not the S3 object. Ran on local dev in both states: chain green with no prior picture, all four skipped with one (picture unchanged).
- Old paths measured: all three answer 404 in CUMS (no template matches), also `/users/me/picture/presigned-url` and `/confirm` (the ownership IT pinned them to 403/400 before; now 404 for everyone). `DELETE /users/me/picture` keeps 403/400.
- `uploadService.ts` gained `confirmUpload(purpose, uploadId, target?)`; `uploadTarget().confirm` uses it, `userApi.importProfilePicture` = `importUpload` + `confirmUpload`. `userAccountApi.uploadToS3` now sends the signed `requiredHeaders`. Dead `Logo*` upload types in `userAccount.types.ts` removed.
- Spec floors in `specMatcher.test.ts` and `msw/examples.test.ts` lowered (operations 278, JSON 2xx 242 measured).
- Year dropped from all final keys: `CompanyLogoUploadHandler.logoKey` (also used by the logo request approve), `EventThemeImageUploadHandler.finalKey`, `ProfilePictureService.pictureKey` (handler and Google avatar import).
- Docs: ADR-002 (purpose table + 17.11a section), `uploads-api`/`users-api` descriptions, `docs/api/README.md`, `01-system-overview`, `03-data-architecture`, `04-api-user-management`, `06-backend-architecture`, `06b-user-lifecycle-sync`, user-guide changelog.

## Plan Change Log

## Review Triage Log

## Design Notes

**Architecture note.** Modules: shared-kernel `upload` (one SPI default), CUMS users, api-gateway routing, frontend services and hooks. New dependencies: the CUMS handler uses `UserOwnership` and `UserRepository`, both in CUMS. New endpoints: none; `profile-picture` is a new value of the existing `/uploads/{purpose}` (nearest: `company-logo`). Reused: `UploadService`, `ImageUrlFetcher`, `uploadTarget`/`importUpload`, `UserOwnership`. Deviation: none.

**Routing by map, not prefix.** `profile-picture`, `teaser-image` and `session-material` (11a, 11c, 11d) have no `event-`/`company-` prefix. Renaming them would contradict the ticket names. An explicit `Map<String,String>` of purpose to service in `DomainRouter` is one line per purpose and fails closed. The spec text "routed by the purpose prefix" becomes "by purpose".

**Cleaner.** Removed: 3 operations, ProfilePictureService presign/confirm, two frontend presign/confirm helpers. Fixed: confirm without an S3 check; the year-rollover bug is gone because keys no longer carry a year (all purposes, owner 2026-11-13). Pictures get a pending state and the expiry job. Spectral 91 → 89.

## Verification

**Commands:**
- `./gradlew :shared-kernel:test :services:company-user-management-service:test :api-gateway:test --max-workers=4` (one at a time) -- green
- `cd web-frontend && npm run type-check && npx vitest run --maxWorkers=4 && npm run lint:api && npm run check:api-paths` -- green
- `make check-test-ratchet` -- no RAISED
- Bruno `users-api` against local dev; Playwright `user-account/photo-upload.spec.ts` -- green

**Manual checks:**
- Local dev: the walkthrough in the first AC.
