# Epic 5: Complete Event Management Workflow

**Status:** 🔄 **IN PROGRESS** - Phase B Complete (4/8 stories, 50%)

**Last Updated:** 2025-12-20 (Workflow Consolidation Complete)

**Workflow Redesign (2025-12-19):** Epic 5 has been redesigned from a linear 16-step workflow to a parallel workflow architecture with 9 event states, per-speaker workflows, and configurable task management. This reflects the actual implementation reality discovered during Stories 5.1-5.4.

**Key Architectural Changes:**
- **Event Workflow:** Simplified to 9 high-level states (down from 16 linear steps)
- **Speaker Workflow:** Per-speaker state machine with parallel quality review and slot assignment
- **Task System:** Configurable tasks (newsletters, catering, partner meetings) separate from workflow states
- **Organizer-Driven:** Manual speaker coordination without requiring speaker self-service portal

**Progress Summary:**
- ✅ **Phase A: Event Setup** - COMPLETE (2 stories + 2 sub-stories)
  - ✅ 5.1 - Event Type Definition (COMPLETE)
  - ✅ 5.1a - Workflow State Machine Foundation (COMPLETE - CRITICAL DEPENDENCY)
  - ✅ 5.2 - Topic Selection & Speaker Brainstorming (COMPLETE)
  - ✅ 5.2b - Multi-Topic Heat Map Visualization (COMPLETE)
- ✅ **Phase B: Speaker Coordination** - COMPLETE (2 stories)
  - ✅ 5.3 - Speaker Outreach Tracking (COMPLETE)
  - ✅ 5.4 - Speaker Status Management (COMPLETE - QA PASS 95/100)
- 🔄 **Phase C: Overflow & Slot Management** - PENDING (3 stories)
  - 🔄 5.5 - Content Submission, Quality Review & Task System (IN PROGRESS)
  - ⏳ 5.6 - Overflow Management & Voting (PENDING - Documented)
  - ⏳ 5.7 - Slot Assignment & Progressive Publishing (PENDING - Documented)
- ⏳ **Phase D: Event Lifecycle** - PENDING (1 story)
  - ⏳ 5.8 - Agenda Finalization & Event Lifecycle (PENDING - Documented)

---

## Epic Overview

**Epic Goal**: Implement a complete event management workflow with parallel speaker coordination and configurable task management, enabling organizers to manage the entire event lifecycle from topic selection through event completion.

**Deliverable**: Integrated workflow system with:
- **Event Workflow**: 9-state state machine for event lifecycle management
- **Speaker Workflow**: Per-speaker state machine with parallel quality review and slot assignment
- **Task System**: Configurable task templates for organizer coordination (newsletters, catering, partner meetings, etc.)

**Architecture Context**:
- **Core Service**: Event Management Service (Java 21 + Spring Boot 3.2)
- **Data Model**:
  - `speaker_pool`: Potential speakers with workflow states
  - `sessions`: Time slots with presentation title/abstract
  - `session_users`: Junction table linking speakers (username) to sessions
  - `task_templates` & `event_tasks`: Configurable task management
- **Frontend**: React components for organizer dashboard and workflow management
- **Publishing**: Progressive publishing engine (topic → speakers → agenda)
- **Infrastructure**: AWS SES for newsletters, CloudFront CDN, Caffeine caching
- **Database**: PostgreSQL with workflow state tracking

**Duration**: 8 weeks (reduced from 16 weeks through workflow redesign and consolidation)

**Dependencies**:
- Epic 2 complete (CRUD APIs operational)
- Story 2.7 complete (Partner Coordination Service)
- Story 5.1 complete (Event Type Definition)
- **Story 5.1a complete (Workflow State Machine Foundation) ← CRITICAL DEPENDENCY**

**Key Architectural Insights**:
- **Not a Linear Workflow**: The original "16-step workflow" was a misconception. Events progress through high-level states while speakers progress through per-speaker workflows in parallel.
- **Tasks ≠ Workflow States**: Activities like newsletters, catering, partner meetings are assignable tasks with due dates, not workflow states.
- **Parallel Progression**: Quality review and slot assignment can happen in any order per speaker; event progresses when all speakers reach required states.

---

## Redesigned Workflow Architecture

### Event Workflow (9 States)

High-level event lifecycle states:

```
CREATED → TOPIC_SELECTION → SPEAKER_IDENTIFICATION → SLOT_ASSIGNMENT →
AGENDA_PUBLISHED → AGENDA_FINALIZED → EVENT_LIVE → EVENT_COMPLETED → ARCHIVED
```

**State Transitions:**
- `CREATED → TOPIC_SELECTION`: When topic selected
- `TOPIC_SELECTION → SPEAKER_IDENTIFICATION`: When minimum speakers added to pool (per event type)
- `SPEAKER_IDENTIFICATION → SLOT_ASSIGNMENT`: When all slots filled (after overflow voting if needed)
- `SLOT_ASSIGNMENT → AGENDA_PUBLISHED`: When agenda published
- `AGENDA_PUBLISHED → AGENDA_FINALIZED`: Manual (2 weeks before event)
- `AGENDA_FINALIZED → EVENT_LIVE`: Automatic (event day)
- `EVENT_LIVE → EVENT_COMPLETED`: Manual (after event)
- `EVENT_COMPLETED → ARCHIVED`: Manual

### Speaker Workflow (Per Speaker - Parallel)

Each speaker progresses through their own workflow:

```
identified → contacted → ready → accepted/declined
                                    ↓ (if accepted)
                            content_submitted
                                    ↓
                            quality_reviewed
                                    ↓
                               confirmed
```

**Slot Assignment (Orthogonal Action):**
- Sets `sessions.start_time` (NOT a workflow state)
- Can happen at any point after ACCEPTED
- Auto-confirmation triggers when: speaker reaches QUALITY_REVIEWED state AND session.start_time != null

**Key States (10 total):**
- `identified`: Added to speaker pool
- `contacted`: Organizer recorded outreach
- `ready`: Speaker is ready to accept/decline
- `accepted`/`declined`: Speaker decision
- `content_submitted`: Presentation title/abstract submitted
- `quality_reviewed`: Content approved by moderator
- `confirmed`: Auto-updated when quality_reviewed AND session.start_time != null (slot assigned)
- `overflow`: Backup speaker (accepted but no slot available)
- `withdrew`: Speaker drops out after accepting

**Note:** `slot_assigned` is NOT a state - it's an orthogonal action that sets `sessions.start_time`

**Stored in:** `speaker_pool.status` column

