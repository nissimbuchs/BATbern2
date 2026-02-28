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

### Story 10.5: Analytics Dashboard

**Story file**: `_bmad-output/implementation-artifacts/10-5-analytics-dashboard.md`
**Brainstorm**: `_bmad-output/brainstorming/brainstorming-session-2026-02-25.md`
**Status**: ready-for-dev
**Prerequisite**: None (independent — no backend changes conflict with other stories)

**User Story:**
As an **organizer or partner**, I want a dedicated Analytics page with rich, tabbed visualizations of BATbern event statistics, so that during partner meetings I can showcase community growth, speaker contributions, and company engagement with live, data-driven charts.

**Scope:**

**Page Shell:**
- Route `/organizer/analytics` already exists (stub page to replace)
- 4 tabs: **Overview** (default) · **Attendance** · **Topics** · **Companies**
- Global time range filter: All Time / Last 5 Years / Last 2 Years (top-right, cascades to all time-sensitive charts)
- Chart library: Recharts (already installed `^3.5.0`)
- Color palette: BATbern brand colors (from `theme.ts`)
- Per-chart layout: chart on top + `▼ Show data table` collapsible MUI Table

**Tab 1 — Overview:**
- 4 KPI cards: Total Events · Total Attendees · Companies Represented · Total Sessions (all-time, not time-filtered)
- Event cadence timeline: all events as colored bars on a time axis; colored by topic category; always shows all events

**Tab 2 — Attendance:**
- Attendees per event: `ComposedChart` (bars + trend line); label toggle (title/category/both)
- Returning vs. New attendees per event: stacked bar chart (warm=returning, cool=new)

**Tab 3 — Topics:**
- Events per category: horizontal bar chart
- Topic popularity vs. attendee count: scatter plot (X=event count, Y=avg attendees per event on topic)

**Tab 4 — Companies:**
- Partner's own company (from `user.company`) auto-highlighted and pinned in all three charts
- Top N toggle: 5 / 10 / All (default 10); own company always shown
- Attendees per company over time: stacked bar by year
- Sessions per company: bar chart with unique speaker count label
- Attendee distribution per company: pie chart with per-event filter dropdown

**Backend — New `AnalyticsController` in event-management-service:**
- `GET /api/v1/analytics/overview` — KPI totals + timeline data
- `GET /api/v1/analytics/attendance?fromYear={year}` — per-event attendance with returning/new breakdown
- `GET /api/v1/analytics/topics?fromYear={year}` — events per category + topic scatter data
- `GET /api/v1/analytics/companies?fromYear={year}` — company attendance over time, sessions, distribution
- `GET /api/v1/analytics/companies/distribution?eventCode={code}` — distribution for a single event
- ORGANIZER + PARTNER roles; aggregate data only (no individual names)

**Key data model:**
- `Registration.attendeeCompanyId` — denormalized company name (indexed), used for all company-level attendance analytics
- `SpeakerPool.company` — denormalized company name for session counting
- `Event.topicCode → Topic.category` join for category-based charts

**Key new files:**
```
web-frontend/src/pages/organizer/OrganizerAnalyticsPage.tsx          ← REPLACE stub
web-frontend/src/components/organizer/Analytics/                      ← new folder
  ChartCard.tsx, DataTable.tsx, EmptyChartState.tsx, KpiCard.tsx
  CHART_COLORS.ts, OverviewTab.tsx, EventCadenceTimeline.tsx
  AttendanceTab.tsx, AttendeesPerEventChart.tsx, ReturningVsNewChart.tsx
  TopicsTab.tsx, EventsPerCategoryChart.tsx, TopicScatterChart.tsx
  CompaniesTab.tsx, CompanyAttendanceOverTimeChart.tsx
  SessionsPerCompanyChart.tsx, CompanyDistributionPieChart.tsx
web-frontend/src/services/analyticsService.ts
web-frontend/src/hooks/useAnalytics.ts
services/event-management-service/.../controller/AnalyticsController.java
services/event-management-service/.../service/AnalyticsService.java
services/event-management-service/.../repository/AnalyticsRepository.java
```

**Key modified files:**
```
docs/api/events.openapi.yml                              — analytics endpoints (FIRST — ADR-006)
public/locales/en/organizer.json + de/organizer.json     — analytics.* keys
```

