---
title: 'Epic 14 Story 14.F.3 — Topic selection as a focused overlay from Details (pick → pin → brainstorm)'
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

**Problem:** Choosing/changing an event's topic leaves the event page entirely — the Details ▸ Info "Change topic" button navigates to the standalone `/organizer/topics?eventCode=` route (`TopicManagementPage` → `TopicBacklogManager`, a 4-column adaptive grid: filter sidebar | list/heat-map | detail panel | brainstorming). The UX spec §9 + PRD 14.F.3 (FR40/41/42, AR7) call for a **focused overlay** opened in-page from Details: pick → pin → brainstorm as one continuous setup gesture with **no page change**.

**Approach (frontend-only, AR9/NFR9 — no backend):** New `TopicSelectionOverlay` (MUI `Dialog`, `fullScreen` on mobile / `maxWidth="lg"` scrollable on desktop) that **recomposes existing machinery** — `useTopics`, `useSelectTopicForEvent`, `useSimilarTopics`, `useUserList`, `CreateTopicModal`, `SpeakerBrainstormingPanel` — into the §9 two-state layout:
- **State A (pick, 1 grid):** filters on a **horizontal top bar** (search [client-side title filter over the loaded list], category, status, sort, list/heat-map toggle, ＋New topic) + a **single card grid** of `TopicSelectionCard`s, each folding in its detail (title, staleness chip + colour-coded left border per `colorZone`, category, last-used + usage count, inline similarity warning) with card actions `Select for event · Edit · 🗑️ Delete` (delete disabled `usageCount>0`); a too-recent (red, `stalenessScore<50`) card shows "Select anyway…" + keeps the override/justification dialog.
- **State B (brainstorm, pinned):** on select the topic **pins to the top** as a confirmed banner ("✓ Topic selected · staleness · Change topic") and the body becomes `SpeakerBrainstormingPanel`; footer "Skip for now" (close) / "Continue to outreach →" (close + `?tab=speakers&view=pool`). An event that **already has** `topicCode` opens straight into State B.

