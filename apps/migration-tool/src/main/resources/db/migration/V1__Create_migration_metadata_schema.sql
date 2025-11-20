-- Migration Tool Metadata Schema
-- Story: 3.2.1 - Migration Tool Implementation
-- Purpose: Track migration job execution, entity ID mappings, and errors

-- Migration job execution tracking
CREATE TABLE migration_job_execution (
    job_execution_id BIGSERIAL PRIMARY KEY,
    job_name VARCHAR(100) NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP,
    status VARCHAR(20) NOT NULL,  -- STARTED, COMPLETED, FAILED
    exit_message TEXT,
    records_processed INTEGER DEFAULT 0,
    records_failed INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Index for job name lookups
CREATE INDEX idx_migration_job_execution_job_name ON migration_job_execution(job_name);
CREATE INDEX idx_migration_job_execution_status ON migration_job_execution(status);

-- Entity ID mapping (legacy ID → new UUID)
-- Supports foreign key resolution in dependent jobs
CREATE TABLE entity_id_mapping (
    id BIGSERIAL PRIMARY KEY,
    entity_type VARCHAR(50) NOT NULL,  -- Company, User, Event, Session, Speaker
    legacy_id VARCHAR(255) NOT NULL,
    new_id UUID NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (entity_type, legacy_id)
);

-- Index for fast lookups during migration
CREATE INDEX idx_entity_id_mapping_type_legacy ON entity_id_mapping(entity_type, legacy_id);
CREATE INDEX idx_entity_id_mapping_new_id ON entity_id_mapping(new_id);

-- Migration errors for manual review
CREATE TABLE migration_errors (
    id BIGSERIAL PRIMARY KEY,
    job_execution_id BIGINT REFERENCES migration_job_execution(job_execution_id),
    entity_type VARCHAR(50) NOT NULL,
    legacy_id VARCHAR(255),
    error_message TEXT NOT NULL,
    stack_trace TEXT,
    retry_count INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    resolved BOOLEAN DEFAULT FALSE,
    resolved_at TIMESTAMP
);

-- Index for error analysis
CREATE INDEX idx_migration_errors_job_execution ON migration_errors(job_execution_id);
CREATE INDEX idx_migration_errors_entity_type ON migration_errors(entity_type);
CREATE INDEX idx_migration_errors_resolved ON migration_errors(resolved);

-- Comments for documentation
COMMENT ON TABLE migration_job_execution IS 'Tracks Spring Batch job execution metadata for monitoring and rollback';
COMMENT ON TABLE entity_id_mapping IS 'Maps legacy IDs to new UUIDs for foreign key resolution across migration jobs';
COMMENT ON TABLE migration_errors IS 'Logs migration errors for manual review and retry';

COMMENT ON COLUMN entity_id_mapping.entity_type IS 'Entity type: Company, User, Event, Session, Speaker';
COMMENT ON COLUMN entity_id_mapping.legacy_id IS 'Original ID from legacy system (e.g., company name, BAT number)';
COMMENT ON COLUMN entity_id_mapping.new_id IS 'New UUID assigned in target system';
