---
title: 'Dead API surface removed (17.1)'
type: 'refactor'
ticket: '1'
created: '2026-11-07'
status: done
baseline_revision: '1de313f14be0facb0c9b888bf0e3fbbcf368fbd1'
route: 'full'
route_source: 'auto'
risk: 'medium'
review: 'quick'
review_source: 'pinned'
lenses_ran: ['quick']
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/initiative-architecture-hardening/tickets/epic-dead-surface-and-gates/tickets/notes-dead-api-surface.md'
  - '{project-root}/docs/architecture/module-map.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Two reference-only specs (`speakers-api`, `attendees-api`), six operations nothing calls, and a frontend publish-preview chain that calls a path that exists nowhere. Developers and agents extend dead twins, and the 17.3 contract check would start red.

**Approach:** Delete the specs, the six operations and the preview chain in one change through spec, generated code, backend, frontend, tests and docs. Keep every service method that has another caller, and move behaviour that survives through another path (slides-online → registrant notices) onto that path's tests.

## Boundaries & Constraints

**Always:**
- TDD where behaviour survives: move the tests first, keep them green through the deletion.
- Regenerate the frontend types and commit them.
- One heavy job at a time on rack, with output through `tee` and `set -o pipefail`.

**Never:**
- Touch `/logos/upload-from-url` or `/users/{u}/profile-picture/upload-from-url`, which are live.
- Remove the stub service modules, stacks or ECS services; that is 17.2.
- Write a migration.
- Delete a test whose behaviour survives elsewhere.
- Open a PR without the owner's go.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Slides-online, fresh | organizer, `POST /registrant-notices/send` with `templateKey=slides-online`, no prior send | 200, PENDING with registrant count | – |
| Slides-online, running | same, a send in progress | 409 | `DuplicateNewsletterSendException` |
| Slides-online, already sent | same, a completed send exists | 409 | `SlidesOnlineAlreadySentException` |
| Non-organizer | user role on the same call | 403 | – |
| Unknown event | organizer, event `NOPE-EVENT` | 404 | – |
| Removed path | any call to one of the 6 removed operations | 404; 405 where a surviving sibling route shares the path (`…/materials/upload-from-url`, `…/publish/preview`, `/logos/fetch-from-url`). Owner decision 2026-11-07 | – |
| Live preview | organizer opens the Publishing tab | iframe shows the `getPreviewUrl()` public URL; no `/publish/*/preview` request | – |

</frozen-after-approval>

## Code Map

Full inventory with line numbers and keep-lists: the notes file in `context`. It was re-verified on `1de313f1` (no code changes since `89b3618f`, operation count 342).

- `docs/api/speakers-api.openapi.yml`, `docs/api/attendees-api.openapi.yml` -- delete
- `docs/api/{companies,event-speakers,events-core,event-newsletter,event-sessions}-api.openapi.yml` -- remove the 6 operations and schemas only they use. Grep `schemas/<Name>` across all specs first.
- CUMS `controller/LogoController.java` -- remove `fetchImageFromUrl`. Tests: `LogoControllerTest`, fetch cases in `LogoUrlIntegrationTest`.
- EMS `controller/SpeakerInvitationController.java` + `SpeakerInvitationService.inviteBatch` -- remove. Tests: 3 in `SpeakerInvitationControllerIntegrationTest`.
- EMS `controller/AdminController.java` -- delete the file (its generated `AdminApi` disappears). CSV row in `OrganizerOnlyEndpointsIntegrationTest`. Keep `SpeakerNameBackfillService` and `SpeakerNameBackfillRunner`.
- EMS `controller/SlidesOnlineController.java` -- delete the file (`SlidesOnlineApi` disappears). `SlidesOnlineEmailService.sendSlidesOnline` -- delete. Keep the rest of the service.
- EMS `test/.../service/SlidesOnlineIntegrationTest.java` -- move its 5 MockMvc cases (lines ~219-272) to `POST /api/v1/events/{c}/registrant-notices/send` with body `{"templateKey":"slides-online"}`. No registrant-notice MockMvc test exists today.
- EMS `test/.../service/SlidesOnlineEmailServiceTest.java:113,127,140` -- rewrite the 3 `sendSlidesOnline_*` tests against `sendRegistrantNotice(event, "slides-online", user)`.
- EMS `controller/SessionMaterialsController.uploadMaterialFromUrl` -- remove. Keep `SessionMaterialsService.uploadMaterialFromUrl`, which `SessionBatchImportService` uses.
- EMS `controller/PublishingEngineController.getPublishPreview` + `service/publishing/PublishingService.getPreview` (line 167) and its exclusive helpers -- remove. Test: `PublishingEngineControllerIntegrationTest:331`.
- Frontend:
  - `services/publishingService/publishingService.ts`, `hooks/usePublishing/usePublishing.ts` -- remove the preview chain.
  - `components/Publishing/LivePreview/LivePreview.tsx` -- remove the preview chain.
  - `types/event.types.ts:469` -- remove `PublishPreviewResponse`.
  - Tests: `LivePreview.test.tsx` (mock fields, loading/error/retry cases ~210-316), `usePublishing.test.ts`, `.test.tsx`, `publishingService.test.ts`.
