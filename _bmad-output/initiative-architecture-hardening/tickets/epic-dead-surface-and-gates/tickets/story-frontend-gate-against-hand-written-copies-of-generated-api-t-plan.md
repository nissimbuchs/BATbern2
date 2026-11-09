---
title: 'Frontend gate against hand-written copies of generated API types (17.3c)'
type: 'feature'
ticket: '3c'
created: '2026-11-09'
status: 'built'
baseline_revision: '67c384470d9e93f4483527071b42e5993f4fd896'
route: 'full'
route_source: 'auto'
risk: 'medium'
review: 'quick'
review_source: 'pinned'
lenses_ran: ['quick']
review_loop_iteration: 0
followup_review_recommended: false
context:
  - '/tmp/claude-1000/-home-nissim-dev-bat-BATbern-bmad-v7/4631b2e5-8c64-4b93-94d3-3fc01dbf8c22/scratchpad/overnight-brief.md'
warnings: ['oversized']
deferred: []
---

<intent-contract>

## Intent

**Problem:** `web-frontend/src` holds hand-written `export interface`s named like schemas in `src/types/generated/` (ADR-006 forbids the copies; 72 interfaces in 21 files on 2026-11-09, 75 in 20 on 2026-11-08). They drift silently from the spec: the regenerated types (null-vs-optional option A) already differ from many of them, and nothing stops a new one.

**Approach:** A new check `check:api-type-copies` in the `frontend-static` job (feeds the required `build-frontend`) fails a new `export interface` or object-literal `export type` whose name equals a generated schema; aliases pass; a committed baseline that only shrinks (NEW and STALE fail) with an owner file in which every line is owned. In the same story, replace the true copies with aliases, rename or list the name matches that are a different shape, and freeze only what a later story owns (16.8c newsletter, 17.10 user and speaker paths). *(Ticket 3c, owner 2026-11-08; C15 leave it cleaner.)*

## Boundaries & Constraints

**Always:** the check follows `scripts/api-lint.ts` (baseline + owner file, `--update`, NEW / STALE / UNOWNED / AMBIGUOUS / STALE OWNER / BAD OWNER) and `scripts/check-api-paths.ts` (Node 22 type stripping, plain TS, same `EXCLUDED` file filter, exported pure functions for tests); key a baseline line by `file TAB name`, never by line number; when an alias widens a type (field now optional or `| null`), fix every caller properly (a real default, a guard, or a visible empty state), never `!` or `as` to silence it; a wire defect the alias exposes (the UI reads a field the server never sends) is fixed in production code test first, own commit (overnight brief, "Goal of 17.0"); behaviour of every screen stays as it is otherwise.

**Never:** no OpenAPI or backend change (an apparent spec defect becomes an Implementation Note, not a spec edit); no edit of `src/types/generated/`; no `exception:` owner without a concrete reason; no widening of the gate beyond name matches (ticket Notes); no push, PR, staging or production.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| new copy | `export interface EventResponse { ... }` in a new file, `EventResponse` is a generated schema | `NEW  src/x.ts  EventResponse` | exit 1 |
| object-literal type | `export type EventResponse = { ... }` (also generic `<T>`) | NEW | exit 1 |
| alias | `export type EventResponse = components['schemas']['EventResponse']` (or a union / intersection / `Omit<...>` not starting with `{`) | passes | exit 0 |
| other name | `export interface EventCardProps {}` | passes | exit 0 |
| fixed copy | baseline line whose declaration is gone or now an alias | `STALE` | exit 1, hint `--update` |
| unowned / ambiguous | baseline line matched by 0 or 2+ owner groups | `UNOWNED` / `AMBIGUOUS` | exit 1 |
| stale owner | owner group matching no baseline line | `STALE OWNER` | exit 1 |
| non-exported / comment | `interface X {}` not exported, or inside a comment | not counted | exit 0 |

</intent-contract>

## Code Map

