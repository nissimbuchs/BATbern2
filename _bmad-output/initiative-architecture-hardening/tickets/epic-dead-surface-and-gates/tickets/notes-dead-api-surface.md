# Notes: Dead API surface removed (17.1)

Code inventory verified 2026-11-07 on develop `89b3618f` plus docs. Carried over from the v6 story file `17-1-dead-api-surface.md` (create-story, John, 2026-11-07). The builder re-checks line numbers; the facts are what matter.

## Architecture note (draft, confirm in the plan)

- **Modules:** EMS speakers (L4), publishing (L5), sessions/media (L3), mail center newsletter (L6), platform admin; CUMS logos; frontend publishing (`services/publishingService`, `hooks/usePublishing`, `components/Publishing/LivePreview`), speaker types.
- **New dependencies:** none. Four frontend files move their type import from `speakers-api.types` to `event-speakers-api.types`.
- **Endpoints:** 6 removed, none added or changed. Nearest kept equivalents: `POST /events/{c}/registrant-notices/send` (and the Mail Center composer) for slides-online; `POST /events/{c}/speakers/{username}/send-invitation` for batch invite; `/logos/upload-from-url` for logo import.
- **Reused:** `SlidesOnlineEmailService.sendRegistrantNotice`, `SessionMaterialsService.uploadMaterialFromUrl`, `SpeakerNameBackfillService` and `ImageUrlFetcher` all stay, because they have other callers.
- **Deviations from the map:** none.

## Criterion 1: everything that names the two specs

- Commented generator blocks: `services/speaker-coordination-service/build.gradle:35`, `services/attendee-experience-service/build.gradle:29`.
- `.githooks/pre-push` cases (lines 115-120).
- `security-scan.yml` ZAP matrix entries (about lines 75 and 79). `scripts/ci/check-zap-spec-coverage.py` fails STALE while they remain.
- `web-frontend/package.json`: script `generate:api-types:speakers` and its call in `generate:api-types`.
- `web-frontend/src/types/generated/speakers-api.types.ts` and its two rows in `web-frontend/src/types/generated/README.md`.

## Criterion 2: frontend type move

- Importers: `services/speakerStatusService.ts:14`, `types/speakerOutreach.types.ts:11,25`, `components/organizer/SpeakerDrawer/UnifiedHistoryPanel.tsx:39`, plus `speakerStatusService.test.ts`.
- The four status schemas (`UpdateStatusRequest`, `SpeakerStatusResponse`, `StatusHistoryItem`, `StatusSummaryResponse`) have identical shapes in `event-speakers-api`. A field-level diff found differences only in descriptions and examples.
- `OutreachHistory` becomes `OutreachHistoryResponse`, which adds a required `updatedAt` and makes `notes` nullable. Check `OutreachHistoryTimeline.tsx`. `ContactMethod` becomes `OutreachHistoryResponse['contactMethod']`, the same enum `email | phone | in_person`.
- The re-export `export type { components } from './generated/speakers-api.types'` in `speakerOutreach.types.ts` points to `event-speakers-api.types`, or is removed if nothing imports it.
- `attendees-api` has no generated types and no importer.

## Criterion 3: per operation

| Operation | Spec | Controller | Also delete | Keep |
|---|---|---|---|---|
| `POST /logos/fetch-from-url` | `companies-api` + `FetchImageFromUrlRequest` | `LogoController.fetchImageFromUrl` | its tests in `LogoControllerTest` (~440-500), fetch cases in `LogoUrlIntegrationTest` | `ImageUrlFetcher`, `/logos/upload-from-url` (`useCompanyBatchImport.ts:27`) |
| `POST /events/{c}/speakers/invite-batch` | `event-speakers-api` + `BatchInviteRequest`/`BatchInviteResponse` and schemas only they use | `SpeakerInvitationController.inviteSpeakerBatch` | `SpeakerInvitationService.inviteBatch`, 3 tests in `SpeakerInvitationControllerIntegrationTest` (~515-600) | `sendSpeakerInvitation` |
| `POST /admin/backfill-speaker-names` | `events-core-api` + `BackfillSpeakerNamesResponse`; tag `Admin` disappears, so the generated `AdminApi` goes | the whole `AdminController` | CSV row in `OrganizerOnlyEndpointsIntegrationTest:50` | `SpeakerNameBackfillService` + `SpeakerNameBackfillRunner` (`@Profile("backfill-speakers")`) |
| `POST /events/{c}/slides-online/send` | `event-newsletter-api`; tag `Slides Online` disappears, so the generated `SlidesOnlineApi` goes | the whole `SlidesOnlineController` | `SlidesOnlineEmailService.sendSlidesOnline` (lines 128-140, a one-line delegate); 5 MockMvc tests in `SlidesOnlineIntegrationTest` (~225-275) | `SlidesOnlineSendResponse`, `SlidesOnlineAlreadySentException` + its handler, `TEMPLATE_KEY`, the rest of the service (`RegistrantAudienceAdapter`, `RegistrantNoticeController`, `MailGoldenTest`) |
| `POST /events/{c}/sessions/{s}/materials/upload-from-url` | `event-sessions-api` + `UploadMaterialFromUrlRequest` | `SessionMaterialsController.uploadMaterialFromUrl` | – | `SessionMaterialsService.uploadMaterialFromUrl` (`SessionBatchImportService:185,270`), `SessionMaterialResponse` |
| `GET /events/{c}/publish/preview` | `events-core-api` + `PublishPreviewResponse` | `PublishingEngineController.getPublishPreview` | `PublishingService.getPreview` and private helpers only it uses; its tests in `PublishingEngineControllerIntegrationTest` | the other publishing endpoints |

