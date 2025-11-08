-- V9__Unified_anonymous_registration.sql
-- Unified user profile approach for anonymous and authenticated users
-- Based on: ADR-006 Unified User Profile for Anonymous and Authenticated Users
-- Architecture: docs/architecture/03-data-architecture.md (registrations table)
-- Story: 4.1.5a Registration Architecture Foundation
--
-- KEY ARCHITECTURAL CHANGE (ADR-006):
-- Anonymous users are stored in user_profiles table (company-user-management-service)
-- with cognito_user_id = NULL. When they create an account, we simply UPDATE
-- their user_profile with the cognito_user_id. This eliminates the need for:
-- - anonymous_* fields in registrations (data duplication)
-- - Complex account linking logic (20+ lines → 3 lines)
-- - Free-text company field (now uses company_id FK in user_profiles)
--
-- Note: Table keeps existing name 'registrations' to avoid refactoring existing code
-- Note: Column keeps existing name 'registration_code' (from V4) to avoid refactoring
--

-- Step 1: attendee_id stays NOT NULL (always references user_profiles)
-- In ADR-006, ALL users (anonymous + authenticated) are in user_profiles
-- No change needed - attendee_id is already NOT NULL in V2

-- Step 2: Drop old attendee_name and attendee_email (data now in user_profiles)
ALTER TABLE registrations
  DROP COLUMN IF EXISTS attendee_name,
  DROP COLUMN IF EXISTS attendee_email;

-- Step 3: Create sequence for registration codes
-- Sequence for BAT-YYYY-NNNNNN format (new format, existing column from V4)
-- Note: registration_code column already exists from V4, we're just updating the format
CREATE SEQUENCE IF NOT EXISTS registration_code_seq START 1;

-- Step 4: Add communication preferences
ALTER TABLE registrations
  ADD COLUMN newsletter_subscribed BOOLEAN DEFAULT FALSE,
  ADD COLUMN event_reminders BOOLEAN DEFAULT TRUE;

-- Step 6: Add special requests field (dietary, accessibility, etc.)
ALTER TABLE registrations
  ADD COLUMN special_requests TEXT;

-- Step 7: Update session_preferences to match architecture (should be TEXT[], not missing)
-- Check if column exists first
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='registrations' AND column_name='session_preferences'
    ) THEN
        ALTER TABLE registrations ADD COLUMN session_preferences TEXT[] DEFAULT '{}';
    END IF;
END $$;

-- Step 8: Add attendance tracking columns if missing
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='registrations' AND column_name='attendance_confirmed'
    ) THEN
        ALTER TABLE registrations ADD COLUMN attendance_confirmed BOOLEAN DEFAULT FALSE;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='registrations' AND column_name='actual_attendance'
    ) THEN
        ALTER TABLE registrations ADD COLUMN actual_attendance BOOLEAN DEFAULT FALSE;
    END IF;
END $$;

-- Step 9: Drop old unique constraint (event_id, attendee_email)
ALTER TABLE registrations
  DROP CONSTRAINT IF EXISTS registrations_event_id_attendee_email_key;

-- Step 10: Add unique constraint for registrations
-- Only one registration per attendee per event (enforced via unique index)
CREATE UNIQUE INDEX idx_registrations_unique
  ON registrations (event_id, attendee_id);

-- Step 11: Add indexes for performance
-- Note: Confirmation code index not needed - UNIQUE constraint creates implicit index

CREATE INDEX idx_registrations_attendee_id
  ON registrations (attendee_id);

-- Step 12: Keep existing indexes, add new ones if needed
CREATE INDEX IF NOT EXISTS idx_registrations_event_id
  ON registrations (event_id);

CREATE INDEX IF NOT EXISTS idx_registrations_status
  ON registrations (status);

-- Step 13: Add table and column comments for documentation
COMMENT ON TABLE registrations IS 'Event registrations for all users. Anonymous users have user_profiles.cognito_user_id = NULL. See ADR-006 for unified user profile pattern.';
COMMENT ON COLUMN registrations.attendee_id IS 'Foreign key to user_profiles (company-user-management-service). Always NOT NULL - anonymous users are in user_profiles with cognito_user_id = NULL.';
COMMENT ON COLUMN registrations.registration_code IS 'Unique registration code (BAT-YYYY-NNNNNN format, from V4) acts as access token for public registration lookup';
COMMENT ON COLUMN registrations.session_preferences IS 'Event-level registration - sessions are preferences only, not commitments';

-- Rollback instructions (for reference, DO NOT EXECUTE):
/*
-- To rollback this migration (DESTRUCTIVE - will lose registration data):

-- Drop sequence
DROP SEQUENCE IF EXISTS registration_code_seq;

-- Drop new constraints and indexes
DROP INDEX IF EXISTS idx_registrations_unique;
DROP INDEX IF EXISTS idx_registrations_attendee_id;

-- Drop new columns
ALTER TABLE registrations
  DROP COLUMN IF EXISTS newsletter_subscribed,
  DROP COLUMN IF EXISTS event_reminders,
  DROP COLUMN IF EXISTS special_requests,
  DROP COLUMN IF EXISTS session_preferences,
  DROP COLUMN IF EXISTS attendance_confirmed,
  DROP COLUMN IF EXISTS actual_attendance;

-- Add back old columns
ALTER TABLE registrations
  ADD COLUMN attendee_name VARCHAR(255),
  ADD COLUMN attendee_email VARCHAR(255);

-- Restore old unique constraint
ALTER TABLE registrations ADD CONSTRAINT registrations_event_id_attendee_email_key
  UNIQUE (event_id, attendee_email);

-- Note: registration_code column from V4 is kept (not dropped in rollback)
*/
