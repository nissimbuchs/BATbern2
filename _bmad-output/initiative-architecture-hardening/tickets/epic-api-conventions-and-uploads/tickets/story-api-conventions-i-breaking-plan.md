---
title: 'API conventions I (breaking): tasks, settings, nested abstract analysis'
type: 'refactor'
ticket: '9'
created: '2026-11-09'
status: 'built'
baseline_revision: '32e579339ce8ae889a72b7261df6b2bcf14d66d3'
route: 'full'
route_source: 'auto'
risk: 'high'
review: 'quick'
review_source: 'pinned'
lenses_ran: ['quick']
review_loop_iteration: 0
context:
  - '{project-root}/docs/architecture/ADR-013-rest-api-crud-conventions.md'
  - '{project-root}/docs/architecture/coding-standards.md'
  - '{project-root}/docs/architecture/module-map.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Tasks change state through three verb endpoints beside an existing PATCH. Settings are split over several resources: app settings in EMS `/admin/settings/{key}` and CUMS `/settings/presentation`, user settings in `/users/me/settings` and `/users/me/preferences`. The abstract analysis loads a speaker-pool row by bare UUID at `/speakers/{speakerId}/ai/analyze-abstract`, without scoping it to an event. So there is more than one endpoint to extend per resource.

**Approach:** Breaking, no deprecation period. One `PATCH /tasks/{taskId}` carries status, notes, due date and assignee. App settings live on one resource backed by `AdminSettingsService`. User settings become one resource that merges both groups. The analysis moves under the event and loads the speaker scoped to it. Spec, generated types, backend, frontend, Bruno and Playwright change in the same PR.

## Boundaries & Constraints

**Always:**
- No deferred work (owner 2026-11-09). A test that fails because a mock is gone, or a defect found on the way, is fixed in the code within this PR. Never work around it or move it to deferred-work. Touched tests leave own-code mocks wherever the harness allows.
- Contract first: change `docs/api/` first, then run `npm run generate:api-types`.
- Tests are TDD and follow the test fidelity rules:
  - Integration tests use the production security chain.
  - Mocks sit at the boundary only. OpenAI goes through `MockRestServiceServer` on `BatbernAiService`'s builder seam, never `@MockitoBean BatbernAiService` or own repositories.
  - Touched tests come off own-code mocks: `AiAssistControllerIntegrationTest`, `TaskWidget.test.tsx`, and every `taskService`, `adminSettingsService`, `presentationService`, `userAccountApi`, `userApi` or `useUserAccount` test that is touched.
- `@PreAuthorize` stays on every write.
- Each new or changed operation has a spec example.
- The Spectral baseline shrinks. Record line counts before and after for the Spectral baseline, the ratchet, the i18n keys and the type copies.

**Never:**
- No deprecation aliases and no old paths kept.
- Never edit an applied Flyway migration.
- No new reads of another service's tables at runtime.
- Never touch the `mailcenter.notify-instead-of-forward` value.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|---|---|---|---|
| Complete a task | organizer `PATCH /tasks/{id}` `{status:"completed", notes:"x"}` | status completed, completedDate now, completedByUsername = caller, notes set | — |
| Reopen a task | `PATCH {status:"todo"}` on a completed task | completedDate and completedByUsername cleared | — |
| Reassign or unassign | `PATCH {assignedOrganizerUsername:"a"}` or `""` | assignee set, or cleared | — |
| Bad status | `PATCH {status:"done"}` | 400 | validation |
| Old task verbs | `PUT /tasks/{id}/complete` | 404 or 405, operation gone | — |
| User settings | `PATCH /users/me/settings {theme:"dark", timezone:"Europe/Zurich"}` | both stored, GET returns the merged object | other fields unchanged |
| Analysis on the wrong event | `POST /events/BATbern60/speakers/{idOfOtherEvent}/abstract-analysis` | 404 | — |
| Presentation read | anonymous `GET /public/settings/presentation` | same body as before the move | — |

</frozen-after-approval>

## Code Map

