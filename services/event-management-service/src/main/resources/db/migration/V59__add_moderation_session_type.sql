-- V59: Add 'moderation' to sessions.session_type check constraint
-- Required for structural session generation (Plan: Schedule Preview)
-- The moderation type represents the opening/closing segments run by the event organizer

ALTER TABLE sessions
    DROP CONSTRAINT IF EXISTS sessions_session_type_check;

ALTER TABLE sessions
    ADD CONSTRAINT sessions_session_type_check
        CHECK (session_type IN (
            'keynote', 'presentation', 'workshop', 'panel_discussion',
            'networking', 'break', 'lunch', 'moderation'
        ));

COMMENT ON COLUMN sessions.session_type IS
    'Type of session: keynote, presentation, workshop, panel_discussion, networking, break, lunch, moderation';
