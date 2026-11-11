---
title: 'API conventions III (breaking), part 1: EMS verb and namespace paths'
type: 'refactor'
ticket: '10b'
created: '2026-11-11'
status: 'built'
baseline_revision: 'd1d85bdcff25ed7d1ced36a124eee6cfc7d383cc'
route: 'full'
route_source: 'auto'
risk: 'high'
review: 'quick'
review_source: 'pinned'
lenses_ran: ['quick']
review_loop_iteration: 0
context:
  - '{project-root}/docs/architecture/coding-standards.md'
  - '{project-root}/docs/architecture/ADR-013-rest-api-crud-conventions.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** EMS still has verb paths (`notifications/batch-read`, `batch-delete`, `{id}/read`, `ai-prompts/{key}/reset`, `sessions/batch-import`) and literals under `/events` (`types`, `recent-photos`, `registrations`) that shadow `{eventCode}` and need the GHSA-qhqv matchers. Notifications are not scoped to their recipient: any organizer can read, mark and delete another organizer's notifications by id or `?username=`. That breaks the "own notifications" premise of exception X4.

**Approach:** Breaking, no aliases (as in 4.9b, 4.10, 4.10c). Each old operation moves to the shape ADR-013 §1 gives it. Notifications are loaded scoped to the caller. Spec, generated types, backend, frontend, Bruno and Playwright change in the same change (C4). This is part 1 of 10b (EMS). The CUMS items go to new entry 10d (see Open Questions).

## Boundaries & Constraints

**Always:**
- Owner blanket go 2026-11-10. Commit locally on `feature/17-overnight-2026-11-10` and never open a PR. Verify on local dev only. Fix any defect found on the way in this change.
- No real outbound mail. Bruno requests that write data run only in dev-only collections, and Playwright specs that write are guarded to `TEST_ENV=development`.
- Contract first: `docs/api/` first, then `npm run generate:api-types`. Every new operation has a spec example. Put `@PreAuthorize` on every write. Follow the test fidelity rules. Touched tests come off own-code mocks and class-level `@Transactional`.
- Change the gateway and EMS together: route, `permitAll` in both chains, `scripts/ci/access-control-public.txt`.
- One heavy job at a time on rack. Run Gradle with `--max-workers=4` and Vitest with `--maxWorkers=4`. Write logs with `tee` under `~/.cache/batbern-test-logs/` behind `set -o pipefail`, and wrap long commands in `timeout`. Restart dev services only with `timeout 600 make dev-native-restart-service SERVICE=<name>`.
- The Spectral baseline shrinks. Record the counts before and after.

