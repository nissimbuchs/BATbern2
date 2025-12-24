-- V26: Add session_id to speaker_status_history
-- Story 5.5: Speakers don't have sessions until content is submitted
-- Migration created: 2025-12-23
--
-- Change:
-- - Add session_id column (nullable) to speaker_status_history table
-- - Speakers don't get sessions until they submit presentation content
-- - History records before CONTENT_SUBMITTED will have NULL session_id
--
-- Rollback:
-- ALTER TABLE speaker_status_history DROP COLUMN session_id;

ALTER TABLE speaker_status_history
ADD COLUMN session_id UUID;

-- Add comment explaining the change
COMMENT ON COLUMN speaker_status_history.session_id IS 'Session ID - NULL until speaker submits content (CONTENT_SUBMITTED status)';
