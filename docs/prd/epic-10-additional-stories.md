# Epic 10: Additional Stories

**Status:** 🔨 **IN PROGRESS** — Stories 10.1–10.4 in sprint (2026-02-24). Stories 10.19–10.25 added (2026-03-02). Story 10.26 added (2026-03-11).

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
**Status**: done
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
- [x] V67 migration runs cleanly; subscribe/unsubscribe endpoints work without auth
- [x] Registration with newsletter checkbox auto-subscribes (silent, no UI change)
- [x] Homepage footer widget subscribes anonymous users; duplicate → 409 shown inline
- [x] `/unsubscribe?token=valid` confirms and unsubscribes; invalid token → error state
- [x] `/account` Settings → Notifications shows newsletter toggle for authenticated users
- [x] EventPage → Newsletter tab: subscriber count, send history, preview, send/reminder buttons with confirmation
- [x] Every sent email contains `{{unsubscribeLink}}` footer link (GDPR)
- [x] Newsletter templates visible/editable in admin Email Templates tab (NEWSLETTER category)
- [x] OpenAPI spec committed before any backend implementation (ADR-006)
- [x] All tests pass: `NewsletterSubscriberServiceTest`, `NewsletterControllerIntegrationTest`, `NewsletterEmailServiceTest` + 3 frontend tests
- [x] Type-check passes, no TypeScript errors; Checkstyle passes
- [x] i18n: `newsletter.*` keys in both `en.json` and `de.json`

**Robustness Addendum (2026-03-10):** Fixed P0 thread-pool overflow bug (only ~110/3000 emails actually sent).
Redesigned `sendNewsletter()` as fire-and-forget with single `@Async` background job + paginated processing (50/page).
Added: V87 migration (status tracking), duplicate-send prevention (409), SES rate limiting (70ms/email),
retry for PARTIAL/FAILED sends, progress polling endpoint, frontend LinearProgress UI.
See `docs/plans/newsletter-robustness-v87-plan.md` for full details.

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

### Story 10.10: Registration Status Indicator for Logged-in Users

**Status**: ready-for-dev
**Prerequisite**: None (independent — uses existing registration + auth APIs)

**User Story:**
As a **logged-in attendee**, I want to see my registration status for an upcoming event directly on the public homepage and event cards, so that I don't accidentally try to register twice and I always know my current status at a glance.

**Background:**
Currently, a logged-in user who has already registered for an event sees the same public homepage as an anonymous visitor — there is no visual indication that they are already registered. The only way to discover this is by clicking "Register" and submitting the form, at which point the backend returns an error. This creates confusion and poor UX for returning attendees.

The `RegistrationService.createRegistration()` already handles duplicate detection (returns existing pending registration or throws `IllegalStateException` for confirmed). A new **read-only status endpoint** will allow the frontend to efficiently check the current user's status without side effects.

**Scope:**

**Backend — New Read-Only Status Endpoint:**
- `GET /api/v1/events/{eventCode}/my-registration` — returns the current authenticated user's registration status for this event (or 404 if not registered)
- Response: `{ registrationCode, status, registrationDate }` — minimal DTO, no PII duplication
- Auth: requires `ROLE_USER` (any authenticated role); anonymous requests → 401
- Implementation: `RegistrationRepository.findByEventCodeAndAttendeeUsername(eventCode, username)` — new query method
- No new DB migration needed — reads from existing `registrations` table
- Cache: 5-minute Caffeine cache keyed by `(eventCode, username)` — invalidated on registration mutation events

**Frontend — Public Homepage Status Banner:**
- After authentication check resolves, `useMyRegistration(eventCode)` hook fires for the "current event" (most recent `AGENDA_PUBLISHED`/`AGENDA_FINALIZED`/`EVENT_LIVE` event)
- If status = `registered` → amber info banner below hero: "You are registered for this event. [Manage Registration]"
- If status = `confirmed` → green success banner: "Your registration is confirmed. We'll see you there! [Manage Registration]"
- If status = `waitlist` → blue info banner: "You are on the waitlist for this event. We'll notify you if a spot opens. [Manage Registration]"
- If status = `cancelled` → grey banner: "Your registration was cancelled. [Register again]"
- If not registered or anonymous → no banner (existing CTA "Register Now" shown as before)
- "Manage Registration" links to `/register/{eventCode}` (RegistrationWizard shows status; deregistration from Story 10.12)

**Frontend — Event Cards (Archive / Upcoming List):**
- `EventCard.tsx` receives optional `myRegistrationStatus?: string` prop
- If status present, a small status chip overlays the top-right corner of the card:
  - ✅ confirmed (green) · 🕐 registered (amber) · ⏳ waitlist (blue) · ✗ cancelled (grey)
- Parent component (`ArchivePage`, `UpcomingEventsSection`) passes status only for past 12 months (to avoid N+1 on full archive)

**Frontend — Registration Wizard (RegistrationWizard.tsx):**
- On step 1 load, if `useMyRegistration` returns data, show "You are already registered" with current status + option to continue to deregistration (Story 10.12)
- This replaces the current confusing error-on-submit behavior

**Key new files:**
```
web-frontend/src/hooks/useMyRegistration.ts
web-frontend/src/components/public/RegistrationStatusBanner.tsx
```

**Key modified files:**
```
docs/api/events.openapi.yml                            — GET /events/{eventCode}/my-registration (FIRST)
services/event-management-service/.../repository/RegistrationRepository.java  — new query method
services/event-management-service/.../service/RegistrationService.java         — getMyRegistration()
services/event-management-service/.../controller/EventController.java          — new endpoint
web-frontend/src/pages/public/HomePage.tsx             — RegistrationStatusBanner integration
web-frontend/src/components/public/EventCard.tsx       — status chip overlay
web-frontend/src/components/public/RegistrationWizard.tsx  — already-registered guard
public/locales/de/registration.json + en/registration.json — registrationStatus.* keys
```

**Definition of Done (Story 10.10):**
- [ ] OpenAPI spec committed before backend implementation (ADR-006)
- [ ] TDD: `RegistrationStatusIntegrationTest` written first — tests 200 (registered), 200 (confirmed), 200 (waitlist), 404 (not registered), 401 (anonymous)
- [ ] Authenticated user who is confirmed sees green banner on homepage; non-registered user sees no banner
- [ ] Banner shows within 500ms of page load (no layout shift — skeleton placeholder during fetch)
- [ ] Registration wizard shows status guard if already registered
- [ ] Event cards show status chip for events registered within past 12 months
- [ ] Cache invalidated on registration creation (Story 10.12 deregistration must also invalidate)
- [ ] i18n: `registrationStatus.*` keys in de/en registration files; no hardcoded strings
- [ ] Type-check passes; Checkstyle passes; `npm run lint` passes

---

### Story 10.11: Venue Capacity Enforcement & Waitlist Management

**Status**: done
**Prerequisite**: Story 10.10 (registration status indicator) recommended first for UX coherence; technically independent

**User Story:**
As an **organizer**, I want to set a maximum capacity for an event based on venue size, so that we never overbook the venue. When the event is full, new registrations automatically go to a waitlist. When a registered attendee cancels, the next person on the waitlist is automatically promoted and notified.

As an **attendee**, I want to know my position on the waitlist and be automatically promoted if a spot opens up.

**Background:**
Currently, `Registration.status` has a `waitlist` value in the enum but there is no enforcement logic — any number of registrations are accepted. The `Event` entity has a `venueCapacity` field that is already stored (from venue management) but is not used for registration gating. This story wires the two together.

**Scope:**

**Backend — Capacity Enforcement:**
- Flyway **V73**: Add `waitlist_position` column to `registrations` table (nullable INTEGER; NULL for non-waitlist registrations); add `registration_capacity` to `events` table (nullable INTEGER; NULL = unlimited, preserving backward compatibility)
- `RegistrationService.createRegistration()` extended:
  1. Count `registrations` with status IN (`registered`, `confirmed`) for the event
  2. Load event's `registrationCapacity` (nullable)
  3. If capacity is NULL or count < capacity → create with status `registered` (existing behavior)
  4. If count >= capacity → create with status `waitlist`; assign `waitlistPosition = nextWaitlistPosition(eventId)`
- `nextWaitlistPosition(eventId)`: `MAX(waitlist_position) + 1` for the event (0 if first)
- New `WaitlistPromotionService`:
  - `promoteFromWaitlist(eventCode)`: finds the registration with the lowest `waitlistPosition` for this event, updates status to `registered`, clears `waitlist_position`, sends promotion email
  - Called by `RegistrationService.cancelRegistration()` (Story 10.12) after every successful cancellation
  - `WaitlistPromotionEmailService`: sends `waitlist-promotion-de/en.html` email template (new classpath templates seeded by `EmailTemplateSeedService`)

**Backend — Capacity Management API:**
- `PATCH /api/v1/events/{eventCode}` already exists — extend request DTO to include `registrationCapacity: Integer` (nullable)
- `GET /api/v1/events/{eventCode}` response extended with `registrationCapacity`, `confirmedCount`, `waitlistCount`, `spotsRemaining` (computed: capacity - confirmedCount, null if unlimited)
- Organizer-only endpoints; public read access to `spotsRemaining` and `waitlistCount` only (no PII)

**Frontend — Organizer Attendees Tab:**
- `EventParticipantsTab.tsx` extended:
  - Capacity bar at top: `[███████░░░] 42/60 confirmed · 3 on waitlist`
  - Two sections in `EventParticipantList`: "Registered / Confirmed" (existing table) + collapsible "Waitlist" section (table with `waitlistPosition` column shown)
  - `RegistrationActionsMenu` for waitlist rows: "Promote to Registered" (manual promotion, bypasses auto logic), "Remove from Waitlist"
- `EventSettingsTab.tsx` or event edit form: "Registration Capacity" numeric field (blank = unlimited)
- Capacity editable only when event status is not `ARCHIVED`

**Frontend — Public Homepage & Registration Wizard:**
- `RegistrationWizard.tsx` Step 1: if `spotsRemaining === 0`, show "This event is full — you will be added to the waitlist" info alert before form submission; user must acknowledge
- `RegistrationStatusBanner.tsx` (Story 10.10): add waitlist position display: "You are #3 on the waitlist"
- `HomePage.tsx`: "X spots remaining" or "Full — join waitlist" badge on event hero if capacity is set

**Email Templates (new classpath content fragments):**
```
services/event-management-service/src/main/resources/email-templates/waitlist-promotion-de.html
services/event-management-service/src/main/resources/email-templates/waitlist-promotion-en.html
services/event-management-service/src/main/resources/email-templates/waitlist-confirmation-de.html
services/event-management-service/src/main/resources/email-templates/waitlist-confirmation-en.html
```
All use `batbern-default` layout. Variables: `recipientName`, `eventTitle`, `eventCode`, `eventDate`, `venueAddress`, `registrationCode`.

**Key new files:**
```
services/event-management-service/src/main/resources/db/migration/V73__add_capacity_and_waitlist.sql
services/event-management-service/.../service/WaitlistPromotionService.java
services/event-management-service/.../service/WaitlistPromotionEmailService.java
services/event-management-service/.../service/WaitlistPromotionServiceTest.java
web-frontend/src/components/organizer/EventPage/WaitlistSection.tsx
web-frontend/src/components/public/CapacityIndicator.tsx
```

