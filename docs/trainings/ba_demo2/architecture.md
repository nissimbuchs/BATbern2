---
stepsCompleted: ['step-01-init', 'step-02-context', 'step-03-starter', 'step-04-decisions', 'step-05-patterns', 'step-06-structure', 'step-07-validation', 'step-08-complete']
lastStep: 8
status: 'complete'
completedAt: '2026-04-05'
inputDocuments:
  - docs/trainings/ba_demo2/prd.md
  - docs/trainings/ba_demo2/ux-design-specification.md
  - docs/trainings/ba_demo2/brainstorming-session-2026-04-04.md
  - docs/trainings/ba_demo2/quick-brainstorm.md
  - _bmad-output/project-context.md
documentCounts:
  briefs: 0
  research: 0
  brainstorming: 2
  prd: 1
  uxDesign: 1
  projectDocs: 1
workflowType: 'architecture'
project_name: 'BATbern'
user_name: 'Nissim'
date: '2026-04-05'
classification:
  projectType: web_app
  domain: event_management_community_governance
  complexity: medium
  projectContext: brownfield
---

# Architecture Decision Document — BATbern Active Partner Meeting Agenda

_This document builds collaboratively through step-by-step discovery. Sections are appended as we work through each architectural decision together._

## Project Context Analysis

### Requirements Overview

**Functional Requirements:**
10 requirement groups (FR1–FR10) covering the complete meeting lifecycle:
- **Meeting entity management** (FR1): Configurable agenda with active + passive items, ordering, ICS invites, status lifecycle (DRAFT → SCHEDULED → IN_PROGRESS → COMPLETED)
- **RSVP & Attendance** (FR2): Pre-meeting RSVP, live presence toggling, stand-in delegation with "New" badge for first-year partners
- **Presenter Mode** (FR3): Full-screen dark-themed projector interface with three-zone layout, linear + sidebar navigation, keyboard shortcuts, persistent auto-saving notes, auto-save indicator
- **Document Upload & Viewing** (FR4): PDF/spreadsheet upload via S3 presigned URLs, embedded viewer in Presenter Mode, permanent attachment to meeting record
- **Voting** (FR5): Proxied approve/decline voting — organizer casts on behalf of each partner with per-partner attribution, stand-in proxy chains, reversible before completion
- **Meeting Minutes** (FR6): Auto-assembled from agenda item interactions (attendance, votes, documents, snapshots, notes), editable post-meeting, publishable with email distribution
- **Meeting Prep** (FR7): Dynamic checklist based on enabled items — green/yellow/red status, soft-blocking Presenter Mode start
- **Post-Meeting Workflows** (FR8): Async fee acceptance with reminders (7/14 days), feedback pulse survey (3 questions, 24h after publish), organizer tracking dashboards
- **Action Items** (FR9): Created at any agenda item, assigned owners + deadlines, carry-forward to next meeting, urgency indicators for 2+ carry-overs
- **Analytics Snapshot** (FR10): Embed existing analytics dashboard, capture immutable snapshot attached to minutes

**Non-Functional Requirements:**
- Performance: Presenter Mode load < 3s (pre-fetch all), item transitions < 300ms (client-side), auto-save < 2s, document render < 5s
- Security: Existing Cognito auth, organizer-only for meeting management/voting/publishing, partner-only for minutes viewing/fee acceptance/feedback
- Reliability: Auto-save every action server-side, draft persistence, graceful Presenter Mode exit with resume, single-user editing (no conflict resolution needed)
- Accessibility: WCAG 2.1 AAA (7:1+) for Presenter Mode dark theme, WCAG 2.1 AA for partner portal, no color-only information
- Scalability: Up to 30 partners per meeting, unlimited meeting history, S3-backed documents

**Scale & Complexity:**
- Primary domain: Event Management / Community Governance (brownfield extension)
- Complexity level: Medium — rich frontend (Presenter Mode), straightforward backend extending existing Partner Coordination Service
- Estimated architectural components: ~15 (6 backend entities, 4 reusable frontend components, 3 API endpoint groups, 2 scheduled workflows)

### Technical Constraints & Dependencies

**Platform Dependencies (Existing — Reuse):**
- Partner Coordination Service (Epic 8) — partner entities, contacts, portal auth, topic voting
- Company/User Management Service — partner company data, logos, user profiles (HTTP enrichment, ADR-004)
- S3 presigned URL upload — existing file upload pattern (ADR-002)
- AWS Cognito — existing auth for organizer and partner roles
- SES email — existing infrastructure for notifications

**Architectural Constraints (Existing — Follow):**
- ADR-003: Meaningful identifiers in public APIs — no UUIDs exposed; use companyName, username
- ADR-004: No user field duplication in domain entities — enrich via HTTP + Caffeine cache
- ADR-006: OpenAPI contract-first — specs are source of truth, controllers implement generated interfaces
- Single PostgreSQL database, shared public schema, Flyway per-service migrations
- Backend layered architecture: Controller → Service → Repository with generated DTOs
- Frontend: React 19 + TypeScript, MUI + Tailwind, service layer for API calls, generated types from OpenAPI

**New Constraints (From PRD):**
- Single-operator model — no multi-user real-time editing, no WebSocket requirement
- Bi-annual usage — UI must be self-explanatory with zero learning curve
- Projector-first — Presenter Mode designed for 1920x1080 projected, min 1280px
- Swiss governance context — financial sign-off records may be audited years later

### Cross-Cutting Concerns Identified

