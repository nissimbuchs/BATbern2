# Feature Status & Roadmap

## Overview

This page tracks implementation status of all BATbern features. Use this to understand what's available now, what's in progress, and what's coming soon.

**Last Updated**: 2026-06-13

**Platform Status**: ✅ **PRODUCTION & FEATURE-RICH** — All MVP epics (1-5) complete; Speaker Portal (6), Partner Coordination (8), the Epic 10 organizer/admin/public cluster, the unified Cognito speaker workflow (11), and Google Single Sign-On (12) all live. Attendee contribution features (Epic 7) are largely implemented (mostly in review). Spring Boot 4 migration (Epic 13) is planned.

**Platform Readiness:**
- **Epics 1-2**: ✅ 100% Complete (Foundation, Entity CRUD)
- **Epic 3**: ✅ 100% Complete (Historical data migration tooling ready, production import pending a user trigger)
- **Epic 4**: ✅ 100% Complete (Public website, 3-step registration wizard, archive browsing, content search, SEO)
- **Epic 5**: ✅ 100% Complete (event workflow, speaker coordination, auto-publishing, lifecycle automation)
- **Epic 6**: ✅ 100% Complete (Speaker self-service portal — invitations, response, materials, dashboard, reminders). Note: speaker login was later migrated to AWS Cognito by Epic 11 (magic-link removed).
- **Epic 7**: 🔨 Largely implemented (in review) — Attendee Experience: right-sized contribution & touchpoints (no new backend service)
- **Epic 8**: ✅ 100% Complete (Partner coordination — attendance analytics, topic voting, meeting coordination, partner notes, iCal RSVP)
- **Epic 10**: ✅ Largely complete — admin tools, newsletter, registration lifecycle/waitlist/deregistration, photo gallery, teaser images, moderator presentation, Turnstile, additional user emails, legacy BAT export/import
- **Epic 11**: ✅ 100% Complete — Unified Speaker Workflow Refactor (8-state model, Cognito speaker auth, magic-link teardown)
- **Epic 12**: ✅ Complete & live — Federated Identity / Google SSO ("Continue with Google")
- **Epic 13**: 💡 Planned — Spring Boot 4 migration (backlog)

> **Note**: Epic 9 (the original JWT magic-link speaker authentication plan) was **superseded** by Epics 11 + 12 and is no longer active. Speaker authentication is now Cognito-based. A separate **BATbern Watch App** (iOS/watchOS companion) exists on its own product track; this guide documents the web platform.

### Epic Status Summary

| Epic | Title | Status |
|------|-------|--------|
| 1 | Foundation & Core Infrastructure | ✅ `[IMPLEMENTED]` |
| 2 | Entity CRUD & Domain Services | ✅ `[IMPLEMENTED]` |
| 3 | Historical Data Migration | ✅ `[IMPLEMENTED]` (tooling; prod import pending) |
| 4 | Public Website & Content Discovery | ✅ `[IMPLEMENTED]` |
| 5 | Enhanced Organizer Workflows | ✅ `[IMPLEMENTED]` |
| 6 | Speaker Self-Service Portal | ✅ `[IMPLEMENTED]` |
| 7 | Attendee Experience — Right-Sized Contribution | 🔨 `[IN PROGRESS]` (largely done, in review) |
| 8 | Partner Coordination | ✅ `[IMPLEMENTED]` |
| 9 | Speaker Authentication (original plan) | ⛔ Superseded by Epics 11 + 12 |
| 10 | Admin Tools, Newsletter & Public Enhancements | ✅ `[IMPLEMENTED]` (largely) |
| 11 | Unified Speaker Workflow Refactor | ✅ `[IMPLEMENTED]` |
| 12 | Federated Identity / Google SSO | ✅ `[IMPLEMENTED]` & live |
| 13 | Spring Boot 4 Migration | 💡 `[BACKLOG]` (planned) |
| — | BATbern Watch App (separate track) | ✅ `[IMPLEMENTED]` |

**Status Definitions**:
- `[IMPLEMENTED]` - Feature is live and available for use
- `[IN PROGRESS]` - Feature is under active development
- `[PLANNED]` - Feature is designed and prioritized for future release
- `[BACKLOG]` - Feature is documented but not yet scheduled

---

## Status Legend

