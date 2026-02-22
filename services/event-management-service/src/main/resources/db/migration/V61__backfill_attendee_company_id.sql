-- V61: Backfill attendee_company_id on registrations from user_profiles.
--
-- Historical registrations were imported via batch without setting attendee_company_id,
-- leaving the column NULL for all ~8000 rows. Since all services share one PostgreSQL
-- database, this joins registrations with user_profiles on attendee_username.
--
-- Wrapped in DO block so it is a no-op when user_profiles does not yet exist
-- (e.g. in isolated EMS integration-test containers that omit CUM migrations).

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'user_profiles'
    ) THEN
        UPDATE registrations r
        SET attendee_company_id = up.company_id
        FROM user_profiles up
        WHERE r.attendee_username = up.username
          AND r.attendee_company_id IS NULL
          AND up.company_id IS NOT NULL;
    END IF;
END $$;