1. **Minutes auto-assembly** — Every agenda item interaction must contribute to the minutes entity as a side effect. This is the core architectural pattern: a consistent "minutes contribution" interface across all item types.
2. **Auto-save reliability** — Every organizer action persisted within 2 seconds. Debounced PATCH pattern with optimistic UI and visible save indicator.
3. **Email notifications** — ICS meeting invites, minutes distribution, fee acceptance requests, fee reminders (7/14 day scheduled), feedback pulse (24h scheduled).
4. **Document storage** — S3 presigned URL upload (existing pattern) for audit PDFs, budget spreadsheets. Documents permanently attached to meeting records.
5. **Cross-service enrichment** — Partner gallery requires company logos + user profile photos/names via HTTP calls to Company/User services (existing Caffeine-cached pattern).
6. **Action item lifecycle** — Spans across meetings: created in one, carried forward to next, urgency escalation at 2+ carry-overs. Requires meeting-to-meeting linkage.

## Starter Template Evaluation

### Primary Technology Domain

Brownfield web application — full-stack extension of the existing BATbern platform.

### Starter Options Considered

**No starter template evaluation needed.** This is a brownfield feature extension, not a greenfield project. The entire technology stack, project structure, build tooling, testing infrastructure, and deployment pipeline are already established and production-proven through Epics 1-8.

### Selected Approach: Extend Existing Codebase

**Rationale:**
- The Active Partner Meeting Agenda extends the existing Partner Coordination Service (backend) and web-frontend (React SPA)
- All technology decisions are already made and documented in project-context.md (65 rules)
- Creating a separate service or frontend would violate the established microservice boundaries — partner meetings belong in the partner coordination domain
- The existing component conventions, service layer patterns, and testing infrastructure provide the foundation

**What the existing codebase provides:**

- **Language & Runtime:** Java 21 (backend) + TypeScript 5.3 / React 19 (frontend)
- **Styling:** Tailwind CSS 4.x + MUI 7.x — Presenter Mode adds a dark theme variant using the existing zinc-950 + blue-400 palette
- **Build Tooling:** Gradle 8.x (backend, from repo root) + Vite 7.x (frontend)
- **Testing:** JUnit 5 + Testcontainers PostgreSQL (backend), Vitest + RTL + Playwright (frontend)
- **Code Organization:** Established layered architecture (Controller → Service → Repository), React component structure (src/components/{role}/)
- **API Pattern:** OpenAPI 3.1 contract-first (ADR-006), generated interfaces + DTOs
- **State Management:** TanStack Query (server state) + Zustand (client state)

**New architectural elements to introduce:**
1. `PresenterLayout` — dedicated full-screen layout for Presenter Mode (alongside existing OrganizerLayout)
2. Meeting lifecycle state machine — DRAFT → SCHEDULED → IN_PROGRESS → COMPLETED (follows existing event workflow pattern)
3. Auto-save debounce pattern — new frontend hook for < 2s persistence
4. Minutes auto-assembly — new backend pattern for aggregating agenda item contributions

**Note:** No project initialization story needed. First implementation story begins directly with database schema and entity design.

## Core Architectural Decisions

### Decision Priority Analysis

**Critical Decisions (Block Implementation):**
1. Entity model structure — defines the database schema and all service interfaces
2. Minutes auto-assembly pattern — core architectural innovation
3. Presenter Mode layout strategy — shapes the entire frontend feature

**Important Decisions (Shape Architecture):**
4. Auto-save API pattern — affects UX reliability
5. Agenda item component architecture — determines frontend extensibility
6. Presenter Mode authorization — ensures single-operator integrity

**Deferred Decisions (Post-MVP):**
- Minutes PDF export format/library — Phase 3
- Historized analytics archive browsing UI — Phase 3
- New partner onboarding tour implementation — Phase 3

### Data Architecture

**Entity Model:** 11 entities within the Partner Coordination Service database, following existing Flyway migration patterns.

| Entity | Meaningful ID (ADR-003) | Cross-Service Refs |
|--------|------------------------|-------------------|
| PartnerMeetingEntity | `meetingCode` (e.g., PM-2026-Spring) | — |
| MeetingAgendaItemEntity | — (UUID FK to meeting) | — |
| MeetingAttendanceEntity | — | `companyName` → Company Service |
| MeetingVoteEntity | — (UUID FK to agenda item) | — |
| MeetingVoteRecordEntity | — | `companyName` → Company Service |
| MeetingMinutesEntity | — (UUID FK to meeting) | — |
| MeetingActionItemEntity | — | `ownerUsername` → User Service |
| MeetingDocumentEntity | — (UUID FK to agenda item) | — |
| FeeAcceptanceEntity | — | `companyName` → Company Service |
| FeedbackPulseEntity | — | `username` → User Service |
| AnalyticsSnapshotEntity | — (UUID FK to meeting) | — |

**Key Design Decisions:**
- `meetingCode` as meaningful public identifier (pattern: `PM-{year}-{Spring|Autumn}`)
- Minutes content as JSONB — structured sections for auto-assembly and rendering
- Action item carry-forward via self-referencing `sourceItemId` FK
- All cross-service references use meaningful IDs per ADR-003 (`companyName`, `ownerUsername`)
- Meeting status lifecycle: DRAFT → SCHEDULED → IN_PROGRESS → COMPLETED
- Minutes status: DRAFT → PUBLISHED (immutable after publish)
- Vote immutability: vote records locked once vote is completed

**Database Migration:** New Flyway migrations in partner-coordination-service (V-next sequential).

### Authentication & Security

**Authorization Model:**
- Organizer role: create/edit meetings, Presenter Mode, voting, minutes publishing
- Partner role: RSVP, view published minutes, fee acceptance, feedback pulse
- No public endpoints for meeting features

**Presenter Mode Soft Lock:**
- `presenterSessionStartedBy` (username) + `presenterSessionStartedAt` (timestamp) on PartnerMeetingEntity
- Not a distributed lock — informational only for multi-organizer awareness
- Cleared when meeting transitions to COMPLETED or organizer exits Presenter Mode

