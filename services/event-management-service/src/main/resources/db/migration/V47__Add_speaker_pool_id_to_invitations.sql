-- V47: Add speaker_pool_id to support invitations without user accounts
-- Story 6.x: Speaker invitations can now be sent to speakers in the pool
-- who don't have a user account (username is optional)

-- Add speaker_pool_id to reference the speaker pool entry directly
ALTER TABLE speaker_invitations
ADD COLUMN speaker_pool_id UUID;

-- Add denormalized email and name for invitation delivery
-- (avoids cross-table lookups during email sending)
ALTER TABLE speaker_invitations
ADD COLUMN speaker_email VARCHAR(255);

ALTER TABLE speaker_invitations
ADD COLUMN speaker_name VARCHAR(255);

-- Make username nullable (was NOT NULL)
-- Invitations can now be sent via speakerPoolId without requiring username
ALTER TABLE speaker_invitations
ALTER COLUMN username DROP NOT NULL;

-- Add index for speaker_pool_id lookups
CREATE INDEX idx_speaker_invitations_speaker_pool_id ON speaker_invitations(speaker_pool_id);

-- Add constraint: must have either username OR speaker_pool_id
-- This ensures every invitation references a valid speaker identifier
ALTER TABLE speaker_invitations
ADD CONSTRAINT chk_speaker_identifier
CHECK (username IS NOT NULL OR speaker_pool_id IS NOT NULL);

-- Add comments for documentation
COMMENT ON COLUMN speaker_invitations.speaker_pool_id IS 'UUID of SpeakerPool entry - used for speakers without user accounts';
COMMENT ON COLUMN speaker_invitations.speaker_email IS 'Denormalized email for invitation delivery';
COMMENT ON COLUMN speaker_invitations.speaker_name IS 'Denormalized name for email personalization';
