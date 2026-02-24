# Epic 10: Additional Stories

**Status:** 🔨 **IN PROGRESS** — Stories 10.1 and 10.2 ready for dev (2026-02-24).

**Epic Goal**: Consolidate scattered admin configuration and tooling into coherent, discoverable interfaces. These are standalone improvements that don't belong to any prior epic's domain.

**Deliverable**: A single Event Management Administration page at `/organizer/admin` that unifies event type config, batch data imports, task template management, and email template management — replacing scattered entry points with one organized hub.

**Story split (IR review 2026-02-24):** Originally one story; split into 10.1 (frontend-only, 3 tabs) and 10.2 (email templates backend + 4th tab) to reduce delivery risk and unblock frontend work from backend ADR compliance changes.

**L&F template pattern (added 2026-02-24):** Story 10.2 introduces a layout template (`batbern-default`) that owns the BATbern HTML shell (CSS, logo header, footer). Individual content templates hold only email-specific body copy. Content templates are edited with TinyMCE (WYSIWYG). The layout template is edited with Monaco (source code). Story 10.3 (future) will migrate the 22 existing system templates from standalone full-HTML to content-only using the layout.

---

## Epic Overview

### Architecture Context

- **Primary service**: `event-management-service` — email template storage + CRUD API (Story 10.2)
- **Frontend**: React 19 SPA — new `/organizer/admin` page with 3 tabs (Story 10.1) + 4th tab added (Story 10.2)
- **No new services required** — all changes land in existing services
- **DB change**: New `email_templates` table in event-management-service (Flyway V62) — Story 10.2 only. Includes `is_layout` + `layout_key` columns for the L&F pattern.
- **Email sending**: All existing email services updated to load templates from DB, merge with layout if `layout_key` set, then substitute variables. Classpath fallback preserved. — Story 10.2 only
- **L&F layout template**: `batbern-default-{de,en}` layout templates seeded from new classpath files; own the full HTML shell (CSS, BATbern header, footer). Content templates injected at `{{content}}` placeholder.
- **Editor split**: Layout templates → Monaco Editor (technical, full HTML). Content templates → TinyMCE WYSIWYG (simple, paragraphs/CTAs only).

### Prerequisites

- ✅ Epic 5 — Task system + task templates (backend + frontend complete)
- ✅ Epic 6 — Email template classpath files exist (22 HTML files to seed)
- ✅ Epic 2 — Batch import modals implemented for all entity types
- ✅ Epic 5 — Event type configuration admin page exists

---

## Epic 10 Stories

### Story 10.1: Event Management Administration Page (3 tabs — frontend only)

**Story file**: `docs/stories/archived/epic-10/10-1-event-management-administration-page.md`
**Status**: ready-for-dev

**User Story:**
As an **organizer**, I want a single Event Management Administration page accessible from the user menu, so that I can manage event types, import data, and task templates in one organized place instead of hunting across different pages.

**Scope:**

**Tab 0 — Event Types:**
- Moves content from `/organizer/event-types` into this page (tab 0)
- Edit FULL_DAY / AFTERNOON / EVENING slot configurations
- Old route redirects to `/organizer/admin?tab=0`
- No functionality change — pure relocation

**Tab 1 — Import Event Data:**
- Consolidates all 5 batch import modals (Events, Sessions, Companies, Speakers, Participants/Attendees)
- Each import type in its own Card with description + trigger button
- Import buttons **removed** from their current homes: `EventManagementDashboard`, `CompanyManagementScreen`, `UserList`

**Tab 2 — Task Templates:**
- Standalone management (previously only accessible contextually from TaskBoardPage or event form)
- Default templates: read-only list (name, trigger state, due date)
- Custom templates: create / edit / delete
- Reuses `CustomTaskModal` (create, `eventId=null`) and a new `TaskTemplateEditModal`
- Backed by existing `taskService.updateTemplate()` / `taskService.deleteTemplate()`

**Key new files:**
```
web-frontend/src/pages/organizer/EventManagementAdminPage.tsx
web-frontend/src/components/organizer/Admin/EventTypesTab.tsx
web-frontend/src/components/organizer/Admin/ImportDataTab.tsx
web-frontend/src/components/organizer/Admin/TaskTemplatesTab.tsx
web-frontend/src/components/organizer/Admin/TaskTemplateEditModal.tsx
```

**Key modified files:**
```
web-frontend/src/App.tsx                            — new route + redirect /organizer/event-types
web-frontend/src/components/.../UserMenuDropdown.tsx — Administration menu item
web-frontend/src/components/.../EventManagementDashboard.tsx — remove imports
web-frontend/src/components/.../CompanyManagementScreen.tsx  — remove imports
web-frontend/src/components/.../UserList.tsx                 — remove imports
public/locales/de/common.json + en/common.json      — menu.administration key
```

---

### Story 10.2: Email Template Management

