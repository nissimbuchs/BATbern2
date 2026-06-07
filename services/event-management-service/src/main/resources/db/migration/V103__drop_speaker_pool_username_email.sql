-- V103: Drop speaker_pool.username and speaker_pool.email columns.
--
-- Story 11.E.9 (post-Epic-11 cleanup, deferred from the BATbern75 follow-up commit).
--
-- Rationale: Phase A's session_users-based identity overlay + Phase B's
-- PrimarySpeakerResolver route every identity read (organizer drawer, email
-- recipient routing, JWT claims, response DTOs) through session_users.username +
-- UserApiClient. The speaker_pool.username and speaker_pool.email columns are
-- now dead-weight duplicates that go stale whenever an organizer reassigns the
-- session's primary speaker on the Sessions tab.
--
-- Pre-flight: enforce the post-11.E.8 invariant that any pool row past READY has
-- a session attached. A row with a non-null legacy username but a null session_id
-- would lose its identity when the column is dropped — refuse to migrate.
DO $$
DECLARE
    orphan_count BIGINT;
BEGIN
    SELECT COUNT(*) INTO orphan_count
    FROM speaker_pool
    WHERE username IS NOT NULL AND session_id IS NULL;
    IF orphan_count > 0 THEN
        RAISE EXCEPTION
            'V103 abort: % speaker_pool row(s) have a legacy username but no session_id. '
            'These rows would lose their identity when the column is dropped. '
            'Resolve by promoting them through CONTACTED -> READY (which provisions a '
            'session + PRIMARY_SPEAKER session_users row), or by clearing username manually '
            'if the row is abandoned brainstorm data.', orphan_count;
    END IF;
END$$;

-- Idempotent backfill: ensure sessions.speaker_pool_id mirrors speaker_pool.session_id
-- for any session reachable from a pool row. Most rows are already in sync after the
-- 2026-05-21 BATbern75 fix; this is a belt-and-suspenders pass that's cheap to repeat.
UPDATE sessions s
SET speaker_pool_id = sp.id
FROM speaker_pool sp
WHERE sp.session_id = s.id
  AND (s.speaker_pool_id IS NULL OR s.speaker_pool_id <> sp.id);

-- Drop the email-lookup index from V44 before dropping the column.
DROP INDEX IF EXISTS idx_speaker_pool_event_email;

-- Drop the columns themselves.
ALTER TABLE speaker_pool DROP COLUMN IF EXISTS username;
ALTER TABLE speaker_pool DROP COLUMN IF EXISTS email;
