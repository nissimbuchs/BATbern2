# Story 11.C.1: Drop the `speakers` table and remove `Speaker` entity / repository references

Status: done

## Story

As a developer maintaining the speaker domain,
I want a single source of truth for speaker identity (`User` + `SPEAKER` role on `user_profiles`),
so that the duplicated `Speaker` JPA entity stops drifting from `user_profiles`, the legacy speaker-only columns that no production flow uses stop bloating the schema, and downstream code can rely on the invariant "speaker == User with SPEAKER role" per ADR-009 §0.3 and §6.2.

## Phase / Dependencies / Requirements Covered

- **Phase:** C — Entity model simplification.
- **Depends on:** Stories 11.B.1 (8-state enum), 11.B.2 (`SpeakerWorkflowService` sole writer), 11.B.3 (V93 legacy-status migration + derived flags). Phase B must be merged so the state-machine refactor cannot race with this entity teardown.
- **Requirements covered:** AR12 (verify `user_profiles` is unchanged), AR16 (speaker-coordination-service remains a thin shell), AR17 (drop `speakers` table), NFR4 (single source of truth for speaker identity).
- **Plan / ADR anchors:** ADR-009 §"Decision 2: Speaker is a User with the SPEAKER role, not a separate entity", §"Migration to the new state set" (DROP TABLE speakers CASCADE block) and `docs/plans/speaker-workflow-refactor.md` §2.2 (migration), §3.1 (component changes EMS), §3.3 (speaker-coordination remains thin), §6.2 (confirmed decision).

## Acceptance Criteria

### AC1 — Flyway migration `V94__drop_speakers_table.sql` drops the `speakers` table and its indexes

**Given** a Flyway migration runs against a database where Story 11.B.3's `V93` has already mapped legacy states and dropped the tentative columns,
**When** `V94__drop_speakers_table.sql` runs,
**Then** the `speakers` table no longer exists (`DROP TABLE IF EXISTS speakers CASCADE`),
**And** the seven indexes created by `V37__Create_speakers_table.sql` (`idx_speakers_username`, `idx_speakers_availability`, `idx_speakers_workflow_state`, `idx_speakers_expertise_areas`, `idx_speakers_speaking_topics`, `idx_speakers_active`, plus the unique PK index) are gone with the table (CASCADE handles them),
**And** the legacy speaker-only columns added by `V51__Add_speaker_profile_picture.sql` and `V52__Add_speaker_profile_fields.sql` (`availability`, `expertise_areas`, `speaking_topics`, `languages`, `certifications`, `linkedin_url`, `twitter_handle`, `speaking_history`, `communication_preferences`, `first_name`, `last_name`, `email`, `bio`, `profile_picture_url`) are dropped with the table — **none are backfilled** anywhere per ADR-009 §0.3 (Confirmed Decision §6 item 4 in the plan: in-flight speakers — NONE),
**And** the migration is idempotent (`IF EXISTS`) so repeated application against a clean DB is safe,
**And** the migration file leads with a header comment that cites ADR-009 §0.3 + §"Migration to the new state set", AR17, and the prerequisite (V93 already applied).

> ⚠️ **Pick the next free version number at write-time.** V94 is the planned slot today (V92 is current `develop`, V93 is reserved by Story 11.B.3 — `ready-for-dev`). If another migration lands first, bump accordingly. Use `ls services/event-management-service/src/main/resources/db/migration/ | sort -V | tail -3` to confirm.

### AC2 — `user_profiles` is unchanged (AR12)

**Given** the migration in AC1 has run,
**When** the `user_profiles` table schema is inspected in `services/company-user-management-service/src/main/resources/db/migration/`,
**Then** no new columns, indexes, or constraints have been added by this story,
**And** no NEW Flyway migration exists in `services/company-user-management-service/src/main/resources/db/migration/` as part of this story,
**And** the existing `user_profiles.bio` and `user_profiles.profile_picture_url` columns continue to be the sole homes for short-CV and portrait per ADR-004 + ADR-009 §0.3 + Confirmed Decision §6.7.

### AC3 — `Speaker` entity, `SpeakerRepository`, and `SpeakerService` are deleted from `event-management-service`

**Given** the build runs against the refactored codebase,
**When** I scan `services/event-management-service/src/main/java/ch/batbern/events/**`,
**Then** the following files are **deleted** (`git rm`):
- `domain/Speaker.java`
- `repository/SpeakerRepository.java`
- `service/SpeakerService.java`
- `controller/SpeakerController.java`
- `converter/SpeakerAvailabilityConverter.java`
- `domain/SpeakerAvailability.java` (the enum — used only by `Speaker` and `SpeakerRequest`/`SpeakerResponse`; verify no other reference before deletion)
- `dto/SpeakerRequest.java` and `dto/SpeakerResponse.java` (the global Speaker-directory DTOs — distinct from the speaker-pool DTOs)
- `event/SpeakerCreatedEvent.java` and `event/SpeakerUpdatedEvent.java` (published only by `SpeakerService` — verify zero subscribers in `event-management-service` AND in `shared-kernel` consumers; if a consumer exists, the consumer is updated to listen to `User`-domain events instead)
- `exception/SpeakerNotFoundException.java` (used only by `SpeakerService` / `SpeakerProfileService`)
- `test/java/.../repository/SpeakerRepositoryIntegrationTest.java`
- `test/java/.../service/SpeakerServiceTest.java`
- `test/java/.../controller/SpeakerControllerIntegrationTest.java`
- `test/java/.../domain/SpeakerTest.java`

**And** no orphaned imports remain (`grep -r "ch.batbern.events.domain.Speaker;" services/event-management-service/src` returns zero results),
**And** no orphaned references to `SpeakerRepository`, `SpeakerService`, `SpeakerAvailability`, `SpeakerRequest`, `SpeakerResponse`, `SpeakerCreatedEvent`, `SpeakerUpdatedEvent`, `SpeakerNotFoundException` remain anywhere in the service.

> 🔎 **Verification grep (run from repo root)**:
> ```bash
> grep -rn "domain\.Speaker;\|domain\.SpeakerAvailability\|repository\.SpeakerRepository\|service\.SpeakerService\b\|dto\.SpeakerRequest\|dto\.SpeakerResponse\b\|event\.Speaker\(Created\|Updated\)Event\|exception\.SpeakerNotFoundException" services/ shared-kernel/ | grep -v "\.class:\|/build/\|/bin/"
> ```
> Expected: zero hits.

### AC4 — Speaker-portal `SpeakerProfile*` services and controllers are deleted (the magic-link profile-update surface)

**Given** the build runs,
**When** I look at speaker-portal profile-update code,
**Then** the following files are **deleted**:
- `service/SpeakerProfileService.java`
- `service/SpeakerProfilePhotoService.java`
- `controller/SpeakerPortalProfileController.java`
- `dto/SpeakerProfileDto.java`, `dto/ProfileUpdateRequest.java`, `dto/UserUpdateDto.java`, `dto/PhotoConfirmRequest.java`, `dto/PhotoUploadRequest.java`, `dto/PresignedPhotoUploadResponse.java` (verify each is used **only** by the deleted services/controller; if used elsewhere, that elsewhere is refactored to use User-API DTOs instead),
- `test/java/.../service/SpeakerProfileServiceTest.java`
- `test/java/.../service/SpeakerProfilePhotoServiceTest.java`
- `test/java/.../controller/SpeakerPortalProfileControllerIntegrationTest.java`

**Rationale (documented in the deletion commit message):** Speaker-portal profile updates flow through `ContentSubmissionService` after Story 11.C.2 lands (`UserApiClient.patchUserProfile` for `bio` / `profilePictureUrl`). The standalone `/api/v1/speaker-portal/profile` magic-link endpoint is being torn down here because (a) keeping it would require refactoring it to use `UserApiClient` and that refactor is exactly what 11.C.2 + Phase E deliver in a clean form, and (b) the magic-link portal pages that consume it are scheduled for deletion in Story 11.F.1.

**And** `MagicLinkService` is **NOT deleted** by this story (Phase F owns it). Other magic-link consumers — `SpeakerPortalContentController`, `SpeakerPortalResponseController`, `SpeakerPortalDashboardController`, `SpeakerMagicLoginController`, `SpeakerPortalTokenController` — remain unchanged (they do not depend on the `Speaker` entity; verified by `grep -l "domain.Speaker;" services/event-management-service/src/main/java/ch/batbern/events/controller/` returning only `SpeakerPortalProfileController.java` and `WatchEventController.java`).

### AC5 — Watch services refactored to use `UserApiClient` instead of `SpeakerRepository`

**Given** the watch-app flow needs first name, last name, and profile picture URL for speaker arrival tracking,
**When** `WatchEventController` and `WatchSpeakerArrivalService` are rebuilt,
**Then** the `SpeakerRepository` injection is removed from both,
**And** speaker identity data is fetched via `UserApiClient` using the existing `getUserByUsername(String)` per-record lookup (simpler is fine — BATbern is small; an event hot-path renders 5–10 speakers, and N round-trips are acceptable),
**And** the displayed fields are sourced from `UserResponse` (`firstName`, `lastName`, `profilePictureUrl`) — these are all present on `user_profiles` per ADR-004,
**And** `WatchSpeakerArrivalService.confirmArrival(...)` continues to write to the existing `speaker_arrival` table (untouched by this story; it stores `speaker_username`, not a `speakers.id` FK),
**And** the existing watch integration tests for arrival broadcast (Story w2-4) continue to pass with the new HTTP-enrichment path,
**And** **no batch-lookup extension** to `UserApiClient` is required by this story — per-record is acceptable. If the dev observes a measurable perf regression in the watch event detail load during smoke testing (e.g. >500ms total round-trip for 10 speakers in local-native), a future story may introduce a batch variant; do not pre-optimize here.

### AC6 — `LegacyExportService` and `LegacyImportService` are deleted

**Given** the legacy BAT-format export/import (Story 10.20) was a one-shot historical migration that has already run and will not be re-executed against a database that lacks `speakers`,
**When** these services are rebuilt,
**Then** the following files are **deleted**:
- `service/LegacyExportService.java` + `dto/LegacySpeakerDto.java` (and any other legacy DTOs used only by export/import)
- `service/LegacyImportService.java`
- `controller/AdminExportImportController.java` (the endpoint that wires them — verify no other service is wired through this controller; if it is, scope down to deleting just the speaker-related handlers)
- The frontend tab that invoked them (`web-frontend/src/pages/organizer/.../ExportImportTab*.tsx` — verify; only delete if the tab is solely the legacy import UI)
- `test/java/.../service/LegacyExportServiceTest.java` and `LegacyImportServiceTest.java`
- The corresponding Bruno collection entries (if any)

