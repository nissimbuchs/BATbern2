-- V49: Allow re-inviting speakers who previously declined
-- Modifies the unique constraint to exclude declined invitations
-- Story 6.1a: Speaker invitation workflow - re-invitation after decline

-- Drop the existing partial unique index
DROP INDEX IF EXISTS idx_speaker_invitations_unique_per_event;

-- Recreate with exclusion for both EXPIRED and DECLINED responses
-- This allows organizers to send new invitations to speakers who:
-- 1. Have an expired invitation (no response in time)
-- 2. Previously declined (might change their mind)
CREATE UNIQUE INDEX idx_speaker_invitations_unique_per_event
    ON speaker_invitations(username, event_code)
    WHERE invitation_status NOT IN ('expired')
      AND NOT (invitation_status = 'responded' AND response_type = 'declined');

-- Also create similar constraint for speaker_pool_id based invitations
-- (speakers without user accounts)
CREATE UNIQUE INDEX IF NOT EXISTS idx_speaker_invitations_unique_pool_per_event
    ON speaker_invitations(speaker_pool_id, event_code)
    WHERE speaker_pool_id IS NOT NULL
      AND invitation_status NOT IN ('expired')
      AND NOT (invitation_status = 'responded' AND response_type = 'declined');

COMMENT ON INDEX idx_speaker_invitations_unique_per_event IS
    'Prevents duplicate active invitations per speaker/event, but allows re-invitation after decline or expiry';
COMMENT ON INDEX idx_speaker_invitations_unique_pool_per_event IS
    'Prevents duplicate active invitations per speaker pool entry/event, but allows re-invitation after decline or expiry';