### Task System (Configurable)

Tasks are assignable work items with due dates, NOT workflow states:

**Default Task Templates:**
1. Venue Booking (trigger: TOPIC_SELECTION, due: 90 days before event)
2. Partner Meeting (trigger: TOPIC_SELECTION, due: same day as event)
3. Moderator Assignment (trigger: TOPIC_SELECTION, due: 14 days before event)
4. Newsletter: Topic (trigger: TOPIC_SELECTION, due: immediate)
5. Newsletter: Speakers (trigger: AGENDA_PUBLISHED, due: 30 days before event)
6. Newsletter: Final (trigger: AGENDA_FINALIZED, due: 14 days before event)
7. Catering (trigger: AGENDA_FINALIZED, due: 30 days before event)

**Organizers can:**
- Add custom tasks when creating event
- Define trigger state and due date for each task
- Assign tasks to specific organizers
- Save custom tasks as templates for reuse

**Stored in:** `task_templates` and `event_tasks` tables

---

## Workflow Mapping to Stories

| Story | Covers | Event States | Speaker States | Tasks |
|-------|--------|--------------|----------------|-------|
| **5.1** | Event Type Definition | CREATED | - | - |
| **5.1a** | Workflow State Machine | All 9 states | All 10 states | - |
| **5.2** | Topic Selection & Brainstorming | TOPIC_SELECTION | identified | - |
| **5.3** | Speaker Outreach | - | contacted | - |
| **5.4** | Speaker Status Management | - | ready, accepted, declined | - |
| **5.5** | Content, Quality Review & Tasks | - | content_submitted, quality_reviewed, confirmed | Task templates + event tasks (separate from workflow states) |
| **5.6** | Overflow & Voting | - | overflow | - |
| **5.7** | Slot Assignment & Publishing | SLOT_ASSIGNMENT, AGENDA_PUBLISHED | [Orthogonal slot assignment action] | Newsletter tasks |
| **5.8** | Finalization & Lifecycle | AGENDA_FINALIZED, EVENT_LIVE, EVENT_COMPLETED, ARCHIVED | withdrew | Catering task |

**Note on Tasks:** Tasks (newsletters, catering, partner meetings, etc.) are NOT workflow states. They are assignable work items with due dates, stored in separate tables (task_templates, event_tasks), and auto-created when events reach trigger states. They exist independently from the event and speaker workflow state machines.

**Old Mapping (16 Stories) → New Mapping (8 Stories):**
- Stories 5.1-5.4: ✅ Already implemented (no changes)
- Stories 5.5-5.7 (old) → Story 5.5 (new): Content submission + quality review + task system
- Stories 5.8-5.9 (old) → Story 5.6 (new): Overflow management + voting
- Stories 5.10-5.11 (old) → Story 5.7 (new): Slot assignment + progressive publishing
- Stories 5.12-5.15 (old) → Task system in Story 5.5: Newsletters, catering, etc. become configurable tasks

---

## Phase A: Event Setup (Stories 5.1, 5.1a, 5.2)

### Story 5.1: Event Type Definition (Workflow Step 1 - Partial)

**Status:** ✅ **COMPLETE** (2025-12-19)

**User Story:**
As an **organizer**, I want to define event types with slot requirements, so that I can create events tailored to our different format requirements (full-day, afternoon, evening).

**Architecture Integration:**
- **Service**: Event Management Service
- **Database**: PostgreSQL event_types and event_slots tables
- **Frontend**: React event type configuration component
- **Cache**: Caffeine in-memory cache for event type templates

**Acceptance Criteria:**

**Event Type Configuration:**
1. ✅ **Event Type Definition**: Configure three event types:
   - Full-day: 6-8 slots (9:00-17:00)
   - Afternoon: 6-8 slots (13:00-18:00)
   - Evening: 3-4 slots (18:00-21:00)
2. ✅ **Slot Requirements**: Define minimum/maximum slots per event type
3. ✅ **Timing Templates**: Create reusable timing templates for each format
4. ✅ **Capacity Planning**: Set default attendee capacity based on event type

**Technical Implementation:**
5. ✅ **Event Type Entity**: Create EventType aggregate with validation rules
6. ✅ **REST API**: POST/GET/PUT /api/events/types endpoints
7. ✅ **React Component**: EventTypeSelector with template preview
8. ✅ **Validation**: Ensure slot counts match event type requirements

**Implementation Evidence:**
- `EventSlotConfigurationResponse.java` - Event slot configuration DTOs
- `UpdateEventSlotConfigurationRequest.java` - Event type update API
- Event Management Service implements full event type management
- Integration with Story 5.1a workflow state machine

**Definition of Done:**
- [x] Event type configuration supports all three formats
- [x] Timing templates automatically populate slot times
- [x] API endpoints fully documented in OpenAPI spec
- [x] Frontend component validates slot requirements
- [x] Unit tests cover all event type scenarios
- [x] Integration test verifies event type creation

**Actual Duration:** 1 week

---

### Story 5.1a: Workflow State Machine Foundation (NEW - Technical Infrastructure)

**Status:** ✅ **COMPLETE** (2025-12-19)

**User Story:**
As a **platform architect**, I want to implement the workflow state machine infrastructure, so that all Epic 5 stories can track event and speaker lifecycle states with proper validation and business rule enforcement.

**Architecture Integration:**
- **Service**: Event Management Service + Speaker Coordination Service
- **Database**: PostgreSQL with workflow_state columns in events and session_users tables
- **Shared Kernel**: EventWorkflowState and SpeakerWorkflowState enums
- **Domain Events**: EventWorkflowTransitionEvent, SpeakerWorkflowStateChangeEvent

**Story Reference:** Full specification at `docs/prd/story-5.1a-workflow-state-machine-foundation.md`

**Acceptance Criteria:**

**Phase 1: State Enums (Day 1)**
1. **EventWorkflowState Enum**: 9 states for high-level event lifecycle (CREATED → ARCHIVED)
2. **SpeakerWorkflowState Enum**: 10 states for speaker workflow (IDENTIFIED, CONTACTED, READY, ACCEPTED, DECLINED, CONTENT_SUBMITTED, QUALITY_REVIEWED, CONFIRMED, OVERFLOW, WITHDREW)
3. **Domain Events**: EventWorkflowTransitionEvent, SpeakerWorkflowStateChangeEvent
4. **Unit Tests**: Verify enum values and event serialization

