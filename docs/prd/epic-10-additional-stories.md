# Epic 10: Additional Stories

**Status:** 🔨 **IN PROGRESS** — Stories 10.1–10.4 in sprint (2026-02-24).

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

### Story 10.3: Task Deadline Reminder Email

**Story file**: `docs/stories/archived/epic-10/10-3-task-deadline-reminder-email.md`
**Status**: ✅ done (2026-02-24)
**Prerequisite**: Story 10.2 (DB-backed template loading + `task-reminder-*` seeded by `EmailTemplateSeedService`)

**User Story:**
As an **organizer**, I want to receive an email reminder the day before a task I'm assigned to is due, so that I don't miss important deadlines for event-related tasks.

**Scope:**

**Scheduler (`TaskDeadlineReminderScheduler`):**
- Runs daily at 8 AM Swiss time (`cron: 0 0 8 * * *`, configurable via `batbern.task-reminders.cron`)
- ShedLock prevents duplicate execution across ECS instances (`lockAtMostFor: PT30M`)
- Time window: `startOfTomorrow` → `endOfTomorrow` in `Europe/Zurich`
- Dispatches `TaskReminderEmailService.sendTaskDeadlineReminder()` per matching task

**Task query:**
- `EventTaskRepository.findTasksDueForReminder(Instant from, Instant to)` — non-completed tasks (`status != completed`) with assigned organizer, due in the tomorrow window

**Email service (`TaskReminderEmailService`):**
- `@Async` — loads organizer email via `userApiClient.getUserByUsername()` (15-min cached)
- DB template (`task-reminder-de/en`) via `emailTemplateService` with classpath fallback
- Variables: `recipientName`, `taskName`, `eventTitle`, `eventCode`, `dueDate`, `taskNotes`, `taskBoardLink`
- Subject DE: `"Aufgabenerinnerung: {taskName} fällig morgen"` / EN: `"Task Reminder: {taskName} due tomorrow"`
- Email send failures are swallowed (logged only) — scheduler continues

**Templates:** `task-reminder-de.html` + `task-reminder-en.html` — content fragments (no HTML wrapper; `batbern-default` layout provides shell). Auto-seeded by `EmailTemplateSeedService` into `TASK_REMINDER` category.

**Key new files:**
```
services/event-management-service/.../service/TaskReminderEmailService.java
services/event-management-service/.../scheduler/TaskDeadlineReminderScheduler.java
services/event-management-service/src/main/resources/email-templates/task-reminder-de.html
services/event-management-service/src/main/resources/email-templates/task-reminder-en.html
services/event-management-service/.../service/TaskReminderEmailServiceTest.java
services/event-management-service/.../scheduler/TaskDeadlineReminderSchedulerTest.java
```

**Key modified files:**
```
services/event-management-service/.../repository/EventTaskRepository.java — findTasksDueForReminder query
```

---

### Story 10.5: Migrate System Templates to Layout-Based (future)

**Story file**: TBD
**Status**: backlog
**Prerequisite**: Story 10.2 complete and `batbern-default` layout proven in production

**Scope**: Extract the content body from each of the 22 system templates; strip the HTML shell (moved to `batbern-default`). Each template becomes content-only (`layout_key='batbern-default'`). Allows organizers to edit all 22 templates in TinyMCE. Updating the BATbern logo/footer in the layout propagates to all 22 emails automatically.

*(Previously tracked as `10-3-migrate-system-templates-to-layout` in sprint-status — renumbered to 10.5 to accommodate Story 10.3 Task Reminder and Story 10.4 Blob Selector.)*

---

### Story 10.4: Blob Topic Selector

**Story file**: `_bmad-output/implementation-artifacts/10-4-blob-topic-selector.md`
**Spec**: `_bmad-output/implementation-artifacts/blob-topic-selector-spec.md`
**Brainstorm**: `_bmad-output/brainstorming/brainstorming-session-2026-02-24.md`
**Status**: ready-for-dev
**Prerequisite**: None (independent of 10.1–10.3)

**User Story:**
As an **organizer**, I want a full-screen physics-based blob visualization for topic selection, accessible via a button on the topic management page, so that during our Teams meeting I can intuitively feel how well a proposed topic aligns with partner interests and event history — without reading a single number.

**Concept:** Three types of blobs interact through D3 force simulation physics:
- 🔵 **Blue blobs** — proposed topics, summoned by typing
- 🟢 **Green blobs** — partner interests (up to 20, with company logos), attracted to similar blue blobs
- ⭐ **Red star blobs** — past BATbern event topics (57 events), dormant background constellation; ignite and repel when a blue blob covers similar recent territory (within 6 events ≈ 2 years)
- 👻 **Ghost candidates** — latent ideas from organizer backlog, partner suggestions, and AI-fetched trending topics; click to awaken as a blue blob

The organizer team watches a single driver share their screen on Teams. The driver types topics, drags blobs, overrides AI-detected similarities manually, and double-clicks to accept — generating a session note attached to the event.

**Scope:**