**Definition of Done (Story 10.5):**
- [ ] OpenAPI spec committed before any backend implementation
- [ ] TDD: integration tests written first (`AnalyticsControllerIntegrationTest`)
- [ ] All 10 ACs implemented
- [ ] Recharts renders at 60fps with full dataset (~58 events)
- [ ] Partner auto-highlight works when logged in as PARTNER role
- [ ] Empty states shown for each chart when no data in selected time range
- [ ] Data tables collapsible per chart, sortable by column header
- [ ] i18n: `analytics.*` keys in both `en/` and `de/` organizer files
- [ ] Type-check passes, no TypeScript errors

---

---

### Story 10.7: Newsletter Subscription & Sending

**Story file**: `_bmad-output/implementation-artifacts/10-7-newsletter-subscription-and-sending.md`
**Status**: ready-for-dev
**Prerequisites**: Story 10.2 (Email Template Management — provides `EmailTemplateService`, `EmailTemplateSeedService`, `NEWSLETTER` category, `batbern-default` layout)

**User Story:**
As an **organizer**, I want to send event newsletters and reminder emails to subscribed community members, so that we can replace Meetup.com / Hostpoint mailing lists with a fully in-house system.
As a **community member**, I want to subscribe without an account and unsubscribe instantly from any email link, so that I can stay informed about events without friction.
As a **logged-in user**, I want to manage my subscription in the public account settings page, not the organizer portal.

**Scope:**

**Backend (event-management-service):**
- Flyway **V67**: 3 new tables — `newsletter_subscribers` (email, username, unsubscribe_token, language, source, unsubscribed_at), `newsletter_sends`, `newsletter_recipients`
- `NewsletterSubscriberService` — subscribe/upsert/reactivate/unsubscribe-by-token
- `NewsletterEmailService` — build + send newsletter; loads template from DB/classpath; per-recipient unsubscribe link injection
- `NewsletterController` — public + authenticated + organizer-only endpoints
- Wire in `RegistrationService`: if `newsletterSubscribed=true` on registration → auto-subscribe silently
- Two new classpath templates (content-only fragments): `newsletter-event-de.html`, `newsletter-event-en.html`
- **Single template, dual use**: `{{reminderPrefix}}` variable is `""` for newsletter, `"Erinnerung: "` / `"Reminder: "` for reminder — no separate reminder template needed
- Newsletter templates seeded by `EmailTemplateSeedService` under `NEWSLETTER` category; editable in admin Email Templates tab

**API Endpoints:**
```
# Public (permitAll in EMS + API gateway)
POST   /api/v1/newsletter/subscribe
GET    /api/v1/newsletter/unsubscribe/verify?token=
POST   /api/v1/newsletter/unsubscribe

# Authenticated (any role)
GET    /api/v1/newsletter/my-subscription
PATCH  /api/v1/newsletter/my-subscription

# Organizer only
GET    /api/v1/newsletter/subscribers
GET    /api/v1/events/{eventCode}/newsletter/history
POST   /api/v1/events/{eventCode}/newsletter/preview
POST   /api/v1/events/{eventCode}/newsletter/send
```

**Frontend:**
- `NewsletterSubscribeWidget.tsx` — email input + subscribe button in **homepage footer** (shadcn Input + Button, i18n, success/409 states)
- `UnsubscribePage.tsx` at `/unsubscribe?token={token}` — shows email to confirm, confirm button, success/error states (PublicLayout, shadcn Card)
- `EventNewsletterTab.tsx` — new tab on EventPage: subscriber count, send history table, language selector, preview iframe, "Send Newsletter" + "Send Reminder" buttons with confirmation dialog
- `UserSettingsTab.tsx` — Newsletter toggle in Notifications sub-tab, wired to `PATCH /my-subscription`
- `App.tsx` — add `/unsubscribe` public route

**GDPR:** Every sent email contains `{{unsubscribeLink}}` → `{baseUrl}/unsubscribe?token={subscriber.unsubscribeToken}` (unique per subscriber, never expires).