### API & Communication Patterns

**Minutes Auto-Assembly: Query-Based Assembly (Option A)**
- Each organizer action persists to its own entity (attendance, votes, documents, notes, action items)
- `MeetingMinutesService.assembleMinutes(meetingCode)` queries all related entities and structures them into the JSONB minutes content
- Minutes assembled on-demand when organizer clicks "Review Minutes" — always reflects current state
- After organizer edits and publishes, the JSONB content becomes the immutable published record
- Rationale: Simple, no new patterns, source data always authoritative, bi-annual usage doesn't justify event sourcing complexity

**Auto-Save Pattern:**
- Frontend: Debounced `useMutation` via TanStack Query — fires 2 seconds after last input
- Backend: `PATCH /api/v1/partner-meetings/{meetingCode}/agenda-items/{itemId}/notes`
- Optimistic UI with "Saved ✓" indicator (fades after 2s)
- Error handling: toast notification + retry on transient failure

**API Endpoint Groups:**
1. Meeting management: CRUD + status transitions + agenda configuration
2. Presenter Mode: attendance toggling, voting, note saving, document upload, snapshot capture
3. Post-meeting: minutes review/publish, fee acceptance, feedback pulse

### Frontend Architecture

**Presenter Mode: Dedicated PresenterLayout**
- New top-level layout at `/partner/meetings/{meetingCode}/present`
- Full-screen, no navigation bar, dark theme (zinc-950) forced
- Three fixed zones: 280px sidebar + fluid main + 180px bottom notes
- Keyboard shortcuts: →/← (navigate), 1-9 (jump), N (focus notes), V (start vote), Esc (exit with confirm)

**Component Architecture:**
```
src/components/presenter/
  ├── PresenterLayout.tsx          # Three-zone shell + keyboard handler
  ├── AgendaSidebar.tsx            # Agenda navigation with item states
  ├── NotePanel.tsx                # Auto-saving notes (bottom zone)
  ├── items/
  │   ├── GreetingItem.tsx         # Partner gallery + presence toggle
  │   ├── AnalyticsItem.tsx        # Embedded analytics + snapshot
  │   ├── AuditSignoffItem.tsx     # Document viewer + vote
  │   ├── BudgetApprovalItem.tsx   # Document viewer + vote
  │   ├── TopicDiscussionItem.tsx  # Voting results + notes
  │   └── CustomItem.tsx           # Free-form notes only
  └── shared/
      ├── VotePanel.tsx            # Reusable proxied voting
      ├── DocumentViewer.tsx       # PDF/spreadsheet embedded viewer
      └── ActionItemForm.tsx       # Quick action item creation
```

**State Management:**
- Meeting data: TanStack Query with `staleTime: Infinity` (pre-loaded, single-user)
- Presenter navigation state: Zustand store (current item index, sidebar open/closed)
- Auto-save: TanStack Query mutations with debounce

### Infrastructure & Deployment

**Scheduled Workflows: Spring @Scheduled**
- Fee acceptance reminders: daily cron checks for pending acceptances past 7/14 day thresholds
- Feedback pulse trigger: daily cron checks for published minutes past 24h without pulse sent
- Runs within existing partner-coordination-service Fargate task — no new infrastructure
- Rationale: Service already runs 24/7, bi-annual meeting cadence doesn't justify external scheduling

**No New Infrastructure Required:**
- No new ECS services — extends partner-coordination-service
- No new databases — extends existing PostgreSQL schema
- No WebSocket infrastructure — standard HTTP sufficient
- No new CDN configuration — documents served via existing S3 presigned URL pattern

### Decision Impact Analysis

**Implementation Sequence:**
1. Database schema (Flyway migrations) + entity classes
2. OpenAPI spec for meeting management endpoints
3. Backend services (meeting CRUD, agenda management)
4. Presenter Mode frontend layout + navigation
5. Agenda item components (iterative, one item type at a time)
6. Minutes assembly + review/publish flow
7. Post-meeting workflows (fee acceptance, feedback pulse, reminders)
8. Action item carry-forward

**Cross-Component Dependencies:**
- VotePanel + DocumentViewer are shared across multiple agenda items — build first
- Minutes assembly depends on all agenda item entities being stable — build last in backend
- Fee acceptance depends on budget vote result — sequential dependency
- Action item carry-forward depends on meeting-to-meeting entity linkage — design in schema upfront

## Implementation Patterns & Consistency Rules

### Pattern Categories Defined

**Critical Conflict Points Identified:** 12 areas specific to the Active Partner Meeting Agenda where AI agents could make different choices. General BATbern patterns (ADR-003, ADR-004, ADR-006, naming conventions, layered architecture) are already documented in project-context.md and apply unchanged.

### Naming Patterns (Meeting-Specific)

**Database Tables — Meeting Domain:**
All new tables prefixed with `partner_meeting_` to namespace within the shared public schema:
```sql
partner_meetings              -- main entity
partner_meeting_agenda_items  -- agenda configuration
partner_meeting_attendance    -- presence records
partner_meeting_votes         -- vote sessions
partner_meeting_vote_records  -- per-partner vote attribution
partner_meeting_minutes       -- auto-assembled minutes
partner_meeting_action_items  -- action items with carry-forward
partner_meeting_documents     -- uploaded files
partner_meeting_fee_acceptance -- async fee responses
partner_meeting_feedback_pulse -- post-meeting survey
partner_meeting_analytics_snapshots -- immutable analytics captures
```

**Meeting Code Format:**
- Pattern: `PM-{YYYY}-{Spring|Autumn}` (e.g., `PM-2026-Spring`)
- Generated on creation, immutable after
- Used in all API URLs: `/api/v1/partner-meetings/PM-2026-Spring`
- Column: `meeting_code VARCHAR(20) UNIQUE NOT NULL`

