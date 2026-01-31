# Epic 6: Automated Speaker Onboarding - Architecture Plan

**Created**: 2026-01-22
**Author**: Winston (Architect Agent)
**Status**: DRAFT - Pending Review
**Key Decision**: Simplified 6-state workflow model (reduced from 11 states)

---

## Executive Summary

This document outlines the architecture for refactoring speaker onboarding to an automated, self-service workflow. The goal is to reduce organizer workload by ~40% while enabling speakers to independently:
1. Respond to event invitations (via magic link - no login required)
2. Provide/update their speaker profile
3. Submit presentation **title and abstract** (required, triggers quality review)
4. Upload presentation files - PPTX, PDF (optional, can be added anytime)

### Simplified Workflow Model

**6 Core States**: `IDENTIFIED` → `INVITED` → `ACCEPTED` → `CONFIRMED`
                                      ↘ `DECLINED`    ↘ `WITHDREW`

**Content tracked separately**: `PENDING` → `SUBMITTED` → `APPROVED` (with `REVISION_NEEDED` loop)

This simplified model replaces the previous 11-state machine, providing:
- Clearer mental model for organizers and speakers
- Decoupled content workflow allowing multiple revision cycles
- Easier validation of state transitions

---

## Current State Analysis

### What Exists Today

| Component | Status | Location |
|-----------|--------|----------|
| **Speaker Entity** | ✅ Complete | `event-management-service/domain/Speaker.java` |
| **Speaker CRUD API** | ✅ Complete | `GET/POST/PATCH/DELETE /api/v1/speakers/{username}` |
| **Speaker Pool (Event-specific)** | ✅ Complete | `SpeakerPool.java` - brainstorming phase |
| **Speaker Workflow States** | ✅ Complete | `SpeakerWorkflowState` enum (shared-kernel) |
| **File Upload Infrastructure** | ✅ Complete | ADR-002 presigned URL pattern |
| **Session Materials Upload** | ✅ Complete | Story 5.9 - `SessionMaterialsController` |
| **Email Service** | ✅ Complete | `shared-kernel/EmailService.java` (AWS SES) |
| **Domain Events** | ✅ Complete | `SpeakerCreatedEvent`, `SpeakerInvitedEvent` |

### What's Missing (Gap Analysis)

| Component | Gap | Epic 6 Story |
|-----------|-----|--------------|
| **Automated Invitations** | Manual contact only | Story 6.1 |
| **Speaker Response Portal** | No self-service | Story 6.2 |
| **Material Self-Submission** | Organizer uploads only | Story 6.3 |
| **Speaker Dashboard** | No speaker view | Story 6.4 |
| **Deadline Reminders** | Manual follow-up | Story 6.5 |
| **Magic Link Auth** | No passwordless auth | Story 6.1/6.4 |

---

## Target Architecture

### Simplified Speaker Workflow (6 States)

**Design Decision**: Use 6 core workflow states for speaker lifecycle, track content progress separately with timestamps/status fields. This simplifies the state machine while maintaining full functionality.

#### State Machine

```
        ┌──────────────┐
        │  IDENTIFIED  │  Speaker added to pool
        └──────┬───────┘
               │ Send invitation (auto/manual)
               ▼
        ┌──────────────┐
        │   INVITED    │  Awaiting response
        └──────┬───────┘
               │
        ┌──────┴──────┐
        ▼             ▼
┌──────────────┐ ┌──────────────┐
│   ACCEPTED   │ │   DECLINED   │ (terminal)
└──────┬───────┘ └──────────────┘
       │
       ├─────────────────┐
       ▼                 ▼
┌──────────────┐  ┌──────────────┐
│  CONFIRMED   │  │   WITHDREW   │ (terminal)
└──────────────┘  └──────────────┘
   (terminal)
```

#### State Definitions

| # | State | Description | Triggered By | Next States |
|---|-------|-------------|--------------|-------------|
| 1 | `IDENTIFIED` | Speaker added to pool, not yet contacted | Organizer adds to brainstorm list | → INVITED |
| 2 | `INVITED` | Invitation email sent with magic link | Automated email or manual outreach | → ACCEPTED, DECLINED |
| 3 | `ACCEPTED` | Speaker confirmed participation | Speaker clicks "Accept" in portal | → CONFIRMED, WITHDREW |
| 4 | `DECLINED` | Speaker rejected invitation (terminal) | Speaker clicks "Decline" in portal | — |
| 5 | `CONFIRMED` | Speaker in final published agenda (terminal) | Event workflow reaches "Published" | — |
| 6 | `WITHDREW` | Speaker backed out after accepting (terminal) | Speaker or organizer cancels | — |

#### Handling TENTATIVE Responses

**Design Decision**: TENTATIVE is handled as a **flag on INVITED state**, not a separate state.

When a speaker responds "Maybe/Tentative":
- Workflow state remains `INVITED`
- `is_tentative = TRUE`
- `tentative_reason` stores their constraints (e.g., "Need to check travel dates")
- Organizer can follow up and speaker can later Accept or Decline

```
INVITED (is_tentative=false) ──▶ Speaker clicks "Maybe" ──▶ INVITED (is_tentative=true)
                                                                    │
                                                         ┌──────────┴──────────┐
                                                         ▼                     ▼
                                                    ACCEPTED              DECLINED
```

This keeps the workflow at 6 states while supporting the tentative use case.

#### Session Assignment

**Design Decision**: Session is assigned **at invite time or later by organizer**.

| Scenario | Session Assignment |
|----------|-------------------|
| Invite with specific session | `session_id` set at invite |
| Invite without session (general pool) | `session_id` NULL, assigned later |
| Speaker submits content | Requires `session_id` (organizer must assign first) |

