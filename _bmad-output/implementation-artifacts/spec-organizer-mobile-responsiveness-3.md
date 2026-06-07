---
title: 'Organizer mobile responsiveness — round 3 (beta feedback)'
type: 'bugfix'
created: '2026-06-05'
status: 'done'
context: []
baseline_commit: '9348db760e5e59c25fe7ee5240790870ea8286f6'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Second beta pass found 4 remaining issues: partner-topics still a table on mobile, slot-assignment timetable has fixed widths (should be fluid on mobile AND desktop), user-detail event-participation list not cards, newsletter actions hidden behind a 3-dot menu.

**Approach:** Same branch `fix/organizer-mobile-responsiveness`, same patterns (card view <md, desktop table unchanged — except slot grid, which goes fluid on ALL viewports per explicit instruction; newsletter actions become directly visible buttons in BOTH views). Redeploy to beta via `scripts/deploy/publish-beta-frontend.sh`; do NOT push.

## Boundaries & Constraints

**Always:** isMobile = down('md'). New visible strings → all 10 locales (prefer reusing existing keys + icons w/ tooltips). TDD. Desktop ≥960px unchanged EXCEPT slot-assignment fluid grid and newsletter visible actions (both explicitly requested).

**Ask First:** Dropping any data field from the new card layouts.

**Never:** Backend changes. New dependencies.

**Post-completion (renegotiated by user 2026-06-05):** after fixes are verified + beta-deployed, PUSH the branch and CREATE a PR to `develop`.

</frozen-after-approval>

## Code Map

- `web-frontend/src/components/organizer/NewsletterSubscribers/NewsletterSubscriberTable.tsx:56-282` -- reference card pattern + the kebab menu to remove
- `web-frontend/src/components/organizer/TopicStatusPanel.tsx:77-414` -- 7-col table, per-row {status, plannedEvent, saving} state, Save/Edit/Delete
- `web-frontend/src/components/SlotAssignment/DragDropSlotAssignment/DragDropSlotAssignment.tsx:419` -- minWidth { xs: 560, md: 800 }
- `web-frontend/src/components/organizer/UserManagement/EventsParticipatedTable.tsx:38-141` -- read-only 4-col participation table (in UserDetailView)

## Tasks & Acceptance

**Execution:**

- [x] `components/organizer/TopicStatusPanel.tsx` -- mobile (<md) card view: title+description, CompanyLogo, votes, status Select, plannedEvent TextField (when SELECTED), Save/Edit/Delete in CardActions (reuse per-row state/mutations); desktop table unchanged; drop now-dead xs-only sx (xs column hiding, minWidth xs key).
- [x] `components/SlotAssignment/DragDropSlotAssignment/DragDropSlotAssignment.tsx:419` -- remove the `minWidth` entirely so the timetable grid is fluid to viewport width on mobile AND desktop (Grid fractions already proportional); keep slot minHeight ≥44 for drag targets; keep overflowX wrapper as harmless safety.
- [x] `components/organizer/UserManagement/EventsParticipatedTable.tsx` -- mobile (<md) card view: eventCode + title, date, status (colored); desktop table unchanged; read-only.
- [x] `components/organizer/NewsletterSubscribers/NewsletterSubscriberTable.tsx` -- remove MoreVert kebab + Menu entirely (and the breakpoint anchor-clearing effect); render the status-conditional actions as directly visible icon buttons with Tooltips (reuse `actions.*` keys) in BOTH desktop actions cell and card CardActions: active→unsubscribe (UnsubscribeIcon), unsubscribed→resubscribe (MarkEmailReadIcon), suppressed→unsuppress (RestartAltIcon), all→delete (DeleteOutlineIcon, error color).
- [x] Unit tests -- TopicStatusPanel card switch (375 vs 1280 via parsed matchMedia mock); EventsParticipatedTable card switch; NewsletterSubscriberTable: visible action buttons replace menu (per status), in both views; DragDropSlotAssignment: minWidth rule gone (stylesheet assertion updated/removed).

**Acceptance Criteria:**

- Given 375px, when /organizer/partner-topics renders, then topics are cards with working status/save/edit/delete; at ≥960px the table is unchanged.
- Given any viewport, when slot assignment renders, then the timetable fills the available width with no horizontal scroll caused by a fixed min width.
- Given 375px, when a user detail page shows participations, then they render as cards; desktop table unchanged.
- Given any viewport, when a newsletter subscriber row/card renders, then its applicable actions are directly visible (no 3-dot menu anywhere).
- Given the suite, test + build + lint pass.

## Spec Change Log

- 2026-06-05 review round (patches, no loopback): restored `minWidth: 800` on TopicStatusPanel desktop table (auditor: `sm:800` removal exceeded task scope); added created-date caption to topic cards (Ask-First boundary: no field drops); EventsParticipatedTable stable keys + de-flaked empty-state test. KEEP: shared renderStatusSelect/renderPlannedEventField/renderActions between table+card branches; visible newsletter action buttons in both views.

## Verification

**Commands:**
- `cd web-frontend && set -o pipefail && npm run test 2>&1 | tee /tmp/mobile3-test.log` -- all pass
- `cd web-frontend && set -o pipefail && npm run build 2>&1 | tee /tmp/mobile3-build.log` -- success
- `cd web-frontend && set -o pipefail && npm run lint 2>&1 | tee /tmp/mobile3-lint.log` -- 0 errors

**Manual checks (if no CLI):**
- Beta: /organizer/partner-topics at 375px (cards), slot assignment at 375px + 1440px (fluid grid), user detail participations at 375px, newsletter list both widths (visible actions).

## Suggested Review Order

**Partner topics → cards**

- Entry point: shared per-row renderers reused by both card and table branches
  [`TopicStatusPanel.tsx:300`](../../web-frontend/src/components/organizer/TopicStatusPanel.tsx#L300)

**Fluid slot grid (mobile + desktop)**

- minWidth removed entirely; Grid fractions size to viewport
  [`DragDropSlotAssignment.tsx:417`](../../web-frontend/src/components/SlotAssignment/DragDropSlotAssignment/DragDropSlotAssignment.tsx#L417)

**Participation list → cards**

- Read-only card branch, stable keys, de-CH dates kept
  [`EventsParticipatedTable.tsx:114`](../../web-frontend/src/components/organizer/UserManagement/EventsParticipatedTable.tsx#L114)

**Newsletter visible actions**

- Kebab Menu deleted; per-status Tooltip IconButtons in table cell + card footer
  [`NewsletterSubscriberTable.tsx:1`](../../web-frontend/src/components/organizer/NewsletterSubscribers/NewsletterSubscriberTable.tsx#L1)

**Tests (peripherals)**

- Card switches, visible-button per-status coverage, fluid-grid assertion
  [`TopicStatusPanel.test.tsx:1`](../../web-frontend/src/components/organizer/TopicStatusPanel.test.tsx#L1)
  [`EventsParticipatedTable.test.tsx:1`](../../web-frontend/src/components/organizer/UserManagement/EventsParticipatedTable.test.tsx#L1)
  [`NewsletterSubscriberTable.test.tsx:1`](../../web-frontend/src/components/organizer/NewsletterSubscribers/__tests__/NewsletterSubscriberTable.test.tsx#L1)