**Agenda Item Type Enum:**
```java
public enum AgendaItemType {
    GREETING,           // Who Is Who partner gallery
    ANALYTICS_REVIEW,   // Embedded analytics + snapshot
    AUDIT_SIGNOFF,      // Document viewer + vote
    BUDGET_APPROVAL,    // Document viewer + vote
    TOPIC_DISCUSSION,   // Topic voting results + notes
    HOT_TOPICS,         // Free-form discussion
    CUSTOM              // User-defined passive items
}
```
- Database: stored as `lowercase_snake_case` (e.g., `'analytics_review'`)
- JSON/API: `UPPER_CASE` (e.g., `"ANALYTICS_REVIEW"`)
- No `@JsonValue` annotation — default serialization per project-context.md

**Meeting Status Enum:**
```java
public enum MeetingStatus {
    DRAFT,          // Created, agenda being configured
    SCHEDULED,      // ICS invites sent, accepting RSVPs
    IN_PROGRESS,    // Presenter Mode active
    COMPLETED       // Meeting ended, minutes in review or published
}
```

**Minutes Status Enum:**
```java
public enum MinutesStatus {
    DRAFT,       // Auto-assembling during/after meeting
    PUBLISHED    // Immutable, distributed to partners
}
```

**Vote Decision Enum:**
```java
public enum VoteDecision {
    APPROVE,
    DECLINE
}
```

**Fee Acceptance Status Enum:**
```java
public enum FeeAcceptanceStatus {
    PENDING,
    ACCEPTED,
    DECLINED
}
```

### Structure Patterns (Meeting-Specific)

**Backend Package Structure:**
```
ch.batbern.partnercoordination/
  ├── meeting/
  │   ├── controller/
  │   │   ├── PartnerMeetingController.java    # implements generated PartnerMeetingApi
  │   │   └── MeetingMinutesController.java    # implements generated MeetingMinutesApi
  │   ├── service/
  │   │   ├── PartnerMeetingService.java       # meeting CRUD + status transitions
  │   │   ├── MeetingPresenterService.java     # attendance, voting, notes, snapshots
  │   │   ├── MeetingMinutesService.java       # minutes assembly + publish
  │   │   ├── FeeAcceptanceService.java        # async fee workflow
  │   │   └── MeetingScheduledTasks.java       # @Scheduled reminders
  │   ├── repository/
  │   │   ├── PartnerMeetingRepository.java
  │   │   ├── MeetingAgendaItemRepository.java
  │   │   ├── MeetingAttendanceRepository.java
  │   │   ├── MeetingVoteRepository.java
  │   │   ├── MeetingActionItemRepository.java
  │   │   └── ...
  │   ├── domain/
  │   │   ├── PartnerMeetingEntity.java
  │   │   ├── MeetingAgendaItemEntity.java
  │   │   ├── MeetingAttendanceEntity.java
  │   │   ├── MeetingVoteEntity.java
  │   │   ├── MeetingVoteRecordEntity.java
  │   │   ├── MeetingMinutesEntity.java
  │   │   ├── MeetingActionItemEntity.java
  │   │   ├── MeetingDocumentEntity.java
  │   │   ├── FeeAcceptanceEntity.java
  │   │   ├── FeedbackPulseEntity.java
  │   │   └── AnalyticsSnapshotEntity.java
  │   └── mapper/
  │       ├── PartnerMeetingMapper.java
  │       └── MeetingMinutesMapper.java
```

**Frontend Route Structure:**
```
/partner/meetings                           → MeetingListPage (organizer)
/partner/meetings/create                    → MeetingCreatePage (organizer)
/partner/meetings/:meetingCode              → MeetingDetailPage (organizer — prep dashboard)
/partner/meetings/:meetingCode/present      → PresenterModePage (organizer — full-screen)
/partner/meetings/:meetingCode/minutes      → MinutesReviewPage (organizer — edit + publish)
/partner/meetings/:meetingCode/view         → MeetingViewPage (partner — published minutes)
/partner/meetings/:meetingCode/fee          → FeeAcceptancePage (partner)
/partner/meetings/:meetingCode/feedback     → FeedbackPulsePage (partner)
```

**Frontend Component Location:**
```
src/components/
  ├── organizer/
  │   └── meetings/
  │       ├── MeetingCreateForm.tsx
  │       ├── MeetingPrepDashboard.tsx
  │       ├── AgendaBuilder.tsx
  │       ├── MinutesEditor.tsx
  │       └── FeeAcceptanceTracker.tsx
  ├── presenter/                          # Dedicated Presenter Mode components
  │   ├── PresenterLayout.tsx
  │   ├── AgendaSidebar.tsx
  │   ├── NotePanel.tsx
  │   ├── items/                          # One component per agenda item type
  │   │   ├── GreetingItem.tsx
  │   │   ├── AnalyticsItem.tsx
  │   │   ├── AuditSignoffItem.tsx
  │   │   ├── BudgetApprovalItem.tsx
  │   │   ├── TopicDiscussionItem.tsx
  │   │   └── CustomItem.tsx
  │   └── shared/
  │       ├── VotePanel.tsx
  │       ├── DocumentViewer.tsx
  │       └── ActionItemForm.tsx
  └── partner/
      └── meetings/
          ├── PublishedMinutesView.tsx
          ├── FeeAcceptanceWidget.tsx
          └── FeedbackPulseForm.tsx
```

### Format Patterns (Meeting-Specific)