**Story file**: `docs/stories/archived/epic-10/10-2-email-template-management.md`
**Status**: ready-for-dev
**Prerequisite**: Story 10.1 must be complete

**User Story:**
As an **organizer**, I want to view and edit email templates directly from the Administration page, so that I can update subject lines and email content without requiring a code deploy.

**Scope:**

**Backend — Email Templates API:**
- New DB-backed storage (`email_templates` table, Flyway V62)
- 22 existing classpath HTML templates seeded on startup via `EmailTemplateSeedService` (`@PostConstruct`, idempotent)
- REST API at `/api/v1/email-templates` — ADR-003 compliant paths: `/{templateKey}/{locale}`
- System templates (seeded): editable but not deletable
- Custom templates: full CRUD
- Email services updated to load templates from DB with classpath fallback
- OpenAPI spec written FIRST (ADR-006 contract-first)

**Tab 3 — Email Templates Frontend:**
- 4th tab added to the `/organizer/admin` page
- **Layout Templates section**: `batbern-default` templates listed with Monaco editor (source HTML)
- **Content Templates section**: category filter (Speaker | Registration | Task Reminders), DE/EN toggle; TinyMCE WYSIWYG editor; preview shows fully merged branded email (layout + content)
- New custom templates default to `layoutKey='batbern-default'`
- Newsletter category scaffolded in DB but omitted from initial UI (no seeded templates)

**Key new files:**
```
web-frontend/src/services/emailTemplateService.ts
web-frontend/src/hooks/useEmailTemplates.ts
web-frontend/src/components/organizer/Admin/EmailTemplatesTab.tsx
web-frontend/src/components/organizer/Admin/EmailTemplatePreviewModal.tsx
web-frontend/src/components/organizer/Admin/EmailTemplateEditModal.tsx
services/event-management-service/.../domain/EmailTemplate.java
services/event-management-service/.../service/EmailTemplateService.java
services/event-management-service/.../service/EmailTemplateSeedService.java
services/event-management-service/.../service/mapper/EmailTemplateMapper.java
services/event-management-service/.../controller/EmailTemplateController.java  ← implements EmailTemplatesApi
services/event-management-service/src/main/resources/db/migration/V62__create_email_templates_table.sql
```

**Key modified files:**
```
docs/api/events.openapi.yml                         — email-templates endpoints (FIRST — ADR-006)
web-frontend/src/pages/organizer/EventManagementAdminPage.tsx — add 4th tab
public/locales/de/organizer.json + en/organizer.json — emailTemplates.* keys
services/.../SpeakerInvitationEmailService.java     — DB lookup with classpath fallback
services/.../SpeakerReminderEmailService.java        — DB lookup with classpath fallback
services/.../SpeakerAcceptanceEmailService.java      — DB lookup with classpath fallback
services/.../RegistrationEmailService.java           — DB lookup with classpath fallback
```

---

## Success Criteria

**Epic 10 Success Criteria:**
- ✅ Single entry point (`/organizer/admin`) for all event management configuration
- ✅ No scattered admin buttons on entity management pages (imports moved)
- ✅ Email templates editable by organizers without code deploys
- ✅ Task templates manageable standalone (not just inside event form context)
- ✅ All email sending continues to work (DB lookup with classpath fallback)

**Metrics:**
- 22 email templates seeded and queryable via REST API
- Import flow accessible from admin page, removed from 3 original pages
- `/organizer/event-types` route redirects cleanly

## Definition of Done (Epic 10)

**Story 10.1:**
- [ ] All 4 ACs implemented with passing tests
- [ ] Type-check passes, no TypeScript errors
- [ ] Manual E2E: all 3 tabs verified, imports removed from originals
- [ ] i18n: `menu.administration` key in de/en

**Story 10.2:**
- [ ] All 2 ACs implemented with passing tests
- [ ] Layout classpath files (`layout-batbern-default-de/en.html`) authored before seed service implemented
- [ ] TDD: email template controller + seed service tests written first (ADR-006)
- [ ] OpenAPI spec committed before any implementation begins
- [ ] Type-check passes, no TypeScript errors
- [ ] Manual E2E: 4th tab visible; 24 templates (22 content + 2 layout); preview shows fully branded merged email; layout edit via Monaco; content edit via TinyMCE; email sender uses updated subject
- [ ] i18n: `emailTemplates.*` keys in de/en organizer files

---

### Story 10.3: Migrate System Templates to Layout-Based (future)

**Story file**: TBD
**Status**: backlog
**Prerequisite**: Story 10.2 complete and `batbern-default` layout proven in production

**Scope**: Extract the content body from each of the 22 system templates; strip the HTML shell (moved to `batbern-default`). Each template becomes content-only (`layout_key='batbern-default'`). Allows organizers to edit all 22 templates in TinyMCE. Updating the BATbern logo/footer in the layout propagates to all 22 emails automatically.

---

**END OF EPIC 10**
