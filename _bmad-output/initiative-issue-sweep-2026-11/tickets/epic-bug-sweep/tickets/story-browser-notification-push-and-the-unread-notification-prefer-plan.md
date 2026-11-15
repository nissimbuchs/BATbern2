---
title: 'Browser notification push and the unread notification preferences are gone'
type: 'refactor'
ticket: '4'
created: '2026-11-15'
status: 'in-progress'
baseline_revision: '26e0102b40799bf19cf68f63f04668d5d1010302'
route: 'full'
route_source: 'auto'
risk: 'medium'
review: ''
review_source: ''
lenses_ran: []
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The organizer dashboard opens a STOMP client for in-app notification push that connects only on localhost, so production never gets a push (#1222 item 4); EMS still sends every IN_APP row to `/topic/notifications/{username}`. The preferences `inAppNotifications`, `pushNotifications` and `notificationFrequency` are stored, offered in the profile and never read by any sender (#1222 item 3).

**Approach:** Delete the browser WS client, its hook and the server push; keep the Watch STOMP endpoint and broker. Delete the three preferences from users-api, cums-internal, CUMS entity and service, EMS `UserPreferences` and client mapping, the auth context, the settings UI and its locale keys. The columns are dropped in epic 2 (decision below).

## Boundaries & Constraints

**Always:** Watch live timing, presence and arrivals keep running over STOMP (`WebSocketConfig`, `WatchWebSocketController`, `WatchPresenceService`, `WatchSpeakerArrivalService` untouched). The bell's REST source (`useNotifications`, `/notifications`) stays for story 7. `emailNotifications`, `qnaNotificationFrequency` and quiet hours stay. Touched tests move off own-code mocks (C6). Spec first, then `npm run generate:api-types`.

**Never:** Change quiet-hours logic (story 5). Edit an applied migration. Touch the Cognito `custom:preferences` sign-up blob beyond its `push` key.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Read settings | `GET /users/me?include=settings` | No `inAppNotifications`, `pushNotifications`, `notificationFrequency` keys | — |
| Patch settings | `PATCH /users/me/settings` with `emailNotifications:false` | Saved, other fields unchanged | — |
| Domain event for organizers | `OrganizerNotificationListener` | IN_APP rows written, nothing sent to `/topic/notifications/*` | — |
| Watch client | STOMP connect + presence | unchanged | — |

**Decision (owner 2026-11-15):** the columns stay in this PR (nullable with defaults); their drop migration ships in epic 2's PR, after this image is live. This PR adds no migration.

</frozen-after-approval>

## Code Map

- `web-frontend/src/services/notificationWebSocketClient.ts`, `web-frontend/src/hooks/useNotificationWebSocket.ts` -- delete.
- `web-frontend/src/components/organizer/EventManagement/EventManagementDashboard.tsx:26,76-91` -- drop the hook and the effect; `__tests__/EventManagementDashboard.test.tsx:28-55,148` drop the stompjs/sockjs fakes.
- `web-frontend/package.json` -- remove `@stomp/stompjs`, `sockjs-client`, `@types/sockjs-client` (only users are the client above).
- `services/event-management-service/.../notification/NotificationService.java:34,165-186` -- drop `SimpMessagingTemplate` and `pushNotificationViaWebSocket`; `OrganizerNotificationListener.java:143` drop the call; `NotificationServiceTest.java:52,65` drop the mock.
- `.../events/notification/UserPreferences.java`, `.../client/impl/UserApiClientImpl.java:310-313`, `src/test/.../config/CumsContractHarness.java` -- drop the three fields.
- `docs/api/users-api.openapi.yml` (~318-320, 2057-2059, 2106, 2129-2131, `UserSettings` required + properties ~3475-3512, `UpdateUserSettingsRequest` ~3588-3593), `docs/api/internal/cums-internal.openapi.yml:103-105` -- drop the fields and examples (2106 example switches to another field).
- `services/company-user-management-service/.../domain/UserPreferences.java:38-48`, `service/UserSettingsService.java`, tests `UserSettingsControllerIntegrationTest`, `UserPreferencesTest`, `migration/UserManagementMigrationsTest.java:105-107`.
- `web-frontend/src/types/userAccount.types.ts:58-61`, `services/api/userAccountApi.ts:70-73,92-97,173-175`, `contexts/AuthContext.tsx:153-157`, `types/auth.ts:13-17` (`push`; `sms` is constant false and read nowhere: drop the `notifications` object), `services/auth/authService.ts:325-330,555`, `components/user/UserSettingsTab/UserSettingsTab.tsx:506-507,699-764` -- drop fields and the in-app, push and frequency controls; tests `userAccountApi.test.ts`, `userApi.test.ts`, `useUserAccount.test.ts`, `UserAccountPage.test.tsx`, `AuthContext.test.tsx`, `src/test/fixtures/people.ts`.
- `web-frontend/public/locales/*/userManagement.json` -- remove `settings.notifications.channelInApp|channelPush|frequency|frequencyImmediate|frequencyDaily|frequencyWeekly`.
- `docs/architecture/06d-notification-system.md` (lines ~39, 160-166, 191, 285), `04-api-user-management.md`, `06b-user-lifecycle-sync.md` -- remove the push and the three preferences.

## Tasks & Acceptance

**Execution:**
- [ ] specs -- remove fields, regenerate types -- contract first
- [ ] CUMS -- RED: settings IT asserts the three keys are absent from GET and that PATCH with `emailNotifications` still saves; remove fields from entity, service, tests
- [ ] EMS -- RED: listener test asserts no STOMP send (no `SimpMessagingTemplate` bean use outside watch, ArchUnit-free: constructor no longer takes it); remove push, prefs fields, harness fields
- [ ] frontend -- RED: settings tab test asserts email and Q&A controls present, in-app/push/frequency absent; remove client, hook, deps, fields, controls, locale keys
- [ ] docs -- 06d, 04, 06b

**Acceptance Criteria:**
- Given the repo, when grepping `src`, `services`, `docs/api` for the three preference names (Q&A frequency excluded) and for `notificationWebSocketClient|useNotificationWebSocket|/topic/notifications`, then nothing is found.
- Given the EMS suite, when the Watch STOMP ITs run, then they pass.
- Given CUMS, when `S2sProviderContractIntegrationTest` and `UserSettingsControllerIntegrationTest` run, then they pass.

## Design Notes

Architecture note: modules touched: EMS notification (L1 notify core), EMS client, CUMS user settings, frontend organizer dashboard and account. No new dependencies; one dependency removed (EMS notification no longer uses the STOMP template). No new endpoints; `PATCH /users/me/settings` and `GET /internal/users/{username}?include=settings` lose fields. Reused: the existing settings IT and MSW harness. Deviations: none.

## Verification

**Commands:**
- `./gradlew :services:company-user-management-service:test` and `:services:event-management-service:test` (one at a time, tee'd) -- green.
- `cd web-frontend && npm run type-check && npx vitest run src/components/user src/contexts src/services/api src/hooks/useUserAccount src/pages/UserAccountPage src/components/organizer/EventManagement --maxWorkers=4` -- green.
- `make check-test-ratchet`, `cd web-frontend && npm run check:api-types && npm run lint` -- clean.

**Rack rules:** one heavy build at a time (never two Gradle/Vitest runs in parallel), Gradle `--max-workers=4`, Vitest `--maxWorkers=4`, output via `set -o pipefail; ... | tee ~/.cache/batbern-test-logs/<name>.log`. Do not commit or push; leave the changes in the working tree.