**Key new files:**
```
services/event-management-service/src/main/resources/db/migration/V67__create_newsletter_tables.sql
services/event-management-service/.../domain/NewsletterSubscriber.java
services/event-management-service/.../domain/NewsletterSend.java
services/event-management-service/.../repository/NewsletterSubscriberRepository.java
services/event-management-service/.../repository/NewsletterSendRepository.java
services/event-management-service/.../service/NewsletterSubscriberService.java
services/event-management-service/.../service/NewsletterEmailService.java
services/event-management-service/.../controller/NewsletterController.java
services/event-management-service/src/main/resources/email-templates/newsletter-event-de.html
services/event-management-service/src/main/resources/email-templates/newsletter-event-en.html
web-frontend/src/services/newsletterService.ts
web-frontend/src/hooks/useNewsletter/useNewsletter.ts
web-frontend/src/components/public/NewsletterSubscribeWidget.tsx
web-frontend/src/pages/public/UnsubscribePage.tsx
web-frontend/src/components/organizer/EventPage/EventNewsletterTab.tsx
```

**Key modified files:**
```
services/event-management-service/.../service/RegistrationService.java
services/event-management-service/.../config/SecurityConfig.java    ← permitAll newsletter public paths
api-gateway/.../DomainRouter.java                                    ← route /api/v1/newsletter/** → EMS
api-gateway/.../SecurityConfig.java                                  ← same permitAll
web-frontend/src/components/organizer/EventPage/EventPage.tsx       ← add Newsletter tab
web-frontend/src/components/user/UserSettingsTab/UserSettingsTab.tsx ← newsletter toggle
web-frontend/src/App.tsx                                             ← /unsubscribe public route
web-frontend/src/components/public/HomePage.tsx                     ← footer subscribe widget
web-frontend/src/i18n/en.json + de.json                             ← newsletter.* keys
docs/api/events.openapi.yml                                          ← newsletter endpoints (FIRST)
```

**Definition of Done (Story 10.7):**
- [ ] V67 migration runs cleanly; subscribe/unsubscribe endpoints work without auth
- [ ] Registration with newsletter checkbox auto-subscribes (silent, no UI change)
- [ ] Homepage footer widget subscribes anonymous users; duplicate → 409 shown inline
- [ ] `/unsubscribe?token=valid` confirms and unsubscribes; invalid token → error state
- [ ] `/account` Settings → Notifications shows newsletter toggle for authenticated users
- [ ] EventPage → Newsletter tab: subscriber count, send history, preview, send/reminder buttons with confirmation
- [ ] Every sent email contains `{{unsubscribeLink}}` footer link (GDPR)
- [ ] Newsletter templates visible/editable in admin Email Templates tab (NEWSLETTER category)
- [ ] OpenAPI spec committed before any backend implementation (ADR-006)
- [ ] All tests pass: `NewsletterSubscriberServiceTest`, `NewsletterControllerIntegrationTest`, `NewsletterEmailServiceTest` + 3 frontend tests
- [ ] Type-check passes, no TypeScript errors; Checkstyle passes
- [ ] i18n: `newsletter.*` keys in both `en.json` and `de.json`

---

### Story 10.8a: Moderator Presentation Page — Functional

**Story file**: `_bmad-output/implementation-artifacts/10-8a-moderator-presentation-page.md`
**Status**: ready-for-dev
**Prerequisite**: None (independent — uses existing public event APIs)

**User Story:**
As an **event moderator**, I want to open a single fullscreen webpage on the projector laptop and navigate through the evening using my presentation remote, so that I can guide the audience through the BATbern event without ever touching PowerPoint — and the content is always live from the platform.

**Scope**: Full functional presenter page — all sections, navigation, break flow, sidebar, 60s poll, error handling, backend settings API. No Framer Motion. Animations and polish delivered in Story 10.8b.

**Route & Entry Point:**
- New public route `/present/:eventCode` — no authentication required
- All data sourced from existing public APIs

**Section Flow (dynamically generated from event data):**

| Section | Type | Content |
|---------|------|---------|
| 1 | Welcome | BATbern logo, hashtag `#BATbernXX`, topic title, date, venue |
| 2 | About BATbern | Admin-configurable Verein purpose text + partner count |
| 3 | Committee | All active organizer photo cards (no stagger in this story) |
| 4 | Topic Reveal | Topic title large + image emphasis |
| 5 | Agenda Preview | Full agenda centered, all sessions neutral/upcoming |
| 6…N | Speaker Sessions | One section per `PRESENTATION/KEYNOTE/WORKSHOP/PANEL_DISCUSSION` session |
| (Break) | Break | "Pause" + resume time; inserted if break/lunch session exists |
| (Recap) | Agenda Recap | Pre-break sessions greyed (✓), post-break sessions lit |
| N+1 | Upcoming Events | Next 3 future BATbern events |
| N+2 | Apéro | Closing visual with BATbern `~` text |