- `web-frontend/scripts/api-lint.ts` -- model for baseline + owner file: `parseOwners`, `checkOwners`, `OWNER` regex (story id / `#issue` / `exception: <reason>`), `run(argv, paths)` returning the exit code, header text of the generated baseline. Reuse the shape, not the Spectral parts.
- `web-frontend/scripts/check-api-paths.ts:430-459` -- `EXCLUDED` + `listSourceFiles(root, dir)`; import and reuse them (export `EXCLUDED` if needed), and `scripts/lib/mask-comments.ts` `maskComments` so commented declarations do not count. Also exclude `src/types/generated/`.
- `web-frontend/scripts/check-api-paths.test.ts`, `scripts/api-lint.test.ts` -- test style; fixtures under `scripts/__fixtures__/<name>/` (add to `.prettierignore` like `i18n-keys` if layout matters).
- Schema names: in each `src/types/generated/*.types.ts`, the members of `interface components { schemas: { ... } }` (363 names on 2026-11-09). Parse with a small text scan of that block or the `typescript` package (already a devDependency; `scripts/check-i18n-keys.ts` shows what imports Node type stripping allows).
- `web-frontend/package.json` -- add `check:api-type-copies` (same Node-version guard as `check:api-paths`), add the two new files to `lint` and `lint:ci`; `tsconfig.scripts.json` include `scripts/check-api-type-copies*.ts`.
- `.github/workflows/build.yml:552-561` (`frontend-static`) -- add "Generated type copy check tests" (`npx vitest run scripts/check-api-type-copies.test.ts`) and "Generated type copy check" (`npm run check:api-type-copies`) after the API path check, with a comment like the neighbours. `build-frontend` already needs `frontend-static`.
- `Makefile:506-516` -- target `check-api-type-copies`, add it to `.PHONY` (line 14).
- Docs (same commit as the gate): `docs/architecture/coding-standards.md` new `#### Frontend: generated API type copies` beside "Frontend: i18n keys" (~line 687); `docs/deployment/cicd-pipeline-guide.md:80-90` frontend-static list; `_bmad-output/project-context.md:55` names the gate.
- Copies on 2026-11-09 (name match, object shape), field-level diff measured with a TS-AST script; "same fields" = same property set, differing only in optional / `| null` / enum written inline vs `$ref`:
  - same fields, replace with alias: `components/BlobTopicSelector/types.ts` (PastEventEntry, BacklogItem, TopicSimilarityResponse); `services/api/partnerAnalyticsApi.ts` AttendanceSummaryRecord; `services/api/partnerMeetingsApi.ts` RsvpDTO, RsvpSummary, MeetingRsvpListResponse; `services/api/partnerTopicsApi.ts` TopicDTO, TopicSuggestionRequest, TopicStatusUpdateRequest; `services/api/userAccountApi.ts` + `types/userAccount.types.ts` AdditionalEmail (two copies: one alias, the other re-exports it); `services/deregistrationService.ts` (3); `services/registrantNoticeService.ts` (2); `services/speakerContentService.ts` SubmitContentRequest, ReviewRequest; `services/speakerPortalService.ts` SpeakerContentInfo, ContentSubmitRequest, ContentSubmitResponse, MaterialUploadRequest; `services/venueCoordinationService.ts` (4; `VenueRecipientRole` vs generated `VenueRole`, `locale` hand `'de'|'en'` vs `string`); `types/notification.ts` NotificationCountResponse, MarkAsReadResponse, DeleteNotificationResponse; `types/eventParticipant.types.ts` PaginationMetadata; `types/sessionImport.types.ts` (3); `types/speakerPool.types.ts` AddSpeakerToPoolRequest, PatchSpeakerPoolRequest, PromoteSpeakerRequest, SendInvitationRequest, SendReminderRequest; `types/userAccount.types.ts` LogoUploadRequest; `types/watch.ts` (4); `types/event.types.ts` SessionTimingRequest, BulkTimingRequest, BulkTimingResponse, TimingConflictError, ConflictAnalysisResponse (inline structures: compare the referenced schemas before aliasing); `newsletterService.ts` same-field ones (Unsubscribe, AddressSubscription, MySubscriptions, Preview, SubscriberCount, SubscribeRequest, SubscriptionStatusResponse, SendStatusResponse).
  - different shape (field set differs or enum values differ): `types/api.ts` PaginationMetadata (`total`/`hasPrevious` vs `totalItems`/`hasPrev`), `types/auth.ts` UserPreferences, `types/userAccount.types.ts` UserPreferences + UserSettings (upper-case enums vs lower-case wire), `types/event.types.ts` Topic, `types/speakerImport.types.ts` LegacySpeaker, `types/speakerPool.types.ts` SendInvitationResponse + SendReminderResponse, `types/notification.ts` NotificationsResponse (`unreadCount`), `speakerContentService.ts` SpeakerContentResponse, `speakerPortalService.ts` SpeakerResponsePreferences, SpeakerResponseRequest, SpeakerResponseResult (`message` vs wire `errorMessage`), `newsletterService.ts` NewsletterSendRequest, NewsletterSendResponse.
- `_bmad-output/initiative-architecture-hardening/null-vs-optional-options.md`, `deferred-work.md` last item -- context on the `| null` regeneration and the "never filled" fields (e.g. `SpeakerResponseResult.errorMessage`).

