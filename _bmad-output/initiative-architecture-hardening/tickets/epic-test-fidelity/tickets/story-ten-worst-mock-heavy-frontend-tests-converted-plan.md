---
title: 'Ten worst mock-heavy frontend tests converted (17.0c2)'
type: 'refactor'
ticket: '0c2'
created: '2026-11-08'
status: 'built'
baseline_revision: 'fa880af6192b1291149491b772d9c1971b2b6690'
route: 'full'
route_source: 'auto'
risk: 'medium'
review: 'quick'
review_source: 'pinned'
lenses_ran: ['quick']
review_loop_iteration: 0
followup_review_recommended: false
context:
  - '{project-root}/docs/architecture/coding-standards.md'
  - '/tmp/claude-1000/-home-nissim-dev-bat-BATbern-bmad-v7/4631b2e5-8c64-4b93-94d3-3fc01dbf8c22/scratchpad/overnight-brief.md'
warnings: ['oversized']
deferred: []
---

<intent-contract>

## Intent

**Problem:** The files with the most own-code `vi.mock`/`vi.spyOn` in the 17.0a ratchet baseline test mocks, not code: their hooks, services and child components are replaced, so wrong paths, wrong response shapes and broken wiring pass (the investigation already found three: undeclared registration query params, a `publish/schedule` body the backend does not accept, an unbound `this` in `searchCompanies`).

**Approach:** Convert the 11 files below onto the 17.0c1 MSW harness (`api.on`/`api.calls`) and the 17.0d fixtures: real hooks, services, stores and child components, real i18n. Fix test-first every production defect or spec gap the conversion exposes. The ratchet baseline falls by their counts.

