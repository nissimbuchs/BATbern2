---
title: 'Upload purpose teaser-image'
type: 'refactor'
ticket: '11c'
created: '2026-11-13'
status: 'done'
baseline_revision: '59c369b89'
route: 'full'
route_source: 'auto'
risk: 'low'
review: 'quick'
review_source: ''
lenses_ran: []
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Teaser images (per event and global) use their own presign and `POST /events/{eventCode}/teaser-images/confirm` in `EventTeaserImageService`. The client sends back the S3 key, and there is no size limit and no pending state.

**Approach:** Breaking, no aliases. EMS gets purpose `teaser-image` on the 17.11 contract, with target `eventCode` (required). `_global` names the global set, as the existing paths do. Organizers only. Confirm with the target creates the `event_teaser_images` row, and the limit of 10 per scope is checked at reserve time and again on attach. List, PATCH and DELETE stay. The old presign and confirm are deleted along with their frontend callers. The 11b target binding refuses a confirm against another event.

## Boundaries & Constraints

**Always:** Types stay jpeg/png/webp/svg. A size limit is new, because presign signs a size: 10 MB, the photo limit (see Design Notes). Final keys stay `events/{ec}/teaser/{uploadId}.{ext}` and `global/teaser/{uploadId}.{ext}`, and `imageUrl` is built via `CloudFrontUrlBuilder` as today. Event cache eviction as today. The 11a rules apply.

**Never:** No change to existing rows, PATCH/DELETE or the presentation order.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected | Error |
|---|---|---|---|
| Event teaser | organizer, target `{eventCode}` | row, shown on the event page | type: 400; > 10 MB: 400 |
| Global teaser | target `{eventCode:"_global"}` | row with null event code | — |
| Limit | 10 images in scope already | — | 409 at reserve (and at attach) with the existing limit error |
| Other event | reserved for A, confirmed for B | — | 404 |
| Old paths | presign, confirm | pinned status | — |

</frozen-after-approval>

## Code Map

- `docs/api/events-core-api.openapi.yml` -- delete L1760 `generateTeaserImageUploadUrl` and L1799 `confirmTeaserImageUpload`. Keep L1725 list and L1845 PATCH/DELETE.
- `docs/api/uploads-api.openapi.yml` -- `teaser-image`; the `eventCode` description mentions `_global`.
- EMS `controller/EventTeaserImageController.java` (`GLOBAL_CODE` L43, `resolveEventCode`) -- remove L70/L80. Move `GLOBAL_CODE`/`resolveEventCode` somewhere the handler can share. `service/EventTeaserImageService.java` -- remove presign/confirm (L82-132). Keep the limit check as a method the handler calls (`TeaserImageLimitExceededException`, keep its status).
- New EMS `service/TeaserImageUploadHandler.java` -- `requiresTarget`, ORGANIZER, rules (types above, 10 MB), `checkTarget` (event exists or `_global`; limit), `finalKey`, `attach` (row + evict `EVENT_WITH_INCLUDES_CACHE`).
- Frontend `hooks/useEventTeaserImages.ts` L39 + `eventApiClient` teaser presign/confirm (~L465-499) → `uploadTarget('teaser-image', {eventCode})`. Callers `EventSettingsTab.tsx` and `Admin/GlobalImagesTab.tsx`. Tests: `useEventTeaserImages.test` (axios-mock-adapter → MSW), `EventSettingsTab.test.tsx` (ratchet L443 `vi.mock(eventApiClient)` → MSW).
- Tests: `EventTeaserImageControllerIntegrationTest` (ratchet L101 class-level `@Transactional`: remove while touching), `EventTeaserImageServiceTest`. Playwright `admin-global-images-crud.spec.ts` + `helpers/global-image-fixture.ts` (`/events/_global/teaser-images`) move to the contract. Bruno: teaser reserve/old path in the 11b dev-only media collection.
- Baselines: Spectral line 25 (N3).

## Tasks & Acceptance

**Execution:**
- [x] Specs + types.
- [x] EMS RED IT `TeaserImageUploadIntegrationTest` (matrix), handler, removals, IT de-`@Transactional`.
- [x] Gateway map `teaser-image` → EMS.
- [x] Frontend hook, client, tests on MSW.
- [x] Bruno, Playwright (global CRUD, plus one event teaser).
- [x] `lint:api --update` (−1), remove N3, ratchet update.

**Acceptance Criteria:**
- Given local dev, when an organizer adds an event teaser and a global teaser, then both show (event page, global images tab) after reload, and the 11th in one scope is refused with the existing message.

## Implementation Notes