```
┌─────────────────────────────────────────────────────────────────┐
│                    SESSION ASSIGNMENT FLOW                      │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Option A: Invite with session                                  │
│  ───────────────────────────────                                │
│  Organizer selects session → Invite sent with sessionId         │
│  Speaker accepts → Can immediately submit content               │
│                                                                 │
│  Option B: Invite to general pool                               │
│  ─────────────────────────────────                              │
│  Organizer invites without session → sessionId = NULL           │
│  Speaker accepts → Organizer assigns session later              │
│  Session assigned → Speaker can now submit content              │
│                                                                 │
│  Content submission blocked until session_id is set             │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

#### Content Progress (Tracked Separately)

Content submission is **decoupled from workflow state** to allow multiple submission/revision cycles:

| Field | Type | Description |
|-------|------|-------------|
| `content_status` | ENUM | `PENDING`, `SUBMITTED`, `APPROVED`, `REVISION_NEEDED` |
| `content_submitted_at` | TIMESTAMP | When speaker submitted content |
| `content_approved_at` | TIMESTAMP | When organizer approved content |
| `content_approved_by` | VARCHAR | Username of approving organizer |
| `is_overflow` | BOOLEAN | Speaker accepted but event is full |
| `is_tentative` | BOOLEAN | Speaker responded "maybe" (still in INVITED state) |
| `tentative_reason` | TEXT | Why speaker is tentative (constraints, etc.) |
| `response_deadline` | TIMESTAMP | Deadline for speaker to respond |
| `content_deadline` | TIMESTAMP | Deadline for content submission |

### High-Level Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        SPEAKER ONBOARDING WORKFLOW                          │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐    ┌───────────┐ │
│  │  IDENTIFIED  │───▶│   INVITED    │───▶│   ACCEPTED   │───▶│ CONFIRMED │ │
│  │   (Pool)     │    │   (Email)    │    │   (Portal)   │    │  (Final)  │ │
│  └──────────────┘    └──────────────┘    └──────────────┘    └───────────┘ │
│        │                    │                   │                  │        │
│        ▼                    ▼                   ▼                  ▼        │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐    ┌───────────┐ │
│  │ SpeakerPool  │    │  AWS SES     │    │  Portal UI   │    │  Profile  │ │
│  │ (Organizer)  │    │  Magic Link  │    │  Response    │    │  Content  │ │
│  └──────────────┘    └──────────────┘    │  Form        │    │  Upload   │ │
│                                          └──────────────┘    └───────────┘ │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘

                              ┌──────────────────────┐
                              │   CONTENT WORKFLOW   │
                              │  (Parallel to State) │
                              ├──────────────────────┤
                              │                      │
                              │  ┌────────────────┐  │
                              │  │ Profile Update │  │
                              │  │ - Bio          │  │
                              │  │ - Photo        │  │
                              │  │ - Expertise    │  │
                              │  └────────────────┘  │
                              │         │           │
                              │         ▼           │
                              │  ┌────────────────┐  │
                              │  │ Session Info   │  │
                              │  │ - Title        │  │
                              │  │ - Abstract     │  │
                              │  └────────────────┘  │
                              │         │           │
                              │         ▼           │
                              │  ┌────────────────┐  │
                              │  │ File Upload    │  │
                              │  │ - PPTX/PDF     │  │
                              │  │ - Handouts     │  │
                              │  └────────────────┘  │
                              │         │           │
                              │         ▼           │
                              │  ┌────────────────┐  │
                              │  │ Quality Review │  │
                              │  │ (Organizer)    │  │
                              │  └────────────────┘  │
                              │                      │
                              │  Status: PENDING →   │
                              │  SUBMITTED → APPROVED│
                              │                      │
                              └──────────────────────┘
```

---

## Architecture Components

### 1. Magic Link Authentication System

**Purpose**: Allow speakers to access the portal without creating an account.

**Technical Design**:
```
┌─────────────────────────────────────────────────────────────────┐
│                   MAGIC LINK AUTHENTICATION                     │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Email Link Structure:                                          │
│  https://portal.batbern.ch/speaker/respond/{token}              │
│                                                                 │
│  Token Properties:                                              │
│  - JWT with speaker_invitation claim                            │
│  - Contains: speakerId, eventCode, sessionId, action            │
│  - Expiry: 30 days (configurable)                               │
│  - Single-use for state-changing actions (Accept/Decline)       │
│  - Reusable for read-only actions (View dashboard)              │
│                                                                 │
│  Database Schema:                                               │
│  CREATE TABLE speaker_invitation_tokens (                       │
│      id UUID PRIMARY KEY,                                       │
│      speaker_id UUID NOT NULL,                                  │
│      event_id UUID NOT NULL,                                    │
│      session_id UUID,                                           │
│      token_hash VARCHAR(255) UNIQUE NOT NULL, -- SHA-256        │
│      action VARCHAR(50) NOT NULL, -- RESPOND, SUBMIT, VIEW      │
│      expires_at TIMESTAMP NOT NULL,                             │
│      used_at TIMESTAMP,                                         │
│      created_at TIMESTAMP DEFAULT NOW()                         │
│  );                                                             │
│                                                                 │
│  Security:                                                      │
│  - Token stored as hash (SHA-256)                               │
│  - Rate limiting: 5 requests/minute per token                   │
│  - IP logging for audit trail                                   │
│  - HTTPS only                                                   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

**Implementation Location**: `services/event-management-service/src/main/java/ch/batbern/events/auth/`

### 2. Automated Invitation System

**Purpose**: Send automated email invitations with unique response links.

**Technical Design**:
```java
// Email Template Structure
public record SpeakerInvitationEmail(
    String speakerName,
    String speakerEmail,
    String eventName,
    String eventDate,
    String sessionTitle,
    String sessionDescription,
    String responseDeadline,
    String acceptLink,      // Magic link for acceptance
    String declineLink,     // Magic link for decline
    String organizerName,
    String organizerEmail
) {}

