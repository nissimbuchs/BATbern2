-- V5: Create logos table for generic file upload service
-- Story 2.5.3a: Event Theme Image Upload
-- ADR-002: Generic File Upload Service Architecture
--
-- This table implements a state machine pattern for file uploads:
-- PENDING -> CONFIRMED -> ASSOCIATED
--
-- Note: This is a SHARED TABLE accessed by multiple services (shared database pattern)
-- Same schema exists in company-user-management-service (V10)
-- Using CREATE TABLE IF NOT EXISTS to handle both services creating the table

CREATE TABLE IF NOT EXISTS logos (
    -- Primary key
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Public identifier (used in URLs and API responses)
    upload_id VARCHAR(100) UNIQUE NOT NULL,

    -- S3 storage details
    s3_key VARCHAR(500) NOT NULL,
    cloudfront_url VARCHAR(1000),
    file_extension VARCHAR(10) NOT NULL,
    file_size BIGINT NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    checksum VARCHAR(100),

    -- State machine (PENDING, CONFIRMED, ASSOCIATED)
    status VARCHAR(20) NOT NULL,

    -- Entity association (NULL until ASSOCIATED)
    associated_entity_type VARCHAR(50),
    associated_entity_id VARCHAR(255),

    -- Lifecycle timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,

    -- Constraints
    CONSTRAINT chk_logos_status CHECK (status IN ('PENDING', 'CONFIRMED', 'ASSOCIATED')),
    CONSTRAINT chk_logos_file_extension CHECK (file_extension IN ('png', 'jpg', 'jpeg', 'svg')),
    CONSTRAINT chk_logos_file_size CHECK (file_size > 0 AND file_size <= 5242880),
    CONSTRAINT chk_logos_associated_entity CHECK (
        (status = 'ASSOCIATED' AND associated_entity_type IS NOT NULL AND associated_entity_id IS NOT NULL)
        OR
        (status IN ('PENDING', 'CONFIRMED') AND associated_entity_type IS NULL AND associated_entity_id IS NULL)
    )
);

-- Performance indexes (IF NOT EXISTS for shared db pattern)
CREATE INDEX IF NOT EXISTS idx_logos_upload_id ON logos(upload_id);
CREATE INDEX IF NOT EXISTS idx_logos_status_expires ON logos(status, expires_at) WHERE expires_at IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_logos_entity ON logos(associated_entity_type, associated_entity_id) WHERE status = 'ASSOCIATED';