**Minutes JSONB Structure:**
All agents must use this exact structure for the minutes content field:
```json
{
  "meetingCode": "PM-2026-Spring",
  "date": "2026-05-15",
  "location": "Bern, Kulturhof",
  "sections": [
    {
      "agendaItemType": "GREETING",
      "title": "Who Is Who — Attendance",
      "attendance": {
        "present": [
          { "companyName": "TechCorp", "delegateName": "Max Müller", "isStandIn": false }
        ],
        "absent": [
          { "companyName": "DesignCo", "delegateName": null, "isStandIn": false }
        ]
      },
      "notes": "Welcome remarks by organizer..."
    },
    {
      "agendaItemType": "AUDIT_SIGNOFF",
      "title": "Financial Report 2025 — Audit Sign-off",
      "document": { "fileName": "audit-2025.pdf", "s3Key": "..." },
      "vote": {
        "result": "APPROVED",
        "tally": { "approve": 8, "decline": 0 },
        "records": [
          { "companyName": "TechCorp", "decision": "APPROVE", "voterType": "PARTNER" }
        ]
      },
      "notes": "Auditor confirmed clean report..."
    }
  ],
  "actionItems": [
    {
      "title": "Review venue options",
      "ownerUsername": "sarah.mueller",
      "deadline": "2026-08-01",
      "status": "OPEN",
      "carriedForwardFrom": null
    }
  ]
}
```

**Auto-Save Debounce — Exact Pattern:**
All agents implementing auto-save must use this hook pattern:
```typescript
// useAutoSave.ts — single implementation, used by all auto-save components
const useAutoSave = (mutationFn: MutationFunction, delayMs = 2000) => {
  const mutation = useMutation({ mutationFn });
  const debouncedSave = useMemo(
    () => debounce((data) => mutation.mutate(data), delayMs),
    [mutation, delayMs]
  );
  return { save: debouncedSave, status: mutation.status };
};
```

**Vote Result Display Format:**
Consistent format across audit and budget votes:
- Banner text: `"{Document Title} — {Approved|Declined} ({approve}/{total})"`
- Example: `"Financial Report 2025 — Approved (8/8)"`

### Communication Patterns (Meeting-Specific)

**Meeting Status Transitions:**
```
DRAFT → SCHEDULED          # Triggered by: organizer saves + confirms (sends ICS invites)
SCHEDULED → IN_PROGRESS    # Triggered by: organizer clicks "Start Presenter Mode"
IN_PROGRESS → COMPLETED    # Triggered by: organizer clicks "End Meeting"
```
- No backward transitions allowed (COMPLETED → IN_PROGRESS is forbidden)
- Status transitions are auditable: `statusChangedAt`, `statusChangedBy` fields

**Email Notification Types:**
Agents adding email functionality must use these exact template identifiers:
```
partner_meeting_invite          — ICS invite on DRAFT → SCHEDULED
partner_meeting_minutes         — Minutes published notification
partner_meeting_fee_request     — Async fee acceptance request
partner_meeting_fee_reminder    — Fee reminder at 7/14 days
partner_meeting_feedback_pulse  — Feedback survey 24h after publish
```

### Process Patterns (Meeting-Specific)

**Presenter Mode Entry/Exit:**
```
Entry:
  1. Pre-fetch ALL meeting data (agenda items, partners, documents, previous action items)
  2. Validate meeting status = SCHEDULED
  3. Transition meeting to IN_PROGRESS
  4. Set presenterSessionStartedBy/At
  5. Render PresenterLayout with first agenda item

Exit (normal):
  1. Organizer clicks "End Meeting" on last item
  2. Confirm dialog: "End meeting and proceed to minutes review?"
  3. Transition meeting to COMPLETED
  4. Clear presenterSession fields
  5. Navigate to MinutesReviewPage

Exit (escape):
  1. Organizer presses Esc
  2. Confirm dialog: "Exit Presenter Mode? Your progress is saved."
  3. Navigate back to meeting detail (meeting stays IN_PROGRESS)
  4. Organizer can re-enter Presenter Mode to resume
```

**Minutes Assembly Sequence:**
```
1. Query MeetingAttendanceRepository → build attendance section
2. For each agenda item (sorted by sortOrder):
   a. Query item-specific data (votes, documents, snapshots)
   b. Query notes for this item
   c. Build section with type-appropriate structure
3. Query MeetingActionItemRepository → build action items section
4. Merge into JSONB structure
5. Store in MeetingMinutesEntity (DRAFT)
```

**Action Item Carry-Forward Logic:**
```
When creating a new meeting:
  1. Query all MeetingActionItems where status = OPEN and meeting.status = COMPLETED
  2. For each open item:
     a. Create new MeetingActionItem on new meeting
     b. Set sourceItemId = original item's ID
     c. Calculate carryForwardCount (follow sourceItemId chain)
     d. If carryForwardCount >= 2, set urgencyFlag = true
```

### Enforcement Guidelines

**All AI Agents MUST:**
1. Follow the table naming prefix `partner_meeting_` for all new tables
2. Use the exact JSONB structure for minutes content — no variations
3. Use the `useAutoSave` hook for all auto-save functionality — no custom implementations
4. Follow the Presenter Mode entry/exit sequence exactly — no shortcuts
5. Use meaningful IDs (`meetingCode`, `companyName`, `ownerUsername`) in all API URLs and responses — never expose UUIDs
6. Store enum values as `lowercase_snake_case` in the database, `UPPER_CASE` in JSON/API
7. Place all Presenter Mode components under `src/components/presenter/` — not in `organizer/` or `shared/`

### Anti-Patterns to Avoid

| Anti-Pattern | Correct Approach |
|-------------|-----------------|
| Storing partner email/name in attendance entity | Store `companyName` only, enrich via HTTP (ADR-004) |
| Creating a separate "meeting-service" microservice | Extend partner-coordination-service |
| Using WebSocket for auto-save | Debounced HTTP PATCH is sufficient |
| Storing minutes as flat HTML/text | Use structured JSONB with typed sections |
| Hardcoding agenda items in frontend | All items from backend AgendaItem entities |
| Adding `@JsonValue` to meeting enums | Default Jackson serialization (project-context.md rule) |
| Direct `fetch()` calls for auto-save | Use TanStack Query `useMutation` via service layer |
| Mutable votes after completion | VoteRecords are immutable once vote is completed |