**New Backend:**
```
GET  /api/v1/public/settings/presentation     → { aboutText, partnerCount }  (public)
PUT  /api/v1/settings/presentation            (organizer auth required)
```
Single-row `presentation_settings` table — Flyway **V10** in company-user-management-service.

**Definition of Done (Story 10.8a):**
- [ ] OpenAPI spec committed before implementation (ADR-006); `PresentationSettingsControllerIntegrationTest` written first (TDD)
- [ ] Route `/present/BATbernXX` loads without authentication; Welcome section renders
- [ ] All 42 ACs pass
- [ ] Break flow: `→` navigates pre-break → Break → Recap → post-break
- [ ] B-key overlay shows/hides BreakSlide without changing section index
- [ ] 60-second poll updates sidebar times; error screen on initial load failure
- [ ] Renders at 1920×1080 and 2560×1440; no horizontal scrollbar; font sizes meet legibility spec
- [ ] Type-check passes; Checkstyle passes; no Framer Motion dependency

---

### Story 10.8b: Moderator Presentation Page — Animations

**Story file**: `_bmad-output/implementation-artifacts/10-8b-moderator-presentation-page-animations.md`
**Status**: ready-for-dev
**Prerequisite**: Story 10.8a complete and deployed

**User Story:**
As an **event moderator**, I want the presentation page to have polished, fluid animations, so that the audience experience feels dynamic and professional — not like a static slide deck.

**Scope**: Framer Motion animation layer added on top of Story 10.8a. No new data, routing, or business logic. All changes are additive.