| Badge | Meaning | Timeframe | Notes |
|-------|---------|-----------|-------|
| ✅ `[IMPLEMENTED]` | Available now | N/A | Fully functional, documented, tested |
| 🔨 `[IN PROGRESS]` | Partially complete | 1-4 weeks | Some functionality working, see details |
| 📋 `[PLANNED]` | Designed, scheduled | 1-6 months | Requirements finalized, development pending |
| 💡 `[BACKLOG]` | Documented, not scheduled | 6+ months | Dependent on priorities and resources |

---

## Foundation Features (Epic 1)

**Status**: ✅ 90% Complete

### Authentication & Authorization

| Feature | Status | Notes | Documentation |
|---------|--------|-------|---------------|
| Email/Password Login | ✅ `[IMPLEMENTED]` | AWS Cognito integration | [Login Guide](../getting-started/login.md) |
| Password Reset | ✅ `[IMPLEMENTED]` | Email-based reset flow | [Auth Troubleshooting](../troubleshooting/authentication.md#forgot-password) |
| Session Management | ✅ `[IMPLEMENTED]` | 8-hour sessions, 2-hour idle timeout | [Login Guide](../getting-started/login.md#session-duration) |
| Role-Based Access Control | ✅ `[IMPLEMENTED]` | ORGANIZER, ADMIN, SPEAKER, PARTNER, ATTENDEE | [User Management](../entity-management/users.md#roles-permissions) |
| Single Sign-On (SSO) — "Continue with Google" | ✅ `[IMPLEMENTED]` | Google OIDC at `auth.batbern.ch`; transparent account linking, JIT provisioning, ToS consent gate, avatar import (Epic 12). Apple/generic OIDC deferred (Story 12-10) | [Login Guide](../getting-started/login.md) |
| Multi-Factor Authentication (MFA) | 💡 `[BACKLOG]` | Authenticator app + SMS backup | — |

### Infrastructure

| Feature | Status | Notes |
|---------|--------|-------|
| AWS ECS Fargate Deployment | ✅ `[IMPLEMENTED]` | Serverless container orchestration |
| PostgreSQL Database (RDS) | ✅ `[IMPLEMENTED]` | Single-AZ db.t4g.micro (cost-optimized) |
| S3 File Storage | ✅ `[IMPLEMENTED]` | Logos, presentations, documents |
| CloudFront CDN | ✅ `[IMPLEMENTED]` | Global file delivery <50ms |
| CloudWatch Monitoring | ✅ `[IMPLEMENTED]` | Logs, metrics, alarms |
| Automated Backups | ✅ `[IMPLEMENTED]` | Daily DB snapshots, 7-day retention |

---

## Entity Management (Epic 2)

**Status**: ✅ 95% Complete

### Companies

| Feature | Status | Notes | Documentation |
|---------|--------|-------|---------------|
| CRUD Operations | ✅ `[IMPLEMENTED]` | Create, Read, Update, Delete | [Company Management](../entity-management/companies.md) |
| Swiss UID Validation | ✅ `[IMPLEMENTED]` | CHE-XXX.XXX.XXX format with check digit | [Companies](../entity-management/companies.md#swiss-uid-validation) |
| Logo Upload | ✅ `[IMPLEMENTED]` | Presigned S3 URLs, max 5 MB | [File Uploads](../features/file-uploads.md) |
| Search & Autocomplete | ✅ `[IMPLEMENTED]` | Full-text search, instant results | [Companies](../entity-management/companies.md#search) |
| Duplicate Detection | 🔨 `[IN PROGRESS]` | Fuzzy matching, 80% complete | Auto-suggest merges |
| Company Hierarchy | 📋 `[PLANNED]` | Q1 2025 | Parent/subsidiary relationships |

### Users

| Feature | Status | Notes | Documentation |
|---------|--------|-------|---------------|
| CRUD Operations | ✅ `[IMPLEMENTED]` | Full user lifecycle management | [User Management](../entity-management/users.md) |
| Role Management | ✅ `[IMPLEMENTED]` | 4 roles with permission matrix | [Users](../entity-management/users.md#roles-permissions) |
| Promotion/Demotion | ✅ `[IMPLEMENTED]` | Admin-only role changes | [Users](../entity-management/users.md#promotion-demotion) |
| GDPR Compliance | ✅ `[IMPLEMENTED]` | Data export, deletion, anonymization | [Users](../entity-management/users.md#gdpr-compliance) |
| Bulk User Import | 📋 `[PLANNED]` | Q2 2025 | CSV upload, validation, preview |
| User Groups | 💡 `[BACKLOG]` | TBD | Organizer teams, speaker pools |

### Events

| Feature | Status | Notes | Documentation |
|---------|--------|-------|---------------|
| CRUD Operations | ✅ `[IMPLEMENTED]` | All event types supported | [Event Management](../entity-management/events.md) |
| Event Types | ✅ `[IMPLEMENTED]` | Full-day, Afternoon, Evening | [Events](../entity-management/events.md#event-types) |
| Timeline/Deadlines | ✅ `[IMPLEMENTED]` | Configurable per step | [Events](../entity-management/events.md#timeline) |
| Resource Expansion API | ✅ `[IMPLEMENTED]` | `?expand=speakers,topics` | [Events](../entity-management/events.md#resource-expansion) |
| Recurring Events | 📋 `[PLANNED]` | Q2 2025 | Quarterly template, auto-create |
| Event Templates | 📋 `[PLANNED]` | Q2 2025 | Reusable event configurations |

### Partners

| Feature | Status | Notes | Documentation |
|---------|--------|-------|---------------|
| CRUD Operations | ✅ `[IMPLEMENTED]` | Full partner management | [Partner Management](../entity-management/partners.md) |
| Partner Directory | ✅ `[IMPLEMENTED]` | Tier badges, logos, contacts | [Partners](../entity-management/partners.md#directory) |
| Contacts Management | ✅ `[IMPLEMENTED]` | Multiple contacts per partner | [Partners](../entity-management/partners.md#contacts) |
| Meeting Coordination | ✅ `[IMPLEMENTED]` | Schedule partner logistics | [Partners](../entity-management/partners.md#meetings) |
| Attendance Analytics | ✅ `[IMPLEMENTED]` | Partner dashboard with cost-per-attendee KPI + XLSX export | [Analytics](../features/analytics.md) |
| Topic Voting | ✅ `[IMPLEMENTED]` | Partners suggest topics and toggle-vote | [Partners](../entity-management/partners.md) |
| Partner Meeting Coordination | ✅ `[IMPLEMENTED]` | ICS calendar invites + meeting notes | [Partners](../entity-management/partners.md#meetings) |
| Partner Portal | ✅ `[IMPLEMENTED]` | Self-service analytics and voting (Epic 8) | [Partners](../entity-management/partners.md) |

### Speakers

| Feature | Status | Notes | Documentation |
|---------|--------|-------|---------------|
| CRUD Operations | ✅ `[IMPLEMENTED]` | Full speaker management | [Speaker Management](../entity-management/speakers.md) |
| Profile Management | ✅ `[IMPLEMENTED]` | Bio, headshot, expertise | [Speakers](../entity-management/speakers.md#profiles) |
| Status Tracking | ✅ `[IMPLEMENTED]` | 8 states (Identified → Published) | [Speakers](../entity-management/speakers.md#status-tracking) |
| Content Requirements | ✅ `[IMPLEMENTED]` | Presentation specs, deadlines | [Speakers](../entity-management/speakers.md#content) |
| Speaker Ratings | 📋 `[PLANNED]` | Q2 2025 | Post-event feedback, track performance |
| Speaker Pool | 📋 `[PLANNED]` | Q2 2025 | Reusable database across events |

---

## Event Workflow Management (Epic 5)

**Status**: ✅ 100% Complete (8/8 stories)

**Note:** Epic 5 workflow is now a **parallel workflow architecture** with event states, per-speaker workflows, and configurable tasks - not a linear 16-step process.

### Phase A: Setup

| Feature | Status | Notes | Documentation |
|---------|--------|-------|---------------|
| Step 1: Event Setup | ✅ `[IMPLEMENTED]` | Event type, date, venue configuration | [Phase A](../workflow/phase-a-setup.md#step-1) |
| Step 2: Topic Selection | ✅ `[IMPLEMENTED]` | Topic backlog with heat map | [Phase A](../workflow/phase-a-setup.md#step-2) |
| Step 3: Speaker Brainstorming | ✅ `[IMPLEMENTED]` | Identify 8-10+ speakers | [Phase A](../workflow/phase-a-setup.md#step-3) |

### Phase B: Outreach

| Feature | Status | Notes | Documentation |
|---------|--------|-------|---------------|
| Step 4: Outreach Initiated | ✅ `[IMPLEMENTED]` | Email invitations to speakers | [Phase B](../workflow/phase-b-outreach.md#step-4) |
| Step 5: Status Management | ✅ `[IMPLEMENTED]` | Track Accepted/Rejected/Pending | [Phase B](../workflow/phase-b-outreach.md#step-5) |
| Step 6: Content Collection | ✅ `[IMPLEMENTED]` | Gather presentation materials (≤1000 chars) | [Phase B](../workflow/phase-b-outreach.md#step-6) |
| Automated Reminders | ✅ `[IMPLEMENTED]` | Speaker invitation emails + 3-tier deadline reminder escalation (Epic 6) | [Notifications](../features/notifications.md) |

### Phase C: Quality Review

| Feature | Status | Notes | Documentation |
|---------|--------|-------|---------------|
| Step 7: Quality Review | ✅ `[IMPLEMENTED]` | Approve/Request Revisions/Reject | [Phase C](../workflow/phase-c-quality.md#step-7) |
| Step 8: Threshold Validation | ✅ `[IMPLEMENTED]` | Minimum 10 approved speakers (full-day) | [Phase C](../workflow/phase-c-quality.md#step-8) |
| Review Queue | ✅ `[IMPLEMENTED]` | Prioritized review interface | [Phase C](../workflow/phase-c-quality.md#review-queue) |

### Phase D: Slot Assignment & Publishing

| Feature | Status | Notes | Documentation |
|---------|--------|-------|---------------|
| Step 9: Overflow Management | ✅ `[IMPLEMENTED]` | Voting for overflow speakers | [Phase D](../workflow/phase-d-assignment.md#step-9) |
| Step 10: Slot Assignment | ✅ `[IMPLEMENTED]` | Drag-and-drop time slot assignment | [Phase D](../workflow/phase-d-assignment.md#step-10) |
| Conflict Detection | ✅ `[IMPLEMENTED]` | Speaker unavailability, double-booking | [Phase D](../workflow/phase-d-assignment.md#conflicts) |

### Phase E: Archival & Publishing

| Feature | Status | Notes | Documentation |
|---------|--------|-------|---------------|
| Step 11: Topic Publishing | ✅ `[IMPLEMENTED]` | Progressive disclosure: Topics first | [Phase E](../workflow/phase-e-publishing.md#step-11) |
| Step 12: Speaker Publishing | ✅ `[IMPLEMENTED]` | Speaker profiles → Agenda finalization | [Phase E](../workflow/phase-e-publishing.md#step-12) |
| Dropout Handling | ✅ `[IMPLEMENTED]` | Manage last-minute withdrawals | [Phase E](../workflow/phase-e-publishing.md#dropout-handling) |
| Auto-Publishing Engine | ✅ `[IMPLEMENTED]` | CDN integration, scheduled publishing | Part of Event Lifecycle |
| Event Finalization | ✅ `[IMPLEMENTED]` | AGENDA_FINALIZED state transition | Part of Event Lifecycle |
| Event Completion | ✅ `[IMPLEMENTED]` | EVENT_COMPLETED→ARCHIVED automation | Part of Event Lifecycle |

### Phase F: Event Completion & Communication

| Feature | Status | Notes | Documentation |
|---------|--------|-------|---------------|
| Newsletter Integration | ✅ `[IMPLEMENTED]` | 3 newsletter task templates | Part of Event Lifecycle |
| Overflow Management | ✅ `[IMPLEMENTED]` | Speaker voting and backup slots | Part of Event Lifecycle |
| Task System | ✅ `[IMPLEMENTED]` | Configurable tasks with triggers | [Task System](../workflow/task-system.md) |
| Partner Meetings | ✅ `[IMPLEMENTED]` | Coordinate sponsor logistics | [Task System](../workflow/task-system.md) |

---

## Implemented Post-MVP Features (Phase 2)

### Epic 6: Speaker Self-Service Portal ✅

| Feature | Status | Notes |
|---------|--------|-------|
| Automated Speaker Invitations | ✅ `[IMPLEMENTED]` | Magic link emails via AWS SES with unique response links |
| Speaker Self-Service Response Portal | ✅ `[IMPLEMENTED]` | Accept/Decline via unique link, no authentication required |
| Speaker Material Self-Submission | ✅ `[IMPLEMENTED]` | Title, abstract, CV, photo, presentation upload via S3 presigned URLs |
| Speaker Dashboard (View-Only) | ✅ `[IMPLEMENTED]` | Upcoming/past events; WCAG 2.1 AA; i18n EN/DE |
| Automated Deadline Reminders | ✅ `[IMPLEMENTED]` | 3-tier escalation: friendly → urgent → organizer escalation |

### Epic 8: Partner Coordination ✅

| Feature | Status | Notes |
|---------|--------|-------|
| Partner Attendance Analytics Dashboard | ✅ `[IMPLEMENTED]` | Last 5 years table, cost-per-attendee KPI, XLSX export (Apache POI) |
| Topic Suggestions & Voting | ✅ `[IMPLEMENTED]` | Partners suggest + toggle-vote; organizers set Selected/Declined |
| Partner Meeting Coordination | ✅ `[IMPLEMENTED]` | RFC 5545 ICS calendar invites with agenda via AWS SES; meeting notes |

---

## Attendee Experience (Epic 7) 🔨

**Status**: 🔨 Largely implemented (in review). No new backend service — composes on existing topic-suggestion, speaker-workflow, registration, task, and SSO machinery. Identity rides "Continue with Google" (Epic 12); the default `ATTENDEE` role suffices for all contribution endpoints.

| Feature | Status | Notes |
|---------|--------|-------|
| Topics From the Floor (7.1) | ✅ `[IMPLEMENTED]` | Logged-in attendees suggest future topics (title + rationale) into the existing topic-suggestion pool, tagged `source = community`; organizers triage in the topic-suggestion admin UI (community badge) |
| "I Could Speak on That" — Speaker Self-Nomination (7.2) | ✅ `[IMPLEMENTED]` | Once an event's topic is set and published, logged-in attendees self-nominate (session title + abstract). Creates a `speaker_pool` entry at `IDENTIFIED`; organizers triage via the 8-state speaker workflow. No auto-provisioning at nomination |
| "The Slides Are Online" Mail (7.3) | 🔨 `[IN PROGRESS]` | Auto-created organizer task (~1 day post-event) sends a "slides are online" email to active registrants (DE + EN), honouring email opt-out, with a double-send guard |
| Thank-the-Organizers (7.4) | 🔨 `[IN PROGRESS]` | One-click post-event thank-you, optional note. Anonymous allowed (Turnstile + rate-limit); logged-in deduped to one per event. Aggregate public appreciation counter; notes are organizer-visible |
| The Apéro Continues — Post-Event Q&A (7.5) | 🔨 `[IN PROGRESS]` | Time-boxed (~14-day, organizer-overridable) per-session Q&A. Logged-in attendees post/answer; organizers can take down posts and extend/close early. On close it freezes read-only and attaches to the session archive page. Reading frozen Q&A is public; posting is login-gated |
| Attendee Event History (7.6) | 🔨 `[IN PROGRESS]` | Logged-in attendee dashboard listing the events they registered for / attended |
| Curated Thank-You Marquee (7.7) | 📋 `[PLANNED]` | Organizers feature selected thank-you notes intermingled into the public homepage partner marquee (ready-for-dev) |

---

## Admin Tools, Newsletter & Public Enhancements (Epic 10) ✅

**Status**: ✅ Largely complete. (Deferred: 10-24 Cognito provisioning-from-user-mgmt, 10-25 partner-meeting iCal auto-creation. Cancelled: 10-6, 10-13, 10-15.)

### Administration Page

| Feature | Status | Notes |
|---------|--------|-------|
| Organizer Admin Page | ✅ `[IMPLEMENTED]` | `/organizer/admin` with Event Types, Import Data, Task Templates, and Email Template management tabs |
| Event Type Configuration | ✅ `[IMPLEMENTED]` | Slot templates for FULL_DAY, AFTERNOON, EVENING |
| Task Template Management | ✅ `[IMPLEMENTED]` | Default read-only templates + custom template CRUD with trigger states and timing |
| Email Template Editor | ✅ `[IMPLEMENTED]` | TinyMCE WYSIWYG (content templates) + Monaco (layout templates); DE/EN toggle; live preview |
| Task Deadline Reminder Emails | ✅ `[IMPLEMENTED]` | Triggered by due date / state |
| Blob / Heat-Map Topic Selector | ✅ `[IMPLEMENTED]` | Interactive topic selection visual |
| Legacy BAT-Format Data Export/Import | ✅ `[IMPLEMENTED]` | Round-trips the legacy BATbern data format |
| AI-Assisted Event Content Creation | ✅ `[IMPLEMENTED]` | Drafting assistance for event content |

### Newsletter & Email

| Feature | Status | Notes |
|---------|--------|-------|
| Newsletter Subscription & Sending | ✅ `[IMPLEMENTED]` | Subscribe, send, template selection, subscriber management |
| Email-Reply Unsubscribe / Deregistration | ✅ `[IMPLEMENTED]` | Reply-based unsubscribe and event deregistration |
| SES Email Forwarding / Distribution Lists | ✅ `[IMPLEMENTED]` | `batbernNN@batbern.ch` forwarding |
| SES Bounce Processing + List Hygiene | ✅ `[IMPLEMENTED]` | Automatic newsletter list cleanup on bounces |
| Additional User Emails | ✅ `[IMPLEMENTED]` | "Receive at" + "send-as" authorised aliases per user |

### Registration Lifecycle & Public Site

| Feature | Status | Notes |
|---------|--------|-------|
| Registration Status Indicator | ✅ `[IMPLEMENTED]` | Shows current registration state to attendees |
| Venue Capacity Enforcement + Waitlist | ✅ `[IMPLEMENTED]` | Caps registration at venue capacity; manages a waitlist |
| Self-Service Deregistration | ✅ `[IMPLEMENTED]` | Attendees can cancel their own registration |
| Event Photos Gallery | ✅ `[IMPLEMENTED]` | Post-event photo gallery |
| Event Teaser Image | ✅ `[IMPLEMENTED]` | Teaser image on event + homepage |
| Event Description on Public Homepage | ✅ `[IMPLEMENTED]` | Description section surfaced publicly |
| Moderator Presentation Page | ✅ `[IMPLEMENTED]` | On-stage presentation view (+ animations) |
| Organizer Analytics Dashboard | ✅ `[IMPLEMENTED]` | Event progress, speaker pipeline, team activity |
| Turnstile Bot Protection | ✅ `[IMPLEMENTED]` | Cloudflare Turnstile on public submit flows |

---

## Unified Speaker Workflow Refactor (Epic 11) ✅

**Status**: ✅ 100% Complete (ADR-009). Supersedes the abandoned Epic 9 plan.

| Feature | Status | Notes |
|---------|--------|-------|
| 8-State Speaker Workflow | ✅ `[IMPLEMENTED]` | Reduced workflow to an 8-state model; `SpeakerWorkflowService` is the sole status writer |
| Workflow Unified into Event-Management Service | ✅ `[IMPLEMENTED]` | Dropped the standalone `speakers` table + speaker-coordination service refs |
| Cognito Speaker Authentication | ✅ `[IMPLEMENTED]` | Speaker portal is Cognito-authenticated; the original magic-link login was fully torn down |
| Cognito Provisioning at READY | ✅ `[IMPLEMENTED]` | Cognito user provisioning happens at the `READY` transition |
| Kanban Drawer Redesign + Guided Drag | ✅ `[IMPLEMENTED]` | Redesigned organizer speaker-pool drawer |
| Speaker Company Self-Service (11.G.1) | ✅ `[IMPLEMENTED]` | Speakers manage their own company association |

---

## Federated Identity / Google SSO (Epic 12) ✅

**Status**: ✅ Complete & live (ADR-010). "Continue with Google" login at `auth.batbern.ch`.

| Feature | Status | Notes |
|---------|--------|-------|
| "Continue with Google" Login | ✅ `[IMPLEMENTED]` | Google OIDC federation at `auth.batbern.ch` |
| Transparent Account Linking | ✅ `[IMPLEMENTED]` | Links a Google identity to an existing account by email |
| JIT Provisioning | ✅ `[IMPLEMENTED]` | Just-in-time user creation on first SSO login |
| Terms-of-Service Consent Gate | ✅ `[IMPLEMENTED]` | Federated onboarding completion (consent / company / newsletter) |
| Google Avatar Import | ✅ `[IMPLEMENTED]` | Imports the user's Google profile photo |
| Runtime Kill-Switch | ✅ `[IMPLEMENTED]` | `FEATURES_SSO_ENABLED` toggles SSO at runtime |
| Apple / Generic OIDC | 💡 `[BACKLOG]` | Story 12-10 — deferred |

---

## Spring Boot 4 Migration (Epic 13) 💡

**Status**: 💡 Planned (backlog). Spring Boot 3.5 OSS support ends 2026-06-30.

| Feature | Status | Notes |
|---------|--------|-------|
| Spring Boot 4 Upgrade | 💡 `[BACKLOG]` | Backend framework migration across all services |

---

## Advanced Features

### Topic Heat Maps ✅

| Feature | Status | Notes | Documentation |
|---------|--------|-------|---------------|
| Historical Visualization | ✅ `[IMPLEMENTED]` | 20+ years of event history | [Heat Maps](../features/heat-maps.md) |
| Color-Coded Frequency | ✅ `[IMPLEMENTED]` | Recency + frequency algorithm | [Heat Maps](../features/heat-maps.md#color-scale) |
| Interactive Filtering | ✅ `[IMPLEMENTED]` | Time range, categories, thresholds | [Heat Maps](../features/heat-maps.md#filtering) |
| Topic Aliases | 📋 `[PLANNED]` | Q2 2025 | Merge similar topic names |

### File Uploads ✅

| Feature | Status | Notes | Documentation |
|---------|--------|-------|---------------|
| Presigned S3 URLs | ✅ `[IMPLEMENTED]` | Direct browser-to-S3 upload | [File Uploads](../features/file-uploads.md) |
| Company Logos | ✅ `[IMPLEMENTED]` | PNG, JPG, SVG (max 5 MB) | [File Uploads](../features/file-uploads.md) |
| Speaker Materials | ✅ `[IMPLEMENTED]` | PDF, PPTX (max 25 MB) | [File Uploads](../features/file-uploads.md) |
| Progress Tracking | ✅ `[IMPLEMENTED]` | Real-time upload progress | [File Uploads](../features/file-uploads.md) |
| Auto-Conversion | 📋 `[PLANNED]` | Q2 2025 | PPTX/KEY → PDF |
| Virus Scanning | 📋 `[PLANNED]` | Q2 2025 | ClamAV integration |

### Notifications

| Feature | Status | Notes | Documentation |
|---------|--------|-------|---------------|
| Transactional Email Notifications | ✅ `[IMPLEMENTED]` | Speaker invitations, deadline reminders, registration confirmations, partner ICS | [Notifications](../features/notifications.md) |
| Speaker Deadline Reminder Escalation | ✅ `[IMPLEMENTED]` | 3-tier: 1 month / 2 weeks / 3 days before content deadline | [Notifications](../features/notifications.md) |
| In-App Notification Center | 🔨 `[IN PROGRESS]` | Feed with mark-as-read, delete, view all; full rules engine planned | [Notifications](../features/notifications.md) |
| Custom Notification Rules | 📋 `[PLANNED]` | Per-event settings, quiet hours, digest scheduling |

### Analytics

| Feature | Status | Notes | Documentation |
|---------|--------|-------|---------------|
| Partner Attendance Analytics | ✅ `[IMPLEMENTED]` | Attendance table, cost-per-attendee KPI, XLSX export (Epic 8) | [Analytics](../features/analytics.md) |
| Organizer Workflow Analytics | ✅ `[IMPLEMENTED]` | Event progress, speaker pipeline, team activity feed | [Analytics](../features/analytics.md) |
| Full Attendance Funnel | 📋 `[PLANNED]` | Registration funnel, no-show rates, dropout analysis | [Analytics](../features/analytics.md) |
| Content Performance | 📋 `[PLANNED]` | Session attendance, speaker ratings | [Analytics](../features/analytics.md) |
| Satisfaction Surveys (CSAT/NPS) | 📋 `[PLANNED]` | Post-event surveys, open feedback | [Analytics](../features/analytics.md) |
| Financial Analytics | 📋 `[PLANNED]` | Budget variance, ROI, cost breakdown | [Analytics](../features/analytics.md) |

### Keyboard Shortcuts 📋

| Feature | Status | Notes | Documentation |
|---------|--------|-------|---------------|
| Navigation Shortcuts | 📋 `[PLANNED]` | Q2 2025 | G+D, G+E, J/K navigation | [Shortcuts](keyboard-shortcuts.md) |
| CRUD Shortcuts | 📋 `[PLANNED]` | Q2 2025 | Ctrl+N, Ctrl+S, Ctrl+E | [Shortcuts](keyboard-shortcuts.md) |
| Workflow Shortcuts | 📋 `[PLANNED]` | Q2 2025 | Ctrl+Enter, A/R approve/reject | [Shortcuts](keyboard-shortcuts.md) |
| Custom Keymaps | 📋 `[PLANNED]` | Q3 2025 | User-defined shortcuts |

---

## Roadmap Summary

### Completed (2025 - Q2 2026)

**✅ All delivered:**

- ✅ Epics 1-6: Foundation → CRUD → Migration → Public Website → Organizer Workflows → Speaker Portal
- ✅ Epic 8: Partner Coordination (analytics, topic voting, meeting coordination, partner notes, iCal RSVP)
- ✅ Epic 10: Admin tools, newsletter, registration lifecycle/waitlist/deregistration, photo gallery, teaser images, moderator presentation, Turnstile, additional user emails, legacy BAT export/import
- ✅ Epic 11: Unified Speaker Workflow Refactor (8-state model, Cognito speaker auth, magic-link teardown)
- ✅ Epic 12: Federated Identity / Google SSO ("Continue with Google")
- ✅ Multi-role E2E test auth (organizer / speaker / partner)
- ✅ 4-layer test suite (shell scripts, Bruno API, Playwright UI, CDK infra)

> **Superseded**: Epic 9 (original JWT magic-link speaker auth plan) is no longer active — replaced by Epics 11 + 12.

### In Review (Q2 2026)

**Focus**: Attendee contribution & touchpoints (Epic 7)

- 🔨 Topics from the floor, speaker self-nomination (done)
- 🔨 "Slides are online" mail, thank-the-organizers, post-event Q&A, attendee event history (in review)
- 📋 Curated thank-you marquee (ready-for-dev)
- 📋 Epic 3: Production historical data import (pending a user trigger)

### Planned / Backlog

- 💡 Epic 13: Spring Boot 4 migration (SB 3.5 OSS support ends 2026-06-30)
- 💡 Apple / generic OIDC SSO (Story 12-10)
- 💡 Multi-Factor Authentication (MFA)
- 💡 Company Hierarchy (parent/subsidiary)
- 💡 Recurring Events & Templates
- 💡 Financial Analytics & Reporting

---

## Feature Request Process

### How to Request a Feature

1. **Check This Page**: Verify feature isn't already planned
2. **Email**: Send detailed request to product@batbern.ch
3. **Include**:
   - Feature description (what it does)
   - Use case (why you need it)
   - Priority (how urgent)
   - Workaround (current solution, if any)

### Prioritization Criteria

Features are prioritized based on:

1. **User Impact** - How many organizers benefit?
2. **Business Value** - Does it enable new use cases?
3. **Development Effort** - How complex to implement?
4. **Dependencies** - Does it block other features?
5. **Strategic Fit** - Aligns with product vision?

### Typical Timelines

- **Small**: 1-2 weeks (minor UI enhancements, bug fixes)
- **Medium**: 4-8 weeks (new entity types, workflow extensions)
- **Large**: 12-16 weeks (major features like analytics, notifications)
- **Epic**: 20+ weeks (platform overhauls, mobile apps)

---

## Beta / Early Access

### How to Join Beta Program

**Beta Features** (available for testing):
- Attendee contribution features (Epic 7) — "slides are online" mail, thank-the-organizers, post-event Q&A, and attendee event history are in review and may be previewed by beta organizers

**Sign Up**:
1. Email beta@batbern.ch with subject "Beta Interest"
2. Include: Name, role, event count per year
3. We'll notify you when beta slots open

**Beta Requirements**:
- Active organizer (created at least 2 events)
- Willing to provide feedback
- Understand features may be incomplete or unstable

---

## Deprecation Notice

### No Deprecated Features

All currently implemented features remain supported. When features are deprecated in future, this section will provide:

- **Feature name** and deprecation date
- **Reason** for deprecation
- **Replacement** feature or workaround
- **Timeline** for removal (minimum 6 months notice)
- **Migration guide** for affected users

---

## Related Resources

- **[Changelog](changelog.md)** - Version history and release notes
- **[Glossary](glossary.md)** - Platform terminology
- **[Documentation Home](../README.md)** - Full user guide

---

**Have questions?** Contact product@batbern.ch or support@batbern.ch

**Back to Main**: Return to [Documentation Home](../README.md) →
