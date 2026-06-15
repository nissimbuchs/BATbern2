---
title: 'Epic 14 Phase G — Full mobile navigation (bottom nav + More sheet, mobile Cockpit, tap-to-assign Slots, Registrations cards)'
type: 'feature'
created: '2026-06-14'
baseline_commit: '937ba1d8'
status: 'done'
context:
  - '{project-root}/docs/prd/epic-14-event-detail-redesign.md'
  - '{project-root}/docs/ux/event-detail-redesign-spec.md'
  - '{project-root}/_bmad-output/project-context.md'
---

<frozen-after-approval reason="human-owned intent — auto-accepted by Nissim 2026-06-14 (overnight autonomous run)">

## Intent

**Problem:** The organizer event page (now a 7-tab IA) must work on a phone. Today the mobile `BottomNavigation` (`EventPage.tsx`) crams **all 7 tabs** as icon-only actions; the Cockpit doesn't adapt; Slots is drag-only (hostile on touch). UX spec §11 + PRD Phase G (FR44–47): reshape mobile to a 4-primary bottom bar + ⋯ More sheet, adapt the Cockpit, add tap-to-assign Slots, and ensure Registrations renders as cards with a ⋯ overflow.

**What already exists (verified 2026-06-14) — Phase G is partly satisfied incidentally:**
- `LifecycleSpine` has a `compact` prop (→ "Step N/8" bar); `MetricTiles` is already 2-up on mobile (`xs:'repeat(2,1fr)'`); `AttentionList` auto-fills full-width. **Gap:** `CockpitTab` never passes `compact` on mobile (G.2).
- `EventParticipantTable` already renders cards on mobile mounting `RegistrationActionsMenu`. **Gap:** the actions are inline icon buttons, not the FR47 **⋯ overflow** menu (G.4).

