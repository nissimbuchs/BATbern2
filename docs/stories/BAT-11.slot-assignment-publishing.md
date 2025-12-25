# Story: 5.7 - Slot Assignment & Progressive Publishing

**Linear Issue**: [BAT-11](https://linear.app/batbern/issue/BAT-11/57-slot-assignment-and-progressive-publishing) ← **PRIMARY SOURCE**

**Story File**: This file contains **ONLY** dev implementation notes

---

## ⚠️ IMPORTANT: Story Content Location

This file contains **ONLY** dev implementation notes. For story content, see Linear:

- **User Story**: [Linear description](https://linear.app/batbern/issue/BAT-11/57-slot-assignment-and-progressive-publishing)
- **Acceptance Criteria**: [Linear issue](https://linear.app/batbern/issue/BAT-11/57-slot-assignment-and-progressive-publishing) (see checkboxes)
- **Tasks/Subtasks**: [Linear subtasks](https://linear.app/batbern/issue/BAT-11/57-slot-assignment-and-progressive-publishing)
- **QA Results**: [Linear comments](https://linear.app/batbern/issue/BAT-11/57-slot-assignment-and-progressive-publishing)
- **Status**: [Linear workflow state](https://linear.app/batbern/issue/BAT-11/57-slot-assignment-and-progressive-publishing)

---

## Dev Agent Record

### Agent Model Used
Claude Sonnet 4.5 (claude-sonnet-4-5-20250929)

### Wireframe References

**Architecture Decision**: Slot assignment is a **dedicated separate page** at `/organizer/events/:eventCode/slot-assignment`, NOT integrated into Story 5.8 tabs. Rationale: Workflow complexity requires full screen, focused attention, and strategic decision-making environment.

**Phase 1: CRITICAL Wireframes (Blocks Implementation)**

1. **`docs/wireframes/story-5.7-slot-assignment-page.md`** - Main slot assignment page
   - Dedicated full-page workflow for session timing assignment
   - Three-column layout: Speaker pool | Session timeline grid | Quick actions
   - Drag-and-drop interface with preference match highlighting
   - Progress tracking and success states
   - Route: `/organizer/events/:eventCode/slot-assignment`

2. **`docs/wireframes/story-5.7-navigation-integration.md`** - Navigation flows
   - Entry points: Speakers tab, Overview tab, Publishing tab
   - Call-to-action banners with progress indicators
   - Return navigation: Breadcrumbs, [Back to Event] button
   - Success banner linking to Publishing tab

3. **`docs/wireframes/story-5.7-conflict-resolution-modal.md`** - Conflict detection & resolution
   - Triggered on timing conflicts during drag-drop
   - Conflict types: room_overlap, speaker_double_booked, speaker_unavailable
   - Resolution options with visual timeline of conflicts
   - Severity indicators (error vs warning)

4. **`docs/wireframes/story-5.7-speaker-preference-panel.md`** - Preference display
   - Right drawer panel showing speaker preferences
   - Time-of-day preferences (morning/afternoon/evening)
   - A/V requirements and room setup needs
   - Dynamic match scoring during drag operations
   - Color-coded highlights: Green (80-100%), Yellow (50-79%), Red (<50%)

5. **`docs/wireframes/story-2.3-basic-publishing-engine.md`** (UPDATED) - Publishing controls & validation
   - Publishing timeline visualization
   - Content validation dashboard **with session timing validation**
   - **NEW**: Session Timings validation item (blocks Phase 3 Agenda publish)
   - **NEW**: [Assign Timings] button linking to slot assignment page
   - Live preview (desktop/mobile/print)
   - Publishing mode controls (draft/progressive/complete)
   - Version control with rollback
   - Newsletter notification controls
   - CDN invalidation status display

**Phase 2: Enhanced UX Wireframes**

6. **`docs/wireframes/story-5.7-bulk-auto-assignment.md`** - Auto-assignment feature
   - Algorithm selection modal (optimize for preferences vs expertise vs balanced)
   - Preview assignments with match scores
   - Side-by-side comparison of current vs proposed
   - Conflict detection in bulk operations

**Phase 3: Polish Wireframes**

7. **`docs/wireframes/story-5.7-accessibility-features.md`** - Keyboard navigation & ARIA
   - Keyboard shortcuts for drag-drop (arrow keys + Enter)
   - ARIA labels and announcements
   - Focus management and screen reader support
   - High-contrast mode and reduced motion support

8. **`docs/wireframes/story-5.7-responsive-design.md`** - Mobile/tablet adaptations
   - Desktop: Full three-column layout
   - Tablet: Collapsible sidebar, two-column main
   - Mobile: Single column, bottom sheet for speaker pool

9. **`docs/wireframes/story-5.7-celebration-animations.md`** - Success animations
   - Confetti animation on completing all assignments
   - Progress bar fill animations
   - Success badge pulse effects

**Legacy Reference** (for comparison):
- `docs/wireframes/story-3.1-speaker-matching-interface.md` - Original speaker matching concept
  - Shows early drag-drop pattern (now superseded by story-5.7-slot-assignment-page.md)
  - Demonstrates initial UI thinking for slot assignment
  - Use as reference for drag interaction patterns only

### Template References

**Implementation Patterns to Use**:
{List applicable templates from docs/templates/README.md}

**Backend Templates**:
- State Machine Pattern: For workflow state transitions
- Service Layer Pattern: For business logic separation
- Repository Pattern: For data access

**Frontend Templates**:
- Drag-and-Drop Component: React DnD integration (see wireframe story-3.1)
- Dashboard Widget Pattern: Publishing controls (see wireframe story-2.3)
- Timeline Visualization: Event schedule display

**Existing Code References**:
- Similar to: Story 5.1a (Workflow State Machine Foundation)
- Similar to: Story 5.4 (Speaker Status Management - state transitions)
- Similar to: Story 5.5 (Task System - scheduled jobs)

### Test Implementation Details (HOW to test)

**CRITICAL**: All backend integration tests MUST use PostgreSQL via Testcontainers. NEVER use H2 - it creates false confidence and hides PostgreSQL-specific issues (JSONB types, functions, etc.).

#### Test File Locations (Exact Paths)

**Frontend Tests**:
- `web-frontend/src/components/SlotAssignment/DragDropSlotAssignment/DragDropSlotAssignment.test.tsx`
- `web-frontend/src/components/SlotAssignment/SpeakerPreferencePanel/SpeakerPreferencePanel.test.tsx`
- `web-frontend/src/components/SlotAssignment/ConflictDetectionAlert/ConflictDetectionAlert.test.tsx`
- `web-frontend/src/components/SlotAssignment/UnassignedSpeakersList/UnassignedSpeakersList.test.tsx`
- `web-frontend/src/components/Publishing/PublishingTimeline/PublishingTimeline.test.tsx`
- `web-frontend/src/components/Publishing/ValidationDashboard/ValidationDashboard.test.tsx`
- `web-frontend/src/components/Publishing/LivePreview/LivePreview.test.tsx`
- `web-frontend/src/components/Publishing/PublishingControls/PublishingControls.test.tsx`
- `web-frontend/src/components/Publishing/VersionControl/VersionControl.test.tsx`
- `web-frontend/src/hooks/useSlotAssignment/useSlotAssignment.test.ts`
- `web-frontend/src/hooks/usePublishing/usePublishing.test.ts`
- `web-frontend/src/services/slotAssignmentService/slotAssignmentService.test.ts`
- `web-frontend/src/services/publishingService/publishingService.test.ts`

**Backend Tests**:
- Unit: `services/event-management-service/src/test/unit/service/SlotGenerationServiceTest.java`
- Unit: `services/event-management-service/src/test/unit/service/SlotAssignmentServiceTest.java`
- Unit: `services/event-management-service/src/test/unit/service/ConflictDetectionServiceTest.java`
- Unit: `services/event-management-service/src/test/unit/service/PreferenceMatchingAlgorithmTest.java`
- Unit: `services/event-management-service/src/test/unit/service/PublishingPhaseServiceTest.java`
- Unit: `services/event-management-service/src/test/unit/service/CDNInvalidationServiceTest.java`
- Unit: `services/event-management-service/src/test/unit/service/VersionTrackingServiceTest.java`
- Integration: `services/event-management-service/src/test/integration/controller/SlotAssignmentControllerIntegrationTest.java`
- Integration: `services/event-management-service/src/test/integration/controller/PublishingControllerIntegrationTest.java`
- Integration: `services/event-management-service/src/test/integration/workflow/SlotAssignmentWorkflowIntegrationTest.java`
- Integration: `services/event-management-service/src/test/integration/workflow/ProgressivePublishingWorkflowIntegrationTest.java`

**E2E Tests**:
- `e2e/workflows/slot-assignment/slot-assignment-workflow.spec.ts`
- `e2e/workflows/progressive-publishing/progressive-publishing-workflow.spec.ts`

#### Test Data & Mocks Configuration

**Test Data Builders**:
- SessionTestDataBuilder for creating test sessions (used as slots)
- SessionTimingHistoryTestDataBuilder for timing change history tests
- PublishingVersionTestDataBuilder for version tracking tests
- SpeakerPreferenceTestDataBuilder for preference matching tests

**Mock Services**:
- Mock CloudFront invalidation API responses
- Mock AWS SES responses for newsletter distribution
- Mock EventBridge scheduled rule triggers

**Test Containers (MANDATORY)**:
- PostgreSQL 16 Alpine via Testcontainers for ALL integration tests
  - All integration tests MUST extend AbstractIntegrationTest
  - Use singleton pattern with withReuse(true) for performance
  - Enable Flyway migrations for production parity
- LocalStack for AWS service mocks (CloudFront, SES, EventBridge)

**Test Configuration**:
```properties
# application-test.properties
spring.datasource.driver-class-name=org.postgresql.Driver
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.hibernate.ddl-auto=validate
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
spring.flyway.baseline-on-migrate=true

# CloudFront mock endpoint
aws.cloudfront.distribution-id=E1234567890ABC
aws.cloudfront.endpoint=http://localhost:4566  # LocalStack

# SES mock endpoint
aws.ses.endpoint=http://localhost:4566  # LocalStack

# EventBridge mock endpoint
aws.eventbridge.endpoint=http://localhost:4566  # LocalStack
```

### Story-Specific Implementation

**Deviations from Templates** (max 100 lines):
```java
// ONLY code that differs from templates
// To be filled during implementation

// Example: Custom slot assignment algorithm
public class PreferenceMatchingAlgorithm {
    /**
     * Assigns speakers to slots based on:
     * 1. Speaker time preferences (morning/afternoon)
     * 2. Topic flow optimization
     * 3. Conflict avoidance
     * 4. Room setup requirements
     */
    public Map<Slot, Speaker> assignSpeakersToSlots(
        List<Slot> availableSlots,
        List<Speaker> confirmedSpeakers,
        Map<Speaker, SpeakerPreferences> preferences
    ) {
        // Implementation details to be added
    }
}
```

```typescript
// Example: Drag-and-drop state management
export const useSlotAssignment = (eventId: string) => {
  const [slots, setSlots] = useState<Slot[]>([]);
  const [unassignedSpeakers, setUnassignedSpeakers] = useState<Speaker[]>([]);

  const handleDrop = useCallback((speakerId: string, slotId: string) => {
    // Call API to assign speaker to slot
    // Update local state optimistically
    // Revert on error
  }, [eventId]);

  // Implementation details to be added
};
```

### API Contracts (OpenAPI Excerpts)

> **📋 API Design Notes:**
> - Uses **meaningful IDs** (eventCode, sessionSlug) per ADR-003
> - Sessions ARE slots - timing assigned via PATCH to sessions
> - Speaker-to-session link already exists (speaker_pool.session_id from V20)
> - This story adds timing assignment + publishing capabilities

```yaml
# Slot Assignment & Publishing APIs
paths:
  # ============================================================================
  # Session Timing Assignment (Slot Assignment)
  # ============================================================================

  /api/v1/events/{eventCode}/sessions/unassigned:
    get:
      summary: Get sessions without timing (placeholder sessions)
      description: |
        Returns sessions created when speakers accepted but not yet assigned timing.
        These are placeholder sessions with null startTime/endTime/room.
        Used by drag-and-drop slot assignment UI to show unscheduled sessions.
      operationId: getUnassignedSessions
      tags:
        - Sessions
        - Slot Assignment
      parameters:
        - name: eventCode
          in: path
          required: true
          schema:
            type: string
            pattern: '^BATbern[0-9]+$'
            example: BATbern142
      responses:
        '200':
          description: Unassigned sessions retrieved successfully
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/Session'
        '404':
          description: Event not found

  /api/v1/events/{eventCode}/sessions/{sessionSlug}/timing:
    patch:
      summary: Assign timing to session (slot assignment)
      description: |
        Assigns startTime/endTime/room to a session during slot assignment workflow.
        Updates placeholder session with timing details.
        Creates audit record in session_timing_history table.
      operationId: assignSessionTiming
      tags:
        - Sessions
        - Slot Assignment
      parameters:
        - name: eventCode
          in: path
          required: true
          schema:
            type: string
            pattern: '^BATbern[0-9]+$'
            example: BATbern142
        - name: sessionSlug
          in: path
          required: true
          schema:
            type: string
            pattern: '^[a-z0-9]+(-[a-z0-9]+)*$'
            example: john-doe-acme-corp
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/SessionTimingRequest'
      responses:
        '200':
          description: Timing assigned successfully
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/Session'
        '409':
          description: Timing conflict detected (overlapping sessions, speaker unavailable)
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/TimingConflictError'
        '404':
          description: Event or session not found

  /api/v1/events/{eventCode}/sessions/bulk-timing:
    post:
      summary: Bulk assign timing to multiple sessions
      description: |
        Assigns timing to multiple sessions at once.
        Used for auto-assignment based on speaker preferences.
        Validates for conflicts before applying changes (atomic operation).
      operationId: bulkAssignSessionTiming
      tags:
        - Sessions
        - Slot Assignment
      parameters:
        - name: eventCode
          in: path
          required: true
          schema:
            type: string
            pattern: '^BATbern[0-9]+$'
            example: BATbern142
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/BulkTimingRequest'
      responses:
        '200':
          description: Bulk timing assigned successfully
          content:
            application/json:
              schema:
                type: object
                properties:
                  assignedCount:
                    type: integer
                  sessions:
                    type: array
                    items:
                      $ref: '#/components/schemas/Session'
        '409':
          description: Conflicts detected - no changes applied
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/BulkTimingConflictError'

  /api/v1/events/{eventCode}/sessions/conflicts:
    get:
      summary: Detect session timing conflicts
      description: |
        Analyzes current session timing for conflicts:
        - Overlapping sessions in same room
        - Speaker double-booked (same speaker in overlapping sessions)
        - Sessions scheduled during speaker unavailable times
      operationId: detectTimingConflicts
      tags:
        - Sessions
        - Slot Assignment
      parameters:
        - name: eventCode
          in: path
          required: true
          schema:
            type: string
            pattern: '^BATbern[0-9]+$'
            example: BATbern142
      responses:
        '200':
          description: Conflict analysis completed
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ConflictAnalysisResponse'

  # ============================================================================
  # Progressive Publishing
  # ============================================================================

  /api/v1/events/{eventCode}/publish/{phase}:
    post:
      summary: Publish event content phase
      description: |
        Publishes a specific phase of event content to public website.
        Phases: topic → speakers → agenda (progressive disclosure)
        Invalidates CloudFront CDN cache and creates version snapshot.
      operationId: publishPhase
      tags:
        - Publishing
      parameters:
        - name: eventCode
          in: path
          required: true
          schema:
            type: string
            pattern: '^BATbern[0-9]+$'
            example: BATbern142
        - name: phase
          in: path
          required: true
          schema:
            type: string
            enum: [topic, speakers, agenda]
            description: |
              - topic: Event topic, date, venue
              - speakers: Speaker lineup (requires speakers accepted)
              - agenda: Complete schedule (requires all sessions have timing)
      requestBody:
        required: false
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/PublishRequest'
      responses:
        '200':
          description: Phase published successfully
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/PublishingVersion'
        '422':
          description: Validation failed - content not ready for this phase
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/PublishValidationError'

  /api/v1/events/{eventCode}/publishing/versions:
    get:
      summary: List publishing version history
      description: Get all publishing versions for rollback capability
      operationId: listPublishingVersions
      tags:
        - Publishing
      parameters:
        - name: eventCode
          in: path
          required: true
          schema:
            type: string
            pattern: '^BATbern[0-9]+$'
            example: BATbern142
      responses:
        '200':
          description: Versions retrieved successfully
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/PublishingVersion'

  /api/v1/events/{eventCode}/publishing/versions/{versionNumber}/rollback:
    post:
      summary: Rollback to previous publishing version
      description: |
        Restores content to a previous published version.
        Invalidates CDN cache and creates new version entry marking rollback.
      operationId: rollbackVersion
      tags:
        - Publishing
      parameters:
        - name: eventCode
          in: path
          required: true
          schema:
            type: string
            pattern: '^BATbern[0-9]+$'
            example: BATbern142
        - name: versionNumber
          in: path
          required: true
          schema:
            type: integer
            example: 3
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/RollbackRequest'
      responses:
        '200':
          description: Version rolled back successfully
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/PublishingVersion'
        '404':
          description: Version not found

components:
  schemas:
    # ============================================================================
    # Session Timing Assignment Schemas
    # ============================================================================

    Session:
      type: object
      description: |
        Session entity from events-api.openapi.yml
        Supports placeholder sessions (null timing) and fully scheduled sessions
      required:
        - sessionSlug
        - eventCode
        - title
      properties:
        sessionSlug:
          type: string
          pattern: '^[a-z0-9]+(-[a-z0-9]+)*$'
          example: john-doe-acme-corp
        eventCode:
          type: string
          pattern: '^BATbern[0-9]+$'
          example: BATbern142
        title:
          type: string
          example: John Doe - Acme Corp
        description:
          type: string
        sessionType:
          type: string
          nullable: true
          enum: [keynote, presentation, workshop, panel_discussion, networking, break, lunch]
        startTime:
          type: string
          format: date-time
          nullable: true
          description: Null for placeholder sessions
        endTime:
          type: string
          format: date-time
          nullable: true
          description: Null for placeholder sessions
        room:
          type: string
          nullable: true
          example: Main Hall
        capacity:
          type: integer
        speakers:
          type: array
          items:
            type: object
            properties:
              username:
                type: string
              displayName:
                type: string
              companyName:
                type: string

    SessionTimingRequest:
      type: object
      description: Request to assign timing to a session during slot assignment
      required:
        - startTime
        - endTime
      properties:
        startTime:
          type: string
          format: date-time
          example: "2025-05-15T09:00:00Z"
        endTime:
          type: string
          format: date-time
          example: "2025-05-15T09:45:00Z"
        room:
          type: string
          example: Main Hall
        sessionType:
          type: string
          enum: [keynote, presentation, workshop, panel_discussion, networking, break, lunch]
          description: Optional - update session type during timing assignment
        changeReason:
          type: string
          enum: [initial_assignment, drag_drop_reassignment, conflict_resolution, preference_matching, manual_adjustment]
          default: drag_drop_reassignment
        notes:
          type: string
          maxLength: 500

    BulkTimingRequest:
      type: object
      description: Bulk assign timing to multiple sessions (auto-assignment)
      required:
        - assignments
      properties:
        assignments:
          type: array
          items:
            type: object
            required:
              - sessionSlug
              - startTime
              - endTime
            properties:
              sessionSlug:
                type: string
              startTime:
                type: string
                format: date-time
              endTime:
                type: string
                format: date-time
              room:
                type: string
              sessionType:
                type: string
        changeReason:
          type: string
          enum: [preference_matching, manual_adjustment]
          default: preference_matching

    TimingConflictError:
      type: object
      description: Timing conflict detected during assignment
      properties:
        error:
          type: string
          example: TIMING_CONFLICT
        message:
          type: string
          example: Session timing conflicts with existing schedule
        conflicts:
          type: array
          items:
            type: object
            properties:
              type:
                type: string
                enum: [room_overlap, speaker_double_booked, speaker_unavailable]
              conflictingSessionSlug:
                type: string
              conflictingTimeRange:
                type: object
                properties:
                  start:
                    type: string
                    format: date-time
                  end:
                    type: string
                    format: date-time
              details:
                type: string

    BulkTimingConflictError:
      type: object
      description: Conflicts detected in bulk timing assignment
      properties:
        error:
          type: string
          example: BULK_TIMING_CONFLICTS
        message:
          type: string
          example: Multiple conflicts detected - no changes applied
        conflictCount:
          type: integer
        conflicts:
          type: array
          items:
            type: object
            properties:
              sessionSlug:
                type: string
              conflictType:
                type: string
              details:
                type: string

    ConflictAnalysisResponse:
      type: object
      description: Complete conflict analysis for event sessions
      properties:
        hasConflicts:
          type: boolean
        conflictCount:
          type: integer
        conflicts:
          type: array
          items:
            type: object
            properties:
              sessionSlug:
                type: string
              conflictType:
                type: string
                enum: [room_overlap, speaker_double_booked, speaker_unavailable]
              severity:
                type: string
                enum: [error, warning]
              affectedSessions:
                type: array
                items:
                  type: string
              timeRange:
                type: object
                properties:
                  start:
                    type: string
                    format: date-time
                  end:
                    type: string
                    format: date-time
              resolution:
                type: string
                description: Suggested resolution

    # ============================================================================
    # Progressive Publishing Schemas
    # ============================================================================

    PublishRequest:
      type: object
      description: Request to publish a phase with optional validation override
      properties:
        mode:
          type: string
          enum: [draft, progressive, complete]
          default: progressive
          description: |
            - draft: Preview only, not live
            - progressive: Publish this phase only
            - complete: Publish all phases
        approvalOverride:
          type: boolean
          default: false
          description: Override approval requirements (organizer role only)
        notifySubscribers:
          type: boolean
          default: true
          description: Send newsletter to subscribers when publishing

    PublishingVersion:
      type: object
      description: Publishing version snapshot for rollback capability
      properties:
        id:
          type: string
          format: uuid
        eventCode:
          type: string
          pattern: '^BATbern[0-9]+$'
          example: BATbern142
        versionNumber:
          type: integer
          example: 1
        publishedPhase:
          type: string
          enum: [topic, speakers, agenda]
        publishedAt:
          type: string
          format: date-time
        publishedBy:
          type: string
          description: Username of organizer who published
        cdnInvalidationId:
          type: string
          description: CloudFront invalidation ID for cache busting
        cdnInvalidationStatus:
          type: string
          enum: [pending, in_progress, completed, failed]
        contentSnapshot:
          type: object
          description: JSON snapshot of published content for rollback
        isCurrent:
          type: boolean
          description: Whether this is the currently published version
        rolledBackAt:
          type: string
          format: date-time
          nullable: true
        rolledBackBy:
          type: string
          nullable: true

    PublishValidationError:
      type: object
      description: Content validation failed for publishing phase
      properties:
        error:
          type: string
          example: PUBLISH_VALIDATION_FAILED
        message:
          type: string
          example: Content not ready for this publishing phase
        phase:
          type: string
          enum: [topic, speakers, agenda]
        validationErrors:
          type: array
          items:
            type: object
            properties:
              field:
                type: string
                example: sessions.timing
              message:
                type: string
                example: Not all sessions have timing assigned
              requirement:
                type: string
                example: All sessions must have startTime and endTime for agenda phase

    RollbackRequest:
      type: object
      description: Request to rollback to previous version
      required:
        - reason
      properties:
        reason:
          type: string
          minLength: 10
          maxLength: 500
          description: Explanation for rollback (audit trail)
          example: Incorrect speaker information published
```

### Database Schema (SQL)

```sql
-- V28__Add_slot_assignment_and_publishing.sql
-- Story BAT-11: Slot Assignment & Progressive Publishing
--
-- IMPORTANT: Sessions table (V2) already serves as our slot model
-- V20 added speaker_pool.session_id FK to link speakers to sessions
-- V21 made session times nullable for placeholder sessions
--
-- This migration adds ONLY new features not covered by existing tables:
-- 1. Speaker time preferences for slot assignment algorithm
-- 2. Session timing history for audit trail (not speaker assignment history)
-- 3. Publishing versioning system with rollback capability
-- 4. Auto-publish configuration per event
--
-- REMOVED from original proposal:
-- - event_slots table (redundant with sessions table)
-- - slot_assignments table (speaker assignment tracked in speaker_status_history)

-- ============================================================================
-- SECTION 1: Slot Assignment Features
-- ============================================================================

-- Session Timing History Table (NEW - audit trail for timing changes)
-- Tracks when session times are set/changed during drag-and-drop slot assignment
-- NOTE: This is different from speaker assignment (tracked in speaker_status_history)
CREATE TABLE IF NOT EXISTS session_timing_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,

    -- Timing changes
    previous_start_time TIMESTAMP WITH TIME ZONE,
    previous_end_time TIMESTAMP WITH TIME ZONE,
    previous_room VARCHAR(100),
    new_start_time TIMESTAMP WITH TIME ZONE,
    new_end_time TIMESTAMP WITH TIME ZONE,
    new_room VARCHAR(100),

    -- Change metadata
    changed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    changed_by VARCHAR(255) NOT NULL, -- Username of organizer who made the change
    change_reason VARCHAR(50) CHECK (change_reason IN (
        'initial_assignment',      -- First time slot assigned
        'drag_drop_reassignment',  -- Organizer drag-and-drop
        'conflict_resolution',     -- Resolving speaker/room conflict
        'preference_matching',     -- Algorithm-based assignment
        'manual_adjustment'        -- Manual override
    )),
    notes TEXT -- Optional notes about the change
);

CREATE INDEX idx_session_timing_history_session ON session_timing_history(session_id);
CREATE INDEX idx_session_timing_history_changed_at ON session_timing_history(changed_at DESC);
CREATE INDEX idx_session_timing_history_changed_by ON session_timing_history(changed_by);

COMMENT ON TABLE session_timing_history IS
'Audit trail for session timing changes during slot assignment - tracks all drag-and-drop and algorithm-based time assignments';

-- Speaker Preferences Table (for slot preference tracking)
CREATE TABLE IF NOT EXISTS speaker_slot_preferences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    speaker_id UUID NOT NULL REFERENCES speaker_pool(id) ON DELETE CASCADE,
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    preferred_time_of_day VARCHAR(50) CHECK (preferred_time_of_day IN ('morning', 'afternoon', 'evening', 'any')),
    avoid_times JSONB DEFAULT '[]', -- Array of time ranges to avoid
    av_requirements JSONB DEFAULT '{}',
    room_setup_notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_speaker_event_preference UNIQUE(speaker_id, event_id)
);

CREATE INDEX idx_speaker_preferences_speaker_event ON speaker_slot_preferences(speaker_id, event_id);

-- Publishing Versions Table
CREATE TABLE IF NOT EXISTS publishing_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    version_number INTEGER NOT NULL,
    published_phase VARCHAR(50) NOT NULL CHECK (published_phase IN ('topic', 'speakers', 'agenda')),
    published_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    published_by VARCHAR(255) NOT NULL, -- username
    cdn_invalidation_id VARCHAR(255),
    cdn_invalidation_status VARCHAR(50) CHECK (cdn_invalidation_status IN ('pending', 'in_progress', 'completed', 'failed')),
    content_snapshot JSONB NOT NULL, -- Snapshot of published content for rollback
    is_current BOOLEAN DEFAULT true,
    rolled_back_at TIMESTAMP WITH TIME ZONE,
    rolled_back_by VARCHAR(255),
    rollback_reason TEXT,
    CONSTRAINT unique_event_version UNIQUE(event_id, version_number),
    CONSTRAINT unique_current_version UNIQUE(event_id, is_current) WHERE is_current = true
);

CREATE INDEX idx_publishing_versions_event_id ON publishing_versions(event_id);
CREATE INDEX idx_publishing_versions_current ON publishing_versions(is_current) WHERE is_current = true;
CREATE INDEX idx_publishing_versions_published_at ON publishing_versions(published_at);

-- Publishing Configuration Table
CREATE TABLE IF NOT EXISTS publishing_config (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    auto_publish_speakers BOOLEAN DEFAULT true,
    auto_publish_speakers_days_before INTEGER DEFAULT 30,
    auto_publish_agenda BOOLEAN DEFAULT true,
    auto_publish_agenda_days_before INTEGER DEFAULT 14,
    requires_approval BOOLEAN DEFAULT false,
    preview_url VARCHAR(1024),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_event_publishing_config UNIQUE(event_id)
);

CREATE INDEX idx_publishing_config_event_id ON publishing_config(event_id);

-- Add publishing state to events table (if not exists from Story 5.1a)
-- This may already exist from V20 or V21, check before running
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'events' AND column_name = 'current_published_phase'
    ) THEN
        ALTER TABLE events
        ADD COLUMN current_published_phase VARCHAR(50)
        CHECK (current_published_phase IN ('none', 'topic', 'speakers', 'agenda'));

        ALTER TABLE events
        ADD COLUMN last_published_at TIMESTAMP WITH TIME ZONE;
    END IF;
END $$;

-- Trigger to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_speaker_preferences_updated_at BEFORE UPDATE ON speaker_slot_preferences
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_publishing_config_updated_at BEFORE UPDATE ON publishing_config
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
```

### Implementation Approach

**Total Estimated Effort**: 16-18 development days (~3.5 weeks)

**Phase 1: Database & Backend Foundation (Days 1-2) - CRITICAL PATH**

*Dependencies: None*
*Deliverables: Migration V28, JPA entities, repositories*

Day 1:
- [ ] Create Flyway migration V28 for slot assignment and publishing tables
  - `session_timing_history` table (audit trail for timing changes)
  - `speaker_slot_preferences` table (time-of-day preferences, A/V requirements)
  - `publishing_versions` table (version snapshots for rollback)
  - `publishing_config` table (auto-publish configuration)
  - Add `current_published_phase` to events table if not exists
- [ ] Create JPA entities with proper relationships
  - SessionTimingHistoryEntity
  - SpeakerSlotPreferenceEntity
  - PublishingVersionEntity
  - PublishingConfigEntity
  - Update SessionEntity to support nullable timing (already done in V21)
- [ ] Create repositories with custom queries
  - SessionTimingHistoryRepository (findBySessionId, findRecentChanges)
  - SpeakerSlotPreferenceRepository (findBySpeakerAndEvent)
  - PublishingVersionRepository (findByEventIdOrderByVersionDesc, findCurrentVersion)
  - SessionRepository custom query: findUnassignedSessions (startTime IS NULL)

Day 2:
- [ ] Write unit tests for entities and repositories (Testcontainers PostgreSQL)
- [ ] Test migration rollback and data integrity
- [ ] Verify JSONB columns work correctly (avoid_times, av_requirements, content_snapshot)

**Phase 2: Slot Assignment Backend (Days 3-5) - CRITICAL PATH**

*Dependencies: Phase 1 complete*
*Deliverables: Session timing APIs, conflict detection, preference matching*

Day 3:
- [ ] Implement SessionTimingService
  - `assignTiming(sessionSlug, startTime, endTime, room)` - updates session, creates history record
  - `unassignTiming(sessionSlug)` - sets timing to null
  - `getUnassignedSessions(eventCode)` - returns placeholder sessions
  - `getTimingHistory(sessionSlug)` - returns audit trail
- [ ] Implement ConflictDetectionService
  - `detectRoomOverlap(eventCode, startTime, endTime, room)` - checks room availability
  - `detectSpeakerDoubleBooking(speakerId, startTime, endTime)` - checks speaker availability
  - `detectSpeakerPreferenceConflicts(speakerId, startTime)` - checks against preferences
  - `analyzeAllConflicts(eventCode)` - comprehensive conflict analysis
- [ ] Write unit tests with mocked repositories

Day 4:
- [ ] Implement PreferenceMatchingAlgorithm
  - `calculateMatchScore(speaker, session)` - percentage match based on preferences
  - `findBestSlots(speakerId, availableSlots)` - ranked list of suitable slots
  - `autoAssignSpeakers(eventCode, strategy)` - bulk assignment algorithm
    - Strategies: PREFERENCE_OPTIMIZED, EXPERTISE_OPTIMIZED, BALANCED
- [ ] Implement BulkTimingAssignmentService
  - `bulkAssignTiming(assignments)` - atomic bulk operation
  - Validates all assignments before applying (no partial failures)
- [ ] Write unit tests for algorithm logic

Day 5:
- [ ] Implement REST controllers
  - GET `/api/v1/events/{eventCode}/sessions/unassigned`
  - PATCH `/api/v1/events/{eventCode}/sessions/{sessionSlug}/timing`
  - POST `/api/v1/events/{eventCode}/sessions/bulk-timing`
  - GET `/api/v1/events/{eventCode}/sessions/conflicts`
- [ ] Write integration tests (Testcontainers, MockMvc)
  - Test timing assignment happy path
  - Test conflict detection (room overlap, speaker double-booking)
  - Test bulk assignment with rollback on conflicts
  - Test preference matching calculations
- [ ] Test API contracts match OpenAPI spec (lines 186-793)

**Phase 3: Publishing Engine Backend (Days 6-8)**

*Dependencies: Phase 2 complete (session timing APIs needed for validation)*
*Deliverables: Publishing APIs, CDN invalidation, version control, auto-publish*

Day 6:
- [ ] Implement PublishingPhaseService
  - `publishPhase(eventCode, phase, mode)` - validates and publishes content
    - Validation rules:
      - Phase 1 (Topic): Event has topic, date, venue
      - Phase 2 (Speakers): ≥1 speaker accepted
      - Phase 3 (Agenda): ALL sessions have timing (blocks if any null)
  - `unpublishPhase(eventCode, phase)` - removes from public view
  - `getPublishingStatus(eventCode)` - current published phase and validation state
- [ ] Implement ContentSnapshotService
  - `createSnapshot(eventCode, phase)` - captures event data as JSON for version
  - `restoreFromSnapshot(snapshotJson)` - rollback to previous version
- [ ] Write unit tests for validation logic

Day 7:
- [ ] Implement CDNInvalidationService
  - `invalidateCache(distributionId, paths)` - calls CloudFront API
  - `getInvalidationStatus(invalidationId)` - polls for completion
  - `waitForCompletion(invalidationId, timeout)` - async wait
- [ ] Implement NewsletterService (if not exists from Story 5.5)
  - `sendPublishNotification(eventCode, phase, recipientList)` - SES integration
  - Uses existing newsletter templates from Story 5.5
- [ ] Implement VersionTrackingService
  - `createVersion(eventCode, phase, contentSnapshot)` - saves version record
  - `getVersionHistory(eventCode)` - returns all versions
  - `rollbackToVersion(eventCode, versionNumber, reason)` - restores previous version
- [ ] Set up LocalStack for AWS service mocks (CloudFront, SES)
- [ ] Write integration tests with LocalStack

Day 8:
- [ ] Implement AutoPublishSchedulingService
  - `scheduleAutoPublish(eventCode, phase, targetDate)` - creates EventBridge rule
  - `cancelAutoPublish(eventCode, phase)` - removes rule
  - Event handler: `handleAutoPublishTrigger(eventCode, phase)` - executes publish
- [ ] Implement REST controllers
  - POST `/api/v1/events/{eventCode}/publish/{phase}`
  - GET `/api/v1/events/{eventCode}/publishing/versions`
  - POST `/api/v1/events/{eventCode}/publishing/versions/{versionNumber}/rollback`
- [ ] Write integration tests for publishing workflow
  - Test phase progression (topic → speakers → agenda)
  - Test validation blocking (agenda blocked without timings)
  - Test version creation and rollback
  - Test CDN invalidation (mocked)
  - Test scheduled auto-publish (mocked EventBridge)
- [ ] Test API contracts match OpenAPI spec

**Phase 4: Slot Assignment Frontend (Days 9-11) - PARALLEL WITH PHASE 3**

*Dependencies: Phase 2 backend APIs available*
*Deliverables: Slot assignment page, drag-drop, preferences, conflicts*

Day 9:
- [ ] Set up React DnD library (@dnd-kit/core, @dnd-kit/sortable)
- [ ] Create page structure: `SlotAssignmentPage.tsx`
  - Three-column layout (wireframe: story-5.7-slot-assignment-page.md lines 19-78)
  - Left: SpeakerPoolSidebar (300px)
  - Center: SessionTimelineGrid (flexible width)
  - Right: QuickActionsPanel (collapsible)
- [ ] Implement SpeakerPoolSidebar component
  - Progress indicator: "X of Y assigned (Z%)"
  - Filters: [All] [Assigned] [Unassigned]
  - Draggable speaker cards with grab handle
  - Unassigned badge: "🔶 N Remaining"
  - [View Preferences] button per speaker
- [ ] Create API service: `slotAssignmentService.ts`
  - `getUnassignedSessions(eventCode)`
  - `assignSessionTiming(eventCode, sessionSlug, timing)`
  - `bulkAssignTiming(eventCode, assignments)`
  - `detectConflicts(eventCode)`
- [ ] Wire SpeakerPoolSidebar to API, display speaker list

Day 10:
- [ ] Implement SessionTimelineGrid component
  - Multi-day timeline view (wireframe lines 79-166)
  - Time slots: 08:00-20:00 hourly rows
  - Room columns: dynamic based on event venue
  - Droppable zones for each slot
  - Placeholder sessions (gray, dashed border)
  - Assigned sessions (colored, speaker name visible)
- [ ] Implement drag-and-drop logic
  - `useDragAndDrop` hook:
    - `handleDragStart` - shows preference match highlights
    - `handleDragOver` - updates drop zone visuals
    - `handleDrop` - calls API, updates state optimistically
    - `handleDragEnd` - clears highlights
  - Preference match highlighting during drag (green/yellow/red)
  - Optimistic updates with rollback on error
- [ ] Implement ConflictDetectionAlert modal (wireframe: story-5.7-conflict-resolution-modal.md)
  - Triggered on API 409 Conflict error response
  - Show conflict type (room_overlap, speaker_double_booked, speaker_unavailable)
  - Visual timeline showing overlap (lines 426-470)
  - Resolution options:
    - [Find Alternative Slot] - suggests available slots
    - [Change Room] - assigns to different room
    - [Reassign Other Session] - moves conflicting session
    - [Override Warning] - force assignment (warnings only, not errors)
    - [Cancel] - abort assignment
- [ ] Write component tests (Vitest, React Testing Library)

Day 11:
- [ ] Implement SpeakerPreferencePanel drawer (wireframe: story-5.7-speaker-preference-panel.md)
  - Right drawer (400px) sliding on [View Preferences] click
  - Sections:
    - Time preferences: Morning/Afternoon/Evening (preferred/neutral/avoid icons)
    - Specific avoid times: List of date-time ranges
    - A/V requirements: Checkboxes for microphone, projector, recording, etc.
    - Room setup: Standing desk, natural light, flip chart, notes field
    - Dynamic match score: Shows % match when hovering over session slot
  - Color-coded match indicators (lines 601-613)
  - [Assign to Current Slot] [Find Best Match] buttons
- [ ] Implement QuickActionsPanel
  - Session summary: "X total, Y assigned, Z pending"
  - [Auto-Assign All] button → opens BulkAutoAssignmentModal
  - [Clear All Assignments] button (with confirmation)
  - [Export Schedule PDF] button (generates PDF)
- [ ] Implement BulkAutoAssignmentModal (wireframe: story-5.7-bulk-auto-assignment.md)
  - Step 1: Algorithm selection (preferences/expertise/balanced)
  - Step 2: Preview assignments with match scores
  - Step 3: Confirmation and apply
- [ ] Implement navigation integration (wireframe: story-5.7-navigation-integration.md)
  - Entry points from Speakers tab, Overview tab, Publishing tab
  - Breadcrumb: "Event Management > BATbern 2025 > Slot Assignment"
  - [Back to Event] button
  - Success banner: "✓ All timings assigned! [Go to Publishing Tab →]"
- [ ] Write integration tests for full workflow

**Phase 5: Publishing Frontend (Days 12-14) - PARALLEL WITH PHASE 4**

*Dependencies: Phase 3 backend APIs available*
*Deliverables: Publishing tab enhancements, validation, preview, version control*

Day 12:
- [ ] Update EventPublishingTab component (existing from Story 5.8)
- [ ] Enhance ValidationDashboard with session timing validation
  - Add new validation item: "Session Timings"
    - Status: "Ready (X/Y sessions assigned)" or "Incomplete (X/Y sessions)"
    - Expandable sub-items showing unassigned sessions (wireframe: story-2.3 lines 47-50)
    - [Assign Timings] button → `/organizer/events/:eventCode/slot-assignment`
  - Blocking logic: Phase 3 (Agenda) publish disabled if any session lacks timing
  - Visual: ⚠️ icon if incomplete, ✓ if all assigned (line 47)
- [ ] Implement PublishingTimeline component
  - Phases: Topic → Speakers → Agenda → Updates
  - Milestone markers with dates
  - Current phase highlight
  - Auto-publish schedule display
- [ ] Write component tests

Day 13:
- [ ] Implement LivePreview component
  - Device toggle: [Desktop] [Mobile] [Print]
  - Preview URL generation with mode parameter
  - Iframe integration for preview display
  - Refresh on content changes
- [ ] Implement PublishingControls component
  - Mode selection: Draft / Progressive / Complete (radio buttons)
  - [Publish Phase] button (enabled only if validation passes)
  - [Schedule Publish] with date-time picker
  - Newsletter toggle: "☑ Notify subscribers when publishing"
  - [Preview Newsletter] button
- [ ] Implement NewsletterPreviewModal (wireframe: story-2.3 lines 744-766)
  - Subject line preview
  - Email body preview (formatted HTML)
  - [Send Test Email] button
  - Subscriber count: "Will send to X active subscribers"
- [ ] Write component tests

Day 14:
- [ ] Implement VersionControl component
  - Version history table:
    - Columns: Version | Published | Phase | Status | Actions
    - Show: version number, publish timestamp, phase, CDN status
    - [Rollback] button per version
  - RollbackConfirmationModal with reason field (required, 10-500 chars)
  - Rollback execution: calls API, shows progress, refreshes on success
- [ ] Implement CDN invalidation status display (wireframe: story-2.3 lines 826-870)
  - During publish: "⏳ CDN cache invalidating... Estimated time: 30-60 seconds"
  - Success: "✓ Cache invalidation completed in Xs"
  - Error: "⚠️ CDN cache invalidation failed" with retry button
  - Status in version history: ✓ CDN cleared / ⚠️ CDN partial / ✗ CDN failed
- [ ] Add subscriber notification UI (wireframe: story-2.3 lines 715-764)
  - Checkbox: "☑ Notify subscribers when publishing"
  - Subscriber count display
  - Newsletter preview modal
  - After-publish success: "Newsletter sent to X subscribers at HH:MM"
- [ ] Write component tests and integration tests

**Phase 6: Integration, Testing & Polish (Days 15-16)**

*Dependencies: Phases 1-5 complete*
*Deliverables: E2E tests, accessibility, responsive design, deployment ready*

Day 15:
- [ ] E2E tests with Playwright (`e2e/workflows/slot-assignment/`)
  - Test: Organizer assigns session timings via drag-drop
    - Navigate from Speakers tab → Slot Assignment page
    - Drag speaker to time slot
    - Verify conflict detection modal on overlap
    - Resolve conflict and complete assignment
    - Verify success banner and navigation to Publishing tab
  - Test: Organizer uses auto-assignment feature
    - Open auto-assignment modal
    - Select algorithm (balanced)
    - Preview assignments
    - Accept and apply
    - Verify sessions assigned correctly
  - Test: Organizer publishes agenda phase
    - Navigate to Publishing tab
    - Verify session timing validation passes
    - Publish Phase 3 (Agenda)
    - Verify CDN invalidation triggered
    - Verify newsletter sent
    - Check version created
  - Test: Organizer rolls back to previous version
    - Navigate to version history
    - Click rollback on previous version
    - Enter reason
    - Verify rollback success and CDN re-invalidation
- [ ] E2E tests for progressive publishing (`e2e/workflows/progressive-publishing/`)
  - Test full publish flow: Topic → Speakers → Agenda
  - Test validation blocking (can't publish Agenda without timings)
  - Test auto-publish scheduling
  - Test newsletter delivery

Day 16:
- [ ] Implement accessibility features (wireframe: story-5.7-accessibility-features.md)
  - Keyboard navigation for drag-drop:
    - Tab to speaker card → Space to grab → Arrow keys to move → Enter to drop
  - ARIA labels for all interactive elements
  - Focus management: trap focus in modals, restore after close
  - Screen reader announcements:
    - "Session timing assigned successfully"
    - "Conflict detected, 3 options available"
    - "All session timings complete, ready to publish"
  - High-contrast mode support
  - Reduced motion preference support (disable animations)
- [ ] Implement responsive design (wireframe: story-5.7-responsive-design.md)
  - Desktop (≥1280px): Full three-column layout
  - Tablet (768px-1279px): Collapsible sidebar, two-column main
  - Mobile (<768px): Single column, speaker pool in bottom sheet
  - Touch-friendly drag-drop for mobile (long-press to grab)
- [ ] Implement celebration animations (wireframe: story-5.7-celebration-animations.md)
  - Confetti animation on completing all assignments (canvas-confetti library)
  - Progress bar fill animation (CSS transitions)
  - Success badge pulse effect (Material-UI keyframes)
- [ ] Performance optimization
  - Lazy load timeline grid rows (virtual scrolling for large events)
  - Debounce preference match calculations
  - Memoize expensive components (React.memo)
  - Optimize API calls (batch preference fetches)
- [ ] Code review and refactoring
  - Extract reusable components
  - DRY up duplicate logic
  - Ensure consistent error handling
  - Update OpenAPI spec if any API changes
  - Generate frontend types: `npm run generate:api-types`
- [ ] Documentation updates
  - Update CLAUDE.md with new routes and components
  - Update architecture diagrams if needed
  - Document deployment requirements (CloudFront, EventBridge, SES config)
- [ ] Final regression testing
  - Run full test suite: `make test`
  - Verify coverage meets ≥85% target
  - Test in all supported browsers (Chrome, Firefox, Safari, Edge)
  - Test with screen readers (NVDA, JAWS, VoiceOver)
  - Load testing: simulate 50 concurrent organizers assigning timings

**Risk Mitigation**:
- **Risk**: CloudFront API rate limits during testing
  - Mitigation: Use LocalStack for all integration tests, only hit real CloudFront in staging
- **Risk**: React DnD performance with 100+ sessions
  - Mitigation: Implement virtual scrolling, lazy render off-screen slots
- **Risk**: Conflict detection race conditions (concurrent assignments)
  - Mitigation: Use database-level row locking with SELECT FOR UPDATE
- **Risk**: CDN invalidation failures blocking publish
  - Mitigation: Mark as warning, allow publish to proceed, retry invalidation in background

**Success Criteria**:
- ✅ All 29 acceptance criteria pass QA
- ✅ All unit tests pass (≥90% coverage for business logic)
- ✅ All integration tests pass (≥80% coverage for APIs)
- ✅ E2E tests pass for critical workflows
- ✅ Accessibility audit passes (WCAG 2.1 AA)
- ✅ Performance targets met (LCP <2.5s, drag latency <100ms)
- ✅ No P0/P1 bugs in QA review

### Debug Log
See: `.ai/debug-log.md#story-5.7` for detailed implementation debugging

### Completion Notes
{To be filled by dev agent}

### File List
**Created**:
- services/event-management-service/src/main/resources/db/migration/V28__Add_slot_assignment_and_publishing.sql

**Modified**:
- docs/stories/BAT-11.slot-assignment-publishing.md (Agent Model Used, File List, Change Log updated)

**Deleted**:
- (none)

### Change Log
- 2025-12-25: Task 0 Complete - Created V28 migration for slot assignment and publishing schema (session_timing_history, speaker_slot_preferences, publishing_versions, publishing_config tables)
- 2025-12-25: Phase 4 completion - Wireframe review, architecture decision documentation, route definition, implementation estimation

### Deployment Notes
{Special deployment considerations}

**Infrastructure Requirements**:
- CloudFront distribution ID configuration
- EventBridge scheduled rules for auto-publish
- SES verified domain for newsletters (should already exist from Story 5.5)
- IAM permissions for CloudFront invalidation

**Configuration**:
```yaml
# application.yml additions
aws:
  cloudfront:
    distribution-id: ${CLOUDFRONT_DISTRIBUTION_ID}
    invalidation-timeout-seconds: 300
  eventbridge:
    auto-publish-rule-prefix: "batbern-auto-publish"
  ses:
    verified-domain: ${SES_VERIFIED_DOMAIN}

publishing:
  default-auto-publish-speakers-days: 30
  default-auto-publish-agenda-days: 14
  preview-url-template: "https://preview.batbern.ch/events/{eventId}?mode=preview"
```

### Status
Ready for Implementation

**Phase 4 Complete** (2025-12-25):
- ✅ All Phase 1-3 wireframes reviewed and verified complete (9 wireframes total)
- ✅ Story file updated with comprehensive wireframe references and architecture decision
- ✅ Route definition added: `/organizer/events/:eventCode/slot-assignment`
- ✅ Detailed 16-18 day implementation plan created with day-by-day breakdown
- 🎯 **Ready to begin Phase 1 implementation** (Database & Backend Foundation)

**Next Steps**:
1. Assign developer to begin Phase 1 (Days 1-2): Database schema and JPA entities
2. Review implementation plan with team for final adjustments
3. Set up project board with tasks from implementation approach
4. Begin TDD implementation following the plan
