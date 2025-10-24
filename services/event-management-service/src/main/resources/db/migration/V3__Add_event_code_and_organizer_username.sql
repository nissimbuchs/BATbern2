-- V3__Add_event_code_column.sql
-- Story 1.16.2: Eliminate UUIDs from API - Add eventCode and organizerUsername as meaningful identifiers
-- Add event_code and organizer_username columns to events table as public identifiers instead of UUIDs

-- Add event_code column (nullable initially to allow data migration)
ALTER TABLE events ADD COLUMN IF NOT EXISTS event_code VARCHAR(50);

-- Add organizer_username column (nullable initially to allow data migration)
ALTER TABLE events ADD COLUMN IF NOT EXISTS organizer_username VARCHAR(255);

-- Populate event_code for existing rows using event_number
-- Format: BATbern{event_number} (e.g., BATbern142)
UPDATE events
SET event_code = 'BATbern' || event_number
WHERE event_code IS NULL;

-- Populate organizer_username for existing rows with placeholder
-- In a real migration, this would lookup actual usernames from the user service
UPDATE events
SET organizer_username = 'migrated-user-' || organizer_id
WHERE organizer_username IS NULL;

-- Make event_code NOT NULL and UNIQUE after populating existing rows
ALTER TABLE events ALTER COLUMN event_code SET NOT NULL;
ALTER TABLE events ADD CONSTRAINT uk_events_event_code UNIQUE (event_code);

-- Make organizer_username NOT NULL after populating existing rows
ALTER TABLE events ALTER COLUMN organizer_username SET NOT NULL;

-- Make organizer_id nullable (it's no longer the primary organizer identifier)
-- Story 1.16.2: organizer_username is now the primary identifier, organizer_id kept for legacy/migration only
ALTER TABLE events ALTER COLUMN organizer_id DROP NOT NULL;

-- Add indexes for performance
CREATE INDEX IF NOT EXISTS idx_events_event_code ON events(event_code);
CREATE INDEX IF NOT EXISTS idx_events_organizer_username ON events(organizer_username);

-- Comments documenting the column purposes
COMMENT ON COLUMN events.event_code IS 'Human-readable event identifier (e.g., BATbern142) - Story 1.16.2: Public API identifier instead of UUID';
COMMENT ON COLUMN events.organizer_username IS 'Username of event organizer - Story 1.16.2: Public API identifier instead of UUID';