**Key modified files:**
```
docs/api/events.openapi.yml                                      — capacity fields + waitlist response (FIRST)
services/event-management-service/.../domain/Registration.java   — waitlistPosition field
services/event-management-service/.../domain/Event.java          — registrationCapacity field
services/event-management-service/.../service/RegistrationService.java — capacity enforcement
services/event-management-service/.../repository/RegistrationRepository.java — waitlist queries
web-frontend/src/components/organizer/EventPage/EventParticipantsTab.tsx — capacity bar + waitlist section
web-frontend/src/components/public/RegistrationWizard.tsx        — waitlist acknowledgment
web-frontend/src/pages/public/HomePage.tsx                       — capacity badge
public/locales/de/events.json + en/events.json                   — waitlist.* keys
public/locales/de/registration.json + en/registration.json       — waitlist.* keys
```

**Definition of Done (Story 10.11):**
- [x] V73 migration runs cleanly; `waitlist_position` nullable; `registration_capacity` nullable on events
- [x] TDD: `WaitlistPromotionServiceTest` and `RegistrationCapacityIntegrationTest` written first (RED→GREEN confirmed)
- [x] Creating registration when event is full → status=waitlist with correct sequential position (1, 2, 3…)
- [ ] Cancelling a registration when waitlist exists → first waitlisted person auto-promoted + email sent *(Story 10.12 — `cancelRegistration` endpoint implemented there; `WaitlistPromotionService.promoteFromWaitlist()` ready)*
- [x] Manual promotion by organizer works from attendees tab (`POST /{eventCode}/registrations/{registrationCode}/promote`)
- [x] Organizer can set/clear capacity from event settings; NULL = unlimited (existing events unaffected)
- [x] Waitlist email templates seeded (`registration-waitlist-confirmation-de/en`, `waitlist-promotion-de/en`) and editable via Email Templates admin tab (Story 10.13)
- [x] Public homepage shows capacity badge when capacity is set (`CapacityIndicator` in logistics section)
- [x] Registration wizard shows waitlist acknowledgment when full (Alert + Checkbox in Step 2; submit disabled until acknowledged; waitlist-specific success title)
- [x] `RegistrationStatusBanner` shows "You are #N on the waitlist" when `waitlistPosition` is set (AC13)
- [x] OpenAPI spec committed before implementation (ADR-006) — T1 done first
- [x] i18n: `waitlist.*` and `capacity.*` keys in all 10 locales; type-check passes; lint passes
- [x] `RegistrationStatus` type corrected: `WAITLISTED` → `WAITLIST` (consistent with OpenAPI fix)

---

### Story 10.12: Self-Service Deregistration

**Status**: ready-for-dev
**Prerequisite**: Story 10.11 (waitlist promotion must fire on cancellation)

**User Story:**
As a **registered attendee**, I want to cancel my event registration easily without contacting an organizer — either by clicking a link in my confirmation email or by entering my email address on the event page — so that my spot can be given to someone else.

**Background:**
Currently there is no self-service cancellation path. The `Registration.status` enum includes `cancelled` but no endpoint or UI supports transitioning to it from the attendee side. Organizers can manually cancel from the attendees tab but this requires contacting the organizer. The todo explicitly requests:
1. A non-expiring magic link embedded in the confirmation email
2. An email-input form on the public portal for deregistration

**Scope:**

**Backend — Deregistration Token:**
- Flyway **V74**: Add `deregistration_token` column to `registrations` (UUID, NOT NULL, unique — generated on creation, never rotated, never expires)
- `RegistrationService.createRegistration()` extended: generate UUID deregistration token and persist
- Migration backfill: `UPDATE registrations SET deregistration_token = gen_random_uuid() WHERE deregistration_token IS NULL`

**Backend — Deregistration Endpoints:**
```
# Public (no auth required — token IS the auth)
GET  /api/v1/registrations/deregister/verify?token={uuid}
     → 200: { registrationCode, eventCode, eventTitle, eventDate, attendeeFirstName }
     → 404: { error: "invalid_token" } (token not found or already cancelled)

POST /api/v1/registrations/deregister
     body: { token: uuid }
     → 200: success; fires waitlist promotion (Story 10.11)
     → 404: invalid token
     → 409: already cancelled

# Public (no auth required — email verification flow)
POST /api/v1/registrations/deregister/by-email
     body: { email: string, eventCode: string }
     → 200: always (anti-enumeration: "if registered, you'll receive a deregistration email")
     Sends email with deregistration link (containing token) if registration found
```

**Email Template:**
- `deregistration-link-de.html` + `deregistration-link-en.html` — content fragments with `batbern-default` layout
- Variables: `recipientName`, `eventTitle`, `eventCode`, `eventDate`, `deregistrationLink`
- `deregistrationLink` = `{baseUrl}/deregister?token={deregistrationToken}`
- Also embed deregistration link in the existing registration **confirmation** email: "To cancel: [Cancel Registration]"

**Frontend — Deregistration Page (`/deregister?token=`):**
- Public route (no auth required)
- Step 1 (token verify): shows event title, date, attendee name — "Are you sure you want to cancel your registration?"
- "Confirm Cancellation" button → POST /deregister → success state
- Invalid/used token → error state with contact info
- Already cancelled → "Your registration was already cancelled" state
- Component: `DeregistrationPage.tsx` (new public page, same pattern as `UnsubscribePage.tsx`)

**Frontend — Deregistration via Email Form:**
- On `HomePage.tsx` (when event is in `AGENDA_PUBLISHED`/`AGENDA_FINALIZED`/`EVENT_LIVE` state):
  - Secondary link "Cancel your registration" below registration CTA
  - Opens `DeregistrationByEmailModal.tsx` — email + eventCode input → submit → shows "Check your inbox" message
- On `RegistrationWizard.tsx` (when user is already registered — Story 10.10 guard):
  - "Cancel my registration" button alongside status display → opens same modal

**Frontend — Organizer Attendees Tab:**
- Cancelled registrations shown in table with grey `CANCELLED` chip
- Existing `RegistrationActionsMenu` already has cancel action for organizers — ensure it uses the same `RegistrationService.cancelRegistration()` that triggers waitlist promotion

**Key new files:**
```
services/event-management-service/src/main/resources/db/migration/V74__add_deregistration_token.sql
services/event-management-service/src/main/resources/email-templates/deregistration-link-de.html
services/event-management-service/src/main/resources/email-templates/deregistration-link-en.html
services/event-management-service/.../service/DeregistrationService.java
services/event-management-service/.../controller/DeregistrationController.java
services/event-management-service/.../service/DeregistrationServiceTest.java
services/event-management-service/.../controller/DeregistrationControllerIntegrationTest.java
web-frontend/src/pages/public/DeregistrationPage.tsx
web-frontend/src/components/public/DeregistrationByEmailModal.tsx
web-frontend/src/hooks/useDeregistration.ts
```

**Key modified files:**
```
docs/api/events.openapi.yml                                  — deregistration endpoints (FIRST)
services/event-management-service/.../domain/Registration.java — deregistrationToken field
services/event-management-service/.../service/RegistrationService.java — generate token on create; cancelRegistration() triggers waitlist promotion
services/event-management-service/.../service/RegistrationEmailService.java — add deregistrationLink variable to confirmation email
services/event-management-service/.../config/SecurityConfig.java — permitAll /deregister/** public paths
api-gateway/.../SecurityConfig.java                          — permitAll /api/v1/registrations/deregister/**
web-frontend/src/App.tsx                                     — /deregister public route
web-frontend/src/pages/public/HomePage.tsx                   — "Cancel registration" link
web-frontend/src/components/public/RegistrationWizard.tsx    — cancel button when already registered
public/locales/de/registration.json + en/registration.json   — deregistration.* keys
```

**Definition of Done (Story 10.12):**
- [ ] V74 migration runs cleanly; backfill generates tokens for all existing registrations
- [ ] TDD: `DeregistrationServiceTest` + `DeregistrationControllerIntegrationTest` written first
- [ ] Valid token → verify shows correct event/attendee info; confirm → status=cancelled; waitlist promotion fires (Story 10.11)
- [ ] Used/invalid token → 404; already cancelled → 409
- [ ] By-email flow: valid email → sends deregistration link email; unknown email → 200 (anti-enumeration, no email sent)
- [ ] Confirmation email (existing) includes deregistration link
- [ ] `/deregister?token=valid` page renders correctly (no auth required)
- [ ] Organizer cancellation via attendees tab also triggers waitlist promotion
- [ ] Deregistration email templates seeded and editable in admin Email Templates tab
- [ ] OpenAPI spec committed before implementation (ADR-006)
- [ ] i18n: `deregistration.*` keys in de/en; Type-check passes; Checkstyle passes

---

### Story 10.13: Registration & Portal Email Templates — Editable in Admin

**Status**: ready-for-dev
**Prerequisite**: Story 10.2 (Email Template Management — provides DB storage, seed service, admin UI)

**User Story:**
As an **organizer**, I want the registration confirmation email and all speaker portal emails to be editable from the Email Templates admin tab — just like speaker invitation emails — so that I can update their subject and content without a code deploy.

**Background:**
Story 10.2 seeded and made editable 22 classpath email templates (speaker invitations, task reminders, newsletter, etc.). However, two important categories were explicitly left out of the initial UI:
1. **Registration emails**: `registration-confirmation-de/en.html` (sent by `RegistrationEmailService`)
2. **Speaker portal registration emails**: Emails sent when a speaker creates their portal account or responds to an invitation via the portal (`SpeakerAcceptanceEmailService`, `PortalRegistrationEmailService`)

Additionally, organizers have requested a shortcut to jump directly to an event-relevant email template from the event page (e.g., "Edit Registration Template" from the event attendees tab) without navigating to the admin page manually.

**Scope:**

**Backend — Seed Additional Templates:**
- `EmailTemplateSeedService.@PostConstruct` extended to seed:
  - `registration-confirmation-de/en` (REGISTRATION category) — from existing classpath files
  - `registration-waitlist-confirmation-de/en` (REGISTRATION category) — from Story 10.11 files
  - `deregistration-link-de/en` (REGISTRATION category) — from Story 10.12 files
  - `waitlist-promotion-de/en` (REGISTRATION category) — from Story 10.11 files
  - `portal-registration-de/en` (SPEAKER category) — speaker portal account creation email
- All new templates: system templates (editable, not deletable)
- New `REGISTRATION` category added to `EmailTemplateCategory` enum (if not already present)

**Frontend — Email Templates Tab Enhancement:**
- `EmailTemplatesTab.tsx`: Add `REGISTRATION` category to the category filter (alongside Speaker, Task Reminders, Newsletter)
- Registration templates visible and editable in same TinyMCE editor as other content templates
- Preview merges with `batbern-default` layout and renders branded email preview

**Frontend — Quick Template Access from Event Pages:**
- `EventParticipantsTab.tsx`: "Edit Registration Email Template" icon-button (top-right) → opens `EmailTemplateQuickEditDrawer.tsx` with `registration-confirmation-{locale}` pre-loaded
- `EventParticipantsTab.tsx`: "Edit Waitlist Confirmation Email" icon-button → opens drawer with `registration-waitlist-confirmation-{locale}`
- `EventParticipantsTab.tsx`: "Edit Deregistration Email" icon-button → `deregistration-link-{locale}`
- `EmailTemplateQuickEditDrawer.tsx`: Right-side MUI Drawer (480px); embeds the same `EmailTemplateEditModal` content but without navigating away from the event page. Header shows "Email Template: [name]" + locale toggle + "Open in Admin" link

**Key new files:**
```
web-frontend/src/components/organizer/EventPage/EmailTemplateQuickEditDrawer.tsx
```