**Phase 2: Event Workflow State Machine (Days 2-3)**
5. **EventWorkflowStateMachine Service**: Core orchestrator with transitionToState() method
6. **Validation Logic**: validateMinimumSpeakersInPool(), validateAllSlotsHaveSpeakers(), validateAllSpeakersConfirmed(), validateAgendaPublished(), validateEventDateInFuture(), validateEventDateInPast()
7. **WorkflowTransitionValidator**: State transition matrix and business rule enforcement
8. **Database Migration**: Add workflow_state column to events table with index
9. **JPA Converter**: EventWorkflowStateConverter for enum ↔ VARCHAR conversion
10. **Event Entity Update**: Change status field to workflowState with proper annotations
11. **Unit Tests**: >90% coverage for state machine logic
12. **Integration Tests**: Complete workflow sequence with Testcontainers PostgreSQL

**Phase 3: Speaker Workflow Service (Days 4-5)**
13. **SpeakerWorkflowService**: Speaker state management with updateSpeakerWorkflowState() method
14. **State-Specific Logic**: CONTACTED (set timestamp, send notification), ACCEPTED (check overflow), DECLINED (handle decline)
    - **Note:** Slot assignment is handled separately by SlotAssignmentService (orthogonal action, not a state transition)
15. **Database Migration**: Add workflow_state column to session_users table with index
16. **SessionUser Update**: Add workflowState field, keep existing fields for backwards compatibility
17. **Overflow Detection**: checkForOverflow() detects when speakers exceed max slots
18. **Unit Tests**: >90% coverage for speaker state transitions
19. **Integration Tests**: Complete speaker workflow with overflow detection

**Phase 4: REST API Integration (Day 6)**
20. **EventWorkflowController**: PUT /api/v1/events/{code}/workflow/transition, GET /api/v1/events/{code}/workflow/status
21. **SpeakerWorkflowController**: PUT /api/v1/events/{id}/speakers/{id}/status
22. **OpenAPI Specs**: Update event-management-api.openapi.yml and speaker-coordination-api.openapi.yml
23. **Integration Tests**: API endpoints with success/error scenarios

**Phase 5: Frontend Integration (Day 7)**
24. **WorkflowProgressBar Update**: Connect to backend API, display current state, show validation blockers
25. **SpeakerStatusDashboard Component**: Kanban-style lanes for speaker states with drag-and-drop
26. **TypeScript Generation**: Run npm run generate:api-types
27. **Manual Testing**: Verify UI updates in real-time

**Phase 6: E2E Testing (Day 7)**
28. **Integration Test**: Complete workflow transition sequence CREATED → ARCHIVED
29. **Bruno API Tests**: bruno-tests/workflows/event-workflow-transitions.bru with 5+ test cases
30. **Performance Testing**: State transitions <200ms P95, workflow status <100ms P95

**Implementation Evidence:**
- `EventWorkflowState.java` - Event workflow state enum with 9 states
- `TransitionStateRequest.java` - State transition API request DTO
- `WorkflowStatusDto.java` - Workflow status response DTO
- Event Management Service implements EventWorkflowStateMachine
- SpeakerWorkflowService with state management
- Integration with Stories 5.2, 5.3, 5.4 for workflow transitions

**Definition of Done:**
- [x] EventWorkflowState and SpeakerWorkflowState enums operational
- [x] EventWorkflowStateMachine service with full validation
- [x] SpeakerWorkflowService with state management
- [x] REST APIs operational and documented
- [x] Database migrations applied successfully
- [x] Frontend components connected and working
- [x] Unit tests >90%, integration tests >80%
- [x] All Bruno API tests pass
- [x] Performance requirements met

**Actual Duration:** 1 week (7 days)

**CRITICAL NOTE**: This story is a **dependency for ALL Stories 5.2-5.15**. Without the state machine, subsequent stories cannot track workflow progression, validate transitions, or enforce business rules. ✅ FOUNDATION COMPLETE - All subsequent stories can now build on this infrastructure.

---

### Story 5.2: Topic Selection & Speaker Brainstorming (Workflow Steps 1-3)

**Status:** ✅ **COMPLETE** (2025-12-19)

**User Story:**
As an **organizer**, I want to select topics from our backlog with intelligent suggestions and brainstorm potential speakers, so that I can plan compelling events with appropriate speaker assignments.

**Architecture Integration:**
- **Service**: Event Management Service with topic management
- **Database**: PostgreSQL topics table with usage history, speaker_pool table
- **AI/ML**: Basic similarity detection using PostgreSQL full-text search
- **Frontend**: React topic selector with search, suggestions, and speaker brainstorming interface

**Wireframe Reference:**
- `docs/wireframes/story-2.2-topic-backlog-management.md` ✅

**Acceptance Criteria:**

**Topic Selection (Step 1-2):**
1. **Topic Backlog**: Display searchable list of all available topics
2. **Heat Map Visualization**: Display topic usage heat map showing frequency over last 24 months
3. **Color-Coded Freshness**: Topics color-coded by staleness (red: <6 months, yellow: 6-12 months, green: >12 months)
4. **Similarity Scoring**: Calculate similarity scores between topics using TF-IDF and cosine similarity
5. **Duplicate Warnings**: Automatic warnings when similarity score >70% with recent topics
6. **Staleness Score**: Display 0-100 staleness score (100 = safe to reuse, 0 = too recent)
7. **Override Capability**: Allow organizers to override warnings with justification
8. **Topic Creation**: Allow new topic creation with description inline

**Speaker Brainstorming (Step 2-3):**
9. **Speaker Pool**: Organizers can add potential speakers to event (name, company, expertise)
10. **Speaker Notes**: Free-text notes field for each potential speaker
11. **Assignment Strategy**: Assign speakers to specific organizers for outreach
12. **Contact Distribution**: Track which organizer will contact which speaker
13. **Speaker Status**: Initial status = "OPEN" (not yet contacted)

**Workflow Engine Integration:**
14. **Event State Transition**: When topic selected, call `eventWorkflowStateMachine.transitionToState(eventId, EventWorkflowState.TOPIC_SELECTION, organizerId)`
15. **Speaker Pool State**: When speaker added to pool, call `speakerWorkflowService.updateSpeakerWorkflowState(sessionId, speakerId, SpeakerWorkflowState.IDENTIFIED, organizerId)`
16. **Validation**: Ensure event can transition to TOPIC_SELECTION state before allowing topic selection

**Technical Implementation:**
17. **Topic Entity Enhancement**: Add staleness tracking, similarity scores, usage history
18. **Speaker Pool Table**: event_speaker_pool (event_id, speaker_name, company, expertise, assigned_organizer_id, status, notes)
19. **REST API**: GET /api/topics, POST /api/events/{id}/speakers/pool
20. **React Components**: TopicSelector, SpeakerBrainstormingPanel
21. **Domain Events**: TopicSelectedEvent, SpeakerAddedToPoolEvent

