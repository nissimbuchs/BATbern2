-- V9__Remove_denormalized_attendee_data.sql
-- Story 4.1.5a: Architecture consolidation for anonymous event registration
-- ADR-004: Factor User Fields from Domain Entities (no duplication)
-- ADR-005: Anonymous Event Registration (cross-service API access)
-- Based on: docs/architecture/ADR-004-factor-user-fields-from-domain-entities.md
--         docs/architecture/ADR-005-anonymous-event-registration.md

-- Remove denormalized attendee fields that duplicate user_profiles data
-- Event Service will call User Management API to fetch user details
-- Follows ADR-004 principle: domain entities reference users, never duplicate

-- Remove attendee_name (duplicates user_profiles.first_name + last_name)
ALTER TABLE registrations DROP COLUMN IF EXISTS attendee_name;

-- Remove attendee_email (duplicates user_profiles.email)
ALTER TABLE registrations DROP COLUMN IF EXISTS attendee_email;

-- Remove attendee_id (replaced by attendee_username for cross-service reference)
ALTER TABLE registrations DROP COLUMN IF EXISTS attendee_id;

-- Update column comment to clarify cross-service reference pattern
COMMENT ON COLUMN registrations.attendee_username IS
    'Reference to user_profiles.username in Company User Management Service. This is NOT a foreign key (cross-service boundary). Use existing UserManagementClient API to fetch full user details (email, name, company). Follows ADR-004 (no duplication) and ADR-005 (anonymous registration support).';

-- Update table comment
COMMENT ON TABLE registrations IS
    'Event registrations for whole events (not individual sessions). Stores only registration-specific data. User details (email, name, company) fetched via UserManagementClient API (ADR-004, ADR-005).';

-- Log migration for audit trail
SELECT
    'V9 Migration Complete: Removed denormalized attendee fields from registrations (ADR-004, ADR-005)' as migration_status,
    'Attendee details now fetched via UserManagementClient API' as implementation_note,
    NOW() as executed_at;
