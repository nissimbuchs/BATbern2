-- V152: Story 4.9 (API conventions I). The moderator presentation settings move from CUMS
-- (table presentation_settings, single row id = 1, CUMS V14) into the EMS app_settings store,
-- key 'presentation', as JSON {"aboutText": ..., "partnerCount": ...}.
--
-- A one-off copy in the shared database, not a runtime read of another service's table
-- (ADR-016 unchanged). Copy only: the table stays, unread, so that an old CUMS image (which
-- still maps the entity under ddl-auto validate) can start during the deploy or after an
-- automatic ECS rollback. A follow-up migration drops it one release later. Where the table does
-- not exist (EMS test containers, a fresh database) this is a no-op; an existing key is kept.
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
    END IF;
END $$;