// Email Service Extension
@Service
public class SpeakerInvitationService {

    @Async
    public void sendInvitation(SpeakerInvitation invitation) {
        // 1. Generate magic links (accept/decline)
        String acceptToken = generateMagicToken(invitation, Action.ACCEPT);
        String declineToken = generateMagicToken(invitation, Action.DECLINE);

        // 2. Build email from template
        String emailBody = templateEngine.process("speaker-invitation", Map.of(
            "acceptLink", buildPortalUrl(acceptToken),
            "declineLink", buildPortalUrl(declineToken),
            // ... other variables
        ));

        // 3. Send via AWS SES
        emailService.sendHtmlEmail(
            invitation.speakerEmail(),
            "BATbern Invitation: " + invitation.eventName(),
            emailBody
        );

        // 4. Emit domain event
        eventPublisher.publish(new SpeakerInvitationSentEvent(...));

        // 5. Update speaker status
        updateSpeakerStatus(invitation.speakerId(), CONTACTED);
    }
}
```

**Email Templates Location**: `services/event-management-service/src/main/resources/templates/email/`

### 3. Speaker Self-Service Portal

**Purpose**: Web interface for speakers to respond, update profile, and submit materials.

**Frontend Architecture**:
```
web-frontend/src/pages/speaker-portal/
├── SpeakerPortalLayout.tsx       # Layout with minimal nav (no full auth)
├── InvitationResponsePage.tsx    # Accept/Decline/Tentative
├── ProfileUpdatePage.tsx         # Bio, photo, expertise
├── ContentSubmissionPage.tsx     # Title, abstract, files
├── SpeakerDashboardPage.tsx      # View upcoming/past events
└── components/
    ├── ResponseForm.tsx          # Availability, constraints
    ├── ProfileForm.tsx           # Speaker profile editor
    ├── ContentWizard.tsx         # Multi-step submission
    └── MaterialUpload.tsx        # Reuse FileUpload component
```

**API Endpoints (Speaker-facing)**:
```yaml
# Speaker Portal API (public with magic link auth)

# Respond to invitation
POST /api/v1/speaker-portal/respond
  Body: {
    token: string,
    response: "ACCEPT" | "DECLINE" | "TENTATIVE",
    reason?: string,           # Required if DECLINE or TENTATIVE
    constraints?: string       # Optional: travel, schedule constraints
  }
  Response: 200 OK {
    speakerName: string,
    eventName: string,
    eventDate: string,
    sessionTitle?: string,     # If session assigned
    nextSteps: string[],       # ["Complete your profile", "Submit title and abstract"]
    contentDeadline?: string   # If accepted
  }
  Notes:
    - ACCEPT → workflow_state = ACCEPTED
    - DECLINE → workflow_state = DECLINED
    - TENTATIVE → workflow_state stays INVITED, is_tentative = true

# Get speaker profile (combined User + Speaker data)
GET /api/v1/speaker-portal/profile?token={token}
  Response: 200 OK {
    username: string,
    email: string,
    firstName: string,
    lastName: string,
    bio?: string,
    profilePictureUrl?: string,
    expertiseAreas?: string[],
    speakingTopics?: string[],
    linkedInUrl?: string,
    languages?: string[]
  }

# Update speaker profile (syncs to User entity per ADR-004)
PATCH /api/v1/speaker-portal/profile
  Body: {
    token: string,
    bio?: string,              # Updates User.bio in Company Service
    firstName?: string,        # Updates User.firstName
    lastName?: string,         # Updates User.lastName
    expertiseAreas?: string[], # Updates Speaker.expertiseAreas
    speakingTopics?: string[], # Updates Speaker.speakingTopics
    linkedInUrl?: string,      # Updates Speaker.linkedInUrl
    languages?: string[]       # Updates Speaker.languages
  }
  Response: 200 OK { updated profile }
  Notes:
    - Profile changes sync to User entity (single source of truth)
    - Changes apply to ALL events (not event-specific)

# Submit content (title + abstract)
POST /api/v1/speaker-portal/content
  Body: {
    token: string,
    title: string,             # Max 200 chars
    abstract: string           # Max 1000 chars
  }
  Response: 201 Created {
    submissionId: string,
    version: number,
    status: "SUBMITTED",
    sessionTitle: string
  }
  Error 400: "Session not assigned - contact organizer"
  Notes:
    - Requires session_id on SpeakerPool (organizer must assign first)
    - Sets content_status = SUBMITTED
    - Creates ContentSubmission record with version

# Get presigned URL for file upload
POST /api/v1/speaker-portal/materials/presigned-url
  Body: {
    token: string,
    fileName: string,
    contentType: string        # application/pdf, application/vnd.ms-powerpoint, etc.
  }
  Response: 200 OK {
    uploadUrl: string,         # S3 presigned PUT URL
    uploadId: string,
    expiresIn: number          # seconds until URL expires (900)
  }

# Confirm file upload
POST /api/v1/speaker-portal/materials/confirm
  Body: {
    token: string,
    uploadId: string,
    materialType: "PRESENTATION" | "DOCUMENT" | "VIDEO" | "OTHER"
  }
  Response: 200 OK {
    materialId: string,
    fileName: string,
    cloudfrontUrl: string
  }
