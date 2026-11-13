---
title: 'One upload contract, first purpose company-logo'
type: 'refactor'
ticket: '11'
created: '2026-11-12'
status: 'done'
baseline_revision: 'c91c1377cc9256722ede6d68d73dc4563a260451'
route: 'full'
route_source: 'auto'
risk: 'high'
review: 'quick'
review_source: 'pinned'
lenses_ran: ['quick']
review_loop_iteration: 0
context:
  - '{project-root}/docs/architecture/ADR-002-generic-file-upload-service.md'
  - '{project-root}/docs/architecture/ADR-013-rest-api-crud-conventions.md'
  - '{project-root}/docs/architecture/coding-standards.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Eight classes implement presign and confirm on their own. Company logos run on six `/logos/*` operations, and EMS reads and writes the CUMS `logos` table (event theme images) in the shared database, so one service handles the other's files (ADR-002 decision 2026-11-07).

**Approach:** Breaking, no aliases. Shared-kernel gets the upload mechanics: key layout, presign, size and type rules, PENDING → CONFIRMED → ASSOCIATED, expiry cleanup. One spec, `uploads-api`, defines `POST /uploads/{purpose}`, `/{uploadId}/confirm` (associates when `target` is given) and `/import`. Each service implements it with its own table and purpose handlers: CUMS `company-logo` (create form, existing company, logo request, batch import from URL) and EMS `event-theme-image` (the only EMS use of `logos`, needed for AC3). The gateway routes by purpose. The frontend gets one hook, and every `/logos/*` operation is deleted.

## Boundaries & Constraints

**Always:**
- Contract first: `docs/api/uploads-api.openapi.yml`, then `npm run generate:api-types`. `@PreAuthorize` on every write. Test fidelity rules apply (S3, the clock and SES are the only fakes). Every removed path is pinned by an integration test to the status it now returns.
- No data carry-over: owner 2026-11-12, no logo upload or company logo request is in flight, so the new tables start empty and `logos` rows are not copied.
- Existing S3 objects and CDN URLs are untouched. The final keys keep their current layout.
- One heavy job at a time on rack (Gradle `--max-workers=4`, Vitest `--maxWorkers=4`). Logs go through `tee` under `~/.cache/batbern-test-logs/` behind `set -o pipefail`. Verify on local dev, no real mail.
- Push and PR only with the owner's go (a PR to develop deploys to production).