**Key modified files:**
```
services/event-management-service/.../service/EmailTemplateSeedService.java  — seed REGISTRATION category templates
services/event-management-service/.../domain/EmailTemplateCategory.java      — add REGISTRATION enum value
web-frontend/src/components/organizer/Admin/EmailTemplatesTab.tsx             — add REGISTRATION category filter
web-frontend/src/components/organizer/EventPage/EventParticipantsTab.tsx     — quick template access buttons
public/locales/de/organizer.json + en/organizer.json                          — emailTemplates.categories.registration key
```

**Definition of Done (Story 10.13):**
- [ ] All registration/portal email template classpath files are seeded to DB on service startup (idempotent)
- [ ] REGISTRATION category visible in Email Templates tab filter — all seeded templates listed and editable
- [ ] TinyMCE editor opens for registration templates; preview shows branded email with batbern-default layout
- [ ] Quick-edit drawer opens from EventParticipantsTab without navigating away; changes reflect immediately in admin tab
- [ ] Existing `RegistrationEmailService` loads templates from DB with classpath fallback (same pattern as other email services)
- [ ] Waitlist and deregistration email templates (Stories 10.11, 10.12) also editable via same tab
- [ ] i18n: `emailTemplates.categories.registration` key in de/en; Type-check passes

---

### Story 10.14: Newsletter Sending with Template Selection

**Status**: ready-for-dev
**Prerequisite**: Story 10.2 (email template management), Story 10.7 (newsletter sending infrastructure)

**User Story:**
As an **organizer**, I want to choose which email template to use when sending a newsletter for an event, so that I can use different templates for different types of communications (general newsletter, event reminder, partner announcement) without needing separate template hardcoding.

**Background:**
Story 10.7 implemented newsletter sending from the `EventNewsletterTab` with a single hardcoded template key (`newsletter-event-{locale}`). The admin `EmailTemplatesTab` (Story 10.2) supports multiple templates in the `NEWSLETTER` category, but the newsletter sending UI doesn't let organizers pick which one to use — it always uses the hardcoded default. This story adds template selection to the newsletter send flow.

**Scope:**

**Backend — Newsletter Send API Extension:**
- `POST /api/v1/events/{eventCode}/newsletter/send` request body extended with optional `templateKey: string`
  - If `templateKey` is provided → use that template from DB
  - If omitted → use default `newsletter-event-{locale}` (backward compatible)
- `NewsletterEmailService.sendNewsletter()` updated to accept `templateKey` parameter
- `POST /api/v1/events/{eventCode}/newsletter/preview` same extension for preview

**Backend — Newsletter Template Listing:**
- `GET /api/v1/email-templates?category=NEWSLETTER` — already works via existing `EmailTemplateController` (no changes needed)

**Frontend — EventNewsletterTab:**
- Before "Send Newsletter" and "Send Reminder" buttons: add a "Template" select dropdown
  - Populated by `GET /api/v1/email-templates?category=NEWSLETTER` response
  - Default selection: `newsletter-event-{currentLocale}` (same as current hardcoded behavior)
  - Options: all NEWSLETTER category templates (display: template name + language badge)
- Locale toggle (DE/EN) now also affects which templates are shown in the dropdown (filtered by locale suffix)
- "Preview" iframe uses selected template for preview render
- "Create new template" shortcut link → opens admin Email Templates tab with NEWSLETTER filter pre-applied
- Confirmation dialog for "Send Newsletter" shows selected template name: "Send newsletter using '[Template Name]' to 234 subscribers?"

**Key modified files:**
```
docs/api/events.openapi.yml                                        — extend newsletter send request (FIRST)
services/event-management-service/.../service/NewsletterEmailService.java  — accept templateKey param
services/event-management-service/.../controller/NewsletterController.java — pass templateKey through
web-frontend/src/components/organizer/EventPage/EventNewsletterTab.tsx    — template selector + preview
web-frontend/src/hooks/useNewsletter/useNewsletter.ts                     — pass templateKey to API
public/locales/de/organizer.json + en/organizer.json                       — newsletter.templateSelect.* keys
```

**Definition of Done (Story 10.14):**
- [ ] Newsletter send with explicit `templateKey` uses that template; send without `templateKey` uses default (backward compatible)
- [ ] `EventNewsletterTab` shows template dropdown populated from DB templates; default = `newsletter-event-{locale}`
- [ ] Preview updates immediately when different template is selected
- [ ] Confirmation dialog names the selected template
- [ ] All existing Story 10.7 tests continue to pass
- [ ] i18n: `newsletter.templateSelect.*` keys in de/en; Type-check passes

---

### Story 10.15: Newsletter Subscription Integrity & Language Fix

**Status**: ready-for-dev
**Prerequisite**: Story 10.7 (newsletter subscriber tables + service)

**User Story:**
As a **community member**, I want my newsletter subscription language preference to match the language I was browsing in when I registered, so that I receive newsletters in my preferred language — not always in German.

As an **organizer**, I want to confirm that all newsletter opt-in paths (public registration wizard, speaker portal registration) are wired correctly so that no subscriber is silently lost.

**Background:**
Two gaps identified in the todo:

**Gap 1 — Language hardcoding in RegistrationService:**
`RegistrationService.createRegistration()` (line 146) hardcodes `"de"` as the newsletter subscription language when a user opts in during registration:
```java
newsletterSubscriberService.subscribe(request.getEmail(), request.getFirstName(), "de", "registration", username);
```
This should use the user's `communicationPreferences.preferredLanguage` (already in the `CreateRegistrationRequest` DTO) or fall back to the browser Accept-Language header.

**Gap 2 — Speaker portal registration:**
When a speaker creates their BATbern account via the self-service portal (Epic 9 / Speaker Authentication), there is no newsletter opt-in checkbox or auto-subscription. The `SpeakerPortalRegistrationService` (if it exists) should respect the speaker's language preference for any newsletter subscription.

**Scope:**

**Backend — Language Fix (RegistrationService):**
- `CreateRegistrationRequest` already has `communicationPreferences.preferredLanguage` (String, nullable)
- `RegistrationService.createRegistration()`: replace hardcoded `"de"` with:
  ```java
  String lang = Optional.ofNullable(request.getCommunicationPreferences())
      .map(p -> p.getPreferredLanguage())
      .filter(l -> l != null && !l.isBlank())
      .orElse("de");  // fallback to German
  ```
- Unit test: `RegistrationServiceTest` — assert newsletter subscription uses `preferredLanguage` from request

**Backend — Speaker Portal Newsletter Opt-in (if 9.x speaker auth service exists):**
- If `SpeakerPortalAccountService` or equivalent exists in `speaker-coordination-service`:
  - Add `newsletterOptIn: boolean` field to speaker portal registration request DTO
  - On account creation: call `NewsletterSubscriberService.subscribe()` using speaker's language preference if `newsletterOptIn = true`
  - If speaker-coordination-service cannot call newsletter service directly (cross-service boundary): emit `SpeakerPortalRegisteredEvent` via shared-kernel domain event; EMS consumes and subscribes
- If Epic 9 speaker auth is not yet deployed: scaffold the opt-in field in the DTO + stub the wiring; the feature becomes active when Story 9.x (Cognito account creation) is implemented

**Frontend — Registration Wizard:**
- `RegistrationWizard.tsx` Step 3 (Communication Preferences): ensure `preferredLanguage` field value is passed in `CreateRegistrationRequest` payload
- Currently the wizard likely sends the i18n language from the UI — verify and fix if not

**Frontend — Speaker Portal Registration (Epic 9 alignment):**
- `SpeakerPortalRegistrationPage.tsx` (if exists): add "Subscribe to BATbern newsletter" checkbox in account creation form, defaulting to checked

**Testing — Newsletter Opt-in Integration Tests:**
- `NewsletterOptInIntegrationTest`: POST registration with `newsletterSubscribed=true` + `preferredLanguage=en` → subscriber created with `language=en`
- POST registration with `newsletterSubscribed=true` + no language → subscriber created with `language=de` (fallback)
- POST registration with `newsletterSubscribed=false` → no subscriber created
- POST registration with `newsletterSubscribed=true` for already-subscribed email → idempotent (no error, subscriber unchanged)

**Key modified files:**
```
services/event-management-service/.../service/RegistrationService.java    — language from request
services/event-management-service/.../service/RegistrationServiceTest.java — new language assertions
services/speaker-coordination-service/.../ (if exists)                    — newsletter opt-in on portal registration
web-frontend/src/components/public/RegistrationWizard.tsx                  — ensure lang passed in payload
```

**Definition of Done (Story 10.15):**
- [ ] `RegistrationService` uses `preferredLanguage` from request (not hardcoded "de"); unit test proves it
- [ ] Integration test: registration with `preferredLanguage=fr` → newsletter subscriber created with `language=fr`
- [ ] Registration with `newsletterSubscribed=false` → no newsletter subscriber created (negative test)
- [ ] Speaker portal newsletter opt-in wired (or scaffolded with TODO comment linked to Epic 9 story)
- [ ] No regression in existing newsletter tests (Story 10.7 suite passes)
- [ ] Type-check passes; Checkstyle passes

---

### Story 10.16: AI-Assisted Event Content Creation

**Status**: ready-for-dev
**Prerequisite**: Story 10.2 (email template management introduces OpenAI dependency pattern)

**User Story:**
As an **organizer**, I want to use AI assistance to generate a polished event description from a topic title, create a themed event image, and get a quality summary of a speaker's abstract — so that I can produce professional event content in minutes instead of hours.

**Background:**
The codebase has keyword-based topic classification (`BatbernTopicClusterService`) but no generative AI integration. The `TrendingTopicsService` has a comment noting "Optional — falls back gracefully when API key absent", indicating OpenAI integration was always planned. This story introduces a controlled, optional AI layer:
- All AI calls are **gated by `batbern.ai.enabled: ${AI_ENABLED:false}`** — disabled by default; enabled in staging/prod via environment variable
- All AI calls have **graceful fallbacks** — if disabled or if the API call fails, the UI degrades to manual input
- **OpenAI API** (GPT-4o for text, DALL-E 3 for images) via the `openai-java` SDK

**Scope:**

**Backend — AI Service Infrastructure:**
- New dependency: `com.theokanning.openai-gpt3-java:service:{version}` (or `com.openai:openai-java:1.x.x` — latest official SDK)
- `AiConfig.java`: bean configuration; reads `${OPENAI_API_KEY:}` and `${batbern.ai.enabled:false}`; returns no-op stub when disabled
- `BatbernAiService.java`: central service wrapping OpenAI SDK:
  - `generateEventDescription(topicTitle, topicCategory, eventNumber): String` → GPT-4o prompt: "Write a 2-paragraph German event description for BATbern#{n}, a Swiss software architecture conference. Topic: {topicTitle}. Style: professional, enthusiastic, 150-200 words."
  - `generateThemeImage(topicTitle, topicCategory): String (S3 URL)` → DALL-E 3 prompt: "Abstract illustration for a software architecture conference themed '{topicTitle}', dark navy and blue tones, Swiss minimalist style, no text"; result uploaded to S3; returns presigned URL
  - `analyzeAbstract(abstract, speakerName): AbstractAnalysisResult` → GPT-4o: returns `{ noPromotionScore: 1-10, noPromotionFeedback: string, lessonsLearnedScore: 1-10, lessonsLearnedFeedback: string, wordCount: integer, shortenedAbstract: string|null }` — scores absence-of-promotion and presence-of-lessons-learned; provides shortened German abstract when >160 words
