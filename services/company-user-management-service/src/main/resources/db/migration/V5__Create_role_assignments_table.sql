-- V5__Create_role_assignments_table.sql
-- Story 1.16.2: Role assignments use UUID FK internally (user_profiles.id)
-- API uses username for role management endpoints
-- Based on: docs/stories/1.14-2.user-management-service-foundation.md

CREATE TABLE role_assignments (
    user_id UUID NOT NULL REFERENCES user_profiles(id) ON DELETE CASCADE,
    role VARCHAR(50) NOT NULL CHECK (role IN ('ORGANIZER', 'SPEAKER', 'PARTNER', 'ATTENDEE')),
    granted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    granted_by UUID REFERENCES user_profiles(id),
    PRIMARY KEY (user_id, role)
);

COMMENT ON TABLE role_assignments IS 'User role assignments - uses UUID FK internally, API endpoints use username';
COMMENT ON COLUMN role_assignments.user_id IS 'Internal UUID FK to user_profiles.id';
COMMENT ON COLUMN role_assignments.role IS 'User role: ORGANIZER, SPEAKER, PARTNER, or ATTENDEE';
COMMENT ON COLUMN role_assignments.granted_at IS 'Timestamp when role was granted';
COMMENT ON COLUMN role_assignments.granted_by IS 'User who granted this role (NULL for system-assigned)';
