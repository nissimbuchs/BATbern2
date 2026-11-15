-- =============================================================================
-- V104 EMERGENCY ROLLBACK — Story 11.E.9 column drop
-- =============================================================================
--
-- DRAFT ONLY — DO NOT PLACE IN db/migration UNLESS WE ACTUALLY NEED TO ROLL BACK.
--
-- Purpose: undo Flyway V103 (drop_speaker_pool_username_email.sql) so the pre-V103
-- code can boot again. Re-adds the dropped columns + recreates the email index +
-- backfills both columns from the canonical post-Phase-A source (session_users +
-- user_profiles).
--
-- When to apply:
--   1. V103 ran successfully on staging (columns are gone).
--   2. New code is failing in production (e.g. unexpected NullPointerException in
--      a code path that wasn't covered by tests, dashboard regression, etc.).
--   3. We want to git-revert commits 2719ab47 + 7facf3bd to get back to the
--      previous code — but that code expects the columns to exist.
--
-- How to apply:
--   Option A (preferred): cherry-pick this file as V104 onto a hotfix branch,
--   `git revert 2719ab47 7facf3bd` on the same branch, deploy. Flyway picks
--   up V104 on EMS boot, columns are restored, reverted code finds what it
--   expects.
--
--   Option B (emergency manual): run this SQL directly against the prod DB via
--   the bastion tunnel. Then redeploy the previous ECS task definition (which
--   carries the pre-V103 code). Skip cherry-picking V104 — but be aware Flyway
--   in the redeployed pre-V103 service will not have V103 in its history, which
--   Spring's default `validateOnMigrate: true` rejects. Either set
--   `spring.flyway.validate-on-migrate=false` for that boot OR manually
--   `DELETE FROM flyway_schema_history_event_management WHERE version='103'`
--   after this script (since the columns are back, "V103 never happened" from
--   the schema's POV).
--
-- Caveats:
--   - The backfill assumes user_profiles and session_users live in the same DB
--     (true on staging — verified via bastion tunnel; same DB cluster, single
--     schema).
--   - Pool rows without a PRIMARY_SPEAKER session_users row (the 3 truly orphan
--     rows on staging: Sue Ajdini, Marcus Schwemmle, Oliver Chatelain) end up
--     with NULL username + email. This matches their pre-V103 state — they
--     never had a username to begin with.
--   - The pool rows that V96 backfilled (the 3 BATbern58 orphans) end up with
--     the username they had pre-V96 (resolved via session_users.username). Email
--     comes from user_profiles where the User exists; NULL otherwise.
-- =============================================================================

-- Step 1: re-add the columns (idempotent).
ALTER TABLE speaker_pool ADD COLUMN IF NOT EXISTS username VARCHAR(255);
ALTER TABLE speaker_pool ADD COLUMN IF NOT EXISTS email VARCHAR(255);

-- Step 2: backfill speaker_pool.username from the PRIMARY_SPEAKER session_users
-- row joined via session_id. One pool row → at most one PRIMARY per session.
UPDATE speaker_pool sp
SET username = su.username
FROM sessions s
JOIN session_users su
  ON su.session_id = s.id
 AND su.speaker_role = 'primary_speaker'
WHERE sp.session_id = s.id
  AND sp.username IS NULL
  AND su.username IS NOT NULL
  AND su.username <> '';

-- Step 3: backfill speaker_pool.email from user_profiles via the just-restored
-- username. CUMS and EMS share a DB on staging, so the join works directly.
UPDATE speaker_pool sp
SET email = u.email
FROM user_profiles u
WHERE sp.username = u.username
  AND sp.email IS NULL
  AND u.email IS NOT NULL
  AND u.email <> '';

-- Step 4: recreate the V44 index (partial, only on non-null emails) so the
-- legacy findByEventIdAndEmail query keeps its O(log n) lookup performance.
CREATE INDEX IF NOT EXISTS idx_speaker_pool_event_email
    ON speaker_pool(event_id, email)
    WHERE email IS NOT NULL;

-- Step 5: diagnostic — surface any rows that did NOT get a username back. These
-- match the pre-V103 "NULL username" pre-condition: brainstorm rows where the
-- workflow never reached READY, OR truly orphan post-READY rows with no
-- session_users row. Both states are pre-existing data drift that V104 cannot fix.
DO $$
DECLARE
    null_username_count BIGINT;
    null_email_count BIGINT;
BEGIN
    SELECT COUNT(*) INTO null_username_count
      FROM speaker_pool
     WHERE username IS NULL
       AND status NOT IN ('identified', 'contacted', 'declined');
    SELECT COUNT(*) INTO null_email_count
      FROM speaker_pool
     WHERE email IS NULL
       AND username IS NOT NULL;
    IF null_username_count > 0 THEN
        RAISE NOTICE 'V104 rollback: % post-READY speaker_pool rows still have NULL '
                     'username after backfill — these are pre-existing orphans (no '
                     'PRIMARY_SPEAKER session_users row). Same state as pre-V103.',
                     null_username_count;
    END IF;
    IF null_email_count > 0 THEN
        RAISE NOTICE 'V104 rollback: % speaker_pool rows have a username but NULL '
                     'email — User exists in CUMS without an email recorded, or the '
                     'CUMS row was deleted. Pre-existing state, not introduced by V103.',
                     null_email_count;
    END IF;
END $$;

-- Done. Speaker_pool now has username + email + the email index, populated from
-- the canonical session_users + user_profiles sources. The pre-V103 code can boot.