- All methods: `if (!aiEnabled) return Optional.empty()` — callers handle absent result gracefully
- Results cached 1 hour by input hash via Caffeine (max 500 entries); OpenAI-side rate limits apply for burst protection
- Flyway **V75**: `ai_generation_log` table — `(id, event_code, type, input_hash, generated_at, tokens_used, was_accepted)` — for cost monitoring

**Backend — New AI Endpoints:**
```
# Organizer only
POST /api/v1/events/{eventCode}/ai/description
     body: { topicTitle, topicCategory }
     response: { description: string } | 503 (if AI disabled)

POST /api/v1/events/{eventCode}/ai/theme-image
     body: { topicTitle, topicCategory }
     response: { imageUrl: string, s3Key: string } | 503 (if AI disabled)

POST /api/v1/speakers/{speakerId}/ai/analyze-abstract
     body: { abstract: string, speakerName?: string }
     response: { noPromotionScore, noPromotionFeedback, lessonsLearnedScore, lessonsLearnedFeedback, wordCount, shortenedAbstract } | 503 (if AI disabled)

POST /api/v1/events/{eventCode}/ai/theme-image/apply
     body: { imageUrl: string }
     response: 200 | 404 (event not found) — persists imageUrl on event record
```

**Frontend — AI Assist Buttons (Organizer):**

*Event Description (EventSettingsTab or EventOverviewTab):*
- "Event Description" textarea: right-aligned "✨ Generate with AI" ghost button (only if `aiEnabled=true` feature flag from `/api/v1/public/settings` response)
- Click → loading spinner → inserts generated text into textarea (user can edit before saving)
- "Regenerate" button appears after first generation

*Theme Image (EventSettingsTab):*
- "Theme Image" section: "✨ Generate theme image with AI" button alongside existing file upload
- Click → "Generating image…" skeleton → generated image preview with "Use this image" / "Regenerate" / "Upload my own" choices
- "Use this image" → stores S3 key on event record via PATCH event endpoint

*Abstract Analysis (QualityReviewDrawer — reached from EventSpeakersTab):*
- "Analyze Abstract" button inside `QualityReviewDrawer` (visible to organizers only, gated on `aiContentEnabled`, appears only when abstract is non-empty)
- Click → inline results: two score badges (`noPromotionScore`, `lessonsLearnedScore`, each 1-10 color-coded), per-score German feedback text, optional shortened abstract accordion (when original >160 words) with copy button
- Original abstract unchanged

**Frontend — AI Feature Flag:**
- `useFeatureFlags()` hook reads from `/api/v1/public/settings/presentation` (or new `/api/v1/public/settings/features`) response
- `{ aiContentEnabled: boolean }` — all AI UI only shown when flag is true
- Feature flag endpoint returns false when `batbern.ai.enabled=false` (zero frontend changes for on/off toggle)

**Key new files:**
```
services/event-management-service/src/main/resources/db/migration/V77__create_ai_generation_log.sql
services/event-management-service/.../config/AiConfig.java
services/event-management-service/.../service/BatbernAiService.java
services/event-management-service/.../service/BatbernAiServiceTest.java
services/event-management-service/.../controller/AiAssistController.java
services/event-management-service/.../controller/AiAssistControllerIntegrationTest.java
web-frontend/src/hooks/useAiAssist.ts
web-frontend/src/hooks/useFeatureFlags.ts
web-frontend/src/components/organizer/EventPage/AiAssistDrawer.tsx
```

**Key modified files:**
```
docs/api/events-api.openapi.yml                                         — AI assist endpoints + /apply (FIRST)
services/event-management-service/.../security/SecurityConfig.java       — permitAll for /public/settings/features
web-frontend/src/components/organizer/EventPage/EventOverviewTab.tsx     — AI description + image buttons
web-frontend/src/components/organizer/SpeakerStatus/QualityReviewDrawer.tsx — abstract analysis (design pivot: merged here)
public/locales/de/organizer.json + en/organizer.json + 8 others          — aiAssist.* keys (all 10 locales)
```

**Definition of Done (Story 10.16):** ✅ COMPLETE (2026-03-03)
- [x] `AI_ENABLED=false` (default): all AI endpoints return 503; AI buttons hidden from UI (gated on `useFeatureFlags().aiContentEnabled`); all existing tests pass unchanged
- [x] `AI_ENABLED=true` + valid `OPENAI_API_KEY`: description generation returns German text within 10s; image generation returns S3 URL; `/apply` persists image URL on event
- [x] Graceful degradation: if OpenAI API returns error → UI shows "KI-Generierung fehlgeschlagen, bitte manuell eingeben" toast
- [x] `ai_generation_log` table (V77) records each generation attempt with event_code, type, input_hash, generated_at
- [x] Abstract analysis (in `QualityReviewDrawer`) shows noPromotion + lessonsLearned scores with German feedback; optional shortened abstract
- [x] TDD: `BatbernAiServiceTest` (6 unit tests); `AiAssistControllerIntegrationTest` (9 integration tests, including /apply)
- [x] OpenAPI spec updated first (ADR-006): all 5 AI endpoints documented including /apply
- [x] i18n: `aiAssist.*` keys in all 10 locales; Type-check ✅; Checkstyle ✅

---

### Story 10.17: Email Reply-based Unsubscribe & Deregistration (Inbound Email Handler)

**Status**: ready-for-dev
**Prerequisite**: Story 10.12 (deregistration service), Story 10.7 (newsletter unsubscribe)

**User Story:**
As a **subscriber or attendee**, I want to unsubscribe from the newsletter or cancel my event registration simply by replying "UNSUBSCRIBE" or "CANCEL" to any email from BATbern, so that I can manage my preferences without clicking any links — especially useful on mobile.

**Background:**
All outbound emails currently use `noreply@batbern.ch`. There is no inbound email processing. This story sets up AWS SES inbound email receiving and routes replies to the appropriate service action. The implementation uses the standard AWS pattern: SES Receiving → S3 (raw email storage) → SQS → Spring `@SqsListener`.

**Scope:**

**AWS Infrastructure (CDK changes in `infrastructure/`):**
- SES receiving rule set for `replies@batbern.ch` (separate from `noreply@batbern.ch` sending address)
- SES receipt rule: all mail to `replies@batbern.ch` → store to S3 bucket `batbern-inbound-emails-{env}` → send notification to SQS queue `batbern-inbound-email-{env}`
- IAM: EMS task role gains `sqs:ReceiveMessage` + `sqs:DeleteMessage` on inbound queue + `s3:GetObject` on inbound bucket
- CDK stack: `InboundEmailStack` (new) in `infrastructure/lib/`

**Backend — Inbound Email Processor (event-management-service):**
- New dependency: `io.awspring.cloud:spring-cloud-aws-starter-sqs:{version}` (already in project for other SQS work, likely)
- `InboundEmailConfig.java`: SQS listener bean configuration; reads `${AWS_INBOUND_EMAIL_QUEUE_URL}`
- `InboundEmailListenerService.java` (`@SqsListener`):
  1. Receive SQS message (S3 event notification → raw email S3 key)
  2. Fetch raw email from S3 via `S3Client.getObject()`
  3. Parse MIME message with `jakarta.mail.internet.MimeMessage` (Java Mail API)
  4. Extract: sender email (`From:` header), `In-Reply-To` / `References` headers (to match original email), and first non-quoted line of plain-text body (normalized: trim, lowercase, remove punctuation)
- `InboundEmailRouter.java`: routes parsed email to action handler based on body content:
  - Body contains `unsubscribe` / `abmelden` / `désinscription` → `NewsletterSubscriberService.unsubscribeByEmail(senderEmail)`
  - Body contains `cancel` / `deregister` / `abmelden` / `absagen` + event code (parsed from reply subject "Re: BATbernXX Registration Confirmation") → `DeregistrationService.cancelByEmail(senderEmail, eventCode)`
  - Unrecognized → log and discard; no action
- **Anti-abuse**: max 10 inbound emails per sender per hour (Caffeine rate limiter)
- **Reply confirmation**: After successful action, send a brief confirmation email ("Your newsletter subscription has been cancelled")
- **From header**: Update all outbound emails to use `Reply-To: replies@batbern.ch` (keeping `From: noreply@batbern.ch`)

**Key new files:**
```
infrastructure/lib/inbound-email-stack.ts
services/event-management-service/.../config/InboundEmailConfig.java
services/event-management-service/.../service/InboundEmailListenerService.java
services/event-management-service/.../service/InboundEmailRouter.java
services/event-management-service/.../service/InboundEmailRouterTest.java
services/event-management-service/src/main/resources/email-templates/unsubscribe-confirmation-de.html
services/event-management-service/src/main/resources/email-templates/unsubscribe-confirmation-en.html
```

**Key modified files:**
```
infrastructure/lib/batbern-stack.ts                                — add InboundEmailStack
shared-kernel/.../service/EmailService.java                        — add Reply-To: replies@batbern.ch header
services/event-management-service/build.gradle                     — spring-cloud-aws-starter-sqs (if not present)
services/event-management-service/.../service/NewsletterSubscriberService.java — unsubscribeByEmail() method
services/event-management-service/.../service/DeregistrationService.java       — cancelByEmail() method
```

**Definition of Done (Story 10.17):**
- [ ] CDK `InboundEmailStack` deploys cleanly to staging; `replies@batbern.ch` receiving rule active
- [ ] All outbound emails include `Reply-To: replies@batbern.ch` header
- [ ] Replying "unsubscribe" to a newsletter email → subscriber unsubscribed; confirmation email sent
- [ ] Replying "cancel" to a registration confirmation email → registration cancelled; waitlist promotion fires (Story 10.11/10.12); confirmation email sent
- [ ] Unknown reply body → silently discarded; no error; no action
- [ ] Rate limiter: >10 emails from same sender in 1h → excess messages discarded
- [ ] TDD: `InboundEmailRouterTest` with mocked S3/SQS; WireMock for SES in integration test
- [ ] Confirmation email templates seeded and editable in admin Email Templates tab
- [ ] Checkstyle passes; CDK synth passes

---

### Story 10.18: Event Archival Task & Notification Cleanup

**Status**: ready-for-dev
**Prerequisite**: None (bug fix / data cleanup — independent)

**User Story:**
As an **organizer**, I want all pending tasks and in-app notifications for an event to be automatically cleaned up when the event is archived, so that my task board and notification center are not cluttered with stale items from past events.

**Background (Error from todo.md):**
When an event transitions to `ARCHIVED` state via the event state machine, currently:
- Open tasks (`status != completed`) remain open and appear in organizer task boards
- Any queued task reminder emails continue to fire (if the scheduler window includes past-due tasks from archived events)
- In-app notifications referencing the archived event linger in the notification center

This is a data integrity bug — the `EventLifecycleService` (or equivalent state machine handler) does not include a cleanup step on the `ARCHIVED` transition.

**Scope:**

**Backend — Archival Cleanup:**
- `EventArchivalCleanupService.java` — called from event state machine `onEntry(ARCHIVED)` or `EventStateTransitionHandler`:
  1. **Task cancellation**: `EventTaskRepository.findByEventIdAndStatusNot(eventId, "completed")` → bulk update all to status `cancelled` + set `cancelledReason = "Event archived"` + `cancelledAt = now()`
  2. **Task reminder suppression**: `ShedLock`-based scheduler already queries for non-completed tasks due tomorrow — now task status = `cancelled` so they are naturally excluded. Verify `TaskDeadlineReminderScheduler.findTasksDueForReminder()` query excludes `cancelled` status (add if missing)
  3. **Notification dismissal**: `NotificationRepository.findByEventCodeAndReadFalse(eventCode)` → bulk mark as `read = true` + `dismissedAt = now()`; if no notification table exists, skip this step and log a warning
  4. **Registration cleanup**: Waitlisted registrations (`status = waitlist`) for the archived event → update to `cancelled`, `cancelledReason = "Event archived"`. Confirmed/registered registrations → no change (historical data preserved)
