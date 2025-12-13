-- V12__add_workflow_state_to_events.sql
-- Story 5.1a: Workflow State Machine Foundation
-- Adds workflow_state column to events table for 16-step event workflow tracking
-- SOURCE OF TRUTH: Story 5.1a AC1 defines 16 workflow states

-- Add workflow_state column with default value 'created'
-- Note: Database stores lowercase_with_underscores (e.g., 'speaker_outreach')
-- Java enum uses UPPER_CASE (e.g., SPEAKER_OUTREACH)
-- EventWorkflowStateConverter handles the conversion
ALTER TABLE events
ADD COLUMN workflow_state VARCHAR(50) NOT NULL DEFAULT 'created'
    CHECK (workflow_state IN (
        'created',
        'topic_selection',
        'speaker_brainstorming',
        'speaker_outreach',
        'speaker_confirmation',
        'content_collection',
        'quality_review',
        'threshold_check',
        'overflow_management',
        'slot_assignment',
        'agenda_published',
        'agenda_finalized',
        'newsletter_sent',
        'event_ready',
        'partner_meeting_complete',
        'archived'
    ));

-- Index for workflow state queries (performance optimization)
CREATE INDEX idx_events_workflow_state ON events(workflow_state);

-- Documentation
COMMENT ON COLUMN events.workflow_state IS 'Current workflow state from EventWorkflowState enum (16 states) - Story 5.1a. Stored as lowercase_with_underscores (e.g., speaker_outreach), converted to/from Java UPPER_CASE enum via EventWorkflowStateConverter.';