```

### 4. Content Submission Workflow

**Purpose**: Collect presentation title, abstract, and files with quality review.

**Design Decision**: Content progress is tracked **separately from workflow state** via `content_status` field. This allows:
- Multiple submission/revision cycles without changing workflow state
- Speaker remains in `ACCEPTED` state while iterating on content
- Cleaner separation of concerns

**Content Status Flow**:
```
┌─────────────────────────────────────────────────────────────────┐
│              CONTENT STATUS (Independent of Workflow)           │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│   Speaker in ACCEPTED state can submit/revise content:          │
│                                                                 │
│   ┌─────────┐    ┌───────────┐    ┌──────────┐                 │
│   │ PENDING │───▶│ SUBMITTED │───▶│ APPROVED │                 │
│   └─────────┘    └─────┬─────┘    └──────────┘                 │
│                        │                                        │
│                        │  ⬅─── QUALITY REVIEW HAPPENS HERE      │
│                        │        (Organizer reviews content)     │
│                        ▼                                        │
│                 ┌────────────────┐                              │
│                 │REVISION_NEEDED │──▶ Speaker fixes ──▶ SUBMITTED│
│                 └────────────────┘                              │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

**Content Status Definitions**:

| Status | Description | Actor | Action |
|--------|-------------|-------|--------|
| `PENDING` | Speaker accepted but hasn't submitted content yet | — | Speaker submits |
| `SUBMITTED` | Content awaiting quality review | Speaker | **Organizer reviews** |
| `APPROVED` | Content passed quality review | Organizer | Done |
| `REVISION_NEEDED` | Quality review failed, changes requested | Organizer | Speaker revises → SUBMITTED |

**Quality Review Process** (performed when status = `SUBMITTED`):

```
┌─────────────────────────────────────────────────────────────────┐
│                    QUALITY REVIEW CHECKLIST                     │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Organizer reviews content when content_status = SUBMITTED:     │
│                                                                 │
│  REQUIRED:                                                      │
│  □ Title: Clear, descriptive, not promotional                   │
│  □ Abstract: ≤1000 chars, includes lessons learned              │
│  □ Abstract: Not product promotion or sales pitch               │
│                                                                 │
│  OPTIONAL (can be uploaded later):                              │
│  ○ Presentation file (PPTX/PDF)                                 │
│  ○ Handouts                                                     │
│                                                                 │
│  Actions:                                                       │
│  ├─ Title + Abstract OK → Set status to APPROVED                │
│  └─ Issues found → Set status to REVISION_NEEDED                │
│                    + Add reviewer_feedback                      │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

**File Upload**: Presentation files can be uploaded at any time after acceptance, independent of the quality review. Files are tracked via `SessionMaterial` (Story 5.9) and are **not required** for content approval.

**Database Schema Extension**:

### Migration Sequence

| Migration | Purpose |
|-----------|---------|
| V43 | Add speaker content tracking fields to speaker_pool |
| V44 | Create speaker_invitation_tokens table |
| V45 | Migrate existing workflow states (CONTACTED→INVITED, remove obsolete states) |

```sql
-- ============================================
-- Flyway Migration: V43__Add_speaker_content_tracking.sql
-- ============================================

-- Extend speaker_pool with simplified workflow + content tracking
-- NOTE: workflow_state column may already exist - use IF NOT EXISTS pattern

-- New columns for 6-state workflow
ALTER TABLE speaker_pool ADD COLUMN IF NOT EXISTS content_status VARCHAR(20) DEFAULT 'PENDING';
ALTER TABLE speaker_pool ADD COLUMN IF NOT EXISTS content_submitted_at TIMESTAMP;
ALTER TABLE speaker_pool ADD COLUMN IF NOT EXISTS content_approved_at TIMESTAMP;
ALTER TABLE speaker_pool ADD COLUMN IF NOT EXISTS content_approved_by VARCHAR(255);
ALTER TABLE speaker_pool ADD COLUMN IF NOT EXISTS is_overflow BOOLEAN DEFAULT FALSE;

-- Timestamps for state transitions
ALTER TABLE speaker_pool ADD COLUMN IF NOT EXISTS invited_at TIMESTAMP;
ALTER TABLE speaker_pool ADD COLUMN IF NOT EXISTS accepted_at TIMESTAMP;
ALTER TABLE speaker_pool ADD COLUMN IF NOT EXISTS declined_at TIMESTAMP;
ALTER TABLE speaker_pool ADD COLUMN IF NOT EXISTS withdrew_at TIMESTAMP;
ALTER TABLE speaker_pool ADD COLUMN IF NOT EXISTS confirmed_at TIMESTAMP;

-- Deadline tracking
ALTER TABLE speaker_pool ADD COLUMN IF NOT EXISTS response_deadline TIMESTAMP;
ALTER TABLE speaker_pool ADD COLUMN IF NOT EXISTS content_deadline TIMESTAMP;

-- Tentative response tracking (speaker interested but not committed)
ALTER TABLE speaker_pool ADD COLUMN IF NOT EXISTS is_tentative BOOLEAN DEFAULT FALSE;
ALTER TABLE speaker_pool ADD COLUMN IF NOT EXISTS tentative_reason TEXT;

-- Indexes for common queries
CREATE INDEX IF NOT EXISTS idx_speaker_pool_content_status ON speaker_pool(content_status);
CREATE INDEX IF NOT EXISTS idx_speaker_pool_overflow ON speaker_pool(is_overflow) WHERE is_overflow = TRUE;
CREATE INDEX IF NOT EXISTS idx_speaker_pool_response_deadline ON speaker_pool(response_deadline)
    WHERE response_deadline IS NOT NULL;

-- Content submissions (supports versioning for revision cycles)
CREATE TABLE IF NOT EXISTS speaker_content_submissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    speaker_pool_id UUID NOT NULL REFERENCES speaker_pool(id) ON DELETE CASCADE,
    session_id UUID REFERENCES sessions(id),
    title VARCHAR(200) NOT NULL,
    abstract TEXT NOT NULL,  -- Max 1000 chars enforced in app
    abstract_char_count INTEGER NOT NULL,
    submission_version INTEGER DEFAULT 1,
    reviewer_feedback TEXT,
    submitted_at TIMESTAMP DEFAULT NOW(),
    reviewed_at TIMESTAMP,
    reviewed_by VARCHAR(255),

    CONSTRAINT chk_abstract_length CHECK (abstract_char_count <= 1000)
);

