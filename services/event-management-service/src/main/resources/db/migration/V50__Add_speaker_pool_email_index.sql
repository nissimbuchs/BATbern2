-- V50__Add_speaker_pool_email_index.sql
-- Story 6.3: Speaker Account Creation and Linking
-- Add index for efficient case-insensitive email lookup during user registration auto-linking

-- Create partial index on lowercase email for unlinked speaker pool entries
-- This enables efficient lookups when Cognito Lambda links speakers by email
CREATE INDEX IF NOT EXISTS idx_speaker_pool_email_lower
ON speaker_pool(LOWER(email))
WHERE email IS NOT NULL;

-- Comment explaining index purpose
COMMENT ON INDEX idx_speaker_pool_email_lower IS 'Enables efficient case-insensitive email lookup for auto-linking speakers during registration (Story 6.3)';
