---
title: "Speakers and organizers tick 'Publicly visible after the event'"
type: 'feature'
ticket: '4'
created: '2026-11-20'
status: 'built'
baseline_revision: '4c8d538fcd5095da79ddaa19225e88b91d1314d1'
route: 'full'
route_source: 'auto'
risk: 'medium'
review: 'quick'
review_source: 'pinned'
lenses_ran: [quick]
review_loop_iteration: 0
context:
  - '{project-root}/docs/architecture/coding-standards.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Since stories 1 to 3 the backend stores `publiclyVisible` per material, publishes only public ones and takes the value on confirm, association and `PATCH`, but no screen shows or sets it: a speaker who uploads the PowerPoint next to the PDF cannot see that it stays internal, nor change it (#1260 AC1, AC3, AC5).

**Approach:** A checkbox "Publicly visible after the event" with the hint that a PDF is the recommended public copy, per material, in the organizer's SessionEditModal Materials tab (new uploads before save and existing materials) and in the speaker portal's PresentationUpload (the session's materials). New: pre-set by type (PDF on, others off), sent with the association (organizer) or the confirm (speaker). Existing: from `publiclyVisible`, a change is a `PATCH`.

## Boundaries & Constraints

**Always:** Services through the API client (`sessionApiClient`, `speakerPortalService`); generated types (`SessionMaterial` becomes an alias of `SessionMaterialResponse`); PDF = extension `pdf` or type `application/pdf`, case-insensitive (the backend rule); keys in all 10 locales, DE with real umlauts and Swiss ss; tests through the MSW harness with the spec shapes, red first; touched screen tests move off own-code mocks (SessionEditModal: i18n stub, SessionSpeakersTab mock).

**Never:** No backend or spec change. No filter on the public pages (the backend sends only public ones). No Playwright run against production. No change to ContentSubmissionPage (the speaker list lives in PresentationUpload, which has event and session).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Organizer new upload | `talk.pdf`, `talk.pptx` uploaded, not saved | checkbox on for PDF, off for PPTX | none |
| Organizer save | PPTX ticked on | association item `{uploadId, publiclyVisible: true}`, PDF item `false` if unticked | save error shown as today |
| Organizer removes new upload | delete icon on a not yet saved file | file gone from the list and not associated on save | none |
| Organizer existing | material `publiclyVisible: false` | checkbox off; tick → `PATCH {publiclyVisible: true}`, checkbox on | PATCH fails: error shown, checkbox back to off |
| Speaker list | session with a PDF (public) and a PPTX (not) | both listed with their state and the hint | list load fails: error text, upload still works |
| Speaker upload | `talk.pptx` | confirm body `{target, publiclyVisible: false}`, list reloaded | upload error as today |
| Speaker toggle | tick the PPTX | `PATCH` on that material, checkbox on | PATCH fails: error, checkbox unchanged |
| Public pages | session with `materials: []` or `null` | no materials block, no crash | none |

</frozen-after-approval>

## Code Map

- `web-frontend/src/types/event.types.ts:50-65` -- hand-written `SessionMaterial` → alias of `sessionsComponents['schemas']['SessionMaterialResponse']` (name not a schema name, so the copy gate never saw it); `SessionUI.materials` keeps the name.
- `web-frontend/src/utils/materialVisibility.ts` -- new `isPublicByDefault(fileName, mimeType)`.
- `web-frontend/src/services/api/sessionApiClient.ts` -- add `listMaterials(eventCode, slug)` (GET, `SessionMaterialsResponse.materials`) and `updateMaterial(eventCode, slug, id, {publiclyVisible})` (PATCH, `updateSessionMaterial`); fix `associateMaterials` return type (wire is `SessionMaterialsResponse`, typed as an array; no caller reads it).
- `web-frontend/src/services/speakerPortalService.ts:215-268` -- `uploadMaterial(..., publiclyVisible?)`; `services/uploadService.ts confirmUpload` gets an optional `publiclyVisible` in the body.
- `web-frontend/src/components/shared/FileUpload/FileUpload.tsx:241-262` -- optional `renderFileDetails(file)` under each uploaded file (multiple mode); other users unchanged.
- `web-frontend/src/components/organizer/EventManagement/SessionEditModal.tsx` -- state `newVisibility` by uploadId; `FileUpload` gets `onFileRemove` (defect, see tasks) and `renderFileDetails`; association sends `publiclyVisible`; existing list (`:637`) gets the checkbox → `updateMaterial`.
- `web-frontend/src/components/speaker-portal/PresentationUpload.tsx` -- loads the session's materials, lists them with the checkbox (names as plain text: the e2e spec finds the uploaded file by its single link), reloads after an upload.
- No shared checkbox component: the organizer modal is MUI, the speaker portal Tailwind, and their keys live in different namespaces (`events`, `common`).
- i18n: `public/locales/*/events.json` `sessionEdit.materials.{publiclyVisible,publicHint,visibilityError}`, `public/locales/*/common.json` `speakerPortal.upload.{yourFiles,publiclyVisible,publicHint,visibilityError,listError}`.
- Tests: `SessionEditModal.test.tsx` (touch rule), `PresentationUpload.test.tsx`, `sessionApiClient.test.ts`, `speakerPortalService.test.ts`, `SessionCards.test.tsx`/`EventProgram.test.tsx` fixtures via new `aSessionMaterial()` in `src/test/fixtures/wire.ts`; null-materials case for SessionCards.
- E2E: `e2e/organizer/session-materials-upload.spec.ts`, `e2e/speaker/presentation-upload.spec.ts` -- assert `publiclyVisible: true` on the uploaded PDF; selectors unaffected (checked).
- Docs: `docs/user-guide/speaker-portal/content-submission.md` §2, `docs/user-guide/features/file-uploads.md` materials steps, `docs/user-guide/appendix/changelog.md` (#1260 block).

## Tasks & Acceptance

**Execution:**
- [x] Defect, own commit, test first (C9): SessionEditModal passes no `onFileRemove` to `FileUpload`, so removing a not yet saved file leaves it in the list and associates it on save.
- [x] Tests red: matrix rows (modal, PresentationUpload, services, public null case).
- [x] Type alias + fixture builder; util; services; FileUpload prop; modal; PresentationUpload.
- [x] i18n 10 locales ×2 namespaces; e2e assertions; docs + changelog.
- [x] Full Vitest, type-check, lint, check-frontend-api-paths, check-test-ratchet (+ `--update` if STALE), check-i18n-keys, check-api-type-copies.

**Acceptance Criteria:**
- Given a speaker uploads a PPTX and a PDF to one session, then the portal shows the PDF ticked and the PPTX unticked, and the backend stored that (AC1 through stories 1-2; production check after deploy, read-only).
- Ratchet baseline does not grow and SessionEditModal's two lines go away.

## Implementation Notes

- Implemented directly by the build session (coordinator brief: no coding subagent on rack, one read-only reviewer); the checkpoint approval was the brief.
- Touch rule first, own commit: `SessionEditModal.test.tsx` lost its `react-i18next` stub and its `SessionSpeakersTab` mock; all 24 tests passed unchanged on the real `en` bundle and the real tab; ratchet baseline −2 lines.
- Defect (C9), own commit, red first: the modal gave `FileUpload` no `onFileRemove`, so a removed unsaved file stayed listed and was associated on save.
- Feature tests red first (18 failing for the right reasons: missing methods, missing checkboxes, confirm body without `publiclyVisible`), then green. `isPublicByDefault` was written before its pure-function test. The organizer's "forbidden" message branch (a 401/403 arrives as the AxiosError, whose message means nothing to a person) was added after the first green run; its test was checked by a mutant (branch removed: test fails).
- `SessionMaterial` is now the generated `SessionMaterialResponse`; production code compiled unchanged, test fixtures moved to the new `aSessionMaterial()` builder (`src/test/fixtures/wire.ts`).
- `associateMaterials` returned the wire wrapper typed as an array; it now returns `response.data.materials` (no caller read it, so no visible defect).
- `uploadMaterial(eventCode, slug, file, publiclyVisible, onProgress?)`: the flag is required, so every caller decides.
- i18n: 3 keys in `events` and 5 in `common`, all 10 locales; the 8 locales without `sessionEdit.materials.delete/deleteError` got them (they fell back to German). gsw-BE `speakerPortal.upload` stays sparse apart from the new keys (it falls back to `de` by design).
- E2E: both specs assert `publiclyVisible: true` for the uploaded PDF, the speaker spec also the ticked checkbox; existing selectors unaffected (the speaker list shows names as text, the single link stays). Not run (production).
- Not verified: AC1 in production (after deploy, read-only), the look of the checkbox rows in a real browser.

## Plan Change Log

## Review Triage Log

Pass 1 (quick lens, one context-free read-only reviewer). 7 findings: high 0, medium 1, low 4, false 0, not-a-defect 2.

| # | Finding | Verdict | Route | Evidence / action |
|---|---|---|---|---|
| 1 | Speaker list heading "Your files" while it lists every file of the session (co-speaker's, organizer's), which the speaker can toggle | low | patch | `SessionMaterialAccess.mayUseNonPublic` grants any `session_users` row, so the list holds others' files. Key renamed `sessionFiles` ("Files of this session" / "Dateien dieser Session"), 10 locales; the doc says the list shows every file of the session. |
| 2 | Speaker hint promises non-public files stay "with the organizers and the moderator"; the backend rule is organizers and the session's speakers, no moderator branch | medium | patch | Read `SessionMaterialAccess`: confirmed. Hint now says "stays internal, for the organizers and the speakers of this session", 10 locales; user-guide sentence corrected. |
| 3 | `FileUpload` wraps every multiple-mode row's text in a Box without `flex: 1`, changing row width for all callers | low | patch | Real (the Box shrinks `ListItemText`); only SessionEditModal uses multiple mode today. Box gets `flex: 1`. |
| 4 | Matrix row "materials null" not tested; the wire value is `null` | low | patch | `SessionUI.materials` did not admit `null` although the wire sends it; widened to `SessionMaterial[] \| null` (type-check clean, every reader already guards), SessionCards test now uses `null`. EventProgram keeps its `[]` test. |
| 5 | `loadMaterials` on mount and after upload can resolve out of order and overwrite the newer list | low | reject | Needs the mount GET to outlast presign + S3 PUT + confirm; unlikely in use, and the fix adds a request-ordering guard. |
| 6 | Removing an unsaved file leaves its confirmed, target-less upload on the server | low | reject | Not caused by this change (the upload was always left behind; before the fix it was even attached). Cleanup of unattached uploads not checked. |
| 7 | AC1 not verified (PPTX + PDF in the real portal; e2e cover a PDF only and were not run) | n/a | n/a | Known and stated: AC1 is the production check after deploy (read-only); Playwright targets production and is not run by this build. |

## Design Notes

**Architecture note.** Modules: web-frontend organizer EventManagement, speaker-portal, shared FileUpload, services. New dependencies: none. New endpoints: none (uses story 1's `PATCH` and the existing `GET …/materials`). Reused: `sessionApiClient`, `uploadService.confirmUpload`, `FileUpload`, MSW harness. Deviation: none.

**Speaker flow.** The upload starts on file selection, so there is no moment to tick before it; the speaker's chosen value for a new file is the type default, sent explicitly on the confirm, and the list right below lets them change it at once (PATCH). The speaker sees the session's materials through `GET …/materials`, which since story 2 gives a session speaker every material with its flag; the content-info DTO carries only the latest file's URL and name, no id.

**Store sizes at start:** ratchet baseline 524 lines, api-type-copies baseline 6, i18n-keys baseline 4.
**Store sizes at end:** ratchet baseline 522 lines (SessionEditModal's `fe-mock-components` and `fe-mock-i18n` gone); api-type-copies and i18n-keys baselines unchanged (the alias was never in the copy baseline: its name is not a schema name).

## Verification

Run 2026-11-20 (logs `~/.cache/batbern-test-logs/epic5-s4-*.log`): targeted files green (SessionEditModal 31 + 2 skipped, PresentationUpload 21, sessionApiClient, speakerPortalService, SessionCards, EventProgram, util); full Vitest after the review patches 484 files, 6139 passed, 36 skipped, 20 todo, 0 failed; `type-check`, `lint`, `check-frontend-api-paths` (272/272), `check-test-ratchet`, `check-i18n-keys`, `check-api-type-copies` clean. Playwright not run (targets production).

**Commands:**
- `cd web-frontend && npx vitest run --maxWorkers=4 <touched tests>` -- green
- `cd web-frontend && npx vitest run --maxWorkers=4` -- green
- `npm run type-check && npm run lint` (web-frontend), `make check-frontend-api-paths check-test-ratchet check-i18n-keys check-api-type-copies` -- clean
