-- V21: Make session fields nullable for placeholder sessions
-- Story 5.4 TODO Resolution - IMPL-001
--
-- Rationale: When speakers are added to the pool, we create "placeholder" sessions
-- with title (SpeakerName - Company) but without type and timing details.
-- The type and timing are filled in later during agenda planning.

ALTER TABLE sessions
ALTER COLUMN session_type DROP NOT NULL;

ALTER TABLE sessions
ALTER COLUMN start_time DROP NOT NULL;

ALTER TABLE sessions
ALTER COLUMN end_time DROP NOT NULL;

COMMENT ON COLUMN sessions.session_type IS
'Session type - nullable for placeholder sessions, assigned during agenda planning';

COMMENT ON COLUMN sessions.start_time IS
'Session start time - nullable for placeholder sessions, assigned during agenda finalization';

COMMENT ON COLUMN sessions.end_time IS
'Session end time - nullable for placeholder sessions, assigned during agenda finalization';
