-- V10: Create logos table for generic file upload service
-- Story 1.16.3: Generic File Upload Service
-- ADR-002: Generic File Upload Service Architecture
--
-- This table implements a state machine pattern for file uploads:
-- PENDING -> CONFIRMED -> ASSOCIATED
--
-- PENDING: Upload URL generated, file may not yet be in S3 (expires after 24h)
-- CONFIRMED: File successfully uploaded to S3 and verified (expires after 7 days if not associated)
-- ASSOCIATED: File linked to an entity (company, user, event, etc.), kept indefinitely
--
-- Note: This is a SHARED TABLE accessed by multiple services (shared database pattern)
-- Same schema exists in event-management-service (V5)
-- Using CREATE TABLE IF NOT EXISTS to handle both services creating the table

CREATE TABLE IF NOT EXISTS logos (
    -- Primary key
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Public identifier (used in URLs and API responses)
    upload_id VARCHAR(100) UNIQUE NOT NULL,

    -- S3 storage details
    s3_key VARCHAR(500) NOT NULL,              -- Current S3 object key (temp or final location)
    cloudfront_url VARCHAR(1000),              -- CDN URL for file access
    file_extension VARCHAR(10) NOT NULL,       -- png, jpg, jpeg, svg
    file_size BIGINT NOT NULL,                 -- File size in bytes
    mime_type VARCHAR(100) NOT NULL,           -- image/png, image/jpeg, image/svg+xml
    checksum VARCHAR(100),                     -- SHA-256 checksum for integrity verification

    -- State machine (PENDING, CONFIRMED, ASSOCIATED)
    status VARCHAR(20) NOT NULL,

    -- Entity association (NULL until ASSOCIATED)
    associated_entity_type VARCHAR(50),        -- COMPANY, USER, EVENT, PARTNER, SPEAKER
    associated_entity_id VARCHAR(255),         -- Entity's identifier (e.g., company name, username)

    -- Lifecycle timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,                      -- For cleanup: PENDING (24h), CONFIRMED (7 days), ASSOCIATED (NULL)

    -- Constraints
    CONSTRAINT chk_status CHECK (status IN ('PENDING', 'CONFIRMED', 'ASSOCIATED')),
    CONSTRAINT chk_file_extension CHECK (file_extension IN ('png', 'jpg', 'jpeg', 'svg')),
    CONSTRAINT chk_file_size CHECK (file_size > 0 AND file_size <= 5242880), -- Max 5MB
    CONSTRAINT chk_associated_entity CHECK (
        (status = 'ASSOCIATED' AND associated_entity_type IS NOT NULL AND associated_entity_id IS NOT NULL)
        OR
        (status IN ('PENDING', 'CONFIRMED') AND associated_entity_type IS NULL AND associated_entity_id IS NULL)
    )
);

-- Performance indexes (IF NOT EXISTS for shared db pattern)
CREATE INDEX IF NOT EXISTS idx_logos_upload_id ON logos(upload_id);
CREATE INDEX IF NOT EXISTS idx_logos_status_expires ON logos(status, expires_at) WHERE expires_at IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_logos_entity ON logos(associated_entity_type, associated_entity_id) WHERE status = 'ASSOCIATED';

-- Comments for documentation
COMMENT ON TABLE logos IS 'Generic file upload storage with state machine pattern (PENDING -> CONFIRMED -> ASSOCIATED)';
COMMENT ON COLUMN logos.upload_id IS 'Public identifier used in API requests and responses';
COMMENT ON COLUMN logos.s3_key IS 'S3 object key: temp location (logos/temp/{uploadId}/) or final (logos/{year}/{type}/{name}/)';
COMMENT ON COLUMN logos.cloudfront_url IS 'CloudFront CDN URL for file delivery';
COMMENT ON COLUMN logos.status IS 'Upload lifecycle state: PENDING (upload initiated), CONFIRMED (file in S3), ASSOCIATED (linked to entity)';
COMMENT ON COLUMN logos.expires_at IS 'Expiration timestamp for cleanup: PENDING (24h), CONFIRMED (7 days), ASSOCIATED (NULL - indefinite)';
COMMENT ON COLUMN logos.associated_entity_type IS 'Type of entity this logo is associated with (COMPANY, USER, EVENT, etc.)';
COMMENT ON COLUMN logos.associated_entity_id IS 'Identifier of the associated entity (e.g., company name, username)';
