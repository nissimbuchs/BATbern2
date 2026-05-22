-- Story 11.E.8 follow-up — content/session consolidation, step 2 of 3.
--
-- Switch the versioned content history to be session-keyed (one talk = one session =
-- one audit log), and drop the speaker_pool_id FK that conflated "who submitted" with
-- "which talk". Multi-speaker sessions become natural — the talk is the talk, regardless
-- of which speaker writes the next version.
--
-- Three steps:
--   1. Backfill session_id from speaker_pool.session_id for any row that has a null
--      session_id (pre-11.E.8 rows where the session was created inside getOrCreateSession
--      but the FK wasn't always copied through).
--   2. Assert: no rows left without session_id. Refuse to migrate if any.
--   3. Drop speaker_pool_id (FK + column), enforce session_id NOT NULL, rename the table.
--
-- After this migration the entity will be ch.batbern.events.domain.SessionContentVersion
-- mapped to session_content_history.

-- 1. Backfill session_id where missing
UPDATE speaker_content_submissions cs
SET session_id = sp.session_id
FROM speaker_pool sp
WHERE cs.speaker_pool_id = sp.id
  AND cs.session_id IS NULL
  AND sp.session_id IS NOT NULL;

-- 2. Sanity check — refuse to lose audit data
DO $$
DECLARE
    orphan_count INT;
BEGIN
    SELECT COUNT(*) INTO orphan_count
    FROM speaker_content_submissions
    WHERE session_id IS NULL;
    IF orphan_count > 0 THEN
        RAISE EXCEPTION 'V99 refusing to rename: % content_submissions rows still have NULL session_id. '
                        'These would lose their audit trail. Investigate with: '
                        'SELECT id, speaker_pool_id, submitted_at FROM speaker_content_submissions '
                        'WHERE session_id IS NULL;', orphan_count;
    END IF;
END $$;

-- 3a. Drop FK constraint (Postgres infers the constraint name from columns; query
--     pg_constraint to drop it without hardcoding the auto-generated name)
DO $$
DECLARE
    fk_name TEXT;
BEGIN
    SELECT conname INTO fk_name
    FROM pg_constraint
    WHERE conrelid = 'speaker_content_submissions'::regclass
      AND contype = 'f'
      AND conkey @> (SELECT array_agg(attnum)
                     FROM pg_attribute
                     WHERE attrelid = 'speaker_content_submissions'::regclass
                       AND attname = 'speaker_pool_id');
    IF fk_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE speaker_content_submissions DROP CONSTRAINT %I', fk_name);
    END IF;
END $$;

-- 3b. Drop the index on speaker_pool_id (if it exists, depending on V53 schema)
DROP INDEX IF EXISTS idx_speaker_content_submissions_speaker_pool;

-- 3c. Drop the column itself
ALTER TABLE speaker_content_submissions DROP COLUMN speaker_pool_id;

-- 3d. Enforce session_id NOT NULL — it's now the primary linkage
ALTER TABLE speaker_content_submissions ALTER COLUMN session_id SET NOT NULL;

-- 3e. Rename the table
ALTER TABLE speaker_content_submissions RENAME TO session_content_history;

-- 3f. Rename any leftover indexes that hardcoded the old name
ALTER INDEX IF EXISTS idx_speaker_content_submissions_submitted_by
RENAME TO idx_session_content_history_submitted_by;
ALTER INDEX IF EXISTS idx_speaker_content_submissions_session
RENAME TO idx_session_content_history_session;
