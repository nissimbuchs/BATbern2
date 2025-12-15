-- Migration: Remove legacy status column from events table
-- Rationale: workflowState (16-step Epic 5 workflow) provides comprehensive state tracking; status is redundant
-- Data Loss: Acceptable per product decision
-- Story: Event Status Field Cleanup
-- Date: 2025-12-15

-- Remove status column (drops all existing status data)
ALTER TABLE events DROP COLUMN IF EXISTS status;

-- Update table comment to document removal
COMMENT ON TABLE events IS 'Events table - status column removed in V17 (2025-12-15), replaced by workflow_state (16-step Epic 5 workflow from Story 5.1a)';

-- Rollback instructions (if needed):
-- ALTER TABLE events ADD COLUMN status VARCHAR(50) DEFAULT 'planning';
-- ALTER TABLE events ADD CONSTRAINT events_status_check
--     CHECK (status IN (
--         'planning', 'topic_defined', 'speakers_invited', 'agenda_draft',
--         'published', 'registration_open', 'registration_closed',
--         'in_progress', 'completed', 'archived'
--     ));
-- CREATE INDEX idx_events_status ON events(status);
