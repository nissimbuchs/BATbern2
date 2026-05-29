-- V107: add metadata JSONB to registrations
--
-- Auto-participant enrolment for accepted speakers (spec:
-- _bmad-output/implementation-artifacts/spec-auto-participant-email-aliases-excel-export.md)
-- needs a flexible audit field to record WHERE the auto-registration trigger fired
-- (POOL_ACCEPTED, POOL_ACCEPTED_ON_BEHALF, SESSION_PRIMARY_SPEAKER, SESSION_CO_SPEAKER).
-- The existing registration columns are all attendee-PII / status-machine fields, so we
-- add a JSONB metadata column (same shape used on events.metadata and notifications.metadata).
--
-- Default '{}' so the JPA save() path with builder()-only-fields works without a
-- non-null violation; the metadata column is otherwise free-form.

ALTER TABLE registrations
    ADD COLUMN IF NOT EXISTS metadata JSONB NOT NULL DEFAULT '{}';

COMMENT ON COLUMN registrations.metadata IS
    'JSONB audit metadata. Speaker auto-registration records {"autoRegisteredFrom": "<trigger>"}.';
