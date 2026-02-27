# Feature Status & Roadmap

## Overview

This page tracks implementation status of all BATbern features. Use this to understand what's available now, what's in progress, and what's coming soon.

**Last Updated**: 2026-02-27

**MVP Status**: ✅ **100% COMPLETE & PRODUCTION READY** - All 5 MVP epics (1-5) complete. Epics 6 and 8 also complete.

**Platform Readiness:**
- **Epics 1-2**: 100% Complete (Foundation, Entity CRUD)
- **Epic 3**: 100% Complete (Historical data migration tooling ready, production import pending)
- **Epic 4**: 100% Complete (Public website, registration, archive browsing, content search)
- **Epic 5**: 100% Complete (8/8 stories done, event workflow, speaker coordination, auto-publishing, lifecycle automation)
- **Epic 6**: 100% Complete (Speaker self-service portal — invitations, response, materials, dashboard, reminders)
- **Epic 8**: 100% Complete (Partner coordination — attendance analytics, topic voting, meeting coordination)

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
| Role-Based Access Control | ✅ `[IMPLEMENTED]` | ORGANIZER, ADMIN, SPEAKER, ATTENDEE | [User Management](../entity-management/users.md#roles-permissions) |
| Multi-Factor Authentication (MFA) | 📋 `[PLANNED]` | Q2 2025 | Authenticator app + SMS backup |
| Single Sign-On (SSO) | 💡 `[BACKLOG]` | Enterprise requirement | SAML 2.0, OAuth 2.0 |

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

## Deferred Features (Phase 3+)

**Status**: 📦 Deferred to Phase 3+ (Post-Epic 8)

### Epic 7: Attendee Experience Enhancements

| Feature | Status | Notes |
|---------|--------|-------|
| Personal Attendee Dashboard | 💡 `[BACKLOG]` | Epic 7 - Phase 3+ | Bookmarks, learning progress, recommendations |
| Content Discovery (Advanced) | 💡 `[BACKLOG]` | Epic 7 - Phase 3+ | Personal content bookmarks and collections |
| Mobile PWA | 💡 `[BACKLOG]` | Epic 7 - Phase 3+ | Progressive Web App with offline capabilities |
| Advanced User Preferences | 💡 `[BACKLOG]` | Epic 7 - Phase 3+ | Content language, experience level, formats |
| Language & Accessibility | 💡 `[BACKLOG]` | Epic 7 - Phase 3+ | UI language, date formats, accessibility options |

**Current Functionality**: Public archive browsing and registration flow operational (Epic 4).

### Epic 9: Speaker Authentication & Account Integration 🔨 In Progress

| Feature | Status | Notes |
|---------|--------|-------|
| JWT-Based Magic Link Auth | ✅ `[IMPLEMENTED]` | Story 9.1 complete — RS256 JWT, HTTP-only cookie, session bridge to existing dashboard; backward compatible with Epic 6 tokens |
| Auto Account Creation on Invitation Accept | 📋 `[PLANNED]` | Story 9.2 — Cognito user creation/role extension with SPEAKER role |
| Dual Auth (Magic Link + Email/Password) | 📋 `[PLANNED]` | Story 9.3 — email/password fallback for speakers |
| Unified Multi-Role Navigation | 📋 `[PLANNED]` | Story 9.5 — speaker + attendee portal in single session |
| Epic 6 Token Migration Script | 📋 `[PLANNED]` | Story 9.4 — 7-day grace period migration of staging token-based users |

### Epic 10: Admin Tools & Email Templates 🔨 In Progress

| Feature | Status | Notes |
|---------|--------|-------|
| Organizer Admin Page | 🔨 `[IN PROGRESS]` | Story 10.1 — `/organizer/admin` with 4 tabs: Event Types, Import Data, Task Templates, Email Templates |
| Event Type Configuration | 🔨 `[IN PROGRESS]` | Slot templates for FULL_DAY, AFTERNOON, EVENING |
| Historical Data Import | 🔨 `[IN PROGRESS]` | 5 batch import modals: Events, Sessions, Companies, Speakers, Participants; CSV/JSON with validation and error export |
| Task Template Management | 🔨 `[IN PROGRESS]` | Default read-only templates + custom template CRUD with trigger states and timing |
| Email Template Editor | 🔨 `[IN PROGRESS]` | Story 10.2 — Monaco editor (layout templates) + TinyMCE WYSIWYG (content templates); 22 system templates seeded; DE/EN toggle; live preview |

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

### Completed (2025 - Q1 2026)

**✅ All delivered:**

- ✅ Epics 1-6: Foundation → CRUD → Migration → Public Website → Organizer Workflows → Speaker Portal
- ✅ Epic 8: Partner Coordination (analytics, topic voting, meeting coordination)
- ✅ Multi-role E2E test auth (organizer / speaker / partner)
- ✅ 4-layer test suite (shell scripts, Bruno API, Playwright UI, CDK infra)

### Q1–Q2 2026 (Next)

**Focus**: Epic 9 Completion + Production Launch

- 🔨 Epic 9 Story 9.1: JWT magic link auth — ✅ DONE
- 📋 Epic 9 Stories 9.2–9.5: Cognito account creation, dual auth, migration script, multi-role navigation
- 📋 Epic 3: Production data import (2,307 historical participants, ~1 day effort)
- 📋 Production deployment of Epics 6 and 8

**Priority**: High — enables production go-live

### Q3–Q4 2026

**Focus**: Attendee Experience (Epic 7)

- 💡 Personal attendee dashboard (bookmarks, learning progress)
- 💡 Advanced content discovery and collections
- 💡 Mobile PWA with offline capabilities
- 💡 Advanced user preferences (language, accessibility)

**Priority**: Medium — enhances attendee engagement

### Future Backlog

- 💡 Multi-Factor Authentication (MFA)
- 💡 Company Hierarchy (parent/subsidiary)
- 💡 Recurring Events & Templates
- 💡 Single Sign-On (SSO)
- 💡 Slack/Teams Integration
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
- Currently no beta features available
- Check back when Epic 10 Admin Tools reach release-candidate status

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
