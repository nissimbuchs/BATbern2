-- V26: Make session_id nullable in speaker_status_history
-- Story 5.5: Speakers don't have sessions until content is submitted
-- Migration created: 2025-12-23
--
-- Change:
-- - Make session_id column nullable in speaker_status_history table
-- - Speakers don't get sessions until they submit presentation content
-- - History records before CONTENT_SUBMITTED will have NULL session_id
--
-- Rollback:
-- ALTER TABLE speaker_status_history ALTER COLUMN session_id SET NOT NULL;

ALTER TABLE speaker_status_history
ALTER COLUMN session_id DROP NOT NULL;

-- Add comment explaining the change
COMMENT ON COLUMN speaker_status_history.session_id IS 'Session ID - NULL until speaker submits content (CONTENT_SUBMITTED status)';
