-- R__Backfill_session_users_username.sql
-- Repeatable migration to backfill username from user_profiles table
--
-- This migration populates the username field in session_users by joining
-- with the user_profiles table. This is safe because:
-- 1. It's idempotent (only updates NULL usernames)
-- 2. It's repeatable (runs whenever checksum changes)
-- 3. Only runs if user_profiles table exists (graceful handling for test environments)
--
-- After full API migration is complete and validated, this can be removed.

-- Check if user_profiles table exists and backfill if it does
DO $$
DECLARE
    table_exists BOOLEAN;
    rows_backfilled INTEGER := 0;
    rows_remaining INTEGER := 0;
BEGIN
    -- Check if user_profiles table exists
    SELECT EXISTS (
        SELECT FROM information_schema.tables
        WHERE table_schema = 'public'
        AND table_name = 'user_profiles'
    ) INTO table_exists;

    IF table_exists THEN
        -- Backfill username from user_profiles table (only for rows where username is NULL)
        UPDATE session_users su
        SET username = up.username
        FROM user_profiles up
        WHERE su.user_id = up.id
          AND su.username IS NULL;

        -- Log backfill results
        SELECT COUNT(*) INTO rows_backfilled
        FROM session_users
        WHERE username IS NOT NULL;

        SELECT COUNT(*) INTO rows_remaining
        FROM session_users
        WHERE username IS NULL;

        RAISE NOTICE 'Backfill complete: % rows have username, % rows still NULL',
                     rows_backfilled, rows_remaining;

        -- If all rows backfilled, we're ready for NOT NULL constraint in future migration
        IF rows_remaining = 0 THEN
            RAISE NOTICE 'All session_users rows have username - ready for NOT NULL constraint';
        END IF;
    ELSE
        RAISE NOTICE 'Skipping backfill: user_profiles table does not exist (test environment or API-only mode)';
    END IF;
END $$;
