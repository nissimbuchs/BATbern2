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
{To be filled by dev agent}

### Wireframe References

**Slot Assignment UI**:
- `docs/wireframes/story-3.1-speaker-matching-interface.md` - Drag-and-drop slot assignment interface
  - Shows accepted speakers list (left panel)
  - Shows slot assignment panel (right panel) with morning/afternoon sections
  - Demonstrates drag-and-drop interaction pattern
  - Visual feedback during drag operations
  - Empty slot drop zones

**Progressive Publishing UI**:
- `docs/wireframes/story-2.3-basic-publishing-engine.md` - Publishing controls and validation
  - Publishing timeline visualization
  - Content validation dashboard
  - Live preview (desktop/mobile/print)
  - Publishing mode controls (draft/progressive/complete)
  - Version control with rollback

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
{To be filled by dev agent during implementation}

**Phase 1: Database & Backend Foundation (Days 1-2)**
- Create Flyway migration V28 (session timing history, preferences, publishing tables)
- Create JPA entities for SessionTimingHistory, SpeakerSlotPreference, PublishingVersion, PublishingConfig
- Set up repositories with custom queries (note: Session entity already exists)

**Phase 2: Slot Assignment Backend (Days 3-5)**
- Implement SessionTimingService (assigns start/end times to sessions)
- Implement SlotAssignmentService with drag-drop support (updates session timing)
- Implement ConflictDetectionService (detects session timing conflicts)
- Implement PreferenceMatchingAlgorithm (matches speakers to time slots)
- Create REST controllers and integration tests

**Phase 3: Publishing Engine Backend (Days 6-8)**
- Implement PublishingPhaseService
- Implement CDNInvalidationService for CloudFront
- Implement VersionTrackingService
- Set up scheduled jobs (EventBridge rules)
- Create REST controllers and integration tests

**Phase 4: Slot Assignment Frontend (Days 9-11)**
- Set up React DnD library
- Implement DragDropSlotAssignment component
- Implement speaker preference and conflict detection UIs
- Wire to backend APIs

**Phase 5: Publishing Frontend (Days 12-14)**
- Implement PublishingTimeline component
- Implement ValidationDashboard
- Implement LivePreview with responsive modes
- Implement VersionControl with rollback

**Phase 6: Integration & Testing (Days 15-16)**
- E2E workflow tests
- Performance testing
- CDN cache invalidation testing
- Scheduled job testing

### Debug Log
See: `.ai/debug-log.md#story-5.7` for detailed implementation debugging

### Completion Notes
{To be filled by dev agent}

### File List
**Created**:
- {files}

**Modified**:
- {files}

**Deleted**:
- {files}

### Change Log
- {date}: {change}

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
Draft
