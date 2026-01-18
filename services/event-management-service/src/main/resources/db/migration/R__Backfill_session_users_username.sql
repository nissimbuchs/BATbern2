-- R__Backfill_session_users_username.sql
-- DEPRECATED: This migration is now a no-op.
--
-- Background:
-- This migration previously backfilled username from user_id in session_users.
-- V19 migration removed user_id column and made username NOT NULL.
-- The backfill is complete and this migration is no longer needed.
--
-- This file is kept to maintain Flyway checksums but does nothing.

DO $$
BEGIN
    RAISE NOTICE 'R__Backfill_session_users_username: No-op - backfill complete since V19';
END $$;