CREATE INDEX IF NOT EXISTS idx_content_submissions_speaker ON speaker_content_submissions(speaker_pool_id);
CREATE INDEX IF NOT EXISTS idx_content_submissions_session ON speaker_content_submissions(session_id);

-- ============================================
-- Flyway Migration: V44__Create_speaker_invitation_tokens.sql
-- ============================================

CREATE TABLE speaker_invitation_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    speaker_pool_id UUID NOT NULL REFERENCES speaker_pool(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,  -- SHA-256 hash
    action VARCHAR(20) NOT NULL,             -- RESPOND, SUBMIT, VIEW
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    used_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    CONSTRAINT chk_token_action CHECK (action IN ('RESPOND', 'SUBMIT', 'VIEW'))
);

CREATE INDEX idx_invitation_tokens_hash ON speaker_invitation_tokens(token_hash);
CREATE INDEX idx_invitation_tokens_speaker ON speaker_invitation_tokens(speaker_pool_id);
CREATE INDEX idx_invitation_tokens_expires ON speaker_invitation_tokens(expires_at)
    WHERE used_at IS NULL;

-- ============================================
-- Flyway Migration: V45__Migrate_workflow_states.sql
-- ============================================

-- Migrate existing workflow states to simplified 6-state model
-- Old state → New state mapping:
--   CONTACTED → INVITED
--   READY → INVITED (with is_tentative = true)
--   CONTENT_SUBMITTED → ACCEPTED (content_status = SUBMITTED)
--   QUALITY_REVIEWED → ACCEPTED (content_status = APPROVED)
--   SLOT_ASSIGNED → ACCEPTED (content_status = APPROVED)
--   OVERFLOW → ACCEPTED (is_overflow = true)

UPDATE speaker_pool SET status = 'INVITED' WHERE status = 'CONTACTED';
UPDATE speaker_pool SET status = 'INVITED', is_tentative = TRUE WHERE status = 'READY';
UPDATE speaker_pool SET status = 'ACCEPTED', content_status = 'SUBMITTED' WHERE status = 'CONTENT_SUBMITTED';
UPDATE speaker_pool SET status = 'ACCEPTED', content_status = 'APPROVED' WHERE status = 'QUALITY_REVIEWED';
UPDATE speaker_pool SET status = 'ACCEPTED', content_status = 'APPROVED' WHERE status = 'SLOT_ASSIGNED';
UPDATE speaker_pool SET status = 'ACCEPTED', is_overflow = TRUE WHERE status = 'OVERFLOW';

-- Add constraint to enforce new valid states only
ALTER TABLE speaker_pool ADD CONSTRAINT chk_workflow_state
    CHECK (status IN ('IDENTIFIED', 'INVITED', 'ACCEPTED', 'DECLINED', 'CONFIRMED', 'WITHDREW'));
```

**Workflow + Content Status Combinations**:

| Workflow State | Content Status | Meaning | Next Action |
|----------------|----------------|---------|-------------|
| IDENTIFIED | — | Not yet invited | Organizer sends invitation |
| INVITED | — | Awaiting response | Speaker responds |
| ACCEPTED | PENDING | Accepted, no content yet | Speaker submits content |
| ACCEPTED | SUBMITTED | **Quality review queue** | Organizer reviews |
| ACCEPTED | REVISION_NEEDED | Changes requested | Speaker revises content |
| ACCEPTED | APPROVED | Content approved, ready | Wait for event finalization |
| CONFIRMED | APPROVED | In final agenda | Done |

**Note**: Quality review only happens when `content_status = SUBMITTED`. This is when the organizer:
1. Reviews **title and abstract** (required for submission)
2. Either approves (→ `APPROVED`) or requests revisions (→ `REVISION_NEEDED` with feedback)

**File upload is separate**: Presentation files (PPTX/PDF) can be uploaded anytime after acceptance and are tracked independently via `SessionMaterial`. Not required for quality review approval.

### 5. Automated Deadline Reminders

**Purpose**: Send configurable reminders before deadlines.

**Technical Design**:
```java
// Reminder Configuration
public record ReminderRule(
    EventType eventType,          // Conference, Workshop, etc.
    ReminderTier tier,            // TIER_1 (friendly), TIER_2 (urgent), TIER_3 (escalate)
    int daysBeforeDeadline,       // e.g., 30, 14, 3
    boolean autoEscalate,         // Escalate to organizer after TIER_3
    String emailTemplate          // Template name
) {}

