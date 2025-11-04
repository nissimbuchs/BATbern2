-- V7__Add_session_users_junction_table.sql
-- Many-to-many relationship between sessions and users (speakers)
-- Based on ADR-004: Domain entities reference User via userId FK
-- Source: docs/architecture/03-data-architecture.md Section on Sessions

-- Session-User junction table (many-to-many)
-- Users with SPEAKER role can be assigned to multiple sessions
-- Sessions can have multiple speakers with different roles
CREATE TABLE IF NOT EXISTS session_users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_id UUID NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    user_id UUID NOT NULL, -- FK to user_profiles.id in company-user-management-service
    speaker_role VARCHAR(50) NOT NULL CHECK (speaker_role IN (
        'primary_speaker', 'co_speaker', 'moderator', 'panelist'
    )),
    presentation_title VARCHAR(255), -- Optional speaker-specific presentation title
    is_confirmed BOOLEAN DEFAULT FALSE,
    invited_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    confirmed_at TIMESTAMP WITH TIME ZONE,
    declined_at TIMESTAMP WITH TIME ZONE,
    decline_reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    -- Ensure one user can only have one role per session
    CONSTRAINT unique_session_user UNIQUE (session_id, user_id)
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_session_users_session_id ON session_users(session_id);
CREATE INDEX IF NOT EXISTS idx_session_users_user_id ON session_users(user_id);
CREATE INDEX IF NOT EXISTS idx_session_users_confirmed ON session_users(is_confirmed);
CREATE INDEX IF NOT EXISTS idx_session_users_role ON session_users(speaker_role);

-- Trigger for automatic updated_at on session_users
DROP TRIGGER IF EXISTS update_session_users_updated_at ON session_users;
CREATE TRIGGER update_session_users_updated_at BEFORE UPDATE ON session_users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Comments documenting architecture alignment
COMMENT ON TABLE session_users IS 'Many-to-many relationship between sessions and users (speakers) - ADR-004 pattern: references User via user_id FK';
COMMENT ON COLUMN session_users.user_id IS 'Foreign key to user_profiles.id (in company-user-management-service) - ADR-004: domain entities reference User';
COMMENT ON COLUMN session_users.speaker_role IS 'Speaker role in session: primary_speaker (main presenter), co_speaker (co-presenter), moderator (panel moderator), panelist (panel participant)';
COMMENT ON COLUMN session_users.presentation_title IS 'Optional speaker-specific presentation title if different from session title';