- Operation ids to search for: `fetchImageFromUrl`, `inviteSpeakerBatch`, `backfillSpeakerNames`, `sendSlidesOnline`, `uploadMaterialFromUrl`, `getPublishPreview`.
- Keep `ImageUrlFetcher`: it serves `UserController` (profile pictures) and `FederatedAvatarImportService` (SSO avatar import). `/users/{u}/profile-picture/upload-from-url` is live (`useSpeakerBatchImport.ts:58`).
- Before deleting a schema, grep all specs for `schemas/<Name>`, because cross-file `$ref`s exist (`./_shared.openapi.yml#`).
- Search scope for the "gone" check: `services/`, `web-frontend/src`, `web-frontend/e2e`, `bruno-tests`, `apps/BATbern-watch`.

## Criterion 4: slides-online tests

- The three `sendSlidesOnline_*` unit tests in `SlidesOnlineEmailServiceTest` (~110-165) are rewritten against `sendRegistrantNotice(event, "slides-online", user)`:
  - in progress → `DuplicateNewsletterSendException`;
  - already sent → `SlidesOnlineAlreadySentException`;
  - happy path → PENDING with the registrant count.
- They pass already (the old method only delegates), so keep them green through the deletion.
- The 409 stays covered once at the HTTP layer through `RegistrantNoticeController`. Add a MockMvc case there if none exists.

## Criterion 5: publish-preview chain

- `publishingService.getPublishPreview` calls `/events/{c}/publish/{phase}/preview`, which exists nowhere.
- `usePublishing.fetchPreview` is referenced by `LivePreview.tsx:94` (`handleRetry`), and `LivePreview` is rendered by `EventPublishingTab.tsx:155`. It is still dead in practice:
  - the query is `enabled: false` with a `null` placeholder;
  - the mount-time fetch is commented out (`LivePreview.tsx:61-66`);
  - the retry button renders only when `previewError` is set, and only `fetchPreview` can set it.
- Delete:
  - in `usePublishing.ts`: the `previewKey` query, `fetchPreviewMutation`, and the return fields `preview`, `fetchPreview`, `isLoadingPreview`, `previewError`;
  - in `LivePreview.tsx`: the `usePublishing` call, the `prevPreviewRef` effect, `handleRetry`, the error/retry branch (~212-235) and the `isLoadingPreview` conditions;
  - `PublishPreviewResponse` in `types/event.types.ts:469`.
- `LivePreview` keeps rendering the iframe from `getPreviewUrl()`. Vitest: `LivePreview`, `usePublishing` (`.test.ts` and `.test.tsx`), `publishingService`, `EventPublishingTab`, with one test asserting the iframe `src`.

## Criterion 6: Bruno

- `bruno-tests/events/mails-preview-invalid.bru` and `bruno-tests/tasks-api/01-list-task-templates.bru` name `slides-online` only as a template key. Leave them unchanged.

## Criterion 8: docs to touch

- `docs/architecture/03-data-architecture.md:114` (drop `fetch-from-url`) and `:1295` (backfill now runs only through the `backfill-speakers` profile runner).
- `ADR-002` table row at about line 613: mark it removed by 17.1.
- `ADR-014`: note that 17.1 deleted the two specs.
- Leave as they are: `docs/stories/archived/**`, `docs/qa/**`, `api-consolidation-plan.md`, and the ADR-004/009 revision logs.

## Operation count

- 342 operations at develop `89b3618f` (PRD Story 17.1, AC4). Adjust for whatever merged since, and record the actual base count.

## Verification commands (one heavy job at a time on rack, `tee` + `set -o pipefail`)

- `./gradlew :services:event-management-service:test` and `:services:company-user-management-service:test`. Spec-file edits make the pre-push hook run these integration suites, so run them first, and not in parallel with Vitest.
- `./gradlew :services:event-management-service:checkstyleMain checkstyleTest`, because `UnusedImports` catches leftover DTO imports.
- `python3 scripts/ci/check-zap-spec-coverage.py`; `python3 scripts/ci/test-access-control-check.py`.
- Frontend: `npm run generate:api-types` (commit the result), `npm run check:api-types`, `npm run type-check`, `npm run lint`, Vitest.
- Local dev (`make dev-native-up`): the Publishing tab's live preview renders; run Bruno against local dev and grep for `Skipping invalid file`.