- All 4 steps in a single `@Transactional` method; failures in steps 2-4 are caught and logged but do not roll back step 1 (task cancellation is most important)
- Idempotent: running cleanup twice has no side effects (already-cancelled tasks are skipped)

**Backend — Scheduler Safety Net:**
- `TaskDeadlineReminderScheduler.findTasksDueForReminder()` JPQL query: ensure `status NOT IN ('completed', 'cancelled')` (add `cancelled` if not already excluded)
- Unit test: scheduler does NOT send reminder for tasks belonging to ARCHIVED events

**Frontend — No Changes Required:**
- Task board already filters by event; once tasks are cancelled, they naturally fall into the "Cancelled" bucket (or are hidden if the task board filters by `pending|in_progress` only)
- Notification dismissal is backend-side; frontend already respects `read=true`

**Key new files:**
```
services/event-management-service/.../service/EventArchivalCleanupService.java
services/event-management-service/.../service/EventArchivalCleanupServiceTest.java
```

**Key modified files:**
```
services/event-management-service/.../service/EventLifecycleService.java (or EventStateTransitionHandler.java)
     — call EventArchivalCleanupService.cleanup(eventCode) on ARCHIVED transition
services/event-management-service/.../scheduler/TaskDeadlineReminderScheduler.java
     — ensure 'cancelled' excluded from reminder query
services/event-management-service/.../repository/EventTaskRepository.java
     — bulk cancel query + findByEventIdAndStatusNot
services/event-management-service/.../repository/NotificationRepository.java (if exists)
     — bulk dismiss query
```

**Definition of Done (Story 10.18):**
- [ ] TDD: `EventArchivalCleanupServiceTest` written first — covers task bulk cancel, waitlist cancel, notification dismiss
- [ ] Archiving an event with 5 open tasks → all 5 tasks transition to `cancelled` within the same transaction
- [ ] Task reminder scheduler does not send emails for tasks with `status=cancelled` or belonging to `ARCHIVED` events
- [ ] Waitlisted registrations cancelled on archival; confirmed registrations preserved (historical)
- [ ] Cleanup is idempotent — running twice produces same result with no errors
- [ ] Integration test: archive event → verify DB state (task statuses, notification read flags)
- [ ] Checkstyle passes; no TypeScript changes needed

---

---

### Story 10.19: Remove Organizer Email from Public Presenter & About Pages

**Status**: ready-for-dev
**Prerequisite**: None (pure UI cleanup — independent)

**User Story:**
As an **organizer**, I want our personal email addresses removed from the public-facing About page and the moderator presentation page, so that we don't expose team members' private contact details to every event attendee.

**Background:**
The `OrganizerDisplay.tsx` component (used on `/about`) renders a clickable `mailto:` link for every organizer whose `email` field is populated. The same presenter view (`/present/:eventCode`) displays organizer bios. The general contact email `info@berner-architekten-treffen.ch` already exists in the About page Contact section, making individual emails redundant.

**Scope:**

**Frontend — OrganizerDisplay.tsx:**
- Remove the `{organizer.email && (<a href={`mailto:...`}>)}` block (lines 69–76)
- The organizer portrait, name, title, and bio remain unchanged
- No backend change needed — the email field still exists in the DB/API for organizer-internal use (e.g., task notifications); it simply must not be rendered on public pages

**Frontend — PresentationPage.tsx (if email is rendered):**
- Audit `PresentationPage.tsx` and all child components (e.g., speaker bio sections, organizer credit slides) for any `email` renders
- Remove any public-facing `mailto:` or email text renders found; retain email in organizer-only internal views

**Frontend — About i18n:**
- If any `t('contact.email')` or similar keys were used solely to label the email display, remove those uses (keys themselves may remain for Contact section)
- No new i18n keys needed

**Key modified files:**
```
web-frontend/src/components/public/About/OrganizerDisplay.tsx   — remove email block
web-frontend/src/pages/PresentationPage.tsx                     — remove any email renders (if present)
```

**Definition of Done (Story 10.19):**
- [ ] `/about` page renders all organizer cards without any email address or mailto link
- [ ] `/present/:eventCode` renders with no organizer or speaker email addresses visible
- [ ] The Contact section on `/about` still shows `info@berner-architekten-treffen.ch` (unchanged)
- [ ] Organizer email field remains in DB and API (no schema change); only the UI render is removed
- [ ] Type-check passes; lint passes; no test changes needed for this pure UI removal

---

### Story 10.20: Legacy BAT Format Data Export & Import (Admin Tool)

**Status**: ready-for-dev
**Prerequisite**: Story 10.1 (Admin page must exist to add new tab)

**User Story:**
As an **organizer**, I want to export all BATbern data (events, sessions, speakers, companies, attendees, portraits, logos, and presentations) in the legacy BAT JSON format, and to import data in that same format, so that we can migrate data between system versions and maintain interoperability with the old BATspa platform.

**Background:**
The legacy BATspa application uses a proprietary JSON export format for events, sessions, speakers, bios, companies, and attendees. Binary assets (speaker portraits, company logos, presentation files) are stored in S3. This story adds a new "Export / Import" tab (Tab 5) to the existing `/organizer/admin` Administration page that allows full data snapshots and restores using this format.

**Scope:**

**Backend — Export API (`event-management-service`):**
- New endpoint: `GET /api/v1/admin/export/legacy` (organizer-only)
  - Collects all events with their sessions, speaker pools, registrations (attendees)
  - Collects all companies (via HTTP call to company-user-management-service)
  - Collects all users with role SPEAKER (bio, portrait S3 key, links)
  - Serializes to the legacy BAT JSON envelope: `{ version, exportedAt, events[], companies[], speakers[], attendees[] }`
  - Returns as `application/json` download (`Content-Disposition: attachment; filename=batbern-export-{date}.json`)
- New endpoint: `GET /api/v1/admin/export/assets` (organizer-only)
  - Returns a JSON manifest of presigned S3 GET URLs for all portraits, logos, and presentation PDFs/files (one URL per asset, valid 1 hour) — **chosen approach: presigned URL manifest** (simpler, no async complexity, no Lambda needed) *(IR fix 2026-03-02: resolved "async ZIP vs manifest" ambiguity in favour of manifest)*
  - Assets identified via S3 keys stored on User (portrait), Company (logo), and SessionMaterials (presentation) entities
  - Response: `{ exportedAt, assetCount, assets: [{ type, entityId, filename, presignedUrl }] }`

**Backend — Import API (`event-management-service`):**
- New endpoint: `POST /api/v1/admin/import/legacy` (organizer-only, `multipart/form-data`)
  - Accepts `file` (JSON in legacy BAT format)
  - Parses and validates the envelope; returns 400 with structured error list on validation failure
  - Import strategy: **upsert by legacy ID / event code** — existing records updated, new records inserted, nothing deleted
  - Speaker bios and company data delegated via internal service calls (company-user-management-service)
  - Attendee registrations imported as `registered` status (no waitlist logic during import)
  - Returns `{ imported: { events: N, sessions: N, speakers: N, companies: N, attendees: N }, skipped: [...], errors: [...] }`
- Portrait / logo / asset import: **separate** `POST /api/v1/admin/import/assets` endpoint
  - Accepts a ZIP file; unpacks to S3 under `imports/{timestamp}/` prefix
  - Links assets to entities by filename convention matching the legacy export manifest

**Frontend — New Admin Tab 5 "Export / Import":**
- `web-frontend/src/components/organizer/Admin/ExportImportTab.tsx`
- **Export section:**
  - "Export All Data (JSON)" button → triggers `GET /api/v1/admin/export/legacy` download
  - "Export Assets (Portraits, Logos, Presentations)" button → triggers `GET /api/v1/admin/export/assets` (opens presigned URL or shows ZIP progress)
- **Import section:**
  - JSON import: file picker (`.json`) + "Import" button → `POST /api/v1/admin/import/legacy` → shows result summary table (imported counts + skipped/error list)
  - Asset import: file picker (`.zip`) + "Import Assets" button → `POST /api/v1/admin/import/assets`
  - Confirmation dialog before import: "This will upsert data. Existing records will be updated. This cannot be undone automatically."
- Tab added to `EventManagementAdminPage.tsx` as Tab 5 (index 5)

**Key new files:**
```
services/event-management-service/.../controller/AdminExportImportController.java
services/event-management-service/.../service/LegacyExportService.java
services/event-management-service/.../service/LegacyImportService.java
services/event-management-service/.../dto/LegacyExportEnvelope.java
services/event-management-service/.../dto/LegacyImportResult.java
web-frontend/src/components/organizer/Admin/ExportImportTab.tsx
```

**Key modified files:**
```
docs/api/events-api.openapi.yml                         — export/import endpoints (FIRST)
web-frontend/src/pages/organizer/EventManagementAdminPage.tsx — add Tab 5
public/locales/de/common.json + en/common.json          — admin.exportImport.* keys
```

**Definition of Done (Story 10.20):**
- [ ] `GET /api/v1/admin/export/legacy` returns valid legacy JSON for a seeded dataset; organizer-only (403 for others)
- [ ] `POST /api/v1/admin/import/legacy` upserts all entity types; returns structured result; idempotent (importing same file twice has no side effects)
- [ ] Asset export endpoint returns JSON presigned URL manifest (`{ exportedAt, assetCount, assets: [...] }`); each URL valid 1 hour; asset import ZIP is unpacked to S3 and linked to entities
- [ ] Frontend tab shows export buttons and import file pickers; result summary renders after import
- [ ] Confirmation dialog shown before any import action
- [ ] TDD: `LegacyExportServiceTest` and `LegacyImportServiceTest` with mocked repositories; integration test covers round-trip (export → import → verify DB)
- [ ] OpenAPI spec committed before implementation (ADR-006)
- [ ] i18n: `admin.exportImport.*` keys in de/en; Type-check passes; Checkstyle passes

---

### Story 10.21: Event Photos Gallery

**Status**: ready-for-dev
**Prerequisite**: None — `EventPhotosTab` is added to `EventPage.tsx` (organizer event detail), not to the Admin page. Independent of Story 10.1. *(IR fix 2026-03-02)*

**User Story:**
As an **organizer**, I want to upload and manage photos from each event directly in the event detail page, so that we can preserve visual memories of our events.

As a **visitor**, I want to see real photos from past BATbern events on the homepage — replacing the dummy testimonials — and I want to see each archived event's own photos when I browse the archive.

**Background:**
The homepage `TestimonialSection` currently shows 20 hardcoded fake testimonials in an infinite marquee. This story replaces that row with up to 20 real event photos randomly selected from the last 5 events. Each archived event detail page also gets an image marquee showing that event's photos. Photos are stored in S3 under `events/{eventCode}/photos/{filename}`.

**Scope:**