## Project Structure & Boundaries

### Complete Project Structure (New Files Only)

This feature adds files within the existing BATbern monorepo. Only new/modified paths shown.

**Backend — Partner Coordination Service:**
```
services/partner-coordination-service/
  └── src/
      ├── main/java/ch/batbern/partnercoordination/
      │   └── meeting/
      │       ├── controller/
      │       │   ├── PartnerMeetingController.java
      │       │   └── MeetingMinutesController.java
      │       ├── service/
      │       │   ├── PartnerMeetingService.java
      │       │   ├── MeetingPresenterService.java
      │       │   ├── MeetingMinutesService.java
      │       │   ├── FeeAcceptanceService.java
      │       │   └── MeetingScheduledTasks.java
      │       ├── repository/
      │       │   ├── PartnerMeetingRepository.java
      │       │   ├── MeetingAgendaItemRepository.java
      │       │   ├── MeetingAttendanceRepository.java
      │       │   ├── MeetingVoteRepository.java
      │       │   ├── MeetingVoteRecordRepository.java
      │       │   ├── MeetingMinutesRepository.java
      │       │   ├── MeetingActionItemRepository.java
      │       │   ├── MeetingDocumentRepository.java
      │       │   ├── FeeAcceptanceRepository.java
      │       │   ├── FeedbackPulseRepository.java
      │       │   └── AnalyticsSnapshotRepository.java
      │       ├── domain/
      │       │   ├── PartnerMeetingEntity.java
      │       │   ├── MeetingAgendaItemEntity.java
      │       │   ├── MeetingAttendanceEntity.java
      │       │   ├── MeetingVoteEntity.java
      │       │   ├── MeetingVoteRecordEntity.java
      │       │   ├── MeetingMinutesEntity.java
      │       │   ├── MeetingActionItemEntity.java
      │       │   ├── MeetingDocumentEntity.java
      │       │   ├── FeeAcceptanceEntity.java
      │       │   ├── FeedbackPulseEntity.java
      │       │   ├── AnalyticsSnapshotEntity.java
      │       │   ├── MeetingStatus.java              # Enum
      │       │   ├── MinutesStatus.java               # Enum
      │       │   ├── AgendaItemType.java              # Enum
      │       │   ├── VoteDecision.java                # Enum
      │       │   ├── FeeAcceptanceStatus.java         # Enum
      │       │   └── PresenceStatus.java              # Enum (PRESENT, ABSENT, STAND_IN)
      │       └── mapper/
      │           ├── PartnerMeetingMapper.java
      │           ├── MeetingMinutesMapper.java
      │           └── MeetingPresenterMapper.java
      └── main/resources/db/migration/
          ├── V{next}__create_partner_meetings.sql
          ├── V{next+1}__create_meeting_agenda_items.sql
          ├── V{next+2}__create_meeting_attendance.sql
          ├── V{next+3}__create_meeting_votes_and_records.sql
          ├── V{next+4}__create_meeting_minutes.sql
          ├── V{next+5}__create_meeting_action_items.sql
          ├── V{next+6}__create_meeting_documents.sql
          ├── V{next+7}__create_fee_acceptance.sql
          ├── V{next+8}__create_feedback_pulse.sql
          └── V{next+9}__create_analytics_snapshots.sql
```

**Backend — Integration Tests:**
```
services/partner-coordination-service/
  └── src/test/java/ch/batbern/partnercoordination/
      └── meeting/
          ├── controller/
          │   ├── PartnerMeetingControllerIntegrationTest.java
          │   ├── MeetingPresenterIntegrationTest.java
          │   └── MeetingMinutesControllerIntegrationTest.java
          └── service/
              ├── PartnerMeetingServiceTest.java
              ├── MeetingMinutesServiceTest.java
              └── FeeAcceptanceServiceTest.java
```

**OpenAPI Spec:**
```
docs/api/
  └── partner-meeting.openapi.yml    # New spec for all meeting endpoints
```

**Frontend — New Components:**
```
web-frontend/src/
  ├── components/
  │   ├── organizer/
  │   │   └── meetings/
  │   │       ├── MeetingListPage.tsx
  │   │       ├── MeetingCreateForm.tsx
  │   │       ├── MeetingPrepDashboard.tsx
  │   │       ├── AgendaBuilder.tsx
  │   │       ├── MinutesEditor.tsx
  │   │       └── FeeAcceptanceTracker.tsx
  │   ├── presenter/
  │   │   ├── PresenterLayout.tsx
  │   │   ├── AgendaSidebar.tsx
  │   │   ├── NotePanel.tsx
  │   │   ├── items/
  │   │   │   ├── GreetingItem.tsx
  │   │   │   ├── AnalyticsItem.tsx
  │   │   │   ├── AuditSignoffItem.tsx
  │   │   │   ├── BudgetApprovalItem.tsx
  │   │   │   ├── TopicDiscussionItem.tsx
  │   │   │   └── CustomItem.tsx
  │   │   └── shared/
  │   │       ├── VotePanel.tsx
  │   │       ├── DocumentViewer.tsx
  │   │       └── ActionItemForm.tsx
  │   └── partner/
  │       └── meetings/
  │           ├── PublishedMinutesView.tsx
  │           ├── FeeAcceptanceWidget.tsx
  │           └── FeedbackPulseForm.tsx
  ├── hooks/
  │   ├── useAutoSave.ts
  │   ├── usePresenterKeyboard.ts
  │   └── usePresenterNavigation.ts
  ├── services/
  │   └── partnerMeetingService.ts
  ├── stores/
  │   └── presenterStore.ts            # Zustand store for presenter navigation state
  └── types/generated/
      └── partner-meeting-api.types.ts  # Auto-generated from OpenAPI
```

