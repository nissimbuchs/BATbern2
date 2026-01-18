-- Story 6.1: Automated Speaker Invitation System
-- Creates the speaker_invitations table per ADR-003/ADR-004 patterns
-- References Speaker/Event via username/event_code (meaningful IDs), NOT UUIDs
-- NO foreign key constraints (cross-service references)

CREATE TABLE IF NOT EXISTS speaker_invitations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- ADR-003: Meaningful ID references (NOT UUIDs, NO foreign keys)
    -- Cross-service reference - NO foreign key constraint
    username VARCHAR(100) NOT NULL,           -- Speaker username (references speakers table by convention)
    event_code VARCHAR(50) NOT NULL,          -- Event code (references events table by convention)

    -- Unique response token for passwordless speaker response
    response_token VARCHAR(64) NOT NULL UNIQUE,

    -- Invitation status tracking
    invitation_status VARCHAR(50) NOT NULL DEFAULT 'pending'
        CHECK (invitation_status IN ('pending', 'sent', 'opened', 'responded', 'expired')),

    -- Timestamps for workflow tracking
    sent_at TIMESTAMP WITH TIME ZONE,
    responded_at TIMESTAMP WITH TIME ZONE,

    -- Response details
    response_type VARCHAR(50)                 -- 'accepted', 'declined', 'tentative'
        CHECK (response_type IS NULL OR response_type IN ('accepted', 'declined', 'tentative')),
    decline_reason TEXT,

    -- Expiration and reminder tracking
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    reminder_count INTEGER DEFAULT 0,
    last_reminder_at TIMESTAMP WITH TIME ZONE,

    -- AWS SES tracking
    email_message_id VARCHAR(255),            -- SES message ID for delivery tracking
    email_opened_at TIMESTAMP WITH TIME ZONE,

    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_by VARCHAR(100) NOT NULL          -- Organizer username who sent invitation

    -- NO FK to speakers table (cross-service, ADR-004)
    -- NO FK to events table (cross-service)
);

-- Primary lookup: unique token for response
CREATE UNIQUE INDEX IF NOT EXISTS idx_speaker_invitations_token ON speaker_invitations(response_token);

-- Find invitations for a speaker
CREATE INDEX IF NOT EXISTS idx_speaker_invitations_username ON speaker_invitations(username);

-- Find invitations for an event
CREATE INDEX IF NOT EXISTS idx_speaker_invitations_event_code ON speaker_invitations(event_code);

-- Filter by status
CREATE INDEX IF NOT EXISTS idx_speaker_invitations_status ON speaker_invitations(invitation_status);

-- Find pending invitations approaching expiration
CREATE INDEX IF NOT EXISTS idx_speaker_invitations_expires_at ON speaker_invitations(expires_at)
    WHERE invitation_status NOT IN ('responded', 'expired');

-- Composite index for checking duplicate invitations
CREATE UNIQUE INDEX IF NOT EXISTS idx_speaker_invitations_unique_per_event ON speaker_invitations(username, event_code)
    WHERE invitation_status NOT IN ('expired');

COMMENT ON TABLE speaker_invitations IS 'Speaker invitation tracking for automated invitation workflow. ADR-003/ADR-004 compliant (meaningful IDs, no cross-service FKs). Story 6.1.';
COMMENT ON COLUMN speaker_invitations.username IS 'Speaker username - meaningful ID per ADR-003';
COMMENT ON COLUMN speaker_invitations.event_code IS 'Event code - meaningful ID per ADR-003';
COMMENT ON COLUMN speaker_invitations.response_token IS 'Unique 64-char cryptographic token for passwordless speaker response';
COMMENT ON COLUMN speaker_invitations.invitation_status IS 'Workflow status: pending → sent → opened → responded/expired';
