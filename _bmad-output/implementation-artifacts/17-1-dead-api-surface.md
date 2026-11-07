# Story 17.1: Dead API surface

Status: ready-for-dev

## Story

As a **developer or agent searching the API**,
I want **only endpoints and specs that something actually calls**,
so that **I extend the real endpoint instead of a dead twin, and 16.8c can fold the Mail Center without a stray send endpoint**.

> Source: `docs/prd/epic-17-architecture-hardening.md` → Story 17.1; action list `docs/plans/architecture-review-2026-11-actions.md` item 1; ADR-014 decision 2026-11-07.
> Depends on: nothing. Unblocks 17.3 (contract check starts clean), 17.4 (lint baseline), 16.8c.
> **Ships together with 17.3 in one PR if the owner agrees** (one prod deploy). Otherwise 17.1 first.

## Architecture note

- **Modules:** EMS speakers (L4), publishing (L5), sessions/media (L3), mail center newsletter (L6), platform admin; CUMS logos; frontend publishing (`services/publishingService`, `hooks/usePublishing`, `components/Publishing/LivePreview`), speaker types.
- **New dependencies:** none. Four frontend files move their type import from `speakers-api.types` to `event-speakers-api.types` (same shapes, verified below).
- **Endpoints:** 6 removed, none added or changed. Nearest kept equivalents: registrant notices `POST /events/{c}/registrant-notices/send` (and Mail Center compose) for slides-online; `POST /events/{c}/speakers/{username}/send-invitation` for batch invite; `/logos/upload-from-url` for logo import.
- **Reused:** `SlidesOnlineEmailService.sendRegistrantNotice`, `SessionMaterialsService.uploadMaterialFromUrl`, `SpeakerNameBackfillService`, `ImageUrlFetcher` all stay (other callers).
- **Deviations from the map:** none.

## Acceptance Criteria

1. **AC1 — two reference specs gone.** `docs/api/speakers-api.openapi.yml` and `docs/api/attendees-api.openapi.yml` are deleted, with every reference: the commented generator blocks in `services/speaker-coordination-service/build.gradle:35` and `services/attendee-experience-service/build.gradle:29`, the two `.githooks/pre-push` cases (lines 115-120), the two `security-scan.yml` ZAP matrix entries (lines ~75 and ~79), `web-frontend/package.json` script `generate:api-types:speakers` and its call in `generate:api-types`, `web-frontend/src/types/generated/speakers-api.types.ts`, and the two rows in `web-frontend/src/types/generated/README.md`.
   **Guard:** `python3 scripts/ci/check-zap-spec-coverage.py` exits 0 (it fails STALE if the matrix still names a deleted spec); `npm run check:api-types` and `npm run type-check` pass.
2. **AC2 — frontend no longer typed against the dead spec.** `services/speakerStatusService.ts`, `types/speakerOutreach.types.ts`, `components/organizer/SpeakerDrawer/UnifiedHistoryPanel.tsx` (and their tests) import from `@/types/generated/event-speakers-api.types`. `OutreachHistory` → `OutreachHistoryResponse`; `ContactMethod` → `OutreachHistoryResponse['contactMethod']` (same enum `email | phone | in_person`). The `export type { components } from './generated/speakers-api.types'` re-export in `speakerOutreach.types.ts` points to `event-speakers-api.types` or is removed if nobody imports it.
   **Guard:** `npm run type-check`, Vitest for the touched files; grep for `speakers-api.types` in `web-frontend/src` returns only `event-speakers-api.types`.
