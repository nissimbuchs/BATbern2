# BATbern Event Management Platform - Enhanced PRD

**Last Updated:** 2026-11-04 (doc audit #1210: epic statuses through Epic 16, FR24–FR41 for shipped capabilities, owner-decision notes on FR7/FR14/FR15/FR18/FR20 and §4.2; earlier 2026-10-24: FR2 8 states, task rules)

## 1. Project Analysis and Context

### Enhancement Type
✅ **Complete Platform Rewrite** + ✅ New Feature Addition + ✅ UI/UX Overhaul

### Enhancement Description
Complete rewrite of BATbern as a comprehensive event management platform using React frontend with Java/Spring Boot backend, transforming from static conference website into dynamic event ecosystem with automated workflows, real-time analytics, and intelligent content management.

### Impact Assessment
✅ **Revolutionary Impact** (complete architectural transformation)

### Goals
- Automate event planning through an 8-state event workflow with parallel speaker coordination and configurable task management
- Eliminate manual speaker coordination and material collection via self-service workflows
- Provide real-time ROI analytics for partners
- Provide searchable content archive across 20+ years of conference content
- Transform static website into dynamic event management platform

### Strategic Decisions
- **Technology Stack**: React frontend + Java/Spring Boot backend
- **Approach**: Complete platform rewrite (revolutionary transformation)
- **Implementation**: Custom solution for all components
- **Sequence**: Foundation → Event Timeline → Speaker Portal → Partner Analytics

## 2. Requirements

### Functional Requirements

**FR1**: The platform shall provide role-based authentication with distinct interfaces for organizers, speakers, partners, and attendees

**FR2**: Event organizers shall manage events through an 8-state workflow with parallel speaker coordination and configurable task management, including automated deadline tracking, stakeholder coordination, and real-time progress dashboards with cross-role visibility

**Event Workflow (8-State State Machine):**
The event progresses through high-level states while speakers progress individually in parallel.
`AGENDA_FINALIZED` was removed (V82/V84); the agenda goes from published straight to the event.

| State | Description | Entry Condition | Exit Condition |
|-------|-------------|-----------------|----------------|
| **CREATED** | Event created, no topic selected | Event created | Topic selected |
| **TOPIC_SELECTION** | Topic selected, brainstorming speakers | Topic selected | Minimum speakers in pool |
| **SPEAKER_IDENTIFICATION** | Building speaker pool, outreach in progress | Min speakers in pool | All slots filled |
| **SLOT_ASSIGNMENT** | Speakers assigned to time slots | All slots filled | Agenda published |
| **AGENDA_PUBLISHED** | Agenda public, accepting registrations | Agenda published (manually, when all sessions are timed, or by auto-publish 14 days before) | Event day (scheduler) |
| **EVENT_LIVE** | Event currently happening | Event day | All sessions done (Watch) or scheduler |
| **EVENT_COMPLETED** | Event finished, post-processing | After event | Archival |
| **ARCHIVED** | Event archived | Archival trigger | Terminal state |

Every state change goes through the workflow state machine, so that tasks, Q&A windows and
archival cleanup react to it, whether the change comes from an organizer, the auto-publish job,
an event edit or the Watch.

**Speaker Workflow (Parallel Per-Speaker Progression):**
Per ADR-009, each speaker progresses independently through an 8-state machine:
- **IDENTIFIED** → **CONTACTED** → **READY** → **INVITED** → **ACCEPTED** → **CONTENT_SUBMITTED** → **QUALITY_REVIEWED** (terminal happy state)
- **DECLINED** is reachable from any non-terminal state (covers leads that don't pan out, declined invitations, and post-acceptance withdrawals via status-history reason)
- **QUALITY_REVIEWED → CONTENT_SUBMITTED** is a legal back-transition (Story 11.E.8): a speaker (or organizer-on-behalf) can revise content after the moderator's review; the new submission lands in `session_content_history` with a fresh version, and the derived content status returns to `SUBMITTED` until re-reviewed
- `slot_assigned` and `publishable` are **derived flags** computed at read time (no persisted columns) — `slot_assigned := session.start_time IS NOT NULL`, `publishable := QUALITY_REVIEWED AND slot_assigned`
- Speaker provisioning (User row + Cognito account + SPEAKER role grant) occurs at the **CONTACTED → READY** transition, gated by presence of email
- **`Session` + PRIMARY_SPEAKER `session_users` row** are also provisioned at **CONTACTED → READY** (Story 11.E.8), making the existing organizer Sessions tab and speaker portal content/material flows reachable from READY onward — not blocked behind content submission
- Capacity is enforced at **READY → INVITED**: organizers cannot exceed `max_slots` for the event
- Content versioning lives on **`session_content_history`** keyed by `(session_id, submission_version)` and is **inserted only by `ContentSubmissionService.submit()`** — a pure audit log of *speaker* submissions. The **canonical current title and abstract live on `sessions.title` / `sessions.description`** (Story 11.E.8 §2.9); every surface (public archive, organizer kanban, organizer session-edit modal, speaker portal form) reads from there, ensuring an edit on any surface is immediately visible to the others. `speaker_pool` carries only workflow state, no denormalized content fields. The legacy `speaker_pool.content_status` string is now derived at read time via `ContentStatusDeriver` (PENDING / SUBMITTED / REVISION_NEEDED / APPROVED) from the latest history row's `reviewer_feedback` + workflow state — JSON shape preserved for the frontend, source of truth moved. Speaker-portal drafts live in browser `localStorage`; the backend `/content/draft` endpoint was retired

**Task Management System (Configurable Work Items):**
Tasks are NOT workflow states - they are assignable work items with due dates. A task is created
`pending` and becomes active at whichever comes first: its trigger (a workflow state, or the
speakers being published), or a configurable number of days before its due date (default 14;
none for post-event tasks). A daily job re-checks both rules, so a missed transition can no
longer leave a task invisible. A newsletter task is cancelled automatically once a later
announcement has gone out.

| Default Task | Trigger | Due Date |
|--------------|---------|----------|
| Venue Booking | TOPIC_SELECTION | 90 days before event |
| Partner Meeting | TOPIC_SELECTION | Same day as event |
| Moderator Assignment | TOPIC_SELECTION | 14 days before event |
| Newsletter: Topic | TOPIC_SELECTION | Immediate; cancelled once speakers or agenda are published |
| Newsletter: Speakers | Speakers published | 30 days before event; cancelled once the agenda is published |
| Newsletter: Final | AGENDA_PUBLISHED | 14 days before event |
| Catering (final headcount) | AGENDA_PUBLISHED | 5 days before event |
| Namensschilder (name tags) | AGENDA_PUBLISHED | 2 days before event |
| Wein (wine) | AGENDA_PUBLISHED | Event day |
| Newsletter: Slides Are Online | EVENT_COMPLETED | 1 day after event |

Custom tasks can be created with configurable triggers, due dates, activation lead days, and assigned organizers.

See [Workflow State Machines](./architecture/06a-workflow-state-machines.md) for detailed implementation.

**FR3**: The system shall provide automated speaker invitation, submission, and material collection workflows with real-time status updates. Speaker authentication uses standard AWS Cognito with `FORCE_CHANGE_PASSWORD` on first login (per ADR-009 §0.5) — the invitation email carries a login link and a temporary password; no magic-link or token-based auth path exists

**FR4**: Partners shall access analytics dashboards showing employee attendance of their company for the past events

**FR5**: The platform shall support progressive event publishing with automatic content updates from topic definition through final agenda publication

**FR6**: Attendees shall access a prominent current event landing page featuring the upcoming BATbern event with complete logistics (date, location, free attendance), speaker lineup, agenda details, and registration functionality

**FR7**: The system shall integrate email notification workflows for event updates, speaker deadlines, and newsletter distribution, supported by a comprehensive email template management system that allows organizers to create, customize, and version email templates with variable substitution, multilingual support, and A/B testing capabilities for different stakeholder groups (speakers, attendees, partners, organizers)

> **Not implemented as of 2026-11-04 (owner decision on strike/defer/rewrite pending: #1210).** What exists: database-backed email template management with variable substitution and DE/EN templates (Story 10.2), layouts, newsletter template selection (Story 10.14), own text with placeholders and preview (Story 16.5). Not found in code: A/B testing, template version history.

**FR8**: Partner users shall vote on topic priorities and submit strategic topic suggestions outside formal partner meetings

**FR10**: Speaker users shall access self-service portal for submission management, agenda viewing, and presentation upload

**FR11**: The system shall maintain complete event archive with presentation downloads, speaker profiles, and photo galleries accessible via secondary navigation

**FR12**: Event organizers shall manage multi-year venue reservations, catering coordination, partner meeting scheduling, and logistics through integrated workflow tools with automated reminders and stakeholder notifications

**FR13**: [REMOVED - Strategic refocus per Sprint Change Proposal 2025-10-01]

**FR14**: Attendees shall manage personal engagement through newsletter subscriptions, content bookmarking, and presentation downloads with personalized preferences, including granular notification preference controls allowing users to opt in/out of specific notification types (event updates, speaker announcements, partner communications, system alerts) across multiple channels (email, in-app, push notifications) with frequency management and quiet hours settings

> **Not implemented as of 2026-11-04 (owner decision on strike/defer/rewrite pending: #1210).** What exists: newsletter subscription (Story 10.7), per-user preference columns including quiet hours and a push flag (CUMS `UserPreferences`), a quiet-hours check in EMS `NotificationService`. Not found: a push channel (no FCM/SNS), content bookmarking, per-channel opt-in UI.

**FR15**: The platform shall provide mobile-optimized attendee experience with offline content access, event check-in capabilities, and progressive web app functionality

> **Not implemented as of 2026-11-04 (owner decision on strike/defer/rewrite pending: #1210).** What exists: responsive UI, a PWA manifest and Workbox service worker (`vite-plugin-pwa`, navigations NetworkFirst). Not found: offline content access beyond the service-worker cache, attendee check-in.

**FR16**: [REMOVED - Strategic refocus per Sprint Change Proposal 2025-10-01]

**FR17**: The system shall provide intelligent speaker matching and assignment tracking with the unified 8-state speaker workflow (IDENTIFIED → CONTACTED → READY → INVITED → ACCEPTED → CONTENT_SUBMITTED → QUALITY_REVIEWED; DECLINED reachable from any non-terminal state) per ADR-009, supporting parallel per-speaker progression, real-time organizer collaboration including slot preference collection and technical requirement tracking. Capacity is enforced by a **slot-capacity gate** at the READY → INVITED transition: `(count(ACCEPTED) + count(INVITED)) >= max_slots` blocks further invitations. There is no overflow / parking-lane state and no organizer voting mechanism — if a slot opens up (e.g., an invited speaker declines), the next speaker in READY can be invited

**FR18**: Event organizers shall access smart topic backlog management with visual heat map representation showing topic usage frequency over time, ML-powered similarity scoring to identify duplicate or similar topics with automated avoidance warnings, staleness detection algorithms that calculate recommended wait periods before topic reuse based on historical patterns and partner influence metrics, and intelligent duplicate avoidance that prevents organizers from selecting recently used or semantically similar topics

> **Not implemented as of 2026-11-04 (owner decision on strike/defer/rewrite pending: #1210).** What exists: topic backlog with heat map, staleness scores and TF-IDF cosine similarity warnings at >70 % (Story 5.2, `SimilarityCalculationService`). Not found: ML-based similarity, partner-influence weighting, hard blocking of recent topics (warnings only).

**FR19**: The platform shall implement progressive publishing engine that automatically validates content readiness (moderator quality review, abstract length limits) and publishes event information in phases (topic immediate → speakers 1 month prior → progressive agenda updates → final agenda → post-event materials) with quality control checkpoints and automated content standards enforcement

**FR20**: Event organizers shall receive intelligent notification system with role-based alerts, cross-stakeholder visibility, and automated escalation workflows for deadline management and task coordination, implementing multi-tier escalation rules (reminder → warning → critical → escalation to backup organizer) with configurable timing thresholds, automatic fallback notification paths, priority-based delivery guarantees, and real-time escalation status dashboards with audit trails

> **Not implemented as of 2026-11-04 (owner decision on strike/defer/rewrite pending: #1210).** What exists: tiered speaker deadline reminders with an organizer escalation after tier 3 (Story 6.5, `SpeakerReminderService`), task due dates in the organizer cockpit (#1105). Not found: escalation to a backup organizer, configurable multi-tier escalation for tasks, escalation status dashboard.

**FR21**: The system shall support long-term planning capabilities including multi-year venue booking workflows, seasonal partner meeting coordination, and strategic budget planning with automated scheduling and reminder systems

**FR22**: Event organizers shall manage user roles with capabilities to promote users to speaker or organizer roles, demote users with approval workflows for organizer demotions, and enforce business rules requiring minimum 2 organizers per event, maintaining complete audit trails of all role changes

**FR23**: Event organizers shall access a User Management interface displaying all platform users with comprehensive CRUD operations (create, read, update, delete), advanced filtering by role/company/status, search functionality with autocomplete, and the ability to view detailed user information and manage role assignments through an intuitive administrative interface, with all operations respecting business rules (minimum 2 organizers) and maintaining complete audit trails

### Functional Requirements added 2026-11-04 (shipped capabilities)

These record what is live in production; each line points to its epic, story or PR.

**FR24**: Users can sign in with Google (OIDC federation via Cognito, JIT provisioning, account linking) — Epic 12, ADR-010

**FR25**: Users can hold additional email addresses and sign in with SSO through a verified additional email — Story 10.32, #745

**FR26**: Organizers and users (self-claim) can merge two profiles of one person — #1183, #1204

**FR27**: Registration enforces venue capacity with a waitlist; saving a higher capacity fills free places from the waitlist — Story 10.11, #1208

**FR28**: Unconfirmed attendees are recovered via 4-day registration links and a daily resend — #683

**FR29**: Organizers can add an existing user as a confirmed participant ("Add participant") — #792

**FR30**: Participants are auto-enrolled and each event has its own email aliases — #686

**FR31**: Organizers export name badges as XLSX and printable DOCX — #686, #688

**FR32**: Organizers coordinate venue and caterer with a mail composer — #690

**FR33**: A moderator presenter page and a Live Control page run the event day (REST polling) — Stories 10.8a/b, 15.5, 15.1 P1 (#800), #1207

**FR34**: Attendees contribute topics, self-nominate as speakers, thank the organizers, and use per-session Q&A — Epic 7, #776, #777, #785

**FR35**: Organizers send newsletters to managed subscribers with bounce handling — Stories 10.7, 10.14, 10.28, 10.29

**FR36**: Public forms are protected by Cloudflare Turnstile — Story 10.31

**FR37**: Organizers see per event what went out, what came back and what needs an answer, and answer from one shared composer (Mail Center) — Epic 16, #1166, #1174, #1189, #1194

**FR38**: Mail to event and team aliases is forwarded by EMS (forwarder Lambda removed) — #1137

**FR39**: Organizers work from a lifecycle-aware event cockpit that shows tasks when they become relevant — Epic 14 (#788), #1105

**FR40**: A watchOS companion app shows the event and lets organizers control session timing — `docs/watch-app/epics.md`

**FR41**: Partner meeting invitations are sent and recorded per recipient — Story 16.9, #1180

### Non-Functional Requirements

**NFR1**: Platform shall provide responsive design optimized for mobile, tablet, and desktop access

**NFR2**: Database shall support full-text search across all historical content with sub-second response times

**NFR3**: System shall integrate with external services (email, payment processing, file storage) through configurable APIs

**NFR4**: Platform shall support multi-language content (German, English) with internationalization framework

### Email & Notification Infrastructure Requirements

**EIR1**: The system shall integrate AWS Simple Email Service (SES) for transactional and marketing email delivery with the following specifications:
- **Configuration**: Production account with verified domain (batbern.ch) and DKIM/SPF/DMARC setup
- **Throughput**: Support for 50,000 emails/day with burst capacity to 200,000/day
- **Deliverability**: Maintain >98% delivery rate with bounce and complaint handling
- **Templates**: Support for SES template versioning and variable substitution
- **Monitoring**: CloudWatch metrics for delivery, bounces, complaints, and reputation
- **Compliance**: GDPR-compliant unsubscribe handling and data retention policies

**EIR2**: The notification system shall provide multi-channel delivery infrastructure supporting email (via AWS SES), in-app notifications (via WebSocket), and future extensibility for SMS (via AWS SNS) and push notifications (via Firebase Cloud Messaging)

**EIR3**: Email template management system shall support HTML/text dual-format emails, inline CSS processing, responsive design testing tools, preview functionality across email clients, and version control with rollback capabilities

### Compatibility Requirements

**CR1**: New platform shall migrate all existing event data (54+ events, presentations, speaker profiles) without data loss

**CR2**: System shall maintain existing external integrations (Slideshare, Twitter, venue websites) during transition period

**CR3**: Platform shall preserve existing URL structure for SEO and bookmark compatibility where feasible

**CR4**: New system shall export data in standard formats for partner analytics and reporting tools

## 3. Technical Architecture and Implementation

### Architecture Overview
The BATbern Event Management Platform follows a comprehensive **Domain-Driven Design microservices architecture** as detailed in our [Architecture Documentation](./architecture/index.md).

### Technology Stack
- **Frontend**: React 19 with TypeScript, MUI 7, Zustand + React Query (TanStack)
- **Backend**: Java 21 LTS with Spring Boot 4.0 (Epic 13), PostgreSQL, Caffeine
- **Infrastructure**: AWS (ECS Fargate, Cognito, S3, CloudFront)
- **Development**: Gradle, Vite, GitHub Actions, AWS CDK

### Architectural Components
The platform was scaffolded as four bounded contexts with dedicated services. Per
[ADR-014](./architecture/ADR-014-backend-service-consolidation.md) (2026-06-26) the backend consolidated around
event-management-service (EMS), company-user-management-service (CUMS) and partner-coordination-service;
the dormant speaker and attendee stub services were removed by Story 17.2 (2026-11).

1. **Event Management Domain** - Organizer workflows and automation (EMS)
2. **Speaker Coordination Domain** - Speaker portal and workflows (served by EMS since Epic 11)
3. **Partner Analytics Domain** - ROI tracking and strategic input (partner-coordination-service)
4. **Attendee Experience Domain** - Content discovery and registration (served by EMS)

### Detailed Architecture References
For comprehensive technical implementation details, refer to the following architecture documents:

- **[System Overview](./architecture/01-system-overview.md)** - C4 model and component relationships
- **[Infrastructure & Deployment](./architecture/02-infrastructure-deployment.md)** - AWS deployment strategy and CI/CD
- **[Data Architecture](./architecture/03-data-architecture.md)** - Domain models and database schemas
- **[API Design](./architecture/04-api-design.md)** - REST API specifications and workflows
- **[Frontend Architecture](./architecture/05-frontend-architecture.md)** - React component patterns and PWA
- **[Backend Architecture](./architecture/06-backend-architecture.md)** - Service patterns and security
- **[Workflow State Machines](./architecture/06a-workflow-state-machines.md)** - Event workflow, speaker workflow, and task management
- **[Development Standards](./architecture/07-development-standards.md)** - Coding standards and setup
- **[Operations & Security](./architecture/08-operations-security.md)** - Security and performance requirements

### Integration Constraints
- **Data Migration**: Complete migration of 54+ historical events from existing JSON structure
- **External Integrations**: Maintain compatibility with Slideshare, venue websites, and partner systems
- **Performance**: Sub-second search response across 20+ years of content
- **Security**: Role-based access control with AWS Cognito integration
- **Scalability**: Multi-tenant architecture supporting concurrent user sessions

## 4. Epic Structure and Sprint Planning

### Epic Approach
**Vertical Slice Strategy**: Sixteen epics (Epic 9 superseded by Epic 11) delivering end-to-end functionality, each providing demonstrable value to users while building progressively on the platform foundation.

### Sprint Planning Overview

**Setup Phase**: Sprint 0 (2 weeks)
- Project setup, environment configuration, team alignment
- Architecture finalization with architect
- Development environment preparation

**Epic Timeline (REVISED - CRUD-First Strategy with API Consolidation)**:
- **Epic 1**: Weeks 1-9 (Foundation & Essential Infrastructure) - ✅ **100% COMPLETE**
- **Epic 2**: Weeks 10-18 (Entity CRUD & Domain Services + API Consolidation) - ✅ **100% COMPLETE**
- **Epic 3**: Weeks 19-21 (Historical Data Migration) - ✅ **100% COMPLETE** (tooling ready)
- **Epic 4**: Weeks 22-26 (Public Website & Content Discovery) - ✅ **100% COMPLETE**
- **Epic 5**: Weeks 27-35 (Enhanced Organizer Workflows) - ✅ **100% COMPLETE** (including BAT-16)
- **Epic 6**: Weeks 36-44 (Speaker Portal & Support) - ✅ **100% COMPLETE** (All stories deployed to staging, Story 6.4 QA passed)
- **Epic 7**: Attendee Experience — Right-Sized Contribution & Touchpoints - ✅ **COMPLETE** (Stories 7.1–7.7; #776, #777, #785, June 2026)
- **Epic 8**: Weeks 45+ (Partner Coordination - ✅ **100% COMPLETE** 2026-02-22)
- **Epic 9**: Speaker Authentication - 📦 **SUPERSEDED** by Epic 11 (ADR-009)
- **Epic 10**: Additional Stories - ✅ **COMPLETE** (10.6, 10.13, 10.15 cancelled; 10.24, 10.25 deferred)
- **Epic 11**: Unified Speaker Workflow Refactor - ✅ **COMPLETE** (Phases A–F per ADR-009; 11.F.1 magic-link teardown 2026-05-25; leftovers 11.E.5, 11.G.1, 11.F.2 open)
- **Epic 12**: Federated Identity / Google SSO - ✅ **LIVE** 2026-06-04 (ADR-010; Story 12.10 Apple/generic OIDC in backlog)
- **Epic 13**: Spring Boot 4 Migration - 🔄 SB 4.0.9 **LIVE** (4.0.7 via #797 2026-06-19, 4.0.9 2026-11-05); 13.4 Jackson 3 flip and 13.5 open
- **Epic 14**: Event Detail Page — Lifecycle-Aware Redesign - ✅ **COMPLETE** (#788, 2026-06-15)
- **Epic 15**: Post-Event #2 Hardening - 🔄 **IN PROGRESS** (15.1 P2/P3, 15.4, 15.8 open)
- **Epic 16**: Mail Center - 🔄 16.0–16.11 **LIVE** (2026-10-30 to 2026-11-03); 16.8c gated, 16.12/16.13 backlog

**Reorganization Rationale**: Epic structure revised to prioritize functional delivery (CRUD with consolidated APIs, data migration, public website) before workflow automation. Epic 5 completed with the event workflow (9 states then, consolidated to 8 in V82), per-speaker coordination, task management, auto-publishing, and lifecycle automation. Epic 6 fully deployed with automated speaker invitation, self-service response portal, content submission, dashboard (WCAG 2.1 AA), and deadline reminders. Epic 8 complete with partner attendance analytics, topic voting, and meeting coordination with RFC 5545 calendar invites. Epic 7 was rewritten 2026-06-09 from its deferred over-scoped form (dashboards/PWA/recommendation engine) into a right-sized set of five organizer-voted attendee contribution & touchpoint features (see `prd/epic-7-attendee-experience-enhancements.md`); the prior Epic 9 (JWT magic-link speaker authentication) is superseded by **Epic 11** per ADR-009, which adopts standard AWS Cognito with `FORCE_CHANGE_PASSWORD` first-login as the sole speaker auth path (no parallel JWT / magic-link stack).

### 4.2 Content Management & Storage Architecture

> **Not implemented as of 2026-11-04 (owner decision on strike/defer/rewrite pending: #1210).** This section is a target design. Verified reality: one content bucket `batbern-content-${env}` (`infrastructure/lib/stacks/storage-stack.ts`, S3-managed encryption, versioned in production), region eu-central-1, presigned uploads, an image-resize Lambda behind `cdn.batbern.ch`. Not found in code: per-type buckets, ClamAV scanning, role quotas, cross-region replication (eu-west-1 is not used), `cdn-staging`/`cdn-dev` domains.

The BATbern platform manages diverse content types including company logos, speaker photos and CVs, event presentations, and historical archives. This section defines the storage architecture, CDN strategy, and content policies.

#### AWS S3 Storage Strategy

**Bucket Architecture:**
- **Environment Separation**: Separate S3 buckets per environment (development, staging, production)
- **Bucket Naming**: `batbern-{environment}-{content-type}` (e.g., `batbern-prod-presentations`, `batbern-prod-logos`)
- **Content Types**:
  - `presentations`: Speaker presentation files (PDF, PPTX)
  - `logos`: Company and partner logos (PNG, JPG, SVG)
  - `profiles`: Speaker photos and CVs (JPG, PNG, PDF)
  - `archives`: Historical event materials and photo galleries

**S3 Key Structure:**
```
/{content-type}/{year}/{entity-id}/{filename-with-uuid}
Examples:
  /presentations/2024/evt-123/speaker-456-presentation-a7b3c9d2.pdf
  /logos/2024/company-789/logo-f3e8d1a4.png
  /profiles/2024/speaker-456/photo-b2c9e3f1.jpg
```

**Lifecycle Policies:**
- **Active Content**: S3 Standard storage class for content < 1 year old
- **Historical Content**: Transition to S3 Standard-IA after 1 year
- **Archive Content**: Transition to S3 Glacier after 3 years
- **Retention**: Indefinite retention for all event-related content

**Security & Access Control:**
- **Private Buckets**: All buckets private by default, no public access
- **Presigned URLs**: Time-limited presigned URLs for downloads (15-minute expiration)
- **Upload Authentication**: All uploads require valid JWT with appropriate role
- **Server-Side Encryption**: AES-256 encryption at rest (S3-SSE)
- **Versioning**: Enabled for accidental deletion protection

#### CloudFront CDN Configuration

**Distribution Strategy:**
- **Global CDN**: CloudFront distribution per environment for fast worldwide delivery
- **Origin**: S3 buckets as CloudFront origins with Origin Access Identity (OAI)
- **Caching**: Aggressive caching for static content (TTL: 7 days)
- **Custom Domain**: `cdn.batbern.ch`, `cdn-staging.batbern.ch`, `cdn-dev.batbern.ch`

**Performance Optimization:**
- **Edge Locations**: AWS global edge network for low-latency delivery
- **Compression**: Automatic gzip/brotli compression for text-based files
- **HTTP/2**: Enabled for multiplexing and reduced latency
- **Image Optimization**: Lambda@Edge for automatic image resizing and format conversion

**Security Headers:**
- **HTTPS Only**: Redirect HTTP to HTTPS
- **Security Headers**: CORS, CSP, X-Frame-Options, X-Content-Type-Options
- **Signed URLs**: Optional signed URLs for sensitive content

#### File Size and Format Constraints

**Per Content Type:**

| Content Type | Max Size | Allowed Formats | Validation |
|--------------|----------|-----------------|------------|
| Company Logo | 5 MB | PNG, JPG, SVG | Dimensions: 500x500 to 2000x2000 |
| Speaker Photo | 10 MB | JPG, PNG | Dimensions: min 800x800, aspect ratio 1:1 or 4:3 |
| Speaker CV | 5 MB | PDF | Max 10 pages |
| Presentation | 100 MB | PDF, PPTX | Virus scan required |
| Event Photo | 20 MB | JPG, PNG | Max 4000x4000 |

**Upload Requirements:**
- **Multipart Upload**: Files > 10 MB use multipart upload for reliability
- **Progress Tracking**: Real-time upload progress for files > 1 MB
- **Virus Scanning**: ClamAV integration for all uploaded files
- **Checksum Verification**: SHA-256 checksum validation on upload completion

#### Storage Quota Policies Per Role

**Role-Based Quotas:**

| Role | Total Storage Quota | Max Files | Max File Size | Notes |
|------|---------------------|-----------|---------------|-------|
| Organizer | Unlimited | Unlimited | 100 MB | Full platform administration |
| Speaker | 200 MB | 20 files | 100 MB | Presentation + supporting materials |
| Partner | 50 MB | 5 files | 5 MB | Company logo + marketing materials |
| Attendee | 10 MB | 5 files | 5 MB | Profile photo + bookmarked content |

**Quota Enforcement:**
- **Pre-Upload Check**: Validate available quota before upload
- **Soft Limits**: Warning at 80% quota usage
- **Hard Limits**: Upload rejection at 100% quota
- **Quota Increase**: Manual approval process for speakers exceeding limits

#### Backup and Disaster Recovery

**Backup Strategy:**
- **Cross-Region Replication**: Replicate production bucket to secondary region (eu-west-1 → eu-central-1)
- **Versioning**: S3 versioning enabled for 30-day rollback capability
- **Daily Snapshots**: S3 bucket inventory reports for disaster recovery planning
- **Backup Testing**: Quarterly restore drills to validate backup integrity

**Disaster Recovery:**
- **RTO (Recovery Time Objective)**: 4 hours for full content restoration
- **RPO (Recovery Point Objective)**: 1 hour maximum data loss
- **Failover**: Automatic CloudFront failover to secondary region on origin failure
- **Restoration Process**: Documented restoration procedures with runbooks

**Data Integrity:**
- **Checksum Validation**: SHA-256 checksums stored in database for integrity verification
- **Periodic Audits**: Monthly automated integrity checks comparing S3 and database records
- **Orphan Detection**: Identify and cleanup orphaned S3 objects without database references

### Detailed Epic Implementation

The comprehensive epic breakdown follows a CRUD-first approach, prioritizing functional entity management and public website delivery before workflow automation:

**Phase 1: MVP Complete (Weeks 1-35) - ✅ 100% COMPLETE**
- **[Epic 1: Foundation & Essential Infrastructure](./prd/epic-1-foundation-stories.md)** - ✅ **COMPLETE** - Platform foundation with authentication, monitoring, security (9 weeks)
- **[Epic 2: Entity CRUD & Domain Services](./prd/epic-2-entity-crud-domain-services.md)** - ✅ **COMPLETE** - End-to-end CRUD with consolidated RESTful APIs for Event, Speaker, Company, User entities (9 weeks, includes API consolidation 1.15a.1-1.15a.8)
- **[Epic 3: Historical Data Migration](./prd/epic-3-historical-data-migration.md)** - ✅ **COMPLETE** - Migrate 20+ years of BATbern event data (3 weeks, tooling ready)
- **[Epic 4: Public Website & Content Discovery](./prd/epic-4-public-website-content-discovery.md)** - ✅ **COMPLETE** - Public event pages, registration, historical archive browsing (5 weeks)
- **[Epic 5: Enhanced Organizer Workflows](./prd/epic-5-enhanced-organizer-workflows.md)** - ✅ **COMPLETE** - Event workflow (now 8 states) with parallel speaker coordination, task management, auto-publishing, lifecycle automation (9 weeks)

**Phase 2: Speaker Self-Service Portal (Weeks 36-44) - ✅ 100% COMPLETE**
- **[Epic 6: Speaker Portal & Support](./prd/epic-6-speaker-portal-support.md)** - ✅ **100% COMPLETE** - Self-service speaker portal with automated invitations, response handling, content submission, deadline reminders, and speaker dashboard with full accessibility compliance (8 weeks, all 6 stories complete and deployed to staging)

**Phase 3: Enhanced Experiences**
- **[Epic 7: Attendee Experience — Right-Sized Contribution & Touchpoints](./prd/epic-7-attendee-experience-enhancements.md)** - ✅ **COMPLETE** - Five organizer-voted features (topics from the floor, speaker self-nomination, slides-online mail, thank-the-organizers, per-session Q&A) plus attendee event history (7.6) and the curated thank-you marquee (7.7)
- **[Epic 8: Partner Coordination](./prd/epic-8-partner-coordination.md)** - ✅ **COMPLETE** (2026-02-22) - Attendance dashboard (table + Excel export), topic suggestions & voting (simple toggle), partner meeting coordination with ICS calendar invites, partner notes
- **[Epic 9: Speaker Authentication](./prd/epic-9-speaker-authentication.md)** - 📦 **SUPERSEDED** by Epic 11
- **[Epic 10: Additional Stories](./prd/epic-10-additional-stories.md)** - ✅ **COMPLETE** - Administration, email templates, newsletter, presenter, capacity + waitlist, photos, Turnstile, additional emails, alias forwarding (10.6/10.13/10.15 cancelled, 10.24/10.25 deferred)
- **[Epic 11: Unified Speaker Workflow Refactor](./prd/epic-11-speaker-workflow-refactor.md)** - ✅ **COMPLETE** - 8-state speaker workflow per ADR-009, Cognito speaker auth, magic links removed
- **[Epic 12: Federated Identity / Google SSO](./plans/sso-oidc-federation.md)** - ✅ **LIVE** 2026-06-04 - ADR-010; no PRD file, the plan is the source
- **[Epic 13: Spring Boot 4 Migration](./plans/spring-boot-4-migration.md)** - 🔄 SB 4.0.9 live (#797, patch 2026-11-05); 13.4, 13.5 open; no PRD file, the plan is the source
- **[Epic 14: Event Detail Page — Lifecycle-Aware Redesign](./prd/epic-14-event-detail-redesign.md)** - ✅ **COMPLETE** (#788)
- **[Epic 15: Post-Event #2 Hardening](./prd/epic-15-post-event-2-hardening.md)** - 🔄 **IN PROGRESS**
- **[Epic 16: Mail Center](./prd/epic-16-mail-center.md)** - 🔄 16.0–16.11 live; 16.8c gated; 16.12, 16.13 backlog

Each epic document contains:
- Detailed user stories with architecture alignment
- Technical acceptance criteria
- Integration requirements
- Performance metrics
- Definition of done criteria
- Clear deliverables demonstrating value

---

## Success Metrics

### Phase 1: MVP Complete (Weeks 1-35) - ✅ ALL SUCCESS CRITERIA MET

**Epic 1 Success (Foundation - Week 9)** - ✅ **ACHIEVED**
- ✅ User authentication operational for all 4 roles (Organizer, Speaker, Partner, Attendee)
- ✅ API Gateway routing to all microservices with <50ms overhead
- ✅ CI/CD pipeline with automated deployments and <10min build time
- ✅ Infrastructure monitoring with <5min MTTD (Mean Time To Detection)
- ✅ Essential security controls and GDPR compliance operational
- ✅ Platform foundation ready for feature development

**Epic 2 Success (Entity CRUD + API Consolidation - Week 18)** - ✅ **ACHIEVED**
- ✅ All entity CRUD operations functional with <200ms P95 response time
- ✅ **Consolidated RESTful APIs operational** (Stories 1.15a.1-1.15a.8: 80-90% reduction in HTTP requests)
- ✅ Company management with Swiss UID validation operational
- ✅ Event CRUD with type support (full-day, afternoon, evening)
- ✅ Speaker profiles and basic coordination working
- ✅ User role management with promotion/demotion workflows
- ✅ React frontend with role-adaptive navigation deployed
- ✅ **Resource expansion working** (`?include=` pattern reduces multi-API calls to 1-2 per page)
- ✅ **Advanced filtering** with JSON filter syntax operational across all entities

**Epic 3 Success (Data Migration - Week 21)** - ✅ **ACHIEVED** (tooling ready)
- ✅ 100% historical data migration tooling complete (54+ events)
- ✅ All speaker profiles and presentations ready for S3 migration
- ✅ Company relationships established in Company Management Service
- ✅ Data integrity validation 100% pass rate
- ✅ Search indexes built for content discovery
- 📅 Production data import scheduled (pending user trigger)

**Epic 4 Success (Public Website - Week 26)** - ✅ **ACHIEVED**
- ✅ Public event landing page live with current/upcoming events
- ✅ Event registration flow functional (3-step wizard with QR code)
- ✅ Historical archive browsing operational (20+ years of content)
- ✅ Full-text search across events, speakers, presentations working
- ✅ Mobile-responsive design with <2.5s Largest Contentful Paint
- ✅ >99.5% uptime for public-facing pages

**Epic 5 Success (Enhanced Organizer Workflows - Week 35)** - ✅ **ACHIEVED**
- ✅ Event creation to publication in <30 minutes
- ✅ Event workflow operational (9 states at the time, 8 since V82) with parallel speaker coordination and configurable task management
- ✅ Progressive publishing engine with automated content updates (30/14 day auto-publish)
- ✅ Topic selection with duplicate detection
- ✅ Automated deadline tracking and escalation via task management system
- ✅ EVENT_LIVE and EVENT_COMPLETED lifecycle automation

### Phase 2: Speaker Self-Service Portal (Weeks 36-44) - ✅ 100% COMPLETE

**Epic 6 Success Criteria (Speaker Portal & Support)** - ✅ **100% COMPLETE (Deployed 2026-02-06, Dashboard 2026-02-16)**

**Completed Features (Stories 6.0-6.5):**
- ✅ Automated speaker invitation system operational (per ADR-009, authentication uses standard Cognito with FORCE_CHANGE_PASSWORD on first login — login link + temporary password delivered in invitation email)
- ✅ Self-service response portal (accept/decline) with real-time status updates
- ✅ Speaker material self-submission portal (title, abstract, CV, photo, presentation)
- ✅ Direct S3 uploads via presigned URLs with progress tracking
- ✅ Automated deadline reminder system with tiered escalation
- ✅ Hybrid workflow supporting both self-service and organizer-driven approaches
- ✅ 40% estimated organizer workload reduction via automation
- ✅ Speaker dashboard with upcoming/past events, material status, and deadlines (Story 6.4 - complete with WCAG 2.1 AA accessibility)
- ✅ Full WCAG 2.1 AA accessibility compliance (ARIA labels, semantic HTML, keyboard navigation, screen reader support)

**Advanced Features (Phase 3+):**
- 📅 Performance metrics and speaking history analytics
- 📅 Communication hub with real-time messaging
- 📅 Advanced material management with version control

### Phase 3: Enhanced Experiences

**Epic 7 Success Criteria (Attendee Experience — Right-Sized Contribution & Touchpoints)** - ✅ **DELIVERED** (Stories 7.1–7.7; the criteria below are the original targets, not measured results)
- Attendees can suggest topics into the existing topic pool and self-nominate as speakers (Contribution Loop adoption per event cycle > 0)
- At least one self-nominated speaker promoted to READY and presenting within the first 3 event cycles
- Event-triggered "slides are online" email measurably lifts post-event archive/session visits
- Thank-the-organizers count per event > 0; the gratitude gap is closed
- ≥ 1 frozen post-event Q&A thread attached per session, enriching the archive
- Guardrail compliance: no feature alters the physical event character; organizer recurring effort unchanged

**Epic 8 Success Criteria (Partner Coordination)** - ✅ **COMPLETE** (2026-02-22)
- Partners can view attendance table (company vs total per event) with Excel export
- All partners can suggest topics and cast simple toggle votes; organizer can set status
- Organizer can send ICS calendar invite (RFC 5545) to partner contacts via SES, one mail per recipient since #1180
- Partner meeting records linked to BATbern events with agenda and post-meeting notes
- Dashboard load P95 < 5s; all role-based access rules enforced and integration-tested

### Overall Platform Success - ✅ MVP + PHASE 2 ACHIEVED

**Phase 1 Achievements (Epics 1-5):**
- ✅ Successful foundation of 20+ years of event content migration tooling
- ✅ Platform adopted by all 4 stakeholder groups (organizers, speakers, partners, attendees)
- ✅ Measurable improvement in event planning efficiency via the event workflow (now 8 states)
- ✅ Enhanced organizer productivity with task management and auto-publishing
- ✅ Increased community participation through public website and registration
- ✅ Production-ready platform ready for launch

**Phase 2 Achievements (Epic 6 - Deployed to Staging 2026-02-06, Dashboard 2026-02-16):**
- ✅ Speaker self-service portal with automated invitation and response workflows
- ✅ Material self-submission portal reducing organizer data entry by 80%
- ✅ Automated deadline reminder system with tiered escalation
- ✅ 40% estimated reduction in organizer speaker coordination workload
- ✅ Hybrid workflow supporting both self-service and manual organizer approaches
- ✅ Speaker dashboard with upcoming/past events, material progress tracking, and deadline management (WCAG 2.1 AA compliant, 98/100 quality score)

**Phase 3 Enhancements:**
- ✅ Partner analytics dashboards and coordination tools (Epic 8, 2026-02-22)
- ✅ Attendee contribution and touchpoints (Epic 7)
- Not built: PWA offline access for attendees (see FR15 note), advanced speaker dashboard with performance metrics

## Next Steps

Open work as of 2026-11-04 (source: `_bmad-output/implementation-artifacts/sprint-status.yaml`):

1. **Owner decisions (#1210)**: strike, defer or rewrite FR7, FR14, FR15, FR18, FR20 and §4.2
2. **Epic 13**: 13.4 Jackson 3 flag flip, 13.5 unpin BOM-managed versions
3. **Epic 15**: 15.1 P2 (watch off WebSockets) and P3 (WebSocket teardown), 15.4, 15.8
4. **Epic 16**: 16.8c (after one release, owner go), 16.12, 16.13
5. **Epic 12**: Story 12.10 (Apple/generic OIDC) after deferred-work cluster A
6. **Epic 3**: production data import
