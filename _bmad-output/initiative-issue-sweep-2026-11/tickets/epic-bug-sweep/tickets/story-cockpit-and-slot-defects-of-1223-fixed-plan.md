---
title: 'Cockpit and slot defects of #1223 fixed'
type: 'bugfix'
ticket: '8'
created: '2026-11-18'
status: 'in-progress'
baseline_revision: 'd4d23d1a2e15abb127fb78fad6cfdd0dba5cfcd4'
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

**Problem:** #1223 items observed in the running app (not traced to code by the reporter): (1) Speakers & Agenda → Slots shows "0 Total Sessions / 0 Assigned / 0 Pending" and "0 of 0 assigned (0%)" while every BATbern60 session is slotted; (2) cockpit card links read "Open Registrations → →"; (3) "Due in 0 days" instead of "Due today"; (5) admin task templates show "-90d before event", and "Newsletter: Slides Are Online" shows "1d before event" though it is due after the event; (6) Details → Tasks says tasks "will be created when you save the event", but on an existing event they are created as soon as they are ticked; (9) on a phone the Slots "UNASSIGNED" label is cut off.

**Approach:** Trace each item to its code, fix it at the source, one test per item that fails before the fix.

## Boundaries & Constraints

**Always:** Real i18next, auth helper and MSW harness in component tests; touched tests leave own-code mocks (C6). New or changed UI text in all 10 locales. Item 1: find why the counters read 0 (wrong field, wrong source, a filter) and fix the data path, not the display. Item 5: the offset text says "N days before/after the event" with the sign handled, never "-90d before". Item 6: the text differs for a new event (created on save) and an existing one (created when ticked).

**Never:** Change backend task-template data. Change slot assignment behaviour.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Slot counters | fixture event with N sessions, all slotted | "N Total / N Assigned / 0 Pending", "N of N assigned (100%)" | — |
| Card link | any cockpit card | one arrow | — |
| Due today | due date today | "Due today" (10 locales) | — |
| Offset -90 | template offset -90 | "90 days before the event" | — |
| Offset +1 | slides newsletter, +1 | "1 day after the event" | — |
| Offset 0 | 0 | "On the event day" | — |
| Tasks text | existing event vs. new event | two different texts | — |
| Phone 375px | Slots view | "UNASSIGNED" fully visible | — |

</frozen-after-approval>

## Code Map

- Item 1: `web-frontend/src/components/organizer/EventPage/EventSpeakersTab.tsx` (and the slots view it renders; grep `totalSessions`, "of {{total}} assigned"), the hook/query that feeds it.
- Item 2: `public/locales/*/events.json` `cockpit…open`: "Open {{tab}} →" and "Open →" plus a component that appends its own arrow/icon (`components/organizer/EventPage/cockpit/*.tsx`).
- Item 3: `events.json:36` `dueSoon: "Due in {{days}} days"` and the caller computing days.
- Item 5: admin `components/organizer/Admin/TaskTemplatesTab.tsx` offset chip; event-form templates list (`components/organizer/Tasks/EventTasksTab.tsx`).
- Item 6: `events.json:676` `defaultTemplatesDescription` and where it is shown (event form Details → Tasks).
- Item 9: the slots view header/column label for UNASSIGNED (grep in `components/organizer/` for the slot assignment components).

## Tasks & Acceptance

**Execution:**
- [ ] RED: one test per item (layout assertion or Playwright-style width check for item 9 if jsdom cannot measure: assert the label is not inside an element with `noWrap`/ellipsis styles, or the styles allow wrapping)
- [ ] fixes, 10 locales for text changes

**Acceptance Criteria:**
- Every matrix row has a test that failed before its fix.

## Design Notes

Architecture note: frontend only (cockpit, slots, task templates, event form). No new dependency or endpoint. Deviations: none.

## Verification

**Commands:**
- `cd web-frontend && npm run type-check && npm run lint && npx vitest run --maxWorkers=4` over every touched folder -- green.
- `make check-test-ratchet`, `make check-i18n-keys` -- clean.

**Rack rules:** one heavy build at a time, Vitest `--maxWorkers=4`, output via `set -o pipefail; ... | tee ~/.cache/batbern-test-logs/<name>.log`. Do not commit or push. A bug found on the way is fixed too and reported (initiative C9).
