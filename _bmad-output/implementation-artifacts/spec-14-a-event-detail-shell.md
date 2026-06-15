---
title: 'Epic 14 Phase A — Lifecycle-aware 8-tab event-page shell'
type: 'refactor'
created: '2026-06-13'
status: 'done'
baseline_commit: '0bb6c4116498b3b6fb96e889cf86311ef1227a26'
context:
  - '{project-root}/docs/prd/epic-14-event-detail-redesign.md'
  - '{project-root}/docs/ux/event-detail-redesign-spec.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The organizer event-detail page (`EventPage.tsx`) is 10 flat tabs that show
everything regardless of where the event is in its lifecycle. Organizers run ~3 events/year with no
muscle memory, so on every return they must re-derive what matters. There is no notion of "which
tabs are relevant for this state" and no way to see where work is waiting without opening each tab.

**Approach:** Phase A of Epic 14 — the **foundation + shell**, frontend-only, recompose-not-rewrite.
Three ordered pieces: (1) author a single declarative `workflowState → relevance` map + a
`getTabRelevance` helper in the existing `workflowState.ts`; (2) rebuild `EventPage.tsx` from 10 flat
tabs into the **8-tab lifecycle-aware IA** (work cluster `Cockpit · Speakers & Agenda · Registrations
· Communications · Publishing · Wrap-up`, then config cluster `Details · Settings`), mounting today's
existing components into the new slots with unchanged content and rendering dim/lock states from the
map; (3) add React-Query hooks over the existing `taskService` and drive count-based attention badges
on the rail. No backend changes. The interim Cockpit is today's Overview content; Phases B–F replace
each tab's internals later.

## Boundaries & Constraints

**Always:**
- Target the shipped **8 workflow states** only — `CREATED, TOPIC_SELECTION, SPEAKER_IDENTIFICATION,
  SLOT_ASSIGNMENT, AGENDA_PUBLISHED, EVENT_LIVE, EVENT_COMPLETED, ARCHIVED`. There is **no
  `AGENDA_FINALIZED`** — do not introduce it (AR1). The "finalized" emphasis (final newsletter /
  catering / print agenda) is attributed to `AGENDA_PUBLISHED`.
- Frontend-only. Reuse existing components/hooks/services; change arrangement, not substance (AR9).
- Service layer only (no `fetch`/`axios` in components); generated types from `src/types/generated/`;
  `config` objects (no `process.env`); `@/` alias; all strings via `useTranslation()` (NFR8).
- Every new/changed user-facing string ships in **all 10 locales** (`de,en,fr,it,rm,es,fi,nl,ja,
  gsw-BE`), EN+DE first-class (NFR5).
- New interactive surfaces (tab dim/lock, badges) are keyboard-operable + screen-reader-labelled,
  WCAG 2.1 AA (NFR7). Locked tabs are `aria-disabled` and non-focusable.
- `Cockpit`, `Details`, and `Settings` are **active in every state**. `Wrap-up` is **locked**
  (dimmed + 🔒 + non-interactive) for `CREATED…AGENDA_PUBLISHED`, active from `EVENT_LIVE` onward.

**Ask First:**
- If any badge ("ready to publish", "needs slot/review", "overdue comms") cannot be computed from
  existing client data without a backend change — HALT (a backend change breaks the frontend-only /
  dual-serve constraint NFR9).
- If retiring/consolidating an old tab would orphan an in-app navigator or external bookmark — HALT
  (route/navigation retirement is a Phase C concern, not Phase A).

**Never:**
- No backend change; no migration; no removed/renamed API field; nothing the old `www` frontend
  can't tolerate (NFR9 — beta serves the same prod backend as www).
- Do not build Cockpit internals (spine widget, task cards, metric tiles), the 4-phase kanban,
  in-tab Slots, the Communications 4-audience form, the Topic overlay, or any mobile reshape — those
  are Phases B–G (deferred). Phase A only establishes the 8 slots + relevance + badges, mounting
  existing components as-is.
- Do not remove the `/slot-assignment` route or any other route here.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Wrap-up locked early | `getTabRelevance('CREATED','wrapup')` | `'locked'` | N/A |
| Wrap-up unlocks | `getTabRelevance('EVENT_LIVE','wrapup')` | `'active'` | N/A |
| Always-on tabs | `getTabRelevance(<any state>, 'cockpit'\|'details'\|'settings')` | `'active'` | N/A |
| Unknown state | `getTabRelevance('BOGUS','wrapup')` | safe default `'active'` (never throw / never hide a tab) | log-free fallback |
| Locked tab click | user clicks a `'locked'` tab | no tab switch, no URL change; stays on current tab | N/A |
| Speakers badge | sessions needing a slot or content review = N (>0) | rail shows count badge `N` on Speakers & Agenda | N/A |
| Speakers badge zero | that count = 0 | no badge on the tab | N/A |
| Comms dot | an overdue comms task exists | red dot on Communications | N/A |
| Publishing badge | a publishing phase passes validation & is ready | badge on Publishing | N/A |
| Task fetch fails | `useEventTasks`/`useMyTasks` error | rail still renders; affected count-badges simply absent (no crash, no error UI on the rail) | swallow → no badge |

