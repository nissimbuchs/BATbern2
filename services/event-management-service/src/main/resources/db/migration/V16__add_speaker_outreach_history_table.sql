-- V16__add_speaker_outreach_history_table.sql
-- Story 5.3: Speaker Outreach Tracking
-- Creates speaker_outreach_history table for tracking organizer contact attempts with potential speakers
-- SOURCE OF TRUTH: docs/stories/5.3-speaker-outreach-tracking.md + Architecture docs/architecture/03-data-architecture.md

-- Enable UUID extension if not already enabled
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Speaker outreach history table (tracks all contact attempts)
-- Note: Table lives in event-management-service DB (colocated with speaker_pool from Story 5.2)
-- Accessed by speaker-coordination-service via service layer for domain separation
CREATE TABLE IF NOT EXISTS speaker_outreach_history (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    speaker_pool_id UUID NOT NULL,
    contact_date TIMESTAMP WITH TIME ZONE NOT NULL,
    contact_method VARCHAR(50) NOT NULL CHECK (contact_method IN (
        'email', 'phone', 'in_person'
    )),
    notes TEXT,
    organizer_username VARCHAR(100) NOT NULL, -- Username of organizer who made contact
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    -- Foreign key constraint with cascade delete
    -- When speaker removed from pool, delete all their outreach history
    CONSTRAINT fk_speaker_outreach_speaker_pool
        FOREIGN KEY (speaker_pool_id)
        REFERENCES speaker_pool(id)
        ON DELETE CASCADE
);

-- Indexes for speaker_outreach_history table (performance optimization)
CREATE INDEX IF NOT EXISTS idx_speaker_outreach_history_speaker_pool_id ON speaker_outreach_history(speaker_pool_id);
CREATE INDEX IF NOT EXISTS idx_speaker_outreach_history_contact_date ON speaker_outreach_history(contact_date DESC);
CREATE INDEX IF NOT EXISTS idx_speaker_outreach_history_organizer ON speaker_outreach_history(organizer_username);

-- Trigger for automatic updated_at timestamp
DROP TRIGGER IF EXISTS update_speaker_outreach_history_updated_at ON speaker_outreach_history;
CREATE TRIGGER update_speaker_outreach_history_updated_at BEFORE UPDATE ON speaker_outreach_history
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Comments documenting architecture alignment
COMMENT ON TABLE speaker_outreach_history IS 'Outreach contact history for potential speakers during event planning (Workflow Step 4) - Story 5.3';
COMMENT ON COLUMN speaker_outreach_history.speaker_pool_id IS 'References speaker_pool.id - speaker being contacted';
COMMENT ON COLUMN speaker_outreach_history.contact_method IS 'Method of contact: email, phone, or in_person (lowercase_with_underscores in DB)';
COMMENT ON COLUMN speaker_outreach_history.organizer_username IS 'Username (not UUID) of organizer who made the contact attempt';
COMMENT ON COLUMN speaker_outreach_history.notes IS 'Free-text notes about the conversation, speaker response, next steps, etc.';