## Tasks & Acceptance

**Execution:**
- [x] `web-frontend/scripts/check-api-type-copies.test.ts` + `scripts/__fixtures__/api-type-copies/` -- (written after the script, not RED first; see Review Triage Log) RED first: tests for schema-name extraction, NEW interface, NEW object-literal type (also generic), alias passes, non-exported and commented pass, STALE, owners (UNOWNED, AMBIGUOUS, STALE OWNER, BAD OWNER), `--update` writes a sorted baseline -- the gate's rules are proven in CI before it enforces.
- [x] `web-frontend/scripts/check-api-type-copies.ts` -- the check (header comment like `api-lint.ts`: purpose, modes, failure kinds, policy pointer); `run(argv, paths)` exported, `main()` guarded like `check-api-paths.ts`.
- [x] `web-frontend/package.json`, `tsconfig.scripts.json`, `Makefile`, `.github/workflows/build.yml` -- wire the script as in the Code Map.
- [x] Measure the start count with the script against an empty baseline (record in Implementation Notes: interfaces, object types, files).
- [x] Replace each "same fields" copy with `export type X = components['schemas']['X']` (keep the export name so imports are unchanged); run `npm run type-check` and fix each caller the widening breaks properly; where a sibling hand type (e.g. `VenueRecipientRole`) is then a duplicate of a generated enum, alias it too.
- [x] For each "different shape" match decide and record: (a) a frontend model that is not the wire shape -> rename (e.g. `PageInfo`, `UserPreferencesView`) and update imports; (b) meant as the wire shape but wrong -> if the UI reads a field the server never sends or sends differently, that is a defect: test first, fix in production code, own commit, then alias; (c) owned by a later story (16.8c deletes the newsletter send/preview chain; 17.10 merges the user profile / preferences / settings and speaker paths) -> stays on the baseline with that owner. Anything else gets `exception: <reason>`.
- [x] `web-frontend/scripts/api-type-copies-baseline.txt` (generated by `--update`) + `scripts/api-type-copies-owners.txt` (hand-written, header like `api-lint-owners.txt`) -- every line owned; end count lower than start.
- [x] Docs listed in the Code Map, same commit as the gate.
- [x] `make check-test-ratchet` -- no RAISED; on STALE run `npm run check:test-ratchet -- --update` and commit.

**Acceptance Criteria:**
- Given the branch, when `npm run check:api-type-copies` runs, then it exits 0 and prints the baseline size; every baseline line matches exactly one owner group.
- Given a temporary `export interface EventResponse { x: string }` in `src/`, when the check runs, then it fails with NEW naming file and type; given the same name as `components['schemas']['EventResponse']` alias, then it passes; given a baselined declaration removed, then it fails STALE (shown by the unit tests on fixtures and once by hand on the real tree, file reverted).
- Given the end state, when counted, then the baseline is smaller than the start count, and start / end / per-owner counts are in Implementation Notes.
- Given the touched files, when `npm run type-check`, `npm run lint:ci`, `npm run format:check` and the full Vitest suite run, then they pass.

## Design Notes

Architecture note: modules: `web-frontend` scripts, `services/`, `types/`, one component types file; CI `frontend-static`; docs · new dependencies: none · endpoints: none · reused: `listSourceFiles`/`EXCLUDED` and `maskComments` from the path check, the baseline/owner pattern of `api-lint.ts` · deviations: none (module-map: types come from `src/types/generated/`, services own the wire shapes).

Baseline line: `src/services/newsletterService.ts<TAB>NewsletterSendResponse`. Owner line: `<group id> TAB <owner> TAB <file regex> TAB <name regex>`, owner `16.8c` / `17.10` / `#1234` / `exception: <reason>`.

Object-literal test: after `=` (and after generic params), the first non-space character is `{`. `export type X = Omit<A, 'b'> & { c: string }` is not object-literal (starts with `Omit`) and passes: the gate is name-based by ticket scope.

## Verification

Rack rules (brief): one heavy job at a time, Vitest `--maxWorkers=4`, logs `~/.cache/batbern-test-logs/1-3c-*.log`, `set -o pipefail`, check exit codes.

**Commands:**
- `cd web-frontend && npx vitest run scripts/check-api-type-copies.test.ts scripts/check-api-paths.test.ts` -- expected: pass.
- `cd web-frontend && npm run check:api-type-copies` -- expected: exit 0, baseline smaller than start.
- `cd web-frontend && npm run check:api-paths` -- expected: exit 0.
- `cd web-frontend && npm run type-check && npm run lint:ci && npm run format:check` -- expected: exit 0.
- `cd web-frontend && npx vitest run --maxWorkers=4` -- expected: no failures.
- `make check-test-ratchet` -- expected: exit 0, no RAISED.

