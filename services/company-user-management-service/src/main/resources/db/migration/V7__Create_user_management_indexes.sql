-- V7__Create_user_management_indexes.sql
-- Story 1.16.2: Critical indexes for dual-identifier pattern
-- Based on: docs/stories/1.14-2.user-management-service-foundation.md

-- Story 1.16.2: Critical indexes for dual-identifier pattern

-- Public API lookups (username is the public ID)
CREATE UNIQUE INDEX idx_users_username ON user_profiles(username);

-- Email uniqueness and login lookups
CREATE UNIQUE INDEX idx_users_email ON user_profiles(email);

-- Company affiliation queries (e.g., GET /users?company=GoogleZH)
CREATE INDEX idx_users_company ON user_profiles(company_id) WHERE company_id IS NOT NULL;

-- Cognito authentication lookups
CREATE UNIQUE INDEX idx_users_cognito_user_id ON user_profiles(cognito_user_id);

-- Active users filter
CREATE INDEX idx_users_active ON user_profiles(is_active) WHERE is_active = TRUE;

-- Role-based queries
CREATE INDEX idx_role_assignments_role ON role_assignments(role);
CREATE INDEX idx_role_assignments_user ON role_assignments(user_id);

-- Activity history queries
CREATE INDEX idx_activity_history_user_timestamp ON activity_history(user_id, timestamp DESC);
CREATE INDEX idx_activity_history_entity ON activity_history(entity_type, entity_id);

-- Case-insensitive username lookups (for search)
CREATE INDEX idx_users_username_lower ON user_profiles(LOWER(username));

-- Case-insensitive company lookups (for search)
CREATE INDEX idx_users_company_lower ON user_profiles(LOWER(company_id)) WHERE company_id IS NOT NULL;

-- Performance index for full name search
CREATE INDEX idx_users_fullname ON user_profiles(first_name, last_name);
CREATE INDEX idx_users_fullname_lower ON user_profiles(LOWER(first_name), LOWER(last_name));

COMMENT ON INDEX idx_users_username IS 'Story 1.16.2: Primary lookup index for public API (username is the exposed ID)';
COMMENT ON INDEX idx_users_company IS 'Story 1.16.2: Lookup by company name (not UUID)';
COMMENT ON INDEX idx_users_username_lower IS 'Case-insensitive username search';
COMMENT ON INDEX idx_users_company_lower IS 'Case-insensitive company name search';
COMMENT ON INDEX idx_activity_history_user_timestamp IS 'Efficient activity history queries sorted by timestamp';
