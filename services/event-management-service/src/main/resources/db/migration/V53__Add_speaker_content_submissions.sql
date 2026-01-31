-- V53__Add_speaker_content_submissions.sql
-- Story 6.3: Speaker Content Self-Submission Portal
-- Creates speaker_content_submissions table for tracking presentation title/abstract submissions
-- SOURCE OF TRUTH: docs/stories/6.3-content-submission.md

-- Add content_status field to speaker_pool for tracking submission workflow
-- Content Status Flow: PENDING → SUBMITTED → APPROVED or REVISION_NEEDED → SUBMITTED
ALTER TABLE speaker_pool
    ADD COLUMN IF NOT EXISTS content_status VARCHAR(50) DEFAULT 'PENDING'
        CHECK (content_status IN ('PENDING', 'SUBMITTED', 'APPROVED', 'REVISION_NEEDED')),
    ADD COLUMN IF NOT EXISTS content_submitted_at TIMESTAMP WITH TIME ZONE;

-- Create speaker_content_submissions table for storing submission history and versions
CREATE TABLE IF NOT EXISTS speaker_content_submissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    speaker_pool_id UUID NOT NULL REFERENCES speaker_pool(id) ON DELETE CASCADE,
    session_id UUID REFERENCES sessions(id),
    title VARCHAR(200) NOT NULL,
    abstract TEXT NOT NULL,
    abstract_char_count INTEGER NOT NULL,
    submission_version INTEGER DEFAULT 1 NOT NULL,
    reviewer_feedback TEXT,
    submitted_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    reviewed_at TIMESTAMP WITH TIME ZONE,
    reviewed_by VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT chk_abstract_length CHECK (abstract_char_count <= 1000),
    CONSTRAINT chk_title_length CHECK (length(title) <= 200),
    CONSTRAINT chk_submission_version CHECK (submission_version >= 1)
);

-- Indexes for efficient lookups
CREATE INDEX IF NOT EXISTS idx_content_submissions_speaker_pool ON speaker_content_submissions(speaker_pool_id);
CREATE INDEX IF NOT EXISTS idx_content_submissions_session ON speaker_content_submissions(session_id);
CREATE INDEX IF NOT EXISTS idx_content_submissions_version ON speaker_content_submissions(speaker_pool_id, submission_version DESC);

-- Index on speaker_pool for content status filtering
CREATE INDEX IF NOT EXISTS idx_speaker_pool_content_status ON speaker_pool(event_id, content_status)
    WHERE content_status IS NOT NULL;

-- Trigger for automatic updated_at timestamp
CREATE OR REPLACE FUNCTION update_content_submissions_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS update_speaker_content_submissions_updated_at ON speaker_content_submissions;
CREATE TRIGGER update_speaker_content_submissions_updated_at
    BEFORE UPDATE ON speaker_content_submissions
    FOR EACH ROW EXECUTE FUNCTION update_content_submissions_updated_at();

-- Comments documenting architecture alignment
COMMENT ON TABLE speaker_content_submissions IS 'Speaker presentation content submissions with version history - Story 6.3';
COMMENT ON COLUMN speaker_content_submissions.speaker_pool_id IS 'Reference to speaker_pool entry (CASCADE on delete)';
COMMENT ON COLUMN speaker_content_submissions.session_id IS 'Optional reference to assigned session';
COMMENT ON COLUMN speaker_content_submissions.title IS 'Presentation title (max 200 chars)';
COMMENT ON COLUMN speaker_content_submissions.abstract IS 'Presentation abstract text';
COMMENT ON COLUMN speaker_content_submissions.abstract_char_count IS 'Pre-computed character count for validation (max 1000)';
COMMENT ON COLUMN speaker_content_submissions.submission_version IS 'Version number (increments on resubmission after revision)';
COMMENT ON COLUMN speaker_content_submissions.reviewer_feedback IS 'Organizer feedback when status is REVISION_NEEDED';
COMMENT ON COLUMN speaker_content_submissions.reviewed_at IS 'Timestamp when content was reviewed';
COMMENT ON COLUMN speaker_content_submissions.reviewed_by IS 'Username of reviewer';

COMMENT ON COLUMN speaker_pool.content_status IS 'Content submission status: PENDING, SUBMITTED, APPROVED, REVISION_NEEDED - Story 6.3';
COMMENT ON COLUMN speaker_pool.content_submitted_at IS 'Timestamp when content was first submitted - Story 6.3';
