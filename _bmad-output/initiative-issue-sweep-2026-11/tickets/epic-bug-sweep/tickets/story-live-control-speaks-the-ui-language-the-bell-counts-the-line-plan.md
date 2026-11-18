---
title: "Live Control speaks the UI language, the bell counts, the lineup card opens Communications"
type: 'bugfix'
ticket: '7'
created: '2026-11-18'
status: 'built'
baseline_revision: '388ccf46282c36e9ecd1d113c7023f46968c8f29'
route: 'full'
route_source: 'auto'
risk: 'low'
review: 'quick'
review_source: 'pinned'
lenses_ran: ['quick']
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
- [x] RED: component test Live Control in en shows English labels; layout/header test shows the unread count from the MSW harness; `resolveTaskTarget` test for the matrix rows
- [x] i18n keys + components
- [x] bell wiring
- [x] rule order

**Acceptance Criteria:**
- Given `grep` over the Live Control components (tests excluded), when looking for German literals, then none is left.

## Design Notes

Architecture note: frontend only (Live Control, layout, cockpit card rules). No new dependency or endpoint; reuses `useNotifications` and `GET /notifications`. Deviations: none.

## Verification

**Commands:**
- `cd web-frontend && npm run type-check && npm run lint && npx vitest run --maxWorkers=4 src/components/organizer/LiveControl src/pages/LiveControlPage src/components/shared/Layout src/components/organizer/EventPage src/App.test.tsx` (whatever exists) -- green.
- `make check-test-ratchet`, `make check-i18n-keys` -- clean.

**Rack rules:** one heavy build at a time, Vitest `--maxWorkers=4`, output via `set -o pipefail; ... | tee ~/.cache/batbern-test-logs/<name>.log`. Do not commit or push. A bug found on the way is fixed too and reported (initiative C9).

## Implementation Notes

- `components/shared/Layout/AuthLayout.tsx` (new) feeds the header from `useNotifications({status:'UNREAD'},{page:1,limit:10})`, the dashboard's cache entry. Deviation from the Boundaries' `enabled: !!user`: only organizers fetch, because `NotificationController` is `hasRole('ORGANIZER')` and others would get 403.
- Found and fixed: the header read `notifications.unreadCount`, which `GET /notifications` never returns; the count is `pagination.totalItems`.
- Live Control: `events:liveControl.*` in 10 locales. Cockpit: a `/newsletter/i` rule first; only "Newsletter: Speaker Lineup" changes target.
- Verification (orchestrator): type-check, lint, ratchet, i18n keys; Vitest 50 files / 493 tests before the review fixes, 12 files / 140 after.

## Review Triage Log

Quick review, 4 findings: 3 patched, 1 rejected.
- low, patched: organizer-only bell is a deviation not recorded in the plan. Recorded above.
- low, patched: no test rendered `LiveControlPage` or opened the delay sheet. Page test (en, MSW, real auth) and delay-sheet test added.
- low, patched: `AppHeader` comment called the prop test-only; rewritten.
- low, rejected: the bell refreshes only on focus, remount or invalidation (2-min stale time). REST source by design (no push, no polling, initiative C1); now stated in `AuthLayout`'s comment.
