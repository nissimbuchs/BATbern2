# Story 10.1: Event Management Administration Page

Status: done

## Story

As an **organizer**,
I want a single Event Management Administration page accessible from the user menu,
so that I can manage event types, import data, and task templates in one organized place
instead of hunting across different pages.

## Context

Admin-level configuration is currently scattered across multiple disconnected pages:
- **Event Types** live at `/organizer/event-types` (an isolated full page)
- **Batch import buttons** are embedded inside `EventManagementDashboard`, `CompanyManagementScreen`, and `UserList` — hard to discover
- **Task templates** can only be created ad-hoc from TaskBoardPage or implicitly via the event form; no standalone management view exists

This story creates `/organizer/admin`, a tabbed administration page with 3 tabs, consolidating these three concerns. Accessible via a new "Administration" item in the organizer user menu dropdown.

Email template management is a follow-on backend+frontend feature delivered in **Story 10.2** — the page is designed with a 4th tab slot reserved for it.

## Acceptance Criteria

1. **AC1 — Admin Page & Navigation**: A new page at `/organizer/admin` with 3 MUI Tabs (Event Types | Import Data | Task Templates). Accessible via a new "Administration" entry in `UserMenuDropdown.tsx` (organizer only). ORGANIZER role guard on the page. Tab index persisted in URL query param `?tab=N`.

2. **AC2 — Event Types Tab**: Content from `EventTypeConfigurationAdmin.tsx` extracted into `EventTypesTab.tsx` and rendered as tab 0. The old `/organizer/event-types` route redirects to `/organizer/admin?tab=0`. All functionality (edit FULL_DAY/AFTERNOON/EVENING configs) preserved.

3. **AC3 — Import Data Tab**: All 5 batch import modals consolidated in `ImportDataTab.tsx` (Events, Sessions, Companies, Speakers, Participants/Attendees), each in a Card with a description and trigger button. Import buttons/modals **removed** from their original pages: `EventManagementDashboard.tsx`, `CompanyManagementScreen.tsx`, `UserList.tsx`.

4. **AC4 — Task Templates Tab**: Standalone management at tab 2. Shows all templates in two sections: Default Templates (read-only list — name, triggerState chip, due date) and Custom Templates (create / edit / delete). Create opens existing `CustomTaskModal` with `eventId={null}`. Edit opens new `TaskTemplateEditModal`. Delete uses `window.confirm()` then calls `taskService.deleteTemplate()`. Uses existing `taskService.updateTemplate()` and `taskService.deleteTemplate()` from `taskService.ts`.

## What Was Deliberately Cut

| Removed | Reason |
|---------|--------|
| Email Templates tab | Requires new backend (DB, API, seed service, email sender updates) — delivered in Story 10.2 |
| Keeping import buttons in original pages | User decision: move-only, not duplicate |
| Template version history / rollback | Phase 3 |
| Audit log for template edits | Phase 3 |

## Tasks / Subtasks

### Task 1: Frontend scaffold + routing (AC: 1)

- [x]Write test for `EventManagementAdminPage` (renders correct tabs, role guard)
- [x]Create `web-frontend/src/pages/organizer/EventManagementAdminPage.tsx`
  - MUI `Tabs` with 3 tabs, tab index from `useSearchParams` (`?tab=N`)
  - ORGANIZER role guard
  - `Breadcrumbs`: Home → Administration
  - Page designed to accept a 4th tab (Email Templates) in Story 10.2 — keep tab component array clean
- [x]`App.tsx`: add lazy route `/organizer/admin`, redirect `/organizer/event-types` → `/organizer/admin?tab=0`
- [x]`UserMenuDropdown.tsx`: add "Administration" `MenuItem` (organizer only) with `AdminPanelSettingsIcon`, navigates to `/organizer/admin`
- [x]i18n: `menu.administration` key in `de/common.json` ("Verwaltung") + `en/common.json` ("Administration")

---

### Task 2: Tab 0 — Event Types (AC: 2)

- [x]Create `web-frontend/src/components/organizer/Admin/EventTypesTab.tsx`
  - Extract JSX body from `EventTypeConfigurationAdmin.tsx` (Grid + Card + Dialog), keep all hooks local
  - Remove outer `Container` and `Breadcrumbs` (page handles those)
- [x]Wire into `EventManagementAdminPage` tab 0
- [x]Verify edit flow still works end-to-end

---

### Task 3: Tab 1 — Import Event Data (AC: 3)

- [x]Create `web-frontend/src/components/organizer/Admin/ImportDataTab.tsx`
  - `Grid` of 5 `Card`s: Events, Sessions, Companies, Speakers, Participants
  - Each card: title, description text, trigger button → opens respective batch import modal
  - State: 5 `open` booleans, one per modal
  - Import all 5 modal components directly