**Implementation Evidence:**
- Story file: `docs/stories/5.2-topic-selection-speaker-brainstorming.md` (Status: Done)
- Full topic backlog management with heat map visualization
- Speaker pool management in event-management-service
- Integration with Story 5.1a workflow state machine

**Definition of Done:**
- [x] Topic backlog displays all historical topics with heat map
- [x] Similarity scoring detects topics with >70% similarity
- [x] Staleness score calculation accurate based on usage patterns
- [x] Organizers can override warnings with recorded justification
- [x] Speaker brainstorming panel allows adding potential speakers
- [x] Organizers can assign speakers to other organizers for outreach
- [x] Speaker pool tracked in database with status
- [x] Recharts heat map renders in <500ms

**Actual Duration:** 2 weeks

---

## Phase B: Speaker Outreach & Coordination (Stories 5.3-5.5)

### Story 5.3: Speaker Outreach Tracking (Workflow Step 4)

**Status:** ✅ **COMPLETE** (2025-12-19)

**User Story:**
As an **organizer**, I want to track my speaker outreach activities, so that I can coordinate with other organizers and ensure all speakers are contacted.

**Organizer-Driven Approach**: Organizers manually contact speakers via email/phone/in-person. The system provides UI to record outreach activities.

**Architecture Integration:**
- **Service**: Speaker Coordination Service
- **Database**: speaker_outreach_history table
- **Frontend**: React outreach tracking interface

**Acceptance Criteria:**

1. **Outreach Dashboard**: List of all speakers in pool with outreach status
2. **Mark as Contacted**: Button to mark speaker as "CONTACTED" with date/time
3. **Contact Notes**: Free-text field to record contact method and conversation notes
4. **Contact History**: Timeline of all outreach attempts per speaker
5. **Assigned Organizer Filter**: Filter speakers by assigned organizer
6. **Bulk Actions**: Mark multiple speakers as contacted at once
7. **Reminder System**: Show speakers not yet contacted with days elapsed since assignment

**Workflow Engine Integration:**
8. **State Transition**: When speaker marked as contacted, call `speakerWorkflowService.updateSpeakerWorkflowState(sessionId, speakerId, SpeakerWorkflowState.CONTACTED, organizerId)`
9. **Validation**: Ensure speaker is in IDENTIFIED or OPEN state before allowing transition to CONTACTED
10. **Event Listener**: Listen for SpeakerWorkflowStateChangeEvent to update UI with real-time status changes

**Technical Implementation:**
11. **Outreach History Entity**: Track date, method (email/phone/in-person), notes, organizer
12. **REST API**: POST /api/events/{id}/speakers/{speakerId}/outreach
13. **React Component**: SpeakerOutreachDashboard with inline editing
14. **Domain Event**: SpeakerContactedEvent

**Implementation Evidence:**
- `RecordOutreachRequest.java` - Outreach recording API request DTO
- `OutreachHistoryResponse.java` - Outreach history response DTO
- `SpeakerOutreachControllerIntegrationTest.java` - Integration tests for outreach tracking
- Event Management Service implements full outreach tracking
- Integration with Story 5.1a workflow state machine (CONTACTED state)

**Definition of Done:**
- [x] Organizers can mark speakers as contacted with notes
- [x] Contact history displays all past outreach attempts
- [x] Filter speakers by assigned organizer working
- [x] Reminder system shows overdue outreach tasks
- [x] Bulk action to mark multiple speakers contacted
- [x] Integration test verifies outreach tracking

**Actual Duration:** 1 week

---

### Story 5.4: Speaker Status Management (Workflow Step 5)

**Status:** ✅ **COMPLETE** - QA PASS (95/100) (2025-12-19)

**User Story:**
As an **organizer**, I want to track speaker status transitions, so that I know which speakers have accepted, declined, or are pending response.

**Architecture Integration:**
- **Service**: Speaker Coordination Service
- **Database**: Speaker status tracked in speakers table
- **Frontend**: React status dashboard with workflow visualization

**Acceptance Criteria:**

**Status Workflow:**
1. **Status Transitions**: OPEN → CONTACTED → READY → ACCEPTED/DECLINED
2. **Manual Status Updates**: Organizer manually updates status via dropdown
3. **Status Change Timestamp**: Track when each status change occurred
4. **Status Change Reason**: Optional notes field for status changes
5. **Status Dashboard**: Visual dashboard showing speakers by status
6. **Acceptance Rate Tracking**: Show % of speakers who accepted vs declined

**Status Indicators:**
7. **Color Coding**: Visual color coding for each status (green=accepted, red=declined, yellow=pending)
8. **Progress Bar**: Overall event progress based on speaker statuses
9. **Notifications**: Notify organizers when speaker status changes (if multiple organizers)

**Workflow Engine Integration:**
10. **State Transition**: Manual status updates call `speakerWorkflowService.updateSpeakerWorkflowState(sessionId, speakerId, newState, organizerId)`
11. **Event Listener**: Listen for SpeakerWorkflowStateChangeEvent to update UI in real-time
12. **Validation**: Enforce valid state transitions (CONTACTED → READY → ACCEPTED/DECLINED)
13. **Acceptance Check**: When speaker moves to ACCEPTED, trigger overflow detection via EventWorkflowStateMachine

**Technical Implementation:**
14. **Status Enum**: SpeakerWorkflowState enum in shared-kernel (created in Story 5.1a)
15. **Status History Table**: Track all status transitions with timestamp, organizer, reason
16. **REST API**: PUT /api/events/{id}/speakers/{speakerId}/status
17. **React Component**: SpeakerStatusDashboard with drag-and-drop status lanes
18. **Domain Event**: SpeakerStatusChangedEvent

**Implementation Evidence:**
- Story file: `docs/stories/5.4-speaker-status-management.md` (Status: Done)
- QA Gate: `docs/qa/gates/5.4-speaker-status-management.yml` (PASS - 95/100)
- `SpeakerStatusHistory.java` - Status history tracking entity
- `SpeakerStatusService.java` - Status management service with caching
- `SpeakerStatusController.java` - REST API endpoints with security
- `StatusTransitionValidator.java` - State machine validation
- Event Management Service implements full status tracking
- 18/18 tests passing (100% success rate)
- All critical QA issues resolved (ARCH-001, SCHEMA-001, SCHEMA-002, PERF-001, IMPL-001, IMPL-002, TEST-001)