3. **AC3 — six orphan operations gone, spec and code.**

   | Operation | Spec | Controller method | Also delete | Keep |
   |---|---|---|---|---|
   | `POST /logos/fetch-from-url` (`fetchImageFromUrl`) | `companies-api` + schema `FetchImageFromUrlRequest` | `LogoController.fetchImageFromUrl` | its tests in `LogoControllerTest` (~440-500) and `LogoUrlIntegrationTest` (fetch cases only) | `ImageUrlFetcher` (UserController, FederatedAvatarImportService), `/logos/upload-from-url` (called by `useCompanyBatchImport`) |
   | `POST /events/{c}/speakers/invite-batch` (`inviteSpeakerBatch`) | `event-speakers-api` + `BatchInviteRequest`, `BatchInviteResponse` and any schema only they use | `SpeakerInvitationController.inviteSpeakerBatch` | `SpeakerInvitationService.inviteBatch` (only caller), 3 tests in `SpeakerInvitationControllerIntegrationTest` (~515-600) | `sendSpeakerInvitation` |
   | `POST /admin/backfill-speaker-names` (`backfillSpeakerNames`) | `events-core-api` + `BackfillSpeakerNamesResponse`; tag `Admin` disappears, so generated `AdminApi` goes | whole `AdminController` | the CSV row `SPEAKER, POST, /api/v1/admin/backfill-speaker-names` in `OrganizerOnlyEndpointsIntegrationTest:50` | `SpeakerNameBackfillService` + `SpeakerNameBackfillRunner` (`@Profile("backfill-speakers")`) |
   | `POST /events/{c}/slides-online/send` (`sendSlidesOnline`) | `event-newsletter-api`; tag `Slides Online` disappears, generated `SlidesOnlineApi` goes | whole `SlidesOnlineController` | `SlidesOnlineEmailService.sendSlidesOnline` (one-line delegate); the 5 MockMvc tests in `SlidesOnlineIntegrationTest` (~225-275) | `SlidesOnlineSendResponse` (used by registrant notices), `SlidesOnlineAlreadySentException` + its handler, `TEMPLATE_KEY`, everything else in the service (Mail Center `RegistrantAudienceAdapter` and `RegistrantNoticeController` use it) |
   | `POST /events/{c}/sessions/{s}/materials/upload-from-url` (`uploadMaterialFromUrl`) | `event-sessions-api` + `UploadMaterialFromUrlRequest` | `SessionMaterialsController.uploadMaterialFromUrl` | – | `SessionMaterialsService.uploadMaterialFromUrl` (used twice by `SessionBatchImportService`); `SessionMaterialResponse` |
   | `GET /events/{c}/publish/preview` (`getPublishPreview`) | `events-core-api` + `PublishPreviewResponse` | `PublishingEngineController.getPublishPreview` | `PublishingService.getPreview` (only caller) and private helpers only it uses; its tests in `PublishingEngineControllerIntegrationTest` | other publishing endpoints |

   **Guard:** the build (generated interfaces no longer declare the methods, so a leftover `@Override` fails compilation); `EndpointAuthorizationGuardTest` in EMS and CUMS; grep for each path and operationId over `services/`, `web-frontend/src`, `web-frontend/e2e`, `bruno-tests`, `apps/BATbern-watch` returns nothing except history docs.
4. **AC4 — slides-online behaviour preserved through the surviving path.** The three `sendSlidesOnline_*` unit tests in `SlidesOnlineEmailServiceTest` (~110-165) are **rewritten** against `sendRegistrantNotice(event, "slides-online", user)`, not deleted: in-progress → `DuplicateNewsletterSendException`, already sent → `SlidesOnlineAlreadySentException`, happy path → PENDING with registrant count. The 409 contract stays covered once for the HTTP layer through `RegistrantNoticeController` (add a MockMvc case there if none exists).
5. **AC5 — frontend publish preview removed.** `publishingService.getPublishPreview` (calls `/events/{c}/publish/{phase}/preview`, which exists nowhere) and everything that only served it are deleted: in `usePublishing.ts` the `previewKey` query, `fetchPreviewMutation` and the return fields `preview`, `fetchPreview`, `isLoadingPreview`, `previewError`; in `LivePreview.tsx` the `usePublishing` call, `prevPreviewRef` effect, `handleRetry`, the error/retry branch (~212-235) and the `isLoadingPreview` conditions; `PublishPreviewResponse` in `types/event.types.ts:469`. `LivePreview` keeps rendering the iframe from `getPreviewUrl()`'s generated public URL, which is what it shows today (`preview` was always null).
   **Guard:** Vitest for `LivePreview`, `usePublishing` (both `.test.ts` and `.test.tsx`), `publishingService`, `EventPublishingTab`; a test asserts the iframe `src` is the generated public preview URL.
