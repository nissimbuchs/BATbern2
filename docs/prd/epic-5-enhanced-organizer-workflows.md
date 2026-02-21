# Epic 5: Complete Event Management Workflow

**Status:** ✅ **COMPLETE** - MVP Ready (8/8 stories, 100%)

**Last Updated:** 2026-01-24
**Completed:** 2026-01-24

**Workflow Redesign (2025-12-19):** Epic 5 has been redesigned from a linear 16-step workflow to a parallel workflow architecture with 9 event states, per-speaker workflows, and configurable task management. This reflects the actual implementation reality discovered during Stories 5.1-5.4.

**Key Architectural Changes:**
- **Event Workflow:** Simplified to 9 high-level states (down from 16 linear steps)
- **Speaker Workflow:** Per-speaker state machine with parallel quality review and slot assignment
- **Task System:** Configurable tasks (newsletters, catering, partner meetings) separate from workflow states
- **Organizer-Driven:** Manual speaker coordination without requiring speaker self-service portal

**Scope Change (2026-01-24):** Overflow Management (Story 5.6) **REMOVED FROM MVP SCOPE** - moved to Phase 2+ backlog. Manual speaker selection by organizers is sufficient for MVP launch. Democratic voting on overflow speakers deferred to future enhancement.

**Progress Summary:**
- ✅ Phase A: Event Setup - COMPLETE (4 stories)
  - ✅ 5.1 - Event Type Definition (COMPLETE)
  - ✅ 5.1a - Workflow State Machine Foundation (COMPLETE - CRITICAL DEPENDENCY)
  - ✅ 5.2 - Topic Selection & Speaker Brainstorming (COMPLETE)
  - ✅ 5.2b - Multi-Topic Heat Map Visualization (Frontend COMPLETE)
- ✅ Phase B: Speaker Coordination - COMPLETE (2 stories)
  - ✅ 5.3 - Speaker Outreach Tracking (COMPLETE)
  - ✅ 5.4 - Speaker Status Management (COMPLETE - QA PASS 95/100)
- ✅ Phase C: Content & Slot Management - COMPLETE (1 story)
  - ✅ 5.5 - Speaker Content, Quality Review & Task System (COMPLETE - All 6 phases done, 100% implemented)
- ✅ Phase D: Publishing & Finalization - COMPLETE (2 stories)
  - ✅ 5.7 - Slot Assignment & Progressive Publishing (COMPLETE - Core functionality + auto-scheduling)
  - ✅ BAT-16 - Epic 5 Completion: Auto-Publishing & Lifecycle (COMPLETE - Stories 5.7 & 5.8 finalized)

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
                ┌───────────────────┴───────────────────┐
                ↓                                       ↓
        content_submitted                       slot_assigned
                ↓                                       ↓
        quality_reviewed                                │
                └───────────────────┬───────────────────┘
                                    ↓
                               confirmed