- Frontend type move:
  - `services/speakerStatusService.ts:14` (+ `.test.ts:17`), `types/speakerOutreach.types.ts:7,11,25`, `components/organizer/SpeakerDrawer/UnifiedHistoryPanel.tsx:39` -- import from `@/types/generated/event-speakers-api.types`.
  - `OutreachHistoryResponse` has a nullable `notes`; check `OutreachHistoryTimeline.tsx`.
- Wiring:
  - `.githooks/pre-push:115-120` -- remove the two cases.
  - `.github/workflows/security-scan.yml:75,79` -- remove the two ZAP entries.
  - `web-frontend/package.json:55` -- remove the script and its call in `generate:api-types`.
  - `web-frontend/src/types/generated/speakers-api.types.ts` -- delete.
  - `web-frontend/src/types/generated/README.md:31,34,60,61` -- remove the rows.
  - `services/{speaker-coordination,attendee-experience}-service/build.gradle` -- remove the commented generator blocks.
- Docs:
  - `docs/architecture/03-data-architecture.md:114,1295`;
  - the `ADR-002` table row (~613);
  - `ADR-014` (lines 22-23, 40, 76: specs deleted by 17.1).
  - Leave the ADR-004 and ADR-009 history lines.

## Tasks & Acceptance

**Execution:**
- [x] Baseline -- record the operation count (342) and the grep of the 6 operation ids in Implementation Notes -- criterion 7
- [x] `SlidesOnlineEmailServiceTest`, `SlidesOnlineIntegrationTest` -- move the tests onto the registrant-notice path; green before any deletion -- criterion 4
- [x] `LivePreview.test.tsx` -- assert the iframe `src` is the `getPreviewUrl()` URL and no preview fields are used; red until the frontend task -- criterion 5
- [x] Specs + wiring -- delete the 2 specs and their references (Code Map, Wiring); remove the 6 operations and their schemas; run `npm run generate:api-types` -- criteria 1, 3
- [x] Backend -- delete the controllers, methods and tests per the Code Map; compile; Checkstyle -- criterion 3
- [x] Frontend -- type-import move; remove the preview chain and its tests -- criteria 2, 5
- [x] Docs -- per the Code Map -- criterion 8
- [x] Verify -- per Verification, then the count after (expect 336) -- criteria 6, 7

**Acceptance Criteria:** criteria 1 to 8 of the story file (`story-dead-api-surface-removed.md`), each as written there. The I/O matrix covers criteria 4 and 5.

## Implementation Notes

**Operation count (criterion 7).** Script, run from the repo root:

```python
import glob, yaml
n = 0
for f in sorted(glob.glob('docs/api/*.openapi.yml')):
    if '_shared' in f: continue
    d = yaml.safe_load(open(f))
    n += sum(1 for p, ops in (d.get('paths') or {}).items()
             for m in ops if m in ('get', 'post', 'put', 'patch', 'delete'))
print(n)
```

