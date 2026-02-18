-- V56__add_watch_session_fields.sql
-- W4.1 Task 5: Watch session control fields for real-time session state broadcast
-- These fields are populated by the session control actions in W4.2+.

ALTER TABLE sessions ADD COLUMN actual_start_time TIMESTAMP;
ALTER TABLE sessions ADD COLUMN actual_end_time TIMESTAMP;
ALTER TABLE sessions ADD COLUMN overrun_minutes INTEGER DEFAULT 0;
ALTER TABLE sessions ADD COLUMN completed_by_username VARCHAR(100);
