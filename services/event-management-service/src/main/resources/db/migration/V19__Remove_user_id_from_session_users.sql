-- V19__Remove_user_id_from_session_users.sql
-- Remove user_id column from session_users table
--
-- Background:
-- Migration to username-based API access is complete (ADR-003: Meaningful Identifiers).
-- The user_id field was kept for backward compatibility but is no longer needed.
-- All code now uses username for User Management Service API calls.
--
-- Changes:
-- 1. Drop unique_session_user constraint (uses user_id)
-- 2. Make username NOT NULL (ensure data integrity)
-- 3. Drop user_id column
-- 4. Drop user_id index

-- Drop old unique constraint that included user_id
ALTER TABLE session_users
DROP CONSTRAINT IF EXISTS unique_session_user;

-- Ensure username is NOT NULL (should already be populated via backfill)
-- This will fail if any rows have NULL username - that's intentional (data quality check)
ALTER TABLE session_users
ALTER COLUMN username SET NOT NULL;

-- Drop user_id index (no longer needed)
DROP INDEX IF EXISTS idx_session_users_user_id;

-- Drop user_id column (no longer needed - username is the primary identifier)
ALTER TABLE session_users
DROP COLUMN IF EXISTS user_id;

-- Update table comment to reflect new architecture
COMMENT ON TABLE session_users IS 'Many-to-many relationship between sessions and users (speakers) - ADR-003: uses username for API-based user data access';
COMMENT ON COLUMN session_users.username IS 'Username (public identifier) for API-based user data access - ADR-003: meaningful identifiers';
