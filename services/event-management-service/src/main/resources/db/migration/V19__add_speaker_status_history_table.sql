-- V19__add_speaker_status_history_table.sql
-- Story 5.4: Speaker Status Management
-- Creates speaker_status_history table for tracking speaker workflow state transitions
-- SOURCE OF TRUTH: docs/stories/5.4-speaker-status-management.md + Architecture docs/architecture/03-data-architecture.md

-- Enable UUID extension if not already enabled
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Speaker status history table (tracks all status transitions)
-- Note: Table lives in event-management-service DB (colocated with speaker_pool from Story 5.2)
-- Accessed by speaker-coordination-service via service layer for domain separation
CREATE TABLE IF NOT EXISTS speaker_status_history (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    speaker_pool_id UUID NOT NULL,
    event_code VARCHAR(50) NOT NULL,
    previous_status VARCHAR(50) NOT NULL CHECK (previous_status IN (
        'identified', 'contacted', 'ready', 'accepted', 'declined',
        'content_submitted', 'quality_reviewed', 'slot_assigned',
        'confirmed', 'withdrew', 'overflow'
    )),
    new_status VARCHAR(50) NOT NULL CHECK (new_status IN (
        'identified', 'contacted', 'ready', 'accepted', 'declined',
        'content_submitted', 'quality_reviewed', 'slot_assigned',
        'confirmed', 'withdrew', 'overflow'
    )),
    changed_by_username VARCHAR(100) NOT NULL,
    change_reason TEXT,
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    -- Foreign key constraints with cascade delete
    -- When speaker removed from pool, delete all their status history
    CONSTRAINT fk_speaker_status_history_speaker_pool
        FOREIGN KEY (speaker_pool_id)
        REFERENCES speaker_pool(id)
        ON DELETE CASCADE,

    -- Foreign key to events table via event_code (meaningful identifier)
    CONSTRAINT fk_speaker_status_history_event_code
        FOREIGN KEY (event_code)
        REFERENCES events(event_code)
        ON DELETE CASCADE
);

-- Indexes for speaker_status_history table (performance optimization)
-- Composite index for efficient history queries per speaker
CREATE INDEX IF NOT EXISTS idx_speaker_status_history_speaker_changed
    ON speaker_status_history(speaker_pool_id, changed_at DESC);

CREATE INDEX IF NOT EXISTS idx_speaker_status_history_event_code
    ON speaker_status_history(event_code);

CREATE INDEX IF NOT EXISTS idx_speaker_status_history_changed_by
    ON speaker_status_history(changed_by_username);

CREATE INDEX IF NOT EXISTS idx_speaker_status_history_new_status
    ON speaker_status_history(new_status);

-- Trigger for automatic updated_at timestamp
DROP TRIGGER IF EXISTS update_speaker_status_history_updated_at ON speaker_status_history;
CREATE TRIGGER update_speaker_status_history_updated_at BEFORE UPDATE ON speaker_status_history
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Comments documenting architecture alignment
COMMENT ON TABLE speaker_status_history IS 'Status transition history for speakers during event planning (Workflow Step 5) - Story 5.4';
COMMENT ON COLUMN speaker_status_history.speaker_pool_id IS 'References speaker_pool.id - speaker whose status changed';
COMMENT ON COLUMN speaker_status_history.event_code IS 'References events.event_code - event context for the status change';
COMMENT ON COLUMN speaker_status_history.previous_status IS 'SpeakerWorkflowState before transition (lowercase_with_underscores in DB, UPPER_CASE in Java)';
COMMENT ON COLUMN speaker_status_history.new_status IS 'SpeakerWorkflowState after transition (lowercase_with_underscores in DB, UPPER_CASE in Java)';
COMMENT ON COLUMN speaker_status_history.changed_by_username IS 'Username (not UUID) of organizer who changed the status';
COMMENT ON COLUMN speaker_status_history.change_reason IS 'Optional free-text reason for status change (max 2000 characters enforced at application layer)';
COMMENT ON COLUMN speaker_status_history.changed_at IS 'Timestamp when the status change occurred';