| | All specs | Without the two reference specs |
|---|---|---|
| Base `1de313f1` | 342 (incl. `speakers-api` 10, `attendees-api` 7) | 325 |
| After the 6 operations, specs still present | 336 | 319 |
| Final branch (reference specs deleted) | 319 | 319 |

The −6 is the six operations. The two reference specs (17 operations) no longer count because
they are deleted, so the final total is 319, not the 336 the plan expected; 336 is the count with
the 6 removed and the reference specs still present.

**Baseline grep** of the 6 operation ids on `1de313f1` (services, web-frontend, apps, bruno-tests;
generated code excluded): controllers `LogoController.fetchImageFromUrl`,
`SpeakerInvitationController.inviteSpeakerBatch`, `AdminController.backfillSpeakerNames`,
`SlidesOnlineController.sendSlidesOnline`, `SessionMaterialsController.uploadMaterialFromUrl`,
`PublishingEngineController.getPublishPreview`; service `SlidesOnlineEmailService.sendSlidesOnline`
(+ 3 unit tests); frontend `publishingService.getPublishPreview`, `usePublishing` and its two
tests. Kept by design: `SessionMaterialsService.uploadMaterialFromUrl` (`SessionBatchImportService`),
`SpeakerNameBackfillService.backfillSpeakerNames` (`SpeakerNameBackfillRunner`). Nothing in
`apps/` or `bruno-tests/`.

**Choices made during the build:**
- `LogoControllerTest.should_return403_when_nonOrganizerFetchesImageFromUrl` was retargeted to
  `/logos/upload-from-url` instead of deleted: the organizer-only rule survives there and had no
  other 403 test.
- `OrganizerOnlyEndpointsIntegrationTest`: the `backfill-speaker-names` row was replaced by
  `SPEAKER GET /admin/settings/some-key`, so the admin area keeps a role-check row.
- New `RemovedEndpointsIntegrationTest` (EMS): an organizer calling each of the 5 removed EMS
  operations gets 404 (I/O matrix row "Removed path").
- New `OutreachHistoryTimeline.test.tsx`: an entry with `notes: null` renders (criterion 2).
- `PublishingService`: `getPreview`, and the `SpeakerPoolRepository` field only it used, removed;
  `SpeakerPreview`/`SessionPreview` schemas went with `PublishPreviewResponse`.
- `SpeakerInvitationService`: `inviteBatch` and its exclusive helpers `getErrorCode`,
  `isUserServiceException` removed.
