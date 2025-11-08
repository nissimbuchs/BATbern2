-- V11__Support_anonymous_users.sql
-- Enable user_profiles to support anonymous users (no Cognito account)
-- Based on: ADR-006 Unified User Profile for Anonymous and Authenticated Users
-- Architecture: docs/architecture/ADR-006-unified-user-profile.md
-- Story: 4.1.5a Registration Architecture Foundation
--
-- ARCHITECTURAL CHANGE (ADR-006):
-- user_profiles now stores BOTH anonymous and authenticated users:
-- - Anonymous users: cognito_user_id = NULL, email/first_name/last_name from registration
-- - Authenticated users: cognito_user_id = Cognito UUID
--
-- Account linking flow:
-- 1. User registers for event anonymously → user_profile created with cognito_user_id = NULL
-- 2. User creates Cognito account later → UPDATE user_profile SET cognito_user_id = <cognito_id>, claimed_at = NOW()
-- 3. All past event_registrations automatically linked (via attendee_id FK)
--
-- Benefits (vs ADR-005 dual-table approach):
-- - 85% less code for account linking (3 lines vs 20+)
-- - Company as FK (not free text) → better analytics
-- - Single source of truth for user data
-- - No data duplication
--

-- Step 1: Make cognito_user_id nullable (support anonymous users)
ALTER TABLE user_profiles
  ALTER COLUMN cognito_user_id DROP NOT NULL;

-- Step 2: Drop UNIQUE constraint on cognito_user_id (will recreate as partial index)
ALTER TABLE user_profiles
  DROP CONSTRAINT IF EXISTS user_profiles_cognito_user_id_key;

-- Step 3: Add partial unique index (only for non-NULL cognito_user_id)
-- This allows multiple NULL values but ensures authenticated users have unique Cognito IDs
CREATE UNIQUE INDEX idx_user_profiles_cognito_user_id_unique
  ON user_profiles (cognito_user_id)
  WHERE cognito_user_id IS NOT NULL;

-- Step 4: Add claimed_at timestamp (when anonymous user created Cognito account)
ALTER TABLE user_profiles
  ADD COLUMN claimed_at TIMESTAMP WITH TIME ZONE;

-- Step 5: Add index for anonymous users (NULL cognito_user_id)
-- Useful for finding unclaimed anonymous profiles
CREATE INDEX idx_user_profiles_anonymous
  ON user_profiles (email)
  WHERE cognito_user_id IS NULL;

-- Step 6: Update comments
COMMENT ON COLUMN user_profiles.cognito_user_id IS
  'AWS Cognito user identifier (NULL for anonymous users until they create account)';
COMMENT ON COLUMN user_profiles.claimed_at IS
  'Timestamp when anonymous user created Cognito account (NULL for always-authenticated users or unclaimed anonymous users)';

-- Rollback instructions (for reference, DO NOT EXECUTE):
/*
-- To rollback this migration:

-- Drop new index and column
DROP INDEX IF EXISTS idx_user_profiles_anonymous;
DROP INDEX IF EXISTS idx_user_profiles_cognito_user_id_unique;
ALTER TABLE user_profiles DROP COLUMN IF EXISTS claimed_at;

-- Restore NOT NULL constraint
ALTER TABLE user_profiles ALTER COLUMN cognito_user_id SET NOT NULL;

-- Restore unique constraint
ALTER TABLE user_profiles ADD CONSTRAINT user_profiles_cognito_user_id_key
  UNIQUE (cognito_user_id);
*/