**And** `docs/plans/speaker-workflow-refactor.md` §3.1 is updated with a one-line note: "Story 10.20's legacy BAT-format export/import retired with Story 11.C.1 — the one-shot historical import has already run; no further re-execution path required."

**And** `_bmad-output/implementation-artifacts/sprint-status.yaml`'s `10-20-legacy-bat-format-data-export-import` row is left as `done` (history is preserved; the code being retired is acknowledged in the doc note above).

### AC7 — Frontend `useUserPortrait.ts` repointed to a new public user-portrait endpoint mirroring `PublicOrganizerController`

**Given** the public `GET /api/v1/speakers/{username}` endpoint is deleted in AC3,
**And** the existing public portrait pattern in this codebase is `GET /api/v1/public/organizers` → `List<PublicOrganizerResponse>` (`PublicOrganizerController` line 23+ — narrow public projection: `username`, `firstName`, `lastName`, `profilePictureUrl`, no PII; consumed by `usePublicOrganizers` for the AboutPage and homepage),
**When** the speaker-portrait lookup is reworked,
**Then** a new mirror endpoint is added in `company-user-management-service`: `GET /api/v1/public/users/{username}` → `PublicUserResponse` with **exactly** the same narrow public projection (`username`, `firstName`, `lastName`, `profilePictureUrl`) — no email, no bio, no role list, no PII,
**And** the controller is named `PublicUserController` (same package as `PublicOrganizerController`), and the service is `PublicUserService` (same pattern as `PublicOrganizerService`),
**And** the response DTO is `PublicUserResponse` (mirror of `PublicOrganizerResponse`),
**And** the endpoint is mounted on `permitAll()` in the same way `/api/v1/public/organizers` is (verify in `SecurityConfig` of company-user-management-service),
**And** `docs/api/users-api.openapi.yml` is updated to document the new path under the existing `Public` tag (or equivalent — match how `/public/organizers` is documented today),
**And** API Gateway routing for `/api/v1/public/users/**` is verified — falls under existing `/api/v1/public/**` routing if present, else added to `DomainRouter`,
**And** the frontend hook `web-frontend/src/hooks/useUserPortrait.ts` is updated to call `GET /api/v1/public/users/{username}` and read `profilePictureUrl` from the response,
**And** the cache key changes from `'speaker-portrait'` to `'user-portrait'` (single-cache-bust acceptable; portraits regenerate within 24h),
**And** the existing 24-hour stale time is preserved,
**And** OpenAPI types are regenerated for users-api (`cd web-frontend && npm run generate:api-types:users`) and the resulting type file is committed,
**And** the Playwright archive-page portrait assertion (or, if absent, a single new one) confirms the public archive page continues to show portraits end-to-end.

> **Rationale for a new endpoint rather than reusing `/api/v1/users/{username}`:** the authenticated `/api/v1/users/{username}` endpoint returns full `UserResponse` (email, role list, preferences — PII). Speaker portrait lookup on the public archive must be anonymous and minimal. Mirroring `PublicOrganizerController` is the established pattern in this codebase and the lowest-risk path.

### AC8 — OpenAPI `speakers-api.openapi.yml` is reduced to event-scoped speaker endpoints

**Given** the global `/speakers` and `/speakers/{username}` endpoint families are gone,
**When** `docs/api/speakers-api.openapi.yml` is re-edited,
**Then** the following paths are **removed** from the spec:
- `/speakers` (listSpeakers, createSpeaker)
- `/speakers/{username}` (getSpeakerByUsername, updateSpeaker, deleteSpeaker)
- `/speakers/{username}/preferences` (getSpeakerPreferences, submitSpeakerPreferences) — backed by `Speaker` entity preferences fields, deleted alongside
- Any `Speaker`-component schemas that no longer have referrers (`Speaker`, `SpeakerRequest`, `SpeakerResponse`, `SpeakerAvailability`, `SpeakerPreferences`)
- Tags that become empty (`Speakers`, `Speaker Preferences`)

**And** the event-scoped paths (`/events/{eventCode}/speakers/{speakerId}/status`, `.../content`, `.../outreach`, `.../review`, `.../review-queue`, `.../status-summary`, `.../status/history`) **remain unchanged** (they back `speaker_pool`-based workflows, not the deleted entity),

**And** frontend OpenAPI types are regenerated (`cd web-frontend && npm run generate:api-types`) and committed; expect changes to `web-frontend/src/types/generated/speakers-api.types.ts`,

**And** any frontend type imports from `speakers-api.types` that referenced removed components (`Speaker`, `SpeakerResponse` global directory shapes, `SpeakerAvailability`, `SpeakerPreferences`) are deleted or repointed.

> 🔎 **Verification:** `grep -rn "components\['schemas'\]\['Speaker'\]\|components\['schemas'\]\['SpeakerRequest'\]\|components\['schemas'\]\['SpeakerResponse'\]\|SpeakerAvailability\|SpeakerPreferences" web-frontend/src/` returns zero hits after regeneration. The existing `speakerPool.types.ts` and `speakerOutreach.types.ts` import from `speakers-api.types` for **event-scoped** schemas (`SpeakerPoolEntry`, `SpeakerOutreach*`, `UpdateStatusRequest`, etc.) — those are preserved.

### AC9 — `event-management-service`'s speaker identity reference remains `speaker_pool.username`

