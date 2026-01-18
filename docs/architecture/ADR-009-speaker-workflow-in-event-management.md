# ADR-009: Speaker Workflow State Machine in Event Management Service

**Status**: Accepted
**Date**: 2026-01-18
**Decision Makers**: Development Team
**Related Stories**:
- Story 5.2 (Topic Selection & Speaker Brainstorming)
- Story 5.3 (Speaker Outreach Tracking)
- Story 5.4 (Speaker Status Management)
- Story 5.5 (Speaker Content Submission)
- Story 6.1a (Speaker Workflow State Machine Foundation)

## Context

Story 6.1a originally specified that the Speaker Workflow State Machine should be implemented in `speaker-coordination-service`. However, during implementation of Epic 5 stories, the speaker workflow functionality was implemented in `event-management-service` instead.

### Original Design (Story 6.1a Specification)

```
Speaker Coordination Service:
├── SpeakerWorkflowService
├── SpeakerWorkflowController
└── session_users table with workflow_state column
```

### Actual Implementation

```
Event Management Service:
├── SpeakerWorkflowService
├── SpeakerStatusService
├── SpeakerStatusController
└── speaker_pool table with status column
```

## Decision

**Implement speaker workflow state machine in `event-management-service` rather than `speaker-coordination-service`.**

## Rationale

### 1. Domain Cohesion

The speaker workflow during event planning is tightly coupled with event-specific data:
- Speaker pool entries are event-scoped (`speaker_pool.event_id`)
- Status transitions depend on event context (slot assignments, session associations)
- Overflow detection requires access to event slot configuration
- Content submission creates sessions within the event

Placing this in `event-management-service` keeps related data and logic together, avoiding cross-service data dependencies.

### 2. Data Locality

The `speaker_pool` table stores potential speakers during event brainstorming (Epic 5). This is distinct from:
- `session_users`: Confirmed speakers linked to published sessions
- `speakers`: Master speaker records with biographical data

The workflow operates on `speaker_pool` entries, which are event-management concerns, not speaker-coordination concerns.

### 3. Transaction Boundaries

Key operations require atomic updates across multiple tables:
- Content submission: Creates session + links speaker + updates status
- Slot assignment: Updates session timing + potentially auto-confirms speaker
- Quality review: Updates speaker status + may trigger auto-confirmation

Keeping these in one service maintains transaction integrity without distributed transactions.

### 4. Service Responsibility Clarity

**event-management-service**: Handles event planning workflow (brainstorming → scheduling → publishing)
- Speaker pool management (potential speakers)
- Session creation and scheduling
- Event workflow state machine
- Speaker workflow state machine (for planning phase)

**speaker-coordination-service**: Handles speaker relationship management
- Speaker profile management
- Speaker outreach history
- Speaker invitation tracking
- Speaker portal (self-service for confirmed speakers)

### 5. Existing Implementation Reality

Epic 5 (Stories 5.2-5.5) established the pattern of speaker workflow in event-management-service:
- V14 migration created `speaker_pool` table
- SpeakerPoolService manages speaker brainstorming
- SpeakerStatusService handles status transitions
- SpeakerContentSubmissionService creates sessions from speaker content

Changing this would require significant refactoring with no functional benefit.

## Consequences

### Positive

1. **Simpler architecture**: No cross-service calls for common operations
2. **Better performance**: Local database access for workflow operations
3. **Transaction safety**: ACID guarantees for multi-table operations
4. **Reduced latency**: No network round-trips during state transitions

### Negative

1. **Story-spec deviation**: Story 6.1a specification no longer matches implementation
2. **Naming inconsistency**: `speaker-coordination-service` doesn't coordinate the workflow
3. **Future migration**: If speaker self-service portal needs workflow access, may require API exposure

### Mitigations

1. **Documentation**: This ADR documents the deviation and rationale
2. **Story update**: Story 6.1a status updated to Done with QA results noting the deviation
3. **API exposure**: SpeakerStatusController exposes REST API that speaker-coordination-service can call if needed

## Implementation Details

### State Enum

```java
// shared-kernel/src/main/java/ch/batbern/shared/types/SpeakerWorkflowState.java
public enum SpeakerWorkflowState {
    IDENTIFIED,      // Initial state when added to speaker pool
    CONTACTED,       // Organizer made contact
    READY,           // Speaker indicated interest
    ACCEPTED,        // Speaker formally accepted
    DECLINED,        // Speaker declined
    CONTENT_SUBMITTED,   // Presentation materials submitted
    QUALITY_REVIEWED,    // Content passed review
    SLOT_ASSIGNED,       // Time slot assigned (legacy - not used)
    CONFIRMED,           // Locked in final agenda
    WITHDREW,            // Speaker backed out after accepting
    OVERFLOW             // Too many speakers accepted
}
```

### Domain Event

```java
// shared-kernel/src/main/java/ch/batbern/shared/events/SpeakerWorkflowStateChangeEvent.java
// Published via Spring ApplicationEventPublisher on every state transition
```

### Database Schema

```sql
-- V14__add_topic_speaker_pool_tables.sql
CREATE TABLE speaker_pool (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES events(id),
    status VARCHAR(50) NOT NULL DEFAULT 'identified',
    -- ... other columns
);
CREATE INDEX idx_speaker_pool_status ON speaker_pool(status);
```

## References

- Story 6.1a: Speaker Workflow State Machine Foundation
- Story 5.2: Topic Selection & Speaker Brainstorming
- Story 5.4: Speaker Status Management
- docs/architecture/06a-workflow-state-machines.md