**Frontend — Tests:**
```
web-frontend/src/
  ├── components/
  │   ├── presenter/
  │   │   ├── PresenterLayout.test.tsx
  │   │   ├── AgendaSidebar.test.tsx
  │   │   ├── NotePanel.test.tsx
  │   │   └── shared/
  │   │       ├── VotePanel.test.tsx
  │   │       └── DocumentViewer.test.tsx
  │   └── organizer/meetings/
  │       ├── AgendaBuilder.test.tsx
  │       └── MinutesEditor.test.tsx
  └── e2e/
      ├── partner-meeting-organizer.spec.ts    # Organizer project
      └── partner/
          └── partner-meeting-view.spec.ts     # Partner project
```

**Bruno API Tests:**
```
bruno-tests/
  └── partner-meetings/
      ├── create-meeting.bru
      ├── get-meeting.bru
      ├── update-agenda.bru
      ├── start-presenter-mode.bru
      ├── toggle-attendance.bru
      ├── cast-vote.bru
      ├── save-notes.bru
      ├── assemble-minutes.bru
      ├── publish-minutes.bru
      ├── fee-acceptance.bru
      └── feedback-pulse.bru
```

### Architectural Boundaries

**API Boundaries:**

| Boundary | Endpoints | Auth | Notes |
|----------|----------|------|-------|
| Meeting Management | `POST/GET/PUT /api/v1/partner-meetings/**` | Organizer | CRUD, agenda config, status transitions |
| Presenter Mode | `PATCH /api/v1/partner-meetings/{code}/presenter/**` | Organizer | Attendance, votes, notes, snapshots |
| Minutes | `GET/PUT/POST /api/v1/partner-meetings/{code}/minutes` | Organizer (edit/publish), Partner (view published) |
| Fee Acceptance | `GET/PUT /api/v1/partner-meetings/{code}/fee-acceptance` | Partner | Accept/decline fee |
| Feedback | `POST /api/v1/partner-meetings/{code}/feedback` | Partner | Submit feedback pulse |
| Documents | `POST/GET /api/v1/partner-meetings/{code}/documents/**` | Organizer (upload), Partner (view via minutes) |

**Service Boundaries — What the Meeting Feature Can Access:**

| Target Service | Access Pattern | Data Retrieved |
|---------------|---------------|----------------|
| Company Service | HTTP GET `/api/v1/companies/{companyName}` | Company logo, displayName |
| User Service | HTTP GET `/api/v1/users/{username}` | Profile photo, firstName, lastName |
| Partner Service (same DB) | Direct repository access | Partner entities, contacts |
| S3 | Presigned URL generation | Document upload/download URLs |
| SES | Email sending | ICS invites, minutes distribution, reminders |

**Data Boundaries:**
- All `partner_meeting_*` tables owned by Partner Coordination Service
- Cross-service: only `companyName` and `username` strings stored — never UUIDs from other services
- Minutes JSONB: self-contained document — no foreign key references, only meaningful IDs
- Analytics snapshots: immutable JSONB — captured data, not live references

### Requirements to Structure Mapping

| FR Group | Backend Package | Frontend Components | API Spec Section |
|----------|----------------|--------------------|--------------------|
| FR1: Meeting Management | `meeting/service/PartnerMeetingService` | `organizer/meetings/MeetingCreateForm`, `AgendaBuilder` | `partner-meetings` |
| FR2: RSVP & Attendance | `meeting/service/MeetingPresenterService` | `presenter/items/GreetingItem` | `presenter/attendance` |
| FR3: Presenter Mode | `meeting/controller/*` | `presenter/PresenterLayout`, `AgendaSidebar`, `NotePanel` | `presenter/*` |
| FR4: Documents | `meeting/service/MeetingPresenterService` | `presenter/shared/DocumentViewer` | `documents` |
| FR5: Voting | `meeting/service/MeetingPresenterService` | `presenter/shared/VotePanel` | `presenter/votes` |
| FR6: Minutes | `meeting/service/MeetingMinutesService` | `organizer/meetings/MinutesEditor`, `partner/meetings/PublishedMinutesView` | `minutes` |
| FR7: Meeting Prep | `meeting/service/PartnerMeetingService` | `organizer/meetings/MeetingPrepDashboard` | `partner-meetings/{code}` |
| FR8: Post-Meeting | `meeting/service/FeeAcceptanceService`, `MeetingScheduledTasks` | `partner/meetings/FeeAcceptanceWidget`, `FeedbackPulseForm` | `fee-acceptance`, `feedback` |
| FR9: Action Items | `meeting/service/MeetingPresenterService` | `presenter/shared/ActionItemForm` | `presenter/action-items` |
| FR10: Analytics Snapshot | `meeting/service/MeetingPresenterService` | `presenter/items/AnalyticsItem` | `presenter/snapshots` |

### Data Flow

