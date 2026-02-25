-- V100: Create minimal user_profiles stub for EMS integration tests.
--
-- INTENTIONAL ARCHITECTURE BREAK (documented in ADR-003):
-- The user_profiles table is owned by company-user-management-service.
-- In production, CUM Flyway migrations create the full table.
-- This stub creates a minimal version for the EMS test container ONLY.
--
-- Only the columns used by EMS native queries are included:
--   - username (JOIN key in session_users and registrations)
--   - company_id (used by findSessionsPerCompany and findUserPortraitsByUsernames)
--
-- This file lives in src/test/resources/db/testmigration/ and is ONLY loaded
-- during integration tests (spring.flyway.locations includes classpath:db/testmigration).
-- It is NEVER deployed to production.

CREATE TABLE IF NOT EXISTS user_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(100) NOT NULL UNIQUE,
    company_id VARCHAR(12),
    -- Columns required by SessionUserRepository.findUserPortraitsByUsernames:
    first_name VARCHAR(100) DEFAULT '',
    last_name VARCHAR(100) DEFAULT '',
    profile_picture_url VARCHAR(2048),
    bio TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
