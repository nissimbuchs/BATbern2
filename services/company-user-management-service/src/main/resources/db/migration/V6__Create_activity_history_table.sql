-- V6__Create_activity_history_table.sql
-- Story 1.16.2: Activity history uses UUID FK internally
-- Based on: docs/stories/1.14-2.user-management-service-foundation.md

CREATE TABLE activity_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES user_profiles(id) ON DELETE CASCADE,
    activity_type VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id VARCHAR(255) NOT NULL,  -- Story 1.16.2: Can be eventCode, username, company name, etc.
    description TEXT NOT NULL,
    metadata JSONB DEFAULT '{}',
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE activity_history IS 'User activity tracking - entity_id can be meaningful IDs (eventCode, username, etc.) per Story 1.16.2';
COMMENT ON COLUMN activity_history.id IS 'Activity record identifier';
COMMENT ON COLUMN activity_history.user_id IS 'Internal UUID FK to user_profiles.id';
COMMENT ON COLUMN activity_history.activity_type IS 'Type of activity (e.g., LOGIN, PROFILE_UPDATE, EVENT_REGISTRATION)';
COMMENT ON COLUMN activity_history.entity_type IS 'Type of entity involved (e.g., USER, EVENT, COMPANY)';
COMMENT ON COLUMN activity_history.entity_id IS 'Meaningful identifier of entity (e.g., john.doe, BAT2024, GoogleZH)';
COMMENT ON COLUMN activity_history.description IS 'Human-readable activity description';
COMMENT ON COLUMN activity_history.metadata IS 'Additional activity metadata in JSON format';
COMMENT ON COLUMN activity_history.timestamp IS 'When the activity occurred';
