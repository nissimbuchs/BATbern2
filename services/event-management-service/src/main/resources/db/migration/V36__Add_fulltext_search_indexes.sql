-- V36__Add_fulltext_search_indexes.sql
-- Story 4.2: Historical Event Archive Browsing (BAT-109)
-- Purpose: Add GIN full-text search indexes to events and sessions tables for archive search functionality
-- SOURCE OF TRUTH: docs/stories/BAT-109.archive-browsing.md, AC9 (full-text search requirement)

-- Add full-text search vector columns to events table (GENERATED ALWAYS AS)
-- Pattern matches topics table from V14 migration
-- LANGUAGE: 'german' - BATbern is a Swiss/German conference with German-language content
ALTER TABLE events
ADD COLUMN IF NOT EXISTS title_vector tsvector
    GENERATED ALWAYS AS (to_tsvector('german', title)) STORED;

ALTER TABLE events
ADD COLUMN IF NOT EXISTS description_vector tsvector
    GENERATED ALWAYS AS (to_tsvector('german', coalesce(description, ''))) STORED;

-- Add full-text search vector columns to sessions table (GENERATED ALWAYS AS)
-- LANGUAGE: 'german' - Sessions are in German (titles, descriptions)
ALTER TABLE sessions
ADD COLUMN IF NOT EXISTS title_vector tsvector
    GENERATED ALWAYS AS (to_tsvector('german', title)) STORED;

ALTER TABLE sessions
ADD COLUMN IF NOT EXISTS description_vector tsvector
    GENERATED ALWAYS AS (to_tsvector('german', coalesce(description, ''))) STORED;

-- Create GIN indexes for events full-text search (Story 4.2 AC9, AC19)
CREATE INDEX IF NOT EXISTS idx_events_title_vector ON events USING GIN(title_vector);
CREATE INDEX IF NOT EXISTS idx_events_description_vector ON events USING GIN(description_vector);

-- Create GIN indexes for sessions full-text search (Story 4.2 AC9, AC19)
CREATE INDEX IF NOT EXISTS idx_sessions_title_vector ON sessions USING GIN(title_vector);
CREATE INDEX IF NOT EXISTS idx_sessions_description_vector ON sessions USING GIN(description_vector);

-- Add index on workflow_state for filtering archived events (Story 4.2 AC10)
-- Index already exists from V2 (idx_events_status), but status was removed in V17
-- Add new index on workflow_state for archive filtering
CREATE INDEX IF NOT EXISTS idx_events_workflow_state ON events(workflow_state);

-- Comments for documentation
COMMENT ON COLUMN events.title_vector IS 'Full-text search vector for event title in German (auto-generated, Story 4.2 AC9)';
COMMENT ON COLUMN events.description_vector IS 'Full-text search vector for event description in German (auto-generated, Story 4.2 AC9)';
COMMENT ON COLUMN sessions.title_vector IS 'Full-text search vector for session title in German (auto-generated, Story 4.2 AC9)';
COMMENT ON COLUMN sessions.description_vector IS 'Full-text search vector for session description in German (auto-generated, Story 4.2 AC9)';