## Implementation Notes

- **Start count** (script against an empty baseline, 2026-11-09, baseline revision 67c38447): 72 copies, 72 interfaces, 0 object-literal types, in 21 files; 363 schema names. Matches the Code Map.
- **End count:** baseline 2 lines, 1 file, both owner `16.8c` (`src/services/newsletterService.ts` NewsletterSendRequest, NewsletterSendResponse). Per owner: 16.8c 2, 17.10 0, exception 0.
- **Aliased (same fields, keep export name):** BlobTopicSelector (3), partnerAnalyticsApi (1), partnerMeetingsApi (3), partnerTopicsApi (3), deregistrationService (3), registrantNoticeService (2), speakerContentService (3, incl. SpeakerContentResponse: the copy had `poolId`, the wire has `speakerPoolId`; no screen reads either), speakerPortalService (SpeakerContentInfo, ContentSubmitRequest/Response, SpeakerResponsePreferences/Request/Result, plus SpeakerResponseType to the generated enum), venueCoordinationService (4, plus VenueRecipientRole = VenueRole, the second copy in useVenueCoordinationConfig now re-exports it), notification (3), eventParticipant PaginationMetadata, sessionImport (3), speakerPool (7, incl. SendInvitation/ReminderResponse: no screen reads their fields), userAccount AdditionalEmail (userAccountApi re-exports it), watch (4), event.types timing (5), newsletterService (8; NewsletterSubscribeRequest as `Omit<..., 'language'> & Partial<Pick<..., 'language'>>`, see below).
- **Renamed (frontend model, not the wire shape):** `types/auth.ts` UserPreferences -> `UserContextPreferences`; `types/userAccount.types.ts` UserPreferences/UserSettings -> `UserPreferencesView`/`UserSettingsView` (upper-case enums, nested channels, mapped in userAccountApi; 17.10 still merges these paths); `types/event.types.ts` Topic -> `TopicListItem`; `types/speakerImport.types.ts` LegacySpeaker -> `LegacySpeakerRecord` (the speakers.json file format); `types/notification.ts` NotificationsResponse -> `NotificationPage` (the enriched list the UI holds).
- **Removed:** `src/types/api.ts` (PaginationMetadata with `total`/`hasPrevious`): nothing imported the file (grep of src, e2e, scripts). `userAccount.types.ts` LogoUploadRequest: the two profile-picture calls now take the generated `ProfilePictureUploadRequest` (user-api), the schema of the endpoint they call. `speakerPortalService` MaterialUploadRequest: replaced by the generated `SpeakerMaterialUploadRequest`, the schema of `/speaker-portal/.../materials/presigned-url`.
- **Production defect found by the alias (own commit 54e1cd25, test first, RED seen):** the agenda-full 409 of the slot endpoint is EMS's generic error body (AgendaFullException, no `conflicts`); `ConflictDetectionAlert` read `conflict.conflicts[0].type` and crashed the render. It now treats a missing list as empty and shows the message.
- **UI reading fields the server never sends (dead branches removed, behaviour unchanged):** ConflictDetectionAlert `details` and `conflictingTimeRange` (EMS sends `type` + `conflictingSessionSlug` only; the timeline never rendered), InvitationResponsePage `responseResult.message` (the wire has `errorMessage`, never set; listed in deferred-work as never filled). Their tests had asserted on the invented fields; fixtures now carry what EMS sends. The i18n keys `slotAssignment.conflicts.timeRange/start/end` were then unused and are removed from all 10 `events.json` (review patch; no other use found by grep of src and e2e, also no dynamic `conflicts.${...}` key).
- **Callers fixed for the widening:** RSVP panel and summary (missing counts 0, no chip without status, no date without `respondedAt`), PartnerAttendanceDashboard (maps the all-optional wire record to its `SummaryRow`, skips a row without event code or date), topic lists (createdAt null shows `–`, sorts as empty), Venue composer (`sentTo`/`ccEmails` absent = none), Watch pairing (no countdown without `expiresAt`), ContentSubmissionPage (`?? null`), notification count (`?? 0`). No `!`, no new `as`; one existing `as TimingConflictError` dropped.
- **Spec observation (not changed, no spec edit in this story):** `NewsletterSubscribeRequest.language` has `default: de`, which openapi-typescript turns into a required property; the public widget sends `{ email }` and the server applies the default. The alias makes `language` optional again with Omit/Pick and says why. Same generator behaviour may affect other request schemas with defaults.
- **AC wording:** `EventResponse` is not a generated schema name (events-core-api has `Event`), so the by-hand check used `SessionResponse`: NEW with file:line and name, exit 1; as an alias exit 0; a baselined declaration made non-exported -> STALE, exit 1; file reverted.
- `EXCLUDED` + `listSourceFiles` moved to `scripts/lib/source-files.ts` (re-exported from check-api-paths.ts, its tests unchanged).
- **Review patches (2026-11-09):** test fixtures typed with the aliased wire types now carry the real shapes (SendInvitationResponse with `speakerPoolId`/`status`/`responseDeadline`, no `token`/`workflowState`; TimingConflictItem only `type` + `conflictingSessionSlug`; SpeakerContentResponse `speakerPoolId`; no `username` in SubmitContentRequest; TopicDTO `source`; AdditionalEmail `receiveEventMail`; PromoteSpeakerRequest names; upper-case SpeakerWorkflowState; Session `language` and SessionSpeaker names; newsletter mutation vars `{ request }`). 23 test files added to `tsconfig.test-harness.json`, so `npm run type-check` now covers them. Not included: tests whose errors are unrelated to the aliased names (Event/Session fixtures in EventManagement, public, DragDrop, UnassignedSpeakersList; PartnerMeetingDTO), left as they were.
- Scanner masks string and template literal contents (`maskComments(src, { strings: true })`; default unchanged, so check-api-paths, check-i18n-keys and test-ratchet behave as before, their tests pass). Fixture now has a multi-line template with schema `TemplateOne`, which is reported without masking.
- `--update` refuses (exit 1, baseline untouched) to write a copy the committed baseline does not list; `--update --allow-growth` for an owner-accepted exception. Docs, CI comment updated; Makefile help already said "may only shrink".
- `speakerPortalService` MaterialUploadResponse, MaterialConfirmRequest, MaterialConfirmResponse are aliases of the generated SpeakerMaterialUpload/Confirm schemas (the two confirm types were the same class of copy under another name). Callers: `uploadMaterial` fails before any S3 PUT without `uploadUrl`/`uploadId`, sends no headers when `requiredHeaders` is absent; PresentationUpload reports UPLOAD_FAILED without `cloudFrontUrl` and falls back to the picked file's name. Tests first, RED seen for both.