**Definition of Done:**
- [x] Status transitions follow workflow (OPEN → CONTACTED → READY → ACCEPTED/DECLINED)
- [x] Organizer can manually update status via UI
- [x] Status dashboard shows speakers grouped by status
- [x] Color coding and progress bar display correctly
- [x] Status change history tracked in database
- [x] Integration test verifies status workflow
- [x] QA review passed with 95/100 quality score

**Actual Duration:** 1 week

**QA Results:**
- **Quality Score:** 95/100 (EXCELLENT)
- **Test Coverage:** 18/18 tests passing (100%)
- **Security:** PASS (100%) - @PreAuthorize on all endpoints, username from SecurityContext
- **Performance:** PASS (95%) - Caffeine caching, findByEventCode() optimization, composite indexes
- **Reliability:** PASS (100%) - FK constraints, proper error handling
- **Maintainability:** PASS (95%) - Clean DDD architecture, zero technical debt
- **Production-Ready:** ✅ Approved for deployment

---

### Story 5.5: Speaker Content Submission, Quality Review & Configurable Task System

**User Story:**
As an **organizer**, I want to submit speaker materials, have them quality-reviewed, and manage configurable event tasks, so that I can coordinate all aspects of event planning with proper workflow tracking and task delegation.

**Scope Redesign (2025-12-19):** This story consolidates content submission, quality review, and task management into a single comprehensive story, reflecting the parallel nature of speaker workflows and separating tasks from workflow states.

**Architecture Integration:**
- **Service**: Event Management Service
- **Data Model**:
  - `speaker_pool`: Workflow state tracking (content_submitted, quality_reviewed, confirmed)
  - `sessions`: Presentation title/abstract storage
  - `session_users`: Junction table linking speakers (username) to sessions
  - `task_templates`: Reusable task templates
  - `event_tasks`: Event-specific tasks
- **External Service**: User Management Service (speaker lookup/creation via username)
- **Storage**: AWS S3 for portrait uploads (presigned URLs)
- **Frontend**: React content submission, quality review queue, task dashboard

**Acceptance Criteria:**

**A. Speaker/User Lookup & Management (AC1-5):**
1. **User Autocomplete**: Search users-service by name as organizer types
2. **User Selection**: Select existing user → auto-populate email, company, bio, portrait
3. **User Create Modal**: If not found, open modal to create new user with SPEAKER role
4. **User Edit Modal**: Edit existing speaker profile (updates users-service)
5. **Username Storage**: Store username in speaker_pool and session_users (FK to users-service)

**B. Content Submission (AC6-10):**
6. **Content Form**: Organizer enters presentation title (required), abstract (required, max 1000 chars)
7. **Session Creation**: Create session record with title/abstract in `sessions` table
8. **Session-Speaker Link**: Create `session_users` entry linking speaker (username) to session
9. **Portrait Upload**: S3 presigned URL for portrait upload (stored in users-service)
10. **Workflow State**: Update `speaker_pool.status` = 'content_submitted' when title + abstract entered

**C. Quality Review (AC11-15):**
11. **Review Queue**: List all speaker_pool entries with status='content_submitted'
12. **Review Criteria**: Check abstract for lessons learned, no product promotion, appropriate length
13. **Approve/Reject**: Moderator can approve or request changes with feedback
14. **Workflow State**: Update `speaker_pool.status` = 'quality_reviewed' when approved
15. **Re-review**: If rejected, speaker remains in 'content_submitted' until resubmitted

**D. Auto-Confirmation Logic (AC16-18):**
16. **Orthogonal Slot Assignment**: Slot assignment (setting sessions.start_time) can happen at any point after ACCEPTED state
17. **Auto-Confirmation Trigger**: When speaker reaches QUALITY_REVIEWED state, system checks if session.start_time != null (slot assigned). If so, auto-update speaker_pool.status = 'confirmed'. Order doesn't matter: slot can be assigned before, during, or after quality review.
18. **Visual Indicators**: Dashboard shows which speakers have quality reviewed, slot assigned, or both (confirmed when both conditions met)

**E. Task System Foundation (AC19-27):**
19. **Task Templates Table**: Create `task_templates` table with 7 default templates
20. **Event Tasks Table**: Create `event_tasks` table for event-specific tasks
21. **Event Creation UI**: Show default task templates (pre-checked) + custom task option
22. **Custom Task Creation**: Organizer can add custom tasks with trigger state, due date, assigned organizer
23. **Task Auto-Creation**: When event reaches trigger state, create task with calculated due date
24. **Task Dashboard**: Organizers see assigned tasks grouped by status (todo, in_progress, completed)
25. **Task Completion**: Mark complete with notes, track completed_by and completed_date
26. **Task Templates Library**: View/edit/delete custom templates, apply to new events
27. **Default Templates**: 7 templates (venue, partner meeting, moderator, 3 newsletters, catering)

**Technical Implementation:**
- **Database Migration**: V15__Add_task_system.sql
  - Add `speaker_pool.session_id` column (link to session)
  - Create `task_templates` table
  - Create `event_tasks` table
  - Seed 7 default task templates
- **Backend Services**:
  - UserApiClient (lookup/create users in users-service)
  - SpeakerContentSubmissionService
  - QualityReviewService
  - TaskTemplateService
  - EventTaskService (auto-creation on workflow state transitions)
- **REST APIs**:
  - User: GET /api/users/search, POST /api/users (create), PUT /api/users/{username}
  - Content: POST /api/events/{id}/speakers/{poolId}/content
  - Quality: GET /api/events/{id}/content/review-queue, POST /api/speakers/{poolId}/review
  - Tasks: GET /api/tasks/templates, POST /api/tasks/templates, GET /api/events/{id}/tasks, PUT /api/tasks/{id}/complete
- **Frontend Components**:
  - UserLookupAutocomplete, UserCreateModal, UserEditModal
  - ContentSubmissionForm
  - QualityReviewQueue
  - TaskTemplateSelector, TaskDashboard, TaskCompletionModal

**Definition of Done:**
- [ ] User lookup/create working with users-service integration
- [ ] Content submission creates session + session_users link + updates speaker_pool
- [ ] Quality review queue shows content_submitted speakers
- [ ] Approve/reject updates speaker_pool.status correctly
- [ ] Parallel workflow: confirmed state reached when both quality_reviewed AND slot_assigned
- [ ] Task system: templates created, tasks auto-created on state transitions
- [ ] Task dashboard shows assigned tasks with completion tracking
- [ ] Custom tasks can be created and saved as templates
- [ ] Unit tests >90%, integration tests >80%
- [ ] Bruno API tests cover all endpoints
- [ ] Frontend components fully functional

