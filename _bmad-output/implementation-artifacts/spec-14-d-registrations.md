---
title: 'Epic 14 Phase D — Registrations tab (recompose to the lifecycle IA)'
type: 'feature'
created: '2026-06-14'
status: 'done'
baseline_commit: '326417a5'
context:
  - '{project-root}/docs/prd/epic-14-event-detail-redesign.md'
  - '{project-root}/_bmad-output/project-context.md'
---

## Intent

**Problem:** The Registrations tab (`EventParticipantsTab`) pre-dates Epic 14. Most of Phase D already exists (count chip, capacity bar with red-when-full, XLSX/DOCX exports, debounced search, status filter, row actions, server-side pagination). What's missing is the FR24–FR28 recomposition: the tab isn't badged in the rail, the "Waitlisted" filter is broken (sends `'WAITLISTED'`, an invalid status → returns nothing, which is why a *separate* `WaitlistSection` accordion exists), the status filter is a radio not a segmented control, "Enrol organizers & partners" lives on the interim Overview, and the page has no "Showing X–Y of Z" counter.

**Approach:** Frontend-only recompose of the existing components (AR9 — reuse hooks/services/exports unchanged): fix the waitlist status value and fold the waitlist accordion into the main list as the "Waitlisted" filter (queue-ordered rows + inline Promote-with-confirm), swap the radio for a segmented control, move the Enrol action into the Registrations header, add the pagination counter, and surface a *muted* registrations count on the tab rail. No backend change — all data sources (`useEventRegistrations`, `promoteFromWaitlist`, `enrollStakeholders`, the `totalItems` pagination field, the API's existing 1-based `waitlistPosition`) already exist.

## Boundaries & Constraints

**Always:**
- Frontend-only; touch only `web-frontend/`. Reuse existing services/hooks; add the optional `waitlistPosition?: number` field to the local `EventParticipant` type additively (NFR9/AR9).
- Service layer only (no direct `fetch`/`axios`), `@/` alias, generated types, `config` objects (NFR8).
- Every new/changed user-facing string via `useTranslation('events')` in **all 10 locales** (`de,en,fr,it,rm,es,fi,nl,ja,gsw-BE`), EN+DE first-class (NFR5).
- New interactive surfaces (segmented control, inline Promote, muted tab count) are keyboard-operable + ARIA-labelled, WCAG 2.1 AA (NFR7).
- **Promote sends a consequential email → it MUST open a confirm dialog before the call** (NFR1). Tests use no real recipients.

**Ask First:**
- If any change would require a backend/API/migration edit (it should not), HALT — the beta-first dual-serve constraint forbids it.

**Never:**
- No mobile reshape (tables→cards, bottom nav) — that is Phase G, out of scope. The existing `EventParticipantTable` mobile card view stays as-is.
- No new backend endpoint, param, or field. No virtualization library (server-side pagination already covers NFR3).
- Don't rewrite working pieces (exports, capacity bar, row-actions menu, debounced search, the `useEventRegistrations`/store wiring) — recompose only.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Waitlisted filter selected | segmented control → "Waitlisted" | filter sends `status: ['WAITLIST']`; main list shows queue-ordered rows (#1, #2 … from `waitlistPosition`, else page-offset+index+1) with an inline **Promote** action | list error → existing inline error + retry |
| Promote a waitlisted row | click Promote | a **confirm dialog** opens first; on confirm calls `promoteFromWaitlist`, invalidates queries, queue re-numbers | failed promote → inline error/snackbar, row stays, queue unchanged |
| Pagination counter | `totalItems=140, page=1, limit=25` | renders "Showing 1–25 of 140" | — |
| Counter — empty / partial | `totalItems=0` / `totalItems=7,limit=25` | "Showing 0 of 0" / "Showing 1–7 of 7"; no empty trailing page | — |
| Filter/search change while on page>1 | active filter changes | store resets to page 1 (already wired); counter recomputes | — |
| Enrol organizers & partners | click header action | calls `enrollStakeholders`, snackbar with enrolled/skipped (moved verbatim from Overview) | failure → error snackbar |
| Tab rail count | `confirmedCount+waitlistCount=140` | Registrations tab shows a **muted** count "140" (visually lighter than the speakers count badge — not a coloured Badge bubble); 0 → no count | — |

## Code Map

- `web-frontend/src/components/organizer/EventPage/EventParticipantFilters.tsx` — radio→`ToggleButtonGroup` (segmented, FR26); **fix `'WAITLISTED'`→`'WAITLIST'`** (enables FR28).
- `web-frontend/src/components/organizer/EventPage/EventParticipantTable.tsx` — waitlist mode: leading `#` position column + inline Promote (with confirm). Detect mode from active status filter.
- `web-frontend/src/components/organizer/EventPage/EventParticipantList.tsx` — render "Showing X–Y of Z" from `data.pagination.totalItems`/page/limit (boundaries); pass active-status + promote wiring to the table.
- `web-frontend/src/components/organizer/EventPage/EventParticipantsTab.tsx` — remove `<WaitlistSection>`; add **Enrol organizers & partners** to the header action row (beside exports).
- `web-frontend/src/components/organizer/EventPage/EventOverviewTab.tsx` — remove the enroll button + `handleEnrollStakeholders` + its snackbar (moved to Registrations; FR25/FR39).
- `web-frontend/src/components/organizer/EventPage/WaitlistSection.tsx` (+ test) — **delete** (folded into the list).
- `web-frontend/src/components/organizer/EventPage/tabBadges.ts` + `useTabBadges.ts` — add `registrations: number` to `TabBadges` (= `confirmedCount + waitlistCount`, passed from event in the hook).
- `web-frontend/src/components/organizer/EventPage/EventPage.tsx` — `tabBadge('registrations')` renders the muted count (distinct rendering from the primary count badges).
- `web-frontend/src/types/eventParticipant.types.ts` — add `waitlistPosition?: number` (additive).
- `web-frontend/public/locales/{10}/events.json` — counter + waitlist-position + promote-confirm keys; reuse existing `eventPage.overview.enrollStakeholders*` + waitlist Promote keys.

## Tasks & Acceptance

**Execution:**
- [x] `EventParticipantFilters.tsx` — replaced `RadioGroup` with a single-select `ToggleButtonGroup` (same options/handler); fixed the Waitlisted value to `'WAITLIST'`; kept debounced search; ARIA-labelled the group.
- [x] `eventParticipant.types.ts` — added optional `waitlistPosition?: number | null`.
- [x] `EventParticipantTable.tsx` — waitlist mode: `#` position column + inline Promote button (delegates to parent `onPromote`, which gates a confirm dialog). Position from `waitlistPosition ?? pageOffset+index+1`, queue-ordered.
- [x] `EventParticipantList.tsx` — added the "Showing X–Y of Z" summary (empty/partial boundaries); threaded waitlist mode + the promote-confirm flow (owns `promoteFromWaitlist` + invalidation + snackbar).
- [x] `EventParticipantsTab.tsx` — removed the `<WaitlistSection>` render; added the Enrol action to the header (reused `enrollStakeholders` + snackbar moved from Overview).
- [x] `EventOverviewTab.tsx` — removed the enroll button/handler/state/import/snackbar (left "Preview public page" for Phase F).
- [x] deleted `WaitlistSection.tsx` (no test existed).
- [x] `tabBadges.ts`/`useTabBadges.ts`/`EventPage.tsx` — added `registrations` count + the subtle muted inline render (distinct from the primary count/dot badges).
- [x] `events.json` ×10 — added `participantList.showing`/`showingEmpty` + `participantsTab.promoteConfirmTitle`/`promoteConfirmMessage`; reused existing `waitlistPromote*` / `enrollStakeholders*` / `actions.cancel`.
- [x] tests — filters (segmented + `'WAITLIST'` value + All-clears), table (waitlist position, offset fallback, promote delegates not auto-commit, hidden outside mode), list (counter boundaries + promote-confirm gate), tabBadges (registrations count incl. 0), participantsTab (Enrol present + click + no accordion).

**Acceptance Criteria:**
- Given the Registrations tab in any state, when the rail renders, then the tab shows a muted count = active total (FR24); 0 → no count.
- Given the header, when it renders, then it shows the count chip + capacity bar (red when full) + XLSX/DOCX exports + **Enrol organizers & partners**, and Enrol no longer appears on the Overview/Cockpit content (FR25/FR39).
- Given the status segmented control, when "Waitlisted" is selected, then the main list swaps to queue-ordered rows with inline Promote, the separate accordion is gone, and Promote requires confirmation before promoting + re-numbers the queue (FR26/FR28/NFR1).
- Given ~200 registrants, when the list renders, then server-side pagination shows "Showing 1–25 of 140" with correct empty/partial/last-page counts and page-resets-to-1 on filter/search change (NFR3/FR D.4).
- Given the diff, when reviewed, then it touches only `web-frontend/` and introduces no backend change (NFR9/AR9).

## Verification

**Commands:**
- `cd web-frontend && npm run type-check` -- expected: no errors (new `waitlistPosition` field + props typecheck).
- `cd web-frontend && npx vitest run src/components/organizer/EventPage` -- expected: all Registrations/tabBadges suites green.
- `cd web-frontend && npm run lint -- --max-warnings 50` -- expected: passes.
- `node web-frontend/scripts/i18n/<locale-completeness check>` or manual diff -- expected: new keys present in all 10 `events.json`.

**Manual checks:**
- Select "Waitlisted" on an event with a waitlist → queue-ordered rows + Promote (confirm) appear; other filters show the normal table.
- Tab rail shows a muted "140" on Registrations; Enrol button sits in the header; no waitlist accordion below the list.

## Suggested Review Order

**Waitlist-as-filter (FR28 — the core change + the review-found fix)**

- Entry point: the "Waitlisted" filter drives queue mode + the promote-confirm flow this component owns.
  [`EventParticipantList.tsx:59`](../../web-frontend/src/components/organizer/EventPage/EventParticipantList.tsx#L59)
- The load-bearing review fix: backend `waitlistPosition` is now carried through the transform (was dropped → numbering was page-index, not the real queue).
  [`eventRegistrationService.ts:60`](../../web-frontend/src/services/api/eventRegistrationService.ts#L60)
- Queue ordering + position fallback + inline Promote button in the table.
  [`EventParticipantTable.tsx:122`](../../web-frontend/src/components/organizer/EventPage/EventParticipantTable.tsx#L122)
- Promote opens a confirm dialog before the consequential call (NFR1); double-submit + empty-page guards.
  [`EventParticipantList.tsx:70`](../../web-frontend/src/components/organizer/EventPage/EventParticipantList.tsx#L70)
- The "Waitlisted" segment now sends the canonical `WAITLIST` (was the dead `WAITLISTED`).
  [`EventParticipantFilters.tsx:52`](../../web-frontend/src/components/organizer/EventPage/EventParticipantFilters.tsx#L52)

**Segmented status control (FR26)**

- RadioGroup → exclusive ToggleButtonGroup, null-on-reclick preserved, ARIA-labelled.
  [`EventParticipantFilters.tsx:15`](../../web-frontend/src/components/organizer/EventPage/EventParticipantFilters.tsx#L15)

**Pagination counter (FR D.4 / NFR3)**

- "Showing X–Y of Z" derived from the FETCHED page (avoids the keepPreviousData lag) with empty/partial guards.
  [`EventParticipantList.tsx:148`](../../web-frontend/src/components/organizer/EventPage/EventParticipantList.tsx#L148)

**Header relocation (FR25 / FR39 cross-ref)**

- Enrol organizers & partners added to the Registrations header (reuses the moved handler).
  [`EventParticipantsTab.tsx:46`](../../web-frontend/src/components/organizer/EventPage/EventParticipantsTab.tsx#L46)
- Enrol removed from the interim Overview (Preview-public left for Phase F).
  [`EventOverviewTab.tsx:424`](../../web-frontend/src/components/organizer/EventPage/EventOverviewTab.tsx#L424)

**Muted tab count (FR24)**

- Subtle inline count on the Registrations tab — distinct from the primary count/dot badges.
  [`EventPage.tsx:293`](../../web-frontend/src/components/organizer/EventPage/EventPage.tsx#L293)
- Count computed as confirmed + waitlist (frontend only).
  [`tabBadges.ts:93`](../../web-frontend/src/components/organizer/EventPage/tabBadges.ts#L93)

**Peripherals**

- Additive optional type field (NFR9/AR9-safe).
  [`eventParticipant.types.ts:43`](../../web-frontend/src/types/eventParticipant.types.ts#L43)
- Tests: filters, table (waitlist mode), list (counter + promote-confirm), service (waitlistPosition map), tabBadges, participantsTab; + 4 new i18n keys ×10 locales.