</frozen-after-approval>

## Code Map

- `web-frontend/src/utils/workflow/workflowState.ts` -- has `WORKFLOW_STATE_ORDER` (8 states) +
  `getWorkflowProgress`/`getWorkflowStepNumber`/`isEarlyStage`/`isLateStage`/`getProgressColor`/
  `getWorkflowStateLabel`/`isValidWorkflowState`. **No relevance map.** ⚠️ JSDoc ~line 173 still says
  "9 valid workflow states" — stale, must be corrected to 8.
- `web-frontend/src/components/organizer/EventPage/EventPage.tsx` -- the `TABS` array (10 entries,
  `{id,labelKey,icon}`), `renderTabContent` switch, `?tab={id}` query-param selection, MUI `Tabs`
  (desktop) + `BottomNavigation` (mobile), `event.title` header. `data-testid="event-tab-{id}"`.
- `web-frontend/src/components/organizer/EventPage/Event{Overview,Speakers,Venue,Participants,
  Publishing,Newsletter,RegistrantNotices,Settings,Photos,Appreciation}Tab.tsx` -- existing tab
  bodies to recompose into the 8 slots (eager-imported today).
- `web-frontend/src/components/organizer/EventManagement` (`EventForm`) -- identity edit surface,
  currently a modal; mounted in-tab as Details (interim).
- `web-frontend/src/services/taskService.ts` -- `listEventTasks(eventCode)` (GET
  `/events/{code}/tasks`), `getMyTasks(critical?)` (GET `/tasks/my-tasks?critical=`). **No RQ hooks.**
- `web-frontend/src/hooks/useEvents.ts` -- `useEvent(eventCode, include[])` (supports
  `metrics`/`registrations`/`workflow`/`sessions`); existing unimplemented `useCriticalTasks()`
  placeholder. New task hooks land here or a sibling hooks file.
- `web-frontend/src/components/organizer/EventManagement/WorkflowProgressBar.tsx` -- ⚠️ JSDoc ~line
  10 also says "Step X/9" — correct to 8.
- `web-frontend/public/locales/{de,en,fr,it,rm,es,fi,nl,ja,gsw-BE}/events.json` -- `eventPage.tabs.*`
  labels; add `cockpit`, `speakersAgenda`, `communications`, `wrapup`, `details` + lock/badge aria.

## Tasks & Acceptance

**Execution:**

- [x] `web-frontend/src/utils/workflow/workflowState.ts` -- **(Story 14.A.1)** add a declarative
  `WORKFLOW_RELEVANCE` table — exactly **8 rows** keyed by the 8 states — each mapping to
  `{ cockpitEmphasis: string; focusTabs: TabId[]; dimmedTabs: TabId[]; lockedTabs: TabId[] }`, plus
  `getTabRelevance(state, tabId): 'active' | 'dimmed' | 'locked'`. Wrap-up locked `CREATED…
  AGENDA_PUBLISHED`, active `EVENT_LIVE`+; Cockpit/Details/Settings always active; unknown state →
  `'active'` default. Define/export a `TabId` union for the 8 tabs. **Also fix the stale "9"→"8"
  JSDoc** here and in `WorkflowProgressBar.tsx`.
- [x] `web-frontend/src/utils/workflow/workflowState.test.ts` -- unit-test the map: assert every
  state→tab pairing for all 8×8 combinations, the always-active trio, the Wrap-up lock boundary, the
  unknown-state default, and that the table has exactly 8 rows with no `AGENDA_FINALIZED`. (I/O Matrix rows 1–5.)
- [x] `web-frontend/src/components/organizer/EventPage/EventPage.tsx` -- **(Story 14.A.2)** replace
  the 10-entry `TABS` with **8 tabs** in order — `Cockpit · Speakers & Agenda · Registrations ·
  Communications · Publishing · Wrap-up` (work) then `Details · Settings` (config), visually grouped
  with config last and the `event.title` persistent header retained. Update `renderTabContent` to
  mount existing components: Cockpit→`EventOverviewTab` (interim), Speakers & Agenda→`EventSpeakersTab`,
  Registrations→`EventParticipantsTab`, Communications→a thin container holding `EventNewsletterTab` +
  `EventRegistrantNoticesTab` + `EventVenueTab`, Publishing→`EventPublishingTab`, Wrap-up→a container
  holding `EventPhotosTab` + `EventAppreciationTab`, Details→`EventForm` shown in-tab, Settings→
  `EventSettingsTab`. Drive dim/lock from `getTabRelevance(event.workflowState, tab.id)`: dimmed =
  de-emphasized; locked = dimmed + 🔒 + non-interactive + `aria-disabled`. Keep stable `?tab=` keys +
  `data-testid="event-tab-{id}"`.
