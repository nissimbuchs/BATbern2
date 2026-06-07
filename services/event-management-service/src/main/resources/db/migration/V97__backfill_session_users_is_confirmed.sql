-- Story 11.E.8 follow-up: backfill session_users.is_confirmed for legacy rows.
-- The V96 backfill only created NEW session_users rows for speaker_pool entries whose
-- session_id was NULL. It did NOT touch rows that already existed — those were created
-- by the old ContentSubmissionService.getOrCreateSession path (at CONTENT_SUBMITTED time,
-- before today's changes) with is_confirmed=false, and the speaker's ACCEPTED transition
-- predated the new runAcceptedHook that calls SessionUser.confirm().
--
-- Symptom: a speaker who reached ACCEPTED+ before today is observed with
-- session_users.is_confirmed=false in the API response, even though their workflow status
-- is content_submitted or quality_reviewed.
--
-- This migration sweeps the population once: for every session_users row whose linked
-- speaker_pool entry is in ACCEPTED / CONTENT_SUBMITTED / QUALITY_REVIEWED, flip
-- is_confirmed=true and stamp confirmed_at from speaker_pool.accepted_at (NOW() as a
-- safety net if accepted_at is somehow null on a post-ACCEPTED row).
--
-- The link is via speaker_pool.session_id → sessions.id ← session_users.session_id,
-- matched on username so we only confirm the PRIMARY_SPEAKER associated with that
-- speaker_pool entry (CO_SPEAKER / MODERATOR rows added manually by an organizer keep
-- their existing is_confirmed value).
--
-- Idempotent via the is_confirmed=false gate — re-running this against an already-healed
-- DB is a no-op.

UPDATE session_users su
SET is_confirmed = TRUE,
    confirmed_at = COALESCE(sp.accepted_at, NOW()),
    updated_at = NOW()
FROM speaker_pool sp
WHERE su.is_confirmed = FALSE
  AND su.session_id = sp.session_id
  AND su.username = sp.username
  AND sp.status IN ('accepted', 'content_submitted', 'quality_reviewed');