**Backend — Event Photos API (`event-management-service`):**
- Flyway **V75**: New `event_photos` table: `id (UUID PK)`, `event_code (VARCHAR FK → events.event_code)`, `s3_key (TEXT NOT NULL)`, `display_url (TEXT NOT NULL)`, `filename (TEXT)`, `uploaded_at (TIMESTAMPTZ)`, `uploaded_by (VARCHAR)`, `sort_order (INT DEFAULT 0)` *(V74 is already taken by `V74__migrate_waitlisted_to_waitlist.sql` — IR fix 2026-03-02)*
- New endpoints (organizer-only for write; public for read):
  - `GET /api/v1/events/{eventCode}/photos` → `List<EventPhotoResponse>` (public)
  - `POST /api/v1/events/{eventCode}/photos/upload-url` → presigned S3 upload URL + `photoId` (organizer)
  - `POST /api/v1/events/{eventCode}/photos/confirm` (body: `photoId`) → marks upload complete, stores `display_url` (organizer)
  - `DELETE /api/v1/events/{eventCode}/photos/{photoId}` → deletes DB record + S3 object (organizer)
- New endpoint for homepage use: `GET /api/v1/events/recent-photos?limit=20&lastNEvents=5` → returns up to `limit` photo URLs randomly sampled from the last `lastNEvents` events (by event date)

**Frontend — Organizer Event Page: Photos Tab:**
- New tab "Photos" added to `EventPage.tsx` tab list (after existing tabs)
- `web-frontend/src/components/organizer/EventPage/EventPhotosTab.tsx`:
  - Photo grid (3-4 columns, thumbnails)
  - "Upload Photo" button → triggers presigned URL flow (same pattern as logo/presentation uploads)
  - Each photo card has "Delete" icon; confirmation before delete
  - Drag-to-reorder for sort_order (optional enhancement)

**Frontend — Public Homepage: Replace Dummy Testimonials:**
- `TestimonialSection.tsx`:
  - First marquee row: replace hardcoded `testimonials[]` array with `useRecentEventPhotos(limit=20, lastNEvents=5)` hook
  - If < 3 photos available (not enough data), fall back to a subset of the current testimonial cards
  - Photo cards use `<img src={photo.displayUrl} alt="BATbern event" className="...rounded-lg object-cover h-48 w-64" />`
  - Remove `TestimonialCard` component if unused after this change (or keep for fallback)

**Frontend — Archive Event Detail: Photos Marquee:**
- Archive event detail page (public) gets a "Photos" section below the sessions list: `InfiniteMarquee` showing that event's photos (fetched via `GET /api/v1/events/{eventCode}/photos`)
- Only shown if the event has ≥ 1 photo; section hidden otherwise

**Key new files:**
```
services/event-management-service/src/main/resources/db/migration/V75__create_event_photos.sql
services/event-management-service/.../domain/EventPhoto.java
services/event-management-service/.../repository/EventPhotoRepository.java
services/event-management-service/.../service/EventPhotoService.java
services/event-management-service/.../controller/EventPhotoController.java
web-frontend/src/components/organizer/EventPage/EventPhotosTab.tsx
web-frontend/src/hooks/useRecentEventPhotos.ts
web-frontend/src/hooks/useEventPhotos.ts
```

**Key modified files:**
```
docs/api/events-api.openapi.yml                                     — photo endpoints (FIRST)
web-frontend/src/components/organizer/EventPage/EventPage.tsx       — add Photos tab
web-frontend/src/components/public/Testimonials/TestimonialSection.tsx — replace dummy data with real photos
web-frontend/src/pages/public/ArchiveEventDetailPage.tsx (or similar) — add photos marquee section
public/locales/de/events.json + en/events.json                      — photos.* keys
```

**Definition of Done (Story 10.21):**
- [ ] V75 migration runs cleanly; `event_photos` table created
- [ ] Organizer can upload photos to an event via presigned URL flow; photos appear in photo grid
- [ ] Organizer can delete a photo; S3 object and DB record both removed
- [ ] `GET /api/v1/events/recent-photos` returns up to 20 photos from last 5 events (public, no auth)
- [ ] Homepage marquee shows real event photos; graceful fallback if < 3 photos exist
- [ ] Archive event detail page shows event's own photos in marquee when photos exist; section hidden otherwise
- [ ] TDD: `EventPhotoServiceTest` covers upload confirmation, delete, recent-photos query
- [ ] OpenAPI spec committed before implementation (ADR-006)
- [ ] i18n: `photos.*` keys in de/en; Type-check passes; Checkstyle passes
- [ ] ❌ Drag-to-reorder (`sort_order`) is explicitly **NOT in scope** for this story — defer to a follow-up

---

### Story 10.22: Event Teaser Images on Moderator Presentation Page

**Status**: ready-for-dev
**Prerequisite**: Story 10.8a (PresentationPage must exist)
**Updated**: 2026-03-03 — expanded from single-image to multi-image design (IR review)

**User Story:**
As an **organizer**, I want to upload one or more teaser images for an event from the event detail page, so that each image appears as its own full-screen slide on the moderator presentation screen — after the topic reveal and before the agenda — giving the audience a visual mood-setter sequence.

**Background:**
The moderator presentation page (`/present/:eventCode`) currently shows slides: intro → topic → agenda. This story inserts one full-screen image slide per uploaded teaser image, consecutively between the topic introduction and the first agenda slide. Images are stored in S3 and managed from the event detail Settings tab. Maximum 10 images per event.

**Scope:**

**Backend — Teaser Images (child table):**
- Flyway migration: create `event_teaser_images` table (`id UUID PK`, `event_code TEXT FK → events`, `s3_key TEXT`, `image_url TEXT`, `display_order INTEGER`, `created_at TIMESTAMPTZ`) *(check latest migration version before coding)*
- `EventResponse` DTO: add `teaserImages: List<TeaserImageItem>` (empty list if none set); `TeaserImageItem { id, imageUrl, displayOrder }`
- New endpoints (organizer-only, max 10 images enforced at confirm):
  - `POST /api/v1/events/{eventCode}/teaser-images/upload-url` → `{ uploadUrl, s3Key, expiresIn }`; S3 key: `events/{eventCode}/teaser/{uuid}.{ext}`
  - `POST /api/v1/events/{eventCode}/teaser-images/confirm` (body: `{ s3Key }`) → `TeaserImageItem`; persists new row; returns 422 if already at 10 images
  - `DELETE /api/v1/events/{eventCode}/teaser-images/{imageId}` → 204; deletes DB row + S3 object

**Frontend — Event Settings Tab (Organizer):**
- `EventSettingsTab.tsx`: new "Teaser Images" subsection (below Registration Capacity, above Notifications)
  - Thumbnail gallery of all uploaded images; each thumbnail has a per-image remove button
  - "+ Add Teaser Image" button → presigned URL flow → on completion, gallery refetches
  - Upload button disabled when `isArchived` or image count ≥ 10; limit message shown at cap
  - Inline error message on upload/remove failure; gallery state unchanged on error

**Frontend — Presentation Page:**
- `usePresentationSections.ts`: loop over `event.teaserImages` array; push one `teaser-image` section per image (unique key: `teaser-image-{id}`, `imageUrl` on section object)
- New `TeaserImageSlide.tsx`: full-screen `<img>` with `object-fit: cover`, no overlay, no animation
- `PresentationPage.tsx` `SectionRenderer`: `case 'teaser-image'` → `<TeaserImageSlide imageUrl={section.imageUrl} />`
- If `teaserImages` is empty, no slides inserted; existing behaviour preserved
- Slide navigation (`ArrowRight` / `ArrowLeft`) already handles dynamic slide counts

**Key new files:**
```
services/event-management-service/src/main/resources/db/migration/V??__create_event_teaser_images.sql
services/event-management-service/.../domain/EventTeaserImage.java
services/event-management-service/.../repository/EventTeaserImageRepository.java
services/event-management-service/.../service/EventTeaserImageService.java
services/event-management-service/.../controller/EventTeaserImageController.java
web-frontend/src/pages/presentation/slides/TeaserImageSlide.tsx
```

**Key modified files:**
```
docs/api/events-api.openapi.yml                                        — commit FIRST (ADR-006)
services/event-management-service/.../service/EventService.java        — populate teaserImages in toDto()
web-frontend/src/hooks/usePresentationSections.ts                      — PresentationSection.imageUrl + loop
web-frontend/src/pages/PresentationPage.tsx                            — SectionRenderer case
web-frontend/src/components/organizer/EventPage/EventSettingsTab.tsx   — gallery upload UI
web-frontend/public/locales/de/events.json + en/events.json            — teaserImage.* keys
web-frontend/src/types/generated/events-api.types.ts                   — regenerate after spec change
```

**Definition of Done (Story 10.22):**
- [ ] Migration runs cleanly; `event_teaser_images` table created; existing events unaffected
- [ ] Organizer can upload multiple teaser images via presigned URL; thumbnail gallery shown in Settings tab
- [ ] Organizer can remove any individual teaser image by ID; DB row deleted, S3 object deleted
- [ ] Presentation page shows one full-screen slide per teaser image (in displayOrder), between topic-reveal and agenda-preview
- [ ] When `teaserImages` is empty, presentation page renders identically to pre-story behaviour
- [ ] Max 10 images enforced: confirm returns 422 at limit; Upload button disabled and limit message shown in UI
- [ ] Upload/remove failures show inline error; gallery state unchanged
- [ ] TDD: `EventTeaserImageServiceTest` covers upload-URL generation, confirm, delete, limit enforcement; integration test verifies DB state
- [ ] OpenAPI spec committed before implementation (ADR-006)
- [ ] i18n: `teaserImage.*` keys in de/en; Type-check passes; Checkstyle passes

---

### Story 10.23: Event Description Section on Public Homepage

**Status**: ready-for-dev
**Prerequisite**: None (field already exists in DB and API)

**User Story:**
As a **visitor**, I want to read the event description on the public homepage right below the hero section, so that I can understand what this BATbern event is about before deciding to register.

**Background:**
The `Event` entity already has a `description` (TEXT) field stored in the DB and returned in `EventResponse.description`. The field is settable from the organizer's event settings panel. However, it is currently not displayed anywhere on the public-facing pages — the homepage hero goes straight from the event title/date into the speakers section. This story surfaces the description as a distinct styled section between the hero and the speakers/content sections.

**Scope:**

**Frontend — Public Homepage:**
- In `HomePage.tsx` (or whichever component renders the active event public view): add an `EventDescriptionSection` component rendered after the `HeroSection` and before the speaker/agenda sections
- Only rendered when `activeEvent.description` is non-null and non-empty; hidden otherwise (no placeholder shown to anonymous visitors)
- Component: `web-frontend/src/components/public/Event/EventDescriptionSection.tsx`
  - Styled card or prose block (consistent with site dark theme)
  - Renders description as formatted paragraph text (plain text stored in DB; render with `white-space: pre-wrap` or convert `\n` to `<br/>`)
  - Optional section heading: `t('events.description.heading')` → "About This Event" / "Über diese Veranstaltung"

**Frontend — Archive Event Detail Page:**
- If `ArchiveEventDetailPage` (or equivalent) renders event details, also display `description` in a similar prose block (if non-empty) below the event metadata and above the sessions list
- Consistent styling with homepage section

**No backend changes required** — `EventResponse.description` already populated from the existing `description` column (confirmed at `EventResponse.java:37`).

**Key new files:**
```
web-frontend/src/components/public/Event/EventDescriptionSection.tsx
```

**Key modified files:**
```
web-frontend/src/pages/public/HomePage.tsx                      — render EventDescriptionSection after hero
web-frontend/src/pages/public/ArchiveEventDetailPage.tsx (or equivalent) — add description block
public/locales/de/events.json + en/events.json                  — description.heading key
```

**Definition of Done (Story 10.23):**
- [ ] Homepage shows event description below the hero section when `activeEvent.description` is non-empty
- [ ] Section is completely absent (no empty card/space) when description is null or empty
- [ ] Description text wraps correctly on mobile and desktop
- [ ] Archive event detail page also shows description when available
- [ ] No backend changes; no DB migrations needed
- [ ] i18n: `events.description.heading` key in all locales (10 languages); Type-check passes; lint passes

