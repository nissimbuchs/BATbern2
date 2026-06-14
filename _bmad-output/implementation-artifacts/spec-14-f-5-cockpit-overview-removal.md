---
title: 'Epic 14 Story 14.F.5 — Cockpit replaces interim Overview (remove the old Overview content)'
type: 'refactor'
created: '2026-06-14'
baseline_commit: '937ba1d8'
status: 'done'
context:
  - '{project-root}/docs/prd/epic-14-event-detail-redesign.md'
  - '{project-root}/_bmad-output/project-context.md'
---

<frozen-after-approval reason="human-owned intent — auto-accepted by Nissim 2026-06-14 (overnight autonomous run)">

## Intent

**Problem:** Phase A mounted today's `EventOverviewTab` as the interim Cockpit. Phases B/D since moved every piece elsewhere — metrics → `MetricTiles`, workflow bar → `LifecycleSpine`, identity/topic/AI → `EventInfoTab` (Details), enrol → Registrations, preview → Publishing. `EventOverviewTab` is now **orphaned**: the `cockpit` tab already renders `<CockpitTab>`, and `EventOverviewTab` survives only as the `default:` fallback case in `EventPage.renderTabContent()` (line ~333) plus its own (now-stale) test files. PRD 14.F.5 (FR3/FR7): the Cockpit must be the **sole** landing surface with **no leftover Overview** duplicating identity, metrics, topic, or quick-actions.

**Approach (frontend-only, AR9/NFR9 — pure cleanup, no behaviour change for users):**
- Delete `EventOverviewTab.tsx` and its test files (both locations).
- In `EventPage.tsx`: drop the `EventOverviewTab` import, the barrel re-export, and replace the `default:` fallback so an unknown/invalid `effectiveTab` renders `<CockpitTab>` (the Cockpit is the canonical landing — and `effectiveTab` is already normalised to a valid tab, so `default` is purely defensive). Remove the now-dead `handleEdit`/`onEdit` wiring **only if** nothing else uses it (the `EventForm` edit modal is opened from elsewhere — verify before removing).
- Update `EventPage` shell tests to drop the `EventOverviewTab` mock and assert the Cockpit is the landing/fallback.

## Boundaries & Constraints

**Always:**
- Frontend-only; touch only `web-frontend/`. No backend, no API, no i18n key additions (removal only).
- Keep the build green: every `EventOverviewTab` import/reference removed in the same change.
- Preserve the `EventForm` edit modal path — confirm whether `handleEdit`/`openEditModal` is still reachable (it is opened by the Cockpit/other surfaces); if `handleEdit` becomes unused after removing the `onEdit` prop, delete it; if still used, keep it.

**Ask First / Never:**
- If removing `EventOverviewTab` breaks a still-needed surface (e.g. a route other than the default fallback imports it) → HALT and report (don't force).
- Don't touch `CockpitTab` / `LifecycleSpine` / `AttentionList` / `MetricTiles` internals — they already own the Cockpit (Phase B). This story only removes the orphan.
- Don't change tab IDs, the relevance map, or any other tab's content.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Land on page | no `?tab=` | renders `<CockpitTab>` (unchanged) | — |
| Stale/invalid `?tab=` | unknown value | `effectiveTab` normalises → Cockpit; `default:` also renders `<CockpitTab>` | — |
| `EventOverviewTab` references | grep after change | zero references in `src/` (component + barrel + tests gone) | build fails if any remain |
| `handleEdit` / `onEdit` | after removal | if unused → removed; if still used by another surface → kept | type-check catches dangling refs |
| Cockpit content | any state | identity/metrics/topic/quick-actions appear ONLY via Phase B/Details — never duplicated | — |

</frozen-after-approval>

## Code Map

- `web-frontend/src/components/organizer/EventPage/EventOverviewTab.tsx` — **DELETE**.
- `web-frontend/src/components/organizer/EventPage/EventOverviewTab.test.tsx` and `__tests__/EventOverviewTab.test.tsx` — **DELETE** (both stale-orphan locations).
- `web-frontend/src/components/organizer/EventPage/EventPage.tsx` — remove the `import { EventOverviewTab }`; `default:` case returns `<CockpitTab event={event} eventCode={eventCode!} onNavigate={handleCardNavigate} />`; drop the trailing "(default delegates to the Cockpit's Overview content…)" comment; remove `handleEdit`/`onEdit` only if unused after this change (verify `openEditModal` callers).
- `web-frontend/src/components/organizer/EventPage/index.ts` — remove the `EventOverviewTab` re-export.
- `web-frontend/src/components/organizer/EventPage/__tests__/EventPage.test.tsx` — remove the `EventOverviewTab` mock; keep/adjust the "lands on the Cockpit by default" assertion; add an "invalid ?tab= falls back to Cockpit" assertion.

## Tasks & Acceptance

**Execution (TDD-lite — adjust tests first, then delete + rewire, keep green):**
- [x] Update `EventPage.test.tsx`: drop `EventOverviewTab` mock; assert Cockpit is default + invalid-tab fallback.
- [x] `EventPage.tsx`: `default:` → `<CockpitTab>`; remove import + dead `onEdit`/`handleEdit` (if unused).
- [x] Delete `EventOverviewTab.tsx` + both test files; remove barrel export.
- [x] grep `EventOverviewTab` → zero hits in `src/`.
- [x] type-check + lint + scoped vitest green.

**Acceptance Criteria:**
- Given Phase F completes, when the page renders any landing/invalid-tab state, then the **Cockpit (Phase B) is the only landing tab** and no duplicated identity/metrics/topic/quick-actions remain (FR3, FR7).
- Given the codebase, when grepped, then `EventOverviewTab` has **zero references** in `web-frontend/src/`.
- Given the diff, when reviewed, then only `web-frontend/` changes, no i18n keys added, the `EventForm` edit modal path still works, and the suite is green (NFR9/AR9).

## Design Notes

- **Pure removal of an orphan** — the migration was 95% done by Phases B/D; this is the last 5% (delete the dead component + its only remaining reference, the defensive fallback).
- `effectiveTab` is already clamped to a valid `TabId`, so `default:` is unreachable in practice; pointing it at `<CockpitTab>` removes the last dependency on `EventOverviewTab` while keeping a sane defensive branch.

## Verification

**Commands:**
- `cd web-frontend && npm run type-check` — no errors (catches any dangling `EventOverviewTab`/`onEdit` ref).
- `cd web-frontend && grep -rn "EventOverviewTab" src/` — zero hits.
- `cd web-frontend && npx vitest run src/components/organizer/EventPage/__tests__/EventPage src/components/organizer/EventPage/cockpit` — shell + Cockpit suites green.
- `cd web-frontend && npm run lint` — passes.

**Manual checks:**
- Open an event → Cockpit lands. Force `?tab=bogus` → Cockpit renders. No second "Overview" surface anywhere.
