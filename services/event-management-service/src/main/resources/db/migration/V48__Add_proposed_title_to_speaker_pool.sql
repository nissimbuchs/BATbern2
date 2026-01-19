-- V48: Add proposed presentation title to speaker pool
-- Stores the presentation title proposed by speaker when accepting invitation
-- This allows organizers to see proposed titles in the speaker dashboard

ALTER TABLE speaker_pool
ADD COLUMN proposed_presentation_title VARCHAR(200),
ADD COLUMN comments_for_organizer TEXT;

COMMENT ON COLUMN speaker_pool.proposed_presentation_title IS 'Presentation title proposed by speaker when accepting invitation';
COMMENT ON COLUMN speaker_pool.comments_for_organizer IS 'Additional comments from speaker to organizer';