// Scheduler Job
@Scheduled(cron = "0 0 9 * * *")  // Daily at 9 AM
public void processDeadlineReminders() {
    // 1. Find speakers with upcoming deadlines
    List<SpeakerDeadline> deadlines = findUpcomingDeadlines();

    // 2. Match against reminder rules
    for (SpeakerDeadline deadline : deadlines) {
        ReminderRule rule = findApplicableRule(deadline);
        if (rule != null && !alreadySent(deadline, rule)) {
            sendReminder(deadline, rule);
            logReminder(deadline, rule);
        }
    }
}
```

**Reminder Templates**:
- `reminder-tier1.html` - Friendly reminder (30 days before)
- `reminder-tier2.html` - Urgent reminder (14 days before)
- `reminder-tier3.html` - Final reminder with escalation warning (3 days before)

---

## Data Architecture

### Entity Relationships (Simplified Model)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    SPEAKER DOMAIN MODEL (6-State Workflow)                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌──────────────┐        ┌──────────────────┐        ┌──────────────────┐  │
│  │    User      │◄───────│     Speaker      │        │   SpeakerPool    │  │
│  │ (Company Svc)│   via  │ (Global Profile) │        │ (Event-specific) │  │
│  └──────────────┘ username└──────────────────┘        └────────┬─────────┘  │
│        │                        │                              │            │
│        ▼                        ▼                              │            │
│  ┌──────────────┐        ┌──────────────────┐                  │            │
│  │ Profile Data │        │ Speaker-specific │                  │            │
│  │ - name       │        │ - expertise[]    │                  │            │
│  │ - email      │        │ - topics[]       │                  │            │
│  │ - bio        │        │ - languages[]    │                  │            │
│  │ - photo      │        │ - linkedin       │                  │            │
│  └──────────────┘        └──────────────────┘                  │            │
│                                                                │            │
│  ┌─────────────────────────────────────────────────────────────┘            │
│  │                                                                          │
│  │  SpeakerPool (Event-Speaker Association)                                 │
│  │  ┌────────────────────────────────────────────────────────────────────┐  │
│  │  │ workflow_state: IDENTIFIED|INVITED|ACCEPTED|DECLINED|CONFIRMED|WITHDREW│
│  │  │ content_status: PENDING|SUBMITTED|APPROVED|REVISION_NEEDED         │  │
│  │  │ is_overflow: boolean, is_tentative: boolean, tentative_reason: text│  │
│  │  │ response_deadline, content_deadline                                │  │
│  │  │ invited_at, accepted_at, declined_at, withdrew_at, confirmed_at    │  │
│  │  │ content_submitted_at, content_approved_at, content_approved_by     │  │
│  │  └────────────────────────────────────────────────────────────────────┘  │
│  │           │                              │                               │
│  │           ▼                              ▼                               │
│  │  ┌──────────────────┐           ┌──────────────────┐                    │
│  │  │ ContentSubmission│           │  SessionMaterial │                    │
│  │  │ - title          │           │  - file (S3)     │                    │
│  │  │ - abstract       │           │  - type (PPTX,   │                    │
│  │  │ - version        │           │    PDF, VIDEO)   │                    │
│  │  │ - reviewer_      │           │  - cloudfront_url│                    │
│  │  │   feedback       │           └──────────────────┘                    │
│  │  └──────────────────┘                                                   │
│  │                                                                          │
│  └──────────────────────────────────────────────────────────────────────────┘
│                                                                             │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │                    Magic Link Tokens                                 │   │
│  │  ┌────────────────────────────────────────────────────────────────┐  │   │
│  │  │ speaker_invitation_tokens                                      │  │   │
│  │  │ - token_hash (SHA-256)                                         │  │   │
│  │  │ - speaker_pool_id                                              │  │   │
│  │  │ - action: RESPOND|SUBMIT|VIEW                                  │  │   │
│  │  │ - expires_at, used_at                                          │  │   │
│  │  └────────────────────────────────────────────────────────────────┘  │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Key Design Decisions

1. **6 Workflow States** - Simplified from 11 states for clearer mental model
2. **Content Status Decoupled** - Allows multiple revision cycles without state changes
3. **Timestamps per State** - Track when each transition occurred
4. **Overflow as Flag** - Boolean instead of separate state, applied to ACCEPTED speakers
5. **Versioned Submissions** - Support multiple content revisions with reviewer feedback

### Speaker-User Assignment

**How is a Speaker linked to a User?**

```
┌─────────────────────────────────────────────────────────────────┐
│                    SPEAKER-USER RELATIONSHIP                    │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Speaker.username ──references──▶ User.username                 │
│  (Event Service)                  (Company Service)             │
│                                                                 │
│  - No database FK (cross-service, ADR-004)                      │
│  - User data fetched via HTTP enrichment                        │
│  - username is the meaningful identifier (ADR-003)              │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

**Speaker Assignment Flow (for Self-Service Portal):**

```
┌─────────────────────────────────────────────────────────────────┐
│              INVITE SPEAKER (Auto-Create User)                  │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  1. Organizer enters speaker email (+ optional name)            │
│                                                                 │
│  2. System checks: Does User with this email exist?             │
│     ├─ YES: Link SpeakerPool to existing username               │
│     └─ NO:  Create User with role=SPEAKER                       │
│             └─ Generate username from email (john.doe@x.com     │
│                → john.doe or john.doe.2 if taken)               │
│                                                                 │
│  3. Create SpeakerPool entry:                                   │
│     - event_id: current event                                   │
│     - username: linked user                                     │
│     - workflow_state: IDENTIFIED                                │
│                                                                 │
│  4. Send invitation email with magic link                       │
│     - workflow_state → INVITED                                  │
│                                                                 │
│  5. Speaker clicks link, can:                                   │
│     - Complete profile (updates User entity)                    │
│     - Accept/Decline (updates SpeakerPool)                      │
│     - Submit content (creates ContentSubmission)                │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

**User Creation on Invite:**

```java
// When organizer invites a speaker by email
public SpeakerPool inviteSpeaker(String eventId, String email, String name) {
    // 1. Find or create User
    User user = userService.findByEmail(email)
        .orElseGet(() -> userService.createSpeakerUser(email, name));

    // 2. Create SpeakerPool entry
    SpeakerPool speakerPool = SpeakerPool.builder()
        .eventId(eventId)
        .username(user.getUsername())  // Link via username
        .workflowState(IDENTIFIED)
        .build();

    return speakerPoolRepository.save(speakerPool);
}

