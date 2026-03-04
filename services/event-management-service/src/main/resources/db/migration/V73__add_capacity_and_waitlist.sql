-- Story 10.11: Venue Capacity Enforcement & Waitlist Management
-- AC1: Add registration_capacity to events and waitlist_position to registrations

-- Add waitlist_position to registrations table (nullable — NULL for non-waitlist rows)
ALTER TABLE registrations
    ADD COLUMN waitlist_position INTEGER;

-- Add registration_capacity to events table (nullable — NULL = unlimited)
ALTER TABLE events
    ADD COLUMN registration_capacity INTEGER;

-- Index for efficient waitlist promotion queries (partial index on waitlist rows only)
CREATE INDEX idx_registrations_event_waitlist
    ON registrations (event_id, status, waitlist_position)
    WHERE status = 'waitlist';

-- Update registration status check constraint to include 'waitlist' (Story 10.11 uses 'waitlist', not 'waitlisted')
ALTER TABLE registrations DROP CONSTRAINT IF EXISTS registrations_status_check;
ALTER TABLE registrations ADD CONSTRAINT registrations_status_check
    CHECK (status IN ('registered', 'waitlisted', 'waitlist', 'confirmed', 'cancelled', 'attended'));
