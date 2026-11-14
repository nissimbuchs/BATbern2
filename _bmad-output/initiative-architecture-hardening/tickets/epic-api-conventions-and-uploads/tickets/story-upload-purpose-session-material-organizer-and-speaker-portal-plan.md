---
title: 'Upload purpose session-material, organizer and speaker portal'
type: 'refactor'
ticket: '11d'
created: '2026-11-13'
status: 'built'
baseline_revision: '733c99031'
route: 'full'
route_source: 'auto'
risk: 'medium'
review: 'quick'
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
- [x] Specs + types.
- [x] Shared-kernel `targetKeys` + `fileName` + migrations + unit tests (RED first).
- [x] EMS RED IT (matrix), handler, association rewrite, removals, ratchet fixes in touched ITs.
- [x] Gateway: map entry, `/materials` route gone, `DomainRouterTest`.
- [x] Frontend organizer and speaker paths, tests on MSW.
- [x] Bruno, Playwright (organizer + speaker).
- [x] `lint:api --update` (−2), remove N4, ratchet update.
- [x] Docs: ADR-002 purpose table final, `module-map.md` upload row (5 purposes), doc-drift mapped docs.

**Acceptance Criteria:**
- Given local dev, when an organizer adds a PDF to a new session and to an existing one, and a speaker uploads slides in the portal, then each material is listed and downloads with its original name, and the network log shows only `/uploads/session-material*` for the upload.

## Implementation Notes

