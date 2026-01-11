-- V10__Add_attendee_search_cache_fields.sql
-- Performance Optimization: Add denormalized search fields for database-level filtering
--
-- Context:
-- V9 removed attendee_name/attendee_email following ADR-004 (no duplication).
-- This caused severe performance issues with registration search:
--   1. Fetch ALL registrations for an event from database
--   2. HTTP call to User Management Service for EACH registration
--   3. Filter in-memory by search/company
--   4. Paginate the results
--
-- For an event with 500 registrations, this means:
--   - 500 database rows fetched
--   - 500 HTTP calls to enrich user data
--   - In-memory filtering
--   - Return 25 results
--
-- Solution:
-- Add denormalized SEARCH CACHE fields to enable database-level filtering BEFORE pagination.
-- These are NOT the source of truth - full enrichment still happens via UserManagementClient API.
-- Fields are populated on registration creation and updated when user profiles change.
--
-- This follows a common performance pattern:
-- - Store minimal data for filtering/search (cache)
-- - Fetch full data from source of truth for display (enrichment)

-- Add attendee search cache fields
ALTER TABLE registrations
    ADD COLUMN IF NOT EXISTS attendee_first_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS attendee_last_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS attendee_email VARCHAR(255),
    ADD COLUMN IF NOT EXISTS attendee_company_id VARCHAR(100);

-- Add indexes for search performance
CREATE INDEX IF NOT EXISTS idx_attendee_email ON registrations(attendee_email);
CREATE INDEX IF NOT EXISTS idx_attendee_company_id ON registrations(attendee_company_id);

-- Add column comments explaining purpose
COMMENT ON COLUMN registrations.attendee_first_name IS
    'Search cache field for database-level filtering (performance optimization). NOT source of truth - full user data fetched via UserManagementClient API. Updated on registration creation and user profile changes.';

COMMENT ON COLUMN registrations.attendee_last_name IS
    'Search cache field for database-level filtering (performance optimization). NOT source of truth - full user data fetched via UserManagementClient API. Updated on registration creation and user profile changes.';

COMMENT ON COLUMN registrations.attendee_email IS
    'Search cache field for database-level filtering (performance optimization). NOT source of truth - full user data fetched via UserManagementClient API. Updated on registration creation and user profile changes.';

COMMENT ON COLUMN registrations.attendee_company_id IS
    'Search cache field for database-level filtering (performance optimization). NOT source of truth - full user data fetched via UserManagementClient API. Updated on registration creation and user profile changes.';

-- Update table comment
COMMENT ON TABLE registrations IS
    'Event registrations for whole events (not individual sessions). Stores registration-specific data plus denormalized search cache fields. Full user details (email, name, company) fetched via UserManagementClient API (ADR-004, ADR-005). Search cache fields enable database-level filtering before pagination for performance.';

-- Log migration for audit trail
SELECT
    'V10 Migration Complete: Added attendee search cache fields for performance optimization' as migration_status,
    'Fields enable database-level filtering before pagination' as performance_benefit,
    'Full user enrichment still via UserManagementClient API' as implementation_note,
    NOW() as executed_at;