- [x] `web-frontend/src/components/organizer/EventPage/EventCommunicationsContainer.tsx`,
  `EventWrapupContainer.tsx` -- new thin **container** components that stack the existing tab bodies
  (no content change). Mounted by the Communications / Wrap-up slots; Phases E/F replace internals.
- [x] `web-frontend/src/hooks/useEvents.ts` (or sibling) -- **(Story 14.A.3)** add `useEventTasks(eventCode)`
  and `useMyTasks({critical})` React-Query hooks wrapping `taskService.listEventTasks` /
  `taskService.getMyTasks` (frontend only — no backend change). Implement/replace the
  `useCriticalTasks` placeholder if it conflicts.
- [x] `web-frontend/src/components/organizer/EventPage/EventPage.tsx` (+ a small `useTabBadges`
  selector) -- compute count-driven rail badges from `useEvent(['metrics','sessions'])` + the task
  hooks + the existing publishing-validation predicate: **Speakers & Agenda** = sessions needing a
  slot or a content review; **Publishing** = a phase ready to publish; **Communications** = red dot
  when a comms task is overdue. Zero → no badge. (I/O Matrix rows 6–10.) The "ready to publish"
  signal reuses the Publishing tab's existing client-side validation — extract it to a shared
  selector; if it can't be derived client-side without backend, HALT (Ask First).
- [x] `web-frontend/public/locales/<10 locales>/events.json` -- add the new tab labels
  (`cockpit`, `speakersAgenda`, `communications`, `wrapup`, `details`) + aria text for locked tabs
  ("locked until the event is live") and badge labels, across all 10 locales (EN+DE first-class).
- [x] `web-frontend/src/components/organizer/EventPage/__tests__/EventPage.test.tsx` -- extend: the 8
  tabs render in the two clusters; the title persists; a locked Wrap-up (pre-`EVENT_LIVE`) is
  non-interactive (click does not switch); old standalone tabs (`venue`, `newsletter`,
  `registrant-notices`, `photos`, `appreciation`, `overview`) are no longer top-level; badges appear
  for non-zero counts and vanish at zero; rail still renders when the task fetch errors.

**Acceptance Criteria:**
- Given the 8-state map, when `getTabRelevance` is exercised for all 8 states, then Wrap-up is
  `locked` through `AGENDA_PUBLISHED` and `active` from `EVENT_LIVE`; Cockpit/Details/Settings are
  `active` everywhere; the table has exactly 8 rows and no `AGENDA_FINALIZED` (FR4/FR5/AR1).
- Given `EventPage.tsx`, when it renders, then exactly 8 tabs show in the work-then-config cluster
  order, the event title stays in the header on every tab, and the 6 consolidated standalone tabs no
  longer appear as top-level tabs (FR1/FR2/FR3).
- Given a state where a tab is locked, when the organizer attempts to open it, then it is dimmed+🔒,
  `aria-disabled`, and the click is a no-op (FR5/NFR7).
- Given event metrics + task hooks, when the rail renders, then Speakers & Agenda shows a count
  badge for sessions needing a slot/review, Publishing badges when a phase is ready to publish, and
  Communications shows a red dot for an overdue comms task — and each is absent when its count is 0
  (FR6).
- Given the task fetch fails, when the rail renders, then the page still renders and the affected
  badges are simply absent (no crash).
- Given the whole change, when the diff is inspected, then it touches only `web-frontend/` (no
  backend, no migration) and the new strings exist in all 10 locales (NFR9/NFR5/NFR8).

## Spec Change Log

- **2026-06-13 — review patches (code-only, no frozen-section change).** Three adversarial
  reviewers ran; acceptance auditor returned PASS (frontend-only, all ACs met). Applied patches:
  (1) narrowed the interim comms-overdue task regex in `tabBadges.ts` to clearly comms-owned names
  (dropped over-broad `mail|reminder|erinnerung`) to kill false positives; (2) added an EN fallback
  to the `eventPage.details.intro` `t()` call for consistency; (3) added a URL-normalization effect
  in `EventPage.tsx` so a locked-tab deep-link (e.g. `?tab=wrapup` on an early event) rewrites the
  URL to match the Cockpit fallback. Accepted-by-design (not changed): unknown-`workflowState` →
  Wrap-up active — the frozen I/O-matrix "never hide a tab" rule; `workflowState` is always present
  on this route. Verified non-issues: `event.sessions` / `pendingMaterialsCount` / `event.topic?.name`
  are the correct DTO accessors (no silent no-op). Deferred to Phase E: venue/caterer comms-dot +
  speakers-badge de-dup (see deferred-work.md). KEEP: the relevance-map invariants (always-active
  trio, Wrap-up lock boundary, 8 rows, unknown→active) and their exhaustive unit coverage.