**Never:**
- No deprecation aliases or kept paths. Do not touch `users-api` or `companies-api` (10d), `GET /events/current` (#1070), or `photos/confirm` (11b).
- Never edit an applied Flyway migration (none is needed). Never touch `mailcenter.notify-instead-of-forward`.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|---|---|---|---|
| Mark one read | organizer `PATCH /notifications/{id}` `{status:READ}` on own | 200, `readAt` set | another organizer's id: 404, unchanged |
| Mark many read | `PATCH /notifications` `{notificationIds, status:READ}` | 200, `markedCount` = own ids only | foreign ids ignored, unchanged |
| Delete | `DELETE /notifications/{id}` | 200 own | foreign id: 404, row kept |
| List and count | `GET /notifications[/count]` | the caller's notifications only (no `username` param) | non-organizer: 403 |
| Prompt reset | `DELETE /ai-prompts/{promptKey}/override` | 200, `promptText == defaultText` | unknown key: 404; speaker: 403 |
| Session import | `POST /events/{c}/sessions` `{imports:[…]}` | 200, per-item result, duplicates skipped | none or both modes: 400 |
| Single session | `POST /events/{c}/sessions` with the single fields | 201 as today | — |
| Event types | `GET /event-types` anonymous; `GET`/`PUT /event-types/{type}` | 200; PUT organizer-only | PUT by speaker: 403 |
| Recent photos | anonymous `GET /public/event-photos?limit&lastNEvents` | 200 as today | — |
| Attendee history | organizer `GET /registrations?filter={"attendeeUsername":…}` | 200, paginated, that user only | no filter: 400; attendee: 403 |
| Old paths | the 8 removed operations | 404/405, or the status the `{eventCode}`/`{id}` template gives, pinned by an IT | — |

**Decisions (2026-11-11, decided by agent under owner's blanket go):**
- Split: this plan covers the EMS items. The additional-email key and companies get-or-create (CUMS) are entry 10d, added to the ticket tree on 2026-11-11; 11a waits on 10d.
- `batch-delete` and `GET /notifications/history` are removed, not converted: nothing calls them, and the history read anyone's notifications.
- Session import follows the 4.10c precedent: one create on the collection, mode check in the service (400), one response schema (single → 201 `{session}`, import → 200 counts + `details`).
- Notifications are scoped to the caller everywhere (exception X4 assumes "own notifications"); another organizer's id answers 404.

</frozen-after-approval>

## Code Map

- **Notifications:** `event-notifications-api.openapi.yml` L115-280; `notification/NotificationController.java` (unscoped `findById`, `findAllById`, `deleteAllById`, `username` param); `NotificationRepository` (add `findByIdAndRecipientUsername`, `findAllByIdInAndRecipientUsername`); ITs `NotificationControllerIntegrationTest`, `security/OrganizerOnlyEndpointsIntegrationTest`, `security/RemovedEndpointsIntegrationTest`. Frontend: `services/notificationApiClient.ts` L18-160 and test, `hooks/useNotifications.ts` (drop `username`), `NotificationsPage.tsx`, `TeamActivityFeed.tsx`, `EventManagementDashboard.tsx`.
- **AI prompts:** `event-ai-api.openapi.yml` L207 → `/ai-prompts/{promptKey}/override` DELETE; `controller/AiPromptController.java`, `service/AiPromptService.reset`. There is no IT today: add `AiPromptControllerIntegrationTest`. Frontend: `services/aiPromptService.ts` L37, `hooks/useAiPrompts.ts` and test, `Admin/AiPromptsTab.tsx`.
- **Sessions:** `event-sessions-api.openapi.yml` L396-560 (`createSession`), L1254 (removed), `CreateSessionRequest` L2908; `controller/SessionController.java` L345; `SessionBatchImportService`; `SessionBatchImportIntegrationTest`. Frontend: `services/sessionService.ts` L30-45 and test, `hooks/useSessionBatchImport`, `SessionBatchImportModal.tsx`.
- **Event types:** `events-core-api.openapi.yml` L1461-1700; `controller/EventTypeController.java`; `EventTypeControllerIntegrationTest`; `services/eventTypeService.ts` L22 and test; Bruno `event-types-api/01-09`; `e2e/helpers/event-fixture.ts`, `organizer/event-type-selection.spec.ts`.
- **Recent photos:** `event-media-api.openapi.yml` L29; `EventPhotoController` L47; `EventPhotoControllerIntegrationTest`, `EventPhotoServiceRecentPhotosIntegrationTest`; `services/eventApiClient.ts` L453 and `eventApiClient.mocked.test.ts`, `hooks/useRecentEventPhotos`.
- **Registrations list:** `event-registrations-api.openapi.yml` L1141; `EventController` L2062-2100, which moves to `ParticipantsController` (module map: no endpoints in `EventController`); `services/api/eventRegistrationService.ts` L245-256 and test, `EventsParticipatedTable.tsx` and test.
- **Gateway and chains:** `api-gateway/.../routing/DomainRouter.java` L92-95 (add exact `/api/v1/public/event-photos`); `config/SecurityConfig.java` L217-234; EMS `config/SecurityConfig.java` L84-106 (drop the GHSA-qhqv `/events/registrations` matcher, add `GET /event-types` and `/public/event-photos` exact); tests `ProductionSecurityFilterChainTest` L100-117, `EmsProductionSecurityFilterChainTest`, `DomainRouterTest`; `scripts/ci/access-control-public.txt` L15-16.
- **Lint:** `web-frontend/scripts/api-lint-{baseline,owners}.txt` (N5 4 lines, N6 6 lines with 1 for 10d, X4 `PUT /notifications/{id}/read`); `docs/api/.spectral.yaml` verb comments.
- **Not affected (checked):** the Watch app (no call to these paths), mail templates (no links to these paths), infrastructure (no path lists), and the verify link of 10d.

## Tasks & Acceptance

**Execution:**
- [ ] Record the start sizes (table below).
- [ ] Notifications: spec first. RED ITs on the production chain: own vs another organizer's notification for PATCH one, PATCH many, DELETE, list and count; old paths. Then the scoped repository methods and the controller. Then frontend service and hooks on MSW (`notificationApiClient.test`, `useNotifications.test` off own-code mocks). Commit.
- [ ] AI prompts: spec, new IT (override deleted → default, 404, 403), controller, frontend. Commit.
- [ ] Sessions: spec (one request schema, `imports` ≤ 100, mode rule in the description, one response schema). Move `SessionBatchImportIntegrationTest` to the collection, add mode-error tests, then the controller, frontend and Bruno `sessions-api` 09/20 (wrapped response). Commit.
- [ ] Event types, recent photos, registrations: spec, ITs (anonymous allowed only where listed; the registrations filter is required; the list is paginated per §6), controllers (move the registrations list out of `EventController`), gateway route, both chains, access-control list, frontend. Commit.
- [ ] Bruno (development): new dev-only `notifications-api` (create a `BRUNO-TEST-` event to cause a notification, then list, mark one, mark many, delete; foreign-id 404 not testable with one organizer, covered by IT), `ai-prompts-api` (capture, PUT, DELETE override, restore the exact captured text), `session-import-api` (own event, import of 2 synthetic items without PDF, duplicate skipped, mode 400), each registered in `dev_only_collections`. Change paths in `event-types-api`. Add an anonymous `GET /public/event-photos` to `events` and the registrations list to `participants-api`. Prose goes in `docs {}`.
- [ ] Playwright (local dev guard): new `organizer/notifications.spec.ts` (mark read, delete), `organizer/ai-prompt-reset.spec.ts` (edit, reset, restore), `organizer/session-batch-import.spec.ts` (synthetic JSON fixture). Extend `event-type-selection.spec.ts` with an admin event-type edit that restores the exact prior values.
- [ ] Lint `--update`: remove N5, reduce N6 to the companies line under a new group owned by 10d, set N8's owner to 10d, and swap X4 `PUT …/read` for `PATCH /notifications/{id}`. Regenerate the types. Update the ratchet with `--update` (it may only shrink). Docs: ADR-013 note for 10b, `06-backend-architecture.md`, doc-drift-mapped docs. Commit.

**Acceptance Criteria:**
- Given the specs, when `npm run lint:api` runs, then the baseline has 88 entries (97 − 9), N5 is gone, X4 holds `PATCH /notifications/{id}` instead of `PUT …/read`, and nothing is UNOWNED or STALE.
- Given EMS ITs, when an organizer marks, deletes, lists or counts notifications, then only their own are affected or returned, and another organizer's id answers 404 and stays unchanged.
- Given ITs per new operation, then each matrix row passes, and each removed operation answers the status its IT pins.
- Given local dev, when an organizer marks and deletes notifications, resets a prompt, imports sessions, edits an event type, and opens the homepage and a user's event history, then each change persists after a reload. Bruno (development) and the Playwright specs above pass with no `Skipping invalid file`.
- Given `make check-frontend-api-paths` (17.3), then it passes.
- After deploy (owner): the old paths answer 404 or their pinned status in production, anonymous `GET /event-types` and `GET /public/event-photos` answer 200, and the access-control DAST passes.

## Implementation Notes

## Plan Change Log

## Review Triage Log

Pass 1 (quick lens), 2026-11-11: 2 high, 2 medium, 2 low, 0 false.

| # | Finding | Verdict | Route | Evidence / action |
|---|---------|---------|-------|-------------------|
| 1 | shared-kernel `ResponseConformanceTest` L124-129 and `ContractServerTest` L424 still use the removed `GET /events/registrations` (live specs) | high | patch | Suite would go red; move the examples to a path that exists, fix the javadocs and the coding-standards sentence. |
| 2 | Frontend notification queries lost the username in their cache key; no cache clear on sign-out → a second organizer in the same tab sees the first one's inbox | high | patch | Clear the query cache on sign-out (class fix) and key per user. |
| 3 | Four removed operations accept any 4xx instead of a pinned status | medium | patch | Pin the measured status (400) in the ITs and Bruno 11. |
| 4 | A failed session-import chunk discards the totals of chunks already committed | medium | patch | Accumulate per chunk, report partial results with the error. |
| 5 | Session mode check lives in the controller; plan and spec say the service | low | patch | Move it to the service. |
| 6 | `markedCount` counts already-read notifications and overwrites their `readAt` | low | patch | Count and change only unread ones. |

## Design Notes

**Architecture note.**
- **Modules:** EMS organizer notifications (L6), ai (L6), sessions and registrations and media (L3), event types (L2); gateway routing and security.
- **New dependencies:** none.
- **Endpoints, each with its nearest existing operation:**
  - `PATCH /notifications/{id}` and `PATCH /notifications` (nearest `DELETE /notifications/{id}`).
  - `DELETE /ai-prompts/{promptKey}/override` (nearest `PUT /ai-prompts/{promptKey}`).
  - The session import is a mode of `POST /events/{c}/sessions`.
  - `/event-types[/{type}]` (nearest `/events/{eventCode}`; PUT stays, its body has required fields, ADR-013 §1 row 3).
  - `GET /public/event-photos` (nearest `GET /events/{c}/photos`; ADR-013 §4 anonymous variant).
  - `GET /registrations` (nearest `GET /events/{c}/registrations`).
- **Reused:** `FilterCriteria` parsing (sessions), `PaginatedResponse`, the 4.10c mode-create idiom.
- **Deviations:** `POST /events/{c}/sessions/structural` stays. It is a generation job over the agenda (X9 class, like `auto-assign`), not a create of given sessions.

**Epic 17 rule (C15).** Violations exposed and fixed:
- unscoped notification loads, and list/count by any `username`;
- dead `batch-delete` and `history`;
- a list endpoint in `EventController`, and a filter passed in a header;
- the GHSA-qhqv literal matchers;
- a missing AI prompt IT, Bruno and Playwright.

| Store | Start (`d1d85bdc`) | Expected end |
|---|---|---|
| Spectral baseline | 97 entries | 88 (10d: 87) |
| Test ratchet | 597 entries; touched files carry 13 | shrinks |
| ArchUnit EMS / CUMS | 4 / 4 lines | unchanged |
| s2s guard, API layer, type copies, i18n | 0 | 0 |

## Verification

**Commands:**
- `set -o pipefail; timeout 3600 ./gradlew :services:event-management-service:test --max-workers=4 2>&1 | tee ~/.cache/batbern-test-logs/410b-ems.log`, then `:api-gateway:test`, one at a time -- expected: BUILD SUCCESSFUL, ArchUnit stores unchanged
- `cd web-frontend && npm run lint:api && npm run check:api-types && npm run type-check && timeout 1800 npx vitest run --maxWorkers=4` -- expected: green, 88 entries
- `make check-test-ratchet && make check-frontend-api-paths && make check-s2s-client-urls` -- expected: exit 0
- `./scripts/ci/run-bruno-tests.sh development` -- expected: all collections PASS, including the three new dev-only ones
- Playwright on local dev (`TEST_ENV=development`): the three new specs, `organizer/event-type-selection`, `organizer/admin-tabs` and the homepage spec -- expected: pass

**Manual checks:**
- After `timeout 600 make dev-native-restart-service SERVICE=event-management` and the same for `api-gateway`: walk the ACs. Through the gateway, the old paths answer their pinned statuses, and `/dev/mails` gets nothing new.

Final verification 2026-11-11 after the review patches (bade6e7c): shared-kernel, CUMS, EMS, PCS and gateway suites green; lint:api (88), lint:api-internal, check:api-types, type-check, check:s2s-client-urls, Vitest 5878 passed, test ratchet and API paths green. Local dev before the patches: Bruno 21/21 collections, Playwright 24 passed. After deploy: old paths at their pinned status, anonymous GET /event-types and /public/event-photos 200, access-control DAST.
