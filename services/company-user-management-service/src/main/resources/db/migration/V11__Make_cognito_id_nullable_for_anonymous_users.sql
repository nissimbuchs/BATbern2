-- V11__Make_cognito_id_nullable_for_anonymous_users.sql
-- Story 4.1.5a: Architecture consolidation for anonymous event registration
-- ADR-005: Anonymous Event Registration
-- Based on: docs/architecture/ADR-005-anonymous-event-registration.md

-- Allow anonymous user profiles (cognito_user_id = NULL)
-- This enables public event registration without requiring account creation
ALTER TABLE user_profiles
    ALTER COLUMN cognito_user_id DROP NOT NULL;

-- Ensure data integrity: must have either cognito_id OR email
-- Anonymous users MUST have email, authenticated users MUST have cognito_user_id
ALTER TABLE user_profiles
    ADD CONSTRAINT check_user_identity
    CHECK ((cognito_user_id IS NOT NULL) OR (email IS NOT NULL));

-- Optimize queries for anonymous user lookups by email
-- Partial index only includes rows where cognito_user_id is NULL
CREATE INDEX idx_user_profiles_email_anonymous
    ON user_profiles(email)
    WHERE cognito_user_id IS NULL;

-- Update column comment to reflect new anonymous user capability
COMMENT ON COLUMN user_profiles.cognito_user_id IS
    'AWS Cognito User ID from Cognito User Pool. ' ||
    'NULL for anonymous users who registered for events without creating an account. ' ||
    'Set via auto-linking when user creates Cognito account with matching email (ADR-005).';

COMMENT ON CONSTRAINT check_user_identity ON user_profiles IS
    'ADR-005: Ensures user has either Cognito account (authenticated) or email (anonymous). ' ||
    'Anonymous users: cognito_user_id=NULL, email NOT NULL. ' ||
    'Authenticated users: cognito_user_id NOT NULL.';

-- Log migration for audit trail
SELECT
    'V11 Migration Complete: user_profiles.cognito_user_id is now NULLABLE for anonymous users (ADR-005)' as migration_status,
    NOW() as executed_at;