- `docs/api/event-tasks-api.openapi.yml` L382/427/472: remove `completeTask`, `reassignTask`, `updateTaskStatus` and their request schemas. L517 `updateTask` (PATCH, `UpdateEventTaskRequest` L841) gains `status` (enum `pending|todo|in_progress|completed`).
- `services/event-management-service/.../controller/EventTaskController.java` L234/260/288/313 and `service/EventTaskService.java` L373/425/459/577: fold complete, reassign and status into `updateTask`. Completing sets `completedByUsername` to the caller, which also fixes the inconsistency noted at L481. Integration tests: `EventTaskControllerIntegrationTest` L158/186 and `EventTaskServiceIntegrationTest` L210/285/320.
- `web-frontend/src/services/taskService.ts` L226/245/263/292: callers are `TaskBoardPage.tsx` L113/140, `TaskWidget.tsx` L54, `TaskBoardModal.tsx` L94. Bruno `tasks-api/07-complete-task.bru` and `08-reassign-task.bru` switch to PATCH.
- App settings:
  - EMS `AdminSettingsController` / `AdminSettingsService` (table `app_settings`, V88) is the one store.
  - CUMS `PresentationSettingsController` / `PresentationSettingsService` (table `presentation_settings`, row 1: `aboutText`, `partnerCount`) and `companies-api.openapi.yml` L840/869 move to EMS. Value under key `presentation`, as JSON like `venue.coordination.config`.
  - The public read becomes `GET /public/settings/presentation` in EMS next to `/public/settings/features` (`event-app-settings-api.openapi.yml`). The write is `PUT /admin/settings/presentation`.
  - Gateway `DomainRouter.java` L99/113, `SecurityConfig.java` L281/284; CUMS `SecurityConfig` L93/142; EMS `SecurityConfig` L154; `scripts/ci/access-control-public.txt` L33-34.
  - Frontend `services/presentationService.ts` L91/102, `PresentationSettingsTab.tsx`, `usePresentationData.ts`.
- User settings:
  - `users-api.openapi.yml` `/users/me/preferences` L2037 and `/users/me/settings` L2130, schemas `UserPreferences` ~3993 and `UserSettings` ~4037, includes at L240/252/403 and user schema at L3524/3528.
  - CUMS `UserPreferencesController`, `UserSettingsController`, `UserService` L1091-1105/1152-1306. The embeddables `UserPreferences`/`UserSettings` on `user_profiles` stay as storage.
  - EMS `client/impl/UserApiClientImpl.java` L270/441 reads `?include=preferences` (language, `qnaNotificationFrequency`), and `docs/api/internal/cums-internal.openapi.yml` describes it.
  - Frontend: `services/api/userAccountApi.ts` L161-207 (sends `timezone` to preferences, a field not in that schema), `services/api/userApi.ts` L35 (`LanguageSwitcher.tsx` L40, `UserMenuDropdown.tsx` L81), `hooks/useUserAccount/useUserAccount.ts` L32-95, `UserSettingsTab.tsx`, `AuthContext.tsx` L150, `types/user.ts` L41/50, `types/userAccount.types.ts` L52/70. Playwright: `e2e/user-account/settings-management.spec.ts`, `e2e/helpers/mock-user-profile.ts` L33.
- AI:
  - `event-ai-api.openapi.yml` L423-445 (`analyzeAbstract`) and `AiAssistController.java` L133-159.
  - Add `SpeakerPoolRepository.findByIdAndEventId`; `NewsletterSendRepository` L44 is the model.
  - Remove the gateway special case `DomainRouter.java` L73-79 and its tests `DomainRouterTest` L370-395.
  - Frontend `aiAssistService.ts` L57, `useAiAssist.ts` L93, `QualityReviewSubView.tsx` L66 (has `eventCode`).
- Lint:
  - `api-lint-baseline.txt` lines 23, 50, 51, 77, 107-109 go.
  - `api-lint-owners.txt` G03, G04, G05 go. The new analysis uuid line falls under G08 (17.10, speaker path key).
  - `scripts/api-lint.test.ts` L247-249 pins removed lines: repoint it to surviving baseline lines.
  - `.spectral.yaml` L69 drops `analyze-abstract` if nothing else uses it.

## Tasks & Acceptance