6. **AC6 — no side effects in Bruno.** `bruno-tests/events/mails-preview-invalid.bru` and `bruno-tests/tasks-api/01-list-task-templates.bru` mention `slides-online` only as a template key; they stay unchanged. Bruno run output has no `Skipping invalid file` and no failure from a removed path.
7. **AC7 — counted.** The story records the operation count before and after (expect −6) with the same counting method as the review (`paths × {get,post,put,patch,delete}` over `docs/api/*.openapi.yml`, excluding `_shared`), and notes that speakers/attendees specs are no longer counted.
8. **AC8 — docs.** `docs/architecture/03-data-architecture.md:114` (drop `fetch-from-url`) and `:1295` (backfill now only via the `backfill-speakers` profile runner); `ADR-002` table row at ~613 marked removed by 17.1; `ADR-014` notes the two specs deleted by 17.1. History documents (`docs/stories/archived/**`, `docs/qa/**`, `api-consolidation-plan.md`, ADR-004/009 revision logs) stay as they are.

## Tasks / Subtasks

- [ ] **T1 Baseline (AC7)** — count operations on the branch start; save the six operationIds' grep output to the Dev Agent Record.
- [ ] **T2 RED** (AC2, AC4, AC5)
  - [ ] Rewrite the 3 `sendSlidesOnline_*` unit tests against `sendRegistrantNotice(..., "slides-online", ...)`; they pass already (delegate), keep them green through the deletion.
  - [ ] Add/adjust `LivePreview` test: no `usePublishing` preview fields used; iframe `src` = generated public URL. Fails until T5.
- [ ] **T3 Specs and codegen (AC1, AC3)**
  - [ ] Delete the 2 specs, their gradle comments, pre-push cases, ZAP matrix entries, npm script + aggregate call, generated `speakers-api.types.ts`, README rows.
  - [ ] Remove the 6 operations and their now-unused schemas from the 5 specs. Before deleting a schema, grep all specs for `schemas/<Name>` (cross-file `$ref`s exist, e.g. `./_shared.openapi.yml#`).
  - [ ] `cd web-frontend && npm run generate:api-types` and commit the regenerated types.
- [ ] **T4 Backend (AC3)** — delete controller methods/controllers, the 3 service methods only they used (`inviteBatch`, `getPreview` + exclusive helpers, `sendSlidesOnline`), tests per table. Compile, then `./gradlew :services:event-management-service:checkstyleMain checkstyleTest` (UnusedImports).
- [ ] **T5 Frontend (AC2, AC5)** — repoint the 4 type imports; remove publish-preview code; `npm run type-check`, `npm run lint`, Vitest for touched files.
- [ ] **T6 Docs (AC8)**.
- [ ] **T7 Verify** (one heavy job at a time on rack, output via `tee`, `set -o pipefail`)
  - [ ] `./gradlew :services:event-management-service:test` and `:services:company-user-management-service:test` → green.
  - [ ] `python3 scripts/ci/check-zap-spec-coverage.py`; `python3 scripts/ci/test-access-control-check.py`.
  - [ ] Frontend: `npm run check:api-types`, `npm run type-check`, Vitest full run.
  - [ ] Local dev (`make dev-native-up`): open an event's Publishing tab, live preview renders; Bruno run against local dev, grep for `Skipping invalid file`.
  - [ ] AC7 count after.

## Dev Notes

### What exists today (verified in code 2026-11-07, branch `docs/architecture-review-2026-11` = develop `89b3618f` + docs)

