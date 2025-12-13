-- Migration: Add version column for optimistic locking on events table
-- Story: 5.1a - Workflow State Machine Foundation
-- Purpose: Enable optimistic locking to prevent concurrent workflow state updates

-- Add version column with default 0 for existing rows
ALTER TABLE events
ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Add comment for documentation
COMMENT ON COLUMN events.version IS 'Version number for JPA optimistic locking (@Version)';
