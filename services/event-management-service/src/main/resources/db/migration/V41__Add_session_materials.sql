-- V41__Add_session_materials.sql
-- Session materials upload support (Story 5.9)
-- Enables speakers and organizers to upload presentations, documents, and videos for sessions
-- Based on ADR-002 Generic File Upload Service pattern

-- Create session_materials table
CREATE TABLE session_materials (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    upload_id VARCHAR(100) UNIQUE NOT NULL,          -- From generic upload service
    s3_key VARCHAR(500) NOT NULL,                     -- S3 object key
    cloudfront_url VARCHAR(1000) NOT NULL,           -- CDN URL for access
    file_name VARCHAR(255) NOT NULL,                 -- Original file name
    file_extension VARCHAR(10) NOT NULL,             -- pptx, pdf, mp4, etc.
    file_size BIGINT NOT NULL,                       -- Size in bytes
    mime_type VARCHAR(100) NOT NULL,                 -- application/pdf, video/mp4, etc.
    material_type VARCHAR(50) NOT NULL CHECK (material_type IN (
        'PRESENTATION', 'DOCUMENT', 'VIDEO', 'OTHER'
    )),
    uploaded_by VARCHAR(255) NOT NULL,               -- Username who uploaded
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,

    -- Content extraction tracking (for future RAG search - Story 5.10)
    content_extracted BOOLEAN NOT NULL DEFAULT FALSE,
    extraction_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (extraction_status IN (
        'PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'NOT_APPLICABLE'
    ))
);

-- Indexes for session_materials
CREATE INDEX idx_session_materials_session_id ON session_materials(session_id);
CREATE INDEX idx_session_materials_upload_id ON session_materials(upload_id);
CREATE INDEX idx_session_materials_extraction_status ON session_materials(extraction_status);
CREATE INDEX idx_session_materials_material_type ON session_materials(material_type);
CREATE INDEX idx_session_materials_uploaded_by ON session_materials(uploaded_by);

-- Add trigger for automatic updated_at
CREATE TRIGGER update_session_materials_updated_at
BEFORE UPDATE ON session_materials
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Extend sessions table with materials summary (for efficient querying)
ALTER TABLE sessions ADD COLUMN materials_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE sessions ADD COLUMN has_presentation BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE sessions ADD COLUMN materials_status VARCHAR(20) DEFAULT 'NONE' CHECK (materials_status IN (
    'NONE', 'PARTIAL', 'COMPLETE'
));

-- Create index for materials_status queries
CREATE INDEX idx_sessions_materials_status ON sessions(materials_status);

-- Function to update session materials summary
CREATE OR REPLACE FUNCTION update_session_materials_summary()
RETURNS TRIGGER AS $$
BEGIN
    -- Update the session's materials summary after INSERT or DELETE
    UPDATE sessions SET
        materials_count = (SELECT COUNT(*) FROM session_materials WHERE session_id = COALESCE(NEW.session_id, OLD.session_id)),
        has_presentation = (SELECT COUNT(*) > 0 FROM session_materials WHERE session_id = COALESCE(NEW.session_id, OLD.session_id) AND material_type = 'PRESENTATION')
    WHERE id = COALESCE(NEW.session_id, OLD.session_id);

    -- Update materials_status based on materials presence
    UPDATE sessions SET
        materials_status = CASE
            WHEN materials_count = 0 THEN 'NONE'
            WHEN has_presentation THEN 'COMPLETE'
            ELSE 'PARTIAL'
        END
    WHERE id = COALESCE(NEW.session_id, OLD.session_id);

    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

-- Trigger to update session materials summary on INSERT
CREATE TRIGGER trigger_update_session_materials_summary_insert
AFTER INSERT ON session_materials
FOR EACH ROW EXECUTE FUNCTION update_session_materials_summary();

-- Trigger to update session materials summary on DELETE
CREATE TRIGGER trigger_update_session_materials_summary_delete
AFTER DELETE ON session_materials
FOR EACH ROW EXECUTE FUNCTION update_session_materials_summary();

-- Comments documenting the schema
COMMENT ON TABLE session_materials IS 'Session materials (presentations, documents, videos) uploaded by speakers/organizers - Story 5.9';
COMMENT ON COLUMN session_materials.upload_id IS 'Unique upload identifier from Generic File Upload Service (ADR-002)';
COMMENT ON COLUMN session_materials.material_type IS 'Type of material: PRESENTATION (pptx, key), DOCUMENT (pdf, doc), VIDEO (mp4, mov), OTHER';
COMMENT ON COLUMN session_materials.extraction_status IS 'Content extraction status for RAG search (Story 5.10) - PENDING by default';
COMMENT ON COLUMN sessions.materials_count IS 'Cached count of materials for this session - updated by trigger';
COMMENT ON COLUMN sessions.has_presentation IS 'Whether session has at least one PRESENTATION material - updated by trigger';
COMMENT ON COLUMN sessions.materials_status IS 'Overall materials status: NONE (no materials), PARTIAL (materials but no presentation), COMPLETE (has presentation)';