- **FLIP animation**: Agenda transitions center↔sidebar via Framer Motion `layout` prop (ACs #1–4)
- **Section spring transitions**: Directional `AnimatePresence` between sections (ACs #5–7)
- **Ken Burns**: Topic background `scale 1.0 → 1.06`, 30s loop via `motion.img` (AC #8)
- **BlankOverlay fade**: AnimatePresence 0.3s fade on B-key (AC #9)
- **Committee stagger**: Cards fly-in with `delay: index × 0.12s` (AC #10)
- **BreakSlide animations**: `~` spinner, coffee cup + steam, floating beans (ACs #11–13)
- **AperoSlide spinner**: `~` CSS @keyframes rotation (AC #14)

**Definition of Done (Story 10.8b):**
- [ ] `framer-motion` in `package.json`; `npm run build` clean
- [ ] All 14 animation ACs pass; all 10.8a ACs continue to pass (no regression)
- [ ] FLIP: agenda animates center→sidebar at §5→§6; reverses on backward nav
- [ ] Ken Burns plays on topic image; BlankOverlay fades; committee cards stagger; break/apéro animations visible
- [ ] Type-check passes; no console errors

---

### Story 10.9: i18n Cleanup — Deduplication, Hardcoded Text, Test Resilience & Unused Key Removal

**Story file**: `_bmad-output/implementation-artifacts/10-9-i18n-cleanup.md`
**Plan**: `docs/plans/i18n-cleanup-plan.md`
**Status**: ready-for-dev
**Prerequisite**: None (independent — frontend-only, no backend or DB changes)

**User Story:**
As a **frontend developer and future contributor**, I want the translation system to be clean, consistent, and test-resilient, so that adding a new language or renaming a translation value never causes surprises in tests or the UI.

**Scope:**

Four phases executed **in dependency order** (do not parallelize):

| Phase | Goal | Scope |
|-------|------|-------|
| **1 — Deduplicate** | Consolidate cross-namespace duplicate values into `common` | 89 redirect-to-common + 80 new common keys; rename awkward key paths |
| **2 — Hardcoded text** | Replace hardcoded UI strings with `t()` calls | 78 prod files, 254 hits (excl. PrivacyPage, SupportPage) |
| **3 — Test resilience** | Add `data-testid` anchors; refactor unit + E2E tests off text literals | 180 unit test files (~4,400 assertions); 32 E2E spec files (~320 brittle patterns) |
| **4 — Unused key analysis** | Smart automated analysis of 830 flagged unused keys; delete confirmed dead keys | Scripted tooling + deletion |

**Phase 1 — Consolidate Duplicates:**
- 302 unique values appear in 2+ keys; 169 span multiple namespaces
- Sub-task 1.1: 89 duplicates already have a canonical `common` key — redirect callers, delete duplicates from other namespaces
- Sub-task 1.2: 80 duplicates not yet in `common` — add under `common:labels.*`, `common:filters.*`, `common:actions.*` hierarchy
- Rename awkward existing `common` keys to semantic generics (e.g., `common:company.batchImport.columns.status` → `common:labels.status`)
- All 9 locale files must stay in sync (same keys removed/added in same commit)
- Full duplicates list: `/tmp/duplicates_report.txt`

**Phase 2 — Hardcoded Text:**
- Groups: A (public pages, 41 hits), B (speaker portal, 50 hits), C (organizer, 56 hits), D (shared/user, 45 hits)
- EN translation value must be a **character-for-character copy** of the hardcoded string replaced (same casing, punctuation, whitespace) — this is what keeps co-located tests passing
- Non-EN locales: add key with `[MISSING]` prefix as placeholder
- Excluded: `PrivacyPage.tsx`, `SupportPage.tsx` (intentionally German)
- Full hardcoded detail: `/tmp/hardcoded_prod_detail.txt`

**Phase 3 — Test Resilience:**
- 3A: Add `data-testid` attributes to components (naming: `[feature]-[component]-[element]` kebab-case)
- 3B: Refactor Vitest unit tests — replace `getByText('...')` with `getByRole(..., { name: /regex/i })` or `getByTestId`
- 3C: Refactor Playwright E2E — replace `button:has-text('...')`, `text=/regex/i`, `locator('h1')` with `getByRole` or `getByTestId`
- Keep data-content assertions intact (e.g., `text=BATbern57`)

**Phase 4 — Unused Key Analysis:**
- Detect dynamic `t()` patterns (`` t(`prefix.${var}`) ``) to build prefix exclusion list
- Cross-reference prop-drilling patterns (`label: t(...)`, `title: t(...)`)
- Classify 830 flagged keys into: Definitely unused / Possibly dynamic / Needs manual check (target: <50 in last bucket)
- Delete confirmed unused keys from all 9 locale files
- Commit `scripts/i18n/analyze-unused.py` and `docs/plans/i18n-unused-keys-report.md`

**Critical test context:**
Tests run with **real i18n** (not mocks) — `src/test/setup.ts` initialises `src/i18n/config` and calls `i18n.changeLanguage('en')`. Tests assert on rendered EN text. If an EN translation value differs from the hardcoded string it replaces — even in capitalisation — co-located `getByText` tests will break.

**Key new files:**
```
scripts/i18n/scan-hardcoded.py               — Phase 2 scanner
scripts/i18n/analyze-unused.py              — Phase 4 analyzer
docs/plans/i18n-unused-keys-report.md       — Phase 4 output report
```

**Key modified files:**
```
web-frontend/public/locales/en/*.json       — canonical EN locale (all 10 namespaces)
web-frontend/public/locales/{de,fr,it,rm,es,fi,nl,ja}/*.json  — all other locales
web-frontend/src/components/**/*.tsx        — data-testid additions (Phase 3A)
web-frontend/src/**/__tests__/**/*.test.tsx — unit test refactors (Phase 3B)
web-frontend/e2e/**/*.spec.ts               — E2E refactors (Phase 3C)
```

**Definition of Done (Story 10.9):**
- [ ] Phase 1: All 169 cross-namespace duplicates resolved; awkward `common` key paths renamed; all 9 locales in sync; `npm run test -- --run` 0 failures; `npm run type-check` passes; Playwright `chromium` passes
- [ ] Phase 2: 0 hardcoded UI strings in 78 scoped files (re-run scanner); all high-risk test files verified green (RegistrationWizard ×197, InvitationResponsePage ×91, ParticipantBatchImportModal ×74, PartnerFilters ×70, FilterSidebar ×61, CompanyAutocomplete ×63); all 3 Playwright projects pass
- [ ] Phase 3: 0 `button:has-text()`, 0 `text=/...translation.../i`, 0 `locator('h1/h2/label/tbody tr')` in any spec file; 0 `getByText('EN label')` in unit tests; all 3 Playwright projects pass; `data-testid` convention followed
- [ ] Phase 4: `analyze-unused.py` committed and runnable; "Definitely unused" keys deleted; "Needs manual check" ≤ 50 keys; `i18n-unused-keys-report.md` committed; full test suite passes after deletion
- [ ] No `i18next: missing key` warnings in browser console for EN locale after each phase
- [ ] `npm run lint` passes after each phase

---

**END OF EPIC 10**
