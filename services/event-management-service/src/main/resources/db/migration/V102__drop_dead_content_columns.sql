-- Story 11.E.8 follow-up — content/session consolidation, step 3 of 3.
--
-- Drop four columns that became dead after V98+V99:
--
--   speaker_pool.initial_presentation_title  — legacy "working title" left over from an
--     earlier story; never written by current code. Sessions.title is the canonical now.
--   speaker_pool.content_status              — denormalized projection of "latest history
--     row has feedback OR not". Now derived at read time in DTOs (PENDING / SUBMITTED /
--     REVISION_NEEDED / APPROVED).
--   speaker_pool.content_submitted_at        — same denormalization issue; derived from
--     latest session_content_history.submitted_at.
--   session_users.presentation_title         — vestigial subtitle field, never populated
--     by the submit flow. BATbern's pattern is one talk per session.
--
-- Drops are irreversible by design — the user requested "no backward compatibility".
-- Anything that read these columns was migrated to derive-from-history in the same
-- patch series.

ALTER TABLE speaker_pool DROP COLUMN IF EXISTS initial_presentation_title;
ALTER TABLE speaker_pool DROP COLUMN IF EXISTS content_status;
ALTER TABLE speaker_pool DROP COLUMN IF EXISTS content_submitted_at;
ALTER TABLE session_users DROP COLUMN IF EXISTS presentation_title;