- `speakerOutreach.types.ts`: the `components` re-export dropped (no importer).
- `UnifiedHistoryPanel.tsx` also used `components['schemas']['OutreachHistory']` and
  `['ContactMethod']` (not in the notes' inventory); now `OutreachHistoryResponse` and its
  `contactMethod` field type.
- i18n keys `livePreview.failedToLoad` (all 10 locales) are now unused; left in place.

**Verification results (2026-11-07, logs in `~/.cache/batbern-test-logs/171-*.log`):**
- Slides-online tests moved first and green before any deletion (15 passed).
- `LivePreview.test.tsx` red (17 failed) before the component change, green after.
- EMS `:test`: 2484 run, 2482 passed, 2 failed, both rows of the new
  `RemovedEndpointsIntegrationTest` (405 instead of 404, see Plan Change Log). After the fix,
  that class plus `OrganizerOnlyEndpointsIntegrationTest` re-run green. The full EMS suite was not
  re-run after that one-test fix.
- CUMS `:test`: BUILD SUCCESSFUL, 806 passed.
- Checkstyle EMS + CUMS main/test: clean (after fixing one unused import and one long line).
- `check-zap-spec-coverage.py`: 18 of 18 specs, exit 0. `test-access-control-check.py`: 8 OK.
- Frontend: `check:api-types` 0 (with the regenerated files staged; it diffs worktree against
  the index), `type-check` 0, `lint` 0, Vitest 442 files / 5774 tests passed.
- Gone-grep: only `SessionMaterialsService`/`SessionBatchImportService` and
  `SpeakerNameBackfill*`, as expected.
- Operation count: 319 (see table above).
- Not done: the manual checks (local dev Publishing tab, Bruno against local dev). Local dev was
  not started in this worktree.

- Coordinator, after the implementer returned (2026-11-07):
  - Deleted `livePreview.failedToLoad` from the 9 locales that had it; gsw-BE never had it. JSON otherwise identical.
  - Added `web-frontend/.vitest/` to `.gitignore`.
  - Owner decision: a removed path answers 404, or 405 where a surviving sibling route shares its path. The frozen I/O row was changed on that decision, superseding the earlier "matrix left as frozen" change-log line.
  - Added the CUMS test `LogoUrlIntegrationTest.should_return405_when_removedFetchFromUrlIsCalled` (passes).
  - Full verification re-run, all green:
    - EMS 2466 passed, 0 failed;
    - Checkstyle clean;
    - ZAP 18 of 18;
    - access-control check ok;
    - `check:api-types`, type-check and lint pass;
    - Vitest 442 files, 5774 passed.
  - The criterion 7 count is 342 → 319 over the specs present, of which 325 → 319 excludes the two deleted reference specs: −6.
- Coordinator, after the review patches (2026-11-07):
  - Patches 6–8 applied by the implementer. EMS compile and Checkstyle pass, and the full EMS suite was re-run: 2466 passed, 0 failed.
  - Criterion 6, run against a dev instance of this branch (`make dev-native-up-instance BASE_PORT=9000`; `development.bru` pointed at 9000 for the run, then restored):
    - Bruno 15 of 15 collections passed;
    - 0 `Skipping invalid file`;
    - `tasks-api/01-list-task-templates` and `events/mails-preview-invalid` ran unchanged and passed;
    - log `~/.cache/batbern-test-logs/171-bruno.log`.
  - Criterion 5: Playwright as the organizer on `http://localhost:9100/organizer/events/BATbern60?tab=publishing` (a one-off script, not committed):
    - the iframe is visible, with `src=/events/BATbern60?preview=true&phase=agenda`, and shows the public page in preview mode;
    - none of the 54 API requests goes to a preview path.
  - Review finding 1 is resolved by these runs.

## Plan Change Log

- 2026-11-07, build: I/O matrix row "Removed path" expects 404. Measured: 404 for invite-batch,
  backfill-speaker-names and slides-online/send; 405 for `POST …/materials/upload-from-url` and
  `GET …/publish/preview`, because they match surviving templates with other methods
  (`DELETE …/materials/{materialId}`, `POST …/publish/{phase}`). Either way no operation exists;
  `RemovedEndpointsIntegrationTest` pins the actual status per path. Matrix left as frozen.
- 2026-11-07, build: expected final count 336 assumed the reference specs still counted; with
  them deleted the total is 319 (325 → 319 without them, −6). See Implementation Notes.

## Review Triage Log

### Pass 1 (quick lens), 2026-11-07: 1 medium, 3 low, 6 false/rejected

| # | Finding | Verdict | Route | Evidence |
|---|---|---|---|---|
| 1 | Criteria 5 (manual) and 6 (Bruno vs local dev) unverified | medium | unfinished verification, done before step 5 | No local-dev or Bruno run exists yet. Not a code defect; the Verify task stays open until it is run |
| 2 | New CUMS 405 test never run | false | reject | Ran after the implementer returned: `LogoUrlIntegrationTest` 4/4 PASSED, BUILD SUCCESSFUL (`~/.cache/batbern-test-logs/171-v-cums-logo.log`) |
| 3 | Full EMS suite not re-run after the last test fix | false | reject | Full re-run by the coordinator: 2466 passed, 0 failed, 18 skipped, BUILD SUCCESSFUL (`171-v-ems.log`) |
| 4 | Plan Change Log contradicts the edited frozen matrix row | low | reject (fix edits this plan) | The row was changed on the owner's decision of 2026-11-07 (story Notes); recorded below in Implementation Notes |
| 5 | Plan says i18n keys stay; the diff deletes them | low | reject (fix edits this plan) | The coordinator deleted them after the implementer's note (9 files; gsw-BE never had the key); recorded below |
| 6 | ADR-002 "Effect" paragraph (~664) still counts fetch-from-url and 3 upload-from-url endpoints | low | patch | Confirmed at `ADR-002…md:664-665`; a direct correction to the dated measurement (criterion 8) |
| 7 | `RemovedEndpointsIntegrationTest` Javadoc says 404 too broadly | low | patch | Confirmed; a comment fix |
| 8 | `SlidesOnlineEmailService.TEMPLATE_KEY` has no reader | low | patch | Confirmed: `git grep` finds only its declaration (line 80); its sole reader was the deleted `sendSlidesOnline`. Direct deletion (criterion 3) |
| 9 | `.gitignore` change rides along | false | reject | Deliberate coordinator fix: every local Vitest run writes `web-frontend/.vitest/` (HTML reporter, `vite.config.ts:457`) and nothing ignored it. Announced in the build summary |
| 10 | Count "expected 336" conflicts with the recorded 319 | low | reject (fix edits this plan) | 336 assumed the reference specs still counted. 325 → 319 is −6 without them, recorded below |

## Design Notes

**Architecture note:**
- **Modules:** EMS speakers (L4), publishing (L5), sessions/media (L3), mail center newsletter (L6), platform admin; CUMS logos; frontend publishing and speaker types.
- **New dependencies:** none. Four frontend files move from `speakers-api.types` to `event-speakers-api.types` (identical shapes).
- **Endpoints:** 6 removed, none added or changed. The nearest kept ones:
  - registrant notices `POST /events/{c}/registrant-notices/send`, for slides-online;
  - `POST /events/{c}/speakers/{username}/send-invitation`, for batch invite;
  - `/logos/upload-from-url`, for logo import.
- **Reused:** `SlidesOnlineEmailService.sendRegistrantNotice`, `SessionMaterialsService.uploadMaterialFromUrl`, `SpeakerNameBackfillService` and `ImageUrlFetcher`, all kept because they have other callers.
- **Deviations from the map:** none. The removals implement its rules "no `…/send` + `…/preview` pair" and "API calls only to spec'd paths".

A generated interface whose tag loses its last operation disappears (`AdminApi`, `SlidesOnlineApi`), so those two controllers go entirely. Any leftover `@Override` fails compilation, which is the guard for criterion 3.

## Verification

**Commands** (rack memory rules: one heavy job at a time; Gradle `--max-workers=4`, `./gradlew --stop` before Node; Vitest `--maxWorkers=4`; logs on disk, each `set -o pipefail; … 2>&1 | tee ~/.cache/batbern-test-logs/171-<name>.log`, never in the /tmp tmpfs; check the `anon` line of `/sys/fs/cgroup/dev.slice/memory.stat` before each run, stop above 12 GB):
- `./gradlew :services:event-management-service:test` -- expected: BUILD SUCCESSFUL (includes `EndpointAuthorizationGuardTest`)
- `./gradlew :services:company-user-management-service:test` -- expected: BUILD SUCCESSFUL
- `./gradlew :services:event-management-service:checkstyleMain checkstyleTest :services:company-user-management-service:checkstyleMain checkstyleTest` -- expected: no violations
- `python3 scripts/ci/check-zap-spec-coverage.py` and `python3 scripts/ci/test-access-control-check.py` -- expected: exit 0
- `cd web-frontend && npm run check:api-types && npm run type-check && npm run lint && npx vitest run` -- expected: all pass
- `git grep -n -e fetchImageFromUrl -e inviteSpeakerBatch -e backfillSpeakerNames -e sendSlidesOnline -e uploadMaterialFromUrl -e getPublishPreview -e 'speakers-api.types' -- services web-frontend apps bruno-tests | grep -v event-speakers-api` -- expected: only `SessionMaterialsService`/`SessionBatchImportService` (the kept `uploadMaterialFromUrl` service method) and `SpeakerNameBackfill*` (kept `backfill`), nothing else
- Operation count script (Implementation Notes) -- expected: 336

**Manual checks:**
- Local dev (`make dev-native-up`): the Publishing tab's live preview renders.
- Run Bruno against local dev: no `Skipping invalid file`, and no failure from a removed path.