**Estimated Duration:** 2.5 weeks (12 days)

**Database Schema Changes:**
```sql
-- V15__Add_task_system.sql will create:
-- 1. task_templates table
-- 2. event_tasks table
-- 3. speaker_pool.session_id column (FK to sessions)
-- 4. Indexes for performance
-- 5. Seed 7 default task templates
```

**Implementation Phases:**
- Phase 1: Database & Backend Foundation (Days 1-2)
- Phase 2: Speaker/User Lookup (Days 3-4)
- Phase 3: Content Submission (Days 5-6)
- Phase 4: Quality Review (Days 7-8)
- Phase 5: Task System Backend (Days 9-10)
- Phase 6: Task System Frontend (Days 11-12)

---

## Phase C: Overflow & Slot Management (Stories 5.6-5.7)

### Story 5.6: Overflow Management & Voting

**Status:** ⏳ **PENDING**

**User Story:**
As an **organizer**, I want to vote on speaker selection when we have more speakers than slots, so that we can democratically choose the best speaker lineup.

**Architecture Integration:**
- **Service**: Event Management Service
- **Database**: speaker_pool with overflow status
- **Data Model**: Uses OverflowManagementService and SpeakerSelectionVoteRepository from workflow state machines
- **Frontend**: React voting interface and overflow management dashboard

**Acceptance Criteria:**

**Overflow Detection (AC1-4):**
1. **Automatic Detection**: System detects when accepted speakers > maximum slots for event type
2. **Overflow Pool Creation**: Speakers marked with status='overflow' in speaker_pool when slots full
3. **Overflow Dashboard**: Show all speakers with status='overflow' requiring voting decision
4. **Speaker Comparison View**: Side-by-side comparison of speaker abstracts, expertise, past performance

**Voting Workflow (AC5-9):**
5. **Organizer Voting**: Each organizer votes APPROVE or REJECT for each overflow speaker
6. **Vote Tracking**: Show who has voted and who hasn't (voting progress indicator)
7. **Vote Results**: Display vote counts per speaker in real-time
8. **Selection Algorithm**: Top N speakers by vote count selected for available slots
9. **Overflow List**: Remaining speakers kept with status='overflow' as backup

**Overflow Management (AC10-12):**
10. **Promote on Dropout**: If speaker with status='withdrew', suggest top overflow speaker for promotion
11. **Manual Override**: Organizers can manually select from overflow pool (bypass voting)
12. **Notification**: Notify speakers of selection/overflow status after voting complete

**Workflow Engine Integration:**
13. **Overflow Detection**: Use `OverflowManagementService.checkForOverflow(eventId)` when accepted speakers > max_slots
14. **Voting Complete**: Call `OverflowManagementService.selectFinalSpeakers(overflowId)` to finalize selection
15. **State Update**: Selected speakers transition to ACCEPTED, unselected remain in OVERFLOW state
16. **Domain Event**: Listen for SpeakerOverflowDetectedEvent to trigger voting workflow

**Technical Implementation:**
- **OverflowManagementService**: From workflow state machines (06a lines 661-768)
- **SpeakerSelectionVote Entity**: Track organizer_id, speaker_id, vote (approve/reject), reason
- **REST API**:
  - POST /api/events/{eventId}/overflow/vote
  - GET /api/events/{eventId}/overflow/status
  - POST /api/events/{eventId}/overflow/promote/{speakerId}
- **React Components**: OverflowVotingInterface, SpeakerComparisonView, OverflowDashboard
- **Domain Events**: OverflowDetectedEvent, VotingCompleteEvent, SpeakerSelectedEvent, SpeakerPromotedFromOverflowEvent

**Definition of Done:**
- [ ] System automatically detects overflow when speakers > max slots
- [ ] Overflow dashboard shows all speakers requiring voting
- [ ] Organizers can vote on each overflow speaker
- [ ] Vote results calculate top N speakers for selection
- [ ] Overflow pool maintained with ranking for backup
- [ ] Promote overflow speaker on dropout working
- [ ] Integration test verifies overflow voting workflow
- [ ] Unit tests >90%, integration tests >80%
- [ ] Bruno API tests cover all overflow endpoints

**Estimated Duration:** 2 weeks

---

### Story 5.7: Slot Assignment & Progressive Publishing

**Status:** ⏳ **PENDING**

**User Story:**
As an **organizer**, I want to assign speakers to time slots and progressively publish the event agenda, so that I can create an optimized schedule and share information with attendees at the right time.

**Architecture Integration:**
- **Service**: Event Management Service
- **Database**: sessions table with start_time (slot assignment), events table with workflow_state
- **Data Model**: Uses SlotAssignmentService from workflow state machines (06a lines 517-576)
- **CDN**: CloudFront for public content delivery
- **Frontend**: React drag-and-drop slot assignment + publishing controls
- **Cache**: Caffeine cache with CDN invalidation

**Wireframe Reference:**
- `docs/wireframes/story-2.3-basic-publishing-engine.md` ✅

**Acceptance Criteria:**

**A. Slot Assignment (AC1-13):**
1. **Automatic Slot Generation**: Generate slots based on event type timing template
2. **Slot Customization**: Organizer can adjust session start_time/end_time
3. **Slot Types**: Support keynote, session, panel, workshop, networking slots
4. **Break Slots**: Include break, lunch, networking slots in schedule
5. **Drag-and-Drop UI**: Drag speaker cards to time slots
6. **Visual Schedule**: Timeline view showing all slots and assignments
7. **Speaker Preferences**: Show speaker's time preferences (morning/afternoon, conflicts)
8. **Technical Requirements**: Track A/V needs, room setup requirements per session
9. **Conflict Detection**: Warn if speaker has conflicting commitment at same time
10. **Flow Optimization**: Suggest optimal speaker order based on topic flow
11. **Preference Matching**: Highlight when slot matches speaker preference
12. **Unassigned Speakers**: Show list of speakers not yet assigned to slots
13. **Bulk Assignment**: Auto-suggest assignments based on preferences and flow

