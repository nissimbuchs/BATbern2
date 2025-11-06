-- V8__Add_username_to_session_users.sql
-- Add username field to session_users for API-based user data access
--
-- Background:
-- Migrating from direct database access (UserRepository) to REST API (UserApiClient).
-- Username is the public identifier used in API calls to User Management Service.
-- This allows us to fetch user data without maintaining a direct DB dependency on user_profiles table.
--
-- Migration Strategy:
-- 1. Add username column as nullable
-- 2. Backfill will happen in separate repeatable migration (R__Backfill_session_users_username.sql)
-- 3. Once backfilled, make NOT NULL in future migration

-- Add username column (nullable initially for backfill)
ALTER TABLE session_users
ADD COLUMN IF NOT EXISTS username VARCHAR(100);

-- Add index for performance (username lookups will be common)
CREATE INDEX IF NOT EXISTS idx_session_users_username ON session_users(username);

-- Update unique constraint to include username (prepare for future when we drop user_id)
-- Note: Keep existing constraint for now, will drop user_id in later migration after full API migration
ALTER TABLE session_users
DROP CONSTRAINT IF EXISTS unique_session_user_username;

ALTER TABLE session_users
ADD CONSTRAINT unique_session_user_username UNIQUE (session_id, username);

-- Comment documenting the change
COMMENT ON COLUMN session_users.username IS 'Username (public identifier) for API-based user data access - replaces direct user_id FK dependency';