---

### Story 10.24: AWS Cognito User Provisioning from User Management

**Status**: ready-for-dev
**Prerequisite**: Epic 9 Story 9.1 (Cognito integration patterns established)

**User Story:**
As an **organizer**, I want to create an AWS Cognito account for a BATbern user directly from the User Management list — either as part of creating a new user or for an existing user who is not yet linked to AWS — so that I can onboard people without requiring them to self-register.

**Architectural Clarification — Who Actually Calls AWS:**

> **The organizer's own Cognito account has zero special AWS rights.** A regular Cognito user pool token cannot call `AdminCreateUser` — that API requires IAM credentials.
>
> This works as follows:
> 1. The organizer (authenticated via Cognito JWT) calls the BATbern **backend** endpoint.
> 2. The backend (`company-user-management-service`) runs on ECS with an **IAM task role**.
> 3. The ECS task role has `cognito-idp:AdminCreateUser` + `cognito-idp:AdminAddUserToGroup` permissions granted via CDK.
> 4. The backend calls the AWS Cognito API using those IAM credentials — fully transparent to the organizer.
>
> In short: the organizer only needs ORGANIZER role in BATbern. AWS IAM is handled entirely by the backend's ECS role.

**Background:**
The `User` domain entity has a nullable `cognitoUserId` field (`cognito_user_id` column). A user can exist in the BATbern DB without a Cognito account (e.g., imported from legacy data or pre-created by an organizer). This story detects that state and allows the organizer to trigger Cognito provisioning on demand. AWS Cognito's `AdminCreateUser` creates the user with a temporary password and `FORCE_CHANGE_PASSWORD` status; the user must set a new password on first login. If a Cognito account with the same email already exists, the API returns `UsernameExistsException`.

**Scope:**

**Backend — Cognito User Provisioning (`company-user-management-service`):**
- New service: `CognitoAdminService.java`
  - Wraps `AdminCreateUser` (using `software.amazon.awssdk:cognitoidentityprovider` SDK, already present for Cognito sync)
  - Call parameters: `email`, `givenName`, `familyName`; `MessageAction = SUPPRESS` (no Cognito default email — BATbern sends a custom one)
  - Adds user to the appropriate Cognito group via `AdminAddUserToGroup` based on their BATbern role(s)
  - Sends custom welcome email via `EmailService` using template `user-welcome-temp-password-{de,en}.html` (new classpath templates seeded by `EmailTemplateSeedService`)
  - Catches `UsernameExistsException` → propagates as structured 409; does not create duplicate
  - On success: updates `users.cognito_user_id` with the new Cognito `sub` (linking DB ↔ Cognito)
- New endpoint: `POST /api/v1/users/{userId}/provision-cognito` (organizer-only)
  - Works for both new-user flows and existing users without a cognitoUserId
  - Body: `{ language }` (role inferred from DB user record)
  - Returns: `{ cognitoUsername, temporaryPasswordSent: true }` on success
  - Returns 409 if `UsernameExistsException` from Cognito, or if user already has a `cognitoUserId`
- IAM: ECS task role for company-user-management-service must have:
  - `cognito-idp:AdminCreateUser` on the user pool
  - `cognito-idp:AdminAddUserToGroup` on the user pool
  - Both added to the existing CDK IAM inline policy for this service

**Frontend — User Table: Row Action Menu (`UserTable.tsx`):**
- Add new action `"linkCognito"` in the `Menu` (alongside existing `view`, `editRoles`, `delete`)
- Action is **only rendered** when `user.cognitoUserId` is null/undefined:
  ```tsx
  {!user.cognitoUserId && (
    <MenuItem onClick={() => handleAction('linkCognito')}>
      {t('actions.createAwsAccount')}
    </MenuItem>
  )}
  ```
- `UserList.tsx` handles the `'linkCognito'` action by opening a new `LinkCognitoDialog` for the selected user

