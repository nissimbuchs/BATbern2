-- V46: Add profile picture URL to speakers table
-- Story 6.2b: Speaker Portal Profile - Photo Upload
--
-- Stores the speaker's profile photo URL locally to avoid cross-service
-- authentication issues in the speaker portal (token-based, no JWT).

-- Add profile_picture_url column to speakers table
ALTER TABLE speakers
    ADD COLUMN IF NOT EXISTS profile_picture_url VARCHAR(1000);

-- Add comment for documentation
COMMENT ON COLUMN speakers.profile_picture_url IS 'CloudFront URL for speaker profile photo, managed via speaker portal';