## Verification

**Commands:**
- `cd web-frontend && npm run type-check` -- expected: no TS errors.
- `cd web-frontend && npx vitest run src/utils/workflow/workflowState.test.ts src/components/organizer/EventPage/__tests__/EventPage.test.tsx 2>&1 | tee /tmp/e14a-test.log` -- expected: all green (`set -o pipefail`).
- `cd web-frontend && npm run lint` -- expected: clean.
- `for l in de en fr it rm es fi nl ja gsw-BE; do node -e "JSON.parse(require('fs').readFileSync('public/locales/$l/events.json'))"; done` -- expected: all 10 parse; new keys present in each.

**Manual checks:**
- Load `/organizer/events/:eventCode` against an event in an early state (e.g. `TOPIC_SELECTION`):
  8 tabs, Wrap-up dimmed+🔒 and unclickable, title in header. Then an `EVENT_LIVE` event: Wrap-up
  active. Confirm existing tab bodies still render their unchanged content in their new slots.

## Suggested Review Order

**The lifecycle foundation (14.A.1) — start here**

- Entry point: the single declarative map + helper that drives every tab's state.
  [`workflowState.ts:249`](../../web-frontend/src/utils/workflow/workflowState.ts#L249)
- Resolution order — always-active trio first, unknown→active, then locked>dimmed.
  [`workflowState.ts:316`](../../web-frontend/src/utils/workflow/workflowState.ts#L316)
- The 8-tab id union the map and the page share.
  [`workflowState.ts:194`](../../web-frontend/src/utils/workflow/workflowState.ts#L194)

**The shell (14.A.2)**

- The new 8-tab IA in work+config clusters (replaces the old 10).
  [`EventPage.tsx:70`](../../web-frontend/src/components/organizer/EventPage/EventPage.tsx#L70)
- Locked-tab fallback + URL normalization so content and address bar never disagree.
  [`EventPage.tsx:141`](../../web-frontend/src/components/organizer/EventPage/EventPage.tsx#L141)
- Per-tab dim/lock rendering from the relevance map (disabled + lock icon + aria).
  [`EventPage.tsx:355`](../../web-frontend/src/components/organizer/EventPage/EventPage.tsx#L355)
- Existing components recomposed into the new slots (interim Cockpit = Overview).
  [`EventPage.tsx:271`](../../web-frontend/src/components/organizer/EventPage/EventPage.tsx#L271)
- Consolidation containers — Communications (newsletter/notices/venue) and Wrap-up (photos/appreciation).
  [`EventCommunicationsContainer.tsx:28`](../../web-frontend/src/components/organizer/EventPage/EventCommunicationsContainer.tsx#L28)
  [`EventWrapupContainer.tsx:28`](../../web-frontend/src/components/organizer/EventPage/EventWrapupContainer.tsx#L28)
- Interim Details: identity summary + reuse of the existing edit modal.
  [`EventDetailsTab.tsx:26`](../../web-frontend/src/components/organizer/EventPage/EventDetailsTab.tsx#L26)

**The attention badges (14.A.3)**

- Pure, testable badge derivation from existing client data (narrowed comms regex after review).
  [`tabBadges.ts:64`](../../web-frontend/src/components/organizer/EventPage/tabBadges.ts#L64)
- Composes the data sources; shares usePublishing's query key for cache dedup.
  [`useTabBadges.ts:17`](../../web-frontend/src/components/organizer/EventPage/useTabBadges.ts#L17)
- Thin React-Query wrappers over the existing taskService (no backend change).
  [`useEventTasks.ts:16`](../../web-frontend/src/hooks/useEventTasks.ts#L16)
- Badge rendering on the rail (suppressed on locked tabs; zero→no badge).
  [`EventPage.tsx:237`](../../web-frontend/src/components/organizer/EventPage/EventPage.tsx#L237)

**Peripherals — tests & i18n**

- Exhaustive relevance-map coverage (8×8 pairings, lock boundary, unknown→active).
  [`workflowState.test.ts`](../../web-frontend/src/utils/workflow/workflowState.test.ts)
- Badge selector edge cases (slot/review count, publishing-ready, comms-overdue).
  [`tabBadges.test.ts`](../../web-frontend/src/components/organizer/EventPage/tabBadges.test.ts)
- 8-tab shell behaviour, dim/lock, badges, error/loading.
  [`EventPage.test.tsx`](../../web-frontend/src/components/organizer/EventPage/__tests__/EventPage.test.tsx)