**Entry Point:**
- "Blob Selector" button added to `TopicManagementPage` (only when `eventCode` present in query params)
- Navigates to new route `/organizer/events/:eventCode/topic-blob`
- `TopicBacklogManager` and its existing 3 view modes are **untouched**

**Full-Screen Page (`BlobTopicSelectorPage`):**
- No sidebar/nav — full viewport SVG canvas (`100vw × 100vh`, dark navy `#0d1b2a`)
- Fixed back button (top-left) with unsaved-changes warning dialog
- Fixed "Fit All" + "Snap to Active" zoom controls (top-right)
- Infinite canvas via `d3.zoom()` (scale `[0.1, 4]`)
- 10-second scripted onboarding animation on first visit (localStorage-gated)

**Physics Engine:**
- D3 v7 force simulation (new dependency: `npm install d3 @types/d3`)
- `forceManyBody` (repulsion) + `forceCollide` (no overlaps) + `forceLink` (partner attraction) + `forceCenter` (soft pull to center)
- Custom forces for red star repulsion and orbit mechanics

**Interaction Language:**
1. **Summon** — type anywhere → text input → Enter → blue blob slides in from right edge
2. **Awaken** — click ghost → becomes blue blob, physics activates
3. **Override** — drag-merge (1.5s hold → merge halo → confirm). Red star dragged near blue blob → becomes orbiting satellite ("acknowledged warning")
4. **Accept** — double-click blue blob → confirm dialog → writes `topicSelectionNote` to event → navigate to speakers tab

**Backend (new endpoints in event-management-service):**
- `GET /api/v1/events/{eventCode}/topic-session-data` — aggregates partner topics, past events with cluster, organizer backlog topics (from existing topics table, `status=AVAILABLE`), AI-fetched trending topics (LLM call, 1h cache, hardcoded fallback)
- `POST /api/v1/events/{eventCode}/topic-similarity` — returns `{ cluster, similarityScore, relatedPastEventNumbers[] }`. Pre-hardcoded 7-cluster map for all 57 BATbern events; OpenAI embeddings for novel topics; keyword fallback.
- `PATCH /api/v1/events/{eventCode}` — add `topicSelectionNote: TEXT` field (Flyway V64)
- `BatbernTopicClusterService` — hardcoded cluster map (AI_ML, SECURITY, ARCHITECTURE, DATA, CLOUD_INFRA, MOBILE, BUSINESS_OTHER)

**Existing infrastructure reused (no new CRUD):**
- Topic backlog: existing `/api/v1/topics` (5 categories, CRUD already at `/organizer/topics`)
- Past events: existing events table (eventNumber + title)
- Partner topics: existing partner coordination data
- `TopicSelectionResponse` flow: existing PATCH on event

**Key new files:**
```
web-frontend/src/pages/organizer/BlobTopicSelectorPage.tsx
web-frontend/src/components/BlobTopicSelector/BlobTopicSelector.tsx
web-frontend/src/components/BlobTopicSelector/useBlobSimulation.ts
web-frontend/src/components/BlobTopicSelector/useTopicSessionData.ts
web-frontend/src/components/BlobTopicSelector/types.ts
web-frontend/src/components/BlobTopicSelector/OnboardingOverlay.tsx
web-frontend/src/components/BlobTopicSelector/AcceptTopicDialog.tsx
web-frontend/src/services/blobTopicService.ts
services/event-management-service/.../BatbernTopicClusterService.java
services/event-management-service/.../TopicSimilarityController.java
services/event-management-service/.../TopicSessionDataController.java
services/event-management-service/.../TrendingTopicsService.java
services/event-management-service/src/main/resources/db/migration/V64__add_topic_selection_note.sql
```

**Key modified files:**
```
web-frontend/src/App.tsx                            — new route /organizer/events/:eventCode/topic-blob
web-frontend/src/pages/organizer/TopicManagementPage.tsx — add Blob Selector button
public/locales/de/events.json + en/events.json      — navigation.blobSelector key
public/locales/de/organizer.json + en/organizer.json — blobSelector.* keys
docs/api/events.openapi.yml                         — topic-session-data + topic-similarity endpoints
```

**Definition of Done (Story 10.4):**
- [ ] All 34 ACs passing with unit + integration tests (backend) and E2E test (frontend)
- [ ] D3 v7 installed, canvas renders in Chromium at 60fps with ≤90 blobs
- [ ] "Blob Selector" button visible on `/organizer/topics?eventCode=BATbernXX`, hidden without eventCode
- [ ] Full-screen page has no sidebar/nav. Back button triggers warning dialog.
- [ ] Green partner logos visible at ≥32px inside absorbed blue blobs
- [ ] Red star repulsion correct: events within 6 of most recent are active; beyond 6 are dormant
- [ ] Accepting a topic writes `topicSelectionNote` to event and navigates to speakers tab
- [ ] Onboarding animation plays on first visit only (localStorage-gated)
- [ ] i18n: `navigation.blobSelector` (events) + `blobSelector.*` keys (organizer) in de/en
- [ ] Type-check passes, no TypeScript errors
- [ ] OpenAPI spec updated before backend implementation (ADR-006)

---

**END OF EPIC 10**
