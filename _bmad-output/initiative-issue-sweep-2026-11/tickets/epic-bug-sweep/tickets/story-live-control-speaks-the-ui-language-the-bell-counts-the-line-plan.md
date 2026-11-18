---
title: "Live Control speaks the UI language, the bell counts, the lineup card opens Communications"
type: 'bugfix'
ticket: '7'
created: '2026-11-18'
status: 'in-progress'
baseline_revision: '203d1a882a8ad1756ce52430ac6d5ee01710c62a'
route: 'full'
route_source: 'auto'
risk: 'low'
review: ''
review_source: ''
lenses_ran: []
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** (#1219) Live Control hard-codes German labels (`components/organizer/LiveControl/*`, `pages/LiveControlPage/*`, e.g. "Verlängern", "Nächste Session", "← Verzögern"). The notification bell never shows an unread count: `App.tsx` `AuthLayout` renders `<BaseLayout>` without `notifications`. The "Newsletter: Speaker Lineup" cockpit card opens Speakers → Pool because `cockpitCards.ts` `TASK_TARGET_RULES` matches `/speaker|…|line.?up/` before the newsletter catch-all.

**Approach:** Every Live Control string becomes an i18n key in all 10 locales (EN, DE first-class). `AuthLayout` (or `BaseLayout`) loads the caller's unread notifications through the existing `useNotifications` hook (`{ status: 'UNREAD' }`, the REST source story 4 kept) and passes them to the header. A newsletter rule goes first in `TASK_TARGET_RULES`.

## Boundaries & Constraints

**Always:** Real i18next, auth helper and MSW harness in tests; touched tests leave own-code mocks (C6, ratchet). Bell data only for signed-in users (`enabled: !!user`). Keys in all 10 locales (`make check-i18n-keys`).

**Never:** Change the notifications API or backend. Re-add any WebSocket.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Live Control in en | UI en, active session | English labels ("Extend", "Next session", "Delay") | — |
| Live Control in de | UI de | German labels as today | — |
| Bell | 3 UNREAD in the MSW answer | badge shows 3 | request fails → no badge, no crash |
| Signed out / public page | — | no notifications request | — |
| Lineup card | task "Newsletter: Speaker Lineup" | target Communications | — |
| Other cards | "Speaker Briefing", "Newsletter: Final Agenda", "Thank-you slides" | unchanged targets (speakers pool, communications, wrapup) | — |

</frozen-after-approval>

## Code Map

- `web-frontend/src/components/organizer/LiveControl/{ActiveSessionCard,AgendaList,DelaySessionSheet,ExtendSessionSheet}.tsx` and `src/pages/LiveControlPage/` -- literal strings, also aria-labels and toasts; existing `ActiveSessionCard.test.tsx`.
- `web-frontend/src/App.tsx:197-200` `AuthLayout`; `components/shared/Layout/BaseLayout.tsx:20-23,72` (`notifications?: NotificationPage`) → `AppHeader`; `hooks/useNotifications.ts` (used by `EventManagementDashboard`, `NotificationsPage`) -- reuse its query key so marking read elsewhere updates the bell.
- `web-frontend/src/components/organizer/EventPage/cockpit/cockpitCards.ts:154-172` `TASK_TARGET_RULES` + its tests in `cockpit/__tests__/`.
- Locales: `web-frontend/public/locales/*/` (pick the namespace Live Control already uses, else `events`).

## Tasks & Acceptance

**Execution:**
- [ ] RED: component test Live Control in en shows English labels; layout/header test shows the unread count from the MSW harness; `resolveTaskTarget` test for the matrix rows
- [ ] i18n keys + components
- [ ] bell wiring
- [ ] rule order

**Acceptance Criteria:**
- Given `grep` over the Live Control components (tests excluded), when looking for German literals, then none is left.

## Design Notes

Architecture note: frontend only (Live Control, layout, cockpit card rules). No new dependency or endpoint; reuses `useNotifications` and `GET /notifications`. Deviations: none.

## Verification

**Commands:**
- `cd web-frontend && npm run type-check && npm run lint && npx vitest run --maxWorkers=4 src/components/organizer/LiveControl src/pages/LiveControlPage src/components/shared/Layout src/components/organizer/EventPage src/App.test.tsx` (whatever exists) -- green.
- `make check-test-ratchet`, `make check-i18n-keys` -- clean.

**Rack rules:** one heavy build at a time, Vitest `--maxWorkers=4`, output via `set -o pipefail; ... | tee ~/.cache/batbern-test-logs/<name>.log`. Do not commit or push. A bug found on the way is fixed too and reported (initiative C9).
