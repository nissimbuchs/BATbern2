---
title: 'Upload purpose session-material, organizer and speaker portal'
type: 'refactor'
ticket: '11d'
created: '2026-11-13'
status: 'ready-for-dev'
baseline_revision: ''
route: 'full'
route_source: 'auto'
risk: 'medium'
review: ''
review_source: ''
lenses_ran: []
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Session materials have two separate upload paths with different rules: organizer `/materials/presigned-url` + `/materials/{uploadId}/confirm` (100 MB, many types; confirm only logs), and speaker portal `…/materials/presigned-url` + `…/materials/confirm` (50 MB, slides only). Neither stores a pending upload. The association rebuilds the temp key from an extension and a file name the client sends.

**Approach:** Breaking, no aliases. EMS gets purpose `session-material` on the 17.11 contract, with target `{eventCode, sessionSlug}`. The contract learns two-key targets. The rules depend on the caller: organizer 100 MB and the organizer type list, speaker 50 MB and slides. Organizers may attach to any session, a speaker only to a session they are on (`session_users`). Confirm with the target creates the `session_materials` row. The organizer's session editor uploads before the session may exist, so it confirms without a target and the existing `POST …/sessions/{slug}/materials` takes only `{uploadId, materialType}`, reading name, size and type from the upload. The four old operations are deleted. Download, list and delete stay.

## Boundaries & Constraints

**Always:** Upload tables gain `file_name` (CUMS V33, EMS V156, nullable), so materials keep their original name. Final key without the year (owner 2026-11-13, as 11a): `materials/events/{ec}/sessions/{slug}/file-{uploadId}.{ext}`; existing keys untouched. `SessionMaterialsUploadedEvent` and cache clearing as today. The material type is the one the organizer chooses, else the one derived from the MIME type (as `getMaterialTypeFromMimeType`), and the speaker default stays PRESENTATION. The 11a rules apply.

**Never:** No change to `uploadMaterialFromUrl` (session batch import, server-side), download, delete, or `content_submissions`.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected | Error |
|---|---|---|---|
| Organizer, existing session | target `{eventCode, sessionSlug}` | material row, listed, downloadable | type/size per organizer rules: 400 |
| Organizer, new session | confirm without target; session save; `POST …/materials {items:[{uploadId, materialType}]}` | rows created | uploadId of another purpose or expired: 404 |
| Speaker, own session | speaker on the session, pptx ≤ 50 MB | row with PRESENTATION | 60 MB or .mp4: 400 |
| Speaker, other session | speaker not on that session | — | 403 on reserve and confirm |
| Other session | reserved for session A, confirmed for B | — | 404 |
| Partial target | only `sessionSlug` | — | 400 |
| Old paths | the four presign/confirm operations | pinned status | — |

</frozen-after-approval>

## Code Map