**Execution:**
- [x] Record counts before: Spectral baseline, ratchet, type copies, i18n.
- [x] Tasks: spec, then EMS (red integration test for PATCH status complete/reopen/bad status, plus old path gone), then frontend service and three callers, then tests on MSW, then Bruno 07/08.
- [x] App settings: EMS spec ops, then EMS public read plus admin key `presentation` with JSON validation. Add an EMS Flyway migration that, only when `presentation_settings` exists (`to_regclass`), copies its row into key `presentation` and then drops the table in the same migration, so the copy cannot be lost to deploy order and it is a no-op in EMS tests. The pre-deploy RDS snapshot covers it. Remove the CUMS controller, service, entity and spec ops. Owner 2026-11-09: no deferred work. Update gateway routing and permitAll, the service security chains, `access-control-public.txt`, the frontend and its tests.
- [x] User settings: one `UserSettings` schema with all fields of both groups. `GET` + `PATCH /users/me/settings` (partial). Remove `/users/me/preferences` and `include=preferences`; `include=settings` returns the merged object. Update CUMS, EMS `UserApiClientImpl` with the internal spec and its contract test, frontend services, hooks, types, `LanguageSwitcher`, `UserMenuDropdown`, `AuthContext`, Playwright helper and spec. Fix `timezone` so it is actually stored.
- [x] AI: spec `POST /events/{eventCode}/speakers/{speakerId}/abstract-analysis`, then `findByIdAndEventId`, then the controller. Integration test: other-event id gives 404, happy path through OpenAI `MockRestServiceServer`, 503 on empty. Remove the gateway special case; frontend gets eventCode in hook and service.
- [x] Lint baseline, owners, `api-lint.test.ts`, `.spectral.yaml`; regenerate types; ratchet, i18n and type-copy baselines updated (only shrink).
- [x] Docs: ADR-013 note if the settings decision needs one, `docs/architecture` settings section, doc-drift mappings.

**Acceptance Criteria:**
- Given the specs, when `npm run lint:api` runs, then the baseline has 6 fewer lines net (8 removed, 1 G08 added, no others) and no UNOWNED or STALE.
- Given the frontend, when `make check-frontend-api-paths` runs, then no call targets a removed operation.
- Given CUMS, EMS and gateway, when their full suites run, then they are green, the response conformance gate included.
- Given local dev, when an organizer completes a task, edits presentation settings and changes language and theme, then each change persists after a reload.

## Implementation Notes

Commits on `chore/17-0-tickets-done` (not pushed): 90107322 tasks, 7fd0a5e0 AI, 56fd646a app
settings, 4f2bf339 user settings (+ docs).

Counts (entry lines, file lines in brackets):
- Spectral baseline: 124 → 118 (131 → 125): 8 removed, 1 G08 added. Owner groups G03, G04, G05 removed.
- Test ratchet: 678 → 662 (683 → 667), only shrinks.
- i18n keys baseline: 0 entries before and after (4 lines). One new key `settings.saveFailed` in 10 locales.
- API type copies: 2 before and after (8 lines).
- tsconfig.test-harness exclude list: presentationService.test.ts and taskService.test.ts removed.

Defects found and fixed on the way: drag-and-drop completion left completedByUsername empty;
AiAssist IT used a topic category the DB check rejects (hidden by mocked repository); the
Upcoming Events slide sent an undeclared `status` param to `/events` and showed nothing (now
`/public/events?scope=upcoming`); timezone was never stored; CUMS/PCS answered 500 for a body
that does not bind (ClientErrorClassifier now 400); settings tab swallowed save errors;
UserMenuDropdown test mocked only `apiClient.put`, so the language save failed silently.

Review fixes (coordinator, 2026-11-09):
- V152 copies only (to_regclass guard, ON CONFLICT DO NOTHING), no DROP: an old CUMS image still
  maps `presentation_settings` under ddl-auto validate and must start mid-deploy or after the
  automatic ECS rollback. The table stays unread; **a follow-up migration drops it one release
  later** (to schedule as a ticket). Docs 03 and 06 say so.
- `venue.coordination.config` is validated on write with the read-side parse (400 on invalid),
  `VenueCoordinationConfigSettingIntegrationTest`.
- `UserSettingsControllerIntegrationTest` and `EventTaskControllerIntegrationTest` commit and
  clean up (no class-level @Transactional); `AuthContext.test.tsx` runs the real authService on
  the Amplify boundary mock. Ratchet 662 → 659.
- Settings tab shows a failed save only under the panel that saved; settings saves invalidate
  `['userProfile']` too; quiet hours are cleared with an empty string (spec, IT); AI abstract
  analysis: 503 tests for an empty and an unparseable completion.

Not done: live verification on local dev (AC4), Bruno tasks collection and the Playwright
settings spec against local dev. `make dev-native-*` uses the shared `batbern_development`
database; starting this branch's EMS applies V152 there and drops `presentation_settings`,
which breaks CUMS of the other worktrees on develop at their next start. Needs owner go or a
separate database.

## Plan Change Log

- 2026-11-09, review pass 1: the Execution line "copies ... and then drops the table in the same migration" is amended. V152 copies only, because a drop in the same release breaks the CUMS rollback (Hibernate validate). The drop follows as a forward migration one release later. KEEP: the to_regclass guard and ON CONFLICT DO NOTHING.