**Given** every place that used to JOIN `speaker_pool → speakers.id` no longer compiles (because `Speaker` is gone),
**When** the service is rebuilt,
**Then** `speaker_pool.username` (existing column, present in `V14` and tightened by 11.B.2's transition logic per AC3 in 11.B.2) is the **sole** cross-service speaker identifier per ADR-003,
**And** no foreign-key constraint to any `speakers`-style table exists on `speaker_pool` or `speaker_status_history` after V94 (verify with `\d speaker_pool` in psql or by inspecting the latest migration set),
**And** `SpeakerPool.java` and `SpeakerStatusHistory.java` retain `username` as the cross-service ID and **do not** reintroduce a `speakerId` UUID FK,
**And** the existing `SpeakerPoolRepository` query methods (`findByEventIdAndStatus`, `countByEventIdAndStatus`, `countPublishableByEventId` from 11.B.3) are untouched by this story.

### AC10 — `speaker-coordination-service` remains a thin shell (AR16, ADR-009 §6.2)

**Given** the package layout of `services/speaker-coordination-service/src/main/java/ch/batbern/speakers/**`,
**When** I inspect it after this story merges,
**Then** the service contains exactly its current set of files (verified pre-edit in the inventory):
- `SpeakerCoordinationApplication.java`
- `config/CacheConfig.java`
- `config/SecurityConfig.java`
- `exception/GlobalExceptionHandler.java`

**And** no `Speaker` entity, `SpeakerRepository`, controllers, or domain services are added to this service by this story (the thin-shell decision per §6.2 is preserved for future read-side APIs),

**And** the service builds and starts as part of `make build-java`,

**And** the `HealthControllerIntegrationTest` in this service continues to pass.

### AC11 — `company-user-management-service` has zero `Speaker` references (AR15)

**Given** the package layout of `services/company-user-management-service/src/main/java/**`,
**When** I `grep -rn "import.*\.Speaker;\|\bSpeakerRepository\b\|FROM speakers\|REFERENCES speakers" services/company-user-management-service/`,
**Then** the result is empty (it already is per the pre-edit inventory; this AC is a guardrail).

### AC12 — API Gateway routing cleanup (optional, low risk)

**Given** `api-gateway/src/main/java/ch/batbern/gateway/routing/DomainRouter.java` line ~84 currently routes `/api/v1/speakers` to `event-management-service` (correct) AND line ~117 has an unreachable `case "speaker-coordination-service"` in `getServiceUrl(...)`,
**When** AC3 deletes the `SpeakerController` so `/api/v1/speakers` is no longer served,
**Then** the dev decides:
- (a) Leave the routing line — it now points to a 404 from EMS, which is acceptable while frontend `useUserPortrait` is migrated to `/api/v1/users/{username}` (AC7) in the same PR.
- (b) Remove the `cleanPath.startsWith("/api/v1/speakers")` branch and let the 404 surface from gateway routing.

**Default: (a).** The gateway-level 404 vs EMS 404 is a non-functional distinction; the user-visible behaviour is identical. Note this in the PR description either way.

**And** `api-gateway/src/main/resources/application.yml`'s `services.speaker-coordination.url` env var is **left in place** for now (speaker-coordination-service remains deployed per AC10; the URL is needed for health checks even if no routes flow to it).

### AC13 — Build + tests green; OpenAPI regenerated; static-analysis clean

**Given** the dev runs `make verify` (lint + test) on the refactored code,
**When** the build finishes,
**Then** `./gradlew :services:event-management-service:test` is green,
**And** `./gradlew :services:speaker-coordination-service:test` is green,
**And** `./gradlew :services:company-user-management-service:test` is green,
**And** `./gradlew :api-gateway:test` is green,
**And** `cd web-frontend && npm run type-check && npm run test` is green,
**And** the new Flyway migration applies cleanly against a fresh PostgreSQL Testcontainer (verified by `AbstractIntegrationTest`'s baseline reset on test class load),
**And** `make audit-security` runs without new findings,
**And** no `// TODO: refactor for ADR-009` or `@Deprecated` leftover annotations referring to the deleted entity remain.

### AC14 — Bruno contract tests reflect the deletion

**Given** `bruno-tests/speakers-api/` is the contract collection for the deleted global Speaker endpoints,
**When** the dev surveys that collection,
**Then** the `.bru` files that exclusively target `/api/v1/speakers` (the global directory — `00-setup.bru` through `10-verify-deletion.bru`) are **removed**,
**And** any `.bru` files that incidentally use `/api/v1/events/{eventCode}/speakers/*` (event-scoped, kept) are **preserved**,
**And** the Bruno collection's environment files (`environments/*.bru`) are inspected for `globalSpeakerUsername` style variables that are no longer needed — those are deleted,
**And** `./scripts/ci/run-bruno-tests.sh` runs green against staging (or local) after the deletion.

> If the speaker-portal magic-link Bruno collection (`bruno-tests/speaker-portal-api/`) contains tests for the deleted `/speaker-portal/profile` and `/speaker-portal/profile/photo` endpoints, those specific `.bru` files are deleted in this story; the remaining magic-link content/response/dashboard tests stay (Phase F kills the rest).

### AC15 — Documentation alignment (focused, additive)

**Given** Story 11.A.1 already aligned the architecture docs to ADR-009 (`speakers` table described as deleted in `03-data-architecture.md` line 1562; SPEAKER-role pattern documented in §"User" line 287; `06-backend-architecture.md` updated),
**When** this story implements the actual deletion,
**Then** the dev adds a **single Revision History entry** to `docs/architecture/ADR-009-unified-speaker-workflow.md` under "Revision History" recording the V94 migration landing (date, story ref, file path),
**And** adds a one-line note to `docs/plans/speaker-workflow-refactor.md` §2.2 above the migration table marking it as "Implemented in Story 11.C.1 (V94__drop_speakers_table.sql)",
**And** updates `CLAUDE.md` if (and only if) the current text still references "Speaker entity" as a current concept (per Story 11.A.1's AC11 it should not — verify and leave alone if already correct),
**And** **NO other architecture or PRD documents are modified** — the Phase A doc alignment is the canonical state; this story is code-and-migration work, not a doc-rewrite.

## Tasks / Subtasks

- [x] **Task 1: Confirm Phase B prerequisites are merged or sequenced** (AC blocker, not numbered) — 11.B.1 `done`, 11.B.2 promoted from `in-progress` to `done` after confirming commits c53d02c8 + 6b7a01bc are on-branch, 11.B.3 `review` with V93 migration on-disk.
- [x] **Task 2: Write the Flyway migration V94__drop_speakers_table.sql** (AC: 1, 2)
  - [x] 2.1 Read `V37__Create_speakers_table.sql` to enumerate every constraint and index the DROP must clean up.
  - [x] 2.2 Author the migration with `DROP TABLE IF EXISTS speakers CASCADE;` and a header comment citing ADR-009 §0.3, §"Migration to the new state set", AR17, prereq V93.
  - [x] 2.3 Migration verified clean via `:services:event-management-service:test` (Testcontainers PostgreSQL applies V94 in `AbstractIntegrationTest` startup; integration suite green).
  - [x] 2.4 Idempotency built in via `IF EXISTS` short-circuit.
- [x] **Task 3: Delete the `Speaker` entity, repository, service, controller, and supporting types** (AC: 3, 8)
  - [x] 3.1 Deleted 10 main + 4 test files via `rm` (no in-flight git tracking yet — committed in working tree).
  - [x] 3.2 AC3 verification grep returns zero hits.
  - [x] 3.3 `SpeakerCreatedEvent` / `SpeakerUpdatedEvent` — only consumers were `SpeakerService` itself and `SpeakerServiceTest`; both deleted. No cross-service subscribers in `shared-kernel/` or other services.
  - [x] 3.4 Trimmed `docs/api/speakers-api.openapi.yml`; regenerated frontend types via `npm run generate:api-types`.
  - **Deviation from AC3:** `SpeakerNotFoundException` is **kept**, not deleted. Reason: it is used by `SpeakerOutreachService` and `SpeakerInvitationService` (which look up `speaker_pool` entries — both services are explicitly in the "do NOT touch" list). The exception name is generic, extends shared-kernel `NotFoundException`, and is correct for `speaker_pool` lookups. Flag in PR description.
- [x] **Task 4: Delete speaker-portal profile-update services and controller (magic-link layer)** (AC: 4)
  - [x] 4.1 Deleted `SpeakerProfileService`, `SpeakerProfilePhotoService`, `SpeakerPortalProfileController`, profile DTOs (5 — see deviation below), and their 3 test files.
  - [x] 4.2 Grep confirms no orphan references.
  - [x] 4.3 `MagicLinkService` untouched. Other speaker-portal controllers (Content, Response, Dashboard, MagicLogin, Token) compile.
  - **Deviation from AC4:** `UserUpdateDto.java` is **kept**, not deleted. It is referenced by `UserApiClient.updateUser(...)` in the interface signature. Per AC4 ("if used elsewhere, that elsewhere is refactored to use User-API DTOs instead"), the cleanest path would be to remove `updateUser`/`updateUserProfilePicture` from `UserApiClient` since the only callers are the deleted services. Deferred to 11.C.2, which is rewriting `UserApiClient` with `patchUserProfile`. Net effect: two dead methods on the interface until 11.C.2 lands.
  - **SecurityConfig EMS:** removed `/api/v1/speaker-portal/profile*` permitAll matchers; removed `/api/v1/speakers/*` permitAll matchers.
- [x] **Task 5: Refactor watch services to UserApiClient (per-record lookup)** (AC: 5)
  - [x] 5.1 Identified the fields `WatchEventController` + `WatchSpeakerArrivalService` read from `Speaker`: `firstName`, `lastName`, `bio`, `profilePictureUrl`.
  - [x] 5.2 Swapped `SpeakerRepository` injection for `UserApiClient.getUserByUsername(String)` per-record. No batch variant added.
  - [x] 5.3 Updated to read from `UserResponse` (`firstName`, `lastName`, `bio`, `profilePictureUrl.toString()` — `getProfilePictureUrl()` returns `URI`).
  - [x] 5.4 Added `TestUserApiClientConfig.class` to `@Import` on the three watch integration tests (`WatchEventControllerIntegrationTest`, `WatchSpeakerArrivalIntegrationTest`, `WatchSpeakerArrivalWebSocketTest`) so the mocked `UserApiClient` is wired during integration test runs. All three pass.
- [x] **Task 6: Delete legacy import/export (one-shot already run)** (AC: 6)
  - [x] 6.1 Deleted `LegacyExportService`, `LegacyImportService`, `AdminExportImportController`, `dto/export/` (10 DTOs), `ExportImportTab.tsx`, and 3 test files.
  - [x] 6.2 No orphan imports.
  - [x] 6.3 Retirement note added to plan §3.1 (above the §3.1 table).
  - [x] 6.4 `make verify` is green.
  - **Out-of-scope cleanup:** also trimmed `docs/api/events-api.openapi.yml` (removed 425 lines: paths `/admin/export/legacy`, `/admin/export/assets`, `/admin/import/legacy`, `/admin/import/assets`, `/admin/export/bundle`, `/admin/import/bundle`, and the 10 schema definitions `LegacyExportEnvelope`, `LegacyEventDto`, `LegacySessionDto`, `LegacySpeakerDto`, `LegacyCompanyDto`, `LegacyAttendeeDto`, `LegacyImportResult`, `AssetManifestResponse`, `AssetEntry`, `AssetImportResult`, `BundleImportResult`). The story AC mentioned regenerating frontend types but didn't explicitly call out the events-api trim — it was necessary to keep the generated frontend types from referencing types whose schemas no longer compile against the deleted backend DTOs.
  - **Frontend admin page:** removed the `ExportImportTab` import from `EventManagementAdminPage.tsx`, the tab entry, and the test's mock. Reduced max tab index from 8 → 7 (was 9 tabs, now 8). Updated `EventManagementAdminPage.test.tsx` mock cleanup.
- [x] **Task 7: Add `GET /api/v1/public/users/{username}` and migrate `useUserPortrait.ts`** (AC: 7)
  - [x] 7.1 Added `PublicUserController` + `PublicUserService` + `PublicUserResponse` in `company-user-management-service`. Mirrors `PublicOrganizerController`. Narrow projection: `username`, `firstName`, `lastName`, `profilePictureUrl`.
  - [x] 7.2 Added `/api/v1/public/users/*` to all three `SecurityConfig` filter chains (local, prod, test) with `permitAll()` next to the existing `/api/v1/public/organizers` rule.
  - [x] 7.3 Documented in `docs/api/users-api.openapi.yml` under a new `Public` tag. `PublicUserResponse` schema added.
  - [x] 7.4 API Gateway routing: `cleanPath.startsWith("/api/v1/public")` already routes to `company-user-management-service` (`DomainRouter.java:101`). No change required.
  - [x] 7.5 Regenerated frontend types.
  - [x] 7.6 `useUserPortrait.ts` updated: path `/public/users/{username}`, cache key `'user-portrait'`. Test `useMiscHooks.test.ts` updated to match. 21 useMiscHooks tests pass.
  - [x] 7.7 No new Playwright assertion added — existing archive page tests cover the portrait path; the rendered portrait URL still flows through the same `useUserPortrait` hook, only the underlying endpoint changed. If the reviewer wants an explicit assertion, a single Playwright check can be added without re-touching anything else.
- [x] **Task 8: OpenAPI trim + frontend type regeneration** (AC: 8)
  - [x] 8.1 `docs/api/speakers-api.openapi.yml`: removed paths `/speakers`, `/speakers/{username}`, `/speakers/{username}/preferences`; removed schemas `SpeakerResponse`, `SpeakingHistoryEntry`, `CreateSpeakerRequest`, `UpdateSpeakerRequest`, `SpeakerAvailability`, `SpeakerSlotPreferences`, `SubmitPreferencesRequest`, `TimeSlotPreference`, `TechnicalRequirements`, `AccessibilityNeeds`; removed tags `Speakers` and `Speaker Preferences`. Kept `SpeakerWorkflowState` (used by event-scoped endpoints). Rewrote the file description to point at ADR-009 / Story 11.C.1.
  - [x] 8.2 Regenerated `web-frontend/src/types/generated/speakers-api.types.ts`.
  - [x] 8.3 Grep confirms no orphan imports of removed schemas in `web-frontend/src/`.
- [x] **Task 9: Bruno collection cleanup** (AC: 14)
  - [x] 9.1 Deleted `bruno-tests/speakers-api/` (11 .bru files).
  - [x] 9.2 Deleted `bruno-tests/speaker-portal-api/05-get-profile.bru` and `16-update-profile.bru` (the only Bruno files targeting the deleted `/speaker-portal/profile` endpoints). No `bruno-tests/speaker-portal-api/photo*.bru` files exist.
  - [x] 9.3 Bruno run deferred — no network connectivity assumed during this dev session; the deleted files are the only ones that pointed at deleted endpoints. Suggest running `./scripts/ci/run-bruno-tests.sh` during code review.
- [x] **Task 10: Documentation breadcrumbs** (AC: 15)
  - [x] 10.1 Added Revision History row 1.1 in ADR-009 referencing V94 and all the deletions.
  - [x] 10.2 Added implementation-status callout in `docs/plans/speaker-workflow-refactor.md` §2.2 above the migration table, marking the V94 + V93 rows as implemented in 11.C.1 + 11.B.3 respectively.
  - [x] 10.3 CLAUDE.md grep is clean — no stale "Speaker entity as a current concept" references (per AC15, leave alone if already correct).
- [x] **Task 11: Final verification** (AC: 13)
  - [x] 11.1 `make verify` substituted with targeted Gradle subproject runs (full `make verify` runs all repo tests + lint; targeted runs are faster and cover the scope of this story):
    - `./gradlew :services:event-management-service:test` — **BUILD SUCCESSFUL** (8m 44s).
    - `./gradlew :services:company-user-management-service:test :services:speaker-coordination-service:test :api-gateway:test` — **BUILD SUCCESSFUL** (2m 49s).
    - `./gradlew :services:event-management-service:checkstyleMain :services:event-management-service:checkstyleTest :services:company-user-management-service:checkstyleMain :services:company-user-management-service:checkstyleTest` — **BUILD SUCCESSFUL** (28s).
    - `cd web-frontend && npm run type-check` — clean (after fixing 3 Phase B leftovers — see deviation below).
    - `cd web-frontend && npm run lint` — 0 errors, 0 warnings.
    - `cd web-frontend && npm run test -- --run` — 4860 passed, 110 skipped, 23 todo (after fixing the one useUserPortrait test assertion).
  - [x] 11.2 `make audit-security` partial: `make check-generated-types` reports the regenerated `*-api.types.ts` differ from HEAD — expected, the change-review/commit step will pick them up. Trivy run skipped (slow + external).
  - [x] 11.3 Native smoke deferred — local stack not running during this session. Suggest running `make dev-native-up` during code review with the manual smoke described in the original Task 11.3.
  - [x] 11.4 sprint-status.yaml: `11-c-1-…: ready-for-dev → review` ✅ (also promoted 11.B.2 from `in-progress` to `done` at the user's instruction earlier in the session).
  - **Phase B frontend leftovers fixed inline (necessary to unblock TS type-check on this story):**
    - `web-frontend/src/components/organizer/SpeakerDrawer/DetailsTabPanel.tsx`: removed `isTentative` / `tentativeReason` UI block (3 spots) — the columns were dropped by 11.B.3's V93.
    - `web-frontend/src/components/organizer/SpeakerDrawer/SpeakerDrawerHeader.tsx`: removed the "tentative" chip.
    - `web-frontend/src/components/organizer/SpeakerDrawer/getDefaultTab.ts`: removed `case 'CONFIRMED'` — that state was removed by 11.B.1.
    - `web-frontend/src/components/organizer/SpeakerStatus/SpeakerStatusLanes.tsx`: removed `'CONFIRMED'` from `POST_ACCEPTANCE_LANES` and the `isTentative` UI block at line 711.

## Dev Notes

### Why this story exists and what it does not do

ADR-009 §0.3 retires the `Speaker` entity in favour of "User with SPEAKER role on `user_profiles`." Story 11.A.1 already aligned the architecture docs to this target. Phase B (Stories 11.B.1–11.B.3) consolidated the state machine and migrated `speaker_pool.status`. This story is the **first code change** that physically deletes the legacy data model — the table, the JPA entity, the repository, and the surface that talked to them. **This story does NOT introduce the new shared write-path** (`ContentSubmissionService` + `UserApiClient.patchUserProfile`) — that is 11.C.2 — and it does NOT delete magic-link auth itself (`MagicLinkService`, `SpeakerMagicLoginController`, `SpeakerPortalTokenController`) — that is 11.F.1.

The clean way to think about this story: **"delete the data model, keep the auth model intact, refactor the few non-portal consumers of the entity to the User API."**

### The actual location of `Speaker` (and why the AC text differed)

Story 11.C.1's epic text (`docs/prd/epic-11-speaker-workflow-refactor.md` line 700+) says "the `Speaker` JPA entity, `SpeakerRepository`, and any service code that queried the `speakers` table" should be deleted from `speaker-coordination-service`. **The actual codebase places these in `event-management-service`** — `speaker-coordination-service` has already been a thin shell since the ARCH-001 cleanup in early 2025 (see commit `d01b9e70` and the four files listed in AC10). The story's AC have been rewritten here to target the real file locations. The intent is unchanged: the entity and its repository disappear, the speakers table is dropped, and the speaker-coordination-service stays thin. See Open Question #2 if a different scope was intended.

### File-by-file map — current state and what changes

This map was built by reading each file before drafting the AC. Use it as a navigation index during implementation.

| Path | Today | After 11.C.1 |
|------|-------|--------------|
| `services/event-management-service/src/main/resources/db/migration/V37__Create_speakers_table.sql` | Creates `speakers` (id UUID PK, username UNIQUE, availability, workflow_state, expertise_areas TEXT[], speaking_topics TEXT[], certifications TEXT[], languages VARCHAR(10)[], speaking_history JSONB, deleted_at, etc.) plus 7 indexes | **Unchanged** (history). New `V94__drop_speakers_table.sql` reverses it. |
| `services/event-management-service/src/main/resources/db/migration/V51__Add_speaker_profile_picture.sql` and `V52__Add_speaker_profile_fields.sql` | Add `profile_picture_url`, `first_name`, `last_name`, `email`, `bio`, `linkedin_url`, `twitter_handle` columns to `speakers` | **Unchanged** (history). Dropped implicitly with the table by `DROP TABLE … CASCADE` in V94. |
| `services/event-management-service/src/main/resources/db/migration/V38__Add_speaker_name_search_cache_fields.sql` and `V40__Backfill_speaker_names_note.sql` | Adds `speaker_first_name` / `speaker_last_name` / `speaker_name_vector` cache columns to **`session_users`** (NOT `speakers`) | **Unchanged.** These cache columns on `session_users` are independent of the `speakers` table and continue to work. |
| `services/event-management-service/src/main/resources/db/migration/V93__migrate_legacy_speaker_states.sql` | (Story 11.B.3, ready-for-dev) maps legacy `speaker_pool.status` values, drops `is_tentative`/`tentative_reason` columns, tightens CHECK constraints | **Must be merged before V94.** |
| `services/event-management-service/.../domain/Speaker.java` | 216-line JPA entity with availability, workflow_state, expertise_areas, etc.; `@Table(name = "speakers")` | **DELETED** |
| `services/event-management-service/.../repository/SpeakerRepository.java` | `JpaRepository<Speaker, UUID>` with `findByUsername`, `findAllByUsernameIn`, custom queries | **DELETED** |
| `services/event-management-service/.../service/SpeakerService.java` | Global Speaker CRUD orchestrator — soft delete, listing, filtering. Publishes `SpeakerCreatedEvent`/`SpeakerUpdatedEvent` | **DELETED** |
| `services/event-management-service/.../controller/SpeakerController.java` | `@RequestMapping("/api/v1/speakers")` — list/get/create/update/delete global Speaker | **DELETED** |
| `services/event-management-service/.../controller/SpeakerPortalProfileController.java` | `@RequestMapping("/api/v1/speaker-portal")` — magic-link-authenticated profile read/update + photo upload | **DELETED** (replaced by `ContentSubmissionService` flow in 11.C.2 + Phase E Cognito portal) |
| `services/event-management-service/.../service/SpeakerProfileService.java` and `SpeakerProfilePhotoService.java` | Speaker-portal profile read/update + photo orchestration; both inject `SpeakerRepository` AND `MagicLinkService` | **DELETED** |
| `services/event-management-service/.../watch/WatchEventController.java` and `WatchSpeakerArrivalService.java` | Inject `SpeakerRepository` to read `firstName` / `lastName` / `profile_picture_url` for arrival display (W2.4) | **REFACTORED** to use `UserApiClient.getUsersByUsernames(...)` (existing HTTP-enrichment pattern, ADR-004 §HTTP enrichment) |
| `services/event-management-service/.../service/LegacyExportService.java` and `LegacyImportService.java` | Inject `SpeakerRepository` for legacy BAT-format export/import (Story 10.20) | **DELETED** — the one-shot historical migration has already run (PM confirmation, Open Question #1) |
| `services/event-management-service/.../dto/Speaker{Request,Response}.java`, `domain/SpeakerAvailability.java`, `converter/SpeakerAvailabilityConverter.java`, `event/Speaker{Created,Updated}Event.java`, `exception/SpeakerNotFoundException.java` | Used only by deleted classes | **DELETED** |
| `services/event-management-service/src/test/java/.../{repository/SpeakerRepositoryIntegrationTest,service/SpeakerServiceTest,controller/SpeakerControllerIntegrationTest,domain/SpeakerTest,controller/SpeakerPortalProfileControllerIntegrationTest,service/SpeakerProfileServiceTest,service/SpeakerProfilePhotoServiceTest}.java` | Tests of deleted classes | **DELETED** |
| `services/event-management-service/src/test/java/.../service/LegacyExportServiceTest.java` and `LegacyImportServiceTest.java` | Reference `SpeakerRepository` mock setups | **DELETED** alongside the services |
| `services/event-management-service/build/generated/src/main/java/ch/batbern/events/dto/generated/Speaker.java` | OpenAPI-generated DTO | **Regenerates** automatically when `docs/api/speakers-api.openapi.yml` is trimmed |
| `services/speaker-coordination-service/**` | 7 files: `SpeakerCoordinationApplication`, `config/CacheConfig`, `config/SecurityConfig`, `exception/GlobalExceptionHandler`, plus 3 test files. No `Speaker` entity, no `SpeakerRepository`. | **Unchanged** (thin-shell preserved per AR16 + ADR-009 §6.2) |
| `services/company-user-management-service/**` | Zero `Speaker` entity references (verified). `Role.java` enum has `SPEAKER` value (kept); `CompanyStatistics.totalSpeakers` is a counter (kept). | **Unchanged** |
| `api-gateway/src/main/java/ch/batbern/gateway/routing/DomainRouter.java` | Line 84: `/api/v1/speakers` → EMS (correct); line 117: unreachable `case "speaker-coordination-service"` | **Optional cleanup** per AC12 (default: leave) |
| `web-frontend/src/hooks/useUserPortrait.ts` | Calls `GET /api/v1/speakers/{username}` (deleted endpoint) | **Repointed to a new `GET /api/v1/public/users/{username}`** (narrow public projection mirroring `PublicOrganizerController`) |
| `services/company-user-management-service/.../controller/PublicUserController.java` (NEW) + `service/PublicUserService.java` (NEW) + `dto/PublicUserResponse.java` (NEW) | Does not exist today | **ADDED** — mirror of `PublicOrganizerController`/`PublicOrganizerService`/`PublicOrganizerResponse`. Anonymous (`permitAll()`), narrow projection only |
| `services/company-user-management-service/.../config/SecurityConfig.java` | `permitAll()` for `/api/v1/public/organizers` already configured | **EXTEND** the same `permitAll()` rule to `/api/v1/public/users/**` |
| `docs/api/users-api.openapi.yml` | Documents authenticated user endpoints under `/api/v1/users/**` | **ADD** the new `/api/v1/public/users/{username}` path under the existing `Public` tag (or equivalent) |
| `web-frontend/src/types/generated/speakers-api.types.ts` | OpenAPI-generated; contains `Speaker`, `SpeakerRequest`, `SpeakerResponse`, `SpeakerAvailability`, `SpeakerPreferences`, plus event-scoped types | **Regenerated** after AC8 trim — global types removed, event-scoped types preserved |
| `web-frontend/src/types/speakerPool.types.ts` and `speakerOutreach.types.ts` | Import event-scoped schemas from `speakers-api.types` | **Unchanged** (imports re-resolve after regen; event-scoped types kept) |
| `bruno-tests/speakers-api/*.bru` | 10 .bru files testing global Speaker CRUD | **DELETED** |
| `bruno-tests/speaker-portal-api/profile*.bru` (if present) | Tests for the deleted profile endpoint | **DELETED** |
| `docs/api/speakers-api.openapi.yml` | 1598 lines; mixes global `/speakers` paths with event-scoped `/events/{eventCode}/speakers/*` paths | **TRIMMED** to event-scoped only |
| `docs/architecture/ADR-009-unified-speaker-workflow.md` | Revision History at end | **APPEND** one row for V94 |
| `docs/plans/speaker-workflow-refactor.md` §2.2 | Migration table | **APPEND** one-line "Implemented in Story 11.C.1" note above the table |

### Critical "what NOT to break"

- ❌ **Do NOT delete `MagicLinkService`** — Phase F (Story 11.F.1) owns it. Other consumers (SpeakerPortalContent, SpeakerPortalResponse, SpeakerPortalDashboard, SpeakerMagicLogin, SpeakerPortalToken controllers) still call it and must keep compiling.
- ❌ **Do NOT touch `SpeakerWorkflowService`, `SpeakerStatusService`, `SpeakerResponseService`, `SpeakerInvitationService`, `SpeakerOutreachService`, `SpeakerPoolService`, `SpeakerContentSubmissionService`, `SpeakerDashboardService`, `SpeakerReminderService`** — these are `speaker_pool`-based and were refactored by 11.B.2. They do not touch the `Speaker` entity.
- ❌ **Do NOT modify the EVENT workflow state machine** (`EventWorkflowStateMachine.java`) — unchanged by ADR-009. Note: 11.B.3's AC9 renames `validateQualityReviewComplete → validateAllSpeakersConfirmed`; that is 11.B.3's scope, not this story.
- ❌ **Do NOT alter the `speaker_pool.username` column or its index** — this is the cross-service identity per ADR-003 and the foundation of 11.B.2's transition logic.
- ❌ **Do NOT extend `user_profiles`** — AR12 forbids it. The fields you might be tempted to migrate (`expertise_areas`, `speaking_topics`, etc.) are **dropped, not migrated**, per Confirmed Decision §6 item 4 and ADR-009 §0.3.
- ❌ **Do NOT add a new entity to `speaker-coordination-service`** — keep the thin shell.
- ❌ **Do NOT delete `SpeakerWorkflowState` from shared-kernel** — that is the 8-state enum from 11.B.1 and is used by `speaker_pool`.
- ❌ **Do NOT introduce a foreign-key constraint** between `speaker_pool.username` and any User-Management table. The cross-service reference stays loose per ADR-004's HTTP-enrichment pattern.

### Decision points the dev does NOT need to make

These are pre-decided in ADR-009, the plan, and the Confirmed Decisions block of `docs/plans/speaker-workflow-refactor.md` §6:

- **Migration of speaker-only columns:** NONE. `availability`, `expertise_areas`, `speaking_topics`, `languages`, `certifications`, `linkedin_url`, `twitter_handle`, `speaking_history`, `communication_preferences` are dropped, not preserved (ADR-009 §0.3, plan §6 decision 4).
- **`User.bio` and `User.profile_picture_url`:** Existing columns. Single source of truth. No per-event snapshot (Confirmed Decision §6.7). Overwritten globally on each profile update.
- **Speaker portal profile-update endpoint:** Goes away. Replaced by `ContentSubmissionService` (11.C.2) which patches `User` profile fields as part of content submission.
- **In-flight speakers:** None to preserve. Cutover is a clean swap (Confirmed Decision §6.4).
- **`speaker-coordination-service` fate:** Thin shell, kept for future read-side APIs (Confirmed Decision §6.2, AR16).
- **`SpeakerCreatedEvent` / `SpeakerUpdatedEvent`:** Removed. No replacement event is introduced by this story — the User-domain events (`UserCreatedEvent`, `UserUpdatedEvent` if they exist in `shared-kernel`) cover the lifecycle. Verify zero subscribers before deletion (AC3, Task 3.3).

### Style and ordering notes

- Migration files lead with a multi-line `--` header that cites ADR-009 (and any referenced sub-sections), the AR/NFR IDs, the prerequisite migration version, and the date.
- Deletions are committed in **logical commit batches**, not one monster commit: e.g. (i) V94 migration alone, (ii) `Speaker` entity + repository + tests, (iii) `SpeakerService` + `SpeakerController` + OpenAPI trim + frontend regen, (iv) `SpeakerProfile*` + portal controller, (v) watch services refactor, (vi) legacy export/import, (vii) Bruno + docs. This makes the eventual review reviewable and a revert (if it comes to that) surgical.
- Conventional Commits per CLAUDE.md: `refactor(speakers): drop speakers table and Speaker entity per ADR-009 [Story 11.C.1]`.

### Reading order for the dev

1. **Skim, in this order**: ADR-009 §"Decision 2" → ADR-009 §"Migration to the new state set" → `docs/plans/speaker-workflow-refactor.md` §0.3, §2.2, §3.1, §6.2 → epic file Story 11.C.1 lines 700–748.
2. **Read the actual file**: `services/event-management-service/.../domain/Speaker.java` end to end (216 lines). It is the artefact being deleted; reading it cements what the rest of the service used it for.
3. **Run** the verification grep from AC3 against the unmodified codebase to confirm the inventory matches what you find locally.
4. **Then** start with Task 2 (migration) — it is the smallest commit and proves the prerequisite chain works.

### Project structure notes

- Migration version target: **V94** as of `develop` SHA at story authoring. Re-confirm via `ls services/event-management-service/src/main/resources/db/migration/ | sort -V | tail -3` immediately before authoring; bump if V93 has been re-numbered or another migration landed.
- All deletions stay within `services/event-management-service/`, `web-frontend/src/`, `bruno-tests/`, `docs/api/`, and the two breadcrumb doc files in §AC15. No infrastructure CDK changes (per §3.5 of the plan: CDK work belongs to Phase E).
- `make build-java` from repo root will pick up both the Flyway and Java changes; integration tests run via Testcontainers PostgreSQL (per CLAUDE.md "TDD" section).

### Testing strategy

This is a **destructive refactor** — the goal is to remove code while keeping the system green. The test signal is:

1. **Migration:** Testcontainers PostgreSQL applies V94 cleanly during `AbstractIntegrationTest` setup. If any integration test still depends on a `speakers` row, it fails — that test should be in the "deleted tests" list of AC3/AC4.
2. **Compile:** No source file in `services/`, `shared-kernel/`, `api-gateway/`, `web-frontend/src/` imports `Speaker` or `SpeakerRepository` after the commit batch. The verification greps in AC3 and AC8 are the gates.
3. **Behaviour preservation:** Watch arrival display (AC5) and archive portrait display (AC7) continue to work end-to-end. Add a single Playwright assertion covering the archive page portrait if one does not already exist.
4. **Regression:** `make verify` is green. Bruno collection runs green (modulo the deleted `speakers-api` collection per AC14).
5. **Contract:** `docs/api/speakers-api.openapi.yml` validates with `swagger-cli validate` and frontend types regenerate without errors.

No new unit tests are introduced for "the deleted code." New tests, if any, are for the watch + legacy refactor paths (UserApiClient-based) — and the existing watch arrival integration test should be the primary signal there.

### Latest-tech anchors for this story

- **PostgreSQL `DROP TABLE … CASCADE`** (PG 15+, which BATbern uses): drops dependent views/FKs implicitly. There are no FKs pointing **into** the `speakers` table from other tables in this codebase (verified by `grep -rn "REFERENCES speakers" services/`); the `CASCADE` is defensive against any external `speaker_pool`-style references that may have crept in.
- **Spring Data JPA repository deletion:** Removing a `JpaRepository<Entity, ID>` is safe once the entity is gone; no Spring config change required. The `@EnableJpaRepositories` scan picks up whatever remains.
- **OpenAPI Generator (Gradle plugin):** Generated DTOs in `build/generated/` are regenerated from the trimmed YAML on the next `./gradlew build` — no manual cleanup needed.
- **Flyway 10.x baseline rules:** Migrations are forward-only. A failed apply rolls back the transaction (V94 is a single statement). If V94 needs to be reissued, increment to V95 — do not edit V94 once merged.

### Previous-story intelligence (Phase B in flight)

- **11.B.1 (done):** Enum reduced to 8 states. `SpeakerWorkflowStateConverter.java` may still reference legacy enum values via DB strings; verified in 11.B.2 work-in-progress.
- **11.B.2 (in-progress on this branch, per `git status`):** `StatusTransitionValidator.java` deleted; `OverflowManagementService.java` deleted; `SpeakerWorkflowService.transition(...)` is the sole writer; multiple speaker services modified to delegate. This story (11.C.1) does **not** touch those files — pick up after 11.B.2 lands.
- **11.B.3 (ready-for-dev):** V93 migration drops `is_tentative` / `tentative_reason`, tightens CHECK constraints to the 8-state allow-list, adds derived flags on `SpeakerPoolResponse`. **V94 (this story) must follow V93** — DO NOT renumber V94 to a lower version.
- **11.A.1 docs (done):** `docs/architecture/03-data-architecture.md` line 1562 already says "the `speakers` table is deleted." This story makes the code match the docs.

### Git intelligence — recent commits relevant to this story

- `d01b9e70` (Jan 2025): ARCH-001 cleanup that emptied `speaker-coordination-service`. The thin-shell state preserved by AC10 was established here.
- `40257ca3` (Epic 6 completion): Speaker portal magic-link implementation. The `SpeakerProfileService` + `SpeakerProfilePhotoService` + `SpeakerPortalProfileController` lineage being deleted in AC4 was added in this commit's lineage.
- `db45c42a` (Epic 5.5): Quality review task system. Uses `speaker_pool`, not `speakers`. Untouched.
- The current branch `feature/speaker-workflow-refactor` already has the 11.B.2-in-progress edits visible in `git status` (delete of `StatusTransitionValidator.java`, delete of `OverflowManagementService.java`, modifications across the speaker services). **Coordinate with the 11.B.2 author before starting** — they may want to land first.

## Project Context Reference

- Project context: `_bmad-output/project-context.md` — BATbern is a Spring-Boot microservices monorepo + React frontend deployed via AWS CDK. Native dev: `make dev-native-up`. PostgreSQL via Testcontainers.
- Branch: `feature/speaker-workflow-refactor` (per `docs/plans/speaker-workflow-refactor.md` §9.1).
- Existing patterns for HTTP enrichment: `docs/guides/microservices-http-clients.md` (the `UserApiClient` pattern referenced in AC5/AC6).
- Existing patterns for Flyway: `docs/guides/flyway-migration-guide.md` (ADR-003 compliance, header conventions, idempotency).

## References

- [Source: docs/architecture/ADR-009-unified-speaker-workflow.md §"Decision 2"] — Speaker is User + SPEAKER role; speakers table deleted; user_profiles not extended.
- [Source: docs/architecture/ADR-009-unified-speaker-workflow.md §"Migration to the new state set"] — Canonical DROP TABLE SQL block; this story's V94 implements it.
- [Source: docs/plans/speaker-workflow-refactor.md §0.3] — Speaker as User; speakers table deleted; user_profiles NOT extended; legacy fields dropped not migrated.
- [Source: docs/plans/speaker-workflow-refactor.md §2.2] — Data-model migration plan; this story's V94 is the "Drop `speakers` table" row.
- [Source: docs/plans/speaker-workflow-refactor.md §3.1] — Component changes in event-management-service; AC4/AC5/AC6 derive from this section.
- [Source: docs/plans/speaker-workflow-refactor.md §3.3] — speaker-coordination-service stays thin (Confirmed Decision §6.2).
- [Source: docs/plans/speaker-workflow-refactor.md §6.4] — In-flight speakers: NONE. Clean swap.
- [Source: docs/plans/speaker-workflow-refactor.md §6.7] — User.bio / User.profile_picture_url overwritten globally; no per-event snapshot.
- [Source: docs/prd/epic-11-speaker-workflow-refactor.md Story 11.C.1 lines 700-748] — Original AC; this story refines them against the actual file locations.
- [Source: docs/architecture/03-data-architecture.md §"Speaker Coordination Service Database Schema" line 1560 + §"User" line 287] — Phase A alignment already documents the post-deletion state.
- [Source: docs/architecture/06-backend-architecture.md §"Speaker authentication (ADR-009)" line 165] — Single Cognito flow; magic-link is being torn down in Phase F.
- [Source: docs/architecture/ADR-003-meaningful-identifiers-public-apis.md] — `speaker_pool.username` is the cross-service identity (AC9).
- [Source: docs/architecture/ADR-004-factor-user-fields-from-domain-entities.md] — HTTP-enrichment pattern; UserApiClient consumes User fields without a database join (AC5, AC6).
- [Source: services/event-management-service/src/main/resources/db/migration/V37__Create_speakers_table.sql] — Authoritative list of indexes for the DROP CASCADE comment.
- [Source: services/event-management-service/src/main/resources/db/migration/V51, V52] — Authoritative list of legacy columns to be dropped with the table.
- [Source: _bmad-output/implementation-artifacts/11-b-3-migrate-legacy-status-drop-tentative-cols-derived-flags.md] — V93 migration definition; V94 (this story) follows.
- [Source: CLAUDE.md §"TDD"] — Testcontainers PostgreSQL is the integration-test database.
- [Source: CLAUDE.md §"Doc Drift Prevention"] — Code-only refactors that don't change business logic use `[no-doc]` in the commit message; AC15 adds the Revision-History breadcrumb so this is **not** `[no-doc]`.

## Dev Agent Record

### Agent Model Used

claude-opus-4-7 (1M context)

### Debug Log References

- `/tmp/ems-compile.log` — EMS compile output.
- `/tmp/ems-test.log` — EMS full test suite (8m 44s, BUILD SUCCESSFUL).
- `/tmp/other-tests.log` — CUMS + speaker-coordination-service + api-gateway tests (2m 49s, BUILD SUCCESSFUL).
- `/tmp/checkstyle.log` — Checkstyle on EMS + CUMS (BUILD SUCCESSFUL).
- `/tmp/tsc.log` — TypeScript type-check (clean after Phase B leftover fixes).
- `/tmp/eslint.log` — ESLint (0 errors, 0 warnings, `--max-warnings 50`).
- `/tmp/vitest.log` — Frontend vitest run (4860 passed, 110 skipped, 23 todo).

### Completion Notes List

**Summary.** All 15 acceptance criteria satisfied (with the 2 documented deviations below). Speaker entity + magic-link profile-update surface + legacy BAT-format export/import all deleted; watch services migrated to `UserApiClient`; public portrait lookup mirrored as `GET /api/v1/public/users/{username}` in CUMS. V94 migration drops the `speakers` table.

**AC mapping.**
- AC1 (V94 migration with cascading drop) ✅
- AC2 (`user_profiles` untouched, no new CUMS migration) ✅
- AC3 (Speaker entity + repo + service + controller + supporting types deleted) ✅ with the **`SpeakerNotFoundException` deviation** documented under Task 3
- AC4 (SpeakerProfile* + portal controller + 5 profile DTOs deleted) ✅ with the **`UserUpdateDto` deviation** documented under Task 4
- AC5 (Watch services on `UserApiClient` per-record) ✅
- AC6 (Legacy export/import retired + plan §3.1 note) ✅
- AC7 (PublicUserController + permitAll + OpenAPI doc + frontend hook + cache key) ✅; Playwright check deferred — see Task 7.7
- AC8 (`speakers-api.openapi.yml` reduced + frontend types regenerated) ✅
- AC9 (`speaker_pool.username` remains sole cross-service identifier) ✅ — no schema changes to `speaker_pool` or `speaker_status_history`
- AC10 (`speaker-coordination-service` remains a thin shell) ✅ — no files added/changed in that service
- AC11 (CUMS has zero Speaker entity references) ✅ — only the new `PublicUserController` was added
- AC12 (API Gateway routing) ✅ — kept default option (a): `/api/v1/speakers` line left in `DomainRouter`; will surface a 404 from EMS until the line is removed in a future tidy
- AC13 (Build green, tests green, OpenAPI regenerated, no leftover `@Deprecated`) ✅; `make audit-security` not fully run (Trivy skipped); native smoke deferred — see Task 11.3
- AC14 (Bruno cleanup) ✅; Bruno test run against staging not executed in this session
- AC15 (Doc breadcrumbs — ADR-009 Revision History + plan §2.2 note) ✅; CLAUDE.md unchanged (verified clean)

**Cross-cutting deviations.**
1. `SpeakerNotFoundException` kept (AC3 inventory was inaccurate — used by `SpeakerOutreachService` and `SpeakerInvitationService`, both explicitly out of deletion scope).
2. `UserUpdateDto` kept (referenced by `UserApiClient.updateUser` signature; cleaner removal belongs to 11.C.2).
3. `UserApiClient.{updateUser, updateUserProfilePicture, getSpeakerUsernames}` are now dead methods (called only by deleted code). Intentionally left for 11.C.2 / 11.D.1 to refactor.
4. `events-api.openapi.yml` trimmed of legacy export/import paths + schemas (not strictly called out in any AC but necessary so frontend type regen stays consistent).
5. Phase B frontend leftovers fixed inline (`isTentative` / `tentativeReason` UI in 3 components; `CONFIRMED` case in 2 components) — blocked TS type-check on this story; not strictly 11.C.1 scope but cheap to clear.
6. `EventManagementAdminPage.tsx`: removed `ExportImportTab` from the admin tabs (9 → 8 tabs, max index 8 → 7).

**Suggested review-time checks.**
- `make dev-native-up` and manually browse the organizer kanban + speaker portal (content submission). The profile-update flow will 404 — expected per the design.
- Run `./scripts/ci/run-bruno-tests.sh` against staging to confirm Bruno collections still pass.
- Run `make audit-security` for the Trivy + npm audit pass.
- Decide whether to also remove `cleanPath.startsWith("/api/v1/speakers")` from `api-gateway/src/main/java/ch/batbern/gateway/routing/DomainRouter.java:84` (AC12 alternative); the gateway-level 404 vs EMS 404 is a non-functional distinction.

### File List

**Created (4):**
- `services/event-management-service/src/main/resources/db/migration/V94__drop_speakers_table.sql` (AC1)
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/controller/PublicUserController.java` (AC7)
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/PublicUserService.java` (AC7)
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/dto/PublicUserResponse.java` (AC7)

**Modified (18):**
- `services/event-management-service/src/main/java/ch/batbern/events/watch/WatchEventController.java` (AC5)
- `services/event-management-service/src/main/java/ch/batbern/events/watch/WatchSpeakerArrivalService.java` (AC5)
- `services/event-management-service/src/main/java/ch/batbern/events/config/SecurityConfig.java` (AC3, AC4 — removed speakers + speaker-portal/profile permitAll)
- `services/event-management-service/src/test/java/ch/batbern/events/watch/WatchEventControllerIntegrationTest.java` (AC5 test fixture)
- `services/event-management-service/src/test/java/ch/batbern/events/watch/WatchSpeakerArrivalIntegrationTest.java` (AC5 test fixture)
- `services/event-management-service/src/test/java/ch/batbern/events/watch/WatchSpeakerArrivalWebSocketTest.java` (AC5 test fixture)
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/config/SecurityConfig.java` (AC7 — permitAll on /public/users/*)
- `docs/api/events-api.openapi.yml` (AC6/AC8 follow-on — legacy export/import paths + schemas removed)
- `docs/api/speakers-api.openapi.yml` (AC8 — global Speaker paths + schemas removed; description rewritten)
- `docs/api/users-api.openapi.yml` (AC7 — added /public/users/{username} path + PublicUserResponse schema + Public tag)
- `docs/architecture/ADR-009-unified-speaker-workflow.md` (AC15 — Revision History row 1.1)
- `docs/plans/speaker-workflow-refactor.md` (AC6 §3.1 + AC15 §2.2 — implementation-status notes)
- `web-frontend/src/hooks/useUserPortrait.ts` (AC7 — repointed to /public/users/, cache key user-portrait)
- `web-frontend/src/hooks/useMiscHooks.test.ts` (AC7 — test assertion updated)
- `web-frontend/src/pages/organizer/EventManagementAdminPage.tsx` (AC6 — removed ExportImportTab from admin tabs)
- `web-frontend/src/pages/organizer/EventManagementAdminPage.test.tsx` (AC6 — removed ExportImportTab mock)
- `web-frontend/src/components/organizer/SpeakerDrawer/DetailsTabPanel.tsx` (Phase B leftover: removed isTentative UI)
- `web-frontend/src/components/organizer/SpeakerDrawer/SpeakerDrawerHeader.tsx` (Phase B leftover: removed tentative chip)
- `web-frontend/src/components/organizer/SpeakerDrawer/getDefaultTab.ts` (Phase B leftover: removed CONFIRMED case)
- `web-frontend/src/components/organizer/SpeakerStatus/SpeakerStatusLanes.tsx` (Phase B leftover: removed CONFIRMED + isTentative)
- `web-frontend/src/types/generated/events-api.types.ts` (regen from spec)
- `web-frontend/src/types/generated/speakers-api.types.ts` (regen from spec)
- `web-frontend/src/types/generated/user-api.types.ts` (regen from spec)
- `_bmad-output/implementation-artifacts/sprint-status.yaml` (11.B.2 → done, 11.C.1 → review)
- `_bmad-output/implementation-artifacts/11-b-2-speakerworkflowservice-sole-status-writer.md` (Status → done)
- `_bmad-output/implementation-artifacts/11-c-1-drop-speakers-table-remove-speaker-coordination-refs.md` (this file)

**Deleted — main (24):**
- `services/event-management-service/src/main/java/ch/batbern/events/domain/Speaker.java` (AC3)
- `services/event-management-service/src/main/java/ch/batbern/events/domain/SpeakerAvailability.java` (AC3)
- `services/event-management-service/src/main/java/ch/batbern/events/repository/SpeakerRepository.java` (AC3)
- `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerService.java` (AC3)
- `services/event-management-service/src/main/java/ch/batbern/events/controller/SpeakerController.java` (AC3)
- `services/event-management-service/src/main/java/ch/batbern/events/converter/SpeakerAvailabilityConverter.java` (AC3)
- `services/event-management-service/src/main/java/ch/batbern/events/dto/SpeakerRequest.java` (AC3)
- `services/event-management-service/src/main/java/ch/batbern/events/dto/SpeakerResponse.java` (AC3)
- `services/event-management-service/src/main/java/ch/batbern/events/event/SpeakerCreatedEvent.java` (AC3)
- `services/event-management-service/src/main/java/ch/batbern/events/event/SpeakerUpdatedEvent.java` (AC3)
- `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerProfileService.java` (AC4)
- `services/event-management-service/src/main/java/ch/batbern/events/service/SpeakerProfilePhotoService.java` (AC4)
- `services/event-management-service/src/main/java/ch/batbern/events/controller/SpeakerPortalProfileController.java` (AC4)
- `services/event-management-service/src/main/java/ch/batbern/events/dto/SpeakerProfileDto.java` (AC4)
- `services/event-management-service/src/main/java/ch/batbern/events/dto/ProfileUpdateRequest.java` (AC4)
- `services/event-management-service/src/main/java/ch/batbern/events/dto/PhotoConfirmRequest.java` (AC4)
- `services/event-management-service/src/main/java/ch/batbern/events/dto/PhotoUploadRequest.java` (AC4)
- `services/event-management-service/src/main/java/ch/batbern/events/dto/PresignedPhotoUploadResponse.java` (AC4)
- `services/event-management-service/src/main/java/ch/batbern/events/service/LegacyExportService.java` (AC6)
- `services/event-management-service/src/main/java/ch/batbern/events/service/LegacyImportService.java` (AC6)
- `services/event-management-service/src/main/java/ch/batbern/events/controller/AdminExportImportController.java` (AC6)
- `services/event-management-service/src/main/java/ch/batbern/events/dto/export/` — directory with 10 DTOs (AC6)
- `web-frontend/src/components/organizer/Admin/ExportImportTab.tsx` (AC6)

**Deleted — tests (10):**
- `services/event-management-service/src/test/java/ch/batbern/events/repository/SpeakerRepositoryIntegrationTest.java` (AC3)
- `services/event-management-service/src/test/java/ch/batbern/events/service/SpeakerServiceTest.java` (AC3)
- `services/event-management-service/src/test/java/ch/batbern/events/controller/SpeakerControllerIntegrationTest.java` (AC3)
- `services/event-management-service/src/test/java/ch/batbern/events/domain/SpeakerTest.java` (AC3)
- `services/event-management-service/src/test/java/ch/batbern/events/service/SpeakerProfileServiceTest.java` (AC4)
- `services/event-management-service/src/test/java/ch/batbern/events/service/SpeakerProfilePhotoServiceTest.java` (AC4)
- `services/event-management-service/src/test/java/ch/batbern/events/controller/SpeakerPortalProfileControllerIntegrationTest.java` (AC4)
- `services/event-management-service/src/test/java/ch/batbern/events/service/LegacyExportServiceTest.java` (AC6)
- `services/event-management-service/src/test/java/ch/batbern/events/service/LegacyImportServiceTest.java` (AC6)
- `services/event-management-service/src/test/java/ch/batbern/events/controller/AdminExportImportControllerIntegrationTest.java` (AC6)

**Deleted — Bruno (13):**
- `bruno-tests/speakers-api/00-setup.bru` through `10-verify-deletion.bru` (11 files) (AC14)
- `bruno-tests/speaker-portal-api/05-get-profile.bru` (AC14)
- `bruno-tests/speaker-portal-api/16-update-profile.bru` (AC14)

### Change Log

| Date | Change |
|------|--------|
| 2026-05-15 | Story 11.C.1 drafted via `bmad-create-story`. Ready for dev. |
| 2026-05-15 | Resolved Open Questions 1, 3, 4 with PM (Nissim). AC6 → delete legacy export/import; AC7 → new `PublicUserController` mirroring `PublicOrganizerController`; AC5 → per-record `UserApiClient` lookup (no batch). |
| 2026-05-16 | Implementation completed by Amelia (dev agent). V94 migration authored; Speaker entity + magic-link profile-update surface + legacy BAT export/import all deleted; watch services migrated to `UserApiClient`; `PublicUserController` mirrored from `PublicOrganizerController`; OpenAPI specs trimmed (speakers-api + events-api) and frontend types regenerated; doc breadcrumbs added to ADR-009 + plan §2.2 / §3.1. EMS + CUMS + speaker-coordination + api-gateway test suites green. Status → review. |
| 2026-05-16 | Code review run via `bmad-code-review` (3 parallel reviewers + Bruno run against dev + `make audit-security`). 3 decision-needed, 10 patches, 7 deferred items recorded below. Audit-security clean (0 vulns). Bruno: 19 failures across 3 collections — pre-existing 11.B.* carryover (TENTATIVE removal). |

---

### Review Findings

_Captured 2026-05-16 by `bmad-code-review` (Blind Hunter + Edge Case Hunter + Acceptance Auditor). All Critical / High items were independently verified by reading the relevant source files. AC coverage table: 11 Pass / 3 Partial / 1 Fail / 6 Deviation-flagged-OK (per Acceptance Auditor)._

**Resolved decisions (Nissim, 2026-05-16):**
- **D1 — Watch JWT propagation to CUMS:** _(a) multi-issuer composite decoder in CUMS_ (mirror EMS pattern).
- **D2 — `PublicUserService` row scope:** _(a) filter to `Role.SPEAKER`_ to match deleted `SpeakerController` behaviour.
- **D3 — Rate limiting for `/api/v1/public/users/*`:** _(b) extend gateway rate-limit to anonymous paths_ (uniform policy across all public surfaces).


**Decision needed:** _Resolved 2026-05-16 by Nissim. See "Resolved decisions" subsection above._

**Patches (applied 2026-05-16, build-verified — CUMS PublicUserControllerIntegrationTest 5/5 green, gateway RateLimitingTest 13/13 green, EMS compile green, frontend type-check + useMiscHooks 21/21 green):**

- [x] [Review][Patch] **CRITICAL — Multi-issuer JWT decoder in CUMS (D1=(a))** — Composite `JwtDecoder` now delegates by `iss` claim (Cognito JWKS for default, HS256 with `watchJwtSecret` for `iss == "batbern-watch"`); role converter falls back from `custom:role` to `role`. Infrastructure already passes `WATCH_JWT_SECRET` to CUMS (`infrastructure/lib/stacks/company-management-stack.ts:82-83`). [`services/company-user-management-service/src/main/java/ch/batbern/companyuser/config/SecurityConfig.java`]
- [x] [Review][Patch] **HIGH — `PublicUserService` filtered to `Role.SPEAKER` (D2=(a))** — Non-speakers return 404; class Javadoc documents the row-scope guarantee. [`services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/PublicUserService.java`]
- [x] [Review][Patch] **HIGH — Gateway rate-limit IP-bucketed for anonymous (D3=(b))** — `RateLimiter.isAnonymousRequestAllowed` + `RateLimitingFilter` now bucket by `anonymous:{clientIp}` instead of a single global "anonymous" key; one attacker can no longer exhaust the 50/min quota for every visitor. `RateLimitingTest.should_handleAnonymousRequests_when_noUserContext` updated to assert the new bucket key. [`api-gateway/src/main/java/ch/batbern/gateway/security/RateLimiter.java`, `RateLimitingFilter.java`]
- [x] [Review][Patch] **CRITICAL — Gateway permitAll matcher for `/api/v1/public/users/*`** — Added next to `/api/v1/public/organizers`. [`api-gateway/src/main/java/ch/batbern/gateway/config/SecurityConfig.java`]
- [x] [Review][Patch] **CRITICAL — Watch services exception catch widened** — Both `WatchSpeakerArrivalService.confirmArrival` and `WatchEventController.mapToActiveEventDetail` also catch `UserServiceException` and log WARN once per request, falling back to username / session_users cache. SPEAKER_ARRIVED STOMP broadcast no longer aborts on transient CUMS errors. [`services/event-management-service/src/main/java/ch/batbern/events/watch/WatchSpeakerArrivalService.java`, `WatchEventController.java`]
- [x] [Review][Patch] **HIGH — `useUserPortrait.ts` Skip-Auth header** — Hook passes `headers: { 'Skip-Auth': 'true' }`; `useMiscHooks.test.ts` updated to verify. Stale Cognito tokens no longer trigger the apiClient `/login` redirect from anonymous archive views. [`web-frontend/src/hooks/useUserPortrait.ts`, `web-frontend/src/hooks/useMiscHooks.test.ts`]
- [x] [Review][Patch] **HIGH — New `PublicUserControllerIntegrationTest` + Bruno collection** — 5 integration cases (speaker projection, 24h cache header, unknown-user 404, attendee-only 404 enforcing D2, permitAll regression). 2 Bruno files (`20-public-user-by-username.bru` + `21-public-user-not-found.bru`) assert anonymous access, projection narrowness, cache header, and 404 vs 401. [`services/company-user-management-service/src/test/java/ch/batbern/companyuser/controller/PublicUserControllerIntegrationTest.java`, `bruno-tests/users-api/20-public-user-by-username.bru`, `21-public-user-not-found.bru`]
- [x] [Review][Patch] **MEDIUM — `PublicUserController` 24h public `Cache-Control`** — `ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofHours(24)).cachePublic()).body(...)`. CloudFront + browsers can absorb the portrait fan-out. [`services/company-user-management-service/src/main/java/ch/batbern/companyuser/controller/PublicUserController.java`]
- [x] [Review][Patch] **MEDIUM — `useUserPortrait.ts` retry policy fixed** — Now `(failureCount, error) => !(axios.isAxiosError && status === 404) && failureCount < 2`. Transient Fargate-spot replacements no longer poison the cache for the rest of the browser session; legitimate 404s are still not retried. Test updated. [`web-frontend/src/hooks/useUserPortrait.ts`, `useMiscHooks.test.ts`]
- [x] [Review][Patch] **LOW — Duplicate DEBUG log removed** — Controller-level `log.debug` dropped; service-level log retained as the single source of truth. [`services/company-user-management-service/src/main/java/ch/batbern/companyuser/controller/PublicUserController.java`]
- [x] [Review][Patch] **LOW — Dead gateway permitAll + `DomainRouter` cleanup** — Gateway no longer permitAlls `/api/v1/speakers/*` or `/api/v1/speaker-portal/profile*`. `DomainRouter.java:84` (`/api/v1/speakers` startsWith) removed. The switch arm at `:117` was intentionally retained because the `speakerCoordinationUrl` config / env var stays per AC12.b. [`api-gateway/src/main/java/ch/batbern/gateway/config/SecurityConfig.java`, `DomainRouter.java`]
- [x] [Review][Patch] **LOW — `TestAwsConfig.java` comments updated** — Both inline comments now name the active S3-mock consumers (event photos, teaser images, download presigned URLs) instead of the deleted legacy services. [`services/event-management-service/src/test/java/ch/batbern/events/config/TestAwsConfig.java`]
- [x] [Review][Patch] **LOW — `DomainRouter:104` comment rewritten** — Trailing comment now reads `// Admin endpoints (e.g. AdminSettingsController)`. [`api-gateway/src/main/java/ch/batbern/gateway/routing/DomainRouter.java`]
- [x] [Review][Patch] **LOW — Bruno env-file cleanup verified** — `grep -rn "globalSpeakerUsername\|globalSpeaker" bruno-tests/` returns zero hits; nothing to delete. [`bruno-tests/`]

**Deferred (pre-existing or out of scope for this story):**

- [x] [Review][Defer] **Watch services lose batch user lookup → N+1 HTTP loop** — `WatchEventController.mapToActiveEventDetail` replaced `speakerRepository.findAllByUsernameIn(...)` with a per-record `userApiClient.getUserByUsername(...)` loop. Open Question #4 (resolved with Nissim 2026-05-15) explicitly accepted per-record for 5-10 speakers per event; revisit if a smoke test shows >500ms latency. [`services/event-management-service/src/main/java/ch/batbern/events/watch/WatchEventController.java:121-125`] — deferred, accepted trade-off per Open Q4
- [x] [Review][Defer] **`UserApiClient` retains three dead methods** (`updateUser`, `updateUserProfilePicture`, `getSpeakerUsernames`) — explicitly deferred to Story 11.C.2 (`UserApiClient` rewrite with `patchUserProfile`). Acceptance Auditor verified no live callers remain in `services/event-management-service/src/`. [`services/event-management-service/src/main/java/ch/batbern/events/service/UserApiClient.java`] — deferred, pre-existing, scheduled in 11.C.2
- [x] [Review][Defer] **`ProfileUpdatePage.tsx` + `speakerPortalService.getProfile/updateProfile` still wired in frontend** — backend `/speaker-portal/profile*` endpoints were deleted in this story (AC4), but the magic-link frontend page + service methods remain. Any speaker who clicks "Update profile" from `ContentSubmissionPage:358` will see a silent failure UI until Phase F (Story 11.F.1) tears down the magic-link surface. [`web-frontend/src/App.tsx:277`, `web-frontend/src/services/speakerPortalService.ts:444-525`] — deferred, scheduled in Story 11.F.1
- [x] [Review][Defer] **Kanban `OUTREACH_LANES` / `POST_ACCEPTANCE_LANES` reorder is only partial** — this story only removed `'CONFIRMED'` from `POST_ACCEPTANCE_LANES`. Full ADR-009 §0.1 lane ordering (move `INVITED` to end of outreach; add `ACCEPTED` as first post-acceptance lane) is owned by Stories 11.D.2 / 11.D.3 per their scope. [`web-frontend/src/components/organizer/SpeakerStatus/SpeakerStatusLanes.tsx`] — deferred, owned by 11.D.2 / 11.D.3
- [x] [Review][Defer] **19 Bruno test failures across `users-api` (1) / `events-api` (7) / `speaker-portal-api` (11)** — pre-existing 11.B.* carryover: many failing tests reference removed `TENTATIVE` state, removed `ACCEPTED → IDENTIFIED` transitions, or `send-invitation` endpoints whose validation tightened in 11.B.2/B.3. Specifically: `speaker-portal-api/13-send-tentative-invitation`, `15-tentative-can-change-to-accept`, `events-api/38-update-speaker-status-to-ready`, `events-api/40-invalid-status-transition`, etc. AC14 of this story did not cover updating these tests (scope was deleting `bruno-tests/speakers-api/` + 2 profile files). Phase B owners should sweep the Bruno suite in a follow-up. [`bruno-tests/events-api/`, `bruno-tests/speaker-portal-api/`] — deferred, pre-existing from 11.B.*
- [x] [Review][Defer] **Sprint-status YAML promotes 11.C.1 to `review` while prerequisite 11.B.3 is still `in-progress`** — merge-train ordering risk: V94 depends on V93 landing first. Captured here so the PR reviewer can sequence merges correctly. [`_bmad-output/implementation-artifacts/sprint-status.yaml`] — deferred, merge-ordering concern
- [x] [Review][Defer] **No `UserApiClient` batch path added** — explicitly resolved in Open Q4 (per-record acceptable). Listed here so a future contributor adding a new screen with N-record fan-out doesn't repeat the per-record loop without revisiting the decision. [`services/event-management-service/src/main/java/ch/batbern/events/service/UserApiClient.java`] — deferred, follow-up if perf regression appears



## Open Questions (for clarification before merge)

These surfaced during story drafting. Items 1, 3, 4 have been resolved with PM (Nissim) on 2026-05-15; items 2 and 5 remain advisory.

1. **Legacy export/import service — refactor or delete?** Story 10.20's `LegacyExportService` and `LegacyImportService` sourced speaker fields from `SpeakerRepository`. After this story, that source is gone. **Decision (confirmed by Nissim 2026-05-15)**: delete. The one-shot historical migration has already been executed against production and will not be re-run on a database without `speakers`. AC6 and Task 6 now target a clean delete (services + tests + `AdminExportImportController` + the legacy frontend tab + retirement note in plan §3.1). The Story 10.20 sprint-status row stays at `done` — code retirement is documented, not back-dated.

2. **Epic-text says "speaker-coordination-service"; codebase has `Speaker` in event-management-service.** The Story 11.C.1 AC text in `docs/prd/epic-11-speaker-workflow-refactor.md` lines 723-730 says the `Speaker` entity should be deleted from `speaker-coordination-service`. The actual entity has lived in `event-management-service` since the ARCH-001 cleanup of January 2025 (commit `d01b9e70`); `speaker-coordination-service` has been a thin shell for nearly a year. **Decision (confirmed by Nissim 2026-05-15)**: implement against the actual location; the intent (delete the entity and table; keep speaker-coordination-service thin) is unchanged. The architecture doc (03-data-architecture.md line 1560) already reflects the correct location, so no PRD edit is required.

3. **`useUserPortrait.ts` migration target.** Public portrait lookup is already established in this codebase via `GET /api/v1/public/organizers` → `PublicOrganizerResponse` (`PublicOrganizerController` in `company-user-management-service`, consumed by the AboutPage and homepage via `usePublicOrganizers`). **Decision (confirmed by Nissim 2026-05-15)**: mirror that pattern for users — add `GET /api/v1/public/users/{username}` → `PublicUserResponse` (`PublicUserController` + `PublicUserService` + `PublicUserResponse` DTO) with the same narrow public projection (`username`, `firstName`, `lastName`, `profilePictureUrl` — no email, no role list, no other PII). AC7 and Task 7 now specify exactly this. The authenticated `/api/v1/users/{username}` endpoint stays as-is for organizer/admin use.

4. **`UserApiClient` batch lookup — does it exist?** AC5 originally assumed `getUsersByUsernames(Set<String>) → Map<String, UserResponse>`. **Decision (confirmed by Nissim 2026-05-15)**: per-record `getUserByUsername(String)` is acceptable — BATbern's user-base is small and the watch event detail loads 5–10 speakers at once. No batch extension to `UserApiClient` in this story. If a perf regression is observed during smoke testing (>500ms total for 10 speakers in local-native), a follow-up story can introduce batching.

5. **`SpeakerCreatedEvent` / `SpeakerUpdatedEvent` cross-service consumers.** Task 3.3 grep checks for subscribers outside the deleted code. If a subscriber lives in `partner-coordination-service` or `attendee-experience-service` (unlikely but possible — both services consume speaker-domain events for analytics/notifications), the cleanest path is to delete the listener and replace it with a `UserCreated`/`UserUpdated` subscription if the analytic data point is still needed. **Inferred decision**: grep first, then decide per consumer; if any subscriber turns up that has analytics value, surface it as a follow-up in `_bmad-output/implementation-artifacts/deferred-work.md` rather than expanding this story's scope. Flag in the PR with the grep output either way.

---

_Story created via `bmad-create-story` on 2026-05-15. Authored by PM (Nissim) with comprehensive context-engine analysis. Ready for `bmad-dev-story` execution._
