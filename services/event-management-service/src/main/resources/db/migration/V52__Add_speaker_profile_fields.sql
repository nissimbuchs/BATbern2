-- V52: Add profile fields to speakers table for speaker portal
-- Story 6.2b: Speaker Profile Update Portal
--
-- These fields store speaker profile data locally, allowing
-- the speaker portal to update them without JWT authentication.
-- Values are synced from User service when available, but can be
-- edited directly via the speaker portal.

-- Add first name
ALTER TABLE speakers ADD COLUMN IF NOT EXISTS first_name VARCHAR(100);
COMMENT ON COLUMN speakers.first_name IS 'Speaker first name (editable via speaker portal)';

-- Add last name
ALTER TABLE speakers ADD COLUMN IF NOT EXISTS last_name VARCHAR(100);
COMMENT ON COLUMN speakers.last_name IS 'Speaker last name (editable via speaker portal)';

-- Add email (may already exist from previous migration)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'speakers' AND column_name = 'email'
    ) THEN
        ALTER TABLE speakers ADD COLUMN email VARCHAR(255);
        COMMENT ON COLUMN speakers.email IS 'Speaker email for invitations';
    END IF;
END $$;

-- Add bio
ALTER TABLE speakers ADD COLUMN IF NOT EXISTS bio VARCHAR(2000);
COMMENT ON COLUMN speakers.bio IS 'Speaker biography (editable via speaker portal)';
