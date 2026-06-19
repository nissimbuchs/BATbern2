---
title: 'Epic 14 Story 14.F.2 — Details tab: Info / Tasks / Settings sub-tabs (config-cluster merge)'
type: 'feature'
created: '2026-06-14'
baseline_commit: '0b937840'
status: 'done'
context:
  - '{project-root}/docs/prd/epic-14-event-detail-redesign.md'
  - '{project-root}/_bmad-output/project-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The prototype's Details tab covers only event identity ("Info"), and the **Tasks** surface (the `EventForm` modal's 2nd tab — a form-coupled template checklist) has no home in the 8-tab IA. Settings is a separate always-active config tab. **Decision (Nissim, 2026-06-14):** merge the config cluster into a **single "Details" tab with three sub-tabs — Info · Tasks · Settings** (rail 8 → 7), matching the sub-tab pattern used on Speakers/Communications/Wrap-up.

**Approach (frontend-only, AR9/NFR9):**
- **Info** sub-tab = the inline-editable identity form (FR38/FR39) reusing `useUpdateEvent` (PATCH), `<FileUpload>`, the `useAiGenerate*`/`useAiApplyThemeImage` hooks, and the shared zod schema; "Change topic" → interim `/organizer/topics` nav (14.F.3 swaps to the overlay).
- **Tasks** sub-tab = the existing `EventTasksTab` template checklist made **live** for an existing event: toggling a template creates/deletes its task instance immediately, assignee changes persist, custom tasks add/edit/delete live (no form-save round-trip), via the existing `taskService`.
- **Settings** sub-tab = the existing `EventSettingsTab`, mounted unchanged.
- Shell: collapse the top-level `settings` tab into `details` (now a container); relevance map keeps the merged tab always-active. **No backend change.**

## Boundaries & Constraints

**Always:**
- Frontend-only; touch only `web-frontend/`. Reuse `useUpdateEvent`, AI hooks, `<FileUpload>`, `taskService`, `EventSettingsTab`, `topicService` — all exist (AR9/NFR9). `@/` alias, generated types, `config` (NFR8).
- New/changed strings via `useTranslation('events')` in **all 10 locales** (NFR5), EN+DE first-class; reuse existing `form.*`/`validation.*`/`tabs.*` keys.
- New surfaces keyboard-operable + labelled, WCAG 2.1 AA (NFR7).
- Consequential actions show feedback + no silent loss: Info Save shows pending/saved/error and retains values on failure; a Tasks template-toggle that **creates a task** (which may seed notifications) persists immediately with success/error feedback.

**Ask First:**
- If anything needs a backend/API/migration change → HALT (must not).

**Never:**
- Don't duplicate `EventForm`'s validation: **extract** `createEventSchema` + `transformDatesForApi` (+ `getChangedFields`) into a shared module both import.
- Don't remove the `EventForm` modal — the **create flow** (EventManagementDashboard) still needs Info+Tasks pre-save. This story makes Details the *edit* surface; retiring the edit-modal path is a later cleanup (note, don't do here).
- Don't build the topic overlay (14.F.3) — "Change topic" is interim route nav. No "Preview public page"/"Enrol" on Info (FR39). No mobile reshape (Phase G).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Sub-tab switch | Info / Tasks / Settings | renders the matching surface; `details-subtab-*` testids; Info is default | — |
| Info — edit + Save | change fields | shared zod validates; Save (dirty-only) → `useUpdateEvent.mutateAsync(changed)`; pending/saved | fail → inline error, values kept |
| Info — ✨ AI description / theme image | topic set + `aiContentEnabled` | existing AI hooks fill description / apply theme image | AI error → inline msg |
| Info — Change topic | click | nav `/organizer/topics?eventCode={code}` (interim) | — |
| Tasks — toggle a template on | check a template | `taskService.createTasksFromTemplates` creates the instance live; list refreshes; template now shows as active/disabled | fail → inline error, checkbox reverts |
| Tasks — toggle a template off | uncheck (instance exists) | deletes that task instance live (confirm if it carries state) | fail → inline error, stays checked |
| Tasks — assignee change / custom add-edit-delete | change | persists via `taskService` immediately; list refreshes | fail → inline error |
| Settings sub-tab | select | renders existing `EventSettingsTab` unchanged | its own states |
| Rail | any state | 7 tabs; the merged "Details" (Info/Tasks/Settings) is always active (never dimmed/locked) | — |

</frozen-after-approval>

## Code Map

