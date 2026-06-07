-- =============================================================================
-- V103 STAGING PRE-FLIGHT CLEANUP — Story 11.E.9
-- =============================================================================
--
-- DRAFT — apply manually via the bastion tunnel BEFORE merging the PR.
-- Not a Flyway migration — pure operational data hygiene.
--
-- Context: staging is at flyway version V92 (Epic 11 migrations V93–V102 have
-- never run). When this PR deploys, V93→V103 all apply sequentially on EMS
-- boot. The plumbing is fine, but two pieces of data drift on staging will
-- produce ugly side-effects:
--
--   1. THREE archived BATbern58 brainstorm-leftover pool rows have status
--      `content_submitted` + a populated `username` + a NULL `session_id`.
--      V96 (backfill_session_users_for_post_ready_speakers.sql) would
--      provision placeholder Session + PRIMARY_SPEAKER session_users rows for
--      these — adding 3 ghost sessions to BATbern58's archive page.
--      Mitigation: NULL their username so V96's WHERE clause skips them.
--
--   2. THREE active-event pool rows (Sue Ajdini, Marcus Schwemmle in
--      BATbern59; Oliver Chatelain in BATbern60) are at `accepted`/`ready`
--      with NULL username AND NULL session_id — pre-Epic-11 brainstorm
--      entries that never went through the proper CONTACTED → READY
--      provisioning. The post-merge code can't resolve identity for them
--      (no User, no Cognito, no session_users). They'd appear in the
--      organizer kanban without identity, generating confusion.
--      Mitigation: DECLINE them with a clear reason; organizer can re-add
--      via the proper invitation flow if needed.
--
-- Apply with the bastion tunnel running:
--   source /tmp/staging-pg-creds.sh
--   PGPASSWORD="$STAGING_PG_PASSWORD" psql -h localhost -p 5433 \
--     -U "$STAGING_PG_USER" -d batbern -f docs/plans/v103-staging-preflight-cleanup.sql
-- =============================================================================

BEGIN;

-- ──────────────────────────────────────────────────────────────────────────
-- TIER 1 (REQUIRED) — Prevent V96 from creating ghost sessions on BATbern58
-- ──────────────────────────────────────────────────────────────────────────
-- The 3 BATbern58 rows: Baltisar Oswald, Andy Grütter, "Nissim"/nadine.buchs.
-- All status `content_submitted`, no session_id. Real BATbern58 speakers
-- (Roger Wetzel, Simon Martinelli, Nissim Buchs) are correctly linked.

UPDATE speaker_pool
SET username = NULL
WHERE id IN (
    '12d0d4ca-9ef3-44dc-8bb7-fb803140c875',  -- Baltisar Oswald
    'f310c5a0-0bcf-417a-b353-33254413b8db',  -- Andy Grütter (andreas.gruetter)
    '198f72bb-67cc-41aa-b7c5-3ec03676382f'   -- "Nissim" / nadine.buchs duplicate
)
RETURNING id, speaker_name, status, username;
-- Expected: 3 rows with username now NULL.

-- ──────────────────────────────────────────────────────────────────────────
-- TIER 2 (RECOMMENDED) — DECLINE stale brainstorm rows on active events
-- ──────────────────────────────────────────────────────────────────────────
-- Sue Ajdini + Marcus Schwemmle (BATbern59), Oliver Chatelain (BATbern60).
-- Status `accepted`/`ready` with NULL username — no User, no Cognito, no
-- session. These were almost certainly added via the old workflow and never
-- properly invited. Without this cleanup they show in the organizer kanban
-- as accepted/ready speakers with no identity.

UPDATE speaker_pool
SET status         = 'declined',
    declined_at    = NOW(),
    decline_reason = 'Pre-Epic-11 brainstorm entry without User provisioning. '
                  || 'Re-invite via the proper invitation flow if still needed. '
                  || '(Cleaned up 2026-05-21 ahead of Story 11.E.9 deploy.)',
    updated_at     = NOW()
WHERE id IN (
    '76d1b201-7557-4bb8-a9b3-48d8246a138b',  -- Sue Ajdini (BATbern59, accepted)
    'e997c4cf-7968-4130-ae59-e3fe15cc6b66',  -- Marcus Schwemmle (BATbern59, accepted)
    '2e8dfb29-c1dc-48e1-8ee9-9d9577f1b9fe'   -- Oliver Chatelain (BATbern60, ready)
);

-- Also write history rows for the audit trail (V93 will tighten the CHECK
-- constraints later; for now both old + new states are allowed).
INSERT INTO speaker_status_history (
    id, speaker_pool_id, event_id, session_id,
    previous_status, new_status, changed_by_username, change_reason, changed_at
)
SELECT
    uuid_generate_v4(), sp.id, sp.event_id, NULL,
    -- We're declining from the ORIGINAL status — capture it from a CTE-style
    -- subquery. Since we already UPDATEd above, read from history if needed.
    -- Simpler: hardcode the previous statuses (they're known from the SELECT
    -- output above; we ran this script with full knowledge of the data).
    CASE sp.id::text
        WHEN '76d1b201-7557-4bb8-a9b3-48d8246a138b' THEN 'accepted'
        WHEN 'e997c4cf-7968-4130-ae59-e3fe15cc6b66' THEN 'accepted'
        WHEN '2e8dfb29-c1dc-48e1-8ee9-9d9577f1b9fe' THEN 'ready'
    END,
    'declined',
    'system',  -- pre-flight cleanup script, not an organizer action
    sp.decline_reason,
    NOW()
FROM speaker_pool sp
WHERE sp.id IN (
    '76d1b201-7557-4bb8-a9b3-48d8246a138b',
    'e997c4cf-7968-4130-ae59-e3fe15cc6b66',
    '2e8dfb29-c1dc-48e1-8ee9-9d9577f1b9fe'
);

-- ──────────────────────────────────────────────────────────────────────────
-- POST-CLEANUP VERIFICATION
-- ──────────────────────────────────────────────────────────────────────────
-- All counts should be ZERO. If any is non-zero, do NOT merge yet.

SELECT
    'V103 abort blockers' AS check_name,
    COUNT(*) AS row_count
FROM speaker_pool
WHERE username IS NOT NULL AND session_id IS NULL
UNION ALL
SELECT
    'Post-READY rows missing PRIMARY_SPEAKER session_users (on ACTIVE events only)',
    COUNT(*)
FROM speaker_pool sp
JOIN events e ON e.id = sp.event_id
LEFT JOIN sessions s ON s.id = sp.session_id
LEFT JOIN session_users su ON su.session_id = s.id AND su.speaker_role = 'primary_speaker'
WHERE sp.status IN ('ready','invited','accepted','content_submitted','quality_reviewed')
  AND su.id IS NULL
  AND e.event_date > NOW() - INTERVAL '60 days';

-- Roll back if anything looks wrong:
--   ROLLBACK;
-- Commit if both counts are zero:
COMMIT;