- Association body: the frozen matrix writes `{items:[{uploadId, materialType}]}`; the array keeps its name `materials` (`SessionMaterialAssociationRequest.materials`), only the items shrink to `{uploadId, materialType?}` (`additionalProperties: false`, `materialType` now an enum). Decided in review: keep `materials`.
- "Organizer, new session": no UI creates a session with materials before the session exists. The only caller of `associateMaterials` is `SessionEditModal`, which edits an existing session; it confirms its uploads without a target and attaches them on save, so a cancel attaches nothing (the confirmed uploads expire after 7 days). The targetless-confirm + associate path serves that modal. The AC's "new session" part has no UI today; the API path is covered by `SessionMaterialUploadIntegrationTest` (`should_attachConfirmedUploads_when_associatedAfterSessionSave`). No new UI built.
- Download (review decision, with the AC): `generateDownloadUrl` passes the material's original file name as `attachmentName`, so the presigned URL carries `response-content-disposition: attachment; filename*=UTF-8''…` and the browser saves the original name; a material without a name (the column is NOT NULL, so defensive only) falls back to the key's last segment. Covered by the IT (asserts the decoded URL; a mutation passing null fails it), Bruno 26 and the organizer Playwright spec (`download.suggestedFilename()`, listened for on every page of the context from the start, since the download opens in a `noopener` popup; `stripPresignedAuthHeader` now also takes a context so the popup's GET loses the injected Authorization). For this the EMS test `S3Presigner` is now a real presigner with static test credentials (local signing, no network) instead of a Mockito mock with a fixed URL; `TestAwsConfigGuardTest` checks it is the test bean by the endpoint it signs for.
- Delete (review): checks the event too (`session(eventCode, slug)`, a slug of another event is 404, nothing deleted) and deletes the material's `session-material` upload row, as photos and teaser images do; both in the IT `should_deleteMaterialAndUploadRow_when_deleted`.
- Material type (review): honoured from the client only for organizers; a speaker's material is always PRESENTATION, also when the association names another type (IT `should_ignoreMaterialType_when_speakerAssociates`).
- Frontend (review): `FileUploadPresignedUrl.fileExtension`, `FileUploadConfirmRequest` (fileId, fileExtension, checksum, read only by the removed material endpoint) and the hook's checksum and extension helpers are gone; `FileUploadTarget.confirm(uploadId)` takes the id only.
- Association runs through `UploadService.associate(purpose, uploadId, targetId)`, not `associateAt` as the Code Map says: `associate` also checks the target binding (an upload reserved for session A and named for B is 404, matrix row "Other session") and takes the handler's final key and target type. `associateAt` skips the binding check.
- Bean cycle avoided: the handler owns the row creation (`createMaterial`), the event and the cache clearing (`published`); `SessionMaterialsService` depends on `UploadService` and the handler, the handler on repositories only. The ownership rule (`checkMayChangeMaterials`, `session_users`) and the session lookup (`session(eventCode, slug)`, 404 when the session is not in the event) live in the handler; the controller's delete uses them, so the old `assertSessionOwnershipUnlessOrganizer` body is gone. The association now also refuses a slug of another event (404); it ignored `eventCode` before.
- Material type: organizer-chosen when given in the association; else PRESENTATION for a speaker, derived from the content type for an organizer (`SessionMaterialUploadHandler.materialTypeOf`, the former `getMaterialTypeFromMimeType`). The confirm with a target has no type field, so it always derives (organizer) or uses PRESENTATION (speaker). The session editor no longer derives a type client-side; it sends uploadIds only.
- `UploadService.targetId`: the present keys must equal the purpose's `targetKeys()` exactly (set equality), else 400; the values are joined with `/` in key order. Target id `{eventCode}/{sessionSlug}`, at most 50 + 1 + 200 characters, fits `target_id VARCHAR(255)`.
- `UploadRecord.fileName` is `updatable = false`; the import stores the `baseName` as given (no extension appended), as the Code Map says.
- `SpeakerContentInfo.sessionSlug` (new, nullable) feeds the portal's upload target; `ContentSubmissionPage` renders `PresentationUpload` only with a slug. `ContentSubmissionServiceIntegrationTest` covers it.
- Removed with the four operations: `MaterialsUploadController`, `MaterialsUploadService`, `SpeakerPortalMaterialsService` (+ its unit test, nothing else in it), the speaker portal controller's two methods, and the now unused `FileSizeExceededException`, `InvalidFileTypeException` with their 422 handler. `sessionApiClient.materialUploadTarget` is gone; `speakerPortalService` lost `getMaterialPresignedUrl`, `confirmMaterialUpload` and the `SpeakerMaterial*` types; `uploadMaterial(eventCode, sessionSlug, file, onProgress)` returns `{fileUrl, fileName}`.
- Old paths answer **404** (no handler, and `/api/v1/materials` is no longer routed by the gateway). Pinned in `SessionMaterialUploadIntegrationTest` (all four) and Bruno 26 (through the gateway).
- `SessionMaterialsControllerIntegrationTest`: no class `@Transactional` (ratchet line gone), no `TestSecurityConfig`, no `@MockitoBean` S3, own event code `BATbern9961` with targeted deletes instead of `deleteAll()`. Its three association tests with fake upload ids (3.1, 3.7, 3.9) moved to `SessionMaterialUploadIntegrationTest` with real uploads; 3.5 and 3.8 stay (session and ownership are checked before the uploads). `SessionMaterialsServiceTest` lost its association tests (2.1 to 2.4), now in the IT, the domain event through `@RecordApplicationEvents`.
- Not RED-first for the EMS handler (written before the IT ran); the shared-kernel tests were written first but only ran after the implementation (compile-red).
- Frontend tests on MSW: `PresentationUpload.test.tsx` and `speakerPortalService.test.ts` (ratchet lines gone), `sessionApiClient.http.test.ts` (axios-mock-adapter gone), `SessionEditModal.test.tsx` (MockAdapter gone, new test: upload in the Materials tab, then save sends `{materials: [{uploadId}]}`). `FileUpload.test.tsx` and `useFileUpload.test.ts` moved from the old material endpoints to `session-material`. The PresentationUpload test no longer checks the 50 % progress value (the MSW XHR interceptor gives no controllable progress); it checks the progress bar while the PUT is pending.
- Playwright, found along the way and fixed: (1) the `speaker` and `partner` projects inherited the global `extraHTTPHeaders` Authorization (the organizer token), which overrides the app's own header, so every API call of a speaker or partner spec ran as the organizer; each project now sends its own token (`headersFor` in `playwright.config.ts`). (2) `run-playwright-tests.sh` captured the stdout of `refresh-token.sh` into the role tokens (a status line before the JWT), an invalid header once (1) used them; stdout now goes to /dev/null. Speaker project 5/5 and partner project 8/8 green after both.
- Bruno (dev-only `event-media-api`): fixture session (20), reserve with the two-key target (21), PUT (22), partial target 400 (23), confirm (24), listed with original name and type DOCUMENT (25), old `/materials/presigned-url` 404 (26), delete (27).
- The spec floors in `specMatcher.test.ts` and `msw/examples.test.ts` dropped to 260 operations / 225 JSON responses: the count fell to exactly 270 / 235 with the four removed operations.
- Spectral baseline 80 → 78 lines (N4 group removed); test ratchet −3 lines.

## Plan Change Log

- 2026-11-13, build: the two Playwright fixes above (config and runner script) are outside the Code Map; without them the speaker spec cannot run as a speaker.
- 2026-11-13, review: the download carries the original file name (one line in `generateDownloadUrl`). Decided with the AC ("downloads with its original name"): a file name in the download is not the change the Never rule ("no change to download") meant.
- 2026-11-13, build: `uploadMaterialFromUrl` (session batch import) now writes keys without the year, through the shared `finalS3Key`. This follows the owner's 2026-11-13 no-year decision for all final keys (11a) and overrides the frozen "no change to `uploadMaterialFromUrl`" for the key only; its fetch, row and event are unchanged, and existing keys stay.

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
