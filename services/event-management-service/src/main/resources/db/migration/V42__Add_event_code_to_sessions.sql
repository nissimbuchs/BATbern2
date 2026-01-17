-- V42: Add event_code column to sessions table
-- Story 5.9: Make eventCode persistent for microservice-ready architecture
-- Date: 2026-01-17

-- Add event_code column (nullable initially for data migration)
ALTER TABLE sessions ADD COLUMN event_code VARCHAR(50);

-- Backfill event_code from events table
UPDATE sessions
SET event_code = (SELECT event_code FROM events WHERE events.id = sessions.event_id);

-- Make event_code NOT NULL after backfill
ALTER TABLE sessions ALTER COLUMN event_code SET NOT NULL;

-- Add index for efficient queries by event_code
CREATE INDEX idx_sessions_event_code ON sessions(event_code);

-- Add comment
COMMENT ON COLUMN sessions.event_code IS 'Event code this session belongs to - denormalized for microservice separation';
