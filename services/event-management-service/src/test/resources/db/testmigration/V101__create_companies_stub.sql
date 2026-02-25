-- V101: Create minimal companies stub for EMS integration tests.
--
-- INTENTIONAL ARCHITECTURE BREAK (documented in ADR-003):
-- The companies table is owned by company-user-management-service.
-- In production, CUM Flyway migrations create the full table.
-- This stub creates a minimal version for the EMS test container ONLY.
--
-- Only the columns used by EMS native queries are included:
--   - name        (JOIN key in analytics queries: LEFT JOIN companies c ON c.name = ...)
--   - display_name (returned by analytics queries as human-readable company label)
--
-- This file lives in src/test/resources/db/testmigration/ and is ONLY loaded
-- during integration tests (spring.flyway.locations includes classpath:db/testmigration).
-- It is NEVER deployed to production.

CREATE TABLE IF NOT EXISTS companies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE,
    display_name VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