- Old paths: `POST …/teaser-images/upload-url` and `POST …/teaser-images/confirm` now answer **405** (they fall on `/teaser-images/{imageId}`, PATCH and DELETE only). Pinned in `TeaserImageUploadIntegrationTest` and Bruno 14.
- Not strictly RED-first: the handler was written before the IT ran. Instead, the cache eviction was mutation-checked: with `cache.clear()` commented out in `attach` (before the review moved it after the commit), the event-teaser IT case fails (the event GET is cached before the upload); restored, green.
- `GLOBAL_CODE` and `scopeOf(eventCode)` (`_global` → null) moved to `EventTeaserImageService`; the controller and `TeaserImageUploadHandler` use them. `GLOBAL_TEASER_CODE` is exported from `eventApiClient.ts` for the hook.
- Limit (after review): `EventTeaserImageService.checkRoomFor` runs in the new shared hook `UploadPurposeHandler.checkCanReserve`, called on create only, so a confirm against a full scope first gets the contract's answers (unknown upload 404, other target 404, already attached 409; IT `should_answerContractFirst_when_scopeFullOnConfirm`). On confirm the limit is checked in `attach` (`addUploaded`) under `pg_advisory_xact_lock` keyed on the scope (`event_teaser_images:` + event code or `_global`); the former `FOR UPDATE` row-lock queries are removed (they lock nothing in an empty scope and do not hold back inserts). The attach-time limit is covered by the advisory-lock ITs `should_admitExactlyOne_when_confirmsRaceForTheLastPlace` (9 in scope, 4 concurrent confirms: one 200, three 409, 10 rows, distinct display orders) and `should_giveDistinctDisplayOrders_when_confirmsRaceInEmptyScope`; with the lock call commented out both failed (mutation check). A confirm refused for the limit rolls back: the upload stays PENDING and the final S3 object copied in that transaction is deleted after the rollback (shared `UploadService`, unit test plus the IT verifying the delete).
- Target type `EVENT_TEASER` (target id is the event code or `_global`), temp key `events/temp/{uploadId}/teaser-{uploadId}.{ext}`, `.jpeg` stored as `.jpg` (reuses `EventPhotoUploadHandler.keyExtension`). The teaser row keeps its generated id (not the upload id, unlike photos: the entity has `@GeneratedValue`, and changing it was out of scope).
- Deleting a teaser image deletes its upload row too (new `EventManagementUploadRepository.findByPurposeAndS3Key`), as 11b does for photos: an ASSOCIATED row never expires. Images added before 17.11c have none. Deleting an event cascades to `event_teaser_images` only; upload rows and S3 objects of its teasers stay (same as photos).
- Event cache (after review): `attach` clears `EVENT_WITH_INCLUDES_CACHE` after the commit (`CacheConfig.clearAfterCommit`, a `TransactionSynchronization.afterCommit`); `EventThemeImageUploadHandler` moved to the same helper for its two caches.
- Frontend: `useUploadTeaserImage` takes `{ file }` only; it rethrows a refused step with the server's `message` (`withServerMessage`), so the tabs show "Maximum of 10 teaser images reached for event: …" instead of Axios' "Request failed with status code 409". The file-name helper of 11b moved to `uploadService.fileNameWithExtension`; `photoFileName` wraps it.
- `EventSettingsTab` got `data-testid` `teaser-image-input` and `teaser-image-card` (`data-image-id`) for the new Playwright spec `e2e/organizer/event-teaser-image-upload.spec.ts` (local dev only, like the 11b photo spec).
- `EventTeaserImageServiceTest` (three mocks, two of them own repositories) is deleted; what it covered is in the ITs on PostgreSQL: limit, global count and display order in `TeaserImageUploadIntegrationTest`, position update and not-found in `EventTeaserImageControllerIntegrationTest`, which now also verifies the S3 delete and covers the `_global` delete and scope separation.
- `TeaserImageUploadIntegrationTest` removes only its own global rows (fill rows with key prefix `global/teaser/it-17-11c-`, rows created by its uploads) and fills a scope up to a total, so other classes' global rows are left alone.
- `EventTeaserImageControllerIntegrationTest`: no class `@Transactional`, no `TestSecurityConfig`, no `@MockitoBean` S3 (the `TestAwsConfig` mocks serve), and its `deleteAll()` on `events` and `event_teaser_images` replaced by deletes of its own event code (with commits, `deleteAll` would have wiped other classes' rows).

## Plan Change Log

- 2026-11-13, build: **limit status 409, not 422.** The frozen matrix says "409 at reserve (and at attach) with the existing limit error"; the Code Map said "keep its status" (422). The frozen intent wins: `TeaserImageLimitExceededException` now extends `UploadException` with 409 and keeps its exact message, and its 422 handler in `GlobalExceptionHandler` is removed (no other thrower). The upload contract documents 409 for a full target (create and confirm). Owner to confirm in the PR body; a script that checked for 422 on the old confirm sees 409 now (the old confirm is gone anyway).
- 2026-11-13, build: deleting a teaser image also deletes its upload row (see Implementation Notes). Not in the plan; done for parity with 11b, which otherwise leaves ASSOCIATED rows forever.

## Review Triage Log

## Design Notes

**Architecture note.** Modules: EMS event core (teasers, L2), gateway, frontend. New dependencies: none. New endpoints: none (purpose value). Reused: the 11b handler pattern and target binding. Deviation: none.

**10 MB.** Today a teaser has no limit, because the presign signs no size. The contract signs one, so a limit is needed. 10 MB matches photos, and no production teaser near that has been measured (not checked; the IT pins the limit).

**Cleaner.** Removed: 2 operations and the client-supplied key. Gained: a size limit, pending state and expiry, and one ratchet mock and one transactional IT fewer. Spectral −1.

## Verification

**Run results (2026-11-13, local dev from this worktree, EMS and gateway restarted):** full EMS and gateway suites green (before the review fixes); after the review fixes shared-kernel, the EMS teaser/photo/upload/theme/guard tests, the CUMS upload/logo/profile-picture tests, the affected Vitest files, `check:test-ratchet`, Bruno `event-media-api` (17/17) and both Playwright specs, `admin-global-images-crud.spec.ts` and `event-teaser-image-upload.spec.ts`, green on local dev. Live API check on local dev: the 11th teaser of one event is refused at reserve with 409 and the existing message.

**Commands:**
- `./gradlew :services:event-management-service:test --max-workers=4` -- green
- frontend `type-check`, Vitest, `lint:api`, `check:api-paths`; `make check-test-ratchet` -- green
- Playwright `admin-global-images-crud.spec.ts` against local dev -- green
