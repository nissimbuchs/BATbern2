-- V152: Story 4.9 (API conventions I). The moderator presentation settings move from CUMS
-- (table presentation_settings, single row id = 1, CUMS V14) into the EMS app_settings store,
-- key 'presentation', as JSON {"aboutText": ..., "partnerCount": ...}.
--
-- A one-off move in the shared database, not a runtime read of another service's table
-- (ADR-016 unchanged): copy and drop happen in this one statement, so the copy cannot be lost
-- to the order in which CUMS and EMS deploy. Where the table does not exist (EMS test
-- containers, a fresh database) this is a no-op. The pre-deploy RDS snapshot covers it.
DO $$
BEGIN
    IF to_regclass('presentation_settings') IS NOT NULL THEN
        INSERT INTO app_settings (setting_key, setting_value, updated_by)
        SELECT 'presentation',
               json_build_object('aboutText', about_text, 'partnerCount', partner_count)::text,
               'system:migration-v152'
        FROM presentation_settings
        WHERE id = 1
        ON CONFLICT (setting_key) DO NOTHING;

        DROP TABLE presentation_settings;
    END IF;
END $$;
