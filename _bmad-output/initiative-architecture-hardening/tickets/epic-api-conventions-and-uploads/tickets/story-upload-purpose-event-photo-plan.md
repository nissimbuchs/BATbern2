---
title: 'Upload purpose event-photo'
type: 'refactor'
ticket: '11b'
created: '2026-11-13'
status: 'built'
baseline_revision: '91c6b4ca1'
route: 'full'
route_source: 'auto'
risk: 'medium'
review: 'quick'
review_source: ''
lenses_ran: []
review_loop_iteration: 0
context:
  - '{project-root}/docs/architecture/ADR-002-generic-file-upload-service.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Event photos use their own presign (`/photos/upload-url`) and `POST /events/{eventCode}/photos/confirm` in `EventPhotoService`. There is no pending state, the client sends back the S3 key on confirm, and nothing ties an upload to the event it was reserved for.

**Approach:** Breaking, no aliases. EMS gets purpose `event-photo` on the 17.11 contract, with target `eventCode` (required), for organizers only. Confirm with the target creates the `event_photos` row. The shared contract now records the target given at reserve time, and a confirm against another target is refused. That one rule serves 11b, 11c and 11d. The two old operations are deleted along with their frontend callers. Bruno and Playwright gain the photo flow they lack today.

## Boundaries & Constraints

**Always:** Spec first, then regenerate types. Rules stay as today: jpeg/png/webp, 10 MB. Final key `events/{eventCode}/photos/{uploadId}.{ext}`, and `display_url` built exactly as `EventPhotoService` builds it today (domain + "/" + key). List, delete and `GET /public/event-photos` are unchanged. The test fidelity and rack rules from 11a apply.

**Never:** No change to existing photo rows or S3 objects. No new EMS uploads table (17.11 V153 exists).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected | Error |
|---|---|---|---|
| Upload | organizer: create `{target:{eventCode}}`, PUT, confirm with the same target | photo row, in the gallery list | not jpeg/png/webp or > 10 MB: 400 |
| Other event | reserved for event A, confirmed with target B | — | 404, no row |
| No target | create without target | — | 400 |
| Non-organizer | speaker creates | — | 403 |
| Unknown event | target eventCode does not exist | — | 404 |
| Old paths | `POST …/photos/upload-url`, `POST …/photos/confirm` | pinned status | — |

</frozen-after-approval>

## Code Map

- `docs/api/event-media-api.openapi.yml` -- delete L104 `requestEventPhotoUploadUrl` and L143 `confirmEventPhotoUpload` (plus unused schemas). Keep L77 list, L185 delete, L29 public.
- `docs/api/uploads-api.openapi.yml` -- add `event-photo` (table, both enums). Confirm description: a target other than the reserved one is 404.
- `shared-kernel/.../upload/UploadRecord.java` `start(...)` + `UploadService.create/confirm` -- when create has a target, store `targetType`/`targetId` on the PENDING row. When confirm's target differs, answer 404. When create had no target, confirm may still name one. `UploadServiceTest` RED first.
- EMS `service/EventPhotoService.java` -- remove `requestUploadUrl` L83 and `confirmUpload` L119. Keep list/delete/recent. `controller/EventPhotoController.java` L67/L79 removed.
- New EMS `service/EventPhotoUploadHandler.java` -- pattern `EventThemeImageUploadHandler`. `requiresTarget` (11a SPI). `attach` saves the `EventPhoto` (filename = `photo.{ext}`, there is no original name until 11d adds it; `uploadedBy` = `upload.getCreatedBy()`; sort order as `confirmUpload` sets it today). Temp key `events/temp/{id}/photo-{id}.{ext}`.
- Frontend `services/api/eventApiClient.ts` L406-427 (photo presign/PUT/confirm) -- `useUploadEventPhoto` (`hooks/useEventPhotos.ts` L26) uses `uploadTarget('event-photo', {eventCode})` with the existing PUT. `useEventPhotos.test.ts` moves from `vi.mock(eventApiClient)` to MSW (ratchet line 502).
- Tests: `EventPhotoControllerIntegrationTest` (ratchet line 7, be-mock-in-process: remove the mock while touching it), `EventPhotoServiceTest`. New `EventPhotoUploadIntegrationTest`.
- Bruno new `event-media-api/` photo upload (dev-only collection: reserve, invalid type, old path, cleanup by delete). Playwright new `e2e/organizer/event-photos-upload.spec.ts` (local dev).
- Baselines: Spectral line 12 (N2).

## Tasks & Acceptance

**Execution:**
- [x] Specs + types.
- [x] Shared-kernel target binding + unit tests.
- [x] EMS RED IT (matrix), handler, removals, ratchet mock removed in `EventPhotoControllerIntegrationTest`.
- [x] Gateway map entry `event-photo` → EMS (map from 11a).
- [x] Frontend hook, client, tests on MSW.
- [x] Bruno, Playwright.
- [x] `lint:api --update` (−1 line), remove N2, ratchet update.

