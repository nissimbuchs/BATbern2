---
title: 'Epic 14 Story 14.F.1 — Wrap-up tab (photos + thank-you notes, stacked)'
type: 'feature'
created: '2026-06-14'
baseline_commit: '0d3245dd'
status: 'done'
context:
  - '{project-root}/docs/prd/epic-14-event-detail-redesign.md'
  - '{project-root}/_bmad-output/project-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The Wrap-up tab (`EventWrapupContainer`, Phase A interim) hides Photos and Thank-you notes behind an internal **sub-tab switch**. The redesign prototype (`#p-wrapup`) shows them as **two stacked sections in one panel** (📸 Photos card + 💌 Thank-you notes card), so an organizer sees both at once. FR36 (photos) and FR37 (thank-you notes + ★-feature-disabled-for-anonymous) are already fully implemented in the child components — only the container layout is wrong.

**Approach:** Frontend-only recompose (AR9): drop the sub-tab `Tabs` switch and stack the existing `EventPhotosTab` + `EventAppreciationTab` (both unchanged) in one scrolling panel separated by a divider. No backend, no child rewrite. Locking (🔒 until `EVENT_LIVE`) already lives on the tab rail via the relevance map — unchanged. The "slides are online" send stays a Cockpit task → Communications and is NOT added here.

## Boundaries & Constraints

**Always:**
- Frontend-only; touch only `web-frontend/`. Reuse the children, hooks, and services unchanged (AR9/NFR9). `@/` alias, generated types, `useTranslation`, no `process.env` (NFR8).
- Both sections keyboard-navigable + screen-reader-labelled; each child already renders an `<h6>` heading (`photos.title` / `appreciation.title`) — preserve that landmark structure (NFR7).

**Ask First:**
- If this seems to need any backend/API/migration change → HALT (it must not; beta-first NFR9).

**Never:**
- Don't rewrite `EventPhotosTab` or `EventAppreciationTab` — they already deliver FR36/FR37 (upload/grid/delete-confirm; per-note quote+author, ★ Feature toggle `disabled={anonymous}` with the `cannotFeatureAnonymous` tooltip).
- Don't add the "slides are online" send here (FR37 — it's a Cockpit task).
- Don't touch the relevance-map lock (Phase A owns it). No mobile reshape (Phase G).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Wrap-up tab renders (active) | `workflowState >= EVENT_LIVE`, tab mounted | both sections show stacked: Photos above, Thank-you notes below, divider between; no sub-tab switch | child loading/error states unchanged |
| Sub-tab switch removed | — | `wrapup-subtab-photos` / `wrapup-subtab-appreciation` no longer rendered | — |
| Anonymous thank-you note | note with no `thankedByUsername` | ★ Feature toggle disabled + tooltip (unchanged child behaviour, just now visible alongside Photos) | — |

</frozen-after-approval>

## Code Map

- `web-frontend/src/components/organizer/EventPage/EventWrapupContainer.tsx` — remove the `Tabs`/`subView` switch + `wrapup-subtab-*` tabs; render `<EventPhotosTab>` and `<EventAppreciationTab>` stacked in a `Stack` with a `Divider`. Drop now-unused `useState`/`Tabs`/`Tab` (and `useTranslation` if no longer used). `data-testid="wrapup-panel"`.
- `web-frontend/src/components/organizer/EventPage/EventPhotosTab.tsx` — **unchanged** (FR36 already met).
- `web-frontend/src/components/organizer/EventPage/EventAppreciationTab.tsx` — **unchanged** (FR37 + anonymous-disabled already met).
- `web-frontend/src/components/organizer/EventPage/__tests__/EventWrapupContainer.test.tsx` — **NEW**: both sections render together; no sub-tab switch.

## Tasks & Acceptance

**Execution:**
- [x] `EventWrapupContainer.tsx` — replaced the `Tabs`/`subView` switch with a `Stack` (spacing 4) of `EventPhotosTab` + `Divider` + `EventAppreciationTab` (children unchanged); removed the sub-tab testids + now-unused `useState`/`Tabs`/`Tab`/`useTranslation`/`Box`; added `data-testid="wrapup-panel"`.
- [x] `EventWrapupContainer.test.tsx` (new) — stubs both children; asserts both render simultaneously, the `wrapup-subtab-*` switch is gone, and the event code threads through. 2 tests green.

**Acceptance Criteria:**
- Given the Wrap-up tab is active, when it renders, then Photos and Thank-you notes are both visible stacked in one panel (no internal sub-tab switch) (FR36/FR37, UX-DR12).
- Given an anonymous thank-you note, when shown, then its ★ Feature toggle is disabled (unchanged child behaviour, now surfaced alongside Photos) (FR37).
- Given the tab rail, when `workflowState` is before `EVENT_LIVE`, then Wrap-up stays locked via the existing relevance map (FR5 — not re-implemented here).
- Given the diff, when reviewed, then it touches only `web-frontend/` and adds no backend change (NFR9/AR9).

## Verification

**Commands:**
- `cd web-frontend && npm run type-check` -- expected: no errors.
- `cd web-frontend && npx vitest run src/components/organizer/EventPage` -- expected: Wrap-up container + appreciation suites green, no regressions.
- `cd web-frontend && npx eslint src/components/organizer/EventPage/EventWrapupContainer.tsx src/components/organizer/EventPage/__tests__/EventWrapupContainer.test.tsx` -- expected: clean.

**Manual checks:**
- Open Wrap-up on an `EVENT_LIVE`+ event → Photos grid (upload/delete) and Thank-you notes (★ toggle, anonymous disabled) both visible at once; no sub-tabs.

## Suggested Review Order

- Entry point: the whole change — sub-tab switch replaced by a stacked `Photos / Divider / Thank-you notes` panel; both children unchanged.
  [`EventWrapupContainer.tsx:27`](../../web-frontend/src/components/organizer/EventPage/EventWrapupContainer.tsx#L27)
- Test: both sections render together, the `wrapup-subtab-*` switch is gone, event code threads through.
  [`EventWrapupContainer.test.tsx:1`](../../web-frontend/src/components/organizer/EventPage/__tests__/EventWrapupContainer.test.tsx#L1)
- Reference (unchanged, delivers FR37 ★-feature-disabled-for-anonymous): the thank-you note toggle.
  [`EventAppreciationTab.tsx:118`](../../web-frontend/src/components/organizer/EventPage/EventAppreciationTab.tsx#L118)
