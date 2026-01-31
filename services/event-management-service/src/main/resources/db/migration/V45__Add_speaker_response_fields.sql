-- V45__Add_speaker_response_fields.sql
-- Story 6.2a: Invitation Response Portal
-- Adds response tracking and preference fields to speaker_pool table
-- SOURCE OF TRUTH: docs/stories/6.2a-invitation-response.md

-- Add response tracking fields to speaker_pool
ALTER TABLE speaker_pool
    ADD COLUMN accepted_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN declined_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN decline_reason TEXT,
    ADD COLUMN is_tentative BOOLEAN DEFAULT FALSE,
    ADD COLUMN tentative_reason TEXT;

-- Add speaker preference fields (captured during response)
ALTER TABLE speaker_pool
    ADD COLUMN preferred_time_slot VARCHAR(100),
    ADD COLUMN travel_requirements TEXT,
    ADD COLUMN technical_requirements TEXT,
    ADD COLUMN initial_presentation_title VARCHAR(500),
    ADD COLUMN preference_comments TEXT;

-- Index for finding tentative speakers (for follow-up reminders)
CREATE INDEX idx_speaker_pool_tentative ON speaker_pool(event_id, is_tentative)
    WHERE is_tentative = TRUE AND status = 'invited';

-- Comments documenting architecture alignment
COMMENT ON COLUMN speaker_pool.accepted_at IS 'Timestamp when speaker accepted invitation (Story 6.2a)';
COMMENT ON COLUMN speaker_pool.declined_at IS 'Timestamp when speaker declined invitation (Story 6.2a)';
COMMENT ON COLUMN speaker_pool.decline_reason IS 'Optional reason for declining - freeform text (Story 6.2a)';
COMMENT ON COLUMN speaker_pool.is_tentative IS 'Speaker marked tentative - can still respond later (Story 6.2a)';
COMMENT ON COLUMN speaker_pool.tentative_reason IS 'Reason for tentative status - freeform text (Story 6.2a)';
COMMENT ON COLUMN speaker_pool.preferred_time_slot IS 'Speaker preference for time slot - freeform (Story 6.2a)';
COMMENT ON COLUMN speaker_pool.travel_requirements IS 'Travel/accommodation requirements (Story 6.2a)';
COMMENT ON COLUMN speaker_pool.technical_requirements IS 'Technical requirements for presentation (Story 6.2a)';
COMMENT ON COLUMN speaker_pool.initial_presentation_title IS 'Initial/working title for presentation (Story 6.2a)';
COMMENT ON COLUMN speaker_pool.preference_comments IS 'Additional comments/preferences from speaker (Story 6.2a)';
