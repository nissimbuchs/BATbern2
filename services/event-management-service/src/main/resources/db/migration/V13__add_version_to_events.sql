-- Migration: Add version column for optimistic locking on events table
-- Story: 5.1a - Workflow State Machine Foundation
-- Purpose: Enable optimistic locking to prevent concurrent workflow state updates

-- Add version column with default 0 for existing rows (idempotent migration)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'events' AND column_name = 'version'
    ) THEN
        ALTER TABLE events
        ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
    END IF;
END $$;

-- Add comment for documentation
COMMENT ON COLUMN events.version IS 'Version number for JPA optimistic locking (@Version)';