## Review Triage Log

Pass 1 (quick lens, 2026-11-09): high 1, medium 5, low 3, false 0, rejected 1 (plan edit).
- high, patch: V152 drops `presentation_settings` while the old CUMS image (ddl-auto validate) still maps it; a rollback or a mid-deploy CUMS start fails. Fix: copy only; drop one release later (rollback safety). Plan deviation recorded in Plan Change Log.
- medium, patch: the docs claim `venue.coordination.config` is validated on write, but it was only parsed on read. Fix: validate on write, with an IT.
- medium, patch: the touched write ITs (UserSettings, EventTask controller) keep a class-level @Transactional. Fix: commit and clean up.
- medium, patch: the touched `AuthContext.test.tsx` still mocks `authService`. Fix: move it to the boundary.
- medium, patch: the stale `['userProfile']` cache after a settings PATCH. Fix: invalidate it.
- medium, patch: quiet hours can no longer be cleared after PUT changed to PATCH (reported by the implementer). Fix: "" clears.
- low, patch: a save error shows in both settings panels. Fix: track per section.
- low, patch: no 503 test for an empty OpenAI answer. Fix: add one.
- open, verification: AC4 live check on local dev, Bruno, Playwright. Done after all three builds, on a cloned dev database.
- rejected: the plan's "8 removed" count (the fix would be a plan edit). The actual diff is 7 removed, 1 rewritten, net −6 (124→118).

## Design Notes

Work rules on rack:
- One heavy job at a time: never run a Gradle suite, Vitest, Playwright or a `git commit`/`git push` hook in parallel.
- Gradle runs with `--max-workers=4`, Vitest with `--maxWorkers=4`.
- Logs go to `~/.cache/batbern-test-logs/` via `tee`, with `set -o pipefail`.
- Commit on the current branch in conventional-commit steps (tests first where sensible), never `--no-verify`, never push.
- Services are managed only with `make dev-native-*`.

Architecture note:
- **Modules:** EMS tasks (L3), ai (L6), platform admin settings (L0); CUMS users; gateway routing.
- **New dependencies:** none. Presentation settings leave CUMS for EMS `AdminSettingsService`; nothing calls across services for them.
- **Endpoints:** task verbs fold into the existing `PATCH /tasks/{taskId}`. Presentation joins `/admin/settings/{key}` (write) and `/public/settings/*` (read, nearest is `/public/settings/features`). Preferences fold into `/users/me/settings`. `analyze-abstract` becomes `/events/{eventCode}/speakers/{speakerId}/abstract-analysis`; nearest are the `/events/{eventCode}/speakers/{speakerId}/*` operations, and the noun avoids a verb segment.
- **Reused:** `AdminSettingsService`, `findByIdAnd{Parent}` pattern.
- **Deviations:** the one-off EMS migration reads and drops a former CUMS table. It is a move, not a runtime read; ADR-016 is untouched.

Decisions taken by the agent under the owner's blanket go (2026-11-09, "assume all is accepted"):
- (a) The user resource is the flat merged `UserSettings`, written with PATCH, because ADR-013 makes partial updates PATCH.
- (b) Presentation is stored as one JSON key, as `venue.coordination.config` already is.
- (c) Feature flags (`/public/settings/features`) and the gateway `/config` are derived configuration, not stored settings, so they stay.
- (d) The noun path `abstract-analysis`.

## Verification

**Commands:**
- `set -o pipefail; ./gradlew :services:event-management-service:test --max-workers=4 2>&1 | tee ~/.cache/batbern-test-logs/49-ems.log`: expected BUILD SUCCESSFUL. Same for CUMS and `:api-gateway:test`, one at a time.
- `cd web-frontend && npm run type-check && npm run lint:api && npx vitest run --maxWorkers=4`: expected green.
- `make check-frontend-api-paths check-test-ratchet check-i18n-keys check-api-type-copies`: expected green.
- Bruno tasks collection and the settings Playwright spec against local dev (`make dev-native-up`): expected green.

Verification after review (2026-11-10): shared-kernel, EMS, CUMS, PCS and gateway full suites green; frontend type-check, vitest, lint:api, lint:ci, ratchet, i18n, type-copy and API-path checks green (logs `~/.cache/batbern-test-logs/v49core/`). Live check on local dev, Bruno tasks and the settings Playwright spec run after 4.9b and 16.8c, on a cloned dev database. Follow-up drop of `presentation_settings`: #1280 (one release later, rollback safety).