**B. Workflow Engine Integration (AC14-18):**
14. **Slot Assignment Action**: Call `slotAssignmentService.assignSpeakerToSlot(eventCode, speakerId, sessionId, organizerId)` (sets sessions.start_time, does NOT change speaker workflow state)
15. **Auto-Confirmation**: If speaker is QUALITY_REVIEWED, system auto-confirms to CONFIRMED when slot assigned
16. **Event State Transition**: When all slots assigned, call `eventWorkflowStateMachine.transitionToState(eventId, EventWorkflowState.SLOT_ASSIGNMENT, organizerId)`
17. **Use SlotAssignmentService**: Use `SlotAssignmentService.assignSpeakersToSlots(eventId, useAutomaticAssignment)` for automatic assignment
18. **Validation**: State machine validates all quality reviews complete before allowing slot assignment

**C. Progressive Publishing (AC19-26):**
19. **Topic Publishing**: Publish event topic, date, venue when event reaches TOPIC_SELECTION state
20. **Speaker Lineup Publishing**: Transition to AGENDA_PUBLISHED state, publish speaker names/companies 1 month before event
21. **Final Agenda Publishing**: Publish complete agenda with time slots when all slots assigned
22. **Manual Publish Controls**: Publish/unpublish buttons per phase
23. **Auto-Publish Scheduling**: Cron jobs auto-publish at 1 month and 2 weeks before event
24. **Preview Mode**: Preview how event will appear publicly before publishing
25. **Publishing Validation**: Validate all slots assigned and speakers confirmed before AGENDA_PUBLISHED
26. **CDN Cache Management**: Invalidate CloudFront cache on publish/update

**D. Content Visibility (AC27-30):**
27. **Phase-Based Display**: Public site shows only published content based on workflow_state
28. **Progressive Disclosure**: More content appears as workflow_state advances
29. **Update Notifications**: Notify subscribers when new phase publishes (via newsletter task)
30. **Continuous Updates**: Allow organizers to update published content anytime (re-invalidate CDN)

**Technical Implementation:**
- **SlotAssignmentService**: From workflow state machines (06a lines 517-576)
- **sessions table**: start_time column for slot assignment (orthogonal to speaker workflow state)
- **EventWorkflowStateMachine**: SLOT_ASSIGNMENT → AGENDA_PUBLISHED state transitions
- **REST API**:
  - POST /api/events/{eventId}/sessions (create time slots)
  - PUT /api/events/{eventId}/sessions/{sessionId}/assign (assign speaker to slot)
  - POST /api/events/{eventId}/publish/{phase} (publish phase)
  - DELETE /api/events/{eventId}/publish/{phase} (unpublish)
  - GET /api/events/{eventId}/preview (preview mode)
- **Scheduled Tasks**: Cron jobs for auto-publish (1 month, 2 weeks before)
- **React Components**:
  - DragDropSlotAssignment (timeline visualization)
  - PublishingControls (publish/unpublish buttons)
  - EventPreview (preview mode)
  - PublicEventPage (public-facing event display)
- **CDN Integration**: CloudFront invalidation API calls
- **Domain Events**:
  - SlotAssignedEvent
  - SpeakerConfirmedEvent (auto-triggered by slot assignment)
  - EventWorkflowTransitionEvent (SLOT_ASSIGNMENT, AGENDA_PUBLISHED)
  - PhasePublishedEvent
  - EventUpdatedEvent

**Definition of Done:**
- [ ] Slots automatically generated based on event type
- [ ] Drag-and-drop slot assignment working smoothly
- [ ] Visual timeline shows all slots and assignments
- [ ] Speaker preferences displayed during assignment
- [ ] Conflict detection warns of scheduling issues
- [ ] Unassigned speakers list updates in real-time
- [ ] Auto-confirmation triggers when QUALITY_REVIEWED + slot assigned
- [ ] Event transitions to AGENDA_PUBLISHED when all slots assigned
- [ ] Topic published immediately when event created
- [ ] Speaker lineup auto-publishes 1 month before event
- [ ] Final agenda auto-publishes 2 weeks before (or when manually triggered)
- [ ] Manual publish/unpublish controls working per phase
- [ ] Preview mode shows public view before publishing
- [ ] CDN cache invalidated on publish/update
- [ ] Public site displays content based on published phases
- [ ] Integration test verifies slot assignment + publishing workflow
- [ ] Unit tests >90%, integration tests >80%
- [ ] Bruno API tests cover all endpoints

**Estimated Duration:** 3 weeks

---

## Phase D: Event Lifecycle (Story 5.8)

### Story 5.8: Agenda Finalization & Event Lifecycle

**Status:** ⏳ **PENDING**

**User Story:**
As an **organizer**, I want to finalize the event agenda and manage the event lifecycle from finalization through archival, so that I can lock the agenda for printing and track the event through completion.

**Architecture Integration:**
- **Service**: Event Management Service
- **Database**: events table with workflow_state, speaker_pool with withdrew status
- **Data Model**: Uses EventWorkflowStateMachine (06a lines 49-172)
- **Frontend**: React agenda finalization interface and event lifecycle dashboard

**Acceptance Criteria:**

**A. Agenda Finalization (AC1-8):**
1. **Finalize Action**: Button to transition event to AGENDA_FINALIZED state (2 weeks before event)
2. **Finalization Validation**: Validate all slots assigned, all speakers confirmed before finalizing
3. **Lock Editing**: After finalization, major changes require manual unlock (minor edits allowed)
4. **Print View**: Generate printable PDF agenda for event day materials
5. **Final Review Checklist**: Verify all slots assigned, materials collected, quality reviewed
6. **Finalization Date**: Track when agenda was finalized (events.finalized_at timestamp)
7. **Unlock Workflow**: Admin can unlock agenda with justification
8. **Change Log**: Track all changes after finalization with timestamp and reason

**B. Dropout Handling (AC9-12):**
9. **Dropout Detection**: Organizer can mark speaker as 'withdrew' status
10. **Overflow Promotion**: Suggest top overflow speaker to fill dropout slot (from Story 5.6)
11. **Quick Reassignment**: Fast workflow to promote overflow speaker to open slot
12. **Notification**: Notify replacement speaker and update published agenda (invalidate CDN)

**C. Event Lifecycle States (AC13-18):**
13. **AGENDA_FINALIZED**: Manual transition 2 weeks before event (locks agenda)
14. **EVENT_LIVE**: Auto-transition on event day (scheduled job)
15. **EVENT_COMPLETED**: Manual transition after event (trigger post-processing)
16. **ARCHIVED**: Manual transition when event fully processed
17. **Workflow Validation**: Each transition validates required conditions via EventWorkflowStateMachine
18. **State History**: Track all state transitions with timestamp and organizer

