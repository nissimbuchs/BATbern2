---
title: 'Epic 14 Story 14.F.4 — Publishing layout to match prototype (+ Settings FR43 verified)'
type: 'feature'
created: '2026-06-14'
baseline_commit: '1348c610'
status: 'done'
context:
  - '{project-root}/docs/prd/epic-14-event-detail-redesign.md'
---

## Intent

**Problem:** Publishing (FR35) was functionally complete (validation, phases timeline, gated publish, live preview, and the Phase A `publishingReady` badge) but the **layout** didn't match the redesign prototype (`#p-publishing`) — it stacked four boxes vertically, so the publish buttons sat far below a tall timeline with a lot of empty void. Settings (FR43) needed verification.

**Approach:** Frontend-only layout recompose of `EventPublishingTab` (children reused unchanged). Settings (FR43) verified already-complete — no work.

## Code Map

- `web-frontend/src/components/organizer/EventPage/EventPublishingTab.tsx` — top row is now a **3-column grid, 40/40/20** (`minmax(0,2fr) minmax(0,2fr) minmax(0,1fr)`), held at 3 columns even when narrow; `alignItems: stretch` + each column a flex-column so the three cards are **equal height**; each column has a title (`publishing.sections.*`) above a `<Paper sx={{ p:2, flexGrow:1 }}>` card; live preview full-width below.
- `web-frontend/src/components/Publishing/PublishingControls/PublishingControls.tsx` — the three publish buttons stack **vertically, full-width, `size="small"`, left-aligned content** (`justifyContent: flex-start` so the icons line up); internal "Publish Phases" heading removed (the parent provides the column title).
- `web-frontend/src/components/Publishing/PublishingTimeline/PublishingTimeline.tsx` — stepper connector `minHeight 40px → 12px` (removes the inter-phase void).
- `web-frontend/src/components/Publishing/ValidationDashboard/ValidationDashboard.tsx` — root `Paper` gets `flexGrow:1` so the Validation card fills its column to equal height.
- `web-frontend/public/locales/{10}/events.json` — added `publishing.sections.{validation,phases,publishNext}` ("Validation" / "Publishing phase" / "Publish next phase") in all 10 locales.

All four Publishing children are used only by `EventPublishingTab` (verified) — safe to adjust.

## Tasks & Acceptance

**Execution:**
- [x] `EventPublishingTab.tsx` — 3-col 40/40/20 grid, equal-height flex cards, per-column titles + Paper wrappers, full-width live preview.
- [x] `PublishingControls.tsx` — vertical, full-width, small, left-aligned buttons; removed redundant internal heading.
- [x] `PublishingTimeline.tsx` — tightened connector gap.
- [x] `ValidationDashboard.tsx` — `flexGrow:1` for equal height.
- [x] `events.json` ×10 — `publishing.sections.*` titles.

**Acceptance Criteria:**
- Given the Publishing tab, when it renders, then Validation · Publishing phase · Publish next phase show as three equal-height cards (40/40/20) with a full-width live preview below, matching the prototype (FR35).
- Given Settings, when reviewed, then FR43 is already satisfied (moderator, capacity ≤ venue, Session Q&A, teaser images, danger zone; logistics absent — moved to Communications) — verify-only, no change this story.
- Given the diff, when reviewed, then it touches only `web-frontend/` and adds no backend change (NFR9/AR9); new strings are in all 10 locales (NFR5).

## Verification

**Commands:**
- `cd web-frontend && npm run type-check` -- expected: no errors.
- `cd web-frontend && npx vitest run src/components/Publishing src/components/organizer/EventPage` -- expected: green.
- `cd web-frontend && npm run lint` -- expected: passes.

**Manual checks:**
- Publishing tab shows three equal-height grey cards (40/40/20), stacked left-aligned full-width publish buttons, compact phase timeline, full-width preview below.

## Notes

- **Settings cancel-event is a `console.log` stub** (`EventSettingsTab.handleCancelEvent`) — pre-existing, not introduced here. Flagged in `deferred-work.md` for a focused follow-up; FR43's "Cancel event (notifies registrants)" should wire a real call.