**Never:**
- No drop of `logos` in either service (the drop needs the owner's go; filed as an issue). No change to the profile-picture, event-photo, teaser-image or material flows (11a–11d). No `mail-attachment` purpose.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected | Error |
|---|---|---|---|
| Logo in create form | `POST /uploads/company-logo` → PUT → confirm, no target; create the company with `logoUploadId` | CONFIRMED, then ASSOCIATED, `logoUrl` set | type not png/jpeg/svg: 400; > 5 MB: 400 |
| Logo on existing company | confirm with `target: {companyName}` | ASSOCIATED, company `logoUrl` replaced | unknown company: 404; role not allowed: 403 |
| Logo request | upload and confirm, then `POST /users/me/company-logo-requests` | request opens; approve sets the logo | upload not CONFIRMED: 409 |
| Batch import | `POST /uploads/company-logo/import {url}` | CONFIRMED upload | non-HTTPS or unreachable URL: 400/502 as the `ImageUrlFetcher` reason says |
| Theme image | `event-theme-image` on create (`themeImageUploadId`) and on an existing event (target `eventCode`) | `themeImageUrl` set | uploadId of another purpose: 404 |
| Wrong service | `POST /uploads/event-theme-image` reaching CUMS directly | 404 | — |
| Confirm twice or expired | confirm an ASSOCIATED or expired upload | 409 / 404 | — |
| Expiry | PENDING older than 24 h, CONFIRMED older than 7 d | row and S3 temp object deleted by the job | — |
| Old paths | the six `/logos/*` operations | pinned status (404) | — |

**Decisions (2026-11-12, owner):**
- Plan kept whole (about 2,400 tokens): `/logos/*` removal and the EMS theme image move cannot ship apart.
- Logo request paths keep `{requestId}`; the 3 G13 operations join exception X2 (transient request handle, like `/users/merge-requests/{requestId}`). Moving them under `/companies/{companyName}/logo-requests/{requestId}` needs a company name in the Mail Center contract and a backfill of production conversations; recorded in deferred-work, not in this story.
- The contract's `POST /uploads/{purpose}/{uploadId}/confirm` (ADR-002 shape) is accepted under exception X2: an upload id is a transient technical handle; 11a to 11d reuse the same line. Spectral baseline 94 → 91.

</frozen-after-approval>

## Code Map

- `services/company-user-management-service/.../service/GenericLogoService.java` -- rules (5 MB, png/jpeg/svg, presign 15 min, PENDING 24 h, CONFIRMED 7 d), temp key `logos/temp/{uploadId}/…`, final key `logos/{year}/companies/{name}/logo-{uploadId}.{ext}` (L536), `reassignLogo` (L212) used by logo request approve. Mechanics move to shared-kernel, company rules go to the CUMS handler.
- `.../service/LogoCleanupService.java` -- `@Scheduled` 02:00 (L60), ShedLock. Becomes the shared expiry job.
- `.../controller/LogoController.java` + `companies-api.openapi.yml` L50–L192 -- 6 operations, all deleted (cleanup statistics and trigger have no caller).
- `.../service/CompanyService.java` L105–127 create, L324–351 update, `applyRequestedLogo` L512 -- keep `logoUploadId`, read it through the new upload service.
- `.../service/CompanyLogoRequestService.java` L63, L79, L122 -- `ENTITY_TYPE="LOGO_REQUEST"`, key `logos/requests/{uploadId}/logo.{ext}`.
- `.../service/ImageUrlFetcher.java` -- moves to `shared-kernel/.../upload/`. Callers: LogoController L119, UserController L519, FederatedAvatarImportService L158.
- CUMS `domain/Logo.java`, `LogoRepository`, `V10` -- entity and repository removed, table kept. Next CUMS migration is V30.
- EMS `service/GenericLogoService.java`, `domain/Logo.java`, `repository/LogoRepository.java`, `EventController.associateThemeImage` (~L2255, called at L1018, L1157, L1259) -- removed. Theme image goes through the EMS handler. Next EMS migration is V153. `@EntityScan` and `@EnableJpaRepositories` list only `ch.batbern.events.*`.
- `shared-kernel/.../utils/S3Links.java` -- reuse `uploadUrl`. Shared-kernel already has S3, data-jpa and Flyway, but no entity, no scheduler and no auto-configuration (component scan only).
- `api-gateway/.../routing/DomainRouter.java` L106 `/api/v1/logos` → CUMS. Precedent for routing by segment: `/watch/pair` (L63), `/admin/test-fixtures/{svc}`.
- Frontend: `services/api/companyApi.ts` `logoUploadTarget` L276, `uploadLogoFromUrl` L295; `hooks/useFileUpload/useFileUpload.ts` (base; dev bucket hard-coded at L68); `components/shared/FileUpload/FileUpload.tsx` L116 defaults to the logo target, used by `CompanyForm` L494 and `EventForm` L921 (theme); `EventInfoTab.tsx` L83; `CompanyLogoSection.tsx` L57/L85; `useCompanyBatchImport.ts` L26.
- Tests: CUMS `LogoControllerTest`, `LogoUrlIntegrationTest`, `GenericLogoServiceTest`, `LogoCleanupServiceTest`, `CompanyLogoRequestIntegrationTest`; `TestAwsConfig` mocks S3Client and S3Presigner at the boundary. FE `companyApi.upload.test.ts`, `useFileUpload.test.ts`, `FileUpload.test.tsx`, `CompanyLogoSection.test.tsx`, `useCompanyBatchImport.test.tsx`, `msw/harness.test.ts` L40 (uses the logo path as its example). Bruno `file-upload-api/01–07`. No Playwright test of company create with a logo yet.
- Baselines: Spectral 94 lines, G12 = 4 `/logos` lines (L9, L10, L39, L40), G13 = 3 lines. Ratchet entries for `LogoControllerTest`, `LogoUrlIntegrationTest`, `CompanyLogoSection.test.tsx`, `CompanyLogoRequestPanel.test.tsx`. EMS ArchUnit store has 6 rules.

## Tasks & Acceptance

**Execution:**
- [x] `docs/api/uploads-api.openapi.yml` -- three operations, `purpose` enum (`company-logo`, `event-theme-image`), `target` as meaningful IDs (`companyName`, `eventCode`); remove `/logos/*` from `companies-api`; regenerate types -- contract first.
- [x] `shared-kernel/.../upload/` -- `UploadRecord` (`@MappedSuperclass`), `UploadStatus`, `UploadPurposeHandler` SPI (who may upload for the target, types, size, final key, what confirm attaches, `supportsImport`), `UploadService` (presign, confirm, associate, import), `UploadExpiryJob` logic, `ImageUrlFetcher` moved; unit tests for the state machine and expiry -- AC4.
- [x] CUMS `V30__create_company_user_uploads.sql` (empty table, no copy from `logos`), entity, repository, `UploadsController`, `CompanyLogoUploadHandler`; rewire `CompanyService`, `CompanyLogoRequestService`, `UserController`, `FederatedAvatarImportService` imports; delete `LogoController`, `GenericLogoService`, `Logo*`, `LogoCleanupService` -- AC1, AC2.
- [x] EMS `V153__create_event_management_uploads.sql`, entity, repository, `UploadsController`, `EventThemeImageUploadHandler`; `EventController` uses it; delete the EMS `GenericLogoService`, `Logo`, `LogoRepository`; add the ArchUnit rule "no EMS entity maps `logos`" with a fixture test -- AC3.
- [x] `DomainRouter.java` -- `/api/v1/uploads/{purpose}` routed by purpose (`event-…` → EMS, `company-…` → CUMS, unknown → 404); remove `/logos`; router unit test.
- [x] Frontend -- `services/uploadService.ts` with `uploadTarget(purpose, target?)` and `importUpload`; `useFileUpload` becomes the one hook (drop the hard-coded bucket); `FileUpload` gets a required `uploadTarget`; move every caller listed in the Code Map; migrate tests to MSW, `harness.test.ts` gets another example path.
- [x] Bruno `file-upload-api` rewritten for `/uploads/company-logo` (presign, confirm, invalid type, too large, old path 404). Playwright `e2e/companies/company-create-logo.spec.ts` (local dev).
- [x] Baselines -- `npm run lint:api -- --update`; remove G12 and G13, add the 3 logo request operations to X2 with the reason; `make check-test-ratchet`; record before and after counts.
- [x] Docs -- ADR-002 Implementation Status, `docs/api` README, `module-map.md` (upload component), mapped doc-drift docs; issue "drop `logos` (owner go)"; story notes for 11b: the EMS uploads table already exists.

**Acceptance Criteria:**
- Given local dev, when an organizer creates a company with a logo, replaces the logo of an existing company, a user requests a logo that an organizer approves, and an organizer sets a theme image on create and on edit, then each image shows, and the network log shows only `/uploads/*` calls.
- Given the six `/logos/*` operations, when called, then they answer 404, `companies-api` has no `/logos` path, and the Spectral baseline is smaller by G12's 4 lines; G13's 3 lines and the new confirm path sit under X2 (94 → 91).
- Given the EMS code, when the ArchUnit rule runs, then no EMS entity maps `logos`, and the fixture test proves the rule catches one.

## Review Triage Log

Pass 1 (quick, 2026-11-12): 11 findings. medium 5, low 4, false 1; rejected: 6 (low), 7 (false), 9 (low).

| # | Finding | Verdict | Route | Evidence |
|---|---|---|---|---|
| 1 | Baseline 94 → 91, not 90 | medium | resolved by owner (a) | Measured: the new `POST /uploads/{purpose}/{uploadId}/confirm` adds a uuid-path-param line; the path shape is ADR-002's owner contract of 2026-11-07, so no spec change avoids it. Asked the owner instead of reverting: the code is the same for either answer. |
| 2 | X2 extended to the confirm path without owner go | medium | resolved by owner (a) | Same root cause as 1; `api-lint-owners.txt` X2 now holds the confirm path. |
| 3 | Specs omit the new 404/409 on company and event create/update/patch | medium | patch | `createEvent` lists 403,201,400,500; `requireThemeImage` and `CompanyService` now throw 404/409. |
| 4 | Temp S3 object deleted inside the DB transaction | medium | patch | `UploadService.associate` copies, deletes temp, saves; `CompanyLogoRequestService.request` rolls back on Mail Center 502, row returns to CONFIRMED with a deleted key, retry 500. Same order as the old GenericLogoService, now shared. |
| 5 | `FileUpload.test.tsx` still mocks `useFileUpload` | medium | patch | `vi.mock` at L17; `FileUpload.tsx` changed, touch rule applies. |
| 6 | `UploadServiceTest` fakes its own repository | low | reject | Real, but the state machine also runs against PostgreSQL in the CUMS and EMS upload ITs; the fix needs a JPA test context in shared-kernel, more than a direct correction. |
| 7 | No issue for the `logos` drop | false | reject | #1307 "Drop the unused logos table (owner go), after Story 17.11" is open. |
| 8 | Playwright header claims cleanup removes uploads | low | patch | `deleteCompany` deletes only the company row; direct comment correction. |
| 9 | Preview blob URLs never revoked | low | reject | Real; one object URL per chosen file until unload, rarely met; fix adds lifecycle code. |
| 10 | Other-purpose 404 test uses a made-up id | medium | patch | No row inserted, so `UploadService.live`'s purpose filter is never reached; the matrix row is uncovered. |
| 11 | Expiry cut-off inclusive vs strict | low | patch | `isExpired` uses `!isBefore`, query uses `Before`; one-word correction. |

## Design Notes

**Architecture note.** Modules: shared-kernel `upload` (new), CUMS companies/users, EMS events (theme image), api-gateway routing, frontend services and hooks. New dependencies: CUMS and EMS → `shared-kernel.upload`; EMS `@EntityScan` stays on its own entity (the base class is `@MappedSuperclass`). New endpoints: 3 under `/uploads/{purpose}`. The nearest are `/logos/presigned-url` and `/logos/{uploadId}/confirm`; they cannot be extended because they belong to CUMS and the theme image is EMS data (ADR-002 2026-11-07). Reused: `S3Links`, `ImageUrlFetcher`, the ShedLock setup, `useFileUpload`. Deviation: none.

**Tables.** All services share one database, so the tables are named per service: `company_user_uploads` and `event_management_uploads`. That is how "own `uploads` table" is implemented here.

**Cleaner.** Removed: 2 GenericLogoService classes, 2 Logo entities and repositories, LogoController, LogoCleanupService, 6 operations, the FileUpload default that sent theme images through the logo endpoints, and the hard-coded dev bucket. Baseline 94 → 91 (G12 gone; G13 and the new confirm path under X2, owner 2026-11-12). EMS ArchUnit store gains no entry: the new rule passes without a freeze.

## Verification

**Commands:**
- `./gradlew :shared-kernel:test :services:company-user-management-service:test :services:event-management-service:test :api-gateway:test --max-workers=4` -- green (run one at a time)
- `cd web-frontend && npm run type-check && npx vitest run --maxWorkers=4 && npm run lint:api && npm run check:api-paths` -- green
- `make check-test-ratchet` -- no RAISED line
- Bruno `file-upload-api`, `companies-api` against local dev; Playwright `company-create-logo.spec.ts` -- green

**Manual checks:**
- Local dev: the walkthrough in the first AC.