Selection/override/similarity logic is **extracted** into a `useTopicSelection({eventCode,onConfirmed})` hook (mirrors `TopicDetailsPanel`'s flow exactly) so it lives once and is unit-testable. `EventInfoTab`'s "Change topic" swaps `navigate(...)` for opening the overlay; on confirm it refetches the event so the topic chip updates.

## Boundaries & Constraints

**Always:**
- Frontend-only; touch only `web-frontend/`. Reuse existing hooks/components/services (AR9/NFR9). `@/` alias, generated types, `config` (NFR8).
- New/changed strings via `useTranslation` in **all 10 locales** (NFR5), EN+DE first-class; reuse existing `topicBacklog.*` / `common:*` keys wherever they already exist (filters, dialogs, details buttons).
- Overlay keyboard-operable + labelled, focus-trapped (MUI Dialog), WCAG 2.1 AA (NFR7). `data-testid` on every interactive surface.
- No silent consequential action: `Select for event` runs the same similarity/override confirm flow as today; success/error feedback retained.

**Ask First / Never:**
- If anything needs a backend/API/migration → must NOT (HALT). Search is **client-side** over the already-loaded page (no new `search` query param).
- **Do NOT modify `TopicBacklogManager`, `TopicManagementPage`, or the `/organizer/topics` route** — they keep serving standalone topic management unchanged. The overlay is additive.
- Don't duplicate the select/override/similarity logic inline in the card — it lives in `useTopicSelection`.
- No mobile-only reshape beyond the Dialog's `fullScreen` (Phase G owns the rest).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Open from Details ▸ Info | click "Change topic" | overlay opens in-page (no route change); `topic-overlay` testid | — |
| Open, event has no topic | `event.topicCode` empty | State A (pick) renders | — |
| Open, event already has topic | `event.topicCode` set | State B (pinned banner + brainstorm) renders | — |
| Filter | category / status / sort change | re-queries `useTopics`; page resets to 1 | load fail → inline error |
| Search | type in search box | client-side filter of loaded cards by title (case-insensitive) | empty → "no topics" hint |
| Card — Select (safe) | `stalenessScore≥50`, no >0.7 similarity | commits via `useSelectTopicForEvent`; → State B | fail → inline error, stay in A |
| Card — Select (similar) | a >0.7 similarity exists | similarity confirm dialog (titles + optional justification) → commit | — |
| Card — Select (too recent) | `stalenessScore<50` ("Select anyway…") | override dialog requires justification → commit | confirm disabled until justification |
| Card — Edit / New topic | click | opens `CreateTopicModal` (edit/create); list refreshes on success | its own states |
| Card — Delete | `usageCount===0` | delete confirm → `topicService.deleteTopic`; list refreshes | disabled when `usageCount>0` |
| State B — Change topic | click banner action | returns to State A (pick) | — |
| State B — Skip / Continue | click footer | Skip closes; Continue closes + navigates `?tab=speakers&view=pool` | — |
| Confirmed | mutation success | overlay `onConfirmed(topicCode)` → parent refetches event; chip updates | — |

</frozen-after-approval>

## Code Map

- `web-frontend/src/components/organizer/EventPage/topicOverlay/useTopicSelection.ts` — **NEW** hook: `useSelectTopicForEvent` + justification state + similar/override mode + `useSimilarTopics(actingTopic)`; `requestSelect(topic)` routes safe/similar/override; `commit()` mutates and calls `onConfirmed(topicCode)`. Pure logic, fully unit-tested.
- `web-frontend/src/components/organizer/EventPage/topicOverlay/TopicSelectionCard.tsx` — **NEW**: folded-detail card (title, staleness chip + colour-coded left border via `colorZone`, category chip, last-used + usage, inline similarity warning from `similarityScores>0.7`); actions Select / "Select anyway…" / Edit / Delete (delete disabled `usageCount>0`).
- `web-frontend/src/components/organizer/EventPage/topicOverlay/TopicSelectionOverlay.tsx` — **NEW**: the Dialog. State A (top filter bar: search + category/status/sort selects reusing the same option sets as `TopicFilterPanel` + list/heat-map toggle + ＋New topic; card grid or `MultiTopicHeatMap`) + State B (pinned banner + `SpeakerBrainstormingPanel`, footer Skip/Continue). Owns `useTopics`, `useUserList` organizers, `CreateTopicModal`, the similar/override dialogs driven by `useTopicSelection`.
- `web-frontend/src/components/organizer/EventPage/EventInfoTab.tsx` — replace the "Change topic" `navigate('/organizer/topics?eventCode=')` with `setTopicOverlayOpen(true)`; mount `<TopicSelectionOverlay open eventCode currentTopicCode onClose onConfirmed={refetch} />`; keep the topic chip.
- `web-frontend/src/components/organizer/EventPage/topicOverlay/__tests__/` — **NEW** tests: `useTopicSelection` (safe/similar/override routing + commit + onConfirmed), `TopicSelectionCard` (render fields + delete-disable + action callbacks), `TopicSelectionOverlay` (State A↔B, has-topic→B, filter, search, select→pin→continue). Update `EventInfoTab` test (Change-topic opens overlay, not navigate).
- `web-frontend/public/locales/{10}/events.json` — `eventPage.topicOverlay.*` (title, pickHeading, searchPlaceholder, banner.selected, banner.change, footer.skip, footer.continue, emptyHint). Reuse `organizer:topicBacklog.*` + `common:*` for filter/dialog/card-action strings (overlay uses `useTranslation(['events','organizer','common'])`). All 10 locales.

## Tasks & Acceptance

**Execution (TDD per slice, commit when green):**
- [x] `useTopicSelection` hook + unit tests (safe → direct commit; similar → dialog; recent → override; commit calls mutation + `onConfirmed`).
- [x] `TopicSelectionCard` + tests (fields, colour border, delete-disable, callbacks).
- [x] `TopicSelectionOverlay` + tests (State A grid + top filters + search; State B pin + brainstorm; has-topic opens B; Continue navigates).
- [x] Wire `EventInfoTab` "Change topic" → overlay; update its test.
- [x] `events.json` ×10 — `eventPage.topicOverlay.*`.
- [x] type-check + lint + scoped vitest green.

**Acceptance Criteria:**
- Given Details ▸ Info, when "Change topic" is clicked, then a **focused overlay opens in-page** (no route change) (FR40, AR7).
- Given State A, when rendered, then filters are a **horizontal top bar** and topics show as a **single card grid** with folded detail + inline similarity warning, actions `Select for event · Edit · 🗑️ Delete` (delete disabled `usageCount>0`); a red topic shows "Select anyway…" + keeps the override/justification dialog (FR40/FR41).
- Given a topic is selected, when State B renders, then the topic **pins to the top** as a confirmed banner and the body becomes `SpeakerBrainstormingPanel` with footer "Skip for now" / "Continue to outreach →" landing on Speakers ▸ Pool (FR42).
- Given the event already has a topic, when the overlay opens, then it starts in State B (pinned).
- Given the diff, when reviewed, then only `web-frontend/` changes, `TopicBacklogManager`/route are untouched, new strings exist in all 10 locales, no backend call is added (NFR5/NFR9/AR9).

## Design Notes

- **Overlay is additive, route untouched** — lowest-risk beta-first path; standalone topic management still works via `/organizer/topics`.
- **`useTopicSelection` mirrors `TopicDetailsPanel`** (same similarity>0.7 + `stalenessScore<50` thresholds, same `selectTopicForEvent` request shape with optional justification) — single source of selection truth, no behaviour drift.
- **Search is client-side** by design (no `search` API param exists; staying frontend-only). Filtering the loaded page is the honest scope; documented in the empty-state hint.

## Verification

**Commands:**
- `cd web-frontend && npm run type-check` — no errors.
- `cd web-frontend && npx vitest run src/components/organizer/EventPage/topicOverlay src/components/organizer/EventPage/__tests__/EventInfoTab` — overlay/card/hook/Info suites green.
- `cd web-frontend && npm run lint` — passes.
- new `eventPage.topicOverlay.*` keys present in all 10 `events.json`.

**Manual checks:**
- Details ▸ Info → Change topic opens the overlay (no navigation). Pick a topic → it pins, brainstorm shows; Continue lands on Speakers ▸ Pool. Re-open on an event with a topic → starts pinned.
