-- V122: Add 'aperitif' to sessions.session_type check constraint (Story 15.2)
-- Apéro is a first-class structural slot (like break/lunch/moderation) and is persisted as a
-- Session by StructuralSessionService when an event configures an apéro. Widen the V59 constraint
-- to admit it (preserve every existing value; append 'aperitif').

ALTER TABLE sessions
    DROP CONSTRAINT IF EXISTS sessions_session_type_check;

ALTER TABLE sessions
    ADD CONSTRAINT sessions_session_type_check
        CHECK (session_type IN (
            'keynote', 'presentation', 'workshop', 'panel_discussion',
            'networking', 'break', 'lunch', 'moderation', 'aperitif'
        ));

COMMENT ON COLUMN sessions.session_type IS
    'Type of session: keynote, presentation, workshop, panel_discussion, networking, break, lunch, moderation, aperitif';
