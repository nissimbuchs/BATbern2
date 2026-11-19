-- Issue sweep 2026-11, epic 2 (#1222 items 3, 4): drop the three notification preferences that
-- nothing reads any more. The code stopped using them in the previous deploy (PR #1323), so no
-- running CUMS task and no rollback target reads these columns (the #1308/#1309 two-deploy pattern).
ALTER TABLE user_profiles
    DROP COLUMN IF EXISTS pref_in_app_notifications,
    DROP COLUMN IF EXISTS pref_push_notifications,
    DROP COLUMN IF EXISTS pref_notification_frequency;
