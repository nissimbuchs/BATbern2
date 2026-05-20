-- Story 11.E.8 follow-up — content/session consolidation, step 1 of 3.
--
-- Add submitted_by_username to speaker_content_submissions so the versioned audit row
-- is fully self-describing — once V99 drops speaker_pool_id we can no longer reach
-- speaker_status_history to recover "who submitted version N". The submitter (speaker
-- on the portal or organizer on behalf) is the actor we already record at the workflow
-- transition layer; replicate it here.
--
-- Backfill strategy: for each existing row, find the closest speaker_status_history
-- transition into CONTENT_SUBMITTED for the same speaker_pool around the submission
-- timestamp (±5 seconds, since submit() writes both in the same @Transactional). Use
-- that row's changed_by_username. Fallback to reviewed_by (for legacy reject-paths
-- that synthesized a content_submission), else 'system' so the NOT NULL constraint
-- can be enforced.

ALTER TABLE speaker_content_submissions
ADD COLUMN submitted_by_username VARCHAR(100);

UPDATE speaker_content_submissions cs
SET submitted_by_username = (
    SELECT sh.changed_by_username
    FROM speaker_status_history sh
    WHERE sh.speaker_pool_id = cs.speaker_pool_id
      AND sh.new_status = 'content_submitted'
      AND sh.changed_at BETWEEN cs.submitted_at - INTERVAL '5 seconds'
                            AND cs.submitted_at + INTERVAL '5 seconds'
    ORDER BY abs(extract(epoch FROM (sh.changed_at - cs.submitted_at)))
    LIMIT 1
)
WHERE submitted_by_username IS NULL;

UPDATE speaker_content_submissions
SET submitted_by_username = COALESCE(reviewed_by, 'system')
WHERE submitted_by_username IS NULL;

ALTER TABLE speaker_content_submissions
ALTER COLUMN submitted_by_username SET NOT NULL;

CREATE INDEX idx_speaker_content_submissions_submitted_by
ON speaker_content_submissions (submitted_by_username);