- `web-frontend/src/components/organizer/EventManagement/eventFormSchema.ts` — **NEW**: extract `createEventSchema(t)` + `transformDatesForApi` + `getChangedFields` from `EventForm.tsx` (pure move). `EventForm.tsx` imports them (delete inline copies) — import-only change, behaviour unchanged.
- `web-frontend/src/components/organizer/EventPage/EventDetailsContainer.tsx` — **NEW**: sub-tab switch (`'info' | 'tasks' | 'settings'`), `details-subtab-*` testids; mounts `EventInfoTab` / `EventTasksLiveTab` / `EventSettingsTab`. Default `info`.
- `web-frontend/src/components/organizer/EventPage/EventInfoTab.tsx` — **NEW** (replaces the read-only `EventDetailsTab`): inline identity form (theme image `<FileUpload>` + AI-generate/apply, Title, Description + AI-generate, Topic chip + Change topic, When & where via `EventTypeSelector` + date/deadline/venue), react-hook-form + shared zod, explicit Save via `useUpdateEvent`. Delete the old `EventDetailsTab.tsx` (+ its onEdit use).
- `web-frontend/src/components/organizer/EventPage/EventTasksLiveTab.tsx` — **NEW**: wraps the existing `EventTasksTab` UI but owns live state — loads templates + `taskService.listEventTasks`, and each toggle/assignee/custom action calls `taskService` immediately (create/delete/update) + refreshes, instead of deferring to a form save. Reuse `EventTasksTab`'s presentational structure + `CustomTaskModal`.
- `web-frontend/src/components/organizer/EventPage/EventPage.tsx` — TABS: drop the standalone `settings` entry; `details` mounts `<EventDetailsContainer event eventCode />`; remove the `EventDetailsTab onEdit` wiring. Keep `handleEdit`/`EventForm` for the create/default path.
- `web-frontend/src/utils/workflow/workflowState.ts` — `EventTabId`: remove `'settings'`; drop it from every relevance-map row; `getTabRelevance` keeps `details` always-active. Update unit tests.
- `web-frontend/src/components/organizer/EventPage/tabBadges.ts` / `useTabBadges.ts` — remove any `settings` references (none expected to be badged).
- `web-frontend/src/components/organizer/EventPage/__tests__/` — **NEW** tests: `EventDetailsContainer` (3 sub-tabs + routing), `EventInfoTab` (render/edit+Save/validation/AI/Change-topic/no-Preview-Enrol), `EventTasksLiveTab` (toggle creates/deletes live, custom add); update `workflowState`/EventPage shell tests for 7 tabs.
- `web-frontend/public/locales/{10}/events.json` — sub-tab labels `eventPage.details.subtabs.{info,tasks,settings}`; Info `eventPage.details.*` labels + save/saved/saveError; reuse `form.*`/`validation.*`/`tabs.tasks`. All 10 locales.

## Tasks & Acceptance

**Execution (implement + verify incrementally, commit per slice):**
- [x] Extract `eventFormSchema.ts`; re-point `EventForm` imports (committed `64cc1d3e`; EventForm suite green).
- [x] Shell merge: `EventPage` 7-tab rail (`details` container, no standalone `settings`); `workflowState.ts` `EventTabId`/`ALWAYS_ACTIVE_TABS` drop `settings`; `cockpitCards` moderator/Q&A deep-links retargeted `settings`→`details`; shell + cockpitCards tests updated to 7 tabs.
- [x] `EventDetailsContainer` (new) + Settings sub-tab mount (existing `EventSettingsTab`).
- [x] `EventInfoTab` (new) inline identity form (reuses shared schema + `useUpdateEvent` + `AiAssistDrawer` + `FileUpload` + `EventTypeSelector`); deleted old `EventDetailsTab`.
- [x] `EventTasksLiveTab` (new) — live template toggle (create/delete + confirm-on-stateful), assignee via `updateTask`, custom add/edit via live `CustomTaskModal`.
- [x] `events.json` ×10 — `eventPage.details.subtabs.*` + Info labels + `tasks.deleteConfirm*`.
- [x] tests — `EventDetailsContainer` (3), `EventInfoTab` (5), `EventTasksLiveTab` (5); EventPage shell + cockpitCards updated. 653 pass / 0 fail.

**Acceptance Criteria:**
- Given the config cluster, when the rail renders, then there are **7 tabs** and a single "Details" tab opens to **Info · Tasks · Settings** sub-tabs, always active (FR1 revised; FR38/FR43).
- Given Info, when edited + Saved, then changed fields PATCH via `useUpdateEvent` with feedback; AI-generate + theme-image work; Change topic navigates; no Preview/Enrol (FR38/FR39).
- Given Tasks, when a template is toggled / a custom task added, then the change persists **immediately** via `taskService` (no form-save) with success/error feedback.
- Given Settings, when selected, then the existing `EventSettingsTab` renders unchanged (FR43).
- Given the diff, when reviewed, then only `web-frontend/` is touched, new strings exist in all 10 locales, and the `EventForm` schema extraction changes no behaviour (its suite stays green) (NFR5/NFR9/AR9).

## Design Notes

- **Rail 8 → 7 is a deliberate revision of FR1** (Nissim, 2026-06-14): config cluster collapses to one "Details" tab with Info/Tasks/Settings sub-tabs; the orphaned modal-Tasks surface gets a home. Record in the epic's grounding/notes.
- **Info Save = explicit** (not the modal's auto-save). **Tasks = live** (each action persists immediately — that's the point of lifting it out of the form).
- **Schema extraction over duplication** keeps Info/EventForm validation identical with zero drift; the EventForm edit is import-only.
- **Edit-modal not retired here** — create flow still uses it; retiring the *edit* path is a follow-up.

## Verification

**Commands:**
- `cd web-frontend && npm run type-check` -- no errors.
- `cd web-frontend && npx vitest run src/components/organizer/EventPage src/components/organizer/EventManagement src/utils/workflow` -- Details/Info/Tasks/shell/workflow + EventForm suites green.
- `cd web-frontend && npm run lint` -- passes.
- new keys present in all 10 `events.json`.

**Manual checks:**
- One "Details" tab (rail = 7) → Info / Tasks / Settings sub-tabs. Info edits save + AI + Change-topic work; Tasks toggles create/delete tasks live; Settings unchanged.