**Frontend — Link Cognito Dialog (`LinkCognitoDialog.tsx`):**
- Simple confirmation dialog (not the full create/edit modal):
  - Title: "Create AWS Account for {firstName} {lastName}"
  - Body: "This will create an AWS Cognito account for {email}. They will receive a welcome email with a temporary password and must reset it on first login."
  - Language selector (dropdown: DE / EN, defaults to user's stored language)
  - "Create Account" + "Cancel" buttons
  - On confirm: calls `POST /api/v1/users/{userId}/provision-cognito`
  - Success state: inline success message "AWS account created. Welcome email sent to {email}." — dialog auto-closes after 2s
  - Error (409 UsernameExistsException): "An AWS account already exists for this email address. Use the user sync function to link it."
  - Error (other): generic error alert within dialog

**Frontend — Create/Edit User Modal: "Also Create AWS Account" toggle (`UserCreateEditModal.tsx`):**
- In **create mode only** (`!isEditMode`): add a `FormControlLabel` with a `Switch` (or `Checkbox`) below the role selector:
  - Label: "Also create an AWS Cognito account (send temporary password)"
  - Default: unchecked
  - When checked: language selector appears (DE / EN)
  - On form submit with toggle enabled: first calls the existing create-user endpoint, then (on success) calls `POST /api/v1/users/{newUserId}/provision-cognito` automatically
  - If Cognito provisioning fails after user creation: user is still created in BATbern; a warning toast appears: "User created in BATbern but AWS account creation failed. Use the 'Create AWS Account' action from the user list to retry."

**Note on `UserResponse` DTO:** The `cognitoUserId` field does not need to be returned in the public API response (it is an internal identifier). Instead, expose a boolean `hasCognitoAccount: boolean` in `UserResponse` / the user list endpoint so the frontend can conditionally show/hide the "Create AWS Account" action without exposing the raw Cognito sub.

**Email Templates (new classpath content fragments, seeded by `EmailTemplateSeedService` in company-user-management-service):**
```
services/company-user-management-service/src/main/resources/email-templates/user-welcome-temp-password-de.html
services/company-user-management-service/src/main/resources/email-templates/user-welcome-temp-password-en.html
```
Variables: `recipientName`, `loginUrl`, `temporaryPasswordNote` (text block explaining first-login reset requirement).

**Key new files:**
```
services/company-user-management-service/.../service/CognitoAdminService.java
services/company-user-management-service/.../service/CognitoAdminServiceTest.java
services/company-user-management-service/.../dto/ProvisionCognitoResponse.java
web-frontend/src/components/organizer/UserManagement/LinkCognitoDialog.tsx
services/company-user-management-service/src/main/resources/email-templates/user-welcome-temp-password-de.html
services/company-user-management-service/src/main/resources/email-templates/user-welcome-temp-password-en.html
```

**Key modified files:**
```
docs/api/users-api.openapi.yml
     — POST /api/v1/users/{userId}/provision-cognito endpoint + hasCognitoAccount in UserResponse (FIRST)
infrastructure/lib/company-user-management-stack.ts
     — IAM policy: AdminCreateUser + AdminAddUserToGroup on user pool ARN
services/company-user-management-service/.../controller/UserController.java
     — new provision-cognito endpoint
services/company-user-management-service/.../service/UserService.java
     — update cognitoUserId after successful provisioning
services/company-user-management-service/.../dto/UserResponse.java (or generated DTO)
     — add hasCognitoAccount boolean
services/company-user-management-service/.../service/EmailTemplateSeedService.java
     — seed welcome-temp-password templates
web-frontend/src/components/organizer/UserManagement/UserTable.tsx
     — conditional "Create AWS Account" menu item based on hasCognitoAccount
web-frontend/src/components/organizer/UserManagement/UserList.tsx
     — handle 'linkCognito' action → open LinkCognitoDialog
web-frontend/src/components/organizer/UserManagement/UserCreateEditModal.tsx
     — "Also create AWS account" toggle in create mode
web-frontend/src/types/user.types.ts
     — add hasCognitoAccount to User type
public/locales/de/userManagement.json + en/userManagement.json
     — actions.createAwsAccount, linkCognito.* keys
```

**Definition of Done (Story 10.24):**
- [ ] `POST /api/v1/users/{userId}/provision-cognito` creates Cognito user with `FORCE_CHANGE_PASSWORD`; links `cognitoUserId` in DB; sends custom welcome email; organizer-only (403 for non-organizers)
- [ ] Backend uses ECS IAM task role for `AdminCreateUser` — organizer's own Cognito token has no AWS IAM rights; this is confirmed in a `CognitoAdminServiceTest` that mocks the SDK client (not Cognito tokens)
- [ ] 409 returned if email already exists in Cognito (`UsernameExistsException`) — no duplicate created, DB unchanged
- [ ] 409 returned if user already has a `cognitoUserId` in DB
- [ ] `UserResponse` includes `hasCognitoAccount: boolean` (does not expose raw `cognitoUserId`)
- [ ] User table row action menu shows "Create AWS Account" only for users with `hasCognitoAccount = false`
- [ ] `LinkCognitoDialog` shows confirmation, language selector, success/error states correctly
- [ ] Create-user modal toggle "Also create AWS account" triggers provisioning after DB user is created; when Cognito provisioning fails: (a) the BATbern user record is still persisted, (b) `cognitoUserId` remains null, (c) a warning toast is shown "User created but AWS account creation failed — use 'Create AWS Account' from the user list to retry", (d) integration test asserts DB state and toast presence
- [ ] IAM: CDK grants `AdminCreateUser` + `AdminAddUserToGroup` scoped to the specific user pool ARN
- [ ] Welcome email templates seeded and visible/editable in Email Templates admin tab
- [ ] TDD: `CognitoAdminServiceTest` covers success, `UsernameExistsException`, SDK error, already-linked guard
- [ ] OpenAPI spec (users-api) committed before implementation (ADR-006)
- [ ] i18n: `actions.createAwsAccount`, `linkCognito.*` keys in de/en userManagement namespace; Type-check passes; Checkstyle passes

---

### Story 10.25: Partner Meeting iCal Auto-creation & Year-End Reminders

**Status**: ready-for-dev
**Prerequisite**: Story 8.3 (all prerequisites below verified ✅); Story 10.2 (DB-backed email templates with admin edit UI)
**Note (IR 2026-03-02)**: ShedLock is already configured in `event-management-service` (`V31__Add_shedlock_table.sql` + `shedlock-spring:5.10.0` dependency) — no setup needed. Transitive block: Story 10.2 depends on Story 10.1; if 10.1 is delayed, 10.25 is also delayed.

**Audit of Story 8.3 — What is ALREADY implemented (verified by code inspection):**

| Capability | Status | Location |
|---|---|---|
| Partner meeting CRUD (create, list, get, update) | ✅ Fully implemented | `PartnerMeetingController` + `PartnerMeetingService` |
| Date auto-populated from event on creation | ✅ Implemented | `PartnerMeetingService.createMeeting()` |
| RFC 5545 iCal generation (2 VEVENTs) | ✅ Implemented | `IcsGeneratorService.generate()` |
| "Send invite" endpoint (`POST /{id}/send-invite`) | ✅ Implemented | `PartnerMeetingController` returns 202 |
| Actual email sent via AWS SES with ICS attachment | ✅ Implemented | `PartnerInviteEmailService` → `EmailService.sendHtmlEmailWithAttachments()` → `sesClient.sendRawEmail()` |
| Frontend "Send Invite" button with confirmation dialog | ✅ Implemented | `MeetingDetailPanel.tsx` |
| `inviteSentAt` timestamp shown as status chip | ✅ Implemented | `PartnerMeetingsPage.tsx` + `MeetingDetailPanel.tsx` |

**What was missing in Story 8.3 and is the subject of THIS story:**

| Gap | This Story Adds |
|---|---|
| Partner meetings must be created manually | Auto-create on event creation |
| Email body is hardcoded German HTML in `buildEmailBody()` | Replace with DB-backed, editable template (Story 10.2 pattern) |
| No year-end reminder to coordinate partners for next year | `YearEndReminderScheduler` creates tasks on 31 December |
| Local dev: `sesClient == null` → email only logged, not sent | Not a story gap — expected local behavior; SES works in staging/prod |

> **Note on "I only saw a status change, no real email":** In local development, when `sesClient` is `null` (no SES configured locally), `EmailService` logs `"Would send email with N attachment(s) to: ..."` and skips the send. The status chip (`inviteSentAt`) is still set, making it look like only a status change happened. On staging/production with a real SES client, the email with .ics attachment is actually sent. The behavior is correct — this is the expected local fallback.

**User Stories:**

As an **organizer**, I want a partner meeting to be automatically created whenever I create a new BATbern event, so that I don't have to set it up manually each time.

As an **organizer**, I want the partner meeting invite email body to be editable from the admin Email Templates section (like all other system emails), so that I can customize the content without a code deploy.

As an **organizer**, I want a task to be automatically created on 31 December each year reminding me to send iCal invitations to partners for the coming year's event, so we never forget partner coordination.

**Scope:**

**Backend — Auto-create Partner Meeting on Event Creation:**
- When a new BATbern event is created (saved with status `CREATED`), `event-management-service` makes a best-effort HTTP call to `partner-coordination-service` to auto-create the linked partner meeting
- Use the existing `EventManagementClient` / inter-service HTTP client pattern (partner-coordination-service already calls event-management-service via `EventManagementClientImpl`; the reverse direction needs a new `PartnerCoordinationClient` in event-management-service, or trigger from partner-service side by listening to an event)
- **Simplest pattern (recommended):** `EventController.createEvent()` or `EventService.createEvent()` fires a Spring `ApplicationEvent` `EventCreatedEvent(eventCode, eventDate)`. A new `@EventListener` in event-management-service calls `PartnerCoordinationClient.createMeetingForEvent(eventCode)` — a one-way best-effort HTTP call (`try/catch`, failure only logged, does not roll back event creation)
- Auto-created `PartnerMeeting` fields:
  - `eventCode` = new event's code
  - `meetingDate` = event date (populated by partner service via its own `EventManagementClient.getEventSummary()`)
  - `startTime` = `12:00` (from `partner.meeting.default-start-time` property, default `12:00`)
  - `endTime` = `14:00` (from `partner.meeting.default-end-time` property, default `14:00`)
  - `location` = `""` (empty; organizer fills in later)
  - `meetingType` = `SPRING` (default; organizer can update)
  - `agenda` = `null`, `notes` = `null`
- `inviteSentAt` remains null (invite not yet sent — organizer triggers manually via existing "Send Invite" button)
- Idempotent: if a `PartnerMeeting` with `eventCode` already exists, skip creation (no duplicate)

**Backend — Editable Partner Meeting Invite Email Template:**
- Currently `PartnerInviteEmailService.buildEmailBody()` returns a hardcoded German HTML string
- Replace with DB-backed template loading (Story 10.2 pattern):
  - New classpath template files seeded by `EmailTemplateSeedService` **in partner-coordination-service** (the service that sends this email):
    ```
    services/partner-coordination-service/src/main/resources/email-templates/partner-meeting-invite-de.html
    services/partner-coordination-service/src/main/resources/email-templates/partner-meeting-invite-en.html
    ```
  - Template variables: `{{meetingDate}}`, `{{meetingStartTime}}`, `{{meetingEndTime}}`, `{{location}}`, `{{eventTitle}}`, `{{organizerName}}`
  - `PartnerInviteEmailService.sendCalendarInvites()`: replace `buildEmailBody()` call with `emailTemplateService.loadAndRender("partner-meeting-invite", language, variables)` with fallback to classpath
  - Templates appear in Admin → Email Templates tab under category `PARTNER`
- Language: since partner users may have different language preferences, send in DE by default (or in the user's stored language if available from `UserServiceClient`)

**Backend — Year-End Reminder Task (Scheduler in `event-management-service`):**
- New `YearEndReminderScheduler.java` (ShedLock-protected, cron `0 0 8 31 12 *` — 08:00 on 31 December):
  - Creates two organizer tasks if they don't already exist for `{nextYear}`:
    1. `"Send partner meeting iCal invitations for {nextYear}"` — due: 31 January of next year
    2. `"Create BATbern event calendar entry for {nextYear}"` — due: 31 January of next year
  - Assigned to all users with ORGANIZER role (fetched via `UserServiceClient` or internal query)
  - `eventCode` = null (global tasks, not tied to a specific event)
  - Idempotent: checks for existing tasks matching title + year before inserting

**Frontend — No New Components Required:**
- Auto-creation is fully server-side; the existing `PartnerMeetingsPage` list will show the auto-created meeting
- Email template appears automatically in Email Templates admin tab (seeded by `EmailTemplateSeedService`)
- Year-end tasks appear in the existing task board

**Key new files:**
```
services/partner-coordination-service/src/main/resources/email-templates/partner-meeting-invite-de.html
services/partner-coordination-service/src/main/resources/email-templates/partner-meeting-invite-en.html
services/event-management-service/.../scheduler/YearEndReminderScheduler.java
services/event-management-service/.../scheduler/YearEndReminderSchedulerTest.java
services/event-management-service/.../client/PartnerCoordinationClient.java  (if HTTP trigger pattern used)
```

**Key modified files:**
```
services/event-management-service/.../service/EventService.java (or EventController)
     — fire EventCreatedEvent after successful event save
services/event-management-service/.../listener/PartnerMeetingAutoCreateListener.java (new)
     — @EventListener on EventCreatedEvent → call PartnerCoordinationClient
services/partner-coordination-service/.../service/PartnerInviteEmailService.java
     — replace buildEmailBody() with template load + variable substitution
services/partner-coordination-service/.../service/EmailTemplateSeedService.java (new, mirrors EMS pattern)
     — seed partner-meeting-invite-de/en on startup
services/partner-coordination-service/.../repository/PartnerMeetingRepository.java
     — add existsByEventCode() for idempotent auto-creation guard
```

**Definition of Done (Story 10.25):**
- [ ] Creating a new BATbern event automatically creates a partner meeting (correct date, 12:00–14:00 defaults, empty location)
- [ ] Auto-creation is idempotent — creating the same event twice does not produce duplicate meetings
- [ ] Auto-creation failure (partner service down) does not fail or roll back event creation; error only logged
- [ ] `PartnerInviteEmailService` loads invite body from DB template with classpath fallback; hardcoded `buildEmailBody()` removed
- [ ] `partner-meeting-invite-de/en` templates are seeded and appear in Admin → Email Templates under category PARTNER
- [ ] `YearEndReminderScheduler` fires on 31 December (verified by unit test with `@SpyBean` / manual trigger); creates two tasks for next year; is idempotent
- [ ] Year-end tasks have due dates of 31 January of next year and are assigned to all active organizers
- [ ] TDD: `PartnerMeetingAutoCreateListenerTest` (mocked client, idempotency, failure isolation); `YearEndReminderSchedulerTest` (task creation, dedup); `PartnerInviteEmailServiceTest` (template load, fallback)
- [ ] No OpenAPI changes needed (no new public endpoints)
- [ ] Checkstyle passes; Type-check passes

---

### Story 10.26: SES Email Forwarding & Role-Based Distribution Lists

**Story file**: `_bmad-output/implementation-artifacts/10-26-ses-email-forwarding-distribution-lists.md`
**Status**: draft
**Prerequisites**: Story 10.17 (InboundEmailStack, SES receipt rules)

**User Story:**
As an **organizer**, I want `ok@batbern.ch`, `info@batbern.ch`, `events@batbern.ch`, `partner@batbern.ch`, `support@batbern.ch`, and `batbern{N}@batbern.ch` to automatically forward emails to the right recipients based on user roles and event registrations, so that we have zero-cost distribution lists without needing any mailbox provider.

**Scope:**
- Extend existing `InboundEmailStack` with SES receipt rules for forwarding addresses
- New Lambda forwarder (Node.js 20.x) triggered by S3 events under `forwarding/` prefix
- **Address resolution** via existing APIs (no new service endpoints):
  - `ok@batbern.ch` → all ORGANIZER users (organizers only can send)
  - `info@batbern.ch` / `events@batbern.ch` → all ORGANIZER users (anyone can send)
  - `partner@batbern.ch` → all PARTNER users (organizers only can send)
  - `support@batbern.ch` → configurable contacts from Admin Settings (anyone can send)
  - `batbern{N}@batbern.ch` → confirmed registrants of event N (organizers only can send)
- **Sender authorization**: organizer-only for restricted addresses; 5-min TTL cache
- **Admin Settings tab** on `/admin` page for configuring `support@` forwarding recipients
- New `app_settings` table (key-value) in Event Management Service
- Rate-limited sending (70ms delay, matching newsletter pattern) — 300 recipients ≈ 21s, fits 60s Lambda timeout
- Route 53 MX record for `batbern.ch` → SES inbound SMTP
- CloudWatch metrics (`EmailsForwarded`, `EmailsRejected`, `EmailsUnresolved`) + alarm

**Key files (estimated):**
```
infrastructure/lib/stacks/inbound-email-stack.ts            — extended with forwarding receipt rules + Lambda
infrastructure/lambda/email-forwarder/index.ts              — new Lambda forwarder
infrastructure/lambda/email-forwarder/package.json          — new
infrastructure/test/unit/inbound-email-stack.test.ts        — extended
services/event-management-service/.../domain/AppSetting.java                    — new entity
services/event-management-service/.../repository/AppSettingRepository.java      — new repository
services/event-management-service/.../service/AdminSettingsService.java         — new service
services/event-management-service/.../controller/AdminSettingsController.java   — new controller
services/event-management-service/.../resources/db/migration/V*.sql             — new migration
web-frontend/src/components/organizer/admin/AdminSettingsTab.tsx                — new component
web-frontend/src/services/adminSettingsService.ts                               — new service
```

**Definition of Done (Story 10.26):**
- [ ] Sending email to `ok@batbern.ch` from organizer → delivered to all organizers
- [ ] Sending email to `info@batbern.ch` or `events@batbern.ch` from anyone → delivered to all organizers
- [ ] Sending email to `partner@batbern.ch` from organizer → delivered to all partners
- [ ] Sending email to `support@batbern.ch` from anyone → delivered to configured support contacts
- [ ] Sending email to `batbern58@batbern.ch` from organizer → delivered to all confirmed registrants of event 58
- [ ] Non-organizer sending to `ok@` / `partner@` / `batbern{N}@` → silently rejected
- [ ] Admin Settings tab visible on `/admin` page; support contacts configurable and persisted
- [ ] From rewritten to `{name} via BATbern <noreply@batbern.ch>`; Reply-To = original sender
- [ ] CloudWatch metrics published; alarm fires on >20 rejections/hour
- [ ] MX record for `batbern.ch` resolves to SES inbound SMTP
- [ ] CDK unit tests + Lambda Jest tests + Admin Settings integration tests all pass
- [ ] Cost: ~$0.20/month (SES free tier + Lambda free tier)

---

**END OF EPIC 10**