**Acceptance Criteria:**
- Given local dev, when an organizer uploads a photo on the event page, then it shows in the gallery after reload and on the public recent photos, and the network log shows only `/uploads/event-photo*` for the upload.

## Implementation Notes

- Old paths: both `POST …/photos/upload-url` and `POST …/photos/confirm` now answer **405** (they fall on `/photos/{photoId}`, DELETE only). Pinned in `EventPhotoUploadIntegrationTest` and Bruno 07.
- RED shown: `UploadServiceTest` 3 new tests failed before the binding; `EventPhotoUploadIntegrationTest` 5 of 6 failed with the handler unregistered (the 405 pin is independent of it).
- Photo id = upload id (`UUID.fromString(uploadId)`), so the gallery card, the S3 key and the upload row share one id.
- Removed with the operations: `PhotoUploadNotFoundException` and `PhotoConfirmResponse` (unused after the removal / already unused).
- Deleting a photo also deletes its upload row (`event_management_uploads`, uploadId = photo id, purpose `event-photo`): an ASSOCIATED row never expires. Deleting an event cascades to `event_photos` in the database only; the upload rows and S3 objects of its photos stay (unchanged behaviour for S3, accepted for now).
- `UploadService.confirm` checks the target binding before the ASSOCIATED → 409 check, and `isBoundElsewhere` covers ASSOCIATED rows too: another target always gets 404, the same target on an attached upload 409.
- Playwright reads `/public/event-photos?lastNEvents=1` (the fixture is the latest past event), since the 5-event sample is random beyond 100 photos.
- `EventPhotosTab` got `data-testid` `event-photos-input` and `event-photo-card` (`data-photo-id`) for the Playwright spec.

## Plan Change Log

- 2026-11-13, review (accepted): the frozen constraint "`display_url` built exactly as `EventPhotoService` builds it today" is not kept literally. `CloudFrontUrlBuilder` is byte-identical to `domain + "/" + key` in production: it adds the bucket segment only for a `localhost` domain. This deviation from a frozen constraint goes to the owner in the PR body.
- 2026-11-13, review: stricter server rule. The contract takes the extension from the file name and refuses a name with no extension or an unknown one (400), even when the content type is a valid image; the former `EventPhotoService` fell back to the content type. The frontend now sends a name with an extension: `photoFileName` in `useEventPhotos.ts` adds the extension of `file.type` when the name has no accepted one. A script calling the API with such a name now gets 400.
- 2026-11-13, review: final keys keep the old `.jpg` for a `.jpeg` file (`EventPhotoUploadHandler.keyExtension`, photos only); the file name is `photo.jpg` then too.
- 2026-11-13, build: `display_url` is the upload's `fileUrl` (`CloudFrontUrlBuilder`), not `domain + "/" + key`. Identical in production (the CDN domain has no `localhost`); in local dev the old form lacked the MinIO bucket segment (the comment in `EventPhotoService` claiming the domain carried it is contradicted by `start-all-native.sh`), so photos would not render locally.
- 2026-11-13, build: `uploadedBy` is the confirming organizer's username via `SecurityContextHelper.getCurrentUsernameOrNull()`, not `upload.getCreatedBy()`. `createdBy` is `auth.getName()`, which is the Cognito `sub` (a UUID) for a JWT; the old controller deliberately avoided that (ADR-003). Creator and confirmer are the same organizer in the UI flow.
- 2026-11-13, build: the binding is also enforced in `UploadService.associate(purpose, uploadId, targetId)` (entity create or update taking an upload), else a bound upload could be confirmed without a target and then attached elsewhere. No current caller reserves with a target (all frontend `uploadTarget` calls for logos and theme images pass none), so nothing changes for them.

## Review Triage Log

## Design Notes

**Architecture note.** Modules: shared-kernel `upload` (target binding), EMS media (L3), gateway, frontend. New dependencies: none across modules (the handler sits in media next to `EventPhotoService`). New endpoints: none (purpose value; nearest `event-theme-image`). Reused: `UploadService`, `uploadTarget`, `EventPhotoRepository`. Deviation: none.

**Cleaner.** Removed: 2 operations and the client-supplied S3 key. Gained: pending state and expiry for photos, a refusal of cross-target confirm, a Bruno and Playwright photo flow (none existed), one ratchet mock fewer. Spectral −1.

## Verification

**Commands:**
- `./gradlew :shared-kernel:test :services:event-management-service:test --max-workers=4` (one at a time) -- green
- frontend `type-check`, Vitest, `lint:api`, `check:api-paths`; `make check-test-ratchet` -- green, no RAISED
- Bruno photo collection and Playwright `event-photos-upload.spec.ts` against local dev -- green