- **The review was wrong twice; both are corrected above.**
  1. `speakers-api.openapi.yml` is *not* frontend-dead: it generates `speakers-api.types.ts`, imported by `speakerStatusService.ts:14`, `speakerOutreach.types.ts:11,25`, `UnifiedHistoryPanel.tsx:39` (+ `speakerStatusService.test.ts`). The four status schemas (`UpdateStatusRequest`, `SpeakerStatusResponse`, `StatusHistoryItem`, `StatusSummaryResponse`) have **identical shapes** in `event-speakers-api` (field-level diff, only descriptions/examples differ). `OutreachHistory` → `OutreachHistoryResponse` adds a required `updatedAt` and makes `notes` nullable; code reading `item.notes` must tolerate `null` (check `OutreachHistoryTimeline.tsx`). `attendees-api` has no generated types and no importer.
  2. `usePublishing.fetchPreview` *is* referenced, by `LivePreview.tsx:94` (`handleRetry`), and `LivePreview` is rendered by `EventPublishingTab.tsx:155`. It is still dead in practice: the preview query is `enabled: false` with a `null` placeholder, the mount-time fetch is commented out (`LivePreview.tsx:61-66`), and the retry button only renders when `previewError` is set, which only `fetchPreview` can set. So no user ever triggered the 404. Remove the whole chain, keep the iframe.
- **Generated interfaces disappear with their tag.** `AdminApi` (tag `Admin`) and `SlidesOnlineApi` (tag `Slides Online`) have only the one deleted operation each, so the generator stops producing them and `AdminController` / `SlidesOnlineController` must go entirely. The other four controllers keep their other methods.
- **Do not delete shared services.** `SlidesOnlineEmailService` is the registrant-notice engine (Mail Center `RegistrantAudienceAdapter`, `RegistrantNoticeController`, `MailGoldenTest`); only `sendSlidesOnline` (lines 128-140) goes. `SessionMaterialsService.uploadMaterialFromUrl` serves `SessionBatchImportService:185,270`. `SpeakerNameBackfillService` serves the profile-gated runner. `ImageUrlFetcher` serves avatar import and profile pictures.
- `/logos/upload-from-url` and `/users/{u}/profile-picture/upload-from-url` are **live** (`useCompanyBatchImport.ts:27`, `useSpeakerBatchImport.ts:58`). Do not touch them; 17.11 replaces them with `/uploads/{purpose}/import`.
- The CDK, ECS and gateway parts of the two stub services belong to **17.2**, not here. Here only the commented generator blocks in their `build.gradle` go; the modules stay in the build until 17.2.

### Rules that bite

- **A PR to `develop` deploys to production.** Push, then ask the owner before opening the PR. One PR for 17.1 (+17.3 if agreed).
- Regenerate types after spec changes and commit them; the pre-commit hook runs `check:api-types` when `docs/api/` is staged.
- Checkstyle `UnusedImports` will catch the leftover DTO imports in the controllers that keep other methods.
- Spec-file edits trigger the pre-push hook to run the owning service's integration tests (needs Docker). Run them yourself first, not in parallel with Vitest.
- No migration in this story. If you find yourself writing one, stop.

### Testing standards

- Java: `should_expectedBehavior_when_condition`; integration tests extend `AbstractIntegrationTest`.
- Frontend: RTL `screen` queries, `userEvent`, `waitFor` for async.
- Deleting a test is only correct when the behaviour it covers is deleted. Where behaviour survives through another path (slides-online → registrant notice), move the test (AC4).

### References

- `docs/prd/epic-17-architecture-hardening.md` → Story 17.1, rules for every story
- `docs/plans/architecture-review-2026-11-actions.md` → item 1
- `docs/architecture/ADR-014-backend-service-consolidation.md` → Decision 2026-11-07
- `docs/architecture/module-map.md` → API rules, Reuse
- `scripts/ci/check-zap-spec-coverage.py` (fails on stale ZAP entries)
- `CLAUDE.md` → Deployment (PR deploys), Quality Standards (one heavy build at a time, `tee` + `pipefail`)

## Dev Agent Record

### Agent Model Used

### Debug Log References

### Completion Notes List

- 2026-11-07 (create-story, John): story context created; inventory verified in code. Two review claims corrected (speakers-api types in use; `fetchPreview` referenced by `LivePreview`). ZAP matrix entries added to scope (would fail `check-zap-spec-coverage.py`).

### File List
