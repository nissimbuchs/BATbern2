-- Story 6.0: Speaker Profile Foundation
-- Creates the speakers table per ADR-003/ADR-004 patterns
-- References User via username (meaningful ID), NOT userId UUID
-- NO foreign key constraint (cross-service)

CREATE TABLE speakers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- ADR-003: Meaningful ID reference to User (NOT userId UUID)
    -- Cross-service reference - NO foreign key constraint
    username VARCHAR(100) NOT NULL UNIQUE,

    -- Domain-specific fields only (NO email, name, bio, photo, company, position)
    availability VARCHAR(50) NOT NULL CHECK (availability IN (
        'available', 'busy', 'unavailable'
    )) DEFAULT 'available',
    workflow_state VARCHAR(50) NOT NULL CHECK (workflow_state IN (
        'identified', 'contacted', 'ready', 'declined', 'accepted',
        'content_submitted', 'quality_reviewed', 'confirmed', 'overflow', 'withdrew'
    )) DEFAULT 'identified',
    -- Note: slot assignment tracked via session.startTime, NOT as speaker state
    expertise_areas TEXT[] DEFAULT '{}',
    speaking_topics TEXT[] DEFAULT '{}',
    linkedin_url VARCHAR(500),
    twitter_handle VARCHAR(100),
    certifications TEXT[] DEFAULT '{}',
    languages VARCHAR(10)[] DEFAULT ARRAY['de', 'en'],
    speaking_history JSONB DEFAULT '[]',

    -- Timestamps
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    deleted_at TIMESTAMP WITH TIME ZONE  -- Soft delete support

    -- NO FOREIGN KEY to users table (cross-service per ADR-003)
);

-- Indexes per architecture spec
CREATE UNIQUE INDEX idx_speakers_username ON speakers(username);
CREATE INDEX idx_speakers_availability ON speakers(availability);
CREATE INDEX idx_speakers_workflow_state ON speakers(workflow_state);
CREATE INDEX idx_speakers_expertise_areas ON speakers USING GIN(expertise_areas);
CREATE INDEX idx_speakers_speaking_topics ON speakers USING GIN(speaking_topics);
CREATE INDEX idx_speakers_active ON speakers(deleted_at) WHERE deleted_at IS NULL;