**Decisions (taken on the owner's behalf under the overnight mandate, 2026-11-08):**
1. *Which ten.* Ranked by own-code mock count (services+hooks+components) in the 0a baseline (`75a2d315`). Ranks 1-7 are unambiguous; four files tie at 9 for ranks 8-10. All four are converted (11 files), since the criterion cannot break the tie and leaving one out would be arbitrary.
2. *Auth.* `useAuth` is `useContext(AuthContext)`; the real `AuthProvider` sits on Amplify (a boundary) plus `GET /users/me`. Tests supply the auth state through the real `AuthContext.Provider` from one shared helper (`src/test/auth.tsx`), never `vi.mock('@/hooks/useAuth')`. The hook and every consumer run for real.
3. *WebSocket.* The dashboard's STOMP/SockJS client is a network boundary: the test mocks the third-party packages (`@stomp/stompjs`, `sockjs-client`) or sets the condition the client already uses to stay off, never our `useNotificationWebSocket` hook or `notificationWebSocketClient`.
4. *Spec gap.* `GET /events/{eventCode}/registrations` (hand-mapped in `EventController`) accepts `status` (repeatable), `search`, `companyId` and a 1-indexed `page`; the spec declares only `filter`, `page`, `limit`. The spec is corrected to the backend (the backend is production truth); `filter` is kept only if a caller or the backend uses it.

## Boundaries & Constraints

**Always:** After conversion, the 11 files have zero `fe-mock-services`, `fe-mock-hooks`, `fe-mock-components` and `fe-mock-i18n` lines in the ratchet; the baseline is regenerated with `--update` and only falls. Each converted file keeps (or strengthens) every behaviour its old tests asserted, now against the real tree; an assertion that only checked a mock's call arguments becomes an assertion on `api.calls(op)` (what went over the wire) or on rendered output. `api.on` bodies are typed from `src/types/generated/`; every converted file is added to `tsconfig.test-harness.json`. Test data comes from `src/test/fixtures/` where a fixture fits (event codes `BATbern<N>`, people from `people`/`aUser`). Zustand stores run for real (set state with `useXStore.setState`, reset in `beforeEach`; persisted stores: clear `localStorage`). Mocking allowed only at the boundary: MSW for `/api`, Amplify (already global), browser APIs jsdom lacks (`matchMedia`), the third-party WebSocket packages. Every production bug found is fixed test-first (red, then green). The ratchet does not rise anywhere.

**Never:** No `vi.mock`/`vi.spyOn` of `@/services`, `@/hooks`, `@/components`, `@/pages` or PascalCase modules in the 11 files; no `vi.mock('react-i18next')`; no per-file `setupServer`; no `server.use` for an `/api` URL. No conversion of files outside the 11 (other files only change if a shared type/service change forces it). No edit to applied Flyway migrations, no staging/production access, no push, no PR. Do not delete a test case to make a file pass; if a case tested something that does not exist (a mocked child's fake button), replace it with the real behaviour it stood for.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Registrations filter | `useEventRegistrations('BATbern61', {filters:{status:['CONFIRMED']}, search:'Müller'})` | request validated by the harness (no violation), `api.calls('listRegistrations')[0].query.getAll('status')` = `['CONFIRMED']` | spec missing a param → `SpecViolationError` |
| Company search error | `searchCompanies` against `api.on('searchCompanies', () => HttpResponse.json(problem,{status:500}))` | rejects with the transformed API error, not `TypeError` | test-first fix |
| Publishing schedule | `scheduleAutoPublish('BATbern61', request)` | body matches spec `AutoPublishScheduleRequest` (typed from generated types) | type-check |
| Role guard | ProtectedRoute with an attendee auth state on an organizer route | redirect to `/dashboard` | none |
| Network failure | `api.on('listPartners', () => HttpResponse.error())` | partnerApi call rejects with axios `ERR_NETWORK` | none |

</intent-contract>

## Code Map

The 11 files (0a own-code count) and what replaces their mocks. Harness: `web-frontend/src/test/msw/api.ts` (`api.on`, `api.calls`, `ApiCall.query/params/body/request`), defaults in `server.ts` (spec example; `listUsers`/`searchUsers`/`listEvents`/`listRegistrations` from fixtures). Examples of converted style: `src/services/api/partnerApi.search.test.ts`, `companyApi.search.test.ts`, `src/components/shared/__tests__/UserAutocomplete.test.tsx`. Render helper: `src/test/test-utils.tsx` `renderWithProviders` (QueryClient + MemoryRouter). Baseline green before: 11 files, 183 tests (log `~/.cache/batbern-test-logs/0c2-baseline.log`).

1. `src/components/organizer/PartnerManagement/__tests__/PartnerDirectoryScreen.test.tsx` (32) -- mocks `usePartners` hook, both stores, 5 children. Real: `usePartners` → `GET /partners` `listPartners` (`partnerApi.ts:69`; query `page,size,include,filter,search`; response `{data, pagination|metadata}` per generated type), `PartnerOverviewStats` → `getPartnerStatistics`. Stores `src/stores/partnerStore.ts:58` (persist, key `partner-store`), `partnerModalStore.ts:31`. `PartnerCreateEditModal` renders null while closed; "Add" click → assert `usePartnerModalStore.getState().isOpen` or the dialog. Old "called with tier GOLD" → `api.calls('listPartners')` `filter` contains `partnershipLevel:GOLD`.
2. `src/components/auth/ProtectedRoute/ProtectedRoute.test.tsx` (22) -- spies `useAuth`. Real: `src/hooks/useAuth/useAuth.ts:11` = `useContext(AuthContext)`; `AuthContext` exported at `src/contexts/AuthContext.tsx:73`, value type `UseAuthReturn` (:32-71), `UserContext` in `src/types/auth.ts:23` (role lowercase). Use the new auth helper. No network.
3. `src/components/organizer/EventManagement/__tests__/EventManagementDashboard.test.tsx` (14) -- mocks `useEvents`, `useNotifications`, `useAuth`, `eventStore`, `useNotificationWebSocket`, `taskService`, children. Real calls: `listEvents` (fixtures; dashboard asks `include=registrations`), `listNotifications`, `getMyTasks` (real `src/components/organizer/Tasks/TaskWidget.tsx`; the `'../Tasks/TaskWidget'` mock never matched), `getFeatureFlags` (EventForm mounted closed). WebSocket: `src/services/notificationWebSocketClient.ts:48` skips unless hostname is localhost, `:121` `new SockJS(...)`; see Decision 3. Event titles: set via `api.on('listEvents', ...)` with fixture events (`events()`/`futureEvent()`) when a title is asserted. Team activity text comes from real `TeamActivityFeed` (renders notification `subject`/`body`): override `listNotifications`. Store `src/stores/eventStore.ts:55` (persist).
4. `src/services/api/partnerApi.test.ts` (13) -- `vi.spyOn(apiClient, ...)` + axios-mock-adapter. Ops: `listPartners`, `getPartnerStatistics`, `getPartnerByCompanyName`, `createPartner`, `updatePartner` (PATCH), `getPartnerContacts`, `getMyPartnerCompany`. Network error → `HttpResponse.error()`; 404/409 → `HttpResponse.json(problem,{status})`. Fix drifted test data to generated types (`UpdatePartnerRequest` has no `isActive`; contacts need `username`; list uses `metadata`; `partnershipStartDate` required). `Skip-Auth` header of `listPublicPartners`: assert on `api.calls('listPartners')[0].request.headers` if it survives the interceptor, else assert the documented effect (no `Authorization`). Company names ≤ 12 chars.
5. `src/hooks/useEventManagement/useEventRegistrations.test.tsx` (12) -- mocks `eventRegistrationService`. Real: `eventRegistrationService.ts:71` → `listRegistrations`, params `page,limit,status[],companyId,search`. Needs the spec fix (Task 1). Assert data from fixture registrations, query on the wire, query key via `queryClient.getQueryCache()`.
6. `src/components/organizer/PartnerManagement/CompanyAutocomplete.test.tsx` (11) -- spies `companyApi.searchCompanies`/`getOrCreateCompany`. Real: `searchCompanies` GET `/companies/search?query&limit=20&include=logo`; `getOrCreateCompany` POST `/companies:get-or-create`; `useCompany` `getCompany`. Bug: `companyApi.ts:305` exports `searchCompanies` unbound while its catch uses `this.transformError` (TypeError on HTTP error); `getOrCreateCompany` (:313) is bound -- fix the same way. Component types results as `CompanyResponse[]`; spec returns `CompanySearchResponse` -- use the generated type. Drop the redundant `I18nextProvider`.
7. `src/components/organizer/EventPage/__tests__/EventPage.test.tsx` (11) -- mocks `useEvent`, `useTabBadges`, 8 tab children, Breadcrumbs, partial `react-router-dom` (`useNavigate`) and `@mui/material` (`useMediaQuery`). Real: `getEvent` (`include=...`; override with a fixture event `BATbern54`, `workflowState` per test), `useTabBadges.ts:17` → `listEventTasks` + `getPublishingStatus` (override to produce the badge cases, logic in `tabBadges.ts`), default Cockpit tab → `CockpitTab` (needs auth helper; `useEventTasks`, `getSpeakerPool`, agenda config). Navigation: assert with a `<Route>` sentinel instead of mocking `useNavigate`; mobile: stub `window.matchMedia` (jsdom lacks it) instead of mocking MUI. Sub-tab assertions use the real tabs' visible content. `../EventSettingsTab` mock never matched.
8. `src/services/publishingService/publishingService.test.ts` (9) -- `vi.mock(apiClient)`. Ops `publishPhase`, `unpublishPhase`, `getPublishingStatus`, `configureAutoPublish` (`events-core-api.openapi.yml:2043-2174`). Drift: hand-written `PublishPhaseResponse`, `UnpublishPhaseResponse`, `AutoPublishScheduleRequest/Response` in `src/types/event.types.ts:444-530` do not match the spec/backend (`PublishingEngineController.java` returns generated DTOs); `scheduleAutoPublish` sends `{scheduledDate}`. Move the service and `src/hooks/usePublishing/usePublishing.ts` to the generated types, drop the unused `_phase` arg; delete the hand-written copies no one else uses. Check `PublishingStatusResponse` against generated too (consumers `EventPublishingTab.tsx:45`, `tabBadges.ts:57`, `PublishingControls.tsx:49`). Error cases: use 400/403 (spec), not 422; a 401 triggers the interceptor redirect (`apiClient.ts:141`).
9. `src/pages/organizer/EventManagementAdminPage.test.tsx` (9 + i18n) -- mocks 8 tab components, `react-i18next`, `useAuth`. Only tabs 0-2 render: real `EventTypesTab` (`listEventTypes`), `ImportDataTab` (closed modals), `TaskTemplatesTab` (`useAuth`, `listAllTemplates`, `CustomTaskModal` → `listEvents`). Replace mock testids with real tab content (headings/text from `public/locales/en/admin.json`). Role guard at page :75.
10. `src/components/TopicBacklogManager/TopicBacklogManager.test.tsx` (9 + i18n) -- mocks `useTopics`, `useEvents`, `useUserList`, 7 children, `react-i18next`. Real: `listTopics` (`topics-api/listTopics` key; `sort=-stalenessScore`, `page`, `limit=20`, `include=history`, `filter` JSON), `getEvent`, `getTopicByCode`, `listUsers` (fixtures), `listEvents`, details panel → `/topics/{code}/similar`, `usage-history`, confirm → `selectTopicForEvent` (`onTopicConfirm` receives `topicCode`). Real testids `topic-card-${topicCode}` (`TopicList.tsx:86`), `confirm-topic-selection-button`. The fake "Apply Filter" button: drive the real `TopicFilterPanel` control and assert the `filter` query. recharts renders empty in jsdom (ResizeObserver mocked globally).
11. `src/components/organizer/EventPage/__tests__/EventSpeakersTab.test.tsx` (9) -- mocks 6 children, `useSpeakerPool`, `useEvents`, `speakerStatusService`. Real: `getStatusSummary`, `getSpeakerPool`, `getEvent?include=sessions` (spec example has no sessions: override with two sessions, one `startTime: null`), slots view → `getUnassignedSessions`, `getEventTimetable`. `SpeakerStatusLanes` uses dnd-kit (renders in jsdom; no drag needed). Use `findBy*` (loader until summary+pool resolve).

Other files:
- `docs/api/event-registrations-api.openapi.yml:56-80` -- `listRegistrations` params; backend `services/event-management-service/src/main/java/ch/batbern/events/controller/EventController.java:2120-2145` (hand-mapped `@GetMapping`, not the generated interface: no Java change).
- `web-frontend/src/test/auth.tsx` (new) -- `AuthTestProvider`/`renderWithAuth` and `anAuthUser({role})` building a full `UseAuthReturn` (real `canAccess`/`hasRole` semantics where cheap, overridable).
- `web-frontend/tsconfig.test-harness.json` -- add the 11 files and `src/test/auth.tsx`.
- `web-frontend/scripts/test-ratchet-baseline.txt` -- regenerate with `npm run check:test-ratchet -- --update`.

## Tasks & Acceptance

**Execution:**
- [x] `docs/api/event-registrations-api.openapi.yml` -- declare `status` (array, `style: form, explode: true`, enum of the registration statuses), `search`, `companyId`; `page` 1-indexed default 1, `limit` default 25 max 100 as the controller does; keep `filter` only if used; then `cd web-frontend && npm run generate:api-types`, `npm run lint:api`, and check `fixtureDefaults.ts` paging still matches the corrected `page` semantics (fix + its test if not) -- spec matches production.
- [x] `src/services/api/companyApi.ts` -- failing test first (HTTP error from `searchCompanies` through the harness rejects with the transformed error), then bind `searchCompanies`; `CompanyAutocomplete.tsx` uses the generated `CompanySearchResponse` type.
- [x] `src/services/publishingService/publishingService.ts`, `src/hooks/usePublishing/usePublishing.ts`, `src/types/event.types.ts` -- generated types, spec request body for `configureAutoPublish`; delete unused hand-written copies; `npm run type-check` green.
- [x] `src/test/auth.tsx` -- shared auth-state provider helper (Decision 2).
- [x] The 11 test files -- converted per Code Map; each old assertion kept or replaced by the real-tree equivalent.
- [x] `tsconfig.test-harness.json` -- include the converted files and `src/test/auth.tsx`.
- [x] `scripts/test-ratchet-baseline.txt` -- `--update`; diff shows only removed/lowered lines.
- [x] `docs/architecture/coding-standards.md` "Frontend: the MSW harness" -- one paragraph: auth state via `src/test/auth.tsx`, stores via `setState`, third-party WebSocket packages as boundary; `_bmad-output/project-context.md` Frontend Testing gets the auth-helper line (owner allows agent-context edits in 17.0 stories).

**Acceptance Criteria:**
- Given the ratchet, when `make check-test-ratchet` runs after `--update`, then it passes and the baseline diff removes every counted line of the 11 files and raises none.
- Given the 11 files, when `grep -nE "vi\.(mock|doMock|spyOn)\(" ` runs on them, then no hit targets own code or `react-i18next` (third-party boundary mocks and `console`/`window` spies only).
- Given the full frontend suite, when run, then it passes with zero `SpecViolationError`, and `npm run type-check`, `npm run lint`, `npm run format:check` are clean.
- Given the three exposed defects, when their new tests run against the pre-fix code, then they fail (recorded in Implementation Notes), and pass after the fix.

## Implementation Notes

Built 2026-11-08/09 under the overnight mandate; 13 commits `8e3b7095..d552027a` plus the docs/ratchet commit.

Red before green (recorded runs, logs in `~/.cache/batbern-test-logs/0c2-*`):
- `companyApi.errors.test.ts`: 6 of 7 exported functions rejected with `TypeError` on a 500 (searchCompanies, getCompanies, getCompany, createCompany, updateCompany, deleteCompany; only getOrCreateCompany was bound). Green after binding all.
- `publishingService.test.ts` (`configureAutoPublish` body) failed against the old `{ scheduledDate }` call; green after moving to `AutoPublishScheduleRequest`.
- Registrations spec: `fixtureDefaults.test.ts` (limit 25, status filter) red, then green; `RegistrationConfirmationPage.test.tsx` red on the EMS field names (empty name and e-mail on the live page), then green; EMS `RegistrationControllerIntegrationTest.should_returnWaitlistPosition_when_listingRegistrations` red (`No value at JSON path $.data[0].waitlistPosition`), then green.
- `TopicFilterPanel`: selects had no accessible name (red in TopicBacklogManager conversion), fixed with `labelId`.
- `useSlotAssignment`: duplicate unassigned-sessions fetch on mount (red: 2 calls) and rollback to an empty tray on a fast failure (red: `[]` instead of 2), both fixed.

Verification (run): full Vitest suite 465 files / 6012 passed, 0 failed, no SpecViolationError; `type-check`, `lint`, `format:check`, `lint:api` clean; `make check-test-ratchet` and `make check-frontend-api-paths` pass; baseline diff only removes the 21 lines of the 11 files. EMS: `RegistrationControllerIntegrationTest`, `RegistrationService*`, `PublishingEngineControllerIntegrationTest` green.

The only `vi.mock` left in the 11 files: `@stomp/stompjs` and `sockjs-client` in EventManagementDashboard (Decision 3).

## Plan Change Log

- Decision 4 extended: not only the `listRegistrations` parameters but the `Registration` response schema was wrong (spec `firstName/lastName/email/company`, EMS `attendee*`), and EMS did not send `waitlistPosition` at all. Both fixed to production truth (spec + one EMS DTO field), because the conversion depends on the response shape.
- Publishing errors: the plan said "use 400/403 (spec), not 422". EMS does answer 422 with `PublishValidationError` (asserted by its IT), so the spec gained the 422 instead; the test asserts 422, 401 (redirect) and 403. `PublishingStatusResponse` fields EMS always sets are now required in the spec.
- Auth helper: role/permission/path rules moved into `src/contexts/authRules.ts` so the helper runs the provider's real `canAccess`/`hasRole`.
- `searchCompanies` callers: the pickers and three parent forms now type the selected company as `CompanySearchResponse` (they invented createdAt/updatedAt/website placeholders).

## Findings not fixed (for the owner)

- `ImportDataTab` reads `import.*` keys missing from `public/locales/*/admin.json`; English shows the code fallbacks, other locales too (10-locale rule).
- `data-testid="event-card-<code>"` is set twice (EventList wrapper and EventCard root).
- EventPage's "Event not found" branch is unreachable on the real route; a 404 shows the generic error branch.
- Fixture `Event` allows `sessions: null` while the `getEvent` response type does not; tests build the body with explicit sessions.
- `useSlotAssignment.test.ts`, `DragDropSlotAssignment.test.tsx`, `RegistrationConfirmationPage.test.tsx`, `usePublishing.test.ts(x)` still mock own code (outside the 11; only adapted where a type change forced it).

## Review Triage Log

| # | Finding | Verdict | Route | Evidence |
|---|---------|---------|-------|----------|
| 1 | Confirmation page shows `attendeeCompany`, which is the company key | medium | patch | Checked: EMS `RegistrationService.java:278` sets it from `userProfile.getCompanyId()`. Before 0c2 the page read `registration.company`, which was never sent, so no company was shown. Patch: the company line was removed, the changelog no longer claims the company, and a test checks that the key is not shown. |
| 2 | `expectAccessDenied` asserts no calls synchronously, so it cannot fail | medium | patch | Checked: calls are recorded inside the MSW resolver, after the async auth interceptor. Patch: the helper is async and waits 100 ms inside `act` before asserting. |
| 3 | `Registration` schema description names only the organizer endpoints | low | patch | Checked: the public `getRegistration` returns the same schema with the e-mail masked. Patch: the description now covers both endpoints. |
| 4a | Plan golden pattern uses `anAuthUser`/`user:`, which the helper does not have | low | rejected | The fix would edit this build's plan. The helper's own docblock shows the correct use. |
| 4b | `anAuthState` overrides of `user` keep the rules bound to the old user | low | patch | Checked: the rules close over the `user` argument. Patch: the rules, the token and `isAuthenticated` now follow an overridden `user`. |
| 5a | `partnerApi.test` calls `mockReset` on the global Amplify mock | low | rejected | The global mock has no default implementation, so reset and clear behave the same. No test is affected. |
| 5b | `partnerApi.test` contacts are hand-written, not from the fixtures | low | rejected | Cosmetic: the names are synthetic and the test is correct either way. |

## Design Notes

Architecture note: modules: web-frontend tests (11 files, `src/test/auth.tsx`), `companyApi`, `publishingService`/`usePublishing`, `event.types`, registrations spec · new dependencies: none · endpoints: none new; `GET /events/{eventCode}/registrations` spec corrected to what `EventController.listRegistrations` already accepts (nearest/only op, extended not added) · reused: MSW harness, `api.on`/`api.calls`, 17.0d fixtures, `renderWithProviders`, generated types · deviations: none.

Golden pattern for a component (real tree, harness, auth helper):

```tsx
api.on('listPartners', () => ({ data: [gold], metadata }));
renderWithAuth(<PartnerDirectoryScreen />, { user: anAuthUser({ role: 'organizer' }) });
expect(await screen.findByText(gold.company!.displayName)).toBeInTheDocument();
act(() => usePartnerStore.setState({ filters: { tier: 'GOLD', status: 'all' } }));
await waitFor(() => expect(api.calls('listPartners').at(-1)!.query.get('filter')).toContain('partnershipLevel:GOLD'));
```

## Verification

Rack rules: one heavy job at a time, Vitest `--maxWorkers=4`, logs in `~/.cache/batbern-test-logs/0c2-*.log` (tee with `set -o pipefail`, grep the log, no re-run to search).

**Commands:**
- `cd web-frontend && npx vitest run --maxWorkers=4 <the 11 files> src/services/api/companyApi* src/hooks/usePublishing src/test` -- expected: pass.
- `cd web-frontend && npx vitest run --maxWorkers=4` (full suite) -- expected: 0 failures.
- `cd web-frontend && npm run type-check && npm run lint && npm run format:check && npm run lint:api` -- expected: clean (lint:api at baseline).
- `make check-test-ratchet` -- expected: no RAISED, no STALE after `--update`.
- `make check-frontend-api-paths` -- expected: pass.
