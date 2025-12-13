-- V11__Add_event_type_to_events.sql
-- Story 5.1: Event Type Definition - Add event_type column to events table
-- Architecture Reference: docs/architecture/03-data-architecture.md, Section "Event"

-- Add event_type column with foreign key to event_types table
ALTER TABLE events
ADD COLUMN event_type VARCHAR(20) REFERENCES event_types(type);

-- Set default value for existing events (full_day as most common type)
UPDATE events
SET event_type = 'evening'
WHERE event_type IS NULL;

-- Make event_type NOT NULL after backfilling
ALTER TABLE events
ALTER COLUMN event_type SET NOT NULL;

-- Add index for performance
CREATE INDEX IF NOT EXISTS idx_events_event_type ON events(event_type);

-- Comment documenting purpose
COMMENT ON COLUMN events.event_type IS 'Type of event (full_day, afternoon, evening) - references event_types table';