**Verification (2026-11-09, logs `~/.cache/batbern-test-logs/1-3c-patch-*.log`):**

| Command | Result |
|---|---|
| `npx vitest run --maxWorkers=4` (full) | exit 0: 466 files, 6053 passed, 36 skipped, 20 todo |
| `npm run type-check` | exit 0 |
| `npm run lint:ci` | exit 0 |
| `npm run format:check` | exit 0 |
| `npm run check:api-type-copies` | exit 0, 2 copies, baseline 2 |
| `npm run check:api-paths` | exit 0, 298 calls match the spec |
| `npm run check:i18n-keys` | exit 0 |
| `make check-test-ratchet` | exit 0, no RAISED, no STALE |

- TDD order: the gate script was written before its test file (the test file then ran green except one assertion on a wrong schema name, fixed). The defect fix followed red-green.

## Plan Change Log

## Review Triage Log

| Finding | Severity | Decision | Resolution |
|---|---|---|---|
| Test fixtures typed with aliased wire types carry invented fields, invisible because tsconfig excludes tests | medium | patch | Fixtures fixed to the real shapes; 23 test files added to `tsconfig.test-harness.json` |
| Scanner does not mask strings and templates (`mask-comments.ts`), string test passed for the wrong reason | medium | patch | `maskComments` option `strings`, used by the scanner; fixture with a multi-line template naming a real schema |
| `--update` lets the baseline grow | medium | patch | Refused without `--allow-growth`, tested; docs and CI comment updated |
| `speakerPortalService` MaterialUploadResponse duplicates SpeakerMaterialUploadResponse | medium | patch | Aliased (plus the two confirm types); callers guard the now-optional fields, tests first |
| Unused i18n keys `slotAssignment.conflicts.timeRange/start/end` | low | patch | Removed from all 10 `events.json` (with the dead-branch commit) |
| Gate written before its tests | low | rejected | Tests exist and a hand probe proved NEW/STALE/alias; cannot be fixed retroactively |
| Dead-branch removals mixed into the story diff | low | patch | Committed separately (`refactor(frontend): drop UI for fields the server never sends`) |
| Plan checkboxes not ticked | low | patch | Ticked |
| Verification not recorded | low | patch | Recorded in Implementation Notes |
