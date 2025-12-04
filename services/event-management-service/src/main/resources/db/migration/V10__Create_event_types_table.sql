-- V10__Create_event_types_table.sql
-- Event Type Configuration table for Story 5.1
-- Based on architecture: docs/architecture/03-data-architecture.md, Section "EventType"
-- SOURCE OF TRUTH: Architecture document defines EventType enum and EventSlotConfiguration

-- Event types configuration table
CREATE TABLE IF NOT EXISTS event_types (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    type VARCHAR(20) NOT NULL UNIQUE CHECK (type IN ('FULL_DAY', 'AFTERNOON', 'EVENING')),
    min_slots INTEGER NOT NULL CHECK (min_slots > 0),
    max_slots INTEGER NOT NULL CHECK (max_slots >= min_slots),
    slot_duration INTEGER NOT NULL CHECK (slot_duration >= 15),
    theoretical_slots_am BOOLEAN NOT NULL DEFAULT true,
    break_slots INTEGER NOT NULL DEFAULT 0 CHECK (break_slots >= 0),
    lunch_slots INTEGER NOT NULL DEFAULT 0 CHECK (lunch_slots >= 0),
    default_capacity INTEGER NOT NULL CHECK (default_capacity > 0),
    typical_start_time TIME,
    typical_end_time TIME,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Index for fast lookup by type (used in public API)
CREATE INDEX IF NOT EXISTS idx_event_types_type ON event_types(type);

-- Trigger for automatic updated_at
DROP TRIGGER IF EXISTS update_event_types_updated_at ON event_types;
CREATE TRIGGER update_event_types_updated_at BEFORE UPDATE ON event_types
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Seed data for three event types
INSERT INTO event_types (type, min_slots, max_slots, slot_duration, theoretical_slots_am, break_slots, lunch_slots, default_capacity, typical_start_time, typical_end_time)
VALUES
    ('FULL_DAY', 6, 8, 45, true, 2, 1, 200, '09:00', '17:00'),
    ('AFTERNOON', 6, 8, 30, false, 1, 0, 150, '13:00', '18:00'),
    ('EVENING', 3, 4, 45, false, 1, 0, 100, '18:00', '21:00')
ON CONFLICT (type) DO NOTHING;

-- Comments documenting architecture alignment
COMMENT ON TABLE event_types IS 'Event type configuration templates - matches architecture 03-data-architecture.md EventType enum';
COMMENT ON COLUMN event_types.type IS 'Event type identifier: FULL_DAY, AFTERNOON, EVENING (matches EventType enum)';
COMMENT ON COLUMN event_types.min_slots IS 'Minimum number of session slots for this event type';
COMMENT ON COLUMN event_types.max_slots IS 'Maximum number of session slots for this event type';
COMMENT ON COLUMN event_types.slot_duration IS 'Duration of each slot in minutes';
COMMENT ON COLUMN event_types.theoretical_slots_am IS 'Whether theoretical presentations are scheduled in morning slots';
COMMENT ON COLUMN event_types.break_slots IS 'Number of break slots included in event';
COMMENT ON COLUMN event_types.lunch_slots IS 'Number of lunch slots included in event';
COMMENT ON COLUMN event_types.default_capacity IS 'Default attendee capacity for this event type';