```

**Key States:**
- `identified`: Added to speaker pool
- `contacted`: Organizer recorded outreach
- `ready`: Speaker is ready to accept/decline
- `accepted`/`declined`: Speaker decision
- `content_submitted`: Presentation title/abstract submitted
- `quality_reviewed`: Content approved by moderator
- `slot_assigned`: Assigned to time slot
- `confirmed`: Both quality_reviewed AND slot_assigned (order doesn't matter)
- `overflow`: Backup speaker (accepted but no slot available)
- `withdrew`: Speaker drops out after accepting

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
| **5.1a** | Workflow State Machine | All 9 states | All 11 states | - |
| **5.2** | Topic Selection & Brainstorming | TOPIC_SELECTION | identified | - |
| **5.3** | Speaker Outreach | - | contacted | - |
| **5.4** | Speaker Status Management | - | ready, accepted, declined | - |
| **5.5** | Content, Quality Review & Tasks | - | content_submitted, quality_reviewed, confirmed | Task system foundation |
| **5.6** | Overflow & Voting | - | overflow | - |
| **5.7** | Slot Assignment & Publishing | SLOT_ASSIGNMENT, AGENDA_PUBLISHED | slot_assigned | Newsletter tasks |
| **5.8** | Finalization & Lifecycle | AGENDA_FINALIZED, EVENT_LIVE, EVENT_COMPLETED, ARCHIVED | withdrew | Catering task |

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
1. **EventWorkflowState Enum**: 16 states for complete event lifecycle (CREATED → ARCHIVED)
2. **SpeakerWorkflowState Enum**: 11 states for speaker workflow (OPEN → OVERFLOW)
3. **Domain Events**: EventWorkflowTransitionEvent, SpeakerWorkflowStateChangeEvent
4. **Unit Tests**: Verify enum values and event serialization

**Phase 2: Event Workflow State Machine (Days 2-3)**
5. **EventWorkflowStateMachine Service**: Core orchestrator with transitionToState() method
6. **Validation Logic**: validateMinimumSpeakersIdentified(), validateAllContentSubmitted(), validateMinimumThresholdMet(), validateAllSlotsAssigned(), validateQualityReviewComplete()
7. **WorkflowTransitionValidator**: State transition matrix and business rule enforcement
8. **Database Migration**: Add workflow_state column to events table with index
9. **JPA Converter**: EventWorkflowStateConverter for enum ↔ VARCHAR conversion
10. **Event Entity Update**: Change status field to workflowState with proper annotations
11. **Unit Tests**: >90% coverage for state machine logic
12. **Integration Tests**: Complete workflow sequence with Testcontainers PostgreSQL

**Phase 3: Speaker Workflow Service (Days 4-5)**
13. **SpeakerWorkflowService**: Speaker state management with updateSpeakerWorkflowState() method
14. **State-Specific Logic**: CONTACTED (set timestamp, send notification), ACCEPTED (check overflow), DECLINED (handle decline), SLOT_ASSIGNED (validate assignment)
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
- `EventWorkflowState.java` - Event workflow state enum with 16 states
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

**Status:** ✅ **COMPLETE** - All 6 Phases COMPLETE (100% Implementation)

**User Story:**
As an **organizer**, I want to submit speaker materials, have them quality-reviewed, and manage configurable event tasks, so that I can coordinate all aspects of event planning with proper workflow tracking and task delegation.

**Scope Redesign (2025-12-19):** This story consolidates content submission, quality review, and task management into a single comprehensive story, reflecting the parallel nature of speaker workflows and separating tasks from workflow states.

**Completion Summary (2025-12-24):**
- ✅ Phase 1: Database & Backend Foundation COMPLETE (V22 migration, entities, repositories)
- ✅ Phase 2: Speaker/User Lookup COMPLETE (existing infrastructure verified)
- ✅ Phase 3: Content Submission COMPLETE (SpeakerContentSubmissionService fully implemented, ContentSubmissionDrawer component)
- ✅ Phase 4: Quality Review COMPLETE (QualityReviewService fully implemented with parallel workflow, QualityReviewDrawer component)
- ✅ Phase 5: Task System Backend COMPLETE (TaskTemplateService, EventTaskService with auto-creation, EventTaskController, TaskTemplateController)
- ✅ Phase 6: Task System Frontend COMPLETE (CustomTaskModal, TaskBoardPage, EventTasksTab, TaskWidget, TaskCard components)
- ✅ Integration Tests: 6 test files complete (SpeakerContentSubmissionServiceIntegrationTest, QualityReviewServiceIntegrationTest, EventTaskServiceIntegrationTest, TaskTemplateServiceIntegrationTest, EventTaskControllerIntegrationTest, TaskTemplateControllerIntegrationTest)
- ✅ Template Extraction: Story documentation reduced from 1,624 to ~470 lines (71% reduction via template library)

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

**D. Parallel Workflow Support (AC16-18):**
16. **Flexible Order**: quality_reviewed and slot_assigned can happen in any order
17. **Confirmed State**: Auto-update `speaker_pool.status` = 'confirmed' when BOTH quality_reviewed AND slot_assigned
18. **Visual Indicators**: Dashboard shows which speakers have quality reviewed, slot assigned, or both

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
- **Database Migration**: V22__Add_task_system.sql ✅ COMPLETE
  - `speaker_pool.session_id` already exists from V20 (no changes needed)
  - Create `task_templates` table ✅
  - Create `event_tasks` table ✅
  - Seed 7 default task templates ✅
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
- [x] **Database:** V22 migration creates task_templates, event_tasks tables with 7 default templates
- [x] **Phase 1 & 2:** Service scaffolding complete, user management infrastructure verified
- [x] **Phase 3:** Content submission creates session + session_users link + updates speaker_pool
- [x] **Phase 4:** Quality review queue shows content_submitted speakers, approve/reject working
- [x] **Phase 5:** Task system backend with auto-creation on workflow transitions
- [x] **Phase 6:** Frontend components (ContentSubmissionDrawer, QualityReviewDrawer, TaskDashboard)
- [x] Parallel workflow: confirmed state reached when both quality_reviewed AND slot_assigned
- [x] Integration tests >80% coverage (6 test files complete)
- [x] All REST API endpoints operational
- [x] All functionality fully tested and production-ready

**Actual Duration:** 2.5 weeks (12 days)
**Progress:** 6/6 phases complete (100%) ✅ COMPLETE

**Database Schema Changes:**
```sql
-- V22__Add_task_system.sql (COMPLETE - 2025-12-20)
-- 1. task_templates table ✅
-- 2. event_tasks table ✅
-- 3. speaker_pool.session_id already exists from V20 (no changes needed) ✅
-- 4. Indexes for performance ✅
-- 5. Seed 7 default task templates ✅
```

**Implementation Phases:**
- ✅ Phase 1: Database & Backend Foundation (Days 1-2) - COMPLETE
- ✅ Phase 2: Speaker/User Lookup (Days 3-4) - COMPLETE
- ✅ Phase 3: Content Submission (Days 5-6) - COMPLETE
- ✅ Phase 4: Quality Review (Days 7-8) - COMPLETE
- ✅ Phase 5: Task System Backend (Days 9-10) - COMPLETE
- ✅ Phase 6: Task System Frontend (Days 11-12) - COMPLETE

**Production-Ready:** ✅ All features implemented, tested, and ready for deployment

---

## Phase C: Quality & Threshold (Stories 5.6-5.7)

### Story 5.6: Content Quality Review (Workflow Step 7)

**User Story:**
As a **moderator**, I want to review speaker abstracts and materials for quality standards, so that we maintain high-quality event content.

**Architecture Integration:**
- **Service**: Event Management Service
- **Database**: content_reviews table
- **Frontend**: React moderator review queue

**Acceptance Criteria:**

**Review Queue:**
1. **Review Dashboard**: List of all submitted speaker materials pending review
2. **Review Status**: PENDING, APPROVED, REQUIRES_CHANGES
3. **Abstract Standards Check**: Flag abstracts missing lessons learned or containing product promotion
4. **Material Completeness**: Check all required materials submitted (title, abstract, CV, photo)
5. **Approve/Reject Actions**: Moderator can approve or request changes
6. **Feedback Field**: Free-text feedback to speaker/organizer on required changes

**Quality Criteria:**
7. **Abstract Length**: Enforce 1000 character maximum
8. **Lessons Learned Indicator**: Flag if abstract lacks lessons learned
9. **Product Promotion Detection**: Warn if abstract appears promotional
10. **Photo Quality Check**: Manual check for appropriate photo (headshot, professional)
11. **CV Validation**: Ensure CV uploaded and readable

**Workflow Engine Integration:**
12. **Review Approved**: When content approved, call `speakerWorkflowService.updateSpeakerWorkflowState(sessionId, speakerId, SpeakerWorkflowState.QUALITY_REVIEWED, moderatorId)`
13. **Event State Update**: When all content reviews approved, enable event transition to QUALITY_REVIEW complete state
14. **Validation**: Use QualityReviewService from EventWorkflowStateMachine to enforce review completion before publishing

**Technical Implementation:**
15. **Content Review Entity**: Track reviewer, status, feedback, review_date
16. **REST API**: GET /api/events/{id}/materials/pending, POST /api/materials/{id}/review
17. **React Component**: ModeratorReviewQueue with approve/reject actions
18. **Domain Event**: MaterialsReviewedEvent

**Definition of Done:**
- [ ] Moderator review queue displays all pending materials
- [ ] Moderator can approve or request changes with feedback
- [ ] Abstract standards automatically flagged
- [ ] Review status tracked in database
- [ ] Organizers notified when changes required
- [ ] Integration test verifies review workflow

**Estimated Duration:** 1.5 weeks

---

### Story 5.7: Minimum Threshold Check (Workflow Step 8)

**User Story:**
As an **organizer**, I want automatic threshold checks to prevent proceeding without minimum speakers, so that I don't publish incomplete events.

**Architecture Integration:**
- **Service**: Event Management Service
- **Database**: Event workflow state in events table
- **Frontend**: React threshold indicator and warnings

**Acceptance Criteria:**

**Threshold Validation:**
1. **Minimum Speaker Count**: Based on event type (full-day: 6, afternoon: 6, evening: 3)
2. **Threshold Indicator**: Dashboard widget showing "4/6 speakers confirmed" status
3. **Blocking Validation**: Cannot proceed to slot assignment until minimum met
4. **Warning Messages**: Clear messaging when threshold not met
5. **Progress Tracking**: Visual progress bar to minimum threshold

**Automatic Checks:**
6. **Real-time Updates**: Threshold status updates when speaker status changes
7. **Workflow Gate**: Slot assignment step disabled until threshold met
8. **Email Reminder**: Automatic reminder to organizers if threshold not met 2 weeks before event
9. **Override Capability**: Admin can override threshold requirement with justification

**Workflow Engine Integration:**
10. **Threshold Validation**: Use `EventWorkflowStateMachine.validateMinimumThresholdMet(event)` before allowing progression to SLOT_ASSIGNMENT state
11. **State Transition**: When threshold met, enable transition to SLOT_ASSIGNMENT state via `eventWorkflowStateMachine.transitionToState(eventId, EventWorkflowState.SLOT_ASSIGNMENT, organizerId)`
12. **Blocking Validation**: State machine enforces threshold check - cannot proceed without minimum speakers

**Technical Implementation:**
13. **Threshold Validation Service**: Check accepted speaker count vs minimum for event type
14. **REST API**: GET /api/events/{id}/threshold-status
15. **React Component**: ThresholdIndicator widget on event dashboard
16. **Domain Event**: ThresholdMetEvent, ThresholdNotMetWarningEvent

**Definition of Done:**
- [ ] Threshold indicator shows current vs minimum speaker count
- [ ] Cannot proceed to slot assignment if threshold not met
- [ ] Real-time threshold updates when speaker status changes
- [ ] Warning messages clear and actionable
- [ ] Email reminder sent 2 weeks before event if threshold not met
- [ ] Admin override capability working
- [ ] Integration test verifies threshold validation

**Estimated Duration:** 1 week

---

## Phase D: Selection & Assignment (Stories 5.8-5.9)

### Story 5.8: Speaker Selection & Overflow Management (Workflow Step 9)

**User Story:**
As an **organizer**, I want to vote on speaker selection when we have more speakers than slots, so that we can democratically choose the best speaker lineup.

**Architecture Integration:**
- **Service**: Event Management Service
- **Database**: overflow_speakers, speaker_votes tables
- **Frontend**: React voting interface and overflow management dashboard

**Acceptance Criteria:**

**Overflow Detection:**
1. **Automatic Detection**: System detects when accepted speakers > maximum slots for event type
2. **Overflow Pool Creation**: Automatically create overflow pool with excess speakers
3. **Overflow Dashboard**: Show all speakers requiring voting decision
4. **Speaker Comparison View**: Side-by-side comparison of speaker abstracts, expertise, past performance

**Voting Workflow:**
5. **Organizer Voting**: Each organizer votes APPROVE or REJECT for each overflow speaker
6. **Vote Tracking**: Show who has voted and who hasn't
7. **Vote Results**: Display vote counts per speaker
8. **Selection Algorithm**: Top N speakers by vote count selected for slots
9. **Overflow List**: Remaining speakers kept in overflow list as backup

**Overflow Management:**
10. **Promote on Dropout**: If selected speaker drops out, automatically suggest top overflow speaker
11. **Manual Override**: Organizers can manually select from overflow pool
12. **Notification**: Notify speakers of selection/overflow status after voting complete

**Workflow Engine Integration:**
13. **Overflow Detection**: Use `OverflowManagementService.checkForOverflow(eventId)` when accepted speakers > max_slots
14. **Voting Complete**: When voting done, call `OverflowManagementService.selectFinalSpeakers(overflowId)` to finalize selection
15. **State Update**: Selected speakers transition to ACCEPTED state, unselected speakers transition to OVERFLOW state via `speakerWorkflowService.updateSpeakerWorkflowState()`
16. **Domain Event**: Listen for SpeakerOverflowDetectedEvent to trigger voting workflow

**Technical Implementation:**
17. **Overflow Entity**: Track overflow speakers with vote counts, priority ranking (uses OverflowManagementService from Story 5.1a)
18. **Speaker Vote Entity**: Track organizer_id, speaker_id, vote (approve/reject), reason
19. **REST API**: POST /api/events/{id}/speakers/overflow/vote, GET /api/events/{id}/overflow
20. **React Component**: OverflowVotingInterface with speaker cards and voting buttons
21. **Domain Events**: OverflowDetectedEvent, VotingCompleteEvent, SpeakerSelectedEvent

**Definition of Done:**
- [ ] System automatically detects overflow when speakers > max slots
- [ ] Overflow dashboard shows all speakers requiring voting
- [ ] Organizers can vote on each overflow speaker
- [ ] Vote results calculate top N speakers for selection
- [ ] Overflow pool maintained with ranking for backup
- [ ] Promote overflow speaker on dropout working
- [ ] Integration test verifies overflow voting workflow

**Estimated Duration:** 2 weeks

---

### Story 5.9: Speaker-to-Slot Assignment (Workflow Step 10)

**User Story:**
As an **organizer**, I want to assign speakers to specific time slots considering preferences and technical needs, so that I can create an optimized event schedule.

**Architecture Integration:**
- **Service**: Event Management Service
- **Database**: event_slots, slot_assignments tables
- **Frontend**: React drag-and-drop slot assignment interface

**Acceptance Criteria:**

**Slot Creation:**
1. **Automatic Slot Generation**: Generate slots based on event type timing template
2. **Slot Customization**: Organizer can adjust slot start/end times
3. **Slot Types**: Support keynote, session, panel, workshop, networking slots
4. **Break Slots**: Include break, lunch, networking slots in schedule

**Assignment Interface:**
5. **Drag-and-Drop UI**: Drag speaker cards to time slots
6. **Visual Schedule**: Timeline view showing all slots and assignments
7. **Speaker Preferences**: Show speaker's time preferences (morning/afternoon, conflicts)
8. **Technical Requirements**: Track A/V needs, room setup requirements per speaker
9. **Conflict Detection**: Warn if speaker has conflicting commitment at same time

**Assignment Optimization:**
10. **Flow Optimization**: Suggest optimal speaker order based on topic flow
11. **Preference Matching**: Highlight when slot matches speaker preference
12. **Unassigned Speakers**: Show list of speakers not yet assigned to slots
13. **Bulk Assignment**: Auto-suggest assignments based on preferences and flow

**Workflow Engine Integration:**
14. **Assignment State**: When slot assigned to speaker, call `speakerWorkflowService.updateSpeakerWorkflowState(sessionId, speakerId, SpeakerWorkflowState.SLOT_ASSIGNED, organizerId)`
15. **Event State**: When all slots assigned, transition event via `eventWorkflowStateMachine.transitionToState(eventId, EventWorkflowState.AGENDA_PUBLISHED, organizerId)`
16. **Use SlotAssignmentService**: Use `SlotAssignmentService.assignSpeakersToSlots(eventId, useAutomaticAssignment)` from Story 5.1a for automatic assignment
17. **Validation**: State machine enforces that all quality reviews must be complete before slot assignment

**Technical Implementation:**
18. **Event Slot Entity**: slot_number, start_time, end_time, slot_type, assigned_speaker_id
19. **Slot Assignment Entity**: slot_id, speaker_id, assignment_date, assigned_by, match_score
20. **REST API**: POST /api/events/{id}/slots, PUT /api/events/{id}/slots/{slotId}/assign
21. **React Component**: DragDropSlotAssignment with timeline visualization
22. **Domain Events**: SlotCreatedEvent, SpeakerAssignedToSlotEvent

**Definition of Done:**
- [ ] Slots automatically generated based on event type
- [ ] Drag-and-drop slot assignment working smoothly
- [ ] Visual timeline shows all slots and assignments
- [ ] Speaker preferences displayed during assignment
- [ ] Conflict detection warns of scheduling issues
- [ ] Unassigned speakers list updates in real-time
- [ ] Integration test verifies slot assignment workflow

**Estimated Duration:** 2.5 weeks

---

## Phase E: Publishing & Finalization (Stories 5.10-5.11)

### Story 5.10: Progressive Publishing Engine (Workflow Step 11)

**User Story:**
As an **organizer**, I want to progressively publish event information (topic immediately, speakers 1 month before, final agenda 2 weeks before), so that attendees receive timely information as it becomes available.

**Architecture Integration:**
- **Service**: Event Management Service
- **CDN**: CloudFront for public content delivery
- **Frontend**: React publishing controls and public event pages
- **Cache**: Caffeine cache with CDN invalidation

**Wireframe Reference:**
- `docs/wireframes/story-2.3-basic-publishing-engine.md` ✅

**Acceptance Criteria:**

**Publishing Phases:**
1. **Phase 1 - Topic**: Publish event topic, date, venue immediately upon creation
2. **Phase 2 - Speakers**: Publish speaker lineup 1 month before event (auto-triggered)
3. **Phase 3 - Final Agenda**: Publish complete agenda with time slots 2 weeks before event
4. **Continuous Updates**: Allow organizers to update published content anytime

**Publishing Controls:**
5. **Publish/Unpublish**: Manual publish/unpublish buttons per phase
6. **Auto-Publish Scheduling**: Automatically publish Phase 2 at 1 month before, Phase 3 at 2 weeks before
7. **Preview Mode**: Preview how event will appear publicly before publishing
8. **Publishing Validation**: Validate required content before allowing publish per phase

**Content Visibility:**
9. **Phase-Based Display**: Public site shows only published phases
10. **Progressive Disclosure**: As more phases publish, more content appears
11. **Update Notifications**: Notify subscribers when new phase publishes
12. **CDN Cache Management**: Invalidate CloudFront cache on publish/update

**Workflow Engine Integration:**
13. **Publishing States**: Call `eventWorkflowStateMachine.transitionToState(eventId, EventWorkflowState.AGENDA_PUBLISHED, organizerId)` per publishing phase
14. **Scheduled Jobs**: Cron jobs call state machine for auto-publish at 1 month and 2 weeks before event
15. **Validation**: State machine validates all slots assigned and quality reviews complete before allowing AGENDA_PUBLISHED transition
16. **Event Listener**: Listen for EventWorkflowTransitionEvent to trigger CDN cache invalidation

**Technical Implementation:**
17. **Publishing Phase Enum**: TOPIC_PUBLISHED, SPEAKERS_PUBLISHED, AGENDA_PUBLISHED (integrated with EventWorkflowState from Story 5.1a)
18. **Scheduled Tasks**: Cron jobs to auto-publish at 1 month and 2 weeks before
19. **REST API**: POST /api/events/{id}/publish/{phase}, DELETE /api/events/{id}/publish/{phase}
20. **React Components**: PublishingControls, EventPreview, PublicEventPage
21. **CDN Integration**: CloudFront invalidation API calls
22. **Domain Events**: PhasePublishedEvent, EventUpdatedEvent

**Definition of Done:**
- [ ] Topic published immediately when event created
- [ ] Speaker lineup auto-publishes 1 month before event
- [ ] Final agenda auto-publishes 2 weeks before event
- [ ] Manual publish/unpublish controls working per phase
- [ ] Preview mode shows public view before publishing
- [ ] CDN cache invalidated on publish/update
- [ ] Public site displays content based on published phases
- [ ] Integration test verifies progressive publishing workflow

**Estimated Duration:** 2 weeks

---

### Story 5.11: Agenda Finalization (Workflow Step 12)

**User Story:**
As an **organizer**, I want to finalize the event agenda 2 weeks before the event with dropout handling, so that I can lock the agenda for printing and handle last-minute changes.

**Architecture Integration:**
- **Service**: Event Management Service
- **Database**: Event workflow state tracking
- **Frontend**: React agenda finalization interface

**Acceptance Criteria:**

**Finalization Workflow:**
1. **Finalize Action**: Button to mark agenda as "FINALIZED" status
2. **Finalization Date**: Track when agenda was finalized
3. **Lock Editing**: After finalization, major changes require approval workflow
4. **Print View**: Generate printable PDF agenda for event day materials
5. **Final Review Checklist**: All slots assigned, materials collected, quality reviewed

**Dropout Handling:**
6. **Dropout Detection**: Organizer can mark speaker as "DROPPED_OUT"
7. **Overflow Promotion**: System suggests top overflow speaker to fill dropout slot
8. **Quick Reassignment**: Fast workflow to promote overflow speaker to open slot
9. **Notification**: Notify replacement speaker and update published agenda
10. **Dropout History**: Track all dropouts and replacements for reporting

**Lock Controls:**
11. **Minor Changes Allowed**: After finalization, minor edits (typos) allowed without unlocking
12. **Major Change Approval**: Major changes (speaker swap, slot change) require unlock approval
13. **Unlock Workflow**: Admin can unlock agenda with justification
14. **Change Log**: Track all changes after finalization with timestamp and reason

**Workflow Engine Integration:**
15. **Finalization State**: When agenda finalized, call `eventWorkflowStateMachine.transitionToState(eventId, EventWorkflowState.AGENDA_FINALIZED, organizerId)`
16. **Lock Validation**: State machine enforces lock after finalization - major changes require unlock workflow
17. **Dropout Handling**: Promote overflow speaker via `OverflowManagementService.promoteFromOverflow(eventId, droppedSpeakerId, replacementSpeakerId)`
18. **Validation**: State machine validates all slots assigned before allowing AGENDA_FINALIZED transition

**Technical Implementation:**
19. **Agenda Status Enum**: DRAFT, FINALIZED, LOCKED_PRINT (integrated with EventWorkflowState from Story 5.1a)
20. **Dropout Entity**: Track speaker_id, dropout_date, reason, replacement_speaker_id
21. **REST API**: POST /api/events/{id}/finalize, POST /api/events/{id}/unlock
22. **React Component**: AgendaFinalizationPanel with dropout handling
23. **PDF Generation**: Generate printable agenda using PDF library
24. **Domain Events**: AgendaFinalizedEvent, SpeakerDroppedOutEvent, AgendaUnlockedEvent

**Definition of Done:**
- [ ] Finalize button marks agenda as finalized with lock
- [ ] Final review checklist validates all requirements met
- [ ] Printable PDF agenda generation working
- [ ] Dropout detection and overflow promotion working
- [ ] Change log tracks all post-finalization changes
- [ ] Unlock workflow requires justification
- [ ] Integration test verifies finalization workflow

**Estimated Duration:** 1.5 weeks

---

## Phase F: Communication & Logistics (Stories 5.12-5.15)

### Story 5.12: Newsletter Distribution (Workflow Step 13)

**User Story:**
As an **organizer**, I want to send progressive newsletters to our mailing list, so that subscribers stay informed about upcoming events from topic announcement through final agenda.

**Architecture Integration:**
- **Service**: Event Management Service with email integration
- **Email**: AWS SES (already provisioned per EIR1)
- **Database**: newsletters, newsletter_recipients, newsletter_sends tables
- **Frontend**: React newsletter management interface

**Acceptance Criteria:**

**Newsletter Templates:**
1. **Template 1 - Topic Announcement**: Sent immediately when topic published
   - Event topic, date, venue
   - Call for speaker suggestions
   - Save the date message
2. **Template 2 - Speaker Lineup**: Sent 1 month before when speakers published
   - Speaker names, companies, titles
   - Presentation abstracts
   - Registration link
3. **Template 3 - Final Agenda**: Sent 2 weeks before when agenda finalized
   - Complete schedule with time slots
   - Venue details and logistics
   - Last chance to register

**Newsletter Management:**
4. **Manual Send**: Organizer can manually trigger any newsletter
5. **Auto-Send Option**: Optionally auto-send when phase publishes
6. **Preview Newsletter**: Preview email before sending
7. **Recipient List**: Pull from event registrations + newsletter subscribers table
8. **Variable Substitution**: Replace {{eventName}}, {{date}}, {{speakers}} with actual values

**Send Tracking:**
9. **Send Status**: Track sent, failed, bounced emails per recipient
10. **Send History**: Log all newsletter sends with timestamp, recipient count
11. **Delivery Confirmation**: AWS SES delivery notifications tracked
12. **Unsubscribe Handling**: Honor unsubscribe requests from previous newsletters

**Technical Implementation:**
13. **Newsletter Entity**: newsletter_type, event_id, sent_date, recipient_count, template_html
14. **AWS SES Integration**: Send emails via SES with bounce/complaint handling
15. **REST API**: POST /api/events/{id}/newsletters/{type}/send, GET /api/newsletters/history
16. **React Component**: NewsletterManager with preview and send controls
17. **Domain Events**: NewsletterSentEvent

**MVP Scope:**
- ✅ 3 pre-defined templates (no editor)
- ✅ AWS SES integration
- ✅ Pull recipients from registrations + subscribers
- ✅ Track "sent" status only (no open/click tracking)
- ❌ Template editor (Phase 2)
- ❌ Open/click tracking (Phase 2)

**Definition of Done:**
- [ ] 3 pre-defined newsletter templates implemented
- [ ] Organizer can preview newsletter before sending
- [ ] Manual send working via AWS SES
- [ ] Variable substitution replaces placeholders correctly
- [ ] Recipient list pulls from registrations + subscribers
- [ ] Send status tracked in database
- [ ] Unsubscribe handling functional
- [ ] Integration test verifies newsletter sending

**Estimated Duration:** 1.5 weeks

---

### Story 5.13: Moderation Assignment (Workflow Step 14)

**User Story:**
As an **organizer**, I want to assign a moderator to the event, so that someone is responsible for day-of-event coordination and timing.

**Architecture Integration:**
- **Service**: Event Management Service
- **Database**: Events table (moderator_id field)
- **Frontend**: React moderator assignment dropdown

**Acceptance Criteria:**

**Moderator Assignment:**
1. **Moderator Dropdown**: Select moderator from list of organizers
2. **Assignment Notification**: Notify assigned moderator via email
3. **Moderator Dashboard Access**: Moderator sees assigned events in their dashboard
4. **Event View**: Moderator can view event details, agenda, speaker contacts
5. **Unassign Moderator**: Organizer can change or remove moderator assignment

**Moderator Information:**
6. **Briefing Access**: Moderator has read access to all event information
7. **Speaker Contact List**: Moderator sees speaker phone numbers and emails for day-of contact
8. **Timeline View**: Moderator sees event timeline with slot timing
9. **Logistics Notes**: Moderator sees venue details, catering notes, special requirements

**Technical Implementation:**
10. **Events Table Update**: Add moderator_id field (foreign key to users)
11. **REST API**: PUT /api/events/{id}/moderator
12. **React Component**: ModeratorAssignmentDropdown in event settings
13. **Email Notification**: Send "You've been assigned as moderator" email
14. **Domain Event**: ModeratorAssignedEvent

**MVP Scope:**
- ✅ Simple dropdown field (select from organizers)
- ✅ Notification to assigned moderator
- ✅ Moderator sees event dashboard
- ❌ No special briefing materials (Phase 2)
- ❌ No day-of tools (Phase 2)

**Definition of Done:**
- [ ] Moderator dropdown shows all organizers
- [ ] Assignment saves to database
- [ ] Notification email sent to moderator
- [ ] Moderator sees assigned events in dashboard
- [ ] Moderator has read access to all event details
- [ ] Integration test verifies moderator assignment

**Estimated Duration:** 0.5 weeks

---

### Story 5.14: Catering Coordination (Workflow Step 15)

**User Story:**
As an **organizer**, I want a reminder to contact the caterer 1 month before the event, so that I can arrange menu and headcount for event day catering.

**Architecture Integration:**
- **Service**: Event Management Service with task management
- **Database**: event_tasks table
- **Frontend**: React task management interface

**Acceptance Criteria:**

**Task Creation:**
1. **Automatic Task**: Create "Contact caterer" task automatically 1 month before event
2. **Task Dashboard**: Show catering task in organizer task list
3. **Task Notification**: Email reminder sent to organizers when task created
4. **Due Date**: Task due date = event date minus 1 month
5. **Task Assignment**: Task assigned to event creator or all organizers

**Menu Coordination:**
6. **Menu Notes Field**: Free-text field to record menu details from caterer
7. **Headcount Tracking**: Show current registration count for caterer headcount estimate
8. **Menu Confirmation**: Checkbox to mark menu confirmed
9. **Task Completion**: Mark task complete when catering arranged

**Task Management:**
10. **Overdue Indicator**: Highlight task if overdue (event <1 month away and not complete)
11. **Reminder Escalation**: Send reminder 2 weeks before event if task incomplete
12. **Task Notes**: Add notes/comments to task for coordination between organizers
13. **Task History**: Track task completion date and who completed it

**Technical Implementation:**
14. **Event Task Entity**: task_type, event_id, due_date, status, notes, assigned_to, completed_by, completed_date
15. **Scheduled Job**: Daily cron job creates catering tasks for events 1 month out
16. **REST API**: GET /api/events/{id}/tasks, PUT /api/events/tasks/{taskId}/complete
17. **React Component**: EventTaskList with catering task detail panel
18. **Domain Events**: CateringTaskCreatedEvent, CateringTaskCompletedEvent

**MVP Scope:**
- ✅ Task reminder: "Contact caterer 1 month before"
- ✅ Free-text notes field for menu details
- ✅ Mark task complete when done
- ✅ Show registration count for headcount estimate
- ❌ No dietary preferences collection (not needed per user)
- ❌ No menu selection UI (Phase 2)
- ❌ No caterer email integration (Phase 2)

**Definition of Done:**
- [ ] Catering task automatically created 1 month before event
- [ ] Task appears in organizer task dashboard
- [ ] Email notification sent when task created
- [ ] Menu notes field allows recording caterer details
- [ ] Registration count displayed for headcount planning
- [ ] Task can be marked complete
- [ ] Overdue indicator shows if task incomplete <1 month before event
- [ ] Integration test verifies catering task workflow

**Estimated Duration:** 1 week

---

### Story 5.15: Partner Meeting Coordination (Workflow Step 16)

**User Story:**
As an **organizer**, I want to coordinate partner meetings scheduled on same day as BATbern events, so that we can discuss budgets, review statistics, and brainstorm topics for upcoming events.

**Architecture Integration:**
- **Service**: Partner Coordination Service (Story 2.7)
- **Database**: partner_meetings table (from Story 2.7)
- **Frontend**: React partner meeting coordination interface

**Acceptance Criteria:**

**Meeting Scheduling:**
1. **Meeting Date Field**: Date/time field for partner meeting (defaults to lunchtime on event day)
2. **Same-Day Indicator**: Visual indicator if meeting same day as BATbern event
3. **Meeting Type**: Spring or Autumn partner meeting designation
4. **Attendee List**: Select which partners invited to meeting
5. **Location Field**: Meeting venue (often same as event venue)

**Meeting Agenda:**
6. **Agenda Template**: Pre-filled template with sections:
   - Budget Review (current year spending, next year forecast)
   - Event Statistics (attendance, engagement metrics from last events)
   - Topic Brainstorming (new topic ideas for backlog)
7. **Agenda Customization**: Organizer can edit template for specific meeting
8. **Agenda Sharing**: Send agenda to partner attendees before meeting

**Topic Brainstorming:**
9. **Brainstorming Capture**: During/after meeting, organizer can add topics to backlog directly
10. **Topic Source Tag**: Topics tagged as "From Partner Meeting {date}"
11. **Partner Attribution**: Track which partner suggested which topic
12. **Backlog Integration**: Topics added directly to topic backlog (Story 5.2 integration)

**Meeting Notes:**
13. **Meeting Notes Field**: Free-text notes from meeting discussion
14. **Budget Discussion Notes**: Record budget decisions and allocations
15. **Action Items**: Track follow-up action items from meeting
16. **Meeting History**: View all past partner meetings with notes

**Technical Implementation:**
17. **Partner Meeting Entity**: meeting_date, meeting_type (spring/autumn), location, agenda, notes, attendee_ids
18. **REST API**: POST /api/partner-meetings, GET /api/partner-meetings/history
19. **React Component**: PartnerMeetingCoordinator with agenda template
20. **Topic Backlog Integration**: Call POST /api/topics/backlog from meeting interface
21. **Domain Events**: PartnerMeetingScheduledEvent, TopicAddedFromMeetingEvent

**MVP Scope:**
- ✅ Date/time field (lunchtime same day as event)
- ✅ Agenda template (Budget Review, Event Statistics, Topic Brainstorming)
- ✅ Meeting notes field (free text)
- ✅ Add topics to backlog from meeting (use existing Story 5.2 topic backlog)
- ✅ Use Story 2.7 Partner Service for tracking
- ❌ No calendar integration (Phase 2)
- ❌ No structured budget tracking (Epic 8 - Partner Analytics)

**Definition of Done:**
- [ ] Partner meeting can be scheduled with date/time
- [ ] Meeting type (Spring/Autumn) selectable
- [ ] Agenda template pre-fills with 3 sections
- [ ] Organizer can customize agenda
- [ ] Meeting notes field allows recording discussion
- [ ] Topics can be added to backlog from meeting interface
- [ ] Topics tagged with meeting source
- [ ] Meeting history shows all past partner meetings
- [ ] Integration test verifies partner meeting workflow

**Estimated Duration:** 1.5 weeks

---

## Epic 5 Success Metrics

**Functional Success (End-to-End Workflow):**
- ✅ Organizers can complete full 16-step workflow from topic selection through partner meeting
- ✅ Event creation to publication achievable in timeline with manual speaker coordination
- ✅ All 16 workflow steps functional and tested
- ✅ Organizers can manage events without requiring speaker self-service portal

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

## Implementation Notes2

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
