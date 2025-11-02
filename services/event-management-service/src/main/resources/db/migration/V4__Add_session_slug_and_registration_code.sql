-- V4: Add session_slug and registration_code for Story 1.16.2
-- Story 1.16.2: Eliminate UUIDs from API - Use meaningful IDs for sessions and registrations
-- Date: 2025-10-27

-- Add session_slug column to sessions table
ALTER TABLE sessions
ADD COLUMN session_slug VARCHAR(200);

-- Generate session slugs from existing titles (for backward compatibility)
-- This handles existing data by creating slugs from titles
UPDATE sessions
SET session_slug = LOWER(
    REGEXP_REPLACE(
        REGEXP_REPLACE(title, '[^a-zA-Z0-9\s-]', '', 'g'),
        '\s+', '-', 'g'
    )
) || '-' || SUBSTRING(CAST(id AS TEXT), 1, 8)
WHERE session_slug IS NULL;

-- Now make session_slug NOT NULL and UNIQUE
ALTER TABLE sessions
ALTER COLUMN session_slug SET NOT NULL,
ADD CONSTRAINT uk_sessions_session_slug UNIQUE (session_slug);

-- Add registration_code column to registrations table
ALTER TABLE registrations
ADD COLUMN registration_code VARCHAR(100);

-- Generate registration codes from existing data (for backward compatibility)
UPDATE registrations
SET registration_code =
    (SELECT event_code FROM events WHERE events.id = registrations.event_id)
    || '-reg-' || SUBSTRING(CAST(registrations.id AS TEXT), 1, 8)
WHERE registration_code IS NULL;

-- Now make registration_code NOT NULL and UNIQUE
ALTER TABLE registrations
ALTER COLUMN registration_code SET NOT NULL,
ADD CONSTRAINT uk_registrations_registration_code UNIQUE (registration_code);

-- Add attendee_username column to registrations table
ALTER TABLE registrations
ADD COLUMN attendee_username VARCHAR(100);

-- Populate attendee_username with placeholder values for existing data
-- In production, this would be populated from actual user data
UPDATE registrations
SET attendee_username = 'user.' || SUBSTRING(CAST(attendee_id AS TEXT), 1, 8)
WHERE attendee_username IS NULL;

-- Now make attendee_username NOT NULL
ALTER TABLE registrations
ALTER COLUMN attendee_username SET NOT NULL;

-- Make attendee_id nullable (legacy column, being phased out)
ALTER TABLE registrations
ALTER COLUMN attendee_id DROP NOT NULL;

-- Create indexes for performance
CREATE INDEX idx_registrations_attendee_username ON registrations(attendee_username);

-- Comments for documentation
COMMENT ON COLUMN sessions.session_slug IS 'Story 1.16.2: URL-friendly slug generated from session title, used as public identifier';
COMMENT ON COLUMN registrations.registration_code IS 'Story 1.16.2: Unique registration code in format eventCode-username, used as public identifier';
COMMENT ON COLUMN registrations.attendee_username IS 'Story 1.16.2: Attendee username in firstname.lastname format, replaces UUID attendee_id';