- Specs: `event-media-api` L215 `generateMaterialPresignedUrl` and L255 `confirmMaterialUpload` deleted. `event-speakers-api` L1530 `generatePresignedUrl` and L1574 `confirmUpload` deleted. `event-sessions-api` L1830 `associateMaterials` body becomes `{uploadId, materialType?}` items. `uploads-api`: `session-material`, `UploadTarget.sessionSlug`.
- Shared-kernel: `UploadPurposeHandler` `default List<String> targetKeys()` (default `List.of(targetKey())`); `UploadService.targetId` joins them in order with `/`, all or none (400 otherwise); `UploadRecord.fileName` (from `create`'s `fileName`, and the import `baseName`). Migrations CUMS `V33__upload_file_name.sql`, EMS `V156__upload_file_name.sql`.
- EMS remove `controller/MaterialsUploadController.java`, `service/MaterialsUploadService.java` (`getMaterialTypeFromMimeType` L190 moves into the handler or `SessionMaterialsService`), `SpeakerPortalContentController` L128/L159, `SpeakerPortalMaterialsService` presign/confirm (L120-147).
- EMS `SessionMaterialsService.associateMaterialsWithSession` L75 -- `uploadService.associateAt(PURPOSE, uploadId, "SESSION", ec/slug, finalKey)` then the row; `generateFinalS3Key` L437 drops the year and is reused by the handler (and stays used by `uploadMaterialFromUrl`, which gets the new layout through it). `SessionMaterialsController.assertSessionOwnershipUnlessOrganizer` (~L125) becomes the handler's `checkTarget` rule (shared method).
- New EMS `service/SessionMaterialUploadHandler.java` -- caller-dependent `rules()`, `targetKeys [eventCode, sessionSlug]`, `checkMayUpload` ORGANIZER or SPEAKER, `checkTarget` session exists in event (404), ownership (403), `attach` row + event + cache.
- Gateway `DomainRouter` L86 `/api/v1/materials` route removed (nothing left under it); map `session-material` → EMS.
- Frontend `services/api/sessionApiClient.ts` `materialUploadTarget` L317 → `uploadTarget('session-material', target?)`; `associateMaterials` L106 body slimmed; `SessionEditModal.tsx` L50/L324/L647. `speakerPortalService.ts` L211-241 → `uploadTarget('session-material', {eventCode, sessionSlug})`; `PresentationUpload.tsx` (needs the speaker's session slug, from the speaker portal content response; check while building). Tests: `sessionApiClient.http.test.ts` (axios-mock-adapter → MSW), `PresentationUpload.test.tsx` (ratchet L483), `speakerPortalService.test.ts` (L555), `SessionEditModal.test.tsx` (L178) → MSW.
- Tests: `SessionMaterialsControllerIntegrationTest` (ratchet L113 transactional), `SessionMaterialsServiceTest`, `SpeakerPortalMaterialsServiceTest`. New `SessionMaterialUploadIntegrationTest`.
- Bruno: material reserve/old path in the dev-only media collection. Playwright new `e2e/organizer/session-materials-upload.spec.ts` (+ download), `e2e/speaker/presentation-upload.spec.ts` (speaker project, local dev).
- Baselines: Spectral lines 24, 40 (N4).

## Tasks & Acceptance

**Execution:**
- [ ] Specs + types.
- [ ] Shared-kernel `targetKeys` + `fileName` + migrations + unit tests (RED first).
- [ ] EMS RED IT (matrix), handler, association rewrite, removals, ratchet fixes in touched ITs.
- [ ] Gateway: map entry, `/materials` route gone, `DomainRouterTest`.
- [ ] Frontend organizer and speaker paths, tests on MSW.
- [ ] Bruno, Playwright (organizer + speaker).
- [ ] `lint:api --update` (−2), remove N4, ratchet update.
- [ ] Docs: ADR-002 purpose table final, `module-map.md` upload row (5 purposes), doc-drift mapped docs.

**Acceptance Criteria:**
- Given local dev, when an organizer adds a PDF to a new session and to an existing one, and a speaker uploads slides in the portal, then each material is listed and downloads with its original name, and the network log shows only `/uploads/session-material*` for the upload.

## Implementation Notes

## Plan Change Log

## Review Triage Log

## Design Notes

**Architecture note.** Modules: shared-kernel `upload` (two-key target, file name), EMS sessions (L3) + speaker portal (L4, uses the L3 handler: downward), gateway, frontend. New dependencies: none upward. New endpoints: none (purpose value; `associateMaterials` body changes, nearest itself). Reused: `UploadService.associateAt`, `generateFinalS3Key`, the ownership rule. Deviation: none.

**One purpose, two rule sets.** The ticket names one purpose. The limits differ by role today, so `rules()` reads the caller's role (the same `SecurityContext` the SPI's `callerHasRole` reads). This keeps today's behaviour without a second purpose.

**Cleaner.** Removed: 4 operations, 2 presign/confirm services, the `/materials` gateway route, and client-supplied extension/name/size on association. Gained: pending state and expiry, a cross-session refusal, an original file name on uploads, a Playwright material flow (none existed), and ratchet lines fewer. Spectral −2 (91 → 85 over 11a to 11d).

## Verification

**Commands:**
- `./gradlew :shared-kernel:test :services:company-user-management-service:test :services:event-management-service:test :api-gateway:test --max-workers=4` (one at a time) -- green
- frontend `type-check`, Vitest, `lint:api`, `check:api-paths`, `check:api-types`; `make check-test-ratchet` -- green
- Bruno and Playwright (`chromium` and `speaker`) against local dev -- green
