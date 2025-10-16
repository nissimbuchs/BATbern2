-- V3__Create_companies_schema.sql
-- Company Management domain schema
-- Based on: docs/architecture/03-data-architecture.md (lines 1217-1256)

CREATE TABLE IF NOT EXISTS companies (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL UNIQUE,
    display_name VARCHAR(255),
    swiss_uid VARCHAR(20),
    website VARCHAR(500),
    industry VARCHAR(100),
    description TEXT,
    logo_url VARCHAR(1000),
    logo_s3_key VARCHAR(500),
    logo_file_id VARCHAR(100),
    is_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by VARCHAR(255) NOT NULL
);

CREATE INDEX idx_company_name ON companies(name);
CREATE INDEX idx_company_uid ON companies(swiss_uid);
CREATE INDEX idx_companies_is_verified ON companies(is_verified);

COMMENT ON TABLE companies IS 'Company entities with Swiss business validation and logo management';
COMMENT ON COLUMN companies.name IS 'Official company name (unique identifier)';
COMMENT ON COLUMN companies.display_name IS 'Display name for UI (defaults to name if not provided)';
COMMENT ON COLUMN companies.swiss_uid IS 'Swiss UID format: CHE-XXX.XXX.XXX (optional)';
COMMENT ON COLUMN companies.website IS 'Company website URL';
COMMENT ON COLUMN companies.industry IS 'Company industry/sector classification';
COMMENT ON COLUMN companies.description IS 'Company description text';
COMMENT ON COLUMN companies.is_verified IS 'Whether company has been verified by organizer';
COMMENT ON COLUMN companies.logo_url IS 'CloudFront CDN URL for company logo';
COMMENT ON COLUMN companies.logo_s3_key IS 'S3 storage key for company logo';
COMMENT ON COLUMN companies.logo_file_id IS 'File identifier for company logo';
COMMENT ON COLUMN companies.created_at IS 'Timestamp when company was created';
COMMENT ON COLUMN companies.updated_at IS 'Timestamp when company was last updated';
COMMENT ON COLUMN companies.created_by IS 'User ID who created this company';