- [x]Remove from `EventManagementDashboard.tsx`: `isBatchImportOpen` + `isSessionBatchImportOpen` state, QuickActions import buttons, `EventBatchImportModal` + `SessionBatchImportModal` renders + imports
- [x]Remove from `CompanyManagementScreen.tsx`: `isBatchImportOpen` state, upload button, `CompanyBatchImportModal` render + import
- [x]Remove from `UserList.tsx`: `batchImportModalOpen` + `participantImportModalOpen` state, both upload buttons, both modal renders + imports

---

### Task 4: Tab 2 — Task Templates (AC: 4)

- [x]Create `web-frontend/src/components/organizer/Admin/TaskTemplateEditModal.tsx`
  - Props: `open`, `onClose`, `template: TaskTemplateResponse`
  - Fields: name (TextField), triggerState (Select — 9 workflow states), dueDateType (Select: immediate/relative_to_event/absolute), dueDateOffsetDays (number TextField, shown only if `relative_to_event`)
  - Save → `taskService.updateTemplate(template.id, request)` → `queryClient.invalidateQueries(['tasks', 'templates'])` → close
- [x]Create `web-frontend/src/components/organizer/Admin/TaskTemplatesTab.tsx`
  - `useQuery(['tasks', 'templates'], taskService.listAllTemplates)`
  - **Default Templates** section: read-only list (name, triggerState Chip, dueDate summary)
  - **Custom Templates** section: `[+ Add Template]` → `CustomTaskModal` (eventId=null); per-row [Edit] → `TaskTemplateEditModal`; [Delete] → `window.confirm()` + `taskService.deleteTemplate()` + invalidate
- [x]Wire into `EventManagementAdminPage` tab 2
- [x]Reuses existing `taskService.updateTemplate()`, `taskService.deleteTemplate()`, `CustomTaskModal`

---

## Key Reused Components / Services

| Existing Asset | Used In |
|----------------|---------|
| `CustomTaskModal` (`web-frontend/src/components/organizer/Tasks/CustomTaskModal.tsx`) | Task Templates tab — create flow (eventId=null) |
| `taskService.updateTemplate()`, `taskService.deleteTemplate()` (`web-frontend/src/services/taskService.ts`) | Task Templates tab |
| `EventBatchImportModal`, `SessionBatchImportModal` | Import Data tab |
| `CompanyBatchImportModal` | Import Data tab |
| `SpeakerBatchImportModal`, `ParticipantBatchImportModal` | Import Data tab |
| `useEventTypes`, `useUpdateEventType`, `EventTypeConfigurationForm`, `SlotTemplatePreview` | Event Types tab |
| `OrganizerSelect` | Task Templates tab |

---

## Frontend Files

**New:**
```
web-frontend/src/
├── pages/organizer/EventManagementAdminPage.tsx                 NEW
├── pages/organizer/EventManagementAdminPage.test.tsx            NEW
└── components/organizer/Admin/
    ├── EventTypesTab.tsx                                        NEW
    ├── ImportDataTab.tsx                                        NEW
    ├── TaskTemplatesTab.tsx                                     NEW
    └── TaskTemplateEditModal.tsx                                NEW
```

**Modified:**
- `src/App.tsx` — new route + redirect `/organizer/event-types` → `/organizer/admin?tab=0`
- `src/components/shared/Navigation/UserMenuDropdown.tsx` — Administration item
- `src/components/shared/Navigation/UserMenuDropdown.test.tsx` — Administration navigation tests
- `src/components/organizer/EventManagement/EventManagementDashboard.tsx` — remove import buttons/modals
- `src/components/organizer/EventManagement/QuickActions.tsx` — update Event Types nav to `/organizer/admin?tab=0`
- `src/components/shared/Company/CompanyManagementScreen.tsx` — remove import button/modal
- `src/components/organizer/UserManagement/UserList.tsx` — remove import buttons/modals
- `src/components/organizer/UserManagement/UserList.test.tsx` — remove stale SpeakerBatchImportModal mock
- `public/locales/de/common.json` + `en/common.json` — `menu.administration`
- `docs/api/events-api.openapi.yml` — Story 10.2 Email Templates API spec (pre-defined for next story)

---

## Testing Strategy

### Frontend
```bash
cd web-frontend && npm run type-check 2>&1 | tee /tmp/typecheck.log
npm test -- --run 2>&1 | tee /tmp/fe-tests.log
```

### Manual verification
1. Navigate to `/organizer/admin` — 3 tabs visible
2. Tab 0: Edit FULL_DAY event type config, save → persists
3. Tab 1: Open all 5 import modals — each opens correctly
4. Verify import buttons **gone** from Events Dashboard, Companies page, Users page
5. Tab 2: Create custom task template, edit it, delete it; default templates read-only
6. `/organizer/event-types` URL → redirects to `/organizer/admin?tab=0`

---

## References

- Epic 10: `docs/prd/epic-10-additional-stories.md`
- Story 10.2 (Email Templates): `docs/stories/archived/epic-10/10-2-email-template-management.md`
- `docs/architecture/coding-standards.md` — TDD, service layer pattern
- `docs/guides/service-foundation-pattern.md` — standard service structure
- Existing task template infrastructure: `web-frontend/src/services/taskService.ts`
