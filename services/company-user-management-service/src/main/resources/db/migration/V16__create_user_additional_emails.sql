-- V16__create_user_additional_emails.sql
-- Story 10.32: Additional email addresses per user profile.
--
-- One row per (user, email). Users may register up to N additional emails
-- (cap enforced in the service layer; see UserService.MAX_ADDITIONAL_EMAILS).
-- Used by:
--   1. SES email forwarder Lambda (Story 10.26) — additional emails count as
--      authorised senders for ok@/partner@/batbern{N}@ and receive forwarded
--      copies on the fan-out.
--   2. Event registration confirmation emails (CC'd to additional emails).
--
-- Trigger incident: 2026-05-20 — Nissim's iPhone-Mail forward from legacy
-- info@berner-architekten-treffen.ch to ok@batbern.ch was silently dropped
-- because the shared address was not in role_assignments for ORGANIZER.

CREATE TABLE user_additional_emails (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    email VARCHAR(255) NOT NULL,
    label VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- v1 always writes NULL; the column is reserved for the future verification
    -- flow (deferred per Story 10.32 Resolved Decision #1).
    verified_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT fk_user_additional_emails_user
        FOREIGN KEY (user_id) REFERENCES user_profiles(id)
        ON DELETE CASCADE
);

-- Uniqueness within the additional-emails table (case-insensitive).
CREATE UNIQUE INDEX uq_user_additional_emails_email_lower
    ON user_additional_emails (LOWER(email));

-- Lookup by owning user (used by JOIN FETCH from the User aggregate).
CREATE INDEX idx_user_additional_emails_user_id
    ON user_additional_emails (user_id);

-- Cross-table uniqueness: an additional email may not collide with any primary
-- email on user_profiles. The service layer (UserService.addAdditionalEmail)
-- enforces this on the INSERT path, but we also install a defensive trigger so
-- a direct DB insert cannot violate the invariant. Migration-time check first
-- to ensure existing data is clean.
DO $$
DECLARE
    v_collision_count INTEGER;
BEGIN
    -- Safety check: no existing additional-email row can collide with a
    -- user_profiles.email at migration time. The table is empty on this V16
    -- migration so the check is a tautology, but it documents the invariant.
    SELECT COUNT(*) INTO v_collision_count
    FROM user_additional_emails uae
    JOIN user_profiles up ON LOWER(up.email) = LOWER(uae.email);

    IF v_collision_count > 0 THEN
        RAISE EXCEPTION 'Data integrity error: % additional emails collide with primary emails on user_profiles', v_collision_count;
    END IF;
END $$;

CREATE OR REPLACE FUNCTION enforce_additional_email_not_primary()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM user_profiles up
        WHERE LOWER(up.email) = LOWER(NEW.email)
    ) THEN
        RAISE EXCEPTION 'Additional email % collides with a primary email on user_profiles', NEW.email
            USING ERRCODE = 'unique_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_user_additional_emails_no_primary_collision
    BEFORE INSERT OR UPDATE OF email ON user_additional_emails
    FOR EACH ROW
    EXECUTE FUNCTION enforce_additional_email_not_primary();

-- Symmetric guard on the primary side: a change of user_profiles.email cannot
-- collide with an existing additional email. Primary-email changes are out of
-- scope for Story 10.32, but the constraint costs nothing and prevents a
-- future regression. The trigger excludes the case where the new primary
-- equals an additional email of the SAME user (the only valid "swap" path
-- a future story would exercise).
CREATE OR REPLACE FUNCTION enforce_primary_email_not_other_additional()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM user_additional_emails uae
        WHERE LOWER(uae.email) = LOWER(NEW.email)
          AND uae.user_id <> NEW.id
    ) THEN
        RAISE EXCEPTION 'Primary email % already exists as another user''s additional email', NEW.email
            USING ERRCODE = 'unique_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_user_profiles_no_other_additional_collision
    BEFORE INSERT OR UPDATE OF email ON user_profiles
    FOR EACH ROW
    EXECUTE FUNCTION enforce_primary_email_not_other_additional();

COMMENT ON TABLE  user_additional_emails IS 'Story 10.32: additional email addresses per user. Receive forwarded mail + authorise as sender.';
COMMENT ON COLUMN user_additional_emails.label IS 'Optional free-text user hint (e.g., "Hostpoint shared")';
COMMENT ON COLUMN user_additional_emails.verified_at IS 'Reserved for v2 verification flow; v1 always writes NULL';
