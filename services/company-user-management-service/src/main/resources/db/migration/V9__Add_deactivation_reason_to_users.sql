-- Story 1.2.5: User Sync and Reconciliation Implementation
-- Add deactivation_reason column to user_profiles table

ALTER TABLE user_profiles
ADD COLUMN deactivation_reason TEXT;

COMMENT ON COLUMN user_profiles.deactivation_reason IS 'Reason why user account was deactivated (e.g., Cognito user deleted, admin action)';
