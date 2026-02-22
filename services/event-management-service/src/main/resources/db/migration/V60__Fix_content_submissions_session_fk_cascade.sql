-- V60__Fix_content_submissions_session_fk_cascade.sql
-- Fix: speaker_content_submissions.session_id FK missing ON DELETE SET NULL
-- Without this, deleting an event cascades to sessions, then fails because
-- speaker_content_submissions.session_id still references the session.
-- Setting to SET NULL preserves content history when sessions are removed.

ALTER TABLE speaker_content_submissions
    DROP CONSTRAINT IF EXISTS speaker_content_submissions_session_id_fkey;

ALTER TABLE speaker_content_submissions
    ADD CONSTRAINT speaker_content_submissions_session_id_fkey
        FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE SET NULL;

COMMENT ON CONSTRAINT speaker_content_submissions_session_id_fkey
    ON speaker_content_submissions IS
    'ON DELETE SET NULL: preserves content submissions when sessions are deleted';