// User creation for new speakers
public User createSpeakerUser(String email, String name) {
    String username = generateUsername(email);  // john.doe@x.com → john.doe

    return User.builder()
        .username(username)
        .email(email)
        .firstName(extractFirstName(name))
        .lastName(extractLastName(name))
        .role(Role.SPEAKER)
        .status(UserStatus.PENDING)  // Activated on first portal access
        .build();
}
```

**Benefits of Auto-Create:**
- Organizer just needs email to invite anyone
- Speaker completes profile themselves via portal
- No manual user creation step
- Consistent model (all speakers have User accounts)

### API Design (ADR-003 Compliance)

All APIs use **meaningful identifiers**:
- Speaker: `username` (not UUID)
- Event: `eventCode` (not UUID)
- Session: `sessionSlug` (not UUID)

---

## Security Considerations

### Magic Link Security

| Concern | Mitigation |
|---------|------------|
| Token theft | Short expiry (30 days), single-use for state changes |
| Replay attacks | Token invalidated after sensitive actions |
| Enumeration | Rate limiting (5 req/min), no error differentiation |
| MITM | HTTPS-only, HSTS enabled |
| Phishing | Clear sender identity, consistent branding |

### RBAC for Speaker Portal

```java
// Speaker can only access their own data
@PreAuthorize("@speakerPortalSecurity.validateToken(#token)")
public ResponseEntity<?> getProfile(@RequestParam String token) {
    // Token validation extracts speakerId
    // Only returns data for that speaker
}

// Organizer override for assistance
@PreAuthorize("hasRole('ORGANIZER')")
public ResponseEntity<?> getAnyProfile(@PathVariable String username) {
    // Full access for organizers
}
```

---

## Implementation Phases

### Phase 1: Foundation (Week 1-2)

**Story 6.1a: Magic Link Infrastructure**
- [ ] Create `speaker_invitation_tokens` table (Flyway V43)
- [ ] Implement `MagicLinkService` for token generation/validation
- [ ] Add token endpoints to API Gateway routing
- [ ] Unit + integration tests with PostgreSQL

**Story 6.1b: Email Template System**
- [ ] Create email templates (invitation, reminder tiers)
- [ ] Extend `EmailService` with template rendering
- [ ] Add email tracking (sent, opened, clicked)
- [ ] AWS SES configuration for sender identity

### Phase 2: Response Portal (Week 3-4)

**Story 6.2a: Invitation Response**
- [ ] `InvitationResponsePage.tsx` - Accept/Decline/Tentative UI
- [ ] `POST /speaker-portal/respond` endpoint
- [ ] Status update integration with SpeakerPool
- [ ] Organizer notification on response

**Story 6.2b: Profile Update**
- [ ] `ProfileUpdatePage.tsx` - Edit bio, photo, expertise
- [ ] Photo upload via existing FileUpload component
- [ ] Profile validation (required fields)
- [ ] Sync with User entity (Company Service)

### Phase 3: Content Submission (Week 5-6)

**Story 6.3a: Title & Abstract**
- [ ] `ContentSubmissionPage.tsx` - Multi-step wizard
- [ ] Abstract validation (1000 char max, quality hints)
- [ ] Draft auto-save (localStorage + server)
- [ ] Quality review workflow trigger

**Story 6.3b: File Upload**
- [ ] Reuse `MaterialUpload` from Story 5.9
- [ ] Speaker-specific S3 path: `materials/{year}/events/{eventCode}/speakers/{username}/`
- [ ] File type validation (PPTX, PDF, KEY)
- [ ] Progress indicator and retry handling

### Phase 4: Dashboard & Reminders (Week 7-8)

**Story 6.4: Speaker Dashboard**
- [ ] `SpeakerDashboardPage.tsx` - Upcoming/past events
- [ ] Material status overview
- [ ] Organizer contact information
- [ ] Mobile-responsive design

**Story 6.5: Automated Reminders**
- [ ] `ReminderSchedulerJob` with configurable rules
- [ ] Reminder logging and deduplication
- [ ] Escalation to organizer workflow
- [ ] Manual reminder override option

---

## Integration Points

### Existing Services

| Service | Integration | Pattern |
|---------|-------------|---------|
| Company User Management | User data enrichment | HTTP client (ADR-004) |
| Event Management | Session/Event data | Direct (same service) |
| AWS SES | Email sending | Shared-kernel EmailService |
| AWS S3 | File storage | Presigned URLs (ADR-002) |
| CloudFront | File delivery | CDN distribution |

### Domain Events

```java
// Domain Events for Epic 6 (Simplified 6-State Model)

// Workflow State Changes
public record SpeakerWorkflowStateChangedEvent(
    String speakerPoolId,
    String username,
    String eventCode,
    String sessionId,
    WorkflowState previousState,  // IDENTIFIED, INVITED, ACCEPTED, DECLINED, CONFIRMED, WITHDREW
    WorkflowState newState,
    String changedBy,
    Instant changedAt
) implements DomainEvent {}

// Content Status Changes (separate from workflow)
public record SpeakerContentStatusChangedEvent(
    String speakerPoolId,
    String username,
    String eventCode,
    ContentStatus previousStatus,  // PENDING, SUBMITTED, APPROVED, REVISION_NEEDED
    ContentStatus newStatus,
    String changedBy,
    String feedback,  // Optional: reviewer feedback for REVISION_NEEDED
    Instant changedAt
) implements DomainEvent {}

// Specific Events for Automation
public record SpeakerInvitationSentEvent(
    String speakerPoolId,
    String username,
    String email,
    String eventCode,
    String sessionId,
    String magicLinkToken,
    Instant sentAt,
    Instant expiresAt
) implements DomainEvent {}

public record SpeakerResponseReceivedEvent(
    String speakerPoolId,
    String username,
    String eventCode,
    WorkflowState response,  // ACCEPTED or DECLINED
    String declineReason,    // Optional: if DECLINED
    Instant respondedAt
) implements DomainEvent {}

