-- V44__Add_speaker_invitation_fields.sql
-- Story 6.1b: Speaker Invitation System
-- Adds invitation tracking fields to speaker_pool table and INVITED status
-- SOURCE OF TRUTH: docs/stories/6.1b-speaker-invitation-system.md

-- Add invitation tracking fields to speaker_pool
ALTER TABLE speaker_pool
    ADD COLUMN email VARCHAR(255),
    ADD COLUMN invited_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN response_deadline DATE,
    ADD COLUMN content_deadline DATE;

-- Update the status CHECK constraint to include 'invited' state (Story 6.1b)
-- First, find and drop the existing constraint (it was created inline in V14)
ALTER TABLE speaker_pool DROP CONSTRAINT IF EXISTS speaker_pool_status_check;

-- Recreate with 'invited' status included (between 'identified' and 'contacted')
ALTER TABLE speaker_pool ADD CONSTRAINT speaker_pool_status_check CHECK (status IN (
    'identified', 'invited', 'contacted', 'ready', 'accepted', 'declined',
    'content_submitted', 'quality_reviewed', 'slot_assigned',
    'confirmed', 'withdrew', 'overflow'
));

-- Index for finding speaker by event and email (idempotency check)
CREATE INDEX idx_speaker_pool_event_email ON speaker_pool(event_id, email) WHERE email IS NOT NULL;

-- Index for finding speakers with pending invitations (response deadline tracking)
CREATE INDEX idx_speaker_pool_response_deadline ON speaker_pool(response_deadline)
    WHERE response_deadline IS NOT NULL AND status = 'invited';

-- Update the speaker_status_history constraints to include 'invited' status as well
ALTER TABLE speaker_status_history DROP CONSTRAINT IF EXISTS speaker_status_history_previous_status_check;
ALTER TABLE speaker_status_history DROP CONSTRAINT IF EXISTS speaker_status_history_new_status_check;

ALTER TABLE speaker_status_history ADD CONSTRAINT speaker_status_history_previous_status_check CHECK (previous_status IN (
    'identified', 'invited', 'contacted', 'ready', 'accepted', 'declined',
    'content_submitted', 'quality_reviewed', 'slot_assigned',
    'confirmed', 'withdrew', 'overflow'
));

ALTER TABLE speaker_status_history ADD CONSTRAINT speaker_status_history_new_status_check CHECK (new_status IN (
    'identified', 'invited', 'contacted', 'ready', 'accepted', 'declined',
    'content_submitted', 'quality_reviewed', 'slot_assigned',
    'confirmed', 'withdrew', 'overflow'
));

-- Comments documenting architecture alignment
COMMENT ON COLUMN speaker_pool.email IS 'Speaker email address - denormalized for invitation queries (Story 6.1b)';
COMMENT ON COLUMN speaker_pool.invited_at IS 'Timestamp when invitation email was sent (Story 6.1b)';
COMMENT ON COLUMN speaker_pool.response_deadline IS 'Deadline for speaker to respond to invitation (Story 6.1b)';
COMMENT ON COLUMN speaker_pool.content_deadline IS 'Deadline for speaker to submit content - optional (Story 6.1b)';
