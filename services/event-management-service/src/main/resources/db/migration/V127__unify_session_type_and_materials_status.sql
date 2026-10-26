-- V127: One value per meaning for sessions.session_type and sessions.materials_status (#1123)
--
-- 1. 'aperitif' (V122, Story 15.2) duplicated 'networking' (V2): both are the apéro slot. Apple Watch
--    2.3 knows only 'networking' and fails to decode an event that contains 'aperitif'. Fold the
--    data back to 'networking' and drop 'aperitif' from the allowed values.
-- 2. The V41 trigger stored 'PARTIAL' for a session whose files include no PRESENTATION. The rule
--    is per session: as soon as one file is there, the session has material. Every other code path
--    already computes NONE/COMPLETE that way; the trigger now does too.
--
-- V41 and V122 stay untouched (applied migrations are frozen).

UPDATE sessions SET session_type = 'networking' WHERE session_type = 'aperitif';

ALTER TABLE sessions
    DROP CONSTRAINT IF EXISTS sessions_session_type_check;

ALTER TABLE sessions
    ADD CONSTRAINT sessions_session_type_check
        CHECK (session_type IN (
            'keynote', 'presentation', 'workshop', 'panel_discussion',
            'networking', 'break', 'lunch', 'moderation'
        ));

COMMENT ON COLUMN sessions.session_type IS
    'Type of session: keynote, presentation, workshop, panel_discussion, networking (the apéro), break, lunch, moderation';

UPDATE sessions SET materials_status = 'COMPLETE' WHERE materials_status = 'PARTIAL';

ALTER TABLE sessions
    DROP CONSTRAINT IF EXISTS sessions_materials_status_check;

ALTER TABLE sessions
    ADD CONSTRAINT sessions_materials_status_check
        CHECK (materials_status IN ('NONE', 'COMPLETE'));

CREATE OR REPLACE FUNCTION update_session_materials_summary()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE sessions SET
        materials_count = (SELECT COUNT(*) FROM session_materials WHERE session_id = COALESCE(NEW.session_id, OLD.session_id)),
        has_presentation = (SELECT COUNT(*) > 0 FROM session_materials WHERE session_id = COALESCE(NEW.session_id, OLD.session_id) AND material_type = 'PRESENTATION')
    WHERE id = COALESCE(NEW.session_id, OLD.session_id);

    UPDATE sessions SET
        materials_status = CASE WHEN materials_count = 0 THEN 'NONE' ELSE 'COMPLETE' END
    WHERE id = COALESCE(NEW.session_id, OLD.session_id);

    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

COMMENT ON COLUMN sessions.materials_status IS
    'NONE (no materials) or COMPLETE (at least one material of any type) - updated by trigger';