public record SpeakerContentSubmittedEvent(
    String speakerPoolId,
    String username,
    String eventCode,
    String sessionId,
    String submissionId,
    int submissionVersion,
    Instant submittedAt
) implements DomainEvent {}

public record SpeakerReminderSentEvent(
    String speakerPoolId,
    String username,
    String eventCode,
    ReminderTier tier,  // TIER_1, TIER_2, TIER_3
    ReminderType type,  // RESPONSE_DEADLINE, CONTENT_DEADLINE
    Instant sentAt
) implements DomainEvent {}

public record SpeakerOverflowDetectedEvent(
    String eventCode,
    long acceptedCount,
    int maxSlots,
    List<String> overflowSpeakers,  // usernames marked as overflow
    Instant detectedAt
) implements DomainEvent {}
```

### Workflow State Enum (Simplified)

```java
// Update shared-kernel: SpeakerWorkflowState.java
public enum SpeakerWorkflowState {
    /**
     * Speaker added to pool, not yet contacted.
     */
    IDENTIFIED,

    /**
     * Invitation email sent with magic link.
     * Speaker has been contacted and is awaiting response.
     */
    INVITED,

    /**
     * Speaker confirmed participation.
     * Can now submit content (profile, title, abstract, files).
     */
    ACCEPTED,

    /**
     * Speaker rejected invitation.
     * Terminal state - no further transitions.
     */
    DECLINED,

    /**
     * Speaker locked in final published agenda.
     * Terminal state - event is published.
     */
    CONFIRMED,

    /**
     * Speaker backed out after accepting.
     * Terminal state - need replacement speaker.
     */
    WITHDREW
}

// Content status tracked separately
public enum ContentStatus {
    PENDING,          // No content submitted yet
    SUBMITTED,        // Content awaiting review
    APPROVED,         // Content approved by organizer
    REVISION_NEEDED   // Organizer requested changes
}
```

---

## Testing Strategy

### Test Categories

| Category | Coverage Target | Tools |
|----------|-----------------|-------|
| Unit Tests | >85% | JUnit 5, Mockito |
| Integration Tests | >80% | Testcontainers (PostgreSQL) |
| E2E Tests | Critical paths | Playwright, Bruno |
| Security Tests | OWASP Top 10 | OWASP ZAP, manual review |

### Key Test Scenarios

1. **Magic Link Flow**: Generate → Send → Validate → Expire
2. **Response Flow**: Receive email → Click link → Submit response → Notification sent
3. **Content Flow**: Login → Edit profile → Submit content → Upload files → Review
4. **Reminder Flow**: Configure rules → Scheduler runs → Email sent → Not duplicated

---

## Success Metrics

| Metric | Target | Measurement |
|--------|--------|-------------|
| Self-service adoption | 70%+ | Speakers using portal vs manual |
| Response time | <3 days avg | Time from invitation to response |
| Material submission | <1 week avg | Time from acceptance to content submitted |
| Organizer workload reduction | 40% | Hours spent on speaker coordination |

---

## Design Decisions (Resolved)

| # | Question | Decision | Rationale |
|---|----------|----------|-----------|
| 1 | **Profile Sync** | Sync to User entity | Single source of truth (ADR-004). Profile changes apply to all events. |
| 2 | **TENTATIVE Response** | Flag on INVITED state | Keeps 6 states. `is_tentative=true` + `tentative_reason` track "maybe" responses. |
| 3 | **Session Assignment** | At invite or later | Flexible: invite with session OR assign after acceptance. Content blocked until assigned. |
| 4 | **Migration Strategy** | V45 migrates old states | Maps 11 states to 6 states with content_status and is_overflow flags. |

## Open Questions for Discussion

1. **Magic Link Expiry**: Should tokens expire after 30 days or 60 days? What's the typical speaker response window?
   - Current default: 30 days

2. **Quality Review Automation**: Should we implement AI-assisted abstract quality checking (length, promotional content detection)?
   - Recommendation: Defer to future enhancement, start with manual review

3. **Hybrid Mode**: How should the system behave when some speakers use portal and others prefer email/phone communication?
   - **Decision**: Organizer can manually transition IDENTIFIED → INVITED → ACCEPTED for speakers who respond offline
   - Manual transitions allowed for organizers via existing status management UI

4. **Undo Declined**: If a speaker accidentally declines, can they re-accept?
   - **Decision**: Organizer can transition DECLINED → INVITED to re-send invitation
   - Speaker receives new magic link email

5. **Overflow Handling**: When a speaker is marked as overflow, should they be automatically promoted if another speaker withdraws?
   - **Decision**: Send notification to organizer, let them decide manually
   - Organizer sets `is_overflow=false` to promote speaker

---

## Next Steps

1. **Review this plan** with product owner and development team
2. **Prioritize stories** based on MVP requirements
3. **Create detailed story specs** following template in `/docs/templates/`
4. **Set up feature branch** `feature/epic-6-speaker-portal`
5. **Begin Phase 1** implementation with TDD approach

---

## References

- Epic 6 PRD: `docs/prd/epic-6-speaker-portal-support.md`
- Story 6.0 (Foundation): `docs/stories/6.0.speaker-profile-foundation.md`
- Story 5.9 (Materials): `docs/stories/5.9-session-materials-upload.md`
- Speaker API Design: `docs/architecture/04-api-speaker-coordination.md`
- ADR-002 (File Uploads): `docs/architecture/ADR-002-generic-file-upload.md`
- ADR-003 (Meaningful IDs): `docs/architecture/ADR-003-meaningful-identifiers-public-apis.md`
- ADR-004 (User Fields): `docs/architecture/ADR-004-factor-user-fields-from-domain-entities.md`
