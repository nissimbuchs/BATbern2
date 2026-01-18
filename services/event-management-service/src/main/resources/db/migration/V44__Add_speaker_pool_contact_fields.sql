-- V44__Add_speaker_pool_contact_fields.sql
-- Add email and phone fields to speaker_pool table for contact information
-- Story 6.1: Allow organizers to record speaker contact details during outreach

-- Add email column
ALTER TABLE speaker_pool
    ADD COLUMN IF NOT EXISTS email VARCHAR(255);

-- Add phone column
ALTER TABLE speaker_pool
    ADD COLUMN IF NOT EXISTS phone VARCHAR(50);

-- Add column comments
COMMENT ON COLUMN speaker_pool.email IS 'Email address for contacting the speaker during outreach phase';
COMMENT ON COLUMN speaker_pool.phone IS 'Phone number for contacting the speaker during outreach phase';
