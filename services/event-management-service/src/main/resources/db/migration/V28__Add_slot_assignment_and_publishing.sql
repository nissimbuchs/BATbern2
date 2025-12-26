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

-- Add speaker_pool_id to sessions table for slot assignment
-- This allows linking a session (slot) to a speaker without full JPA relationship
ALTER TABLE sessions ADD COLUMN IF NOT EXISTS speaker_pool_id UUID REFERENCES speaker_pool(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_sessions_speaker_pool ON sessions(speaker_pool_id);

-- Add username to speaker_pool table for authenticated speakers
-- This field is used for linking speakers to their user accounts
ALTER TABLE speaker_pool ADD COLUMN IF NOT EXISTS username VARCHAR(255);

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
    CONSTRAINT unique_event_version UNIQUE(event_id, version_number)
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