**Approach (frontend-only, AR9/NFR9 — no backend):**
- **G.1** Reshape `EventPage` mobile nav: bottom bar = **Cockpit · Speakers · Registrations · Communications** (with the existing badge logic) + a **⋯ More** action opening a bottom sheet (`Drawer anchor="bottom"`) listing **Publishing · Wrap-up · Details**, each reading `getTabRelevance` (Wrap-up shows 🔒 + disabled until `EVENT_LIVE`). Reuse `tabBadge`/`useTabBadges`, `getTabRelevance`, the tab icons/labels.
- **G.2** `CockpitTab` detects mobile (`useBreakpoints`) and passes `compact` to `LifecycleSpine`; tiles/cards already responsive (assert, don't rebuild).
- **G.3** `DragDropSlotAssignment`: extract a shared `assignSessionToSlot(session,time,room)` from `handleDrop`; add `selectedSessionSlug` tap state. On touch, tapping a tray session selects+highlights it and lights up empty droppable cells; tapping an empty cell assigns via the shared path and clears selection. Structural cells stay non-interactive. `UnassignedSpeakersList` gains optional `onSessionTap`/`selectedSessionSlug` for tap-select + highlight.
- **G.4** Add a ⋯ overflow variant to `RegistrationActionsMenu` (MUI `Menu`) and use it in the mobile card so the row actions (Resend/Cancel/Delete) sit behind a single ⋯ button (FR47); desktop table keeps inline icons.

## Boundaries & Constraints

**Always:**
- Frontend-only; touch only `web-frontend/`. Reuse `useBreakpoints`, `getTabRelevance`, `useTabBadges`/`tabBadges`, `useSlotAssignment.assignTiming`, `RegistrationActionsMenu` actions, MUI `Drawer`/`Menu` (AR9/NFR9). `@/` alias, generated types, `config` (NFR8).
- New strings via `useTranslation('events')` in **all 10 locales** (NFR5), EN+DE first-class; reuse existing `eventPage.tabs.*` / `common:*`.
- Touch targets ≥44px, keyboard-operable, labelled; locked entries `aria-label` "(locked)" like desktop (NFR7). `data-testid` on every new control (`event-more-button`, `event-more-sheet`, `slot-tap-*`, `registration-actions-overflow`).
- No silent consequential action: tap-assign opens the same conflict path (`conflict` 409) as drag; locked tabs are non-interactive on mobile exactly as desktop.

**Ask First / Never:**
- If anything needs a backend/API change → must NOT (HALT). Tap-assign reuses the exact `assignTiming` contract.
- Don't change desktop layouts: desktop `Tabs` rail, desktop Slots drag, desktop table all unchanged. Mobile branches only (`isMobile`).
- Don't break drag-drop — tap-to-assign is **additive** alongside the native HTML5 handlers, sharing `assignSessionToSlot`.
- Don't alter tab IDs or the relevance map.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Mobile bottom bar | `isMobile` | 4 actions: Cockpit·Speakers·Registrations·Communications + ⋯ More; badges shown | — |
| ⋯ More | tap | bottom sheet lists Publishing·Wrap-up·Details; tap navigates + closes | — |
| Wrap-up in sheet | state `< EVENT_LIVE` | 🔒 + disabled (non-interactive); from `EVENT_LIVE` interactive | — |
| Active tab in sheet | current tab is a sheet tab | More entry shows selected/highlighted state | — |
| Mobile Cockpit | `isMobile` | `LifecycleSpine compact` ("Step N/8"); cards full-width; tiles 2-up tappable | — |
| Slots — tap session (touch) | tap tray card | selects + highlights; empty cells light up | — |
| Slots — tap empty cell | a session selected | `assignSessionToSlot` assigns; selection clears; grid refreshes | 409 → `ConflictDetectionAlert`; selection clears |
| Slots — tap structural cell | a session selected | ignored (non-droppable) | — |
| Slots — desktop | not mobile | drag-drop unchanged; tap-select inactive | — |
| Registrations card (mobile) | `isMobile` | card + a single ⋯ overflow → Resend/Cancel/Delete menu | per-action states (resend/cancel/delete) |
| Registrations desktop | not mobile | table with inline action icons unchanged | — |

</frozen-after-approval>

## Code Map

- `web-frontend/src/components/organizer/EventPage/EventPage.tsx` — mobile branch: split TABS into `primary` (cockpit, speakers, registrations, communications) + `more` (publishing, wrapup, details); render `BottomNavigation` with 4 primary + a ⋯ More `BottomNavigationAction` (`event-more-button`) toggling a `Drawer anchor="bottom"` (`event-more-sheet`) of the `more` tabs, each via `getTabRelevance` (locked → `LockIcon` + disabled) and `tabBadge`. Selecting any tab routes via existing `handleTabChange`/`navigateToTab` and closes the sheet. Desktop unchanged.
- `web-frontend/src/components/organizer/EventPage/cockpit/CockpitTab.tsx` — `const { isMobile } = useBreakpoints();` → `<LifecycleSpine workflowState={workflowState} compact={isMobile} />`.
- `web-frontend/src/components/SlotAssignment/DragDropSlotAssignment/DragDropSlotAssignment.tsx` — extract `assignSessionToSlot(session, time, room)` (the timing-resolve + `assignTiming` + invalidate body of `handleDrop`); `handleDrop` calls it. Add `selectedSessionSlug` state; on mobile, tray tap selects, empty-cell tap calls `assignSessionToSlot(selected, time, room)` then clears; cells get a "lit" style when a session is selected (`slot-tap-target` testid); structural cells excluded.
- `web-frontend/src/components/SlotAssignment/UnassignedSpeakersList/UnassignedSpeakersList.tsx` — add optional `onSessionTap?(session)` + `selectedSessionSlug?`; item gets `onClick`/highlight (additive; drag handlers untouched).
- `web-frontend/src/components/organizer/EventPage/RegistrationActionsMenu.tsx` — add `variant?: 'inline' | 'overflow'` (default `inline`); `overflow` renders one ⋯ `IconButton` → MUI `Menu` of the same Resend/Cancel/Delete actions (same handlers + delete-confirm dialog). `registration-actions-overflow` testid.
- `web-frontend/src/components/organizer/EventPage/EventParticipantTable.tsx` — mobile card passes `variant="overflow"` to `RegistrationActionsMenu`.
- `web-frontend/src/components/.../__tests__/` — **NEW/updated** tests: `EventPage` (4 primary + More sheet, locked Wrap-up in sheet, badge in bar), `CockpitTab` (compact on mobile), `DragDropSlotAssignment` (tap-select → tap-cell assigns; structural ignored; desktop drag intact), `RegistrationActionsMenu` (overflow menu exposes 3 actions), `EventParticipantTable` (mobile card uses overflow).
- `web-frontend/public/locales/{10}/events.json` — `eventPage.mobile.more` ("More"), `eventPage.mobile.moreSheetTitle`, `slotAssignment.tapToAssign.*` (selectHint, placeHint). Reuse existing tab labels + lock suffix. All 10 locales.

## Tasks & Acceptance

**Execution (TDD per story, commit per slice):**
- [x] G.2 — `CockpitTab` passes `compact={isMobile}`; test.
- [x] G.4 — `RegistrationActionsMenu` overflow variant + `EventParticipantTable` mobile uses it; tests.
- [x] G.1 — `EventPage` 4-primary bottom nav + ⋯ More sheet (relevance + badges); tests.
- [x] G.3 — extract `assignSessionToSlot`; tap-to-assign in `DragDropSlotAssignment` + `UnassignedSpeakersList` tap props; tests.
- [x] `events.json` ×10 — mobile/tap keys.
- [x] type-check + lint + scoped vitest green.

**Acceptance Criteria:**
- Given mobile, when the nav renders, then the bottom bar holds **Cockpit · Speakers · Registrations · Communications** (with badges/dots) + a **⋯ More** entry opening a sheet with **Publishing · Wrap-up · Details**; Wrap-up shows 🔒 and is non-interactive before `EVENT_LIVE` (FR44).
- Given mobile, when the Cockpit renders, then the lifecycle spine is a "Step N/8" bar, task cards stack full-width, and the four metric tiles render 2-up and remain tappable deep-links (FR45).
- Given the 2-column Slots sub-view on touch, when a tray session is tapped then an empty slot is tapped, then the session is assigned to that slot (drag-drop replaced by tap-to-assign on touch); conflicts surface the same alert (FR46).
- Given Registrations on mobile, when rendered, then each registrant is a card with a **⋯ overflow** exposing Resend/Cancel/Delete (FR47).
- Given the diff, when reviewed, then only `web-frontend/` changes, desktop is unchanged, new strings exist in all 10 locales, no backend call is added (NFR5/NFR9/AR9).

## Design Notes

- **Additive mobile branches** — every change is gated on `isMobile`/`variant`; desktop drag, table, and `Tabs` rail are byte-unchanged, so the beta-first risk is contained to phones.
- **`assignSessionToSlot` extraction** is the crux of G.3: one assignment path for both drag and tap means tap-to-assign inherits the exact timing-resolve + conflict handling already proven for drag — no contract divergence.
- **G.2/G.4 are mostly verification** — the responsive primitives already exist; this story closes the two genuine gaps (`compact` wiring, ⋯ overflow) and locks them with tests.

## Verification

**Commands:**
- `cd web-frontend && npm run type-check` — no errors.
- `cd web-frontend && npx vitest run src/components/organizer/EventPage src/components/SlotAssignment` — EventPage/Cockpit/Registrations/Slots suites green.
- `cd web-frontend && npm run lint` — passes.
- new mobile/tap keys present in all 10 `events.json`.

**Manual checks (375px viewport):**
- Bottom bar shows 4 + ⋯; More sheet lists Publishing/Wrap-up/Details with Wrap-up locked early. Cockpit shows Step N/8 + 2-up tiles. Slots: tap a session → empty cells light up → tap one places it. Registrations cards show a ⋯ menu with all 3 actions.
