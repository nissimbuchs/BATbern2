-- V38__Add_speaker_name_search_cache_fields.sql
-- Feature Enhancement: Add speaker name search to archive browsing
--
-- Context:
-- Archive search (Story BAT-109) currently searches event/session titles and descriptions,
-- but does NOT search speaker names. Users want to find events by speaker name.
--
-- Solution:
-- Add denormalized SEARCH CACHE fields for speaker names to session_users table.
-- These are NOT the source of truth - full user data fetched via UserManagementClient API.
-- Fields are populated on session assignment and updated when user profiles change.
--
-- This follows the same pattern as V35 (attendee search cache fields):
-- - Store minimal data for filtering/search (cache)
-- - Fetch full data from source of truth for display (enrichment)

-- Add speaker name search cache fields to session_users table
ALTER TABLE session_users
    ADD COLUMN IF NOT EXISTS speaker_first_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS speaker_last_name VARCHAR(100);

-- Add full-text search vector column for speaker names (GENERATED ALWAYS AS)
-- LANGUAGE: 'german' - BATbern is a Swiss/German conference with German-language names
ALTER TABLE session_users
ADD COLUMN IF NOT EXISTS speaker_name_vector tsvector
    GENERATED ALWAYS AS (
        to_tsvector('german',
            coalesce(speaker_first_name, '') || ' ' || coalesce(speaker_last_name, '')
        )
    ) STORED;

-- Create GIN index for speaker name full-text search
CREATE INDEX IF NOT EXISTS idx_session_users_speaker_name_vector
    ON session_users USING GIN(speaker_name_vector);

-- Add column comments explaining purpose
COMMENT ON COLUMN session_users.speaker_first_name IS
    'Search cache field for database-level filtering (performance optimization). NOT source of truth - full user data fetched via UserManagementClient API. Updated on session assignment and user profile changes.';

COMMENT ON COLUMN session_users.speaker_last_name IS
    'Search cache field for database-level filtering (performance optimization). NOT source of truth - full user data fetched via UserManagementClient API. Updated on session assignment and user profile changes.';

COMMENT ON COLUMN session_users.speaker_name_vector IS
    'Full-text search vector for speaker names in German (auto-generated). Enables archive search by speaker name.';

-- Update table comment
COMMENT ON TABLE session_users IS
    'Many-to-many relationship between sessions and users (speakers) - ADR-004 pattern. Includes denormalized search cache fields for speaker names. Full user details fetched via UserManagementClient API.';

-- Log migration for audit trail
SELECT
    'V38 Migration Complete: Added speaker name search cache fields' as migration_status,
    'Speaker names now searchable in archive browsing' as feature_benefit,
    'Full user enrichment still via UserManagementClient API' as implementation_note,
    NOW() as executed_at;