```
Pre-Meeting:
  Organizer → MeetingCreateForm → partnerMeetingService → POST /api/v1/partner-meetings
  Organizer → AgendaBuilder → partnerMeetingService → PUT /api/v1/partner-meetings/{code}/agenda
  System → SES → Partner (ICS invite email)
  Partner → RSVP → partnerMeetingService → PUT /api/v1/partner-meetings/{code}/rsvp

During Meeting (Presenter Mode):
  Organizer → PresenterLayout → presenterStore (Zustand) → navigation state
  Organizer → GreetingItem → partnerMeetingService → PATCH .../presenter/attendance
  Organizer → VotePanel → partnerMeetingService → POST .../presenter/votes
  Organizer → NotePanel → useAutoSave → PATCH .../presenter/notes (debounced 2s)
  Organizer → DocumentViewer → S3 presigned URL → direct browser render
  All actions → auto-persisted → available for minutes assembly

Post-Meeting:
  Organizer → "Review Minutes" → MeetingMinutesService.assembleMinutes() → JSONB
  Organizer → MinutesEditor → PUT .../minutes → edits
  Organizer → "Publish" → POST .../minutes/publish → SES → all partners
  Partner → PublishedMinutesView → GET .../minutes (published)
  Partner → FeeAcceptanceWidget → PUT .../fee-acceptance
  System → @Scheduled → fee reminders (7/14 days) → SES
  System → @Scheduled → feedback pulse (24h) → SES
  Partner → FeedbackPulseForm → POST .../feedback
```

## Architecture Validation Results

### Coherence Validation ✅

**Decision Compatibility:** All technology choices are from the existing BATbern stack — zero version conflicts. JSONB minutes content supported by PostgreSQL 15. TanStack Query + Zustand + debounced mutations is a proven pattern. Spring @Scheduled runs in existing Fargate task.

**Pattern Consistency:** All naming conventions align with project-context.md. Database prefix `partner_meeting_*` consistent with existing tables. Enum handling follows established convention. API URLs follow ADR-003.

**Structure Alignment:** Backend sub-package structure matches existing services. Frontend Presenter Mode properly separated. Test structure mirrors source. Bruno collection follows naming conventions.

### Requirements Coverage Validation ✅

**All 10 Functional Requirement Groups:** Fully covered by architectural decisions, entity model, component structure, and API boundaries.

**All Non-Functional Requirements:** Addressed through specific patterns (pre-fetch for performance, debounced save for reliability, dark theme contrast for accessibility, role boundaries for security).

### Implementation Readiness Validation ✅

**Decision Completeness:** 11 entities, 6 enums, API boundaries, JSONB structure example, and auto-save hook pattern — all specified with sufficient detail for AI agent implementation.

**Structure Completeness:** Every FR group mapped to backend package + frontend component + API spec section. Full data flow documented across all meeting phases.

**Pattern Completeness:** 7 enforcement rules, 8 anti-patterns, 3 detailed process sequences (Presenter entry/exit, minutes assembly, action item carry-forward).

### Gap Analysis

**Critical Gaps:** None

**Important Gaps (resolve during implementation):**
1. ICS invite library → recommend `ical4j` (standard Java library)
2. PDF viewer approach → start with browser-native `<object>`/`<iframe>`, add `react-pdf` if needed
3. Email template HTML content → follows existing SES template patterns

**Nice-to-Have:**
- Drag-and-drop library for AgendaBuilder → recommend `@dnd-kit`
- Presenter Mode transition animations → pure CSS transitions sufficient

### Architecture Completeness Checklist

**✅ Requirements Analysis**
- [x] Project context thoroughly analyzed (PRD + UX + brainstorming + project-context.md)
- [x] Scale and complexity assessed (medium — rich frontend, straightforward backend)
- [x] Technical constraints identified (brownfield, existing ADRs, single-operator model)
- [x] Cross-cutting concerns mapped (minutes assembly, auto-save, notifications, documents, enrichment, action items)

**✅ Architectural Decisions**
- [x] Entity model fully specified (11 entities, ADR-003 compliant)
- [x] Minutes auto-assembly pattern chosen (query-based, not event-sourced)
- [x] Frontend layout strategy decided (dedicated PresenterLayout)
- [x] Auto-save pattern defined (debounced TanStack Query mutation)
- [x] Scheduled workflows approach chosen (Spring @Scheduled)
- [x] No new infrastructure required

**✅ Implementation Patterns**
- [x] Database naming conventions established (partner_meeting_* prefix)
- [x] All enums defined with values and serialization rules
- [x] Backend package structure specified
- [x] Frontend component and route structure specified
- [x] Minutes JSONB structure defined with example
- [x] Process sequences documented (presenter flow, minutes assembly, carry-forward)

**✅ Project Structure**
- [x] Complete directory structure for new files
- [x] API boundaries with auth requirements
- [x] Service boundaries with access patterns
- [x] Requirements-to-structure mapping
- [x] End-to-end data flow documented

### Architecture Readiness Assessment

**Overall Status:** READY FOR IMPLEMENTATION

**Confidence Level:** High — brownfield extension with established patterns, no novel infrastructure, medium complexity

**Key Strengths:**
- Leverages proven BATbern patterns end-to-end (ADR-003/004/006, layered architecture, TanStack Query)
- Minutes-as-byproduct is architecturally elegant — no new backend pattern needed, just good entity design
- Single-operator model eliminates entire classes of complexity (no WebSocket, no conflict resolution, no distributed locking)
- All cross-service communication follows existing HTTP enrichment pattern

**Areas for Future Enhancement:**
- Minutes PDF export (Phase 3) may require additional library evaluation
- Historized analytics archive browsing may need pagination/search architecture
- New partner onboarding tour may need a guided tour library selection

### Implementation Handoff

**AI Agent Guidelines:**
- Read `project-context.md` (65 rules) before implementing any code
- Follow all patterns in this architecture document exactly
- Use the enforcement guidelines and anti-patterns as a checklist
- Respect service boundaries — never store cross-service UUIDs

**First Implementation Priority:**
1. OpenAPI spec: `docs/api/partner-meeting.openapi.yml`
2. Flyway migrations: all `partner_meeting_*` tables
3. Entity classes with enums
4. Meeting CRUD endpoints (FR1)
5. Then iteratively: Presenter Mode → agenda items → minutes → post-meeting