**D. Post-Event Processing (AC19-22):**
19. **Event Day Dashboard**: Live dashboard showing current session, next session, timing
20. **Session Check-In**: Mark sessions as started/completed during event
21. **Post-Event Notes**: Capture organizer notes, attendance, feedback after event
22. **Archival**: Archive event when all post-processing complete

**Technical Implementation:**
- **EventWorkflowStateMachine**: Handles all state transitions (06a lines 49-172)
- **Validation Methods**:
  - validateAgendaPublished() (before AGENDA_FINALIZED)
  - validateEventDateInFuture() (14 days minimum before finalize)
  - validateEventDateInPast() (before EVENT_COMPLETED)
- **REST API**:
  - PUT /api/events/{eventId}/finalize (transition to AGENDA_FINALIZED)
  - PUT /api/events/{eventId}/unlock (unlock finalized agenda)
  - PUT /api/events/{eventId}/speakers/{speakerId}/withdraw (mark speaker as withdrew)
  - POST /api/events/{eventId}/overflow/promote/{speakerId} (promote from overflow)
  - PUT /api/events/{eventId}/lifecycle/{state} (transition to EVENT_LIVE, EVENT_COMPLETED, ARCHIVED)
  - GET /api/events/{eventId}/pdf (generate printable PDF agenda)
- **Scheduled Tasks**:
  - Daily cron job to auto-transition to EVENT_LIVE on event day
- **React Components**:
  - AgendaFinalizationPanel (finalize/unlock controls)
  - DropoutHandlingModal (withdraw speaker, promote overflow)
  - EventLifecycleDashboard (live event dashboard)
  - PrintableAgendaGenerator (PDF generation)
- **PDF Generation**: Generate printable agenda using PDF library
- **Domain Events**:
  - EventWorkflowTransitionEvent (AGENDA_FINALIZED, EVENT_LIVE, EVENT_COMPLETED, ARCHIVED)
  - SpeakerWithdrewEvent
  - SpeakerPromotedFromOverflowEvent
  - AgendaUnlockedEvent

**Definition of Done:**
- [ ] Finalize button transitions event to AGENDA_FINALIZED with lock
- [ ] Validation enforces all slots assigned and speakers confirmed before finalize
- [ ] Final review checklist validates all requirements met
- [ ] Printable PDF agenda generation working
- [ ] Dropout detection and overflow promotion working
- [ ] Change log tracks all post-finalization changes
- [ ] Unlock workflow requires justification
- [ ] Auto-transition to EVENT_LIVE on event day
- [ ] Manual transitions to EVENT_COMPLETED and ARCHIVED working
- [ ] Event lifecycle dashboard shows current state
- [ ] Integration test verifies finalization + lifecycle workflow
- [ ] Unit tests >90%, integration tests >80%
- [ ] Bruno API tests cover all lifecycle endpoints

**Estimated Duration:** 2 weeks

---


## **WORKFLOW CONSOLIDATION COMPLETE**

**The old 16-story structure has been successfully consolidated into the new 8-story structure.**

All NEW Stories (5.1-5.8) are now documented above with alignment to the redesigned workflow state machines.

**Consolidation Summary:**
- ✅ **Stories 5.1-5.4**: Already implemented (no changes needed)
- ✅ **Story 5.5**: Content Submission, Quality Review & Task System (consolidates old 5.5-5.7)
- ✅ **Story 5.6**: Overflow Management & Voting (consolidates old 5.8-5.9)
- ✅ **Story 5.7**: Slot Assignment & Progressive Publishing (consolidates old 5.9-5.11)
- ✅ **Story 5.8**: Agenda Finalization & Event Lifecycle (extracted from old 5.11)
- ✅ **Old Stories 5.12-5.15**: Integrated into Story 5.5 task system

The old Phase C-F sections (Stories 5.6-5.15 from 16-story structure) have been removed.

---

## Epic 5 Success Metrics

**Functional Success (End-to-End Workflow):**
- ✅ Organizers can complete full event workflow (9 event states + 10 speaker states) from topic selection through archival
- ✅ Event creation to publication achievable in timeline with manual speaker coordination
- ✅ All 8 stories functional and tested (Stories 5.1-5.4 complete, 5.5-5.8 pending)
- ✅ Organizers can manage events without requiring speaker self-service portal
- ✅ Task system separates configurable tasks from workflow states

**Technical Performance:**
- **Response Times**: API responses <200ms P95 for all workflow operations
- **Publishing Speed**: <1 minute from publish action to public visibility
- **Topic Search**: Results in <500ms with heat map visualization
- **Newsletter Delivery**: <5 minutes to send to all recipients via AWS SES
- **System Availability**: >99.5% uptime

**Business Value:**
- **Workflow Completeness**: 100% of FR2 workflow steps implemented
- **Organizer Efficiency**: Complete workflow reduces manual coordination time by 60%
- **Topic Intelligence**: Heat map and similarity detection prevent duplicate topics
- **Publishing Automation**: Progressive publishing reduces manual updates
- **Communication Automation**: Newsletters reduce manual email coordination
- **Quality Control**: Review workflow ensures high-quality speaker content

**Phase 2 Enhancement Opportunities:**
- Epic 6: Speaker self-service portal (speakers submit own content, reducing organizer burden)
- Epic 8: Advanced partner analytics, voting, and meeting automation
- Advanced features: Template editor, open/click tracking, calendar integration, catering menu UI

---

## Implementation Notes

**Sequence Recommendations:**
1. **Start with Phase A-B** (Stories 5.1-5.5): Core event setup and speaker coordination
2. **Then Phase C-D** (Stories 5.6-5.9): Quality control and assignment
3. **Then Phase E** (Stories 5.10-5.11): Publishing and finalization
4. **Finally Phase F** (Stories 5.12-5.15): Communication and logistics

**Testing Strategy:**
- Each story has integration tests verifying end-to-end workflow
- End-of-epic test: Complete 16-step workflow test from topic selection to partner meeting
- Performance tests for topic search, publishing, newsletter sending
- Load tests for concurrent organizer access

**Deployment Strategy:**
- Deploy stories incrementally as completed
- Feature flags for incomplete workflow phases
- Beta testing with small group of organizers before full rollout
- Rollback plan if workflow automation causes issues

---

**Files Modified by Epic 5:**
- Event Management Service: All 16 workflow steps
- Speaker Coordination Service: Steps 4-6 (outreach, status, content)
- Partner Coordination Service: Step 16 (partner meetings) - extends Story 2.7
- Frontend: React organizer dashboard with workflow interface
- Infrastructure: AWS SES integration for newsletters (Story 5.12)
